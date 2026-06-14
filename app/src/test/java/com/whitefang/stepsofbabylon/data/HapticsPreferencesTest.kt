package com.whitefang.stepsofbabylon.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HapticsPreferencesTest {

    @Test
    fun `defaults to enabled and round-trips`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = HapticsPreferences(context)

        assertTrue(prefs.isEnabled())   // default ON

        prefs.setEnabled(false)
        assertFalse(prefs.isEnabled())

        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
    }
}
