package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveStatsTest {

    private val sut = ResolveStats()
    private val eps = 0.001

    @Test
    fun `empty maps return base stats`() {
        val stats = sut(emptyMap())
        assertEquals(ZigguratBaseStats.BASE_DAMAGE, stats.damage, eps)
        assertEquals(ZigguratBaseStats.BASE_ATTACK_SPEED, stats.attackSpeed, eps)
        assertEquals(ZigguratBaseStats.BASE_HEALTH, stats.maxHealth, eps)
        assertEquals(ZigguratBaseStats.BASE_REGEN, stats.healthRegen, eps)
        assertEquals(ZigguratBaseStats.BASE_RANGE, stats.range, 0.1f)
        assertEquals(0.0, stats.critChance, eps)
        assertEquals(2.0, stats.critMultiplier, eps)
        assertEquals(1, stats.multishotTargets)
        assertEquals(0, stats.bounceCount)
        assertEquals(0, stats.orbCount)
    }

    @Test
    fun `workshop only damage at level 10`() {
        val stats = sut(mapOf(UpgradeType.DAMAGE to 10))
        assertEquals(10.0 * (1 + 10 * 0.02), stats.damage, eps) // 12.0
    }

    @Test
    fun `in-round only damage at level 5`() {
        val stats = sut(emptyMap(), mapOf(UpgradeType.DAMAGE to 5))
        assertEquals(10.0 * (1 + 5 * 0.02), stats.damage, eps) // 11.0
    }

    @Test
    fun `combined workshop and in-round multiply`() {
        val stats = sut(mapOf(UpgradeType.DAMAGE to 10), mapOf(UpgradeType.DAMAGE to 5))
        assertEquals(10.0 * 1.2 * 1.1, stats.damage, eps) // 13.2
    }

    @Test
    fun `crit chance caps at 80 percent`() {
        val stats = sut(mapOf(UpgradeType.CRITICAL_CHANCE to 200))
        assertEquals(0.80, stats.critChance, eps)
    }

    @Test
    fun `range caps at 3x base`() {
        val stats = sut(mapOf(UpgradeType.RANGE to 200))
        assertEquals(ZigguratBaseStats.BASE_RANGE * 3.0f, stats.range, 0.1f)
    }

    @Test
    fun `multishot thresholds`() {
        assertEquals(1, sut(emptyMap()).multishotTargets)
        assertEquals(2, sut(mapOf(UpgradeType.MULTISHOT to 20)).multishotTargets)
        assertEquals(3, sut(mapOf(UpgradeType.MULTISHOT to 40)).multishotTargets)
        assertEquals(5, sut(mapOf(UpgradeType.MULTISHOT to 100)).multishotTargets)
    }

    @Test
    fun `bounce thresholds`() {
        assertEquals(0, sut(emptyMap()).bounceCount)
        assertEquals(1, sut(mapOf(UpgradeType.BOUNCE_SHOT to 15)).bounceCount)
        assertEquals(4, sut(mapOf(UpgradeType.BOUNCE_SHOT to 60)).bounceCount)
    }

    @Test
    fun `orb count caps at 6`() {
        assertEquals(0, sut(emptyMap()).orbCount)
        assertEquals(6, sut(mapOf(UpgradeType.ORBS to 6)).orbCount)
        assertEquals(6, sut(mapOf(UpgradeType.ORBS to 10)).orbCount)
    }

    @Test
    fun `defense percent caps at 75 percent`() {
        assertEquals(0.75, sut(mapOf(UpgradeType.DEFENSE_PERCENT to 300)).defensePercent, eps)
    }

    @Test
    fun `lifesteal caps at 15 percent`() {
        assertEquals(0.15, sut(mapOf(UpgradeType.LIFESTEAL to 100)).lifestealPercent, eps)
    }

    @Test
    fun `death defy caps at 50 percent`() {
        assertEquals(0.50, sut(mapOf(UpgradeType.DEATH_DEFY to 100)).deathDefyChance, eps)
    }
}
