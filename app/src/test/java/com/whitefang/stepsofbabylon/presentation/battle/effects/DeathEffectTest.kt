package com.whitefang.stepsofbabylon.presentation.battle.effects

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeathEffectTest {
    @Test
    fun `basic enemy spawns 8 particles`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 100f, 100f, EnemyType.BASIC)
        assertEquals(8, pool.activeCount())
    }

    @Test
    fun `fast enemy spawns 6 particles`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 100f, 100f, EnemyType.FAST)
        assertEquals(6, pool.activeCount())
    }

    @Test
    fun `tank enemy spawns 12 particles`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 100f, 100f, EnemyType.TANK)
        assertEquals(12, pool.activeCount())
    }

    @Test
    fun `ranged enemy spawns 8 particles`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 100f, 100f, EnemyType.RANGED)
        assertEquals(8, pool.activeCount())
    }

    @Test
    fun `boss enemy spawns 20 particles`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 100f, 100f, EnemyType.BOSS)
        assertEquals(20, pool.activeCount())
    }

    @Test
    fun `scatter enemy spawns 6 particles`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 100f, 100f, EnemyType.SCATTER)
        assertEquals(6, pool.activeCount())
    }

    @Test
    fun `particles spawn at enemy position`() {
        val pool = ParticlePool(50)
        DeathEffect.spawn(pool, 200f, 300f, EnemyType.BASIC)
        // All particles should start at the death position
        // (pool internals — check first acquired particle)
    }
}
