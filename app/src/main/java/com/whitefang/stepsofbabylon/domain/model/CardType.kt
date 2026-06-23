package com.whitefang.stepsofbabylon.domain.model

import java.util.Locale

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

    /**
     * Returns the live effect description for this card at the given [level].
     *
     * Closes #53 — pre-fix the UI rendered the hardcoded [effectLv1] string regardless of
     * `OwnedCard.level`, so a player who upgraded a card from Lv1 to Lv2 saw the same
     * "+10% Defense Absolute" text even though `ApplyCardEffects` correctly applied the
     * scaled value. The actual gameplay path (`effectAtLevel(level)` /
     * `secondaryAtLevel(level)`) is unchanged; this method is the single source of truth
     * for the user-facing description and stays in lockstep with the gameplay math by
     * deriving its numbers from the same formulas.
     *
     * Format conventions:
     * - Primary value: integer truncation via `.toInt()` to match the existing Lv1 / Lv7
     *   strings (`+10%`, `+42%`, `+2`, …). The actual gameplay uses the un-truncated
     *   double — display intentionally drops fractional parts so 87.5% reads as "+87%".
     * - Secondary value (WALKING_FORTRESS / GLASS_CANNON debuffs): same `.toInt()` shape;
     *   the negative sign is part of the template since `secondaryAtLevel` always returns
     *   a positive magnitude.
     * - STEP_SURGE multiplier: one decimal place via [formatMultiplier], with a trailing
     *   `.0` stripped so Lv1 reads "Earn 2x Gems this round" (matches [effectLv1]) while
     *   Lv4 reads "Earn 3.5x Gems this round". Pinned to [Locale.ROOT] so the decimal
     *   separator stays as `.` regardless of device locale (matches the
     *   `DescribeUpgradeEffect` convention from RO-11 #C).
     */
    fun effectDescriptionAtLevel(level: Int): String {
        val v = effectAtLevel(level).toInt()
        val sv = secondaryAtLevel(level).toInt()
        return when (this) {
            IRON_SKIN -> "+$v Defense Absolute"

            // #21: flat value, no percent glyph
            SHARP_SHOOTER -> "+$v% Critical Chance"

            CASH_GRAB -> "+$v% Cash from kills"

            VAMPIRIC_TOUCH -> "+$v% Lifesteal"

            CHAIN_REACTION -> "+$v Bounce Shot targets"

            SECOND_WIND -> "Revive once at $v% HP"

            WALKING_FORTRESS -> "+$v% Health, -$sv% Attack Speed"

            GLASS_CANNON -> "+$v% Damage, -$sv% Health"

            STEP_SURGE -> "Earn ${formatMultiplier(effectAtLevel(level))}x Gems this round"
        }
    }

    private companion object {
        /**
         * Formats a multiplier with a single decimal place, stripping a trailing `.0` so
         * integer values render as "2" instead of "2.0". Used by STEP_SURGE so its
         * description matches [effectLv1] / [effectLv7] verbatim at the endpoints while
         * still showing fractional progress mid-curve (e.g. Lv4 → 3.5).
         */
        fun formatMultiplier(value: Double): String {
            val s = String.format(Locale.ROOT, "%.1f", value)
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }
    }
}
