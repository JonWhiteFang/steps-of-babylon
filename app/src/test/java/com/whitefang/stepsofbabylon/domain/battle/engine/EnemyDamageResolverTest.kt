package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.DamageableEnemy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [EnemyDamageResolver] (#306 Slice 2). Pins the four branches lifted verbatim from
 * the pre-hoist `EnemyEntity.takeDamage`: corpse guard (#146), armor absorb (#17), non-lethal hit, lethal
 * hit — plus the enemy-specific NO-HP-FLOOR property (HP goes negative on overkill), which diverges from
 * the ziggurat resolver's `coerceAtLeast(0.0)`.
 */
class EnemyDamageResolverTest {
    private class FakeEnemy(
        override var currentHp: Double,
        override val maxHp: Double,
        override var armorHits: Int = 0,
    ) : DamageableEnemy

    private val resolver = EnemyDamageResolver()

    @Test
    fun `corpse guard returns zero and does not mutate when not alive`() {
        val e = FakeEnemy(currentHp = 5.0, maxHp = 10.0, armorHits = 1)
        val out = resolver.resolve(e, amount = 3.0, isAlive = false)
        assertEquals(0.0, out.dealt, 1e-9)
        assertFalse(out.died)
        assertEquals(5.0, e.currentHp, 1e-9, "corpse guard must not touch HP")
        assertEquals(1, e.armorHits, "corpse guard must not touch armor")
    }

    @Test
    fun `armor charge absorbs the hit and is consumed with zero dealt`() {
        val e = FakeEnemy(currentHp = 10.0, maxHp = 10.0, armorHits = 2)
        val out = resolver.resolve(e, amount = 4.0, isAlive = true)
        assertEquals(0.0, out.dealt, 1e-9, "armor-absorbed hit deals no HP damage (#17)")
        assertFalse(out.died)
        assertEquals(10.0, e.currentHp, 1e-9, "HP untouched while armor absorbs")
        assertEquals(1, e.armorHits, "one armor charge consumed")
    }

    @Test
    fun `non-lethal hit reduces HP and reports damage dealt`() {
        val e = FakeEnemy(currentHp = 10.0, maxHp = 10.0)
        val out = resolver.resolve(e, amount = 4.0, isAlive = true)
        assertEquals(4.0, out.dealt, 1e-9)
        assertFalse(out.died)
        assertEquals(6.0, e.currentHp, 1e-9)
    }

    @Test
    fun `lethal hit reports died`() {
        val e = FakeEnemy(currentHp = 3.0, maxHp = 10.0)
        val out = resolver.resolve(e, amount = 3.0, isAlive = true)
        assertEquals(3.0, out.dealt, 1e-9)
        assertTrue(out.died, "HP reaching exactly 0.0 is lethal (<= 0.0)")
        assertEquals(0.0, e.currentHp, 1e-9)
    }

    @Test
    fun `overkill drives HP negative with no floor and reports died`() {
        val e = FakeEnemy(currentHp = 2.0, maxHp = 10.0)
        val out = resolver.resolve(e, amount = 5.0, isAlive = true)
        assertEquals(5.0, out.dealt, 1e-9)
        assertTrue(out.died)
        assertEquals(-3.0, e.currentHp, 1e-9, "enemy HP has NO floor (diverges from the ziggurat resolver)")
    }
}
