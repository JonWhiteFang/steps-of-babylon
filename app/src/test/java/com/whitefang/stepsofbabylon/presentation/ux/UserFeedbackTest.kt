package com.whitefang.stepsofbabylon.presentation.ux

import androidx.lifecycle.SavedStateHandle
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import com.whitefang.stepsofbabylon.presentation.ui.UiMessage
import com.whitefang.stepsofbabylon.presentation.workshop.WorkshopViewModel
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
class UserFeedbackTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var workshopRepo: FakeWorkshopRepository
    private lateinit var playerRepo: FakePlayerRepository
    private val missionRepo =
        com.whitefang.stepsofbabylon.fakes
            .FakeMissionRepository()

    @BeforeEach
    fun setup() =
        runTest(dispatcher) {
            Dispatchers.setMain(dispatcher)
            workshopRepo = FakeWorkshopRepository()
            playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 0))
            workshopRepo.upgrades.value = UpgradeType.entries.associateWith { 0 }
        }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `workshop purchase with zero balance shows feedback message`() =
        runTest(dispatcher) {
            val vm = WorkshopViewModel(workshopRepo, playerRepo, missionRepo, SavedStateHandle())
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.purchase(UpgradeType.DAMAGE)
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.userMessage)
            assertEquals(
                UiMessage.NotEnoughSteps,
                vm.uiState.value.userMessage,
            )
        }

    @Test
    fun `clearMessage resets userMessage to null`() =
        runTest(dispatcher) {
            val vm = WorkshopViewModel(workshopRepo, playerRepo, missionRepo, SavedStateHandle())
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.purchase(UpgradeType.DAMAGE)
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.userMessage)

            vm.clearMessage()
            advanceUntilIdle()
            assertNull(vm.uiState.value.userMessage)
        }

    @Test
    fun `quickInvest with zero balance shows feedback message`() =
        runTest(dispatcher) {
            val vm = WorkshopViewModel(workshopRepo, playerRepo, missionRepo, SavedStateHandle())
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.quickInvest()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.userMessage)
        }
}
