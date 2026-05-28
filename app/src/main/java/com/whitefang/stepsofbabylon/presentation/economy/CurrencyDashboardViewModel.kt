package com.whitefang.stepsofbabylon.presentation.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.data.local.DailyLoginDao
import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.WeeklyChallengeDao
import com.whitefang.stepsofbabylon.data.local.WeeklyChallengeEntity
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class CurrencyDashboardViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val weeklyChallengeDao: WeeklyChallengeDao,
    private val dailyLoginDao: DailyLoginDao,
    private val dailyStepDao: DailyStepDao,
) : ViewModel() {

    private data class SnapshotData(
        val weeklySteps: Long = 0,
        val weeklyClaimedTier: Int = 0,
        val todayPsClaimed: Boolean = false,
        val todayGemsClaimed: Boolean = false,
        val weeklyTimeRemaining: String = "",
        val weeklyHistory: List<WeeklyResult> = emptyList(),
    )

    private val snapshot = MutableStateFlow(SnapshotData())

    init { viewModelScope.launch { refresh() } }

    val uiState: StateFlow<EconomyUiState> = combine(
        playerRepository.observeProfile(),
        snapshot,
    ) { profile, snap ->
        EconomyUiState(
            gems = profile.gems,
            powerStones = profile.powerStones,
            weeklySteps = snap.weeklySteps,
            weeklyClaimedTier = snap.weeklyClaimedTier,
            currentStreak = profile.currentStreak,
            todayPsClaimed = snap.todayPsClaimed,
            todayGemsClaimed = snap.todayGemsClaimed,
            isLoading = false,
            weeklyTimeRemaining = snap.weeklyTimeRemaining,
            weeklyHistory = snap.weeklyHistory,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EconomyUiState())

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            val weekStart = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val weekEnd = sunday.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val weeklySteps = dailyStepDao.sumCreditedSteps(weekStart, weekEnd)
            val weekly = weeklyChallengeDao.getByWeek(weekStart) ?: WeeklyChallengeEntity(weekStartDate = weekStart)
            val login = dailyLoginDao.getByDate(todayStr)

            // V1X-16: fetch last 4 weeks excluding the current week (newest first → drop current,
            // keeping the previous 4). If fewer than 5 rows exist, show whatever's available.
            val historyRows = weeklyChallengeDao.getLastNWeeks(5)
                .filter { it.weekStartDate != weekStart }
                .take(4)
                .map { entity ->
                    WeeklyResult(
                        weekStartDate = entity.weekStartDate,
                        totalSteps = entity.totalSteps,
                        claimedTier = entity.claimedTier,
                        powerStonesEarned = WeeklyResult.powerStonesForTier(entity.claimedTier),
                    )
                }

            // V1X-16: time remaining until Sunday 23:59 (week end). Format "Nd Hh".
            val nextMonday = monday.plusDays(7)
            val nowMillis = System.currentTimeMillis()
            val nextResetMillis = nextMonday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val deltaMillis = (nextResetMillis - nowMillis).coerceAtLeast(0)
            val deltaHours = deltaMillis / (1000L * 60 * 60)
            val days = deltaHours / 24
            val hours = deltaHours % 24
            val timeRemaining = "${days}d ${hours}h"

            snapshot.value = SnapshotData(
                weeklySteps = weeklySteps,
                weeklyClaimedTier = weekly.claimedTier,
                todayPsClaimed = login?.powerStoneClaimed ?: false,
                todayGemsClaimed = login?.gemsClaimed ?: false,
                weeklyTimeRemaining = timeRemaining,
                weeklyHistory = historyRows,
            )
        }
    }
}
