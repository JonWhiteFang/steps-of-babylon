package com.whitefang.stepsofbabylon.presentation.missions

import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
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
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MissionsViewModelTest {

    // MissionsViewModel has a while(true) ticker — test use cases directly
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var missionDao: FakeDailyMissionDao
    private lateinit var milestoneDao: FakeMilestoneDao
    private lateinit var playerRepo: FakePlayerRepository
    private val today = LocalDate.now().toString()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        missionDao = FakeDailyMissionDao()
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 50, powerStones = 10, totalStepsEarned = 5000))
        milestoneDao = FakeMilestoneDao(linkedPlayer = playerRepo)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `generate daily missions creates 3 missions`() = runTest {
        val generate = GenerateDailyMissions(missionDao)
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
        val claimMission = ClaimMission(missionDao, playerRepo)

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
        val claim = ClaimMilestone(milestoneDao, playerRepo, mock<PlayerProfileDao>(), FakeCosmeticRepository())
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
}
