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
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.ui.descriptionRes
import com.whitefang.stepsofbabylon.presentation.ui.nameRes
import com.whitefang.stepsofbabylon.presentation.ui.pulseScale
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse
import com.whitefang.stepsofbabylon.presentation.ui.theme.BronzeSurface
import com.whitefang.stepsofbabylon.presentation.ui.theme.Gold
import com.whitefang.stepsofbabylon.presentation.ui.theme.Ivory
import com.whitefang.stepsofbabylon.presentation.ui.theme.StatusSuccess

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
                        text = stringResource(info.type.nameRes()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(info.type.descriptionRes()),
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
                    // i18n #34 phase 3: resolve the per-type stat-value unit label via @StringRes here at
                    // the render boundary (statValueLabel is @Composable). Resolve first, then guard on empty
                    // (the `else -> ""` types render no readout, same as the old statValue.isNotEmpty() gate).
                    val statLabel = statValueLabel(info.type, info.stats)
                    if (statLabel.isNotEmpty()) {
                        Text(text = statLabel, style = MaterialTheme.typography.labelSmall, color = Gold)
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

/**
 * i18n #34 phase 3 (D-workshop): resolves the per-UpgradeType stat-value unit label from @StringRes,
 * mirroring the UW `pathValueAtNext` extraction (PR3c). Keeps ROOT-locale numeric formatting so the
 * exact value the old `statValueFor`'s `String.format(Locale.ROOT, "%.0f"/"%.1f"/"%.2f", …)` received is
 * reproduced byte-for-byte — only the unit WORD/suffix moved to the translatable resource. The `*100`
 * multipliers and the f0/f1/f2 decimal choice per branch MUST match the old `statValueFor`.
 */
@Composable
private fun statValueLabel(
    type: UpgradeType,
    s: ResolvedStats,
): String {
    // Params are Number (ResolvedStats mixes Double and Float — e.g. range/knockbackForce are Float)
    // so the exact value the old statValueFor's `String.format(Locale.ROOT, …, value: Number)` received
    // is preserved with no narrowing — byte-identical formatting.
    fun f1(v: Number) = String.format(java.util.Locale.ROOT, "%.1f", v)

    fun f2(v: Number) = String.format(java.util.Locale.ROOT, "%.2f", v)

    fun f0(v: Number) = String.format(java.util.Locale.ROOT, "%.0f", v)
    return when (type) {
        UpgradeType.DAMAGE -> {
            stringResource(R.string.stat_dmg, f1(s.damage))
        }

        UpgradeType.ATTACK_SPEED -> {
            stringResource(R.string.stat_per_sec, f2(s.attackSpeed))
        }

        UpgradeType.CRITICAL_CHANCE -> {
            stringResource(R.string.stat_percent, f1(s.critChance * 100))
        }

        UpgradeType.CRITICAL_FACTOR -> {
            stringResource(R.string.stat_multiplier, f1(s.critMultiplier))
        }

        UpgradeType.RANGE -> {
            stringResource(R.string.stat_range, f0(s.range))
        }

        UpgradeType.HEALTH -> {
            stringResource(R.string.stat_hp, f0(s.maxHealth))
        }

        UpgradeType.HEALTH_REGEN -> {
            stringResource(R.string.stat_per_sec, f1(s.healthRegen))
        }

        UpgradeType.DEFENSE_PERCENT -> {
            stringResource(R.string.stat_percent, f1(s.defensePercent * 100))
        }

        UpgradeType.DEFENSE_ABSOLUTE -> {
            stringResource(R.string.stat_block, f0(s.defenseAbsolute))
        }

        UpgradeType.KNOCKBACK -> {
            stringResource(R.string.stat_force, f1(s.knockbackForce))
        }

        UpgradeType.THORN_DAMAGE -> {
            stringResource(R.string.stat_percent, f0(s.thornPercent * 100))
        }

        UpgradeType.LIFESTEAL -> {
            stringResource(R.string.stat_percent, f1(s.lifestealPercent * 100))
        }

        UpgradeType.DAMAGE_PER_METER -> {
            stringResource(R.string.stat_percent_per_m, f0(s.damagePerMeterBonus * 100))
        }

        UpgradeType.DEATH_DEFY -> {
            stringResource(R.string.stat_percent, f0(s.deathDefyChance * 100))
        }

        UpgradeType.MULTISHOT -> {
            stringResource(R.string.stat_targets, s.multishotTargets)
        }

        UpgradeType.BOUNCE_SHOT -> {
            stringResource(R.string.stat_bounces, s.bounceCount)
        }

        UpgradeType.ORBS -> {
            stringResource(R.string.stat_orbs, s.orbCount)
        }

        else -> {
            ""
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
