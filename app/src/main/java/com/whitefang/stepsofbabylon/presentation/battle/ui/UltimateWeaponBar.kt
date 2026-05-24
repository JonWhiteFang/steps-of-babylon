package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.presentation.battle.UWSlotInfo

/**
 * R4-06: passive cooldown indicator for equipped Ultimate Weapons. Pre-R4-06 this was a
 * tap-to-activate button row driving [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel.activateUW];
 * R4-06 moves activation into the engine ([GameEngine.updateUWs] auto-trigger when
 * cooldown reaches 0 AND enemies are present), so the bar is now a status display only.
 *
 * Each slot shows:
 * - The UW's first 2 letters when ready (cooldown reached 0).
 * - The remaining cooldown in whole seconds when on cooldown.
 * - A bottom progress fill indicating cooldown progress (visual reinforcement of the
 *   numeric countdown, fills as the UW gets closer to firing).
 *
 * No clickable, no `onActivate` callback \u2014 the engine is the sole activator.
 */
@Composable
fun UltimateWeaponBar(slots: List<UWSlotInfo>) {
    if (slots.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { slot ->
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (slot.isReady) Color(0xFF6A5ACD) else Color(0xFF2A2A3E))
                    .semantics {
                        contentDescription = if (slot.isReady) {
                            "${slot.typeName} ready"
                        } else {
                            "${slot.typeName} on cooldown, ${slot.cooldownRemaining.toInt()} seconds remaining"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (slot.isReady) {
                    Text(slot.typeName.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Text("${slot.cooldownRemaining.toInt()}", color = Color.Gray, fontSize = 11.sp)
                }
                // Bottom-anchored cooldown progress fill. `cooldownTotal` is `0f` only
                // for fresh-engine sentinel state; guard against div-by-zero.
                val progress = if (slot.cooldownTotal > 0f) {
                    1f - (slot.cooldownRemaining / slot.cooldownTotal).coerceIn(0f, 1f)
                } else {
                    1f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progress * 0.06f) // tiny strip at the bottom
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFFD4A843).copy(alpha = if (slot.isReady) 0.0f else 0.6f)),
                )
            }
        }
    }
}
