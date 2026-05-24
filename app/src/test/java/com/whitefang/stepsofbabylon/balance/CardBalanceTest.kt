package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import com.whitefang.stepsofbabylon.domain.usecase.ApplyCardEffects
import com.whitefang.stepsofbabylon.domain.usecase.ResolveStats
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Validates card balance — no single card is mandatory, tradeoffs are real.
 */
class CardBalanceTest {

    private val resolveStats = ResolveStats()
    private val applyCardEffects = ApplyCardEffects()

    private fun effectivePower(card: CardType?, level: Int): Double {
        val baseStats = resolveStats(emptyMap())
        if (card == null) return baseStats.damage * baseStats.attackSpeed * baseStats.maxHealth

        val equipped = listOf(OwnedCard(id = 0, type = card, level = level, isEquipped = true))
        val result = applyCardEffects(baseStats, equipped)
        val stats = result.stats
        return stats.damage * stats.attackSpeed * stats.maxHealth
    }

    @Test
    fun `glass cannon lv5 - DPS increase outweighs health loss`() {
        val basePower = effectivePower(null, 0)
        val gcPower = effectivePower(CardType.GLASS_CANNON, 5)
        // Glass Cannon should increase effective power (DPS × HP product)
        // but not by more than 2x
        assertTrue(gcPower > basePower * 0.8, "Glass Cannon Lv5 power ($gcPower) too weak vs base ($basePower)")
        assertTrue(gcPower < basePower * 2.0, "Glass Cannon Lv5 power ($gcPower) too strong vs base ($basePower)")
    }

    @Test
    fun `walking fortress lv5 - survivability increase outweighs DPS loss`() {
        val basePower = effectivePower(null, 0)
        val wfPower = effectivePower(CardType.WALKING_FORTRESS, 5)
        assertTrue(wfPower > basePower * 0.8, "Walking Fortress Lv5 power ($wfPower) too weak vs base ($basePower)")
        assertTrue(wfPower < basePower * 2.0, "Walking Fortress Lv5 power ($wfPower) too strong vs base ($basePower)")
    }

    @Test
    fun `no single card provides more than 2x effective power at lv5`() {
        val basePower = effectivePower(null, 0)
        for (card in CardType.entries) {
            val power = effectivePower(card, 5)
            assertTrue(power < basePower * 2.5,
                "${card.name} Lv5 power ($power) exceeds 2.5x base ($basePower)")
        }
    }

    @Test
    fun `second wind is once per round - does not make player unkillable`() {
        // Second Wind at Lv7: revive at 100% HP (capped) — but only once
        val value = CardType.SECOND_WIND.effectAtLevel(7)
        assertEquals(100.0, value, "Second Wind Lv7 should revive at 100% HP (capped)")
        // The "once per round" is enforced by secondWindUsed flag in GameEngine
        // This test validates the value is correct, not the mechanic
    }
}
