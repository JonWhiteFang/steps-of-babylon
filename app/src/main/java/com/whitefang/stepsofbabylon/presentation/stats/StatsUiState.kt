package com.whitefang.stepsofbabylon.presentation.stats

enum class StatsPeriod(
    val label: String,
) {
    WEEK("7 Days"),
    MONTH("30 Days"),
    QUARTER("12 Weeks"),
}

data class DailyBarData(
    val label: String,
    val sensorSteps: Long,
    val stepEquivalents: Long,
) {
    val total: Long get() = sensorSteps + stepEquivalents
}

data class StatsUiState(
    val todaySteps: Long = 0,
    val todayStepEquivalents: Long = 0,
    val todayActivityMinutes: Map<String, Int> = emptyMap(),
    val allTimeSteps: Long = 0,
    val bars: List<DailyBarData> = emptyList(),
    val selectedPeriod: StatsPeriod = StatsPeriod.WEEK,
    val bestWavePerTier: Map<Int, Int> = emptyMap(),
    val totalRoundsPlayed: Long = 0,
    val totalEnemiesKilled: Long = 0,
    val totalCashEarned: Long = 0,
    val totalGemsEarned: Long = 0,
    val totalGemsSpent: Long = 0,
    val totalPowerStonesEarned: Long = 0,
    val totalPowerStonesSpent: Long = 0,
    val currentGems: Long = 0,
    val currentPowerStones: Long = 0,
    val totalWorkshopLevels: Int = 0,
    val daysActive: Int = 0,
    val averageDailySteps: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)
