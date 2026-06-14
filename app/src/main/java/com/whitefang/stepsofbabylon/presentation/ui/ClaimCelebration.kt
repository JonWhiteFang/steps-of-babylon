package com.whitefang.stepsofbabylon.presentation.ui

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
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck
import kotlinx.coroutines.delay

/** One-shot claim/reward payload (Bundle C, #162). A flat, pre-formatted label built in the VM. */
data class ClaimCelebrationEvent(val label: String)

/**
 * Brief one-shot reward chip shown when a claim succeeds. Scales+fades in, fires a success haptic
 * once on appearance, auto-dismisses after ~1.4s, then calls [onConsumed] to clear the VM event.
 * Under reduced-motion it appears/disappears instantly (no scale/fade) but the haptic still fires.
 */
@Composable
fun ClaimCelebration(event: ClaimCelebrationEvent?, onConsumed: () -> Unit) {
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
                    event?.label ?: "",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun snapSpec() = androidx.compose.animation.core.snap<Float>()
