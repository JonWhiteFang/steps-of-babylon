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
) {
    /**
     * #224: gates the Home first-walk teaching prompt. Shown only at the genuine core-loop kickoff
     * moment — the player has walked nothing today AND has essentially no Steps to spend — so a
     * returning player with a stockpile (who just hasn't walked *today* yet) isn't nagged. Suppressed
     * while loading or in an error state (those own the screen). Pure + derived so it stays unit-tested
     * (see `HomeFirstWalkPromptTest`) without a Compose harness.
     */
    val showFirstWalkPrompt: Boolean
        get() = !isLoading && error == null && todaySteps == 0L && stepBalance < FIRST_WALK_BALANCE_THRESHOLD

    companion object {
        /** A balance at/above this means the player has progressed past the first-walk moment. */
        const val FIRST_WALK_BALANCE_THRESHOLD = 100L
    }
}
