package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LabDao {
    @Query("SELECT * FROM lab_research")
    fun getAll(): Flow<List<LabResearchEntity>>

    @Query("SELECT * FROM lab_research WHERE researchType = :researchType")
    fun getByType(researchType: String): Flow<LabResearchEntity?>

    @Query("SELECT * FROM lab_research WHERE startedAt IS NOT NULL")
    fun getActive(): Flow<List<LabResearchEntity>>

    @Upsert
    suspend fun upsert(entity: LabResearchEntity)
}
