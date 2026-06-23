package com.whitefang.stepsofbabylon.data.sensor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepRateLimiterTest {
    @Test
    fun `under cap gives full credit`() {
        val limiter = StepRateLimiter()
        assertEquals(100L, limiter.credit(100, 1000))
    }

    @Test
    fun `burst window allows 250`() {
        val limiter = StepRateLimiter()
        assertEquals(250L, limiter.credit(250, 1000))
    }

    @Test
    fun `burst cap enforced within burst window`() {
        val limiter = StepRateLimiter()
        assertEquals(250L, limiter.credit(250, 1000))
        assertEquals(0L, limiter.credit(1, 1001))
    }

    @Test
    fun `normal cap is 200 after burst window expires`() {
        val limiter = StepRateLimiter()
        // Keep window alive for 5+ minutes so firstEntryMs stays at origin
        var t = 0L
        repeat(11) {
            limiter.credit(1, t)
            t += 30_000L
        }
        // t = 330_000. firstEntryMs = 0. 330000 - 0 > 300000 → NORMAL mode.
        // Window has ~2 entries from recent 60s (steps already counted).
        // Request 200 — should be capped to (200 - windowTotal)
        val credited = limiter.credit(200, t)
        assertTrue(credited in 195..200, "Should credit close to 200 (minus window residue), got $credited")
        // Now at cap — no more allowed (same timestamp so no eviction)
        assertEquals(0L, limiter.credit(1, t))
    }

    @Test
    fun `window expiry allows new credits`() {
        val limiter = StepRateLimiter()
        assertEquals(250L, limiter.credit(250, 0))
        assertEquals(0L, limiter.credit(1, 100))
        // After 60s, window empties → all entries evicted → fresh start
        val credited = limiter.credit(100, 60_001)
        assertEquals(100L, credited)
    }

    @Test
    fun `zero delta returns 0`() {
        assertEquals(0L, StepRateLimiter().credit(0, 1000))
    }

    @Test
    fun `negative delta returns 0`() {
        assertEquals(0L, StepRateLimiter().credit(-5, 1000))
    }
}
