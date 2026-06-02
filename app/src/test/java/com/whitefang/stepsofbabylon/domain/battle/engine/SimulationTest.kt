package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.EntityProtocol
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

    // ---- tickEntities ----

    private class FakeEntity(
        override var x: Float = 0f,
        override var y: Float = 0f,
        override var width: Float = 0f,
        override val isChronoSlowable: Boolean = false,
    ) : EntityProtocol {
        override var isAlive = true
        var lastDt = Float.NaN
        var tickCount = 0
        override fun update(deltaTime: Float) {
            lastDt = deltaTime
            tickCount++
        }
    }

    @Test
    fun `tickEntities scales dt for chrono-slowable entities`() {
        val enemy = FakeEntity(isChronoSlowable = true)
        Simulation().tickEntities(listOf(enemy), deltaTime = 0.2f, chronoSlowFactor = 0.1f)
        assertEquals(0.02f, enemy.lastDt, 1e-4f)
    }

    @Test
    fun `tickEntities uses full dt for non-slowable entities even when chrono is active`() {
        val projectile = FakeEntity(isChronoSlowable = false)
        Simulation().tickEntities(listOf(projectile), deltaTime = 0.2f, chronoSlowFactor = 0.1f)
        assertEquals(0.2f, projectile.lastDt, 1e-4f)
    }

    @Test
    fun `tickEntities with factor 1 ticks slowable entities at full dt (chrono inactive)`() {
        val enemy = FakeEntity(isChronoSlowable = true)
        Simulation().tickEntities(listOf(enemy), deltaTime = 0.2f, chronoSlowFactor = 1f)
        assertEquals(0.2f, enemy.lastDt, 1e-4f)
    }

    @Test
    fun `tickEntities updates every entity in the list exactly once`() {
        val enemy = FakeEntity(isChronoSlowable = true)
        val projectile = FakeEntity(isChronoSlowable = false)
        Simulation().tickEntities(listOf(enemy, projectile), deltaTime = 0.2f, chronoSlowFactor = 0.1f)
        assertEquals(1, enemy.tickCount)
        assertEquals(1, projectile.tickCount)
        assertEquals(0.02f, enemy.lastDt, 1e-4f)
        assertEquals(0.2f, projectile.lastDt, 1e-4f)
    }

    // ---- detectProjectileEnemyHits ----

    @Test
    fun `detectProjectileEnemyHits fires onHit for an overlapping projectile-enemy pair`() {
        val proj = FakeEntity(x = 0f, y = 0f, width = 20f)
        val enemy = FakeEntity(x = 5f, y = 0f, width = 20f) // dist 5 < (20+20)/2 = 20
        val hits = mutableListOf<Pair<FakeEntity, FakeEntity>>()
        Simulation().detectProjectileEnemyHits(listOf(proj), listOf(enemy)) { p, e -> hits.add(p to e) }
        assertEquals(1, hits.size)
        assertSame(proj, hits[0].first)
        assertSame(enemy, hits[0].second)
    }

    @Test
    fun `detectProjectileEnemyHits stops at the first overlapping enemy per projectile`() {
        val proj = FakeEntity(x = 0f, y = 0f, width = 20f)
        val first = FakeEntity(x = 3f, y = 0f, width = 20f)  // overlaps
        val second = FakeEntity(x = 5f, y = 0f, width = 20f) // also overlaps
        val hits = mutableListOf<FakeEntity>()
        Simulation().detectProjectileEnemyHits(listOf(proj), listOf(first, second)) { _, e -> hits.add(e) }
        assertEquals(listOf(first), hits)
    }

    @Test
    fun `detectProjectileEnemyHits does not fire when nothing overlaps`() {
        val proj = FakeEntity(x = 0f, y = 0f, width = 20f)
        val enemy = FakeEntity(x = 100f, y = 0f, width = 20f) // dist 100 > 20
        var fired = false
        Simulation().detectProjectileEnemyHits(listOf(proj), listOf(enemy)) { _, _ -> fired = true }
        assertFalse(fired)
    }

    @Test
    fun `detectProjectileEnemyHits observes a mid-sweep moved enemy so a later projectile misses it`() {
        val p1 = FakeEntity(x = 0f, y = 0f, width = 20f)
        val p2 = FakeEntity(x = 0f, y = 0f, width = 20f)
        val enemy = FakeEntity(x = 5f, y = 0f, width = 20f)
        val hits = mutableListOf<FakeEntity>()
        // onHit knocks the enemy far away — mirrors GameEngine.onProjectileHitEnemy applyKnockback.
        // Because firing is interleaved with iteration, p2 re-reads the moved position and misses.
        Simulation().detectProjectileEnemyHits(listOf(p1, p2), listOf(enemy)) { p, e ->
            hits.add(p)
            e.x = 1000f
        }
        assertEquals(listOf(p1), hits)
    }

    // ---- detectZigguratHits ----

    @Test
    fun `detectZigguratHits fires for a projectile overlapping the ziggurat`() {
        val proj = FakeEntity(x = 5f, y = 0f, width = 20f) // dist 5 < 20/2 + 20/2 = 20
        val hits = mutableListOf<FakeEntity>()
        Simulation().detectZigguratHits(listOf(proj), zigX = 0f, zigY = 0f, zigWidth = 20f) { hits.add(it) }
        assertEquals(listOf(proj), hits)
    }

    @Test
    fun `detectZigguratHits does not fire outside the overlap radius`() {
        val proj = FakeEntity(x = 100f, y = 0f, width = 20f)
        var fired = false
        Simulation().detectZigguratHits(listOf(proj), zigX = 0f, zigY = 0f, zigWidth = 20f) { fired = true }
        assertFalse(fired)
    }

    // ---- advanceUWTimers (UW lifecycle, slice 5) ----

    @Test
    fun `advanceUWTimers decrements the cooldown and clamps it at zero`() {
        val r = Simulation().advanceUWTimers(cooldownRemaining = 0.3f, effectTimeRemaining = 0f, deltaTime = 0.5f)
        assertEquals(0f, r.cooldownRemaining, 1e-4f)
    }

    @Test
    fun `advanceUWTimers leaves a zero cooldown and inactive effect untouched`() {
        val r = Simulation().advanceUWTimers(cooldownRemaining = 0f, effectTimeRemaining = 0f, deltaTime = 0.5f)
        assertEquals(0f, r.cooldownRemaining, 1e-4f)
        assertEquals(0f, r.effectTimeRemaining, 1e-4f)
        assertFalse(r.effectWasActive)
        assertFalse(r.justExpired)
    }

    @Test
    fun `advanceUWTimers counts down an active effect without expiring it`() {
        val r = Simulation().advanceUWTimers(cooldownRemaining = 5f, effectTimeRemaining = 2f, deltaTime = 0.5f)
        assertEquals(4.5f, r.cooldownRemaining, 1e-4f)
        assertEquals(1.5f, r.effectTimeRemaining, 1e-4f)
        assertTrue(r.effectWasActive)
        assertFalse(r.justExpired)
    }

    @Test
    fun `advanceUWTimers flags justExpired and clamps the effect on the crossing frame`() {
        val r = Simulation().advanceUWTimers(cooldownRemaining = 0f, effectTimeRemaining = 0.3f, deltaTime = 0.5f)
        assertEquals(0f, r.effectTimeRemaining, 1e-4f)
        assertTrue(r.effectWasActive) // ongoing effects still run on the expiry frame
        assertTrue(r.justExpired)
    }

    // ---- isUWReadyToFire ----

    @Test
    fun `isUWReadyToFire is true when off cooldown and not mid-effect`() {
        assertTrue(Simulation().isUWReadyToFire(cooldownRemaining = 0f, effectTimeRemaining = 0f))
    }

    @Test
    fun `isUWReadyToFire is false while on cooldown`() {
        assertFalse(Simulation().isUWReadyToFire(cooldownRemaining = 1.5f, effectTimeRemaining = 0f))
    }

    @Test
    fun `isUWReadyToFire is false while mid-effect`() {
        assertFalse(Simulation().isUWReadyToFire(cooldownRemaining = 0f, effectTimeRemaining = 2f))
    }
}
