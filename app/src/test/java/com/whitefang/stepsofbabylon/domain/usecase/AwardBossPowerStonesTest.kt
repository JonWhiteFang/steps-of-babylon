package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AwardBossPowerStonesTest {
    private lateinit var fakePlayer: FakePlayerRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var fakeTime: FakeTimeProvider
    private lateinit var useCase: AwardBossPowerStones
    private val today = "2026-05-24"

    @BeforeEach
    fun setup() {
        fakePlayer = FakePlayerRepository()
        // #227: boss-PS atomic credit moved behind StepRepository; the fake routes to linkedPlayer.
        stepRepo = FakeStepRepository(linkedPlayer = fakePlayer)
        fakeTime = FakeTimeProvider(fixedDate = LocalDate.of(2026, 5, 24))
        useCase = AwardBossPowerStones(stepRepo, fakeTime)
    }

    @Test
    fun `tier 1 boss awards 1 PS`() =
        runTest {
            val credited = useCase(tier = 1)
            assertEquals(1L, credited)
            assertEquals(1L, stepRepo.getBossPsEarnedToday(today))
            assertEquals(1L, fakePlayer.profile.value.powerStones)
        }

    @Test
    fun `tier 5 boss awards 5 PS`() =
        runTest {
            val credited = useCase(tier = 5)
            assertEquals(5L, credited)
            assertEquals(5L, stepRepo.getBossPsEarnedToday(today))
            assertEquals(5L, fakePlayer.profile.value.powerStones)
        }

    @Test
    fun `tier 10 boss awards 10 PS`() =
        runTest {
            val credited = useCase(tier = 10)
            assertEquals(10L, credited)
            assertEquals(10L, stepRepo.getBossPsEarnedToday(today))
        }

    @Test
    fun `cap at 100 PS per day`() =
        runTest {
            // Earn 95 PS first
            stepRepo.seedBossPsEarned(today, 95L)
            // Tier 10 boss requests 10 but only 5 remain
            val credited = useCase(tier = 10)
            assertEquals(5L, credited)
            assertEquals(100L, stepRepo.getBossPsEarnedToday(today))
        }

    @Test
    fun `zero credit when cap exhausted`() =
        runTest {
            stepRepo.seedBossPsEarned(today, 100L)
            val credited = useCase(tier = 5)
            assertEquals(0L, credited)
            assertEquals(100L, stepRepo.getBossPsEarnedToday(today))
        }

    @Test
    fun `multiple boss kills accumulate`() =
        runTest {
            useCase(tier = 3)
            useCase(tier = 5)
            useCase(tier = 2)
            assertEquals(10L, stepRepo.getBossPsEarnedToday(today))
            assertEquals(10L, fakePlayer.profile.value.powerStones)
        }

    @Test
    fun `tier 0 or negative coerced to 1`() =
        runTest {
            val credited = useCase(tier = 0)
            assertEquals(1L, credited)
        }

    @Test
    fun `uses atomic DAO path`() =
        runTest {
            useCase(tier = 3)
            assertEquals(1, stepRepo.creditBossPsAtomicCallCount)
        }

    @Test
    fun `day rollover resets cap`() =
        runTest {
            // Exhaust today
            stepRepo.seedBossPsEarned(today, 100L)
            assertEquals(0L, useCase(tier = 5))
            // Advance to tomorrow
            fakeTime.fixedDate = LocalDate.of(2026, 5, 25)
            val credited = useCase(tier = 5)
            assertEquals(5L, credited)
            assertEquals(5L, stepRepo.getBossPsEarnedToday("2026-05-25"))
        }
}
