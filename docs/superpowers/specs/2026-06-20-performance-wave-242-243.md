# Spec — Performance wave (#242 · #243)

**Date:** 2026-06-20
**Status:** REVIEWED (single-agent Adversarial Review Gate, ultracode off — developer chose lighter
review; 8 findings F1–F8 [0 critical, 3 major: #242 concurrency model F1/F4/F5, #243 1× density F3], all
applied 2026-06-20). Ready for plan stage.
**Scope:** two confirmed `severity:major` performance findings from the 2026-06-18 complete-app review,
one combined PR. **No schema / economy / engine-formula change** — behaviour-preserving perf fixes.

## Why these two together

Both are confirmed before-public `severity:major` performance defects on hot paths (battle entry/exit
and the per-tick game loop), both are self-contained, and they share the "behaviour-preserving GC/main-
thread-cost reduction" shape this project has shipped before (cf. ADR-0025 A28/A29/A31). One
build-verified PR.

⚠️ **#243 touches a documented fragile zone** — the trail-spawn loop runs inside `GameEngine.update()`
under `entitiesLock`, and `ParticlePool` is part of `presentation/battle/effects/`. The fix must not
change locking, must stay loop-thread-confined, and must preserve the `EffectEngine.pool` /
`entitiesLock` invariants (#118/#191, ADR-0026). See the constraints in each section.

## #242 — Background music decodes a 1.3 MB OGG on the main thread every Battle↔menu nav (medium · small · partial→confirmed)

**Confirmed.** `MusicManager.playWalking()`/`playBattle()` (`MusicManager.kt:42-61`) call `stopActive()`
(`:126-129`, which `release()`s **both** players) then `createPlayer()` (`:110-114` → the
`playerFactory` default `MediaPlayer.create`, `:21`). `MediaPlayer.create()` is documented to prepare
synchronously on the calling thread (open + parse headers + buffer). It is invoked on the **main
thread** from `MainActivity`'s `bottomBar` `LaunchedEffect(currentRoute)` (`MainActivity.kt:234-240`),
which fires `playBattle()`/`playWalking()` on **every** route change to/from `Screen.Battle`. So every
battle entry and every battle exit pays a full synchronous 1.3 MB OGG decode on the main thread →
visible hitch / ANR-risk on low-end SDK-34 devices. There is no caching: the prepared player is
released and rebuilt each transition.

**Fix — cache both prepared players, build off the main thread, switch via pause/start (audit's
preferred option).**

- Create each track's `MediaPlayer` **at most once**, **off the main thread**, then keep it. Track
  switches `pause()` the outgoing player and `start()` the incoming one — no `release()`/recreate on
  navigation. `release()` of both players happens only in `MusicManager.release()` (`onDestroy`).
- **Off-main-thread creation.** `MusicManager` has no coroutine scope today and is constructed directly
  in `MainActivity.onCreate` (`MainActivity.kt:105`). Add a private single-thread `Executor` owned by
  `MusicManager`, used **only** for the blocking `playerFactory(...)` decode call. Constraint: the
  **`MediaPlayer.create` (decode) must not run on the main thread**; the public API
  (`playWalking`/`playBattle`/`pause`/`resume`/`setVolume`/`setMuted`/`release`) stays synchronous and
  main-thread-callable.
- **Concurrency model (F1/F4/F5 — REQUIRED, this is the risky part).** All shared state
  (`desiredTrack`, `activeTrack`, the two player refs, `muted`, `focusLost`, `volume`) and all
  `start()`/`pause()`/`seekTo()`/focus calls are **serialized onto the main thread** — only the decode
  runs on the executor. Concretely:
  - **Split the track state.** Introduce a synchronous `desiredTrack` (the last requested track, set
    immediately by `playWalking`/`playBattle`) distinct from `activeTrack` (what is actually started).
    The early-return dedup guard checks **`desiredTrack`** (so a re-request of the in-flight track is a
    no-op), NOT `activeTrack`. Last-write-wins: `playWalking()` then `playBattle()` before the first
    decode finishes leaves `desiredTrack = BATTLE`.
  - **Deferred start.** When a track is requested and its player isn't built yet, dispatch the decode on
    the executor; on completion, **marshal back to the main thread** (post to a main-thread
    `Handler`/`Looper`) and start the player **only if it is still the `desiredTrack`** (else just keep
    it cached, paused, not started — it was superseded). This handles Battle→menu→Battle faster than a
    decode.
  - **`release()` vs in-flight decode.** A `released` flag: `release()` (onDestroy) sets it, releases
    both built players, abandons focus, and shuts down the executor. A decode callback completing after
    `released` must `release()` the just-built player and NOT start it or re-request focus. No executor
    thread leak.
  - `setMuted`/`setVolume`/focus changes arriving before a player exists stay safe (current
    null-tolerance via `activePlayer()?` is preserved — these run on the main thread, same as today).
- **Track-switch position behaviour:** current code recreates → each track **restarts from 0** on entry.
  Preserve that feel: on switching INTO a cached track, `seekTo(0)` before `start()` so battle music
  begins at the top each battle (no behaviour change from today). `seekTo(0)` is valid on a
  prepared-but-paused player (Prepared/Paused are seekable states).
- **Audio focus:** unchanged — start still requests focus; the `onAudioFocusChange` duck/pause/resume
  logic (`:92-107`) is untouched. Both players exist but only one is started at a time, so focus
  semantics are identical to today (the focus callback also lands on the main thread → same serialized
  state).

**Test.** Extend `MusicManagerNullPlayerTest` (Robolectric + `playerFactory` seam + mockito `MediaPlayer`):
- A track switch (`playWalking()` then `playBattle()` then back) builds **each player at most once** —
  assert `playerFactory` is invoked once per track across repeated switches (the core regression: today
  it's once *per navigation*). Use a counting factory.
- Switching pauses the outgoing player and starts the incoming one (verify `pause()`/`start()` on the
  mocks), and `release()` releases both.
- The off-thread creation must be deterministic in the test: the plan injects the executor (default a
  real single-thread executor; tests inject a **direct/synchronous executor**) so the Robolectric test
  doesn't flake on thread timing. The `playerFactory` seam mocks the *decode*, but the new code
  dispatches that factory call onto the executor — so the synchronous executor is still **required** for
  the once-per-track / `pause()`-`start()`-ordering assertions to be deterministic. **(F8)**
- **Robolectric looper caveat (F8):** with a synchronous executor the post-decode marshal-to-main-thread
  step must also resolve under Robolectric's paused main looper — the test may need
  `shadowOf(Looper.getMainLooper()).idle()` after the trigger, OR the marshal must be a direct call when
  already on the (test) main thread. The plan picks one and the test drives it deterministically.
- The existing null-degrades-to-silent and happy-path assertions must still pass (don't regress #246).

**Out of scope:** switching `MusicManager` to Hilt injection (it's a small, activity-owned object;
keep the direct construction). No change to the two `.ogg` assets or to `SoundManager` (SFX).

## #243 — Projectile-trail effect thrashes the 200-particle pool (medium · small · confirmed)

**Confirmed.** `GameEngine.update()` spawns one trail particle **per alive projectile per tick**
(`GameEngine.kt:476-486`): for every `ProjectileEntity`, `ProjectileTrailEffect.spawn(fx.pool, …)`
→ `pool.acquire()` (`ParticlePool.kt:35-51`). `update()` runs up to 4× per render frame at 4× speed
(GameLoopThread accumulator catch-up, `GameLoopThread.kt:56-65`). With multishot + bounce + high
attack-speed there can be dozens of live projectiles → ~hundreds of `acquire()` calls per visual frame.
The 200-slot pool exhausts within a frame, so `acquire()` falls to its O(capacity) linear scan
(`ParticlePool.kt:37-45`) and then **recycles still-live particles** (`:46-50`), starving death/UW
effects and wasting the pool on trails. Each active particle is its own `canvas.drawCircle`
(`ParticlePool.renderAll`, `:55`).

**Fix — per-projectile time-based trail throttle (audit's primary recommendation).** Emit a trail
particle for a projectile at most once every `TRAIL_INTERVAL` seconds of *projectile sim-time*, by
storing a small last-emit timer on `ProjectileEntity`. **Why this kills the storm (F2, corrected):** the
loop calls `engine.update(TICK_NS/1e9f)` with a **fixed** `deltaTime` (~0.0167 s); at 4× the `while
(accumulator >= TICK_NS)` loop runs ~4× as many times per render frame (`GameLoopThread.kt:56-66`) — so
today the per-frame spawn count scales with both projectile count AND speed. A timer advanced by the
same fixed `deltaTime` bounds emissions to **elapsed projectile sim-time** (one trail per
`TRAIL_INTERVAL` of game-time), so the per-frame spawn count is capped regardless of how many catch-up
`update()` calls a frame batches. Simultaneous trail particles per projectile become
`lifetime / TRAIL_INTERVAL` (`lifetime = 0.3 s`, `ProjectileTrailEffect.kt:13`) — a fixed budget
independent of speed.

- Add a `private var trailTimer: Float` to `ProjectileEntity`, advanced by the trail-spawn code by
  `deltaTime` (NOT inside `ProjectileEntity.update` — keep that delegating purely to `ProjectileState`;
  the trail timer is a presentation concern advanced where the trail is spawned). Emit + reset when it
  crosses `TRAIL_INTERVAL`.
- **`deltaTime` is in scope (F6, resolved):** the trail block at `GameEngine.kt:476-486` is lexically
  inside `fun update(deltaTime: Float)` (signature `:442`, lock `:451`) — no plumbing needed. Advance
  each projectile's timer by that same fixed sim `deltaTime`.
- **`TRAIL_INTERVAL` choice + the 1× density question (F3 — RESOLVED, decision recorded).** Today at 1×
  the trail emits ~60×/sec/projectile (one per ~16.7 ms tick) → ~18 simultaneous particles
  (`0.3 / 0.0167`). That high 1× rate is itself part of the over-spawn. **Decision: `TRAIL_INTERVAL =
  0.03 s`** → ~10 simultaneous particles/projectile at any speed (`0.3 / 0.03`), roughly **halving** 1×
  density while still reading as a continuous trail, and **capping the 4× storm to the same 10** (vs
  today's unbounded). This is a deliberate, tunable named `const`, NOT "invisible at 1×": acceptance
  criterion 5 is reworded accordingly. Flag for the developer's feel sign-off; trivial to retune.
- **Do NOT merge the trail loop into the A28 `projScratch` partition pass (F7).** The trail loop
  (`:480-485`) and the A28 collision-prep partition (`:494-500`) are two separate iterations of
  `entities`. Keep them separate — folding trail emission into A28 would couple a presentation concern
  into the fragile collision-prep zone for no real saving. The trail loop stays exactly where it is.

**Fragile-zone constraints (MUST hold):**
- The trail loop stays exactly where it is — inside `update()` under `entitiesLock` (`:451`), iterating
  `entities` (already lock-held), still gated by `!reducedMotion` (`:477`). Do **not** move it, do
  **not** add a second lock, do **not** touch `EffectEngine`/`effectsLock` ordering.
- `trailTimer` is mutated only on the loop thread (inside `update()`), so it needs no synchronization.
  **Confirmed (review):** `ProjectileEntity` is constructed fresh per shot (`GameEngine.kt:341` primary,
  `:1068` bounce) and **never pooled/reused**, so a field initialised to `0f` at construction is correct
  — no reset logic needed. All construction + trail-ticking happens under `entitiesLock` on the loop
  thread (`pendingAdd` drained at `:468` inside the same lock); no off-loop-thread path touches it.
- No change to `ParticlePool` internals, `acquire()`, or capacity. (Optionally the audit suggests a
  dedicated sub-pool; **out of scope** — the throttle alone resolves the starvation and is lower-risk.)
- No change to collision (`projScratch` partition at `:493-499`), `aliveEnemyCount`, or the #146/#125
  death guards.

**Test.** `ProjectileEntity` / the throttle is pure-ish (a float timer) — extract the throttle decision
as a testable unit if it doesn't force Android imports:
- Pure JVM test of the throttle: feeding N small `deltaTime` steps totalling T seconds yields
  `floor(T / TRAIL_INTERVAL)` (±1) emissions — i.e. emission count tracks elapsed projectile-time, NOT
  call count. The simplest seam: a pure helper `fun advanceTrail(timer, dt): Pair<Boolean, Float>` (emit?
  + new timer) or a method on a pure holder, JVM-tested without `Canvas`/`Paint`.
- Optionally an engine-level assertion is hard (GameEngine is Android-bound); rely on the pure throttle
  test + build verification + on-device/feel check that trails look unchanged at 1× and the pool no
  longer starves at 4× (device-verified, documented boundary).

## Out of scope (explicit non-goals)

- No `MusicManager` → Hilt migration; no asset re-encoding; no SFX/`SoundManager` change.
- No `ParticlePool` capacity change, no dedicated trail sub-pool, no render batching of `drawCircle`.
- No change to the game-loop fixed-timestep / catch-up clamp (`GameLoopThread`/`SimulationMath`).
- No schema / economy / engine-formula change.

## Files expected to change

| File | Change | Issue |
|---|---|---|
| `presentation/audio/MusicManager.kt` | cache both players, off-thread create, pause/start switch, injectable executor | #242 |
| `presentation/audio/MusicManagerNullPlayerTest.kt` (or a new sibling) | once-per-track + switch + release assertions | #242 |
| `presentation/battle/entities/ProjectileEntity.kt` | `trailTimer` field (loop-thread-only) | #243 |
| `presentation/battle/effects/ProjectileTrailEffect.kt` and/or `GameEngine.kt:476-486` | time-based throttle + `TRAIL_INTERVAL` | #243 |
| (new) pure throttle test | emission-tracks-elapsed-time, not call-count | #243 |
| ADR? | likely NO ADR (perf bug-fixes on established patterns; cf. ADR-0025 was a larger infra change). Decide in plan. | — |

## Acceptance criteria

1. Entering/leaving battle no longer decodes the OGG on the main thread; each player is built at most
   once (off-thread) and reused; `MusicManagerNullPlayerTest` proves once-per-track + the #246
   null-degrade path still passes.
2. Trail particles are emitted on a per-projectile time budget, independent of tick frequency; a pure
   JVM test proves emission count tracks elapsed projectile-time, not `update()` call count.
3. No locking / fragile-zone change in `GameEngine`/`EffectEngine`/`ParticlePool`; `entitiesLock` and
   `effectsLock` invariants intact; `GameEngineConcurrencyTest` / `EffectEngineConcurrencyTest` green.
4. `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; JVM count moves only by
   net-new tests; no regressions.
5. Behaviour preserved: music still restarts at the top on battle entry. Trails remain visually
   coherent; the modest 1× density reduction (`TRAIL_INTERVAL = 0.03 s` → ~10 particles/projectile vs
   ~18 today) is an accepted, device-verified trade and the 4× pool-starvation storm is gone.
