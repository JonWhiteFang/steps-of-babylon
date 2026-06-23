package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalculateDefenseTest {
    private val sut = CalculateDefense()
    private val eps = 0.001

    @Test
    fun `zero defense passes full damage`() {
        assertEquals(100.0, sut(100.0, ResolvedStats()), eps)
    }

    @Test
    fun `75 percent defense reduces to 25 percent`() {
        val stats = ResolvedStats(defensePercent = 0.75)
        assertEquals(25.0, sut(100.0, stats), eps)
    }

    @Test
    fun `flat block only`() {
        val stats = ResolvedStats(defenseAbsolute = 30.0)
        assertEquals(70.0, sut(100.0, stats), eps)
    }

    @Test
    fun `combined percent and flat block`() {
        val stats = ResolvedStats(defensePercent = 0.50, defenseAbsolute = 10.0)
        // 100 * 0.5 - 10 = 40
        assertEquals(40.0, sut(100.0, stats), eps)
    }

    @Test
    fun `result never below zero`() {
        val stats = ResolvedStats(defensePercent = 0.75, defenseAbsolute = 100.0)
        assertEquals(0.0, sut(10.0, stats), eps)
    }

    @Test
    fun `large incoming damage`() {
        val stats = ResolvedStats(defensePercent = 0.50, defenseAbsolute = 5.0)
        assertEquals(10000.0 * 0.5 - 5.0, sut(10000.0, stats), eps)
    }
}
