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

    // -------- RO-08: in-round multiplier coverage for ALL stat-bearing upgrades --------
    // Pre-RO-08 only DAMAGE / ATTACK_SPEED / HEALTH had an `ir(...)` term. The other 14
    // stat-bearing upgrades silently produced no in-round effect even though the in-round
    // upgrade menu deducted cash for them. These tests guard the closed gap.

    @Test
    fun `RO08 in-round attackSpeed stacks multiplicatively with workshop`() {
        val stats = sut(
            mapOf(UpgradeType.ATTACK_SPEED to 10),
            mapOf(UpgradeType.ATTACK_SPEED to 5),
        )
        // base × (1 + ws*0.015) × (1 + ir*0.015)
        val expected = ZigguratBaseStats.BASE_ATTACK_SPEED * 1.15 * 1.075
        assertEquals(expected, stats.attackSpeed, eps)
    }

    @Test
    fun `RO08 in-round healthRegen stacks multiplicatively`() {
        val stats = sut(emptyMap(), mapOf(UpgradeType.HEALTH_REGEN to 20))
        // base × (1 + 0) × (1 + 20*0.02) = base × 1.40
        assertEquals(ZigguratBaseStats.BASE_REGEN * 1.40, stats.healthRegen, eps)
    }

    @Test
    fun `RO08 in-round range stacks multiplicatively and respects 3x cap`() {
        // Workshop alone, level 50 → multiplier 2.0×, well under the cap.
        val ws = sut(mapOf(UpgradeType.RANGE to 50))
        assertEquals(ZigguratBaseStats.BASE_RANGE * 2.0f, ws.range, 0.5f)

        // Combined workshop level 100 (3.0×) + in-round level 100 (3.0×) = 9.0× pre-cap;
        // the `coerceAtMost(BASE × 3)` clamp must hold.
        val capped = sut(
            mapOf(UpgradeType.RANGE to 100),
            mapOf(UpgradeType.RANGE to 100),
        )
        assertEquals(ZigguratBaseStats.BASE_RANGE * 3.0f, capped.range, 0.5f)
    }

    @Test
    fun `RO08 in-round knockback stacks multiplicatively`() {
        val stats = sut(emptyMap(), mapOf(UpgradeType.KNOCKBACK to 25))
        // base × (1 + 0) × (1 + 25*0.02) = base × 1.50
        assertEquals(ZigguratBaseStats.BASE_KNOCKBACK * 1.50f, stats.knockbackForce, 0.01f)
    }

    @Test
    fun `RO08 in-round critChance sums additively with workshop and respects 80 percent cap`() {
        val combined = sut(
            mapOf(UpgradeType.CRITICAL_CHANCE to 50),
            mapOf(UpgradeType.CRITICAL_CHANCE to 30),
        )
        // (50 + 30) × 0.005 = 0.40
        assertEquals(0.40, combined.critChance, eps)

        // Combined 200 levels → 1.0 pre-cap; cap holds.
        val capped = sut(
            mapOf(UpgradeType.CRITICAL_CHANCE to 100),
            mapOf(UpgradeType.CRITICAL_CHANCE to 100),
        )
        assertEquals(0.80, capped.critChance, eps)
    }

    @Test
    fun `RO08 in-round critFactor adds to the 2x base`() {
        val stats = sut(emptyMap(), mapOf(UpgradeType.CRITICAL_FACTOR to 5))
        // 2.0 + (5 × 0.1) = 2.5
        assertEquals(2.5, stats.critMultiplier, eps)
    }

    @Test
    fun `RO08 in-round defensePercent sums additively and caps at 75 percent`() {
        val combined = sut(
            mapOf(UpgradeType.DEFENSE_PERCENT to 100),
            mapOf(UpgradeType.DEFENSE_PERCENT to 100),
        )
        // (100 + 100) × 0.003 = 0.60
        assertEquals(0.60, combined.defensePercent, eps)

        val capped = sut(
            mapOf(UpgradeType.DEFENSE_PERCENT to 200),
            mapOf(UpgradeType.DEFENSE_PERCENT to 200),
        )
        assertEquals(0.75, capped.defensePercent, eps)
    }

    @Test
    fun `RO08 in-round defenseAbsolute sums flat`() {
        val stats = sut(
            mapOf(UpgradeType.DEFENSE_ABSOLUTE to 5),
            mapOf(UpgradeType.DEFENSE_ABSOLUTE to 3),
        )
        assertEquals(8.0, stats.defenseAbsolute, eps)
    }

    @Test
    fun `RO08 in-round thornPercent sums additively`() {
        val stats = sut(emptyMap(), mapOf(UpgradeType.THORN_DAMAGE to 7))
        assertEquals(0.07, stats.thornPercent, eps)
    }

    @Test
    fun `RO08 in-round lifesteal sums additively and caps at 15 percent`() {
        val combined = sut(
            mapOf(UpgradeType.LIFESTEAL to 30),
            mapOf(UpgradeType.LIFESTEAL to 20),
        )
        // (30 + 20) × 0.002 = 0.10
        assertEquals(0.10, combined.lifestealPercent, eps)

        val capped = sut(
            mapOf(UpgradeType.LIFESTEAL to 50),
            mapOf(UpgradeType.LIFESTEAL to 50),
        )
        assertEquals(0.15, capped.lifestealPercent, eps)
    }

    @Test
    fun `RO08 in-round damagePerMeter sums additively`() {
        val stats = sut(emptyMap(), mapOf(UpgradeType.DAMAGE_PER_METER to 10))
        assertEquals(0.10, stats.damagePerMeterBonus, eps)
    }

    @Test
    fun `RO08 in-round deathDefy sums additively and caps at 50 percent`() {
        val capped = sut(
            mapOf(UpgradeType.DEATH_DEFY to 30),
            mapOf(UpgradeType.DEATH_DEFY to 30),
        )
        // (30 + 30) × 0.01 = 0.60 → cap at 0.50
        assertEquals(0.50, capped.deathDefyChance, eps)
    }

    @Test
    fun `RO08 in-round multishot sums levels for the per-20 threshold`() {
        // Workshop 10 + in-round 10 = 20 → +1 target = 2 total
        val combined = sut(
            mapOf(UpgradeType.MULTISHOT to 10),
            mapOf(UpgradeType.MULTISHOT to 10),
        )
        assertEquals(2, combined.multishotTargets)

        // Combined 100 → +5 targets, cap at 5 total
        val capped = sut(
            mapOf(UpgradeType.MULTISHOT to 50),
            mapOf(UpgradeType.MULTISHOT to 50),
        )
        assertEquals(5, capped.multishotTargets)
    }

    @Test
    fun `RO08 in-round bounce sums levels for the per-15 threshold`() {
        // Workshop 8 + in-round 7 = 15 → 1 bounce
        val combined = sut(
            mapOf(UpgradeType.BOUNCE_SHOT to 8),
            mapOf(UpgradeType.BOUNCE_SHOT to 7),
        )
        assertEquals(1, combined.bounceCount)
    }

    @Test
    fun `RO08 in-round orbs sums levels and caps at 6`() {
        val combined = sut(
            mapOf(UpgradeType.ORBS to 2),
            mapOf(UpgradeType.ORBS to 3),
        )
        assertEquals(5, combined.orbCount)

        val capped = sut(
            mapOf(UpgradeType.ORBS to 4),
            mapOf(UpgradeType.ORBS to 4),
        )
        assertEquals(6, capped.orbCount)
    }
}
