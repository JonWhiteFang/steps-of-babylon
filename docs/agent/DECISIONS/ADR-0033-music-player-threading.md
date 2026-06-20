# ADR-0033: Background-music player caching + threading model

Status: Accepted (2026-06-20)

## Context

The 2026-06-18 complete-app review filed two `severity:major` performance findings on hot paths:

- **#242** — `MusicManager.playWalking()`/`playBattle()` called `stopActive()` (which `release()`d both
  players) then `MediaPlayer.create()` on **every** Battle↔menu navigation. `MediaPlayer.create()`
  prepares synchronously (open + parse + buffer a 1.3 MB OGG) on the calling thread, and it was invoked
  on the **main thread** from `MainActivity`'s `bottomBar` `LaunchedEffect(currentRoute)`. Result: a
  visible main-thread hitch / ANR-risk on low-end SDK-34 devices on a very frequent navigation, with no
  caching (release + re-decode each transition).
- **#243** — the projectile-trail effect spawned one particle per alive projectile per `update()` tick,
  and `update()` runs up to 4× per render frame at 4× speed (GameLoopThread catch-up), thrashing the
  200-slot `ParticlePool` and starving death/UW effects.

`MusicManager` was previously a single-threaded, main-thread-only class with no coroutine scope or
executor; it is constructed directly in `MainActivity.onCreate` (not Hilt-injected).

## Decision

### #242 — cache both players, decode off the main thread, switch via pause/start

- Each track's `MediaPlayer` is built **at most once**, **off the main thread** (a `decodeExecutor` runs
  ONLY the blocking `playerFactory(...)` decode), then **cached**. Navigation `pause()`es the outgoing
  player and `start()`s the incoming one — no release/recreate. `release()` (onDestroy) frees both.
- **Concurrency invariant (the core of this ADR):** ALL shared state (`desiredTrack`, `activeTrack`, the
  two player refs, the pending flags, `muted`, `focusLost`, `volume`, `released`) and ALL `MediaPlayer`
  control calls (`isLooping`/`setVolume`/`seekTo`/`start`/`pause`) run on the **main thread**. The
  executor runnable does nothing but decode and `mainHandler.post { onDecoded(track, player) }` — it
  reads no shared field off-thread. `MediaPlayer` permits cross-thread control as long as calls are not
  concurrent; serializing every control call to the main thread satisfies that.
- **State split:** `desiredTrack` (last requested, set synchronously, last-write-wins) is distinct from
  `activeTrack` (what is actually started). The frequent menu→menu navigations that all call
  `playWalking()` no-op via `activeTrack == track`.
- **Build-once-or-in-flight guard:** a per-track `pending` flag dedups in-flight decodes, so a request
  arriving mid-decode (e.g. Battle→menu→Battle faster than a decode) never dispatches a second decode.
  (Dedup on built-OR-in-flight, NOT on `desiredTrack` alone — the latter double-decodes on an
  A→B→A interleave.)
- **`onDecoded` (main thread)** clears the pending flag, then: if `released` → release the just-built
  player and return; if the decode failed (null, #246) → degrade to silent, leave the track unbuilt for
  a later re-attempt; else cache + configure, and start it **via `startIfNotMuted()`** only if it is
  still `desiredTrack` (a superseded track stays cached + paused). `startIfNotMuted()` — never a bare
  `start()` — preserves the muted/focus contract.
- **Track-restart feel preserved:** switching INTO a cached track `seekTo(0)` before start, matching the
  pre-#242 recreate behaviour (each track begins at the top on entry).
- **Executor lifecycle:** the owned single-thread `ExecutorService` is shut down in `release()`; an
  injected (test) executor is left alone. Tests inject a synchronous executor + idle the main looper.

### #243 — per-projectile time-based trail throttle

- A `trailTimer` on `ProjectileEntity` (loop-thread-only, under `entitiesLock`; the entity is built
  fresh per shot and never pooled, so `0f` needs no reset) is advanced by the fixed sim `deltaTime` via
  a pure `advanceTrail(timer, dt)` helper; a trail emits at most once per `TRAIL_INTERVAL = 0.03 s` of
  projectile sim-time. This bounds the per-frame spawn count to elapsed sim-time, immune to the 2×/4×
  catch-up (more fixed-dt ticks, not larger ones), capping simultaneous trail particles per projectile
  to `lifetime / TRAIL_INTERVAL` ≈ 10. The trail loop stays exactly where it is (under `entitiesLock`,
  `!reducedMotion`-gated) — no lock change, no `ParticlePool`/`EffectEngine` change, no merge with the
  A28 collision-prep partition.

## Rationale

- **Cache over recreate (#242).** The only behaviour-relevant property of the old recreate was
  "restart at the top"; `seekTo(0)` reproduces it without the decode cost. Caching two prepared players
  costs a bounded amount of memory for a large, frequent main-thread win.
- **Main-thread serialization over locks.** `MusicManager`'s API is already main-thread-called; keeping
  all state + control there (only the decode off-thread) avoids introducing a lock and the
  `MediaPlayer`-concurrent-call hazard. Simpler and matches the class's existing shape.
- **Time-based throttle over a count cap or sub-pool (#243).** A per-projectile time budget is immune to
  both speed and projectile-count growth, needs no speed plumbing into the engine, and is the audit's
  primary recommendation. A dedicated trail sub-pool was considered and rejected as higher-risk for no
  added benefit once the storm is throttled.
- **Accepted 1× density trade.** `TRAIL_INTERVAL = 0.03 s` roughly halves 1× trail density (~18 → ~10
  simultaneous particles/projectile). That high 1× rate was itself part of the over-spawn; the constant
  is tunable and flagged for the developer's feel sign-off.

## Consequences

- No schema / economy / engine-formula change. New tests: `ProjectileTrailThrottleTest` (4),
  `MusicManagerNullPlayerTest` +5 (build-once, switch, in-flight race, muted-deferred, release-race;
  3 existing migrated to the synchronous-executor + looper-idle model). 1130 → 1139 JVM. Both new
  suites mutation-verified.
- `MusicManager` gains a constructor param (`injectedExecutor`, defaulted) — `MainActivity` construction
  unchanged. The off-main-thread decode means a track may start a few ms after navigation (imperceptible;
  far better than the prior synchronous hitch).
- Fragile zones intact: `entitiesLock`/`effectsLock`/`ParticlePool` untouched;
  `GameEngineConcurrencyTest` / `EffectEngineConcurrencyTest` green.
- On-device feel sign-off (no battle-nav hitch; 1× trail density) is a developer/device step, not
  claimable from the repo.

## References

- Spec: `docs/superpowers/specs/2026-06-20-performance-wave-242-243.md` (reviewed).
- Plan: `docs/superpowers/specs/2026-06-20-performance-wave-242-243-plan.md` (reviewed).
- Issues #242, #243; review `docs/reviews/2026-06-18-complete-app-review.md`.
- Related: ADR-0025 (the A28/A29/A31 behaviour-preserving GC-churn perf fixes); ADR-0026 (`entitiesLock`/
  `effectsLock` fragile-zone invariants); ADR-0006 (the audio/ads SDK integration).
