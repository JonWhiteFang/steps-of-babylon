package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Combat-power index (#29, spec §2): a steady-state single-target DPS *proxy*
 * = damage × attackSpeed × (1 + critChance × (critMultiplier − 1)).
 * Deliberately ignores multishot/bounce/orbs/range/sustain.
 */
class CombatPowerTest {
    private val sut = CombatPower()

    @Test
    fun `base stats with no crit equal damage times attackSpeed`() {
        // ResolvedStats defaults: damage 10, attackSpeed 1, critChance 0, critMultiplier 2.
        val power = sut(ResolvedStats())
        assertEquals(10.0, power, 1e-9)
    }

    @Test
    fun `damage scales power linearly`() {
        val power = sut(ResolvedStats(damage = 20.0, attackSpeed = 1.0, critChance = 0.0))
        assertEquals(20.0, power, 1e-9)
    }

    @Test
    fun `attack speed scales power linearly`() {
        val power = sut(ResolvedStats(damage = 10.0, attackSpeed = 2.0, critChance = 0.0))
        assertEquals(20.0, power, 1e-9)
    }

    @Test
    fun `crit chance and multiplier raise power via expected-crit factor`() {
        // 10 × 1 × (1 + 0.5 × (3 − 1)) = 10 × 2 = 20.
        val power = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.5, critMultiplier = 3.0))
        assertEquals(20.0, power, 1e-9)
    }

    @Test
    fun `crit factor with zero crit chance does not change power`() {
        // The §3.3 synergy: (1 + 0 × (critMultiplier − 1)) = 1 for any critMultiplier.
        val low = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.0, critMultiplier = 2.0))
        val high = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.0, critMultiplier = 5.0))
        assertEquals(low, high, 1e-9)
    }

    @Test
    fun `multishot bounce orbs and range do NOT affect the index`() {
        val plain = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.0))
        val decorated =
            sut(
                ResolvedStats(
                    damage = 10.0,
                    attackSpeed = 1.0,
                    critChance = 0.0,
                    multishotTargets = 5,
                    bounceCount = 4,
                    orbCount = 6,
                    range = 900f,
                ),
            )
        assertEquals(plain, decorated, 1e-9)
    }

    @Test
    fun `more of any contributing stat strictly increases power`() {
        val baseline = sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.1, critMultiplier = 2.0))
        assertTrue(
            sut(ResolvedStats(damage = 11.0, attackSpeed = 1.0, critChance = 0.1, critMultiplier = 2.0)) > baseline,
        )
        assertTrue(
            sut(ResolvedStats(damage = 10.0, attackSpeed = 1.1, critChance = 0.1, critMultiplier = 2.0)) > baseline,
        )
        assertTrue(
            sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.2, critMultiplier = 2.0)) > baseline,
        )
        assertTrue(
            sut(ResolvedStats(damage = 10.0, attackSpeed = 1.0, critChance = 0.1, critMultiplier = 3.0)) > baseline,
        )
    }
}
