package com.whitefang.stepsofbabylon.presentation.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.WeeklyChallenge
import com.whitefang.stepsofbabylon.domain.repository.DailyLoginRepository
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import com.whitefang.stepsofbabylon.domain.repository.WeeklyChallengeRepository
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CurrencyDashboardViewModel
    @Inject
    constructor(
        private val playerRepository: PlayerRepository,
        private val weeklyChallengeRepository: WeeklyChallengeRepository,
        private val dailyLoginRepository: DailyLoginRepository,
        private val stepRepository: StepRepository,
    ) : ViewModel() {
        private data class SnapshotData(
            val weeklySteps: Long = 0,
            val weeklyClaimedTier: Int = 0,
            val todayPsClaimed: Boolean = false,
            val todayGemsClaimed: Boolean = false,
            val weeklyResetDays: Int? = null,
            val weeklyResetHours: Int? = null,
            val weeklyHistory: List<WeeklyResult> = emptyList(),
        )

        private val snapshot = MutableStateFlow(SnapshotData())

        // #194: bump to re-subscribe the data flow after a load error (retry).
        private val _retry = MutableStateFlow(0)

        init {
            viewModelScope.launch { refresh() }
        }

        val uiState: StateFlow<EconomyUiState> =
            _retry
                .flatMapLatest {
                    combine(
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
                            weeklyResetDays = snap.weeklyResetDays,
                            weeklyResetHours = snap.weeklyResetHours,
                            weeklyHistory = snap.weeklyHistory,
                        )
                    }
                        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
                        // flatMapLatest so retry() re-subscribes.
                        .catch { emit(EconomyUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EconomyUiState())

        /** #194: re-subscribe the data flow after a load error. */
        fun retry() {
            _retry.value++
        }

        fun refresh() {
            viewModelScope.launch {
                val today = LocalDate.now()
                val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6)
                val weekStart = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val weekEnd = sunday.format(DateTimeFormatter.ISO_LOCAL_DATE)

                val weeklySteps = stepRepository.sumCreditedSteps(weekStart, weekEnd)
                val weekly =
                    weeklyChallengeRepository.getByWeek(weekStart) ?: WeeklyChallenge(weekStartDate = weekStart)
                val login = dailyLoginRepository.getByDate(todayStr)

                // V1X-16: fetch last 4 weeks excluding the current week (newest first → drop current,
                // keeping the previous 4). If fewer than 5 rows exist, show whatever's available.
                val historyRows =
                    weeklyChallengeRepository
                        .getLastNWeeks(5)
                        .filter { it.weekStartDate != weekStart }
                        .take(4)
                        .map { wc ->
                            WeeklyResult(
                                weekStartDate = wc.weekStartDate,
                                totalSteps = wc.totalSteps,
                                claimedTier = wc.claimedTier,
                                powerStonesEarned = WeeklyResult.powerStonesForTier(wc.claimedTier),
                            )
                        }

                // V1X-16: time remaining until Sunday 23:59 (week end). Raw days/hours are carried to
                // the UI state and composed at the presentation boundary (i18n #34, "Nd Hh").
                val nextMonday = monday.plusDays(7)
                val nowMillis = System.currentTimeMillis()
                val nextResetMillis =
                    nextMonday
                        .atStartOfDay(
                            java.time.ZoneId.systemDefault(),
                        ).toInstant()
                        .toEpochMilli()
                val deltaMillis = (nextResetMillis - nowMillis).coerceAtLeast(0)
                val deltaHours = deltaMillis / (1000L * 60 * 60)
                val days = deltaHours / 24
                val hours = deltaHours % 24

                snapshot.value =
                    SnapshotData(
                        weeklySteps = weeklySteps,
                        weeklyClaimedTier = weekly.claimedTier,
                        todayPsClaimed = login?.powerStoneClaimed ?: false,
                        todayGemsClaimed = login?.gemsClaimed ?: false,
                        weeklyResetDays = days.toInt(),
                        weeklyResetHours = hours.toInt(),
                        weeklyHistory = historyRows,
                    )
            }
        }
    }
