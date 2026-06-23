package com.whitefang.stepsofbabylon.presentation.ui

import java.util.Locale

/**
 * Formats an UPPER_SNAKE_CASE identifier (typically an enum `name`) as a human-readable
 * "Title Case" label: `IRON_SKIN` → `Iron Skin`. Single source of truth for the three
 * previously-duplicated private `formatName` copies in CardsScreen / LabsScreen /
 * UnclaimedSuppliesScreen (consolidated per code-review reuse finding).
 *
 * #262 L89: `lowercase(Locale.ROOT)` keeps the transform locale-independent (the JVM-default
 * `lowercase()` would corrupt an `I` to dotless `ı` under a Turkish locale). The
 * `replaceFirstChar { c -> c.uppercase() }` uses `Char.uppercase()`, which has no Locale overload
 * and is locale-independent here.
 */
fun String.toDisplayName(): String =
    split("_").joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() } }
