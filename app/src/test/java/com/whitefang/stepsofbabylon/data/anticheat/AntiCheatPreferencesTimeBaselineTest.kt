package com.whitefang.stepsofbabylon.data.anticheat

import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AntiCheatPreferencesTimeBaselineTest {

    @Test
    fun `readTimeBaseline is null before any write`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        assertNull(prefs.readTimeBaseline())
    }

    @Test
    fun `writeTimeBaseline then readTimeBaseline round-trips all four slots`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        prefs.writeTimeBaseline(
            TimeBaseline(lastElapsedRealtime = 1234, lastWallClock = 5678, maxWallClockSeen = 9999, trustedWallClock = 7777),
        )
        val read = prefs.readTimeBaseline()
        assertEquals(TimeBaseline(1234, 5678, 9999, 7777), read)
    }

    @Test
    fun `currentTimeReading returns non-negative monotonic and wall values`() {
        val prefs = AntiCheatPreferences(RuntimeEnvironment.getApplication())
        val r = prefs.currentTimeReading()
        // Robolectric supplies deterministic clocks; both reads are present and non-negative.
        org.junit.Assert.assertTrue(r.elapsedRealtime >= 0)
        org.junit.Assert.assertTrue(r.wallClock >= 0)
    }
}
