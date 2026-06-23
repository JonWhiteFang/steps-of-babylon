package com.whitefang.stepsofbabylon.data.onboarding

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingPreferencesTest {
    @Test
    fun `defaults to not completed`() {
        val prefs = OnboardingPreferences(RuntimeEnvironment.getApplication())
        assertFalse(prefs.hasCompletedOnboarding())
    }

    @Test
    fun `setCompleted then reset round-trip`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = OnboardingPreferences(context)

        prefs.setCompleted()
        assertTrue(prefs.hasCompletedOnboarding())

        prefs.reset()
        assertFalse(prefs.hasCompletedOnboarding())
    }
}
