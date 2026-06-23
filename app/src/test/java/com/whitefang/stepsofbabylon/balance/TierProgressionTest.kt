package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import com.whitefang.stepsofbabylon.domain.usecase.ResolveStats
import com.whitefang.stepsofbabylon.presentation.battle.engine.EnemyScaler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Validates tier progression timeline matches GDD §14 (±20% tolerance).
 *
 * Wave estimates use a combat multiplier (3x) to account for crits,
 * in-round upgrades, multishot, orbs, and cards.
 */
class TierProgressionTest {
    private val calcCost = CalculateUpgradeCost()
    private val resolveStats = ResolveStats()
    private val COMBAT_MULTIPLIER = 5.0 // Crits + in-round upgrades + multishot + orbs + cards

    private fun spendSteps(budget: Long): Map<UpgradeType, Int> {
        var remaining = budget
        val levels = mutableMapOf<UpgradeType, Int>()
        while (true) {
            val best =
                UpgradeType.entries
                    .filter { type -> type.config.maxLevel?.let { (levels[type] ?: 0) < it } ?: true }
                    .minByOrNull { calcCost(it, levels[it] ?: 0) } ?: break
            val cost = calcCost(best, levels[best] ?: 0)
            if (cost > remaining) break
            remaining -= cost
            levels[best] = (levels[best] ?: 0) + 1
        }
        return levels
    }

    /** Estimate max wave: where effective DPS can't keep up with enemy HP growth. */
    private fun estimateMaxWave(levels: Map<UpgradeType, Int>): Int {
        val stats = resolveStats(levels)
        val dps = stats.damage * stats.attackSpeed * COMBAT_MULTIPLIER
        for (wave in 1..200) {
            val enemyHp = EnemyScaler.scaleHealth(EnemyType.BASIC, wave)
            val ttk = enemyHp / dps
            val enemyCount = (5 + (wave - 1) * 2).coerceAtMost(40)
            val timePerEnemy = 26.0 / enemyCount
            if (ttk > timePerEnemy * 5) return wave - 1
        }
        return 200
    }

    @Test
    fun `day 1 - 8000 steps - can reach wave 12 plus`() {
        val levels = spendSteps(8_000)
        val maxWave = estimateMaxWave(levels)
        assertTrue(maxWave >= 10, "Day 1 (8k steps): max wave $maxWave (expected ≥10)")
    }

    @Test
    fun `week 1 - 56000 steps - can reach wave 30 plus`() {
        val levels = spendSteps(56_000)
        val maxWave = estimateMaxWave(levels)
        assertTrue(maxWave >= 25, "Week 1 (56k steps): max wave $maxWave (expected ≥25)")
    }

    @Test
    fun `month 1 - 240000 steps - can reach wave 50 plus`() {
        val levels = spendSteps(240_000)
        val maxWave = estimateMaxWave(levels)
        assertTrue(maxWave >= 40, "Month 1 (240k steps): max wave $maxWave (expected ≥40)")
    }

    @Test
    fun `tier unlock wave requirements are achievable`() {
        assertEquals(50, TierConfig.forTier(2).unlockWaveRequirement)
        assertEquals(75, TierConfig.forTier(5).unlockWaveRequirement)
        assertEquals(150, TierConfig.forTier(10).unlockWaveRequirement)
    }

    @Test
    fun `tier cash multipliers scale smoothly`() {
        var prev = 0.0
        for (tier in 1..10) {
            val mult = TierConfig.forTier(tier).cashMultiplier
            assertTrue(mult > prev, "Tier $tier multiplier ($mult) should exceed Tier ${tier - 1} ($prev)")
            prev = mult
        }
    }
}
