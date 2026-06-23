package com.whitefang.stepsofbabylon.domain.model

data class PlayerProfile(
    val id: Int = 1,
    val totalStepsEarned: Long = 0,
    val stepBalance: Long = 0,
    val gems: Long = 0,
    val powerStones: Long = 0,
    val cardDust: Long = 0,
    val currentTier: Int = 1,
    val highestUnlockedTier: Int = 1,
    val labSlotCount: Int = 1,
    val bestWavePerTier: Map<Int, Int> = emptyMap(),
    val currentStreak: Int = 0,
    val lastLoginDate: String = "",
    val totalGemsEarned: Long = 0,
    val totalGemsSpent: Long = 0,
    val totalPowerStonesEarned: Long = 0,
    val totalPowerStonesSpent: Long = 0,
    val totalRoundsPlayed: Long = 0,
    val totalEnemiesKilled: Long = 0,
    val totalCashEarned: Long = 0,
    val adRemoved: Boolean = false,
    val seasonPassActive: Boolean = false,
    val seasonPassExpiry: Long = 0,
    val freeLabRushUsedToday: String = "",
    val freeCardPackAdUsedToday: String = "",
    val createdAt: Long = 0,
    val lastActiveAt: Long = 0,
) {
    fun toWallet(): PlayerWallet =
        PlayerWallet(
            stepBalance = stepBalance,
            gems = gems,
            powerStones = powerStones,
        )
}
