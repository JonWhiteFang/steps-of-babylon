package com.whitefang.stepsofbabylon.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CheckTierUnlockTest {
    private val sut = CheckTierUnlock()

    @Test
    fun `no best waves returns null`() {
        assertNull(sut(emptyMap(), 1))
    }

    @Test
    fun `wave 50 on tier 1 unlocks tier 2`() {
        assertEquals(2, sut(mapOf(1 to 50), 1))
    }

    @Test
    fun `wave 49 on tier 1 does not unlock`() {
        assertNull(sut(mapOf(1 to 49), 1))
    }

    @Test
    fun `wave 50 on tiers 1-3 unlocks tier 4`() {
        val waves = mapOf(1 to 50, 2 to 50, 3 to 50)
        assertEquals(4, sut(waves, 1))
    }

    @Test
    fun `wave 150 on tier 9 unlocks tier 10`() {
        assertEquals(10, sut(mapOf(9 to 150), 9))
    }

    @Test
    fun `already at tier 10 returns null`() {
        assertNull(sut(mapOf(9 to 999), 10))
    }

    @Test
    fun `chain unlock stops at first unmet requirement`() {
        // Wave 50 on tier 1 and 2, but not tier 3 → unlocks up to tier 3
        val waves = mapOf(1 to 50, 2 to 50)
        assertEquals(3, sut(waves, 1))
    }
}
