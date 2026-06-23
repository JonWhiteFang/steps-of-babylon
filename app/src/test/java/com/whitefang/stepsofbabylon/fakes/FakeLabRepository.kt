package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLabRepository : LabRepository {
    val levels =
        MutableStateFlow<Map<ResearchType, Int>>(
            ResearchType.entries.associateWith { 0 },
        )
    val active = MutableStateFlow<List<ActiveResearch>>(emptyList())

    override fun observeAllResearch(): Flow<Map<ResearchType, Int>> = levels

    override fun observeActiveResearch(): Flow<List<ActiveResearch>> = active

    override suspend fun getResearchLevel(type: ResearchType): Int = levels.value[type] ?: 0

    override suspend fun getActiveResearchCount(): Int = active.value.size

    override suspend fun startResearch(
        type: ResearchType,
        completesAt: Long,
        startedAt: Long,
    ) {
        val level = levels.value[type] ?: 0
        active.update { it + ActiveResearch(type, level, startedAt, completesAt) }
    }

    override suspend fun completeResearch(type: ResearchType) {
        levels.update { it + (type to ((it[type] ?: 0) + 1)) }
        active.update { list -> list.filter { it.type != type } }
    }

    override suspend fun ensureResearchExists() {}
}
