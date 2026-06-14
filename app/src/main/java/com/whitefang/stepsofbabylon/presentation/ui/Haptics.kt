package com.whitefang.stepsofbabylon.presentation.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.whitefang.stepsofbabylon.data.HapticsPreferences

/**
 * Tactile feedback helper (Bundle C, #162). Wraps [View.performHapticFeedback] and gates every
 * pulse on [HapticsPreferences.isEnabled] read at *call* time, so toggling "Haptics" in Settings
 * takes effect on the next tap without recomposition.
 *
 * Uses the no-flag overload: it honours the host view's haptic-enabled state (the Compose default
 * is enabled) and VIRTUAL_KEY additionally honours the system touch-haptic setting — intended.
 * No VIBRATE permission required.
 */
class Haptics(private val view: View, private val prefs: HapticsPreferences) {
    /** Light tick — purchase / equip / battle-start / pause taps. */
    fun tap() {
        if (prefs.isEnabled()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Heavier confirm — claim celebrations + the Post-Round reward sting. */
    fun success() {
        if (prefs.isEnabled()) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) { Haptics(view, HapticsPreferences(context)) }
}
