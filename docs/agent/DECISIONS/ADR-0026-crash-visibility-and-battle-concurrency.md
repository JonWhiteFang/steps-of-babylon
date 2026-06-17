# ADR-0026: Crash visibility (local breadcrumb + guarded loop) and the two remaining battle concurrency fixes

**Status:** Accepted — 2026-06-17 (shipped on branch `fix/190-191-crash-visibility-battle-concurrency`, `[Unreleased]`).

## Context

The 2026-06-17 complete-app review (`docs/reviews/complete-app-review.md`) raised three `severity:blocker`
items gating internal → closed promotion. Two are code defects fixed here (#190, #191); the third (#192
privacy/Data-Safety text) is a separate text/Console change.

- **#190 (REL-1/REL-2) — no crash visibility.** There was no global uncaught-exception handler and no
  crash reporting, and the battle render loop (`GameLoopThread.run`) ran `engine.update()`/`render()`
  with no `try/catch`. A `RuntimeException` on the dedicated `"GameLoop"` thread → silent process death.
  We would run a ≥12-tester / ≥14-day closed soak **blind** — the soak's entire purpose is stability
  signal we cannot currently collect.
- **#191 (CONC-1/CONC-2) — two reachable battle CMEs**, same class as #118 (a shared engine collection
  structurally mutated off the loop thread), on two lists the #118 `entitiesLock` sweep didn't cover:
  `EffectEngine`'s effect lists (mutated on every boss kill / battle-step reward) and `GameEngine.uwStates`
  (mutated on the replay path).

## Decision

**#190 — local-only crash visibility, no remote SDK.**
- New `data/diagnostics/CrashBreadcrumbStore` — a device-local single-slot SharedPreferences breadcrumb
  (`crash_breadcrumb_prefs`), mirroring `OnboardingPreferences`. Synchronous `commit()` (the process may
  be dying); every method best-effort/never-throws; caller supplies the timestamp (the store reads no
  clock, so it's trivially testable).
- A **chaining** global `Thread.setDefaultUncaughtExceptionHandler` (installed in `StepsOfBabylonApp`,
  logic in the JVM-testable top-level `buildCrashHandler`): record the breadcrumb FIRST, then delegate to
  the previous default handler so Android/Play vitals still records the crash.
- `GameLoopThread.run()` wraps the per-tick `update()`/`render()` in a `try/catch`: record breadcrumb →
  stop the loop → fire `onLoopError`. **Policy on a loop crash = stop + surface, not skip-frame**
  (engine exceptions indicate deterministic corrupt state; retrying the same frame would just re-throw).
  `BattleViewModel.onBattleLoopError` sets `BattleUiState.battleError`, breaks the poll, and marks the
  round ended so a crashed round's corrupt totals are **not persisted** — it deliberately does NOT set
  `eng.roundOver` (which would route through `runEndRoundPersistence` and commit the suspect numbers).
- A non-dismissable `BattleErrorOverlay` ("Return to menu") with all interactive round chrome suppressed
  (`showGameChrome = roundActive && !battleError` — transparent scrims don't block touches, so chrome
  must be suppressed, not merely z-ordered). A one-time next-launch notice snackbar surfaces the crash to
  the tester. The new prefs file is added to `DataDeletionManager.PREFS_NAMES`.

**#191 — extend the established monitor pattern to the two missed collections.**
- `EffectEngine` gets a private `effectsLock`; `addEffect`/`update`/`render`/`clear` guard the structural
  touches. Per-effect `update()`/`render()` and the Canvas draw run OUTSIDE the lock (snapshot idiom).
  The `removeAll { isFinished }` sweep is **deferred to a second lock acquisition AFTER** the per-effect
  update so today's `update → removeAll` order is preserved exactly (no 1-frame effect-lifetime change).
- `GameEngine.initUWs` is wrapped in the existing reentrant `entitiesLock`; a new `uwSnapshot()` serves
  the 200ms VM poll read. Lock order is acyclic: `entitiesLock` (outer, held across the whole tick) →
  `effectsLock` (inner); never the reverse (`EffectEngine` holds no `GameEngine` reference).

## Alternatives considered

- **Remote crash SDK (Crashlytics / Sentry / ACRA)** — rejected for v1.0: adds a dependency AND a
  data-egress path that would expand #192's Data-Safety disclosure (the very thing we're trying not to
  understate). The local breadcrumb materially de-risks the soak without that cost; a real SDK remains
  the right long-term answer (tracked with telemetry #23).
- **Loop-crash policy = skip-frame-and-continue** — rejected: would mask a persistently-broken battle and
  spin re-throwing on the same corrupt state every frame. Stop+surface yields one honest, reportable
  failure. (A hybrid "tolerate N then stop" was also considered and rejected as unnecessary complexity.)
- **Set `eng.roundOver = true` on a loop crash to reuse the normal end-of-round path** — rejected: it
  would persist best-wave / milestone-PS / battle-stats computed from a corrupt engine. We instead mark
  `roundEnded` (now `@Volatile`) so both the poll and `onCleared` short-circuit without persisting.
- **#191: a single new engine lock, or `CopyOnWriteArrayList`** — rejected in favour of reusing the
  documented `#118 entitiesLock` for `uwStates` (one monitor, no lock-ordering surprises) and a dedicated
  `effectsLock` confined inside `EffectEngine` (which has no engine reference, keeping the order acyclic).

## Consequences

- **Positive:** no silent loop-thread death; both routine-play CMEs eliminated; the closed soak is now
  observable (local breadcrumb + Play vitals) without new data collection; no schema/economy/balance/dep
  change; the breadcrumb is wiped by "Delete All Data" (privacy-clean).
- **Negative / tradeoffs:** the breadcrumb is local-only (a tester must surface it out-of-band; the
  next-launch notice prompts this) — no automatic aggregation until a real SDK lands. `uwSnapshot()`
  tolerates a torn scalar read (one stale cooldown-ring frame) — cosmetic, by design.
- **Follow-ups:** (1) wrap `onLoopError?.invoke` was added defensively here; (2) backgrounding while the
  error overlay is up re-spins a loop thread that re-crashes (redundant newest-wins breadcrumb, no data
  impact) — accepted for the soak, a proper fix needs cross-lifecycle error-state tracking; (3) real
  remote crash reporting → telemetry #23; (4) #194 "no error states anywhere" is broader UX work this
  PR explicitly does not close (the overlay is scoped to the loop-failure case only).

## Links

- Commits: `2cb2331`/`415a4f1` (store), `2deaf2e` (wipe), `6911e37` (handler), `bef0ad1`/`5b1f40b` (loop
  guard + wiring), `277920b` (battle-error UI + no-persist + notice), `ee6bdd3` (EffectEngine lock),
  `90dcd2c` (uwStates lock), `df63c77` (onLoopError runCatching).
- Spec: `docs/superpowers/specs/2026-06-17-crash-visibility-battle-concurrency-design.md`.
  Plan: `docs/superpowers/plans/2026-06-17-crash-visibility-battle-concurrency.md`. Both passed the
  Adversarial Review Gate (spec 34→25 surviving; plan 18→14 surviving; both 0 unaddressed critical/major).
- Source review: `docs/reviews/complete-app-review.md` §12, §17, §18. Issues #190, #191.
- Related ADRs: builds on the #118 `entitiesLock` thread-safety pattern; #192 (privacy text) is the
  sibling blocker not addressed here.
