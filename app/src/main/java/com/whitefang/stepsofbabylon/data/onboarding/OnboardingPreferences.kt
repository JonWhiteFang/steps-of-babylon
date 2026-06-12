package com.whitefang.stepsofbabylon.data.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local first-launch onboarding state. Deliberately NOT in Room: this is a
 * UI/device preference, not game state, so it must not sync if cloud save (#36) ever
 * lands, and a reinstall correctly re-shows the tutorial. Mirrors the structure of
 * MusicPreferences / AntiCheatPreferences — @Singleton, constructor-injected, no Hilt module.
 */
@Singleton
class OnboardingPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)
    fun setCompleted() { prefs.edit().putBoolean(KEY_COMPLETED, true).apply() }
    fun reset() { prefs.edit().putBoolean(KEY_COMPLETED, false).apply() }

    private companion object {
        const val KEY_COMPLETED = "has_completed_onboarding"
    }
}
