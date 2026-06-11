# Technical Concepts

*Archaeology Phase 5 — list derived from the actual code tree, not docs.
Companion to `small_summary.md` (Phase 1), `intro2codebase.md` +
`intro2deployment.md` (Phase 2), and `traces/` (Phase 3). Each entry: ≤3
sentences, implementation status (Fully / Partial / Missing), and file
pointers. Most central concepts first, then branching sub-concepts.*

---

## 1. Central platform choices

### Android native, Kotlin-only application
Single `:app` Gradle module, Kotlin 2.3.0 on JVM target 17, packaged as the
single APK/AAB `com.whitefang.stepsofbabylon` v1.0.0. minSdk 34, targetSdk 36,
enforced by AGP 9.0.1.
**Implementation status:** Fully.
**Files:** `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.

### Clean Architecture with three layers
`presentation → domain ← data`, with `domain/` having zero Android imports
(pure Kotlin only). Domain never leaks data-layer types and presentation
never imports from data.
**Implementation status:** Fully — enforced by convention, not tooling.
**Files:** whole tree under `app/src/main/java/com/whitefang/stepsofbabylon/{domain,data,presentation}/`,
layer rules stated in `.kiro/steering/structure.md`.

### MVVM + StateFlow + Jetpack Compose
Every screen ViewModel is `@HiltViewModel`, exposes an immutable
`*UiState` via `StateFlow` combined from repository flows, and the Compose
screen collects via `collectAsStateWithLifecycle()`. Battle is the one
exception — rendering runs on a SurfaceView, not Compose.
**Implementation status:** Fully.
**Files:** `presentation/home/HomeViewModel.kt`, `presentation/workshop/WorkshopViewModel.kt`,
10 more ViewModels; `presentation/MainActivity.kt` (NavHost).

### Dagger Hilt dependency injection via KSP
Six `@InstallIn(SingletonComponent::class)` modules (`Database`, `Repository`,
`Step`, `HealthConnect`, `Billing`, `Ad`) wire all bindings. Use cases are
*not* Hilt-injected — they are plain Kotlin classes instantiated inline
inside ViewModels (ADR-0003 run log confirmed this convention).
**Implementation status:** Fully.
**Files:** `di/DatabaseModule.kt`, `di/RepositoryModule.kt`, `di/StepModule.kt`,
`di/HealthConnectModule.kt` (organisational placeholder), `di/BillingModule.kt`,
`di/AdModule.kt`; `StepsOfBabylonApp.kt` (`@HiltAndroidApp`).

### KSP for annotation processing (never kapt)
Hilt, Room, and Hilt-Work compilers all go through KSP. Enforced in
`CONSTRAINTS.md` as a hard "never do" rule.
**Implementation status:** Fully.
**Files:** `app/build.gradle.kts` dependencies block.

### Gradle version catalog as single dependency source
All dependency versions live in `gradle/libs.versions.toml`. Hardcoding
versions in build.gradle.kts is explicitly forbidden.
**Implementation status:** Fully.
**Files:** `gradle/libs.versions.toml`; `app/build.gradle.kts`.

---

## 2. Persistence & data

### Room as single source of truth
`AppDatabase` at schema version 8 with 12 entities and 12 DAOs. All game
state flows from Room via reactive `Flow<T>` queries — no `DataStore`,
no in-memory caches outside of transient battle state.
**Implementation status:** Fully.
**Files:** `data/local/AppDatabase.kt`, all 12 `*Entity.kt` + `*Dao.kt`,
8 `RepositoryImpl.kt`, `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/{7,8}.json`.

### SQLCipher-encrypted database with Keystore-managed passphrase
`System.loadLibrary("sqlcipher")` in the application class; passphrase
generated via `SecureRandom(32 bytes)`, encrypted with an Android Keystore
AES-GCM key, stored as Base64 in SharedPreferences. On decrypt failure the
prefs blob is wiped and a fresh key is generated.
**Implementation status:** Fully. Known gap: the encrypted *DB file* is
not wiped alongside the passphrase, so device restore produces an
unreadable DB (trace 12 §9).
**Files:** `data/local/DatabaseKeyManager.kt`, `di/DatabaseModule.kt`, `StepsOfBabylonApp.kt`.

### Explicit Room migrations
`AppMigrations.ALL` holds every `Migration` object in version order; Room is
built with `.addMigrations(*AppMigrations.ALL).fallbackToDestructiveMigrationOnDowngrade(true)`.
v7→v8 is the first and only migration so far (adds
`DailyStepRecordEntity.battleStepsEarned`).
**Implementation status:** Fully for current version pair; framework in
place for future ones.
**Files:** `data/local/Migrations.kt`, `di/DatabaseModule.kt`; ADR-0003.

### Schema export contract
`room { schemaDirectory("$projectDir/schemas") }` exports a JSON snapshot of
every database version. Schemas are version-controlled and serve as
migration reference but are not loaded at runtime (trace 12 §3).
**Implementation status:** Fully.
**Files:** `app/build.gradle.kts` (`room { ... }`), `app/schemas/.../{1..8}.json`.

### Room TypeConverters for complex columns
Two converters (`Map<Int,Int>` and `Map<String,Int>`) serialise to JSON via
`org.json.JSONObject`. Used for `PlayerProfileEntity.bestWavePerTier` and
`DailyStepRecordEntity.activityMinutes`.
**Implementation status:** Fully.
**Files:** `data/local/Converters.kt`, consumed in `*Entity.kt`.

### Atomic currency adjustments via SQL expressions
DAO queries such as `UPDATE ... SET currentStepBalance = MAX(0, currentStepBalance + :delta)`
perform balance updates in a single SQL statement, avoiding read-modify-write
races. `MAX(0, …)` clamp added during R10 (currency guards).
**Implementation status:** Fully.
**Files:** `data/local/PlayerProfileDao.kt`, `data/local/DailyStepDao.kt`.

### Single-row entity pattern
`PlayerProfileEntity` and `DropGeneratorState` both use a single-row pattern
(`@PrimaryKey val id: Int = 1`). All currency, lifetime stats, streaks, and
monetization flags live in this one table — currently 27 columns.
**Implementation status:** Fully.
**Files:** `data/local/PlayerProfileEntity.kt`, `data/local/PlayerProfileDao.kt`.

### Repository pattern with domain↔entity mapping
Eight domain-defined interfaces (`PlayerRepository`, `WorkshopRepository`, …)
implemented by `@Singleton @Inject constructor` classes in `data/repository/`.
Every implementation maps entities to domain models in a `.toDomain()` extension
so the domain layer never sees `*Entity` types.
**Implementation status:** Fully.
**Files:** `domain/repository/*.kt` (8 interfaces), `data/repository/*Impl.kt` (8 impls).

### Reactive repository contract
Read methods return `Flow<T>` (via `dao.get().filterNotNull().map { toDomain() }`);
write methods are `suspend`. ViewModels `combine()` multiple flows and expose a
single `StateFlow` with `SharingStarted.WhileSubscribed(5000)`.
**Implementation status:** Fully.
**Files:** every ViewModel, every `RepositoryImpl`.

---

## 3. Step ingestion stack

### `TYPE_STEP_COUNTER` sensor adapter
`StepSensorDataSource` wraps the cumulative sensor value into a
`callbackFlow<Long>` of *deltas* by subtracting the previous reading, with
`SENSOR_DELAY_NORMAL`. Registration/unregistration happen in the flow's
lifecycle callbacks.
**Implementation status:** Fully.
**Files:** `data/sensor/StepSensorDataSource.kt`, `di/StepModule.kt` (SensorManager provider).

### Foreground service (`FOREGROUND_SERVICE_HEALTH`)
`StepCounterService` runs `START_STICKY` with `foregroundServiceType="health"`,
collects the sensor flow into `DailyStepManager`, writes a 30 s-throttled
notification showing live counts + Workshop/Battle action buttons. Can
fall back to a minimal "Step tracking active" notification when the user
disables live updates (R2-05).
**Implementation status:** Fully.
**Files:** `service/StepCounterService.kt`, `service/StepNotificationManager.kt`,
`AndroidManifest.xml` (service element), `data/NotificationPreferences.kt`.

### Heartbeat-based service↔worker handoff
`StepIngestionPreferences` stores a service heartbeat (2-minute threshold) and
a day-start sensor counter. `StepSyncWorker` skips sensor catch-up while the
service is alive; otherwise it reads the current sensor counter, diffs against
the Room-persisted `sensorSteps`, and credits the gap.
**Implementation status:** Fully (R01 unification; trace 02).
**Files:** `data/sensor/StepIngestionPreferences.kt`, `service/StepSyncWorker.kt`,
`service/StepCounterService.kt`.

### Periodic WorkManager sync (`@HiltWorker`)
`StepSyncWorker` is a `CoroutineWorker` enqueued as a 15-minute periodic
`PeriodicWorkRequest` via `StepSyncScheduler`. Work performs sensor catch-up
(if needed) + HC gap-fill + HC cross-validation + activity-minute conversion +
smart-reminder check.
**Implementation status:** Fully.
**Files:** `service/StepSyncWorker.kt`, `service/StepSyncScheduler.kt`,
`StepsOfBabylonApp.kt` (`Configuration.Provider`, `HiltWorkerFactory`).

### Boot recovery
`BootReceiver` listens for `BOOT_COMPLETED` and restarts `StepCounterService`
via `startForegroundService`. `RECEIVE_BOOT_COMPLETED` is declared in the
manifest.
**Implementation status:** Fully.
**Files:** `service/BootReceiver.kt`, `AndroidManifest.xml`.

### `DailyStepManager` orchestrator with follow-on pipeline
Single `@Singleton` that funnels every credit through rate limit → velocity
analysis → 50 000-daily-ceiling → Room persist, then runs a 5-stage fan-out
pipeline (widget, supply-drop generation, daily-login, weekly-challenge,
walking-mission progress). Each stage is wrapped in its own `try/catch` so
one failure cannot poison the others.
**Implementation status:** Fully (R2-02 unified the pipeline across sensor
and activity-minute inputs).
**Files:** `data/sensor/DailyStepManager.kt`; trace 04.

### Per-minute overlap deduction map
`stepsPerMinute: MutableMap<Long, Long>` keyed by epoch-minute caps at 1 440
entries and lets `ActivityMinuteConverter` subtract sensor steps from
exercise-minute conversions to prevent double-counting.
**Implementation status:** Fully.
**Files:** `data/sensor/DailyStepManager.kt`, `data/healthconnect/ActivityMinuteConverter.kt`.

### Widget `RemoteViews` + 60 s throttle
`StepWidgetProvider` is an `AppWidgetProvider`; `WidgetUpdateHelper` writes
(`current`, `balance`) to SharedPreferences and calls
`AppWidgetManager.updateAppWidget` at most once per minute. Click on the
widget root launches `MainActivity`.
**Implementation status:** Fully.
**Files:** `service/StepWidgetProvider.kt`, `service/WidgetUpdateHelper.kt`,
`res/layout/widget_step_counter.xml`, `res/xml/step_widget_info.xml`.

---

## 4. Anti-cheat mechanisms

### Rolling-window rate limiter
`StepRateLimiter` enforces 200 steps/min (250 burst) via a 1-minute rolling
queue. Rejected deltas are counted into `AntiCheatPreferences`.
**Implementation status:** Fully.
**Files:** `data/sensor/StepRateLimiter.kt`.

### Step velocity analyser
`StepVelocityAnalyzer` keeps a 15-minute rolling window and returns a 1.0 /
0.5 / 0.0 penalty multiplier when it detects instant jumps (idle→spike) or
constant-rate patterns (CV < 0.05 over 10 minutes) that suggest shaking or
spoofing.
**Implementation status:** Fully.
**Files:** `data/sensor/StepVelocityAnalyzer.kt`.

### Graduated Health Connect cross-validation with escrow
`StepCrossValidator` implements a 4-level state machine (0→3). Levels 0/1
escrow excess by *deducting from the wallet*; Levels 2/3 cap credit at HC
value (or HC minus 10%). Offenses decay after 7 quiet days.
**Implementation status:** Fully. Known gap: `spendSteps` + `updateEscrow`
are two separate writes, not wrapped in a transaction (trace 03 §9, Phase 4 item 2).
**Files:** `data/healthconnect/StepCrossValidator.kt`, `data/anticheat/AntiCheatPreferences.kt`.

### Activity-minute input validation
`ActivityMinuteValidator` drops sessions shorter than 2 minutes, truncates
sessions longer than 4 hours, and caps at 5 distinct exercise types per day.
Rejections counted into `AntiCheatPreferences`.
**Implementation status:** Fully.
**Files:** `data/healthconnect/ActivityMinuteValidator.kt`, `data/healthconnect/ExerciseSessionReader.kt`.

### SharedPreferences-backed offence counters
`AntiCheatPreferences` holds daily counters (rate-rejected, velocity-penalised,
activity-rejected) plus a persistent cross-validation offence count with a
7-day decay — no Room entity, no DB migration.
**Implementation status:** Fully.
**Files:** `data/anticheat/AntiCheatPreferences.kt`.

### Battle-Step day cap via DAO
`AwardBattleSteps` reads `DailyStepRecordEntity.battleStepsEarned`, caps the
request at `DAILY_BATTLE_STEP_CAP = 2_000L`, then does `addSteps` +
`incrementBattleSteps` (UPSERT). Cap is separate from the 50 k walking ceiling
and never additive (ADR-0003, CONSTRAINTS.md).
**Implementation status:** Fully. Known gap: the two writes are not wrapped
in a `@Transaction` (trace 07 §9, Phase 4 item 2).
**Files:** `domain/usecase/AwardBattleSteps.kt`, `data/local/DailyStepDao.kt`.

---

## 5. Battle renderer & game loop

### Custom `SurfaceView` outside Compose
`GameSurfaceView` holds a `SurfaceHolder.Callback`; once the surface is
created it constructs and starts a `GameLoopThread`. Surface teardown ends
the thread gracefully; Compose only owns the overlay (HUD, pause, post-round).
**Implementation status:** Fully.
**Files:** `presentation/battle/GameSurfaceView.kt`, `presentation/battle/BattleScreen.kt` (`AndroidView` wrapper).

### Fixed-timestep game loop with accumulator
`GameLoopThread` uses `System.nanoTime()` and `TICK_NS = 16_666_667L` (60
UPS). The speed multiplier scales the accumulator, not `dt`, so physics stay
deterministic across 1× / 2× / 4× speeds.
**Implementation status:** Fully.
**Files:** `presentation/battle/GameLoopThread.kt`; trace 06.

### `@Volatile` cross-thread state exposure
The game thread writes to `@Volatile` fields on `GameEngine` (`cash`,
`roundOver`, `totalEnemiesKilled`, `onStepReward`, `activeOverdrive`, …) and
the ViewModel polls them every 200 ms via `viewModelScope` to drive UI.
**Implementation status:** Fully.
**Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/BattleViewModel.kt`.

### Callback-hop to `viewModelScope` for persistence
`GameEngine.onStepReward` is invoked on the game thread per kill; the VM
wires a callback that immediately `viewModelScope.launch` → `AwardBattleSteps`
so Room IO never blocks the render loop. Callback is nulled in
`BattleViewModel.onCleared()`.
**Implementation status:** Fully (ADR-0003, trace 07).
**Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/BattleViewModel.kt` (`wireStepRewardCallback`).

### Entity system with pending-add queue
`GameEngine.entities: MutableList<Entity>` is iterated each tick; new
entities go into `pendingAdd` and are merged in at the start of the next
update to avoid `ConcurrentModificationException`.
**Implementation status:** Fully.
**Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/engine/Entity.kt`.

### Pre-allocated particle pool (object pooling)
`ParticlePool` allocates 200 `Particle` instances up-front and hands out
recycled instances via `acquire/release` to avoid GC pressure during combat.
`EffectEngine` owns the pool and the `ScreenShake`.
**Implementation status:** Fully.
**Files:** `presentation/battle/effects/ParticlePool.kt`,
`presentation/battle/effects/EffectEngine.kt`.

### Reduced-motion gating on animations
`ReducedMotionCheck` reads system `ANIMATOR_DURATION_SCALE`; when 0 the engine
skips screen shakes, projectile trails, non-essential particle effects. No
in-app toggle — respects OS accessibility setting directly.
**Implementation status:** Fully.
**Files:** `presentation/battle/effects/ReducedMotionCheck.kt`, consumed in
`GameEngine.init()`.

### `SoundPool` audio with shoot throttling
`SoundManager` loads 7 placeholder `.ogg` sine-wave files; volume/mute are
SharedPreferences-backed. Shoot sound is throttled to avoid audio stampede
during high-fire-rate rounds.
**Implementation status:** Partial — plumbing is complete, audio assets are
placeholders (STATE.md known issue).
**Files:** `presentation/audio/SoundManager.kt`, `data/SoundPreferences.kt`, `res/raw/sfx_*.ogg`.

---

## 6. UI, navigation, and notifications

### Single-Activity + Compose NavHost
`MainActivity` is the lone `Activity` (plus `HealthConnectPermissionActivity`
for the HC rationale); it hosts a `Scaffold { NavHost }` with 12 routes,
hides the bottom nav on `battle`. `enableEdgeToEdge()`.
**Implementation status:** Fully.
**Files:** `presentation/MainActivity.kt`, `presentation/navigation/Screen.kt`,
`presentation/navigation/BottomNavBar.kt`.

### Deep-link via `pendingNavigation` `MutableStateFlow`
Both `onCreate` extras (cold start) and `onNewIntent` (warm start) push the
target route into a `MutableStateFlow<String?>`; a `LaunchedEffect` collects it
and calls `navController.navigate(route)`, then nulls the flow. Only Home,
Workshop, Battle, Missions, Supplies are currently handled.
**Implementation status:** Partial — Store/Stats/Weapons/Cards/Economy/Settings
routes are not wired into the collector (trace 10 §8).
**Files:** `presentation/MainActivity.kt`.

### Permission request flow
`MainActivity` requests `ACTIVITY_RECOGNITION` + `POST_NOTIFICATIONS` via
`ActivityResultContracts.RequestMultiplePermissions`, then HC permissions via
`PermissionController.createRequestPermissionResultContract()`.
**Implementation status:** Fully.
**Files:** `presentation/MainActivity.kt`, `presentation/HealthConnectPermissionActivity.kt`,
`AndroidManifest.xml` (7 `uses-permission` entries).

### Four notification channels
`step_counter` (ongoing FG), `supply_drops`, `milestones`, `reminders`. Each
has its own notifier class with a dedicated PendingIntent route and its own
preference toggle.
**Implementation status:** Fully.
**Files:** `service/StepNotificationManager.kt`, `service/SupplyDropNotificationManager.kt`,
`service/MilestoneNotificationManager.kt`, `service/SmartReminderManager.kt`,
`data/NotificationPreferences.kt`, `data/MilestoneNotificationPreferences.kt`.

### Scaffold + SnackbarHost UX-feedback pattern
`userMessage: String? = null` field on every action-taking `*UiState`;
`LaunchedEffect(state.userMessage)` shows a `SnackbarHostState` message and
calls `clearMessage()`. `isProcessing` guards double-tap races.
**Implementation status:** Fully (added R10).
**Files:** `presentation/{workshop,cards,labs,store}/*Screen.kt` + `*ViewModel.kt` + `*UiState.kt`.

### Lifecycle-aware date refresh
Home and Stats screens observe a `LifecycleEventObserver(ON_RESUME)` and call
`viewModel.refreshDate()`, which pushes `LocalDate.now().toString()` into a
`MutableStateFlow` and flat-maps it into downstream queries. Missions VM
detects day change inside its 1 s ticker.
**Implementation status:** Fully (R10).
**Files:** `presentation/home/HomeScreen.kt` + `HomeViewModel.kt`,
`presentation/stats/StatsScreen.kt` + `StatsViewModel.kt`,
`presentation/missions/MissionsViewModel.kt`.

---

## 7. Security, privacy, & build hardening

### Network security config blocking cleartext
Manifest references `@xml/network_security_config`, which denies cleartext
traffic entirely. The app makes no outbound network requests in v1.0 (offline
first).
**Implementation status:** Fully.
**Files:** `res/xml/network_security_config.xml`, `AndroidManifest.xml`.

### Disabled backup
`android:allowBackup="false"` in the manifest disables both
`android:backupAgent` and Android Auto Backup. Rationale (R05): local-only
game, no meaningful state to restore, eliminates key-mismatch-on-restore
bugs.
**Implementation status:** Fully.
**Files:** `AndroidManifest.xml`, trace 12.

### R8 minify + resource shrinking + ProGuard keep rules
Release build sets `isMinifyEnabled = true` and `isShrinkResources = true`.
`proguard-rules.pro` keeps Health Connect SDK entry points, SensorEventListener
callbacks, `ListenableWorker` subclasses, Room entity fields, org.json.
**Implementation status:** Fully.
**Files:** `app/build.gradle.kts` (`buildTypes.release`), `app/proguard-rules.pro`.

### Opt-in release signing
`keystore.properties` is gitignored; if present, `signingConfigs.release` is
wired into the release build type. `signing-guide.md` documents manual
keystore generation and Play App Signing.
**Implementation status:** Fully (pipeline); keystore itself is a manual
release-time task (STATE.md gap).
**Files:** `app/build.gradle.kts`, `docs/release/signing-guide.md`, `.gitignore`.

---

## 8. Reproducibility, testing, & tooling

### Injected `Random` for seedable stochastic logic
Three use cases with `private val random: Random = Random` constructor
parameter: `CalculateDamage` (crit rolls), `OpenCardPack` (rarity + candidate
selection), `GenerateSupplyDrop` (trigger + reward rolls).
**Implementation status:** Fully for those three; no global RNG abstraction
(Phase 4 item 1 covers the broader gap).
**Files:** `domain/usecase/CalculateDamage.kt`, `domain/usecase/OpenCardPack.kt`,
`domain/usecase/GenerateSupplyDrop.kt`.

### Date-seeded RNG for deterministic daily content
`GenerateDailyMissions` seeds `Random(todayDate.hashCode())` so every device
(same day) picks the same mission trio; idempotent on same date.
**Implementation status:** Fully.
**Files:** `domain/usecase/GenerateDailyMissions.kt`.

### Default-parameter pattern for time inputs
Time-sensitive functions take `today: String = LocalDate.now().toString()` or
`now: Long = System.currentTimeMillis()` as default arguments so tests can
pass fixed values. Used in `AwardBattleSteps`, `StartResearch`, etc.
**Implementation status:** Partial — 53 `System.currentTimeMillis()`/
`LocalDate.now()` call sites exist across 33 files; a centralised `TimeProvider`
is the Phase 4 item 1 proposal.
**Files:** `domain/usecase/AwardBattleSteps.kt`, `domain/usecase/StartResearch.kt`, etc.

### JVM-only test suite with JUnit 5 + Robolectric
412 tests total, run via `./run-gradle.sh test`. Mix of pure JVM (use cases,
domain models, VMs with StandardTestDispatcher) and Robolectric (widget,
schema round-trip).
**Implementation status:** Fully for JVM; no instrumented (`androidTest`)
suite yet (README and Plan 31 note this).
**Files:** `app/src/test/java/...`, `app/build.gradle.kts` (testOptions).

### In-memory fake repositories
15+ `Fake*Repository`, `Fake*Dao`, `FakeBillingManager`, `FakeRewardAdManager`
classes with `MutableStateFlow` backing — used for all ViewModel and use case
tests in place of mocks.
**Implementation status:** Fully.
**Files:** `app/src/test/java/.../fakes/*.kt`.

### `run-gradle.sh` non-TTY wrapper
Gitignored bash wrapper that runs `./gradlew "$@" > /tmp/gradle_out.txt 2>&1 &`
so Gradle doesn't buffer output in non-TTY environments (Kiro CLI, CI).
Recreation instructions in `README.md`.
**Implementation status:** Fully.
**Files:** `run-gradle.sh` (gitignored), `README.md` non-TTY section.

---

## 9. Cross-cutting technical patterns

### Eight SharedPreferences wrapper classes
One class per concern (`BiomePreferences`, `NotificationPreferences`,
`SoundPreferences`, `MilestoneNotificationPreferences`, `StepIngestionPreferences`,
`AntiCheatPreferences` + `DatabaseKeyManager` prefs + `StepWidgetProvider` prefs).
Avoids one god-prefs file; no `DataStore`.
**Implementation status:** Fully.
**Files:** `data/BiomePreferences.kt`, `data/NotificationPreferences.kt`,
`data/SoundPreferences.kt`, `data/MilestoneNotificationPreferences.kt`,
`data/sensor/StepIngestionPreferences.kt`, `data/anticheat/AntiCheatPreferences.kt`,
`data/local/DatabaseKeyManager.kt`, `service/StepWidgetProvider.kt`.

### `lazy` + `by lazy` for cross-concern use-case construction
`DailyStepManager` declares `TrackDailyLogin` / `TrackWeeklyChallenge` as
`by lazy { ... }` because those use cases depend on DAOs that Hilt provides
but the use case itself is not Hilt-scoped. Matches ADR-0003 convention.
**Implementation status:** Fully.
**Files:** `data/sensor/DailyStepManager.kt`, many ViewModels.

### Sealed `Result` types for use case outcomes
`PurchaseResult`, `AdResult`, `StartResearch.Result`, `OpenCardPack.Result`,
etc. — sealed class with `Success` / error variants, consumed in ViewModels
via `when` exhaustiveness.
**Implementation status:** Fully.
**Files:** `domain/model/BillingProduct.kt`, `domain/model/AdPlacement.kt`,
`domain/usecase/StartResearch.kt`, `domain/usecase/OpenCardPack.kt`, etc.

### Canvas rendering for charts and HUD
`WalkingHistoryChart` is a Compose `Canvas` that draws bar chart + axes
manually, no third-party library. Same approach for the battle HUD health bar
(`HealthBarRenderer`).
**Implementation status:** Fully.
**Files:** `presentation/stats/WalkingHistoryChart.kt`,
`presentation/battle/ui/HealthBarRenderer.kt`.
