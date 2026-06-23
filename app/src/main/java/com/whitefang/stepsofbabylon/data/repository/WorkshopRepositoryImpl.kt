package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.local.WorkshopDao
import com.whitefang.stepsofbabylon.data.local.WorkshopUpgradeEntity
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkshopRepositoryImpl
    @Inject
    constructor(
        private val dao: WorkshopDao,
        private val playerProfileDao: PlayerProfileDao,
    ) : WorkshopRepository {
        override fun observeAllUpgrades(): Flow<Map<UpgradeType, Int>> =
            dao.getAll().map { list ->
                list.associate { UpgradeType.valueOf(it.upgradeType) to it.level }
            }

        override fun observeUpgradeLevel(type: UpgradeType): Flow<Int> = dao.getByType(type.name).map { it?.level ?: 0 }

        override fun observeUpgradesByCategory(category: UpgradeCategory): Flow<Map<UpgradeType, Int>> =
            dao
                .getByCategory(UpgradeType.entries.filter { it.category == category }.map { it.name })
                .map { list -> list.associate { UpgradeType.valueOf(it.upgradeType) to it.level } }

        override suspend fun setUpgradeLevel(
            type: UpgradeType,
            level: Int,
        ) = dao.upsert(WorkshopUpgradeEntity(upgradeType = type.name, level = level))

        override suspend fun purchaseUpgradeAtomic(
            type: UpgradeType,
            newLevel: Int,
            cost: Long,
        ): Boolean =
            dao.purchaseUpgradeAtomic(
                type = type.name,
                newLevel = newLevel,
                cost = cost,
                playerDao = playerProfileDao,
            )

        /**
         * Seeds rows for any [UpgradeType] enum entries that do not yet have a row in
         * `workshop_upgrade`. Per-enum filter — not a global "seed if completely empty" gate.
         *
         * Pre-fix (issue #20): the previous implementation only seeded when the table was
         * fully empty (`if (dao.getAll().first().isEmpty())`). Players upgrading from an
         * older AAB that pre-dated a new content addition (e.g. v8 → v9 added
         * [UpgradeType.RAPID_FIRE] in R4-03) had a non-empty table, so the early-return
         * branch fired and the new enum's row was never inserted. Result: the upgrade
         * appeared nowhere in the Workshop UI for upgrade-from-v8 testers.
         *
         * Post-fix: same pattern as [com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImpl.ensureSeedData].
         * Existing rows' levels (and any other state) are preserved — only missing enum
         * entries get a new default-level row. Cheap in steady state (one DAO read on app
         * launch via [com.whitefang.stepsofbabylon.presentation.home.HomeViewModel.init]).
         */
        override suspend fun ensureUpgradesExist() {
            val existing: Set<String> = dao.getAll().first().mapTo(mutableSetOf()) { it.upgradeType }
            val missing = UpgradeType.entries.filter { it.name !in existing }
            if (missing.isNotEmpty()) {
                dao.upsertAll(missing.map { WorkshopUpgradeEntity(upgradeType = it.name) })
            }
        }
    }
