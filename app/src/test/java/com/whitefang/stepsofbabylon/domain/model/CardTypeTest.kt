package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [CardType.effectDescriptionAtLevel] (#53).
 *
 * Pre-fix the Cards UI rendered the hardcoded [CardType.effectLv1] string regardless of
 * `OwnedCard.level`, so a player who upgraded a card from Lv1 to Lv2 saw the same
 * "+10% Defense Absolute" text even though `ApplyCardEffects` correctly applied the
 * scaled value. The fix added [CardType.effectDescriptionAtLevel] as the single source of
 * truth for the level-aware user-facing description.
 *
 * Each card has 3 named tests (Lv1 / Lv4 / Lv7):
 *  - **Lv1** must equal [CardType.effectLv1] verbatim — players who haven't upgraded
 *    yet must see the unchanged baseline string. This is the no-regression contract.
 *  - **Lv4** is the midpoint and must be visibly different from both Lv1 and Lv7.
 *    Catches any "stuck on Lv1" or "jumps straight to Lv7" regressions.
 *  - **Lv7** must equal [CardType.effectLv7] verbatim — the curve must hit the documented
 *    cap exactly, not 1% off because of `.toInt()` rounding drift.
 *
 * Plus a smoke test that every (CardType × level 1..maxLevel) pair produces a non-empty
 * description — catches the case where a future `when` branch is missed when adding a new
 * card type.
 */
class CardTypeTest {

    @Test
    fun `9 entries exist`() {
        assertEquals(9, CardType.entries.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IRON_SKIN — COMMON, primary-only
    // ──────────────────────────────────────────────────────────────────────────

    // #21: IRON_SKIN adds a FLAT defenseAbsolute value (ApplyCardEffects: `defenseAbsolute + v`,
    // no /100), and defenseAbsolute is definitionally flat "damage blocked per hit". The `%`
    // glyph in the old label was a unit error — the player saw a percentage but got a flat
    // value. The label is corrected to drop `%`; the gameplay math is unchanged.

    @Test
    fun `IRON_SKIN Lv1 description matches effectLv1`() {
        assertEquals("+10 Defense Absolute", CardType.IRON_SKIN.effectDescriptionAtLevel(1))
        assertEquals(CardType.IRON_SKIN.effectLv1, CardType.IRON_SKIN.effectDescriptionAtLevel(1))
    }

    @Test
    fun `IRON_SKIN Lv4 shows interpolated value`() {
        // valueLv1=10, valueLv7=42 → at Lv4: 10 + 32 × 3/6 = 26
        assertEquals("+26 Defense Absolute", CardType.IRON_SKIN.effectDescriptionAtLevel(4))
    }

    @Test
    fun `IRON_SKIN Lv7 description matches effectLv7`() {
        assertEquals("+42 Defense Absolute", CardType.IRON_SKIN.effectDescriptionAtLevel(7))
        assertEquals(CardType.IRON_SKIN.effectLv7, CardType.IRON_SKIN.effectDescriptionAtLevel(7))
    }

    @Test
    fun `R21 IRON_SKIN label has no percent sign because it applies a flat value`() {
        // The flat-vs-percent unit fix (#21). defenseAbsolute is flat damage blocked; the `%`
        // glyph misrepresented it. Guards against a future reintroduction of the unit error.
        for (level in 1..CardType.IRON_SKIN.maxLevel) {
            val desc = CardType.IRON_SKIN.effectDescriptionAtLevel(level)
            assertFalse(desc.contains("%"), "IRON_SKIN Lv$level must not show a percent sign: \"$desc\"")
        }
        assertFalse(CardType.IRON_SKIN.effectLv1.contains("%"), "effectLv1 must not contain %")
        assertFalse(CardType.IRON_SKIN.effectLv7.contains("%"), "effectLv7 must not contain %")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHARP_SHOOTER — COMMON, primary-only
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `SHARP_SHOOTER Lv1 description matches effectLv1`() {
        assertEquals("+15% Critical Chance", CardType.SHARP_SHOOTER.effectDescriptionAtLevel(1))
    }

    @Test
    fun `SHARP_SHOOTER Lv4 shows interpolated value`() {
        // valueLv1=15, valueLv7=45 → at Lv4: 15 + 30 × 3/6 = 30
        assertEquals("+30% Critical Chance", CardType.SHARP_SHOOTER.effectDescriptionAtLevel(4))
    }

    @Test
    fun `SHARP_SHOOTER Lv7 description matches effectLv7`() {
        assertEquals("+45% Critical Chance", CardType.SHARP_SHOOTER.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CASH_GRAB — COMMON, primary-only
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `CASH_GRAB Lv1 description matches effectLv1`() {
        assertEquals("+20% Cash from kills", CardType.CASH_GRAB.effectDescriptionAtLevel(1))
    }

    @Test
    fun `CASH_GRAB Lv4 shows interpolated value`() {
        // valueLv1=20, valueLv7=65 → at Lv4: 20 + 45 × 3/6 = 42 (truncated from 42.5)
        assertEquals("+42% Cash from kills", CardType.CASH_GRAB.effectDescriptionAtLevel(4))
    }

    @Test
    fun `CASH_GRAB Lv7 description matches effectLv7`() {
        assertEquals("+65% Cash from kills", CardType.CASH_GRAB.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VAMPIRIC_TOUCH — RARE, primary-only
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `VAMPIRIC_TOUCH Lv1 description matches effectLv1`() {
        assertEquals("+5% Lifesteal", CardType.VAMPIRIC_TOUCH.effectDescriptionAtLevel(1))
    }

    @Test
    fun `VAMPIRIC_TOUCH Lv4 shows interpolated value`() {
        // valueLv1=5, valueLv7=20 → at Lv4: 5 + 15 × 3/6 = 12 (truncated from 12.5)
        assertEquals("+12% Lifesteal", CardType.VAMPIRIC_TOUCH.effectDescriptionAtLevel(4))
    }

    @Test
    fun `VAMPIRIC_TOUCH Lv7 description matches effectLv7`() {
        assertEquals("+20% Lifesteal", CardType.VAMPIRIC_TOUCH.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CHAIN_REACTION — RARE, integer count
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `CHAIN_REACTION Lv1 description matches effectLv1`() {
        assertEquals("+2 Bounce Shot targets", CardType.CHAIN_REACTION.effectDescriptionAtLevel(1))
    }

    @Test
    fun `CHAIN_REACTION Lv4 shows interpolated count`() {
        // valueLv1=2, valueLv7=5 → at Lv4: 2 + 3 × 3/6 = 3 (truncated from 3.5; matches
        // ApplyCardEffects.invoke which casts to Int via v.toInt() at the same level)
        assertEquals("+3 Bounce Shot targets", CardType.CHAIN_REACTION.effectDescriptionAtLevel(4))
    }

    @Test
    fun `CHAIN_REACTION Lv7 description matches effectLv7`() {
        assertEquals("+5 Bounce Shot targets", CardType.CHAIN_REACTION.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SECOND_WIND — RARE, primary-only with 100 % cap
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `SECOND_WIND Lv1 description matches effectLv1`() {
        assertEquals("Revive once at 50% HP", CardType.SECOND_WIND.effectDescriptionAtLevel(1))
    }

    @Test
    fun `SECOND_WIND Lv4 shows interpolated value`() {
        // valueLv1=50, valueLv7=100 → at Lv4: 50 + 50 × 3/6 = 75
        assertEquals("Revive once at 75% HP", CardType.SECOND_WIND.effectDescriptionAtLevel(4))
    }

    @Test
    fun `SECOND_WIND Lv7 description matches effectLv7 and respects 100 percent cap`() {
        assertEquals("Revive once at 100% HP", CardType.SECOND_WIND.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WALKING_FORTRESS — EPIC, primary buff + secondary debuff
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `WALKING_FORTRESS Lv1 description matches effectLv1`() {
        assertEquals("+50% Health, -20% Attack Speed", CardType.WALKING_FORTRESS.effectDescriptionAtLevel(1))
    }

    @Test
    fun `WALKING_FORTRESS Lv4 shows both interpolated values`() {
        // primary 50→125 at Lv4: 50 + 75 × 3/6 = 87 (truncated from 87.5)
        // secondary 20→5 at Lv4: 20 + (5-20) × 3/6 = 12 (truncated from 12.5)
        assertEquals("+87% Health, -12% Attack Speed", CardType.WALKING_FORTRESS.effectDescriptionAtLevel(4))
    }

    @Test
    fun `WALKING_FORTRESS Lv7 description matches effectLv7`() {
        assertEquals("+125% Health, -5% Attack Speed", CardType.WALKING_FORTRESS.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GLASS_CANNON — EPIC, primary buff + secondary debuff
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `GLASS_CANNON Lv1 description matches effectLv1`() {
        assertEquals("+80% Damage, -40% Health", CardType.GLASS_CANNON.effectDescriptionAtLevel(1))
    }

    @Test
    fun `GLASS_CANNON Lv4 shows both interpolated values`() {
        // primary 80→140 at Lv4: 80 + 60 × 3/6 = 110
        // secondary 40→10 at Lv4: 40 + (10-40) × 3/6 = 25
        assertEquals("+110% Damage, -25% Health", CardType.GLASS_CANNON.effectDescriptionAtLevel(4))
    }

    @Test
    fun `GLASS_CANNON Lv7 description matches effectLv7`() {
        assertEquals("+140% Damage, -10% Health", CardType.GLASS_CANNON.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP_SURGE — EPIC, multiplier with one-decimal formatting (Locale.ROOT)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `STEP_SURGE Lv1 description matches effectLv1 and drops trailing zero`() {
        assertEquals("Earn 2x Gems this round", CardType.STEP_SURGE.effectDescriptionAtLevel(1))
    }

    @Test
    fun `STEP_SURGE Lv4 shows fractional multiplier with one decimal`() {
        // valueLv1=2.0, valueLv7=5.0 → at Lv4: 2.0 + 3.0 × 3/6 = 3.5
        assertEquals("Earn 3.5x Gems this round", CardType.STEP_SURGE.effectDescriptionAtLevel(4))
    }

    @Test
    fun `STEP_SURGE Lv7 description matches effectLv7`() {
        assertEquals("Earn 5x Gems this round", CardType.STEP_SURGE.effectDescriptionAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Smoke + cross-card invariants
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `every card produces a non-empty description at every level 1 to maxLevel`() {
        // Catches a future when-branch omission if a new CardType is added without a
        // description case — Kotlin's `when` on enum is exhaustive at compile time, so
        // this test exists to assert the runtime output is meaningful (not just non-null).
        for (type in CardType.entries) {
            for (level in 1..type.maxLevel) {
                val desc = type.effectDescriptionAtLevel(level)
                assertTrue(desc.isNotBlank(), "$type at Lv$level produced blank description")
            }
        }
    }

    @Test
    fun `Lv1 and Lv7 descriptions are different for every card`() {
        // Direct regression for #53 — pre-fix every level rendered effectLv1 verbatim, so
        // Lv1 and Lv7 strings collided. This test would have failed on `main` pre-fix.
        for (type in CardType.entries) {
            assertNotEquals(
                type.effectDescriptionAtLevel(1),
                type.effectDescriptionAtLevel(type.maxLevel),
                "$type Lv1 and Lv${type.maxLevel} descriptions must differ",
            )
        }
    }

    @Test
    fun `Lv4 description is different from both Lv1 and Lv7 for cards with continuous progression`() {
        // CHAIN_REACTION uses integer truncation so Lv4 (3) collides with Lv3 (3) but not
        // with Lv1 (2) or Lv7 (5); excluded from the "different from both ends" guard
        // since the .toInt() collapse is intentional. Every other card has a continuous
        // double progression that should produce a visibly different mid-curve string.
        val continuousCards = CardType.entries - CardType.CHAIN_REACTION
        for (type in continuousCards) {
            val mid = type.effectDescriptionAtLevel(4)
            assertNotEquals(type.effectDescriptionAtLevel(1), mid, "$type Lv4 must differ from Lv1")
            assertNotEquals(type.effectDescriptionAtLevel(type.maxLevel), mid, "$type Lv4 must differ from Lv${type.maxLevel}")
        }
    }
}
