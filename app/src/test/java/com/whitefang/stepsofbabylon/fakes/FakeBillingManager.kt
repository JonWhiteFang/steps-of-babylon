package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.domain.repository.BillingManager

/**
 * Test double for [BillingManager].
 *
 * Configuration knobs:
 * - [nextResult] — single fallback result returned when [resultQueue] is empty.
 *   Default [PurchaseResult.Success].
 * - [resultQueue] — per-call script. If non-empty, each [purchase] call dequeues
 *   the head. Empty queue falls back to [nextResult]. Useful for asserting
 *   retry/rollback paths where the first call fails and the second succeeds.
 * - [adRemoved], [seasonPassActive] — back the corresponding suspend getters.
 * - [purchases] — append-only log of every [purchase] invocation for
 *   verification in test assertions.
 */
class FakeBillingManager : BillingManager {
    var nextResult: PurchaseResult = PurchaseResult.Success
    val resultQueue: ArrayDeque<PurchaseResult> = ArrayDeque()
    var adRemoved: Boolean = false
    var seasonPassActive: Boolean = false
    val purchases = mutableListOf<BillingProduct>()

    /**
     * Number of times [reconcilePendingPurchases] has been invoked. Used by
     * `StoreViewModelTest` to assert the C.5 PR 2 reconciliation hook fires on
     * Store entry. The underlying call is still a no-op (inherited default from
     * the interface); the counter simply records attendance.
     */
    var reconcileCallCount: Int = 0
        private set

    override suspend fun purchase(product: BillingProduct): PurchaseResult {
        purchases += product
        return if (resultQueue.isNotEmpty()) resultQueue.removeFirst() else nextResult
    }
    override suspend fun isAdRemoved(): Boolean = adRemoved
    override suspend fun isSeasonPassActive(): Boolean = seasonPassActive

    override suspend fun reconcilePendingPurchases() {
        reconcileCallCount++
    }
}
