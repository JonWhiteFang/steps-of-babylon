package com.whitefang.stepsofbabylon.presentation.battle.biome

import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #421 (#391 free-lane, C1): pins the [BattlePalette] art-colour source of truth and its wiring into
 * [BiomeTheme]. Complements [BiomeThemeTest] (which pins the `BiomeTheme` shape) by asserting the two
 * agree value-for-value — a regression that changed a palette int but not the theme (or vice versa)
 * fails here.
 */
class BattlePaletteTest {
    @Test
    fun `every biome has a palette with exactly 5 ziggurat colors`() {
        Biome.entries.forEach { biome ->
            assertEquals(5, BattlePalette.forBiome(biome).zigguratColors.size, "$biome should have 5 ziggurat colors")
        }
    }

    @Test
    fun `BiomeTheme derives every field from BattlePalette`() {
        Biome.entries.forEach { biome ->
            val theme = BiomeTheme.forBiome(biome)
            val palette = BattlePalette.forBiome(biome)
            assertEquals(palette.skyColorTop, theme.skyColorTop, "$biome skyColorTop")
            assertEquals(palette.skyColorBottom, theme.skyColorBottom, "$biome skyColorBottom")
            assertEquals(palette.groundColor, theme.groundColor, "$biome groundColor")
            assertEquals(palette.zigguratColors, theme.zigguratColors, "$biome zigguratColors")
            assertEquals(palette.enemyTint, theme.enemyTint, "$biome enemyTint")
            assertEquals(palette.particleColor, theme.particleColor, "$biome particleColor")
            assertEquals(palette.particleDriftX, theme.particleDriftX, "$biome particleDriftX")
            assertEquals(palette.particleDriftY, theme.particleDriftY, "$biome particleDriftY")
            assertEquals(palette.particleCount, theme.particleCount, "$biome particleCount")
        }
    }

    @Test
    fun `sky colors are unique per biome`() {
        val tops = Biome.entries.map { BattlePalette.forBiome(it).skyColorTop }.toSet()
        assertEquals(Biome.entries.size, tops.size, "Each biome should have a unique sky-top color")
    }

    @Test
    fun `enemy base colors cover every enemy type and are opaque`() {
        EnemyType.entries.forEach { type ->
            val color = BattlePalette.enemyBaseColors[type]
            assertTrue(color != null, "$type should have a base color")
            assertEquals(0xFF, (color!! ushr 24) and 0xFF, "$type base color should be fully opaque")
        }
    }

    @Test
    fun `ziggurat default ramp has 5 stops`() {
        assertEquals(5, BattlePalette.zigguratDefaultLayers.size)
    }
}
