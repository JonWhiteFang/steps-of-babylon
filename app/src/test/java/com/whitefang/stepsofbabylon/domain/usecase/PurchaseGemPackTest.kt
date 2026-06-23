package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.fakes.FakeBillingManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PurchaseGemPackTest {
    private val billing = FakeBillingManager()
    private val useCase = PurchaseGemPack(billing)

    @Test
    fun `success delegates to billing and returns Success`() =
        runTest {
            val result = useCase(BillingProduct.GEM_PACK_MEDIUM)
            assertTrue(result is PurchaseResult.Success)
            assertEquals(listOf(BillingProduct.GEM_PACK_MEDIUM), billing.purchases)
        }

    @Test
    fun `error result is forwarded`() =
        runTest {
            billing.nextResult = PurchaseResult.Error("network")
            val result = useCase(BillingProduct.GEM_PACK_SMALL)
            assertTrue(result is PurchaseResult.Error)
            assertEquals("network", (result as PurchaseResult.Error).message)
        }
}
