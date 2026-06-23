package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import kotlinx.coroutines.flow.Flow

interface LabRepository {
    fun observeAllResearch(): Flow<Map<ResearchType, Int>>

    fun observeActiveResearch(): Flow<List<ActiveResearch>>

    suspend fun getResearchLevel(type: ResearchType): Int

    suspend fun getActiveResearchCount(): Int

    suspend fun startResearch(
        type: ResearchType,
        completesAt: Long,
        startedAt: Long = System.currentTimeMillis(),
    )

    suspend fun completeResearch(type: ResearchType)

    suspend fun ensureResearchExists()
}
