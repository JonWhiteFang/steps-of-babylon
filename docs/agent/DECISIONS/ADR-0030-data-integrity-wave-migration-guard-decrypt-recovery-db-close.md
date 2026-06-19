# ADR-0030: Data-integrity wave — migration-chain guard, scoped decrypt-fail recovery, awaited DB-close

Status: Accepted (2026-06-19)

## Context

The 2026-06-18 complete-app review surfaced three independent data-integrity defects in the persistence
layer, all touching non-regenerable local progress (Steps gate behind real-world walking):

- **#237** — nothing asserted that `AppMigrations.ALL` is a contiguous chain topping out at
  `AppDatabase.version`. A future version bump that commits the new schema JSON (passing the CI drift gate)
  but forgets to register the `Migration` object would ship a guaranteed launch crash for every upgrading
  user, uncaught by CI.
- **#238** — `DatabaseKeyManager.getPassphrase` caught *any* `Exception` on decrypt and responded with
  irreversible deletion of the whole DB. A transient Keystore fault thus destroyed all player progress.
- **#248** — `DataDeletionManager.deleteAllData` called `database.close()` immediately after the
  asynchronous `cancelAllWork()`/`stopService()`, racing an in-flight `StepSyncWorker` write against a
  closed DB.

## Decision

**#237 — pure validation helper + JVM guard test.** Added `AppMigrations.validateChain(migrations,
liveVersion, floor)` (pure, Android-free) returning a list of human-readable chain problems, plus
`MIGRATION_FLOOR = 7`. `MigrationChainTest` feeds it the real `ALL` + the live version. The live version is
read from `db.openHelper.readableDatabase.version` (== `PRAGMA user_version`), **not** annotation
reflection — `androidx.room.Database` is `@Retention(CLASS)`, so runtime reflection returns null (verified).

**#238 — scope the wipe to "alias provably absent".** Wipe + regenerate only when the Keystore alias is
absent (true device-restore — the on-disk DB is unrecoverable). A decrypt failure with the alias present is
treated as transient and rethrown (DB open fails this launch, retries next launch — no wipe). The
wipe-vs-rethrow decision is a pure `decideOnDecryptFailure(aliasExists): DecryptFailureAction` seam
(JVM-tested), fed by an injectable `keystoreAliasExists` predicate. If the keystore can't even be opened to
check, default to "present" (no wipe) — prefer data preservation under uncertainty.

**#248 — await work cancellation before close, keep `recreate()`.** `cancelAllWork().result` is awaited via
a bounded `Future.get(2s)` (main-thread-safe, well under the 5s ANR window) before `database.close()`. On
timeout/error: log + proceed (a missed write is wiped anyway). The existing `recreate()` + Room lazy-reopen
self-heal is unchanged (developer-chosen smallest-change approach).

## Alternatives considered

- **#237: `MigrationTestHelper` opening v12 from each prior shipped version.** More thorough but needs
  schema-asset wiring + instrumented infra; the contiguity/version guard is the cheap insurance that catches
  the actual worst case (forgotten registration). Can layer the per-version open test later.
- **#238: rethrow on *all* decrypt failures (never wipe).** Would break the genuine device-restore
  auto-heal (crash-loop on a new device). Rejected — the alias-absent branch must still wipe.
- **#248: `clearAllTables()` without closing the `@Singleton`, then process-restart (ProcessPhoenix).**
  Sidesteps the close-race entirely and genuinely rebuilds the graph, but changes wipe semantics (DB file +
  Keystore key no longer reset to reinstall-fresh) and needs a relaunch primitive. Rejected this session as
  higher behavioral risk; the await-cancel approach removes the most-likely (worker) crash path.

## Consequences

- **Positive:** the most expensive migration failure mode now fails the build, not the user's launch;
  transient Keystore faults no longer destroy progress; the worker-write-after-close crash is gone
  deterministically. All pure/JVM-tested. No schema/economy/engine change.
- **Negative / tradeoffs:** #238 — a *persistent* alias-present decrypt failure now crash-loops instead of
  self-healing via wipe (deliberate; Settings → Delete All Data remains the manual escape). #248 — the
  foreground-service collector half of the close-race is narrowed, not eliminated (documented; full
  elimination needs the rejected process-restart). #238 seam `keystoreAliasExists` is a JVM-global mutable
  `var` on an `object` — tests must reset it in `@After` (enforced by convention + the test's own teardown).
- **Follow-ups:** #258 (pre-v7 upgrade-crash risk + the stale `fallbackToDestructiveMigration()` claim in
  `database-schema.md`) is a separate issue, untouched here; the migration floor const ties to it.

## Links

- Issues: #237, #238, #248 (2026-06-18 complete-app review, data-integrity dimension).
- Spec: `docs/superpowers/specs/2026-06-19-data-integrity-wave-237-238-248.md`.
- Related ADRs: ADR-0020/0027 (economy atomicity), ADR-0026 (crash visibility), ADR-0021 (onboarding flag
  device-local). Migration history: #127 (`MIGRATION_11_12`, `Migration11To12Test`).
