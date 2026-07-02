package com.whitefang.stepsofbabylon.presentation.weapons

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.presentation.ui.EquippedChip
import com.whitefang.stepsofbabylon.presentation.ui.ErrorState
import com.whitefang.stepsofbabylon.presentation.ui.LoadingBox
import com.whitefang.stepsofbabylon.presentation.ui.RarityBadge
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rarityBorder
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusWarning
import com.whitefang.stepsofbabylon.presentation.ui.toDisplayName
import com.whitefang.stepsofbabylon.presentation.ui.uwRarityLabelRes
import com.whitefang.stepsofbabylon.presentation.ui.uwRarityTier

@Composable
fun UltimateWeaponScreen(viewModel: UltimateWeaponViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.error != null) {
        ErrorState(stringResource(state.error!!), onRetry = viewModel::retry)
        return
    }
    if (state.isLoading) {
        LoadingBox()
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.uw_power_stones_balance, state.powerStones),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        if (state.equippedCount >= 3) {
            Text(
                stringResource(R.string.uw_equipped_full),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = StatusWarning,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                stringResource(R.string.uw_equipped_count, state.equippedCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Gray,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.weapons, key = { it.type.name }) { info ->
                UWCard(
                    info = info,
                    canEquipMore = state.equippedCount < 3,
                    onUnlock = { viewModel.unlock(info.type) },
                    onUpgrade = { path -> viewModel.upgrade(info.type, path) },
                    onToggleEquip = { viewModel.toggleEquip(info.type) },
                )
            }
        }
    }
}

@Composable
private fun UWCard(
    info: UWDisplayInfo,
    canEquipMore: Boolean,
    onUnlock: () -> Unit,
    onUpgrade: (UWPath) -> Unit,
    onToggleEquip: () -> Unit,
) {
    val haptics = rememberHaptics()
    val tier = uwRarityTier(info.type.unlockCost)
    // Locked UWs still show their rarity, but dimmed (spec D6) — the rarity affordances share the
    // same alpha so border + badge dim together with the locked container.
    val rarityAlpha = if (info.isUnlocked) 1f else 0.5f
    Card(
        modifier = Modifier.fillMaxWidth().rarityBorder(tier, alpha = rarityAlpha),
        colors =
            CardDefaults.cardColors(
                containerColor = if (info.isUnlocked) Color(0xFF2A2A3E) else Color(0xFF1A1A2E),
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RarityBadge(tier, stringResource(uwRarityLabelRes(tier)), alpha = rarityAlpha)
                        Text(
                            info.type.name.toDisplayName(),
                            fontWeight = FontWeight.Bold,
                            color = if (info.isUnlocked) Color.White else Color.Gray,
                        )
                    }
                    Text(
                        info.type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info.isUnlocked) Color.White.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.5f),
                    )
                }
                if (info.isEquipped) {
                    EquippedChip()
                }
            }
            if (!info.isUnlocked) {
                val unlockPulse = rememberPulse()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Button(
                        onClick = {
                            unlockPulse.trigger()
                            haptics.tap()
                            onUnlock()
                        },
                        enabled = info.canAffordUnlock,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5ACD)),
                        modifier = Modifier.pulseScale(unlockPulse),
                    ) {
                        Text(stringResource(R.string.uw_unlock_cost, info.type.unlockCost))
                    }
                }
            } else {
                // Per-path upgrade rows
                UWPath.ALL.forEach { path ->
                    val pathInfo = info.paths[path] ?: return@forEach
                    UWPathRow(
                        type = info.type,
                        path = path,
                        pathInfo = pathInfo,
                        onUpgrade = { onUpgrade(path) },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            onToggleEquip()
                        },
                        enabled = info.isEquipped || canEquipMore,
                    ) {
                        Text(
                            if (info.isEquipped) {
                                stringResource(
                                    R.string.uw_unequip,
                                )
                            } else {
                                stringResource(R.string.uw_equip)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UWPathRow(
    type: UltimateWeaponType,
    path: UWPath,
    pathInfo: UWPathDisplay,
    onUpgrade: () -> Unit,
) {
    val pulse = rememberPulse()
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(pathLabel(type, path)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Text(
                "L${pathInfo.level} → ${pathValueAtNext(type, path, pathInfo.level)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD4A843),
            )
        }
        if (pathInfo.isMaxed) {
            Text(
                "MAX",
                color = Color(0xFFD4A843),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        } else {
            Button(
                onClick = {
                    pulse.trigger()
                    haptics.tap()
                    onUpgrade()
                },
                enabled = pathInfo.canAfford,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5ACD)),
                modifier = Modifier.pulseScale(pulse),
            ) {
                Text(stringResource(R.string.uw_path_level_cost, pathInfo.level + 1, pathInfo.cost))
            }
        }
    }
}

/**
 * UI-side label for a UW's path (e.g. "Damage" / "Chain length" / "Cooldown"). Display
 * names are intentionally short so the row layout stays single-line on narrow screens.
 */
@StringRes
private fun pathLabel(
    type: UltimateWeaponType,
    path: UWPath,
): Int =
    when (path) {
        UWPath.DAMAGE -> {
            when (type) {
                UltimateWeaponType.CHRONO_FIELD -> R.string.uw_path_slow_factor
                UltimateWeaponType.GOLDEN_ZIGGURAT -> R.string.uw_path_cash_multiplier
                UltimateWeaponType.POISON_SWAMP -> R.string.uw_path_dot
                UltimateWeaponType.BLACK_HOLE -> R.string.uw_path_damage_dps
                else -> R.string.uw_path_damage
            }
        }

        UWPath.SECONDARY -> {
            when (type) {
                UltimateWeaponType.CHAIN_LIGHTNING -> R.string.uw_path_chain_length
                UltimateWeaponType.DEATH_WAVE -> R.string.uw_path_radius
                UltimateWeaponType.BLACK_HOLE -> R.string.uw_path_pull_strength
                UltimateWeaponType.CHRONO_FIELD -> R.string.uw_path_duration
                UltimateWeaponType.POISON_SWAMP -> R.string.uw_path_area
                UltimateWeaponType.GOLDEN_ZIGGURAT -> R.string.uw_path_damage_multiplier
            }
        }

        UWPath.COOLDOWN -> {
            R.string.uw_path_cooldown
        }
    }

/**
 * Format the path's value at the next level (after a hypothetical purchase). Returns a
 * UI-friendly string with the appropriate unit suffix per UW × path. Used by the
 * per-path row's "L0 → 666 dmg" preview line.
 */
@Composable
private fun pathValueAtNext(
    type: UltimateWeaponType,
    path: UWPath,
    currentLevel: Int,
): String {
    val next = (currentLevel + 1).coerceAtMost(UltimateWeaponType.MAX_PATH_LEVEL)
    val v = type.valueAtLevel(path, next)
    return when (path) {
        UWPath.COOLDOWN -> {
            stringResource(R.string.uw_value_seconds, v.toInt())
        }

        UWPath.DAMAGE -> {
            when (type) {
                UltimateWeaponType.CHRONO_FIELD -> stringResource(R.string.uw_value_percent, fmt0(v * 100))
                UltimateWeaponType.GOLDEN_ZIGGURAT -> stringResource(R.string.uw_value_multiplier, fmt1(v))
                UltimateWeaponType.POISON_SWAMP -> stringResource(R.string.uw_value_percent, fmt1(v * 100))
                UltimateWeaponType.BLACK_HOLE -> stringResource(R.string.uw_value_dps, v.toInt())
                else -> stringResource(R.string.uw_value_dmg, v.toInt())
            }
        }

        UWPath.SECONDARY -> {
            when (type) {
                UltimateWeaponType.CHAIN_LIGHTNING -> stringResource(R.string.uw_value_enemies, v.toInt())
                UltimateWeaponType.DEATH_WAVE -> stringResource(R.string.uw_value_percent_screen, fmt0(v * 100))
                UltimateWeaponType.BLACK_HOLE -> stringResource(R.string.uw_value_pxs, v.toInt())
                UltimateWeaponType.CHRONO_FIELD -> stringResource(R.string.uw_value_seconds_f, fmt0(v))
                UltimateWeaponType.POISON_SWAMP -> stringResource(R.string.uw_value_percent_area, fmt0(v * 100))
                UltimateWeaponType.GOLDEN_ZIGGURAT -> stringResource(R.string.uw_value_multiplier_dmg, fmt1(v))
            }
        }
    }
}

// keep ROOT-locale numeric formatting; percent/multiplier/seconds_f take a pre-formatted %1$s.
// Params are Double (UltimateWeaponType.valueAtLevel returns Double) so the exact value the old
// String.format(Locale.ROOT, "%.0f"/"%.1f", …) received is preserved — no Float narrowing.
private fun fmt0(x: Double) = String.format(java.util.Locale.ROOT, "%.0f", x)

private fun fmt1(x: Double) = String.format(java.util.Locale.ROOT, "%.1f", x)
