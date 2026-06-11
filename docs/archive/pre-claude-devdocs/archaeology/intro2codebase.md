# Codebase Architecture — Intro for New Engineers

*Archaeology Phase 2. Written from source, not from prior docs. Where code
and docs disagree, code wins and the discrepancy is called out. For the
non-technical overview of what this app does, read
`devdocs/archaeology/small_summary.md` first; for a one-page reference of
layer names, read `docs/architecture.md`.*

> Companion document: `intro2deployment.md` covers how the same code is built,
> signed, packaged and shipped.

## 1. The 10-second mental model

Steps of Babylon is a **single Android application module** (`:app`). There
is no backend, no second app, no shared library. All state is local. The
code you will be reading is inside one package root:
`app/src/main/java/com/whitefang/stepsofbabylon/`.

The code is laid out in three directories that strictly represent the three
layers of Clean Architecture:

```
com/whitefang/stepsofbabylon/
├── domain/           ← Pure Kotlin (no Android imports). Contract + rules.
├── data/             ← Android-dependent. Implements domain contracts.
└── presentation/     ← Android-dependent. UI + battle renderer.
di/, service/         ← Hilt wiring + Android framework components (services,
                        workers, widget provider, broadcast receivers).
```

The dependency rule: `presentation → domain ← data`. Presentation never
imports data. Data implements interfaces declared in domain. `di/` glues
them together with Hilt. If you find yourself wanting to import a Room DAO
into a ViewModel, stop and go through a repository.

## 2. Application entry points

There are **six** ways the app's code starts running. Knowing all of them
is important because step counting has to keep working even when the UI
is gone.

| # | Entry point | Class | Trigger |
|---|---|---|---|
| 1 | Application onCreate | `StepsOfBabylonApp` | Process start. `@HiltAndroidApp`, loads `libsqlcipher.so`, schedules `StepSyncScheduler`. |
| 2 | Launcher activity | `presentation.MainActivity` | User taps icon. `@AndroidEntryPoint`. Sets Compose content, owns NavHost. |
| 3 | Foreground service | `service.StepCounterService` | Started by MainActivity after ACTIVITY_RECOGNITION granted; also by BootReceiver. Runs as `foregroundServiceType=health`, `START_STICKY`. |
| 4 | Boot receiver | `service.BootReceiver` | `BOOT_COMPLETED` broadcast. Restarts StepCounterService if permission is granted. |
| 5 | Periodic worker | `service.StepSyncWorker` | Every 15 min via WorkManager (`@HiltWorker`, `@AssistedInject`). Catches up missed steps + talks to Health Connect. |
| 6 | Widget provider | `service.StepWidgetProvider` | `AppWidgetManager` update cycle (30 min) and manual push via `WidgetUpdateHelper`. |

Two extras: `presentation.HealthConnectPermissionActivity` handles the
system-initiated permission rationale intent, and `MainActivity.onNewIntent`
handles deep-links of the form `navigate_to=<route>` from notifications.

## 3. Two data-flow paths you must understand

There are fundamentally two flows in the app: **the step ingestion pipeline**
(what mints the primary currency) and **the battle loop** (what spends it).
Every other feature is a variation on wiring Room ⇄ ViewModel ⇄ Compose.

### 3.1 Step ingestion pipeline (the core product)

```
Physical walking
    │
    ▼
Hardware: Android TYPE_STEP_COUNTER sensor
    │ (cumulative counter since last reboot)
    ▼
data/sensor/StepSensorDataSource          [callbackFlow<Long> — emits deltas]
    │
    ├────────────────────────────────────┐
    ▼                                    ▼
service/StepCounterService          service/StepSyncWorker   (15-min periodic)
(foreground, health type)           catch-up only when service is NOT alive
    │                                    │
    │ writes heartbeat + day-start        │ uses heartbeat to skip,
    │ counter via StepIngestionPrefs     │ uses Room sensorSteps as baseline
    │                                    │
    └────────────┬───────────────────────┘
                 ▼
data/sensor/DailyStepManager  (the orchestrator — inject this, not the pieces)
    │
    ├─► StepRateLimiter           (rolling 1-min window, 200/min + 250 burst)
    ├─► StepVelocityAnalyzer      (detects shakers/spoofers → 1.0/0.5/0.0 multiplier)
    ├─► 50,000/day ceiling        (constant DAILY_CEILING)
    ├─► AntiCheatPreferences      (counters for rejected/penalized steps)
    │
    ▼
data/repository/StepRepositoryImpl  → Room (SQLCipher) daily_step_record
data/repository/PlayerRepositoryImpl → Room player_profile (balance + lifetime totals)
    │
    └─► runFollowOnPipeline()  (same method hit by both recordSteps and recordActivityMinutes)
          ├─ WidgetUpdateHelper (60s throttle → widget SharedPrefs)
          ├─ GenerateSupplyDrop use case → WalkingEncounterRepository → Notification
          ├─ TrackDailyLogin   (streak Gems + 1 Power Stone)
          ├─ TrackWeeklyChallenge (weekly Power Stone tiers)
          └─ Walking-mission progress (DailyMissionDao)
```

Parallel input from Health Connect, handled only by the worker:

```
StepSyncWorker
  ├─ StepGapFiller       (recover missed raw steps into daily_step_record)
  ├─ StepCrossValidator  (escrow excess vs HC — graduated 4-level response)
  └─ ExerciseSessionReader → ActivityMinuteValidator → ActivityMinuteConverter
        → DailyStepManager.recordActivityMinutes()  (same follow-on pipeline)
```

Two things about this pipeline are non-obvious:

- **Service ↔ worker coordination.** They are redundant paths to the same
  Room row. `StepIngestionPreferences` (SharedPreferences key `step_ingestion`)
  stores a 2-minute heartbeat and a per-date day-start counter. The worker
  checks `isServiceAlive(now)` and skips the sensor read if the service
  updated its heartbeat in the last 2 minutes. The "source of truth" for
  the no-double-credit property is Room's `sensorSteps` column, not the
  heartbeat.
- **Cross-validation is destructive.** When `StepCrossValidator` detects a
  >20% discrepancy vs Health Connect it escrows the excess *by actually
  deducting it* via `PlayerRepository.spendSteps(excess)`, then either
  restores (release) or keeps the deduction (discard). `PlayerProfileDao`
  clamps with `MAX(0, ...)` on every currency adjustment to prevent
  negative balances.

### 3.2 The battle loop (the hot path)

The battle screen does **not** use Compose for rendering. It uses a
`SurfaceView` and a dedicated thread. Compose renders only the overlays
(HUD, pause menu, post-round summary).

```
presentation/battle/BattleScreen (Compose)
    │ AndroidView(factory = GameSurfaceView(context))
    ▼
presentation/battle/GameSurfaceView  (SurfaceHolder.Callback)
    │ surfaceCreated / surfaceChanged → engine.init(w, h, stats, tier, levels, reducedMotion)
    │ surfaceDestroyed → thread.isRunning=false; thread.join(1000); soundManager.release()
    ▼
presentation/battle/GameLoopThread   (Thread("GameLoop"), fixed timestep)
    │
    ├─ TICK_NS = 16_666_667L           (1e9 / 60 → ~60 UPS)
    ├─ accumulator += elapsed * speedMultiplier    (1× / 2× / 4× supported)
    ├─ while (accumulator >= TICK_NS) engine.update(dt)
    └─ lockCanvas → engine.render(canvas) → unlockCanvasAndPost
    ▼
presentation/battle/engine/GameEngine   (single coordinator, not injected — `new` per round)
    │
    ├─ ZigguratEntity         (5-layer tower; fires via findNearestEnemies lambda)
    ├─ WaveSpawner            (26s spawn + 9s cooldown; 6 enemy types; boss every N waves)
    ├─ EnemyScaler            (1.05^wave HP/damage; per-type cash & step rewards)
    ├─ CollisionSystem        (projectile ↔ enemy; enemy projectile ↔ ziggurat)
    ├─ EffectEngine           (ParticlePool, ScreenShake, FloatingText, biome VFX)
    └─ SoundManager           (SoundPool, 7 effects)
    │
    │ Side-effects pushed out via callbacks:
    │  ├─ onStepReward(amount)   → BattleViewModel (coroutine) → AwardBattleSteps → Room
    │  └─ (round over)           → BattleViewModel.endRound()
    ▼
presentation/battle/BattleViewModel  (@HiltViewModel)
    │ Polls engine state every 200 ms (delay) for HP, wave, cash, UW cooldowns.
    │ Does NOT touch Room from the game thread — always hops to viewModelScope.
    ▼
Compose overlays: HUD, InRoundUpgradeMenu, OverdriveMenu, UltimateWeaponBar,
                   PauseOverlay, PostRoundOverlay, BiomeTransitionOverlay
```

Key invariants to not break:

- **Game loop must not block on I/O.** `GameEngine.onStepReward` is a
  `((Long) -> Unit)?` callback. The ViewModel hops onto `viewModelScope`
  before calling the repository.
- **Stats are resolved once per round.** `domain.usecase.ResolveStats`
  produces an immutable `ResolvedStats` from workshop + in-round levels;
  the engine stores it and re-resolves only when the ViewModel buys an
  in-round upgrade. Card effects are applied as a post-process in
  `ApplyCardEffects` — do not extend `ResolveStats` with card logic.
- **Round state is never persisted.** Cash resets at round end. Best wave
  and tier unlocks are persisted via `UpdateBestWave` + `CheckTierUnlock`
  in `BattleViewModel.endRound()`.

### 3.3 Every other screen is the same four-layer trace

For the other 11 screens the path is always:

```
*Screen.kt (Compose)   hiltViewModel()
    ▼
*ViewModel.kt          @HiltViewModel, exposes StateFlow<*UiState>
    │ combine(repo.observe*(), ...).stateIn(viewModelScope, WhileSubscribed(5000), default)
    ▼
use cases (domain/usecase/*) + repositories (domain/repository/*)
    ▼
*RepositoryImpl.kt     @Inject constructor, @Singleton via RepositoryModule
    ▼
Room DAO (data/local/*Dao.kt)   @Dao
```

Action handlers (`purchase()`, `claim()`, etc.) launch in `viewModelScope`
and call suspend functions on repositories. Read operations use `Flow`.
ViewModels never directly touch Room — they go through repositories, which
convert `*Entity` to domain models (`toDomain()` extension function near
the bottom of every `*RepositoryImpl.kt`).

## 4. Main abstractions

### Hilt modules (`di/`)

Six modules, all scoped `SingletonComponent`:

| Module | Kind | What it provides |
|---|---|---|
| `DatabaseModule` | `object` + `@Provides` | The Room `AppDatabase` (with `SupportOpenHelperFactory(passphrase)`, migrations from `AppMigrations.ALL`, downgrade-destructive) and all 12 DAOs. |
| `RepositoryModule` | `abstract class` + `@Binds` | 8 repository interfaces bound to `*RepositoryImpl` as `@Singleton`. |
| `StepModule` | `object` + `@Provides` | `SensorManager` from `Context.SENSOR_SERVICE`. |
| `HealthConnectModule` | `object` (empty) | Documented as an "organizational placeholder" — `HealthConnectClientWrapper` and friends use `@Inject constructor` directly. |
| `BillingModule` | `abstract class` + `@Binds` | `BillingManager` ← `StubBillingManager`. Swap here when integrating Google Play Billing. |
| `AdModule` | `abstract class` + `@Binds` | `RewardAdManager` ← `StubRewardAdManager`. Swap here when integrating AdMob. |

Use cases are **not** Hilt-annotated. They are plain Kotlin classes
(`domain/usecase/*`) instantiated inline inside ViewModels:
`private val resolveStats = ResolveStats()`. Dependencies they need
(like repositories or DAOs) are passed through the ViewModel constructor
and forwarded. If you see someone trying to `@Inject` a use case, reject
the PR — it breaks a documented architectural decision (see
`AwardBattleSteps` wiring in ADR-0003).

### Repositories (8 total)

All in `data/repository/*RepositoryImpl.kt`, implementing interfaces in
`domain/repository/`:

- `PlayerRepository` — profile + wallet + lifetime counters (the central entity)
- `WorkshopRepository` — 23 upgrade levels
- `LabRepository` — 10 research projects + slot count
- `CardRepository` — inventory + loadout
- `UltimateWeaponRepository` — unlocked/equipped weapons
- `StepRepository` — daily step records + escrow
- `WalkingEncounterRepository` — supply drop inbox
- `CosmeticRepository` — cosmetic store items

Reads return `Flow<T>` (Room re-emits when the underlying table mutates).
Writes are `suspend`, usually delegating to atomic `UPDATE ... SET col = MAX(0, col + :delta)` queries on `PlayerProfileDao`.

### Non-repository services & adapters

- **`StepCounterService`** — foreground service, holds a supervised
  `CoroutineScope(SupervisorJob + Dispatchers.Default)`, collects
  `StepSensorDataSource.stepDeltas`, pushes into `DailyStepManager`,
  updates heartbeat and notification.
- **`StepSyncWorker`** — 15-min periodic `CoroutineWorker` (`@HiltWorker`,
  `@AssistedInject`). Sensor catch-up + Health Connect pipeline +
  `SmartReminderManager`.
- **`HealthConnectClientWrapper` / `HealthConnectStepReader` /
  `ExerciseSessionReader`** — thin adapters around the Health Connect
  SDK. Check `isAvailable()` (`HealthConnectClient.SDK_AVAILABLE`) before
  use; `getClient()` returns null when the platform service is missing.
- **`StubBillingManager` / `StubRewardAdManager`** — stand-ins for
  Google Play Billing and AdMob. Same interface shape, 500 ms / 1 s
  `delay()` to simulate a network round-trip, always succeed.
- **`SoundManager`** — `SoundPool` wrapper owned by `GameSurfaceView`
  (not injected — it needs `Context` and has a definite lifecycle tied
  to the surface). Reads mute/volume from a direct
  `SharedPreferences("sound_prefs")`.
- **`SmartReminderManager` / `StepNotificationManager` /
  `SupplyDropNotificationManager` / `MilestoneNotificationManager`**
  — four separate notification-channel owners. Each owns a channel and
  a throttle.

### Presentation layer

ViewModels expose `StateFlow<*UiState>`. Every screen has a matching
`*UiState.kt` data class. ViewModels combine flows with `combine(...)` and
convert to a hot `StateFlow` with `stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), defaultUiState)`. Compose collects
with `collectAsStateWithLifecycle()`.

Navigation is a single `NavHost` in `MainActivity`, 12 routes declared in
`presentation/navigation/Screen.kt` as a sealed class. Bottom nav
(`BottomNavBar`) is hidden while on the Battle route. Deep-links are
handled through `MainActivity.pendingNavigation: MutableStateFlow<String?>`
— both cold start and warm start (`onNewIntent`) funnel through it.

## 5. Where time / randomness / IDs / config / caching / env access live

This project has no "Clock" or "IdGenerator" abstraction. The architecture
has committed to specific seams; new code must reuse them.

### Time

Direct `System.currentTimeMillis()` and `LocalDate.now()` are used in
~15+ places: repositories, notifications, services, ViewModels,
`StubBillingManager`, `AntiCheatPreferences` decay, `AwardBattleSteps`,
etc. **There is no injected `Clock` today.** Two consequences:

- Use cases that pin time receive it as a parameter
  (`AwardBattleSteps.invoke(amount, today: String = LocalDate.now().toString())`).
  When you add a new use case that is time-sensitive, follow this pattern —
  default to `LocalDate.now()` but accept the date as a parameter for
  tests.
- ViewModels with ticker loops (`LabsViewModel`, `MissionsViewModel`) use
  `while(true) { delay(1000) }`. They are **currently tested at the
  use-case level, not VM level**, because `StandardTestDispatcher` with
  `advanceTimeBy` hangs on them. Do not copy this pattern for new
  ViewModels; consider taking an explicit tick flow parameter if you
  need VM-level tests.
- `HomeViewModel` and `StatsViewModel` avoid the ticker issue by using a
  `MutableStateFlow<LocalDate>` / `MutableStateFlow<String>` they refresh
  from a `Lifecycle.Event.ON_RESUME` observer in the screen. Copy this
  pattern.

Game-loop time (`GameLoopThread`) uses `System.nanoTime()` directly for
frame delta — that is appropriate; there is no benefit to abstracting it.

### Randomness

Domain use cases that test randomness-sensitive behaviour accept an
**injectable `kotlin.random.Random`** as a constructor parameter with
default `Random`:

- `domain/usecase/CalculateDamage.kt` (crit roll)
- `domain/usecase/GenerateSupplyDrop.kt` (drop triggers + reward
  amounts)
- `domain/usecase/OpenCardPack.kt` (rarity + card type rolls)

`domain/usecase/GenerateDailyMissions.kt` is deterministic by design:
`Random(todayDate.hashCode())` — missions are the same for all players on
the same day. Do not change this.

Non-deterministic direct use of `kotlin.random.Random.*` is permitted in:
- `presentation/battle/engine/WaveSpawner.kt` (enemy type + spawn
  position — tested structurally, not by exact output)
- `presentation/battle/effects/*` (particle variation, visual-only)
- `presentation/battle/engine/GameEngine.kt` combat RNG (free-upgrade
  chance, overdrive Fortune roll)

If you add a new use case whose behaviour depends on a dice roll, follow
the `Random = Random` constructor-parameter pattern so tests can seed it.

### IDs

Every entity with an ID uses Room `autoGenerate = true` on a numeric
primary key, or is a single-row record keyed on `id = 1`
(`PlayerProfileEntity`), or is keyed on a stable enum name
(`UpgradeType.name`, `CardType.name`, etc.) or an ISO date string. There
is **no `UUID.randomUUID()` in the codebase.**

### Configuration

- `gradle/libs.versions.toml` — single source of all dependency versions.
- `app/build.gradle.kts` — build-time config (SDK levels, minify,
  signing). No `productFlavors`, no `BuildConfig.BUILD_TYPE` reads
  anywhere in Kotlin.
- `keystore.properties` — gitignored; loaded at build time if present
  (signing is optional for debug builds).
- `local.properties` — gitignored; only `sdk.dir`.
- `gradle.properties` — Gradle daemon JVM heap (2 GB), plain console,
  AndroidX, non-transitive R class.

**There is no runtime feature-flag system**; no remote config; no A/B
framework. Feature toggles at runtime live in `SharedPreferences`
(see next).

### Caching & persistence

| Layer | Mechanism | Location |
|---|---|---|
| Encrypted durable | SQLCipher-encrypted Room | `data/local/AppDatabase.kt` (v8, 12 entities, 12 DAOs) |
| Encryption key | Android Keystore (AES-256-GCM) | `data/local/DatabaseKeyManager.kt`. Passphrase encrypted blob lives in `SharedPreferences("db_key_prefs")`. Auto-recovers by wiping on decrypt failure (e.g., device restore). |
| Small durable state | `SharedPreferences` | See table below. |
| In-memory | plain fields on `@Singleton` classes | `StepRateLimiter`'s ArrayDeque, `DailyStepManager`'s per-minute map, throttle timestamps on notification/widget helpers. No LRU caches. |
| Schema history | `app/schemas/*.json` | 1.json through 8.json — committed. |

SharedPreferences files in use (note: these are the cache / light-state
layer for things that shouldn't go through Room):

| Prefs file | Used by | Purpose |
|---|---|---|
| `db_key_prefs` | `DatabaseKeyManager` | Encrypted DB passphrase blob + IV |
| `step_ingestion` | `StepIngestionPreferences` | Service heartbeat + day-start sensor counter |
| `anti_cheat_prefs` | `AntiCheatPreferences` | Daily rate/velocity counters + 7-day-decaying CV offense count |
| `widget_data` | `StepWidgetProvider` | Steps + balance for widget rendering |
| `sound_prefs` | `GameSurfaceView` (inline) | Mute flag + volume for battle SoundManager |
| (named per wrapper) | `BiomePreferences`, `NotificationPreferences`, `SoundPreferences`, `MilestoneNotificationPreferences` | First-seen biome tracking, 4 notification toggles, sound mute/volume surfaced to Settings, per-milestone notification dedup |

**There is no Jetpack DataStore.** The project committed to SharedPreferences.

### Environment access

- `Context` is injected via `@ApplicationContext` in Hilt modules.
- Sensor API: `SensorManager` via `StepModule`.
- Health Connect SDK: gated through `HealthConnectClientWrapper` —
  check `isAvailable()` and `hasPermissions()` before making calls.
- No HTTP, no sockets, no filesystem outside `Context.filesDir`
  (Room's `"steps_of_babylon.db"`).
- `StepsOfBabylonApp.onCreate` calls `System.loadLibrary("sqlcipher")` —
  required for SQLCipher before the first DB access.

## 6. Design patterns at a glance

- **MVVM + Clean Architecture** — strict layering; domain is Android-free.
- **Repository pattern** — 8 Flow-based interfaces, all injected via
  `@Binds @Singleton` in `RepositoryModule`.
- **Use cases as plain classes** — no `@Inject`, no Hilt, `operator fun invoke(...)` convention.
- **Hilt DI, KSP processing** — no kapt, no Dagger component manual wiring.
- **`@HiltWorker` + `@AssistedInject`** — the one place Assisted
  Injection is used (WorkManager needs runtime `WorkerParameters`).
  `StepsOfBabylonApp : Configuration.Provider` injects `HiltWorkerFactory`
  and disables the default `WorkManagerInitializer` provider in the
  manifest.
- **Callback flow (`callbackFlow`) over sensor listener** — how the Android
  `SensorEventListener` becomes a `Flow<Long>` in `StepSensorDataSource`.
- **Follow-on pipeline pattern** — `DailyStepManager.runFollowOnPipeline`
  is called by both `recordSteps` and `recordActivityMinutes` so any new
  step-based reward (widget, drop, economy, mission) only has to be
  added in one place.
- **Dedicated game-loop thread** — `Thread("GameLoop")` with fixed
  timestep and accumulator. Uses `@Volatile` for cross-thread
  read/write of `isRunning`, `isPaused`, `speedMultiplier`,
  `cash`, `totalEnemiesKilled`, etc. The surface view's `synchronized`
  block guards canvas locking only.
- **Stats as a pure function** — `ResolveStats` + `ApplyCardEffects`
  compose; the engine holds the output `ResolvedStats` and never mutates
  it (only replaces via `setStats`).
- **Graduated response state machine** — `StepCrossValidator` reads
  offense count from `AntiCheatPreferences` and branches into 4
  behaviour levels; offense count decays after 7 days of no new
  offenses.
- **StateFlow + `WhileSubscribed(5000)`** — everywhere in ViewModels.
  The 5 second grace period avoids reconnecting flows across config
  changes.

## 7. Module boundaries

`settings.gradle.kts` only includes `:app`. There are no multi-module
Gradle boundaries. Within `:app`, the only enforced boundary is the
package structure:

- `domain/` has zero Android imports (enforced by convention and code
  review — a CI check could be added, see intro2deployment.md).
- `presentation/` imports `domain/` but never `data/` directly.
- `data/` implements interfaces in `domain/` and has no dependency on
  `presentation/`.

Tests live alongside, in `app/src/test/java/...` with identical package
structure. There is no `app/src/androidTest/` — all tests run on the JVM
(some via Robolectric for Android framework classes).

## 8. Entry-point quick lookup

If the app does X, start reading here:

| Phenomenon | File to open |
|---|---|
| "I see my step count rise" | `StepCounterService.onCreate` → `DailyStepManager.recordSteps` |
| "I bought an upgrade" | `WorkshopViewModel.purchase` → `domain/usecase/PurchaseUpgrade` |
| "I entered battle" | `MainActivity` Battle route → `BattleScreen` → `BattleViewModel.init` → `GameSurfaceView` → `GameEngine.init` |
| "An enemy died" | `GameEngine.handleEnemyDeath` (cash + step callback + death VFX) |
| "I earned a supply drop" | `DailyStepManager.runFollowOnPipeline` → `GenerateSupplyDrop` → `SupplyDropNotificationManager` |
| "My widget updated" | `DailyStepManager.runFollowOnPipeline` → `WidgetUpdateHelper.update` → `StepWidgetProvider.updateAllWidgets` |
| "The app detected cheating" | `StepRateLimiter`, `StepVelocityAnalyzer`, `StepCrossValidator` |
| "I tapped a notification" | `*NotificationManager.buildNotification` → PendingIntent → `MainActivity.onNewIntent` → `pendingNavigation` flow |
| "My phone rebooted" | `BootReceiver.onReceive` → `StepCounterService` |
| "A round ended" | `GameEngine.roundOver=true` → `BattleViewModel.endRound` → `UpdateBestWave`, `AwardWaveMilestone`, `CheckTierUnlock`, `MilestoneNotificationManager` |

## 9. Documentation drift noted (per global rule #1)

The following in-repo docs are out of date relative to code. This
document reflects code; cross-reference if you rely on any other doc.

- `docs/database-schema.md` says "Current schema version: 7" — code is v8
  (`AppDatabase.kt` + `Migrations.kt` + `app/schemas/8.json`).
- `.kiro/steering/source-files.md` comment for `AppDatabase.kt` says
  "version 7".
- `docs/architecture.md` does not mention `CosmeticEntity` or
  `HealthConnectModule` is an empty placeholder. Both are true in code.

These are steering-style docs; fixing them is out of scope for this
archaeology phase but noted here for follow-up.
