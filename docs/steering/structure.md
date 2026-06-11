# Project Structure

## Root Layout

```
.github/
├── workflows/          # CI: ci.yml (PR gate) + instrumented.yml (emulator) + release.yml (Play internal) + dependency-submission.yml — SHA-pinned (Plan 32 / ADR-0018)
└── dependabot.yml      # gradle + github-actions weekly updates
app/src/main/java/com/whitefang/stepsofbabylon/
├── data/
│   ├── local/          # Room database, entities, DAOs, TypeConverters, SQLCipher key manager
│   ├── repository/     # Repository implementations (Room-backed, @Inject constructors)
│   ├── sensor/         # Step sensor data source, rate limiter, velocity analyzer, ingestion preferences, daily step manager
│   ├── healthconnect/  # Health Connect client, step reader, cross-validator, gap filler, activity minutes
│   ├── billing/        # BillingManagerImpl (real Play Billing v8, sole binding for both debug + release as of C.5 PR 3; StubBillingManager deleted after Phase G internal-track on-device verification PASSED 2026-05-18)
│   │   └── internal/   # BillingClientAdapter (SDK-neutral seam) + RealBillingClientAdapter (concrete v8 glue) + ActivityProvider (set/cleared by MainActivity lifecycle, C.5 PR 2; also consumed by data/ads/RewardAdManagerImpl from C.6 PR 1)
│   └── ads/            # RewardAdManagerImpl (real, sole binding for both debug + release as of C.6 PR 3; StubRewardAdManager deleted)
│       └── internal/   # RewardedAdAdapter (SDK-neutral seam) + RealRewardedAdAdapter (concrete AdMob glue) + ConsentManager (UMP seam) + RealConsentManager (concrete UMP glue)
├── domain/             # Pure Kotlin — no Android imports
│   ├── model/          # Data classes and enums
│   ├── repository/     # Repository interfaces (Flow-based)
│   ├── usecase/        # Use case classes (plain Kotlin, no @Inject)
│   ├── time/           # TimeProvider seam (B.1 / RO-01)
│   └── battle/         # V1X-09 simulation extraction (ADR-0012)
│       ├── engine/     # SimulationMath (pure-math helpers) + Simulation (V1X-09 Phase 3 COMPLETE — pure-domain in-round state: cash economy + round-progress counters + entity tick + collision sweep + UW lifecycle timers + the SimulationEvent flow; GameEngine delegates that surface + hasWaveProgress + events) + SimulationEvent (sealed StepReward/BossKilled side-effect events collected by BattleViewModel, replacing GameEngine's two @Volatile callbacks)
│       └── entity/      # Pure entity-motion/simulation state (ProjectileState, OrbState, EnemyState, ZigguratState) + EntityProtocol seam (Phase 3 — exposes isAlive/x/y/width/update so Simulation can run the entity tick + collision sweep) — no Android imports; presentation entities delegate update()
├── presentation/       # Android/Compose layer
│   ├── navigation/     # Screen routes, BottomNavBar
│   ├── home/           # Home screen, ViewModel, UiState
│   ├── workshop/       # Workshop screen, ViewModel, UpgradeCard
│   ├── battle/         # Battle renderer (SurfaceView, game loop, entities)
│   │   ├── engine/     # GameEngine, Entity, WaveSpawner, EnemyScaler, CollisionSystem
│   │   ├── entities/   # ZigguratEntity, ProjectileEntity, EnemyEntity, EnemyProjectileEntity, OrbEntity
│   │   ├── effects/   # ParticlePool, EffectEngine, ScreenShake, DeathEffect, UWVisualEffect, WaveAnnouncement, FloatingText, ProjectileTrailEffect, ReducedMotionCheck
│   │   ├── biome/      # BiomeTheme, BackgroundRenderer (gradient sky + ambient particles)
│   │   └── ui/         # HealthBarRenderer, InRoundUpgradeMenu, PostRoundOverlay, PauseOverlay, BiomeTransitionOverlay, UltimateWeaponBar
│   ├── weapons/        # UltimateWeaponScreen, UltimateWeaponViewModel
│   ├── labs/           # LabsScreen, LabsViewModel
│   ├── cards/          # CardsScreen, CardsViewModel
│   ├── supplies/       # UnclaimedSuppliesScreen, UnclaimedSuppliesViewModel
│   ├── economy/        # CurrencyDashboardScreen, CurrencyDashboardViewModel
│   ├── missions/       # MissionsScreen, MissionsViewModel
│   ├── settings/       # NotificationSettingsScreen, NotificationSettingsViewModel
│   ├── stats/          # StatsScreen, StatsViewModel, WalkingHistoryChart
│   ├── store/          # StoreScreen, StoreViewModel
│   ├── audio/          # SoundManager (SoundPool wrapper, 7 effects, volume/mute)
│   └── ui/theme/       # Compose theme, colors (Material3)
├── di/                 # Hilt modules (DatabaseModule, RepositoryModule, StepModule, HealthConnectModule, BillingModule, AdModule, TimeModule, CoroutineScopeModule)
└── service/            # Foreground step-counting service, WorkManager workers, boot receiver

app/src/test/java/com/whitefang/stepsofbabylon/
├── architecture/       # DomainPurityTest — machine-enforced domain-purity guard (#27; no Android imports in domain/)
├── fakes/              # In-memory fake repositories (FakePlayerRepository, FakeWorkshopRepository, FakeUltimateWeaponRepository, FakeLabRepository, FakeCardRepository, FakeWalkingEncounterRepository, FakeStepRepository, FakeCosmeticRepository, FakeBillingManager, FakeRewardAdManager, FakeMilestoneDao, FakeDailyMissionDao, FakeDailyLoginDao, FakeWeeklyChallengeDao, FakeDailyStepDao, FakeCosmeticDao, FakeTimeProvider)
├── domain/
│   ├── model/          # Domain model invariant tests (TierConfig, Biome, Loadouts, UpgradeType, EnemyType, Milestone, DailyMissionType, BattleConditionEffects)
│   └── usecase/        # All 36 use case tests
├── presentation/
│   ├── battle/
│   │   ├── engine/     # EnemyScaler tests
│   │   ├── biome/      # BiomeTheme tests
│   │   └── effects/    # ParticlePool, ScreenShake, DeathEffect tests
│   ├── home/           # HomeViewModel tests
│   ├── workshop/       # WorkshopViewModel tests
│   ├── labs/           # LabsViewModel tests
│   ├── cards/          # CardsViewModel tests
│   ├── weapons/        # UltimateWeaponViewModel tests
│   ├── supplies/       # UnclaimedSuppliesViewModel tests
│   ├── economy/        # CurrencyDashboardViewModel tests
│   ├── missions/       # MissionsViewModel tests
│   ├── stats/          # StatsViewModel tests
│   ├── store/          # StoreViewModel tests
│   ├── ux/             # CurrencyGuard, UserFeedback tests
│   └── DeepLinkRoutingTest.kt
├── data/
│   ├── sensor/         # StepRateLimiter, StepVelocityAnalyzer, StepIngestionPreferences, StepIngestion, DailyStepManager tests
│   ├── healthconnect/  # StepCrossValidator, ActivityMinuteValidator tests
│   ├── local/          # RoomSchema round-trip tests
│   ├── repository/     # CosmeticRepositoryImpl tests (C.2 PR 2)
│   └── integration/    # Escrow lifecycle tests
├── balance/            # Step economy, cost curves, enemy scaling, tier progression, cash, cards, UW, supply drops
└── service/            # StepWidgetProvider tests
```

The instrumented (`androidTest`) source set lives at `app/src/androidTest/java/com/whitefang/stepsofbabylon/` and was stood up in V1X-08 Phase 1A. Two files: `HiltTestRunner.kt` and `InfrastructureSmokeTest.kt`. Run with `./run-gradle.sh connectedDebugAndroidTest` against a connected emulator (API 34+).

## Layer Rules

- `domain/` must have zero Android imports — pure Kotlin only
- `data/` implements domain repository interfaces via `@Inject constructor`
- `presentation/` depends on domain, never on data directly
- Hilt modules in `di/` wire data implementations to domain interfaces
- Use cases are plain Kotlin classes — no Hilt annotations, injected via constructor

## Naming Conventions

| Pattern | Location | Example |
|---|---|---|
| `*Entity.kt` | `data/local/` | `PlayerProfileEntity` |
| `*Dao.kt` | `data/local/` | `PlayerProfileDao` |
| `*Repository.kt` | `domain/repository/` | `PlayerRepository` |
| `*RepositoryImpl.kt` | `data/repository/` | `PlayerRepositoryImpl` |
| `*ViewModel.kt` | `presentation/*/` | `WorkshopViewModel` |
| `*Screen.kt` | `presentation/*/` | `HomeScreen` |
| `*Module.kt` | `di/` | `DatabaseModule` |
| Use cases | `domain/usecase/` | `CalculateUpgradeCost`, `CanAffordUpgrade` |

## Domain Models

All in `domain/model/`:

- `Currency` — enum: STEPS, CASH, GEMS, POWER_STONES
- `PlayerWallet` — holds currency balances
- `PlayerProfile` — full player profile (maps from `PlayerProfileEntity`)
- `Tier`, `TierConfig` — difficulty tier definitions
- `UpgradeType`, `UpgradeCategory`, `UpgradeConfig` — Workshop upgrade system
- `CardType`, `CardRarity`, `CardLoadout` — Cards system
- `OwnedCard` — player-owned card instance
- `EnemyType`, `BattleCondition`, `RoundState` — Battle system
- `ZigguratBaseStats` — Base stat constants for the ziggurat
- `ResolvedStats` — Computed combat stats from workshop + in-round upgrades
- `UltimateWeaponType`, `UltimateWeaponLoadout` — Special abilities
- `OwnedWeapon` — player-owned ultimate weapon
- `Biome`, `ResearchType`, `ActiveResearch` — Progression systems
- `DailyStepSummary` — daily step record domain model
- `SupplyDrop` — walking encounter supply drop
- `SupplyDropTrigger` — 3 trigger types with notification messages
- `SupplyDropReward` — 4 reward types (Steps, Gems, Power Stones, Card Copy)
- `DropGeneratorState` — generator state tracking
- `Milestone` — 6 walking milestones with step thresholds and rewards
- `MilestoneReward` — sealed class: Gems, PowerStones, Cosmetic
- `DailyMissionType` — 6 daily mission types (walking/battle/upgrade)
- `MissionCategory` — mission categories: WALKING, BATTLE, UPGRADE
- `BillingProduct` — 5 billing products + PurchaseResult sealed class + public `skuId()` returning `name.lowercase()` (Plan 31 Phase F unblocker — Play Console requires `[a-z0-9._]` product IDs) + opt-in Companion for `BillingProduct.fromSkuIdOrNull` lookup in the data layer (C.5 PR 1)
- `AdPlacement` — 3 ad placements + AdResult sealed class
- `CosmeticCategory` — 3 cosmetic categories (ziggurat, projectile, enemy)
- `CosmeticItem` — cosmetic item domain model

## Key Files

| File | Purpose |
|---|---|
| `StepsOfBabylonApp.kt` | `@HiltAndroidApp`, `Configuration.Provider` (HiltWorkerFactory) |
| `di/DatabaseModule.kt` | Hilt module: Room DB (SQLCipher) + all 13 DAOs |
| `di/RepositoryModule.kt` | Hilt module: binds all 8 repository interfaces to impls |
| `di/StepModule.kt` | Hilt module: provides SensorManager |
| `di/HealthConnectModule.kt` | Hilt module: Health Connect organizational module |
| `di/BillingModule.kt` | Hilt module: `@Binds BillingManager → BillingManagerImpl` (C.5 PR 3 collapsed the flag-gated Provider switch after `StubBillingManager` deletion). Sibling `BillingInternalModule` `@Binds` `BillingClientAdapter` → `RealBillingClientAdapter`. Both `internal` so they can reference the internal adapter/impl types. `BuildConfig.USE_REAL_BILLING` was removed in C.5 PR 3 (no remaining readers) |
| `di/AdModule.kt` | Hilt module: `@Binds RewardAdManager → RewardAdManagerImpl` (C.6 PR 3 collapsed the flag-gated Provider switch after `StubRewardAdManager` deletion). Sibling `AdInternalModule` `@Binds` `RewardedAdAdapter` → `RealRewardedAdAdapter` and `ConsentManager` → `RealConsentManager`. Both `internal` so they can reference the internal adapter/impl types. `BuildConfig.USE_REAL_ADS` is no longer read by this module but is still consumed by `MainActivity` to gate the UMP consent prefetch on debug emulators |
| `di/TimeModule.kt` | Hilt module: binds TimeProvider to SystemTimeProvider (B.1, RO-01) |
| `di/CoroutineScopeModule.kt` | Hilt module: provides @ApplicationScope CoroutineScope(SupervisorJob + Dispatchers.Default) that outlives VM cancellation (B.3 PR 2, RO-03) |
| `domain/time/TimeProvider.kt` | Pure-Kotlin seam for wall-clock access; migrated 3 sites in B.1 PR 2 |
| `data/time/SystemTimeProvider.kt` | Production TimeProvider: delegates to Instant.now() / LocalDate.now() |
| `data/local/AppDatabase.kt` | Room database (13 entities, 13 DAOs, version 12; `billing_receipt` added in C.5 PR 1; `ultimate_weapon_state` recreated with per-path columns + `daily_step_record.bossPsEarnedToday` added in v9→10 R4-06 + R4-07; `card_inventory` recreated with `copyCount` aggregation + unique index on `cardType` in v10→11 R4-08; `(date, missionType)` unique index on `daily_mission` added in v11→12 #127) |
| `data/local/DatabaseKeyManager.kt` | SQLCipher passphrase via Android Keystore |
| `data/local/Converters.kt` | TypeConverters for `Map<Int,Int>` and `Map<String,Int>` (JSON) |
| `data/sensor/StepSensorDataSource.kt` | TYPE_STEP_COUNTER wrapper, emits deltas via callbackFlow |
| `data/sensor/StepRateLimiter.kt` | Anti-cheat: 200 steps/min cap (250 burst) |
| `data/sensor/DailyStepManager.kt` | Orchestrates rate limit → 50k ceiling → Room persist |
| `service/StepCounterService.kt` | Foreground service (health type), START_STICKY |
| `service/StepSyncWorker.kt` | @HiltWorker, 15-min periodic catch-up + HC sync |
| `domain/usecase/CalculateUpgradeCost.kt` | Cost formula: `baseCost × scaling^level` |
| `domain/usecase/CanAffordUpgrade.kt` | Affordability check against wallet |
| `domain/usecase/ResolveStats.kt` | Workshop + in-round levels → ResolvedStats |
| `domain/usecase/CalculateDamage.kt` | Raw damage + crit roll + damage/meter bonus |
| `domain/usecase/CalculateDefense.kt` | Damage reduction (cap 75%) + flat block |
| `domain/usecase/UpdateBestWave.kt` | Compares wave to stored best, persists if new record |
| `domain/usecase/CheckTierUnlock.kt` | Checks wave milestones for tier unlock eligibility |
| `domain/usecase/UnlockUltimateWeapon.kt` | Checks Power Stone balance, deducts, unlocks UW |
| `domain/usecase/UpgradeUltimateWeapon.kt` | Cost scaling per level, max level 10 |
| `presentation/MainActivity.kt` | Single Activity, Scaffold + NavHost + BottomNavBar (hidden during battle), permissions |
| `presentation/navigation/Screen.kt` | 13 navigation routes (Home, Workshop, Battle, Labs, Stats, Weapons, Cards, Supplies, Economy, Missions, Settings, Store, Help) |
| `presentation/home/HomeViewModel.kt` | Combines profile + step flows into HomeUiState |
| `presentation/battle/GameSurfaceView.kt` | SurfaceView managing game loop thread lifecycle |
| `presentation/battle/GameLoopThread.kt` | Fixed timestep (60 UPS), accumulator, speed multiplier |
| `presentation/battle/engine/GameEngine.kt` | Central coordinator: entity list, update/render dispatch, wave/collision integration |
| `presentation/battle/engine/WaveSpawner.kt` | Wave lifecycle: 26s spawn + 9s cooldown, enemy composition by wave |
| `presentation/battle/engine/EnemyScaler.kt` | Wave-based stat scaling (1.05^wave), cash rewards per type |
| `presentation/battle/engine/CollisionSystem.kt` | Projectile↔enemy and enemy projectile↔ziggurat collision |
| `presentation/battle/BattleViewModel.kt` | Loads tier, polls engine state, exposes BattleUiState + BattleEvent |
| `res/drawable/ic_launcher_background.xml` | Solid #0E2247 deep-lapis vector background for the adaptive launcher icon |
| `res/drawable/ic_launcher_foreground.xml` | 5-tier stepped-ziggurat silhouette with Gold → SandStone → lightened-DeepBronze vertical gradient |
| `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | Adaptive-icon XML wrappers referencing the two drawables above. minSdk=34 means these are the sole icon source — no raster density fallbacks needed |
| `tools/render_play_store_icon.py` | Pillow-only Python script that re-renders the Play Store 512×512 hi-res PNG icon from the same coordinates / gradient stops as the in-app vector XML. Run via `python3 tools/render_play_store_icon.py` |
| `docs/release/store-assets/play-store-icon-512.png` | Play Store hi-res icon (512×512, ~3.8 KB). Generated artifact — regenerate via the script above |
| `docs/release/store-assets/StepsOfBabylonArt.png` | User-supplied 1376×768 pixel-art source for the feature graphic |
| `docs/release/store-assets/play-store-feature-graphic-1024x500.png` | Play Store feature graphic (1024×500, ~621 KB). Center-vertical-cropped + LANCZOS-downscaled from the source PNG |
| `gradle/libs.versions.toml` | All dependency versions |
| `app/schemas/` | Room schema exports (commit these) |
| `docs/plans/` | Numbered implementation plans (01–32) + 10b/R/R2/R3/R4/V1X (38 master-plan entries) |

## Development Plans

Forward planning is in `docs/plans/plan-FORWARD.md` (path to closed test + launch, keyed to the Closed-Test Readiness Gate). `docs/plans/master-plan.md` is the v1.0 completion record (38 entries). `docs/plans/plan-V1X-roadmap.md` is the post-launch backlog of record. The completed v1.0 plan files (Plans 01–30, 10b, R*, RO-*) are archived under `docs/archive/completed-plans-v1.0/`. Live plan files in `docs/plans/`: master-plan, plan-FORWARD, plan-31-play-console, plan-32-ci, plan-V1X-roadmap.
