package com.whitefang.stepsofbabylon.presentation.battle.biome

import com.whitefang.stepsofbabylon.domain.model.Biome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiomeThemeTest {
    @Test
    fun `all 5 biomes produce a theme`() {
        Biome.entries.forEach { BiomeTheme.forBiome(it) }
    }

    @Test
    fun `each theme has exactly 5 ziggurat colors`() {
        Biome.entries.forEach { biome ->
            assertEquals(5, BiomeTheme.forBiome(biome).zigguratColors.size, "$biome should have 5 ziggurat colors")
        }
    }

    @Test
    fun `particle counts are positive`() {
        Biome.entries.forEach { biome ->
            assertTrue(BiomeTheme.forBiome(biome).particleCount > 0, "$biome should have particles")
        }
    }

    @Test
    fun `sky colors differ across biomes`() {
        val tops = Biome.entries.map { BiomeTheme.forBiome(it).skyColorTop }.toSet()
        assertEquals(5, tops.size, "Each biome should have a unique sky color")
    }
}
