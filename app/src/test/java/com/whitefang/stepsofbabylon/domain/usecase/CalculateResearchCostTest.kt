package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalculateResearchCostTest {
    private val calculate = CalculateResearchCost()

    @Test
    fun `level 0 returns base cost`() {
        assertEquals(2_000L, calculate(ResearchType.DAMAGE_RESEARCH, 0))
        assertEquals(10_000L, calculate(ResearchType.WAVE_SKIP, 0))
    }

    @Test
    fun `cost scales with level`() {
        val level5 = calculate(ResearchType.DAMAGE_RESEARCH, 5)
        val level0 = calculate(ResearchType.DAMAGE_RESEARCH, 0)
        assert(level5 > level0) { "Level 5 cost ($level5) should exceed level 0 cost ($level0)" }
    }

    @Test
    fun `level 10 is approximately 4x base`() {
        val base = ResearchType.DAMAGE_RESEARCH.baseCostSteps
        val level10 = calculate(ResearchType.DAMAGE_RESEARCH, 10)
        val ratio = level10.toDouble() / base
        // 1.15^10 ≈ 4.046
        assert(ratio in 4.0..4.1) { "Expected ~4.05x, got ${ratio}x" }
    }

    @Test
    fun `respects per-type base costs`() {
        val damage = calculate(ResearchType.DAMAGE_RESEARCH, 3)
        val cash = calculate(ResearchType.CASH_RESEARCH, 3)
        // Both at same level, different base costs
        assert(damage > cash) { "Damage (base 2000) at level 3 should cost more than Cash (base 1500)" }
    }
}
