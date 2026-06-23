package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.domain.repository.BillingManager

class PurchaseGemPack(
    private val billingManager: BillingManager,
) {
    suspend operator fun invoke(product: BillingProduct): PurchaseResult = billingManager.purchase(product)
}
