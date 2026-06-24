---
name: new-migration
description: Author a Room schema change in Steps of Babylon — add/alter an @Entity column or table, bump the DB version, and walk the full migration choreography. Triggers on "add a Room migration", "schema change", "add a column", "add a table", "bump the DB version", "change the database schema", or when editing data/local/Migrations.kt / an @Entity.
disable-model-invocation: true
---

# /new-migration — Guided Room schema change

User-invoked only. A schema change is a fragile, **process-controlled** multi-step change: the
hand-written migration, the bumped DB version, and the regenerated schema JSON must all land in the
same PR or CI's schema-drift gate fails — and a forgotten/unregistered migration ships a guaranteed
launch crash for every upgrading user. This skill walks the exact ordered choreography against the
real code. It **guides; it does not auto-apply** — you author each edit, then run the verifications.

## Why this is dangerous (read first)

Room derives the on-disk schema from the `@Entity` classes and bakes a schema hash into generated
code. Three things must agree, or the app crashes on upgrade:

1. **The `@Entity`** — the new column/table/index you actually want.
2. **`@Database(version = N)`** in `AppDatabase` — Room's notion of "what schema this build expects".
3. **A `Migration(N-1, N)`** in `AppMigrations` — the SQL that transforms an existing user's DB from
   the old shape to the new one, registered so Room can find it.

If you bump the version + commit the new schema JSON (passing the drift gate) but **forget to add or
register the `Migration` object**, nothing else in CI's drift gate catches it — but the running app
hits "no migration found" and crashes on every upgrading install. `AppMigrations.validateChain`
(`MigrationChainTest`, #237) is the JVM guard that does catch it, so the test step below is
load-bearing, not optional.

## The real choreography (verified against the repo)

| Concern | Where it lives |
|---|---|
| `@Entity` classes | `app/src/main/java/com/whitefang/stepsofbabylon/data/local/*Entity.kt` |
| DB version constant | `AppDatabase` (`data/local/AppDatabase.kt`) — `@Database(version = …, exportSchema = true)`; **currently 12** |
| Migration authoring | `data/local/Migrations.kt` → `object AppMigrations` (one `MIGRATION_x_y = object : Migration(x, y) { override fun migrate(db) … }` per bump) |
| Migration registration | `AppMigrations.ALL: Array<Migration>` → wired into the Room builder via `.addMigrations(*AppMigrations.ALL)` in `di/DatabaseModule.kt` |
| Chain self-check | `AppMigrations.validateChain` + `MIGRATION_FLOOR = 7`; pinned by `MigrationChainTest` (#237) |
| Exported schemas | `room { schemaDirectory("$projectDir/schemas") }` in `app/build.gradle.kts` → JSON at `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/N.json`. KSP **regenerates** the next `N.json` during `assembleDebug`. |
| CI drift gate | `.github/workflows/ci.yml` → the **"Room schema-drift guard"** step (`git add -N app/schemas` → `git diff --exit-code app/schemas` → `git status --porcelain app/schemas`). #254: `git add -N` is what catches a NEW, untracked `N.json` the dev forgot to `git add` — the most likely real drift. |
| Atomic-DAO contention guard | `AtomicDaoConcurrencyTest` (#252) — extend it if a guarded/atomic DAO changed |
| Per-migration test pattern | `Migration11To12Test` (drives the migration's `migrate(db)` against a real seeded old-version DB) |
| Schema doc | `docs/database-schema.md` (per CLAUDE.md PR Task-List Convention) |

Conventions to mirror from `Migrations.kt`:
- **Additive column:** `ALTER TABLE <table> ADD COLUMN <name> <TYPE> NOT NULL DEFAULT <v>` (see `MIGRATION_7_8`).
- **New table:** `CREATE TABLE IF NOT EXISTS …` mirroring exactly what Room generates from the entity
  (column order + types + NOT NULL constraints), so the post-migration hash matches the export (see `MIGRATION_8_9`).
- **Drop/retype a column, or add a unique index that needs dedup:** the recreate-table dance —
  create the new-shaped table, `INSERT … SELECT` (transform/aggregate), `DROP TABLE` old, `RENAME`,
  then create indexes (see `MIGRATION_9_10` / `MIGRATION_10_11` / `MIGRATION_11_12`). When collapsing
  rows, carry state columns as the group `MAX` so a one-shot `claimed`/`completed` flag is never
  resurrected into a re-claimable row (the #127 lesson).
- The `migrate` body uses `db.execSQL(…)` only; the `Migration` object is anonymous; the KDoc records
  the version edge, the issue/ADR, and the data-transform rule. **Never** hand-edit `app/schemas/N.json`.

## Heads-up: expected permission prompts

`.claude/hooks/guard-sensitive-edits.sh` will fire on this work — this is normal for a real migration:
- Editing/adding anything under `app/schemas/**` → **"ask"** (Tier 1; confirm it's an intended schema change).
- Editing `data/local/Migrations.kt` → **advisory** nudge (Tier 3; emits `additionalContext` pointing
  back at this skill, the edit still proceeds — it does NOT hard-prompt).
Approve/heed them; they are the guardrail doing its job, not a sign you're off-track.

## Ordered checklist (do these in order — create a TodoWrite item per step)

> Throughout, `N` = the new DB version (current live version is **12**, so a new change is `N = 13`,
> migration `Migration(12, 13)`). Confirm the live number in `AppDatabase` before you start — don't
> trust this prose if it has drifted.

1. **Decide the schema delta + scope.** What column/table/index changes, on which entity, with what
   default for existing rows? Confirm the change is additive where possible. Read the affected
   `*Entity.kt` and `docs/database-schema.md` so you migrate the *real* current shape.

2. **Edit the `@Entity`** in `data/local/<Name>Entity.kt`. Add the field/table/index. For a new
   column give it a default so existing rows have a value. KDoc the field with "Added in DB vN" like
   the existing `DailyStepRecordEntity.battleStepsEarned` ("Added in DB v8") / `bossPsEarnedToday`
   ("Added in DB v10 (R4-07)") fields do. (If it's a brand-new table, also add the entity to the
   `entities = [...]` list and a DAO accessor in `AppDatabase`.)

3. **Bump the DB version** in `data/local/AppDatabase.kt`: `@Database(version = N, …)`
   (current 12 → N). Leave `exportSchema = true` untouched.

4. **Author the migration** in `data/local/Migrations.kt`: add
   `val MIGRATION_<N-1>_<N> = object : Migration(N-1, N) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL(…) } }`,
   following the SQL convention above for your delta. KDoc it with the version edge, the rationale,
   the issue (#NNN) / ADR, and the data-transform rule for existing rows.

5. **Register the migration:** add `MIGRATION_<N-1>_<N>` to the `AppMigrations.ALL` array (in version
   order). This is the step that's easy to forget and that `MigrationChainTest` exists to catch. No
   change needed in `DatabaseModule.kt` — it already splats `*AppMigrations.ALL`.

6. **Regenerate the exported schema:** run a debug build so KSP re-exports the schema —
   `./run-gradle.sh assembleDebug`. This writes `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/N.json`.
   `git add` the new file (a NEW, untracked `N.json` is the classic drift the gate trips on). Never
   hand-edit it; if the build fails because your `CREATE TABLE` hash doesn't match, fix the migration
   SQL to mirror the entity, not the JSON.

7. **Run the schema-drift gate locally** (mirror CI's "Room schema-drift guard" step) before pushing:
   ```bash
   git add -N app/schemas
   git diff --exit-code app/schemas \
     || echo "DRIFT: commit the app/schemas changes (new or modified)"
   git status --porcelain app/schemas   # must be empty (nothing untracked)
   ```
   A clean exit + empty porcelain means the committed schema matches what `assembleDebug` produced.

8. **Run the chain guard** so a missing/unregistered migration fails fast:
   `./run-gradle.sh testDebugUnitTest --tests '*MigrationChainTest'`. (It reads the live built-DB
   version via `db.openHelper.readableDatabase.version` and asserts `AppMigrations.ALL` is a
   contiguous +1 chain from `MIGRATION_FLOOR` up to it.)

9. **Add a per-migration test** following `Migration11To12Test`: open a real SQLite DB in the
   v`(N-1)` shape (a `SupportSQLiteOpenHelper.Callback(N-1)` via `FrameworkSQLiteOpenHelperFactory`,
   under `RobolectricTestRunner`), seed representative rows, invoke
   `AppMigrations.MIGRATION_<N-1>_<N>.migrate(db)`, and assert the new column/table/index exists and
   existing-row data was transformed correctly (especially any dedup/aggregation or default-backfill
   rule).

10. **Extend `AtomicDaoConcurrencyTest` (#252) if a guarded/atomic DAO changed.** The
    guarded-deduct (`UPDATE … WHERE balance >= cost` returning rows-affected) and one-shot-claim
    (`UPDATE … WHERE id AND claimed = 0`) invariants must survive the migration. That test fires
    `workerCount = 12` concurrent workers (real threads, start-gate latch, file-backed Room DB) at
    `playerDao`/`encounterDao`/`missionDao`/`milestoneDao`/`workshopDao` and asserts "exactly one
    winner, never over-spent or double-credited". If the migration touches a table backing such a DAO
    (e.g. a new uniqueness constraint, a renamed/retyped column, a recreate-table dance on
    `daily_mission`/`player_profile`/`milestone`/`walking_encounter`/workshop), add/adjust a
    concurrent case so the invariant still holds. Skip only if the change is provably orthogonal to
    every atomic DAO.

11. **Update `docs/database-schema.md`** (PR Task-List Convention): add/adjust the entity's column
    table for the new column/table/index. Note "Added in DB vN" / the migration reference where the
    existing rows already do.

12. **Run the full JVM suite** to confirm nothing regressed: `./run-gradle.sh testDebugUnitTest`
    (DAO tests build a fresh vN DB; the migration tests exercise the upgrade path). Optionally
    `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` to clear the static-analysis/format gates.

13. **Sync the rest of the current-state docs, then checkpoint.** Per CLAUDE.md: add a `CHANGELOG.md`
    section for the PR; update `docs/steering/source-files.md` if you added a file; then run
    `/checkpoint` (STATE + RUN_LOG, and an ADR if the schema change embodies a non-trivial decision)
    — current-state-doc sync runs **before** the STATE/RUN_LOG write.

## Pre-commit gate (do not skip)

Before the commit, confirm with real command output (not assertion):
- `./run-gradle.sh assembleDebug` succeeds **and** the new `app/schemas/N.json` is committed.
- The local drift-gate snippet (step 7) exits clean with empty porcelain.
- `MigrationChainTest` + your new per-migration test + the full `testDebugUnitTest` suite pass.
- `docs/database-schema.md` (and `CHANGELOG.md`) reflect the change.

If any of these fail, fix the root cause (usually: migration SQL not mirroring the entity, or
`MIGRATION_<N-1>_<N>` missing from `AppMigrations.ALL`) before committing.

## Notes

- **Downgrades** reset gracefully (`fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`
  in `DatabaseModule`) — dev/QA only. **Upgrades have no destructive fallback by design**, which is
  exactly why a missing migration crashes rather than silently wiping data.
- The migration floor is `MIGRATION_FLOOR = 7`; pre-v7 upgrades are a separately-tracked concern
  (#258). Don't lower the floor without deliberate intent.
- `BillingReceiptDao` is the one no-port, data-internal DAO (behind the `BillingManager` port); its
  table still follows the same migration rules (its creation was `MIGRATION_8_9`).
