package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.WorkshopDao
import com.whitefang.stepsofbabylon.data.local.WorkshopUpgradeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake for [WorkshopDao].
 *
 * Introduced for the issue #20 fix to support repo-layer end-to-end tests on
 * [com.whitefang.stepsofbabylon.data.repository.WorkshopRepositoryImpl] without a real Room
 * DB. Behaviour mirrors the real DAO's observable-flow + suspend-write contract on the
 * `upgradeType: String` primary key. Tests against the additive-seeding path
 * ([com.whitefang.stepsofbabylon.data.repository.WorkshopRepositoryImpl.ensureUpgradesExist])
 * pre-seed [rows] with a subset of [com.whitefang.stepsofbabylon.domain.model.UpgradeType.entries]
 * to simulate an upgrade-from-older-AAB install state, then assert that `ensureUpgradesExist`
 * fills in the missing entries while preserving existing levels.
 *
 * The fake delegates the [purchaseUpgradeAtomic] default-method body to the real interface
 * implementation, which calls `playerDao.adjustStepBalanceIfSufficient(cost)` and then
 * [upsert]. Tests that exercise the atomic-purchase path must supply a working
 * [com.whitefang.stepsofbabylon.data.local.PlayerProfileDao] (e.g. via Robolectric + real
 * in-memory Room); tests that only exercise [ensureUpgradesExist] do not need the player DAO.
 */
class FakeWorkshopDao : WorkshopDao {
    val rows = MutableStateFlow<List<WorkshopUpgradeEntity>>(emptyList())

    override fun getAll(): Flow<List<WorkshopUpgradeEntity>> = rows

    override fun getByType(upgradeType: String): Flow<WorkshopUpgradeEntity?> =
        rows.map { list -> list.firstOrNull { it.upgradeType == upgradeType } }

    override fun getByCategory(types: List<String>): Flow<List<WorkshopUpgradeEntity>> =
        rows.map { list -> list.filter { it.upgradeType in types } }

    override suspend fun upsert(entity: WorkshopUpgradeEntity) {
        rows.update { list ->
            val idx = list.indexOfFirst { it.upgradeType == entity.upgradeType }
            if (idx >= 0) list.toMutableList().also { it[idx] = entity } else list + entity
        }
    }

    override suspend fun upsertAll(entities: List<WorkshopUpgradeEntity>) {
        entities.forEach { upsert(it) }
    }
}
