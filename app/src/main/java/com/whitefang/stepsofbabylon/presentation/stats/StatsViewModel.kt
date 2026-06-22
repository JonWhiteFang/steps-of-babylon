package com.whitefang.stepsofbabylon.presentation.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val playerRepository: PlayerRepository,
    private val workshopRepository: WorkshopRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val selectedPeriod: StateFlow<StatsPeriod> =
        savedStateHandle.getStateFlow(KEY_SELECTED_PERIOD, StatsPeriod.WEEK)
    private val _today = MutableStateFlow(LocalDate.now())
    // #194: bump to re-subscribe the data flow after a load error (retry).
    private val _retry = MutableStateFlow(0)
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    val uiState: StateFlow<StatsUiState> = combine(_today, _retry) { today, _ -> today }
        .flatMapLatest { today ->
        val historyFlow = stepRepository.observeHistory(
            today.minusDays(89).format(fmt), today.format(fmt)
        )
        combine(
            playerRepository.observeProfile(),
            historyFlow,
            workshopRepository.observeAllUpgrades(),
            selectedPeriod,
        ) { profile, history, upgrades, period ->
            val todayRecord = history.find { it.date == today.format(fmt) }
            val bars = buildBars(history, period, today)
            val daysActive = history.count { it.creditedSteps > 0 }

        StatsUiState(
            todaySteps = todayRecord?.creditedSteps ?: 0,
            todayStepEquivalents = todayRecord?.stepEquivalents ?: 0,
            todayActivityMinutes = todayRecord?.activityMinutes ?: emptyMap(),
            allTimeSteps = profile.totalStepsEarned,
            bars = bars,
            selectedPeriod = period,
            bestWavePerTier = profile.bestWavePerTier,
            totalRoundsPlayed = profile.totalRoundsPlayed,
            totalEnemiesKilled = profile.totalEnemiesKilled,
            totalCashEarned = profile.totalCashEarned,
            totalGemsEarned = profile.totalGemsEarned,
            totalGemsSpent = profile.totalGemsSpent,
            totalPowerStonesEarned = profile.totalPowerStonesEarned,
            totalPowerStonesSpent = profile.totalPowerStonesSpent,
            currentGems = profile.gems,
            currentPowerStones = profile.powerStones,
            totalWorkshopLevels = upgrades.values.sum(),
            daysActive = daysActive,
            averageDailySteps = if (daysActive > 0) profile.totalStepsEarned / daysActive else 0,
            isLoading = false,
        )
    }
        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
        // flatMapLatest so retry() re-subscribes.
        .catch { emit(StatsUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun selectPeriod(period: StatsPeriod) { savedStateHandle[KEY_SELECTED_PERIOD] = period }

    /** #194: re-subscribe the data flow after a load error. */
    fun retry() { _retry.value++ }

    fun refreshDate() {
        val now = LocalDate.now()
        if (now != _today.value) _today.value = now
    }

    private companion object {
        const val KEY_SELECTED_PERIOD = "selectedPeriod"
    }

    private fun buildBars(history: List<DailyStepSummary>, period: StatsPeriod, today: LocalDate): List<DailyBarData> {
        val byDate = history.associateBy { it.date }
        return when (period) {
            StatsPeriod.WEEK -> (6 downTo 0).map { daysAgo ->
                val d = today.minusDays(daysAgo.toLong())
                val rec = byDate[d.format(fmt)]
                DailyBarData(
                    label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    sensorSteps = (rec?.creditedSteps ?: 0) - (rec?.stepEquivalents ?: 0),
                    stepEquivalents = rec?.stepEquivalents ?: 0,
                )
            }
            StatsPeriod.MONTH -> (29 downTo 0).map { daysAgo ->
                val d = today.minusDays(daysAgo.toLong())
                val rec = byDate[d.format(fmt)]
                DailyBarData(
                    label = "${d.dayOfMonth}",
                    sensorSteps = (rec?.creditedSteps ?: 0) - (rec?.stepEquivalents ?: 0),
                    stepEquivalents = rec?.stepEquivalents ?: 0,
                )
            }
            StatsPeriod.QUARTER -> {
                // Aggregate into 12 weekly buckets ending this week.
                // #30: parse each history row's date ONCE and bucket it by week-index in a single
                // O(history) pass, instead of re-parsing all rows inside each of the 12 buckets
                // (the old `history.filter { LocalDate.parse(...) }`-per-bucket was up to 12×90
                // LocalDate.parse calls per emission of a main-thread-bound StateFlow mapper).
                val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sensorByWeek = LongArray(12)
                val equivByWeek = LongArray(12)
                for (rec in history) {
                    val recMonday = LocalDate.parse(rec.date, fmt)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    // weeksAgo: 0 = this week … 11 = eleven weeks back. Out-of-window rows skipped.
                    val weeksAgo = ChronoUnit.WEEKS.between(recMonday, thisMonday).toInt()
                    if (weeksAgo in 0..11) {
                        val idx = 11 - weeksAgo // bar index: oldest week first, current week last
                        sensorByWeek[idx] += rec.creditedSteps - rec.stepEquivalents
                        equivByWeek[idx] += rec.stepEquivalents
                    }
                }
                (0..11).map { idx ->
                    val weekStart = thisMonday.minusWeeks((11 - idx).toLong())
                    DailyBarData(
                        label = "W${weekStart.dayOfMonth}",
                        sensorSteps = sensorByWeek[idx],
                        stepEquivalents = equivByWeek[idx],
                    )
                }
            }
        }
    }
}
