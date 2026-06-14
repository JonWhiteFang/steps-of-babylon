package com.whitefang.stepsofbabylon.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local on/off flag for haptic feedback (Bundle C, #162). Default ON. Mirrors
 * [SoundPreferences] exactly: a @Singleton SharedPreferences wrapper, no Hilt module needed
 * (constructor injection auto-provides it). The Settings ViewModel writes it; the
 * `rememberHaptics()` composable reads it (both resolve the same process-cached
 * "haptics_prefs" instance, so a toggle takes effect on the next tap with no restart).
 */
@Singleton
class HapticsPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("haptics_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean("enabled", enabled).apply()
}
