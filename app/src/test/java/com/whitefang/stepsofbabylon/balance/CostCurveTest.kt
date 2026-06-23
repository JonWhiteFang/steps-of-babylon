package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Validates Workshop cost curves produce smooth progression without walls.
 */
class CostCurveTest {
    private val calcCost = CalculateUpgradeCost()

    // Premium upgrades with intentionally steep scaling (gated progression). MULTISHOT and
    // BOUNCE_SHOT moved out of this set in R4-02b: they are no longer Workshop-purchasable
    // (they're in-round Cash purchases or Labs Steps research instead). The remaining 3
    // entries are the genuine Workshop premium upgrades.
    private val premiumUpgrades =
        setOf(
            UpgradeType.STEP_MULTIPLIER,
            UpgradeType.ORBS,
            UpgradeType.DEATH_DEFY,
        )

    @Test
    fun `standard upgrades do not exceed 50000 steps at level 25`() {
        for (type in UpgradeType.entries) {
            if (type in premiumUpgrades) continue
            // R4-02b: skip upgrades that aren't on the Workshop screen — their cost curves
            // are exercised by the in-round Cash menu (already balance-tested by
            // CashEconomyTest) or Labs research (different progression model).
            if (!type.isWorkshopVisible) continue
            val maxLevel = type.config.maxLevel
            val testLevel = if (maxLevel != null && maxLevel < 25) maxLevel - 1 else 25
            val cost = calcCost(type, testLevel)
            assertTrue(cost <= 50_000, "$type costs $cost at level $testLevel (exceeds 50k)")
        }
    }

    @Test
    fun `premium upgrades are expensive but not unreachable at level 10`() {
        for (type in premiumUpgrades) {
            val maxLevel = type.config.maxLevel
            val testLevel = if (maxLevel != null && maxLevel < 10) maxLevel - 1 else 10
            val cost = calcCost(type, testLevel)
            assertTrue(cost <= 100_000, "$type costs $cost at level $testLevel (exceeds 100k)")
        }
    }

    @Test
    fun `step multiplier ROI - level 10 cost recouped within 3 months at 8k steps per day`() {
        var totalCost = 0L
        for (level in 0 until 10) totalCost += calcCost(UpgradeType.STEP_MULTIPLIER, level)
        // At level 10: +10% bonus steps. At 8k/day for 90 days = 720k steps.
        val bonusSteps = (720_000 * 0.10).toLong()
        assertTrue(
            bonusSteps >= totalCost,
            "Step Multiplier ROI: costs $totalCost to reach Lv10, earns $bonusSteps bonus in 3 months",
        )
    }

    @Test
    fun `diminishing return upgrades affordable at level 30`() {
        // Check cost at level 30 for capped upgrades — should be reachable for active walkers
        val types = listOf(UpgradeType.DEFENSE_PERCENT, UpgradeType.CRITICAL_CHANCE, UpgradeType.LIFESTEAL)
        for (type in types) {
            val cost = calcCost(type, 30)
            assertTrue(cost <= 100_000, "$type costs $cost at level 30 (should be ≤100k)")
        }
    }

    @Test
    fun `cheapest upgrades start under 100 steps`() {
        val cheapest =
            UpgradeType.entries
                .filter { it.isWorkshopVisible }
                .minOf { calcCost(it, 0) }
        assertTrue(cheapest <= 100, "Cheapest upgrade at level 0 costs $cheapest (should be ≤100)")
    }
}
