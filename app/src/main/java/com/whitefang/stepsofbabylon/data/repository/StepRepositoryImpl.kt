package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.DailyStepRecordEntity
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StepRepositoryImpl @Inject constructor(
    private val dao: DailyStepDao,
) : StepRepository {

    override fun observeTodayRecord(date: String): Flow<DailyStepSummary?> =
        dao.getByDate(date).map { it?.toDomain() }

    override fun observeHistory(startDate: String, endDate: String): Flow<List<DailyStepSummary>> =
        dao.getRange(startDate, endDate).map { list -> list.map { it.toDomain() } }

    // #121: each daily_step_record field-update delegates to a column-targeted upsert that
    // touches ONLY its own columns (see [DailyStepDao] for the rationale). The previous
    // read-copy-upsert here rewrote every column from a stale snapshot, so concurrent writers
    // from the sensor service / sync worker / HC validator clobbered one another's fields.
    override suspend fun updateDailySteps(date: String, sensorSteps: Long, creditedSteps: Long) =
        dao.setSensorAndCreditedSteps(date, sensorSteps, creditedSteps)

    override suspend fun getDailyRecord(date: String): DailyStepSummary? =
        dao.getByDateOnce(date)?.toDomain()

    override suspend fun updateHealthConnectSteps(date: String, healthConnectSteps: Long) =
        dao.setHealthConnectSteps(date, healthConnectSteps)

    override suspend fun updateActivityMinutes(date: String, activityMinutes: Map<String, Int>, stepEquivalents: Long) =
        dao.setActivityMinutes(date, activityMinutes, stepEquivalents)

    override suspend fun updateEscrow(date: String, escrowSteps: Long, syncCount: Int) =
        dao.setEscrow(date, escrowSteps, syncCount)

    override suspend fun releaseEscrow(date: String) = dao.clearEscrow(date)

    override suspend fun discardEscrow(date: String) = dao.clearEscrow(date)

    private fun DailyStepRecordEntity.toDomain() = DailyStepSummary(
        date = date,
        sensorSteps = sensorSteps,
        healthConnectSteps = healthConnectSteps,
        creditedSteps = creditedSteps,
        escrowSteps = escrowSteps,
        escrowSyncCount = escrowSyncCount,
        activityMinutes = activityMinutes,
        stepEquivalents = stepEquivalents,
    )
}
