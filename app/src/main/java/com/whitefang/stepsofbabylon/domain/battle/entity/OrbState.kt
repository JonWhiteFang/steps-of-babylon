package com.whitefang.stepsofbabylon.domain.battle.entity

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure orbit-position + radial-oscillation state extracted from
 * `presentation/battle/entities/OrbEntity` for V1X-09 Phase 2 (ADR-0012).
 *
 * No Android imports. Owns the orbit angle, the radial-oscillation phase, and the derived
 * (x, y) position; [update] advances both angles and recomputes the position. The
 * presentation [OrbEntity] delegates its position here and keeps the enemy-proximity /
 * hit-cooldown logic (which needs `EnemyEntity` references) plus the Canvas `render()`.
 *
 * The orbit radius oscillates between [ORBIT_RADIUS_MIN] and [ORBIT_RADIUS_MAX] over one
 * [ORBIT_PERIOD_SEC] cycle (the #54 fix that lets the inner sweep reach the enemy melee
 * zone). Math is identical to the pre-extraction entity.
 */
class OrbState(
    private val zigX: Float,
    private val zigY: Float,
    private var angle: Float,
    private val angularSpeed: Float,
    initialRadialPhase: Float = 0f,
) {
    var x: Float = zigX
        private set
    var y: Float = zigY
        private set

    var radialPhase: Float = initialRadialPhase
        private set

    /** Current orbit radius derived from [radialPhase] via `MID + AMPLITUDE × sin(phase)`. */
    val currentOrbitRadius: Float
        get() = MID_ORBIT_RADIUS + AMPLITUDE_ORBIT_RADIUS * sin(radialPhase)

    init {
        recomputePosition()
    }

    /** Advances the orbit angle + radial-oscillation phase and recomputes the position. */
    fun update(deltaTime: Float) {
        angle += angularSpeed * deltaTime
        radialPhase += RADIAL_ANGULAR_SPEED * deltaTime
        recomputePosition()
    }

    private fun recomputePosition() {
        val r = currentOrbitRadius
        x = zigX + cos(angle) * r
        y = zigY + sin(angle) * r
    }

    companion object {
        /** Inner-sweep orbit radius (px) — just inside enemy meleeRange so HIT_RANGE reaches. */
        const val ORBIT_RADIUS_MIN = 25f

        /** Outer-sweep orbit radius (px) — cleanly outside HIT_RANGE so the kill zone "breathes". */
        const val ORBIT_RADIUS_MAX = 70f

        /** Full radial-oscillation cycle in seconds. */
        const val ORBIT_PERIOD_SEC = 2.5f

        const val MID_ORBIT_RADIUS = (ORBIT_RADIUS_MIN + ORBIT_RADIUS_MAX) / 2f
        const val AMPLITUDE_ORBIT_RADIUS = (ORBIT_RADIUS_MAX - ORBIT_RADIUS_MIN) / 2f

        /** Radial-oscillation angular speed in rad/sec. */
        val RADIAL_ANGULAR_SPEED: Float = (2.0 * PI / ORBIT_PERIOD_SEC).toFloat()
    }
}
