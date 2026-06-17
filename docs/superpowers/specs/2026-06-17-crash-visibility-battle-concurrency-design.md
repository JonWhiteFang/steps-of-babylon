# Design — Crash visibility + two reachable battle crashes (#190 + #191)

**Date:** 2026-06-17
**Issues:** #190 (REL-1 / REL-2 — no crash visibility + unguarded game-loop thread, `severity:blocker`),
#191 (CONC-1 / CONC-2 — `EffectEngine` + `uwStates` mutated off the loop thread, `severity:blocker`)
**Milestone:** `v1.0.0 closed-test gate` (Gate H, `plan-FORWARD.md`)
**Source review:** `docs/reviews/complete-app-review.md` §12, §17, §18

## Why these two together

Both are closed-track promotion blockers and they reinforce each other. #191 makes a battle
`ConcurrentModificationException` *reachable on routine play* (every boss kill / battle-step reward,
and on the replay path). #190 is what turns any such loop-thread exception into **silent process
death with no signal** — so we'd run a ≥12-tester / ≥14-day closed soak blind. Fixing #191 removes two
known crash sites; fixing #190 ensures any *remaining* crash (known or not) is caught, surfaced, and
reportable. The soak's entire purpose is stability signal; #190 is the instrumentation that makes the
soak worth running.

Neither changes game logic, the Room schema, the economy, or any balance constant. #191 is a
pure thread-safety hardening that extends the established **#118 `entitiesLock` monitor pattern** to
two collections that sweep missed. #190 adds a local-only diagnostic surface with **no new dependency
and no new data egress** (deliberately — a remote crash SDK would compound #192's privacy disclosure
and is out of v1.0 scope per the issue text).

## Goals

- **G1** — No uncaught exception on any thread results in silent death without a local record. A
  chaining global handler persists a breadcrumb, then delegates to the previous default handler so
  Play/Android vitals still records the crash.
- **G2** — A runtime exception inside the battle game loop (`update()`/`render()`) does **not** kill
  the process. It is caught, recorded, the loop stops cleanly, and the player sees an honest,
  acknowledgeable "battle error" state rather than a frozen or dead app.
- **G3** — On the next launch, a tester who hit a crash last session is shown a one-time, non-blocking
  notice inviting them to report it (maximises soak feedback quality), then the breadcrumb is cleared.
- **G4** — The two #191 races (`EffectEngine` effect lists; `uwStates` on replay) can no longer throw
  a CME / `IndexOutOfBoundsException`, proven by concurrency stress tests mirroring
  `GameEngineConcurrencyTest`.

## Non-goals (explicit scope fence)

- **No remote crash-reporting SDK** (Crashlytics / Sentry / ACRA). Deferred — the issue says a full SDK
  is the right long-term answer but likely out of v1.0 scope; it also adds a dependency + a data-egress
  path that would expand #192's Data-Safety disclosure. The local breadcrumb materially de-risks the
  soak without that cost.
- **No telemetry/analytics abstraction** (#23) — separate backlog item.
- **No other Gate-H items** (#192 privacy text, #193 no-sensor signal, #194 error states, #195 missions
  rollover) — each is its own issue/PR. *Note:* #194 ("no error state anywhere") is broader UX work; the
  battle-error overlay added here is scoped strictly to the **loop-failure** case for #190 and does not
  claim to close #194.
- **No change to game logic, balance, the Room schema, migrations, the economy spend/claim contract,
  or the existing `entitiesLock` semantics.**

---

## Part A — #190 Part A: global uncaught-exception handler + crash breadcrumb store

### A1. `CrashBreadcrumbStore` (new — `data/diagnostics/CrashBreadcrumbStore.kt`)

A `@Singleton`, constructor-injected SharedPreferences wrapper modeled **exactly** on
`data/onboarding/OnboardingPreferences` (no Hilt module — the `@Inject constructor(@ApplicationContext
context: Context)` form is directly injectable into both `Application` and `Activity`). Deliberately
**not Room**: this is a device-local diagnostic, must not sync if cloud save (#36) ever lands, and a
reinstall correctly discards it.

```kotlin
@Singleton
class CrashBreadcrumbStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE)

    /** Persist the newest crash. Synchronous commit() — the process may be dying. Never throws. */
    fun record(threadName: String, throwable: Throwable)

    /** The persisted breadcrumb, or null if none. */
    fun peek(): CrashBreadcrumb?

    fun clear()
}

data class CrashBreadcrumb(
    val timestampMillis: Long,
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val stackPreview: String,   // truncated
)
```

**Behaviour contract:**
- `record` writes with `.commit()` (NOT `.apply()`) — the JVM may exit before an async write flushes.
- `record` is wrapped so it can **never throw** (a diagnostic that crashes the crash handler is worse
  than useless). Any internal failure is swallowed (best-effort).
- The stack is captured from `throwable.stackTraceToString()` and **truncated** to a bounded length
  (constant `MAX_STACK_CHARS`, ~4 KB) so we never write an unbounded blob to SharedPreferences.
- **One slot, newest-wins** — a second crash overwrites the first. (A soak crash that recurs is more
  signal than the first occurrence; the tester report covers the narrative.)
- `timestampMillis` is supplied by the caller's clock at `record` time
  (`System.currentTimeMillis()` at the call site) — the store itself does not read a clock, keeping it
  trivially testable.

### A2. Chaining global handler (`StepsOfBabylonApp.onCreate`)

`StepsOfBabylonApp` gains `@Inject lateinit var crashBreadcrumbStore: CrashBreadcrumbStore` (same
injection style as the existing `workerFactory`). In `onCreate`, **after** `super.onCreate()` and the
existing SQLCipher + WorkManager setup, install:

```kotlin
val previous = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
    runCatching { crashBreadcrumbStore.record(thread.name, ex) }  // breadcrumb FIRST
    Log.e(TAG, "Uncaught exception on ${thread.name}", ex)
    previous?.uncaughtException(thread, ex)                       // preserve default crash / Play vitals
}
```

**Rationale for the order:** record the breadcrumb *before* delegating, because delegation hands off to
the platform handler that terminates the process — anything after it may not run. The `previous`
handler is preserved and re-invoked so we do **not** suppress the crash (Android still shows its
behaviour; Play Console / Android vitals still aggregates it). This is observability *added on top of*,
not *instead of*, the platform default.

The handler closure must not throw — hence `runCatching` around the (already non-throwing) record call
as defence-in-depth.

---

## Part B — #190 Part B: guarded game loop + battle-error UI

### B1. `GameLoopThread` guard

`engine.update()` and `engine.render()` are called **only** from `GameLoopThread.run()` (verified —
no other caller), so the guard belongs there. Wrap the per-iteration update block and render block in a
single `try/catch` inside the `while (isRunning)` body:

```kotlin
while (isRunning) {
    // ... timing ...
    try {
        if (!isPaused) { /* accumulator + engine.update(...) loop */ }
        // lockCanvas / engine.render(canvas) / unlockCanvasAndPost  (existing inner try/finally kept)
    } catch (t: Throwable) {
        runCatching { crashBreadcrumbStore.record(name, t) }   // name == "GameLoop"
        Log.e(TAG, "Game loop crashed; stopping loop", t)
        isRunning = false
        onLoopError?.invoke(t)
        break
    }
    // ... fps + sleep ...
}
```

**Design points:**
- The loop records the breadcrumb **directly** (not relying solely on the global handler) because the
  loop catches the exception itself — it never reaches the default handler. The global handler (A2) is
  the backstop for crashes on *other* threads.
- On error the loop **stops** (chosen policy: stop + surface, not skip-frame). Engine exceptions
  indicate deterministic corrupt state; re-running the same broken frame would just re-throw. Stopping
  + surfacing produces an honest, single, reportable failure — best for soak signal.
- `onLoopError: ((Throwable) -> Unit)?` is a new `@Volatile var` on `GameLoopThread`. `CrashBreadcrumbStore`
  is a new **constructor parameter**.
- The existing inner `try/finally` around `lockCanvas`/`unlockCanvasAndPost` (`:54-65`) is preserved —
  it guarantees the canvas is always posted/unlocked. The new outer catch sits around the whole body so
  an exception from `engine.update()` (outside the canvas try) or from `engine.render()` (inside it,
  re-thrown after the finally unlocks) is caught and ends the loop.

### B2. Wiring `onLoopError` through `GameSurfaceView` → `BattleViewModel`

- `GameSurfaceView` constructs `GameLoopThread` (`:99`); it now also constructs / holds a
  `CrashBreadcrumbStore` and passes it in. `GameSurfaceView` is a plain `SurfaceView` (not
  Hilt-injected), so it obtains the store the same way it obtains `AndroidStrings` — built from its
  `Context` as `CrashBreadcrumbStore(context)`. This is **correct despite not being the Hilt
  singleton**: SharedPreferences are keyed by file name, so the manually-constructed store and
  MainActivity's injected singleton both read/write the same `crash_breadcrumb_prefs` file (the
  `@Singleton` annotation is a Hilt scoping hint, harmless when the class is constructed directly).
  The loop thread writing the breadcrumb **directly** (rather than via the VM's `onLoopError` callback)
  is deliberate: it removes any dependency on callback-wiring timing — a loop crash is recorded even if
  it somehow fires before `startPollingEngine` has set `onLoopError`.
- `GameSurfaceView` exposes `var onLoopError: ((Throwable) -> Unit)?` that it forwards to whichever
  `GameLoopThread` is current (threads are recreated on every `surfaceCreated`; the field is re-applied
  like the existing `pendingSpeed`/`pendingPaused` mirrors).
- `BattleViewModel.startPollingEngine` sets `surfaceView.onLoopError = { t -> onBattleLoopError(t) }`.
  The callback fires on the loop thread, so the VM marshals onto its state via
  `_uiState.update { it.copy(battleError = true) }` (StateFlow update is thread-safe).

### B3. `battleError` UI state + overlay

- `BattleUiState` gains `val battleError: Boolean = false`.
- `BattleScreen` renders a **non-dismissable** "Battle error" overlay when `state.battleError` is true,
  **taking precedence over** `roundEndState` and the pause overlay (z-order: battle-error is topmost,
  below only the shared snackbar). Copy: a short apology + a single "Return to menu" CTA wired to the
  existing `onExitBattle` lambda (same exit path `PostRoundOverlay` already uses). No "retry" — the loop
  has stopped and the engine state is suspect.
- The overlay reuses existing overlay styling (mirror `PauseOverlay` / `PostRoundOverlay` structure)
  so no new design tokens are introduced.

---

## Part C — #190 Part C: next-launch breadcrumb surfacing

`MainActivity` already injects preferences (`onboardingPreferences`, `:83`) and owns the app-level
`Scaffold` + `SnackbarHost` (`:211-212`). Add `@Inject lateinit var crashBreadcrumbStore:
CrashBreadcrumbStore`, and in a `LaunchedEffect(Unit)` at composition:

- `peek()` once; if non-null, show a **one-time, informational** snackbar: *"The game closed
  unexpectedly last time. Sorry about that!"* — **no action button**, because there is no existing
  in-app feedback/report channel to wire it to (confirmed by grep — only a haptics toggle exists in
  Settings) and this PR does not invent one. The notice still achieves the soak goal: a tester who sees
  it knows a crash happened and can describe it in their out-of-band tester feedback. After the snackbar
  resolves, call `clear()`. (If a feedback channel later lands, the action can be added in that PR.)
- Mirror the existing `showStepPermissionSettingsHint` snackbar idiom (`:246-265`), including the
  "reset AFTER await" ordering note so a rotation mid-snackbar doesn't drop it.
- This is gated to **not** double up against onboarding/permission snackbars on first launch — a fresh
  install has no breadcrumb, so the natural `peek() == null` guard already prevents collision; no
  explicit onboarding-state check is required, but the plan will verify ordering against the existing
  permission-hint effect.

---

## Part D — #191: two reachable battle crashes (CONC-1 / CONC-2)

Same root cause as #118 (a shared engine collection structurally mutated off the loop thread), on two
lists the #118 `entitiesLock` sweep did not cover. Fixed with the **same monitor pattern**.

### D1. Trigger A — `EffectEngine` effect lists (High, confirmed)

**Problem:** `addEffect` (called from `applicationScope` for `StepReward` and `viewModelScope` for
`BossKilled` — `BattleViewModel.kt:430`, `:443`) appends to a plain `mutableListOf` `pendingEffects`
(`EffectEngine.kt:15`), while the loop thread runs `effects.addAll(pendingEffects); pendingEffects.clear()`
in `update()` (`:22`) and **iterates `effects` in `render()` outside `entitiesLock`** (`GameEngine.kt:533`).
Unsynchronized → CME / dropped reward indicator on every boss kill / step reward.

**Fix:** introduce a private monitor inside `EffectEngine` and guard every structural touch of the two
lists:

```kotlin
private val effectsLock = Any()

fun addEffect(effect: Effect) { synchronized(effectsLock) { pendingEffects.add(effect) } }

fun update(dt: Float) {
    val snapshot: List<Effect> = synchronized(effectsLock) {
        effects.addAll(pendingEffects); pendingEffects.clear()
        effects.removeAll { it.isFinished }
        effects.toList()
    }
    pool.updateAll(dt)
    snapshot.forEach { it.update(dt) }          // per-effect logic runs OUTSIDE the lock
    if (!reducedMotion) screenShake.update(dt)
}

fun render(canvas: Canvas) {
    pool.renderAll(canvas, particlePaint)
    val snapshot = synchronized(effectsLock) { effects.toList() }
    snapshot.forEach { it.render(canvas) }      // draw OUTSIDE the lock
}

fun clear() {
    synchronized(effectsLock) { effects.clear(); pendingEffects.clear() }
    pool.clear(); screenShake.reset()
}
```

**Design points:**
- Snapshot-under-lock, mutate/draw-outside-lock mirrors `GameEngine.render`'s `entities.toList()` idiom
  (`:529`) — we never hold a monitor across effect `update`/`render` work or a Canvas draw.
- An `Effect`'s `update(dt)` mutates only its **own** internal state (it doesn't structurally touch the
  `effects` list), so running it on the snapshot outside the lock is safe. *The plan must verify no
  `Effect` implementation calls back into `EffectEngine.addEffect` during its `update`* (would need
  re-entrancy consideration) — see OQ-3.
- **`pool` (`ParticlePool`) and `screenShake` are loop-confined** — only touched in `update`/`render`
  from the loop thread (and `clear` from re-init under `entitiesLock`). They are **out of scope**;
  do not add a lock to them.
- The `effects.removeAll { isFinished }` moves inside the lock (it's a structural mutation); the
  ordering (add pending → remove finished → snapshot) preserves today's behaviour exactly.

### D2. Trigger B — `uwStates` structurally mutated off-thread on replay (Medium, confirmed)

**Problem:** `GameEngine.initUWs` does `uwStates.clear()` + `add()` (`:599-609`) with **no lock**, called
from the main thread in `playAgain` (`BattleViewModel.kt:467`) after `configure()→init()` already set
`roundOver=false`. The loop thread iterates `uwStates` in `updateUWs` (`:712`, `:775-776`) under
`entitiesLock`, and the 200ms poll reads `eng.uwStates.map{}` (`BattleViewModel.kt:233`) on a coroutine
thread. `init()` clears `uwStates` under `entitiesLock` (`:323`) but `initUWs` does not → narrow but
reachable CME / IndexOutOfBounds during a replay.

**Fix:**
- Wrap `initUWs`'s `clear()/add()` in `synchronized(entitiesLock)` (reentrant — already guards `init`'s
  `uwStates.clear()` and the `updateUWs` iteration that runs inside `update`'s lock).
- Add a snapshot accessor for the polling read:
  ```kotlin
  fun uwSnapshot(): List<UWState> = synchronized(entitiesLock) { uwStates.toList() }
  ```
  `BattleViewModel`'s poll iterates `eng.uwSnapshot()` instead of `eng.uwStates` (`:233`). `UWState` is
  mutable, but the snapshot copies the **list structure** (the only thing the race corrupts); the poll
  reads scalar fields (`cooldownRemaining`, `cooldownLevel`, `type`) for display — a torn scalar read is
  cosmetic (one stale frame of a cooldown ring), not a crash. The CME is what we're eliminating.

### D3. #191 tests (mirror `GameEngineConcurrencyTest`)

New stress tests; each runs a background "loop" thread against a main-thread mutator over a high
iteration count and asserts no throwable surfaced (the CME reproduces in milliseconds before the fix):

- **A** — `EffectEngineConcurrencyTest`: race `addEffect(...)` (test thread) against a loop thread
  driving `EffectEngine.update(dt)` + `EffectEngine.render(stubCanvas)`. Assert no throwable. (Uses a
  trivial stub `Effect` and a mock/no-op `Canvas`; `render` draws via `pool` + effect `render` — verify
  a JVM-safe Canvas double, else Robolectric.)
- **B** — extend `GameEngineConcurrencyTest` (or a sibling): race replay `initUWs(equipped)` (test
  thread) against a loop thread driving `eng.update(dt)`, with ≥1 equipped UW so `updateUWs` iterates a
  non-empty `uwStates`. Assert no throwable.

---

## Test plan summary

| Test | Kind | Asserts |
|---|---|---|
| `CrashBreadcrumbStoreTest` | Robolectric | record→peek→clear round-trip; stack truncation to `MAX_STACK_CHARS`; newest-wins overwrite; `record` never throws on a malformed throwable |
| `GameLoopThread` guard test | JVM | stub engine whose `update()` throws → loop sets `isRunning=false`, breadcrumb written once, `onLoopError` fired once, no exception propagates off the thread |
| Global-handler chaining test | JVM/Robolectric | the wrapper records a breadcrumb **and** still calls the captured `previous` handler |
| `EffectEngineConcurrencyTest` | JVM/Robolectric | `addEffect` racing `update`+`render` over N iterations → no throwable |
| `uwStates` replay race test | JVM | `initUWs` racing `update()` loop → no throwable |
| `BattleViewModel` battle-error test | JVM | `onLoopError` callback flips `uiState.battleError = true` |

Headline test count rises; exact delta recorded in the plan + CHANGELOG, not here.

## Files touched (anticipated — plan finalises)

**New:**
- `app/src/main/java/.../data/diagnostics/CrashBreadcrumbStore.kt` (+ `CrashBreadcrumb` model)
- Tests: `CrashBreadcrumbStoreTest`, `EffectEngineConcurrencyTest`, the loop-guard test, the
  chaining-handler test, the VM battle-error test.

**Modified:**
- `StepsOfBabylonApp.kt` — inject store, install chaining handler.
- `presentation/battle/GameLoopThread.kt` — store param, `onLoopError`, guarded loop.
- `presentation/battle/GameSurfaceView.kt` — construct store, forward `onLoopError`.
- `presentation/battle/BattleViewModel.kt` — set `onLoopError`, `onBattleLoopError`, poll via
  `uwSnapshot()`.
- `presentation/battle/BattleUiState.kt` — `battleError` field.
- `presentation/battle/BattleScreen.kt` — battle-error overlay.
- `presentation/battle/effects/EffectEngine.kt` — `effectsLock` + guarded add/update/render/clear.
- `presentation/battle/engine/GameEngine.kt` — `initUWs` under `entitiesLock`; `uwSnapshot()`.
- `presentation/MainActivity.kt` — inject store, next-launch breadcrumb snackbar.

## Fragile-zone interactions (CONSTRAINTS-relevant)

- **`entitiesLock` (#118)** — D2 *extends* it to `initUWs`; reentrant, so the loop-thread paths that
  already hold it (`update`→`updateUWs`) are unaffected. Do not introduce a *second* engine lock.
- **A28 collision scratch buffers / A31 chrono Paint / A29 profile-flow** — untouched; D1/D2 don't go
  near the collision sweep or render Paint.
- **#125 / #146 enemy-count derivation** — untouched; no change to `getAliveEnemies()` /
  `aliveEnemyCount()` / `takeDamage`.

## Resolved during spec authoring (were open questions; settled against the code)

- **OQ-1 (decided)** — `GameSurfaceView` builds `CrashBreadcrumbStore(context)` directly (matching how
  it builds `AndroidStrings`). Both that instance and MainActivity's Hilt-injected singleton operate on
  the same `crash_breadcrumb_prefs` SharedPreferences file (keyed by name), so cross-instance visibility
  holds. See §B2.
- **OQ-2 (resolved)** — There is **no** existing in-app feedback/report channel (grep of `settings/` +
  `help/` found only the haptics toggle). The next-launch notice is therefore informational-only, with
  no action button. See Part C.
- **OQ-3 (resolved)** — `addEffect` has a single definition site and **no** `Effect` implementation
  calls it from its own `update(dt)` (effects spawn particles via `pool` directly). The D1
  snapshot-outside-lock is therefore non-re-entrant and safe. See §D1.
