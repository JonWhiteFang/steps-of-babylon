package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.MilestoneDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MilestoneRepositoryImpl
    @Inject
    constructor(
        private val dao: MilestoneDao,
        // #227: handed into the DAO @Transaction so the wallet credit stays inside the one DB-scoped
        // transaction (#122/ADR-0027). Same AppDatabase instance as `dao` (DatabaseModule).
        private val playerProfileDao: PlayerProfileDao,
    ) : MilestoneRepository {
        override suspend fun getClaimedMilestoneIds(): List<String> =
            dao.getAllOnce().filter { it.claimed }.map { it.milestoneId }

        override fun observeClaimedMilestoneIds(): Flow<Set<String>> =
            dao.getAll().map { list -> list.filter { it.claimed }.map { it.milestoneId }.toSet() }

        override suspend fun claimMilestoneAtomic(
            milestoneId: String,
            gems: Long,
            powerStones: Long,
            claimedAt: Long,
        ): Boolean = dao.claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt, playerDao = playerProfileDao)
    }
