package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Pure-JVM guard for the Bundle E (#164) custom-font wiring. Pins that the Display+Headline tiers
 * carry the Cinzel family (the app's identity lever) and that displayMedium/displayLarge are DEFINED
 * with Cinzel — closing the latent gap where OnboardingScreen consumed displayMedium and silently
 * fell back to the Material3 stock style. Body/Title/Label tiers must stay Roboto (FontFamily.Default).
 */
class SobTypographyTest {
    @Test
    fun `display and headline tiers use the Cinzel family`() {
        val cinzelTiers =
            listOf(
                "displayLarge" to SobTypography.displayLarge.fontFamily,
                "displayMedium" to SobTypography.displayMedium.fontFamily,
                "displaySmall" to SobTypography.displaySmall.fontFamily,
                "headlineLarge" to SobTypography.headlineLarge.fontFamily,
                "headlineMedium" to SobTypography.headlineMedium.fontFamily,
                "headlineSmall" to SobTypography.headlineSmall.fontFamily,
            )
        for ((name, family) in cinzelTiers) {
            assertEquals(Cinzel, family, "$name must use the Cinzel family")
            assertNotEquals(FontFamily.Default, family, "$name must NOT fall back to Roboto")
        }
    }

    @Test
    fun `body and label tiers stay on the default Roboto family`() {
        val robotoTiers =
            listOf(
                "titleLarge" to SobTypography.titleLarge.fontFamily,
                "titleMedium" to SobTypography.titleMedium.fontFamily,
                "bodyLarge" to SobTypography.bodyLarge.fontFamily,
                "bodyMedium" to SobTypography.bodyMedium.fontFamily,
                "labelLarge" to SobTypography.labelLarge.fontFamily,
            )
        for ((name, family) in robotoTiers) {
            assertEquals(FontFamily.Default, family, "$name must stay Roboto for legibility")
        }
    }
}
