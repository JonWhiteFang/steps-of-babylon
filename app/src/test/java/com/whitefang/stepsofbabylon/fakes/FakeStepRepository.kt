package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * In-memory fake for [StepRepository].
 *
 * #227: the battle/boss atomic-credit methods moved from `DailyStepDao` behind this port, so this
 * fake now carries the cap-tracking state + `linkedPlayer` wallet routing + call-counts that
 * `FakeDailyStepDao` used to (the migration template). The atomic methods emulate the Room
 * `@Transaction` contract (cap check + counter increment + wallet credit as one logical step under
 * a [Mutex]) so callers can assert partial-failure absence and concurrent-kill safety without a DB.
 *
 * @param linkedPlayer when supplied, atomic credits forward to this player fake's wallet; null = no-op.
 */
class FakeStepRepository(
    private val linkedPlayer: FakePlayerRepository? = null,
) : StepRepository {
    val records = MutableStateFlow<Map<String, DailyStepSummary>>(emptyMap())

    /** Per-day battle-step / boss-PS counters (test-visible — mirror the DAO columns). */
    private val battleStepsEarned = mutableMapOf<String, Long>()
    private val bossPsEarnedToday = mutableMapOf<String, Long>()
    private val atomicMutex = Mutex()

    var creditBattleStepsAtomicCallCount: Int = 0
        private set
    var creditBossPsAtomicCallCount: Int = 0
        private set

    /** Test accessors / seeders mirroring the former FakeDailyStepDao surface. */
    fun getBattleStepsEarned(date: String): Long = battleStepsEarned[date] ?: 0L

    fun getBossPsEarnedToday(date: String): Long = bossPsEarnedToday[date] ?: 0L

    fun seedBattleStepsEarned(
        date: String,
        value: Long,
    ) {
        battleStepsEarned[date] = value
    }

    fun seedBossPsEarned(
        date: String,
        value: Long,
    ) {
        bossPsEarnedToday[date] = value
    }

    override fun observeTodayRecord(date: String): Flow<DailyStepSummary?> = records.map { it[date] }

    override fun observeHistory(
        startDate: String,
        endDate: String,
    ): Flow<List<DailyStepSummary>> =
        records.map { map -> map.values.filter { it.date in startDate..endDate }.sortedBy { it.date } }

    override suspend fun getDailyRecord(date: String): DailyStepSummary? = records.value[date]

    override suspend fun updateDailySteps(
        date: String,
        sensorSteps: Long,
        creditedSteps: Long,
    ) {
        val existing = records.value[date] ?: DailyStepSummary(date)
        records.value =
            records.value + (date to existing.copy(sensorSteps = sensorSteps, creditedSteps = creditedSteps))
    }

    override suspend fun updateHealthConnectSteps(
        date: String,
        healthConnectSteps: Long,
    ) {
        val existing = records.value[date] ?: DailyStepSummary(date)
        records.value = records.value + (date to existing.copy(healthConnectSteps = healthConnectSteps))
    }

    override suspend fun updateActivityMinutes(
        date: String,
        activityMinutes: Map<String, Int>,
        stepEquivalents: Long,
    ) {
        val existing = records.value[date] ?: DailyStepSummary(date)
        records.value =
            records.value +
            (date to existing.copy(activityMinutes = activityMinutes, stepEquivalents = stepEquivalents))
    }

    override suspend fun updateEscrow(
        date: String,
        escrowSteps: Long,
        syncCount: Int,
    ) {
        val existing = records.value[date] ?: DailyStepSummary(date)
        records.value = records.value + (date to existing.copy(escrowSteps = escrowSteps, escrowSyncCount = syncCount))
    }

    override suspend fun releaseEscrow(date: String) {
        val existing = records.value[date] ?: return
        records.value = records.value + (
            date to
                existing.copy(
                    creditedSteps = existing.creditedSteps + existing.escrowSteps,
                    escrowSteps = 0,
                    escrowSyncCount = 0,
                )
        )
    }

    override suspend fun discardEscrow(date: String) {
        val existing = records.value[date] ?: return
        records.value = records.value + (date to existing.copy(escrowSteps = 0, escrowSyncCount = 0))
    }

    override suspend fun sumCreditedSteps(
        startDate: String,
        endDate: String,
    ): Long =
        records.value.values
            .filter { it.date in startDate..endDate }
            .sumOf { it.creditedSteps }

    override suspend fun creditBattleStepsAtomic(
        date: String,
        requested: Long,
        dailyCap: Long,
    ): Long =
        atomicMutex.withLock {
            creditBattleStepsAtomicCallCount++
            if (requested <= 0L) return@withLock 0L
            val remaining = (dailyCap - getBattleStepsEarned(date)).coerceAtLeast(0L)
            if (remaining <= 0L) return@withLock 0L
            val credited = min(requested, remaining)
            battleStepsEarned[date] = getBattleStepsEarned(date) + credited
            linkedPlayer?.profile?.update { it.copy(stepBalance = it.stepBalance + credited) }
            credited
        }

    override suspend fun creditBossPowerStonesAtomic(
        date: String,
        requested: Long,
        dailyCap: Long,
    ): Long =
        atomicMutex.withLock {
            creditBossPsAtomicCallCount++
            if (requested <= 0L) return@withLock 0L
            val remaining = (dailyCap - getBossPsEarnedToday(date)).coerceAtLeast(0L)
            if (remaining <= 0L) return@withLock 0L
            val credited = min(requested, remaining)
            bossPsEarnedToday[date] = getBossPsEarnedToday(date) + credited
            linkedPlayer?.profile?.update { it.copy(powerStones = it.powerStones + credited) }
            credited
        }
}
