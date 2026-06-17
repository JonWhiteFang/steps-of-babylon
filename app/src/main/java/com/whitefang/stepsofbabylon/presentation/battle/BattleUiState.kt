package com.whitefang.stepsofbabylon.presentation.battle

import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.battle.ui.BiomeTransitionInfo

data class RoundEndState(
    val waveReached: Int,
    val enemiesKilled: Int,
    val totalCashEarned: Long,
    val timeSurvivedSeconds: Float,
    val isNewBestWave: Boolean,
    val previousBest: Int,
    val tierUnlocked: Int? = null,
    val powerStonesAwarded: Int = 0,
    val stepsEarned: Long = 0,
    val adRemoved: Boolean = false,
    val gemAdWatched: Boolean = false,
    val psAdWatched: Boolean = false,
)

data class UWSlotInfo(
    val typeName: String,
    val cooldownRemaining: Float = 0f,
    val cooldownTotal: Float = 0f,
    val isReady: Boolean = true,
)

data class BattleUiState(
    val currentWave: Int = 1,
    val currentHp: Double = 0.0,
    val maxHp: Double = 0.0,
    val cash: Long = 0,
    val enemyCount: Int = 0,
    val wavePhase: String = "",
    val waveProgress: Float = 0f,
    val speedMultiplier: Float = 1f,
    val isPaused: Boolean = false,
    val isLoading: Boolean = true,
    val showUpgradeMenu: Boolean = false,
    val inRoundLevels: Map<UpgradeType, Int> = emptyMap(),
    val lastPurchaseFree: Boolean = false,
    val roundEndState: RoundEndState? = null,
    val biomeTransition: BiomeTransitionInfo? = null,
    val stepBalance: Long = 0,
    val stepsEarnedThisRound: Long = 0,
    val uwSlots: List<UWSlotInfo> = emptyList(),
    val adRemoved: Boolean = false,
    /**
     * Snackbar message surfaced by ad-related actions (`watchGemAd`, `watchPsAd`).
     * Set on `AdResult.Cancelled` and `AdResult.Error`; cleared by
     * [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel.clearMessage]
     * after the snackbar shows. Mirrors the `userMessage` pattern used by
     * `MissionsViewModel` / `CardsViewModel` / `WorkshopViewModel` / `LabsViewModel`.
     */
    val userMessage: String? = null,
    /**
     * #190 REL-2: set when the game-loop thread caught an exception and stopped. Drives a
     * non-dismissable "Battle error" overlay and suppresses all interactive round chrome.
     */
    val battleError: Boolean = false,
)
