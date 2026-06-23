package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Shared UPPER_SNAKE_CASE → "Title Case" formatter extracted from the three identical private
 * `formatName` copies in CardsScreen / LabsScreen / UnclaimedSuppliesScreen (review reuse finding).
 */
class EnumDisplayNameTest {
    @Test
    fun `single token is title-cased`() {
        assertEquals("Common", "COMMON".toDisplayName())
    }

    @Test
    fun `underscore tokens become space-separated title case`() {
        assertEquals("Iron Skin", "IRON_SKIN".toDisplayName())
        assertEquals("Walking Fortress", "WALKING_FORTRESS".toDisplayName())
        assertEquals("Auto Upgrade Ai", "AUTO_UPGRADE_AI".toDisplayName())
    }

    @Test
    fun `matches the legacy formatName behaviour verbatim`() {
        // The exact transform the three private copies used: split('_'), lowercase each, capitalise.
        val legacy = { name: String ->
            name.split("_").joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() } }
        }
        for (sample in listOf("IRON_SKIN", "CASH_GRAB", "STEP_SURGE", "COMMON", "AUTO_UPGRADE_AI")) {
            assertEquals(legacy(sample), sample.toDisplayName(), "drift from legacy formatName for $sample")
        }
    }
}
