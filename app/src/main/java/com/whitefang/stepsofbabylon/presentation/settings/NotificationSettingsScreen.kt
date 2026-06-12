package com.whitefang.stepsofbabylon.presentation.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun NotificationSettingsScreen(
    onReplayTutorial: () -> Unit = {},
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 0 = hidden, 1 = first confirm, 2 = final confirm
    var deleteStep by remember { mutableIntStateOf(0) }
    val activity = LocalActivity.current

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Notification Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Live Step Updates", "Update notification with live step count and balance. A minimal tracking notification is always shown while step counting is active.", state.persistentSteps, viewModel::setPersistent)
        ToggleRow("Supply Drops", "Notifications for walking rewards", state.supplyDrops, viewModel::setSupplyDrops)
        ToggleRow("Smart Reminders", "Upgrade proximity reminders", state.smartReminders, viewModel::setSmartReminders)
        ToggleRow("Milestone Alerts", "Wave records and step milestones", state.milestoneAlerts, viewModel::setMilestoneAlerts)
        Spacer(Modifier.height(16.dp))
        Text("Sound", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Mute Sound Effects", "Silence all in-game sounds", state.soundMuted, viewModel::setSoundMuted)
        ToggleRow("Mute Music", "Silence background music", state.musicMuted, viewModel::setMusicMuted)
        if (!state.musicMuted) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Music Volume", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.4f))
                Slider(
                    value = state.musicVolume,
                    onValueChange = { viewModel.setMusicVolume(it) },
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Help", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedCard(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Replay tutorial", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "See the first-launch walkthrough again",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedCard(
            onClick = { deleteStep = 1 },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Delete All Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    Text("Permanently erase all progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text("Delete All Data?") },
            text = { Text("This will permanently delete all your progress, steps history, upgrades, and currency. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { deleteStep = 2 }) { Text("Continue", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Cancel") } },
        )
    }

    if (deleteStep == 2) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text("Are you absolutely sure?") },
            text = { Text("All steps, upgrades, cards, weapons, and purchases will be lost forever. The app will restart.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteStep = 0
                    activity?.let { viewModel.deleteAllData(it) }
                }) { Text("Delete Everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
