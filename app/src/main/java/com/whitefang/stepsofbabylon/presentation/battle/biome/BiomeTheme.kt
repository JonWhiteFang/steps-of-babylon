package com.whitefang.stepsofbabylon.presentation.battle.biome

import com.whitefang.stepsofbabylon.domain.model.Biome

data class BiomeTheme(
    val skyColorTop: Int,
    val skyColorBottom: Int,
    val groundColor: Int,
    val zigguratColors: List<Int>,
    val enemyTint: Int,
    val particleColor: Int,
    val particleDriftX: Float,
    val particleDriftY: Float,
    val particleCount: Int,
) {
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
                    particleColor = particleColor,
                    particleDriftX = particleDriftX,
                    particleDriftY = particleDriftY,
                    particleCount = particleCount,
                )
            }
    }
}
