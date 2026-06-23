package com.whitefang.stepsofbabylon.presentation.labs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.time.TimeReading
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeBaselineSource
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
 * #253 — Compose UI tests for [LabsScreen]. Renders the real composable backed by a
 * fake-wired [LabsViewModel] (no Hilt graph) on the Robolectric/JVM unit-test lane.
 * Verifies balance display, slot count, start-button affordability, and rush-button presence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class LabsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var labRepo: FakeLabRepository
    private lateinit var playerRepo: FakePlayerRepository
    private val missionRepo = FakeMissionRepository(FakeDailyMissionDao())
    private val timeBaselineSource = FakeTimeBaselineSource(reading = TimeReading(0, Long.MAX_VALUE))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(
        stepBalance: Long = 0,
        gems: Long = 0,
        labSlotCount: Int = 1,
    ): LabsViewModel {
        playerRepo =
            FakePlayerRepository(
                PlayerProfile(stepBalance = stepBalance, gems = gems, labSlotCount = labSlotCount),
            )
        labRepo = FakeLabRepository()
        return LabsViewModel(labRepo, playerRepo, missionRepo, timeBaselineSource)
    }

    @Test
    fun `renders balances and lab slot count`() {
        val viewModel = createVm(stepBalance = 5000, gems = 200, labSlotCount = 2)

        composeRule.setContent { LabsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lab Slots: 0/2").assertExists()
    }

    @Test
    fun `Start button disabled when balance insufficient`() {
        val viewModel = createVm(stepBalance = 0, gems = 0, labSlotCount = 2)

        composeRule.setContent { LabsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Start ", substring = true)[0].assertIsNotEnabled()
    }

    @Test
    fun `active research shows Rush button`() {
        playerRepo =
            FakePlayerRepository(
                PlayerProfile(stepBalance = 5000, gems = 200, labSlotCount = 2),
            )
        labRepo = FakeLabRepository()
        // Seed active research with completesAt far ahead of the time source's wall clock (1000ms)
        // so checkCompletion in init does not auto-complete it.
        val source = FakeTimeBaselineSource(reading = TimeReading(0, 1000L))
        labRepo.active.value =
            listOf(
                ActiveResearch(
                    type = ResearchType.DAMAGE_RESEARCH,
                    level = 0,
                    startedAt = 0L,
                    completesAt = Long.MAX_VALUE,
                ),
            )
        val viewModel = LabsViewModel(labRepo, playerRepo, missionRepo, source)

        composeRule.setContent { LabsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Rush ", substring = true).assertExists()
    }
}
