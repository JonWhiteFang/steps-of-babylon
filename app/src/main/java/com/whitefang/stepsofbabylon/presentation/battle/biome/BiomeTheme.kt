package com.whitefang.stepsofbabylon.presentation.battle.biome

import com.whitefang.stepsofbabylon.domain.model.Biome

data class BiomeTheme(
    val skyColorTop: Int,
    val skyColorBottom: Int,
    val groundColor: Int,
    val zigguratColors: List<Int>,
    val enemyTint: Int,
    /** The named ambient-particle emitter config (#424, C4). */
    val particles: BattlePalette.ParticleConfig,
) {
    // Flat convenience accessors — kept so existing readers (GameEngine, BackgroundRenderer) are untouched.
    val particleColor: Int get() = particles.color
    val particleDriftX: Float get() = particles.driftX
    val particleDriftY: Float get() = particles.driftY
    val particleCount: Int get() = particles.count

    companion object {
        /**
         * #421 (#391 free-lane): the per-biome art colours now derive from the single source of truth
         * [BattlePalette]. This is a thin adapter that maps [BattlePalette.BiomeColors] → [BiomeTheme];
         * the values are unchanged (pinned by `BiomeThemeTest`). Consumers of [BiomeTheme] are untouched.
         */
        fun forBiome(biome: Biome): BiomeTheme =
            with(BattlePalette.forBiome(biome)) {
                BiomeTheme(
                    skyColorTop = skyColorTop,
                    skyColorBottom = skyColorBottom,
                    groundColor = groundColor,
                    zigguratColors = zigguratColors,
                    enemyTint = enemyTint,
                    particles = particles,
                )
            }
    }
}
