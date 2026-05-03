package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.DailyStepRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDailyStepDao : DailyStepDao {
    val data = MutableStateFlow<Map<String, DailyStepRecordEntity>>(emptyMap())

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

    override suspend fun getBattleStepsEarned(date: String): Long =
        data.value[date]?.battleStepsEarned ?: 0L

    override suspend fun incrementBattleSteps(date: String, delta: Long) {
        val existing = data.value[date]
        val updated = existing?.copy(battleStepsEarned = existing.battleStepsEarned + delta)
            ?: DailyStepRecordEntity(date = date, battleStepsEarned = delta)
        data.value = data.value + (date to updated)
    }
}
