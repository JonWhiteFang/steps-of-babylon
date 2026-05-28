package com.whitefang.stepsofbabylon.domain.battle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [SimulationMath] — pure mathematical helpers extracted from GameEngine
 * during V1X-09 Phase 1 simulation extraction.
 *
 * These tests run on pure JVM (no Robolectric, no Android imports) and provide
 * a regression net for the math behaviors that were previously only indirectly
 * tested via GameEngineTest's reflection-heavy approach.
 */
class SimulationMathTest {

    // ---- Recovery Pulse Amount ----

    @Test
    fun `recoveryPulseAmount returns 0 at level 0`() {
        assertEquals(0.0, SimulationMath.recoveryPulseAmount(level = 0, maxHp = 1000.0))
    }

    @Test
    fun `recoveryPulseAmount returns 0 when maxHp is 0`() {
        assertEquals(0.0, SimulationMath.recoveryPulseAmount(level = 5, maxHp = 0.0))
    }

    @Test
    fun `recoveryPulseAmount level 1 = 1 percent of maxHp`() {
        // 1 * 0.01 = 0.01 → 1000 HP * 0.01 = 10 HP
        assertEquals(10.0, SimulationMath.recoveryPulseAmount(level = 1, maxHp = 1000.0))
    }

    @Test
    fun `recoveryPulseAmount level 25 caps at 50 percent per pulse`() {
        // 25 * 0.01 = 0.25, no cap → 250 HP
        assertEquals(250.0, SimulationMath.recoveryPulseAmount(level = 25, maxHp = 1000.0))
    }

    @Test
    fun `recoveryPulseAmount level 60 hits 50 percent cap`() {
        // 60 * 0.01 = 0.60, capped at 0.50 → 500 HP
        assertEquals(500.0, SimulationMath.recoveryPulseAmount(level = 60, maxHp = 1000.0))
    }

    @Test
    fun `recoveryPulseAmount enforces minimum 1 HP per pulse`() {
        // Tiny tower: 1 HP * 0.01 = 0.01, but minimum is 1.0
        assertEquals(1.0, SimulationMath.recoveryPulseAmount(level = 1, maxHp = 50.0))
    }

    // ---- Chrono Multiplier ----

    @Test
    fun `chronoMultiplier returns 1 when not active`() {
        assertEquals(1f, SimulationMath.chronoMultiplier(active = false))
    }

    @Test
    fun `chronoMultiplier returns 0_10 default when active`() {
        assertEquals(0.10f, SimulationMath.chronoMultiplier(active = true))
    }

    @Test
    fun `chronoMultiplier accepts custom slow factor`() {
        assertEquals(0.05f, SimulationMath.chronoMultiplier(active = true, slowFactor = 0.05f))
    }

    // ---- Thorn Reflection Damage ----

    @Test
    fun `thornReflectionDamage returns 0 when thornPercent is 0`() {
        assertEquals(0.0, SimulationMath.thornReflectionDamage(rawDamage = 100.0, thornPercent = 0.0))
    }

    @Test
    fun `thornReflectionDamage returns 0 when rawDamage is 0`() {
        assertEquals(0.0, SimulationMath.thornReflectionDamage(rawDamage = 0.0, thornPercent = 0.10))
    }

    @Test
    fun `thornReflectionDamage applies percentage correctly`() {
        // 100 dmg * 0.20 thorn = 20 reflected
        assertEquals(20.0, SimulationMath.thornReflectionDamage(rawDamage = 100.0, thornPercent = 0.20))
    }

    @Test
    fun `thornReflectionDamage applies condition multiplier`() {
        // 100 dmg * 0.20 thorn * 1.5 condition = 30 reflected
        assertEquals(30.0, SimulationMath.thornReflectionDamage(
            rawDamage = 100.0, thornPercent = 0.20, conditionMultiplier = 1.5
        ))
    }

    // ---- Lifesteal Heal Amount ----

    @Test
    fun `lifestealHealAmount returns 0 when damage is 0`() {
        assertEquals(0.0, SimulationMath.lifestealHealAmount(damageDealt = 0.0, lifestealPercent = 0.10))
    }

    @Test
    fun `lifestealHealAmount returns 0 when percent is 0`() {
        assertEquals(0.0, SimulationMath.lifestealHealAmount(damageDealt = 100.0, lifestealPercent = 0.0))
    }

    @Test
    fun `lifestealHealAmount applies percentage correctly`() {
        // 100 dmg * 0.05 = 5.0 HP heal
        assertEquals(5.0, SimulationMath.lifestealHealAmount(damageDealt = 100.0, lifestealPercent = 0.05))
    }

    @Test
    fun `lifestealHealAmount caps at 15 percent`() {
        // 100 dmg * 0.20 (above cap) → uses 0.15 cap → 15.0 HP
        assertEquals(15.0, SimulationMath.lifestealHealAmount(damageDealt = 100.0, lifestealPercent = 0.20))
    }

    @Test
    fun `lifestealHealAmount conserves sub-1 HP fractions`() {
        // Lv 1: 0.2% × base damage 10 = 0.02 HP
        assertEquals(0.02, SimulationMath.lifestealHealAmount(damageDealt = 10.0, lifestealPercent = 0.002), 0.0001)
    }

    // ---- Lifesteal Accumulator ----

    @Test
    fun `tickLifestealAccumulator below 1 HP threshold yields no visible burst`() {
        val result = SimulationMath.tickLifestealAccumulator(accumulator = 0.0, healAmount = 0.5)
        assertEquals(0.5, result.newAccumulator)
        assertEquals(0, result.visibleHp)
    }

    @Test
    fun `tickLifestealAccumulator crossing 1 HP yields 1 HP burst`() {
        val result = SimulationMath.tickLifestealAccumulator(accumulator = 0.7, healAmount = 0.5)
        assertEquals(0.2, result.newAccumulator, 0.0001)
        assertEquals(1, result.visibleHp)
    }

    @Test
    fun `tickLifestealAccumulator crossing multiple HPs yields multi-HP burst`() {
        // 0.5 + 2.7 = 3.2 → emit 3 HP, keep 0.2
        val result = SimulationMath.tickLifestealAccumulator(accumulator = 0.5, healAmount = 2.7)
        assertEquals(0.2, result.newAccumulator, 0.0001)
        assertEquals(3, result.visibleHp)
    }

    @Test
    fun `tickLifestealAccumulator preserves accumulator across many small heals`() {
        // 50 hits × 0.02 HP = 1.0 HP visible burst over 50 hits
        var acc = 0.0
        var totalVisible = 0
        repeat(50) {
            val r = SimulationMath.tickLifestealAccumulator(accumulator = acc, healAmount = 0.02)
            acc = r.newAccumulator
            totalVisible += r.visibleHp
        }
        assertEquals(1, totalVisible)
        assertTrue(acc < 1.0, "accumulator must be < 1.0 after exactly 50 × 0.02 hits")
    }

    // ---- HP Clamping ----

    @Test
    fun `clampHp clamps over-heal to maxHp`() {
        assertEquals(1000.0, SimulationMath.clampHp(candidateHp = 1500.0, maxHp = 1000.0))
    }

    @Test
    fun `clampHp clamps negative HP to 0`() {
        assertEquals(0.0, SimulationMath.clampHp(candidateHp = -50.0, maxHp = 1000.0))
    }

    @Test
    fun `clampHp passes through valid HP unchanged`() {
        assertEquals(500.0, SimulationMath.clampHp(candidateHp = 500.0, maxHp = 1000.0))
    }

    @Test
    fun `clampHp handles zero maxHp gracefully`() {
        assertEquals(0.0, SimulationMath.clampHp(candidateHp = 100.0, maxHp = 0.0))
    }

    @Test
    fun `clampHp handles negative maxHp by clamping to 0`() {
        // Defensive: coerceAtLeast(0) on the upper bound
        assertEquals(0.0, SimulationMath.clampHp(candidateHp = 5.0, maxHp = -10.0))
    }

    // ---- STEP_MULTIPLIER asymptotic curve (V1X-18) ----

    @Test
    fun `V1X18 stepMultiplierBonus level 0 returns zero`() {
        assertEquals(0.0, SimulationMath.stepMultiplierBonus(0))
    }

    @Test
    fun `V1X18 stepMultiplierBonus level 1 returns 0_05`() {
        // 1 - 0.95^1 = 0.05
        assertEquals(0.05, SimulationMath.stepMultiplierBonus(1), 0.0001)
    }

    @Test
    fun `V1X18 stepMultiplierBonus level 10 returns ~0_4013`() {
        // 1 - 0.95^10 = 0.40126...
        assertEquals(0.4013, SimulationMath.stepMultiplierBonus(10), 0.0005)
    }

    @Test
    fun `V1X18 stepMultiplierBonus level 20 returns ~0_6415`() {
        // 1 - 0.95^20 = 0.64151...
        assertEquals(0.6415, SimulationMath.stepMultiplierBonus(20), 0.0005)
    }

    @Test
    fun `V1X18 stepMultiplierBonus level 50 returns ~0_9231`() {
        // 1 - 0.95^50 = 0.92306...
        assertEquals(0.9231, SimulationMath.stepMultiplierBonus(50), 0.0005)
    }

    @Test
    fun `V1X18 stepMultiplierBonus level 100 asymptotes near 0_994`() {
        // 1 - 0.95^100 = 0.99408...
        assertEquals(0.9941, SimulationMath.stepMultiplierBonus(100), 0.0005)
    }

    @Test
    fun `V1X18 stepMultiplierBonus level 200 stays under cap`() {
        val bonus = SimulationMath.stepMultiplierBonus(200)
        assertTrue(bonus < 1.0, "asymptotic curve never reaches the cap")
        assertTrue(bonus > 0.999, "L200 should be very close to the asymptote")
    }

    @Test
    fun `V1X18 stepMultiplierBonus L99 differs visibly from L100 (dead-content fix)`() {
        // Core V1X-18 motivation: pre-fix L99 = L100 (both = 100% bonus). Post-fix they differ.
        val l99 = SimulationMath.stepMultiplierBonus(99)
        val l100 = SimulationMath.stepMultiplierBonus(100)
        assertTrue(l100 > l99, "L100 must give more bonus than L99 (dead-content fix)")
    }
}
