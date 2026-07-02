package com.whitefang.stepsofbabylon.presentation.cards

import androidx.compose.ui.test.junit4.createComposeRule
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.presentation.ui.effectDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #34 (i18n phase 3 G) — regression coverage for the presentation resolver
 * [CardType.effectDescription], which replaced the domain `CardType.effectDescriptionAtLevel`
 * (deleted so `domain/` stays Android-free). The exact-English string assertions that used to
 * live in `domain/model/CardTypeTest` moved here; they now assert the @StringRes-resolved output,
 * which must remain **byte-identical** to the old domain string (same `.toInt()` truncation, same
 * templates, same STEP_SURGE one-decimal `formatMultiplier`).
 *
 * The resolver is `@Composable` (it calls `stringResource`), so we render it inside a
 * `composeRule.setContent { }` block and capture the resolved strings — same Robolectric/JVM lane
 * wiring as [CardsScreenTest]. Compose's `setContent` may only be called ONCE per test, so we
 * resolve every (CardType × level) pair in a single composition into [resolved] and have each test
 * assert against that map. [resolve] returns the pre-computed string (no second composition).
 *
 * Endpoint contract (mirrors the pre-move CardTypeTest):
 *  - **Lv1** equals [CardType.effectLv1] verbatim — the no-regression baseline.
 *  - **Lv4** is the interpolated midpoint (asserts the `.toInt()` truncation renders correctly).
 *  - **Lv7** equals [CardType.effectLv7] verbatim — the curve hits the documented cap exactly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class CardEffectDescriptionTest {
    @get:Rule
    val composeRule = createComposeRule()

    // (CardType, level) -> resolved English string, populated by the one-shot composition below.
    private val resolved = mutableMapOf<Pair<CardType, Int>, String>()

    @Before
    fun resolveAll() {
        // ONE setContent for the whole test — resolve every card at every valid level in a single
        // composition (Compose forbids a second setContent per test).
        composeRule.setContent {
            for (type in CardType.entries) {
                for (level in 1..type.maxLevel) {
                    resolved[type to level] = type.effectDescription(level)
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Returns the string resolved during [resolveAll] (no new composition). */
    private fun resolve(
        type: CardType,
        level: Int,
    ): String = resolved.getValue(type to level)

    // ── IRON_SKIN — flat value, no percent glyph (#21) ────────────────────────

    @Test
    fun `IRON_SKIN Lv1 matches effectLv1`() {
        assertEquals("+10 Defense Absolute", resolve(CardType.IRON_SKIN, 1))
        assertEquals(CardType.IRON_SKIN.effectLv1, resolve(CardType.IRON_SKIN, 1))
    }

    @Test
    fun `IRON_SKIN Lv4 shows interpolated value`() {
        assertEquals("+26 Defense Absolute", resolve(CardType.IRON_SKIN, 4))
    }

    @Test
    fun `IRON_SKIN Lv7 matches effectLv7`() {
        assertEquals("+42 Defense Absolute", resolve(CardType.IRON_SKIN, 7))
        assertEquals(CardType.IRON_SKIN.effectLv7, resolve(CardType.IRON_SKIN, 7))
    }

    @Test
    fun `R21 IRON_SKIN has no percent sign at any level`() {
        // The flat-vs-percent unit fix (#21): defenseAbsolute is flat damage blocked, so no `%`.
        for (level in 1..CardType.IRON_SKIN.maxLevel) {
            val desc = resolve(CardType.IRON_SKIN, level)
            assertFalse("IRON_SKIN Lv$level must not show a percent sign: \"$desc\"", desc.contains("%"))
        }
    }

    // ── SHARP_SHOOTER ─────────────────────────────────────────────────────────

    @Test
    fun `SHARP_SHOOTER Lv1 matches effectLv1`() {
        assertEquals("+15% Critical Chance", resolve(CardType.SHARP_SHOOTER, 1))
    }

    @Test
    fun `SHARP_SHOOTER Lv4 shows interpolated value`() {
        assertEquals("+30% Critical Chance", resolve(CardType.SHARP_SHOOTER, 4))
    }

    @Test
    fun `SHARP_SHOOTER Lv7 matches effectLv7`() {
        assertEquals("+45% Critical Chance", resolve(CardType.SHARP_SHOOTER, 7))
    }

    // ── CASH_GRAB ─────────────────────────────────────────────────────────────

    @Test
    fun `CASH_GRAB Lv1 matches effectLv1`() {
        assertEquals("+20% Cash from kills", resolve(CardType.CASH_GRAB, 1))
    }

    @Test
    fun `CASH_GRAB Lv4 shows interpolated value truncated from 42_5`() {
        assertEquals("+42% Cash from kills", resolve(CardType.CASH_GRAB, 4))
    }

    @Test
    fun `CASH_GRAB Lv7 matches effectLv7`() {
        assertEquals("+65% Cash from kills", resolve(CardType.CASH_GRAB, 7))
    }

    // ── VAMPIRIC_TOUCH ────────────────────────────────────────────────────────

    @Test
    fun `VAMPIRIC_TOUCH Lv1 matches effectLv1`() {
        assertEquals("+5% Lifesteal", resolve(CardType.VAMPIRIC_TOUCH, 1))
    }

    @Test
    fun `VAMPIRIC_TOUCH Lv4 shows interpolated value truncated from 12_5`() {
        assertEquals("+12% Lifesteal", resolve(CardType.VAMPIRIC_TOUCH, 4))
    }

    @Test
    fun `VAMPIRIC_TOUCH Lv7 matches effectLv7`() {
        assertEquals("+20% Lifesteal", resolve(CardType.VAMPIRIC_TOUCH, 7))
    }

    // ── CHAIN_REACTION — integer count, no percent glyph ──────────────────────

    @Test
    fun `CHAIN_REACTION Lv1 matches effectLv1`() {
        assertEquals("+2 Bounce Shot targets", resolve(CardType.CHAIN_REACTION, 1))
    }

    @Test
    fun `CHAIN_REACTION Lv4 shows interpolated count truncated from 3_5`() {
        assertEquals("+3 Bounce Shot targets", resolve(CardType.CHAIN_REACTION, 4))
    }

    @Test
    fun `CHAIN_REACTION Lv7 matches effectLv7`() {
        assertEquals("+5 Bounce Shot targets", resolve(CardType.CHAIN_REACTION, 7))
    }

    // ── SECOND_WIND — 100% cap ────────────────────────────────────────────────

    @Test
    fun `SECOND_WIND Lv1 matches effectLv1`() {
        assertEquals("Revive once at 50% HP", resolve(CardType.SECOND_WIND, 1))
    }

    @Test
    fun `SECOND_WIND Lv4 shows interpolated value`() {
        assertEquals("Revive once at 75% HP", resolve(CardType.SECOND_WIND, 4))
    }

    @Test
    fun `SECOND_WIND Lv7 matches effectLv7 and respects the 100 percent cap`() {
        assertEquals("Revive once at 100% HP", resolve(CardType.SECOND_WIND, 7))
    }

    // ── WALKING_FORTRESS — primary buff + secondary debuff ────────────────────

    @Test
    fun `WALKING_FORTRESS Lv1 matches effectLv1`() {
        assertEquals("+50% Health, -20% Attack Speed", resolve(CardType.WALKING_FORTRESS, 1))
    }

    @Test
    fun `WALKING_FORTRESS Lv4 shows both interpolated values`() {
        assertEquals("+87% Health, -12% Attack Speed", resolve(CardType.WALKING_FORTRESS, 4))
    }

    @Test
    fun `WALKING_FORTRESS Lv7 matches effectLv7`() {
        assertEquals("+125% Health, -5% Attack Speed", resolve(CardType.WALKING_FORTRESS, 7))
    }

    // ── GLASS_CANNON — primary buff + secondary debuff ────────────────────────

    @Test
    fun `GLASS_CANNON Lv1 matches effectLv1`() {
        assertEquals("+80% Damage, -40% Health", resolve(CardType.GLASS_CANNON, 1))
    }

    @Test
    fun `GLASS_CANNON Lv4 shows both interpolated values`() {
        assertEquals("+110% Damage, -25% Health", resolve(CardType.GLASS_CANNON, 4))
    }

    @Test
    fun `GLASS_CANNON Lv7 matches effectLv7`() {
        assertEquals("+140% Damage, -10% Health", resolve(CardType.GLASS_CANNON, 7))
    }

    // ── STEP_SURGE — multiplier, one-decimal formatting, trailing .0 stripped ─

    @Test
    fun `STEP_SURGE Lv1 matches effectLv1 and drops trailing zero`() {
        assertEquals("Earn 2x Gems this round", resolve(CardType.STEP_SURGE, 1))
    }

    @Test
    fun `STEP_SURGE Lv4 shows fractional multiplier with one decimal`() {
        assertEquals("Earn 3.5x Gems this round", resolve(CardType.STEP_SURGE, 4))
    }

    @Test
    fun `STEP_SURGE Lv7 matches effectLv7`() {
        assertEquals("Earn 5x Gems this round", resolve(CardType.STEP_SURGE, 7))
    }

    // ── Cross-card smoke + endpoint self-checks ───────────────────────────────

    @Test
    fun `every card resolves a non-empty description at every level`() {
        for (type in CardType.entries) {
            for (level in 1..type.maxLevel) {
                assertFalse("$type Lv$level produced a blank description", resolve(type, level).isBlank())
            }
        }
    }

    @Test
    fun `every Lv1 resolves to effectLv1 and Lv7 to effectLv7`() {
        // The resolver must match the enum's baked endpoint constants verbatim — the invariant
        // the pre-move `effectDescriptionAtLevel(1) == effectLv1` assertions guarded.
        for (type in CardType.entries) {
            assertEquals("$type Lv1 must equal effectLv1", type.effectLv1, resolve(type, 1))
            assertEquals("$type Lv7 must equal effectLv7", type.effectLv7, resolve(type, type.maxLevel))
        }
    }
}
