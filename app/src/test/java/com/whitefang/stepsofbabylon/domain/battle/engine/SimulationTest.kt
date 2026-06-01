package com.whitefang.stepsofbabylon.domain.battle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [Simulation] — the pure-domain in-round cash economy extracted from
 * `presentation/battle/engine/GameEngine` during V1X-09 Phase 3 (ADR-0012, slice 1).
 *
 * Pure JVM (no Robolectric, no Android imports). These directly exercise the cash
 * state + mutation primitives that GameEngine previously held inline and that
 * GameEngineTest could only reach through Robolectric + reflection.
 */
class SimulationTest {

    // ---- creditCash ----

    @Test
    fun `creditCash accumulates both balance and lifetime earned`() {
        val sim = Simulation()
        sim.creditCash(50L)
        assertEquals(50L, sim.cash)
        assertEquals(50L, sim.totalCashEarned)
    }

    @Test
    fun `creditCash sums across multiple credits`() {
        val sim = Simulation()
        sim.creditCash(50L)
        sim.creditCash(30L)
        assertEquals(80L, sim.cash)
        assertEquals(80L, sim.totalCashEarned)
    }

    @Test
    fun `creditCash ignores non-positive amounts`() {
        val sim = Simulation()
        sim.creditCash(0L)
        sim.creditCash(-100L)
        assertEquals(0L, sim.cash)
        assertEquals(0L, sim.totalCashEarned)
    }

    // ---- applyInterest ----

    @Test
    fun `applyInterest adds to balance but not lifetime earned`() {
        val sim = Simulation()
        sim.creditCash(1000L)
        sim.applyInterest(5) // 5 * 0.005 = 2.5% of 1000 = 25
        assertEquals(1025L, sim.cash)
        assertEquals(1000L, sim.totalCashEarned)
    }

    @Test
    fun `applyInterest caps at 10 percent`() {
        val sim = Simulation()
        sim.creditCash(1000L)
        sim.applyInterest(30) // 30 * 0.005 = 15% but capped at 10% = 100
        assertEquals(1100L, sim.cash)
    }

    @Test
    fun `applyInterest is a no-op at level 0`() {
        val sim = Simulation()
        sim.creditCash(1000L)
        sim.applyInterest(0)
        assertEquals(1000L, sim.cash)
    }

    // ---- spend ----

    @Test
    fun `spend deducts and returns true when affordable`() {
        val sim = Simulation()
        sim.creditCash(100L)
        assertTrue(sim.spend(60L))
        assertEquals(40L, sim.cash)
        assertEquals(100L, sim.totalCashEarned) // spending never reduces lifetime earned
    }

    @Test
    fun `spend returns false and leaves balance untouched when insufficient`() {
        val sim = Simulation()
        sim.creditCash(50L)
        assertFalse(sim.spend(60L))
        assertEquals(50L, sim.cash)
    }

    // ---- reset ----

    @Test
    fun `reset zeroes both balances`() {
        val sim = Simulation()
        sim.creditCash(1000L)
        sim.spend(200L)
        sim.reset()
        assertEquals(0L, sim.cash)
        assertEquals(0L, sim.totalCashEarned)
    }

    // ---- round-progress counters ----

    @Test
    fun `tickElapsed accumulates the round clock`() {
        val sim = Simulation()
        sim.tickElapsed(0.5f)
        sim.tickElapsed(0.25f)
        assertEquals(0.75f, sim.elapsedSeconds, 1e-4f)
    }

    @Test
    fun `recordEnemyKilled increments the kill counter`() {
        val sim = Simulation()
        sim.recordEnemyKilled()
        sim.recordEnemyKilled()
        assertEquals(2, sim.totalEnemiesKilled)
    }

    @Test
    fun `creditSteps accumulates and ignores non-positive amounts`() {
        val sim = Simulation()
        sim.creditSteps(40L)
        sim.creditSteps(0L)
        sim.creditSteps(-5L)
        sim.creditSteps(10L)
        assertEquals(50L, sim.totalStepsEarned)
    }

    @Test
    fun `hasWaveProgress is false on a fresh round`() {
        assertFalse(Simulation().hasWaveProgress())
    }

    @Test
    fun `hasWaveProgress is true after a tick`() {
        val sim = Simulation()
        sim.tickElapsed(0.1f)
        assertTrue(sim.hasWaveProgress())
    }

    @Test
    fun `hasWaveProgress is true after a kill with no elapsed time`() {
        val sim = Simulation()
        sim.recordEnemyKilled()
        assertTrue(sim.hasWaveProgress())
    }

    @Test
    fun `reset zeroes round-progress counters`() {
        val sim = Simulation()
        sim.tickElapsed(12f)
        sim.recordEnemyKilled()
        sim.creditSteps(99L)
        sim.reset()
        assertEquals(0, sim.totalEnemiesKilled)
        assertEquals(0L, sim.totalStepsEarned)
        assertEquals(0f, sim.elapsedSeconds, 1e-4f)
        assertFalse(sim.hasWaveProgress())
    }
}
