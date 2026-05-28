package com.whitefang.stepsofbabylon.presentation.audio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the frequency-aware SHOOT throttle algorithm in [SoundManager].
 * These are pure-logic tests validating the throttle formula without needing
 * a real SoundPool — we test the coerceIn math directly.
 */
class SoundManagerThrottleTest {

    /** Throttle formula extracted for testability: (interval / 3).coerceIn(30, 100) */
    private fun computeThrottle(expectedIntervalMs: Long): Long =
        (expectedIntervalMs / 3L).coerceIn(30L, 100L)

    @Test
    fun `default 100ms interval produces 33ms throttle`() {
        // 100 / 3 = 33, within [30, 100]
        assertEquals(33L, computeThrottle(100L))
    }

    @Test
    fun `baseline 1000ms interval (1 attack per sec) caps at 100ms`() {
        // 1000 / 3 = 333, capped at 100
        assertEquals(100L, computeThrottle(1000L))
    }

    @Test
    fun `fast 200ms interval (5 attacks per sec) produces 66ms throttle`() {
        // 200 / 3 = 66, within [30, 100]
        assertEquals(66L, computeThrottle(200L))
    }

    @Test
    fun `very fast 60ms interval floors at 30ms`() {
        // 60 / 3 = 20, floored at 30
        assertEquals(30L, computeThrottle(60L))
    }

    @Test
    fun `zero interval floors at 30ms`() {
        // 0 / 3 = 0, floored at 30
        assertEquals(30L, computeThrottle(0L))
    }
}
