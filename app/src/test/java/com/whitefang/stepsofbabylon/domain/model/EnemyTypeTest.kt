package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnemyTypeTest {
    @Test
    fun `6 entries exist`() {
        assertEquals(6, EnemyType.entries.size)
    }

    @Test
    fun `BOSS has highest health multiplier`() {
        assertEquals(20.0, EnemyType.BOSS.healthMultiplier)
        assertTrue(EnemyType.entries.all { it.healthMultiplier <= EnemyType.BOSS.healthMultiplier })
    }

    @Test
    fun `FAST has highest speed multiplier`() {
        assertEquals(2.0, EnemyType.FAST.speedMultiplier)
        assertTrue(EnemyType.entries.all { it.speedMultiplier <= EnemyType.FAST.speedMultiplier })
    }

    @Test
    fun `all multipliers positive`() {
        EnemyType.entries.forEach { type ->
            assertTrue(type.speedMultiplier > 0, "$type speed")
            assertTrue(type.healthMultiplier > 0, "$type health")
            assertTrue(type.damageMultiplier > 0, "$type damage")
        }
    }
}
