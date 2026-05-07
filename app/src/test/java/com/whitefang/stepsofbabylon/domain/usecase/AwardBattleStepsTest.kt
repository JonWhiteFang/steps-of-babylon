package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeDailyStepDao
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AwardBattleStepsTest {

    private val today = "2026-05-03"
    private val tomorrow = "2026-05-04"

    private fun sut(initialBalance: Long = 0L): Triple<AwardBattleSteps, FakePlayerRepository, FakeDailyStepDao> {
        val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = initialBalance))
        val dao = FakeDailyStepDao()
        return Triple(AwardBattleSteps(playerRepo, dao), playerRepo, dao)
    }

    @Test
    fun `first call credits the full amount`() = runTest {
        val (useCase, player, dao) = sut()

        val credited = useCase(5L, today)

        assertEquals(5L, credited)
        assertEquals(5L, player.observeProfile().first().stepBalance)
        assertEquals(5L, dao.getBattleStepsEarned(today))
    }

    @Test
    fun `credits 0 and does not touch wallet once cap is hit`() = runTest {
        val (useCase, player, dao) = sut()
        dao.incrementBattleSteps(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP)

        val credited = useCase(10L, today)

        assertEquals(0L, credited)
        assertEquals(0L, player.observeProfile().first().stepBalance)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, dao.getBattleStepsEarned(today))
    }

    @Test
    fun `partial credit when remaining is smaller than amount`() = runTest {
        val (useCase, player, dao) = sut()
        // Start at cap - 3 → only 3 should be credited out of a 10 request.
        dao.incrementBattleSteps(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 3L)

        val credited = useCase(10L, today)

        assertEquals(3L, credited)
        assertEquals(3L, player.observeProfile().first().stepBalance)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, dao.getBattleStepsEarned(today))
    }

    @Test
    fun `date rollover resets the per-day counter`() = runTest {
        val (useCase, player, dao) = sut()
        // Exhaust today's cap.
        dao.incrementBattleSteps(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP)

        val creditedToday = useCase(5L, today)
        val creditedTomorrow = useCase(5L, tomorrow)

        assertEquals(0L, creditedToday)
        assertEquals(5L, creditedTomorrow)
        assertEquals(5L, player.observeProfile().first().stepBalance)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, dao.getBattleStepsEarned(today))
        assertEquals(5L, dao.getBattleStepsEarned(tomorrow))
    }

    @Test
    fun `zero or negative amounts are no-ops`() = runTest {
        val (useCase, player, dao) = sut()

        assertEquals(0L, useCase(0L, today))
        assertEquals(0L, useCase(-5L, today))
        assertEquals(0L, player.observeProfile().first().stepBalance)
        assertEquals(0L, dao.getBattleStepsEarned(today))
    }

    @Test
    fun `dao is incremented by credited amount not requested amount`() = runTest {
        val (useCase, _, dao) = sut()
        // Only 2 headroom in the cap; caller asks for 50.
        dao.incrementBattleSteps(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 2L)

        val credited = useCase(50L, today)

        assertEquals(2L, credited)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, dao.getBattleStepsEarned(today))
    }

    @Test
    fun `FakeTimeProvider drives the cap reset when caller omits today`() = runTest {
        // Exercises the default `today` expression in invoke() — B.1 PR 2
        // rewired that expression from LocalDate.now().toString() to
        // timeProvider.today().toString(). A FakeTimeProvider fed to the
        // constructor now deterministically drives the date bucket without the
        // caller passing it explicitly.
        val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 0L))
        val dao = FakeDailyStepDao()
        val clock = FakeTimeProvider(fixedDate = LocalDate.parse(today))
        val useCase = AwardBattleSteps(playerRepo, dao, clock)

        // Exhaust today's cap using the default `today` parameter.
        dao.incrementBattleSteps(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP)
        val creditedBeforeRollover = useCase(10L)
        assertEquals(0L, creditedBeforeRollover, "cap exhausted on day 1")

        // Advance the fake clock across midnight. No caller code changes —
        // the same invoke() call now writes to a different date bucket.
        clock.fixedDate = LocalDate.parse(tomorrow)
        val creditedAfterRollover = useCase(10L)

        assertEquals(10L, creditedAfterRollover, "fresh cap on day 2")
        assertEquals(10L, dao.getBattleStepsEarned(tomorrow))
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, dao.getBattleStepsEarned(today),
            "day 1 record untouched")
    }
}
