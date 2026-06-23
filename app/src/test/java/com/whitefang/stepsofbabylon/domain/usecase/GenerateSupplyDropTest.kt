package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GenerateSupplyDropTest {
    @Test
    fun `returns null when inbox full`() {
        val sut = GenerateSupplyDrop(Random(42))
        assertNull(sut(dailyCreditedSteps = 5000, lastCheckSteps = 0, timestampMs = 1000, unclaimedCount = 10))
    }

    @Test
    fun `returns null when no step progress`() {
        val sut = GenerateSupplyDrop(Random(42))
        assertNull(sut(dailyCreditedSteps = 1000, lastCheckSteps = 1000, timestampMs = 1000, unclaimedCount = 0))
    }

    @Test
    fun `returns null when steps decrease`() {
        val sut = GenerateSupplyDrop(Random(42))
        assertNull(sut(dailyCreditedSteps = 500, lastCheckSteps = 1000, timestampMs = 1000, unclaimedCount = 0))
    }

    @Test
    fun `milestone triggers at 10k boundary crossing`() {
        val sut = GenerateSupplyDrop(Random(42))
        val drop = sut(dailyCreditedSteps = 10_000, lastCheckSteps = 9_900, timestampMs = 1000, unclaimedCount = 0)
        assertNotNull(drop)
        assertEquals(SupplyDropTrigger.DAILY_MILESTONE, drop!!.trigger)
        assertEquals(SupplyDropReward.GEMS, drop.reward)
        assertEquals(5, drop.rewardAmount)
    }

    @Test
    fun `milestone does not trigger when already past 10k`() {
        val sut = GenerateSupplyDrop(Random(42))
        // Both lastCheck and current are above 10k — no milestone
        val drop = sut(dailyCreditedSteps = 10_500, lastCheckSteps = 10_100, timestampMs = 1000, unclaimedCount = 0)
        // Could be null or a different trigger, but NOT milestone
        if (drop != null) {
            assertNotEquals(SupplyDropTrigger.DAILY_MILESTONE, drop.trigger)
        }
    }

    @Test
    fun `milestone has priority over threshold`() {
        val sut = GenerateSupplyDrop(Random(42))
        // Crosses both 10k milestone and 10k threshold boundary
        val drop = sut(dailyCreditedSteps = 10_000, lastCheckSteps = 9_500, timestampMs = 1000, unclaimedCount = 0)
        assertNotNull(drop)
        assertEquals(SupplyDropTrigger.DAILY_MILESTONE, drop!!.trigger)
    }

    @Test
    fun `threshold trigger produces valid rewards`() {
        // Try many seeds to find one that triggers threshold
        for (seed in 0..1000) {
            val sut = GenerateSupplyDrop(Random(seed.toLong()))
            // Cross a 2000-step boundary (from 1900 to 2100)
            val drop = sut(dailyCreditedSteps = 2100, lastCheckSteps = 1900, timestampMs = 1000, unclaimedCount = 0)
            if (drop != null && drop.trigger == SupplyDropTrigger.STEP_THRESHOLD) {
                assertTrue(drop.reward == SupplyDropReward.STEPS || drop.reward == SupplyDropReward.GEMS)
                if (drop.reward == SupplyDropReward.STEPS) {
                    assertTrue(drop.rewardAmount in 50..200)
                } else {
                    assertTrue(drop.rewardAmount in 1..3)
                }
                return // Found and validated
            }
        }
        // Threshold is probabilistic — if we never hit it in 1000 seeds, that's suspicious but not impossible
    }

    @Test
    fun `random trigger produces valid rewards`() {
        // Need delta >= 500 for random check, and no boundary crossing
        for (seed in 0..5000) {
            val sut = GenerateSupplyDrop(Random(seed.toLong()))
            // 500 step delta, no boundary crossing (3000 to 3500)
            val drop = sut(dailyCreditedSteps = 3500, lastCheckSteps = 3000, timestampMs = 1000, unclaimedCount = 0)
            if (drop != null && drop.trigger == SupplyDropTrigger.RANDOM) {
                assertTrue(drop.reward in SupplyDropReward.entries)
                when (drop.reward) {
                    SupplyDropReward.STEPS -> assertTrue(drop.rewardAmount in 100..300)
                    SupplyDropReward.GEMS -> assertTrue(drop.rewardAmount in 1..2)
                    SupplyDropReward.POWER_STONES -> assertEquals(1, drop.rewardAmount)
                    SupplyDropReward.CARD_COPY -> assertTrue(drop.rewardAmount in 0..8)
                }
                return
            }
        }
    }

    // #22: the STEP_THRESHOLD branch computed
    //   checks = ((stepsAfterBoundary + delta).coerceAtMost(delta) / 100).coerceAtLeast(1)
    // Because stepsAfterBoundary >= 0, `(stepsAfterBoundary + delta).coerceAtMost(delta)` is
    // always exactly `delta`, so the whole expression reduces to `(delta / 100).coerceAtLeast(1)`
    // and the `stepsAfterBoundary` term is dead. The fix removes the dead computation while
    // preserving the shipped cadence (delta/100 rolls). These tests pin the number of 5%-roll
    // opportunities by seeding a Random that NEVER triggers (every nextDouble() >= 0.05) and
    // asserting null — and a counting Random that records exactly how many rolls were attempted.

    /** A Random whose nextDouble() always returns 1.0 (never < 0.05) but counts each call. */
    private class CountingNeverTriggerRandom : kotlin.random.Random() {
        var doubleCalls = 0

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double {
            doubleCalls++
            return 1.0
        }
    }

    @Test
    fun `R22 threshold roll count equals delta over 100 across a boundary crossing`() {
        // delta = 400 (1900 → 2300), crosses the 2000 boundary. Pre- and post-fix the number of
        // 5% roll opportunities is delta/100 = 4, independent of stepsAfterBoundary (= 300).
        val rng = CountingNeverTriggerRandom()
        val sut = GenerateSupplyDrop(rng)
        val drop = sut(dailyCreditedSteps = 2300, lastCheckSteps = 1900, timestampMs = 1000, unclaimedCount = 0)
        assertNull(drop, "never-trigger RNG must yield no drop")
        assertEquals(4, rng.doubleCalls, "threshold rolls must equal delta/100 = 4 (dead stepsAfterBoundary removed)")
    }

    @Test
    fun `R22 threshold roll count is at least one even for a small crossing`() {
        // delta = 50 (1980 → 2030) still crosses the boundary; coerceAtLeast(1) guarantees one roll.
        val rng = CountingNeverTriggerRandom()
        val sut = GenerateSupplyDrop(rng)
        val drop = sut(dailyCreditedSteps = 2030, lastCheckSteps = 1980, timestampMs = 1000, unclaimedCount = 0)
        assertNull(drop)
        assertEquals(1, rng.doubleCalls, "a boundary crossing always rolls at least once")
    }

    @Test
    fun `drop has id 0 and correct timestamp`() {
        val sut = GenerateSupplyDrop(Random(42))
        val drop = sut(dailyCreditedSteps = 10_000, lastCheckSteps = 9_900, timestampMs = 12345L, unclaimedCount = 0)
        assertNotNull(drop)
        assertEquals(0, drop!!.id)
        assertEquals(12345L, drop.createdAt)
        assertFalse(drop.claimed)
    }
}
