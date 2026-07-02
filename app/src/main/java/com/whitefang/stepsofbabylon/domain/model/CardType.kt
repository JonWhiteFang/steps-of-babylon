package com.whitefang.stepsofbabylon.domain.model

enum class CardType(
    val rarity: CardRarity,
    val effectLv1: String,
    val effectLv7: String,
    val valueLv1: Double,
    val valueLv7: Double,
    val secondaryLv1: Double = 0.0,
    val secondaryLv7: Double = 0.0,
    val maxLevel: Int = 7,
) {
    // #21: defenseAbsolute is FLAT damage blocked per hit (ApplyCardEffects adds the raw value,
    // no /100), so the label drops the `%` glyph that misrepresented it as a percentage.
    IRON_SKIN(CardRarity.COMMON, "+10 Defense Absolute", "+42 Defense Absolute", 10.0, 42.0),
    SHARP_SHOOTER(CardRarity.COMMON, "+15% Critical Chance", "+45% Critical Chance", 15.0, 45.0),
    CASH_GRAB(CardRarity.COMMON, "+20% Cash from kills", "+65% Cash from kills", 20.0, 65.0),
    VAMPIRIC_TOUCH(CardRarity.RARE, "+5% Lifesteal", "+20% Lifesteal", 5.0, 20.0),
    CHAIN_REACTION(CardRarity.RARE, "+2 Bounce Shot targets", "+5 Bounce Shot targets", 2.0, 5.0),
    SECOND_WIND(CardRarity.RARE, "Revive once at 50% HP", "Revive once at 100% HP", 50.0, 100.0),
    WALKING_FORTRESS(
        CardRarity.EPIC,
        "+50% Health, -20% Attack Speed",
        "+125% Health, -5% Attack Speed",
        50.0,
        125.0,
        20.0,
        5.0,
    ),
    GLASS_CANNON(CardRarity.EPIC, "+80% Damage, -40% Health", "+140% Damage, -10% Health", 80.0, 140.0, 40.0, 10.0),
    STEP_SURGE(CardRarity.EPIC, "Earn 2x Gems this round", "Earn 5x Gems this round", 2.0, 5.0),
    ;

    fun effectAtLevel(level: Int): Double {
        val raw = valueLv1 + (valueLv7 - valueLv1) * (level - 1) / 6.0
        // SECOND_WIND caps at 100% HP recovery
        return if (this == SECOND_WIND) raw.coerceAtMost(100.0) else raw
    }

    fun secondaryAtLevel(level: Int): Double = secondaryLv1 + (secondaryLv7 - secondaryLv1) * (level - 1) / 6.0
}
