package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.CosmeticEntity
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the `CosmeticRepository → CosmeticItem.overrideColors` mapping.
 *
 * Covers the last mile of the C.2 RO-07 pipeline plumbed by PR 1: the real
 * [CosmeticRepositoryImpl] must turn the private `ZIGGURAT_COLOR_LOOKUP` table entry for a
 * seeded cosmetic into a non-null [com.whitefang.stepsofbabylon.domain.model.CosmeticItem.overrideColors]
 * when observed. PR 1 proved the `VM → engine.cosmeticOverrides` half of the chain with a
 * synthetic fixture in a fake repo; this test proves the `seed → lookup → toDomain` half on
 * the real impl. Together they close the end-to-end
 * "CosmeticRepo → BattleViewModel → GameEngine.cosmeticOverrides → layer colors" pipeline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CosmeticRepositoryImplTest {
    @Test
    fun `C2PR2 - ensureSeedData inserts zig_jade as first end-to-end cosmetic`() =
        runTest {
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)

            repo.ensureSeedData()

            val jade = repo.observeAll().first().find { it.cosmeticId == "zig_jade" }
            assertNotNull(jade, "seed data must include zig_jade")
            assertEquals(CosmeticCategory.ZIGGURAT_SKIN, jade!!.category)
            assertEquals("Jade Ziggurat", jade.name)
            assertEquals(150L, jade.priceGems)
            assertEquals(false, jade.isOwned, "freshly seeded cosmetic must not be auto-owned")
            assertEquals(false, jade.isEquipped, "freshly seeded cosmetic must not be auto-equipped")
        }

    @Test
    fun `C2PR2 - zig_jade propagates jade palette via overrideColors from ZIGGURAT_COLOR_LOOKUP`() =
        runTest {
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val jade = repo.observeAll().first().single { it.cosmeticId == "zig_jade" }

            assertNotNull(jade.overrideColors, "zig_jade must have overrideColors populated from ZIGGURAT_COLOR_LOOKUP")
            assertEquals(
                5,
                jade.overrideColors!!.size,
                "ZIGGURAT_COLOR_LOOKUP contract: exactly 5 colors, one per ziggurat layer (matches ZigguratEntity.DEFAULT_COLORS)",
            )
            // Palette is bottom → top, deep jade to pale highlight. Asserting the exact values so
            // any accidental palette mutation surfaces as a test failure (content-as-code contract).
            assertEquals(
                listOf(
                    0xFF104E3C.toInt(),
                    0xFF1A6B52.toInt(),
                    0xFF2A8F6E.toInt(),
                    0xFF3CAB82.toInt(),
                    0xFF54C79A.toInt(),
                ),
                jade.overrideColors,
            )
        }

    @Test
    fun `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP`() =
        runTest {
            // C.2 PR 3: the second ZIGGURAT_COLOR_LOOKUP entry. Resolves the IRON_SOLES
            // MilestoneReward.Cosmetic mismatch (see ClaimMilestoneTest end-to-end case).
            // Palette: deep lapis base → bright lapis → pyrite-gold crown (traditional lapis
            // with gold flecks). Exact-value assertions so any accidental palette mutation
            // surfaces as a test failure (content-as-code contract, same pattern as zig_jade).
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val lapis = repo.observeAll().first().single { it.cosmeticId == "lapis_lazuli_skin" }
            assertEquals(CosmeticCategory.ZIGGURAT_SKIN, lapis.category)
            assertEquals("Lapis Lazuli Ziggurat Skin", lapis.name)
            assertEquals(500L, lapis.priceGems)
            assertNotNull(
                lapis.overrideColors,
                "lapis_lazuli_skin must have overrideColors populated from ZIGGURAT_COLOR_LOOKUP",
            )
            assertEquals(
                listOf(
                    0xFF1A1F5C.toInt(),
                    0xFF2A3880.toInt(),
                    0xFF3B4FAB.toInt(),
                    0xFF4F68C8.toInt(),
                    0xFFD4A84A.toInt(),
                ),
                lapis.overrideColors,
            )
        }

    @Test
    fun `C2PR3b - garden_ziggurat_skin propagates hanging-gardens palette via overrideColors`() =
        runTest {
            // C.2 PR 3b: the third ZIGGURAT_COLOR_LOOKUP entry. Resolves the MARATHON_WALKER
            // MilestoneReward.Cosmetic mismatch. Palette: warm terracotta base → cascading
            // foliage → pale bloom canopy. Exact-value assertions lock the content-as-code
            // contract (any accidental palette mutation fails the test).
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val garden = repo.observeAll().first().single { it.cosmeticId == "garden_ziggurat_skin" }
            assertEquals(CosmeticCategory.ZIGGURAT_SKIN, garden.category)
            assertEquals("Garden Ziggurat Skin", garden.name)
            assertEquals(600L, garden.priceGems)
            assertNotNull(
                garden.overrideColors,
                "garden_ziggurat_skin must have overrideColors populated from ZIGGURAT_COLOR_LOOKUP",
            )
            assertEquals(
                listOf(
                    0xFF8B4726.toInt(),
                    0xFFAD7B4C.toInt(),
                    0xFF5E7F47.toInt(),
                    0xFF7BA85A.toInt(),
                    0xFFE0C890.toInt(),
                ),
                garden.overrideColors,
            )
        }

    @Test
    fun `C2PR3c - sandals_of_gilgamesh propagates bronze-ziggurat palette via overrideColors`() =
        runTest {
            // C.2 PR 3c: the fourth ZIGGURAT_COLOR_LOOKUP entry. Resolves the GLOBE_TROTTER
            // MilestoneReward.Cosmetic mismatch. Palette: dark weathered bronze → polished
            // bronze → gold crown (heroic motif). Note the id carries footwear narrative but
            // the cosmetic is implemented as a bronze ziggurat variant (see SEED_COSMETICS
            // inline comment explaining the reframe).
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val sandals = repo.observeAll().first().single { it.cosmeticId == "sandals_of_gilgamesh" }
            assertEquals(CosmeticCategory.ZIGGURAT_SKIN, sandals.category)
            assertEquals("Sandals of Gilgamesh", sandals.name)
            assertEquals(500L, sandals.priceGems)
            assertNotNull(
                sandals.overrideColors,
                "sandals_of_gilgamesh must have overrideColors populated from ZIGGURAT_COLOR_LOOKUP",
            )
            assertEquals(
                listOf(
                    0xFF3B2A1A.toInt(),
                    0xFF6B4A2A.toInt(),
                    0xFF8B6B42.toInt(),
                    0xFFB89152.toInt(),
                    0xFFE8C068.toInt(),
                ),
                sandals.overrideColors,
            )
        }

    @Test
    fun `C2PR2 - other seeded ziggurat cosmetics have null overrideColors pending content PRs`() =
        runTest {
            // Regression guard: zig_jade, lapis_lazuli_skin, garden_ziggurat_skin, sandals_of_gilgamesh
            // and zig_obsidian (V1X-14) ship palettes via ZIGGURAT_COLOR_LOOKUP. The remaining seeded
            // ZIGGURAT_SKIN rows (zig_crystal, zig_golden) must continue to return null overrideColors
            // so the renderer falls through to the biome default (and the StoreScreen keeps them under
            // the "Coming Soon" guard). (#221 removed the dead PROJECTILE_EFFECT/ENEMY_SKIN rows.)
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val allItems = repo.observeAll().first()
            val otherIds = listOf("zig_crystal", "zig_golden")

            otherIds.forEach { id ->
                val item = allItems.single { it.cosmeticId == id }
                assertNull(
                    item.overrideColors,
                    "$id must not have overrideColors until its palette ships in a later PR",
                )
            }
        }

    @Test
    fun `C2PR2 - equipped zig_jade surfaces via observeEquipped with overrideColors intact`() =
        runTest {
            // Proves the equip path: after equip(), observeEquipped() emits zig_jade with the
            // same overrideColors that BattleViewModel then pushes into engine.cosmeticOverrides.
            // This is the repo-layer mirror of the PR 1 VM→engine test — together they prove
            // the full chain from seed to engine.
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            // Purchase is a prerequisite for equip in the UI flow; the DAO accepts equip
            // without owned, but test the realistic path.
            repo.purchase("zig_jade")
            repo.equip("zig_jade")

            val equipped = repo.observeEquipped().first()
            assertEquals(1, equipped.size, "only zig_jade is equipped")
            val jade = equipped.single()
            assertEquals("zig_jade", jade.cosmeticId)
            assertTrue(jade.isEquipped)
            assertTrue(jade.isOwned)
            assertNotNull(jade.overrideColors, "equipped cosmetic must carry overrideColors for engine hydration")
            assertEquals(5, jade.overrideColors!!.size)
        }

    @Test
    fun `C2PR2 - ensureSeedData is idempotent on repeat call`() =
        runTest {
            // Post-ensureSeedData-fix: idempotency now holds because the per-cosmeticId
            // filter computes `missing = SEED_COSMETICS.filter { id !in existingIds }` and
            // upserts only the missing set. Running twice on a steady-state catalogue
            // yields `missing == emptyList()` on the second call, which no-ops at the DAO
            // level \u2014 exactly the same end-state as the old `dao.count() > 0` gate, but
            // arrived at via a different mechanism that also supports partial-catalogue
            // upgrades (see the next test).
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)

            repo.ensureSeedData()
            val countAfterFirst = dao.count()
            repo.ensureSeedData()
            val countAfterSecond = dao.count()

            assertEquals(countAfterFirst, countAfterSecond, "repeat ensureSeedData must not duplicate rows")
            assertEquals(
                7,
                countAfterFirst,
                "7 ziggurat seed rows (zig_jade + lapis_lazuli_skin + garden_ziggurat_skin + sandals_of_gilgamesh + zig_obsidian + zig_crystal + zig_golden)",
            )
        }

    @Test
    fun `ensureSeedData inserts newly-added rows on partial catalogue upgrade`() =
        runTest {
            // Regression guard for the old `dao.count() > 0` gate bug: before the fix, a
            // device that had already seeded the pre-`zig_jade` 7-row catalogue would NEVER
            // receive `zig_jade` on upgrade (short-circuit fired because count was 7, not
            // 0). After the fix, the per-cosmeticId filter sees `zig_jade` missing and
            // inserts only that one row.
            //
            // Simulates the exact upgrade path: pre-seed the 7 legacy rows manually (no
            // `zig_jade`), run ensureSeedData, assert `zig_jade` now present and the
            // original 7 still intact.
            val dao = FakeCosmeticDao()
            val legacySeed =
                listOf(
                    CosmeticEntity(
                        cosmeticId = "zig_obsidian",
                        category = "ZIGGURAT_SKIN",
                        name = "Obsidian Ziggurat",
                        description = "Dark volcanic stone",
                        priceGems = 100,
                    ),
                    CosmeticEntity(
                        cosmeticId = "zig_crystal",
                        category = "ZIGGURAT_SKIN",
                        name = "Crystal Ziggurat",
                        description = "Translucent crystal layers",
                        priceGems = 200,
                    ),
                    CosmeticEntity(
                        cosmeticId = "zig_golden",
                        category = "ZIGGURAT_SKIN",
                        name = "Golden Ziggurat",
                        description = "Pure gold plating",
                        priceGems = 300,
                    ),
                )
            dao.upsertAll(legacySeed)
            assertEquals(3, dao.count(), "baseline: legacy catalogue has 3 ziggurat rows")

            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            assertEquals(
                7,
                dao.count(),
                "after ensureSeedData: zig_jade + lapis_lazuli_skin + garden_ziggurat_skin + sandals_of_gilgamesh added, legacy 3 ziggurat preserved",
            )
            val items = repo.observeAll().first()
            val jade = items.single { it.cosmeticId == "zig_jade" }
            assertNotNull(jade.overrideColors, "new zig_jade row must carry overrideColors from ZIGGURAT_COLOR_LOOKUP")
            assertEquals(5, jade.overrideColors!!.size)
            val lapis = items.single { it.cosmeticId == "lapis_lazuli_skin" }
            assertNotNull(lapis.overrideColors, "new lapis_lazuli_skin row must also carry overrideColors (C.2 PR 3)")
            assertEquals(5, lapis.overrideColors!!.size)
            val garden = items.single { it.cosmeticId == "garden_ziggurat_skin" }
            assertNotNull(
                garden.overrideColors,
                "new garden_ziggurat_skin row must also carry overrideColors (C.2 PR 3b)",
            )
            assertEquals(5, garden.overrideColors!!.size)
            val sandals = items.single { it.cosmeticId == "sandals_of_gilgamesh" }
            assertNotNull(
                sandals.overrideColors,
                "new sandals_of_gilgamesh row must also carry overrideColors (C.2 PR 3c)",
            )
            assertEquals(5, sandals.overrideColors!!.size)
            // Legacy ids still present \u2014 the upgrade is additive, not replacive.
            for (legacyId in legacySeed.map { it.cosmeticId }) {
                assertNotNull(
                    items.find { it.cosmeticId == legacyId },
                    "legacy id `$legacyId` must survive the upgrade",
                )
            }
        }

    @Test
    fun `ensureSeedData preserves player state on existing rows (isOwned, isEquipped)`() =
        runTest {
            // Regression guard for the most player-visible risk of a naive "re-upsert all
            // seed rows" fix: a row with `isOwned = true` / `isEquipped = true` must NOT
            // be reverted to the default `isOwned = false` / `isEquipped = false` when
            // ensureSeedData runs on subsequent launches. The per-cosmeticId filter skips
            // already-present ids entirely \u2014 the DAO never sees the seed row for those
            // ids, so player state on them is untouched.
            val dao = FakeCosmeticDao()
            val ownedEquippedJade =
                CosmeticEntity(
                    cosmeticId = "zig_jade",
                    category = "ZIGGURAT_SKIN",
                    name = "Jade Ziggurat",
                    description = "Deep jade stone with pale highlights",
                    priceGems = 150,
                    isOwned = true,
                    isEquipped = true,
                )
            dao.upsert(ownedEquippedJade)
            assertEquals(1, dao.count())

            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            // All 7 seed rows now present (6 new + the pre-existing zig_jade preserved).
            assertEquals(7, dao.count())
            val jade = repo.observeAll().first().single { it.cosmeticId == "zig_jade" }
            assertTrue(jade.isOwned, "pre-existing player ownership must survive ensureSeedData")
            assertTrue(jade.isEquipped, "pre-existing equipped state must survive ensureSeedData")
            // Palette still plumbs through (proves toDomain still applies ZIGGURAT_COLOR_LOOKUP
            // to the already-persisted row, not just to freshly-seeded rows).
            assertNotNull(jade.overrideColors)
        }

    @Test
    fun `V1X14 - zig_obsidian propagates obsidian palette via overrideColors`() =
        runTest {
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val obsidian = repo.observeAll().first().single { it.cosmeticId == "zig_obsidian" }

            assertNotNull(obsidian.overrideColors, "zig_obsidian must have overrideColors from ZIGGURAT_COLOR_LOOKUP")
            assertEquals(5, obsidian.overrideColors!!.size)
            assertEquals(
                listOf(
                    0xFF1A1A1A.toInt(),
                    0xFF2D2D2D.toInt(),
                    0xFF3F3F3F.toInt(),
                    0xFF525252.toInt(),
                    0xFF7A6F4D.toInt(),
                ),
                obsidian.overrideColors,
            )
        }

    @Test
    fun `R221 - a row whose category does not parse is filtered from observeAll, not crashed`() =
        runTest {
            val dao = FakeCosmeticDao()
            // A row persisted with a category string that is NOT a CosmeticCategory value (simulates a
            // legacy/dead row on an upgraded device). The resilient mapping must drop it rather than
            // throw IllegalArgumentException from CosmeticCategory.valueOf.
            dao.upsert(
                CosmeticEntity(
                    cosmeticId = "legacy_dead",
                    category = "LEGACY_UNKNOWN_CATEGORY",
                    name = "Legacy",
                    description = "A row from before #221",
                    priceGems = 100,
                ),
            )
            val repo = CosmeticRepositoryImpl(dao)

            val items = repo.observeAll().first()
            assertTrue(
                items.none { it.cosmeticId == "legacy_dead" },
                "a row whose category no longer parses must be filtered out of the domain list, not crash",
            )
        }

    @Test
    fun `R221 - ensureSeedData purges known dead cosmetic ids from the DB`() =
        runTest {
            val dao = FakeCosmeticDao()
            // Simulate an already-installed device that still has a dead projectile cosmetic row.
            dao.upsert(
                CosmeticEntity(
                    cosmeticId = "proj_fire",
                    category = "PROJECTILE_EFFECT",
                    name = "Fire Trails",
                    description = "Blazing projectile trails",
                    priceGems = 150,
                ),
            )
            val repo = CosmeticRepositoryImpl(dao)

            repo.ensureSeedData()

            assertTrue(
                dao.observeAll().first().none { it.cosmeticId == "proj_fire" },
                "ensureSeedData must purge known dead cosmetic ids from the DB",
            )
        }
}
