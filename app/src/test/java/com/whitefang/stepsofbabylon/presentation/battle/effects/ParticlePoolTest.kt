package com.whitefang.stepsofbabylon.presentation.battle.effects

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ParticlePoolTest {
    @Test
    fun `acquire returns active particle`() {
        val pool = ParticlePool(10)
        val p = pool.acquire()
        assertTrue(p.active)
        assertEquals(1, pool.activeCount())
    }

    @Test
    fun `acquire multiple fills pool`() {
        val pool = ParticlePool(5)
        repeat(5) { pool.acquire() }
        assertEquals(5, pool.activeCount())
    }

    @Test
    fun `exhausted pool recycles oldest`() {
        val pool = ParticlePool(3)
        val p1 = pool.acquire()
        p1.x = 1f
        val p2 = pool.acquire()
        p2.x = 2f
        val p3 = pool.acquire()
        p3.x = 3f
        // Pool full — next acquire recycles
        val p4 = pool.acquire()
        assertTrue(p4.active)
        assertEquals(3, pool.activeCount()) // Still 3 — one was recycled
    }

    @Test
    fun `released particle can be reacquired`() {
        val pool = ParticlePool(3)
        val p1 = pool.acquire()
        p1.active = false // "release" by marking inactive
        assertEquals(0, pool.activeCount())
        val p2 = pool.acquire()
        assertTrue(p2.active)
        assertEquals(1, pool.activeCount())
    }

    @Test
    fun `updateAll advances age and deactivates expired`() {
        val pool = ParticlePool(5)
        val p = pool.acquire()
        p.lifetime = 0.1f
        pool.updateAll(0.2f)
        assertFalse(p.active)
        assertEquals(0, pool.activeCount())
    }

    @Test
    fun `clear deactivates all`() {
        val pool = ParticlePool(10)
        repeat(10) { pool.acquire() }
        assertEquals(10, pool.activeCount())
        pool.clear()
        assertEquals(0, pool.activeCount())
    }

    @Test
    fun `particle reset clears state`() {
        val p = Particle()
        p.x = 100f
        p.y = 200f
        p.vx = 50f
        p.active = true
        p.age = 0.5f
        p.reset()
        assertEquals(0f, p.x)
        assertEquals(0f, p.y)
        assertEquals(0f, p.vx)
        assertFalse(p.active)
        assertEquals(0f, p.age)
    }

    @Test
    fun `particle update fades alpha`() {
        val p = Particle()
        p.active = true
        p.lifetime = 1f
        p.alpha = 1f
        p.update(0.5f)
        assertTrue(p.alpha < 1f)
        assertTrue(p.active)
    }

    @Test
    fun `particle expires after lifetime`() {
        val p = Particle()
        p.active = true
        p.lifetime = 0.5f
        p.update(0.6f)
        assertFalse(p.active)
    }
}
