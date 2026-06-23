package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import kotlin.math.min

data class CardEffectResult(
    val stats: ResolvedStats,
    val secondWindHpPercent: Double = 0.0,
    val gemMultiplier: Double = 1.0,
    val cashBonusPercent: Double = 0.0,
)

class ApplyCardEffects {
    operator fun invoke(
        stats: ResolvedStats,
        equippedCards: List<OwnedCard>,
    ): CardEffectResult {
        var s = stats
        var secondWind = 0.0
        var gemMult = 1.0
        var cashBonus = 0.0

        for (card in equippedCards) {
            val v = card.type.effectAtLevel(card.level)
            val sv = card.type.secondaryAtLevel(card.level)
            when (card.type) {
                CardType.IRON_SKIN -> {
                    s = s.copy(defenseAbsolute = s.defenseAbsolute + v)
                }

                CardType.SHARP_SHOOTER -> {
                    s = s.copy(critChance = min(s.critChance + v / 100.0, 0.80))
                }

                CardType.CASH_GRAB -> {
                    cashBonus += v
                }

                CardType.VAMPIRIC_TOUCH -> {
                    s = s.copy(lifestealPercent = s.lifestealPercent + v / 100.0)
                }

                CardType.CHAIN_REACTION -> {
                    s = s.copy(bounceCount = s.bounceCount + v.toInt())
                }

                CardType.SECOND_WIND -> {
                    secondWind = v / 100.0
                }

                CardType.WALKING_FORTRESS -> {
                    s =
                        s.copy(
                            maxHealth = s.maxHealth * (1 + v / 100.0),
                            attackSpeed = s.attackSpeed * (1 - sv / 100.0),
                        )
                }

                CardType.GLASS_CANNON -> {
                    s =
                        s.copy(
                            damage = s.damage * (1 + v / 100.0),
                            maxHealth = s.maxHealth * (1 - sv / 100.0),
                        )
                }

                CardType.STEP_SURGE -> {
                    gemMult = v
                }
            }
        }
        return CardEffectResult(s, secondWind, gemMult, cashBonus)
    }
}
