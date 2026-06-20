package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AwardBattleStepsTest {

    private val today = "2026-05-03"
    private val tomorrow = "2026-05-04"

    /**
     * Builds a fresh [AwardBattleSteps] with a linked player/repo pair. #227: the use case now
     * depends on [FakeStepRepository] (the atomic battle-step credit moved behind the port); the
     * fake routes wallet updates through `linkedPlayer` so the existing `player.observeProfile()`
     * assertions keep working. A [FakeTimeProvider] supplies the (now-required) clock.
     */
    private fun sut(initialBalance: Long = 0L): Triple<AwardBattleSteps, FakePlayerRepository, FakeStepRepository> {
        val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = initialBalance))
        val stepRepo = FakeStepRepository(linkedPlayer = playerRepo)
        val useCase = AwardBattleSteps(stepRepo, FakeTimeProvider(fixedDate = LocalDate.parse(today)))
        return Triple(useCase, playerRepo, stepRepo)
    }

    @Test
    fun `first call credits the full amount`() = runTest {
        val (useCase, player, repo) = sut()

        val credited = useCase(5L, today)

        assertEquals(5L, credited)
        assertEquals(5L, player.observeProfile().first().stepBalance)
        assertEquals(5L, repo.getBattleStepsEarned(today))
    }

    @Test
    fun `credits 0 and does not touch wallet once cap is hit`() = runTest {
        val (useCase, player, repo) = sut()
        repo.seedBattleStepsEarned(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP)

        val credited = useCase(10L, today)

        assertEquals(0L, credited)
        assertEquals(0L, player.observeProfile().first().stepBalance)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, repo.getBattleStepsEarned(today))
    }

    @Test
    fun `partial credit when remaining is smaller than amount`() = runTest {
        val (useCase, player, repo) = sut()
        // Start at cap - 3 → only 3 should be credited out of a 10 request.
        repo.seedBattleStepsEarned(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 3L)

        val credited = useCase(10L, today)

        assertEquals(3L, credited)
        assertEquals(3L, player.observeProfile().first().stepBalance)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, repo.getBattleStepsEarned(today))
    }

    @Test
    fun `date rollover resets the per-day counter`() = runTest {
        val (useCase, player, repo) = sut()
        // Exhaust today's cap.
        repo.seedBattleStepsEarned(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP)

        val creditedToday = useCase(5L, today)
        val creditedTomorrow = useCase(5L, tomorrow)

        assertEquals(0L, creditedToday)
        assertEquals(5L, creditedTomorrow)
        assertEquals(5L, player.observeProfile().first().stepBalance)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, repo.getBattleStepsEarned(today))
        assertEquals(5L, repo.getBattleStepsEarned(tomorrow))
    }

    @Test
    fun `zero or negative amounts are no-ops`() = runTest {
        val (useCase, player, repo) = sut()

        assertEquals(0L, useCase(0L, today))
        assertEquals(0L, useCase(-5L, today))
        assertEquals(0L, player.observeProfile().first().stepBalance)
        assertEquals(0L, repo.getBattleStepsEarned(today))
    }

    @Test
    fun `repo is incremented by credited amount not requested amount`() = runTest {
        val (useCase, _, repo) = sut()
        // Only 2 headroom in the cap; caller asks for 50.
        repo.seedBattleStepsEarned(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 2L)

        val credited = useCase(50L, today)

        assertEquals(2L, credited)
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, repo.getBattleStepsEarned(today))
    }

    @Test
    fun `FakeTimeProvider drives the cap reset when caller omits today`() = runTest {
        // Exercises the default `today` expression in invoke() — resolves through the injected
        // TimeProvider, so advancing the fake clock across midnight writes to a new date bucket.
        val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 0L))
        val repo = FakeStepRepository(linkedPlayer = playerRepo)
        val clock = FakeTimeProvider(fixedDate = LocalDate.parse(today))
        val useCase = AwardBattleSteps(repo, clock)

        // Exhaust today's cap using the default `today` parameter.
        repo.seedBattleStepsEarned(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP)
        val creditedBeforeRollover = useCase(10L)
        assertEquals(0L, creditedBeforeRollover, "cap exhausted on day 1")

        clock.fixedDate = LocalDate.parse(tomorrow)
        val creditedAfterRollover = useCase(10L)

        assertEquals(10L, creditedAfterRollover, "fresh cap on day 2")
        assertEquals(10L, repo.getBattleStepsEarned(tomorrow))
        assertEquals(AwardBattleSteps.DAILY_BATTLE_STEP_CAP, repo.getBattleStepsEarned(today),
            "day 1 record untouched")
    }

    // -------- atomicity tests (#227: now asserted on the StepRepository port) --------

    @Test
    fun `successful credit goes through atomic path and bypasses the legacy split path`() = runTest {
        val (useCase, player, repo) = sut()

        assertEquals(7L, useCase(7L, today))

        // Proves AwardBattleSteps delegates to the atomic credit and makes no direct wallet write.
        assertEquals(1, repo.creditBattleStepsAtomicCallCount)
        assertEquals(0, player.spendStepsCallCount, "no direct wallet write outside the atomic path")
    }

    @Test
    fun `two concurrent kills on exactly one headroom - only one credits`() = runTest {
        val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 0L))
        val repo = FakeStepRepository(linkedPlayer = playerRepo)
        val useCase = AwardBattleSteps(repo, FakeTimeProvider(fixedDate = LocalDate.parse(today)))
        repo.seedBattleStepsEarned(today, AwardBattleSteps.DAILY_BATTLE_STEP_CAP - 1L)

        val results = listOf(
            async { useCase(1L, today) },
            async { useCase(1L, today) },
        ).awaitAll()

        val creditedSum = results.sum()
        assertEquals(1L, creditedSum, "total credited must equal the one headroom unit — no overflow")
        assertEquals(
            AwardBattleSteps.DAILY_BATTLE_STEP_CAP,
            repo.getBattleStepsEarned(today),
            "cap counter must advance by exactly 1, not 2",
        )
        assertEquals(1L, playerRepo.profile.value.stepBalance, "wallet must advance by exactly 1")
        assertEquals(2, repo.creditBattleStepsAtomicCallCount, "both calls reached the atomic path")
    }
}
