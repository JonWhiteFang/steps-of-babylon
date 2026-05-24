package com.whitefang.stepsofbabylon.presentation.supplies

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
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
}
