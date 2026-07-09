package com.whitefang.stepsofbabylon.presentation.battle.biome

import com.whitefang.stepsofbabylon.domain.model.Biome
import com.whitefang.stepsofbabylon.domain.model.EnemyType

/**
 * #421 (#391 free-lane, C1): the single source of truth for the battle **art** palette.
 *
 * Procedural battle art (biome backgrounds, enemies, ziggurat) was previously coloured by ~50 anonymous
 * raw `0xFF…` ARGB ints scattered across [BiomeTheme], `EnemyEntity.BASE_COLORS`, and
 * `ZigguratEntity.DEFAULT_COLORS`, with no shared vocabulary. This object names them as intentful
 * constants so the whole art system derives from one place — the code anchor for
 * `docs/steering/style-bible.md`.
 *
 * ## Scope: ART colours only — NOT functional-feedback colours
 * UI-signal colours (HP-bar ratio thresholds, the armor cyan stroke, the ziggurat range-circle alphas,
 * the origin gold marker) are deliberately NOT here — they encode gameplay state, not art direction, and
 * stay inline where they are consumed. See the "functional palette" section of the style bible. The
 * `BattleArtPaletteTest` guard (#426) allowlists those literals.
 *
 * ## Invariant: no visual change on introduction (#421)
 * Every value below is byte-identical to the literal it replaced. [BiomeThemeTest] pins that
 * [BiomeTheme.forBiome] still produces the same palette for all five biomes.
 */
object BattlePalette {
    /** Per-biome art palette. Values mirror the pre-#421 [BiomeTheme] literals exactly. */
    data class BiomeColors(
        val skyColorTop: Int,
        val skyColorBottom: Int,
        val groundColor: Int,
        val zigguratColors: List<Int>,
        val enemyTint: Int,
        val particleColor: Int,
        val particleDriftX: Float,
        val particleDriftY: Float,
        val particleCount: Int,
    )

    fun forBiome(biome: Biome): BiomeColors =
        when (biome) {
            Biome.HANGING_GARDENS -> hangingGardens
            Biome.BURNING_SANDS -> burningSands
            Biome.FROZEN_ZIGGURATS -> frozenZiggurats
            Biome.UNDERWORLD_OF_KUR -> underworldOfKur
            Biome.CELESTIAL_GATE -> celestialGate
        }

    private val hangingGardens =
        BiomeColors(
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

    private val burningSands =
        BiomeColors(
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

    private val frozenZiggurats =
        BiomeColors(
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

    private val underworldOfKur =
        BiomeColors(
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

    private val celestialGate =
        BiomeColors(
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

    /**
     * Per-enemy base body colour, keyed by [EnemyType]. Values mirror the pre-#421
     * `EnemyEntity.BASE_COLORS` map exactly. Repointed by C2 (#422).
     */
    val enemyBaseColors: Map<EnemyType, Int> =
        mapOf(
            EnemyType.BASIC to 0xFFE53935.toInt(),
            EnemyType.FAST to 0xFFFF9800.toInt(),
            EnemyType.TANK to 0xFF8B0000.toInt(),
            EnemyType.RANGED to 0xFF9C27B0.toInt(),
            EnemyType.BOSS to 0xFF4A0000.toInt(),
            EnemyType.SCATTER to 0xFF4CAF50.toInt(),
        )

    /**
     * The five-stop ziggurat layer ramp (bronze → gold) used when a biome does not override it.
     * Mirrors `ZigguratEntity.DEFAULT_COLORS` exactly. Repointed by C3 (#423).
     */
    val zigguratDefaultLayers: List<Int> =
        listOf(
            0xFF8B7355.toInt(),
            0xFF9C8565.toInt(),
            0xFFC2B280.toInt(),
            0xFFCDBFA0.toInt(),
            0xFFD4A843.toInt(),
        )
}
