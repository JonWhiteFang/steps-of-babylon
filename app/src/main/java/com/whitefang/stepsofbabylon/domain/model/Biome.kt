package com.whitefang.stepsofbabylon.domain.model

enum class Biome(val tierRange: IntRange, val isComingSoon: Boolean = false) {
    HANGING_GARDENS(1..3),
    BURNING_SANDS(4..6),
    FROZEN_ZIGGURATS(7..8),
    UNDERWORLD_OF_KUR(9..10),
    CELESTIAL_GATE(11..Int.MAX_VALUE, isComingSoon = true),
    ;

    companion object {
        fun forTier(tier: Int): Biome =
            entries.first { tier in it.tierRange }
    }
}
