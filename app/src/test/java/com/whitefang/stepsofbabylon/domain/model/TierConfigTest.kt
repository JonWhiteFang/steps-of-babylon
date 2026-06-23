package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TierConfigTest {
    @Test
    fun `all 10 tiers are retrievable`() {
        (1..10).forEach { TierConfig.forTier(it) }
    }

    @Test
    fun `tier 1 has no conditions and multiplier 1`() {
        val t = TierConfig.forTier(1)
        assertEquals(1.0, t.cashMultiplier)
        assertTrue(t.battleConditions.isEmpty())
        assertEquals(0, t.unlockWaveRequirement)
    }

    @Test
    fun `tier 6 is first with battle conditions`() {
        (1..5).forEach { assertTrue(TierConfig.forTier(it).battleConditions.isEmpty()) }
        assertTrue(TierConfig.forTier(6).battleConditions.isNotEmpty())
    }

    @Test
    fun `tier 10 has all 7 battle condition types`() {
        assertEquals(7, TierConfig.forTier(10).battleConditions.size)
        assertEquals(BattleCondition.entries.toSet(), TierConfig.forTier(10).battleConditions.keys)
    }

    @Test
    fun `invalid tier throws`() {
        assertThrows<IllegalArgumentException> { TierConfig.forTier(0) }
        assertThrows<IllegalArgumentException> { TierConfig.forTier(11) }
    }
}
