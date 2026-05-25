package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.LabDao
import com.whitefang.stepsofbabylon.data.local.LabResearchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake for [LabDao].
 *
 * Introduced for the issue #20 sibling fix (Labs additive seeding) to support repo-layer
 * end-to-end tests on [com.whitefang.stepsofbabylon.data.repository.LabRepositoryImpl] without
 * a real Room DB. Behaviour mirrors the real DAO's observable-flow + suspend-write contract
 * on the `researchType: String` primary key. Tests against the additive-seeding path
 * ([com.whitefang.stepsofbabylon.data.repository.LabRepositoryImpl.ensureResearchExists])
 * pre-seed [rows] with a subset of [com.whitefang.stepsofbabylon.domain.model.ResearchType.entries]
 * to simulate an upgrade-from-older-AAB install state, then assert that `ensureResearchExists`
 * fills in the missing entries while preserving existing levels and active-research state.
 */
class FakeLabDao : LabDao {
    val rows = MutableStateFlow<List<LabResearchEntity>>(emptyList())

    override fun getAll(): Flow<List<LabResearchEntity>> = rows

    override fun getByType(researchType: String): Flow<LabResearchEntity?> =
        rows.map { list -> list.firstOrNull { it.researchType == researchType } }

    override fun getActive(): Flow<List<LabResearchEntity>> =
        rows.map { list -> list.filter { it.startedAt != null } }

    override suspend fun upsert(entity: LabResearchEntity) {
        rows.update { list ->
            val idx = list.indexOfFirst { it.researchType == entity.researchType }
            if (idx >= 0) list.toMutableList().also { it[idx] = entity } else list + entity
        }
    }
}
