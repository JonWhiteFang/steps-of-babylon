package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Regression coverage for R3-03 / GitHub #1.
 *
 * The bug: `LabsViewModel.init` unconditionally invoked the COMPLETE_RESEARCH mission
 * tick after `CheckResearchCompletion`, so opening Labs with zero in-flight research
 * still advanced the daily mission to progress=1, completed=true. The fix moves the
 * tick into [UpdateCompleteResearchMissionProgress] and gates the DAO write on
 * `completedCount >= 1`.
 *
 * Tests run against the real [FakeDailyMissionDao] so we can assert the row-state
 * directly — no VM construction (and therefore no `while(true) { delay(1000) }` ticker
 * to fight) is required.
 */
class UpdateCompleteResearchMissionProgressTest {

    private lateinit var repo: FakeMissionRepository
    private lateinit var useCase: UpdateCompleteResearchMissionProgress
    private val today = "2026-05-19"

    @BeforeEach
    fun setup() = runTest {
        repo = FakeMissionRepository()
        useCase = UpdateCompleteResearchMissionProgress(repo)
        // Seed today's COMPLETE_RESEARCH mission row exactly as `GenerateDailyMissions`
        // would. target=1 because that's the value of DailyMissionType.COMPLETE_RESEARCH.
        repo.dao.insert(
            DailyMissionEntity(
                date = today,
                missionType = DailyMissionType.COMPLETE_RESEARCH.name,
                target = DailyMissionType.COMPLETE_RESEARCH.target,
                rewardGems = DailyMissionType.COMPLETE_RESEARCH.rewardGems,
            )
        )
    }

    // ---- R3-03 negative cases — the new gating behaviour ----

    @Test
    fun `R303 does NOT tick when completedCount is 0`() = runTest {
        useCase(completedCount = 0, today = today)

        val missions = repo.dao.getByDateOnce(today)
        assertEquals(1, missions.size)
        assertEquals(0, missions[0].progress, "Mission progress must stay at 0 when nothing completed")
        assertFalse(missions[0].completed, "Mission must not be marked completed when nothing completed")
    }

    @Test
    fun `R303 does NOT tick when completedCount is negative`() = runTest {
        // Defensive guard: caller bug should not corrupt mission state.
        useCase(completedCount = -1, today = today)

        val missions = repo.dao.getByDateOnce(today)
        assertEquals(0, missions[0].progress)
        assertFalse(missions[0].completed)
    }

    // ---- R3-03 positive cases — the gating must NOT affect real completions ----

    @Test
    fun `R303 ticks to 1 when completedCount is 1`() = runTest {
        useCase(completedCount = 1, today = today)

        val missions = repo.dao.getByDateOnce(today)
        assertEquals(1, missions[0].progress)
        assertTrue(missions[0].completed)
    }

    @Test
    fun `R303 caps progress at target when multiple research complete in one batch`() = runTest {
        // Auto-completion path: app launch / Labs entry might find several expired
        // research projects at once. The use case must cap progress at the mission
        // target instead of overshooting.
        useCase(completedCount = 5, today = today)

        val missions = repo.dao.getByDateOnce(today)
        assertEquals(
            DailyMissionType.COMPLETE_RESEARCH.target,
            missions[0].progress,
            "Multiple completions must cap at mission target (=1 for COMPLETE_RESEARCH)",
        )
        assertTrue(missions[0].completed)
    }

    // ---- Idempotency / defensive ----

    @Test
    fun `R303 is a no-op when no mission row exists for the given date`() = runTest {
        useCase(completedCount = 1, today = "2099-12-31")

        // The seeded row for `today` must remain untouched.
        val missions = repo.dao.getByDateOnce(today)
        assertEquals(0, missions[0].progress)
        assertFalse(missions[0].completed)
    }
}
