package com.whitefang.stepsofbabylon.data.sensor

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository
import com.whitefang.stepsofbabylon.domain.time.TimeReading
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginDao
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginRepository
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeDailyStepDao
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeWeeklyChallengeDao
import com.whitefang.stepsofbabylon.fakes.FakeWeeklyChallengeRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
import com.whitefang.stepsofbabylon.service.SupplyDropNotificationManager
import com.whitefang.stepsofbabylon.service.WidgetUpdateHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * #232: the four follow-on-pipeline stages in [DailyStepManager.runFollowOnPipeline]
 * (widget update, supply-drop generation, economy rewards, walking-mission progress) were each
 * wrapped in a bare `catch (_: Exception) {}` that swallowed every failure with NO logging and NO
 * breadcrumb. With no analytics SDK in the app, a recurring failure (a player silently stops getting
 * supply drops / streak Gems / mission credit) was completely invisible — the single weakest
 * diagnostic spot in the data layer.
 *
 * The fix keeps the catch (a follow-on failure must never fail the step credit) but stops swallowing
 * silently: each block now reports via the [DailyStepManager.onPipelineError] seam (production wires
 * it to `Log.w`). This test drives a dependency in the supply-drop stage to throw and asserts (a) the
 * step credit still succeeds and (b) the failure is reported rather than vanishing.
 */
class DailyStepManagerErrorReportingTest {
    /** A WalkingEncounterRepository whose getUnclaimedCount() always throws (supply-drop stage). */
    private class ThrowingWalkingEncounterRepository : WalkingEncounterRepository {
        override fun observeUnclaimed(): Flow<List<com.whitefang.stepsofbabylon.domain.model.SupplyDrop>> =
            flowOf(emptyList())

        override fun observeHistory(limit: Int): Flow<List<com.whitefang.stepsofbabylon.domain.model.SupplyDrop>> =
            flowOf(emptyList())

        override fun countUnclaimed(): Flow<Int> = flowOf(0)

        override suspend fun getUnclaimedCount(): Int = throw IllegalStateException("boom")

        override suspend fun createDrop(
            trigger: SupplyDropTrigger,
            reward: SupplyDropReward,
            rewardAmount: Int,
        ): Long = 0L

        override suspend fun claimDrop(id: Int): Boolean = false

        override suspend fun enforceInboxCap(maxSize: Int) {}
    }

    // #211: stub the non-null currentTimeReading() so TimeIntegrity.evaluate (in the economy stage)
    // hits the Trusted/null branch instead of NPEing on a null reading. No baseTime here — literal.
    private fun stubbedAntiCheatPrefs(): AntiCheatPreferences =
        mock<AntiCheatPreferences>().also {
            whenever(it.currentTimeReading()).thenReturn(TimeReading(0, 1_710_000_000_000L))
        }

    private fun newManager(
        playerRepo: FakePlayerRepository,
        walkingRepo: WalkingEncounterRepository,
    ) = DailyStepManager(
        stepRepository = FakeStepRepository(),
        playerRepository = playerRepo,
        rateLimiter = StepRateLimiter(),
        velocityAnalyzer = StepVelocityAnalyzer(),
        antiCheatPrefs = stubbedAntiCheatPrefs(),
        walkingEncounterRepository = walkingRepo,
        supplyDropNotificationManager = mock<SupplyDropNotificationManager>(),
        dailyLoginRepository = FakeDailyLoginRepository(),
        weeklyChallengeRepository = FakeWeeklyChallengeRepository(),
        dailyMissionDao = FakeDailyMissionDao(),
        widgetUpdateHelper = mock<WidgetUpdateHelper>(),
        workshopRepository = FakeWorkshopRepository(),
        labRepository = FakeLabRepository(),
    )

    @Test
    fun `a swallowed follow-on-pipeline failure is reported, not silenced`() =
        runTest {
            val playerRepo = FakePlayerRepository()
            val manager = newManager(playerRepo, ThrowingWalkingEncounterRepository())

            val reported = mutableListOf<Pair<String, Throwable>>()
            manager.onPipelineError = { stage, e -> reported += stage to e }

            manager.recordSteps(100, 1_710_000_000_000L)

            // The step credit itself must still succeed despite the supply-drop stage throwing.
            assertEquals(100L, playerRepo.getStepBalance(), "step credit must survive a follow-on failure")

            // And the failure must be surfaced via onPipelineError instead of vanishing into catch{}.
            assertTrue(reported.isNotEmpty()) {
                "a follow-on-pipeline failure must be reported via onPipelineError, not swallowed silently"
            }
            assertTrue(reported.any { it.second is IllegalStateException }) {
                "the originating exception must be passed to onPipelineError; got $reported"
            }
        }
}
