package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMissionDao {

    @Query("SELECT * FROM daily_mission WHERE date = :date")
    fun getByDate(date: String): Flow<List<DailyMissionEntity>>

    @Query("SELECT * FROM daily_mission WHERE date = :date")
    suspend fun getByDateOnce(date: String): List<DailyMissionEntity>

    @Insert
    suspend fun insert(entity: DailyMissionEntity)

    @Upsert
    suspend fun upsert(entity: DailyMissionEntity)

    @Query("UPDATE daily_mission SET progress = :progress, completed = :completed WHERE id = :id")
    suspend fun updateProgress(id: Int, progress: Int, completed: Boolean)

    /**
     * Atomic guarded claim (#122). The `AND claimed = 0` clause + rows-affected return make the
     * claim idempotent — only the first call for [id] transitions it to claimed and returns `1`;
     * a concurrent/duplicate call returns `0`. Callers credit the reward only when this returns
     * `1`, closing the double-credit window on a rapid double-tap of the Claim button.
     *
     * @return rows affected — `1` on the first claim, `0` if already claimed / not found.
     */
    @Query("UPDATE daily_mission SET claimed = 1 WHERE id = :id AND claimed = 0")
    suspend fun markClaimed(id: Int): Int

    @Query("SELECT COUNT(*) FROM daily_mission WHERE date = :date AND completed = 1 AND claimed = 0")
    fun countClaimable(date: String): Flow<Int>
}
