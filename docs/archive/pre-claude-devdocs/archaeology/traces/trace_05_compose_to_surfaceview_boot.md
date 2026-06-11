# Trace 05 — Compose → SurfaceView: Battle boot sequence

*Phase 3 Deep Trace. Ground truth:
`presentation/battle/BattleScreen.kt`,
`presentation/battle/BattleViewModel.kt`,
`presentation/battle/GameSurfaceView.kt`,
`presentation/battle/GameLoopThread.kt`,
`presentation/battle/engine/GameEngine.kt`,
`presentation/audio/SoundManager.kt`. This is the boundary where the app
crosses from Compose to a custom-rendered SurfaceView with a dedicated
game-loop thread.*

## 1. Entry Point

- User tap on "BATTLE" button in `HomeScreen` →
  `navController.navigate(Screen.Battle.route)`.
- Deep-link from notification extra `navigate_to=battle` (see trace 10)
  can also drive this.
- `MainActivity`'s `NavHost` renders
  `composable(Screen.Battle.route) { BattleScreen(onExitBattle = ...) }`.
- Side effect: `backStackEntry.destination.route == Screen.Battle.route`
  causes `Scaffold.bottomBar` to *hide* the `BottomNavBar`.

## 2. Execution Path

### 2.1 Compose side — composition & initial launch

```
BattleScreen()
  ├─ val viewModel = hiltViewModel<BattleViewModel>()        [@HiltViewModel]
  │   │ viewModelScope.launch {                               [ViewModel.init]
  │   │   workshopLevels = workshopRepository.observeAllUpgrades().first()
  │   │   profile        = playerRepository.observeProfile().first()
  │   │   tier           = profile.currentTier
  │   │   resolvedStats  = resolveStats(workshopLevels)        [pure use case]
  │   │   equippedWeapons = uwRepository.observeEquippedWeapons().first()
  │   │   equippedCards  = cardRepository.observeEquippedCards().first()
  │   │   cardResult     = applyCardEffects(resolvedStats, equippedCards)
  │   │   resolvedStats  = cardResult.stats
  │   │   cardCashBonus / cardSecondWind extracted
  │   │   biomeTransition = if (!biomePreferences.hasSeenBiome(biome)) BiomeTransitionInfo(...) else null
  │   │   _uiState.update { BattleUiState(maxHp=..., isLoading=false, ...) }
  │   │ }
  │
  ├─ val state by viewModel.uiState.collectAsState()          [re-composes on every emit]
  ├─ val surfaceView = remember { GameSurfaceView(context) }  [only built once per composition]
  │   │ → GameSurfaceView's init block:
  │   │   - engine = GameEngine() (entity list, paint instances, volatile fields reset)
  │   │   - holder.addCallback(this)                          [we become a SurfaceHolder.Callback]
  │   │   - soundManager = SoundManager(context)              [SoundPool load of 7 SFX]
  │   │   - muted/volume read from SharedPreferences("sound_prefs") inline
  │   │   - engine.soundManager = soundManager
  │
  ├─ LaunchedEffect(surfaceView) {
  │       viewModel.startPollingEngine(surfaceView.engine, surfaceView)
  │   }
  ├─ LaunchedEffect(state.speedMultiplier) { surfaceView.setSpeedMultiplier(...) }
  ├─ LaunchedEffect(state.isPaused) { surfaceView.setPaused(...) }
  ├─ LaunchedEffect(state.isLoading) {
  │       if (!state.isLoading) surfaceView.configure(resolvedStats, tier, workshopLevels)
  │   }
  │
  ├─ DisposableEffect(lifecycleOwner) {                       [auto-pause on ON_PAUSE]
  │       val observer = LifecycleEventObserver { _, event ->
  │           if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause()
  │       }
  │       lifecycleOwner.lifecycle.addObserver(observer)
  │       onDispose { removeObserver(observer) }
  │   }
  │
  └─ Box { AndroidView(factory = { surfaceView }, ...) + overlays }
```

### 2.2 `GameSurfaceView.configure` and `startPollingEngine`

- `configure(stats, tier, wsLevels)` stashes the args on the surface
  view. If `surfaceReady` is true it *also* calls
  `engine.init(width, height, stats, tier, wsLevels, isReducedMotion)`
  right now. Otherwise, the init is deferred to `surfaceCreated`
  below.
- `viewModel.startPollingEngine(engine, surfaceView)` wires the
  engine into the ViewModel:
    - `engine.setStats(resolvedStats)`
    - `engine.initUWs(equippedWeapons)` — fills `engine.uwStates`
      with `UWState(type, level, cooldownRemaining=0, effectTimeRemaining=0)`
      per equipped weapon.
    - `engine.secondWindHpPercent` / `engine.cashBonusPercent` — set
      from card effects.
    - `wireStepRewardCallback(engine)` — sets
      `engine.onStepReward = { amount -> viewModelScope.launch { ... } }`.
      See trace 07 for where this fires.
    - Starts a **200 ms polling loop** inside `viewModelScope` that
      reads `engine.cash`, `engine.uwStates`, `engine.roundOver` etc.
      and updates the `_uiState` StateFlow.

### 2.3 Android SurfaceHolder lifecycle

Once `AndroidView` has actually attached the `SurfaceView`, the
OS invokes the callbacks on the main thread:

```
surfaceCreated(holder)
  ├─ surfaceReady = true
  ├─ engine.init(width, height, stats, tier, wsLevels, isReducedMotion)
  │     → clears entity list, reseeds all Volatiles, builds
  │       ZigguratEntity, WaveSpawner, BackgroundRenderer, EffectEngine.
  ├─ thread = GameLoopThread(holder, engine)
  ├─ thread.isRunning = true
  ├─ thread.start()                                  [forks new thread "GameLoop"]
  └─ gameThread = thread

surfaceChanged(holder, format, width, height)       [e.g. rotation]
  └─ engine.init(width, height, stats, tier, wsLevels, isReducedMotion)
      (re-initialises the engine without stopping the loop thread — dangerous; see §9)

surfaceDestroyed(holder)                             [leaving battle / backgrounding]
  ├─ surfaceReady = false
  ├─ thread.isRunning = false
  ├─ thread.join(1000)                               [blocks main thread up to 1 s]
  ├─ gameThread = null
  └─ soundManager.release()                          [SoundPool.release]
```

At this point the `BattleScreen` composition is torn down and
`onExitBattle()` returns to Home.

## 3. Resource Management

| Concern | How |
|---|---|
| Thread creation | `GameLoopThread` extends `Thread("GameLoop")`. Exactly one per surface-create; destroyed on surface-destroy. |
| Thread lifecycle | `@Volatile var isRunning: Boolean` + `@Volatile var isPaused: Boolean` + `@Volatile var speedMultiplier: Float` are the cross-thread handles. No lock, only visibility. |
| Surface lock | `surfaceHolder.lockCanvas()` / `unlockCanvasAndPost(canvas)` around `engine.render(canvas)` inside `synchronized(surfaceHolder)`. |
| Join timeout | `thread.join(1000)` ms. Intentionally bounded — blocks UI briefly. |
| Coroutine scope | Two separate scopes: (a) `viewModelScope` for repository reads and the 200-ms polling loop (cancelled on VM clear), (b) `BattleViewModel.wireStepRewardCallback`'s `viewModelScope.launch` per-reward — also cancelled with VM. |
| Engine lifetime | `GameEngine` is constructed by `GameSurfaceView` **once** and re-`init()`-ed each time the surface is created/changed. `roundOver`, `cash`, `elapsedTimeSeconds` etc. are reset by `init()`. |
| SoundPool | Single `SoundManager(context)` per `GameSurfaceView`. Released on surface-destroy. Reads/writes `sound_prefs` SharedPreferences (unthrottled). |
| Reduced motion | `ReducedMotionCheck.isReducedMotionEnabled(context)` reads `Settings.Global.ANIMATOR_DURATION_SCALE` once per construction; passed into `engine.init` so particle/shake code can short-circuit. |

## 4. Error Path

- **`workshopRepository.observeAllUpgrades().first()`** can hang
  indefinitely if the Flow never emits. In practice Room always emits
  the current row list; new installs set these up on first DB access.
- **Surface not ready yet** — if `configure` is called before
  `surfaceCreated` (which happens in the cold-start ordering below),
  `engine.init` is deferred until `surfaceCreated`. The polling loop
  handles this gracefully because `ziggurat` returns `null` from
  `engine.ziggurat` until `init` has run; the loop reads it each tick
  via `val zig = eng.ziggurat ?: continue` and just skips.
- **`viewModel.init` fails** — no user-facing error; `_uiState` stays
  at `isLoading=true` and the overlays never render. Surface still
  shows a black canvas.
- **Thread.start throws** — `java.lang.IllegalThreadStateException`
  only if the same thread is started twice. The code protects against
  that by creating a new instance per `surfaceCreated`; there is no
  guard against a re-entrant `surfaceCreated` without an intervening
  `surfaceDestroyed`, but the Android contract is serial.
- **SoundPool load fails** — SoundManager swallows; SFX simply don't
  play. Game continues.
- **`join(1000)` timeout** — if the game thread is stuck (e.g. a long
  `synchronized` block inside `engine.render`), the main thread waits
  1 s, then continues without reaping. The thread becomes a zombie
  until the JVM collects. The `InterruptedException` catch is silent.

## 5. Performance Characteristics

Boot cost is front-loaded:

- **Compose composition**: ~1 ms.
- **`hiltViewModel()`**: ~1-2 ms (Hilt factory chain).
- **ViewModel init repository reads**: 4× `observe*().first()` calls —
  each suspending until Room emits. Typically a few ms if cached; could
  be 50+ ms on first DB open.
- **`GameSurfaceView` construction**: SoundPool.load of 7 OGG files
  (`SoundManager.init`). Async on SoundPool's own thread, so
  construction returns quickly; actual playback is silent until loads
  finish.
- **`engine.init`**: `O(n)` where n = entity count (zero at start) +
  ~5 paint construction + initial wave announcement. Cheap.
- **Thread.start**: JVM thread creation, low ms.

Polling loop cost: fixed `delay(200)` inside `viewModelScope`. Reads
engine volatiles — no Room I/O. 5 Hz UI updates feel responsive.

Render loop cost: covered in trace 06.

## 6. Observable Effects

- The user sees the Home→Battle navigation animation (biome-coloured
  fade or slide).
- Bottom navigation bar hides.
- A black canvas appears, replaced by the biome-coloured background
  once `engine.init` completes.
- If `biomeTransition != null`: a full-screen overlay announces the
  biome and disappears on tap.
- A `"Wave 1 · 0 enemies"` label, Cash counter, speed controls, pause
  button, upgrade button, overdrive button, and UW bar appear via
  Compose overlays on top.
- The ziggurat appears mid-screen and begins firing after ~1 s (first
  enemy spawn).
- SoundPool emits sounds as the engine triggers them.
- Nothing written to Room until the round ends (trace 08) or an enemy
  is killed (trace 07).

## 7. Why This Design

- **SurfaceView + dedicated game-loop thread** avoids the Compose
  recomposition cost per frame. Compose handles the HUD (~5 Hz updates
  via StateFlow), the game draws at 60 Hz on its own thread.
- **Engine constructed once, `init()` repeatedly** — a play-again
  path reuses the same engine + same surface, only re-seeding the
  state. Avoids GC thrash.
- **`configure` first, `surfaceCreated` later** — because the Compose
  `AndroidView` factory runs *before* the `SurfaceHolder.Callback`
  fires, the view needs somewhere to stash args until the OS gives it
  a drawable surface.
- **Polling instead of Flow from engine** — the engine has plain
  `@Volatile` fields, not `Flow`s, because the game loop writes every
  16 ms and a back-pressured Flow would add overhead. The ViewModel
  polls every 200 ms and publishes via StateFlow to Compose; the
  Compose-level render cost is paid only once every 200 ms.
- **`LaunchedEffect(state.isPaused)` and friends** bridge the
  one-way Compose-state-changes-the-thread direction. Conversely,
  engine-state-changes-Compose uses the polling loop.
- **`DisposableEffect` for ON_PAUSE** — leaving the app mid-battle
  auto-pauses (via `viewModel.pause()` → `_uiState.isPaused=true` →
  `LaunchedEffect(state.isPaused)` → `surfaceView.setPaused(true)` →
  `GameLoopThread.isPaused = true`). The game loop stops advancing
  but continues rendering the static scene.
- **Hiding bottom nav** keeps full-screen immersive and stops
  accidental mis-navigation during a round.

## 8. Feels Incomplete

- **`biomePreferences.markBiomeSeen` is called only from
  `dismissBiomeTransition`.** If the user quits before dismissing,
  next round repeats the "new biome" overlay. Minor but observable.
- **`configure(resolvedStats, tier, workshopLevels)`** is triggered by
  `LaunchedEffect(state.isLoading) { if (!isLoading) surfaceView.configure(...) }`.
  If `isLoading` flips false *before* the surface is ready, then
  `surfaceView.configure` stashes values but doesn't call `engine.init`
  — it waits for `surfaceCreated` which independently calls
  `engine.init(width, height, currentStats, ...)` with the *stashed*
  stats. This works, but the handshake is subtle and easy to break.
- **`wireStepRewardCallback` is called on every `startPollingEngine`**,
  which happens on both initial boot and `playAgain()`. The
  `engine.onStepReward` field is simply overwritten. If a previous
  callback is still in flight (Tail coroutine in `viewModelScope`),
  it will still call into the ViewModel after the new callback is
  installed — likely benign but untested.
- **The 200-ms polling loop lives inside `startPollingEngine`** and
  terminates via `if (eng.roundOver && !roundEnded) { endRound(); break }`.
  It does not check `viewModelScope` cancellation explicitly; Kotlin
  `delay` handles cancellation cooperatively, so this is fine for VM
  destruction. But the loop does `break` on `roundOver`, meaning the
  ViewModel stops publishing engine state updates post-round. The
  post-round overlay relies on the `_uiState.roundEndState` being set
  once by `endRound()`. Fragile if endRound throws.

## 9. Feels Vulnerable

- **`surfaceChanged` re-invokes `engine.init`** without stopping the
  game loop. For a rotation mid-battle, the loop is still executing
  `engine.update(dt)` when `engine.init` clears the entity list and
  re-creates the ziggurat. There are no locks. Observable symptoms
  would be brief visual glitches or a frame with a null ziggurat
  (`val zig = ziggurat ?: return`) inside `update`. A rotation during
  battle is rare in practice but possible.
- **Cross-thread reads of non-`@Volatile` fields**. `BattleViewModel`
  reads `eng.waveSpawner?.currentWave`, `eng.waveSpawner?.enemiesAlive`,
  `eng.waveSpawner?.phase?.name` from the polling loop. These are not
  `@Volatile`. Inconsistent reads could show a phase transition
  without a wave tick, which is harmless but formally a data race.
- **`SoundManager` reads `sound_prefs` via `context.getSharedPreferences(...)`
  inside `GameSurfaceView.init`** — untyped, no injector. A settings
  toggle elsewhere in the app must write the same keys; contract is
  implicit.
- **`GameLoopThread.join(1000)` on the main thread** inside
  `surfaceDestroyed`. If the thread is blocked for >1 s the UI will
  briefly freeze. On slower devices with GC pauses, this could
  manifest as ANR-adjacent behaviour.
- **`engine.onStepReward = null` is set in `BattleViewModel.onCleared`.**
  If the engine somehow outlives the VM (shouldn't — it's owned by
  the surface view inside the same Compose scope), a dangling callback
  wouldn't be GC'd. Low risk given the ownership model.
- **`hiltViewModel()` gives you one VM per back-stack entry.**
  Navigating away and back recreates it. Recreating while the engine
  thread is still alive (fast back+forward) has no explicit
  synchronisation.

## 10. Feels Like Bad Design

- **`GameSurfaceView` owns both the engine and the SoundManager and
  the game-loop thread.** That's three lifecycles managed by one
  class, plus the implicit dependency on `context.getSharedPreferences("sound_prefs")`.
  A cleaner split would be `BattleRuntime` (engine + loop) and an
  injected `SoundManager`.
- **`BattleViewModel.resolvedStats`, `tier`, `workshopLevels` are `var`
  with `private set` but public read** — used by `BattleScreen` to
  pass into `configure`. A proper `BattleSetup` data class would make
  the invariant "these three are always read together" explicit.
- **`startPollingEngine(engine, surfaceView)`** takes two parameters
  but `surfaceView.engine === engine` by construction. Redundant.
- **The `LaunchedEffect(state.isLoading)` dispatch** — there are four
  separate `LaunchedEffect`s driven by different state fields,
  synchronously toggling thread flags via `setSpeedMultiplier`,
  `setPaused`, `configure`. Collapsing them into a single
  `LaunchedEffect(state) { ... }` would centralize the logic.
- **No `BattleLifecycle` interface** documenting the relationship
  between Compose lifecycle, Surface lifecycle, and game-thread
  lifecycle. The current code relies on a human reader to understand
  that `surfaceCreated` happens *after* Compose attaches
  `AndroidView`, which happens *after* `BattleScreen` commits.
  Diagrams would help; lock-step unit tests are hard to write for
  this without Robolectric.
- **`PlaceholderScreen(name)` in `MainActivity.kt`** is not invoked
  anywhere. Not related to this trace directly, but lives adjacent
  and pollutes the file — typical dead-code smell in the Compose
  presentation layer.
