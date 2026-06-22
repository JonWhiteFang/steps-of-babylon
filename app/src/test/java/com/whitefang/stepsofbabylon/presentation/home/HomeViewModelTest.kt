package com.whitefang.stepsofbabylon.presentation.home

import com.whitefang.stepsofbabylon.data.MilestoneNotificationPreferences
import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import com.whitefang.stepsofbabylon.domain.time.TimeReading
import com.whitefang.stepsofbabylon.fakes.*
import com.whitefang.stepsofbabylon.service.MilestoneNotificationManager
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var workshopRepo: FakeWorkshopRepository
    private lateinit var labRepo: FakeLabRepository
    private lateinit var encounterRepo: FakeWalkingEncounterRepository
    private val milestoneDao = FakeMilestoneDao()
    private val dailyMissionDao = FakeDailyMissionDao()
    // #227: the use cases now take ports; HomeVM keeps the raw DAOs for its direct presentation
    // reads (countClaimable / milestone getAll Flow). Wrap the SAME fake DAOs so seeding via the
    // DAO and reading via the use-case port stay consistent.
    private val missionRepo = FakeMissionRepository(dailyMissionDao)
    private val milestoneRepo = FakeMilestoneRepository(dao = milestoneDao)
    private val dailyLoginRepo = FakeDailyLoginRepository()
    private val milestoneNotificationManager = mock<MilestoneNotificationManager>()
    private val milestoneNotificationPrefs = mock<MilestoneNotificationPreferences>()

    @BeforeEach
    fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        playerRepo = FakePlayerRepository(PlayerProfile(
            stepBalance = 5000, gems = 100, powerStones = 20,
            currentTier = 2, highestUnlockedTier = 3,
            bestWavePerTier = mapOf(1 to 15, 2 to 10),
        ))
        stepRepo = FakeStepRepository()
        workshopRepo = FakeWorkshopRepository()
        labRepo = FakeLabRepository()
        encounterRepo = FakeWalkingEncounterRepository()
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    // #211: HomeViewModel derives trusted-now from TimeBaselineSource and gates research completion on
    // it. The default fake (null baseline) yields a Trusted verdict with trustedWallClock == reading's
    // wall. We default the reading's wall to Long.MAX_VALUE so research with a past `completesAt` still
    // completes (trustedNow >= completesAt) — the existing #55 tests seed real-clock-relative completions.
    private fun createVm(
        timeBaselineSource: FakeTimeBaselineSource =
            FakeTimeBaselineSource(reading = TimeReading(0, Long.MAX_VALUE)),
    ) = HomeViewModel(
        playerRepo, stepRepo, workshopRepo, labRepo, encounterRepo,
        missionRepo, milestoneRepo, dailyLoginRepo,
        milestoneNotificationManager, milestoneNotificationPrefs,
        timeBaselineSource,
    )

    @Test
    fun `maps profile to UI state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.currentTier)
        assertEquals(3, state.highestUnlockedTier)
        assertEquals(Biome.HANGING_GARDENS, state.currentBiome)
        assertTrue(state.stepBalance > 0)
    }

    @Test
    fun `todaySteps from step repository`() = runTest(dispatcher) {
        val today = LocalDate.now().toString()
        stepRepo.records.value = mapOf(today to DailyStepSummary(today, creditedSteps = 7500))
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(7500, vm.uiState.value.todaySteps)
    }

    @Test
    fun `bestWave for current tier`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(10, vm.uiState.value.bestWave)
    }

    @Test
    fun `unclaimed drop count`() = runTest(dispatcher) {
        encounterRepo.createDrop(
            com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger.RANDOM,
            com.whitefang.stepsofbabylon.domain.model.SupplyDropReward.GEMS, 5
        )
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.unclaimedDropCount)
    }

    @Test
    fun `selectTier updates profile`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectTier(3)
        advanceUntilIdle()
        assertEquals(3, playerRepo.profile.value.currentTier)
    }

    // ---------------------------------------------------------------------------------------
    // #55 — background research completion via HomeViewModel.init credits the daily mission.
    //
    // Pre-fix `HomeViewModel.init` discarded the `List<ResearchType>` returned by
    // `CheckResearchCompletion`, so a research project that completed in the background (timer
    // elapsed while the app was closed) had its level incremented but never advanced the
    // COMPLETE_RESEARCH daily mission. By the time the player navigated to Labs the research
    // was already marked complete, so `LabsViewModel`'s mission tick early-returned at the
    // R3-03 count gate (`completedCount=0 → no-op`).
    //
    // The fix mirrors the `LabsViewModel` R3-03 pattern: capture `completed.size` and pass to
    // `UpdateCompleteResearchMissionProgress`. The 2 tests below cover both directions: the
    // happy path (mission ticks when something actually completed) and the R3-03 regression
    // guard (no in-flight research → mission stays at 0).
    // ---------------------------------------------------------------------------------------

    @Test
    fun `R55 background research completion credits the COMPLETE_RESEARCH daily mission`() = runTest(dispatcher) {
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()

        // Arrange: an expired research project (timer elapsed while the app was closed). We
        // pick `DAMAGE_RESEARCH` arbitrarily — any wired ResearchType works since the use
        // case keys on the daily mission's missionType, not the research type.
        labRepo.startResearch(
            type = ResearchType.DAMAGE_RESEARCH,
            completesAt = now - 60_000L,    // 60s ago
            startedAt = now - 3_600_000L,    // 1h ago
        )
        // Arrange: a fresh COMPLETE_RESEARCH mission row for today.
        dailyMissionDao.insert(
            DailyMissionEntity(
                date = today,
                missionType = DailyMissionType.COMPLETE_RESEARCH.name,
                target = 1,
                progress = 0,
                completed = false,
                claimed = false,
                rewardGems = 5,
                rewardPowerStones = 1,
            )
        )

        // Act: HomeViewModel.init runs CheckResearchCompletion + updateMissionProgress.
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Assert: the mission row was credited.
        val mission = dailyMissionDao.getByDateOnce(today)
            .first { it.missionType == DailyMissionType.COMPLETE_RESEARCH.name }
        assertEquals(1, mission.progress, "Expected mission progress to advance to 1 after background research completion")
        assertTrue(mission.completed, "Expected mission to be marked completed (target=1)")
        assertFalse(mission.claimed, "Mission should not be auto-claimed — player still has to tap Claim")

        // Assert: the research itself was actually completed (level incremented). Verifies the
        // `CheckResearchCompletion` half of the chain ran end-to-end, not just the mission tick.
        assertEquals(
            1, labRepo.getResearchLevel(ResearchType.DAMAGE_RESEARCH),
            "Expected DAMAGE_RESEARCH level to increment after CheckResearchCompletion ran",
        )
    }

    @Test
    fun `R55 no in-flight research means COMPLETE_RESEARCH mission stays at progress 0 (R3-03 regression guard)`() = runTest(dispatcher) {
        val today = LocalDate.now().toString()

        // Arrange: a fresh COMPLETE_RESEARCH mission but NO active research at all.
        // This is the R3-03 false-trigger scenario — if the fix is wrong, the mission would
        // tick to 1 even though nothing actually completed.
        dailyMissionDao.insert(
            DailyMissionEntity(
                date = today,
                missionType = DailyMissionType.COMPLETE_RESEARCH.name,
                target = 1,
                progress = 0,
                completed = false,
                claimed = false,
                rewardGems = 5,
                rewardPowerStones = 1,
            )
        )

        // Act
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Assert: the mission row stays at progress=0, completed=false. The `completedCount
        // <= 0` early-return in UpdateCompleteResearchMissionProgress is the gating guard;
        // this test would fail if the HomeViewModel call site ever regresses to passing a
        // hardcoded count instead of the actual `completed.size`.
        val mission = dailyMissionDao.getByDateOnce(today)
            .first { it.missionType == DailyMissionType.COMPLETE_RESEARCH.name }
        assertEquals(0, mission.progress, "Mission must stay at 0 when no research actually completed (R3-03 false-trigger regression guard)")
        assertFalse(mission.completed, "Mission must stay incomplete")
    }

    // ---------------------------------------------------------------------------------------
    // #211 — HomeViewModel gates background research completion on the TRUSTED now derived from
    // TimeBaselineSource, not the raw wall clock. The other #55 tests use the default createVm()
    // whose fake reading wall is Long.MAX_VALUE → trusted-now is effectively unbounded, so any
    // research with a past `completesAt` always completes. That masks the trusted-now gate this
    // read-only consumer exists to enforce. This test injects a ROLLBACK baseline so trusted-now
    // is pinned BELOW the research `completesAt` and verifies the research is NOT completed.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `R211 rollback verdict gates trusted-now so background research does not complete`() = runTest(dispatcher) {
        // Active research completes at 100_000 (absolute). A rollback baseline pins trusted-now at
        // 50_000 (< completesAt) → CheckResearchCompletion(now = trustedNow) must NOT complete it.
        labRepo.active.value = listOf(ActiveResearch(ResearchType.DAMAGE_RESEARCH, 0, 0, 100_000))
        val rollbackSource = FakeTimeBaselineSource(
            // maxWallClockSeen (1_000_000) > reading.wallClock (50_000) → Rollback;
            // wallDelta=0, elapsedDelta=0 → cappedDelta=0 → trustedWallClock stays 50_000.
            baseline = TimeBaseline(
                lastElapsedRealtime = 0,
                lastWallClock = 50_000,
                maxWallClockSeen = 1_000_000,
                trustedWallClock = 50_000,
            ),
            reading = TimeReading(elapsedRealtime = 0, wallClock = 50_000),
        )
        val vm = createVm(timeBaselineSource = rollbackSource)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // research still active — trusted-now (50_000) < completesAt (100_000). With the DEFAULT
        // createVm() (trusted-now = Long.MAX_VALUE) the SAME research WOULD complete (size 0).
        assertEquals(
            1, labRepo.active.value.size,
            "Research must stay active — trusted-now (50_000) is below completesAt (100_000)",
        )
        assertEquals(
            0, labRepo.getResearchLevel(ResearchType.DAMAGE_RESEARCH),
            "Research level must NOT increment — completion was gated by the rollback trusted-now",
        )
    }
}
