# Philosophy — Code-Inferred Foundations

*Archaeology Phase 6 — what the codebase *consistently* does, treated as
the project's implicit design philosophy. Every claim traces to specific
files or repeated patterns. Companion docs: `project_description.md`,
`known_requirements.md`.*

> These are **observed** principles, not prescriptive ones. If a future
> change breaks one of them, that is a judgement call, not automatically
> wrong — but the weight of existing code sits on the side described
> below.

---

## 1. Implicit design principles

### Code is the balance sheet, not JSON
Every piece of game-balance data — costs, multipliers, scaling, cooldowns,
reward amounts — lives in Kotlin enums and `const val`s, not in external
configuration. `UpgradeType` (23 configs, `domain/model/UpgradeType.kt`),
`ResearchType`, `CardType` (9 types with `valueLv1` / `valueLv5`),
`UltimateWeaponType`, `OverdriveType`, `EnemyType`, `Milestone`,
`DailyMissionType`, `BillingProduct`, `AdPlacement`, `TierConfig` all
expose their numeric values as properties on the enum entries. There is
no YAML, no JSON, no `assets/balance.json`.

Consequence: balance is typed, reviewed, unit-tested (`balance/*Test.kt`,
39 tests), and shipped atomically with code. It cannot be hot-patched or
A/B tested without an app release — which the project has explicitly
accepted (no Remote Config; see "Tradeoffs" below).

### Steps are sacred
A single rule threads through every layer: **Steps can only be minted by
walking (or Health Connect exercise conversion) and capped battle-kill
rewards; they are never purchasable, never generated passively, never
additive with other caps.** Evidence:

- `BillingProduct` enum does not contain any entry that grants Steps
  (`domain/model/BillingProduct.kt`).
- `DailyStepManager.DAILY_CEILING = 50_000L` hard-caps walking.
- `AwardBattleSteps.DAILY_BATTLE_STEP_CAP = 2_000L` is a *separate*
  bucket tracked on `DailyStepRecordEntity.battleStepsEarned` — not
  additive (ADR-0003, `CONSTRAINTS.md`).
- `GameEngine.handleEnemyDeath` only awards a flat per-type Step
  reward; explicitly not multiplied by Fortune/Cash Bonus/Golden
  Ziggurat (same ADR).

This is the single strongest design invariant in the project.

### Offline-first as a product contract, not a convenience
No HTTP client, no WebSocket, no gRPC, no Firebase, no analytics SDK.
`network_security_config.xml` sets `cleartextTrafficPermitted="false"`
at the base config. `AndroidManifest.xml` has `allowBackup="false"`. The
privacy policy
(`docs/release/privacy-policy.md`) formalises this as a promise: data
stays on device. Going online would require both adding a dependency and
relaxing the security config — neither is a casual change.

### Local state is encrypted by default
Room is wrapped with `SupportOpenHelperFactory(passphrase)` in
`di/DatabaseModule.kt`, and the passphrase is 32 `SecureRandom` bytes
encrypted with an Android Keystore AES-256-GCM key aliased
`steps_of_babylon_db_key`
(`data/local/DatabaseKeyManager.kt`). The passphrase never leaves the
device and never appears in the APK. On Keystore decrypt failure the
passphrase blob is wiped; the DB file is left behind (known gap, noted
in trace 12 §9 and `missing_concepts_list.md`).

### Domain has zero Android imports
The whole of `app/src/main/java/com/whitefang/stepsofbabylon/domain/` is
pure Kotlin. Enforced by convention (no tooling check), declared in
`CONSTRAINTS.md` and `.kiro/steering/structure.md`, repeated in every
package-level run-log entry. Use cases (`domain/usecase/*.kt`) are
plain Kotlin classes with `operator fun invoke(...)`, instantiated
inline by ViewModels — **not** Hilt-injected. The rationale
(ADR-0003) is that Hilt's job is wiring infrastructure, not managing
game rules.

### Room is the single source of truth
Every observable piece of game state is exposed as a `Flow<T>` from a
Room DAO; every write goes through a DAO suspend method. There is no
in-memory cache, no DataStore, no secondary persistence for game state.
ViewModels `combine()` flows and `stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), initial)`. SharedPreferences exists
only for concerns Room is genuinely wrong for: the DB passphrase itself,
service↔worker heartbeat, anti-cheat counters that decay on a wall
clock, widget RemoteViews data, and four tiny user-settings files.

### Geometric cost curves everywhere
The formula `baseCost × scaling^level` is the universal cost shape —
Workshop (`CalculateUpgradeCost`), Labs (`CalculateResearchCost`),
Ultimate Weapons (`UltimateWeaponType.upgradeCost`). Listed in
`CONSTRAINTS.md` as a game-design invariant; validated by
`balance/CostCurveTest.kt`. Hard caps (`crit min(…, 0.80)`,
`defence min(…, 0.75)`) are applied inside the functions that compute
the derived stat, not outside.

### "Credit → persist → fan out" pipeline
`DailyStepManager.recordSteps` and `recordActivityMinutes` both funnel
into the same four-step path: rate limit → velocity → ceiling →
persist → `runFollowOnPipeline(timestampMs)`. The fan-out stages
(widget, supply drop, daily login, weekly challenge, walking missions)
are each wrapped in their own `try/catch(_: Exception) {}` so one
subsystem's failure cannot poison the others (R2-02). This pattern is
copied by any new "this happens after steps are credited" feature.

### Reactive reads, suspend writes
Repository interfaces (`domain/repository/*.kt`) have exactly two
shapes: `fun observeX(): Flow<X>` for reads, `suspend fun mutateX(...)`
for writes. ViewModels collect flows; action handlers `launch`
suspend work. Writes that touch multiple columns on the player profile
go through atomic DAO queries like
`UPDATE ... SET currentStepBalance = MAX(0, currentStepBalance + :delta)`
(`PlayerProfileDao`) — no read-modify-write races, and a guaranteed
non-negative balance (R10 currency guards).

### Immutable `ResolvedStats` and pure stat composition
`domain/usecase/ResolveStats.kt` returns an immutable
`ResolvedStats` data class; `ApplyCardEffects` returns a `copy(...)`.
The engine stores `ResolvedStats` and only replaces it (via `setStats`)
when in-round upgrades change — never mutates it. This makes stat
resolution a unit-testable pure function.

### Seamed randomness and seamed time where testability matters
Three use cases take an **injectable `Random`** constructor parameter
with default `Random`: `CalculateDamage`, `OpenCardPack`,
`GenerateSupplyDrop`. Time-sensitive use cases take a **default-
parameter `today: String = LocalDate.now().toString()`** (or `now: Long
= System.currentTimeMillis()`): `AwardBattleSteps`, `TrackDailyLogin`,
`StartResearch`. These are *the* two contracts for
non-determinism / environment dependence in the domain layer — the
project has deliberately declined to introduce global `Clock` or
`Random` abstractions (see `5_things_or_not.md` §1 for the separate
proposal to do so).

`GenerateDailyMissions` is a study in the opposite direction: it seeds
`Random(todayDate.hashCode())` so every device (on the same day) gets
the same mission trio — **determinism by design**.

### Fail-fast schema, fail-soft pipeline
Two explicit choices, visible in wiring:

- **Database upgrades fail fast.** `fallbackToDestructiveMigration()`
  was deliberately removed (R2-06) so bumping the `@Database(version)`
  without writing a `Migration` crashes at startup. Only downgrades are
  destructive (dev/QA only).
- **Step ingestion side-effects fail soft.** The 5 stages of
  `runFollowOnPipeline` each sit inside `try/catch`. A crash in one
  notifier never stops another.

Asymmetric on purpose: schema violations are programmer errors that
must be caught; runtime side-effects on a user's phone must degrade,
not cascade.

### One Activity, one SurfaceView
The app is aggressively single-Activity. `MainActivity` hosts every
Compose screen via `NavHost`. The one non-Compose surface is the
battle `SurfaceView` — `AndroidView(factory = GameSurfaceView(context))`
sandwiched inside a Compose screen with Compose HUD overlays on top
(`presentation/battle/BattleScreen.kt`). This boundary is deliberate:
everything that benefits from Compose's re-composition uses Compose;
the one thing that needs a deterministic 60 UPS render loop gets its
own thread and canvas.

### Stub-now, swap-later for external SDKs
`BillingManager` / `RewardAdManager` are domain interfaces
(`domain/repository/`). Real implementations will bind into the same
interfaces by replacing the `@Binds` in `di/BillingModule.kt` /
`di/AdModule.kt`. Today those modules bind `StubBillingManager` /
`StubRewardAdManager` — with 500 ms / 1 s `delay()` to simulate
latency and a 100 % success rate. The whole app above the DI layer is
already SDK-shaped; real Play Billing + AdMob integration is a wiring
change, not a refactor.

### Cross-thread communication via `@Volatile` + polling
The game loop runs on `Thread("GameLoop")` and the ViewModel runs on
`viewModelScope` (main). They communicate through `@Volatile` fields
on `GameEngine` (`cash`, `roundOver`, `totalEnemiesKilled`,
`onStepReward`, `activeOverdrive`, etc.) that the ViewModel polls
every 200 ms. No locks beyond the single `synchronized(surfaceHolder)`
block around canvas rendering. Side-effects that must hit Room are
pushed back to `viewModelScope` via callbacks
(`GameEngine.onStepReward`) — the game thread never blocks on I/O.

### UX feedback is always visible
Post-R10 convention: action handlers set a `userMessage: String?` on
`*UiState`, screens show it via `Scaffold { SnackbarHost }`, then call
`clearMessage()`. Double-tap races are prevented with an
`isProcessing` guard. Applies consistently across Workshop / Cards /
Labs / Store. Silent failure is considered a bug.

### Enum-name persistence, explicit keep rule
Enums that persist to Room are stored by their `.name` string
(`UpgradeType.name`, `CardType.name`, etc.) — not by ordinal. R8 is
configured with
`-keep enum com.whitefang.stepsofbabylon.domain.model.** { *; }` so
release obfuscation cannot rename them and silently invalidate saves.
Rationale is declared in the keep rule comment itself.

---

## 2. Consistent patterns

### Coding patterns

- **Files per concern, not per feature.** One file per entity, one
  per DAO, one per use case, one per ViewModel, one per
  `*UiState.kt`. No "god files".
- **`*Impl`-suffix for data-layer implementations of domain
  interfaces.** `PlayerRepositoryImpl` implements `PlayerRepository`
  and so on — consistent across all 8 repositories.
- **`.toDomain()` / `.toEntity()` mappers colocated with the
  repository implementation.** Domain never sees `*Entity` types.
- **`operator fun invoke(...)` for use cases.** Every use case is
  callable as `useCase(arg1, arg2)`.
- **Sealed classes for multi-outcome operations.**
  `StartResearch.Result`, `OpenCardPack.Result`, `ActivateOverdrive.Result`,
  `PurchaseResult`, `AdResult`. Consumed via exhaustive `when`.
- **Single-row entity pattern.** `PlayerProfileEntity`,
  `DropGeneratorState` use `@PrimaryKey val id: Int = 1`.
- **`@Upsert` over `@Insert(onConflict = REPLACE)`.** Declared as a
  convention in `.kiro/steering/lib-room.md`.
- **`filterNotNull().map { it.toDomain() }` in every repository
  Flow.** Room returns nullable rows before insert; the pattern
  suppresses until the row exists.
- **One SharedPreferences file per concern, wrapped in a small class.**
  Eight separate wrapper classes (`BiomePreferences`,
  `NotificationPreferences`, `SoundPreferences`,
  `MilestoneNotificationPreferences`, `StepIngestionPreferences`,
  `AntiCheatPreferences`, `DatabaseKeyManager`'s prefs,
  `StepWidgetProvider`'s prefs) instead of one god-prefs file.

### Architectural patterns

- **Clean Architecture layering** enforced by package:
  `presentation → domain ← data`. Enforced by convention, not by
  Gradle modules (`settings.gradle.kts` only includes `:app`).
- **MVVM** — ViewModels expose `StateFlow<*UiState>`, Compose
  collects via `collectAsStateWithLifecycle()`, actions go back to
  the ViewModel.
- **Repository pattern for all persistent state.** Exactly 8 domain
  interfaces, 8 `@Singleton @Inject constructor` implementations.
- **Six Hilt modules, all `SingletonComponent`.** `DatabaseModule`
  (Room + 12 DAOs), `RepositoryModule` (8 `@Binds`), `StepModule`
  (SensorManager), `HealthConnectModule` (empty organisational
  placeholder), `BillingModule`, `AdModule`.
- **`@HiltWorker` + `@AssistedInject`** only where needed
  (`StepSyncWorker`), with `StepsOfBabylonApp : Configuration.Provider`
  supplying a `HiltWorkerFactory`.
- **One NavHost, 12 sealed-class routes.**
  `presentation/navigation/Screen.kt`.

### Testing patterns

- **JVM-only tests.** 412 tests via JUnit 5, `StandardTestDispatcher`
  + `runTest` for coroutines, Robolectric only where framework
  classes are unavoidable (widget, Room schema round-trip, deep-link
  parsing).
- **In-memory fakes over mocks.** 15+ `Fake*Repository` / `Fake*Dao` /
  `FakeBillingManager` / `FakeRewardAdManager` classes with
  `MutableStateFlow` backing. Mockito is present for Android
  framework classes only.
- **Test file per production file.** Same package, same name +
  `Test`.
- **Balance tested as code.** `balance/*Test.kt` treats balance
  drift as a test failure, not a review gate.
- **Seeded randomness in tests.** `CalculateDamage(Random(seed))`,
  `OpenCardPack(Random(seed))`, `GenerateSupplyDrop(Random(seed))`
  for deterministic assertions.

### Operational patterns

- **Foreground service + periodic worker + widget + boot receiver.**
  Four redundant paths keep step counting alive across every OS state.
- **Heartbeat protocol between service and worker** (2-minute
  threshold, `StepIngestionPreferences.isServiceAlive(now)`) to
  prevent double-counting.
- **Throttled external notifications.** 30 s throttle for the
  persistent step notification, 60 s throttle for widget updates.
- **Four distinct notification channels**, one manager per
  channel, one PendingIntent per deep-link route.
- **Deep-link via single `pendingNavigation: MutableStateFlow<String?>`**
  with cold-start and `onNewIntent` both pushing into it.
- **Runtime permission requests batched in `MainActivity`**
  via `ActivityResultContracts.RequestMultiplePermissions` + the
  Health Connect `PermissionController` contract.
- **Lifecycle-aware date refresh** instead of ticker loops on
  Home / Stats (R10). Missions retains a 1 s ticker because it
  needs a midnight countdown.

### Deployment patterns

- **Version catalog as law.** All dependency versions in
  `gradle/libs.versions.toml`; hardcoding versions in
  `build.gradle.kts` is explicitly forbidden
  (`.kiro/steering/tech.md`, `CONSTRAINTS.md`).
- **KSP everywhere.** No `kapt` anywhere. Listed in `CONSTRAINTS.md`
  as "never do".
- **Opt-in release signing.**
  `if (keystorePropertiesFile.exists()) { ... }` in
  `app/build.gradle.kts` — debug builds never need a keystore.
- **Schemas committed.** `app/schemas/*.json` (1.json through
  8.json). `room { schemaDirectory("$projectDir/schemas") }`.
- **No CI.** Manual gate: `./run-gradle.sh test` + `assembleRelease`.
  `run-gradle.sh` is a gitignored non-TTY wrapper; recreation
  snippet in `README.md`.

---

## 3. Architectural decisions evident in structure

### Single Gradle module
`settings.gradle.kts` includes only `:app`. No `:domain` / `:data` /
`:feature` module split, even though the codebase is large enough to
warrant one. Implication: layer boundaries are enforced by human
convention and code review — a future multi-module split is possible
(the package boundaries are already clean).

### Hilt only for infrastructure
The DI graph contains repositories, DAOs, `SensorManager`,
`BillingManager`, `RewardAdManager`, `StepNotificationManager`,
`SupplyDropNotificationManager`, `MilestoneNotificationManager`,
`WidgetUpdateHelper` — the slow-changing, singleton-shaped, multi-
instantiation-resistant stuff. Use cases, domain models, and pure
calculations stay out of Hilt.

### Health Connect instead of Google Fit (ADR-0002)
The Google Fit APIs are being deprecated (sunset 2026). Picking HC
instead is an architectural commitment to Android's canonical
successor rather than a now-and-then bridge library.
`di/HealthConnectModule.kt` is a deliberately-empty organisational
module — the HC wrappers use `@Inject constructor` directly.

### SurfaceView-and-thread battle renderer
The choice to escape Compose for one screen is deliberate. A pure-
Compose implementation would couple frame rate to the UI thread's
scheduling and would make speed controls (1× / 2× / 4×) hard to make
deterministic. The alternative chosen —
`AndroidView(GameSurfaceView)` + `GameLoopThread` — isolates the
simulation from the rest of the UI pipeline at the cost of having to
hand-marshal state via `@Volatile` + 200 ms polling.

### Pure Kotlin use cases, no Hilt
Declared twice in ADR-0003 and in use-case wiring across the codebase:
use cases are plain Kotlin. Tests construct them directly; ViewModels
construct them inline. The implication is that use cases cannot depend
on `@Inject` for anything — dependencies must be passed through their
constructors.

### Eight small SharedPreferences files, not one
Resistance to god-state. Each wrapper class has a clear scope, a
constant for its filename, and a small typed API. Would have been
easier to have one prefs file + string keys; the code chose the
more-surface-area option.

### First-explicit-migration at v7→v8
Versions 1 through 7 are historical development snapshots with no
written `Migration` objects (they were destructive-migrated during dev).
From v7 onward, `AppMigrations.ALL` must contain one entry per bump,
and downgrade is the only "destructive" path.
`MIGRATION_7_8` is the template.

### Convention-only layer enforcement
The repo contains no Checkstyle rule, no Detekt rule, no custom lint
check that validates "domain has no Android imports". Reviewers catch
it. This is a trade-off between tooling cost and current contributor
count.

### No CI
Absent for v1.0. Rationale is not spelled out in code, but consistent
with: single maintainer, offline product, manual release checklist,
`docs/plans/plan-31-play-console.md` treats CI as not-yet-scheduled.

---

## 4. Tradeoffs that appear deliberate

Every tradeoff below is observable in the code, not inferred.

### Privacy vs observability — privacy wins
No Crashlytics, Sentry, Firebase, or equivalent. No structured logging
framework beyond `android.util.Log`. No `BuildConfig` flags to enable
analytics in release. The cost: production crashes surface only via
Play Console's ANR/crash reports; there is no way to see *why* a user
stopped progressing without asking them.

### Offline fidelity vs device portability — fidelity wins
`allowBackup="false"` means Auto Backup won't ship the DB to Google
servers. Combined with SQLCipher + Keystore, a phone-to-phone restore
results in an unreadable database. The code in `DatabaseKeyManager`
handles this by wiping the passphrase blob on decrypt failure — i.e.
resetting progress. R05 (per `trace_12`) made this trade-off
deliberately: the alternative (data loss corruption after restore)
was considered worse.

### On-device anti-cheat vs server-authoritative validation — client wins
Accepted in `CONSTRAINTS.md`. All four anti-cheat layers live on the
device. A rooted or modified build can defeat all of them.
Server-authoritative validation would require backend infrastructure,
accounts, and networking — all of which the project has declined.

### Build-time balance vs live tuning — build-time wins
No Remote Config, no A/B framework, no server-delivered balance
sheet. Tuning requires a release, and the release cadence is
user-initiated through the Play Store. The cost is slow response;
the benefit is that every device at a given `versionCode` is
running an identical, auditable game.

### Foreground service vs battery budget — counting wins
A `foregroundServiceType="health"` service runs continuously while
steps happen. It uses `SENSOR_DELAY_NORMAL` and throttles its
notification to 30 s, but it is still a persistent wake. No "opt-in
to background step counting" setting exists — the project's contract
is "counting works, always".

### Single-module Gradle vs enforced module boundaries — simplicity wins
Keeping `:app` as the only module makes imports frictionless and the
build graph small; it also means "domain has no Android imports" is
a review-time invariant, not a compile-time one. Acceptable today at
the project's scale.

### `@Volatile` polling vs reactive bridge between game thread and VM
The game thread updates `@Volatile` fields on `GameEngine`; the
ViewModel polls every 200 ms. A reactive alternative
(`MutableSharedFlow` + collect on main) would be more idiomatic
Kotlin but heavier per-tick. The project accepts a 200 ms UI latency
ceiling instead.

### Stub SDKs vs feature flag — stubs win
Rather than a build-flavour toggle or a runtime feature flag, the
monetisation path hardcodes `@Binds BillingManager ←
StubBillingManager` in `di/BillingModule.kt`. Real billing lands by
swapping the binding, not by adding a conditional. This keeps the
release build one configuration with no dead code.

### Eight prefs files vs DataStore — prefs win
Jetpack DataStore is the modern successor. The project stayed on
SharedPreferences because the concerns are small, synchronous reads
dominate (game-loop-adjacent code cannot block), and the data shapes
are trivial. Migrating later is possible; the chosen abstraction
(one class per file) makes that migration a per-wrapper rewrite, not
a large refactor.

### Keep most assets out of the APK vs ship placeholders — ship placeholders
`res/raw/sfx_*.ogg` ships placeholder sine-wave tones (per STATE.md)
rather than blocking release. `SoundManager` plumbing is complete so
swapping the files is a no-code change. No launcher icon ships at
all, which does block Play Store but not Gradle.

### Plan-31 gap vs shipping v1.0 with stubs
The choice to mark v1.0 as "release-prep complete" with Billing/Ads
as stubs is a deliberate staging decision — the alternative (blocking
release on real SDK integration) would have delayed the entire
playable game. The stubs are clearly labelled and the store surface
deliberately gates cosmetics behind a "Coming Soon" button
(`presentation/store/StoreScreen.kt`), so users cannot accidentally
interact with an unfinished path.

---

## 5. What the philosophy explicitly *does not* commit to

Calling these out so future contributors don't read commitments into
silence:

- **There is no documented coding style guide beyond
  `.kiro/steering/*.md`.** Kotlin conventions are followed but not
  enforced by ktlint / Detekt / Spotless.
- **There is no pinned JVM toolchain.** `sourceCompatibility = VERSION_17`
  expects JDK 17 but does not enforce a specific distribution.
- **There is no documented branching / commit-message policy.** Git
  history is linear `main`, mostly Conventional Commits, but no rule
  is declared.
- **There is no cross-device testing matrix.** Plan 31 lists API 34
  and API 36 as the manual gate — no device-lab coverage, no
  emulator grid.
- **There is no "accessibility required for each new feature" gate.**
  Plan 24 is deferred; baseline content descriptions exist but a
  full accessibility pass is post-v1.0 work.
- **There is no documented answer to cloud-save / multi-device.** It
  does not exist in v1.0, and the code actively precludes it
  (`allowBackup=false`, no account system). Whether that is
  permanent or a v2.0 gap is not captured in code.

---

## 6. How to extend without breaking the philosophy

A one-page checklist distilled from the principles above, useful as a
PR heuristic:

- Are you adding a new way to earn `Steps`? It must pass through
  `DailyStepManager` or `AwardBattleSteps`. Don't call
  `playerRepository.addSteps()` directly.
- Are you persisting new state? It goes in Room unless it must
  survive a DB wipe (a prefs file) or is in-memory battle state
  (a `@Volatile` on `GameEngine`).
- Are you adding a new repository? Domain interface,
  `@Inject constructor @Singleton` impl, `@Binds` in
  `RepositoryModule`. No Hilt on the use cases that consume it.
- Are you bumping `@Database(version)`? Write a `Migration`, add it
  to `AppMigrations.ALL`, commit the generated schema JSON, add a
  `MigrationTestHelper` test (convention, even though none exist
  yet).
- Are you adding a new use case with stochastic behaviour? Constructor
  parameter `random: Random = Random`.
- Are you adding a new use case that depends on time? Default
  parameter `today: String = LocalDate.now().toString()` or
  `now: Long = System.currentTimeMillis()`.
- Are you adding a new action handler? Set `userMessage` on failure,
  guard with `isProcessing`, reveal the `SnackbarHost` in the
  screen.
- Are you adding a new notification? New `*NotificationManager`, new
  channel, new PendingIntent route, and extend
  `MainActivity.pendingNavigation` branches. (Today's
  deep-link coverage is partial — see
  `missing_concepts_list.md` §2.)
- Are you adding a new external SDK (HTTP, analytics, etc.)? This
  breaks the offline-first and privacy-by-default principles and
  warrants an ADR, not a PR comment.
- Are you reading or writing files outside Room / prefs? Justify in
  the PR — the project has deliberately confined all durable state
  to those two layers.
