package com.whitefang.stepsofbabylon.domain.battle.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Pure-JVM tests for [ScatterSplit] (#306 Slice 2). Pins the SCATTER-on-death child math lifted verbatim
 * from `CombatResolver.handleEnemyDeath`: count 2..3 (`(2..3).random()` == `nextInt(2, 4)`), each child
 * half the parent's HP/damage, and the fanned `(i - count / 2f) * 15f` X offset.
 */
class ScatterSplitTest {
    /** RNG whose nextInt(from, until) always returns [fixed] (clamped into range by the caller's bounds). */
    private fun fixedIntRandom(fixed: Int) =
        object : Random() {
            override fun nextBits(bitCount: Int): Int = 0

            override fun nextInt(
                from: Int,
                until: Int,
            ): Int = fixed
        }

    @Test
    fun `rolls two children at the low end of the range`() {
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(2))
        assertEquals(2, children.size)
    }

    @Test
    fun `rolls three children at the high end of the range`() {
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(3))
        assertEquals(3, children.size)
    }

    @Test
    fun `each child gets half the parent HP and damage`() {
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(2))
        children.forEach { c ->
            assertEquals(10.0, c.hp, 1e-9)
            assertEquals(10.0, c.maxHp, 1e-9)
            assertEquals(4.0, c.damage, 1e-9)
        }
    }

    @Test
    fun `offsets fan out symmetrically for two children`() {
        // count=2: `count / 2f` is FLOAT division (Int count promoted) → 2/2f = 1.0f.
        // i=0 → (0 - 1.0) * 15 = -15 ; i=1 → (1 - 1.0) * 15 = 0
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(2))
        assertEquals(-15f, children[0].offsetX, 1e-4f)
        assertEquals(0f, children[1].offsetX, 1e-4f)
    }

    @Test
    fun `offsets fan out for three children`() {
        // count=3: `count / 2f` is FLOAT division → 3/2f = 1.5f (NOT integer 1). This matches the verbatim
        // pre-hoist formula `(i - childCount / 2f) * 15f` in CombatResolver — do NOT "fix" the helper to
        // integer division; that would shift SCATTER spawn-X and break behaviour-preservation.
        // i=0 → (0 - 1.5) * 15 = -22.5 ; i=1 → (1 - 1.5) * 15 = -7.5 ; i=2 → (2 - 1.5) * 15 = +7.5
        val children = ScatterSplit.children(parentMaxHp = 20.0, parentDamage = 8.0, random = fixedIntRandom(3))
        assertEquals(-22.5f, children[0].offsetX, 1e-4f)
        assertEquals(-7.5f, children[1].offsetX, 1e-4f)
        assertEquals(7.5f, children[2].offsetX, 1e-4f)
    }
}
