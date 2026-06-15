package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics

/**
 * Layout constants for the battle [BattleControlRail] (#171). Holds the rail's fixed on-screen
 * footprint. (The upgrade menu now spans the full width and clears the rail VERTICALLY via
 * `IN_ROUND_MENU_HEIGHT`, so there is no longer a rail↔menu horizontal-padding coupling to single-source
 * here — that earlier `GAP`/`menuStartPadding()` derivation was retired when the menu went full-width.)
 */
object BattleControlRailDefaults {
    /**
     * Fixed rail footprint. Sized to hold the widest control (4x / pause / upgrade) + pill padding.
     * Cosmetic — tune on-device.
     */
    val WIDTH: Dp = 80.dp
}

/**
 * #171: vertical control rail for the battle screen — speed (1x/2x/4x), pause, and the upgrade-menu
 * toggle, stacked against the left edge. Replaces the old bottom-center Row that overlapped the UW
 * cooldown bar and the upgrade menu. Pure presentational unit: takes state + callbacks, holds no VM
 * reference. Button bodies are copied verbatim from the old Row; only the parent container is a Column.
 *
 * Modifier-order invariant: width → verticalScroll → background → padding. `background` MUST sit AFTER
 * `verticalScroll` so the pill wraps the visible viewport (and only the buttons), not the full
 * scrollable content extent — otherwise on a short/landscape viewport the rounded pill mis-renders
 * (same intent as the old Row comment at BattleScreen.kt:175-176). The caller supplies `align` + the
 * Start window-inset via [modifier].
 */
@Composable
fun BattleControlRail(
    speedMultiplier: Float,
    isPaused: Boolean,
    showUpgradeMenu: Boolean,
    onSetSpeed: (Float) -> Unit,
    onTogglePause: () -> Unit,
    onToggleUpgradeMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .width(BattleControlRailDefaults.WIDTH)
            .verticalScroll(rememberScrollState())
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(1f, 2f, 4f).forEach { speed ->
            val desc = stringResource(R.string.battle_cd_speed, speed.toInt())
            val label = stringResource(R.string.battle_speed_label, speed.toInt())
            if (speedMultiplier == speed) {
                Button(onClick = {}, modifier = Modifier.semantics { contentDescription = desc }) { Text(label) }
            } else {
                FilledTonalButton(
                    onClick = { onSetSpeed(speed) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = desc },
                ) { Text(label, color = Color.White) }
            }
        }
        val pauseDesc = stringResource(if (isPaused) R.string.action_resume else R.string.battle_cd_pause)
        FilledTonalButton(
            onClick = { haptics.tap(); onTogglePause() },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isPaused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.semantics { contentDescription = pauseDesc },
        ) { Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = Color.White) }

        val upgradesDesc = stringResource(R.string.battle_cd_upgrades)
        FilledTonalButton(
            onClick = { onToggleUpgradeMenu() },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (showUpgradeMenu) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.semantics { contentDescription = upgradesDesc },
        ) { Icon(Icons.Filled.Upgrade, contentDescription = null, tint = Color.White) }
    }
}
