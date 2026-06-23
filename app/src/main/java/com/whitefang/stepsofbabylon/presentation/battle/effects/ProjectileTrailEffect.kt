package com.whitefang.stepsofbabylon.presentation.battle.effects

import kotlin.random.Random

/**
 * #243: max one trail particle per [TRAIL_INTERVAL] seconds of projectile sim-time. At ~0.0167s/tick
 * this is ~1 trail every other tick (~10 simultaneous particles given the 0.3s lifetime), down from
 * one-per-tick. The throttle is time-based so it caps emissions per elapsed sim-time — immune to the
 * 2×/4× catch-up that batches extra fixed-`deltaTime` update() ticks per render frame
 * (GameLoopThread), which otherwise thrashed the 200-slot ParticlePool. Deliberate, tunable constant
 * (see spec F3); a modest 1× density reduction is an accepted, device-verified trade.
 */
const val TRAIL_INTERVAL = 0.03f

/**
 * Advance a projectile's trail timer by [dt] (the fixed sim tick). Returns `(shouldEmit, newTimer)`.
 * Subtract-interval (not reset-to-zero) so it doesn't drift under variable dt. Pure — no Android,
 * JVM-tested by [ProjectileTrailThrottleTest].
 */
fun advanceTrail(
    timer: Float,
    dt: Float,
): Pair<Boolean, Float> {
    val t = timer + dt
    return if (t >= TRAIL_INTERVAL) true to (t - TRAIL_INTERVAL) else false to t
}

object ProjectileTrailEffect {
    fun spawn(
        pool: ParticlePool,
        x: Float,
        y: Float,
        color: Int,
    ) {
        val p = pool.acquire()
        p.x = x + Random.nextFloat() * 4f - 2f
        p.y = y + Random.nextFloat() * 4f - 2f
        p.vx = Random.nextFloat() * 10f - 5f
        p.vy = Random.nextFloat() * 10f - 5f
        p.color = color
        p.size = 2f + Random.nextFloat() * 2f
        p.lifetime = 0.3f
        p.alpha = 0.8f
    }
}
