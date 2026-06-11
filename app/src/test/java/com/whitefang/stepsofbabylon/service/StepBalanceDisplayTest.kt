package com.whitefang.stepsofbabylon.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * #43: the step-service collector read the balance with
 *   `try { …observeProfile().first().stepBalance } catch (_) { 0L }`
 * and passed it straight to the always-on notification. A transient repository failure (SQLCipher
 * open contention, a momentary Room exception) therefore flashed "Balance: 0" — alarming wrong
 * data that looks like the player lost all their Steps. The fix routes the read through
 * [resolveDisplayBalance], which folds a freshly-read balance (null = read failed) against the
 * last-known-good value and NEVER coerces a failure to 0. A genuine zero balance is still shown;
 * only a failed read is protected.
 */
class StepBalanceDisplayTest {

    @Test
    fun `R43 a failed read keeps the last known good balance instead of showing zero`() {
        assertEquals(
            12000L,
            resolveDisplayBalance(fresh = null, lastKnown = 12000L),
            "a read failure must retain the last good balance, not coerce to 0",
        )
    }

    @Test
    fun `R43 a successful read replaces the last known balance`() {
        assertEquals(5000L, resolveDisplayBalance(fresh = 5000L, lastKnown = 12000L))
    }

    @Test
    fun `R43 a genuine zero balance is still displayed`() {
        // The protection is for read FAILURES (null) only — a real balance of 0 (fresh install)
        // must still render as 0, not be mistaken for an error.
        assertEquals(0L, resolveDisplayBalance(fresh = 0L, lastKnown = 12000L))
    }

    @Test
    fun `R43 a failed read before any successful read yields null (no known balance to show)`() {
        // Cold start: no last-good value yet. The helper returns null = "balance unknown"; the
        // caller then falls back to 0 for display (matching the initial onCreate notification),
        // which can never regress a KNOWN non-zero balance because there is none yet.
        assertNull(resolveDisplayBalance(fresh = null, lastKnown = null))
    }
}
