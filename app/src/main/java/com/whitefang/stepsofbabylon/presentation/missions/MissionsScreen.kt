package com.whitefang.stepsofbabylon.presentation.missions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.domain.model.Milestone
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyType
import com.whitefang.stepsofbabylon.presentation.ui.CurrencyValue
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionsScreen(viewModel: MissionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmt = NumberFormat.getNumberInstance()
    val snackbarHostState = remember { SnackbarHostState() }

    // C.4: surface non-Success ClaimMilestoneResult (InsufficientSteps, AlreadyClaimed,
    // UnknownCosmetic) as snackbars so claim failures are visible instead of silent.
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Midnight countdown
            item {
                val hours = (state.timeUntilMidnightMs / 3_600_000).toInt()
                val minutes = ((state.timeUntilMidnightMs % 3_600_000) / 60_000).toInt()
                Text("Daily Missions", style = MaterialTheme.typography.headlineSmall)
                Text("Resets in ${hours}h ${minutes}m", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Daily missions
            items(state.missions, key = { it.id }) { mission ->
                MissionCard(mission, onClaim = { viewModel.claimMission(mission.id) }, fmt = fmt)
            }

            // Milestones header
            item {
                Spacer(Modifier.height(8.dp))
                Text("Walking Milestones", style = MaterialTheme.typography.headlineSmall)
            }

            // Milestones
            items(state.milestones, key = { it.milestone.name }) { ms ->
                MilestoneCard(ms, onClaim = { viewModel.claimMilestone(ms.milestone) }, fmt = fmt)
            }
        }
    }
}

@Composable
private fun MissionCard(mission: MissionDisplayInfo, onClaim: () -> Unit, fmt: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mission.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (mission.claimed) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Claimed", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (mission.target > 0) (mission.progress.toFloat() / mission.target).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${fmt.format(mission.progress)} / ${fmt.format(mission.target)}",
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mission.rewardGems > 0) {
                        CurrencyValue(CurrencyType.GEMS, mission.rewardGems.toLong(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (mission.rewardPowerStones > 0) {
                        if (mission.rewardGems > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text("+", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(6.dp))
                        }
                        CurrencyValue(CurrencyType.POWER_STONES, mission.rewardPowerStones.toLong(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (mission.completed && !mission.claimed) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClaim, modifier = Modifier.align(Alignment.End)) {
                    Text("Claim")
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(ms: MilestoneDisplayInfo, onClaim: () -> Unit, fmt: NumberFormat) {
    val milestone = ms.milestone
    val progress = (ms.totalStepsEarned.toFloat() / milestone.requiredSteps).coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ms.isClaimed) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(milestone.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (ms.isClaimed) Icon(Icons.Filled.CheckCircle, contentDescription = "Claimed", tint = MaterialTheme.colorScheme.primary)
            }
            Text("${fmt.format(milestone.requiredSteps)} steps", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(milestone.rewardsSummary(), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${fmt.format(ms.totalStepsEarned)} / ${fmt.format(milestone.requiredSteps)}",
                style = MaterialTheme.typography.bodySmall)
            if (ms.isAchieved && !ms.isClaimed) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClaim, modifier = Modifier.align(Alignment.End)) {
                    Text("Claim")
                }
            }
        }
    }
}
