package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// --- Core Mesopotamian palette (the five brand colours) -----------------------------------------
// These are the identity anchors. Use them for fills, brand accents, and primary actions.
val Gold = Color(0xFFD4A843)
val LapisLazuli = Color(0xFF26619C)
val SandStone = Color(0xFFC2B280)
val DeepBronze = Color(0xFF6B3A2A)
val Ivory = Color(0xFFFFF8E7)

// --- Derived role tokens ------------------------------------------------------------------------
// Added in the look-&-feel polish pass. The deep brand colours read beautifully as *fills*, but
// several were being used as *text on the dark background*, where they fail contrast. These derived
// tokens give an accessible alternative for each such role while keeping the brand intact.

/**
 * Lapis tuned for text/icons on the dark [DeepBronze] surface. The brand [LapisLazuli] (#26619C)
 * is only ~1.45:1 on bronze (hard WCAG fail); this lighter sky-lapis is ~5.3:1 (passes AA for
 * normal text). Use [LapisLight] whenever lapis is the *foreground*; keep [LapisLazuli] for fills,
 * containers, and selected-state backgrounds.
 */
val LapisLight = Color(0xFFA7C7E7)

/** Slightly elevated bronze for cards/sheets that need to lift off the [DeepBronze] background. */
val BronzeSurface = Color(0xFF7A4636)

/** Body text on bronze. Ivory is ~8.8:1 on [DeepBronze] (passes AAA). */
val TextPrimary = Ivory

/** Secondary/supporting text — warm muted sandstone, large-text legible on bronze. */
val TextSecondary = Color(0xFFD8C7A8)

// --- Semantic status tokens (palette-aligned, not raw Material) ---------------------------------
/** Positive / success / "claimed" — a warm-leaning green so it sits with the bronze palette. */
val StatusSuccess = Color(0xFF6FD85D)
/** Caution / streak / season highlight. */
val StatusWarning = Color(0xFFFFD54A)
/** Destructive / error / "off" — warm coral that reads against bronze. */
val StatusDanger = Color(0xFFFF7043)

// --- Currency colours ---------------------------------------------------------------------------
// Premium currencies need distinct, *palette-aligned* identities. Previously these were raw
// Material green/purple/blue (#4CAF50 / #9C27B0 / #2196F3), which clashed with the brand.
/** Gems — lapis-lit cyan that ties to the brand blue while staying legible on dark. */
val GemColor = Color(0xFF6FC3E0)
/** Power Stones — mystic amethyst (kept purple for instant recognisability, but warmer/richer). */
val PowerStoneColor = Color(0xFFB57EDC)
/** Steps — gold, the lifeblood currency, matches the primary brand accent. */
val StepColor = Gold
