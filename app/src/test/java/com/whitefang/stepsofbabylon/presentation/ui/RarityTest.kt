package com.whitefang.stepsofbabylon.presentation.ui

import com.whitefang.stepsofbabylon.domain.model.CardRarity
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-JVM guards for the Bundle D (#163) rarity helper. Covers the mapping/label/colour functions
 * (the @Composable RarityBadge/EquippedChip/rarityBorder are visual, verified on-device per spec §5).
 */
class RarityTest {

    @Test
    fun `uw unlock cost maps to the expected tier for every weapon`() {
        // Iterate entries (not a hard-coded 6) so a re-priced or 7th UW forces a review here.
        val expected = mapOf(
            UltimateWeaponType.DEATH_WAVE to RarityTier.TIER_0,      // 50
            UltimateWeaponType.POISON_SWAMP to RarityTier.TIER_0,    // 60
            UltimateWeaponType.CHAIN_LIGHTNING to RarityTier.TIER_1, // 75
            UltimateWeaponType.CHRONO_FIELD to RarityTier.TIER_1,    // 75
            UltimateWeaponType.GOLDEN_ZIGGURAT to RarityTier.TIER_1, // 80
            UltimateWeaponType.BLACK_HOLE to RarityTier.TIER_2,      // 100
        )
        for (type in UltimateWeaponType.entries) {
            assertEquals(
                expected[type],
                uwRarityTier(type.unlockCost),
                "tier drift for $type (unlockCost=${type.unlockCost})",
            )
        }
    }

    @Test
    fun `uw tier boundaries are range-based (catch-all for re-prices)`() {
        assertEquals(RarityTier.TIER_0, uwRarityTier(60))
        assertEquals(RarityTier.TIER_1, uwRarityTier(61))
        assertEquals(RarityTier.TIER_1, uwRarityTier(89))
        assertEquals(RarityTier.TIER_2, uwRarityTier(90))
        assertEquals(RarityTier.TIER_0, uwRarityTier(0))     // below all → lowest
        assertEquals(RarityTier.TIER_2, uwRarityTier(9999))  // above all → highest
    }

    @Test
    fun `card rarity maps to the expected tier for every rarity`() {
        val expected = mapOf(
            CardRarity.COMMON to RarityTier.TIER_0,
            CardRarity.RARE to RarityTier.TIER_1,
            CardRarity.EPIC to RarityTier.TIER_2,
        )
        for (rarity in CardRarity.entries) {
            assertEquals(expected[rarity], cardRarityTier(rarity), "tier drift for $rarity")
        }
    }

    @Test
    fun `the three tiers map to three distinct colours`() {
        // Exercises the REAL color() (plain fun, JVM-reachable) — not a parallel shadow mapping.
        assertEquals(3, RarityTier.entries.map { it.color() }.toSet().size)
    }
}
