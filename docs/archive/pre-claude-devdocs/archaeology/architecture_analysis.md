# Architecture Analysis — Phase 8 (Reconstruction from code)

*Archaeology Phase 8. Written from code as of commit `a9d0386` (2026-05-04).
Where code disagrees with docs, **code wins** and the discrepancy is called
out explicitly.*

Companion documents, read first for baseline:

- Phase 2 `intro2codebase.md` / `intro2deployment.md` — how the code is laid
  out, what runs where, how it ships.
- Phase 3 `traces/` — 13 end-to-end walkthroughs of real execution paths.
- Phase 4 `5_things_or_not.md` — prioritised improvement list.
- Phase 5 `concepts/` — technical/design/business/missing concept inventory.
- Phase 6 `foundations/` — code-inferred foundations.

Where those documents already say something, this document references them
and does not re-derive. The **value-add here** is the architectural
critique: what the structure *looks like* from the outside, where it is
coherent, where it has drifted, and what is declared vs what is enforced.

---

## 1. Entry points & flows (reference summary)

The app has **six live entry points** to application code. Phase 2 §2
catalogues them. Re-stated here in one line each so this document stands
alone:

| # | Class | File | Trigger |
|---|---|---|---|
| 1 | `StepsOfBabylonApp` | `StepsOfBabylonApp.kt` | Process start — `@HiltAndroidApp`; loads SQLCipher; schedules `StepSyncWorker`. |
| 2 | `MainActivity` | `presentation/MainActivity.kt` | Launcher tap — single Activity, owns NavHost, requests permissions, forwards `navigate_to` deep-links. |
| 3 | `StepCounterService` | `service/StepCounterService.kt` | Started by MainActivity / BootReceiver — `foregroundServiceType=health`, `START_STICKY`, collects sensor flow. |
| 4 | `BootReceiver` | `service/BootReceiver.kt` | `BOOT_COMPLETED` — restarts service if permission granted. |
| 5 | `StepSyncWorker` | `service/StepSyncWorker.kt` | WorkManager every 15 min — sensor catch-up + Health Connect sync. |
| 6 | `StepWidgetProvider` | `service/StepWidgetProvider.kt` | AppWidget update cycle — renders latest figures from its own SharedPreferences. |

Plus two auxiliaries: `HealthConnectPermissionActivity` (privacy policy
rationale) and the package-private `MainActivity.onNewIntent` deep-link
dispatcher (`pendingNavigation: MutableStateFlow<String?>`).

**Two logical flows dominate** (Phase 2 §3 details them):

- **Step ingestion pipeline** — hardware sensor → `StepSensorDataSource` →
  `DailyStepManager` → Room (`PlayerProfileEntity`, `DailyStepRecordEntity`)
  → widget + supply drops + economy rewards + mission progress.
- **Battle loop** — `BattleViewModel` → `GameSurfaceView` → `GameLoopThread`
  → `GameEngine` (SurfaceView, 60 UPS) → `onStepReward` callback → Room.

Everything else in the app is a variation on the canonical
`Room → Flow → ViewModel → StateFlow → Compose` wiring.

---

## 2. Data models — the inventory

### 2.1 Persistence (Room)

12 entities in `data/local/`, all on a single `AppDatabase` (version 8,
SQLCipher-encrypted). Schema exported under `app/schemas/`. The full list:

| Entity | File | PK | Notes |
|---|---|---|---|
| `PlayerProfileEntity` | `data/local/PlayerProfileEntity.kt` | `id = 1` (single-row pattern) | 27 columns covering wallet (Steps, Gems, Power Stones, Card Dust), tier state, login streak, lifetime counters, monetisation flags, timestamps. |
| `WorkshopUpgradeEntity` | `WorkshopUpgradeEntity.kt` | `upgradeType: String` | Enum-name-keyed; 23 rows once seeded. |
| `LabResearchEntity` | `LabResearchEntity.kt` | `researchType: String` | Enum-name-keyed; holds `startedAt`/`completesAt` timestamps. |
| `CardInventoryEntity` | `CardInventoryEntity.kt` | `id` (autoGenerate) | Multi-row; one row per owned copy (duplicates → dust). |
| `UltimateWeaponStateEntity` | `UltimateWeaponStateEntity.kt` | `weaponType: String` | Enum-name-keyed; `isEquipped` inlined, no separate loadout table. |
| `DailyStepRecordEntity` | `DailyStepRecordEntity.kt` | `date: String` (ISO `yyyy-MM-dd`) | Step counters + escrow + `battleStepsEarned` (v8). |
| `WalkingEncounterEntity` | `WalkingEncounterEntity.kt` | `id` (autoGenerate) | Supply-drop inbox, capped at 10 via `enforceInboxCap`. |
| `WeeklyChallengeEntity` | `WeeklyChallengeEntity.kt` | `weekStartDate: String` (Monday) | Weekly PS tier claim state. |
| `DailyLoginEntity` | `DailyLoginEntity.kt` | `date: String` | Per-day PS + Gem claim flags. |
| `MilestoneEntity` | `MilestoneEntity.kt` | `milestoneId: String` | Claim state for the 6 walking milestones. |
| `DailyMissionEntity` | `DailyMissionEntity.kt` | `id` (autoGenerate) | 3 missions per day, mission type stored as enum name. |
| `CosmeticEntity` | `CosmeticEntity.kt` | `id` (autoGenerate) + `cosmeticId: String` | **Double key** (see §6.6). |

**TypeConverters** (`data/local/Converters.kt`) handle only two types:
`Map<Int,Int>` and `Map<String,Int>`, serialised as JSON via
`org.json.JSONObject`. Everything else goes through primitive columns; enums
are stored as their `.name` string and re-hydrated by the repository impls.

**Migrations** — single explicit `Migration(7, 8)` in
`data/local/Migrations.kt`, registered via `AppMigrations.ALL` in
`DatabaseModule.kt:22`. v1→v7 relied on destructive fallback per the v1.0
release note in `docs/database-schema.md`. `fallbackToDestructiveMigrationOnDowngrade`
is still enabled (`DatabaseModule.kt:23`) for dev builds.

### 2.2 Domain models (pure Kotlin)

`domain/model/` has **36 files**. They fall into a small number of shapes:

- **Currency / balances** — `Currency` (enum), `PlayerWallet` (data class),
  `PlayerProfile` (data class). §3.1 details the overlap.
- **Progression configs** — `UpgradeType` + `UpgradeConfig` + `UpgradeCategory`;
  `ResearchType`; `CardType` + `CardRarity`; `UltimateWeaponType`;
  `OverdriveType`; `Tier` + `TierConfig` + `BattleCondition` +
  `BattleConditionEffects`; `Biome`; `Milestone`; `DailyMissionType` +
  `MissionCategory`; `CosmeticCategory` + `CosmeticItem`; `BillingProduct`;
  `AdPlacement`. Most are enums-as-balance-sheet (data baked into enum
  constructor args).
- **Runtime/battle state** — `ZigguratBaseStats` (object of constants),
  `ResolvedStats` (data class, output of `ResolveStats`), `EnemyType`,
  `RoundState`.
- **Ownership / inventory** — `OwnedCard`, `OwnedWeapon`, `CardLoadout`,
  `UltimateWeaponLoadout`, `ActiveResearch`.
- **Rewards / economy events** — `SupplyDrop`, `SupplyDropTrigger`,
  `SupplyDropReward`, `DropGeneratorState`, `MilestoneReward` (sealed),
  `DailyStepSummary`.
- **Sealed result types declared at the use case level, not in model/** —
  `PurchaseResult`, `AdResult`, `StartResearch.Result`,
  `RushResearch.Result`, `UnlockLabSlot.Result`, `OpenCardPack.Result`,
  `ActivateOverdrive.Result`. See §4.3 on the asymmetry.

### 2.3 UI state objects

Each Compose screen has a `*UiState` data class in `presentation/<feature>/`
(15 of them). They exist only as *read* shapes for `collectAsStateWithLifecycle`.
Battle also has a `BattleUiState` with nested `RoundEndState` and `UWSlotInfo`
(see `presentation/battle/BattleUiState.kt`). UiState is the project's
only explicit "DTO" layer — there are no network request/response bodies
because there is no backend.

### 2.4 Commands, events, messages

There is **no explicit command/event system**. The app substitutes:

- **Kotlin function calls** for commands — use cases expose `operator fun invoke(...)`
  and ViewModels dispatch them.
- **`StateFlow`** for events — the VM updates `_userMessage.value = "..."`
  and Compose consumes. There is no event bus, no event class hierarchy, no
  `sealed class MissionEvent` or similar.
- **Volatile fields on `GameEngine`** for cross-thread battle state — e.g.
  `@Volatile var roundOver: Boolean`, `@Volatile var cash: Long`,
  `@Volatile var onStepReward: ((Long) -> Unit)?` — polled every 200 ms by
  `BattleViewModel.startPollingEngine` (line 109). This is the closest thing
  to a message protocol in the codebase, and it is polling, not pub/sub.
- **Android Intents** for process-crossing coordination — deep-link extra
  `navigate_to` on notifications; `AppWidgetManager.ACTION_APPWIDGET_UPDATE`
  on the widget; `BOOT_COMPLETED` for the receiver.
- **SharedPreferences polling** for widget data and service heartbeat —
  see §5 and §7.

---

## 3. Duplicated or overlapping models

### 3.1 `Currency` vs `SupplyDropReward` vs `MilestoneReward` — three ways to name the same concept

File pointers:

- `domain/model/Currency.kt:3` — `enum class Currency { STEPS, CASH, GEMS, POWER_STONES }`
- `domain/model/SupplyDropReward.kt:3` — `enum class SupplyDropReward { STEPS, GEMS, POWER_STONES, CARD_DUST }`
- `domain/model/MilestoneReward.kt:3` — `sealed class MilestoneReward { Gems, PowerStones, Cosmetic }`

Each one encodes "a currency-denominated reward" with a different shape and a
different, incomplete subset:

- `Currency` has `CASH` (transient, per-round) and is missing `CARD_DUST`.
- `SupplyDropReward` has `CARD_DUST` and is missing `CASH`.
- `MilestoneReward` has `Cosmetic` (not a currency) but is missing
  `Steps`, `Cash`, `CardDust`.

There is **no single "reward" abstraction** that subsumes all three. Adding
a new reward kind (e.g. "Card Dust from daily mission" — Plan 21 briefly
hints at this) would require edits in three disjoint places.

### 3.2 `PlayerWallet` vs `PlayerProfile` — two projections, one source

- `domain/model/PlayerWallet.kt:3` — 4 balances: `stepBalance`, `cash`,
  `gems`, `powerStones`. **No `cardDust`.**
- `domain/model/PlayerProfile.kt:3` — 27 fields, includes everything in
  `PlayerWallet` plus lifetime counters plus `cardDust`, but **no `cash`**
  (cash is per-round, not persistent).
- `PlayerProfile.toWallet()` (line 41) builds a wallet from the profile and
  **silently drops cardDust and cash**.

Consequence: any caller that needs to spend card dust must take
`PlayerProfile`, not `PlayerWallet`. `OpenCardPack.invoke` (line 26) does
this correctly by taking `gems: Long` and `ownedCards: List<OwnedCard>`
and touching the repo directly for dust; `UpgradeCard` similarly takes a
primitive `cardDust: Long`. So `PlayerWallet` is effectively a *"balances
you can spend from non-card use cases"* wallet, but the type does not
communicate that restriction.

### 3.3 `CardLoadout` vs `UltimateWeaponLoadout` — identical class twice

- `domain/model/CardLoadout.kt` — `data class CardLoadout(val cards: List<CardType>)` with `init { require(cards.size <= MAX_SIZE) ... require(cards.distinct() ...) }`, `add`, `remove`, `MAX_SIZE = 3`.
- `domain/model/UltimateWeaponLoadout.kt` — same class, same `MAX_SIZE`, `weapons: List<UltimateWeaponType>`.

These two files are line-for-line identical modulo the element type name.
A generic `Loadout<T>(val items: List<T>, val maxSize: Int)` would collapse
them without loss. Neither is exercised at runtime anyway — the "loadout"
state is stored **per-entity** via `isEquipped: Boolean` on
`CardInventoryEntity` / `UltimateWeaponStateEntity`, and enforced by
counting (`equippedCount < 3` at `CardsViewModel.kt:97`,
`UltimateWeaponViewModel.kt:82`). The domain loadout classes are used only
by a handful of tests (`CardLoadoutTest.kt`, `UltimateWeaponLoadoutTest.kt`).

### 3.4 Near-duplicate entity/domain pairs for immutable inventory

Every enum-keyed inventory entity is mirrored by a near-identical domain
data class:

| Entity | Domain twin | File pointers |
|---|---|---|
| `WorkshopUpgradeEntity(upgradeType, level)` | `Map<UpgradeType, Int>` (no twin) | `data/local/WorkshopUpgradeEntity.kt` |
| `LabResearchEntity(researchType, level, startedAt, completesAt)` | `ActiveResearch(type, level, startedAt, completesAt)` | `domain/model/ActiveResearch.kt` |
| `CardInventoryEntity(id, cardType, level, isEquipped)` | `OwnedCard(id, type, level, isEquipped)` | `domain/model/OwnedCard.kt` |
| `UltimateWeaponStateEntity(weaponType, level, isEquipped)` | `OwnedWeapon(type, level, isEquipped)` | `domain/model/OwnedWeapon.kt` |
| `WalkingEncounterEntity(id, triggerType, rewardType, rewardAmount, claimed, createdAt, claimedAt)` | `SupplyDrop(id, trigger, reward, rewardAmount, claimed, createdAt, claimedAt)` | `domain/model/SupplyDrop.kt` |
| `DailyStepRecordEntity(...)` | `DailyStepSummary(...)` | `domain/model/DailyStepSummary.kt` |

The mapping always has the same shape:
`entity.field` → `DomainType.valueOf(entity.fieldName)` for enums, pass-through
for primitives. There is no shared mapper infrastructure — each
`*RepositoryImpl.kt` has a private `fun Entity.toDomain()` extension. The
duplication is shallow (fields), but consistent and mechanical. With 6
pairs, the boilerplate cost is tolerable; the pattern shows up in
`PlayerRepositoryImpl.kt:64`, `CardRepositoryImpl.kt:47`,
`UltimateWeaponRepositoryImpl.kt:44`, `WalkingEncounterRepositoryImpl.kt:51`,
`StepRepositoryImpl.kt:46`, `CosmeticRepositoryImpl.kt:42`.

### 3.5 `PurchaseResult` and `AdResult` live in `domain/model/`; `StartResearch.Result`, `OpenCardPack.Result`, `ActivateOverdrive.Result`, `UnlockLabSlot.Result`, `RushResearch.Result` live **inside their use cases**

This is asymmetric:

- `domain/model/BillingProduct.kt:10` → `sealed class PurchaseResult` (`Success`, `Error`)
- `domain/model/AdPlacement.kt:5` → `sealed class AdResult` (`Rewarded`, `Cancelled`, `Error`)

These are external-SDK-facing. The in-use-case sealed classes
(`StartResearch.Result.InsufficientSteps`, `OpenCardPack.Result.InsufficientGems`,
etc.) are internal. The split makes some sense along "stable contract vs
implementation detail" lines, but it is undocumented and is easy to mis-file.

### 3.6 "Pre-X stats" snapshots duplicated inside the engine

`GameEngine` keeps **two** pre-overdrive stat snapshots:

- `presentation/battle/engine/GameEngine.kt:52` — `private var preOverdriveStats: ResolvedStats? = null`
- `presentation/battle/engine/GameEngine.kt:62` — `private var preGoldenStats: ResolvedStats? = null`

Each is saved/restored in its own `when (type) { ... -> { preX = stats; stats = stats.copy(...) } ... else -> { stats = preX }}` block
(`GameEngine.kt:130–150` for overdrive, `GameEngine.kt:221–226` for
Golden Ziggurat UW). If the two ever overlap in time (player triggers
Golden Ziggurat UW *while* Overdrive is active), the restore order is
implicit and relies on activation order. There is no explicit stat-effect
stack abstraction.

---

## 4. Contracts: interfaces, services, repositories, handlers

### 4.1 Repository interfaces (domain)

Ten `*Repository`-style contracts in `domain/repository/` + two
"Manager" contracts for SDKs:

| Interface | Implementor | Notes |
|---|---|---|
| `PlayerRepository` | `data/repository/PlayerRepositoryImpl.kt` | 23 methods — largest contract; every currency has its own `add/spend` pair. |
| `StepRepository` | `StepRepositoryImpl.kt` | 10 methods; `releaseEscrow` and `discardEscrow` collapse to the same `clearEscrow` DAO call (see §5.2). |
| `WorkshopRepository` | `WorkshopRepositoryImpl.kt` | 5 methods; includes `ensureUpgradesExist` seed. |
| `LabRepository` | `LabRepositoryImpl.kt` | 7 methods; includes `ensureResearchExists` seed. |
| `CardRepository` | `CardRepositoryImpl.kt` | 7 methods; no seed (cards start empty). |
| `UltimateWeaponRepository` | `UltimateWeaponRepositoryImpl.kt` | 5 methods; no seed (unlocked on demand). |
| `WalkingEncounterRepository` | `WalkingEncounterRepositoryImpl.kt` | 7 methods; owns inbox cap enforcement. |
| `CosmeticRepository` | `CosmeticRepositoryImpl.kt` | 7 methods; seed hard-coded inline (`SEED_COSMETICS`). |
| `BillingManager` | `StubBillingManager.kt` | Stub only — 3 methods. |
| `RewardAdManager` | `StubRewardAdManager.kt` | Stub only — 2 methods. |

All bindings in `di/RepositoryModule.kt` are `@Singleton`. All repos inject
DAOs via `@Inject constructor`.

### 4.2 Use cases (domain)

`domain/usecase/` has 36 files. All are plain Kotlin classes with a single
`operator fun invoke(...)`. They fall into three groups:

- **Pure computations** — `CalculateUpgradeCost`, `CalculateResearchCost`,
  `CalculateResearchTime`, `CalculateDamage`, `CalculateDefense`,
  `ResolveStats`, `ApplyCardEffects`, `CheckTierUnlock`,
  `CanAffordUpgrade`, `QuickInvest`, `CheckMilestones`,
  `GenerateSupplyDrop`. Stateless; no repository dependencies. Unit-testable
  with nothing more than constructor args and an injectable `Random` where
  relevant.
- **Orchestrators** — `PurchaseUpgrade`, `StartResearch`, `RushResearch`,
  `CompleteResearch`, `CheckResearchCompletion`, `UnlockLabSlot`,
  `UnlockUltimateWeapon`, `UpgradeUltimateWeapon`, `ActivateOverdrive`,
  `UpdateBestWave`, `OpenCardPack`, `UpgradeCard`, `ManageCardLoadout`,
  `AwardWaveMilestone`, `AwardBattleSteps`, `ClaimMilestone`,
  `ClaimSupplyDrop`, `PurchaseGemPack`. Take repository interfaces
  (usually) or DAOs (6 cases, see §7.3) and mutate.
- **Tracker jobs** — `TrackDailyLogin`, `TrackWeeklyChallenge`,
  `GenerateDailyMissions`. Periodic reconciliation, called from
  `DailyStepManager.runFollowOnPipeline` on every step credit.

### 4.3 Hilt DI module contracts

Six Hilt modules in `di/`, all `@InstallIn(SingletonComponent::class)`:

- `DatabaseModule` (`object`) — 13 `@Provides` (DB + 12 DAOs).
- `RepositoryModule` (`abstract class`) — 8 `@Binds` (one per repository).
- `BillingModule` / `AdModule` (`abstract class`) — 1 `@Binds` each, for
  the stub implementations.
- `StepModule` (`object`) — 1 `@Provides` (SensorManager).
- `HealthConnectModule` (`object`) — **empty body**. Comment:
  "This module exists as an organizational placeholder."
  (`di/HealthConnectModule.kt:8`). All HC classes use `@Inject constructor`
  instead.

**`StepSyncWorker` has its own injection channel** via Hilt-Work's
`@HiltWorker` + `@AssistedInject` (`service/StepSyncWorker.kt:23`). The
`HiltWorkerFactory` is wired into `StepsOfBabylonApp`
(`StepsOfBabylonApp.kt:12`) through `Configuration.Provider`.

### 4.4 Android framework-level contracts

| Contract | File | Scope |
|---|---|---|
| `SensorEventListener` (impl) | `data/sensor/StepSensorDataSource.kt:36` | Wraps `TYPE_STEP_COUNTER` in a `callbackFlow<Long>` emitting deltas. |
| `SurfaceHolder.Callback` | `presentation/battle/GameSurfaceView.kt` | Bridges Compose → custom game loop. |
| `AppWidgetProvider` (abstract subclass) | `service/StepWidgetProvider.kt:13` | Declares a static API (`saveData`, `updateAllWidgets`) used by `WidgetUpdateHelper`. |
| `BroadcastReceiver` (subclass) | `service/BootReceiver.kt` | `BOOT_COMPLETED` handler. |
| `CoroutineWorker` (via `@HiltWorker`) | `service/StepSyncWorker.kt` | Periodic background work. |
| `Service` (subclass) | `service/StepCounterService.kt` | Foreground sensor ingestion. |
| `Application` + `Configuration.Provider` | `StepsOfBabylonApp.kt` | WorkManager custom init. |

### 4.5 Battle-layer "contracts" (de-facto interfaces through callbacks)

`GameEngine` talks to its collaborators via **function-typed constructor
arguments**, not interfaces. Examples:

- `ZigguratEntity` takes `getEnemies: (Int) -> List<EnemyEntity>` and
  `fire: (Float, Float, Float, Float) -> Unit` (`ZigguratEntity` constructor,
  invoked at `GameEngine.kt:138`).
- `WaveSpawner` takes `onSpawnEnemy`, `onEnemyDeath`, `onMeleeHit`,
  `onEnemyFireProjectile`, `onWaveComplete` (`WaveSpawner.kt:13-21`).
- `OrbEntity` takes `getEnemies`, `onHitEnemy` (invoked at
  `GameEngine.kt:389`).
- `CollisionSystem.checkCollisions` takes `onProjectileHitEnemy`,
  `onEnemyProjectileHitZiggurat` (`CollisionSystem.kt:14-15`).
- `GameEngine.onStepReward: ((Long) -> Unit)?` — the only `@Volatile`
  callback, consumed by `BattleViewModel.wireStepRewardCallback`
  (`GameEngine.kt:70`, `BattleViewModel.kt:155`).

These are genuine extension points — new entity types plug in via the same
function signatures — but because they are lambdas, they are not
discoverable through "find all implementations" tooling. An engineer
landing on `WaveSpawner` has to read `GameEngine.kt:141-153` to find the
wiring.

### 4.6 Notification managers as "handlers" of domain events

Three notification managers in `service/`, each owning a notification
channel:

- `StepNotificationManager` — foreground-service ongoing notification.
- `SupplyDropNotificationManager` — handler for `SupplyDrop` events
  (called from `DailyStepManager.runFollowOnPipeline`).
- `MilestoneNotificationManager` — handler for milestone-achieved and
  best-wave events (called from `HomeViewModel.init` and
  `BattleViewModel.endRound`).
- Plus **inline** `SmartReminderManager` which builds its own channel
  inside `init { ... }` (`service/SmartReminderManager.kt:34`).

There is no `NotificationManager` base class or interface; each is a
separate `@Singleton` with its own public API. The dedup "already notified"
state is split: `MilestoneNotificationPreferences` for milestones,
`smart_reminders` inline SharedPreferences for reminders,
`StepNotificationManager.lastUpdateMs` for the service's 30s throttle.

---

## 5. Architectural patterns used

### 5.1 Clean Architecture (partial)

The three-layer split is **visible in the filesystem**:

```
com/whitefang/stepsofbabylon/
├── domain/      — pure Kotlin (mostly — see §7.3)
├── data/        — Android-dependent, implements domain contracts
└── presentation/— Compose + SurfaceView
```

The invariant "domain has zero Android imports" holds literally —
`grep android\.|androidx\. domain/` returns zero matches. **But**
6 domain use cases import `data.local.*Dao` directly (§7.3). The intent of
Clean Architecture (domain depends on nothing) is weaker than the letter
(domain depends on nothing Android). Use cases lean on DAOs because the DAO
interface is pure Kotlin, even though the annotations (`@Dao`,
`@Query`, `@Insert`) are from `androidx.room`. The transitive dependency is
compile-time only; runtime stays clean.

### 5.2 MVVM with StateFlow

All 12 screens follow the same pattern: `@HiltViewModel` class exposes a
`uiState: StateFlow<*UiState>` built via `combine(repo flows) { ... }.stateIn(
viewModelScope, WhileSubscribed(5000), InitialState)`. Compose screens
consume via `collectAsStateWithLifecycle` (wrapped as `collectAsState` in
several places, a minor Compose API drift). See
`HomeViewModel.kt:76`, `MissionsViewModel.kt:69`,
`StatsViewModel.kt:38`, `LabsViewModel.kt:65`, etc.

The exception is `BattleViewModel` (§5.7).

### 5.3 Repository pattern with Flow re-emission

`*RepositoryImpl` expose `Flow` for reads and `suspend` for writes. The
canonical read shape is `dao.get().filterNotNull().map { it.toDomain() }`
(`PlayerRepositoryImpl.kt:19`). Writes either call single-row DAO mutations
or `read-modify-write` with a transient `first()` read (§5.9).

### 5.4 Enum-as-balance-sheet

Game-balance constants are encoded in enum constructor arguments:

- `UpgradeType` — each variant has an `UpgradeConfig(baseCost, scaling,
  maxLevel, perLevelValue, description)` reached via
  `UpgradeType.configFor(this)` or the `config` property
  (`UpgradeType.kt:36-64`).
- `EnemyType` — `speedMultiplier`, `healthMultiplier`, `damageMultiplier`,
  `description`.
- `UltimateWeaponType` — `unlockCost`, `baseCooldownSeconds`,
  `effectDurationSeconds`, `description`, plus `upgradeCost(level)` and
  `cooldownAtLevel(level)` member functions.
- `OverdriveType` — `stepCost`, `durationSeconds`, `description`.
- `CardType` — per-level effect values, `maxLevel`.
- `DailyMissionType` — `category`, `description`, `target`, `rewardGems`,
  `rewardPowerStones`.
- `Milestone` — `requiredSteps`, `rewards: List<MilestoneReward>`.
- `Biome` — `tierRange`.
- `SupplyDropTrigger` — notification `message`.

This keeps balance values co-located with the identity they attach to, and
makes balance tests straightforward (just iterate `entries`). It also means
any balance tweak needs a code change and a new build — there is no runtime
balance configuration.

### 5.5 Seeded randomness (partial)

Seven use cases take `Random` as a constructor parameter with a default of
`Random` (the singleton): `GenerateSupplyDrop`, `OpenCardPack`,
`GenerateDailyMissions` (via `Random(todayDate.hashCode())`),
`CalculateDamage`. Compare this to the **76 `Random.*` references in 15
files** — most are *not* seamed. Battle-time RNG in `GameEngine`
(death defy, `GameEngine.kt:484`), `WaveSpawner` (enemy type picking,
`WaveSpawner.kt:126`, and spawn position, `:147`), and all particle
effect files (`DeathEffect`, `UWVisualEffect`, `OverdriveAuraEffect`,
`ProjectileTrailEffect`, `ScreenShake`) call `Random.nextX()` directly on
the singleton.

Phase 4 §1 flags the parallel gap for `Clock`. This randomness gap is
similar: the pattern exists for gameplay-critical paths (supply drops, card
packs, damage rolls) but not for cosmetic / in-round decisions. Consistency
has drifted.

### 5.6 Default-parameter time injection (sparse)

Where time matters, the pattern is `today: String = LocalDate.now().toString()`
or `now: Long = System.currentTimeMillis()` on the use case:

- `AwardBattleSteps.invoke(amount, today: String = LocalDate.now().toString())`
  (`AwardBattleSteps.kt:27`).
- `StartResearch.invoke(..., now: Long = System.currentTimeMillis())`
  (`StartResearch.kt:25`).
- `DailyStepManager.recordActivityMinutes(..., timestampMs: Long = System.currentTimeMillis())`
  (`DailyStepManager.kt:112`).

The pattern is applied to **5 of 53** time-call sites. The remaining **48 direct
`System.currentTimeMillis()` / `LocalDate.now()` / `Instant.now()` calls**
are scattered across 33 files. Phase 4 item 1 documents this gap in detail.

### 5.7 Fixed-timestep game loop (classical, dedicated thread)

`presentation/battle/GameLoopThread.kt` is a textbook accumulator loop:

```kotlin
while (isRunning) {
    if (!isPaused) {
        accumulator += (elapsed * speedMultiplier).toLong()
        while (accumulator >= TICK_NS) {
            engine.update(TICK_NS / 1_000_000_000f)
            accumulator -= TICK_NS
        }
    }
    canvas = surfaceHolder.lockCanvas() ...
    engine.render(canvas) ...
    surfaceHolder.unlockCanvasAndPost(canvas)
}
```

Fixed at 60 UPS (`TICK_NS = 16_666_667L`), with `@Volatile`
`speedMultiplier` and `isPaused` for thread-safe VM→thread commands. The
engine holds all game state; the thread only drives update/render timing.

The polling protocol between engine (game loop thread) and VM (main) is a
200 ms `while (true) { delay(200) ... }` in
`BattleViewModel.startPollingEngine` (`BattleViewModel.kt:109`). It is the
*only* feature in the app that doesn't use `Flow` for read-side
communication; it's also the only feature running outside coroutines for
compute.

### 5.8 Offline-first single-source-of-truth

All game state lives in Room. `data/` never reaches to the network. The
only "remote" source is Health Connect, which is an **on-device** service
(Platform Android 14+ integrates HC into the OS). Everything else is local
files: Room DB under `steps_of_babylon.db`, SharedPreferences under
`<package>/shared_prefs/`, widget data in its own prefs, schema snapshots
under `app/schemas/`.

Phase 2 §5 and Phase 6 §3 cover the offline model in more detail.

### 5.9 Read-modify-write via `flow.first()`

Used widely to do a one-shot read through a Flow-based DAO:

- `PlayerRepositoryImpl.updateBestWave` (line 53) — read profile, update
  map, write.
- `LabRepositoryImpl.startResearch` / `completeResearch` — same pattern.
- `UltimateWeaponRepositoryImpl.upgradeWeapon` / `equipWeapon` — same.
- `StepRepositoryImpl.updateDailySteps` uses `getByDateOnce` (explicit
  one-shot), not `first()`, but still issues read+write non-atomically.

No `@Transaction` / `withTransaction` appears anywhere in `app/src/main`
(grep confirms 0 matches). Phase 4 item 2 flags this as the obvious next
concurrency improvement.

### 5.10 Stub-then-swap adapter pattern

External SDKs (billing, ads) are wired through domain-layer interfaces
(`BillingManager`, `RewardAdManager`) with stub data-layer implementations
(`StubBillingManager`, `StubRewardAdManager`) bound via Hilt
(`BillingModule`, `AdModule`). Swapping to real SDKs is a one-line DI
change per domain; the caller sites do not need to know.

### 5.11 "Use case as plain Kotlin class, no Hilt annotations"

Stated convention (`docs/architecture.md`, steering `lib-hilt.md`): use
cases are not `@Inject`ed, they are constructed inline by their caller.
The codebase follows this for use cases owned by one caller, e.g.
`LabsViewModel.startResearch = StartResearch(labRepository, playerRepository, ...)`
(`LabsViewModel.kt:37`). But *some* use cases are `@Inject`-friendly because
they take only repo / DAO dependencies (e.g. `PurchaseUpgrade` in
`WorkshopViewModel.kt:28`). Both styles coexist.

---

## 6. What does not make sense (file pointers)

### 6.1 `SupplyDropTrigger.STEP_BURST` is defined but never emitted

`domain/model/SupplyDropTrigger.kt:5` declares `STEP_BURST` with a unique
message. `GenerateSupplyDrop` (the only producer) emits only
`DAILY_MILESTONE`, `STEP_THRESHOLD`, and `RANDOM`
(`GenerateSupplyDrop.kt:31, :52, :68`). `grep STEP_BURST` returns 1 match —
the declaration. No consumer checks for this trigger either.

This is a v1 feature that was designed (the message text suggests "burst
walking pace detection") but never implemented.

### 6.2 `ClaimMilestone` ignores `MilestoneReward.Cosmetic`

`ClaimMilestone.kt:25`:

```kotlin
is MilestoneReward.Cosmetic -> { /* no-op until cosmetics system exists */ }
```

But the cosmetics system **does** exist — `CosmeticEntity`,
`CosmeticDao.markOwned()`, `CosmeticRepository.purchase(cosmeticId)`
(`CosmeticRepositoryImpl.kt:27`) are all wired. Two of the six milestones
(`MARATHON_WALKER`, `IRON_SOLES`, `GLOBE_TROTTER` per `Milestone.kt:12-30`)
include `MilestoneReward.Cosmetic`, so claiming these milestones silently
drops the cosmetic reward. The cosmetic IDs (`garden_ziggurat_skin`,
`lapis_lazuli_skin`, `sandals_of_gilgamesh`) also do not match any
`SEED_COSMETICS` entry in `CosmeticRepositoryImpl.kt:58-66` (which seeds
`zig_obsidian`, `zig_crystal`, `zig_golden`, etc.).

Two parallel cosmetic inventories exist in the app: the paid store
cosmetics (7 seeded) and the milestone reward cosmetics (3 named, never
minted).

### 6.3 `MainActivity.PlaceholderScreen` is dead code

`presentation/MainActivity.kt:237` defines a private `@Composable
PlaceholderScreen(name: String)` that renders "`$name — Coming Soon`".
`grep PlaceholderScreen` returns one match — the declaration. It is not
wired into the NavHost (all 12 routes have real screens). Historical
artefact from earlier plans where features were stubbed.

### 6.4 `Screen.items by lazy` workaround

`presentation/navigation/Screen.kt:17`:

```kotlin
companion object {
    val items by lazy { listOf(Home, Workshop, Battle, Labs, Stats) }
}
```

The `by lazy` is there to work around a sealed-class-initialisation-order
NPE — documented in commit `1872af9`: "Fix crash on launch: lazy-init
Screen.items to avoid sealed class init order NPE". That the workaround is
needed at all suggests the sealed-class-of-data-objects pattern has a
known fragility here. A `enum class Screen(...)` would not have the
initialisation-order problem.

### 6.5 `releaseEscrow` and `discardEscrow` are the same function

`StepRepositoryImpl.kt:50-53`:

```kotlin
override suspend fun releaseEscrow(date: String) = dao.clearEscrow(date)
override suspend fun discardEscrow(date: String) = dao.clearEscrow(date)
```

Both delegate to `DailyStepDao.clearEscrow(date)`
(`DailyStepDao.kt:22`), which zeros `escrowSteps` and `escrowSyncCount`.
The semantic difference (release = add steps back to wallet; discard = keep
them removed) is handled at the **caller** — `StepCrossValidator.kt:85`
calls `playerRepository.addSteps(record.escrowSteps); stepRepository.releaseEscrow(date)`
before the "release" path, whereas the discard path does not add back.

This is not wrong, but the interface implies a semantic difference that
the implementation does not realise. A single `clearEscrow(date)` method
with comments at each call site would communicate more honestly.

### 6.6 `CosmeticEntity` has a double key

`data/local/CosmeticEntity.kt`:

```kotlin
@Entity(tableName = "cosmetics")
data class CosmeticEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cosmeticId: String,
    ...
)
```

`cosmeticId` is the user-facing stable identifier (`"zig_obsidian"`) and
is used in every DAO query (`CosmeticDao.markOwned(cosmeticId)`,
`equip(cosmeticId)`, etc.). The `id: Int` autoGenerate column is never
referenced in code. Had `cosmeticId` been the primary key, the `id`
column and autoGenerate overhead would not exist. This is the only entity
in the schema with this shape.

### 6.7 `GameSurfaceView` re-opens `sound_prefs` manually, ignoring `SoundPreferences`

`presentation/battle/GameSurfaceView.kt:26`:

```kotlin
val prefs = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)
soundManager.setMuted(prefs.getBoolean("muted", false))
soundManager.setVolume(prefs.getFloat("volume", 1f))
```

`data/SoundPreferences.kt` is the canonical wrapper for exactly this file
and these two keys, injected by `NotificationSettingsViewModel.kt:26`. But
`GameSurfaceView` is not a Hilt-injectable class (it's a `SurfaceView`
constructed by Compose's `AndroidView`). So rather than plumb
`SoundPreferences` through, it re-reads the raw prefs file by name.

Any change to the prefs structure (e.g. a migration to DataStore) would
need two edits, and keys could drift silently.

### 6.8 Duplicate daily-mission progress code scattered across 4 ViewModels + `DailyStepManager`

The `DailyMissionType.name` → progress-update logic is copy-pasted in 5
places:

- `DailyStepManager.updateWalkingMissions` (`data/sensor/DailyStepManager.kt:186`)
  — handles `WALKING` category.
- `MissionsViewModel.updateWalkingMissionProgress` (`MissionsViewModel.kt:82`)
  — duplicates the above for the case where the VM initialises after
  steps were already credited.
- `BattleViewModel.endRound` (`BattleViewModel.kt:165-182`) — handles
  `REACH_WAVE_30` and `KILL_500_ENEMIES` inline.
- `WorkshopViewModel.purchase` (`WorkshopViewModel.kt:92-100`) — handles
  `SPEND_5000_WORKSHOP` inline.
- `LabsViewModel.updateResearchMission` (`LabsViewModel.kt:186-192`) —
  handles `COMPLETE_RESEARCH`.

Adding a 7th daily mission type would require touching N of these sites
and writing the hand-coded `missions.find { it.missionType == X.name
&& !it.claimed && !it.completed }` lookup again. There is no
`MissionProgressService` abstraction.

### 6.9 `HealthConnectModule` is empty

`di/HealthConnectModule.kt` is 14 lines, of which 11 are package + imports
+ annotations. The body is `object HealthConnectModule`. Comment on line 8:
"This module exists as an organizational placeholder." In practice, HC
classes all take `@Inject constructor(@ApplicationContext context: Context, ...)`
and are resolved by Hilt without any `@Provides`/`@Binds`. Deleting this
module would have zero compile effect. It exists for symmetry with the
other `*Module` files, not for dependency wiring.

### 6.10 `BattleViewModel.wireStepRewardCallback` uses VM scope but crosses thread

`BattleViewModel.kt:150-164`:

```kotlin
engine.onStepReward = { amount ->
    viewModelScope.launch {
        val credited = awardBattleSteps(amount)
        ...
    }
}
```

This fires on the **game loop thread** (because `handleEnemyDeath` is
called from inside `GameEngine.update`, which runs on `GameLoopThread`).
The lambda immediately posts to `viewModelScope`, which is fine. But:

- If the VM has been cleared (activity destroyed) mid-round, the scope is
  cancelled and the credit is silently dropped. `onCleared` is called before
  `onStepReward = null`, so there's a small window. Phase 4 trace 07 §9
  discusses this.
- The order in which `awardBattleSteps` invocations complete is not
  guaranteed (coroutines may interleave) although the DAO `incrementBattleSteps`
  is atomic per-call (UPSERT with `ON CONFLICT` — `DailyStepDao.kt:35`).

### 6.11 `ViewModel`s inject DAOs

Six ViewModels import `data.local.*Dao` directly:
`BattleViewModel` (DailyMissionDao, DailyStepDao), `HomeViewModel`
(DailyLoginDao, DailyMissionDao, MilestoneDao), `LabsViewModel`
(DailyMissionDao), `WorkshopViewModel` (DailyMissionDao),
`MissionsViewModel` (DailyMissionDao, MilestoneDao, DailyStepDao),
`CurrencyDashboardViewModel` (DailyLoginDao, DailyStepDao,
WeeklyChallengeDao). This breaks the nominal
`presentation → domain ← data` rule (see §7.2).

### 6.12 `TrackDailyLogin` inside `DailyStepManager.recordSteps` does not carry `seasonPassActive`

`data/sensor/DailyStepManager.kt:178`:

```kotlin
trackDailyLogin.checkAndAward(currentDate, dailyCreditedTotal)
```

`TrackDailyLogin.checkAndAward(todayDate, todayCreditedSteps, seasonPassActive = false, seasonPassExpiry = 0)` (`TrackDailyLogin.kt:17`)
has two optional params for the Season Pass +10 Gems/day bonus. The caller
in `DailyStepManager` passes none, so **Season Pass holders who earn their
streak via walking (the common case) lose the +10 Gems bonus**. The only
caller that does pass the pass flags is `HomeViewModel.init`
(`HomeViewModel.kt:58`), which fires only once per app open. If the user
walks enough to trigger the streak reward before opening the app on a given
day, they get Gems without the Season Pass bonus.

### 6.13 `StepCrossValidator` has near-duplicate Level 0 and Level 1 branches

`data/healthconnect/StepCrossValidator.kt:71-91`:

Level 0 (default) and Level 1 (1–2 offenses) branches differ **only** in
`MAX_ESCROW_SYNCS_LEVEL1 = 2` vs `MAX_ESCROW_SYNCS_DEFAULT = 3`. The
body otherwise reads identically:

```kotlin
val excess = sensorSteps - hcSteps
val newSyncCount = record.escrowSyncCount + 1
if (newSyncCount >= MAX_ESCROW_SYNCS && record.escrowSteps > 0) {
    stepRepository.discardEscrow(date)
} else if (record.escrowSteps == 0L) {
    playerRepository.spendSteps(excess)
    stepRepository.updateEscrow(date, excess, newSyncCount)
} else {
    stepRepository.updateEscrow(date, excess, newSyncCount)
}
```

A single helper parametrised by `maxSyncs` would collapse ~20 lines.

---

## 7. Implied but not enforced

### 7.1 "Steps can never be generated passively" — enforced by convention, not the compiler

There is no type or guard that prevents a new developer from adding
`playerRepository.addSteps(1000)` to a menu tap handler. The invariant is
enforced by (a) code review against `docs/agent/CONSTRAINTS.md`, (b) the
4 documented paths that legitimately call `addSteps` (`DailyStepManager`,
`StepCrossValidator`'s release path, `AwardBattleSteps`,
`ClaimSupplyDrop`), and (c) balance regression tests. No structural
barrier.

A type-level fix would be a **capability token** — `addSteps` could take
an `AntiCheatClearance` object that only the four legitimate paths can
produce. The codebase does not do this.

### 7.2 Clean Architecture (the spirit, not the letter)

Stated invariant (steering `structure.md` §"Layer Rules"):
> `presentation` depends on `domain`, never on `data` directly

The compiler does not enforce this — there are no module boundaries
(`:app` is one module). Current violations:

- **6 domain use cases import `data.local.*Dao`**: `AwardBattleSteps`,
  `CheckMilestones`, `ClaimMilestone`, `GenerateDailyMissions`,
  `TrackDailyLogin`, `TrackWeeklyChallenge`. File pointers:
  `domain/usecase/AwardBattleSteps.kt:3`,
  `domain/usecase/ClaimMilestone.kt:3-4`,
  `domain/usecase/GenerateDailyMissions.kt:3-4`,
  `domain/usecase/TrackDailyLogin.kt:3-4`,
  `domain/usecase/TrackWeeklyChallenge.kt:3-5`.
- **6 presentation ViewModels import `data.local.*Dao`**: listed in §6.11.
- **`DailyStepManager` (in `data/sensor/`) directly constructs use cases**
  — `TrackDailyLogin(...)`, `TrackWeeklyChallenge(...)`,
  `GenerateSupplyDrop()` — and reads directly from `DailyMissionDao`,
  `DailyLoginDao`, `WeeklyChallengeDao` (`DailyStepManager.kt:60-64`).
  The data layer instantiates and runs domain use cases; flow is mixed.

Turning `:app` into a multi-module Gradle project with `:app:domain`,
`:app:data`, `:app:presentation` subprojects would surface all 12+ of
these violations at compile time.

### 7.3 "Repositories hide Room" — hidden with leaks

The contract is that ViewModels and use cases see only
`domain/repository/*Repository`. In practice, 12 sites leak Dao types
upward (§6.11, §7.2). For queries the repositories don't cover
(`countClaimable` on `DailyMissionDao`, `sumCreditedSteps` on
`DailyStepDao`), callers reach through. A strict interpretation would
expand the repository interfaces; a pragmatic one would accept the leaks
as explicit.

### 7.4 "Loadout is max 3" — enforced in four separate places, none authoritative

- `CardLoadout.init { require(cards.size <= MAX_SIZE) }` — structural but unused at runtime.
- `CardsViewModel.equipCard { if (equipped < 3) ... }` (`CardsViewModel.kt:114`).
- `ManageCardLoadout.equip(id, equippedCount)` — use case (requires caller to pass count).
- No DAO-level check — nothing stops `UPDATE card_inventory SET isEquipped = 1` from equipping all cards.

Same pattern on weapons: `UltimateWeaponViewModel.toggleEquip { if (equippedCount < 3) ... }` (`UltimateWeaponViewModel.kt:82`), `ManageUWLoadout`, no DAO guard.

The cap is a **game design constraint** treated as **UI guard**. Room sees
no loadout concept; it sees N rows each with `isEquipped`. Corruption
(e.g., a restored-from-backup DB with 5 equipped cards) would pass the
schema but break the game.

### 7.5 "Use cases are stateless" — four have caches

`BattleViewModel` holds `private val applyCardEffects = ApplyCardEffects()`,
`DailyStepManager` holds `private val generateSupplyDrop = GenerateSupplyDrop()`
plus a `dropState: DropGeneratorState` that the use case *reads* on every
call and writes back into via the VM's `copy`. The state is formally on
the caller, but the use case is morally stateful because `lastCheckSteps`
is carried forward.

### 7.6 Domain purity (`domain/` has no Android imports)

This one **is** enforced by convention and passes grep cleanly. Caveat:
domain imports from `data.local.*` pull in types defined in files that
themselves import `androidx.room.*` annotations. Retention is compile-time
only for Room annotations, so runtime purity holds. But "change the DB
layer, the domain rebuilds" — which is the cost Clean Architecture tries
to prevent — is real here.

### 7.7 "Room is the single source of truth"

Stated constraint. 10 separate SharedPreferences files coexist with Room
(`biome_prefs`, `milestone_notification_prefs`, `anti_cheat_prefs`,
`db_key_prefs`, `sound_prefs`, `step_ingestion`, `notification_prefs`,
`widget_data`, `smart_reminders`, `smart_reminders` again inline). Each
stores state that is, in some sense, "game state" (biome seen,
notification-sent flags, anti-cheat counters, widget last values, etc.).
Room is the single source of truth for *progression* state; SharedPreferences
is the single source of truth for *UX dedup* state. The constraint is
more accurate as written than as implemented, but the line between the
two stores is not documented.

### 7.8 "Concurrency safety via coroutine scopes" — no DB transactions

Phase 4 item 2 flagged: `grep '@Transaction|withTransaction app/src/main`
returns 0 matches. Every multi-statement write (e.g.
`PlayerRepositoryImpl.addGems` which does `adjustGems` + `incrementGemsEarned`)
is two separate SQL calls with no atomicity. Under a crash between them
or on rapid-fire calls, the counters can drift. The repository impls are
`@Singleton` so re-entrancy from multiple coroutines is real; `viewModelScope`
does not serialise them.

### 7.9 Currency non-negative clamps are in SQL, not in Kotlin

`PlayerProfileDao.adjustStepBalance` uses
`SET currentStepBalance = MAX(0, currentStepBalance + :delta)`
(`PlayerProfileDao.kt:41`). Same pattern on gems, power stones, card dust.
This is an implicit contract: "spend more than you have" becomes "spend
all you have, silently", not "throw". The R/R2 Currency Guard test
(`test/presentation/ux/CurrencyGuardTest.kt`) validates this but doesn't
surface it in the interface. A caller reading `spendGems` will not know
from its signature (returns `Unit`) that under-zero is clamped.

---

## 8. Summary & cross-references

Phase 2 is the "what is" document; this is the "what's wrong and why" one.
The biggest structural issues:

1. **Clean Architecture is mostly convention** (§5.1, §7.2). A single
   `:app:domain` Gradle module would mechanically enforce it.
2. **Time and randomness are partially seamed** (§5.5, §5.6). Phase 4
   item 1 proposes a `TimeProvider`; the same logic applies to `Random`.
3. **No DB transactions** (§5.9, §7.8). Every
   read-modify-write is racy.
4. **Reward abstractions fragmented** across `Currency`,
   `SupplyDropReward`, `MilestoneReward` (§3.1). A `Reward` sealed
   hierarchy would unify them.
5. **Mission progress update is duplicated 5x** (§6.8). A single
   `MissionProgressTracker` would collapse it.
6. **Loadout cap (3) enforced only in UI** (§7.4). DB cannot prevent
   restored-state violations.
7. **Silent no-op in `ClaimMilestone.Cosmetic`** despite the cosmetic
   system being wired (§6.2).
8. **Dead code: `STEP_BURST` trigger, `PlaceholderScreen`** (§6.1, §6.3).

None of these are game-breaking at v1.0. All are debt-like: the system
works; the next engineer to touch it pays a small tax. The five proposals
in `5_things_or_not.md` (Phase 4) overlap with items 1, 2, 3, 5 above; they
remain the recommended entry points.
