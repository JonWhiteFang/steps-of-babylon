package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalculateResearchTimeTest {
    private val calculate = CalculateResearchTime()

    @Test
    fun `level 0 returns base time`() {
        assertEquals(4.0, calculate(ResearchType.DAMAGE_RESEARCH, 0))
        assertEquals(24.0, calculate(ResearchType.WAVE_SKIP, 0))
    }

    @Test
    fun `time scales with level`() {
        val level5 = calculate(ResearchType.DAMAGE_RESEARCH, 5)
        assert(level5 > 4.0) { "Level 5 time ($level5) should exceed base time (4.0)" }
    }

    @Test
    fun `level 10 is approximately 2_6x base`() {
        val base = ResearchType.DAMAGE_RESEARCH.baseTimeHours
        val level10 = calculate(ResearchType.DAMAGE_RESEARCH, 10)
        val ratio = level10 / base
        // 1.10^10 ≈ 2.594
        assert(ratio in 2.5..2.7) { "Expected ~2.59x, got ${ratio}x" }
    }
}
