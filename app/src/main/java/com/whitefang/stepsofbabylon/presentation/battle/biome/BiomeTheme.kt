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
        fun forBiome(biome: Biome): BiomeTheme =
            when (biome) {
                Biome.HANGING_GARDENS -> {
                    BiomeTheme(
                        skyColorTop = 0xFF2E5D3A.toInt(),
                        skyColorBottom = 0xFF4A7C59.toInt(),
                        groundColor = 0xFF3B5E2B.toInt(),
                        zigguratColors =
                            listOf(
                                0xFF8B7355.toInt(),
                                0xFF9C8565.toInt(),
                                0xFFA09060.toInt(),
                                0xFF7BA05B.toInt(),
                                0xFFD4A843.toInt(),
                            ),
                        enemyTint = 0xFF6B8E4E.toInt(),
                        particleColor = 0x9966BB6A.toInt(),
                        particleDriftX = 8f,
                        particleDriftY = 30f,
                        particleCount = 35,
                    )
                }

                Biome.BURNING_SANDS -> {
                    BiomeTheme(
                        skyColorTop = 0xFFB85C1E.toInt(),
                        skyColorBottom = 0xFFD4943A.toInt(),
                        groundColor = 0xFFC2A060.toInt(),
                        zigguratColors =
                            listOf(
                                0xFF8B4513.toInt(),
                                0xFFA0522D.toInt(),
                                0xFFB8742C.toInt(),
                                0xFFCD853F.toInt(),
                                0xFFFF8C00.toInt(),
                            ),
                        enemyTint = 0xFFD4843A.toInt(),
                        particleColor = 0x66DEB887,
                        particleDriftX = 40f,
                        particleDriftY = 5f,
                        particleCount = 40,
                    )
                }

                Biome.FROZEN_ZIGGURATS -> {
                    BiomeTheme(
                        skyColorTop = 0xFF1A3A5C.toInt(),
                        skyColorBottom = 0xFF4682B4.toInt(),
                        groundColor = 0xFFB0C4DE.toInt(),
                        zigguratColors =
                            listOf(
                                0xFF4682B4.toInt(),
                                0xFF6CA6CD.toInt(),
                                0xFF87CEEB.toInt(),
                                0xFFB0E0E6.toInt(),
                                0xFFE0F0FF.toInt(),
                            ),
                        enemyTint = 0xFF7EB8DA.toInt(),
                        particleColor = 0xBBFFFFFF.toInt(),
                        particleDriftX = 3f,
                        particleDriftY = 15f,
                        particleCount = 50,
                    )
                }

                Biome.UNDERWORLD_OF_KUR -> {
                    BiomeTheme(
                        skyColorTop = 0xFF1A0A2E.toInt(),
                        skyColorBottom = 0xFF2D1B4E.toInt(),
                        groundColor = 0xFF1A1A2E.toInt(),
                        zigguratColors =
                            listOf(
                                0xFF2D1B4E.toInt(),
                                0xFF3D2B5E.toInt(),
                                0xFF4A3570.toInt(),
                                0xFF6A4C93.toInt(),
                                0xFF9B59B6.toInt(),
                            ),
                        enemyTint = 0xFF8E44AD.toInt(),
                        particleColor = 0x99FF6B35.toInt(),
                        particleDriftX = 2f,
                        particleDriftY = -25f,
                        particleCount = 30,
                    )
                }

                Biome.CELESTIAL_GATE -> {
                    BiomeTheme(
                        skyColorTop = 0xFF0A0A2A.toInt(),
                        skyColorBottom = 0xFF1A1A4A.toInt(),
                        groundColor = 0xFF15153A.toInt(),
                        zigguratColors =
                            listOf(
                                0xFF4169E1.toInt(),
                                0xFF6A5ACD.toInt(),
                                0xFF9370DB.toInt(),
                                0xFFBA55D3.toInt(),
                                0xFFFFD700.toInt(),
                            ),
                        enemyTint = 0xFFDA70D6.toInt(),
                        particleColor = 0xCCFFFFFF.toInt(),
                        particleDriftX = 5f,
                        particleDriftY = -3f,
                        particleCount = 45,
                    )
                }
            }
    }
}
