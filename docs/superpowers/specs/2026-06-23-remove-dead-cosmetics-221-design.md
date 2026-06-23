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
| `data/local/FakeCosmeticDao.kt` (test fake) | Implement `deleteByIds`. |
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

Pure-JVM via `FakeCosmeticDao` (JUnit Jupiter, no emulator):

- **Seed count is 7** (the 7 ziggurat rows); the 4 dead ids never appear in `observeAll()`.
- **Existing ziggurat-palette tests stay green** (zig_jade/lapis/garden/sandals/obsidian `overrideColors`).
- **Update existing fixtures:** the `otherIds` regression list (drop the 4 dead ids → `{zig_crystal,
  zig_golden}`), the legacy-upgrade test's pre-seed (it currently pre-seeds the 4 dead rows + asserts
  count 7→11; becomes a pre-seed of legacy *ziggurat* rows, and additionally asserts the dead rows it
  pre-seeds are purged), and the "11 rows" / idempotency count assertions → 7.
- **New test — purge + resilience:** pre-seed a `CosmeticEntity(category = "PROJECTILE_EFFECT", …)` (a
  legacy/dead row) directly via the fake DAO, run `ensureSeedData()`, assert (a) `observeAll()` never
  exposes it (resilient mapping) and (b) `dao.count()` no longer includes it (purged).

Estimated **net ~+1 JVM test** (some assertions updated, ~1–2 added) → headline `1275 → ~1276`.

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
