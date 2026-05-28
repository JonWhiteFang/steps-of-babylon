package com.whitefang.stepsofbabylon.presentation.economy

import com.whitefang.stepsofbabylon.data.local.DailyLoginEntity
import com.whitefang.stepsofbabylon.data.local.WeeklyChallengeEntity
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyDashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var playerRepo: FakePlayerRepository
    private val weeklyChallengeDao = mock<com.whitefang.stepsofbabylon.data.local.WeeklyChallengeDao>()
    private val dailyLoginDao = mock<com.whitefang.stepsofbabylon.data.local.DailyLoginDao>()
    private val dailyStepDao = mock<com.whitefang.stepsofbabylon.data.local.DailyStepDao>()

    private val today = LocalDate.now()
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    @BeforeEach
    fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 100, powerStones = 50, currentStreak = 3))
        whenever(weeklyChallengeDao.getByWeek(any())).thenReturn(WeeklyChallengeEntity(monday.format(DateTimeFormatter.ISO_LOCAL_DATE), totalSteps = 45000, claimedTier = 0))
        whenever(weeklyChallengeDao.getLastNWeeks(any())).thenReturn(emptyList())
        whenever(dailyLoginDao.getByDate(any())).thenReturn(DailyLoginEntity(today.toString(), powerStoneClaimed = true, gemsClaimed = true))
        whenever(dailyStepDao.sumCreditedSteps(any(), any())).thenReturn(45000)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `displays currency balances`() = runTest(dispatcher) {
        val vm = CurrencyDashboardViewModel(playerRepo, weeklyChallengeDao, dailyLoginDao, dailyStepDao)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(100, state.gems)
        assertEquals(50, state.powerStones)
    }

    @Test
    fun `displays weekly progress`() = runTest(dispatcher) {
        val vm = CurrencyDashboardViewModel(playerRepo, weeklyChallengeDao, dailyLoginDao, dailyStepDao)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(45000, vm.uiState.value.weeklySteps)
    }

    @Test
    fun `displays login streak and claim state`() = runTest(dispatcher) {
        val vm = CurrencyDashboardViewModel(playerRepo, weeklyChallengeDao, dailyLoginDao, dailyStepDao)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(3, state.currentStreak)
        assertTrue(state.todayPsClaimed)
        assertTrue(state.todayGemsClaimed)
    }

    @Test
    fun `balances update reactively when profile changes`() = runTest(dispatcher) {
        val vm = CurrencyDashboardViewModel(playerRepo, weeklyChallengeDao, dailyLoginDao, dailyStepDao)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.gems)

        playerRepo.profile.value = playerRepo.profile.value.copy(gems = 250)
        advanceUntilIdle()
        assertEquals(250, vm.uiState.value.gems)
    }

    @Test
    fun `V1X16 - 4-week history populates from DAO`() = runTest(dispatcher) {
        val historyRows = listOf(
            // Current week (excluded by filter)
            WeeklyChallengeEntity(monday.format(DateTimeFormatter.ISO_LOCAL_DATE), totalSteps = 45000, claimedTier = 0),
            // 4 past weeks: mix of met and missed
            WeeklyChallengeEntity(monday.minusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE), totalSteps = 60000, claimedTier = 1),
            WeeklyChallengeEntity(monday.minusWeeks(2).format(DateTimeFormatter.ISO_LOCAL_DATE), totalSteps = 30000, claimedTier = 0),
            WeeklyChallengeEntity(monday.minusWeeks(3).format(DateTimeFormatter.ISO_LOCAL_DATE), totalSteps = 110000, claimedTier = 3),
            WeeklyChallengeEntity(monday.minusWeeks(4).format(DateTimeFormatter.ISO_LOCAL_DATE), totalSteps = 80000, claimedTier = 2),
        )
        whenever(weeklyChallengeDao.getLastNWeeks(any())).thenReturn(historyRows)

        val vm = CurrencyDashboardViewModel(playerRepo, weeklyChallengeDao, dailyLoginDao, dailyStepDao)
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
        val vm = CurrencyDashboardViewModel(playerRepo, weeklyChallengeDao, dailyLoginDao, dailyStepDao)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val tr = vm.uiState.value.weeklyTimeRemaining
        // Expect non-empty "Nd Hh" format. Exact value depends on test run time, but format is fixed.
        assertTrue(tr.isNotBlank(), "weeklyTimeRemaining should be populated")
        assertTrue(tr.matches(Regex("\\d+d \\d+h")), "expected 'Nd Hh' format, got '$tr'")
    }
}
