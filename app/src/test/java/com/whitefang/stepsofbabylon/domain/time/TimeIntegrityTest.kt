package com.whitefang.stepsofbabylon.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeIntegrityTest {
    private fun reading(
        elapsed: Long,
        wall: Long,
    ) = TimeReading(elapsed, wall)

    // 4-slot baseline helper (lastElapsed, lastWall, maxSeen, trustedWall)
    private fun base(
        elapsed: Long,
        wall: Long,
        max: Long,
        trusted: Long,
    ) = TimeBaseline(elapsed, wall, max, trusted)

    @Test
    fun `first run with null baseline is Trusted and seeds maxSeen and trustedWallClock`() {
        val v = TimeIntegrity.evaluate(null, reading(elapsed = 1000, wall = 5000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(5000, v.newBaseline.maxWallClockSeen)
        assertEquals(5000, v.newBaseline.trustedWallClock) // seeded to wall
        assertEquals(5000, v.newBaseline.lastWallClock)
        assertEquals(1000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `normal forward advance is Trusted and trustedWallClock advances by the capped delta`() {
        val b = base(elapsed = 1000, wall = 5000, max = 5000, trusted = 5000)
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 4000, wall = 8000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(8000, v.newBaseline.maxWallClockSeen)
        // wallDelta=3000, elapsedDelta=3000 → capped=3000 → trusted 5000+3000=8000
        assertEquals(8000, v.newBaseline.trustedWallClock)
        assertEquals(8000, v.newBaseline.lastWallClock)
        assertEquals(4000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `in-session forward JUMP advances trustedWallClock only by the elapsed delta (excess discarded)`() {
        val b = base(elapsed = 1000, wall = 5000, max = 5000, trusted = 5000)
        // wall leapt +30 min (1_800_000) but only 5 min (300_000) of monotonic time passed
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 301_000, wall = 1_805_000))
        assertTrue(v is TimeVerdict.Trusted) // forward, not below floor
        // capped = min(1_800_000, 300_000) = 300_000 → trusted 5000+300_000 = 305_000 (NOT 1_805_000)
        assertEquals(305_000, v.newBaseline.trustedWallClock)
        assertEquals(1_805_000, v.newBaseline.maxWallClockSeen) // floor advances to the raw wall
    }

    @Test
    fun `backward jump below the floor is Rollback and trustedWallClock does not move backward`() {
        val b = base(elapsed = 1000, wall = 9000, max = 9000, trusted = 9000)
        // wall jumped BACK to 4000 (< maxSeen 9000) while elapsed advanced
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 2000, wall = 4000))
        assertTrue(v is TimeVerdict.Rollback)
        assertEquals(9000, v.newBaseline.maxWallClockSeen) // floor unchanged (max of prev, 4000)
        // capped = min(wallDelta=-5000, elapsedDelta=1000).coerceAtLeast(0) = 0 → trusted stays 9000
        assertEquals(9000, v.newBaseline.trustedWallClock)
        assertEquals(4000, v.newBaseline.lastWallClock)
        assertEquals(2000, v.newBaseline.lastElapsedRealtime)
    }

    @Test
    fun `reboot (elapsed reset) with wall above floor is Trusted not false-rollback`() {
        val b = base(elapsed = 900_000, wall = 9000, max = 9000, trusted = 9000)
        // after reboot elapsedRealtime resets near 0; wall still >= floor
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 50, wall = 9500))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(9500, v.newBaseline.maxWallClockSeen)
        // reboot fallback (elapsedDelta<0) → capped = full wallDelta 500 → trusted 9000+500=9500 (accepted §2 gap)
        assertEquals(9500, v.newBaseline.trustedWallClock)
    }

    @Test
    fun `wall equal to the floor is Trusted not Rollback (boundary)`() {
        // wall == maxWallClockSeen exactly — strict < means this is NOT a rollback
        val b = base(elapsed = 1000, wall = 9000, max = 9000, trusted = 9000)
        val v = TimeIntegrity.evaluate(b, reading(elapsed = 1500, wall = 9000))
        assertTrue(v is TimeVerdict.Trusted)
        assertEquals(9000, v.newBaseline.maxWallClockSeen)
        // wallDelta=0, elapsedDelta=500 → capped=min(0,500).coerceAtLeast(0)=0 → trusted stays 9000
        assertEquals(9000, v.newBaseline.trustedWallClock)
    }

    @Test
    fun `order-independence -- advancing the baseline then re-deriving from it keeps the jump capped`() {
        // simulates: single owner advances on a forward-jump pass, THEN a read-only consumer
        // re-evaluates against the persisted (advanced) baseline with the same jumped reading.
        val b0 = base(elapsed = 1000, wall = 5000, max = 5000, trusted = 5000)
        val owner = TimeIntegrity.evaluate(b0, reading(elapsed = 301_000, wall = 1_805_000))
        assertEquals(305_000, owner.newBaseline.trustedWallClock)
        // consumer reads the advanced baseline + the same (still-jumped) reading → no further excess folds in
        val consumer = TimeIntegrity.evaluate(owner.newBaseline, reading(elapsed = 301_000, wall = 1_805_000))
        assertEquals(305_000, consumer.newBaseline.trustedWallClock) // unchanged — the jump never re-accepted
    }
}
