package com.whitefang.stepsofbabylon.presentation.home

import com.whitefang.stepsofbabylon.domain.model.Biome

data class HomeUiState(
    val todaySteps: Long = 0,
    val stepBalance: Long = 0,
    val gems: Long = 0,
    val powerStones: Long = 0,
    val currentTier: Int = 1,
    val highestUnlockedTier: Int = 1,
    val currentBiome: Biome = Biome.HANGING_GARDENS,
    val bestWave: Int = 0,
    val bestWavePerTier: Map<Int, Int> = emptyMap(),
    val unclaimedDropCount: Int = 0,
    val claimableMissionCount: Int = 0,
    val seasonPassActive: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)
