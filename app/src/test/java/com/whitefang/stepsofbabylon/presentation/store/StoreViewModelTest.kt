package com.whitefang.stepsofbabylon.presentation.store

import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.fakes.FakeBillingManager
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var billingManager: FakeBillingManager
    private lateinit var cosmeticRepo: FakeCosmeticRepository

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 200, adRemoved = false))
        billingManager = FakeBillingManager()
        cosmeticRepo = FakeCosmeticRepository()
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm() = StoreViewModel(playerRepo, billingManager, cosmeticRepo)

    @Test
    fun `displays gem balance and ad state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(200, vm.uiState.value.gems)
        assertFalse(vm.uiState.value.adRemoved)
    }

    @Test
    fun `displays cosmetics`() = runTest(dispatcher) {
        cosmeticRepo.items.value = listOf(
            CosmeticItem("skin1", CosmeticCategory.ZIGGURAT_SKIN, "Gold Ziggurat", "Shiny", 100)
        )
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.cosmetics.size)
        assertEquals("Gold Ziggurat", vm.uiState.value.cosmetics.first().name)
    }

    @Test
    fun `purchase cosmetic deducts gems`() = runTest(dispatcher) {
        cosmeticRepo.items.value = listOf(
            CosmeticItem("skin1", CosmeticCategory.ZIGGURAT_SKIN, "Gold Ziggurat", "Shiny", 50)
        )
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.purchaseCosmetic("skin1")
        advanceUntilIdle()
        assertEquals(150, playerRepo.profile.value.gems)
        assertTrue(vm.uiState.value.cosmetics.first().isOwned)
    }

    // --- A.4: billing failure-mode coverage ---
    // The ViewModel must forward every PurchaseResult variant without crashing
    // and must always release the _purchasing spinner in a finally block so a
    // failed purchase does not wedge the UI.

    @Test
    fun `purchaseGemPack Success calls billing with correct product`() = runTest(dispatcher) {
        billingManager.nextResult = com.whitefang.stepsofbabylon.domain.model.PurchaseResult.Success
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseGemPack(com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL)
        advanceUntilIdle()

        assertEquals(1, billingManager.purchases.size)
        assertEquals(
            com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL,
            billingManager.purchases.first(),
        )
        assertFalse(vm.uiState.value.isPurchasing, "spinner cleared after success")
    }

    @Test
    fun `purchaseGemPack Error clears spinner without crashing`() = runTest(dispatcher) {
        billingManager.nextResult = com.whitefang.stepsofbabylon.domain.model.PurchaseResult.Error("billing unavailable")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseGemPack(com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL)
        advanceUntilIdle()

        assertEquals(1, billingManager.purchases.size)
        assertFalse(vm.uiState.value.isPurchasing, "spinner must release even on Error")
    }

    @Test
    fun `purchaseAdRemoval Error does not toggle adRemoved`() = runTest(dispatcher) {
        billingManager.nextResult = com.whitefang.stepsofbabylon.domain.model.PurchaseResult.Error("network")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseAdRemoval()
        advanceUntilIdle()

        assertFalse(playerRepo.profile.value.adRemoved, "adRemoved should not flip on failed purchase")
        assertEquals(
            com.whitefang.stepsofbabylon.domain.model.BillingProduct.AD_REMOVAL,
            billingManager.purchases.first(),
        )
    }

    @Test
    fun `sequential Error then Success invocations both reach billing`() = runTest(dispatcher) {
        // Drives the resultQueue feature — first call consumes Error, second
        // consumes Success. The in-flight guard must release between calls.
        billingManager.resultQueue.add(com.whitefang.stepsofbabylon.domain.model.PurchaseResult.Error("retry"))
        billingManager.resultQueue.add(com.whitefang.stepsofbabylon.domain.model.PurchaseResult.Success)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseGemPack(com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL)
        advanceUntilIdle()
        vm.purchaseGemPack(com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL)
        advanceUntilIdle()

        assertEquals(2, billingManager.purchases.size)
        assertFalse(vm.uiState.value.isPurchasing)
    }

    // --- C.5 PR 2: reconcile hook fires on Store entry ---

    @Test
    fun `init reconciles pending purchases exactly once`() = runTest(dispatcher) {
        // Store entry should trigger BillingManager.reconcilePendingPurchases so that
        // PENDING → PURCHASED promotions and unresolved consume/ack retries sweep
        // without requiring a purchase action. C.5 PR 2 / ADR-0005.
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, billingManager.reconcileCallCount)
    }

    // --- Plan 31 PR B: live formatted-price wiring from Play Billing ---

    @Test
    fun `priceDisplays starts empty so the UI falls back to static priceDisplay`() = runTest(dispatcher) {
        // FakeBillingManager has no priceDisplayOverrides set, so getPriceDisplay returns
        // null for every product. The map should never gain a key under those conditions,
        // matching the production behaviour when Play Billing is offline / unconfigured /
        // SKU not yet released.
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(
            vm.uiState.value.priceDisplays.isEmpty(),
            "missing live prices must leave the map empty so the UI falls back to BillingProduct.priceDisplay",
        )
    }

    @Test
    fun `priceDisplays populates from getPriceDisplay results on init`() = runTest(dispatcher) {
        val billing = FakeBillingManager().apply {
            priceDisplayOverrides[com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL] = "£0.79"
            priceDisplayOverrides[com.whitefang.stepsofbabylon.domain.model.BillingProduct.AD_REMOVAL] = "£3.49"
            priceDisplayOverrides[com.whitefang.stepsofbabylon.domain.model.BillingProduct.SEASON_PASS] = "£4.49/mo"
            // GEM_PACK_MEDIUM and GEM_PACK_LARGE deliberately omitted to assert that
            // the map only contains the keys whose live query succeeded.
        }
        val vm = StoreViewModel(playerRepo, billing, cosmeticRepo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val displays = vm.uiState.value.priceDisplays
        assertEquals("£0.79", displays[com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_SMALL])
        assertEquals("£3.49", displays[com.whitefang.stepsofbabylon.domain.model.BillingProduct.AD_REMOVAL])
        assertEquals("£4.49/mo", displays[com.whitefang.stepsofbabylon.domain.model.BillingProduct.SEASON_PASS])
        assertNull(
            displays[com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_MEDIUM],
            "missing live price must NOT have a map entry (UI uses ?: fallback)",
        )
        assertNull(displays[com.whitefang.stepsofbabylon.domain.model.BillingProduct.GEM_PACK_LARGE])
    }

    // --- #249: surface failed/pending Play-Billing purchase errors via userMessage ---

    @Test
    fun `purchase error surfaces a user message`() = runTest(dispatcher) {
        billingManager.nextResult = PurchaseResult.Error("Network error")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseGemPack(BillingProduct.GEM_PACK_SMALL)
        advanceUntilIdle()

        assertEquals("Network error", vm.uiState.value.userMessage)
    }

    @Test
    fun `pending purchase surfaces its message`() = runTest(dispatcher) {
        billingManager.nextResult =
            PurchaseResult.Error("Purchase pending — complete payment to receive your items")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseSeasonPass()
        advanceUntilIdle()

        assertEquals(
            "Purchase pending — complete payment to receive your items",
            vm.uiState.value.userMessage,
        )
    }

    @Test
    fun `successful purchase shows no message`() = runTest(dispatcher) {
        billingManager.nextResult = PurchaseResult.Success
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.purchaseAdRemoval()
        advanceUntilIdle()

        assertNull(vm.uiState.value.userMessage)
    }
}

// Additional tests for R09 fixes

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelSeasonPassTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() { Dispatchers.setMain(dispatcher) }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `expired season pass shows as inactive`() = runTest(dispatcher) {
        val playerRepo = FakePlayerRepository(PlayerProfile(
            seasonPassActive = true,
            seasonPassExpiry = System.currentTimeMillis() - 1000, // expired 1s ago
        ))
        val vm = StoreViewModel(playerRepo, FakeBillingManager(), FakeCosmeticRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.seasonPassActive)
    }

    @Test
    fun `active season pass with future expiry shows as active`() = runTest(dispatcher) {
        val playerRepo = FakePlayerRepository(PlayerProfile(
            seasonPassActive = true,
            seasonPassExpiry = System.currentTimeMillis() + 86_400_000, // expires tomorrow
        ))
        val vm = StoreViewModel(playerRepo, FakeBillingManager(), FakeCosmeticRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.seasonPassActive)
    }

    @Test
    fun `V1X02 daysRemaining is computed when season pass is active`() = runTest(dispatcher) {
        val playerRepo = FakePlayerRepository(PlayerProfile(
            seasonPassActive = true,
            seasonPassExpiry = System.currentTimeMillis() + 14L * 86_400_000, // 14 days from now
        ))
        val vm = StoreViewModel(playerRepo, FakeBillingManager(), FakeCosmeticRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val days = vm.uiState.value.seasonPassDaysRemaining
        assertNotNull(days)
        assertTrue(days!! in 13..14, "Expected ~14 days remaining, got $days")
    }

    @Test
    fun `V1X02 daysRemaining is null when season pass is inactive`() = runTest(dispatcher) {
        val playerRepo = FakePlayerRepository(PlayerProfile(
            seasonPassActive = false,
            seasonPassExpiry = 0,
        ))
        val vm = StoreViewModel(playerRepo, FakeBillingManager(), FakeCosmeticRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNull(vm.uiState.value.seasonPassDaysRemaining)
    }
}
