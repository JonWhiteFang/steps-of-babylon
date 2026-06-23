package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.WeeklyChallengeDao
import com.whitefang.stepsofbabylon.data.local.WeeklyChallengeEntity
import com.whitefang.stepsofbabylon.domain.model.WeeklyChallenge
import com.whitefang.stepsofbabylon.domain.repository.WeeklyChallengeRepository
import javax.inject.Inject

class WeeklyChallengeRepositoryImpl
    @Inject
    constructor(
        private val dao: WeeklyChallengeDao,
    ) : WeeklyChallengeRepository {
        override suspend fun getByWeek(weekStart: String): WeeklyChallenge? = dao.getByWeek(weekStart)?.toDomain()

        override suspend fun upsert(challenge: WeeklyChallenge) = dao.upsert(challenge.toEntity())

        override suspend fun getLastNWeeks(limit: Int): List<WeeklyChallenge> =
            dao.getLastNWeeks(limit).map { it.toDomain() }

        private fun WeeklyChallengeEntity.toDomain() =
            WeeklyChallenge(weekStartDate = weekStartDate, totalSteps = totalSteps, claimedTier = claimedTier)

        private fun WeeklyChallenge.toEntity() =
            WeeklyChallengeEntity(weekStartDate = weekStartDate, totalSteps = totalSteps, claimedTier = claimedTier)
    }
