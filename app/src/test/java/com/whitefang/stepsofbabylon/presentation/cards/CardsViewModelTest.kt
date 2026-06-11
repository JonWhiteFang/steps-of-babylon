package com.whitefang.stepsofbabylon.presentation.cards

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeRewardAdManager
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
class CardsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var cardRepo: FakeCardRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var adManager: FakeRewardAdManager

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        cardRepo = FakeCardRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 500, cardDust = 100))
        adManager = FakeRewardAdManager()
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm() = CardsViewModel(cardRepo, playerRepo, adManager)

    @Test
    fun `displays owned cards`() = runTest(dispatcher) {
        cardRepo.cards.value = listOf(
            OwnedCard(1, CardType.SHARP_SHOOTER, 1, false),
            OwnedCard(2, CardType.IRON_SKIN, 2, true),
        )
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(2, state.ownedCards.size)
        assertEquals(1, state.equippedCount)
    }

    @Test
    fun `gem balance shown`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(500, vm.uiState.value.gems)
    }

    @Test
    fun `equip toggles card state`() = runTest(dispatcher) {
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.SHARP_SHOOTER, 1, false))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.equipCard(1)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.ownedCards.first().isEquipped)
    }

    @Test
    fun `unequip toggles card state`() = runTest(dispatcher) {
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.SHARP_SHOOTER, 1, true))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.unequipCard(1)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.ownedCards.first().isEquipped)
    }

    // #154: a max-level card must report isMaxLevel == true and canAffordUpgrade == false even with
    // a pile of copies — the screen hides the Upgrade button when isMaxLevel, and gates `enabled` on
    // canAffordUpgrade otherwise. Both flags must hold at cap so the buy affordance is gone.
    @Test
    fun `R154 max-level card is maxed and not upgradeable even with surplus copies`() = runTest(dispatcher) {
        val type = CardType.SHARP_SHOOTER
        cardRepo.cards.value = listOf(
            OwnedCard(1, type, type.maxLevel, false, copyCount = 999),
        )
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val card = vm.uiState.value.ownedCards.first()
        assertTrue(card.isMaxLevel, "card at maxLevel should report isMaxLevel")
        assertFalse(card.canAffordUpgrade, "a maxed card must NOT be upgradeable even with surplus copies")
    }

    // #154: upgrading a maxed card must be a true no-op (level + copies unchanged).
    @Test
    fun `R154 upgrading a maxed card does not change level or copies`() = runTest(dispatcher) {
        val type = CardType.SHARP_SHOOTER
        cardRepo.cards.value = listOf(
            OwnedCard(1, type, type.maxLevel, false, copyCount = 999),
        )
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.upgradeCard(1)
        advanceUntilIdle()
        val card = vm.uiState.value.ownedCards.first()
        assertEquals(type.maxLevel, card.level, "maxed card level must be unchanged")
        assertEquals(999, card.copyCount, "no copies consumed at cap")
    }

    @Test
    fun `upgrade decrements copies and increments level`() = runTest(dispatcher) {
        cardRepo.cards.value = listOf(OwnedCard(1, CardType.SHARP_SHOOTER, 1, false, copyCount = 5))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.upgradeCard(1)
        advanceUntilIdle()
        val card = vm.uiState.value.ownedCards.first()
        assertEquals(2, card.level)
        assertEquals(2, card.copyCount) // 5 - 3 (COMMON copiesPerLevel)
    }

    // --- A.4: ad failure-mode coverage for watchFreePackAd ---
    // Each AdResult variant should route correctly: only Rewarded opens a pack
    // and persists the day-stamp; Cancelled and Error must leave state
    // unchanged so the user can retry tomorrow.

    @Test
    fun `watchFreePackAd Rewarded opens a free pack and records the day`() = runTest(dispatcher) {
        adManager.nextResult = com.whitefang.stepsofbabylon.domain.model.AdResult.Rewarded
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.watchFreePackAd()
        advanceUntilIdle()

        assertEquals(
            com.whitefang.stepsofbabylon.domain.model.AdPlacement.DAILY_FREE_CARD_PACK,
            adManager.shown.first(),
        )
        assertEquals(
            java.time.LocalDate.now().toString(),
            playerRepo.profile.value.freeCardPackAdUsedToday,
            "day-stamp should be recorded for Rewarded path",
        )
    }

    @Test
    fun `watchFreePackAd Cancelled does not record the day or open a pack`() = runTest(dispatcher) {
        adManager.nextResult = com.whitefang.stepsofbabylon.domain.model.AdResult.Cancelled
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.watchFreePackAd()
        advanceUntilIdle()

        assertEquals(1, adManager.shown.size) // ad was shown
        assertEquals(
            "",
            playerRepo.profile.value.freeCardPackAdUsedToday,
            "day-stamp must remain empty on Cancelled",
        )
        assertNull(vm.uiState.value.lastPackResult, "no pack result on Cancelled")
        assertEquals(
            "Ad cancelled. Try again.",
            vm.uiState.value.userMessage,
            "user-visible snackbar message on Cancelled (PR A: ad-error UX)",
        )
    }

    @Test
    fun `watchFreePackAd Error does not record the day or open a pack`() = runTest(dispatcher) {
        adManager.nextResult = com.whitefang.stepsofbabylon.domain.model.AdResult.Error("load failed")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.watchFreePackAd()
        advanceUntilIdle()

        assertEquals(
            "",
            playerRepo.profile.value.freeCardPackAdUsedToday,
            "day-stamp must remain empty on Error",
        )
        assertNull(vm.uiState.value.lastPackResult, "no pack result on Error")
        assertEquals(
            "load failed",
            vm.uiState.value.userMessage,
            "AdResult.Error.message surfaces verbatim when non-blank (PR A: ad-error UX)",
        )
    }

    @Test
    fun `watchFreePackAd Error with blank message falls back to a generic snackbar`() = runTest(dispatcher) {
        adManager.nextResult = com.whitefang.stepsofbabylon.domain.model.AdResult.Error("")
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.watchFreePackAd()
        advanceUntilIdle()

        assertEquals(
            "Ad failed to load. Try again later.",
            vm.uiState.value.userMessage,
            "blank Error.message must not surface as an empty snackbar (PR A: ad-error UX)",
        )
    }
}
