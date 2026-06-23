package com.whitefang.stepsofbabylon.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck
import kotlinx.coroutines.delay

/** Structured one-shot reward payload (#260). Formatted at the Compose boundary (localized + plural). */
sealed interface ClaimReward {
    data class Bundle(
        val gems: Int = 0,
        val powerStones: Int = 0,
        val steps: Int = 0,
        val cosmeticNames: List<String> = emptyList(),
        val cards: Int = 0,
    ) : ClaimReward

    /** Pre-localized fixed message (e.g. "All supplies claimed!"). */
    data class Message(
        @StringRes val res: Int,
    ) : ClaimReward

    data object Generic : ClaimReward
}

data class ClaimCelebrationEvent(
    val reward: ClaimReward,
)

/**
 * The joined reward parts WITHOUT the "claimed!" verb, e.g. "+5 Gems +2 Power Stones" — used by BOTH
 * the milestone row (Task 9) and [formatClaimReward]. Single factored helper (no removeSuffix hack).
 */
@Composable
fun formatRewardParts(bundle: ClaimReward.Bundle): String {
    val parts =
        buildList {
            if (bundle.gems > 0) add(pluralStringResource(R.plurals.reward_gems, bundle.gems, bundle.gems))
            if (bundle.powerStones >
                0
            ) {
                add(pluralStringResource(R.plurals.reward_power_stones, bundle.powerStones, bundle.powerStones))
            }
            if (bundle.steps > 0) add(pluralStringResource(R.plurals.reward_steps, bundle.steps, bundle.steps))
            if (bundle.cards > 0) add(pluralStringResource(R.plurals.card_copies, bundle.cards, bundle.cards))
            addAll(bundle.cosmeticNames)
        }
    return parts.joinToString(stringResource(R.string.reward_join))
}

/** Full celebration text. Returns "" for null/empty (exit-safe). */
@Composable
fun formatClaimReward(reward: ClaimReward?): String =
    when (reward) {
        null -> {
            ""
        }

        is ClaimReward.Generic -> {
            stringResource(R.string.reward_generic)
        }

        is ClaimReward.Message -> {
            stringResource(reward.res)
        }

        is ClaimReward.Bundle -> {
            val parts = formatRewardParts(reward)
            if (parts.isEmpty()) {
                stringResource(R.string.reward_generic)
            } else {
                stringResource(R.string.reward_claimed, parts)
            }
        }
    }

/**
 * Brief one-shot reward chip shown when a claim succeeds. Scales+fades in, fires a success haptic
 * once on appearance, auto-dismisses after ~1.4s, then calls [onConsumed] to clear the VM event.
 * Under reduced-motion it appears/disappears instantly (no scale/fade) but the haptic still fires.
 */
@Composable
fun ClaimCelebration(
    event: ClaimCelebrationEvent?,
    onConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(context) }
    val haptics = rememberHaptics()

    LaunchedEffect(event) {
        if (event != null) {
            haptics.success()
            delay(1400)
            onConsumed()
        }
    }

    Box(Modifier.fillMaxSize().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = event != null,
            enter = if (reducedMotion) fadeIn(snapSpec()) else scaleIn() + fadeIn(),
            exit = if (reducedMotion) fadeOut(snapSpec()) else fadeOut(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    formatClaimReward(event?.reward),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun snapSpec() =
    androidx.compose.animation.core
        .snap<Float>()
