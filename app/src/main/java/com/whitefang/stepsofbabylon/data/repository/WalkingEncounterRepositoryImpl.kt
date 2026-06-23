package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.WalkingEncounterDao
import com.whitefang.stepsofbabylon.data.local.WalkingEncounterEntity
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.domain.repository.WalkingEncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WalkingEncounterRepositoryImpl
    @Inject
    constructor(
        private val dao: WalkingEncounterDao,
    ) : WalkingEncounterRepository {
        override fun observeUnclaimed(): Flow<List<SupplyDrop>> =
            dao.getUnclaimed().map { list -> list.map { it.toDomain() } }

        override fun observeHistory(limit: Int): Flow<List<SupplyDrop>> =
            dao.getHistory(limit).map { list -> list.map { it.toDomain() } }

        override fun countUnclaimed(): Flow<Int> = dao.countUnclaimed()

        override suspend fun getUnclaimedCount(): Int = dao.countUnclaimedOnce()

        override suspend fun createDrop(
            trigger: SupplyDropTrigger,
            reward: SupplyDropReward,
            rewardAmount: Int,
        ): Long =
            dao.insert(
                WalkingEncounterEntity(
                    triggerType = trigger.name,
                    rewardType = reward.name,
                    rewardAmount = rewardAmount,
                    createdAt = System.currentTimeMillis(),
                ),
            )

        override suspend fun claimDrop(id: Int): Boolean = dao.markClaimed(id, System.currentTimeMillis()) > 0

        override suspend fun enforceInboxCap(maxSize: Int) {
            while (dao.countUnclaimedOnce() >= maxSize) {
                dao.deleteOldestUnclaimed()
            }
        }

        private fun WalkingEncounterEntity.toDomain() =
            SupplyDrop(
                id = id,
                trigger = SupplyDropTrigger.valueOf(triggerType),
                reward = SupplyDropReward.valueOf(rewardType),
                rewardAmount = rewardAmount,
                claimed = claimed,
                createdAt = createdAt,
                claimedAt = claimedAt,
            )
    }
