package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DailyLoginDao {
    @Query("SELECT * FROM daily_login WHERE date = :date")
    suspend fun getByDate(date: String): DailyLoginEntity?

    @Upsert
    suspend fun upsert(entity: DailyLoginEntity)
}
