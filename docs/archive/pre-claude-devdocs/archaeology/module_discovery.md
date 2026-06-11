# Module Discovery — Phase 8 (Boundary analysis from code)

*Archaeology Phase 8. Written from code as of commit `a9d0386` (2026-05-04).
Companion to `architecture_analysis.md`. Where Phase 2/3/4 already
addressed a point, this document cross-references and adds only what is
new.*

Scope: the app is a **single Gradle module** (`:app`). "Module" in this
document therefore means **package-level logical module** — the units a
multi-module refactor would naturally carve out. The package layout under
`app/src/main/java/com/whitefang/stepsofbabylon/` is the map.

---

## 1. Natural module boundaries

Reading the code structurally (not relying on the stated Clean Architecture
split), the following cohesive clusters emerge. Each cluster has a
consistent internal naming scheme, shares dependencies, and would migrate
together if the app were ever split into Gradle modules.

### M1. `core-domain` — pure-Kotlin balance & contracts

Packages: `domain/model/`, `domain/repository/`.

- 36 model files in `domain/model/` — enums, data classes, value objects,
  sealed reward hierarchies.
- 10 repository interfaces in `domain/repository/`.

Internal dependencies: zero — each file imports only other domain/model
files and Kotlin stdlib. No `android.*` / `androidx.*`. External callers:
every other module.

Cohesion: **high** — one concept per file, enums-as-balance-sheet give
each file a clear role. Coupling: **high fan-in**, low fan-out. Ideal leaf
module for a multi-module layout.

### M2. `core-usecases` — pure-Kotlin orchestrators & computations

Package: `domain/usecase/` (36 files).

- Stateless computations with injectable `Random` — `CalculateUpgradeCost`,
  `CalculateDamage`, `CalculateDefense`, `ResolveStats`, `ApplyCardEffects`,
  `GenerateSupplyDrop`, etc.
- Orchestrators taking `domain/repository/*Repository` —
  `PurchaseUpgrade`, `StartResearch`, `OpenCardPack`,
  `UnlockUltimateWeapon`, etc.
- **6 impure use cases that also take `data/local/*Dao`** — `AwardBattleSteps`,
  `CheckMilestones`, `ClaimMilestone`, `GenerateDailyMissions`,
  `TrackDailyLogin`, `TrackWeeklyChallenge`. These prevent `core-usecases`
  from depending only on `core-domain`. See §6.1 for why.

External callers: ViewModels; `DailyStepManager` (data layer, see §5).

### M3. `persistence` — Room + SQLCipher + migrations + key management

Packages: `data/local/`.

- `AppDatabase`, 12 `*Entity`, 12 `*Dao`, `Converters`, `Migrations`,
  `DatabaseKeyManager`.

All DAOs return `Flow<*>` for reads and `suspend` for writes. `DatabaseKeyManager`
is a static object hanging off `SharedPreferences` + Android Keystore;
slightly orthogonal to Room but lives in the same package because it's
intimately tied to DB bootstrap.

Cohesion: **high** — one concern, one package. Coupling: depends on
`androidx.room`, `net.zetetic.database.sqlcipher` (transitive), Android
Keystore. Callers: only `data/repository/` and `di/DatabaseModule`.

### M4. `repositories` — Room → domain mappers

Package: `data/repository/` (8 files).

Each `*RepositoryImpl` is roughly 40–80 lines: DAO in, domain out,
`fun Entity.toDomain()` extension at the bottom. Pure plumbing. No business
logic.

Unusual inclusion: `WalkingEncounterRepositoryImpl` calls `System.currentTimeMillis()`
directly for `createdAt` / `claimedAt` (`WalkingEncounterRepositoryImpl.kt:29, 33`).

### M5. `sensor` — step ingestion pipeline

Package: `data/sensor/`.

- `StepSensorDataSource` — `TYPE_STEP_COUNTER` wrapper emitting
  `Flow<Long>`.
- `StepRateLimiter` — rolling-window algorithm (pure Kotlin apart from
  `@Inject`/`@Singleton`).
- `StepVelocityAnalyzer` — shaker/spoofer detector (pure Kotlin apart
  from `@Inject`).
- `StepIngestionPreferences` — SharedPreferences wrapper for service
  heartbeat + day-start counter.
- `DailyStepManager` — the orchestrator (`@Singleton`, 12 constructor
  dependencies).

### M6. `healthconnect` — cross-validation & activity-minute conversion

Package: `data/healthconnect/`.

- `HealthConnectClientWrapper`, `HealthConnectStepReader`,
  `ExerciseSessionReader`, `StepGapFiller` — read side.
- `StepCrossValidator` — cross-validation + escrow state machine.
- `ActivityMinuteConverter`, `ActivityMinuteValidator` — exercise-session
  → step-equivalent conversion.

Cohesion: **high**. Couples to Health Connect SDK + `data/anticheat/` +
`domain/repository/` for persistence. All classes are `@Singleton`.

### M7. `anticheat` — counter SharedPreferences

Package: `data/anticheat/`.

Single file: `AntiCheatPreferences`. Stores rate-rejected, velocity-penalized,
activity-rejected counters plus cross-validation offense count + last-offense
date. Shared between `DailyStepManager`, `StepCrossValidator`, and
`ActivityMinuteValidator`.

### M8. `prefs` — scattered SharedPreferences wrappers

No single package — spread across:

- `data/BiomePreferences.kt` (biome seen flags)
- `data/NotificationPreferences.kt` (4 notification toggles)
- `data/SoundPreferences.kt` (muted + volume)
- `data/MilestoneNotificationPreferences.kt` (milestone notification dedup)
- `data/anticheat/AntiCheatPreferences.kt` (§M7 — lives inside anticheat)
- `data/sensor/StepIngestionPreferences.kt` (§M5 — lives inside sensor)
- `data/local/DatabaseKeyManager.kt` (reads `db_key_prefs` inline — §M3)
- `service/SmartReminderManager.kt` (reads `smart_reminders` inline)
- `service/StepWidgetProvider.kt` (reads `widget_data` inline via static
  companion)
- `presentation/battle/GameSurfaceView.kt:26` (reads `sound_prefs` inline
  — §6.7 of `architecture_analysis.md`)

10 distinct SharedPreferences files, 4 different access patterns (injected
wrapper class, inline `getSharedPreferences`, static companion function,
ad-hoc block inside a view). **No shared preferences abstraction.**

### M9. `billing-ads` — stub SDK adapters

Packages: `data/billing/`, `data/ads/`.

- `StubBillingManager` — 30-line fake (`delay(500)`, credits Gems via
  `PlayerRepository`).
- `StubRewardAdManager` — 11-line fake (`delay(1000)`, always rewards).

Binds through `di/BillingModule`, `di/AdModule`. The real-SDK swap point
per ADR-0003 and Plan 31.

### M10. `service` — Android framework processes

Package: `service/` (9 files):

- `StepCounterService` — foreground sensor consumer.
- `StepSyncWorker` — WorkManager sensor catch-up + HC sync.
- `StepSyncScheduler` — one-time enqueue helper.
- `BootReceiver` — auto-restart service.
- `StepWidgetProvider` + `WidgetUpdateHelper` — widget pair.
- `StepNotificationManager` — foreground service notification.
- `SupplyDropNotificationManager`, `MilestoneNotificationManager` — event
  notifications.
- `SmartReminderManager` — upgrade-proximity reminders.

### M11. `navigation` — screen routes + bottom bar

Package: `presentation/navigation/`. Two files: `Screen` (sealed class,
12 routes) and `BottomNavBar`. Tight coupling to `MainActivity` (which
owns the NavHost and dispatches deep-links).

### M12. `screens` — 12 Compose features, one package each

Packages under `presentation/`: `home/`, `workshop/`, `battle/`, `labs/`,
`cards/`, `weapons/`, `supplies/`, `economy/`, `missions/`, `stats/`,
`settings/`, `store/`.

Each is 3–4 files: `*Screen.kt` (Compose), `*ViewModel.kt`
(`@HiltViewModel`), `*UiState.kt` (data class), optional secondary
composables (e.g. `workshop/UpgradeCard.kt`, `home/TierSelector.kt`).

Cohesion within each: **very high**. Cross-screen coupling: **low** —
screens talk to each other via `navController.navigate(Screen.X.route)`
only.

### M13. `battle-engine` — SurfaceView game loop subsystem

Package: `presentation/battle/` and its sub-packages:

- `engine/` — `GameEngine`, `Entity`, `WaveSpawner`, `EnemyScaler`,
  `CollisionSystem`.
- `entities/` — `ZigguratEntity`, `EnemyEntity`, `ProjectileEntity`,
  `EnemyProjectileEntity`, `OrbEntity`.
- `effects/` — `ParticlePool`, `EffectEngine`, `ScreenShake`, `DeathEffect`,
  `UWVisualEffect`, `OverdriveAuraEffect`, `ProjectileTrailEffect`,
  `FloatingText`, `WaveAnnouncement`, `ReducedMotionCheck`.
- `biome/` — `BiomeTheme`, `BackgroundRenderer`.
- `ui/` — post-round overlay, pause overlay, in-round upgrade menu,
  overdrive menu, UW bar, health bar renderer, biome transition overlay.
- Top level: `GameSurfaceView`, `GameLoopThread`, `BattleViewModel`,
  `BattleScreen`, `BattleUiState`.

This is the densest package in the app: 30+ files in one feature. It is
the only feature that uses `android.graphics.Canvas` drawing,
`@Volatile` cross-thread state, and has its own thread. It could cleanly
become a `:app:battle` submodule.

### M14. `audio` — SoundPool wrapper

Package: `presentation/audio/`. One file: `SoundManager` + sealed
`SoundEffect`. Takes `Context` directly (no Hilt). Owned by
`GameSurfaceView` (which creates and releases it in `surfaceDestroyed`).

### M15. `theme` — Compose Material3 theme

Package: `presentation/ui/theme/`. Two files: `Color.kt`, `Theme.kt`.
Trivial but singled out because it's the only UI package that's not
feature-aligned.

### M16. `di` — Hilt wiring

Package: `di/` (6 modules). Depends on *every* implementation package. In
a multi-module world, DI bindings would have to move to the application
module or a dedicated `:app:di` module.

---

## 2. Coupling & cohesion analysis

### 2.1 Summary table

Low coupling + high cohesion = good. High coupling + low cohesion = bad.

| Module | Cohesion | Fan-in | Fan-out | Verdict |
|---|---|---|---|---|
| M1 `core-domain` | **High** | Very high | Zero | Excellent leaf. |
| M2 `core-usecases` | High | High (all VMs, M5, M10) | Low (M1 + 6 DAO leaks) | Good, with §6.1 violations. |
| M3 `persistence` | **High** | 2 (M4, M16) | `androidx.room`, SQLCipher | Self-contained. |
| M4 `repositories` | **High** | Many VMs + M5 + use cases | M3 + M1 | Simple plumbing. Clean. |
| M5 `sensor` | **Low-medium** | M10 | M3 DAOs + M2 use cases + M7 + M10 notification + domain repos + widget | `DailyStepManager` is the cross-cutting hub. |
| M6 `healthconnect` | **High** | M10 (StepSyncWorker) | M4 domain repos + M7 anticheat | Coherent. |
| M7 `anticheat` | High | M5, M6 | SharedPreferences only | OK. |
| M8 `prefs` (virtual) | **None** | Many | — | **Missing boundary.** 10 files, 4 patterns. |
| M9 `billing-ads` | High | M16 | M1 + M4 PlayerRepository | Stub. Deliberately thin. |
| M10 `service` | **Low-medium** | Android OS + M11 nav extras | Almost every other module | Framework adapter surface. |
| M11 `navigation` | High | M12 screens | M1 Screen, `MainActivity` | Narrow. |
| M12 `screens` | **High per screen** | M11 nav | Many (see §2.4) | Each screen self-contained; cross-screen low. |
| M13 `battle-engine` | **Medium** | M12 battle screen + M14 audio | M1 + M2 + M14 | Dense internally; leaks concerns (§3.3). |
| M14 `audio` | High | M13 | `android.media.SoundPool` + `data/SoundPreferences` (via manual prefs read in M13) | Small. |
| M15 `theme` | High | M12 (all screens) | Compose | Trivial. |
| M16 `di` | High | (binds everything) | Every impl module | Expected for DI. |

### 2.2 "Fat" modules

- **M5 `sensor`** — `DailyStepManager` alone has 12 constructor
  dependencies and 11 responsibilities (rate limit, velocity, anti-cheat
  counters, Room persist, per-minute tracking, mission progress, supply
  drop generation, daily login, weekly challenge, widget update, in-memory
  cache). Phase 4 item 4 proposes extracting a `FollowOnPipeline`. The
  cohesion here is *thematic* ("step credit flow"), not *structural*.
- **M13 `battle-engine`** — `GameEngine` is ~570 lines and knows about
  the ziggurat, overdrive state, UW state, cash economy, collision,
  wave progression, effect dispatch, sound, reduced-motion, step reward
  callback. Cohesive around "one frame of battle" but internally structured
  by domain concern, not by architecture layer.
- **M10 `service`** — contains step counting, notifications, WorkManager,
  widget, boot receiver, smart reminders. Consolidated by "Android
  framework entry points" but functionally 4 unrelated features.
- **M12 `screens/home`** — `HomeViewModel` has 11 constructor dependencies
  and its `init { }` runs 5 bootstrapping tasks (`ensureProfileExists`,
  `ensureUpgradesExist`, `ensureResearchExists`, `CheckResearchCompletion`,
  `TrackDailyLogin`, `GenerateDailyMissions`, `CheckMilestones`). It is
  the *de facto* "app launch boot-up" routine masquerading as a VM.

### 2.3 "Thin" modules (overhead)

- **`di/HealthConnectModule`** — empty body, documented as "organisational
  placeholder" (§6.9 of `architecture_analysis.md`). Pure overhead.
- **`presentation/ui/theme/`** — 2 files, 2 constants. Could live inline.
  Kept separate because Compose convention.

### 2.4 Cross-screen coupling in `screens/`

Screens rarely reach each other directly. But some patterns leak:

- `HomeScreen.onStoreClick → Screen.Store` and `CurrencyDashboardScreen.onStoreClick
→ Screen.Store` (`MainActivity.kt:207, 217`) — two entry points to the
  Store. Deep-link `navigate_to=workshop` adds a third (notifications).
- `MissionsScreen` reads both `DailyMissionDao` and `MilestoneDao` and
  is the single claim point for both; `HomeViewModel` separately reads
  `MilestoneDao` and fires milestone notifications — two consumers of
  `milestone` entity state, drifting independently.
- `BattleViewModel.endRound` writes to `DailyMissionDao` (REACH_WAVE_30,
  KILL_500_ENEMIES); `LabsViewModel.updateResearchMission` writes
  COMPLETE_RESEARCH; `WorkshopViewModel.purchase` writes SPEND_5000_WORKSHOP;
  `DailyStepManager.updateWalkingMissions` writes WALK_*; `MissionsViewModel.updateWalkingMissionProgress`
  duplicates the walking case. Five writers, no coordinator. §6.8 of
  `architecture_analysis.md`.

### 2.5 Fan-out of `PlayerRepository`

`PlayerRepository` is injected into **every ViewModel + every mutating
use case + both stubs + `DailyStepManager` + `StubBillingManager`**. Its
interface is 23 methods. It is the single most-depended-upon type in the
codebase. Its methods partition cleanly by currency, making decomposition
possible (e.g. `WalletRepository` for the 4 currencies, `ProfileRepository`
for lifetime stats + tier + flags), but the split has not been made.

---

## 3. Dependency relationships

### 3.1 Graph (package level, actual imports)

```
                    di/  ──────────────────────────┐
                    (binds everything)              │
                                                    ▼
  ┌────────────────────── domain/model/  ◄── domain/repository/
  │                       (pure, zero deps)
  │
  │  domain/usecase/  ────────────┐
  │  ├── depends on model +        │
  │  │   repository (OK)           │
  │  └── ⚠ 6 files also import     │
  │       data/local/*Dao          │
  ▼
  data/
  ├── local/           (Room + SQLCipher + Keystore)
  ├── repository/ ────► local/ + domain/model + domain/repository
  ├── sensor/          ────► local/ + domain/* + data/anticheat + service/ (!)
  ├── healthconnect/   ────► local/ + data/anticheat + domain/repository
  ├── anticheat/       ────► SharedPreferences
  ├── billing/         ────► domain/model + domain/repository
  └── ads/             ────► domain/model + domain/repository

  service/
  ├── StepCounterService    ────► data/sensor + domain/repository + NotificationManager
  ├── StepSyncWorker        ────► data/sensor + data/healthconnect + domain/repository + SmartReminderManager
  ├── BootReceiver          ────► self → service (Intent)
  ├── Step/Supply/MilestoneNotificationManager
  │                         ────► data/NotificationPreferences + presentation/MainActivity (!)
  ├── StepWidgetProvider    ────► presentation/MainActivity (!) + R.layout (gen)
  └── SmartReminderManager  ────► domain/usecase + domain/repository + presentation/MainActivity (!)

  presentation/
  ├── MainActivity          ────► every screen + Screen + StepCounterService
  ├── navigation/           ────► ui.graphics (Compose icon)
  ├── <screen>/             ────► domain/usecase + domain/repository +
  │                               ⚠ 6 screens import data/local/*Dao
  └── battle/               ────► domain/usecase + domain/repository +
                                  audio + effects + entities + ui
                                  + data/BiomePreferences + ⚠ data/local/DailyMissionDao + data/local/DailyStepDao
```

### 3.2 Cycles

**Zero package-import cycles** at the file level. The codebase is a DAG,
which is rare and worth calling out. However:

- **`service` ↔ `presentation` coupling**: several notification managers
  import `presentation.MainActivity` to build `PendingIntent`s. The other
  direction (presentation → service) happens at `MainActivity.onCreate`
  (`context.startForegroundService(Intent(context, StepCounterService::class.java))`).
  The two are co-dependent but Kotlin compiles because both import the
  class at "reference in new Intent" granularity. If `MainActivity` or
  `StepCounterService` moved to different modules, the cycle would become
  real.

- **`data/sensor/DailyStepManager` → `service/SupplyDropNotificationManager` + `service/WidgetUpdateHelper`**:
  data layer takes presentation-framework types as dependencies. In a
  multi-module split, `data/` would depend on `service/`, which is an
  atypical direction.

### 3.3 "Forbidden" direction imports

Clean-architecture-violating imports (domain or presentation → data
implementation types):

| From | To | File:line |
|---|---|---|
| `domain/usecase/AwardBattleSteps` | `data/local/DailyStepDao` | `AwardBattleSteps.kt:3` |
| `domain/usecase/CheckMilestones` | `data/local/MilestoneDao` | `CheckMilestones.kt:3` |
| `domain/usecase/ClaimMilestone` | `data/local/MilestoneDao`, `MilestoneEntity` | `ClaimMilestone.kt:3-4` |
| `domain/usecase/GenerateDailyMissions` | `data/local/DailyMissionDao`, `DailyMissionEntity` | `GenerateDailyMissions.kt:3-4` |
| `domain/usecase/TrackDailyLogin` | `data/local/DailyLoginDao`, `DailyLoginEntity` | `TrackDailyLogin.kt:3-4` |
| `domain/usecase/TrackWeeklyChallenge` | `data/local/DailyStepDao`, `WeeklyChallengeDao`, `WeeklyChallengeEntity` | `TrackWeeklyChallenge.kt:3-5` |
| `presentation/battle/BattleViewModel` | `data/local/DailyMissionDao`, `DailyStepDao` | `BattleViewModel.kt:4-5` |
| `presentation/home/HomeViewModel` | `data/local/DailyLoginDao`, `DailyMissionDao`, `MilestoneDao` | `HomeViewModel.kt:5-8` |
| `presentation/missions/MissionsViewModel` | `data/local/DailyMissionDao`, `MilestoneDao`, `DailyStepDao` | `MissionsViewModel.kt:5-7` |
| `presentation/labs/LabsViewModel` | `data/local/DailyMissionDao` | `LabsViewModel.kt:5` |
| `presentation/workshop/WorkshopViewModel` | `data/local/DailyMissionDao` | `WorkshopViewModel.kt:5` |
| `presentation/economy/CurrencyDashboardViewModel` | `data/local/DailyLoginDao`, `DailyStepDao`, `WeeklyChallengeDao`, `WeeklyChallengeEntity` | `CurrencyDashboardViewModel.kt:5-8` |

**12 files** (6 use cases + 6 VMs) would fail to compile under a strict
`:domain` / `:data` / `:presentation` module split. Most of the breaks
are around DAOs that are not covered by a `Repository` interface (e.g.
`DailyMissionDao.countClaimable(date): Flow<Int>`, `updateProgress(id, progress, completed)`,
`DailyStepDao.sumCreditedSteps(startDate, endDate): Long`). Expanding
the domain repository surface or introducing a handful of new
`MissionRepository`, `WeeklyChallengeRepository`, `LoginRepository`
interfaces would plug every leak.

### 3.4 `MainActivity` as a hub

`MainActivity` imports:

- All 12 screens (`HomeScreen`, `WorkshopScreen`, `BattleScreen`, ...).
- `BottomNavBar`, `Screen`.
- `HealthConnectClientWrapper`, `PlayerRepository`.
- `StepCounterService` (for starting the foreground service).
- `StepsOfBabylonTheme`.

As is conventional for a single-Activity app, it is the central point of
coupling between navigation, permissions, background-service lifecycle,
and Compose theme. It is also the deep-link dispatcher for
`navigate_to=…` intent extras.

---

## 4. Shared utilities and libraries

### 4.1 `domain/` shared utilities

Every use case and every ViewModel touches these:

- `ZigguratBaseStats` — 7 constants, read by `ResolveStats`, `ZigguratEntity`, `GameEngine`.
- `TierConfig.forTier(number): Tier` — lookup helper backing all tier-aware
  logic (biome resolution, battle conditions, cash multiplier).
- `Biome.forTier(tier)` — biome classification.
- `BattleConditionEffects.fromTier(tier)` — derived modifier bundle.

These form an informal "balance lookup" API. Consumers call them from
everywhere; the "single source" is always the enum/object.

### 4.2 Ad-hoc helper objects

- `EnemyScaler` (`presentation/battle/engine/`) — 4 methods, 4 constants.
  Pure Kotlin despite being in presentation because the SurfaceView
  renderer needs it at 60 UPS and it was easier to co-locate.
- `CollisionSystem` (`presentation/battle/engine/`) — single method,
  side-effectful via callbacks. Object because stateless.

### 4.3 Cross-module helpers

- `WidgetUpdateHelper` (`service/`) — injected, called from `DailyStepManager`
  (data/sensor). **Data-layer calling service-layer** helper.
- `SupplyDropNotificationManager`, `MilestoneNotificationManager` —
  injected, called from both presentation (VMs) and data (`DailyStepManager`).
  Sit in `service/` because they own notification channels.

### 4.4 External library dependencies

From `gradle/libs.versions.toml`:

- UI: Jetpack Compose (BOM), Navigation Compose, Lifecycle, Activity
  Compose, Material Icons.
- DI: Hilt 2.59.2 + Hilt-Work + Hilt-Navigation-Compose.
- Persistence: Room 2.8.4 + SQLCipher 4.13.0 + SQLite KTX.
- Background: WorkManager 2.11.0.
- Health: Health Connect 1.2.0-alpha02.
- Logging: (none — raw `android.util.Log` at ~40 sites, no Timber/SLF4J).
- Testing: JUnit5, kotlinx-coroutines-test, Mockito-Kotlin, Robolectric,
  AndroidX Test Core.

The dependency footprint is tight. No JSON library (Moshi, Kotlinx.serialization)
— `Converters` uses `org.json.JSONObject` which ships with Android. No
network client — there is no network. No analytics, crash reporting, or
feature flags.

---

## 5. Cross-cutting concerns

These are features that reach into many modules without a clear single
owner:

### 5.1 Time

Covered in Phase 4 item 1. **53 `System.currentTimeMillis` / `LocalDate.now`
/ `Instant.now` calls across 33 files.** 5 of them (sparse) use the
default-parameter pattern for injection; the remaining 48 call the
singleton clock directly. No `TimeProvider` abstraction.

### 5.2 Randomness

**83 `Random.*` references across 15 files.** 7 use cases accept an
injectable `Random` with `= Random` default — `GenerateSupplyDrop`,
`OpenCardPack`, `GenerateDailyMissions` (seeded), `CalculateDamage`. All
battle-layer uses (`GameEngine`, `WaveSpawner`, all 5 particle effect
files, `ScreenShake`) call the singleton directly. Battle-time
reproducibility is therefore not achievable in tests without monkey-patching.

### 5.3 Persistence (SharedPreferences)

Already inventoried in M8 — **10 distinct preferences files, 4 access
patterns**. No shared abstraction; no migration path if the app ever
switches to DataStore.

### 5.4 Notifications

Four notification channels (`step_counter`, `supply_drops`, `reminders`,
`milestones`), three `*NotificationManager` classes in `service/`, plus
inline channel creation in `SmartReminderManager`. All share the
`notificationPreferences: NotificationPreferences` gate; all build
`PendingIntent(MainActivity)` with `navigate_to` extras.

### 5.5 Anti-cheat

Anti-cheat logic is spread across:

- `data/sensor/StepRateLimiter` — rate cap.
- `data/sensor/StepVelocityAnalyzer` — pattern detection.
- `data/sensor/DailyStepManager.DAILY_CEILING` — 50 k/day ceiling.
- `data/healthconnect/StepCrossValidator` — 4-level graduated response.
- `data/healthconnect/ActivityMinuteValidator` — micro-session / type-
  diversity / duration filters.
- `data/anticheat/AntiCheatPreferences` — counters.
- `domain/usecase/AwardBattleSteps` — 2 k/day battle-Steps cap.

Cohesive thematically but structurally scattered across three packages.
No single `AntiCheatService` front-door. The player-visible impact is also
nowhere: neither the ceiling, nor the escrow, nor the rate limit is
surfaced on `StatsScreen`. Phase 4 item 5 proposes exposing them.

### 5.6 Currency non-negative clamping

`PlayerProfileDao` uses SQL `MAX(0, currentStepBalance + :delta)` on every
adjust. Same for `gems`, `powerStones`, `cardDust`. The Kotlin-level
contract (`PlayerRepository.spendSteps(amount: Long)` returns `Unit`)
doesn't communicate the clamp. Test coverage in
`test/presentation/ux/CurrencyGuardTest.kt`. See §7.9 of
`architecture_analysis.md`.

### 5.7 Logging

Raw `android.util.Log.{d,w}` calls at ~40 sites, no tag convention
(`"AntiCheat"`, `"StepSyncWorker"`, `"DatabaseKeyManager"`). No logger
injection, no log level config, no crash reporting. Phase 2 §6 and Phase
6 note the deliberate observability minimalism.

### 5.8 Coroutine scoping

Every ViewModel uses `viewModelScope`. `StepCounterService` creates its
own `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and cancels
in `onDestroy`. `MainActivity` creates an activity-scoped
`SupervisorJob + Dispatchers.Main.immediate` for the `onResume`
`updateLastActiveAt` write. `StepSyncWorker` is a `CoroutineWorker`.
Pattern is consistent but hand-rolled per Android component (there is no
`ApplicationScope` injected Singleton).

### 5.9 Error handling

`try { … } catch (_: Exception) {}` ("pokemon catch") appears in:

- `DailyStepManager.runFollowOnPipeline` — 4 blocks, silently swallowing
  widget update, supply drop, economy rewards, and mission updates if
  anything throws.
- `BattleViewModel.endRound` — 2 blocks around battle-stats increment
  and mission update.
- `LabsViewModel.updateResearchMission` — 1 block.
- `WorkshopViewModel.purchase` — 1 block.
- `HealthConnect*` — best-effort wrappers with proper logging.

Best-effort is a legitimate pattern for "if this fails, the user still
walks away with the primary reward". But the silent swallow without even
a `Log.w(tag, ...)` is opaque. A small `tryBestEffort(tag, block)` helper
would at least leave traces.

### 5.10 Health Connect availability

`HealthConnectClientWrapper.isAvailable()` is checked at every entry
point (MainActivity permission request, StepSyncWorker try/catch). The
absence of HC (pre-Android-14 / no Health Connect app installed)
degrades gracefully — sensor-only ingestion still works. Phase 2 §4 and
Phase 6 §14 cover this. No centralised "HC available" flag; each caller
checks independently.

---

## 6. Where boundaries are being violated

(These are §7 of `architecture_analysis.md` restated as boundary issues.)

### 6.1 Domain → Data (Dao imports)

**6 files in `domain/usecase/`** import `data/local/*Dao`. The DAO interfaces
are pure Kotlin at the call site but carry `androidx.room` annotations in
their file; domain compiles clean. The spirit-of-Clean-Architecture issue
is real: domain now re-compiles on any DAO signature change.

Fix shape: add narrow `MissionProgressRepository`, `LoginRepository`,
`WeeklyChallengeRepository`, `MilestoneRepository` interfaces to
`domain/repository/`; expose the specific queries the use cases need.
Relocate `GenerateDailyMissions` and `TrackDailyLogin` to take those
interfaces instead of DAOs.

### 6.2 Presentation → Data (Dao imports)

**6 ViewModels** import `data/local/*Dao`. Same fix shape as §6.1. Listed
in §3.3 above.

### 6.3 Data → Service / Presentation (outbound)

- `DailyStepManager` (data/sensor) → `SupplyDropNotificationManager`,
  `WidgetUpdateHelper` (service).
- `DailyStepManager` → `GenerateSupplyDrop`, `TrackDailyLogin`,
  `TrackWeeklyChallenge` (domain use cases — OK) but constructs them
  inline rather than injecting them via Hilt.
- `HomeViewModel` constructs use cases inline too, but that's presentation
  → domain (allowed).

Fix shape: make `DailyStepManager` take `@Inject`ed use-case instances
and a `FollowOnPipeline` collaborator (Phase 4 item 4).

### 6.4 Service → Presentation (notification deep-links)

Every notification in `service/*` builds `PendingIntent.getActivity(context, _, Intent(context, MainActivity::class.java))`.
This is **canonical Android** and not a code smell on its own. But when
multiple modules want a deep-link target, they all hard-code the
`MainActivity` class reference. A `NavIntentFactory(context): Intent`
helper (accepting a `Screen`-typed route) would centralise this — right
now `"workshop"`, `"battle"`, `"supplies"`, `"missions"` are string
literals matched against `Screen` routes in `MainActivity.kt:107-114`.

### 6.5 GameSurfaceView → SharedPreferences (bypassing injection)

`GameSurfaceView.kt:26` re-opens the `sound_prefs` file by raw name
instead of taking the injected `SoundPreferences`. See §6.7 of
`architecture_analysis.md`. A `GameSurfaceView(context, soundPrefs)`
constructor + Compose `AndroidView(factory = {...})` passing the prefs
would fix it cleanly.

### 6.6 Widget ↔ everything via SharedPreferences (third-party side channel)

`StepWidgetProvider.saveData(context, dailySteps, balance)` is a static
method on a `BroadcastReceiver` that writes SharedPreferences, called from
`WidgetUpdateHelper.update()` (`service/WidgetUpdateHelper.kt:18`). The
widget then reads those SharedPreferences in `onUpdate`. The widget has
no other state source.

This is a **cross-process coordination** pattern (the widget runs as a
separate component). Side-channel via prefs is fine; the issue is that
`widget_data` is opened inline and there is no typed wrapper. `WidgetRepository`
(holding the last two values as a `StateFlow`) would be more coherent
but would require the widget provider to cross the Hilt boundary, which
`AppWidgetProvider` doesn't support natively.

### 6.7 Battle engine → domain use cases (tight coupling)

`GameEngine` directly constructs `CalculateDamage()` and `CalculateDefense()`
(`GameEngine.kt:38-39`). Both use cases have `Random` parameters with
defaults. `GameEngine` never passes a seed; tests that need deterministic
battle would require reworking the engine's construction. This is fine
for now but locks in the "Random singleton" choice for the game loop.

### 6.8 Notification managers → MainActivity class reference

Every notification manager in `service/` imports `presentation.MainActivity`
to build `PendingIntent`s. The service layer depends on a specific
Activity class, not an interface. Low severity (every Android app does
this), but flagged for multi-module refactoring — `presentation` would
need to publish an interface that `service` consumes.

---

## 7. Where missing boundaries would help

### 7.1 A `MissionProgressTracker` service

5 callers in 5 modules update daily mission progress (§6.8 of
`architecture_analysis.md`). A `MissionProgressTracker` with methods like
`recordWalkingSteps(todaySteps: Long)`, `recordRoundCompleted(wave: Int,
kills: Int)`, `recordWorkshopSpend(steps: Long)`, `recordResearchComplete()`
would collapse the duplication. It would live in `domain/usecase/` (or
a new `domain/service/`), take `DailyMissionRepository` (new interface),
and eliminate all 5 hand-coded `missions.find { it.missionType == ... }`
lookups.

### 7.2 A `PreferencesStore` abstraction

10 SharedPreferences files, 4 access patterns. A single
`PreferencesStore(Context)` that exposes typed accessors
(`boiome.seen(Biome)`, `milestone.notified(Milestone)`, `anticheat.counters`,
etc.) would give one migration point (e.g. to DataStore), one testable
fake, and one place to lock out of dev builds. Today, testing any code
that reads prefs requires Robolectric (and there's one such test already:
`test/service/StepWidgetProviderTest.kt`).

### 7.3 A `TimeProvider` (Phase 4 item 1)

Phase 4 already makes this argument in detail. Summary: `Clock.System`
instance + default-parameter migration for the ~48 remaining direct time
calls.

### 7.4 A `RandomSource` (parallel to TimeProvider)

Same argument for `Random`. Battle-time determinism is currently
untestable because `WaveSpawner.pickType`, `spawnPosition`,
`GameEngine.applyDamageToZiggurat`, and all particle effects call
`Random.*` directly. An injected `RandomSource` (seeded in tests,
`Random.Default` in production) would make battle replays and
death-defy-probability tests possible.

### 7.5 A `Reward` sealed hierarchy

`Currency` vs `SupplyDropReward` vs `MilestoneReward` (§3.1 of
`architecture_analysis.md`) — three overlapping reward vocabularies. A
single

```kotlin
sealed class Reward {
  data class Steps(val amount: Long) : Reward()
  data class Gems(val amount: Long) : Reward()
  data class PowerStones(val amount: Long) : Reward()
  data class CardDust(val amount: Long) : Reward()
  data class Cosmetic(val id: String) : Reward()
}
```

plus a `PlayerRepository.credit(reward: Reward)` dispatcher would
concentrate currency arithmetic in one place. Today, adding a new reward
kind touches 3+ files.

### 7.6 A `FollowOnPipeline` extractor for `DailyStepManager`

Phase 4 item 4 argues this in detail. `DailyStepManager` has 11
responsibilities; extracting the 5 "after we credit steps, do X" concerns
into a `FollowOnPipeline` collaborator would drop the manager's
constructor arity from 12 to ~5 and make each follow-on independently
testable.

### 7.7 A `LoadoutRepository<T>` with cap enforcement at the data layer

Cards and UWs both enforce the "max 3 equipped" rule in the VM, not in
the data layer (§7.4 of `architecture_analysis.md`). A DAO-level constraint
(trigger, unique index on a `loadout_slot` column) would make the rule
structural. Today, any bad external actor (or future dev writing a DB
repair script) could violate it unilaterally.

### 7.8 A `BattleCommand` abstraction for VM→engine communication

The current protocol is VM mutating `@Volatile` fields on `GameEngine`
(e.g. `engine.activeOverdrive`, `engine.spendCash(...)`, `engine.onStepReward = ...`).
This works but is discoverable only by reading the engine. A
`GameEngine.enqueueCommand(cmd: BattleCommand)` method with a
`sealed class BattleCommand` hierarchy would document the protocol and
(as a bonus) let the loop serialize commands to `update()` time instead
of applying them mid-frame.

### 7.9 Gradle module split

At minimum:

- `:app:core-domain` — `domain/model/`, `domain/repository/`.
- `:app:core-usecases` — `domain/usecase/` (depending on `:core-domain`).
- `:app:data` — `data/` (depending on `:core-domain` + external libs).
- `:app:app` — the rest (depending on all of the above).

This would force the 12 cross-layer leaks (§3.3) to the surface with a
compile error. Most could be fixed by widening the repository interfaces.
It would also give the build a cache-friendly structure for future CI.

### 7.10 A `NotificationDispatcher` with typed events

The three `*NotificationManager` classes each build a channel and
expose a typed `notify*` method. Adding a 4th notification (e.g.
"weekly-challenge-reset") requires a new class in `service/`. A
`NotificationDispatcher` that takes a `sealed class GameNotification`
would give channel-per-variant and one centralised check of
`NotificationPreferences`.

---

## 8. Summary

### What the code has already got right

- Clean three-layer split at the **filesystem** level (`domain/`, `data/`,
  `presentation/`).
- Zero package-import cycles.
- Consistent repository + StateFlow + Flow pattern across 12 screens.
- Tight external-dependency footprint.
- Enum-as-balance-sheet keeps game constants discoverable.
- Hilt wiring is orthogonal and minimal (6 small modules).
- Every screen is high-cohesion, low-coupling relative to its neighbours.
- Widget + service + worker coordination through a narrow, explicit
  SharedPreferences protocol.

### What would pay back most

Prioritised by `effort / payback`:

1. **Widen repository interfaces to absorb the 12 Dao leaks** (§3.3, §6.1, §6.2) — mechanical, unlocks a Gradle module split (§7.9).
2. **Extract `MissionProgressTracker`** (§7.1) — deletes 5 duplications, fixes one correctness bug (VMs not observing rollover).
3. **`TimeProvider` + `RandomSource`** (§7.3, §7.4 = Phase 4 §1) — makes half of Phase 4 trivially testable.
4. **Consolidate 10 SharedPreferences behind `PreferencesStore`** (§7.2) — unblocks DataStore migration if ever needed.
5. **`Reward` sealed hierarchy** (§7.5) — keeps future features from spreading.
6. **Extract `FollowOnPipeline` from `DailyStepManager`** (§7.6 = Phase 4 §4) — single largest cohesion win.

None of these are game-breaking; none require architectural re-thinks.
The shape of the app is sound. What's missing are the small interior
fences that would keep it that way as it grows.
