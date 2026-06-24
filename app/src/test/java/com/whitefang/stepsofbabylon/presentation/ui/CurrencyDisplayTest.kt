package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #34 i18n: `label()` now returns a `@StringRes Int` resolved at the call site, so the exact-text
 * assertions resolve the resource via Robolectric (mirrors EnumLabelResTest). `formatCurrency` stays
 * a pure-JVM grouping check, run on the same Robolectric class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CurrencyDisplayTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun str(id: Int) = ctx.getString(id)

    @Test
    fun `label returns the plural-noun form for each currency`() {
        assertEquals("Steps", str(CurrencyType.STEPS.label()))
        assertEquals("Cash", str(CurrencyType.CASH.label()))
        assertEquals("Gems", str(CurrencyType.GEMS.label()))
        assertEquals("Power Stones", str(CurrencyType.POWER_STONES.label()))
    }

    @Test
    fun `formatCurrency inserts grouping separators`() {
        assertEquals("0", formatCurrency(0))
        assertEquals("999", formatCurrency(999))
        assertEquals("1,000", formatCurrency(1_000))
        assertEquals("1,234,567", formatCurrency(1_234_567))
    }
}
