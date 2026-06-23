# Remove Dead Projectile/Enemy-Skin Cosmetics (#221) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the 4 seeded projectile/enemy-skin cosmetics (`proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) and the 2 unused `CosmeticCategory` values that have no render path, closing the "live trap" (issue #221 / FEAT-1) — safely for already-installed devices.

**Architecture:** Reduce `CosmeticCategory` to `{ ZIGGURAT_SKIN }`. Drop the 4 dead seed rows. Make existing-device data safe two ways (both load-bearing): (1) a runtime `deleteByIds` purge in `ensureSeedData`, and (2) a resilient `toDomainOrNull` mapping that filters rows whose stored String category no longer parses, so `CosmeticCategory.valueOf` can never crash. No Room schema migration (data-only delete; the `cosmetics` table schema is unchanged).

**Tech Stack:** Kotlin, Clean Architecture (`domain/` Android-free), Room (`CosmeticDao`/`CosmeticEntity`), Hilt, JUnit Jupiter pure-JVM tests via `FakeCosmeticDao`. Build/test with `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-23-remove-dead-cosmetics-221-design.md` (adversarial-review-passed).

**Branch:** `feat/221-remove-dead-cosmetics` (already created; spec already committed).

---

## Task ordering rationale (read first)

The **enum reduction, seed-row removal, purge, and all the test updates are one atomic functional change** — they must land in a single commit, because the runtime purge in `ensureSeedData` changes the seeded count (11→7) the instant it is added, and the resilient-mapping assertions only become observable once the enum no longer contains the dead categories. Splitting them across commits would leave an intermediate commit red. So:

- **Task 1** is a *behavior-preserving* refactor: introduce the resilient `toDomainOrNull` mapping (while the enum still has all 3 values, this drops nothing for valid rows, so every existing test stays green). It is TDD-driven by a test that uses a **genuinely-unparseable** category token, so it proves the resilience contract independent of the enum reduction.
- **Task 2** is the atomic removal (purge call + `deleteByIds` DAO/fake + drop the 4 seed rows + reduce the enum + drop labels/strings + update every affected test + add the purge test) — one green commit.

Both tasks end green; every other task (verify/docs/PR) is non-code.

---

## File Structure

| File | Responsibility / change |
|---|---|
| `app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` | T1: `toDomain`→resilient `toDomainOrNull` + `mapNotNull`. T2: drop 4 dead rows from `SEED_COSMETICS`; add `DEAD_COSMETIC_IDS` + purge in `ensureSeedData`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/data/local/CosmeticDao.kt` | T2: add `deleteByIds(ids: List<String>)` `@Query`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeCosmeticDao.kt` | T2: implement `deleteByIds`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/CosmeticCategory.kt` | T2: enum → `{ ZIGGURAT_SKIN }`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt` | T2: remove the 2 dead `labelRes()` branches. |
| `app/src/main/res/values/strings.xml` | T2: remove `cosmetic_cat_projectile_effect` + `cosmetic_cat_enemy_skin`. |
| `app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt` | T1: add resilience test. T2: add purge test; update 3 count assertions + fixtures. |

No schema/version bump. No new dependency. The only DI-visible change is the enum reduction (compiler-enforced).

---

## Task 1: Resilient `toDomainOrNull` mapping (behavior-preserving, TDD)

Introduce exception-free mapping so a row whose stored `category` doesn't parse is filtered out instead of crashing `valueOf`. While the enum still has all 3 values this changes nothing for the real seed rows — every existing test stays green. The new test drives it with a category token that is **not** any `CosmeticCategory` value (so it exercises the filter regardless of the later enum reduction).

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `CosmeticRepositoryImplTest.kt` (inside the class, e.g. after the `V1X14 - zig_obsidian …` test):

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: FAIL — `repo.observeAll().first()` throws `IllegalArgumentException` from `CosmeticCategory.valueOf("LEGACY_UNKNOWN_CATEGORY")` in the current `toDomain` (the row is mapped eagerly), so the new test errors out red.

- [ ] **Step 3: Replace the observe mappings + `toDomain` with resilient `toDomainOrNull`**

In `CosmeticRepositoryImpl.kt`:

(a) The three observe methods. Current:

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

(b) The helper. Current:

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

becomes (exception-free; an unparseable category yields `null`, which `mapNotNull` drops):

```kotlin
        // Resilient mapping (#221): a row whose stored `category` is not a CosmeticCategory value
        // (a legacy/dead row persisted before #221) maps to null and is filtered out, rather than
        // throwing IllegalArgumentException from valueOf. Belt-and-suspenders with the ensureSeedData
        // purge (Task 2): this also covers the window where observeAll() emits before the purge
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

- [ ] **Step 4: Run the tests to verify green**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: PASS — the new test is green (the `legacy_dead` row is filtered), and **every existing test in the class stays green** (while the enum still has all 3 values, `entries.find` parses every real seed row, so `mapNotNull` drops nothing — behavior-identical to the old `toDomain`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt
git commit -m "refactor(#221): resilient toDomainOrNull cosmetic mapping (filters unparseable category)"
```

---

## Task 2: Atomic removal — purge + drop rows + reduce enum + labels/strings + test updates

Everything here lands in **one commit** so no intermediate state is red. Order within the task: write the purge test (red) → add `deleteByIds` (DAO + fake) → add the purge call + drop seed rows → update existing tests → reduce enum + drop labels/strings → all green → commit.

**Files:**
- Modify: `CosmeticDao.kt`, `FakeCosmeticDao.kt`, `CosmeticRepositoryImpl.kt`, `CosmeticCategory.kt`, `EnumLabels.kt`, `strings.xml`
- Test: `CosmeticRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing purge test**

Add to `CosmeticRepositoryImplTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: FAIL — `deleteByIds` does not exist yet, so this won't compile / the row is never purged. (No `valueOf` crash here: this test asserts on `dao.observeAll()` raw entities, and `"PROJECTILE_EFFECT"` is still a valid enum value at this point anyway.)

- [ ] **Step 3: Add `deleteByIds` to the DAO**

In `CosmeticDao.kt`, after the existing `unequipCategory` query:

```kotlin
    @Query("DELETE FROM cosmetics WHERE cosmeticId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
```

- [ ] **Step 4: Implement `deleteByIds` in the fake**

In `FakeCosmeticDao.kt`, after `unequipCategory`:

```kotlin
    override suspend fun deleteByIds(ids: List<String>) {
        rows.update { list -> list.filterNot { it.cosmeticId in ids } }
    }
```

- [ ] **Step 5: Add `DEAD_COSMETIC_IDS` + the purge call, and drop the 4 dead seed rows**

In `CosmeticRepositoryImpl.kt`:

(a) Add the constant inside the `companion object` (e.g. just above `SEED_COSMETICS`):

```kotlin
            // #221: cosmetics removed because they had no render path (PROJECTILE_EFFECT / ENEMY_SKIN).
            // Purged from already-installed devices by ensureSeedData; never re-seeded.
            private val DEAD_COSMETIC_IDS = listOf("proj_fire", "proj_lightning", "enemy_shadow", "enemy_neon")
```

(b) Append the purge to `ensureSeedData`. Current tail:

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

(c) Delete the 4 dead `CosmeticEntity(...)` blocks from `SEED_COSMETICS` (`proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`). After this, `SEED_COSMETICS` has exactly 7 `ZIGGURAT_SKIN` rows: `zig_jade`, `lapis_lazuli_skin`, `garden_ziggurat_skin`, `sandals_of_gilgamesh`, `zig_obsidian`, `zig_crystal`, `zig_golden`.

- [ ] **Step 6: Update the idempotency test's count + message**

In `C2PR2 - ensureSeedData is idempotent on repeat call`, change the `assertEquals(11, countAfterFirst, …)` to:

```kotlin
            assertEquals(
                7,
                countAfterFirst,
                "7 ziggurat seed rows (zig_jade + lapis_lazuli_skin + garden_ziggurat_skin + sandals_of_gilgamesh + zig_obsidian + zig_crystal + zig_golden)",
            )
```

- [ ] **Step 7: Update the `otherIds` regression test (list + comment)**

In `C2PR2 - other seeded ziggurat cosmetics have null overrideColors pending content PRs`, replace the comment block and the `otherIds` list. The current comment is:

```kotlin
            // Regression guard: only the 4 palette-shipping cosmetics (C.2 PR 2 zig_jade,
            // PR 3 lapis_lazuli_skin, PR 3b garden_ziggurat_skin, PR 3c sandals_of_gilgamesh)
            // ship palettes. Remaining seeded ZIGGURAT_SKIN rows (zig_obsidian, zig_crystal,
            // zig_golden) must continue to return null overrideColors so the renderer falls
            // through to the biome default (and the StoreScreen keeps them under the R2-11
            // "Coming Soon" guard). The other category seeds (PROJECTILE_EFFECT, ENEMY_SKIN)
            // are off the ZIGGURAT_COLOR_LOOKUP entirely — also null.
            val dao = FakeCosmeticDao()
            val repo = CosmeticRepositoryImpl(dao)
            repo.ensureSeedData()

            val allItems = repo.observeAll().first()
            val otherIds =
                listOf("zig_crystal", "zig_golden", "proj_fire", "proj_lightning", "enemy_shadow", "enemy_neon")
```

Replace those lines with:

```kotlin
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
```

- [ ] **Step 8: Update the legacy-upgrade test (`legacySeed`, baseline, count)**

In `ensureSeedData inserts newly-added rows on partial catalogue upgrade`:

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

(d) The survivor loop `for (legacyId in legacySeed.map { it.cosmeticId }) { assertNotNull(…) }` needs **no edit** — it iterates `legacySeed`, which now holds only the 3 ziggurat ids, so it no longer asserts the dead ids survive.

- [ ] **Step 9: Update the player-state-preservation test's count + comment**

In `ensureSeedData preserves player state on existing rows (isOwned, isEquipped)`, change the comment + assertion (was `// All 11 seed rows now present (10 new + the pre-existing zig_jade preserved).` then `assertEquals(11, dao.count())`):

```kotlin
            // All 7 seed rows now present (6 new + the pre-existing zig_jade preserved).
            assertEquals(7, dao.count())
```

- [ ] **Step 10: Reduce the enum**

In `CosmeticCategory.kt`, change:

```kotlin
enum class CosmeticCategory { ZIGGURAT_SKIN, PROJECTILE_EFFECT, ENEMY_SKIN }
```

to:

```kotlin
enum class CosmeticCategory { ZIGGURAT_SKIN }
```

- [ ] **Step 11: Remove the dead `labelRes()` branches**

In `EnumLabels.kt`, the `CosmeticCategory.labelRes()` `when` becomes:

```kotlin
@StringRes fun CosmeticCategory.labelRes(): Int =
    when (this) {
        CosmeticCategory.ZIGGURAT_SKIN -> R.string.cosmetic_cat_ziggurat_skin
    }
```

- [ ] **Step 12: Remove the 2 unused string resources**

In `app/src/main/res/values/strings.xml`, delete these two lines (keep `cosmetic_cat_ziggurat_skin`):

```xml
    <string name="cosmetic_cat_projectile_effect">Projectile Effect</string>
    <string name="cosmetic_cat_enemy_skin">Enemy Skin</string>
```

- [ ] **Step 13: Run the repo test class + compile to verify green**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.repository.CosmeticRepositoryImplTest"`
Expected: PASS (both new `R221` tests + all updated counts).
Then: `./run-gradle.sh :app:compileDebugKotlin > /tmp/221-compile.log 2>&1 && tail -n 5 /tmp/221-compile.log`
Expected: BUILD SUCCESSFUL — proves no other site referenced the removed enum values (the `when` is exhaustive; the compiler is the guarantee).

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/local/CosmeticDao.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeCosmeticDao.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/domain/model/CosmeticCategory.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/EnumLabels.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImplTest.kt
git commit -m "feat(#221): remove dead projectile/enemy-skin cosmetics + categories"
```

---

## Task 3: Full verification + lint

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
Expected: `failures=0 errors=0`; total ≈ 1277 (1275 + 2 new tests). Record the exact number for Task 4.

- [ ] **Step 3: assembleDebug (full module wiring)**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/221-assemble.log 2>&1 && tail -n 4 /tmp/221-assemble.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: detekt + ktlint (CI-gated)**

Run: `./run-gradle.sh :app:detekt > /tmp/221-detekt.log 2>&1 && tail -n 4 /tmp/221-detekt.log`
Then: `./lint-kotlin.sh` (use `./lint-kotlin.sh --format` to auto-fix; if it changes files, re-stage + amend the relevant commit).
Expected: both clean.

---

## Task 4: Sync current-state docs + STATE/RUN_LOG (PR Task-List Convention)

> Per CLAUDE.md: sync current-state docs FIRST, then STATE/RUN_LOG, then the final commit. Touch a doc only if this PR invalidates it. No schema change → do NOT touch `docs/database-schema.md`.

**Files:**
- Modify: `CLAUDE.md` (headline test count — only if it changed)
- Modify: `CHANGELOG.md` (`[Unreleased]` entry)
- Modify: `docs/steering/source-files.md` (CosmeticRepositoryImpl / CosmeticDao / CosmeticCategory entries)
- Modify: `docs/agent/STATE.md` (snapshot)
- Append: `docs/agent/RUN_LOG.md` (session entry)

- [ ] **Step 1: Update the CLAUDE.md headline test count**

In `CLAUDE.md` → Testing section, update `**Headline count: 1275 JVM tests + 9 instrumented tests.**` to the count from Task 3 Step 2 (instrumented unchanged at 9).

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
can never crash the catalogue). No Room schema change (data-only delete). +2 JVM tests.
```

If the `[Unreleased]` block has a running test-count line, update it.

- [ ] **Step 3: Update source-files.md**

In `docs/steering/source-files.md`, update the `CosmeticRepositoryImpl.kt`, `CosmeticDao.kt`, and `CosmeticCategory.kt` entries to reflect: enum reduced to `ZIGGURAT_SKIN`; `deleteByIds` purge + resilient `toDomainOrNull` mapping; 7 seed rows (was 11). Match the existing format.

- [ ] **Step 4: Update STATE.md**

In `docs/agent/STATE.md`: add a CURRENT bullet for #221 (shipped to `[Unreleased]`), demote the prior CURRENT to "Previous objective", update the test-count line. Keep to one page.

- [ ] **Step 5: Append a RUN_LOG.md entry**

Append a dated `## 2026-06-23 — #221 …` entry summarizing: issue, spec→adversarial-review→plan→adversarial-review (which caught the task-ordering bug)→TDD, files touched, the existing-device data-safety approach (purge + resilient mapping, no migration), test delta, no schema/economy change.

- [ ] **Step 6: Commit the docs**

```bash
git add CLAUDE.md CHANGELOG.md docs/steering/source-files.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#221): sync current-state docs + STATE/RUN_LOG for dead-cosmetic removal"
```

---

## Task 5: Open the PR

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
- New `R221` resilience test: a row with an unparseable category is filtered from `observeAll`, not crashed.
- New `R221` purge test: `ensureSeedData` deletes the dead ids from the DB.
- Updated `CosmeticRepositoryImplTest` seed counts (11→7) + fixtures.
- Full suite green: <N> JVM tests, 0 failures. detekt + ktlint clean. assembleDebug green.

## Process
Spec → Adversarial Review Gate (13 findings → 6 minor folded) → plan → Adversarial Review Gate (caught a task-ordering bug → restructured to a behavior-preserving refactor + one atomic removal commit) → TDD. Spec: `docs/superpowers/specs/2026-06-23-remove-dead-cosmetics-221-design.md`.
EOF
)"
```

Replace `<N>` with the actual count.

---

## Self-review notes (for the executor)

- **Spec coverage:** resilient `toDomainOrNull` → T1; `deleteByIds` purge → T2 S3-S5; drop seed rows → T2 S5c; enum reduction → T2 S10; labels/strings → T2 S11-S12; all 3 count sites (idempotency S6, legacy-upgrade S8, player-state S9) + survivor loop (S8d) + otherIds list/comment (S7) → T2; two new tests (resilience T1 S1, purge T2 S1). Every spec section maps to a step.
- **Green-at-every-commit (fixed from plan review):** T1 is behavior-preserving (resilient map drops nothing while the enum is full → all existing tests stay green; the new test uses an unparseable token, not a live enum name). T2 bundles the purge + seed-row removal + enum reduction + every test update into ONE commit, so the runtime purge's count change (11→7) never lands without its matching test updates. No intermediate red.
- **Type consistency:** `toDomainOrNull(): CosmeticItem?` + `mapNotNull` (T1 S3). `deleteByIds(ids: List<String>)` identical across DAO (T2 S3), fake (T2 S4), call site (T2 S5b). `DEAD_COSMETIC_IDS` defined once (T2 S5a). `CosmeticItem.priceGems` is `Long` — the entity's `Int` literals widen on map; the new test rows use `priceGems = 100/150` (entity ints), consistent with existing fixtures.
- **Verify-before-assert:** T3 S2 reads the result XMLs for the true count.
```
