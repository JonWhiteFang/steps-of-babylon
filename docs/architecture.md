# Architecture

Steps of Babylon follows MVVM + Clean Architecture with three layers.

## Layer Diagram

```
┌─────────────────────────────────────────┐
│  presentation/                          │
│  ViewModels · Compose Screens ·         │
│  SurfaceView Battle Renderer            │
│         │ exposes StateFlow             │
│         ▼                               │
├─────────────────────────────────────────┤
│  domain/                                │
│  Use Cases · Repository Interfaces ·    │
│  Models · Game Logic                    │
│  ★ Pure Kotlin — zero Android imports   │
│         ▲                               │
├─────────────────────────────────────────┤
│  data/                                  │
│  Room Entities · DAOs ·                 │
│  Repository Implementations ·           │
│  Sensor / Health Connect Data Sources   │
└─────────────────────────────────────────┘
```

Data flow: `presentation → domain ← data`

## Layer Rules

- `domain/` has zero Android imports. Pure Kotlin only.
- `data/` implements domain repository interfaces.
- `presentation/` depends on domain, never on data directly.
- Hilt modules in `di/` wire data implementations to domain interfaces.

## Async Model

- Kotlin coroutines and `Flow` everywhere.
- Room queries return `Flow`.
- ViewModels collect flows and expose `StateFlow` to Compose.

## UI Split

| Surface | Technology | Used For |
|---|---|---|
| Menus & screens | Jetpack Compose | Home, Workshop, Labs, Cards, Stats |
| Battle renderer | Custom `SurfaceView` | Real-time wave combat |

The battle renderer runs on a dedicated thread with a fixed-timestep game loop. Rendering code is separate from game logic.

### Game Loop Architecture

```
Game Thread (SurfaceView)
  └─ Fixed timestep loop
       ├─ Update: entity positions, collision, stats resolution
       └─ Render: draw ziggurat, enemies, projectiles, effects

Stats Resolution = Workshop (permanent) × In-Round (temporary)
Wave Timing = 26s spawn phase + 9s cooldown
Speed Controls = 1x / 2x / 4x
```

## Dependency Injection

Hilt with KSP (not kapt). All modules in `di/`.

- `DatabaseModule` — provides Room database and DAOs (13 entities, 13 DAOs, schema v11)
- `RepositoryModule` — binds repository interfaces to Room-backed implementations
- `StepModule` — provides SensorManager
- `HealthConnectModule` — Health Connect organizational module
- `BillingModule` — `@Binds BillingManager → BillingManagerImpl` (sole binding post-C.5 PR 3 after `StubBillingManager` deletion). Sibling `BillingInternalModule` `@Binds BillingClientAdapter → RealBillingClientAdapter`.
- `AdModule` — `@Binds RewardAdManager → RewardAdManagerImpl` (sole binding post-C.6 PR 3 after `StubRewardAdManager` deletion). Sibling `AdInternalModule` `@Binds RewardedAdAdapter → RealRewardedAdAdapter` and `ConsentManager → RealConsentManager`.
- `TimeModule` — binds `TimeProvider` to `SystemTimeProvider` (B.1 / RO-01: seam for midnight-boundary testability)
- `CoroutineScopeModule` — provides `@ApplicationScope` CoroutineScope (SupervisorJob + Dispatchers.Default) that outlives ViewModel cancellation (B.3 PR 2 / RO-03: lets fire-and-forget end-of-round work survive mid-nav `onCleared`)

## Naming Conventions

| Type | Pattern | Example |
|---|---|---|
| Room entity | `*Entity.kt` | `PlayerProfileEntity` |
| Repository interface | `*Repository.kt` | `WorkshopRepository` |
| Repository impl | `*RepositoryImpl.kt` | `WorkshopRepositoryImpl` |
| Use case | Verb phrase | `CalculateUpgradeCost` |
| ViewModel | `*ViewModel.kt` | `WorkshopViewModel` |
| Compose screen | `*Screen.kt` | `HomeScreen` |
| Hilt module | `*Module.kt` | `DatabaseModule` |

## Security

| Layer | Measure | Details |
|---|---|---|
| Database | SQLCipher encryption | AES-256 full database encryption at rest via `net.zetetic:sqlcipher-android` |
| Key management | Android Keystore | DB passphrase encrypted with AES-256-GCM Keystore key, stored in SharedPreferences. Auto-recovery on keystore mismatch. |
| Backup | Disabled | `allowBackup="false"` — local-only game, prevents restore-related crashes |
| Network | Network security config | Cleartext traffic blocked via `network_security_config.xml` |
| Release build | R8 / ProGuard | Code shrinking, obfuscation, and resource shrinking enabled for release builds |


## Privacy & Permissions

The app does **not** request location permissions. There is no `LocationManager`, `FusedLocationProviderClient`, or GPS/network-location usage anywhere in the codebase. Step counting comes solely from `TYPE_STEP_COUNTER` (Android Sensor API) and Health Connect, which are activity-permission-gated, not location-permission-gated.

The original GDD §2.3 draft proposed an "Exploration Mode" with GPS-based 1.5km distance triggers; this was dropped from the v1.0 scope per [ADR-0016](agent/DECISIONS/ADR-0016-gps-exploration-mode-reconciliation.md) — the battery + privacy + Play Console review-cost trade-off doesn't justify the unproven gameplay payoff. Reserved as a v2.x meta-progression concept that may ship alongside V1X-25 long-term progression.

If a future agent reintroduces location services, that change must:

- Re-submit Play Console data safety form
- Add `play-services-location` dependency to `gradle/libs.versions.toml`
- Foreground service permission upgrade (`ACCESS_BACKGROUND_LOCATION` is intrusive — expect Play review delay)
- Update privacy policy at `docs/release/privacy-policy.md` and the hosted GitHub Pages copy
- Update this section to remove the "no location" guarantee

## Internationalization (i18n)

String externalization is being done in phases ([ADR-0014](agent/DECISIONS/ADR-0014-i18n-string-extraction.md)). All shipped strings are English-only for v1; the work to date makes them *localizable* without changing any behaviour.

**Phase 1 (done):**

- **Notifications** — the 3 notification managers read every channel name / title / content / action label from `strings.xml` via `context.getString(...)`.
- **Engine-internal floating-text** — `GameEngine` / `BattleViewModel` emit battle floating-text ("+12 HP", "RAPID FIRE!", "+45", "+3 Step", "+5 PS") through the pure-Kotlin `domain/Strings` seam (impl `data/AndroidStrings`), so the engine stays `Context`-free (keeps `GameEngineTest` pure-JVM, no Robolectric). Nullable `var strings` with a byte-identical literal fallback.
- **Battle + workshop Compose surfaces** — `WorkshopScreen`, `UpgradeCard`, `BattleScreen`, `PostRoundOverlay`, `PauseOverlay`, `InRoundUpgradeMenu`, `BiomeTransitionOverlay` read every user-facing string via `stringResource(...)` / `context.getString(...)` (the latter only where a `Context` is unavoidable, e.g. an `onClick` building a share `Intent` — hoisted to a `stringResource` `val` at composable scope to satisfy the `LocalContextGetResourceValueCall` lint check).

**Phases 2 / 3 (deferred to v1.3 / v1.4):** the remaining Compose screens (Store, Settings, Cards, Home, Economy, Labs, Missions, Stats, Supplies, UltimateWeapon, Help), then the first non-English locale.

### Lint policy & the Compose limitation

`app/build.gradle.kts` promotes `HardcodedText` to **error** severity:

```kotlin
lint {
    error += "HardcodedText"   // NOT `warningsAsErrors += "…"` — that property is a Boolean
}
```

**Important limitation (empirically verified):** Android Lint's `HardcodedText` is an **XML-only** check (`android:text` / `contentDescription` / `hint` in layout resources). It does **not** flag Jetpack Compose `Text("literal")`. Enabling the guard left `lintDebug` green even though ~110 hardcoded Compose strings still exist on the not-yet-migrated phase-2 screens — proof the rule never inspected them. So this guard concretely protects only XML resources (today: the home-screen widget layout, whose runtime-set `TextView`s use design-time `tools:text` placeholders).

Compose hardcoded-string discipline is therefore held by the phase-1 migration + code review, not by this lint rule. A dedicated Compose check (a custom lint rule, a Detekt-compose rule, or a dependency-free JVM guard test in the spirit of `architecture/DomainPurityTest`) is a candidate for a later phase if regressions appear; it was intentionally not added here to avoid a new dependency.
