package com.whitefang.stepsofbabylon.presentation.battle.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #243: the projectile-trail throttle. `advanceTrail` bounds trail emission to one particle per
 * [TRAIL_INTERVAL] of projectile sim-time, so the per-frame spawn count is capped regardless of how
 * many catch-up `update()` calls the game loop batches at 2×/4× speed (GameLoopThread runs more
 * fixed-`deltaTime` ticks, not larger ones). These are pure JVM tests — no Android, no Canvas.
 */
class ProjectileTrailThrottleTest {
    /** Drive `advanceTrail` over many fixed-dt steps and count emissions. */
    private fun countEmissions(
        totalSeconds: Float,
        dt: Float,
    ): Int {
        var timer = 0f
        var emissions = 0
        var elapsed = 0f
        // Guard against fp drift accumulating an extra/short final step.
        while (elapsed + dt <= totalSeconds + 1e-6f) {
            val (emit, newTimer) = advanceTrail(timer, dt)
            timer = newTimer
            if (emit) emissions++
            elapsed += dt
        }
        return emissions
    }

    @Test
    fun `emission count tracks elapsed sim-time, not call count`() {
        val oneSecond = 1f
        val tick = 1f / 60f // the loop's fixed ~16.67ms tick

        // 1 second of projectile-time → ~ 1 / TRAIL_INTERVAL emissions, independent of dt size.
        val expected = (oneSecond / TRAIL_INTERVAL).toInt()
        val emissionsAt1x = countEmissions(oneSecond, tick)

        assertEquals(
            expected.toFloat(),
            emissionsAt1x.toFloat(),
            1f,
            "1s of sim-time should emit ~1/TRAIL_INTERVAL trails (±1), regardless of tick size",
        )
    }

    @Test
    fun `4x speed does not multiply emissions per elapsed sim-second`() {
        // At 4×, GameLoopThread issues ~4× as many fixed-dt update() calls per WALL second, i.e. it
        // advances ~4 sim-seconds per wall-second. Emissions must track SIM-time: 4 sim-seconds →
        // ~4× the 1-sim-second count (NOT an unbounded per-frame storm). The invariant we pin: the
        // emissions-per-elapsed-SIM-second is constant across "speeds".
        val tick = 1f / 60f
        val oneSimSecond = countEmissions(1f, tick)
        val fourSimSeconds = countEmissions(4f, tick)

        assertEquals(
            oneSimSecond * 4f,
            fourSimSeconds.toFloat(),
            2f,
            "emissions scale with elapsed sim-time linearly, so rate-per-sim-second is constant",
        )
    }

    @Test
    fun `a single fixed tick never emits more than once`() {
        // max pre-tick timer (< TRAIL_INTERVAL) + one tick (~0.0167) stays below 2×TRAIL_INTERVAL,
        // so one advanceTrail call emits at most once — no batching artifact.
        val tick = 1f / 60f
        var timer = TRAIL_INTERVAL - 0.0001f // just under the threshold
        val (emit, newTimer) = advanceTrail(timer, tick)
        assertTrue(emit, "crossing the threshold emits")
        assertTrue(newTimer < TRAIL_INTERVAL, "subtract-interval carries the remainder, never ≥ interval")
    }

    @Test
    fun `subtract-interval carries the remainder so it does not drift`() {
        // Feeding exactly TRAIL_INTERVAL emits and resets to ~0; feeding 1.5× emits once and carries 0.5×.
        val (emit1, t1) = advanceTrail(0f, TRAIL_INTERVAL)
        assertTrue(emit1)
        assertEquals(0f, t1, 1e-5f)

        val (emit2, t2) = advanceTrail(0f, TRAIL_INTERVAL * 1.5f)
        assertTrue(emit2)
        assertEquals(TRAIL_INTERVAL * 0.5f, t2, 1e-5f)
    }
}
