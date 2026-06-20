package com.whitefang.stepsofbabylon.presentation.economy

import com.whitefang.stepsofbabylon.domain.model.DailyLogin
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.WeeklyChallenge
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeWeeklyChallengeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * #219: the VM now reads through repository PORTS (no raw DAOs / entities). Tests seed the fake repos
 * with DOMAIN values (`WeeklyChallenge`/`DailyLogin`/`DailyStepSummary`) instead of stubbing entity-
 * returning DAO mocks — same assertions, same expected values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyDashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var playerRepo: FakePlayerRepository
    private val weeklyRepo = FakeWeeklyChallengeRepository()
    private val loginRepo = FakeDailyLoginRepository()
    private val stepRepo = FakeStepRepository()

    private val today = LocalDate.now()
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val weekStart = monday.format(fmt)

    @BeforeEach
    fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 100, powerStones = 50, currentStreak = 3))
        weeklyRepo.upsert(WeeklyChallenge(weekStart, totalSteps = 45000, claimedTier = 0))
        loginRepo.upsert(DailyLogin(today.toString(), powerStoneClaimed = true, gemsClaimed = true))
        // Credited steps for the current week → sumCreditedSteps(weekStart, weekEnd) == 45000.
        stepRepo.records.value = mapOf(weekStart to DailyStepSummary(weekStart, creditedSteps = 45000))
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm() = CurrencyDashboardViewModel(playerRepo, weeklyRepo, loginRepo, stepRepo)

    @Test
    fun `displays currency balances`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(100, state.gems)
        assertEquals(50, state.powerStones)
    }

    @Test
    fun `displays weekly progress`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(45000, vm.uiState.value.weeklySteps)
    }

    @Test
    fun `displays login streak and claim state`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(3, state.currentStreak)
        assertTrue(state.todayPsClaimed)
        assertTrue(state.todayGemsClaimed)
    }

    @Test
    fun `balances update reactively when profile changes`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.gems)

        playerRepo.profile.value = playerRepo.profile.value.copy(gems = 250)
        advanceUntilIdle()
        assertEquals(250, vm.uiState.value.gems)
    }

    @Test
    fun `V1X16 - 4-week history populates from repository`() = runTest(dispatcher) {
        // Current week (excluded by filter) + 4 past weeks: mix of met and missed.
        weeklyRepo.upsert(WeeklyChallenge(monday.minusWeeks(1).format(fmt), totalSteps = 60000, claimedTier = 1))
        weeklyRepo.upsert(WeeklyChallenge(monday.minusWeeks(2).format(fmt), totalSteps = 30000, claimedTier = 0))
        weeklyRepo.upsert(WeeklyChallenge(monday.minusWeeks(3).format(fmt), totalSteps = 110000, claimedTier = 3))
        weeklyRepo.upsert(WeeklyChallenge(monday.minusWeeks(4).format(fmt), totalSteps = 80000, claimedTier = 2))

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val history = vm.uiState.value.weeklyHistory
        assertEquals(4, history.size, "should show last 4 weeks excluding current")
        assertEquals(1, history[0].claimedTier)
        assertEquals(10, history[0].powerStonesEarned) // tier 1 → 10 PS
        assertEquals(0, history[1].claimedTier)
        assertEquals(0, history[1].powerStonesEarned) // missed → 0 PS
        assertEquals(3, history[2].claimedTier)
        assertEquals(65, history[2].powerStonesEarned) // tier 3 → 65 PS (10+20+35)
        assertEquals(2, history[3].claimedTier)
        assertEquals(30, history[3].powerStonesEarned) // tier 2 → 30 PS (10+20)
    }

    @Test
    fun `V1X16 - weeklyTimeRemaining is populated and formatted`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val tr = vm.uiState.value.weeklyTimeRemaining
        assertTrue(tr.isNotBlank(), "weeklyTimeRemaining should be populated")
        assertTrue(tr.matches(Regex("\\d+d \\d+h")), "expected 'Nd Hh' format, got '$tr'")
    }
}
