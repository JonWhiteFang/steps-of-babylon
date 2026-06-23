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

class CosmeticRepositoryImpl
    @Inject
    constructor(
        private val dao: CosmeticDao,
    ) : CosmeticRepository {
        override fun observeAll(): Flow<List<CosmeticItem>> =
            dao.observeAll().map { list -> list.mapNotNull { it.toDomainOrNull() } }

        override fun observeOwned(): Flow<List<CosmeticItem>> =
            dao.observeOwned().map { list -> list.mapNotNull { it.toDomainOrNull() } }

        override fun observeEquipped(): Flow<List<CosmeticItem>> =
            dao.observeEquipped().map { list -> list.mapNotNull { it.toDomainOrNull() } }

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
            // #221: one-time cleanup of the dead projectile/enemy-skin cosmetics on already-installed
            // devices. DELETE … WHERE cosmeticId IN (…) is a no-op when none are present, so this is
            // idempotent and cheap in steady state (fresh installs never seeded them).
            dao.deleteByIds(DEAD_COSMETIC_IDS)
        }

        override suspend fun idExists(cosmeticId: String): Boolean {
            // Seed lazily so the check is reliable before the Store screen has ever been
            // opened (e.g. when ClaimMilestone fires from the Missions screen first).
            // ensureSeedData is cheap in steady state — its per-cosmeticId filter produces
            // `missing == emptyList()` once every id is present, so no DAO write happens.
            ensureSeedData()
            return dao.observeAll().first().any { it.cosmeticId == cosmeticId }
        }

        // Resilient mapping (#221): a row whose stored `category` is not a CosmeticCategory value
        // (a legacy/dead row persisted before #221) maps to null and is filtered out, rather than
        // throwing IllegalArgumentException from valueOf. Belt-and-suspenders with the ensureSeedData
        // purge: this also covers the window where observeAll() emits before the purge commits
        // (StoreViewModel.init runs ensureSeedData and observeAll in separate coroutines).
        private fun CosmeticEntity.toDomainOrNull(): CosmeticItem? {
            val cat = CosmeticCategory.entries.find { it.name == category } ?: return null
            return CosmeticItem(
                cosmeticId = cosmeticId,
                category = cat,
                name = name,
                description = description,
                priceGems = priceGems,
                isOwned = isOwned,
                isEquipped = isEquipped,
                overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId],
            )
        }

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
             *    crown. Resolves the IRON_SOLES `MilestoneReward.Cosmetic` mismatch (C.4).
             *  - `garden_ziggurat_skin` (C.2 PR 3b) — terracotta base → cascading foliage →
             *    pale bloom. Hanging Gardens biome-themed. Resolves the MARATHON_WALKER mismatch.
             *  - `sandals_of_gilgamesh` (C.2 PR 3c) — weathered bronze → polished bronze → gold
             *    crown. Heroic / Gilgamesh narrative reframes this as a bronze ziggurat variant
             *    so the existing `ZIGGURAT_SKIN` category + palette pipeline still applies (no
             *    schema change, no new `CosmeticCategory` value). Resolves the GLOBE_TROTTER
             *    mismatch. After this PR, all 6 Milestone entries claim cleanly end-to-end.
             */
            private val ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>> =
                mapOf(
                    "zig_jade" to
                        listOf(
                            0xFF104E3C.toInt(), // layer 0 (base) — deep jade
                            0xFF1A6B52.toInt(),
                            0xFF2A8F6E.toInt(),
                            0xFF3CAB82.toInt(),
                            0xFF54C79A.toInt(), // layer 4 (top) — pale jade highlight
                        ),
                    "lapis_lazuli_skin" to
                        listOf(
                            0xFF1A1F5C.toInt(), // layer 0 (base) — deep lapis
                            0xFF2A3880.toInt(),
                            0xFF3B4FAB.toInt(),
                            0xFF4F68C8.toInt(), // layer 3 — bright lapis
                            0xFFD4A84A.toInt(), // layer 4 (top) — pyrite-gold crown (traditional lapis flecks)
                        ),
                    "garden_ziggurat_skin" to
                        listOf(
                            0xFF8B4726.toInt(), // layer 0 (base) — warm terracotta ziggurat stone
                            0xFFAD7B4C.toInt(), // layer 1 — sun-bleached sandstone
                            0xFF5E7F47.toInt(), // layer 2 — mossy greens (vines begin)
                            0xFF7BA85A.toInt(), // layer 3 — lush foliage
                            0xFFE0C890.toInt(), // layer 4 (top) — pale bloom / garden canopy
                        ),
                    "sandals_of_gilgamesh" to
                        listOf(
                            0xFF3B2A1A.toInt(), // layer 0 (base) — dark weathered bronze
                            0xFF6B4A2A.toInt(), // layer 1 — aged bronze
                            0xFF8B6B42.toInt(), // layer 2 — warm bronze
                            0xFFB89152.toInt(), // layer 3 — polished bronze / brass
                            0xFFE8C068.toInt(), // layer 4 (top) — gold crown (heroic motif)
                        ),
                    "zig_obsidian" to
                        listOf(
                            0xFF1A1A1A.toInt(), // layer 0 (base) — deep obsidian black
                            0xFF2D2D2D.toInt(), // layer 1
                            0xFF3F3F3F.toInt(), // layer 2 (mid)
                            0xFF525252.toInt(), // layer 3
                            0xFF7A6F4D.toInt(), // layer 4 (top) — dim gold-bronze cap
                        ),
                )

            // #221: cosmetics removed because they had no render path (PROJECTILE_EFFECT / ENEMY_SKIN).
            // Purged from already-installed devices by ensureSeedData; never re-seeded.
            private val DEAD_COSMETIC_IDS = listOf("proj_fire", "proj_lightning", "enemy_shadow", "enemy_neon")

            private val SEED_COSMETICS =
                listOf(
                    CosmeticEntity(
                        cosmeticId = "zig_jade",
                        category = "ZIGGURAT_SKIN",
                        name = "Jade Ziggurat",
                        description = "Deep jade stone with pale highlights",
                        priceGems = 150,
                    ),
                    // Milestone-reward cosmetic (IRON_SOLES). Listed here so `CosmeticRepository.idExists`
                    // returns true and `ClaimMilestone(IRON_SOLES)` runs the atomic credit instead of
                    // returning `UnknownCosmetic`. Intentionally NOT in StoreScreen.ENABLED_COSMETIC_IDS
                    // yet — appears as "Coming Soon" in the Store. Primary acquisition path remains
                    // the milestone; store pricing is a future UX decision. (C.2 PR 3)
                    CosmeticEntity(
                        cosmeticId = "lapis_lazuli_skin",
                        category = "ZIGGURAT_SKIN",
                        name = "Lapis Lazuli Ziggurat Skin",
                        description = "Deep lapis lazuli stone with pyrite-gold flecks",
                        priceGems = 500,
                    ),
                    // Milestone-reward cosmetic (MARATHON_WALKER). Hanging Gardens biome-themed —
                    // terracotta ziggurat wrapped in cascading greenery with a pale bloom canopy.
                    // Same rationale as `lapis_lazuli_skin`: milestone acquisition only, not in the
                    // Store allow-list. (C.2 PR 3b)
                    CosmeticEntity(
                        cosmeticId = "garden_ziggurat_skin",
                        category = "ZIGGURAT_SKIN",
                        name = "Garden Ziggurat Skin",
                        description = "Cascading hanging gardens draped over terracotta stone",
                        priceGems = 600,
                    ),
                    // Milestone-reward cosmetic (GLOBE_TROTTER). The id carries footwear semantics
                    // from the milestone narrative ("walking the edges of the world"), but the reward
                    // is implemented as a ziggurat skin: a bronze-themed Ziggurat of Gilgamesh variant.
                    // Keeps the existing `ZIGGURAT_SKIN` category + ZIGGURAT_COLOR_LOOKUP pipeline
                    // intact (no schema change, no new `CosmeticCategory` value). If a future
                    // milestone introduces multiple player-avatar cosmetics, revisit adding a
                    // `PLAYER_AVATAR` category then. For one instance, the reframe is cheaper. (C.2 PR 3c)
                    CosmeticEntity(
                        cosmeticId = "sandals_of_gilgamesh",
                        category = "ZIGGURAT_SKIN",
                        name = "Sandals of Gilgamesh",
                        description = "Bronze ziggurat in honour of Gilgamesh, whose sandals walked the edges of the world",
                        priceGems = 500,
                    ),
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
        }
    }
