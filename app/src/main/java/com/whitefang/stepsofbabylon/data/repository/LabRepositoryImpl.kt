package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.LabDao
import com.whitefang.stepsofbabylon.data.local.LabResearchEntity
import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LabRepositoryImpl @Inject constructor(
    private val dao: LabDao,
) : LabRepository {

    override fun observeAllResearch(): Flow<Map<ResearchType, Int>> =
        dao.getAll().map { list ->
            list.associate { ResearchType.valueOf(it.researchType) to it.level }
        }

    override fun observeActiveResearch(): Flow<List<ActiveResearch>> =
        dao.getActive().map { list ->
            list.map { ActiveResearch(
                type = ResearchType.valueOf(it.researchType),
                level = it.level,
                startedAt = it.startedAt!!,
                completesAt = it.completesAt!!,
            ) }
        }

    override suspend fun getResearchLevel(type: ResearchType): Int =
        dao.getByType(type.name).first()?.level ?: 0

    override suspend fun getActiveResearchCount(): Int =
        dao.getActive().first().size

    override suspend fun startResearch(type: ResearchType, completesAt: Long, startedAt: Long) {
        val entity = dao.getByType(type.name).first() ?: return
        dao.upsert(entity.copy(startedAt = startedAt, completesAt = completesAt))
    }

    override suspend fun completeResearch(type: ResearchType) {
        val entity = dao.getByType(type.name).first() ?: return
        dao.upsert(entity.copy(level = entity.level + 1, startedAt = null, completesAt = null))
    }

    /**
     * Seeds rows for any [ResearchType] enum entries that do not yet have a row in
     * `lab_research`. Per-enum filter — not a global "seed if completely empty" gate.
     *
     * Pre-fix (issue #20 sibling): the previous implementation only seeded when the
     * table was fully empty (`if (dao.getAll().first().isEmpty())`). Players upgrading
     * from an older AAB that pre-dated a new content addition (e.g. v8 → v9 added
     * [ResearchType.MULTISHOT_RESEARCH] / [ResearchType.BOUNCE_RESEARCH] in R4-02b) had
     * a non-empty table, so the early-return branch fired and the new enums' rows were
     * never inserted. Result: the research entries appeared nowhere in the Labs UI for
     * upgrade-from-v8 testers.
     *
     * Post-fix: same pattern as [com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImpl.ensureSeedData]
     * and [com.whitefang.stepsofbabylon.data.repository.WorkshopRepositoryImpl.ensureUpgradesExist].
     * Existing rows' levels and active-research state are preserved — only missing
     * enum entries get a new default-level row.
     */
    override suspend fun ensureResearchExists() {
        val existing: Set<String> = dao.getAll().first().mapTo(mutableSetOf()) { it.researchType }
        val missing = ResearchType.entries.filter { it.name !in existing }
        missing.forEach { dao.upsert(LabResearchEntity(researchType = it.name)) }
    }
}
