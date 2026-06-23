# Remove Dead Projectile/Enemy-Skin Cosmetics (#221) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the 4 seeded projectile/enemy-skin cosmetics (`proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) and the 2 unused `CosmeticCategory` values that have no render path, closing the "live trap" (issue #221 / FEAT-1) — safely for already-installed devices.

**Architecture:** Reduce `CosmeticCategory` to `{ ZIGGURAT_SKIN }`. Drop the 4 dead seed rows. Make existing-device data safe two ways (both load-bearing): (1) a runtime `deleteByIds` purge in `ensureSeedData`, and (2) a resilient `toDomainOrNull` mapping that filters rows whose stored String category no longer parses, so `CosmeticCategory.valueOf` can never crash. No Room schema migration (data-only delete; the `cosmetics` table schema is unchanged).

**Tech Stack:** Kotlin, Clean Architecture (`domain/` Android-free), Room (`CosmeticDao`/`CosmeticEntity`), Hilt, JUnit Jupiter pure-JVM tests via `FakeCosmeticDao`. Build/test with `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-23-remove-dead-cosmetics-221-design.md` (adversarial-review-passed: 6 minor survivors folded into the test plan below).

**Branch:** `feat/221-remove-dead-cosmetics` (already created; spec already committed).

---

## File Structure

| File | Responsibility / change |
|---|---|
| `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/CosmeticCategory.kt` | Enum → `{ ZIGGURAT_SKIN }`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/data/local/CosmeticDao.kt` | Add `deleteByIds(ids: List<String>)` `@Query`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeCosmeticDao.kt` | Implement `deleteByIds`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` | Drop 4 dead rows from `SEED_COSMETICS`; add `DEAD_COSMETIC_IDS` + purge in `ensureSeedData`; replace `toDomain` with resilient `toDomainOrNull` + `mapNotNull`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt` | Remove the 2 dead `labelRes()` branches. |
| `app/src/main/res/values/strings.xml` | Remove `cosmetic_cat_projectile_effect` + `cosmetic_cat_enemy_skin`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt` | Update 3 count assertions + fixtures; add purge+resilience test. |

No schema/version bump. No new dependency. The only DI-visible change is the enum reduction (compiler-enforced).

---

## Task 1: Add the resilient mapping + purge (TDD, repo layer)

This task is ordered first because it makes the catalogue crash-safe *before* the enum shrinks, and the new test drives the `deleteByIds`/`toDomainOrNull` additions.

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/local/CosmeticDao.kt`
- Modify: `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeCosmeticDao.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `CosmeticRepositoryImplTest.kt` (anywhere inside the class, e.g. after the existing `V1X14 - zig_obsidian …` test). It pre-seeds a row with a category string that will no longer be a valid enum value and a dead id, then asserts the resilient mapping hides it and the purge removes it:

```kotlin
    @Test
    fun `R221 - dead-category rows are filtered from observeAll and purged by ensureSeedData`() =
        runTest {
            val dao = FakeCosmeticDao()
            // Simulate an already-installed device: a dead projectile cosmetic persisted with a
            // category string that is no longer a CosmeticCategory value after #221.
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

            // (a) Resilient mapping: observeAll never exposes the unparseable-category row,
            // even before ensureSeedData runs (covers the StoreViewModel init race window).
            assertTrue(
                repo.observeAll().first().none { it.cosmeticId == "proj_fire" },
                "a row whose category no longer parses must be filtered out of the domain list, not crash",
            )

            // (b) Purge: ensureSeedData deletes the dead row for good.
            repo.ensureSeedData()
            assertTrue(
                dao.observeAll().first().none { it.cosmeticId == "proj_fire" },
                "ensureSeedData must purge known dead cosmetic ids from the DB",
            )
        }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: FAIL — compile error (`deleteByIds` unresolved on `FakeCosmeticDao`/`CosmeticDao`) and/or the assertions fail (today `toDomain` would throw `IllegalArgumentException` on `valueOf("PROJECTILE_EFFECT")` once the enum value is gone — but at this point the enum still has it, so the row is *not* filtered and assertion (a) fails). Either way: red.

- [ ] **Step 3: Add `deleteByIds` to the DAO**

In `CosmeticDao.kt`, add (after the existing `unequipCategory` query):

```kotlin
    @Query("DELETE FROM cosmetics WHERE cosmeticId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
```

- [ ] **Step 4: Implement `deleteByIds` in the fake**

In `FakeCosmeticDao.kt`, add (after `unequipCategory`):

```kotlin
    override suspend fun deleteByIds(ids: List<String>) {
        rows.update { list -> list.filterNot { it.cosmeticId in ids } }
    }
```

- [ ] **Step 5: Add the resilient mapping + purge to the repo**

In `CosmeticRepositoryImpl.kt`:

(a) Replace the three observe mappings. Current:

```kotlin
        override fun observeAll(): Flow<List<CosmeticItem>> =
            dao.observeAll().map { list ->
                list.map { it.toDomain() }
            }

        override fun observeOwned(): Flow<List<CosmeticItem>> =
            dao.observeOwned().map { list -> list.map { it.toDomain() } }

        override fun observeEquipped(): Flow<List<CosmeticItem>> =
            dao.observeEquipped().map { list -> list.map { it.toDomain() } }
```

becomes:

```kotlin
        override fun observeAll(): Flow<List<CosmeticItem>> =
            dao.observeAll().map { list -> list.mapNotNull { it.toDomainOrNull() } }

        override fun observeOwned(): Flow<List<CosmeticItem>> =
            dao.observeOwned().map { list -> list.mapNotNull { it.toDomainOrNull() } }

        override fun observeEquipped(): Flow<List<CosmeticItem>> =
            dao.observeEquipped().map { list -> list.mapNotNull { it.toDomainOrNull() } }
```

(b) Replace the `toDomain` helper. Current:

```kotlin
        private fun CosmeticEntity.toDomain() =
            CosmeticItem(
                cosmeticId = cosmeticId,
                category = CosmeticCategory.valueOf(category),
                name = name,
                description = description,
                priceGems = priceGems,
                isOwned = isOwned,
                isEquipped = isEquipped,
                overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId],
            )
```

becomes (exception-free — an unparseable/legacy category yields `null`, which `mapNotNull` drops):

```kotlin
        // Resilient mapping (#221): a row whose stored `category` is no longer a CosmeticCategory
        // value (a legacy/dead row persisted before #221) maps to null and is filtered out, rather
        // than throwing IllegalArgumentException from valueOf. Belt-and-suspenders with the
        // ensureSeedData purge: this covers the window where observeAll() emits before the purge
        // commits (StoreViewModel.init runs ensureSeedData and observeAll in separate coroutines).
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
```

(c) Add the purge to `ensureSeedData`. Current tail of the method:

```kotlin
            val existingIds = dao.observeAll().first().mapTo(HashSet()) { it.cosmeticId }
            val missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }
            if (missing.isNotEmpty()) dao.upsertAll(missing)
        }
```

becomes:

```kotlin
            val existingIds = dao.observeAll().first().mapTo(HashSet()) { it.cosmeticId }
            val missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }
            if (missing.isNotEmpty()) dao.upsertAll(missing)
            // #221: one-time cleanup of the dead projectile/enemy-skin cosmetics on already-installed
            // devices. DELETE … WHERE cosmeticId IN (…) is a no-op when none are present, so this is
            // idempotent and cheap in steady state (fresh installs never seeded them).
            dao.deleteByIds(DEAD_COSMETIC_IDS)
        }
```

(d) Add the `DEAD_COSMETIC_IDS` constant inside the `companion object` (e.g. just above `SEED_COSMETICS`):

```kotlin
            // #221: cosmetics removed because they had no render path (PROJECTILE_EFFECT / ENEMY_SKIN).
            // Purged from already-installed devices by ensureSeedData; never re-seeded.
            private val DEAD_COSMETIC_IDS = listOf("proj_fire", "proj_lightning", "enemy_shadow", "enemy_neon")
```

- [ ] **Step 6: Run the new test to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: the new `R221 …` test PASSES. (Other tests in this class may still pass here because the dead rows are still in `SEED_COSMETICS` until Task 2 — that's fine; this step only gates the new test green.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/local/CosmeticDao.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeCosmeticDao.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt
git commit -m "feat(#221): resilient cosmetic mapping + deleteByIds purge for dead rows"
```

---

## Task 2: Drop the 4 dead seed rows + update the existing repo tests

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt`

- [ ] **Step 1: Remove the 4 dead rows from `SEED_COSMETICS`**

In `CosmeticRepositoryImpl.kt`, delete these 4 `CosmeticEntity(...)` blocks from the `SEED_COSMETICS` list (the `proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon` entries). After this, `SEED_COSMETICS` has exactly 7 `ZIGGURAT_SKIN` rows: `zig_jade`, `lapis_lazuli_skin`, `garden_ziggurat_skin`, `sandals_of_gilgamesh`, `zig_obsidian`, `zig_crystal`, `zig_golden`.

- [ ] **Step 2: Update the idempotency test's count + message**

In `CosmeticRepositoryImplTest.kt`, in `C2PR2 - ensureSeedData is idempotent on repeat call` (~line 222), change the `assertEquals(11, countAfterFirst, …)`:

```kotlin
            assertEquals(
                7,
                countAfterFirst,
                "7 ziggurat seed rows (zig_jade + lapis_lazuli_skin + garden_ziggurat_skin + sandals_of_gilgamesh + zig_obsidian + zig_crystal + zig_golden)",
            )
```

- [ ] **Step 3: Update the `otherIds` regression list + its stale comment**

In `C2PR2 - other seeded ziggurat cosmetics have null overrideColors pending content PRs` (~line 170), change the `otherIds` list to drop the 4 dead ids:

```kotlin
            val otherIds = listOf("zig_crystal", "zig_golden")
```

And update the comment block above it (currently mentions `PROJECTILE_EFFECT, ENEMY_SKIN`) to:

```kotlin
            // Regression guard: only the palette-shipping ziggurat cosmetics (zig_jade,
            // lapis_lazuli_skin, garden_ziggurat_skin, sandals_of_gilgamesh, zig_obsidian) carry
            // overrideColors. The remaining seeded ZIGGURAT_SKIN rows (zig_crystal, zig_golden) must
            // continue to return null overrideColors so the renderer falls through to the biome
            // default (and the StoreScreen keeps them under the "Coming Soon" guard).
```

(Note: `zig_obsidian` got a palette in V1X-14, so it is intentionally NOT in `otherIds`.)

- [ ] **Step 4: Update the legacy-upgrade test**

In `ensureSeedData inserts newly-added rows on partial catalogue upgrade` (~line 248):

(a) In `legacySeed`, **delete the 4 dead `CosmeticEntity` rows** (`proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`), leaving only the 3 legacy ziggurat rows (`zig_obsidian`, `zig_crystal`, `zig_golden`).

(b) Change the baseline assertion (was `assertEquals(7, dao.count(), "baseline: legacy catalogue has 7 rows")`) to:

```kotlin
            assertEquals(3, dao.count(), "baseline: legacy catalogue has 3 ziggurat rows")
```

(c) Change the post-`ensureSeedData` assertion (was `assertEquals(11, dao.count(), …)`) to:

```kotlin
            assertEquals(
                7,
                dao.count(),
                "after ensureSeedData: zig_jade + lapis_lazuli_skin + garden_ziggurat_skin + sandals_of_gilgamesh added, legacy 3 ziggurat preserved",
            )
```

(d) The survivor loop `for (legacyId in legacySeed.map { it.cosmeticId }) { assertNotNull(…) }` needs **no edit** — it iterates `legacySeed`, which now contains only the 3 ziggurat ids, so it no longer (wrongly) asserts the dead ids survive.

- [ ] **Step 5: Update the player-state-preservation test's count + comment**

In `ensureSeedData preserves player state on existing rows (isOwned, isEquipped)` (~line 352), change the comment + assertion (was `// All 11 seed rows now present (10 new + the pre-existing zig_jade preserved).` then `assertEquals(11, dao.count())`):

```kotlin
            // All 7 seed rows now present (6 new + the pre-existing zig_jade preserved).
            assertEquals(7, dao.count())
```

- [ ] **Step 6: Run the repo tests to verify they pass**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: PASS (all tests in the class, including the Task-1 `R221` test and the updated counts).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt
git commit -m "feat(#221): drop dead projectile/enemy-skin seed rows + update repo tests"
```

---

## Task 3: Reduce the enum + remove the dead labels/strings

This is ordered last because it's the change that would crash an un-migrated catalogue — Tasks 1+2 have already made the data safe (resilient mapping + purge + no longer seeding the rows), so reducing the enum is now safe.

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/CosmeticCategory.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Reduce the enum**

In `CosmeticCategory.kt`, change:

```kotlin
enum class CosmeticCategory { ZIGGURAT_SKIN, PROJECTILE_EFFECT, ENEMY_SKIN }
```

to:

```kotlin
enum class CosmeticCategory { ZIGGURAT_SKIN }
```

- [ ] **Step 2: Remove the dead `labelRes()` branches**

In `EnumLabels.kt`, the `CosmeticCategory.labelRes()` `when` currently has 3 branches. Remove the two dead ones so it reads:

```kotlin
@StringRes fun CosmeticCategory.labelRes(): Int =
    when (this) {
        CosmeticCategory.ZIGGURAT_SKIN -> R.string.cosmetic_cat_ziggurat_skin
    }
```

(The `when` stays exhaustive — the compiler enforces this; a stray reference to a removed value would now fail to compile, which is the safety net.)

- [ ] **Step 3: Remove the 2 unused string resources**

In `app/src/main/res/values/strings.xml`, delete these two lines (keep `cosmetic_cat_ziggurat_skin`):

```xml
    <string name="cosmetic_cat_projectile_effect">Projectile Effect</string>
    <string name="cosmetic_cat_enemy_skin">Enemy Skin</string>
```

- [ ] **Step 4: Compile the app module (proves the enum reduction breaks nothing)**

Run: `./run-gradle.sh :app:compileDebugKotlin > /tmp/221-compile.log 2>&1 && tail -n 5 /tmp/221-compile.log`
Expected: BUILD SUCCESSFUL. (If any other site referenced `PROJECTILE_EFFECT`/`ENEMY_SKIN`, this fails here — none do, verified in the spec, but the compiler is the guarantee.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/model/CosmeticCategory.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(#221): reduce CosmeticCategory to ZIGGURAT_SKIN; drop dead labels/strings"
```

---

## Task 4: Full verification + lint

**Files:** none (verification only).

- [ ] **Step 1: Run the full JVM unit suite**

Run: `./run-gradle.sh :app:testDebugUnitTest > /tmp/221-test.log 2>&1 && tail -n 6 /tmp/221-test.log`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Confirm the result count from the XML reports (source of truth)**

Run:
```bash
total=0; fails=0; errs=0
for f in $(fd -e xml . app/build/test-results/testDebugUnitTest); do
  t=$(rg -o 'tests="[0-9]+"' "$f" | head -1 | rg -o '[0-9]+')
  fl=$(rg -o 'failures="[0-9]+"' "$f" | head -1 | rg -o '[0-9]+')
  er=$(rg -o 'errors="[0-9]+"' "$f" | head -1 | rg -o '[0-9]+')
  total=$((total + ${t:-0})); fails=$((fails + ${fl:-0})); errs=$((errs + ${er:-0}))
done
echo "TOTAL tests=$total failures=$fails errors=$errs"
```
Expected: `failures=0 errors=0`; total ≈ 1276 (1275 + 1 new test). Record the exact number for Task 5.

- [ ] **Step 3: assembleDebug (full module wiring)**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/221-assemble.log 2>&1 && tail -n 4 /tmp/221-assemble.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: detekt + ktlint (CI-gated)**

Run: `./run-gradle.sh :app:detekt > /tmp/221-detekt.log 2>&1 && tail -n 4 /tmp/221-detekt.log`
Then: `./lint-kotlin.sh` (use `./lint-kotlin.sh --format` to auto-fix; if it changes files, re-stage + amend the relevant commit).
Expected: both clean.

---

## Task 5: Sync current-state docs + STATE/RUN_LOG (PR Task-List Convention)

> Per CLAUDE.md: sync current-state docs FIRST, then STATE/RUN_LOG, then the final commit. Touch a doc only if this PR invalidates it. No schema change → do NOT touch `docs/database-schema.md`.

**Files:**
- Modify: `CLAUDE.md` (headline test count — only if it changed)
- Modify: `CHANGELOG.md` (`[Unreleased]` entry)
- Modify: `docs/steering/source-files.md` (CosmeticRepositoryImpl / CosmeticDao / CosmeticCategory entries)
- Modify: `docs/agent/STATE.md` (snapshot)
- Append: `docs/agent/RUN_LOG.md` (session entry)

- [ ] **Step 1: Update the CLAUDE.md headline test count**

In `CLAUDE.md` → Testing section, update `**Headline count: 1275 JVM tests + 9 instrumented tests.**` to the count from Task 4 Step 2 (instrumented unchanged at 9).

- [ ] **Step 2: Add a CHANGELOG `[Unreleased]` entry**

Under `## [Unreleased]` in `CHANGELOG.md`, add (match the file's entry style):

```markdown
### Removed — Dead projectile/enemy-skin cosmetics (#221 / FEAT-1)

Removed the 4 seeded cosmetics that had no render path (`proj_fire`, `proj_lightning`, `enemy_shadow`,
`enemy_neon`) and the 2 unused `CosmeticCategory` values (`PROJECTILE_EFFECT`, `ENEMY_SKIN`) — closing the
"live trap" flagged by the 2026-06-17 audit (FEAT-1): a cosmetic that could be enabled for sale yet
render nothing. Only `ZIGGURAT_SKIN` (the one category with working `overrideColors` wiring) remains.
Already-installed devices are cleaned up safely at next launch: a new `CosmeticDao.deleteByIds` purge in
`ensureSeedData` removes the dead rows, and `CosmeticRepositoryImpl` now maps via a resilient
`toDomainOrNull` that filters any row whose stored category no longer parses (so `CosmeticCategory.valueOf`
can never crash the catalogue). No Room schema change (data-only delete). +1 JVM test.
```

If the `[Unreleased]` block has a running test-count line, update it.

- [ ] **Step 3: Update source-files.md**

In `docs/steering/source-files.md`, update the `CosmeticRepositoryImpl.kt`, `CosmeticDao.kt`, and `CosmeticCategory.kt` entries to reflect: enum reduced to `ZIGGURAT_SKIN`; `deleteByIds` purge + resilient `toDomainOrNull` mapping; 7 seed rows (was 11). Match the existing format.

- [ ] **Step 4: Update STATE.md**

In `docs/agent/STATE.md`: add a CURRENT bullet for #221 (shipped to `[Unreleased]`), demote the prior CURRENT to "Previous objective", update the test-count line, and remove #221 from the open-issues mention if present. Keep to one page.

- [ ] **Step 5: Append a RUN_LOG.md entry**

Append a dated `## 2026-06-23 — #221 …` entry summarizing: issue, spec→adversarial-review (13→6 survivors)→plan→TDD, files touched, the existing-device data-safety approach (purge + resilient mapping, no migration), test delta, no schema/economy change.

- [ ] **Step 6: Commit the docs**

```bash
git add CLAUDE.md CHANGELOG.md docs/steering/source-files.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#221): sync current-state docs + STATE/RUN_LOG for dead-cosmetic removal"
```

---

## Task 6: Open the PR

**Files:** none.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/221-remove-dead-cosmetics
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(#221): remove dead projectile/enemy-skin cosmetics (FEAT-1)" --body "$(cat <<'EOF'
Closes #221 (FEAT-1).

## What
- Removed the 4 seeded cosmetics with no render path (`proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) and the 2 unused `CosmeticCategory` values (`PROJECTILE_EFFECT`, `ENEMY_SKIN`). Only `ZIGGURAT_SKIN` remains.
- **Existing-device safety (belt-and-suspenders):** new `CosmeticDao.deleteByIds` purge in `ensureSeedData` + resilient `CosmeticRepositoryImpl.toDomainOrNull` that filters rows whose stored String category no longer parses (so `CosmeticCategory.valueOf` can't crash). The resilient mapping also covers the `StoreViewModel.init` race (purge vs `observeAll` Flow in separate coroutines).
- Removed the 2 now-unused `labelRes()` branches + string resources.

## Why
2026-06-17 complete-app review §4 FEAT-1 — un-renderable cosmetics are dead data + a "live trap" (could be enabled for sale and do nothing).

## Scope
No Room schema migration (data-only delete; table schema unchanged). No render-path implementation. No new `CosmeticCategory`. No economy change (the 4 ids were never purchasable).

## Tests
- New `R221` repo test: a persisted dead-category row is filtered from `observeAll` and purged by `ensureSeedData`.
- Updated `CosmeticRepositoryImplTest` seed counts (11→7) + fixtures.
- Full suite green: <N> JVM tests, 0 failures. detekt + ktlint clean. assembleDebug green.

## Process
Spec → Adversarial Review Gate (13 findings → 6 minor confirmed/folded, 4 refuted… see spec) → plan → TDD. Spec: `docs/superpowers/specs/2026-06-23-remove-dead-cosmetics-221-design.md`.
EOF
)"
```

Replace `<N>` with the actual count.

---

## Self-review notes (for the executor)

- **Spec coverage:** enum reduction → Task 3; drop seed rows → Task 2 Step 1; `deleteByIds` purge → Task 1 Steps 3-5; resilient `toDomainOrNull` → Task 1 Step 5; labels/strings → Task 3 Steps 2-3; all 3 count-assertion sites + survivor loop + `otherIds` + stale comment → Task 2 Steps 2-5; new purge/resilience test → Task 1 Step 1. Every spec section maps to a task.
- **Ordering rationale:** Task 1 (safety net) → Task 2 (stop seeding) → Task 3 (reduce enum, now crash-safe). Reducing the enum before Task 1 would make the un-purged catalogue throw; this order keeps every intermediate commit green.
- **Type consistency:** `toDomainOrNull(): CosmeticItem?` + `mapNotNull` used consistently (Task 1 Step 5). `deleteByIds(ids: List<String>)` signature identical across DAO (Step 3), fake (Step 4), and call site (Step 5). `DEAD_COSMETIC_IDS` defined once (Step 5d), referenced in `ensureSeedData` (Step 5c). `CosmeticItem`/`CosmeticEntity` constructor args match the real data classes (`priceGems` is the entity's existing int literal style).
- **Verify-before-assert:** Task 4 Step 2 reads the result XMLs for the true count rather than trusting a log tail.
```
