package com.whitefang.stepsofbabylon.presentation.battle

import com.whitefang.stepsofbabylon.domain.model.OverdriveType
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
    val speedMultiplier: Float = 1f,
    val isPaused: Boolean = false,
    val isLoading: Boolean = true,
    val showUpgradeMenu: Boolean = false,
    val inRoundLevels: Map<UpgradeType, Int> = emptyMap(),
    val lastPurchaseFree: Boolean = false,
    val roundEndState: RoundEndState? = null,
    val biomeTransition: BiomeTransitionInfo? = null,
    val overdriveUsed: Boolean = false,
    val activeOverdriveType: OverdriveType? = null,
    val overdriveTimeRemaining: Float = 0f,
    val stepBalance: Long = 0,
    val stepsEarnedThisRound: Long = 0,
    val showOverdriveMenu: Boolean = false,
    val uwSlots: List<UWSlotInfo> = emptyList(),
    val adRemoved: Boolean = false,
)
