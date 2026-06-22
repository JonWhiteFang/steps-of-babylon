package com.whitefang.stepsofbabylon.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.data.MilestoneNotificationPreferences
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.time.TimeReading
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginRepository
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneRepository
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeBaselineSource
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import com.whitefang.stepsofbabylon.service.MilestoneNotificationManager
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
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var encounterRepo: FakeWalkingEncounterRepository
    private val missionRepo = FakeMissionRepository(FakeDailyMissionDao())
    private val milestoneRepo = FakeMilestoneRepository(dao = FakeMilestoneDao())
    private val dailyLoginRepo = FakeDailyLoginRepository()
    private val milestoneNotificationManager = mock<MilestoneNotificationManager>()
    private val milestoneNotificationPrefs = mock<MilestoneNotificationPreferences>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        stepRepo = FakeStepRepository()
        encounterRepo = FakeWalkingEncounterRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(
        profile: PlayerProfile =
            PlayerProfile(
                stepBalance = 5000,
                gems = 100,
                powerStones = 20,
                currentTier = 2,
                highestUnlockedTier = 3,
                bestWavePerTier = mapOf(1 to 15, 2 to 10),
            ),
    ): HomeViewModel {
        playerRepo = FakePlayerRepository(profile)
        return HomeViewModel(
            playerRepo,
            stepRepo,
            FakeWorkshopRepository(),
            FakeLabRepository(),
            encounterRepo,
            missionRepo,
            milestoneRepo,
            dailyLoginRepo,
            milestoneNotificationManager,
            milestoneNotificationPrefs,
            FakeTimeBaselineSource(reading = TimeReading(0, Long.MAX_VALUE)),
        )
    }

    @Test
    fun `renders today steps and best wave in loaded state`() {
        val viewModel = createVm()

        composeRule.setContent { HomeScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("0 steps").assertExists()
        composeRule.onNodeWithText("Best Wave: 10").assertExists()
    }

    @Test
    fun `renders BATTLE button in loaded state`() {
        val viewModel = createVm()

        composeRule.setContent { HomeScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("BATTLE").assertExists()
    }

    @Test
    fun `shows first-walk prompt when no steps earned and low balance`() {
        val viewModel = createVm(PlayerProfile(stepBalance = 0, gems = 0, powerStones = 0))

        composeRule.setContent { HomeScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Earn your first Steps").assertExists()
    }

    @Test
    fun `does not show first-walk prompt when step balance is above threshold`() {
        val viewModel = createVm(PlayerProfile(stepBalance = 500, gems = 50, powerStones = 10))

        composeRule.setContent { HomeScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Earn your first Steps").assertDoesNotExist()
    }
}
