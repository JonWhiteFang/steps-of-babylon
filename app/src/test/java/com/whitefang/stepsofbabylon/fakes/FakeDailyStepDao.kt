package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.DailyStepRecordEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * In-memory fake for [DailyStepDao].
 *
 * Post-B.2 PR 2 the [creditBattleStepsAtomic] method is the authoritative battle-step credit
 * path and must emulate the Room `@Transaction` contract \u2014 cap check + counter increment +
 * wallet credit applied as a single logical step so callers can assert partial-failure
 * absence and concurrent-kill safety without a real database.
 *
 * @param linkedPlayer when supplied, the fake forwards wallet credits to this player fake's
 *                     `stepBalance`, under a [Mutex] that mirrors the SQLite-level atomicity.
 *                     Callers can pass `mock<PlayerProfileDao>()` as the `playerDao` argument
 *                     to [creditBattleStepsAtomic] \u2014 the fake ignores it and uses
 *                     [linkedPlayer] instead (the real DAO path is exercised by Room's
 *                     generated impl at runtime). When null, wallet updates are no-ops.
 */
class FakeDailyStepDao(
    private val linkedPlayer: FakePlayerRepository? = null,
) : DailyStepDao {
    val data = MutableStateFlow<Map<String, DailyStepRecordEntity>>(emptyMap())

    /** Serialises concurrent [creditBattleStepsAtomic] calls \u2014 mirrors SQL-level atomicity. */
    private val atomicMutex = Mutex()

    /** Number of [creditBattleStepsAtomic] calls \u2014 used by tests to assert the atomic path is live. */
    var creditBattleStepsAtomicCallCount: Int = 0
        private set

    override fun getByDate(date: String): Flow<DailyStepRecordEntity?> = data.map { it[date] }

    override fun getRange(startDate: String, endDate: String): Flow<List<DailyStepRecordEntity>> =
        data.map { map -> map.values.filter { it.date in startDate..endDate }.sortedBy { it.date } }

    override suspend fun upsert(entity: DailyStepRecordEntity) {
        data.value = data.value + (entity.date to entity)
    }

    override suspend fun getByDateOnce(date: String): DailyStepRecordEntity? = data.value[date]

    override suspend fun clearEscrow(date: String) {
        val e = data.value[date] ?: return
        data.value = data.value + (date to e.copy(escrowSteps = 0, escrowSyncCount = 0))
    }

    override suspend fun sumCreditedSteps(startDate: String, endDate: String): Long =
        data.value.values.filter { it.date in startDate..endDate }.sumOf { it.creditedSteps }

    // #121: column-targeted upserts — each touches ONLY its own columns, mirroring the real
    // SQL's `ON CONFLICT(date) DO UPDATE SET <those columns>`. The fake copies the existing
    // row (or starts from a fresh default row) and overwrites only this writer's fields, so
    // a disjoint-column writer cannot clobber another's data through the fake either.
    override suspend fun setSensorAndCreditedSteps(date: String, sensorSteps: Long, creditedSteps: Long) {
        val base = data.value[date] ?: DailyStepRecordEntity(date = date)
        data.value = data.value + (date to base.copy(sensorSteps = sensorSteps, creditedSteps = creditedSteps))
    }

    override suspend fun setHealthConnectSteps(date: String, healthConnectSteps: Long) {
        val base = data.value[date] ?: DailyStepRecordEntity(date = date)
        data.value = data.value + (date to base.copy(healthConnectSteps = healthConnectSteps))
    }

    override suspend fun setActivityMinutes(date: String, activityMinutes: Map<String, Int>, stepEquivalents: Long) {
        val base = data.value[date] ?: DailyStepRecordEntity(date = date)
        data.value = data.value + (date to base.copy(activityMinutes = activityMinutes, stepEquivalents = stepEquivalents))
    }

    override suspend fun setEscrow(date: String, escrowSteps: Long, escrowSyncCount: Int) {
        val base = data.value[date] ?: DailyStepRecordEntity(date = date)
        data.value = data.value + (date to base.copy(escrowSteps = escrowSteps, escrowSyncCount = escrowSyncCount))
    }

    override suspend fun getBattleStepsEarned(date: String): Long =
        data.value[date]?.battleStepsEarned ?: 0L

    override suspend fun incrementBattleSteps(date: String, delta: Long) {
        val existing = data.value[date]
        val updated = existing?.copy(battleStepsEarned = existing.battleStepsEarned + delta)
            ?: DailyStepRecordEntity(date = date, battleStepsEarned = delta)
        data.value = data.value + (date to updated)
    }

    override suspend fun creditBattleStepsAtomic(
        date: String,
        requested: Long,
        dailyCap: Long,
        playerDao: PlayerProfileDao,
    ): Long = atomicMutex.withLock {
        creditBattleStepsAtomicCallCount++
        if (requested <= 0L) return@withLock 0L
        val alreadyEarned = getBattleStepsEarned(date)
        val remaining = (dailyCap - alreadyEarned).coerceAtLeast(0L)
        if (remaining <= 0L) return@withLock 0L
        val credited = min(requested, remaining)
        incrementBattleSteps(date, credited)
        linkedPlayer?.profile?.update { it.copy(stepBalance = it.stepBalance + credited) }
        credited
    }

    override suspend fun getBossPsEarnedToday(date: String): Long =
        data.value[date]?.bossPsEarnedToday ?: 0L

    override suspend fun incrementBossPs(date: String, delta: Long) {
        val existing = data.value[date]
        val updated = existing?.copy(bossPsEarnedToday = existing.bossPsEarnedToday + delta)
            ?: DailyStepRecordEntity(date = date, bossPsEarnedToday = delta)
        data.value = data.value + (date to updated)
    }

    /** Number of [creditBossPowerStonesAtomic] calls. */
    var creditBossPsAtomicCallCount: Int = 0
        private set

    override suspend fun creditBossPowerStonesAtomic(
        date: String,
        requested: Long,
        dailyCap: Long,
        playerDao: PlayerProfileDao,
    ): Long = atomicMutex.withLock {
        creditBossPsAtomicCallCount++
        if (requested <= 0L) return@withLock 0L
        val alreadyEarned = getBossPsEarnedToday(date)
        val remaining = (dailyCap - alreadyEarned).coerceAtLeast(0L)
        if (remaining <= 0L) return@withLock 0L
        val credited = min(requested, remaining)
        incrementBossPs(date, credited)
        linkedPlayer?.profile?.update { it.copy(powerStones = it.powerStones + credited) }
        credited
    }
}
