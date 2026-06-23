package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import com.whitefang.stepsofbabylon.presentation.battle.engine.EnemyScaler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.min

/**
 * Validates in-round cash economy supports meaningful upgrade decisions.
 */
class CashEconomyTest {
    private val calcCost = CalculateUpgradeCost()

    /** Estimate cash earned from kills in a wave (Tier 1, no bonuses). */
    private fun cashFromWave(wave: Int): Long {
        val enemyCount = min(5 + (wave - 1) * 2, 40)
        // Simplified: assume all Basic enemies
        val cashPerKill = EnemyScaler.cashReward(EnemyType.BASIC)
        val killCash = cashPerKill * enemyCount
        val waveCash = SimulationMath.BASE_CASH_PER_WAVE
        return killCash + waveCash
    }

    @Test
    fun `by wave 5 player can afford at least 2 in-round upgrades`() {
        var totalCash = 0L
        for (w in 1..5) totalCash += cashFromWave(w)
        // Cheapest in-round upgrade at level 0
        val cheapest = UpgradeType.entries.minOf { calcCost(it, 0) }
        val affordableUpgrades = totalCash / cheapest
        assertTrue(
            affordableUpgrades >= 2,
            "By wave 5: $totalCash cash, cheapest upgrade $cheapest, can afford $affordableUpgrades",
        )
    }

    @Test
    fun `by wave 15 player has earned enough for 8 plus upgrades`() {
        var totalCash = 0L
        for (w in 1..15) totalCash += cashFromWave(w)
        // Assume average upgrade cost increases — use level 0-7 costs for cheapest type
        var spent = 0L
        var count = 0
        val cheapestType = UpgradeType.entries.minByOrNull { calcCost(it, 0) }!!
        for (level in 0..20) {
            val cost = calcCost(cheapestType, level)
            if (spent + cost > totalCash) break
            spent += cost
            count++
        }
        assertTrue(count >= 8, "By wave 15: $totalCash cash, bought $count upgrades of ${cheapestType.name}")
    }

    @Test
    fun `interest at max level does not dominate kill income`() {
        // Max interest: 10% of held cash per wave
        // Simulate 10 waves with max interest, track interest vs kill income
        var cash = 0L
        var totalInterest = 0L
        var totalKillCash = 0L
        for (w in 1..10) {
            val killCash = cashFromWave(w)
            totalKillCash += killCash
            cash += killCash
            val interest = (cash * 0.10).toLong()
            totalInterest += interest
            cash += interest
        }
        val interestRatio = totalInterest.toDouble() / totalKillCash
        assertTrue(
            interestRatio < 0.65,
            "Interest ratio: $interestRatio (should be <0.65, interest shouldn't dominate)",
        )
    }
}
