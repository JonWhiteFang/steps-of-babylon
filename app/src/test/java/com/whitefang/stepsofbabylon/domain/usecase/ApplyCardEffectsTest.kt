package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplyCardEffectsTest {
    private val apply = ApplyCardEffects()
    private val baseStats = ResolvedStats()

    @Test
    fun `empty loadout returns unchanged stats`() {
        val result = apply(baseStats, emptyList())
        assertEquals(baseStats, result.stats)
        assertEquals(0.0, result.secondWindHpPercent)
        assertEquals(1.0, result.gemMultiplier)
        assertEquals(0.0, result.cashBonusPercent)
    }

    @Test
    fun `iron skin adds defense absolute`() {
        val cards = listOf(OwnedCard(1, CardType.IRON_SKIN, 1, true))
        val result = apply(baseStats, cards)
        assertEquals(baseStats.defenseAbsolute + 10.0, result.stats.defenseAbsolute)
    }

    @Test
    fun `sharp shooter adds crit chance capped at 80`() {
        val stats = baseStats.copy(critChance = 0.75)
        val cards = listOf(OwnedCard(1, CardType.SHARP_SHOOTER, 5, true)) // 35% = 0.35
        val result = apply(stats, cards)
        assertEquals(0.80, result.stats.critChance) // capped
    }

    @Test
    fun `cash grab sets cashBonusPercent`() {
        val cards = listOf(OwnedCard(1, CardType.CASH_GRAB, 1, true))
        val result = apply(baseStats, cards)
        assertEquals(20.0, result.cashBonusPercent)
    }

    @Test
    fun `vampiric touch adds lifesteal`() {
        val cards = listOf(OwnedCard(1, CardType.VAMPIRIC_TOUCH, 1, true))
        val result = apply(baseStats, cards)
        assertEquals(baseStats.lifestealPercent + 0.05, result.stats.lifestealPercent, 0.001)
    }

    @Test
    fun `chain reaction adds bounce count`() {
        val cards = listOf(OwnedCard(1, CardType.CHAIN_REACTION, 1, true))
        val result = apply(baseStats, cards)
        assertEquals(baseStats.bounceCount + 2, result.stats.bounceCount)
    }

    @Test
    fun `second wind sets revive percent`() {
        val cards = listOf(OwnedCard(1, CardType.SECOND_WIND, 1, true))
        val result = apply(baseStats, cards)
        assertEquals(0.50, result.secondWindHpPercent)
    }

    @Test
    fun `walking fortress buffs health and debuffs attack speed`() {
        val cards = listOf(OwnedCard(1, CardType.WALKING_FORTRESS, 1, true))
        val result = apply(baseStats, cards)
        assertTrue(result.stats.maxHealth > baseStats.maxHealth) // +50%
        assertTrue(result.stats.attackSpeed < baseStats.attackSpeed) // -20%
    }

    @Test
    fun `glass cannon buffs damage and debuffs health`() {
        val cards = listOf(OwnedCard(1, CardType.GLASS_CANNON, 1, true))
        val result = apply(baseStats, cards)
        assertTrue(result.stats.damage > baseStats.damage) // +80%
        assertTrue(result.stats.maxHealth < baseStats.maxHealth) // -40%
    }

    @Test
    fun `step surge sets gem multiplier`() {
        val cards = listOf(OwnedCard(1, CardType.STEP_SURGE, 1, true))
        val result = apply(baseStats, cards)
        assertEquals(2.0, result.gemMultiplier)
    }

    @Test
    fun `level scaling works correctly`() {
        val lv1 = apply(baseStats, listOf(OwnedCard(1, CardType.IRON_SKIN, 1, true)))
        val lv7 = apply(baseStats, listOf(OwnedCard(1, CardType.IRON_SKIN, 7, true)))
        assertEquals(baseStats.defenseAbsolute + 10.0, lv1.stats.defenseAbsolute)
        assertEquals(baseStats.defenseAbsolute + 42.0, lv7.stats.defenseAbsolute)
    }
}
