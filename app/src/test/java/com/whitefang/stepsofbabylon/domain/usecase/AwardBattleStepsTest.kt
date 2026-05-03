package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeDailyStepDao
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
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
}
