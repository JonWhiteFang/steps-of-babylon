package com.whitefang.stepsofbabylon.domain.battle.entity

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [ZigguratState] — the pure ziggurat simulation state extracted from
 * `presentation/battle/entities/ZigguratEntity` during V1X-09 Phase 2 (ADR-0012), the final
 * per-entity extraction.
 *
 * Pure JVM (no Robolectric, no Android imports). Establishes the regression net for the
 * HP-regen clamp, attack-cooldown cadence, and derived attackInterval / attackRange reads
 * that previously lived only in the Canvas-coupled entity. `currentHp` starts at `maxHealth`,
 * so regen tests lower it first.
 */
class ZigguratStateTest {
    @Test
    fun `regenHp adds healthRegen times dt below the cap`() {
        val s = ZigguratState(ResolvedStats(maxHealth = 100.0, healthRegen = 10.0))
        s.currentHp = 50.0
        s.regenHp(deltaTime = 1f)
        assertEquals(60.0, s.currentHp, 1e-6)
    }

    @Test
    fun `regenHp clamps at maxHp`() {
        val s = ZigguratState(ResolvedStats(maxHealth = 100.0, healthRegen = 10.0))
        s.currentHp = 95.0
        s.regenHp(deltaTime = 1f) // 95 + 10 = 105 → clamp to 100
        assertEquals(100.0, s.currentHp, 1e-6)
    }

    @Test
    fun `attack cooldown gates firing across the interval`() {
        // attackSpeed 2.0 → attackInterval 0.5s.
        val s = ZigguratState(ResolvedStats(attackSpeed = 2.0))
        assertTrue(s.tickAttackReady(deltaTime = 0.1f)) // starts ready (cooldown 0)
        s.onFired() // cooldown → 0.5
        assertFalse(s.tickAttackReady(deltaTime = 0.3f)) // 0.2 remaining
        assertTrue(s.tickAttackReady(deltaTime = 0.3f)) // interval elapsed → ready
    }

    @Test
    fun `holdReady keeps the ziggurat ready next tick when no targets`() {
        val s = ZigguratState(ResolvedStats(attackSpeed = 2.0))
        assertTrue(s.tickAttackReady(deltaTime = 0.3f)) // ready, overshoot to -0.3
        s.holdReady() // clamp to 0
        assertTrue(s.tickAttackReady(deltaTime = 0.001f)) // still ready next tick
    }

    @Test
    fun `attackInterval reflects attackSpeed and rapidFireMultiplier`() {
        val s = ZigguratState(ResolvedStats(attackSpeed = 2.0))
        assertEquals(0.5f, s.attackInterval, 1e-5f)
        s.rapidFireMultiplier = 2.0f // doubles the rate → halves the interval
        assertEquals(0.25f, s.attackInterval, 1e-5f)
    }

    @Test
    fun `updateStats redirects derived attackRange and attackInterval`() {
        val s = ZigguratState(ResolvedStats(range = 100f, attackSpeed = 2.0))
        assertEquals(100f, s.attackRange, 1e-5f)
        assertEquals(0.5f, s.attackInterval, 1e-5f)
        s.updateStats(ResolvedStats(range = 300f, attackSpeed = 4.0))
        assertEquals(300f, s.attackRange, 1e-5f)
        assertEquals(0.25f, s.attackInterval, 1e-5f)
    }

    @Test
    fun `ZigguratState is a Damageable exposing currentHp and maxHp`() {
        val s: Damageable = ZigguratState(ResolvedStats(maxHealth = 100.0))
        assertEquals(100.0, s.maxHp, 1e-9)
        s.currentHp = 40.0
        assertEquals(40.0, s.currentHp, 1e-9)
    }
}
