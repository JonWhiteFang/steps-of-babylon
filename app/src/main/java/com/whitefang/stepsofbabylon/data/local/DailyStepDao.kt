package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepDao {

    @Query("SELECT * FROM daily_step_record WHERE date = :date")
    fun getByDate(date: String): Flow<DailyStepRecordEntity?>

    @Query("SELECT * FROM daily_step_record WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getRange(startDate: String, endDate: String): Flow<List<DailyStepRecordEntity>>

    @Upsert
    suspend fun upsert(entity: DailyStepRecordEntity)

    @Query("SELECT * FROM daily_step_record WHERE date = :date")
    suspend fun getByDateOnce(date: String): DailyStepRecordEntity?

    @Query("UPDATE daily_step_record SET escrowSteps = 0, escrowSyncCount = 0 WHERE date = :date")
    suspend fun clearEscrow(date: String)

    @Query("SELECT COALESCE(SUM(creditedSteps), 0) FROM daily_step_record WHERE date BETWEEN :startDate AND :endDate")
    suspend fun sumCreditedSteps(startDate: String, endDate: String): Long

    /**
     * Returns today's [DailyStepRecordEntity.battleStepsEarned], or 0 if no
     * row exists for [date] yet.
     */
    @Query("SELECT COALESCE(battleStepsEarned, 0) FROM daily_step_record WHERE date = :date")
    suspend fun getBattleStepsEarned(date: String): Long

    /**
     * Adds [delta] to the [DailyStepRecordEntity.battleStepsEarned] counter for
     * [date], creating the row (with all other fields defaulted) if it does
     * not yet exist. Uses an UPSERT so it is safe to call before any walking
     * step has been recorded on a given day.
     */
    @Query(
        "INSERT INTO daily_step_record (date, battleStepsEarned) VALUES (:date, :delta) " +
            "ON CONFLICT(date) DO UPDATE SET battleStepsEarned = battleStepsEarned + :delta",
    )
    suspend fun incrementBattleSteps(date: String, delta: Long)
}
