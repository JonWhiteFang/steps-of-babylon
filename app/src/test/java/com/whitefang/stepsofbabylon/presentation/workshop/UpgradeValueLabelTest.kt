package com.whitefang.stepsofbabylon.presentation.workshop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpgradeValueLabelTest {
    @Test
    fun `formats one decimal place with leading plus and per-1000-steps suffix`() {
        assertEquals("+4.0% power / 1,000 steps", formatPowerPerKStepsLabel(4.0))
    }

    @Test
    fun `rounds to one decimal`() {
        assertEquals("+1.6% power / 1,000 steps", formatPowerPerKStepsLabel(1.5567))
    }

    @Test
    fun `a positive value that would round to zero is floored to plus 0_1 percent`() {
        // Critical Factor at very low crit chance can be tiny-but-positive; never show "+0.0%".
        assertEquals("+0.1% power / 1,000 steps", formatPowerPerKStepsLabel(0.0001))
    }

    @Test
    fun `uses Locale ROOT decimal point regardless of default locale`() {
        // Would be "+12,5%" under a comma-decimal locale; ROOT pins the dot.
        assertEquals("+12.5% power / 1,000 steps", formatPowerPerKStepsLabel(12.5))
    }
}
