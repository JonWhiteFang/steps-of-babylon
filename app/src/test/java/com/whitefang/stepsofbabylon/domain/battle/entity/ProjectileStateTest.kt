package com.whitefang.stepsofbabylon.domain.battle.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [ProjectileState] — the pure projectile-motion state extracted from
 * `presentation/battle/entities/ProjectileEntity` during V1X-09 Phase 2 (ADR-0012).
 *
 * Pure JVM (no Robolectric, no Android imports). Establishes the regression net for
 * the homing-motion math that previously lived only in the Canvas-coupled entity.
 */
class ProjectileStateTest {
    @Test
    fun `moves proportionally toward target in one step`() {
        // From (0,0) toward (100,0) at speed 50 px/s over dt 1.0s → x advances by 50.
        val s = ProjectileState(x = 0f, y = 0f, targetX = 100f, targetY = 0f, speed = 50f)
        s.update(deltaTime = 1f)
        assertEquals(50f, s.x, 1e-4f)
        assertEquals(0f, s.y, 1e-4f)
        assertTrue(s.isAlive)
    }

    @Test
    fun `splits movement across both axes proportionally`() {
        // Toward (30,40): dist 50; speed 50, dt 0.5 → travels 25 (half) → (15,20).
        val s = ProjectileState(x = 0f, y = 0f, targetX = 30f, targetY = 40f, speed = 50f)
        s.update(deltaTime = 0.5f)
        assertEquals(15f, s.x, 1e-4f)
        assertEquals(20f, s.y, 1e-4f)
        assertTrue(s.isAlive)
    }

    @Test
    fun `dies when target is within one step's reach`() {
        // dist 10 < speed*dt (50) → arrival → isAlive false, position unchanged.
        val s = ProjectileState(x = 0f, y = 0f, targetX = 10f, targetY = 0f, speed = 50f)
        s.update(deltaTime = 1f)
        assertFalse(s.isAlive)
    }

    @Test
    fun `converges and dies after enough steps`() {
        val s = ProjectileState(x = 0f, y = 0f, targetX = 100f, targetY = 0f, speed = 50f)
        repeat(10) { if (s.isAlive) s.update(deltaTime = 0.5f) } // 25px/step, arrives within ~4 steps
        assertFalse(s.isAlive)
    }
}
