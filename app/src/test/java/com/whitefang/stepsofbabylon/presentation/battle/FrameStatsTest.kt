package com.whitefang.stepsofbabylon.presentation.battle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #384: pure-JVM tests for [FrameStats] — the loop-thread-confined frame-timing accumulator behind the
 * DEBUG overlay. No Android; verifies min/avg/max/dropped, the UPS estimate, windowing, and the reset.
 */
class FrameStatsTest {
    private val tickNs = 16_666_667L // ~60 UPS target

    private fun ms(nanos: Long) = nanos / 1_000_000.0

    @Test
    fun `no snapshot until the first window fills`() {
        val stats = FrameStats(windowFrames = 3)
        stats.record(tickNs, tickNs)
        stats.record(tickNs, tickNs)
        assertNull(stats.snapshot(), "snapshot must be null before windowFrames frames recorded")
    }

    @Test
    fun `min avg max computed over the window`() {
        val stats = FrameStats(windowFrames = 3)
        stats.record(10_000_000L, tickNs) // 10ms
        stats.record(20_000_000L, tickNs) // 20ms
        stats.record(30_000_000L, tickNs) // 30ms → window closes
        val snap = stats.snapshot()!!
        assertEquals(ms(10_000_000L), snap.minMs, 1e-9)
        assertEquals(ms(20_000_000L), snap.avgMs, 1e-9) // (10+20+30)/3
        assertEquals(ms(30_000_000L), snap.maxMs, 1e-9)
    }

    @Test
    fun `dropped counts frames slower than the tick budget`() {
        val stats = FrameStats(windowFrames = 4)
        stats.record(5_000_000L, tickNs) // under budget
        stats.record(tickNs + 1, tickNs) // over budget → dropped
        stats.record(tickNs, tickNs) // exactly budget → NOT dropped (strict >)
        stats.record(50_000_000L, tickNs) // over budget → dropped
        val snap = stats.snapshot()!!
        assertEquals(2, snap.dropped)
    }

    @Test
    fun `ups estimate derives from average frame time`() {
        val stats = FrameStats(windowFrames = 2)
        // avg 20ms → 1000/20 = 50 UPS
        stats.record(20_000_000L, tickNs)
        stats.record(20_000_000L, tickNs)
        val snap = stats.snapshot()!!
        assertEquals(50.0, snap.ups, 1e-6)
    }

    @Test
    fun `window resets after closing so the next window is independent`() {
        val stats = FrameStats(windowFrames = 2)
        stats.record(30_000_000L, tickNs)
        stats.record(30_000_000L, tickNs) // window 1 closes: min=max=avg=30ms
        assertEquals(ms(30_000_000L), stats.snapshot()!!.maxMs, 1e-9)
        // window 2: much faster frames — must NOT carry window 1's 30ms max
        stats.record(5_000_000L, tickNs)
        stats.record(5_000_000L, tickNs)
        val snap2 = stats.snapshot()!!
        assertEquals(ms(5_000_000L), snap2.maxMs, 1e-9)
        assertEquals(ms(5_000_000L), snap2.minMs, 1e-9)
        assertEquals(0, snap2.dropped)
    }

    @Test
    fun `partial frames into a new window do not overwrite the last snapshot`() {
        val stats = FrameStats(windowFrames = 2)
        stats.record(10_000_000L, tickNs)
        stats.record(10_000_000L, tickNs) // window closes
        val closed = stats.snapshot()!!
        stats.record(99_000_000L, tickNs) // 1 frame into window 2 — not yet closed
        assertEquals(closed, stats.snapshot(), "snapshot must stay the last CLOSED window until the next closes")
        assertTrue(closed.avgMs > 0.0)
    }
}
