package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult

interface BillingManager {
    suspend fun purchase(product: BillingProduct): PurchaseResult
    suspend fun isAdRemoved(): Boolean
    suspend fun isSeasonPassActive(): Boolean

    /**
     * Returns Play Billing's localized formatted price for [product] (e.g. `"$0.99"`,
     * `"£0.79"`, `"€0,99"`), or `null` if the underlying product-details query failed
     * (no network, Play Services unavailable, SKU not yet released, billing not connected).
     *
     * Callers MUST handle the `null` case gracefully — typically by falling back to the
     * static [com.whitefang.stepsofbabylon.domain.model.BillingProduct.priceDisplay] string,
     * which is a known-good price baked into the AAB at build time.
     *
     * Default no-op returns `null` so the test [com.whitefang.stepsofbabylon.fakes.FakeBillingManager]
     * inherits a do-nothing contract — tests that don't care about live prices keep working.
     * The real [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl] overrides this to
     * query Play Billing's `queryProductDetailsAsync` and read the `formattedPrice` /
     * `subscriptionOfferDetails[0].pricingPhases[0].formattedPrice` field.
     *
     * Introduced by Plan 31 PR B: removes the in-app/Play-Console price drift footgun where
     * static [BillingProduct.priceDisplay] constants could disagree with Play Console.
     */
    suspend fun getPriceDisplay(product: BillingProduct): String? = null

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
