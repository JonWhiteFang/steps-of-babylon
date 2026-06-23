package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CalculateDamageTest {
    private val eps = 0.001

    @Test
    fun `no crit when random rolls high`() {
        val sut = CalculateDamage(Random(seed = 0)) // we override below
        // Use a random that always returns 1.0 (above any crit chance)
        val noCritCalc =
            CalculateDamage(
                object : Random() {
                    override fun nextBits(bitCount: Int) = 0

                    override fun nextDouble() = 1.0
                },
            )
        val stats = ResolvedStats(damage = 100.0, critChance = 0.5)
        val result = noCritCalc(stats, 0f)
        assertFalse(result.isCrit)
        assertEquals(100.0, result.amount, eps)
    }

    @Test
    fun `guaranteed crit when random rolls 0`() {
        val alwaysCrit =
            CalculateDamage(
                object : Random() {
                    override fun nextBits(bitCount: Int) = 0

                    override fun nextDouble() = 0.0
                },
            )
        val stats = ResolvedStats(damage = 100.0, critChance = 0.5, critMultiplier = 3.0)
        val result = alwaysCrit(stats, 0f)
        assertTrue(result.isCrit)
        assertEquals(300.0, result.amount, eps)
    }

    @Test
    fun `damage per meter bonus at max range`() {
        val noCrit =
            CalculateDamage(
                object : Random() {
                    override fun nextBits(bitCount: Int) = 0

                    override fun nextDouble() = 1.0
                },
            )
        val stats = ResolvedStats(damage = 100.0, range = 300f, damagePerMeterBonus = 0.10)
        val result = noCrit(stats, 300f) // distance = range
        assertEquals(100.0 * (1 + 1.0 * 0.10), result.amount, eps) // 110.0
    }

    @Test
    fun `damage per meter bonus at zero distance`() {
        val noCrit =
            CalculateDamage(
                object : Random() {
                    override fun nextBits(bitCount: Int) = 0

                    override fun nextDouble() = 1.0
                },
            )
        val stats = ResolvedStats(damage = 100.0, range = 300f, damagePerMeterBonus = 0.10)
        val result = noCrit(stats, 0f)
        assertEquals(100.0, result.amount, eps)
    }

    @Test
    fun `zero crit chance never crits`() {
        val alwaysLow =
            CalculateDamage(
                object : Random() {
                    override fun nextBits(bitCount: Int) = 0

                    override fun nextDouble() = 0.0
                },
            )
        val stats = ResolvedStats(damage = 50.0, critChance = 0.0)
        val result = alwaysLow(stats, 0f)
        assertFalse(result.isCrit)
    }
}
