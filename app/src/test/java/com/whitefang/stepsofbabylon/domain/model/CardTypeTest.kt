package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Numeric-lerp coverage for [CardType.effectAtLevel] / [CardType.secondaryAtLevel] (#53).
 *
 * The level-aware **display string** used to live on the enum as `effectDescriptionAtLevel`;
 * #34 (i18n phase 3 G) moved that string-building to the presentation resolver
 * `CardType.effectDescription(level)` (which reads @StringRes templates and reuses the numeric
 * accessors below). The exact-English string assertions now live in
 * `presentation/cards/CardEffectDescriptionTest` (Robolectric). This file keeps the pure-domain
 * numeric contract: the interpolation values (post-`.toInt()` truncation, which the resolver
 * consumes verbatim), the SECOND_WIND 100% cap, and the Lv1/Lv7 endpoint constants.
 *
 * Endpoint contract per card:
 *  - **Lv1** — the un-upgraded baseline. `effectAtLevel(1).toInt()` must match the number baked
 *    into [CardType.effectLv1] (the no-regression contract the resolver preserves).
 *  - **Lv4** — the midpoint; asserts the interpolation truncation the resolver renders.
 *  - **Lv7** — the documented cap. Must hit exactly, not 1 off from `.toInt()` drift.
 */
class CardTypeTest {
    @Test
    fun `9 entries exist`() {
        assertEquals(9, CardType.entries.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IRON_SKIN — COMMON, primary-only. valueLv1=10, valueLv7=42
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `IRON_SKIN interpolates 10 to 26 to 42 with toInt truncation`() {
        assertEquals(10, CardType.IRON_SKIN.effectAtLevel(1).toInt())
        // 10 + 32 × 3/6 = 26
        assertEquals(26, CardType.IRON_SKIN.effectAtLevel(4).toInt())
        assertEquals(42, CardType.IRON_SKIN.effectAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHARP_SHOOTER — COMMON, primary-only. valueLv1=15, valueLv7=45
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `SHARP_SHOOTER interpolates 15 to 30 to 45`() {
        assertEquals(15, CardType.SHARP_SHOOTER.effectAtLevel(1).toInt())
        // 15 + 30 × 3/6 = 30
        assertEquals(30, CardType.SHARP_SHOOTER.effectAtLevel(4).toInt())
        assertEquals(45, CardType.SHARP_SHOOTER.effectAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CASH_GRAB — COMMON, primary-only. valueLv1=20, valueLv7=65
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `CASH_GRAB interpolates 20 to 42 to 65 truncating 42_5`() {
        assertEquals(20, CardType.CASH_GRAB.effectAtLevel(1).toInt())
        // 20 + 45 × 3/6 = 42.5 → 42
        assertEquals(42, CardType.CASH_GRAB.effectAtLevel(4).toInt())
        assertEquals(65, CardType.CASH_GRAB.effectAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VAMPIRIC_TOUCH — RARE, primary-only. valueLv1=5, valueLv7=20
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `VAMPIRIC_TOUCH interpolates 5 to 12 to 20 truncating 12_5`() {
        assertEquals(5, CardType.VAMPIRIC_TOUCH.effectAtLevel(1).toInt())
        // 5 + 15 × 3/6 = 12.5 → 12
        assertEquals(12, CardType.VAMPIRIC_TOUCH.effectAtLevel(4).toInt())
        assertEquals(20, CardType.VAMPIRIC_TOUCH.effectAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CHAIN_REACTION — RARE, integer count. valueLv1=2, valueLv7=5
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `CHAIN_REACTION interpolates 2 to 3 to 5 truncating 3_5`() {
        assertEquals(2, CardType.CHAIN_REACTION.effectAtLevel(1).toInt())
        // 2 + 3 × 3/6 = 3.5 → 3 (matches ApplyCardEffects casting to Int at the same level)
        assertEquals(3, CardType.CHAIN_REACTION.effectAtLevel(4).toInt())
        assertEquals(5, CardType.CHAIN_REACTION.effectAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SECOND_WIND — RARE, primary-only with 100% cap. valueLv1=50, valueLv7=100
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `SECOND_WIND interpolates 50 to 75 to 100 and respects the 100 percent cap`() {
        assertEquals(50, CardType.SECOND_WIND.effectAtLevel(1).toInt())
        // 50 + 50 × 3/6 = 75
        assertEquals(75, CardType.SECOND_WIND.effectAtLevel(4).toInt())
        assertEquals(100, CardType.SECOND_WIND.effectAtLevel(7).toInt())
        // The linear curve would exceed 100 past Lv7; the cap must clamp it.
        assertEquals(100.0, CardType.SECOND_WIND.effectAtLevel(7))
        assertTrue(CardType.SECOND_WIND.effectAtLevel(10) <= 100.0, "SECOND_WIND must cap at 100")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WALKING_FORTRESS — EPIC, primary buff + secondary debuff.
    // primary 50→125, secondary 20→5
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `WALKING_FORTRESS interpolates both primary and secondary`() {
        assertEquals(50, CardType.WALKING_FORTRESS.effectAtLevel(1).toInt())
        assertEquals(20, CardType.WALKING_FORTRESS.secondaryAtLevel(1).toInt())
        // primary 50 + 75 × 3/6 = 87.5 → 87 ; secondary 20 + (5-20) × 3/6 = 12.5 → 12
        assertEquals(87, CardType.WALKING_FORTRESS.effectAtLevel(4).toInt())
        assertEquals(12, CardType.WALKING_FORTRESS.secondaryAtLevel(4).toInt())
        assertEquals(125, CardType.WALKING_FORTRESS.effectAtLevel(7).toInt())
        assertEquals(5, CardType.WALKING_FORTRESS.secondaryAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GLASS_CANNON — EPIC, primary buff + secondary debuff.
    // primary 80→140, secondary 40→10
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `GLASS_CANNON interpolates both primary and secondary`() {
        assertEquals(80, CardType.GLASS_CANNON.effectAtLevel(1).toInt())
        assertEquals(40, CardType.GLASS_CANNON.secondaryAtLevel(1).toInt())
        // primary 80 + 60 × 3/6 = 110 ; secondary 40 + (10-40) × 3/6 = 25
        assertEquals(110, CardType.GLASS_CANNON.effectAtLevel(4).toInt())
        assertEquals(25, CardType.GLASS_CANNON.secondaryAtLevel(4).toInt())
        assertEquals(140, CardType.GLASS_CANNON.effectAtLevel(7).toInt())
        assertEquals(10, CardType.GLASS_CANNON.secondaryAtLevel(7).toInt())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP_SURGE — EPIC, multiplier. valueLv1=2.0, valueLv7=5.0
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `STEP_SURGE interpolates 2_0 to 3_5 to 5_0`() {
        assertEquals(2.0, CardType.STEP_SURGE.effectAtLevel(1))
        // 2.0 + 3.0 × 3/6 = 3.5 (the resolver's formatMultiplier renders this as "3.5")
        assertEquals(3.5, CardType.STEP_SURGE.effectAtLevel(4))
        assertEquals(5.0, CardType.STEP_SURGE.effectAtLevel(7))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cross-card invariants
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `Lv1 and Lv7 primary values differ for every card`() {
        // Direct regression for #53 — pre-fix every level rendered effectLv1 verbatim; the
        // numeric curve must actually move between the endpoints so the resolver can too.
        for (type in CardType.entries) {
            assertNotEquals(
                type.effectAtLevel(1).toInt(),
                type.effectAtLevel(type.maxLevel).toInt(),
                "$type Lv1 and Lv${type.maxLevel} primary values must differ",
            )
        }
    }
}
