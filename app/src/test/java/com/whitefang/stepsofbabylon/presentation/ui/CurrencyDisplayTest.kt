package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrencyDisplayTest {
    @Test
    fun `label returns the plural-noun form for each currency`() {
        assertEquals("Steps", CurrencyType.STEPS.label())
        assertEquals("Cash", CurrencyType.CASH.label())
        assertEquals("Gems", CurrencyType.GEMS.label())
        assertEquals("Power Stones", CurrencyType.POWER_STONES.label())
    }

    @Test
    fun `formatCurrency inserts grouping separators`() {
        assertEquals("0", formatCurrency(0))
        assertEquals("999", formatCurrency(999))
        assertEquals("1,000", formatCurrency(1_000))
        assertEquals("1,234,567", formatCurrency(1_234_567))
    }
}
