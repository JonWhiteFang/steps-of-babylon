package com.whitefang.stepsofbabylon.presentation.supplies

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent
import com.whitefang.stepsofbabylon.presentation.ui.ClaimReward
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
class UnclaimedSuppliesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var encounterRepo: FakeWalkingEncounterRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var cardRepo: FakeCardRepository

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        encounterRepo = FakeWalkingEncounterRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 1000))
        cardRepo = FakeCardRepository()
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm() = UnclaimedSuppliesViewModel(encounterRepo, playerRepo, cardRepo)

    @Test
    fun `maps unclaimed drops to UI state`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 100)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.drops.size)
    }

    @Test
    fun `claimDrop removes from list`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val drop = vm.uiState.value.drops.first()
        vm.claimDrop(drop)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.drops.size)
    }

    @Test
    fun `claimAll clears all drops`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 100)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.drops.size)
        vm.claimAll()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.drops.size)
    }

    // Pure mapping test — no VM, no dispatcher. toClaimReward resolves NO resources, so it stays
    // a plain JVM test; the celebration *formatting* is covered by ClaimRewardFormatTest (#260).
    @Test
    fun `toClaimReward maps each reward type to a structured bundle`() {
        fun drop(r: SupplyDropReward, amt: Int) = SupplyDrop(id = 1, trigger = SupplyDropTrigger.RANDOM, reward = r, rewardAmount = amt, claimed = false, createdAt = 0L)
        assertEquals(ClaimReward.Bundle(steps = 150), drop(SupplyDropReward.STEPS, 150).toClaimReward())
        assertEquals(ClaimReward.Bundle(gems = 5), drop(SupplyDropReward.GEMS, 5).toClaimReward())
        assertEquals(ClaimReward.Bundle(powerStones = 2), drop(SupplyDropReward.POWER_STONES, 2).toClaimReward())
        assertEquals(ClaimReward.Bundle(cards = 1), drop(SupplyDropReward.CARD_COPY, 0).toClaimReward())
    }

    @Test
    fun `claimDrop emits one celebration on success`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.celebration.collect { events.add(it) } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val drop = vm.uiState.value.drops.first()
        vm.claimDrop(drop)
        advanceUntilIdle()
        assertEquals(1, events.size)
    }

    @Test
    fun `claimAll emits exactly one aggregate celebration for N drops`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 100)
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.celebration.collect { events.add(it) } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.claimAll()
        advanceUntilIdle()
        assertEquals(1, events.size)   // one aggregate, NOT N
    }

    @Test
    fun `claimAll on an empty batch emits no celebration`() = runTest(dispatcher) {
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.celebration.collect { events.add(it) } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.claimAll()   // no drops → no Success → no event
        advanceUntilIdle()
        assertTrue(events.isEmpty())
    }
}
