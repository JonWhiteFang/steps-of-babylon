package com.whitefang.stepsofbabylon.presentation.supplies

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #20: the unclaimed-supplies row reused `rewardAmount` as a quantity for CARD_COPY drops, but
 * for CARD_COPY that field is actually a card-TYPE index (0..8) — ClaimSupplyDrop awards exactly
 * ONE copy of `CardType.entries[rewardAmount % size]`. So a CARD_COPY drop rendered "+0 Card Copy"
 * .. "+8 Card Copy" (and "+0" reads as a broken/empty reward) while the player always got 1 copy
 * of a specific card. The fix renders the resolved card name + a fixed "x1" for CARD_COPY, and
 * keeps the "+N <label>" shape for the genuine-quantity rewards (Steps/Gems/Power Stones).
 */
class SupplyRewardFormatTest {

    private fun drop(reward: SupplyDropReward, amount: Int) =
        SupplyDrop(id = 1, trigger = SupplyDropTrigger.RANDOM, reward = reward, rewardAmount = amount, claimed = false, createdAt = 0L)

    @Test
    fun `steps reward renders as a quantity`() {
        assertEquals("+150 Steps", formatSupplyReward(drop(SupplyDropReward.STEPS, 150)))
    }

    @Test
    fun `gems reward renders as a quantity`() {
        assertEquals("+3 Gems", formatSupplyReward(drop(SupplyDropReward.GEMS, 3)))
    }

    @Test
    fun `power stones reward renders as a quantity`() {
        assertEquals("+1 Power Stones", formatSupplyReward(drop(SupplyDropReward.POWER_STONES, 1)))
    }

    @Test
    fun `R20 card copy renders the resolved card name with a fixed quantity of one`() {
        // rewardAmount 0 → CardType.entries[0] = IRON_SKIN (the same resolution ClaimSupplyDrop uses).
        val label = formatSupplyReward(drop(SupplyDropReward.CARD_COPY, 0))
        assertEquals("Iron Skin x1", label)
    }

    @Test
    fun `R20 card copy never renders the raw index as a quantity`() {
        // The core bug: "+0 Card Copy" / "+8 Card Copy". No CARD_COPY label may show "+<index>"
        // and a zero-index drop must NOT read as an empty "+0" reward.
        for (index in 0..8) {
            val label = formatSupplyReward(drop(SupplyDropReward.CARD_COPY, index))
            assertFalse(label.startsWith("+"), "CARD_COPY must not render a '+N' quantity: \"$label\"")
            assertFalse(label.contains("Card Copy"), "CARD_COPY must show the resolved card name, not the raw label: \"$label\"")
            assertTrue(label.endsWith("x1"), "CARD_COPY always awards exactly one copy: \"$label\"")
        }
    }

    @Test
    fun `R20 card copy resolves the same card type ClaimSupplyDrop awards`() {
        // The displayed card must match what the player actually receives, for every index.
        for (index in 0..8) {
            val expectedType = CardType.entries[index % CardType.entries.size]
            val expectedName = expectedType.name.split("_")
                .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            assertEquals("$expectedName x1", formatSupplyReward(drop(SupplyDropReward.CARD_COPY, index)))
        }
    }
}
