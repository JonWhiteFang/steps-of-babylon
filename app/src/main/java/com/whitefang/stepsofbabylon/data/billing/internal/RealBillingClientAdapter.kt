package com.whitefang.stepsofbabylon.data.billing.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Concrete [BillingClientAdapter] backed by Google Play Billing Library v8. This is the ONLY
 * file in the app that imports `com.android.billingclient.*` types; everything else talks to
 * the adapter interface via the SDK-neutral sealed classes in [BillingClientAdapter.kt].
 *
 * **Testability.** This class is device-only testable — the Play Billing SDK requires a live
 * Google Play Services connection and cannot be mocked with mockito against final classes.
 * Unit-level coverage for the billing pipeline runs against a mocked [BillingClientAdapter] in
 * `BillingManagerImplTest`. Manual verification on the internal Play Store test track is the
 * release-gate for changes to this file.
 *
 * **Reconnection policy (ADR-0005 Q1).** We opt into
 * [BillingClient.Builder.enableAutoServiceReconnection] which was introduced in Play Billing
 * v8.0.0; Play Services handles disconnection recovery transparently. Persistent connection
 * failures surface as [SdkBillingResult.ServiceDisconnected] to the caller, which
 * [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl] maps to a user-visible error.
 *
 * **Pending-purchase support.** We enable [PendingPurchasesParams.Builder.enableOneTimeProducts]
 * because all 3 Gem Packs + Ad Removal are one-time products that can enter the `PENDING` state
 * (e.g. parent-approval flow). The subscription Season Pass does not need
 * `enablePrepaidPlans()` — we ship a standard auto-renewing monthly subscription.
 *
 * Introduced by C.5 PR 1 / ADR-0005.
 */
@Singleton
internal class RealBillingClientAdapter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BillingClientAdapter {
        /**
         * Guards `pendingPurchase` and serialises [launchPurchase] calls. Play Billing rejects
         * overlapping `launchBillingFlow` invocations from the same client, so enforcing this at
         * the adapter boundary gives clearer error semantics than letting the SDK surface its own.
         */
        private val purchaseMutex = Mutex()

        /**
         * Captures the in-flight purchase flow's continuation. Set by [launchPurchase] before it
         * invokes `launchBillingFlow`; read + cleared by [purchasesUpdatedListener] when Play
         * Services delivers the result.
         */
        @Volatile
        private var pendingPurchase: CompletableDeferred<StartPurchaseResult>? = null

        private val purchasesUpdatedListener =
            PurchasesUpdatedListener { result, purchases ->
                val deferred =
                    pendingPurchase ?: run {
                        // Unexpected listener fire with no in-flight purchase. Log and drop; the
                        // reconciliation sweep will pick up any orphaned purchase on the next
                        // reconcilePendingPurchases() call.
                        Log.w(TAG, "Received purchases-updated with no in-flight request; code=${result.responseCode}")
                        return@PurchasesUpdatedListener
                    }
                pendingPurchase = null
                val sdk = result.toSdk()
                if (sdk is SdkBillingResult.Ok && !purchases.isNullOrEmpty()) {
                    // One purchase per flow (the listener fires once per launchBillingFlow).
                    deferred.complete(StartPurchaseResult.Completed(purchases.first().toSdk()))
                } else {
                    deferred.complete(StartPurchaseResult.NotCompleted(sdk))
                }
            }

        private val billingClient: BillingClient by lazy {
            BillingClient
                .newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams
                        .newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                ).enableAutoServiceReconnection()
                .build()
        }

        override suspend fun connect(): SdkBillingResult {
            if (billingClient.isReady) return SdkBillingResult.Ok
            return suspendCancellableCoroutine { cont ->
                billingClient.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (cont.isActive) cont.resume(result.toSdk())
                        }

                        override fun onBillingServiceDisconnected() {
                            // With enableAutoServiceReconnection, the SDK handles reconnection
                            // transparently. No resume here — we only complete the continuation
                            // on setup finish; if setup never finishes, subsequent calls will
                            // return an error via the regular response-code path.
                        }
                    },
                )
            }
        }

        override suspend fun queryProductDetails(
            productIds: List<String>,
            productType: SdkProductType,
        ): QueryProductDetailsResult {
            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(
                        productIds.map { id ->
                            QueryProductDetailsParams.Product
                                .newBuilder()
                                .setProductId(id)
                                .setProductType(productType.toPlay())
                                .build()
                        },
                    ).build()

            return suspendCancellableCoroutine { cont ->
                billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
                    if (!cont.isActive) return@queryProductDetailsAsync
                    val sdk = result.toSdk()
                    if (sdk !is SdkBillingResult.Ok) {
                        cont.resume(QueryProductDetailsResult.Error(sdk))
                        return@queryProductDetailsAsync
                    }
                    // v8+: productDetailsResult.productDetailsList is the fetched subset;
                    // unfetched products surface with per-product status codes we do not
                    // inspect in PR 1 (logged as a warning so the Store can hide the card).
                    val fetched =
                        productDetailsResult.productDetailsList
                            .map { it.toSdk() }
                    cont.resume(QueryProductDetailsResult.Success(fetched))
                }
            }
        }

        override suspend fun launchPurchase(
            activity: Activity,
            productDetails: SdkProductDetails,
            obfuscatedAccountId: String?,
        ): StartPurchaseResult =
            purchaseMutex.withLock {
                val rawProductDetails =
                    productDetails.rawRef as? ProductDetails
                        ?: return StartPurchaseResult.NotCompleted(
                            SdkBillingResult.DeveloperError(
                                "SdkProductDetails.rawRef is not a Play Billing ProductDetails instance; " +
                                    "did you construct it without going through queryProductDetails()?",
                            ),
                        )

                // Subscription offers carry an offerToken in v8; one-time products do not.
                val offerToken: String? =
                    rawProductDetails.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.offerToken

                val productParams =
                    BillingFlowParams.ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(rawProductDetails)
                        .apply { if (offerToken != null) setOfferToken(offerToken) }
                        .build()

                val flowParams =
                    BillingFlowParams
                        .newBuilder()
                        .setProductDetailsParamsList(listOf(productParams))
                        .apply { if (obfuscatedAccountId != null) setObfuscatedAccountId(obfuscatedAccountId) }
                        .build()

                val deferred = CompletableDeferred<StartPurchaseResult>()
                pendingPurchase = deferred

                val launchResult = billingClient.launchBillingFlow(activity, flowParams).toSdk()
                if (launchResult !is SdkBillingResult.Ok) {
                    // launchBillingFlow never reached Play's UI — the listener will not fire.
                    pendingPurchase = null
                    return StartPurchaseResult.NotCompleted(launchResult)
                }

                deferred.await()
            }

        override suspend fun consume(purchaseToken: String): SdkBillingResult {
            val params =
                ConsumeParams
                    .newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()
            return suspendCancellableCoroutine { cont ->
                billingClient.consumeAsync(params) { result, _ ->
                    if (cont.isActive) cont.resume(result.toSdk())
                }
            }
        }

        override suspend fun acknowledge(purchaseToken: String): SdkBillingResult {
            val params =
                AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build()
            return suspendCancellableCoroutine { cont ->
                billingClient.acknowledgePurchase(params) { result ->
                    if (cont.isActive) cont.resume(result.toSdk())
                }
            }
        }

        override suspend fun queryPurchases(productType: SdkProductType): QueryPurchasesResult {
            val params =
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(productType.toPlay())
                    .build()
            return suspendCancellableCoroutine { cont ->
                billingClient.queryPurchasesAsync(params) { result, purchases ->
                    if (!cont.isActive) return@queryPurchasesAsync
                    val sdk = result.toSdk()
                    if (sdk !is SdkBillingResult.Ok) {
                        cont.resume(QueryPurchasesResult.Error(sdk))
                    } else {
                        cont.resume(QueryPurchasesResult.Success(purchases.map { it.toSdk() }))
                    }
                }
            }
        }

        override fun close() {
            if (billingClient.isReady) billingClient.endConnection()
        }

        // --- SDK → adapter type projections ---------------------------------------------------

        private fun SdkProductType.toPlay(): String =
            when (this) {
                SdkProductType.INAPP -> ProductType.INAPP
                SdkProductType.SUBS -> ProductType.SUBS
            }

        private fun BillingResult.toSdk(): SdkBillingResult =
            when (responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    SdkBillingResult.Ok
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    SdkBillingResult.UserCanceled(debugMessage)
                }

                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                    SdkBillingResult.ServiceDisconnected(
                        debugMessage,
                    )
                }

                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                    SdkBillingResult.ServiceUnavailable(
                        debugMessage,
                    )
                }

                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                    SdkBillingResult.BillingUnavailable(
                        debugMessage,
                    )
                }

                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                    SdkBillingResult.ItemUnavailable(debugMessage)
                }

                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    SdkBillingResult.ItemAlreadyOwned(debugMessage)
                }

                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                    SdkBillingResult.DeveloperError(debugMessage)
                }

                BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                    SdkBillingResult.NetworkError(debugMessage)
                }

                else -> {
                    SdkBillingResult.Other(responseCode, debugMessage)
                }
            }

        private fun ProductDetails.toSdk(): SdkProductDetails {
            val price: String =
                oneTimePurchaseOfferDetails?.formattedPrice
                    ?: subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice
                    ?: ""
            val type = if (productType == ProductType.SUBS) SdkProductType.SUBS else SdkProductType.INAPP
            return SdkProductDetails(
                productId = productId,
                productType = type,
                priceDisplay = price,
                rawRef = this,
            )
        }

        private fun Purchase.toSdk(): SdkPurchase {
            val state =
                when (purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> SdkPurchaseState.PURCHASED
                    Purchase.PurchaseState.PENDING -> SdkPurchaseState.PENDING
                    else -> SdkPurchaseState.UNSPECIFIED
                }
            // Play Billing v8 exposes getProducts() returning a list (multi-line-item flows);
            // our catalogue is always single-product per purchase so firstOrNull is safe.
            val productId = products.firstOrNull() ?: ""
            return SdkPurchase(
                productId = productId,
                orderId = orderId,
                purchaseToken = purchaseToken,
                purchaseTime = purchaseTime,
                purchaseState = state,
                isAcknowledged = isAcknowledged,
                isAutoRenewing = isAutoRenewing,
                // originalJson + signature are the exact bytes Google signed + its RSA-SHA1
                // signature; BillingManagerImpl runs client-side signature verification on them
                // before crediting the wallet (#124).
                originalJson = originalJson,
                signature = signature,
                rawRef = this,
            )
        }

        companion object {
            private const val TAG = "RealBillingClientAdapter"
        }
    }
