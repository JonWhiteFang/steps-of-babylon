package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
 * #253 — Compose UI tests for [WorkshopScreen]. Renders the real composable backed by a
 * fake-wired [WorkshopViewModel] (no Hilt graph) on the Robolectric/JVM unit-test lane.
 * Verifies balance display, upgrade affordability gating, and the purchase-increment flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class WorkshopScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var workshopRepo: FakeWorkshopRepository
    private val missionRepo = FakeMissionRepository(FakeDailyMissionDao())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(balance: Long): WorkshopViewModel {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = balance))
        workshopRepo = FakeWorkshopRepository(linkedPlayer = playerRepo)
        workshopRepo.upgrades.value =
            UpgradeType.entries.filter { it.isWorkshopVisible }.associateWith { 0 }
        return WorkshopViewModel(workshopRepo, playerRepo, missionRepo, SavedStateHandle())
    }

    @Test
    fun `renders step balance and upgrade list`() {
        val viewModel = createVm(balance = 9999)

        composeRule.setContent { WorkshopScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Balance: 9999 Steps").assertExists()
        composeRule.onNodeWithText("Damage").assertExists()
    }

    @Test
    fun `upgrade card is disabled when balance is zero`() {
        val viewModel = createVm(balance = 0)

        composeRule.setContent { WorkshopScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Damage").assertIsNotEnabled()
    }

    @Test
    fun `successful purchase increments level`() {
        val viewModel = createVm(balance = 100_000)

        composeRule.setContent { WorkshopScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Lv. 0")[0].assertExists()
        composeRule.onNodeWithText("Damage").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lv. 1").assertExists()
    }
}
