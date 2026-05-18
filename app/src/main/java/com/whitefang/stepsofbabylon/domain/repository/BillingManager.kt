package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult

interface BillingManager {
    suspend fun purchase(product: BillingProduct): PurchaseResult
    suspend fun isAdRemoved(): Boolean
    suspend fun isSeasonPassActive(): Boolean

    /**
     * Sweeps pending / unresolved purchases from the underlying billing store and reconciles
     * them against local state. Intended to be called on Store entry and app resume so that
     * purchases completed while the app was killed mid-flow (or delivered asynchronously as
     * `PENDING → PURCHASED`) are granted exactly once.
     *
     * Default implementation is a no-op so test fakes inherit a do-nothing contract
     * (`StubBillingManager` was deleted in C.5 PR 3, so the only remaining inheritor is
     * the test [com.whitefang.stepsofbabylon.fakes.FakeBillingManager]). The real
     * [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl] overrides this to drive
     * the Play Billing `queryPurchasesAsync` + receipt-table reconciliation loop defined in
     * ADR-0005.
     */
    suspend fun reconcilePendingPurchases() { /* no-op by default */ }
}
