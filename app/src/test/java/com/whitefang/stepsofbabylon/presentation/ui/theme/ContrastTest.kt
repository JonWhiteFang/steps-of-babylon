package com.whitefang.stepsofbabylon.presentation.ui.theme

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * #213: pins primary-button label contrast to WCAG AA-normal (≥ 4.5:1). `onPrimary` was the brand
 * `DeepBronze` on `Gold` = ~4.19:1 (fails normal text); the fix is a dedicated `OnGold` text-role token
 * (~5.99:1). Pure JVM — WCAG relative-luminance math over plain ARGB Ints (no Compose `Color`),
 * mirroring `ColorLerpTest`/`RarityTest`. A future token tweak that drops below 4.5:1 fails the build.
 */
class ContrastTest {
    // Read the REAL token ARGB consts (plain Ints, no Compose Color) so a regression to the production
    // token fails this guard — not a hardcoded copy that could silently drift from Color.kt.
    private val gold = GoldArgb
    private val onGold = OnGoldArgb
    private val deepBronze = 0xFF6B3A2A.toInt() // the pre-#213 value, intentionally a literal (documents history)

    private fun channelLuminance(c8: Int): Double {
        val s = c8 / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b)
    }

    private fun contrastRatio(
        a: Int,
        b: Int,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `OnGold on Gold passes WCAG AA for normal text`() {
        val ratio = contrastRatio(onGold, gold)
        assertTrue(
            ratio >= 4.5,
            "OnGold on Gold must be >= 4.5:1 for labelLarge (normal text); was $ratio",
        )
    }

    @Test
    fun `the old DeepBronze on Gold failed AA-normal — documents why OnGold exists`() {
        val ratio = contrastRatio(deepBronze, gold)
        assertTrue(
            ratio < 4.5,
            "regression note: DeepBronze on Gold is the pre-#213 value and should fail 4.5:1; was $ratio",
        )
    }
}
