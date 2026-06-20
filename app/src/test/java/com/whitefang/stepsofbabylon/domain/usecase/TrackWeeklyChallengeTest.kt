package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeWeeklyChallengeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class TrackWeeklyChallengeTest {

    private lateinit var weeklyRepo: FakeWeeklyChallengeRepository
    private lateinit var stepRepo: FakeStepRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var useCase: TrackWeeklyChallenge

    private val today = LocalDate.now()
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    @BeforeEach
    fun setup() {
        weeklyRepo = FakeWeeklyChallengeRepository()
        stepRepo = FakeStepRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 0))
        useCase = TrackWeeklyChallenge(weeklyRepo, stepRepo, playerRepo)
    }

    private fun seedSteps(total: Long) {
        // #227: weekly sum now reads StepRepository.sumCreditedSteps (was DailyStepDao). Seed the
        // credited steps as a DailyStepSummary in the Monday bucket of the current week.
        stepRepo.records.value = mapOf(
            monday.format(fmt) to DailyStepSummary(date = monday.format(fmt), creditedSteps = total)
        )
    }

    @Test
    fun `awards 10 PS at 50k`() = runTest {
        seedSteps(55_000)
        useCase.checkAndAward()
        assertEquals(10, playerRepo.profile.value.powerStones)
    }

    @Test
    fun `awards delta 10 PS at 75k after 50k claimed`() = runTest {
        seedSteps(55_000)
        useCase.checkAndAward()
        assertEquals(10, playerRepo.profile.value.powerStones)
        // Now cross 75k
        seedSteps(80_000)
        useCase.checkAndAward()
        assertEquals(20, playerRepo.profile.value.powerStones) // 10 + 10 delta
    }

    @Test
    fun `awards delta 15 PS at 100k after 75k claimed`() = runTest {
        seedSteps(80_000)
        useCase.checkAndAward() // claims tier 1 (10) + tier 2 (10) = 20
        assertEquals(20, playerRepo.profile.value.powerStones)
        seedSteps(105_000)
        useCase.checkAndAward()
        assertEquals(35, playerRepo.profile.value.powerStones) // 20 + 15 delta
    }

    @Test
    fun `no award below threshold`() = runTest {
        seedSteps(30_000)
        useCase.checkAndAward()
        assertEquals(0, playerRepo.profile.value.powerStones)
    }

    @Test
    fun `no double award for same tier`() = runTest {
        seedSteps(55_000)
        useCase.checkAndAward()
        useCase.checkAndAward()
        assertEquals(10, playerRepo.profile.value.powerStones)
    }
}
