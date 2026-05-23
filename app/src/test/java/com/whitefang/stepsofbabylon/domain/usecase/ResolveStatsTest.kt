package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
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
    fun `R402b multishot per-level scaling stacks in-round + Labs with cap 11`() {
        // Post-R4-02b: maxLevel=10 (in-round Cash) + maxLevel=10 (Labs research) + 1 baseline,
        // capped at 11 multishot targets. Workshop levels are always 0 because MULTISHOT is
        // `isWorkshopVisible = false`, but the formula still reads `total(MULTISHOT)` = ws + ir
        // for defensive correctness.
        assertEquals(1, sut(emptyMap()).multishotTargets)
        assertEquals(2, sut(mapOf(UpgradeType.MULTISHOT to 1)).multishotTargets)
        assertEquals(
            6,
            sut(
                workshopLevels = emptyMap(),
                inRoundLevels = mapOf(UpgradeType.MULTISHOT to 5),
                labLevels = emptyMap(),
            ).multishotTargets,
        )
        // Labs alone (no in-round): 1 + 0 + 5 = 6 targets.
        assertEquals(
            6,
            sut(
                workshopLevels = emptyMap(),
                inRoundLevels = emptyMap(),
                labLevels = mapOf(ResearchType.MULTISHOT_RESEARCH to 5),
            ).multishotTargets,
        )
        // ir=10 + Labs=10 = 20 raw, capped at 11.
        assertEquals(
            11,
            sut(
                workshopLevels = emptyMap(),
                inRoundLevels = mapOf(UpgradeType.MULTISHOT to 10),
                labLevels = mapOf(ResearchType.MULTISHOT_RESEARCH to 10),
            ).multishotTargets,
        )
        // Defensive: legacy install with level > maxLevel still clamps at 11.
        assertEquals(11, sut(mapOf(UpgradeType.MULTISHOT to 100)).multishotTargets)
    }

    @Test
    fun `R402b bounce per-level scaling stacks in-round + Labs with cap 10`() {
        // Post-R4-02b: maxLevel=10 (in-round Cash) + maxLevel=10 (Labs research), capped at
        // 10 bounces. Baseline 0 (no extra +1 like multishot has).
        assertEquals(0, sut(emptyMap()).bounceCount)
        assertEquals(1, sut(mapOf(UpgradeType.BOUNCE_SHOT to 1)).bounceCount)
        assertEquals(
            5,
            sut(
                workshopLevels = emptyMap(),
                inRoundLevels = mapOf(UpgradeType.BOUNCE_SHOT to 5),
                labLevels = emptyMap(),
            ).bounceCount,
        )
        // Labs alone: 0 + 5 = 5 bounces.
        assertEquals(
            5,
            sut(
                workshopLevels = emptyMap(),
                inRoundLevels = emptyMap(),
                labLevels = mapOf(ResearchType.BOUNCE_RESEARCH to 5),
            ).bounceCount,
        )
        // ir=10 + Labs=10 = 20 raw, capped at 10.
        assertEquals(
            10,
            sut(
                workshopLevels = emptyMap(),
                inRoundLevels = mapOf(UpgradeType.BOUNCE_SHOT to 10),
                labLevels = mapOf(ResearchType.BOUNCE_RESEARCH to 10),
            ).bounceCount,
        )
        // Defensive: legacy install with level > maxLevel still clamps at 10.
        assertEquals(10, sut(mapOf(UpgradeType.BOUNCE_SHOT to 60)).bounceCount)
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
    fun `R402b in-round multishot sums workshop and in-round levels`() {
        // Post-R4-02b: ws + ir totals additively, +1 target each, baseline 1, cap 11.
        // Workshop levels for MULTISHOT will always be 0 in production (isWorkshopVisible=false)
        // but the formula must still tolerate non-zero values for legacy/test inputs.
        // ws=2 + ir=1 = 3 levels → 1 + 3 = 4 targets.
        val combined = sut(
            mapOf(UpgradeType.MULTISHOT to 2),
            mapOf(UpgradeType.MULTISHOT to 1),
        )
        assertEquals(4, combined.multishotTargets)

        // ws=10 + ir=10 = 20, capped at 11.
        val capped = sut(
            mapOf(UpgradeType.MULTISHOT to 10),
            mapOf(UpgradeType.MULTISHOT to 10),
        )
        assertEquals(11, capped.multishotTargets)
    }

    @Test
    fun `R402b in-round bounce sums workshop and in-round levels`() {
        // Post-R4-02b: ws + ir totals additively, +1 bounce each, baseline 0, cap 10.
        // ws=2 + ir=1 = 3 → 3 bounces.
        val combined = sut(
            mapOf(UpgradeType.BOUNCE_SHOT to 2),
            mapOf(UpgradeType.BOUNCE_SHOT to 1),
        )
        assertEquals(3, combined.bounceCount)

        // ws=10 + ir=10 = 20, capped at 10.
        val capped = sut(
            mapOf(UpgradeType.BOUNCE_SHOT to 10),
            mapOf(UpgradeType.BOUNCE_SHOT to 10),
        )
        assertEquals(10, capped.bounceCount)
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

    // -------- RO-11 #A.1: lab research multiplies the matching base stat --------
    // Pre-RO-11 all 10 [ResearchType] enums were dead — declared with effect descriptions and
    // costing Steps + real-time + Gems to complete, but never read by any combat-path consumer.
    // These tests guard the closed gap for the four research types whose effect lands inside
    // [ResolvedStats]. The remaining lab research types are wired in their own commits:
    // CASH_RESEARCH + UW_COOLDOWN engine-side, STEP_EFFICIENCY in walking-credit.

    @Test
    fun `RO11 DAMAGE_RESEARCH level 10 grants +50 percent base damage`() {
        // 10 levels × 5 % per level = +50 % outer multiplier on damage.
        val stats = sut(
            workshopLevels = emptyMap(),
            inRoundLevels = emptyMap(),
            labLevels = mapOf(ResearchType.DAMAGE_RESEARCH to 10),
        )
        assertEquals(
            ZigguratBaseStats.BASE_DAMAGE * 1.50,
            stats.damage,
            eps,
            "DAMAGE_RESEARCH L10 must multiply base damage by 1.50×",
        )
    }

    @Test
    fun `RO11 HEALTH_RESEARCH level 20 grants +100 percent max health`() {
        // 20 levels × 5 % per level = +100 % outer multiplier on max health.
        val stats = sut(
            workshopLevels = emptyMap(),
            inRoundLevels = emptyMap(),
            labLevels = mapOf(ResearchType.HEALTH_RESEARCH to 20),
        )
        assertEquals(
            ZigguratBaseStats.BASE_HEALTH * 2.00,
            stats.maxHealth,
            eps,
            "HEALTH_RESEARCH L20 must double max health (max-level cap)",
        )
    }

    @Test
    fun `RO11 CRITICAL_RESEARCH level 15 grants +45 percent crit damage on top of the workshop crit factor`() {
        // Workshop CRITICAL_FACTOR L5 → base crit multiplier 2.0 + 5 × 0.1 = 2.5×
        // CRITICAL_RESEARCH L15 → outer multiplier (1 + 15 × 0.03) = 1.45×
        // Combined: 2.5 × 1.45 = 3.625×
        val stats = sut(
            workshopLevels = mapOf(UpgradeType.CRITICAL_FACTOR to 5),
            inRoundLevels = emptyMap(),
            labLevels = mapOf(ResearchType.CRITICAL_RESEARCH to 15),
        )
        assertEquals(
            3.625,
            stats.critMultiplier,
            eps,
            "CRITICAL_RESEARCH L15 must multiply the workshop-derived crit factor by 1.45×",
        )
    }

    @Test
    fun `RO11 REGEN_RESEARCH level 15 grants +60 percent health regen`() {
        // 15 levels × 4 % per level = +60 % outer multiplier on healthRegen.
        val stats = sut(
            workshopLevels = emptyMap(),
            inRoundLevels = emptyMap(),
            labLevels = mapOf(ResearchType.REGEN_RESEARCH to 15),
        )
        assertEquals(
            ZigguratBaseStats.BASE_REGEN * 1.60,
            stats.healthRegen,
            eps,
            "REGEN_RESEARCH L15 must scale health regen by 1.60× (max-level cap)",
        )
    }
}
