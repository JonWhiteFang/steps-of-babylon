package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.CosmeticDao
import com.whitefang.stepsofbabylon.data.local.CosmeticEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake for [CosmeticDao].
 *
 * Introduced in C.2 PR 2 to support repo-layer end-to-end tests on
 * [com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImpl] without a real Room
 * DB. Behaviour mirrors the real DAO's observable-flow + suspend-write contract; the
 * auto-generated primary key is simulated by a monotonically increasing counter so
 * [upsertAll] calls from `ensureSeedData` land distinct rows the way Room does in prod.
 *
 * The fake intentionally does NOT enforce a uniqueness constraint on [CosmeticEntity.cosmeticId];
 * callers that need to test the all-or-nothing `ensureSeedData` gate can inspect [count] /
 * [rows] directly. Tests that want idempotency can drive it via the repo's own guard.
 */
class FakeCosmeticDao : CosmeticDao {
    val rows = MutableStateFlow<List<CosmeticEntity>>(emptyList())
    private var nextId: Int = 1

    override fun observeAll(): Flow<List<CosmeticEntity>> = rows

    override fun observeOwned(): Flow<List<CosmeticEntity>> = rows.map { list -> list.filter { it.isOwned } }

    override fun observeEquipped(): Flow<List<CosmeticEntity>> = rows.map { list -> list.filter { it.isEquipped } }

    override suspend fun count(): Int = rows.value.size

    override suspend fun upsert(entity: CosmeticEntity) {
        rows.update { list ->
            val idx = list.indexOfFirst { it.cosmeticId == entity.cosmeticId }
            if (idx >= 0) {
                list.toMutableList().also { it[idx] = entity.copy(id = list[idx].id) }
            } else {
                list + entity.copy(id = nextId++)
            }
        }
    }

    override suspend fun upsertAll(entities: List<CosmeticEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun markOwned(cosmeticId: String) {
        rows.update { list ->
            list.map { if (it.cosmeticId == cosmeticId) it.copy(isOwned = true) else it }
        }
    }

    override suspend fun equip(cosmeticId: String) {
        rows.update { list ->
            list.map { if (it.cosmeticId == cosmeticId) it.copy(isEquipped = true) else it }
        }
    }

    override suspend fun unequip(cosmeticId: String) {
        rows.update { list ->
            list.map { if (it.cosmeticId == cosmeticId) it.copy(isEquipped = false) else it }
        }
    }

    override suspend fun unequipCategory(category: String) {
        rows.update { list ->
            list.map { if (it.category == category) it.copy(isEquipped = false) else it }
        }
    }

    override suspend fun deleteByIds(ids: List<String>) {
        rows.update { list -> list.filterNot { it.cosmeticId in ids } }
    }
}
