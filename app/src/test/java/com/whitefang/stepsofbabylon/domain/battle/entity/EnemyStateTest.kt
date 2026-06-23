package com.whitefang.stepsofbabylon.domain.battle.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [EnemyState] — the pure movement + attack-cooldown state extracted from
 * `presentation/battle/entities/EnemyEntity` during V1X-09 Phase 2 (ADR-0012).
 *
 * Pure JVM (no Robolectric, no Android imports). Establishes the regression net for the
 * homing-motion + stop-distance + attack-cadence math that previously lived only in the
 * Canvas-coupled entity.
 */
class EnemyStateTest {
    private fun melee(
        speed: Float = 50f,
        attackInterval: Float = 1f,
    ) = EnemyState(targetX = 100f, targetY = 0f, speed = speed, isRanged = false, attackInterval = attackInterval)

    @Test
    fun `moves proportionally toward target while beyond melee range`() {
        // From (0,0) toward (100,0) at speed 50 px/s over dt 1.0s → x advances by 50, no attack.
        val s = melee()
        s.spawn(0f, 0f)
        assertFalse(s.update(deltaTime = 1f))
        assertEquals(50f, s.x, 1e-4f)
        assertEquals(0f, s.y, 1e-4f)
    }

    @Test
    fun `fires attack on the frame it is within melee range`() {
        // Spawn already inside MELEE_RANGE (40): cooldown starts at 0 → attacks immediately, no move.
        val s = melee()
        s.spawn(70f, 0f) // dist 30 < 40
        assertTrue(s.update(deltaTime = 0.5f))
        assertEquals(70f, s.x, 1e-4f)
    }

    @Test
    fun `attack cooldown gates subsequent attacks by the interval`() {
        val s = melee(attackInterval = 1f)
        s.spawn(70f, 0f) // in range
        assertTrue(s.update(deltaTime = 0.5f)) // first hit, cooldown reset to 1.0
        assertFalse(s.update(deltaTime = 0.5f)) // 0.5 elapsed → 0.5 remaining
        assertTrue(s.update(deltaTime = 0.5f)) // interval elapsed → hits again
    }

    @Test
    fun `ranged enemy stops and attacks farther out than melee range`() {
        // initialDist 200 → stopDistance = 200 * 0.4 = 80 (well beyond MELEE_RANGE 40).
        val s = EnemyState(targetX = 200f, targetY = 0f, speed = 130f, isRanged = true, attackInterval = 1f)
        s.spawn(0f, 0f)
        assertFalse(s.update(deltaTime = 1f)) // dist 200 > 80 → moves to (130,0), no attack
        // Now dist is 70 (< 80) so the enemy attacks while still 70px away — proving the ranged
        // stop uses initialDist * RANGED_STOP_FACTOR (80), not the melee 40.
        assertTrue(s.update(deltaTime = 1f))
        assertEquals(130f, s.x, 1e-4f)
    }

    @Test
    fun `knockback shifts position`() {
        val s = melee()
        s.spawn(50f, 50f)
        s.applyKnockback(10f, -5f)
        assertEquals(60f, s.x, 1e-4f)
        assertEquals(45f, s.y, 1e-4f)
    }
}
