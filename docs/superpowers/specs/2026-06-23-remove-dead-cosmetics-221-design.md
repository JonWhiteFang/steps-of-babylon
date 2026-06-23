# Design — Remove dead projectile/enemy-skin cosmetics (#221 / FEAT-1)

**Date:** 2026-06-23
**Issue:** #221 (`[Audit] Projectile/enemy-skin cosmetics seeded with no render path — dead data + a live trap (FEAT-1)`), severity:minor, area:battle/content. From the 2026-06-17 complete-app review §4 (adversarially-verified).
**Status:** design approved — proceeding to implementation plan.

## Problem

Of 11 seeded cosmetics, only `ZIGGURAT_SKIN` rows have a render path (`GameEngine.init` →
`ZigguratEntity.layerColors`, fed from `CosmeticItem.overrideColors` via `ZIGGURAT_COLOR_LOOKUP`). The
4 seeded **`PROJECTILE_EFFECT`** / **`ENEMY_SKIN`** rows — `proj_fire`, `proj_lightning`,
`enemy_shadow`, `enemy_neon` — have **no rendering wiring whatsoever**, and `overrideColors` is only
ever populated for ziggurat ids. They are un-buyable today (`StoreScreen.ENABLED_COSMETIC_IDS =
{zig_jade, zig_obsidian}`), but constitute a **live trap**: widening the enable-list without adding
render wiring would let a player buy a cosmetic that does nothing.

The chosen fix (per the issue's first option) is **removal**, not wiring a renderer (which would touch
the fragile battle renderer for pure content with no v1.0 driver).

## The data-safety problem (why this is not just deleting lines)

`CosmeticEntity.category` is persisted as a **String** column in the Room `cosmetics` table, and
`CosmeticRepositoryImpl.toDomain()` maps it via `CosmeticCategory.valueOf(category)`. Existing devices
already have the 4 dead rows seeded. Therefore:

1. **Removing the enum values alone would crash** on existing devices — `valueOf("PROJECTILE_EFFECT")`
   throws `IllegalArgumentException` the moment those rows are mapped.
2. **There is a real ordering race.** `StoreViewModel.init` launches `ensureSeedData()` (where the
   purge runs) in one coroutine *and* subscribes to `observeAll()` (a Room `Flow`) as a `combine`
   source in a **separate** flow (`StoreViewModel.kt:49` vs `:67`). `observeAll()` emits current table
   contents immediately, so it can emit the dead rows **before** the purge `DELETE` commits. Other
   consumers (`BattleViewModel.observeEquipped()`, `idExists`) also map rows independently.

So cleanup requires **both** a runtime purge of the dead rows **and** resilient mapping that never
crashes on an unknown category. This is belt-and-suspenders, and both halves are load-bearing (the
resilient mapping covers the pre-purge race window + any other map site; the purge removes the cruft
permanently).

## Scope

### In scope
- Reduce `CosmeticCategory` to `{ ZIGGURAT_SKIN }` (drop `PROJECTILE_EFFECT`, `ENEMY_SKIN`).
- Drop the 4 dead rows from `SEED_COSMETICS`.
- One-time runtime purge of the 4 dead `cosmeticId`s in `ensureSeedData()` (idempotent delete-if-present).
- Resilient `toDomain` mapping: a row whose stored `category` no longer parses is filtered out at the
  repo boundary (the domain `CosmeticItem` list only ever contains valid items) — never throws.
- Remove the 2 now-unused `labelRes()` cases + 2 string resources.
- Update + extend `CosmeticRepositoryImplTest`.

### Out of scope (explicit)
- **No schema-version bump / Room migration.** The `cosmetics` *table schema* is unchanged; this is a
  data delete, which a runtime `DELETE` query handles. (Avoids the schema fragile zone, `MigrationChainTest`,
  schema-JSON export churn.)
- **No render-path implementation** (the issue's rejected alternative).
- **No Store UI restructure** and **no new `CosmeticCategory`**.

## Architecture / components

| File | Change |
|---|---|
| `domain/model/CosmeticCategory.kt` | `enum class CosmeticCategory { ZIGGURAT_SKIN }` (was 3 values). |
| `data/local/CosmeticDao.kt` | Add `@Query("DELETE FROM cosmetics WHERE cosmeticId IN (:ids)") suspend fun deleteByIds(ids: List<String>)`. |
| `data/repository/CosmeticRepositoryImpl.kt` | (a) Remove the 4 dead rows from `SEED_COSMETICS`. (b) `ensureSeedData()` purges `DEAD_COSMETIC_IDS` (a private `listOf(...)`) once via `dao.deleteByIds`. (c) Map `observeAll/observeOwned/observeEquipped` through a resilient `toDomainOrNull()` that returns `null` for an unparseable category; callers `mapNotNull`. |
| `app/src/test/.../fakes/FakeCosmeticDao.kt` (test fake, package `com.whitefang.stepsofbabylon.fakes`) | Implement `deleteByIds`. |
| `presentation/ui/EnumLabels.kt` | Remove the `PROJECTILE_EFFECT`/`ENEMY_SKIN` `labelRes()` branches. |
| `res/values/strings.xml` | Remove `cosmetic_cat_projectile_effect` + `cosmetic_cat_enemy_skin`. |
| `data/repository/CosmeticRepositoryImplTest.kt` | Seed-count 11→7; drop the 4 ids from the `otherIds`/legacy-upgrade fixtures; add a purge+resilience test. |

### Resilient mapping detail

`toDomain()` currently does `CosmeticCategory.valueOf(category)`. Replace the call sites with a private
helper:

```kotlin
private fun CosmeticEntity.toDomainOrNull(): CosmeticItem? {
    val cat = CosmeticCategory.entries.find { it.name == category } ?: return null // legacy/dead row
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

and `observeAll/observeOwned/observeEquipped` use `list.mapNotNull { it.toDomainOrNull() }`. `equip()`
already looks up the raw `CosmeticEntity` by id (not via `toDomain`), so it is unaffected; `idExists`
queries raw entities too. Using `entries.find { it.name == … }` (not `runCatching { valueOf }`) keeps the
mapping allocation-light and exception-free.

### Purge detail

```kotlin
private val DEAD_COSMETIC_IDS = listOf("proj_fire", "proj_lightning", "enemy_shadow", "enemy_neon")
```

In `ensureSeedData()`, before/after the existing missing-row upsert, call
`dao.deleteByIds(DEAD_COSMETIC_IDS)`. `DELETE ... WHERE cosmeticId IN (...)` is a no-op when none are
present, so it is idempotent and cheap in steady state (fresh installs never seeded them, so nothing to
delete; updated devices delete once and thereafter no-op). `ensureSeedData` is already called from
`StoreViewModel.init`, `idExists`, and is the established seed-on-launch seam — no new trigger needed.

## Data flow

```
Fresh install:  SEED_COSMETICS (7 ziggurat rows) seeded; dead ids never inserted; purge no-ops.
Updated device: observeAll() may emit dead rows pre-purge → toDomainOrNull() filters them (invisible) →
                ensureSeedData() runs dao.deleteByIds(DEAD_COSMETIC_IDS) → rows gone permanently →
                observeAll() re-emits without them.
```

## Error handling

- `toDomainOrNull` is total — an unknown category yields `null` (filtered), never an exception. This is
  the crash-prevention guarantee for the enum reduction.
- `deleteByIds` on absent ids is a no-op (standard SQL `DELETE ... WHERE ... IN`).
- No new threading; `ensureSeedData` stays a `suspend` fun called from existing `viewModelScope` launches.

## Testing

Pure-JVM via `FakeCosmeticDao` (JUnit Jupiter, no emulator). **Existing ziggurat-palette tests stay
green** (zig_jade/lapis/garden/sandals/obsidian `overrideColors`). Net seed count is **7** (the 7
ziggurat rows); the 4 dead ids never appear in `observeAll()`.

The review surfaced that `CosmeticRepositoryImplTest` has **three** distinct `assertEquals(11, …)`
seed-count sites and a fixture loop that must each change. Enumerated precisely so the plan can't miss
one:

- **`otherIds` regression list** (`CosmeticRepositoryImplTest.kt:185`) — drop the 4 dead ids → just
  `{zig_crystal, zig_golden}`. (Load-bearing: each id is fetched via `allItems.single { … }`, which
  throws `NoSuchElementException` once the id is purged + filtered — not just an assertion failure.)
  Also update the now-stale comment at `:176-178` (it mentions `PROJECTILE_EFFECT, ENEMY_SKIN`).
- **Idempotency test** (`…is idempotent on repeat call`) — the `assertEquals(11, countAfterFirst)` at
  `:242` → **7**, and its message string at `:244` ("ships 11 seed rows … 2 projectile, 2 enemy") →
  rewrite to "7 ziggurat seed rows".
- **Legacy-upgrade test** (`…inserts newly-added rows on partial catalogue upgrade`, `:248-350`) — the
  `legacySeed` list (`:261-312`) currently has 3 ziggurat + the 4 dead rows. **Drop the 4 dead rows
  from `legacySeed`** so it pre-seeds only the 3 legacy ziggurat rows; the `assertEquals(7, …)` baseline
  at `:314` stays 3 → update to 3; the post-`ensureSeedData` `assertEquals(11, …)` at `:319-323` → **7**
  (3 legacy ziggurat + 4 newly-seeded milestone ziggurats). **Critically, the survivor loop at
  `:344-349`** (`for (legacyId in legacySeed.map { it.cosmeticId }) { assertNotNull(… must survive …) }`)
  is driven off `legacySeed`, so once the dead rows are dropped from `legacySeed` it automatically
  covers only ziggurat ids — no separate edit, but it MUST NOT be left asserting the dead ids survive
  (that would contradict the purge). The purge-vs-pre-seed math is covered by the dedicated new test
  below instead, keeping this test focused on the additive-upgrade behavior it was written for.
- **Player-state-preservation test** (`…preserves player state on existing rows (isOwned, isEquipped)`)
  — the `assertEquals(11, dao.count())` at `:379` → **7**, and the comment at `:378` ("All 11 seed rows
  now present (10 new + …)") → "All 7 seed rows now present (6 new + the pre-existing zig_jade …)".
- **New test — purge + resilience:** pre-seed a `CosmeticEntity(category = "PROJECTILE_EFFECT", …)` (a
  legacy/dead row) directly via the fake DAO, run `ensureSeedData()`, assert (a) `observeAll()` never
  exposes it (resilient `toDomainOrNull` mapping — proves no `valueOf` crash even before purge) and
  (b) `dao.count()` no longer includes it (purged by `deleteByIds`).

**No edit needed (note for reviewers):** `EnumLabelResTest.kt:38-41` iterates `CosmeticCategory.entries`
asserting each has a non-blank label ≠ raw name. It **auto-shrinks** to the single `ZIGGURAT_SKIN` entry
and stays green (the `cosmetic_cat_ziggurat_skin` string is retained) — removing the other two strings
does not break it. `StoreViewModelTest`/`BattleViewModelTest` only use `CosmeticCategory.ZIGGURAT_SKIN`
(verified) — unaffected by the enum reduction. The Room schema JSON under `app/schemas` does not
enumerate category values (`category` is a plain `TEXT` column) — confirms no migration needed.

Estimated **net ~+1 JVM test** (count/comment assertions updated in place, 1 added) → headline
`1275 → ~1276`.

## Risks & mitigations

- **Risk:** an owned/equipped dead cosmetic on some device gets silently removed. *Assessment:* benign —
  the 4 dead ids were never purchasable (`ENABLED_COSMETIC_IDS` never contained them) and never had a
  render effect, so no player can have meaningfully "owned" one; removal loses nothing real. No gem
  refund needed (no gems were ever spent on them).
- **Risk:** enum reduction breaks an exhaustive `when` elsewhere. *Mitigation:* the only `when` over
  `CosmeticCategory` is `EnumLabels.labelRes()` (verified) — its 2 dead branches are removed in the same
  change; the compiler enforces exhaustiveness, so a missed site fails the build.
- **Risk:** `valueOf` crash slips through a missed map site. *Mitigation:* the resilient `toDomainOrNull`
  replaces every `toDomain` site in the repo (the only place `valueOf(category)` is called); `equip`/
  `idExists` use raw entities. A code-grounded review confirms no other `CosmeticCategory.valueOf` call.

## Open questions

None — removal scope (rows + enum + labels) and cleanup mechanism (runtime purge + resilient mapping, no
migration) confirmed with the developer.

## Adversarial review (2026-06-23)

Spec passed the mandatory Adversarial Review Gate (multi-agent `Workflow`: 4 code-grounded dimensions →
per-finding skeptic refute). **13 findings → 6 confirmed (all `minor`/`partial`, no surviving
critical/major) / 7 refuted.** The two findings the reviewers tagged `major` were both downgraded by the
skeptic pass (test-only artifacts that fail loudly at build time, substance already present). All
survivors were a single theme — the spec's test-update section under-enumerated exact assertion sites —
and are folded into the rewritten **Testing** section + the **Files** table:

1. *(code-grounding)* `FakeCosmeticDao` path was mislocated (`data/local/` → `app/src/test/.../fakes/`).
2. *(consistency-tests)* the legacy-upgrade test's **survivor loop** (`:344-349`) asserts every
   pre-seeded id survives — would contradict the purge unless the 4 dead rows are dropped from
   `legacySeed`. Now spelled out.
3. *(consistency-tests / scope)* there are **three** `assertEquals(11,…)` count sites (idempotency `:242`,
   legacy-upgrade `:319`, player-state `:379`) + two comments — all now enumerated to land at 7.
4. *(data-safety)* `EnumLabelResTest` auto-shrinks (no edit) — noted so a reviewer doesn't flag it.

Refuted (spec already correct): 1275 test baseline matches CLAUDE.md; the race line-cites are accurate;
the equipped-dead-row case is already documented benign; the labelRes crash is unreachable (filtered at
the repo boundary, and `StoreViewModel` is typed on `CosmeticCategory` so it can't carry a raw string);
purge ordering is correctness-irrelevant (idempotent `DELETE … IN`).
