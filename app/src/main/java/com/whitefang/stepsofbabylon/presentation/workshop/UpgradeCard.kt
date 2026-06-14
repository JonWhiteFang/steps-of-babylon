package com.whitefang.stepsofbabylon.presentation.workshop

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold

@Composable
fun UpgradeCard(info: UpgradeDisplayInfo, onClick: () -> Unit) {
    // Whole-card dim is reserved for the MAXED state (paired with the Gold tint below). An
    // *unaffordable* card now stays fully opaque so its title/description remain readable — only
    // the cost/stat readout dims (see `valueAlpha`). Previously the entire card dropped to 0.5f
    // when unaffordable, which made the upgrade NAME barely legible at a 0-Step balance.
    val cardAlpha = if (info.isMaxed) 0.85f else 1f
    val valueAlpha = when {
        info.isMaxed -> 1f
        info.canAfford -> 1f
        else -> 0.55f
    }

    val pulse = rememberPulse()
    val haptics = rememberHaptics()

    Card(
        onClick = {
            // Guarded redundantly with `enabled` below: at cap (or when unaffordable) the Card is
            // disabled so this never fires, but keep the predicate so a future `enabled` change
            // can't silently re-open the spend path. (#154)
            if (info.canAfford && !info.isMaxed) {
                pulse.trigger()
                haptics.tap()
                onClick()
            }
        },
        // #154: a maxed (or unaffordable) upgrade must be genuinely un-clickable — not just a no-op
        // inside onClick. Disabling the Card also removes the ripple/press feedback so it *looks*
        // disabled, matching the in-round menu / UW / Cards surfaces (consistent "can't buy more" UX).
        enabled = info.canAfford && !info.isMaxed,
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha).pulseScale(pulse),
        // The dim treatment is owned by `alpha` above, so pin disabledContainerColor == containerColor:
        // otherwise a disabled (maxed/unaffordable) Card would swap to Material3's theme-default
        // disabled background and lose the Gold "MAX" tint / normal surface. (#154)
        colors = if (info.isMaxed) {
            CardDefaults.cardColors(
                containerColor = Gold.copy(alpha = 0.15f),
                disabledContainerColor = Gold.copy(alpha = 0.15f),
            )
        } else {
            CardDefaults.cardColors(
                disabledContainerColor = CardDefaults.cardColors().containerColor,
            )
        },
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
            // The level/stat/cost readout carries the affordability dim, so the upgrade name +
            // description (left column) stay fully legible even when the player can't afford it.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.alpha(valueAlpha)) {
                Text(
                    text = if (info.isMaxed) stringResource(R.string.upgrade_max) else stringResource(R.string.upgrade_level, info.level),
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
                        text = stringResource(R.string.upgrade_cost_steps, info.cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (info.canAfford) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
