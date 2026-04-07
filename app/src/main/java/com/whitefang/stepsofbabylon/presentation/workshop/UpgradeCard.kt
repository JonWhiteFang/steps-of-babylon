package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import kotlinx.coroutines.delay

@Composable
fun UpgradeCard(info: UpgradeDisplayInfo, onClick: () -> Unit) {
    val alpha = when {
        info.isMaxed -> 0.7f
        info.canAfford -> 1f
        else -> 0.5f
    }

    val context = LocalContext.current
    val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(context) }
    var pulseActive by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulseActive) 1.05f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(100),
        label = "purchasePulse",
    )

    LaunchedEffect(pulseActive) {
        if (pulseActive) { delay(100); pulseActive = false }
    }

    Card(
        onClick = {
            if (info.canAfford && !info.isMaxed) {
                pulseActive = true
                onClick()
            }
        },
        modifier = Modifier.fillMaxWidth().alpha(alpha).graphicsLayer(scaleX = scale, scaleY = scale),
        colors = if (info.isMaxed) CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.15f))
                 else CardDefaults.cardColors(),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = info.type.name.replace('_', ' '),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (info.isMaxed) "MAX" else "Lv. ${info.level}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (info.isMaxed) Gold else MaterialTheme.colorScheme.onSurface,
                )
                if (info.statValue.isNotEmpty()) {
                    Text(
                        text = info.statValue,
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold,
                    )
                }
                if (!info.isMaxed) {
                    Text(
                        text = "${info.cost} Steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (info.canAfford) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
