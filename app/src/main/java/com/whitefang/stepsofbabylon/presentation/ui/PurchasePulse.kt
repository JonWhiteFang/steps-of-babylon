package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck

/**
 * A one-shot "a spend just happened" scale pulse (Bundle C, #162). Extracted from the inline
 * UpgradeCard pulse and enlarged 1.05× → 1.12× (D9). graphicsLayer scale → no layout reflow.
 * Under reduced-motion the spec uses snap() (instant jump, effectively no pulse).
 *
 * Usage:
 *   val pulse = rememberPulse()
 *   Button(onClick = { pulse.trigger(); haptics.tap(); onClick() }, ...) { ... }
 *   ...Modifier.pulseScale(pulse)
 */
class PulseState internal constructor(
    private val scaleState: State<Float>,
    private val setActive: (Boolean) -> Unit,
) {
    internal val scale: Float get() = scaleState.value

    fun trigger() = setActive(true)
}

private const val PULSE_TARGET = 1.12f

@Composable
fun rememberPulse(): PulseState {
    val context = LocalContext.current
    val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(context) }
    var active by remember { mutableStateOf(false) }
    val scale =
        animateFloatAsState(
            targetValue = if (active) PULSE_TARGET else 1f,
            animationSpec = if (reducedMotion) snap() else tween(100),
            label = "purchasePulse",
        )
    LaunchedEffect(active) {
        if (active) {
            kotlinx.coroutines.delay(100)
            active = false
        }
    }
    return remember { PulseState(scale) { active = it } }
}

fun Modifier.pulseScale(pulse: PulseState): Modifier = this.graphicsLayer(scaleX = pulse.scale, scaleY = pulse.scale)
