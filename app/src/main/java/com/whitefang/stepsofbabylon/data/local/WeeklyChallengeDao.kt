package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WeeklyChallengeDao {

    @Query("SELECT * FROM weekly_challenge WHERE weekStartDate = :weekStart")
    suspend fun getByWeek(weekStart: String): WeeklyChallengeEntity?

    /**
     * Returns the most recent [limit] weekly challenge entries ordered by week start
     * date descending (newest first). Used by V1X-16 to populate the "last 4 weeks
     * history" section of the Economy dashboard.
     */
    @Query("SELECT * FROM weekly_challenge ORDER BY weekStartDate DESC LIMIT :limit")
    suspend fun getLastNWeeks(limit: Int): List<WeeklyChallengeEntity>

    @Upsert
    suspend fun upsert(entity: WeeklyChallengeEntity)
}
