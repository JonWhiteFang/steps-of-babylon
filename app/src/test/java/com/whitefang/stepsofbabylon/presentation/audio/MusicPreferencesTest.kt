package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MusicPreferencesTest {
    @Test
    fun `round-trip mute and volume`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = MusicPreferences(context)

        assertFalse(prefs.isMuted())
        assertEquals(0.5f, prefs.getVolume(), 0.01f)

        prefs.setMuted(true)
        prefs.setVolume(0.8f)

        assertTrue(prefs.isMuted())
        assertEquals(0.8f, prefs.getVolume(), 0.01f)
    }
}
