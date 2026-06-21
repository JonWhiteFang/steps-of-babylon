package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.compose.ui.test.junit4.createComposeRule
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #20: the unclaimed-supplies row reused `rewardAmount` as a quantity for CARD_COPY drops, but
 * for CARD_COPY that field is actually a card-TYPE index (0..8) — ClaimSupplyDrop awards exactly
 * ONE copy of `CardType.entries[rewardAmount % size]`. So a CARD_COPY drop rendered "+0 Card Copy"
 * .. "+8 Card Copy" (and "+0" reads as a broken/empty reward) while the player always got 1 copy
 * of a specific card. The fix renders the resolved card name + a fixed "x1" for CARD_COPY, and
 * keeps the "+N <label>" shape for the genuine-quantity rewards (Steps/Gems/Power Stones).
 *
 * #260: formatSupplyReward is now @Composable (it resolves plural string resources for the
 * genuine-quantity rewards), so this is a Robolectric/Compose test that captures the rendered
 * String through the compose rule. `setContent` may be called only ONCE per test, so the looping
 * cases format every drop inside a single composition. Quantity expectations are the plural output
 * (singular at n=1); the #20 CARD_COPY guards are preserved.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class SupplyRewardFormatTest {

    @get:Rule val rule = createComposeRule()

    private fun drop(reward: SupplyDropReward, amount: Int) =
        SupplyDrop(id = 1, trigger = SupplyDropTrigger.RANDOM, reward = reward, rewardAmount = amount, claimed = false, createdAt = 0L)

    /** Renders every drop inside ONE composition; returns the rendered strings in input order. */
    private fun captureAll(vararg drops: SupplyDrop): List<String> {
        val out = arrayOfNulls<String>(drops.size)
        rule.setContent {
            drops.forEachIndexed { i, d -> out[i] = formatSupplyReward(d) }
        }
        rule.waitForIdle()
        return out.map { it!! }
    }

    private fun capture(drop: SupplyDrop): String = captureAll(drop).single()

    @Test
    fun `steps reward renders as a quantity`() {
        assertEquals("+150 Steps", capture(drop(SupplyDropReward.STEPS, 150)))
    }

    @Test
    fun `gems reward renders as a quantity`() {
        assertEquals("+5 Gems", capture(drop(SupplyDropReward.GEMS, 5)))
    }

    @Test
    fun `power stones reward renders as a quantity (singular at one)`() {
        assertEquals("+1 Power Stone", capture(drop(SupplyDropReward.POWER_STONES, 1)))
    }

    @Test
    fun `R20 card copy renders the resolved card name with a fixed quantity of one`() {
        // rewardAmount 0 → CardType.entries[0] = IRON_SKIN (the same resolution ClaimSupplyDrop uses).
        assertEquals("Iron Skin x1", capture(drop(SupplyDropReward.CARD_COPY, 0)))
    }

    @Test
    fun `R20 card copy never renders the raw index as a quantity`() {
        // The core bug: "+0 Card Copy" / "+8 Card Copy". No CARD_COPY label may show "+<index>"
        // and a zero-index drop must NOT read as an empty "+0" reward.
        val drops = (0..8).map { drop(SupplyDropReward.CARD_COPY, it) }
        captureAll(*drops.toTypedArray()).forEach { label ->
            assertFalse("CARD_COPY must not render a '+N' quantity: \"$label\"", label.startsWith("+"))
            assertFalse("CARD_COPY must show the resolved card name, not the raw label: \"$label\"", label.contains("Card Copy"))
            assertTrue("CARD_COPY always awards exactly one copy: \"$label\"", label.endsWith("x1"))
        }
    }

    @Test
    fun `R20 card copy resolves the same card type ClaimSupplyDrop awards`() {
        // The displayed card must match what the player actually receives, for every index.
        val drops = (0..8).map { drop(SupplyDropReward.CARD_COPY, it) }
        val labels = captureAll(*drops.toTypedArray())
        for (index in 0..8) {
            val expectedType = CardType.entries[index % CardType.entries.size]
            val expectedName = expectedType.name.split("_")
                .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            assertEquals("$expectedName x1", labels[index])
        }
    }
}
