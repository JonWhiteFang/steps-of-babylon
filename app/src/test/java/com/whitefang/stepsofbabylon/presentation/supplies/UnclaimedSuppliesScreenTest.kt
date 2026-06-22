package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #253 — Compose UI tests for [UnclaimedSuppliesScreen]. Renders the real composable backed by a
 * fake-wired [UnclaimedSuppliesViewModel] (no Hilt graph) on the Robolectric/JVM unit-test lane.
 * Verifies supply drop list rendering, empty state, and Claim All affordance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class UnclaimedSuppliesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var encounterRepo: FakeWalkingEncounterRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var cardRepo: FakeCardRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        encounterRepo = FakeWalkingEncounterRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 1000))
        cardRepo = FakeCardRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): UnclaimedSuppliesViewModel =
        UnclaimedSuppliesViewModel(encounterRepo, playerRepo, cardRepo)

    @Test
    fun `renders supply drop list with Claim buttons`() = runTest {
        encounterRepo.createDrop(SupplyDropTrigger.STEP_THRESHOLD, SupplyDropReward.STEPS, 100)
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.GEMS, 5)

        val viewModel = createVm()
        composeRule.setContent { UnclaimedSuppliesScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Claim").assertCountEquals(2)
        composeRule.onNodeWithText(SupplyDropTrigger.STEP_THRESHOLD.message).assertExists()
    }

    @Test
    fun `empty state shows no drops message`() = runTest {
        val viewModel = createVm()
        composeRule.setContent { UnclaimedSuppliesScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No supply drops yet — keep walking!").assertExists()
    }

    @Test
    fun `Claim All button present when drops exist`() = runTest {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.POWER_STONES, 3)

        val viewModel = createVm()
        composeRule.setContent { UnclaimedSuppliesScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Claim All").assertExists()
    }
}
