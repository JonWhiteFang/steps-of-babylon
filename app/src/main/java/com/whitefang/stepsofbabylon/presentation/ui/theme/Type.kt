package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.whitefang.stepsofbabylon.R

/**
 * Cinzel — the Steps of Babylon display face (Bundle E, #164). Roman inscriptional caps, SIL OFL 1.1,
 * bundled in res/font/. Applied to the Display + Headline tiers + (via headlineLarge) the battle
 * BiomeTransitionOverlay biome name. Only Regular (400) + Bold (700) are bundled; every Cinzel'd tier
 * is FontWeight.Bold, so 700 is what renders (400 is the normal-weight anchor). A future Cinzel tier at
 * a non-bundled weight would silently synthesize — bundle that weight if you add one.
 */
val Cinzel =
    FontFamily(
        Font(R.font.cinzel_regular, FontWeight.Normal),
        Font(R.font.cinzel_bold, FontWeight.Bold),
    )

/**
 * Steps of Babylon type scale.
 *
 * This object centralises the scale: deliberate weights + a touch of tracking on labels/titles for a
 * tighter, more "premium ancient" feel, with comfortable lineHeight for body copy.
 *
 * The Display + Headline tiers carry the custom [Cinzel] display face (Bundle E, #164) — this is the
 * app's typographic identity lever and re-themes every Display/Headline consumer app-wide (screen
 * headers, overlay titles, the Home steps hero, the Currency-dashboard balance, biome names on the
 * battle transition). Body / Title / Label tiers stay Roboto (FontFamily.Default) for dense-text
 * legibility. Swapping a tier's fontFamily here re-themes that tier everywhere from one place.
 */
val SobTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.15.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.2.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
