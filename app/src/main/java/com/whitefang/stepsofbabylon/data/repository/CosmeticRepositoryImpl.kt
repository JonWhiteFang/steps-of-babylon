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
        if (dao.count() > 0) return
        dao.upsertAll(SEED_COSMETICS)
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
         * Current entries (C.2 PR 2):
         *  - `zig_jade` — deep jade gradient, first end-to-end cosmetic. Remaining seed rows
         *    ship their palettes in C.2 PR 3+ as content work.
         */
        private val ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>> = mapOf(
            "zig_jade" to listOf(
                0xFF104E3C.toInt(), // layer 0 (base) — deep jade
                0xFF1A6B52.toInt(),
                0xFF2A8F6E.toInt(),
                0xFF3CAB82.toInt(),
                0xFF54C79A.toInt(), // layer 4 (top) — pale jade highlight
            ),
        )

        private val SEED_COSMETICS = listOf(
            CosmeticEntity(cosmeticId = "zig_jade", category = "ZIGGURAT_SKIN", name = "Jade Ziggurat", description = "Deep jade stone with pale highlights", priceGems = 150),
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
