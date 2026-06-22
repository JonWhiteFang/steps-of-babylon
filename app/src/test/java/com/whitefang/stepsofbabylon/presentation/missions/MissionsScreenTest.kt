package com.whitefang.stepsofbabylon.presentation.missions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneRepository
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
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
 * #253 — Compose UI tests for [MissionsScreen]. Renders the real composable backed by a
 * fake-wired [MissionsViewModel] (no Hilt graph) on the Robolectric/JVM unit-test lane.
 * Verifies daily mission cards, claim affordance, and milestones section.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var missionDao: FakeDailyMissionDao
    private lateinit var missionRepo: FakeMissionRepository
    private lateinit var milestoneRepo: FakeMilestoneRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var cosmeticRepo: FakeCosmeticRepository
    private lateinit var timeProvider: FakeTimeProvider

    private val today = "2026-05-07" // FakeTimeProvider default

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        missionDao = FakeDailyMissionDao()
        missionRepo = FakeMissionRepository(missionDao)
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 1000))
        milestoneRepo = FakeMilestoneRepository(dao = FakeMilestoneDao(linkedPlayer = playerRepo))
        stepRepo = FakeStepRepository()
        cosmeticRepo = FakeCosmeticRepository()
        timeProvider = FakeTimeProvider()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): MissionsViewModel =
        MissionsViewModel(missionRepo, milestoneRepo, stepRepo, playerRepo, cosmeticRepo, timeProvider)

    @Test
    fun `renders Daily Missions header and mission cards`() = runTest {
        // Seed a mission before VM creation so generateForDate no-ops (data exists for date).
        missionDao.insert(
            DailyMissionEntity(
                date = today,
                missionType = "KILL_500_ENEMIES",
                target = 500,
                progress = 200,
                rewardGems = 5,
            )
        )

        val viewModel = createVm()
        composeRule.setContent { MissionsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Daily Missions").assertExists()
        composeRule.onNodeWithText("200 / 500").assertExists()

        viewModel.cancelForTest()
    }

    @Test
    fun `Claim button appears when mission is complete but unclaimed`() = runTest {
        missionDao.insert(
            DailyMissionEntity(
                date = today,
                missionType = "WALK_5000",
                target = 5000,
                progress = 5000,
                rewardGems = 5,
                completed = true,
                claimed = false,
            )
        )

        val viewModel = createVm()
        composeRule.setContent { MissionsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Claim").assertExists()

        viewModel.cancelForTest()
    }

    @Test
    fun `milestones section renders`() = runTest {
        // Milestones are static (Milestone.entries) — no seeding needed; just confirm the header.
        missionDao.insert(
            DailyMissionEntity(
                date = today,
                missionType = "WALK_5000",
                target = 5000,
                progress = 0,
                rewardGems = 5,
            )
        )

        val viewModel = createVm()
        composeRule.setContent { MissionsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Walking Milestones").assertExists()

        viewModel.cancelForTest()
    }
}
