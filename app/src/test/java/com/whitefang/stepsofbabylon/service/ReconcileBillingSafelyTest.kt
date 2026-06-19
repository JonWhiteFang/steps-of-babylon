package com.whitefang.stepsofbabylon.service

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import com.whitefang.stepsofbabylon.fakes.FakeBillingManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * #250: unit tests for [reconcileBillingSafely] — the extracted background reconcile helper that
 * [StepSyncWorker.doWork] and `MainActivity.onResume` both call. The full worker has 11 injected
 * deps + needs a Robolectric Context, so the reconcile logic was extracted to a top-level suspend
 * fun to keep it cheaply JVM-testable with a fake [BillingManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconcileBillingSafelyTest {

    @Test
    fun `triggers reconcilePendingPurchases exactly once`() = runTest {
        val billing = FakeBillingManager()
        reconcileBillingSafely(billing)
        assertEquals(1, billing.reconcileCallCount, "the background sweep must call reconcile once")
    }

    @Test
    fun `a thrown reconcile does not propagate (best-effort, never crashes the worker)`() = runTest {
        val throwing = object : BillingManager {
            override suspend fun purchase(product: BillingProduct) = PurchaseResult.Success
            override suspend fun isAdRemoved() = false
            override suspend fun isSeasonPassActive() = false
            override suspend fun reconcilePendingPurchases() { throw RuntimeException("boom") }
        }
        // Must return normally (the catch-all swallows it) — no assertion needed beyond not throwing.
        reconcileBillingSafely(throwing)
    }

    @Test
    fun `a hanging reconcile is time-bounded, not left to hang`() = runTest {
        // BillingManagerImpl.connect() has no internal timeout; a stalled Play Services could hang
        // forever. The helper's withTimeoutOrNull must cancel it. runTest's virtual clock advances
        // past the 20s bound without real waiting; the call must complete.
        val hanging = object : BillingManager {
            override suspend fun purchase(product: BillingProduct) = PurchaseResult.Success
            override suspend fun isAdRemoved() = false
            override suspend fun isSeasonPassActive() = false
            override suspend fun reconcilePendingPurchases() { delay(Long.MAX_VALUE) }
        }
        reconcileBillingSafely(hanging) // returns (cancelled by the timeout) rather than hanging
    }
}
