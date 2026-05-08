package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.CosmeticDao
import com.whitefang.stepsofbabylon.data.local.CosmeticEntity
import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CosmeticRepositoryImpl @Inject constructor(
    private val dao: CosmeticDao,
) : CosmeticRepository {

    override fun observeAll(): Flow<List<CosmeticItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeOwned(): Flow<List<CosmeticItem>> =
        dao.observeOwned().map { list -> list.map { it.toDomain() } }

    override fun observeEquipped(): Flow<List<CosmeticItem>> =
        dao.observeEquipped().map { list -> list.map { it.toDomain() } }

    override suspend fun purchase(cosmeticId: String) = dao.markOwned(cosmeticId)

    override suspend fun equip(cosmeticId: String) {
        val entities = dao.observeAll().first()
        val target = entities.find { it.cosmeticId == cosmeticId } ?: return
        dao.unequipCategory(target.category)
        dao.equip(cosmeticId)
    }

    override suspend fun unequip(cosmeticId: String) = dao.unequip(cosmeticId)

    override suspend fun ensureSeedData() {
        // Per-cosmeticId filter rather than the previous all-or-nothing `dao.count() > 0`
        // gate. The old gate meant new seed rows (e.g. `zig_jade` in C.2 PR 2 and the 3
        // milestone cosmetic ids in C.2 PR 3+) would never land on already-installed
        // devices without a data clear, because `count() > 0` is true the moment the
        // first install ran `ensureSeedData` against the pre-`zig_jade` catalogue.
        //
        // The new shape:
        //  - Fresh install \u2192 existingIds empty, every SEED_COSMETICS row inserted.
        //  - Upgrade from an older catalogue \u2192 only the rows whose `cosmeticId` is not
        //    yet in the DAO are upserted. Existing rows (including any player state
        //    like `isOwned` / `isEquipped`) are left untouched.
        //  - Repeat call in steady state \u2192 `missing` is empty, no-op.
        //
        // The primary key on [CosmeticEntity] is `id` (auto-gen), not `cosmeticId`, so
        // Room's `upsert` on a seed row with `id = 0` would create a new row rather
        // than replace an existing one with a matching `cosmeticId`. The explicit filter
        // sidesteps that entirely by not passing already-present rows to the DAO.
        val existingIds = dao.observeAll().first().mapTo(HashSet()) { it.cosmeticId }
        val missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }
        if (missing.isNotEmpty()) dao.upsertAll(missing)
    }

    override suspend fun idExists(cosmeticId: String): Boolean {
        // Seed lazily so the check is reliable before the Store screen has ever been
        // opened (e.g. when ClaimMilestone fires from the Missions screen first).
        // ensureSeedData is cheap in steady state — its per-cosmeticId filter produces
        // `missing == emptyList()` once every id is present, so no DAO write happens.
        ensureSeedData()
        return dao.observeAll().first().any { it.cosmeticId == cosmeticId }
    }

    private fun CosmeticEntity.toDomain() = CosmeticItem(
        cosmeticId = cosmeticId,
        category = CosmeticCategory.valueOf(category),
        name = name,
        description = description,
        priceGems = priceGems,
        isOwned = isOwned,
        isEquipped = isEquipped,
        overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId],
    )

    companion object {
        /**
         * Ziggurat-skin color palette registry, keyed by [CosmeticEntity.cosmeticId].
         *
         * Populated in content PRs (C.2 PR 2+) as each cosmetic's visual override ships.
         * When a cosmetic's ID appears here, [toDomain] forwards the color list as
         * [CosmeticItem.overrideColors] so the battle renderer can swap it in for the biome
         * default when equipped (see `GameEngine.init` + `ZigguratEntity.layerColors`).
         *
         * Each entry MUST be exactly 5 Ints (one per ziggurat layer, bottom to top) to match
         * [com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity.DEFAULT_COLORS].
         *
         * Current entries:
         *  - `zig_jade` (C.2 PR 2) — deep jade gradient, first end-to-end cosmetic.
         *  - `lapis_lazuli_skin` (C.2 PR 3) — deep lapis base → bright lapis → pyrite-gold
         *    crown. Resolves the IRON_SOLES `MilestoneReward.Cosmetic` mismatch (C.4),
         *    so after this PR `ClaimMilestone(IRON_SOLES)` returns `Success` instead of
         *    `UnknownCosmetic`.
         *
         * Pending entries: `garden_ziggurat_skin` (MARATHON_WALKER, C.2 PR 3b),
         * `sandals_of_gilgamesh` (GLOBE_TROTTER, C.2 PR 3c).
         */
        private val ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>> = mapOf(
            "zig_jade" to listOf(
                0xFF104E3C.toInt(), // layer 0 (base) — deep jade
                0xFF1A6B52.toInt(),
                0xFF2A8F6E.toInt(),
                0xFF3CAB82.toInt(),
                0xFF54C79A.toInt(), // layer 4 (top) — pale jade highlight
            ),
            "lapis_lazuli_skin" to listOf(
                0xFF1A1F5C.toInt(), // layer 0 (base) — deep lapis
                0xFF2A3880.toInt(),
                0xFF3B4FAB.toInt(),
                0xFF4F68C8.toInt(), // layer 3 — bright lapis
                0xFFD4A84A.toInt(), // layer 4 (top) — pyrite-gold crown (traditional lapis flecks)
            ),
        )

        private val SEED_COSMETICS = listOf(
            CosmeticEntity(cosmeticId = "zig_jade", category = "ZIGGURAT_SKIN", name = "Jade Ziggurat", description = "Deep jade stone with pale highlights", priceGems = 150),
            // Milestone-reward cosmetic (IRON_SOLES). Listed here so `CosmeticRepository.idExists`
            // returns true and `ClaimMilestone(IRON_SOLES)` runs the atomic credit instead of
            // returning `UnknownCosmetic`. Intentionally NOT in StoreScreen.ENABLED_COSMETIC_ID
            // yet — appears as "Coming Soon" in the Store. Primary acquisition path remains
            // the milestone; store pricing is a future UX decision. (C.2 PR 3)
            CosmeticEntity(cosmeticId = "lapis_lazuli_skin", category = "ZIGGURAT_SKIN", name = "Lapis Lazuli Ziggurat Skin", description = "Deep lapis lazuli stone with pyrite-gold flecks", priceGems = 500),
            CosmeticEntity(cosmeticId = "zig_obsidian", category = "ZIGGURAT_SKIN", name = "Obsidian Ziggurat", description = "Dark volcanic stone", priceGems = 100),
            CosmeticEntity(cosmeticId = "zig_crystal", category = "ZIGGURAT_SKIN", name = "Crystal Ziggurat", description = "Translucent crystal layers", priceGems = 200),
            CosmeticEntity(cosmeticId = "zig_golden", category = "ZIGGURAT_SKIN", name = "Golden Ziggurat", description = "Pure gold plating", priceGems = 300),
            CosmeticEntity(cosmeticId = "proj_fire", category = "PROJECTILE_EFFECT", name = "Fire Trails", description = "Blazing projectile trails", priceGems = 150),
            CosmeticEntity(cosmeticId = "proj_lightning", category = "PROJECTILE_EFFECT", name = "Lightning Arcs", description = "Electric projectile arcs", priceGems = 150),
            CosmeticEntity(cosmeticId = "enemy_shadow", category = "ENEMY_SKIN", name = "Shadow Enemies", description = "Dark silhouette enemies", priceGems = 100),
            CosmeticEntity(cosmeticId = "enemy_neon", category = "ENEMY_SKIN", name = "Neon Enemies", description = "Glowing neon outlines", priceGems = 100),
        )
    }
}
