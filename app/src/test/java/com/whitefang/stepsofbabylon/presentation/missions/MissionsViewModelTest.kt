package com.whitefang.stepsofbabylon.presentation.missions

import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestone
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestoneResult
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMission
import com.whitefang.stepsofbabylon.domain.usecase.ClaimMissionResult
import com.whitefang.stepsofbabylon.domain.usecase.GenerateDailyMissions
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneRepository
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MissionsViewModelTest {

    // MissionsViewModel has a while(true) ticker — test use cases directly
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var missionDao: FakeDailyMissionDao
    private lateinit var milestoneDao: FakeMilestoneDao
    private lateinit var missionRepo: FakeMissionRepository
    private lateinit var milestoneRepo: FakeMilestoneRepository
    private lateinit var playerRepo: FakePlayerRepository
    private val today = LocalDate.now().toString()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        missionDao = FakeDailyMissionDao()
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 50, powerStones = 10, totalStepsEarned = 5000))
        milestoneDao = FakeMilestoneDao(linkedPlayer = playerRepo)
        // #227: VM + use cases take ports; wrap the SAME fake DAOs so direct seeding stays consistent.
        missionRepo = FakeMissionRepository(missionDao)
        milestoneRepo = FakeMilestoneRepository(dao = milestoneDao)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `generate daily missions creates 3 missions`() = runTest {
        val generate = GenerateDailyMissions(missionRepo)
        generate(today)
        val missions = missionDao.getByDateOnce(today)
        assertEquals(3, missions.size)
    }

    @Test
    fun `claim mission credits gems`() = runTest {
        // #122: claim now goes through the atomic ClaimMission use case (mark-first guarded claim),
        // exercising the same path MissionsViewModel.claimMission delegates to.
        missionDao.insert(DailyMissionEntity(date = today, missionType = DailyMissionType.WALK_5000.name, target = 5000, progress = 5000, completed = true, rewardGems = 5))
        val m = missionDao.getByDateOnce(today).first()
        val claimMission = ClaimMission(missionRepo, playerRepo)

        val result = claimMission(m.id, today)

        assertEquals(ClaimMissionResult.Success, result)
        assertEquals(55, playerRepo.profile.value.gems)
        assertTrue(missionDao.getByDateOnce(today).first().claimed)
    }

    @Test
    fun `claim milestone credits reward`() = runTest {
        // FIRST_STEPS has no Cosmetic reward, so the empty FakeCosmeticRepository is
        // sufficient (cosmetic-id pre-flight check in C.4 is vacuously true for zero
        // Cosmetic rewards).
        val claim = ClaimMilestone(milestoneRepo, playerRepo, FakeCosmeticRepository(), FakeTimeProvider())
        val result = claim(Milestone.FIRST_STEPS)
        assertEquals(ClaimMilestoneResult.Success, result)
        // FIRST_STEPS rewards 60 Gems
        assertEquals(110, playerRepo.profile.value.gems)
        val entity = milestoneDao.getByIdOnce(Milestone.FIRST_STEPS.name)
        assertNotNull(entity)
        assertTrue(entity!!.claimed)
    }

    @Test
    fun `milestone detection with steps`() = runTest {
        // Player has 5000 steps — FIRST_STEPS requires 1000
        val achievable = Milestone.entries.filter { it.requiredSteps <= 5000 }
        assertTrue(achievable.contains(Milestone.FIRST_STEPS))
    }

    // --- Bundle C Task 8: claim celebration event (Success-gated) ---------------------------------
    // The VM's init launches a viewModelScope while(true) ticker on Main (the test dispatcher); a
    // bare runTest{} would HANG on end-of-test cleanup spinning the rescheduling ticker. Every test
    // that constructs the VM therefore calls vm.cancelForTest() as its LAST statement. Label *content*
    // is covered by the pure missionRewardLabel test below (no VM); the VM tests assert emission COUNT.

    private fun createVm(timeProvider: FakeTimeProvider = FakeTimeProvider(fixedDate = LocalDate.parse(today))) =
        MissionsViewModel(
            missionRepository = missionRepo,
            milestoneRepository = milestoneRepo,
            stepRepository = FakeStepRepository(),
            playerRepository = playerRepo,
            cosmeticRepository = FakeCosmeticRepository(),
            timeProvider = timeProvider,
        )

    @Test
    fun `missionRewardLabel formats gems, power-stones, both, and fallback`() {
        assertEquals("+5 Gems claimed!", missionRewardLabel(infoWith(gems = 5, ps = 0)))
        assertEquals("+2 Power Stones claimed!", missionRewardLabel(infoWith(gems = 0, ps = 2)))
        assertEquals("+5 Gems +2 Power Stones claimed!", missionRewardLabel(infoWith(gems = 5, ps = 2)))
        assertEquals("Reward claimed!", missionRewardLabel(null))
    }

    private fun infoWith(gems: Int, ps: Int) = MissionDisplayInfo(
        id = 1, description = "d", target = 1, progress = 1, rewardGems = gems, rewardPowerStones = ps,
        completed = true, claimed = false,
    )

    @Test
    fun `claiming a completed mission emits one celebration`() = runTest {
        missionDao.insert(DailyMissionEntity(date = today, missionType = DailyMissionType.WALK_5000.name, target = 5000, progress = 5000, completed = true, rewardGems = 5))
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        val id = missionDao.getByDateOnce(today).first().id
        vm.claimMission(id)
        runCurrent()
        assertEquals(1, events.size)
        vm.cancelForTest()   // stop the while(true) ticker or runTest cleanup hangs
    }

    @Test
    fun `claiming an achieved milestone emits one celebration`() = runTest {
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        vm.claimMilestone(Milestone.FIRST_STEPS)   // player has >= FIRST_STEPS requirement (5000 >= 1000)
        runCurrent()
        assertEquals(1, events.size)
        vm.cancelForTest()
    }

    @Test
    fun `claiming an unachievable milestone emits no celebration`() = runTest {
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        vm.claimMilestone(Milestone.entries.maxBy { it.requiredSteps })   // 5_000_000 ≫ 5000 → InsufficientSteps
        runCurrent()
        assertTrue(events.isEmpty())
        vm.cancelForTest()
    }

    // #195: across midnight the uiState must switch to the NEW day's missions. Pre-fix the combined
    // StateFlow captured `today` once, so getByDate(today) never re-subscribed and the screen kept
    // showing yesterday's rows. refreshDate() flips _today → flatMapLatest re-subscribes getByDate.
    @Test
    fun `R195 uiState re-subscribes missions on day rollover`() = runTest {
        val day1 = LocalDate.of(2026, 6, 18)
        val day2 = day1.plusDays(1)
        val time = FakeTimeProvider(fixedDate = day1)
        // Seed a distinct, recognisable mission row for each day.
        missionDao.insert(DailyMissionEntity(date = day1.toString(), missionType = DailyMissionType.WALK_5000.name, target = 5000, progress = 10, completed = false, rewardGems = 1))
        missionDao.insert(DailyMissionEntity(date = day2.toString(), missionType = DailyMissionType.WALK_12000.name, target = 12000, progress = 20, completed = false, rewardGems = 2))

        val vm = createVm(timeProvider = time)
        val states = mutableListOf<MissionsUiState>()
        backgroundScope.launch { vm.uiState.toList(states) }
        runCurrent()

        // Day 1: the WALK_5000 row is present, the WALK_12000 (day-2) row is not.
        val day1Missions = vm.uiState.value.missions.map { it.target }
        assertTrue(day1Missions.contains(5000), "day 1 should show the WALK_5000 mission, got $day1Missions")
        assertFalse(day1Missions.contains(12000), "day 1 must NOT show day 2's mission, got $day1Missions")

        // Roll over to day 2 and refresh — the query must re-subscribe to day 2's rows.
        time.fixedDate = day2
        vm.refreshDate()
        runCurrent()

        val day2Missions = vm.uiState.value.missions.map { it.target }
        assertTrue(day2Missions.contains(12000), "day 2 should show the WALK_12000 mission, got $day2Missions")
        assertFalse(day2Missions.contains(5000), "day 2 must NOT still show day 1's mission, got $day2Missions")

        vm.cancelForTest()
    }
}
