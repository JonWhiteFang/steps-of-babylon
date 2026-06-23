package com.whitefang.stepsofbabylon.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted Play Billing receipt — the app's own idempotency record for every purchase that
 * crosses the client/Play-Services boundary. Keyed by `purchaseToken` because `orderId` is
 * nullable on pending purchases (per
 * [Play Billing docs](https://developer.android.com/reference/com/android/billingclient/api/Purchase#getOrderId%28%29)),
 * whereas `purchaseToken` is guaranteed non-null and unique across all purchase states.
 *
 * Lifecycle per receipt (written by [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl]):
 *
 * 1. `purchase()` succeeds in Play Services → row inserted with `granted = false`.
 * 2. Wallet credit + `granted = true` commit atomically via [BillingReceiptDao.grantOnceAtomic].
 *    The atomic path guarantees no crash window can leave the wallet credited but the receipt
 *    un-flagged (double-credit on retry) or the reverse (credit lost).
 * 3. After the grant transaction commits, the manager calls `consumeAsync` (consumables) or
 *    `acknowledgePurchaseAsync` (non-consumables + subscriptions) and flips `consumed` /
 *    `acknowledged` via [BillingReceiptDao.markConsumed] / [BillingReceiptDao.markAcknowledged].
 *    Consume/ack runs outside the grant transaction so SQLite locks are not held across the
 *    Google Play Services RPC. If consume/ack fails the receipt row shows `granted = true,
 *    consumed/acknowledged = false`, and the next reconciliation sweep retries only the
 *    consume/ack call — the wallet-credit side is NOT re-run.
 *
 * Introduced by C.5 PR 1 / ADR-0005 alongside DB schema bump v8 → v9.
 */
@Entity(tableName = "billing_receipt")
data class BillingReceiptEntity(
    /**
     * Play Billing-issued `purchaseToken`. Non-null + unique + stable across re-queries of
     * the same purchase, making it a safe primary key. Large (~200 chars) but SQLite handles
     * TEXT primary keys fine for the tiny row count this table will ever hold (≤ a few dozen
     * per device lifetime).
     */
    @PrimaryKey val purchaseToken: String,
    /**
     * Play Billing `orderId`. Nullable because pending purchases surface without an orderId
     * until the user completes payment (per Play Billing docs).
     */
    @ColumnInfo(defaultValue = "NULL")
    val orderId: String? = null,
    /**
     * SKU identifier — the lowercase `BillingProduct.<variant>` name produced by
     * [com.whitefang.stepsofbabylon.domain.model.BillingProduct.skuId] (e.g. `gem_pack_small`,
     * `ad_removal`, `season_pass`). Play Console's `[a-z0-9._]` product-id requirement made
     * lowercase the canonical wire format post-Plan 31 Phase F (refines ADR-0005 decision #6).
     */
    val productId: String,
    /** Epoch millis when Play Services recorded the purchase. */
    val purchaseTime: Long,
    /**
     * `true` once the wallet side-effect (add gems / set ad-removed / set season-pass) has
     * been committed inside [BillingReceiptDao.grantOnceAtomic]. A `granted = true` row
     * means "do not credit the wallet for this token again, ever."
     */
    @ColumnInfo(defaultValue = "0")
    val granted: Boolean = false,
    /** Epoch millis when [granted] flipped to true. Null while pending. */
    @ColumnInfo(defaultValue = "NULL")
    val grantedAt: Long? = null,
    /**
     * Non-consumable + subscription path. `true` once the Play Billing
     * `acknowledgePurchaseAsync` RPC succeeded. Google auto-refunds purchases that are not
     * acknowledged within 3 days, so this being stuck at `false` is a correctness concern.
     */
    @ColumnInfo(defaultValue = "0")
    val acknowledged: Boolean = false,
    /** Epoch millis when [acknowledged] flipped to true. */
    @ColumnInfo(defaultValue = "NULL")
    val acknowledgedAt: Long? = null,
    /**
     * Consumable path. `true` once the Play Billing `consumeAsync` RPC succeeded. Until this
     * is `true` the user cannot re-purchase the same consumable SKU (Play Services blocks it).
     * Reconciliation retries consume on rows where `granted = true, consumed = false`.
     */
    @ColumnInfo(defaultValue = "0")
    val consumed: Boolean = false,
    /** Epoch millis when [consumed] flipped to true. */
    @ColumnInfo(defaultValue = "NULL")
    val consumedAt: Long? = null,
)
