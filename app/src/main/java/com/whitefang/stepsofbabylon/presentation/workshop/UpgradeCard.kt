package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.theme.BronzeSurface
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.Ivory
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess
import com.whitefang.stepsofbabylon.presentation.ui.toDisplayName

@Composable
fun UpgradeCard(
    info: UpgradeDisplayInfo,
    onClick: () -> Unit,
) {
    // Whole-card dim is reserved for the MAXED state (paired with the Gold tint below). An
    // *unaffordable* card stays fully opaque so its title/description remain readable — only the
    // cost/stat readout dims (see `valueAlpha`).
    val cardAlpha = if (info.isMaxed) 0.85f else 1f
    val valueAlpha =
        when {
            info.isMaxed -> 1f
            info.canAfford -> 1f
            else -> 0.55f
        }

    val pulse = rememberPulse()
    val haptics = rememberHaptics()

    Card(
        onClick = {
            // Guarded redundantly with `enabled` below (#154): at cap/unaffordable the Card is disabled.
            if (info.canAfford && !info.isMaxed) {
                pulse.trigger()
                haptics.tap()
                onClick()
            }
        },
        enabled = info.canAfford && !info.isMaxed,
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha).pulseScale(pulse),
        colors =
            if (info.isMaxed) {
                CardDefaults.cardColors(
                    containerColor = Gold.copy(alpha = 0.15f),
                    disabledContainerColor = Gold.copy(alpha = 0.15f),
                )
            } else {
                CardDefaults.cardColors(
                    // #154: pin disabledContainerColor == containerColor so a disabled (unaffordable) card
                    // keeps the normal surface instead of swapping to Material's default disabled background.
                    disabledContainerColor = CardDefaults.cardColors().containerColor,
                )
            },
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            // #29: "★ BEST BUY" chip on the single highest-value upgrade. Desaturated (opaque fill,
            // not alpha) "save up" variant when the Best Buy isn't currently affordable. Static — NOT a PurchasePulse.
            info.value?.let { value ->
                if (value.isBestBuy) {
                    BestBuyChip(affordable = value.bestBuyAffordable)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = info.type.name.toDisplayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = info.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // #29: Now → Next preview (workshop dimension). Shown when there's a next level.
                    info.nowNext?.let { readout ->
                        readout.next?.let { next ->
                            Text(
                                text = "${readout.current} → $next",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.alpha(valueAlpha)) {
                    Text(
                        text =
                            if (info.isMaxed) {
                                stringResource(
                                    R.string.upgrade_max,
                                )
                            } else {
                                stringResource(R.string.upgrade_level, info.level)
                            },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (info.isMaxed) Gold else MaterialTheme.colorScheme.onSurface,
                    )
                    if (info.statValue.isNotEmpty()) {
                        Text(text = info.statValue, style = MaterialTheme.typography.labelSmall, color = Gold)
                    }
                    if (!info.isMaxed) {
                        Text(
                            text = stringResource(R.string.upgrade_cost_steps, info.cost),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (info.canAfford) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            // #29: combat-power value bar + label. Rendered only for combat upgrades (value != null).
            info.value?.let { value ->
                ValueBar(fraction = value.barFraction)
                Text(
                    text = formatPowerPerKStepsLabel(value.percentPerKSteps),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusSuccess,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BestBuyChip(affordable: Boolean) {
    // Affordable: solid Gold background with dark (DeepBronze = colorScheme.surface) text — ~4.2:1,
    // matches the theme's onPrimary=DeepBronze rationale. Greyed "save up" state: a desaturated SOLID
    // fill (NOT Gold@0.4f over the dark card, which gives illegible ~1.9:1 dark-on-dark — review F1)
    // with light Ivory text for a legible >4:1 contrast. Keep the fill opaque so the greyed chip never
    // composites to dark-on-dark.
    val bg = if (affordable) Gold else BronzeSurface
    val fg = if (affordable) MaterialTheme.colorScheme.surface else Ivory
    Box(
        modifier =
            Modifier
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text =
                if (affordable) {
                    stringResource(
                        R.string.upgrade_best_buy,
                    )
                } else {
                    stringResource(R.string.upgrade_best_buy_save_up)
                },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun ValueBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(clamped)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(StatusSuccess),
        )
    }
}
