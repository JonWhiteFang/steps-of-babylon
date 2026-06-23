package com.whitefang.stepsofbabylon.data.sensor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepVelocityAnalyzerTest {
    @Test
    fun `insufficient data returns 1_0`() {
        val analyzer = StepVelocityAnalyzer()
        assertEquals(1.0, analyzer.analyze(100, 1000))
        assertEquals(1.0, analyzer.analyze(100, 61_000))
        assertEquals(1.0, analyzer.analyze(100, 121_000))
    }

    @Test
    fun `natural walking pattern returns 1_0`() {
        val analyzer = StepVelocityAnalyzer()
        val deltas = listOf(95L, 110L, 88L, 105L, 92L, 115L, 98L, 107L, 90L, 103L, 112L, 85L)
        var result = 1.0
        for ((i, delta) in deltas.withIndex()) {
            result = analyzer.analyze(delta, i * 60_000L)
        }
        assertEquals(1.0, result)
    }

    @Test
    fun `constant rate flags suspicious`() {
        val analyzer = StepVelocityAnalyzer()
        var result = 1.0
        for (i in 0 until 12) {
            result = analyzer.analyze(150, i * 60_000L)
        }
        assertEquals(0.5, result)
    }

    @Test
    fun `instant jump flags suspicious`() {
        val analyzer = StepVelocityAnalyzer()
        for (i in 0 until 5) {
            analyzer.analyze(5, i * 60_000L)
        }
        val result = analyzer.analyze(180, 5 * 60_000L)
        assertEquals(0.5, result)
    }

    @Test
    fun `shaker scenario - jump then constant rate stays penalized`() {
        val analyzer = StepVelocityAnalyzer()
        // Idle baseline
        for (i in 0 until 4) {
            analyzer.analyze(5, i * 60_000L)
        }
        // Jump to constant rate — initially flagged as jump
        val jumpResult = analyzer.analyze(160, 4 * 60_000L)
        assertEquals(0.5, jumpResult, "Jump should flag immediately")

        // Constant rate continues — eventually flagged as constant rate
        var result = 1.0
        for (i in 5 until 15) {
            result = analyzer.analyze(160, i * 60_000L)
        }
        assertEquals(0.5, result, "Constant rate should sustain penalty")
    }

    @Test
    fun `window eviction allows recovery`() {
        val analyzer = StepVelocityAnalyzer()
        for (i in 0 until 12) {
            analyzer.analyze(150, i * 60_000L)
        }
        val baseTime = 16 * 60_000L
        var result = 1.0
        val deltas = listOf(95L, 110L, 88L, 105L, 92L, 115L)
        for ((i, delta) in deltas.withIndex()) {
            result = analyzer.analyze(delta, baseTime + i * 60_000L)
        }
        assertEquals(1.0, result)
    }
}
