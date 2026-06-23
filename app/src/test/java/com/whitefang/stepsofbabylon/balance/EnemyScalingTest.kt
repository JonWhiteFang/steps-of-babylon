package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.ResolveStats
import com.whitefang.stepsofbabylon.presentation.battle.engine.EnemyScaler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Validates enemy HP/damage scaling creates appropriate difficulty ramp.
 *
 * DPS estimates include a combat multiplier (3x) to account for crits,
 * in-round upgrades, multishot, orbs, and cards that the base DPS ignores.
 */
class EnemyScalingTest {
    private val resolveStats = ResolveStats()
    private val COMBAT_MULTIPLIER = 3.0 // Crits + in-round upgrades + multishot + orbs

    private fun effectiveDps(damageLevel: Int): Double {
        val levels = mapOf(UpgradeType.DAMAGE to damageLevel, UpgradeType.ATTACK_SPEED to damageLevel)
        val stats = resolveStats(levels)
        return stats.damage * stats.attackSpeed * COMBAT_MULTIPLIER
    }

    private fun timeToKill(
        dps: Double,
        enemyHp: Double,
    ): Double = enemyHp / dps

    @Test
    fun `wave 10 basic enemies killable in under 5 seconds at workshop level 0`() {
        val dps = effectiveDps(0)
        val hp = EnemyScaler.scaleHealth(EnemyType.BASIC, 10)
        val ttk = timeToKill(dps, hp)
        assertTrue(ttk < 5.0, "Wave 10 Basic TTK at WS0: ${ttk}s (should be <5s)")
    }

    @Test
    fun `wave 50 basic enemies manageable at workshop level 25`() {
        val dps = effectiveDps(25)
        val hp = EnemyScaler.scaleHealth(EnemyType.BASIC, 50)
        val ttk = timeToKill(dps, hp)
        assertTrue(ttk < 15.0, "Wave 50 Basic TTK at WS25: ${ttk}s (should be <15s)")
    }

    @Test
    fun `wave 100 enemies challenging but killable at workshop level 50`() {
        val dps = effectiveDps(50)
        val hp = EnemyScaler.scaleHealth(EnemyType.BASIC, 100)
        val ttk = timeToKill(dps, hp)
        assertTrue(ttk < 120.0, "Wave 100 Basic TTK at WS50: ${ttk}s (should be <120s)")
        assertTrue(ttk > 1.0, "Wave 100 Basic TTK at WS50: ${ttk}s (should be >1s)")
    }

    @Test
    fun `boss at wave 50 is a meaningful fight at workshop level 25`() {
        val dps = effectiveDps(25)
        val bossHp = EnemyScaler.scaleHealth(EnemyType.BOSS, 50)
        val ttk = timeToKill(dps, bossHp)
        assertTrue(ttk < 300.0, "Wave 50 Boss TTK at WS25: ${ttk}s (should be <300s)")
        assertTrue(ttk > 5.0, "Wave 50 Boss TTK at WS25: ${ttk}s (should be >5s)")
    }

    @Test
    fun `enemy damage does not one-shot ziggurat at expected workshop levels`() {
        val levels = mapOf(UpgradeType.HEALTH to 25)
        val stats = resolveStats(levels)
        val enemyDmg = EnemyScaler.scaleDamage(EnemyType.BASIC, 50)
        assertTrue(
            enemyDmg < stats.maxHealth * 0.5,
            "Wave 50 Basic deals $enemyDmg vs ${stats.maxHealth} HP (should be <50% of HP)",
        )
    }

    @Test
    fun `scaling factor produces exponential but not runaway growth`() {
        val wave10Hp = EnemyScaler.scaleHealth(EnemyType.BASIC, 10)
        val wave50Hp = EnemyScaler.scaleHealth(EnemyType.BASIC, 50)
        val wave100Hp = EnemyScaler.scaleHealth(EnemyType.BASIC, 100)
        // Wave 50 should be ~7x wave 10, wave 100 should be ~80x wave 10
        val ratio50to10 = wave50Hp / wave10Hp
        val ratio100to10 = wave100Hp / wave10Hp
        assertTrue(ratio50to10 in 3.0..20.0, "Wave 50/10 ratio: $ratio50to10")
        assertTrue(ratio100to10 in 20.0..500.0, "Wave 100/10 ratio: $ratio100to10")
    }
}
