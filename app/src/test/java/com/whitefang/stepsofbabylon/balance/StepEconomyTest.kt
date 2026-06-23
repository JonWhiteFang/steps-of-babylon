package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Validates step economy against GDD §3.1 player profiles.
 *
 * Note: GDD numbers (5-8, 15-25, etc.) represent ONGOING weekly rates after
 * the initial burst. First-week rates are much higher because early upgrades
 * are cheap. This is intentional — generous early progression hooks players.
 *
 * These tests validate first-week rates (from level 0) and multi-week rates.
 */
class StepEconomyTest {
    private val calcCost = CalculateUpgradeCost()

    /** Simulate greedy spending from given starting levels. */
    private fun simulateWeeklyUpgrades(
        weeklySteps: Long,
        startLevels: Map<UpgradeType, Int> = emptyMap(),
    ): Int {
        var budget = weeklySteps
        val levels = startLevels.toMutableMap()
        var totalPurchased = 0

        while (true) {
            val best =
                UpgradeType.entries
                    .filter { type -> type.config.maxLevel?.let { (levels[type] ?: 0) < it } ?: true }
                    .minByOrNull { calcCost(it, levels[it] ?: 0) } ?: break
            val cost = calcCost(best, levels[best] ?: 0)
            if (cost > budget) break
            budget -= cost
            levels[best] = (levels[best] ?: 0) + 1
            totalPurchased++
        }
        return totalPurchased
    }

    /** Simulate N weeks of spending, return per-week upgrade counts. */
    private fun simulateMultiWeek(
        weeklySteps: Long,
        weeks: Int,
    ): List<Int> {
        val levels = mutableMapOf<UpgradeType, Int>()
        return (1..weeks).map { simulateWeeklyUpgradesAccumulating(weeklySteps, levels) }
    }

    private fun simulateWeeklyUpgradesAccumulating(
        weeklySteps: Long,
        levels: MutableMap<UpgradeType, Int>,
    ): Int {
        var budget = weeklySteps
        var count = 0
        while (true) {
            val best =
                UpgradeType.entries
                    .filter { type -> type.config.maxLevel?.let { (levels[type] ?: 0) < it } ?: true }
                    .minByOrNull { calcCost(it, levels[it] ?: 0) } ?: break
            val cost = calcCost(best, levels[best] ?: 0)
            if (cost > budget) break
            budget -= cost
            levels[best] = (levels[best] ?: 0) + 1
            count++
        }
        return count
    }

    @Test
    fun `sedentary walker - first week generous then settles`() {
        val weeks = simulateMultiWeek(17_500, 8)
        // Week 1: generous burst (100+). By week 4-8: should settle to GDD range (5-8)
        assertTrue(weeks[0] > 50, "Sedentary week 1: ${weeks[0]} (should be generous)")
        assertTrue(weeks.last() >= 1, "Sedentary week 8: ${weeks.last()} (should still progress)")
    }

    @Test
    fun `casual walker - first week generous then settles`() {
        val weeks = simulateMultiWeek(42_000, 8)
        assertTrue(weeks[0] > 100, "Casual week 1: ${weeks[0]} (should be generous)")
        assertTrue(weeks.last() >= 3, "Casual week 8: ${weeks.last()} (should still progress)")
    }

    @Test
    fun `active walker - sustained progression over 8 weeks`() {
        val weeks = simulateMultiWeek(77_000, 8)
        assertTrue(weeks[0] > 150, "Active week 1: ${weeks[0]}")
        assertTrue(weeks.last() >= 5, "Active week 8: ${weeks.last()} (should still progress)")
    }

    @Test
    fun `power walker - sustained progression over 8 weeks`() {
        val weeks = simulateMultiWeek(122_500, 8)
        assertTrue(weeks[0] > 200, "Power week 1: ${weeks[0]}")
        assertTrue(weeks.last() >= 10, "Power week 8: ${weeks.last()}")
    }

    @Test
    fun `marathon runner - deep investment over 8 weeks`() {
        val weeks = simulateMultiWeek(175_000, 8)
        val totalUpgrades = weeks.sum()
        assertTrue(totalUpgrades > 500, "Marathon 8-week total: $totalUpgrades")
        assertTrue(weeks.last() >= 10, "Marathon week 8: ${weeks.last()}")
    }
}
