package com.whitefang.stepsofbabylon.presentation.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.R

@Composable
fun SettingsScreen(
    onReplayTutorial: () -> Unit = {},
    onOptimizeBattery: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 0 = hidden, 1 = first confirm, 2 = final confirm
    var deleteStep by remember { mutableIntStateOf(0) }
    val activity = LocalActivity.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToggleRow(
            stringResource(R.string.settings_live_steps_header),
            stringResource(R.string.settings_live_steps_subtitle),
            state.persistentSteps,
            viewModel::setPersistent,
        )
        ToggleRow(
            stringResource(R.string.settings_supply_drops_header),
            stringResource(R.string.settings_supply_drops_subtitle),
            state.supplyDrops,
            viewModel::setSupplyDrops,
        )
        ToggleRow(
            stringResource(R.string.settings_smart_reminders_header),
            stringResource(R.string.settings_smart_reminders_subtitle),
            state.smartReminders,
            viewModel::setSmartReminders,
        )
        ToggleRow(
            stringResource(R.string.settings_milestone_alerts_header),
            stringResource(R.string.settings_milestone_alerts_subtitle),
            state.milestoneAlerts,
            viewModel::setMilestoneAlerts,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_sound_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            stringResource(R.string.settings_mute_sound_header),
            stringResource(R.string.settings_mute_sound_subtitle),
            state.soundMuted,
            viewModel::setSoundMuted,
        )
        ToggleRow(
            stringResource(R.string.settings_mute_music_header),
            stringResource(R.string.settings_mute_music_subtitle),
            state.musicMuted,
            viewModel::setMusicMuted,
        )
        if (!state.musicMuted) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_music_volume),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(0.4f),
                )
                Slider(
                    value = state.musicVolume,
                    onValueChange = { viewModel.setMusicVolume(it) },
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        ToggleRow(
            stringResource(R.string.settings_haptic_header),
            stringResource(R.string.settings_haptic_subtitle),
            state.hapticsEnabled,
            viewModel::setHapticsEnabled,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_help_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedCard(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        stringResource(R.string.settings_replay_tutorial),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_replay_tutorial_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // #261: durable re-offer of the battery-optimization exemption (onboarding's primer is one-shot,
        // so existing players never see it; this is the persistent entry point). Always available — the
        // system handles the already-exempt case.
        OutlinedCard(onClick = onOptimizeBattery, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        stringResource(R.string.settings_background_activity_header),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_background_activity_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.settings_data_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        // #240: in-app entry point to the hosted privacy policy. Play's User Data policy expects an
        // easily accessible in-app link, not only the store listing; previously the only in-app path
        // was buried in the Health Connect permission rationale.
        OutlinedCard(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        stringResource(R.string.settings_privacy_policy),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_privacy_policy_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedCard(
            onClick = { deleteStep = 1 },
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                ),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_delete_card_header),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.settings_delete_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.settings_delete_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = { deleteStep = 2 }) {
                    Text(stringResource(R.string.action_continue), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteStep = 0 },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (deleteStep == 2) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text(stringResource(R.string.settings_delete_final_title)) },
            text = {
                Text(stringResource(R.string.settings_delete_final_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteStep = 0
                    activity?.let { viewModel.deleteAllData(it) }
                }) {
                    Text(
                        stringResource(R.string.settings_delete_confirm_final),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteStep = 0 },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
