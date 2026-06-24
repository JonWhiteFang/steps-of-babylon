package com.whitefang.stepsofbabylon.presentation.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.ErrorState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import com.whitefang.stepsofbabylon.presentation.ui.formatCount
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.error != null) {
        ErrorState(state.error!!, onRetry = viewModel::retry)
        return
    }
    if (state.isLoading) {
        LoadingBox()
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDate()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Walking History Chart
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.stats_walking_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    WalkingHistoryChart(
                        bars = state.bars,
                        selectedPeriod = state.selectedPeriod,
                        onPeriodSelected = viewModel::selectPeriod,
                    )
                }
            }
        }

        // Today's Activity
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.stats_today),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(stringResource(R.string.stats_row_steps), formatCount(state.todaySteps))
                    if (state.todayStepEquivalents > 0) {
                        StatRow(
                            stringResource(R.string.stats_row_activity_equiv),
                            formatCount(state.todayStepEquivalents),
                        )
                    }
                    state.todayActivityMinutes.forEach { (activity, minutes) ->
                        StatRow(
                            activity
                                .replace("_", " ")
                                .lowercase(Locale.ROOT)
                                .replaceFirstChar { it.uppercase() },
                            "$minutes min",
                        )
                    }
                }
            }
        }

        // Battle Stats
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.stats_battle_stats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.bestWavePerTier.toSortedMap().forEach { (tier, wave) ->
                        StatRow(stringResource(R.string.stats_row_tier_best_wave, tier), "$wave")
                    }
                    StatRow(stringResource(R.string.stats_row_rounds_played), formatCount(state.totalRoundsPlayed))
                    StatRow(stringResource(R.string.stats_row_enemies_killed), formatCount(state.totalEnemiesKilled))
                    StatRow(stringResource(R.string.stats_row_total_cash), formatCount(state.totalCashEarned))
                }
            }
        }

        // All-Time Stats
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.stats_all_time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(stringResource(R.string.stats_row_lifetime_steps), formatCount(state.allTimeSteps))
                    StatRow(stringResource(R.string.stats_row_days_active), "${state.daysActive}")
                    StatRow(stringResource(R.string.stats_row_avg_daily_steps), formatCount(state.averageDailySteps))
                    StatRow(stringResource(R.string.stats_row_workshop_levels), "${state.totalWorkshopLevels}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.stats_gems),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    StatRow(stringResource(R.string.stats_row_current), formatCount(state.currentGems))
                    StatRow(stringResource(R.string.stats_row_earned), formatCount(state.totalGemsEarned))
                    StatRow(stringResource(R.string.stats_row_spent), formatCount(state.totalGemsSpent))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.stats_power_stones),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    StatRow(stringResource(R.string.stats_row_current), formatCount(state.currentPowerStones))
                    StatRow(stringResource(R.string.stats_row_earned), formatCount(state.totalPowerStonesEarned))
                    StatRow(stringResource(R.string.stats_row_spent), formatCount(state.totalPowerStonesSpent))
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
