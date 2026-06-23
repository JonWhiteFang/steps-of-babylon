package com.whitefang.stepsofbabylon.presentation.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.presentation.ui.ErrorState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import java.text.NumberFormat

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
    val fmt = NumberFormat.getNumberInstance()
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
                    Text("Walking History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow("Steps", fmt.format(state.todaySteps))
                    if (state.todayStepEquivalents > 0) {
                        StatRow("Activity Step-Equivalents", fmt.format(state.todayStepEquivalents))
                    }
                    state.todayActivityMinutes.forEach { (activity, minutes) ->
                        StatRow(
                            activity
                                .replace("_", " ")
                                .lowercase()
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
                    Text("Battle Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    state.bestWavePerTier.toSortedMap().forEach { (tier, wave) ->
                        StatRow("Tier $tier Best Wave", "$wave")
                    }
                    StatRow("Rounds Played", fmt.format(state.totalRoundsPlayed))
                    StatRow("Enemies Killed", fmt.format(state.totalEnemiesKilled))
                    StatRow("Total Cash Earned", fmt.format(state.totalCashEarned))
                }
            }
        }

        // All-Time Stats
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("All-Time Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow("Lifetime Steps", fmt.format(state.allTimeSteps))
                    StatRow("Days Active", "${state.daysActive}")
                    StatRow("Avg Daily Steps", fmt.format(state.averageDailySteps))
                    StatRow("Workshop Levels", "${state.totalWorkshopLevels}")
                    Spacer(Modifier.height(8.dp))
                    Text("Gems", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    StatRow("Current", fmt.format(state.currentGems))
                    StatRow("Earned", fmt.format(state.totalGemsEarned))
                    StatRow("Spent", fmt.format(state.totalGemsSpent))
                    Spacer(Modifier.height(8.dp))
                    Text("Power Stones", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    StatRow("Current", fmt.format(state.currentPowerStones))
                    StatRow("Earned", fmt.format(state.totalPowerStonesEarned))
                    StatRow("Spent", fmt.format(state.totalPowerStonesSpent))
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
