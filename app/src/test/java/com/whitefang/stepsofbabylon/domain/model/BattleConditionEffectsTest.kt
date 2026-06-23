package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BattleConditionEffectsTest {
    private val eps = 0.001f

    @Test
    fun `tier 1 has all defaults`() {
        val e = BattleConditionEffects.fromTier(1)
        assertEquals(1f, e.enemySpeedMultiplier, eps)
        assertEquals(1f, e.orbDamageMultiplier, eps)
        assertEquals(1f, e.knockbackMultiplier, eps)
        assertEquals(1f, e.thornMultiplier, eps)
        assertEquals(0, e.armorHits)
        assertEquals(10, e.bossWaveInterval)
    }

    @Test
    fun `tier 6 has enemy speed 1_10`() {
        val e = BattleConditionEffects.fromTier(6)
        assertEquals(1.10f, e.enemySpeedMultiplier, eps)
    }

    @Test
    fun `tier 7 has orb resistance and speed`() {
        val e = BattleConditionEffects.fromTier(7)
        assertEquals(0.80f, e.orbDamageMultiplier, eps)
        assertEquals(1.15f, e.enemySpeedMultiplier, eps)
    }

    @Test
    fun `tier 8 has knockback resistance and armor`() {
        val e = BattleConditionEffects.fromTier(8)
        assertEquals(0.70f, e.knockbackMultiplier, eps)
        assertEquals(5, e.armorHits)
    }

    @Test
    fun `tier 9 has thorn resistance and boss every 7`() {
        val e = BattleConditionEffects.fromTier(9)
        assertEquals(0.70f, e.thornMultiplier, eps)
        assertEquals(7, e.bossWaveInterval)
    }

    @Test
    fun `tier 10 has all conditions`() {
        val e = BattleConditionEffects.fromTier(10)
        assertEquals(1.20f, e.enemySpeedMultiplier, eps)
        assertEquals(1.15f, e.enemyAttackSpeedMultiplier, eps)
        assertEquals(0.80f, e.orbDamageMultiplier, eps)
        assertEquals(0.70f, e.knockbackMultiplier, eps)
        assertEquals(0.70f, e.thornMultiplier, eps)
        assertEquals(5, e.armorHits)
        assertEquals(7, e.bossWaveInterval)
    }
}
