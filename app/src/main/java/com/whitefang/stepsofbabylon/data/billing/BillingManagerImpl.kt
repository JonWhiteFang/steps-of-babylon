package com.whitefang.stepsofbabylon.data.billing

import android.content.Context
import android.util.Log
import com.whitefang.stepsofbabylon.data.billing.internal.ActivityProvider
import com.whitefang.stepsofbabylon.data.billing.internal.BillingClientAdapter
import com.whitefang.stepsofbabylon.data.billing.internal.PurchaseVerifier
import com.whitefang.stepsofbabylon.data.billing.internal.QueryProductDetailsResult
import com.whitefang.stepsofbabylon.data.billing.internal.QueryPurchasesResult
import com.whitefang.stepsofbabylon.data.billing.internal.SdkBillingResult
import com.whitefang.stepsofbabylon.data.billing.internal.SdkProductType
import com.whitefang.stepsofbabylon.data.billing.internal.SdkPurchase
import com.whitefang.stepsofbabylon.data.billing.internal.SdkPurchaseState
import com.whitefang.stepsofbabylon.data.billing.internal.StartPurchaseResult
import com.whitefang.stepsofbabylon.data.local.BillingReceiptDao
import com.whitefang.stepsofbabylon.data.local.BillingReceiptEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [BillingManager] implementation wiring Google Play Billing Library v8 to the app's
 * Room-backed wallet. Sole [BillingManager] binding post-C.5 PR 3 — the previous
 * `StubBillingManager` was deleted after the C.5 PR 2 internal-track verification
 * confirmed real-device wallet credit end-to-end. Introduced by C.5 PR 1 / ADR-0005.
 *
 * **Design invariants.**
 *
 * 1. Wallet credits participate in [BillingReceiptDao.grantOnceAtomic] — the receipt row
 *    flips `granted = true` and the wallet write commit atomically in one SQLite
 *    transaction. A crash between them is impossible.
 * 2. Play Services RPCs (`consumeAsync`, `acknowledgePurchaseAsync`) run AFTER the grant
 *    transaction commits, so a SQLite write lock is never held across a Google Play
 *    round-trip. Failed consume/ack is retried on the next [reconcilePendingPurchases]
 *    sweep without re-crediting the wallet (the `granted = true` guard short-circuits).
 * 3. `purchase()` handlers for `PENDING` purchases write the receipt row with
 *    `granted = false` but do not credit the wallet. The next reconciliation sweep
 *    observes the promotion to `PURCHASED` and routes through [grantOnceAtomic] — still
 *    exactly one credit.
 * 4. SKU IDs are the lowercase [BillingProduct] enum name, byte-for-byte
 *    (per ADR-0005 decision #6, refined post-Plan 31 Phase F to match Play Console's
 *    `[a-z0-9._]` product-id requirement). `gem_pack_small`, `ad_removal`,
 *    `season_pass`, etc. — see [BillingProduct.skuId]. Play Console SKU configuration
 *    must match or product-details queries return empty results.
 * 5. Anti-fraud obfuscatedAccountId (ADR-0005 Q5) is a SHA-256 hex of a device-local UUID
 *    stored in [ANTI_FRAUD_PREFS]; no PII leaves the device.
 *
 * **What this class deliberately does NOT do.**
 *
 * - No server-side receipt verification (forbidden by `CONSTRAINTS.md` for v1.0).
 * - No custom subscription-renewal tracking. Season Pass expiry is computed as
 *   `purchaseTime + 30 days`; Play Store re-delivers the Purchase on each renewal and the
 *   reconciliation sweep refreshes the expiry. Real-time expiry tracking would require
 *   Real-time Developer Notifications + a backend, which v1.0 does not ship.
 * - No caching of [com.whitefang.stepsofbabylon.data.billing.internal.SdkProductDetails].
 *   Every [purchase] call re-queries product details. This is ~100 ms per purchase on
 *   normal networks and sidesteps the whole "cache invalidation on price change" class of
 *   bugs. Revisit post-v1.0 if product-details-fetch latency becomes user-visible.
 */
@Singleton
internal class BillingManagerImpl
    @Inject
    constructor(
        private val adapter: BillingClientAdapter,
        private val receiptDao: BillingReceiptDao,
        private val playerProfileDao: PlayerProfileDao,
        private val playerRepository: PlayerRepository,
        private val activityProvider: ActivityProvider,
        private val verifier: PurchaseVerifier,
        @ApplicationContext private val context: Context,
    ) : BillingManager {
        /** Serialises [purchase] + [reconcilePendingPurchases] so the two cannot race. */
        private val sessionMutex = Mutex()

        @Volatile
        private var connected: Boolean = false

        override suspend fun purchase(product: BillingProduct): PurchaseResult =
            sessionMutex.withLock {
                val connect = ensureConnected()
                if (connect !is SdkBillingResult.Ok) {
                    return@withLock PurchaseResult.Error(connect.toUserMessage())
                }

                val activity =
                    activityProvider.current()
                        ?: return@withLock PurchaseResult.Error("No activity available for purchase")

                val productType = product.sdkProductType()

                // Fetch product details fresh per purchase — ~100ms on normal networks. See class KDoc.
                val detailsResult = adapter.queryProductDetails(listOf(product.skuId()), productType)
                val productDetails =
                    when (detailsResult) {
                        is QueryProductDetailsResult.Success -> {
                            detailsResult.products.firstOrNull()
                                ?: return@withLock PurchaseResult.Error(
                                    "Product ${product.skuId()} not available. " +
                                        "(Play Console SKU may be missing or not yet released.)",
                                )
                        }

                        is QueryProductDetailsResult.Error -> {
                            return@withLock PurchaseResult.Error(detailsResult.result.toUserMessage())
                        }
                    }

                val launch =
                    adapter.launchPurchase(
                        activity = activity,
                        productDetails = productDetails,
                        obfuscatedAccountId = obfuscatedAccountIdHex(),
                    )

                return@withLock when (launch) {
                    is StartPurchaseResult.Completed -> {
                        handleCompletedPurchase(product, launch.purchase)
                    }

                    is StartPurchaseResult.NotCompleted -> {
                        when (launch.result) {
                            is SdkBillingResult.UserCanceled -> PurchaseResult.Error("Purchase cancelled")
                            else -> PurchaseResult.Error(launch.result.toUserMessage())
                        }
                    }
                }
            }

        override suspend fun isAdRemoved(): Boolean = playerRepository.observeProfile().first().adRemoved

        override suspend fun isSeasonPassActive(): Boolean {
            val profile = playerRepository.observeProfile().first()
            if (profile.seasonPassActive && profile.seasonPassExpiry < System.currentTimeMillis()) {
                playerRepository.updateSeasonPass(false, 0)
                return false
            }
            return profile.seasonPassActive
        }

        override suspend fun getPriceDisplay(product: BillingProduct): String? =
            sessionMutex.withLock {
                val connect = ensureConnected()
                if (connect !is SdkBillingResult.Ok) {
                    Log.w(TAG, "getPriceDisplay(${product.skuId()}): connect failed; falling back to null. $connect")
                    return@withLock null
                }
                val productType = product.sdkProductType()
                val result = adapter.queryProductDetails(listOf(product.skuId()), productType)
                when (result) {
                    is QueryProductDetailsResult.Success -> {
                        result.products.firstOrNull()?.priceDisplay
                    }

                    is QueryProductDetailsResult.Error -> {
                        Log.w(
                            TAG,
                            "getPriceDisplay(${product.skuId()}): query failed; falling back to null. ${result.result}",
                        )
                        null
                    }
                }
            }

        override suspend fun reconcilePendingPurchases() =
            sessionMutex.withLock {
                val connect = ensureConnected()
                if (connect !is SdkBillingResult.Ok) {
                    Log.w(TAG, "reconcilePendingPurchases: connect failed, skipping. $connect")
                    return@withLock
                }

                reconcileType(SdkProductType.INAPP)
                reconcileType(SdkProductType.SUBS)
                retryUnresolvedConsumeOrAck()
            }

        // --- private helpers -------------------------------------------------------------------

        private suspend fun ensureConnected(): SdkBillingResult {
            if (connected) return SdkBillingResult.Ok
            val result = adapter.connect()
            if (result is SdkBillingResult.Ok) connected = true
            return result
        }

        private suspend fun handleCompletedPurchase(
            product: BillingProduct,
            purchase: SdkPurchase,
        ): PurchaseResult {
            // PENDING purchases: persist the receipt but do not grant. Next reconciliation sweep
            // picks up the PURCHASED transition and runs grantOnceAtomic then.
            if (purchase.purchaseState == SdkPurchaseState.PENDING) {
                receiptDao.upsert(
                    BillingReceiptEntity(
                        purchaseToken = purchase.purchaseToken,
                        orderId = purchase.orderId,
                        productId = product.skuId(),
                        purchaseTime = purchase.purchaseTime,
                        granted = false,
                    ),
                )
                return PurchaseResult.Error("Purchase pending — complete payment to receive your items")
            }

            // #124: verify Google's RSA signature over the purchase payload AND that the signed
            // productId + purchaseToken match what we're about to grant, BEFORE any grant. A forged
            // / tampered / replayed purchase (rooted device, hooked Play Billing, repackaged APK,
            // or a genuinely-signed cheap receipt re-aimed at an expensive product) is rejected here
            // — no receipt row, no wallet credit, no consume/acknowledge.
            if (!verifier.isValidPurchase(
                    originalJson = purchase.originalJson,
                    signature = purchase.signature,
                    expectedProductId = product.skuId(),
                    expectedPurchaseToken = purchase.purchaseToken,
                )
            ) {
                Log.w(TAG, "Rejecting purchase ${purchase.purchaseToken}: signature verification failed.")
                return PurchaseResult.Error("Purchase could not be verified")
            }

            // PURCHASED or UNSPECIFIED: grant atomically (upsert + wallet credit + granted flag).
            val receipt =
                BillingReceiptEntity(
                    purchaseToken = purchase.purchaseToken,
                    orderId = purchase.orderId,
                    productId = product.skuId(),
                    purchaseTime = purchase.purchaseTime,
                    granted = false, // flipped to true by grantOnceAtomic
                )

            val granted =
                receiptDao.grantOnceAtomic(
                    receipt = receipt,
                    grantedAt = System.currentTimeMillis(),
                    walletCredit = { creditWallet(product, purchase) },
                )

            // grantOnceAtomic returns false when the same purchaseToken was already granted —
            // typically because a concurrent reconciliation sweep beat us to it. Either way the
            // wallet is consistent, so we report success.
            if (!granted) {
                Log.i(TAG, "Receipt ${purchase.purchaseToken} already granted; short-circuit Success.")
            }

            // Consume / acknowledge happens AFTER the grant transaction — intentionally outside
            // the Room lock so we do not hold SQLite across a Play Services RPC.
            finalizePurchase(product, purchase)
            return PurchaseResult.Success
        }

        /**
         * Wallet side-effect of a grant. Called from inside [BillingReceiptDao.grantOnceAtomic]
         * so the writes participate in the receipt transaction.
         */
        private suspend fun creditWallet(
            product: BillingProduct,
            purchase: SdkPurchase,
        ) {
            when (product) {
                BillingProduct.GEM_PACK_SMALL,
                BillingProduct.GEM_PACK_MEDIUM,
                BillingProduct.GEM_PACK_LARGE,
                -> {
                    val amount = product.gemAmount
                    playerProfileDao.adjustGems(amount)
                    playerProfileDao.incrementGemsEarned(amount)
                }

                BillingProduct.AD_REMOVAL -> {
                    playerProfileDao.updateAdRemoved(true)
                }

                BillingProduct.SEASON_PASS -> {
                    val expiry = purchase.purchaseTime + THIRTY_DAYS_MILLIS
                    playerProfileDao.updateSeasonPass(active = true, expiry = expiry)
                }
            }
        }

        /**
         * Post-grant finalization — consume for consumables, acknowledge for non-consumables /
         * subscriptions. Failures are logged and recorded (row stays with `consumed = false` or
         * `acknowledged = false`); [retryUnresolvedConsumeOrAck] picks them up on the next sweep.
         * Google auto-refunds purchases not acknowledged within 3 days, so a release-build device
         * MUST reach this path repeatedly on poor networks — logging only is intentional.
         */
        private suspend fun finalizePurchase(
            product: BillingProduct,
            purchase: SdkPurchase,
        ) {
            when (product) {
                BillingProduct.GEM_PACK_SMALL,
                BillingProduct.GEM_PACK_MEDIUM,
                BillingProduct.GEM_PACK_LARGE,
                -> {
                    val result = adapter.consume(purchase.purchaseToken)
                    if (result is SdkBillingResult.Ok) {
                        receiptDao.markConsumed(purchase.purchaseToken, System.currentTimeMillis())
                    } else {
                        Log.w(TAG, "consume failed for ${purchase.purchaseToken}: $result")
                    }
                }

                BillingProduct.AD_REMOVAL, BillingProduct.SEASON_PASS -> {
                    // Already acknowledged by Play Services? Skip the RPC.
                    if (purchase.isAcknowledged) {
                        receiptDao.markAcknowledged(purchase.purchaseToken, System.currentTimeMillis())
                        return
                    }
                    val result = adapter.acknowledge(purchase.purchaseToken)
                    if (result is SdkBillingResult.Ok) {
                        receiptDao.markAcknowledged(purchase.purchaseToken, System.currentTimeMillis())
                    } else {
                        Log.w(TAG, "acknowledge failed for ${purchase.purchaseToken}: $result")
                    }
                }
            }
        }

        private suspend fun reconcileType(productType: SdkProductType) {
            val query = adapter.queryPurchases(productType)
            if (query !is QueryPurchasesResult.Success) {
                Log.w(TAG, "reconcile: queryPurchases($productType) failed: $query")
                return
            }
            for (purchase in query.purchases) {
                val product = BillingProduct.fromSkuIdOrNull(purchase.productId) ?: continue
                if (purchase.purchaseState == SdkPurchaseState.PENDING) {
                    // Keep the pending receipt in sync; do not grant.
                    receiptDao.upsert(
                        BillingReceiptEntity(
                            purchaseToken = purchase.purchaseToken,
                            orderId = purchase.orderId,
                            productId = product.skuId(),
                            purchaseTime = purchase.purchaseTime,
                            granted = false,
                        ),
                    )
                    continue
                }
                if (purchase.purchaseState != SdkPurchaseState.PURCHASED) continue

                // #124: same signature + product-binding gate as the live purchase path. A forged
                // purchase injected into the queryPurchases() result — or a validly-signed receipt
                // whose signed productId/token doesn't match — is skipped, never granted, never
                // persisted. Note `product` here is derived from the UNSIGNED purchase.productId
                // (line above), so binding the signed productId to product.skuId() is what closes
                // the substitution on this path too.
                if (!verifier.isValidPurchase(
                        originalJson = purchase.originalJson,
                        signature = purchase.signature,
                        expectedProductId = product.skuId(),
                        expectedPurchaseToken = purchase.purchaseToken,
                    )
                ) {
                    Log.w(TAG, "reconcile: rejecting ${purchase.purchaseToken}: signature verification failed.")
                    continue
                }

                val receipt =
                    BillingReceiptEntity(
                        purchaseToken = purchase.purchaseToken,
                        orderId = purchase.orderId,
                        productId = product.skuId(),
                        purchaseTime = purchase.purchaseTime,
                        granted = false,
                    )
                receiptDao.grantOnceAtomic(
                    receipt = receipt,
                    grantedAt = System.currentTimeMillis(),
                    walletCredit = { creditWallet(product, purchase) },
                )
                finalizePurchase(product, purchase)
            }
        }

        /**
         * Recover consume / acknowledge RPCs that failed after the grant landed in Room. The
         * receipt table has `granted = true, consumed/acknowledged = false` on those rows;
         * we re-issue only the failing side, never re-crediting the wallet.
         */
        private suspend fun retryUnresolvedConsumeOrAck() {
            val unresolved = receiptDao.getGrantedButUnresolved()
            for (row in unresolved) {
                val product = BillingProduct.fromSkuIdOrNull(row.productId) ?: continue
                when (product) {
                    BillingProduct.GEM_PACK_SMALL,
                    BillingProduct.GEM_PACK_MEDIUM,
                    BillingProduct.GEM_PACK_LARGE,
                    -> {
                        val result = adapter.consume(row.purchaseToken)
                        if (result is SdkBillingResult.Ok) {
                            receiptDao.markConsumed(row.purchaseToken, System.currentTimeMillis())
                        }
                    }

                    BillingProduct.AD_REMOVAL, BillingProduct.SEASON_PASS -> {
                        val result = adapter.acknowledge(row.purchaseToken)
                        if (result is SdkBillingResult.Ok) {
                            receiptDao.markAcknowledged(row.purchaseToken, System.currentTimeMillis())
                        }
                    }
                }
            }
        }

        /**
         * Returns the hex SHA-256 of a device-local UUID, used as Play Billing's
         * `obfuscatedAccountId` anti-fraud signal. No PII — the UUID is random and never
         * leaves the device. Cleared SharedPreferences regenerates the UUID, which is
         * acceptable because the signal is probabilistic.
         */
        private fun obfuscatedAccountIdHex(): String {
            val prefs = context.getSharedPreferences(ANTI_FRAUD_PREFS, Context.MODE_PRIVATE)
            val uuid =
                prefs.getString(KEY_ANTI_FRAUD_UUID, null)
                    ?: UUID.randomUUID().toString().also {
                        prefs.edit().putString(KEY_ANTI_FRAUD_UUID, it).apply()
                    }
            val digest = MessageDigest.getInstance("SHA-256").digest(uuid.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun BillingProduct.sdkProductType(): SdkProductType =
            when (this) {
                BillingProduct.SEASON_PASS -> SdkProductType.SUBS
                else -> SdkProductType.INAPP
            }

        private fun SdkBillingResult.toUserMessage(): String =
            when (this) {
                is SdkBillingResult.Ok -> "OK"
                is SdkBillingResult.UserCanceled -> "Purchase cancelled"
                is SdkBillingResult.ServiceDisconnected -> "Billing service unavailable. Try again shortly."
                is SdkBillingResult.ServiceUnavailable -> "Billing service unavailable. Try again shortly."
                is SdkBillingResult.BillingUnavailable -> "Google Play billing is not available on this device."
                is SdkBillingResult.ItemUnavailable -> "This product is not available right now."
                is SdkBillingResult.ItemAlreadyOwned -> "You already own this item."
                is SdkBillingResult.DeveloperError -> "Purchase failed (configuration error)."
                is SdkBillingResult.NetworkError -> "Network error. Check your connection and try again."
                is SdkBillingResult.Other -> "Purchase failed (code $responseCode)."
            }

        companion object {
            private const val TAG = "BillingManagerImpl"
            private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
            private const val ANTI_FRAUD_PREFS = "billing_anti_fraud"
            private const val KEY_ANTI_FRAUD_UUID = "obfuscated_account_uuid"
        }
    }

/**
 * Maps a Play Billing `productId` back to its [BillingProduct] enum, or `null` if the id is
 * unknown (a drift / orphan purchase). Kept as an extension on the companion so tests can use
 * the same mapping without exposing internals.
 */
internal fun BillingProduct.Companion.fromSkuIdOrNull(skuId: String): BillingProduct? =
    BillingProduct.entries.firstOrNull { it.skuId() == skuId }
