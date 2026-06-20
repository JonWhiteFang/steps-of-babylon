# Implementation Plan — Performance wave (#242 · #243)

**Date:** 2026-06-20
**Spec:** `2026-06-20-performance-wave-242-243.md` (REVIEWED — F1–F8 applied)
**Status:** REVIEWED (single-agent plan-stage Adversarial Review Gate, ultracode off; 8 findings —
F-C the real bug [build-once guard must be built-or-in-flight], F-A/F-B/F-D/F-E [#242 test migration +
concurrency precision], F-F/F-G [executor/looper], F-H [add ADR-0033] — all applied 2026-06-20; #243
half confirmed sound). Ready to implement.
**Build/test:** `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` (non-TTY).

Branch (already created): `perf/music-particle-242-243`. TDD per task where there's a seam.

---

## Task 1 — #243 projectile-trail throttle (pure seam first)

**Goal:** cap trail emission to one per `TRAIL_INTERVAL` of projectile sim-time, killing the 4×
pool-starvation storm; behaviour-preserving except the documented 1× density halving.

1. **RED — pure throttle helper test.** New
   `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/effects/ProjectileTrailThrottleTest.kt`
   (JUnit Jupiter, no Android). Assert that feeding N fixed `dt` steps totalling `T` seconds yields
   `floor(T / TRAIL_INTERVAL)` (±1) emissions — i.e. emission count tracks elapsed time, NOT call count.
   Test both 1× cadence (dt=0.0167, many calls) and 4× (same dt, ~4× calls per wall-sec) produce the
   SAME emissions-per-elapsed-sim-second.
2. **GREEN — pure helper in `ProjectileTrailEffect.kt`.** Add a pure top-level/`object` function:
   ```kotlin
   const val TRAIL_INTERVAL = 0.03f   // ~10 simultaneous particles/projectile (lifetime 0.3s); see spec F3
   /** Advance a projectile's trail timer; return (shouldEmit, newTimer). Pure — no Android. */
   fun advanceTrail(timer: Float, dt: Float): Pair<Boolean, Float> {
       val t = timer + dt
       return if (t >= TRAIL_INTERVAL) true to (t - TRAIL_INTERVAL) else false to t
   }
   ```
   (Subtract-interval, not reset-to-zero, so it doesn't drift under variable dt. Single emit per tick is
   fine — at the fixed 0.0167s dt, t never reaches 2×interval in one tick.)
3. **`ProjectileEntity.kt`:** add `var trailTimer: Float = 0f` (loop-thread-only; spec confirms no
   reuse/no sync). Fresh per construction → starts at 0.
4. **`GameEngine.kt:476-486`:** in the existing `!reducedMotion` trail loop (inside `update(deltaTime)`
   under `entitiesLock`), replace the unconditional spawn with:
   ```kotlin
   val (emit, newTimer) = advanceTrail(e.trailTimer, deltaTime)
   e.trailTimer = newTimer
   if (emit) ProjectileTrailEffect.spawn(fx.pool, e.x, e.y, biomeTheme.particleColor)
   ```
   Loop stays exactly where it is — no lock change, no merge with the A28 partition (F7), no
   `ParticlePool`/`EffectEngine` change.
5. Verify RED→GREEN; **mutation-check**: bump `TRAIL_INTERVAL` and confirm the throttle test reacts
   (proves it's not a tautology).

**Files:** `ProjectileTrailEffect.kt`, `ProjectileEntity.kt`, `GameEngine.kt`, new `ProjectileTrailThrottleTest.kt`.

**Fragile-zone checklist:** `entitiesLock` untouched (loop already holds it); `effectsLock` untouched
(no EffectEngine change); `GameEngineConcurrencyTest` / `EffectEngineConcurrencyTest` must stay green.

---

## Task 2 — #242 MusicManager: cache players, off-thread decode, pause/start switch

**Goal:** no main-thread OGG decode on Battle↔menu nav; each player built at most once (off-thread),
reused; all state serialized on the main thread.

1. **RED — extend `MusicManagerNullPlayerTest.kt`** (Robolectric + `playerFactory` + injectable
   **synchronous** executor `Executor { it.run() }`). **Every test that triggers a decode must call
   `shadowOf(Looper.getMainLooper()).idle()` after the trigger** (the marshal always posts to main —
   F-G; needs `org.robolectric.Shadows.shadowOf`):
   - **MIGRATE the existing tests (F-A/F-B — REQUIRED, not just "keep green").** All four current tests
     (`:30-45`, `:47-54` null-degrade; `:57-71` happy-path) drive `playWalking`/`playBattle`, which now
     dispatch via the executor + post. Update each to pass the synchronous executor and `idle()` the
     looper after the trigger, so `assertNull(...)` (`:38,:53`) and `verify(player).start()` (`:71`)
     observe the resolved post-state. Without this they flake/false-green under the new default executor.
   - **Once-per-track:** a counting `playerFactory`; `playWalking() → playBattle() → playWalking() →
     playBattle()` builds WALKING once and BATTLE once (2 factory calls total, not 4).
   - **Build-once-or-in-flight (F-C — the regression guard for the real bug):** with the synchronous
     executor that's not enough to exercise the race; add a test using a **deferred executor** (queues
     runnables, runs on demand) for the interleave `playBattle()` → `playWalking()` →
     `playBattle()` BEFORE running any queued decode → then drain + `idle()`. Assert BATTLE is built
     **at most once** (counting factory) — pre-fix this double-decodes. (If a deferred executor is too
     heavy, assert the pending-flag contract directly via a `@VisibleForTesting` accessor.)
   - **Switch semantics:** on switching INTO a cached track, the incoming player gets `seekTo(0)` then
     start (via `startIfNotMuted`), and the outgoing player gets `pause()` (verify on mocks).
   - **Start-while-muted (F-E):** `setMuted(true)` then `playBattle()` with a deferred decode → after
     drain + `idle()`, `verify(player, never()).start()` and the player is retained (cached, not started).
   - **release():** releases both built players; a decode completing after `release()` (deferred
     executor drained post-release) must `release()` the new player and NOT `start()` it.
2. **GREEN — `MusicManager.kt`.** All shared state + all `MediaPlayer` control calls run on the **main
   thread**; the executor runs ONLY the blocking decode (F-D).
   - **Constructor / executor (F-F):** add `private val decodeExecutor: Executor =
     Executors.newSingleThreadExecutor().also { ownsExecutor = true }` — actually, type the field as
     `Executor` and own the lifecycle separately: keep a `private val ownedExecutorService:
     ExecutorService?` that is non-null only when the default is used; `release()` shuts down ONLY that.
     Tests inject a synchronous `Executor { it.run() }` (no `shutdown()` called on it). Keep the
     existing `playerFactory` param. Also add a `private val mainHandler = Handler(Looper.getMainLooper())`
     (new imports `android.os.Handler`, `android.os.Looper`).
   - **State (F1, main-thread-only — NOT `@Volatile`, F-D):** `private var desiredTrack: Track =
     Track.NONE` (set synchronously by playWalking/playBattle; drives dedup). `activeTrack` stays = what's
     actually started. `private var released = false`. **Build-once-or-in-flight guard (F-C):**
     `private var walkingPending = false` / `private var battlePending = false` (a per-track "decode
     in flight" flag).
   - `playWalking()`/`playBattle()` (main thread):
     - set `desiredTrack = thisTrack` (synchronous, last-write-wins).
     - if the player is already built → switch immediately (pause old, `seekTo(0)`, set `activeTrack`,
       `startIfNotMuted()`); return.
     - else if **already pending for this track** (`thisPending`) → return (a decode is in flight; the
       post will resolve against the latest `desiredTrack`). **This is the F-C fix — dedup on
       built-OR-in-flight, not on `desiredTrack`.**
     - else set `thisPending = true` and dispatch `decodeExecutor.execute { val p =
       playerFactory(ctx, resId); mainHandler.post { onDecoded(thisTrack, p) } }`. The executor runnable
       does **nothing but decode + unconditionally post** (F-D) — no field reads off-thread.
   - `onDecoded(track, player)` (main thread): clear `track`'s pending flag. Then:
     - if `released` → `player?.release()`, return (F-D/lifecycle).
     - if `player == null` (#246 null decode) → leave that track unbuilt; if `desiredTrack == track`,
       set `activeTrack = NONE` so a later nav re-attempts (mirror today's `:47-50`); return.
     - cache + configure the player (`isLooping=true`, `setVolume`).
     - if `desiredTrack != track` → keep it cached, paused, **do NOT start** (superseded).
     - else → switch to it: pause old, `seekTo(0)`, set `activeTrack = track`, **`startIfNotMuted()`**
       (F-E — never a bare `start()`, so a muted user stays silent and focus is requested correctly).
   - `pause`/`resume`/`setVolume`/`setMuted`/`onAudioFocusChange`: unchanged logic, all on the main
     thread, still null-tolerant via `activePlayer()?`.
   - `release()`: `released = true`; release both built players; abandon focus; `ownedExecutorService?.
     shutdown()` (injected executors are NOT shut down — F-F).
   - `createPlayer` keeps `isLooping=true` + `setVolume` config (unchanged; #246 null-tolerance kept) —
     now called from `onDecoded`/the immediate-switch path.
3. **MainActivity.kt** — no change needed (it already calls `playBattle()/playWalking()` from the
   `LaunchedEffect`; the public API is unchanged). Confirm `onCreate` construction (`:105`) still
   compiles with the new defaulted ctor params.
4. Verify RED→GREEN; confirm the once-per-track count.

**Files:** `presentation/audio/MusicManager.kt`, `MusicManagerNullPlayerTest.kt`.

**Risk note:** all `MediaPlayer` control calls + shared-field mutations are on the main thread; only the
blocking decode is off-thread (spec F4/F5). This is the crux — the plan-review must confirm no field is
touched from the executor thread except the local new-player ref handed to the main-thread post.

---

## Task 3 — Build + verify

`./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`. Confirm BUILD SUCCESSFUL; new tests ran;
JVM count = 1130 + net-new; `GameEngineConcurrencyTest`/`EffectEngineConcurrencyTest`/`DomainPurityTest`
green; no regressions. Mutation-check both new tests (Task 1 interval; Task 2 once-per-track count).

---

## Task 4 — Sync current-state docs (PR Task-List Convention, BEFORE STATE/RUN_LOG)

- `CHANGELOG.md` — `[Unreleased]` section for the wave; test count.
- `CLAUDE.md` — headline test count (+net-new). The Battle Renderer / fragile-zone prose: confirm still
  accurate (trail loop unchanged in placement; note the throttle only if it adds clarity — likely no
  change needed since the lock story is unchanged).
- `docs/steering/source-files.md` — update `MusicManager.kt`, `ProjectileTrailEffect.kt`,
  `ProjectileEntity.kt`, `GameEngine.kt` (trail throttle) entries; add the new test(s).
- NOT touched: schema/tech/structure/README (no schema/dep/module/build-instruction change).
- **ADR decision (F-H — ADD ADR-0033).** #243 alone is a bug-fix (no ADR). But the #242 audio change
  introduces a new concurrency model on a previously single-threaded class — the main-thread
  serialization invariant, the build-once-or-in-flight guard, and the release-vs-in-flight ordering.
  Per precedent (ADR-0026 `entitiesLock`, ADR-0020 guarded-deduct), that warrants **new ADR-0033**
  scoped to the audio threading model. (ADR-0033 is the next free number.)

## Task 4b — ADR-0033 (the #242 audio threading model)

New `docs/agent/DECISIONS/ADR-0033-music-player-threading.md`: records the off-main-thread decode +
main-thread-serialized control + `desiredTrack`/`activeTrack` split + build-once-or-in-flight guard +
release-vs-in-flight ordering, and notes #243's throttle as a sibling perf fix (no separate ADR).
References the spec, this plan, and the review.

## Task 5 — STATE + RUN_LOG (end-of-run; `/checkpoint`)

Rotate STATE.md current objective; append RUN_LOG with goal/changes/verification/doc-sync/next.

## Task 6 — Commit + PR

After GREEN + doc sync: commit on the branch, push, open PR (closes #242, #243). Monitor CI, merge on
green (per the developer's standing instruction this session).

---

## Verification checklist (acceptance, from the spec)

- [ ] #242: each player built at most once (counting-factory test), off-thread; switch = pause old +
      seekTo(0)+start new; release() releases both; #246 null-path still green.
- [ ] #243: pure throttle test proves emission tracks elapsed sim-time not call-count; `TRAIL_INTERVAL`
      a named const; trail loop unchanged in placement/locking.
- [ ] No `entitiesLock`/`effectsLock`/`ParticlePool` change; concurrency guard tests green.
- [ ] `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; JVM 1130 + net-new; no regressions.
- [ ] Docs synced; STATE/RUN_LOG updated.
- [ ] On-device feel sign-off flagged (trail density at 1×; music no-hitch on battle nav) — documented
      as a developer/device step, not claimable from the repo.
