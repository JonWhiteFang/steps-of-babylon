package com.whitefang.stepsofbabylon.domain.model

/**
 * The domain view of a `weekly_challenge` row (#227). Tracks the per-week step total and the
 * highest tier already claimed, read/written by
 * [com.whitefang.stepsofbabylon.domain.usecase.TrackWeeklyChallenge].
 */
data class WeeklyChallenge(
    val weekStartDate: String,
    val totalSteps: Long = 0,
    val claimedTier: Int = 0,
)
