package com.whitefang.stepsofbabylon.data.repository

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
    fun `C2PR2 - ensureSeedData inserts zig_jade as first end-to-end cosmetic`() = runTest {
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
    fun `C2PR2 - zig_jade propagates jade palette via overrideColors from ZIGGURAT_COLOR_LOOKUP`() = runTest {
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
    fun `C2PR2 - other seeded ziggurat cosmetics have null overrideColors pending content PRs`() = runTest {
        // Regression guard: only zig_jade ships a palette in C.2 PR 2. Remaining seeded
        // ZIGGURAT_SKIN rows must continue to return null overrideColors so the renderer
        // falls through to the biome default (and the StoreScreen keeps them under the
        // R2-11 "Coming Soon" guard). The other category seeds (PROJECTILE_EFFECT,
        // ENEMY_SKIN) are off the ZIGGURAT_COLOR_LOOKUP entirely — also null.
        val dao = FakeCosmeticDao()
        val repo = CosmeticRepositoryImpl(dao)
        repo.ensureSeedData()

        val allItems = repo.observeAll().first()
        val otherIds = listOf("zig_obsidian", "zig_crystal", "zig_golden", "proj_fire", "proj_lightning", "enemy_shadow", "enemy_neon")

        otherIds.forEach { id ->
            val item = allItems.single { it.cosmeticId == id }
            assertNull(item.overrideColors, "$id must not have overrideColors until its palette ships in a later PR")
        }
    }

    @Test
    fun `C2PR2 - equipped zig_jade surfaces via observeEquipped with overrideColors intact`() = runTest {
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
    fun `C2PR2 - ensureSeedData is idempotent on repeat call (count gate holds)`() = runTest {
        // Regression: ensureSeedData short-circuits when dao.count() > 0 (all-or-nothing seed
        // model). Asserting idempotency here documents the current contract so future content
        // PRs that change seed semantics surface as a test failure rather than a silent
        // double-seed.
        val dao = FakeCosmeticDao()
        val repo = CosmeticRepositoryImpl(dao)

        repo.ensureSeedData()
        val countAfterFirst = dao.count()
        repo.ensureSeedData()
        val countAfterSecond = dao.count()

        assertEquals(countAfterFirst, countAfterSecond, "repeat ensureSeedData must not duplicate rows")
        assertEquals(8, countAfterFirst, "PR 2 ships 8 seed rows (4 ziggurat incl. zig_jade + 2 projectile + 2 enemy)")
    }
}
