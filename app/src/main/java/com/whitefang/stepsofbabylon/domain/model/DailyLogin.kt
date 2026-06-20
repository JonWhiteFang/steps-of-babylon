package com.whitefang.stepsofbabylon.domain.model

/**
 * The domain view of a `daily_login` row (#227). Tracks the per-day login-reward state the
 * [com.whitefang.stepsofbabylon.domain.usecase.TrackDailyLogin] use case reads and writes.
 */
data class DailyLogin(
    val date: String,
    val stepsWalked: Long = 0,
    val powerStoneClaimed: Boolean = false,
    val gemsClaimed: Boolean = false,
)
