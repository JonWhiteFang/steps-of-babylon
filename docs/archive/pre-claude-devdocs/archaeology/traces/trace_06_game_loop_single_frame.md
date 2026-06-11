# Trace 06 — Single frame of the game loop

*Phase 3 Deep Trace. Ground truth:
`presentation/battle/GameLoopThread.kt`,
`presentation/battle/engine/GameEngine.kt`,
`presentation/battle/entities/*`,
`presentation/battle/engine/CollisionSystem.kt`,
`presentation/battle/engine/WaveSpawner.kt`,
`presentation/battle/effects/EffectEngine.kt`. Only runs inside the
battle; all other screens are event-driven Compose.*

## 1. Entry Point

- `GameLoopThread.run()` — started by `GameSurfaceView.surfaceCreated`
  (trace 05) on a new `Thread("GameLoop")`.
- Java thread `while (isRunning)` loop, polled at whatever the OS
  wakes us up at; internal sleep tries to cap at ~60 Hz.
- `isRunning` is a `@Volatile Boolean` toggled on surface destroy from
  the main thread.

## 2. Execution Path

### 2.1 The thread's `while (isRunning)` body

```
var previousTime = System.nanoTime()
var accumulator = 0L
var frameCount = 0
var fpsTimer = System.nanoTime()

while (isRunning) {
    currentTime = System.nanoTime()
    elapsed     = currentTime - previousTime
    previousTime = currentTime

    if (!isPaused) {
        accumulator += (elapsed * speedMultiplier).toLong()     [speedMultiplier is Volatile]
        while (accumulator >= TICK_NS) {                         [TICK_NS = 16_666_667 ≈ 60 Hz]
            engine.update(TICK_NS / 1e9f)                       [fixed timestep: always 0.01667 s]
            accumulator -= TICK_NS
        }
    }

    canvas = null
    try {
        canvas = surfaceHolder.lockCanvas()
        if (canvas != null) {
            synchronized(surfaceHolder) {                        [briefly blocks surfaceDestroyed]
                engine.render(canvas)
            }
        }
    } finally {
        canvas?.let { try { surfaceHolder.unlockCanvasAndPost(it) } catch (_) {} }
    }

    frameCount++
    if (currentTime - fpsTimer >= 1_000_000_000L) {
        fps = frameCount
        frameCount = 0
        fpsTimer = currentTime
    }

    // Voluntary sleep to stay near 60 Hz
    val frameTime = System.nanoTime() - currentTime
    val sleepMs = (TICK_NS - frameTime) / 1_000_000
    if (sleepMs > 0) {
        try { sleep(sleepMs) } catch (_: InterruptedException) {}
    }
}
```

Key points:

- **Fixed update timestep, variable render rate.** `engine.update`
  always receives the same `deltaTime = 0.016666667f`. On slow frames,
  the `while (accumulator >= TICK_NS)` catches up by running multiple
  updates per render. On fast frames, the sleep holds us back.
- **Speed multiplier scales the accumulator, not the timestep.**
  At `speedMultiplier=2f`, each wall-clock 16 ms bumps the
  accumulator by 33 ms, which triggers 2 updates per render. This
  keeps physics deterministic across speeds.
- **Pause stops update calls but not render calls.** The last
  rendered frame remains on screen — the scene is frozen.

### 2.2 `GameEngine.update(deltaTime)` — one tick

```
if (roundOver) return
val zig = ziggurat ?: return                    [engine not yet inited]
elapsedTimeSeconds += deltaTime
backgroundRenderer?.update(deltaTime)           [ambient particles drift]
effectEngine?.update(deltaTime)                 [floating text, screen shake, UW vfx]

// Overdrive expiry
if (activeOverdrive != null) {
    overdriveTimeRemaining -= deltaTime
    ziggurat.overdriveProgress = (overdriveTimeRemaining / 60f).coerceIn(0f, 1f)
    if (overdriveTimeRemaining <= 0f) expireOverdrive()
}
updateUWs(deltaTime)                            [cooldowns tick down; black-hole / poison-swamp DoT]

// Wave announcements on edge-trigger
val currentWave = waveSpawner.currentWave
if (currentWave != lastWave) { triggerWaveAnnouncement(currentWave) }

waveSpawner.update(deltaTime, screenWidth, screenHeight)   [spawn / cooldown phase tick]

entities.addAll(pendingAdd); pendingAdd.clear()            [deferred adds this frame]
entities.forEach { it.update(deltaTime) }                   [ziggurat, projectiles, enemies, orbs]

// Projectile trails (skipped if reduced motion)
if (!reducedMotion) entities.filterIsInstance<ProjectileEntity>().forEach {
    ProjectileTrailEffect.spawn(pool, it.x, it.y, biomeTheme.particleColor)
}

CollisionSystem.checkCollisions(
    entities, zig.x, zig.y, zig.width,
    onProjectileHitEnemy = ::onProjectileHitEnemy,          [see trace 07]
    onEnemyProjectileHitZiggurat = { proj ->
        applyDamageToZiggurat(proj.damage, null)
        proj.isAlive = false
    },
)
entities.removeAll { !it.isAlive }                          [single sweep per tick]

if (zig.currentHp <= 0.0) { roundOver = true; soundManager?.play(ROUND_END) }
```

### 2.3 `GameEngine.render(canvas)` — one frame

```
backgroundRenderer?.render(canvas) ?: canvas.drawColor(0xFF6B3A2A)  [fallback brown]
effectEngine?.screenShake?.apply(canvas)                             [canvas.translate]
entities.forEach { it.render(canvas) }                                [z-order by insertion order]
effectEngine?.render(canvas)                                           [particles, text, auras]
if (chronoActive) { draw full-screen translucent blue rect }
effectEngine?.screenShake?.restore(canvas)                             [canvas.restore]
ziggurat.let { healthBarRenderer.render(canvas, currentHp, maxHp, screenWidth) }
```

Note that entity order is insertion order. The ziggurat is inserted
first in `engine.init`, then projectiles/orbs/enemies via `pendingAdd`.
So the ziggurat draws behind its own projectiles and enemies. Enemies
and projectiles are in the same `entities` list intermixed.

## 3. Resource Management

| Concern | How |
|---|---|
| Canvas | `lockCanvas()` / `unlockCanvasAndPost(canvas)` inside try/finally. `synchronized(surfaceHolder)` limits the wrapping scope so `surfaceDestroyed` can reclaim. |
| Thread | Single dedicated `GameLoopThread`. Shared state with main/VM thread via `@Volatile` (`isRunning`, `isPaused`, `speedMultiplier`, and all engine tracking fields like `cash`, `totalEnemiesKilled`, `activeOverdrive`). |
| Entity lists | `entities: MutableList<Entity>` + `pendingAdd: MutableList<Entity>`. Both accessed only on the game thread. `pendingAdd.clear()` at the top of each update prevents unbounded growth. |
| Effect pool | `EffectEngine` owns a `ParticlePool(capacity=200)` — particles are allocated once and reused. `reducedMotion` flag zeroes the `spawn` methods at call sites. |
| GC pressure | Minimal: effect paints are pre-built, the entity list reuses its backing array, projectile trail spawn re-uses pool slots. Hot lambda allocations: a few per tick (Kotlin closure overhead). |
| Sleep | `sleep(sleepMs)` is the throttle. Voluntary — the OS can schedule us any time. |
| Surface | SurfaceView's double-buffer handles tearing. `unlockCanvasAndPost` swaps buffers. |

## 4. Error Path

- **`lockCanvas` returns null** — happens if the surface is being
  destroyed. `if (canvas != null)` guard skips render; loop continues.
- **`unlockCanvasAndPost` throws** — wrapped in silent try/catch. The
  next frame will try again.
- **`engine.update` throws** — exception propagates out of the thread
  body, terminating the thread. No `UncaughtExceptionHandler` is
  installed at thread creation, so it'll use the JVM default (abort).
  On Android this manifests as a crash with `main` still running —
  the user sees the surface view freeze but the Compose overlays still
  respond. In practice, engine bugs should be caught in tests.
- **`engine.render` throws** — same, uncaught.
- **Thread interrupted during `sleep`** — caught silently, loop continues.
- **Thread stuck in a long `render`** — `surfaceDestroyed` waits on
  `thread.join(1000)`. Afterwards the thread continues until it
  finishes, then exits naturally because `isRunning=false`.

## 5. Performance Characteristics

At idle (no enemies, no particles):

- `engine.update(dt)` is ~50 µs mostly spent on `entities.forEach` (1
  entity: the ziggurat).
- `engine.render(canvas)` is ~1-2 ms for the biome background, 5
  ziggurat layers, HP bar.
- Frame budget: ~14 ms remaining → sleep for ~14 ms.

At "wave 20 spawning" with 10-20 enemies + projectiles + orbs + effects:

- `entities.size` may hit 40-60.
- `CollisionSystem.checkCollisions` is O(projectiles × enemies +
  enemyProjectiles × 1). Few hundred checks per tick at worst.
- `ProjectileTrailEffect.spawn` for each live projectile (maybe 5-10
  per tick when attack speed is high).
- `engine.render` costs rise roughly linearly with entity count plus
  particle count (up to 200 particles). Still stays well under 16 ms
  on a mid-tier device.

Speed multiplier 4× multiplies `engine.update` frequency by 4, not
render. So at 4× the thread does:
- `engine.update` × 4 per render
- `engine.render` × 1 per render
The render can drop to effectively 15 Hz wall-clock if update takes
long at these speeds. Perceived as choppy; this is the tradeoff of
4×.

FPS counter (`fps` field) is read by nobody in production code. Pure
developer aid.

## 6. Observable Effects

- Pixels on screen update at ~60 Hz (reduced under speed multipliers).
- `engine.cash`, `engine.totalEnemiesKilled`, `engine.roundOver`,
  `engine.activeOverdrive`, `engine.overdriveTimeRemaining`,
  `engine.uwStates[i].cooldownRemaining` — all mutated from the game
  thread each tick. Read by `BattleViewModel`'s 200-ms polling loop
  (trace 05 §2.2) via `@Volatile` visibility.
- Sound callbacks to `SoundPool` via `soundManager?.play(...)`. These
  enqueue on SoundPool's own thread; the game thread doesn't block.
- `onStepReward(amount)` may be invoked from inside
  `handleEnemyDeath` — trace 07 handles the hop.
- No Room, no SharedPreferences, no notifications from the game thread.
  This is strictly observed.

## 7. Why This Design

- **Fixed timestep** makes physics / gameplay deterministic regardless
  of frame rate. Bouncing, knockback, attack intervals all advance
  by the same `dt` each step, independent of device.
- **Accumulator pattern** (Gaffer on Games classic) decouples update
  rate from render rate and makes "catch up on slow frames" free.
- **Speed multiplier scales accumulator** instead of `dt` so physics
  stays deterministic; the player just sees more updates per frame.
  Alternatives (scaling `dt`) would break the "X is always 16 ms"
  invariant tests rely on.
- **`@Volatile` over locks** because the game thread is the exclusive
  writer for most state; cross-thread reads by the VM are coarse
  enough (200 ms) that torn reads are irrelevant.
- **`pendingAdd` deferred-add queue** prevents `ConcurrentModificationException`
  in `entities.forEach { it.update(...) }` when an entity's `update`
  spawns another entity (e.g. ziggurat spawning a projectile).
- **Single collision sweep at the end of update** so all movement
  happens before any damage — avoids frame-order bias between entities.
- **Rendering with `synchronized(surfaceHolder)`** to give `surfaceDestroyed`
  a chance to grab the holder lock; `thread.join(1000)` on the main
  thread still races.

## 8. Feels Incomplete

- **No max accumulator clamp.** If the device hibernates mid-round and
  resumes, `elapsed` could be huge. `accumulator += elapsed *
  speedMultiplier` would then try to run thousands of updates in one
  frame, freezing the UI. The pause flag protects the common case
  (app backgrounded → `isPaused` true via lifecycle observer), but
  there's no programmatic "first-frame-after-resume" clamp like
  `elapsed = min(elapsed, MAX_FRAME_NS)`.
- **FPS field is unused.** Not exposed to ViewModel or overlay. Easy
  debug win left on the table.
- **`lastWave` comparison** drives wave announcements once per edge. If
  the spawner skips a wave (theoretical — it doesn't), the
  announcement would lag. Brittle coupling.
- **Projectile trails** are spawned *every tick* for every live
  projectile. No throttling; if attack speed is very high the
  `ParticlePool` could exhaust. Pool capacity is 200; active
  projectiles rarely exceed ~10, so this is fine today.
- **The `chronoActive` overlay** is drawn as a raw `Paint(0x222196F3)`
  full-screen rect inside `render`. Not part of the effect engine
  pipeline; scales poorly for more fullscreen overlays.

## 9. Feels Vulnerable

- **`entities.forEach { it.update(deltaTime) }`** iterates while
  entities can mutate fields that the collision system reads later in
  the same tick. Entities don't remove themselves — they set
  `isAlive=false` and the engine sweeps. Safe by convention, not
  enforced.
- **No guard against very long game loop starvation.** If the process
  is under heavy GC or the thread is paused by the OS, the next run
  could inject a very large accumulator. See §8 above.
- **Render reads mutable entity state** that was just written by
  `update`, on the same thread. Safe. But if a VM read were
  interleaved (via the polling loop) between `update` and
  `render`, the VM sees a "one tick ahead" view of cash etc. — the
  polling loop can observe an intermediate state. No correctness
  issue, just possible visual desync for one 200-ms cycle.
- **`healthBarRenderer.render(canvas, it.currentHp, it.maxHp, ...)`**
  reads `ziggurat.currentHp` from inside `render`. If an enemy
  projectile finishes mutation in the same tick while render is
  iterating, the HP bar could show a transient post-mutation value.
  Harmless.
- **`ROUND_END` sound plays when `currentHp <= 0`** inside `update`.
  Because `update` can run multiple times per render under accumulated
  frames, a miss-timed fatal hit could hypothetically play
  `ROUND_END` twice before `render` sees `roundOver=true`. The engine
  sets `roundOver=true` first, so next tick's `if (roundOver) return`
  short-circuits — should be safe.

## 10. Feels Like Bad Design

- **`GameEngine` is 520 lines** and holds 15+ `@Volatile` fields,
  several maps, several callbacks, the effect engine, the sound
  manager, plus combat use cases (`CalculateDamage`,
  `CalculateDefense`). It's doing the work of at least three
  classes: game loop coordinator, combat resolver, VFX driver.
- **`init` and play-again re-invoke it** with all fields reset inline.
  Any future added state must be reset in `init` — easy to miss.
- **`entities` has no z-order.** The drawing order is insertion order,
  which means if a late-spawned boss is hidden behind an earlier
  enemy, it stays hidden. Fine today, but not a first-class render
  tree.
- **Thread-safety relies on `@Volatile` + "game thread is the only
  writer".** There is no documentation of which fields are writable
  by which thread. The convention works but is a maintenance hazard:
  a new feature that writes to `cash` from a VM coroutine would
  silently break atomicity.
- **`applyDamageToZiggurat` is inside the engine** and reads
  `stats.deathDefyChance` via `Random.nextDouble()` — non-deterministic,
  not seedable. Useful for live gameplay, troublesome for repro.
- **`EffectEngine` and `ParticlePool`** are managed inside
  `GameEngine`. VFX code effectively has full access to the engine's
  internals. A stricter "effects can only read engine state, not
  mutate" contract would be clearer.
