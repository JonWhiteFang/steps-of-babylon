package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiomeTest {
    @Test
    fun `tier to biome mapping`() {
        (1..3).forEach { assertEquals(Biome.HANGING_GARDENS, Biome.forTier(it)) }
        (4..6).forEach { assertEquals(Biome.BURNING_SANDS, Biome.forTier(it)) }
        (7..8).forEach { assertEquals(Biome.FROZEN_ZIGGURATS, Biome.forTier(it)) }
        (9..10).forEach { assertEquals(Biome.UNDERWORLD_OF_KUR, Biome.forTier(it)) }
        assertEquals(Biome.CELESTIAL_GATE, Biome.forTier(11))
        assertEquals(Biome.CELESTIAL_GATE, Biome.forTier(100))
    }

    @Test
    fun `V1X15 - only CELESTIAL_GATE is flagged isComingSoon`() {
        // Set-equality contract: any future biome flagged Coming Soon must be a
        // deliberate decision; this test fails on regression in either direction.
        assertTrue(
            Biome.CELESTIAL_GATE.isComingSoon,
            "CELESTIAL_GATE must be Coming Soon (Tier 11+ unreachable in v1.0)",
        )
        assertFalse(Biome.HANGING_GARDENS.isComingSoon)
        assertFalse(Biome.BURNING_SANDS.isComingSoon)
        assertFalse(Biome.FROZEN_ZIGGURATS.isComingSoon)
        assertFalse(Biome.UNDERWORLD_OF_KUR.isComingSoon)
    }
}
