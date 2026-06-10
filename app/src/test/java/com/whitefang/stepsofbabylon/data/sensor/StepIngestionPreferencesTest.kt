package com.whitefang.stepsofbabylon.data.sensor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for StepIngestionPreferences using a fake SharedPreferences-like in-memory store.
 * Since we can't use real SharedPreferences in JVM tests, we test the logic via
 * FakeStepIngestionPreferences which mirrors the real implementation's behavior.
 */
class StepIngestionPreferencesTest {

    private lateinit var prefs: FakeStepIngestionPreferences

    @BeforeEach
    fun setup() {
        prefs = FakeStepIngestionPreferences()
    }

    @Test
    fun `heartbeat defaults to 0`() {
        assertEquals(0L, prefs.getServiceHeartbeat())
    }

    @Test
    fun `heartbeat write and read`() {
        prefs.updateServiceHeartbeat(1000L)
        assertEquals(1000L, prefs.getServiceHeartbeat())
    }

    @Test
    fun `heartbeat overwrites previous value`() {
        prefs.updateServiceHeartbeat(1000L)
        prefs.updateServiceHeartbeat(2000L)
        assertEquals(2000L, prefs.getServiceHeartbeat())
    }

    @Test
    fun `isServiceAlive true when heartbeat is fresh`() {
        prefs.updateServiceHeartbeat(10_000L)
        assertTrue(prefs.isServiceAlive(10_500L)) // 500ms ago
    }

    @Test
    fun `isServiceAlive false when heartbeat is stale`() {
        prefs.updateServiceHeartbeat(10_000L)
        assertFalse(prefs.isServiceAlive(10_000L + 2 * 60 * 1000L)) // exactly 2 min
    }

    @Test
    fun `isServiceAlive false when no heartbeat written`() {
        assertFalse(prefs.isServiceAlive(System.currentTimeMillis()))
    }

    @Test
    fun `day start counter returns null for unset date`() {
        assertNull(prefs.getCounterAtDayStart("2026-03-11"))
    }

    @Test
    fun `day start counter write and read`() {
        prefs.setCounterAtDayStart("2026-03-11", 50000L)
        assertEquals(50000L, prefs.getCounterAtDayStart("2026-03-11"))
    }

    @Test
    fun `day start counter returns null for different date`() {
        prefs.setCounterAtDayStart("2026-03-11", 50000L)
        assertNull(prefs.getCounterAtDayStart("2026-03-12"))
    }

    @Test
    fun `day start counter overwrite works`() {
        prefs.setCounterAtDayStart("2026-03-11", 50000L)
        prefs.setCounterAtDayStart("2026-03-11", 60000L)
        assertEquals(60000L, prefs.getCounterAtDayStart("2026-03-11"))
    }

    @Test
    fun `day rollover clears previous day start`() {
        prefs.setCounterAtDayStart("2026-03-11", 50000L)
        prefs.setCounterAtDayStart("2026-03-12", 70000L)
        assertNull(prefs.getCounterAtDayStart("2026-03-11"))
        assertEquals(70000L, prefs.getCounterAtDayStart("2026-03-12"))
    }
}

/**
 * In-memory fake that mirrors StepIngestionPreferences behavior for JVM testing.
 */
class FakeStepIngestionPreferences {
    private var heartbeat: Long = 0L
    private var dayStartDate: String? = null
    private var dayStartCounter: Long = -1L
    private var sensorStepsAtDayStart: Long = 0L

    fun updateServiceHeartbeat(timestampMs: Long) { heartbeat = timestampMs }
    fun getServiceHeartbeat(): Long = heartbeat
    fun isServiceAlive(nowMs: Long): Boolean = nowMs - heartbeat < 2 * 60 * 1000L

    // #123: mirrors the real prefs — sensorStepsAtDayStart offset defaults to 0.
    fun setCounterAtDayStart(date: String, counterValue: Long, sensorStepsAtDayStart: Long = 0L) {
        dayStartDate = date
        dayStartCounter = counterValue
        this.sensorStepsAtDayStart = sensorStepsAtDayStart
    }

    fun getCounterAtDayStart(date: String): Long? {
        if (dayStartDate != date) return null
        return dayStartCounter.takeIf { it >= 0 }
    }

    fun getSensorStepsAtDayStart(date: String): Long {
        if (dayStartDate != date) return 0L
        return sensorStepsAtDayStart
    }
}
