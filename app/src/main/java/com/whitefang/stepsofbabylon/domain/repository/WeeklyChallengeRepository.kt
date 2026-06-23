package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.WeeklyChallenge

/** Domain port over the `weekly_challenge` persistence (#227). */
interface WeeklyChallengeRepository {
    suspend fun getByWeek(weekStart: String): WeeklyChallenge?

    suspend fun upsert(challenge: WeeklyChallenge)

    /** Most-recent [limit] weeks, newest first (#219 — Economy dashboard history; was a direct DAO read). */
    suspend fun getLastNWeeks(limit: Int): List<WeeklyChallenge>
}
