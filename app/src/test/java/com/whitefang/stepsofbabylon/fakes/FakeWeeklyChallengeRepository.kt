package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.WeeklyChallenge
import com.whitefang.stepsofbabylon.domain.repository.WeeklyChallengeRepository

/** In-memory fake for [WeeklyChallengeRepository] (#227). */
class FakeWeeklyChallengeRepository : WeeklyChallengeRepository {
    private val data = mutableMapOf<String, WeeklyChallenge>()

    override suspend fun getByWeek(weekStart: String): WeeklyChallenge? = data[weekStart]

    override suspend fun upsert(challenge: WeeklyChallenge) { data[challenge.weekStartDate] = challenge }
}
