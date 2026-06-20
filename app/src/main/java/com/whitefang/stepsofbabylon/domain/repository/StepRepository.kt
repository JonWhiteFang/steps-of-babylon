package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import kotlinx.coroutines.flow.Flow

interface StepRepository {
    fun observeTodayRecord(date: String): Flow<DailyStepSummary?>
    fun observeHistory(startDate: String, endDate: String): Flow<List<DailyStepSummary>>
    suspend fun updateDailySteps(date: String, sensorSteps: Long, creditedSteps: Long)
    suspend fun getDailyRecord(date: String): DailyStepSummary?
    suspend fun updateHealthConnectSteps(date: String, healthConnectSteps: Long)
    suspend fun updateActivityMinutes(date: String, activityMinutes: Map<String, Int>, stepEquivalents: Long)
    suspend fun updateEscrow(date: String, escrowSteps: Long, syncCount: Int)
    suspend fun releaseEscrow(date: String)
    suspend fun discardEscrow(date: String)

    /** Sum of credited steps across [startDate]..[endDate] inclusive (weekly-challenge total). */
    suspend fun sumCreditedSteps(startDate: String, endDate: String): Long

    /**
     * Atomically credits a battle-step reward under the per-day [dailyCap] (#227 wraps
     * `DailyStepDao.creditBattleStepsAtomic`; the impl hands the real `PlayerProfileDao` into the
     * Room `@Transaction` so the wallet credit stays inside the one DB-scoped transaction).
     * Returns the amount actually credited (0 when the cap is exhausted, partial under the cap).
     */
    suspend fun creditBattleStepsAtomic(date: String, requested: Long, dailyCap: Long): Long

    /** Atomically credits boss Power Stones under the per-day [dailyCap]. Mirrors
     *  [creditBattleStepsAtomic]. */
    suspend fun creditBossPowerStonesAtomic(date: String, requested: Long, dailyCap: Long): Long
}
