package com.whitefang.stepsofbabylon.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = Gold,
        secondary = LapisLazuli,
        tertiary = SandStone,
        background = DeepBronze,
        surface = DeepBronze,
        // Slightly elevated bronze so Material surfaces that use surfaceVariant (chips, tonal buttons,
        // outlined-card containers) lift off the flat background instead of disappearing into it.
        surfaceVariant = BronzeSurface,
        onPrimary = OnGold, // #213: dark-on-gold text at ~5.99:1 (DeepBronze was only ~4.19:1, AA-normal fail)
        onSecondary = Ivory,
        onBackground = TextPrimary, // Ivory: ~8.8:1 on DeepBronze (AAA)
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        error = StatusDanger,
    )

@Composable
fun StepsOfBabylonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SobTypography,
        shapes = SobShapes,
        content = content,
    )
}
