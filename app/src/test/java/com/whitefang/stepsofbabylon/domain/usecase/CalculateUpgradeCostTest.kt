package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ceil
import kotlin.math.pow

class CalculateUpgradeCostTest {
    private val sut = CalculateUpgradeCost()

    @Test
    fun `level 0 returns baseCost`() {
        UpgradeType.entries.forEach { type ->
            assertEquals(type.config.baseCost, sut(type, 0))
        }
    }

    @Test
    fun `DAMAGE at level 10 equals 156`() {
        assertEquals(156L, sut(UpgradeType.DAMAGE, 10))
    }

    @Test
    fun `ATTACK_SPEED at level 20`() {
        val expected = ceil(75.0 * 1.15.pow(20)).toLong()
        assertEquals(expected, sut(UpgradeType.ATTACK_SPEED, 20))
    }

    @Test
    fun `all types produce positive costs at level 0`() {
        UpgradeType.entries.forEach { type ->
            assertTrue(sut(type, 0) > 0, "$type cost should be positive")
        }
    }

    @Test
    fun `cost increases with level`() {
        UpgradeType.entries.forEach { type ->
            assertTrue(sut(type, 1) > sut(type, 0), "$type cost should increase at level 1")
        }
    }

    @Test
    fun `large level does not overflow`() {
        val cost = sut(UpgradeType.DAMAGE, 200)
        assertTrue(cost > 0, "Cost at level 200 should be positive (no overflow)")
    }
}
