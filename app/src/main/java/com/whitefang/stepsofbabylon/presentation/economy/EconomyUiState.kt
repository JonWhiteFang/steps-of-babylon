package com.whitefang.stepsofbabylon.presentation.economy

data class EconomyUiState(
    val gems: Long = 0,
    val powerStones: Long = 0,
    val weeklySteps: Long = 0,
    val weeklyClaimedTier: Int = 0,
    val currentStreak: Int = 0,
    val todayPsClaimed: Boolean = false,
    val todayGemsClaimed: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** V1X-16: time remaining in current week (formatted, e.g. "3d 14h"). */
    val weeklyTimeRemaining: String = "",
    /** V1X-16: last 4 weeks history, newest first. Empty if no past data. */
    val weeklyHistory: List<WeeklyResult> = emptyList(),
)

/**
 * V1X-16: Result of a past weekly challenge for display in the dashboard history.
 *
 * @property weekStartDate ISO-8601 date of the Monday that started this week.
 * @property totalSteps Steps walked that week.
 * @property claimedTier Highest reward tier claimed (0 = none, 1/2/3 for the 50k/75k/100k thresholds).
 * @property powerStonesEarned Cumulative PS reward at the claimed tier (0/10/30/65 per the 3 thresholds).
 */
data class WeeklyResult(
    val weekStartDate: String,
    val totalSteps: Long,
    val claimedTier: Int,
    val powerStonesEarned: Int,
) {
    companion object {
        /** PS reward per claimed tier (matches existing CurrencyDashboardScreen ThresholdRow values). */
        fun powerStonesForTier(tier: Int): Int = when (tier) {
            1 -> 10
            2 -> 30  // 10 + 20
            3 -> 65  // 10 + 20 + 35
            else -> 0
        }
    }
}
