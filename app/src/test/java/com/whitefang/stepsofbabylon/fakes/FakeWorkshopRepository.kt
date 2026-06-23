package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory fake for [WorkshopRepository].
 *
 * Post-RO-02 the [purchaseUpgradeAtomic] method is the authoritative purchase path and must
 * emulate the Room `@Transaction` semantics — guarded deduct + workshop upsert applied as a
 * single logical step so callers can assert both partial-failure absence and
 * concurrent-purchase safety without a real database.
 *
 * @param linkedPlayer when supplied, the fake reads/writes the linked player's step balance
 *                     inside a [Mutex] to faithfully emulate the SQL `WHERE balance >= :cost`
 *                     guard. When null, [purchaseUpgradeAtomic] simply writes the workshop
 *                     level and returns true \u2014 this keeps existing tests that do not assert
 *                     on wallet state source-compatible without forcing every call site to
 *                     supply a player repo.
 */
class FakeWorkshopRepository(
    private val linkedPlayer: FakePlayerRepository? = null,
) : WorkshopRepository {
    val upgrades = MutableStateFlow<Map<UpgradeType, Int>>(emptyMap())

    /** Serialises concurrent [purchaseUpgradeAtomic] calls \u2014 mirrors the SQL-level atomicity. */
    private val atomicMutex = Mutex()

    /** Number of [purchaseUpgradeAtomic] calls \u2014 used by tests to assert the new path is live. */
    var purchaseUpgradeAtomicCallCount: Int = 0
        private set

    override fun observeAllUpgrades(): Flow<Map<UpgradeType, Int>> = upgrades

    override fun observeUpgradeLevel(type: UpgradeType): Flow<Int> = upgrades.map { it[type] ?: 0 }

    override fun observeUpgradesByCategory(category: UpgradeCategory): Flow<Map<UpgradeType, Int>> =
        upgrades.map { map -> map.filter { it.key.category == category } }

    override suspend fun setUpgradeLevel(
        type: UpgradeType,
        level: Int,
    ) {
        upgrades.update { it + (type to level) }
    }

    override suspend fun purchaseUpgradeAtomic(
        type: UpgradeType,
        newLevel: Int,
        cost: Long,
    ): Boolean =
        atomicMutex.withLock {
            purchaseUpgradeAtomicCallCount++
            val player = linkedPlayer
            if (player != null) {
                // Emulate the SQL-guarded deduct: read-check-write under the mutex so a second
                // concurrent call sees the deducted balance.
                val currentBalance = player.profile.value.stepBalance
                if (currentBalance < cost) return@withLock false
                player.profile.update { it.copy(stepBalance = it.stepBalance - cost) }
                upgrades.update { it + (type to newLevel) }
                true
            } else {
                // Unlinked fallback: acts as a purchase recorder for tests that do not care
                // about the wallet side. The use case's own wallet fast-fail still gates
                // obviously-broke callers before reaching this method.
                upgrades.update { it + (type to newLevel) }
                true
            }
        }

    override suspend fun ensureUpgradesExist() {}
}
