package com.whitefang.stepsofbabylon.presentation.cards

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackRevealStateTest {
    @Test
    fun `round-trips a multi-card list preserving type, isNew, and copies`() {
        val original = listOf(
            CardResult(CardType.entries.first(), isNew = true, copiesAwarded = 1),
            CardResult(CardType.entries.last(), isNew = false, copiesAwarded = 3),
        )
        val restored = original.toPackRevealState().toCardResults()
        assertEquals(original, restored, "DTO round-trip must preserve type/isNew/copiesAwarded")
        assertTrue(restored[0].isNew, "the NEW! badge flag must survive")
        assertEquals(3, restored[1].copiesAwarded, "copies must survive")
    }

    @Test
    fun `unresolvable card type name decodes to null payload (graceful drop)`() {
        val dto = PackRevealState(listOf(RevealedCard("NOT_A_REAL_CARD", isNew = true, copiesAwarded = 1)))
        assertNull(dto.toCardResultsOrNull(), "an unknown enum name drops the reveal rather than crashing")
    }
}
