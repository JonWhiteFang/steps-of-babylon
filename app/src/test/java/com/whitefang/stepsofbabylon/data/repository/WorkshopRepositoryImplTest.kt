package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.local.WorkshopUpgradeEntity
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Tests for [WorkshopRepositoryImpl.ensureUpgradesExist], the additive-seeding fix for
 * GitHub issue #20.
 *
 * Pre-fix behaviour: the implementation only seeded rows when the table was completely
 * empty (`if (dao.getAll().first().isEmpty())`). For a player upgrading from an older
 * AAB whose `workshop_upgrade` table already had rows for all then-existing
 * [UpgradeType] enum entries, a *new* enum entry added in a subsequent release
 * (e.g. [UpgradeType.RAPID_FIRE] in R4-03 / AAB v9) never got a row inserted,
 * making the upgrade invisible in the Workshop UI.
 *
 * Post-fix: per-enum-name filter selects only the missing entries and inserts default-level
 * rows for them while preserving any existing rows' levels (and any other state).
 *
 * The repo's other read/write paths (observe, upgrade level, atomic purchase) already had
 * indirect coverage through [com.whitefang.stepsofbabylon.presentation.workshop.WorkshopViewModelTest]
 * and [com.whitefang.stepsofbabylon.domain.usecase.PurchaseUpgradeTest] via fakes; those are
 * not duplicated here. The mock [PlayerProfileDao] is supplied solely to satisfy the
 * constructor — `ensureUpgradesExist` does not invoke any of its methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopRepositoryImplTest {

    private val playerProfileDao: PlayerProfileDao = mock()

    @Test
    fun `issue 20 - ensureUpgradesExist seeds every UpgradeType on a completely empty table (fresh install path)`() = runTest {
        val dao = FakeWorkshopDao()
        val repo = WorkshopRepositoryImpl(dao, playerProfileDao)

        repo.ensureUpgradesExist()

        val rows = dao.getAll().first()
        assertEquals(
            UpgradeType.entries.size, rows.size,
            "ensureUpgradesExist on an empty table must seed exactly UpgradeType.entries.size rows",
        )
        // Every enum entry has a corresponding row, all at default level 0.
        UpgradeType.entries.forEach { type ->
            val row = rows.firstOrNull { it.upgradeType == type.name }
            assertNotNull(row, "row missing for ${type.name}")
            assertEquals(0, row!!.level, "freshly seeded row for ${type.name} must have level 0")
        }
    }

    @Test
    fun `issue 20 - ensureUpgradesExist inserts the new RAPID_FIRE enum on an upgrade-from-v8 install`() = runTest {
        val dao = FakeWorkshopDao()
        val repo = WorkshopRepositoryImpl(dao, playerProfileDao)

        // Simulate an AAB v8 install state: every UpgradeType entry except the post-v8
        // additions (R4-03 RAPID_FIRE) is already in the table, with assorted levels
        // representing a player who's invested in the game.
        val historicalEnums = UpgradeType.entries.filter { it != UpgradeType.RAPID_FIRE }
        val historicalRows = historicalEnums.mapIndexed { i, type ->
            // Vary levels so the preservation assertion below is meaningful — level 0 for
            // some, levels 1-5 for others.
            WorkshopUpgradeEntity(upgradeType = type.name, level = i % 6)
        }
        dao.rows.value = historicalRows

        repo.ensureUpgradesExist()

        val rows = dao.getAll().first()
        // Total row count must equal the full enum size — the missing RAPID_FIRE row was added.
        assertEquals(UpgradeType.entries.size, rows.size, "missing enum entries must be seeded")
        // RAPID_FIRE specifically is now present at default level 0.
        val rapidFireRow = rows.firstOrNull { it.upgradeType == UpgradeType.RAPID_FIRE.name }
        assertNotNull(rapidFireRow, "RAPID_FIRE row must be present after ensureUpgradesExist")
        assertEquals(0, rapidFireRow!!.level, "newly seeded RAPID_FIRE row must have default level 0")
        // Every historical row's level is preserved unchanged — the additive seed did not
        // overwrite existing player progress.
        historicalRows.forEach { historical ->
            val current = rows.firstOrNull { it.upgradeType == historical.upgradeType }
            assertNotNull(current, "historical row for ${historical.upgradeType} disappeared")
            assertEquals(
                historical.level, current!!.level,
                "historical level for ${historical.upgradeType} must be preserved",
            )
        }
    }

    @Test
    fun `issue 20 - ensureUpgradesExist is idempotent when every UpgradeType is already seeded`() = runTest {
        val dao = FakeWorkshopDao()
        val repo = WorkshopRepositoryImpl(dao, playerProfileDao)

        // Seed every enum at level 0 to simulate a fresh install that has already run
        // ensureUpgradesExist once.
        repo.ensureUpgradesExist()
        val firstPassRows = dao.getAll().first()
        // Mutate one row's level to a non-default value to verify idempotency preserves it.
        dao.rows.value = firstPassRows.map {
            if (it.upgradeType == UpgradeType.DAMAGE.name) it.copy(level = 7) else it
        }

        // Second call — must be a no-op (no inserts, no updates to existing rows).
        repo.ensureUpgradesExist()

        val secondPassRows = dao.getAll().first()
        assertEquals(UpgradeType.entries.size, secondPassRows.size)
        val damageRow = secondPassRows.first { it.upgradeType == UpgradeType.DAMAGE.name }
        assertEquals(7, damageRow.level, "second ensureUpgradesExist call must not reset existing levels")
    }

    @Test
    fun `issue 20 - ensureUpgradesExist preserves an unknown upgradeType row in the table`() = runTest {
        // Defensive: if a future release renames or removes an enum entry, the additive
        // seeding logic must not crash on the orphan row. Today every UpgradeType is
        // production-stable, but coding to the contract avoids surprises if that changes.
        val dao = FakeWorkshopDao()
        val repo = WorkshopRepositoryImpl(dao, playerProfileDao)

        // Pre-seed an orphan row alongside a partial enum subset.
        dao.rows.value = listOf(
            WorkshopUpgradeEntity(upgradeType = "RETIRED_ENUM_NAME", level = 99),
            WorkshopUpgradeEntity(upgradeType = UpgradeType.DAMAGE.name, level = 3),
        )

        repo.ensureUpgradesExist()

        val rows = dao.getAll().first()
        // Orphan row preserved (we don't delete unknown rows — they're not ours to remove).
        val orphan = rows.firstOrNull { it.upgradeType == "RETIRED_ENUM_NAME" }
        assertNotNull(orphan, "unknown rows must not be deleted by ensureUpgradesExist")
        assertEquals(99, orphan!!.level)
        // Existing DAMAGE level preserved.
        val damage = rows.first { it.upgradeType == UpgradeType.DAMAGE.name }
        assertEquals(3, damage.level)
        // All missing enums (every UpgradeType except DAMAGE) seeded at level 0.
        val missingCount = UpgradeType.entries.count { it != UpgradeType.DAMAGE }
        val seededCount = rows.count { it.upgradeType != "RETIRED_ENUM_NAME" && it.upgradeType != UpgradeType.DAMAGE.name }
        assertEquals(missingCount, seededCount, "all missing enum entries must be seeded")
    }
}
