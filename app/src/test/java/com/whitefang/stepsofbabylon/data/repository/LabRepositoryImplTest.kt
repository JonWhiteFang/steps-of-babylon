package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.LabResearchEntity
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.fakes.FakeLabDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for [LabRepositoryImpl.ensureResearchExists], the additive-seeding fix for
 * GitHub issue #20 (sibling — same shape as [WorkshopRepositoryImplTest]).
 *
 * Pre-fix behaviour: the implementation only seeded rows when the table was completely
 * empty (`if (dao.getAll().first().isEmpty())`). For a player upgrading from an older
 * AAB whose `lab_research` table already had rows for all then-existing
 * [ResearchType] enum entries, *new* enum entries added in a subsequent release
 * (e.g. [ResearchType.MULTISHOT_RESEARCH] / [ResearchType.BOUNCE_RESEARCH] in R4-02b /
 * AAB v9) never got rows inserted, making the research entries invisible in the Labs UI.
 *
 * Post-fix: per-enum-name filter selects only the missing entries and inserts default-level
 * rows for them while preserving any existing rows' levels and active-research state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LabRepositoryImplTest {
    @Test
    fun `issue 20 sibling - ensureResearchExists seeds every ResearchType on a completely empty table`() =
        runTest {
            val dao = FakeLabDao()
            val repo = LabRepositoryImpl(dao)

            repo.ensureResearchExists()

            val rows = dao.getAll().first()
            assertEquals(
                ResearchType.entries.size,
                rows.size,
                "ensureResearchExists on an empty table must seed exactly ResearchType.entries.size rows",
            )
            ResearchType.entries.forEach { type ->
                val row = rows.firstOrNull { it.researchType == type.name }
                assertNotNull(row, "row missing for ${type.name}")
                assertEquals(0, row!!.level, "freshly seeded row for ${type.name} must have level 0")
                assertNull(row.startedAt, "freshly seeded row must have startedAt = null")
                assertNull(row.completesAt, "freshly seeded row must have completesAt = null")
            }
        }

    @Test
    fun `issue 20 sibling - ensureResearchExists inserts new MULTISHOT_RESEARCH and BOUNCE_RESEARCH on upgrade-from-v8 install`() =
        runTest {
            val dao = FakeLabDao()
            val repo = LabRepositoryImpl(dao)

            // Simulate an AAB v8 install state: every ResearchType entry except the post-v8
            // additions (R4-02b MULTISHOT_RESEARCH and BOUNCE_RESEARCH) is already in the table.
            // Vary levels and seed one entry as actively researching to verify the additive
            // path preserves both level state AND active-research timestamps.
            val newEnums = setOf(ResearchType.MULTISHOT_RESEARCH, ResearchType.BOUNCE_RESEARCH)
            val historicalEnums = ResearchType.entries.filter { it !in newEnums }
            val historicalRows =
                historicalEnums.mapIndexed { i, type ->
                    // First entry: actively researching (startedAt + completesAt populated). Others: idle.
                    if (i == 0) {
                        LabResearchEntity(
                            researchType = type.name,
                            level = 3,
                            startedAt = 1_000_000L,
                            completesAt = 1_001_000L,
                        )
                    } else {
                        LabResearchEntity(researchType = type.name, level = i % 5)
                    }
                }
            dao.rows.value = historicalRows

            repo.ensureResearchExists()

            val rows = dao.getAll().first()
            // Total row count must equal the full enum size — both missing entries were added.
            assertEquals(ResearchType.entries.size, rows.size, "missing enum entries must be seeded")
            // Both new entries are now present at default level 0 with no active-research state.
            newEnums.forEach { type ->
                val row = rows.firstOrNull { it.researchType == type.name }
                assertNotNull(row, "${type.name} row must be present after ensureResearchExists")
                assertEquals(0, row!!.level, "newly seeded ${type.name} row must have default level 0")
                assertNull(row.startedAt, "newly seeded ${type.name} must have startedAt = null")
                assertNull(row.completesAt, "newly seeded ${type.name} must have completesAt = null")
            }
            // Every historical row's level + active-research state is preserved unchanged.
            historicalRows.forEach { historical ->
                val current = rows.firstOrNull { it.researchType == historical.researchType }
                assertNotNull(current, "historical row for ${historical.researchType} disappeared")
                assertEquals(historical.level, current!!.level)
                assertEquals(historical.startedAt, current.startedAt, "active-research startedAt must be preserved")
                assertEquals(
                    historical.completesAt,
                    current.completesAt,
                    "active-research completesAt must be preserved",
                )
            }
        }

    @Test
    fun `issue 20 sibling - ensureResearchExists is idempotent when every ResearchType is already seeded`() =
        runTest {
            val dao = FakeLabDao()
            val repo = LabRepositoryImpl(dao)

            repo.ensureResearchExists()
            val firstPassRows = dao.getAll().first()
            // Mutate one row to verify idempotency preserves it.
            dao.rows.value =
                firstPassRows.map {
                    if (it.researchType == ResearchType.DAMAGE_RESEARCH.name) it.copy(level = 4) else it
                }

            repo.ensureResearchExists()

            val secondPassRows = dao.getAll().first()
            assertEquals(ResearchType.entries.size, secondPassRows.size)
            val damageRow = secondPassRows.first { it.researchType == ResearchType.DAMAGE_RESEARCH.name }
            assertEquals(4, damageRow.level, "second ensureResearchExists call must not reset existing levels")
        }
}
