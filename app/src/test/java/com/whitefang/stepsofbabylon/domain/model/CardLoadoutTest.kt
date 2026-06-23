package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CardLoadoutTest {
    @Test
    fun `empty loadout is valid`() {
        CardLoadout()
    }

    @Test
    fun `add up to 3 cards succeeds`() {
        val loadout =
            CardLoadout()
                .add(CardType.IRON_SKIN)
                .add(CardType.SHARP_SHOOTER)
                .add(CardType.CASH_GRAB)
        assertEquals(3, loadout.cards.size)
    }

    @Test
    fun `adding 4th card throws`() {
        val full = CardLoadout(listOf(CardType.IRON_SKIN, CardType.SHARP_SHOOTER, CardType.CASH_GRAB))
        assertThrows<IllegalArgumentException> { full.add(CardType.VAMPIRIC_TOUCH) }
    }

    @Test
    fun `duplicate card throws`() {
        assertThrows<IllegalArgumentException> {
            CardLoadout(listOf(CardType.IRON_SKIN, CardType.IRON_SKIN))
        }
    }

    @Test
    fun `remove works`() {
        val loadout = CardLoadout(listOf(CardType.IRON_SKIN)).remove(CardType.IRON_SKIN)
        assertEquals(0, loadout.cards.size)
    }
}
