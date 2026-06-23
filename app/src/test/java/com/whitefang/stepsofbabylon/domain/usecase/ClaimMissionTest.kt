package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * #122 (audit #10): the daily-mission claim must credit its reward exactly once even under a rapid
 * double-tap. Extracted from `MissionsViewModel.claimMission` into a testable [ClaimMission] use
 * case that marks-first via the guarded [com.whitefang.stepsofbabylon.data.local.DailyMissionDao.markClaimed]
 * (`AND claimed = 0` returning rows-affected) and credits only on rows == 1.
 */
class ClaimMissionTest {
    private lateinit var missionRepo: FakeMissionRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var useCase: ClaimMission
    private val today = "2026-06-10"

    @BeforeEach
    fun setup() {
        missionRepo = FakeMissionRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 0, powerStones = 0))
        useCase = ClaimMission(missionRepo, playerRepo)
    }

    private suspend fun seedCompletedMission(
        gems: Int = 5,
        powerStones: Int = 0,
    ): Int {
        missionRepo.dao.insert(
            DailyMissionEntity(
                date = today,
                missionType = DailyMissionType.WALK_5000.name,
                target = 5000,
                progress = 5000,
                completed = true,
                rewardGems = gems,
                rewardPowerStones = powerStones,
            ),
        )
        return missionRepo.dao
            .getByDateOnce(today)
            .first()
            .id
    }

    @Test
    fun `claim credits the reward and marks claimed`() =
        runTest {
            val id = seedCompletedMission(gems = 5)
            val result = useCase(id, today)
            assertEquals(ClaimMissionResult.Success, result)
            assertEquals(5L, playerRepo.profile.value.gems)
            assertTrue(
                missionRepo.dao
                    .getByDateOnce(today)
                    .first()
                    .claimed,
            )
        }

    @Test
    fun `R122 double-tap credits the reward exactly once`() =
        runTest {
            val id = seedCompletedMission(gems = 5, powerStones = 2)

            val first = useCase(id, today)
            val second = useCase(id, today)

            assertEquals(ClaimMissionResult.Success, first)
            assertEquals(ClaimMissionResult.NotClaimable, second, "the second tap must lose the guarded claim race")
            assertEquals(5L, playerRepo.profile.value.gems, "gems credited exactly once")
            assertEquals(2L, playerRepo.profile.value.powerStones, "power stones credited exactly once")
        }

    @Test
    fun `incomplete mission is not claimable`() =
        runTest {
            missionRepo.dao.insert(
                DailyMissionEntity(
                    date = today,
                    missionType = DailyMissionType.WALK_5000.name,
                    target = 5000,
                    progress = 100,
                    completed = false,
                    rewardGems = 5,
                ),
            )
            val id =
                missionRepo.dao
                    .getByDateOnce(today)
                    .first()
                    .id
            assertEquals(ClaimMissionResult.NotClaimable, useCase(id, today))
            assertEquals(0L, playerRepo.profile.value.gems)
        }
}
