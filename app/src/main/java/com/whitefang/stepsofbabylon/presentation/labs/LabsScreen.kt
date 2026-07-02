package com.whitefang.stepsofbabylon.presentation.labs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyCost
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
import com.whitefang.stepsofbabylon.presentation.ui.ErrorState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import com.whitefang.stepsofbabylon.presentation.ui.descriptionRes
import com.whitefang.stepsofbabylon.presentation.ui.nameRes
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.resolve
import java.util.Locale

@Composable
fun LabsScreen(viewModel: LabsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.error != null) {
        ErrorState(stringResource(state.error!!), onRetry = viewModel::retry)
        return
    }
    if (state.isLoading) {
        LoadingBox()
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            // Header: balances + slot info
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CurrencyValue(CurrencyType.STEPS, state.stepBalance)
                CurrencyValue(CurrencyType.GEMS, state.gems)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.labs_slots, state.activeSlots, state.totalSlots),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.totalSlots < 4) {
                    val slotPulse = rememberPulse()
                    val slotHaptics = rememberHaptics()
                    OutlinedButton(
                        onClick = {
                            slotPulse.trigger()
                            slotHaptics.tap()
                            viewModel.unlockSlot()
                        },
                        enabled = state.canAffordSlotUnlock,
                        modifier = Modifier.pulseScale(slotPulse),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.labs_unlock_slot))
                            CurrencyCost(CurrencyType.GEMS, state.slotUnlockCostGems)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.researchList) { info ->
                    ResearchCard(
                        info = info,
                        slotAvailable = state.activeSlots < state.totalSlots,
                        onStart = { viewModel.startResearch(info.type) },
                        onRush = { viewModel.rushResearch(info.type) },
                        freeRushAvailable = state.seasonPassFreeRushAvailable && info.isActive,
                        onFreeRush = { viewModel.freeRush(info.type) },
                    )
                }
            }
        }
    } // Scaffold
}

@Composable
private fun ResearchCard(
    info: ResearchDisplayInfo,
    slotAvailable: Boolean,
    onStart: () -> Unit,
    onRush: () -> Unit,
    freeRushAvailable: Boolean = false,
    onFreeRush: () -> Unit = {},
) {
    val rushPulse = rememberPulse()
    val startPulse = rememberPulse()
    val haptics = rememberHaptics()
    Card(
        Modifier.fillMaxWidth(),
        colors =
            if (info.isActive) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(info.type.nameRes()), style = MaterialTheme.typography.titleSmall)
                when {
                    info.isMaxed -> {
                        Text(
                            "MAX",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    else -> {
                        Text(
                            stringResource(R.string.labs_level_progress, info.level, info.type.maxLevel),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Text(
                stringResource(info.type.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            when {
                info.isMaxed -> {}

                // no actions
                info.isActive -> {
                    // Progress based on remaining vs time to complete
                    val totalMs = (info.timeToCompleteHours * 3_600_000).toLong()
                    val elapsed = totalMs - info.remainingMs
                    val progress = if (totalMs > 0) (elapsed.toFloat() / totalMs).coerceIn(0f, 1f) else 1f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatTime(info.remainingMs, doneLabel = stringResource(R.string.lab_time_done)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (freeRushAvailable) {
                                OutlinedButton(onClick = onFreeRush) {
                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.labs_free))
                                }
                            }
                            Button(
                                onClick = {
                                    rushPulse.trigger()
                                    haptics.tap()
                                    onRush()
                                },
                                enabled = info.canAffordRush,
                                modifier = Modifier.pulseScale(rushPulse),
                            ) {
                                Text(stringResource(R.string.labs_rush))
                                CurrencyCost(CurrencyType.GEMS, info.rushCostGems)
                            }
                        }
                    }
                }

                !slotAvailable -> {
                    Text(
                        stringResource(R.string.labs_no_slot),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                String.format(Locale.ROOT, "%.1fh", info.timeToCompleteHours),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = {
                                startPulse.trigger()
                                haptics.tap()
                                onStart()
                            },
                            enabled = info.canAffordStart,
                            modifier = Modifier.pulseScale(startPulse),
                        ) {
                            Text(stringResource(R.string.labs_start))
                            CurrencyCost(CurrencyType.STEPS, info.costToStart)
                        }
                    }
                }
            }
        }
    }
}

// i18n(#34): "Done!" early-return + the h/m/s duration units are localized. The h/m/s
// values are small (≤ 59 for m/s, hours bounded by lab durations) — .toInt() for the %d args.
@Composable
private fun formatTime(
    ms: Long,
    doneLabel: String,
): String {
    if (ms <= 0) return doneLabel
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.duration_hms, hours.toInt(), minutes.toInt(), seconds.toInt())
        minutes > 0 -> stringResource(R.string.duration_ms, minutes.toInt(), seconds.toInt())
        else -> stringResource(R.string.duration_s, seconds.toInt())
    }
}
