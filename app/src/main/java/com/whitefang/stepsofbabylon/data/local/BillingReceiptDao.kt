package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * DAO for Play Billing receipts — the local idempotency record keyed by `purchaseToken`.
 *
 * The core correctness primitive is [grantOnceAtomic], which combines "insert-or-read the
 * receipt, check `granted`, flip `granted = true`, credit the wallet" into a single SQLite
 * transaction. It is the billing counterpart to the RO-02 pattern already proven by
 * [MilestoneDao.claimMilestoneAtomic] (B.2 PR 4) and [WorkshopDao.purchaseUpgradeAtomic]
 * (B.2 PR 1).
 *
 * Introduced by C.5 PR 1 / ADR-0005 alongside DB schema bump v8 → v9.
 */
@Dao
interface BillingReceiptDao {
    @Query("SELECT * FROM billing_receipt WHERE purchaseToken = :purchaseToken")
    suspend fun getByToken(purchaseToken: String): BillingReceiptEntity?

    @Query("SELECT * FROM billing_receipt ORDER BY purchaseTime DESC")
    suspend fun getAll(): List<BillingReceiptEntity>

    /**
     * Receipts that were granted locally but whose Play Services consume/acknowledge RPC
     * has not yet completed. Reconciliation sweeps this set and retries only the SDK call —
     * the wallet credit already landed in [grantOnceAtomic].
     */
    @Query(
        """
        SELECT * FROM billing_receipt
        WHERE granted = 1
          AND acknowledged = 0
          AND consumed = 0
        """,
    )
    suspend fun getGrantedButUnresolved(): List<BillingReceiptEntity>

    @Upsert
    suspend fun upsert(receipt: BillingReceiptEntity)

    @Query(
        "UPDATE billing_receipt SET acknowledged = 1, acknowledgedAt = :acknowledgedAt " +
            "WHERE purchaseToken = :purchaseToken",
    )
    suspend fun markAcknowledged(
        purchaseToken: String,
        acknowledgedAt: Long,
    )

    @Query(
        "UPDATE billing_receipt SET consumed = 1, consumedAt = :consumedAt " +
            "WHERE purchaseToken = :purchaseToken",
    )
    suspend fun markConsumed(
        purchaseToken: String,
        consumedAt: Long,
    )

    /**
     * Atomically inserts (or updates) the receipt, flips `granted = true`, and credits the
     * wallet via [walletCredit] — all in a single SQLite transaction. The transaction
     * short-circuits with `false` if a row with the same [BillingReceiptEntity.purchaseToken]
     * already has `granted = true`, so the wallet credit is never double-applied.
     *
     * Closes two previously-open correctness windows in the billing path:
     *
     * 1. **Partial-failure gap** — without the transaction, a crash between "wallet credited"
     *    and "receipt row flipped to granted" would let a retry double-credit the wallet.
     * 2. **Concurrent-purchase race** — two `purchase()` calls resolving the same
     *    `purchaseToken` simultaneously (e.g. `purchase()` + `reconcilePendingPurchases()`
     *    racing on app resume) could both read `granted = false` and both credit. SQLite's
     *    default SERIALIZABLE isolation makes the second transaction see `granted = true`
     *    and return `false`.
     *
     * Room wraps this default-method body in a single transaction; because [walletCredit]
     * calls are routed to DAO methods on the same [androidx.room.RoomDatabase] instance,
     * they participate in the enclosing transaction (Room's tracker is database-scoped,
     * not DAO-scoped). This is the same pattern as [MilestoneDao.claimMilestoneAtomic].
     *
     * The Play Billing `consumeAsync` / `acknowledgePurchaseAsync` calls deliberately run
     * OUTSIDE this transaction — they are RPCs to Google Play Services and can take seconds
     * on poor networks; holding a SQLite write lock across them would serialise the whole
     * database. Retry semantics for those RPCs live in [getGrantedButUnresolved].
     *
     * @param receipt The receipt to upsert; the stored row will have `granted = true,
     *                grantedAt = :grantedAt`.
     * @param grantedAt Epoch millis when the grant committed (usually `System.currentTimeMillis()`).
     * @param walletCredit Wallet side-effect — typically DAO calls on [PlayerProfileDao] like
     *                     `adjustGems(amount)` + `incrementGemsEarned(amount)`.
     *                     MUST be idempotent-on-retry is NOT required because Room's
     *                     @Transaction + the `granted` guard prevents retry reaching here.
     * @return `true` iff this call transitioned the receipt to granted and credited the
     *         wallet. `false` means the receipt was already granted — no writes performed.
     */
    @Transaction
    suspend fun grantOnceAtomic(
        receipt: BillingReceiptEntity,
        grantedAt: Long,
        walletCredit: suspend () -> Unit,
    ): Boolean {
        val existing = getByToken(receipt.purchaseToken)
        if (existing?.granted == true) return false
        upsert(receipt.copy(granted = true, grantedAt = grantedAt))
        walletCredit()
        return true
    }
}
