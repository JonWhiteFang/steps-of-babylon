package com.whitefang.stepsofbabylon.presentation.ui

/**
 * Formats an UPPER_SNAKE_CASE identifier (typically an enum `name`) as a human-readable
 * "Title Case" label: `IRON_SKIN` → `Iron Skin`. Single source of truth for the three
 * previously-duplicated private `formatName` copies in CardsScreen / LabsScreen /
 * UnclaimedSuppliesScreen (consolidated per code-review reuse finding).
 */
fun String.toDisplayName(): String =
    split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
