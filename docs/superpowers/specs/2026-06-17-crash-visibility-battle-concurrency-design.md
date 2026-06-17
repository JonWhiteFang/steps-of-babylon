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
    fun record(threadName: String, throwable: Throwable, timestampMillis: Long)

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
- The stack is captured from `throwable.stackTraceToString()` and **truncated** to a bounded
  *character* length — pin the constant `MAX_STACK_CHARS = 4096` (Kotlin `String.take(MAX_STACK_CHARS)`;
  "length" here is chars, not bytes) so we never write an unbounded blob to SharedPreferences.
- **One slot, newest-wins** — a second crash overwrites the first. (A soak crash that recurs is more
  signal than the first occurrence; the tester report covers the narrative.)
- `timestampMillis` is a **`record` parameter** the caller supplies (`System.currentTimeMillis()` at
  the call site, the data-layer idiom used by e.g. `WalkingEncounterRepositoryImpl`) — the store itself
  does not read a clock, keeping it trivially testable. Both call sites (A2, B1) pass it. *(This
  resolves the earlier signature contradiction flagged in review: the model carries `timestampMillis`
  but the two-arg signature had no way to populate it without the store reading a clock.)*

### A2. Chaining global handler (`StepsOfBabylonApp.onCreate`)

`StepsOfBabylonApp` gains `@Inject lateinit var crashBreadcrumbStore: CrashBreadcrumbStore` (same
injection style as the existing `workerFactory`). In `onCreate`, **after** `super.onCreate()` and the
existing SQLCipher + WorkManager setup, install:

```kotlin
val previous = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
    runCatching { crashBreadcrumbStore.record(thread.name, ex, System.currentTimeMillis()) }  // breadcrumb FIRST
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

### A3. `crash_breadcrumb_prefs` must be cleared by "Delete All Data"

The new `crash_breadcrumb_prefs` file holds a captured stack-trace preview + exception message — diagnostic
text that is **user-adjacent** and #192-privacy-relevant. The user-facing **Delete All Data** action wipes
a hard-coded allowlist of prefs files in `DataDeletionManager.PREFS_NAMES`
(`data/DataDeletionManager.kt`). Add `"crash_breadcrumb_prefs"` to that allowlist so a stored breadcrumb
does not survive a full local wipe. This also avoids a concrete UX wart: `deleteAllData` ends with
`activity.recreate()`, and Part C peeks the breadcrumb on composition — without the wipe, tapping "Delete
All Data" would immediately surface a stale "game closed unexpectedly" snackbar. (Note: `onboarding_prefs`
and `haptics_prefs` are *also* absent from `PREFS_NAMES` — a pre-existing inconsistency we are **not**
fixing here; this PR only ensures the *new* privacy-relevant file it introduces is covered, not those.)
Add `DataDeletionManager.kt` to the files-touched list and extend `DataDeletionManagerTest` to assert the
breadcrumb file is cleared.

---

## Part B — #190 Part B: guarded game loop + battle-error UI

### B1. `GameLoopThread` guard

`engine.update()` and `engine.render()` have exactly one **production** caller — `GameLoopThread.run()`
(`:48`, `:58`); tests invoke them directly, which is *why* the guard belongs in the thread (not inside
`engine.update()`/`render()`, where it would mask the exceptions those tests assert on). Wrap the
per-iteration update block and render block in a single `try/catch` inside the `while (isRunning)` body:

```kotlin
while (isRunning) {
    // ... timing ...
    try {
        if (!isPaused) { /* accumulator + engine.update(...) loop */ }
        // lockCanvas / engine.render(canvas) / unlockCanvasAndPost  (existing inner try/finally kept)
    } catch (t: Throwable) {
        runCatching { crashBreadcrumbStore.record(name, t, System.currentTimeMillis()) }  // name == "GameLoop"
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
- `record()`/`commit()` runs on the **loop thread** here (the loop is *stopping*, not dying) — safe
  because the loop thread (not main) absorbs the synchronous disk write, so no main-thread ANR / no
  StrictMode-IO concern (no StrictMode is configured). `.commit()` is still chosen for
  write-before-surface durability. (The A1 contract's "process may be dying" rationale covers the A2
  global-handler path; this B1 path is the non-dying caller of the same `record()`.)
- The existing inner `try/finally` around `lockCanvas`/`unlockCanvasAndPost` (`:54-65`) is preserved —
  it guarantees the canvas is always posted/unlocked. The new outer catch sits around the whole body so
  an exception from `engine.update()` (outside the canvas try) or from `engine.render()` (inside it,
  re-thrown after the finally unlocks) is caught and ends the loop. The inner try/finally must stay
  strictly nested *inside* the outer try/catch so the canvas is never left locked on a render crash.

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
- `GameSurfaceView` exposes `var onLoopError: ((Throwable) -> Unit)?`. The setter forwards to the
  current `gameThread`, **and** — because threads are recreated on every `surfaceCreated` — the new
  `GameLoopThread` built in `surfaceCreated` must be **re-seeded** from the stored field
  (`thread.onLoopError = onLoopError`), exactly the two-part pattern `pendingSpeed`/`pendingPaused`
  use (setter writes the field + the live thread; `surfaceCreated` re-applies the field onto the fresh
  thread — `GameSurfaceView.kt:99-103`). This matters more for `onLoopError` than for those fields:
  `startPollingEngine` sets it **once** (on the `isLoading` true→false transition), not on every toggle,
  so a missing re-seed would silently lose the battle-error UI callback after any background→resume
  (the loop would still record the breadcrumb directly, but `battleError` would never surface).
- `BattleViewModel.startPollingEngine` sets `surfaceView.onLoopError = { t -> onBattleLoopError(t) }`.
  The callback fires on the loop thread, so the VM marshals onto its state via
  `_uiState.update { it.copy(battleError = true) }` (StateFlow update is thread-safe).
- **`onBattleLoopError(t)` must also stop the round cleanly without committing suspect state.** A loop
  crash leaves `engine.roundOver == false`, `roundEnded == false`, and the engine non-null, so left
  alone (a) the 200 ms polling `while (true)` loop keeps reading the frozen engine and overwriting HUD
  state under the error overlay (its only exits are `engine == null` and `eng.roundOver && !roundEnded`
  — `BattleViewModel.kt:220,248`), the `SimulationEvent` collector stays subscribed, and (b) when the
  player taps "Return to menu" → `onExitBattle` → `popBackStack` → `onCleared`, the
  `!roundEnded && hasWaveProgress()` guard (`:485-488`) fires the **full end-of-round persistence
  fan-out**, committing best-wave / milestone PS / battle-stats computed from a corrupt, half-mutated
  engine. Both are wrong. The fix: `onBattleLoopError` sets a `@Volatile`/state flag that **breaks the
  poll loop** (add a `battleError`-gated `break` at the top of the `while (true)`), and sets
  `roundEnded = true` so the `onCleared` persistence guard short-circuits. It must **NOT** set
  `eng.roundOver = true` (that would route through `endRound` → `runEndRoundPersistence` and commit the
  suspect numbers — the opposite of what we want). Net post-crash state: loop stopped, poll stopped,
  collector cancelled on scope teardown, `roundEndState` stays null, only the error overlay shows, and
  **no persistence runs on the crashed round.** The plan must specify the exact break/flag mechanism and
  test the no-persist-on-crash path.

### B3. `battleError` UI state + overlay

- `BattleUiState` gains `val battleError: Boolean = false`.
- `BattleScreen` renders a **non-dismissable** "Battle error" overlay when `state.battleError` is true,
  placed as the **last child before the trailing `SnackbarHost`** (`:225`) so only the snackbar sits
  above it. Copy: a short apology + a single "Return to menu" CTA wired to the existing `onExitBattle`
  lambda (same exit path `PostRoundOverlay` already uses). No "retry" — the loop has stopped and the
  engine state is suspect.
- **The overlay must SUPPRESS the interactive round chrome, not merely paint over it.** The
  `PauseOverlay`/`PostRoundOverlay` scrims are transparent `Box`es with **no** touch-catching modifier,
  so a higher-z overlay does not block taps to earlier-declared siblings at the same coordinates. The
  quit `IconButton` (`:150`), control rail (`:176`), and UW bar (`:160`) sit at the screen edges
  (outside the centered error card) and are gated on `roundActive = roundEndState == null` (`:68`) —
  which stays `true` on a mid-round crash (we deliberately don't set `roundEndState`). So they would
  remain tappable *through* the error scrim, letting a tester drive the stopped engine. Fix: gate all
  round chrome on `roundActive && !state.battleError` (e.g. a `showGameChrome` flag) — quit button,
  control rail, UW bar, in-round upgrade menu, pause overlay, and the post-round overlay are all
  suppressed when `battleError` is true.
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
in `update()` (`EffectEngine.kt:22`) and **iterates `effects` in `render()`** (`effects.forEach { it.render(canvas) }`,
`EffectEngine.kt:31`) — reached via `GameEngine.render`'s `fx?.render(canvas)` call **outside
`entitiesLock`** (`GameEngine.kt:533`). Unsynchronized → CME / dropped reward indicator on every boss
kill / step reward.

**Fix:** introduce a private monitor inside `EffectEngine` and guard every structural touch of the two
lists:

```kotlin
private val effectsLock = Any()

fun addEffect(effect: Effect) { synchronized(effectsLock) { pendingEffects.add(effect) } }

fun update(dt: Float) {
    // Drain pending into effects + snapshot, all under the lock. removeAll is deferred to AFTER the
    // per-effect update so ordering matches today's update→removeAll exactly (see design point).
    val snapshot: List<Effect> = synchronized(effectsLock) {
        effects.addAll(pendingEffects); pendingEffects.clear()
        effects.toList()
    }
    pool.updateAll(dt)
    snapshot.forEach { it.update(dt) }          // per-effect logic runs OUTSIDE the lock
    synchronized(effectsLock) { effects.removeAll { it.isFinished } }   // sweep AFTER update, as today
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
  `effects` list), so running it on the snapshot outside the lock is safe. Verified (OQ-3): `addEffect`
  has a single definition site and no `Effect` implementation calls it from `update(dt)` — so the
  snapshot-outside-lock is non-re-entrant.
- **Ordering preserves today's behaviour exactly.** Today's `update()` runs
  `forEach { update(dt) }` **then** `removeAll { isFinished }` (`EffectEngine.kt:24-25`), so an effect
  that crosses its duration *this* tick is swept this tick. A naïve rewrite that moved `removeAll` to
  the **front** (before the per-effect update) would defer that sweep by one tick — a 1-frame cosmetic
  lifetime extension. To avoid even that benign delta (and so the spec's "no behaviour change" claim is
  literally true), the sweep is deferred: drain+snapshot under the lock, run `update(dt)` on the
  snapshot outside the lock, **then** re-acquire the lock for `removeAll { isFinished }` — matching the
  current update→removeAll order. (The two extra lock acquisitions per tick are uncontended in the
  common case and trivially cheap.)
- **`pool` (`ParticlePool`) and `screenShake` are loop-confined** — every access (`pool.acquire`/
  `updateAll`/`renderAll`, `screenShake.trigger`/`update`/`apply`) is on the loop thread via
  `update()`/`render()`; the off-thread VM `addEffect` calls only touch `pendingEffects` (now under
  `effectsLock`). They are **out of scope**; do not add a lock to them. *(Rationale note: `init()` does
  not call `EffectEngine.clear()` — it constructs a fresh `EffectEngine` (`GameEngine.kt:335`);
  `clear()` is currently uncalled. We still guard `clear()` for future-proofing, but the
  "clear from re-init" path does not exist today.)*
- **Lock ordering is acyclic.** The only nesting is `entitiesLock` (outer, held across the whole tick
  in `GameEngine.update`) → `effectsLock` (inner, taken by `effectEngine.update`/`addEffect`). Off-thread
  `addEffect` callers take `effectsLock` only (leaf); `EffectEngine` holds no `GameEngine` reference, so
  there is no reverse `effectsLock → entitiesLock` path → no deadlock.

### D2. Trigger B — `uwStates` structurally mutated off-thread on replay (Medium, confirmed)

**Problem:** `GameEngine.initUWs` does `uwStates.clear()` + `add()` (`:599-609`) with **no lock**, called
from the main thread in `playAgain` (`BattleViewModel.kt:467`) after `configure()→init()` already set
`roundOver=false`. The loop thread iterates `uwStates` in `updateUWs` (`:712`, `:775-776`) while holding
`entitiesLock` for the whole tick, and the 200 ms poll reads `eng.uwStates.map{}` (`BattleViewModel.kt:233`)
on the **main thread** (the poll runs on `viewModelScope`, i.e. `Dispatchers.Main.immediate`, with no
dispatcher override — `BattleViewModel.kt:210`). `init()` clears `uwStates` under `entitiesLock` (`:323`)
but `initUWs` does not → narrow but reachable CME / IndexOutOfBounds during a replay.

**Fix:**
- Wrap `initUWs`'s `clear()/add()` in `synchronized(entitiesLock)`. The property that makes this correct
  is **mutual exclusion**: `initUWs` runs on the main thread while the loop thread holds `entitiesLock`
  across the whole `update()` tick (including the `updateUWs` `uwStates` iteration), so the main thread
  blocks until the tick releases it and can never mutate the list mid-iteration. (The monitor's
  *reentrancy* is separately relevant only to the loop-thread `updateUWs→activateUW→applyStats`
  self-re-acquire path — it is not what protects `initUWs`.)
- Add a snapshot accessor for the polling read:
  ```kotlin
  fun uwSnapshot(): List<UWState> = synchronized(entitiesLock) { uwStates.toList() }
  ```
  `BattleViewModel`'s poll iterates `eng.uwSnapshot()` instead of `eng.uwStates` (`:233`). `UWState` is
  mutable, but the snapshot copies the **list structure** (the only thing the race corrupts); the poll
  reads scalar fields (`cooldownRemaining`, `cooldownLevel`, `type`) for display — a torn scalar read is
  cosmetic (one stale frame of a cooldown ring), not a crash. The CME is what we're eliminating.
- **Other `uwStates` touch sites are safe / left untouched, deliberately:** `activateUW`'s
  `uwStates.getOrNull(index)` (`:633`) is bounds-safe and loop-confined (only called from `updateUWs`
  under `entitiesLock`); `resetUWCooldowns` (`:784`) iterates `uwStates` unguarded but has **zero
  callers** (dead code) and performs only scalar writes (not structural mutation, so no CME). Neither is
  a live race. We do **not** guard them in this PR; the fragile-zone note records that any future PR
  wiring `resetUWCooldowns` to an off-thread caller must put it under `entitiesLock`.

### D3. #191 tests (mirror `GameEngineConcurrencyTest`)

New stress tests; each runs a background "loop" thread against a main-thread mutator over a high
iteration count and asserts no throwable surfaced (the CME reproduces in milliseconds before the fix):

- **A** — `EffectEngineConcurrencyTest` (**plain-JVM JUnit Jupiter**, no Robolectric): race
  `addEffect(...)` (test thread) against a loop thread driving `EffectEngine.update(dt)` +
  `EffectEngine.render(mock<Canvas>())`. Assert no throwable. Plain JVM is sufficient because
  `unitTests.isReturnDefaultValues = true` (`app/build.gradle.kts:198`) lets the `Paint` field
  construct and `mock<Canvas>()` no-ops the `drawCircle`/`drawText` calls — the existing plain-JVM
  `GameEngineConcurrencyTest` (constructs a `GameEngine` with `Paint` fields) and `BattleViewModelTest`
  (drives `FloatingText` through `addEffect`) are the precedent.
- **B** — extend `GameEngineConcurrencyTest` (plain-JVM Jupiter, same class): race replay
  `initUWs(equipped)` (test thread) against a loop thread driving `eng.update(dt)`, with ≥1 equipped UW
  so `updateUWs` iterates a non-empty `uwStates`. Assert no throwable.

---

## Test plan summary

| Test | Runner | Asserts |
|---|---|---|
| `CrashBreadcrumbStoreTest` | Robolectric (JUnit4 + `@RunWith(RobolectricTestRunner)` + `@Config(sdk=[34], application=Application::class)`, mirror `OnboardingPreferencesTest` 1:1) | record→peek→clear round-trip; stack truncation to `MAX_STACK_CHARS`; newest-wins overwrite; `record` never throws on a malformed throwable; caller-supplied `timestampMillis` round-trips |
| `GameLoopThread` guard test | plain JVM (Jupiter) | `mock<GameEngine>()` whose `update()` throws on the **first** call → start thread (`isPaused=false`), `join(timeout)`, then assert: join completed, `isRunning=false`, `onLoopError` fired exactly once, breadcrumb recorded exactly once. **No `sleep-then-assert`** — join-then-assert (mirror `GameEngineConcurrencyTest`'s `AtomicReference` + `join` discipline). Also assert the canvas is left unlocked after a render-thrown crash. |
| Global-handler chaining test | plain JVM (Jupiter) | the wrapper records a breadcrumb **and** still calls the captured `previous` handler. Invoke the closure directly with a recording stub `previous` — do **not** install a real global handler (no JVM-global teardown needed). |
| `EffectEngineConcurrencyTest` | plain JVM (Jupiter), `mock<Canvas>()` | `addEffect` racing `update`+`render` over N iterations → no throwable |
| `uwStates` replay race test | plain JVM (Jupiter, in `GameEngineConcurrencyTest`) | `initUWs` racing `update()` loop → no throwable |
| `BattleViewModel` battle-error + no-persist test | plain JVM (Jupiter) | `onBattleLoopError` flips `uiState.battleError = true`, breaks the poll, and (via `roundEnded=true`) makes `onCleared` skip end-of-round persistence on the crashed round (assert no repository writes) |

Runner discipline: the repo runs Jupiter and JUnit4+Robolectric side by side under one JUnit Platform.
Do **not** put a JUnit4 `@RunWith` on a Jupiter class or a Jupiter `@Test` in a Robolectric class — either
silently fails to run. New JVM tests are Jupiter (mirror `GameEngineConcurrencyTest`/`BattleViewModelTest`);
`CrashBreadcrumbStoreTest` is JUnit4+Robolectric (mirror `OnboardingPreferencesTest`).

Headline test count rises; exact delta recorded in the plan + CHANGELOG, not here.

## Files touched (anticipated — plan finalises)

**New:**
- `app/src/main/java/.../data/diagnostics/CrashBreadcrumbStore.kt` (+ `CrashBreadcrumb` model)
- Tests: `CrashBreadcrumbStoreTest`, `EffectEngineConcurrencyTest`, the loop-guard test, the
  chaining-handler test, the VM battle-error test.

**Modified:**
- `StepsOfBabylonApp.kt` — inject store, install chaining handler.
- `presentation/battle/GameLoopThread.kt` — store param, `onLoopError`, guarded loop.
- `presentation/battle/GameSurfaceView.kt` — construct store, forward + **re-seed** `onLoopError` on
  `surfaceCreated`.
- `presentation/battle/BattleViewModel.kt` — set `onLoopError`; `onBattleLoopError` (set `battleError`,
  break the poll, set `roundEnded=true` to suppress `onCleared` persistence); poll via `uwSnapshot()`.
- `presentation/battle/BattleUiState.kt` — `battleError` field.
- `presentation/battle/BattleScreen.kt` — battle-error overlay + suppress round chrome on `battleError`.
- `presentation/battle/effects/EffectEngine.kt` — `effectsLock` + guarded add/update(deferred sweep)/render/clear.
- `presentation/battle/engine/GameEngine.kt` — `initUWs` under `entitiesLock`; `uwSnapshot()`.
- `data/DataDeletionManager.kt` — add `"crash_breadcrumb_prefs"` to `PREFS_NAMES` (A3).
- `presentation/MainActivity.kt` — inject store, next-launch breadcrumb snackbar.
- `DataDeletionManagerTest` — assert the breadcrumb file is cleared by `deleteAllData`.

## Fragile-zone interactions (CONSTRAINTS-relevant)

- **`entitiesLock` (#118)** — D2 *extends* it to `initUWs` (main-thread mutual exclusion against the
  loop tick). The loop-thread paths that already hold it (`update`→`updateUWs`, incl. the reentrant
  `activateUW→applyStats` re-acquire) are unaffected. Do not introduce a *second* engine lock. Lock
  order is `entitiesLock` → `effectsLock` (acyclic).
- **`resetUWCooldowns` (`:784`) left unguarded on purpose** — it is dead (zero callers) and does scalar
  writes only (no CME). Any future PR that wires it to an off-thread caller MUST wrap it in
  `synchronized(entitiesLock)`.
- **`GameEngine.effectEngine` field is non-`@Volatile` and read off-lock in `render` (`:522`)** — this
  is **pre-existing** and **out of scope**; the new `effectsLock` guards the *lists inside* an
  `EffectEngine`, not the `effectEngine` *reference* on `GameEngine`. Do not assume D1 fixes the
  reference visibility (it doesn't; the reference is benign-stale at worst, never an NPE/CME).
- **A28 collision scratch buffers / A31 chrono Paint / A29 profile-flow** — untouched; D1/D2 don't go
  near the collision sweep or render Paint.
- **#125 / #146 enemy-count derivation** — untouched; no change to `getAliveEnemies()` /
  `aliveEnemyCount()` / `takeDamage`.
- **Economy / schema / balance** — unchanged. The `uwStates.map` → `uwSnapshot()` poll swap is
  display-only; no Room entity/DAO/migration, no `Currency`/cost formula, no balance constant is touched.

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
