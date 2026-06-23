package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkshopLevelsTest {
    @Test
    fun `levelOf returns the map value or zero when absent`() {
        assertEquals(7, WorkshopLevels.levelOf(mapOf(UpgradeType.DAMAGE to 7), UpgradeType.DAMAGE))
        assertEquals(0, WorkshopLevels.levelOf(emptyMap(), UpgradeType.DAMAGE))
    }

    @Test
    fun `isAtMax is false for an uncapped upgrade regardless of level`() {
        // DAMAGE has maxLevel = null.
        assertFalse(WorkshopLevels.isAtMax(mapOf(UpgradeType.DAMAGE to 9999), UpgradeType.DAMAGE))
    }

    @Test
    fun `isAtMax is true only at or above the workshop cap`() {
        // CRITICAL_CHANCE maxLevel = 160.
        val type = UpgradeType.CRITICAL_CHANCE
        assertFalse(WorkshopLevels.isAtMax(mapOf(type to 159), type))
        assertTrue(WorkshopLevels.isAtMax(mapOf(type to 160), type))
        assertTrue(WorkshopLevels.isAtMax(mapOf(type to 161), type))
    }

    @Test
    fun `withIncremented bumps only the target type by one`() {
        val before = mapOf(UpgradeType.DAMAGE to 3, UpgradeType.ATTACK_SPEED to 5)
        val after = WorkshopLevels.withIncremented(before, UpgradeType.DAMAGE)
        assertEquals(4, after[UpgradeType.DAMAGE])
        assertEquals(5, after[UpgradeType.ATTACK_SPEED])
    }

    @Test
    fun `withIncremented treats an absent type as level zero`() {
        val after = WorkshopLevels.withIncremented(emptyMap(), UpgradeType.DAMAGE)
        assertEquals(1, after[UpgradeType.DAMAGE])
    }
}
