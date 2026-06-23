package com.whitefang.stepsofbabylon.data.billing.internal

import android.app.Activity

/**
 * SDK-neutral seam between [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl] and the
 * Google Play Billing Library. The impl talks to this interface only; the one concrete
 * implementation — [RealBillingClientAdapter] — is the single place in the codebase that
 * references `com.android.billingclient.*` types.
 *
 * Purpose:
 *
 * - **Testability.** `BillingClient` and its collaborators (`Purchase`, `ProductDetails`,
 *   `BillingResult`, `PurchasesUpdatedListener`) are final classes that cannot be mocked with
 *   the default mockito-subclass mock-maker. By contrast, this interface is plain Kotlin and
 *   mocks with zero configuration, which is what `BillingManagerImplTest` relies on.
 * - **Anti-corruption layer.** SDK types leak their listener-callback shape, nullable fields,
 *   and response-code magic numbers into anything that touches them. Collapsing all of that
 *   into a suspend-and-sealed-class surface keeps the impl's logic SDK-version-agnostic.
 * - **Version upgrade insulation.** When Play Billing v9 (or v10) lands with a renamed API,
 *   only [RealBillingClientAdapter] needs changes — the contract with the impl stays stable.
 *
 * All methods assume [connect] has been called first (or will be called lazily on first use).
 * Implementations must be safe for concurrent `purchase()` + `queryPurchases()` on the same
 * client; the default real impl serialises via a single `BillingClient` instance.
 *
 * Introduced by C.5 PR 1 / ADR-0005.
 */
internal interface BillingClientAdapter {
    /**
     * Connects to Play Services. Implementations are expected to apply their own retry /
     * exponential-backoff policy internally and only return [SdkBillingResult.Ok] when the
     * client is fully ready for queries and purchases. Callers may invoke this eagerly at
     * app start, or let the adapter connect lazily inside other methods.
     */
    suspend fun connect(): SdkBillingResult

    /**
     * Queries Play Services for the product details of [productIds], filtered to [productType].
     * Returns [QueryProductDetailsResult.Success] with one entry per product that Play Services
     * recognises; missing products are dropped (callers should diff against their enum and log).
     */
    suspend fun queryProductDetails(
        productIds: List<String>,
        productType: SdkProductType,
    ): QueryProductDetailsResult

    /**
     * Launches the Play Billing purchase flow for [productDetails] from [activity] and
     * suspends until Play Services reports a result. The resulting [StartPurchaseResult]
     * reflects only whether a purchase was created — the caller must inspect the purchase's
     * [SdkPurchase.purchaseState] to distinguish `PURCHASED` from `PENDING`.
     *
     * @param obfuscatedAccountId Opaque, one-way-hashed device-local identifier passed to
     *                            Play Services as an anti-fraud signal (see ADR-0005 Q5).
     *                            Pass `null` to skip — purchases still work but lose that
     *                            fraud signal.
     */
    suspend fun launchPurchase(
        activity: Activity,
        productDetails: SdkProductDetails,
        obfuscatedAccountId: String?,
    ): StartPurchaseResult

    /**
     * Consumes a consumable purchase (Gem packs). After a successful consume, Play Services
     * allows the user to repurchase the same SKU. Must be called AFTER the wallet grant has
     * committed; see [com.whitefang.stepsofbabylon.data.local.BillingReceiptDao.grantOnceAtomic]
     * for the ordering contract.
     */
    suspend fun consume(purchaseToken: String): SdkBillingResult

    /**
     * Acknowledges a non-consumable or subscription purchase (Ad Removal, Season Pass).
     * Google auto-refunds purchases that are not acknowledged within 3 days, so this call is
     * non-optional for the release build. Must be called AFTER the grant commit.
     */
    suspend fun acknowledge(purchaseToken: String): SdkBillingResult

    /**
     * Returns all current purchases of [productType] known to Play Services. Used by the
     * reconciliation sweep to recover purchases that completed while the app was killed
     * mid-flow, and by the session startup path to detect subscription state.
     */
    suspend fun queryPurchases(productType: SdkProductType): QueryPurchasesResult

    /** Releases the underlying Play Billing client. Called from app teardown / tests. */
    fun close()
}

/**
 * Product type shim over Play Billing's `ProductType` string constants. Kept as an enum so
 * the impl can switch on it exhaustively and tests can construct instances without the SDK.
 */
internal enum class SdkProductType { INAPP, SUBS }

/**
 * Response shape from [BillingClientAdapter.queryProductDetails]. `Success` carries zero-or-more
 * product entries (a product-details query that finds nothing is not itself an error — the
 * SDK returns `ProductType.OK` with an empty list); callers must check the list against the
 * requested IDs to detect SKU drift.
 */
internal sealed class QueryProductDetailsResult {
    data class Success(
        val products: List<SdkProductDetails>,
    ) : QueryProductDetailsResult()

    data class Error(
        val result: SdkBillingResult,
    ) : QueryProductDetailsResult()
}

/**
 * Response shape from [BillingClientAdapter.launchPurchase]. A completed purchase is conveyed
 * as [Completed] regardless of whether the state is `PURCHASED` or `PENDING`; the caller
 * decides how to react (credit vs. persist-and-defer). `NotCompleted` covers user-cancelled,
 * service-disconnected, and every other non-purchase outcome.
 */
internal sealed class StartPurchaseResult {
    data class Completed(
        val purchase: SdkPurchase,
    ) : StartPurchaseResult()

    data class NotCompleted(
        val result: SdkBillingResult,
    ) : StartPurchaseResult()
}

/** Response shape from [BillingClientAdapter.queryPurchases]. */
internal sealed class QueryPurchasesResult {
    data class Success(
        val purchases: List<SdkPurchase>,
    ) : QueryPurchasesResult()

    data class Error(
        val result: SdkBillingResult,
    ) : QueryPurchasesResult()
}

/**
 * Platform-neutral projection of Play Billing's `Purchase`. Carries only the fields the
 * impl actually needs, which keeps tests cheap to construct.
 *
 * @property originalJson Play Billing's `Purchase.getOriginalJson()` — the exact bytes Google
 *                        signed. Carried (alongside [signature]) so the impl can run client-side
 *                        RSA signature verification before granting (#124). Nullable because a
 *                        forged / stripped purchase may not carry it, and tests that don't
 *                        exercise verification omit it.
 * @property signature    Play Billing's `Purchase.getSignature()` — the Base64 RSA-SHA1 signature
 *                        over [originalJson]. See [originalJson].
 * @property rawRef       Opaque back-reference to the original SDK object. Used by the real adapter
 *                        when a later RPC (consume / acknowledge) needs the SDK-level purchase
 *                        context. Tests pass `null`.
 */
internal data class SdkPurchase(
    val productId: String,
    val orderId: String?,
    val purchaseToken: String,
    val purchaseTime: Long,
    val purchaseState: SdkPurchaseState,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val originalJson: String? = null,
    val signature: String? = null,
    val rawRef: Any? = null,
)

/** Mirrors Play Billing's `Purchase.PurchaseState`. `UNSPECIFIED` covers all other codes. */
internal enum class SdkPurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/**
 * Platform-neutral projection of Play Billing's `ProductDetails`. `rawRef` holds the
 * original SDK object so the real adapter can pass it to `launchBillingFlow`. Tests construct
 * instances with `rawRef = null`.
 */
internal data class SdkProductDetails(
    val productId: String,
    val productType: SdkProductType,
    val priceDisplay: String,
    val rawRef: Any? = null,
)

/**
 * Platform-neutral Play Billing response codes. Only the codes the impl branches on have
 * dedicated variants; everything else falls through to [Other]. `Ok` signals an actionable
 * success — the caller may proceed.
 *
 * Play Billing response codes map as follows:
 *
 * | Play code                   | Variant                |
 * |-----------------------------|------------------------|
 * | `OK`                        | [Ok]                   |
 * | `USER_CANCELED`             | [UserCanceled]         |
 * | `SERVICE_DISCONNECTED`      | [ServiceDisconnected]  |
 * | `SERVICE_UNAVAILABLE`       | [ServiceUnavailable]   |
 * | `BILLING_UNAVAILABLE`       | [BillingUnavailable]   |
 * | `ITEM_UNAVAILABLE`          | [ItemUnavailable]      |
 * | `ITEM_ALREADY_OWNED`        | [ItemAlreadyOwned]     |
 * | `DEVELOPER_ERROR`           | [DeveloperError]       |
 * | `NETWORK_ERROR`             | [NetworkError]         |
 * | _any other_                 | [Other]                |
 */
internal sealed class SdkBillingResult {
    /** Actionable success. */
    data object Ok : SdkBillingResult()

    data class UserCanceled(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class ServiceDisconnected(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class ServiceUnavailable(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class BillingUnavailable(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class ItemUnavailable(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class ItemAlreadyOwned(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class DeveloperError(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class NetworkError(
        val debugMessage: String?,
    ) : SdkBillingResult()

    data class Other(
        val responseCode: Int,
        val debugMessage: String?,
    ) : SdkBillingResult()
}
