package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.MilestoneDao
import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory fake for [MilestoneDao].
 *
 * Post-B.2 PR 4 the [claimMilestoneAtomic] method is the authoritative claim path and must
 * emulate the Room `@Transaction` contract — mark-claimed + wallet credits applied as a
 * single logical step so callers can assert partial-failure absence and double-claim-race
 * safety without a real database.
 *
 * @param linkedPlayer when supplied, the fake forwards reward credits to this player fake's
 *                     profile (both wallet balance and lifetime totalX earned counters),
 *                     under a [Mutex] that mirrors the SQLite-level atomicity. Callers can
 *                     pass `mock<PlayerProfileDao>()` as the `playerDao` argument to
 *                     [claimMilestoneAtomic] — the fake ignores it and routes the credit
 *                     side through [linkedPlayer] instead (the real DAO path is exercised
 *                     by Room's generated impl at runtime). When null, wallet updates are
 *                     no-ops and only the milestone row is updated.
 */
class FakeMilestoneDao(
    private val linkedPlayer: FakePlayerRepository? = null,
) : MilestoneDao {
    private val data = MutableStateFlow<Map<String, MilestoneEntity>>(emptyMap())

    /** Serialises concurrent [claimMilestoneAtomic] calls — mirrors SQL-level atomicity. */
    private val atomicMutex = Mutex()

    /** Number of [claimMilestoneAtomic] calls — used by tests to assert the atomic path is live. */
    var claimMilestoneAtomicCallCount: Int = 0
        private set

    override fun getAll(): Flow<List<MilestoneEntity>> = data.map { it.values.toList() }

    override suspend fun getAllOnce(): List<MilestoneEntity> = data.value.values.toList()

    override suspend fun getByIdOnce(id: String): MilestoneEntity? = data.value[id]

    override suspend fun upsert(entity: MilestoneEntity) {
        data.value = data.value + (entity.milestoneId to entity)
    }

    override suspend fun claimMilestoneAtomic(
        milestoneId: String,
        gems: Long,
        powerStones: Long,
        claimedAt: Long,
        playerDao: PlayerProfileDao,
    ): Boolean =
        atomicMutex.withLock {
            claimMilestoneAtomicCallCount++
            val existing = data.value[milestoneId]
            if (existing?.claimed == true) return@withLock false
            // Under-the-mutex read-modify-write faithfully emulates the SQL atomic guard.
            data.value = data.value + (
                milestoneId to
                    MilestoneEntity(
                        milestoneId = milestoneId,
                        claimed = true,
                        claimedAt = claimedAt,
                    )
            )
            // Test-level decoy: the real impl calls playerDao.adjustGems / incrementGemsEarned
            // (and the PowerStones equivalents). The fake routes credits to linkedPlayer so tests
            // can keep asserting on the existing FakePlayerRepository flow. Callers pass
            // mock<PlayerProfileDao>() for type satisfaction.
            if (gems > 0L) {
                linkedPlayer?.profile?.update {
                    it.copy(
                        gems = it.gems + gems,
                        totalGemsEarned = it.totalGemsEarned + gems,
                    )
                }
            }
            if (powerStones > 0L) {
                linkedPlayer?.profile?.update {
                    it.copy(
                        powerStones = it.powerStones + powerStones,
                        totalPowerStonesEarned = it.totalPowerStonesEarned + powerStones,
                    )
                }
            }
            true
        }
}
