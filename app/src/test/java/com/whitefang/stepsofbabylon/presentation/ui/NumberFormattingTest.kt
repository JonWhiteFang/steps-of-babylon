package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * #262 L87: `formatCount` is the single number-grouping helper, pinned to `Locale.US` so player-facing
 * counts render with deterministic `,` grouping separators regardless of the device's default locale
 * (previously three different mechanisms existed, two using the JVM default locale).
 */
class NumberFormattingTest {
    @Test
    fun `formatCount uses US grouping even under a non-US default locale`() {
        val original = Locale.getDefault()
        try {
            // GERMANY default would group as 1.234.567 if the helper used the default locale.
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,234,567", formatCount(1_234_567))
            assertEquals("0", formatCount(0))
            assertEquals("1,000", formatCount(1_000))
        } finally {
            Locale.setDefault(original)
        }
    }
}
