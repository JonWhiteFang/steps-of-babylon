package com.whitefang.stepsofbabylon.domain.battle.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [OrbState] — the pure orbit-position + radial-oscillation math extracted from
 * `presentation/battle/entities/OrbEntity` during V1X-09 Phase 2 (ADR-0012).
 *
 * Pure JVM (no Robolectric, no Android imports). The enemy-proximity / hit-cooldown logic
 * stays in the presentation OrbEntity (it needs EnemyEntity references); only the
 * position math moves here. Mirrors the radial-oscillation assertions in the
 * Robolectric `OrbEntityTest` but without the Paint-construction dependency.
 */
class OrbStateTest {

    private val mid = (OrbState.ORBIT_RADIUS_MIN + OrbState.ORBIT_RADIUS_MAX) / 2f // 47.5

    @Test
    fun `radius is mid and position is on the orbit at phase 0 angle 0`() {
        val s = OrbState(zigX = 100f, zigY = 200f, angle = 0f, angularSpeed = 0f, initialRadialPhase = 0f)
        assertEquals(mid, s.currentOrbitRadius, 1e-3f)
        // angle 0 → x = zigX + R, y = zigY
        assertEquals(100f + mid, s.x, 1e-3f)
        assertEquals(200f, s.y, 1e-3f)
    }

    @Test
    fun `radius reaches MAX after a quarter cycle`() {
        val s = OrbState(zigX = 0f, zigY = 0f, angle = 0f, angularSpeed = 0f, initialRadialPhase = 0f)
        s.update(OrbState.ORBIT_PERIOD_SEC / 4f) // +PI/2 → sin = 1 → MAX
        assertEquals(OrbState.ORBIT_RADIUS_MAX, s.currentOrbitRadius, 0.5f)
    }

    @Test
    fun `radius reaches MIN after three quarter cycles`() {
        val s = OrbState(zigX = 0f, zigY = 0f, angle = 0f, angularSpeed = 0f, initialRadialPhase = 0f)
        repeat(3) { s.update(OrbState.ORBIT_PERIOD_SEC / 4f) } // +3PI/2 → sin = -1 → MIN
        assertEquals(OrbState.ORBIT_RADIUS_MIN, s.currentOrbitRadius, 0.5f)
    }

    @Test
    fun `angularSpeed advances the orbit angle around the ziggurat`() {
        // angularSpeed PI/2 rad/s over dt 1s → angle PI/2 → x ~ zigX, y ~ zigY + R (radius MID at phase 0... phase also advances)
        val s = OrbState(zigX = 0f, zigY = 0f, angle = 0f, angularSpeed = (Math.PI / 2).toFloat(), initialRadialPhase = 0f)
        s.update(1f)
        val r = s.currentOrbitRadius
        // angle is now PI/2 → cos≈0, sin≈1 → x≈0, y≈r
        assertEquals(0f, s.x, 1e-2f)
        assertEquals(r, s.y, 1e-2f)
    }
}
