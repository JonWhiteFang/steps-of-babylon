# Project Description — Code-Inferred Foundations

*Archaeology Phase 6 — synthesised from code (and prior phases 1–5 which
are themselves code-grounded). Where anything in this doc disagrees with
marketing copy or plans, the code is authoritative and the discrepancy is
called out. Companion docs: `philosophy.md`, `known_requirements.md`.*

## 1. What the system actually does

Steps of Babylon is a **single-process Android game application** whose
entire gameplay economy is gated on a physical-activity signal sourced
from the user's phone. It runs fully offline with no backend and stores
all state in an encrypted local database.

Concretely, in code, the system does five things:

1. **Continuously counts the user's steps in the background.** A
   `foregroundServiceType="health"` service
   (`service/StepCounterService.kt`) subscribes to the Android
   `TYPE_STEP_COUNTER` sensor via a `callbackFlow` wrapper
   (`data/sensor/StepSensorDataSource.kt`) and feeds a step-ingestion
   orchestrator (`data/sensor/DailyStepManager.kt`). A 15-minute
   periodic `@HiltWorker` (`service/StepSyncWorker.kt`) fills any gap
   if the service was killed.
2. **Filters the step stream through a four-layer anti-cheat
   pipeline.** `StepRateLimiter` (200/min, 250 burst), `StepVelocityAnalyzer`
   (shaker/spoof detection, 1.0 / 0.5 / 0.0 credit multiplier), a hard
   50 000 / day ceiling (`DailyStepManager.DAILY_CEILING = 50_000L`),
   and a Health Connect cross-validator with four graduated response
   levels (`data/healthconnect/StepCrossValidator.kt`).
3. **Converts exercise minutes from Health Connect into
   step-equivalents** for non-ambulatory activity
   (`data/healthconnect/ActivityMinuteConverter.kt` +
   `ActivityMinuteValidator.kt` +
   `ExerciseSessionReader.kt`). Sensor steps and exercise-minute
   credits both flow through the same follow-on pipeline, with
   per-minute overlap deduction to prevent double-counting.
4. **Lets the player spend the resulting `Steps` currency** on 23
   permanent Workshop upgrades
   (`domain/model/UpgradeType.kt`), 10 Labs research projects
   (`domain/model/ResearchType.kt`), and one per-round Step Overdrive
   activation (`domain/model/OverdriveType.kt`), plus up to three
   Ultimate Weapons (`domain/model/UltimateWeaponType.kt`, paid in
   Power Stones) and a 3-card loadout
   (`domain/model/CardType.kt`, packs paid in Gems).
5. **Runs a 60-UPS fixed-timestep tower-defence battle** on a
   dedicated `Thread("GameLoop")`
   (`presentation/battle/GameLoopThread.kt`) rendered into a custom
   `SurfaceView`. The battle produces Cash (in-round), Step rewards
   capped at 2 000/day (`domain/usecase/AwardBattleSteps.kt`, ADR-0003),
   Power Stone wave milestones, and tier unlocks that reveal new
   visual biomes.

Surrounding those five core behaviours, the code also:

- Renders a **home-screen 2×2 widget** showing today's steps and
  balance (`service/StepWidgetProvider.kt` +
  `service/WidgetUpdateHelper.kt`, 60 s-throttled).
- Fires **four categories of notification**: persistent step counter
  (`service/StepNotificationManager.kt`), supply drops
  (`service/SupplyDropNotificationManager.kt`), milestones / best-wave
  (`service/MilestoneNotificationManager.kt`), and smart reminders
  piggy-backed on `StepSyncWorker` (`service/SmartReminderManager.kt`).
  Each tap deep-links back into a Compose route via
  `MainActivity.pendingNavigation`.
- Exposes **12 navigable Compose screens**
  (`presentation/navigation/Screen.kt`): Home, Workshop, Battle, Labs,
  Stats, Weapons, Cards, Supplies, Economy, Missions, Settings, Store.
- Implements a **monetisation surface** with five IAP SKUs (three
  Gem packs, Ad Removal, Season Pass) and three reward-ad placements,
  currently served by `StubBillingManager` / `StubRewardAdManager`
  that simulate latency and always succeed.
- Persists **everything** in an SQLCipher-encrypted Room database
  (`data/local/AppDatabase.kt`, version 8, 12 entities, 12 DAOs) with
  an Android Keystore-managed 32-byte passphrase
  (`data/local/DatabaseKeyManager.kt`).

## 2. Current use cases (what the code can actually be used for)

Derived from screens, services, worker jobs and their wiring — not from
aspirational docs:

- **Earn a primary in-game currency by walking.** The only end-to-end
  path that mints Steps into the player wallet is real-world movement
  (sensor or Health Connect-sourced activity minutes). No code path
  produces Steps outside `DailyStepManager` + `AwardBattleSteps`
  (enforced by absence).
- **Keep step counting working when the app is backgrounded, killed,
  or after reboot.** `START_STICKY` on the foreground service,
  `BootReceiver` restarts it on `android.intent.action.BOOT_COMPLETED`,
  and `StepSyncScheduler` re-enqueues the periodic worker from
  `StepsOfBabylonApp.onCreate` every process start.
- **Play short, offline tower-defence rounds.** The battle is fully
  self-contained; nothing in `presentation/battle/` reads network state
  or cloud progress.
- **Convert Steps into lasting power** (Workshop permanent upgrades,
  Labs research, Ultimate Weapon unlocks) and **transient power**
  (Overdrive, in-round upgrades, cards).
- **Progress through difficulty tiers** that in turn unlock **narrative
  biomes** that change the battle's visual palette
  (`presentation/battle/biome/BiomeTheme.kt`).
- **Get credit for non-walking fitness** (cycling, swimming, yoga,
  etc.) through `ActivityMinuteConverter` — intended explicitly for
  indoor workouts and non-ambulatory users.
- **Receive meaningful push notifications** tied to walking (supply
  drops, milestones), each deep-linked to the appropriate inbox.
- **Buy convenience/cosmetic IAPs** (stub pipeline today). Steps are
  never a purchasable SKU; no `BillingProduct` entry produces Steps.
- **Review history and stats** — walking-history chart (today/week/
  month), battle stats, all-time aggregates
  (`presentation/stats/StatsScreen.kt`).
- **Toggle four notification categories** and mute/volume sound
  (`presentation/settings/NotificationSettingsScreen.kt` +
  `data/NotificationPreferences.kt` + `data/SoundPreferences.kt`).

Use cases that the code does **not** currently support (even if
docs/plans suggest them):

- Real IAP fulfilment (stub only — `di/BillingModule.kt` binds
  `StubBillingManager`).
- Real reward-ad fulfilment (stub only — `di/AdModule.kt` binds
  `StubRewardAdManager`).
- Cosmetic visual effects in battle (ownership and equip flag exist,
  but `presentation/battle/engine/GameEngine.kt` does not read cosmetic
  state — the Store screen itself labels cosmetic buttons "Coming
  Soon").
- Cross-device progress sync (`allowBackup="false"` in
  `AndroidManifest.xml`, no cloud save).
- Account / multiplayer / leaderboards (no network stack, no
  `BillingManager` equivalent for account services).
- A launcher icon (no `mipmap-*` dirs; `android:icon` absent from the
  manifest application element).
- Onboarding / tutorial (no code path).
- Localisation (`res/values/strings.xml` holds only `app_name`; screens
  hardcode English strings inline).

## 3. Actor / user types implied by code

The code reveals the following distinct roles:

### Primary human actor — the player

- Grants up to **7 runtime permissions** in `presentation/MainActivity.kt`:
  `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `health.READ_STEPS`,
  `health.READ_EXERCISE`, plus manifest-declared `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_HEALTH`, `RECEIVE_BOOT_COMPLETED`.
- Walks and exercises; optionally installs the widget; optionally taps
  notifications; interacts with 12 Compose screens and the
  SurfaceView-rendered Battle.
- Optionally pays money for Gems / Ad Removal / Season Pass; optionally
  watches reward ads.
- Is implicitly solo — no shared state, no social graph, no
  opponents beyond the AI-driven enemy waves.

### Platform service actors (not humans, but drive the code)

- **Android OS** — sensor framework, `TYPE_STEP_COUNTER`,
  `WorkManager`, `AlarmManager` (via WorkManager internals),
  `AppWidgetManager`, Notification Manager, Lifecycle framework,
  `BOOT_COMPLETED` broadcast, `SensorManager`.
- **Google Health Connect** — queried via
  `androidx.health.connect.client` (v1.2.0-alpha02). Read-only
  integration (steps + exercise sessions). Availability is probed at
  runtime (`HealthConnectClient.SDK_AVAILABLE`) and the app degrades
  gracefully when missing.
- **Android Keystore** — holds an AES-256-GCM key aliased
  `steps_of_babylon_db_key` used to wrap the Room passphrase
  (`DatabaseKeyManager.kt`).

### Developer / tester actors

- **The Gradle developer.** Builds with `./gradlew assembleDebug /
  assembleRelease / bundleRelease`, runs `./gradlew test` (412 JVM
  tests via JUnit 5 + Robolectric). Uses `./run-gradle.sh` from
  non-TTY environments (Kiro CLI, CI) to avoid Gradle output
  buffering. Optionally provides a `keystore.properties` to sign
  releases.
- **The release engineer.** Manually runs the release checklist
  (`docs/release/release-checklist.md`), uploads AABs to Google Play
  Console test tracks. There is **no CI** — no `.github/workflows`,
  no `.circleci`, no Jenkinsfile. Every green build today is a
  developer-local build.

### Roles that do NOT exist in this codebase

- No **server operator / SRE** — there is no backend.
- No **account-support role** — there is no account system.
- No **admin / moderator** — single-player only, no UGC, no chat.
- No **analytics consumer** — Firebase/Sentry/etc. are not in
  `gradle/libs.versions.toml`; no telemetry is shipped off-device.

## 4. Actual problems being solved

From what is implemented — not aspirations — the system addresses:

### Turning physical activity into a self-contained mobile game loop
The core engineering problem is wiring a hardware sensor
(`TYPE_STEP_COUNTER`), a platform aggregator (Health Connect), and a
local game economy such that the only way to progress is to move.
Every data path in `DailyStepManager.runFollowOnPipeline` is a
side-effect of credited steps: wallet increment, widget, supply drop,
daily-login check, weekly challenge, walking-mission progress.

### Reliable background step counting across device states
A non-trivial amount of code exists solely to make counting survive
the OS-level states a user subjects their phone to: screen off, app
killed, reboot. Evidence: foreground service with `START_STICKY`,
BOOT_COMPLETED receiver, 15-minute periodic worker, service↔worker
2-minute heartbeat protocol
(`data/sensor/StepIngestionPreferences.kt`), day-start counter
bootstrap (`StepCounterService.initDayStartCounter`).

### Client-side anti-cheat without a server
With no backend, cheat mitigation has to live on-device. The code ships
four distinct layers (rate limiter, velocity analyser, daily ceiling,
Health Connect cross-validation) plus battle-step separation (ADR-0003)
to prevent combat from becoming a passive Step generator. The implicit
threat model is a motivated individual with shake-the-phone or
mock-sensor tooling — not a coordinated attacker.

### Accessibility to non-walkers without bending the core rule
`ActivityMinuteConverter` + `ActivityMinuteValidator` solve the product
problem "walking-only gatekeeping excludes users who can't walk". The
conversion happens inside the same anti-cheat-gated pipeline, so the
system's correctness properties (no Steps without real activity,
cap at 50 k/day) hold for exercise minutes too.

### Deterministic, offline persistence with at-rest confidentiality
SQLCipher + Android Keystore encryption solve the problem of holding
up to years of player progress on a device that might be lost,
borrowed, or inspected. The passphrase is never baked into the APK —
each install generates 32 random bytes via `SecureRandom`.

### Real-time 60 UPS simulation embedded in a Compose app
The battle renderer is a direct response to "Compose cannot give a
deterministic 60 UPS simulation on its own." The fix is
`GameLoopThread` (fixed timestep, accumulator scaled by
`speedMultiplier`, `@Volatile` cross-thread state), surfaced as an
`AndroidView(GameSurfaceView)` sandwich with Compose HUD overlays.
The rest of the app stays pure Compose.

### A solo, privacy-respecting fitness product
No analytics, no network client, no account, `allowBackup="false"`,
`cleartextTrafficPermitted="false"`, HC permissions gated behind a
dedicated rationale Activity. The problem being solved is not
"monetise a user's attention" — it is "give a single user a motivating
local experience tied to their own walking history without exporting
anything".

## 5. Primary runtime and delivery model

### Runtime

- **Single Android application process**, package
  `com.whitefang.stepsofbabylon`, v1.0.0 (`versionCode 1`), minSdk 34
  (Android 14), targetSdk/compileSdk 36
  (`app/build.gradle.kts`).
- **One Gradle module**: `:app` (`settings.gradle.kts` includes only
  this). No libraries, no secondary apps, no backend services.
- **Six runtime entry points** (per `intro2codebase.md` §2):
  Application `onCreate`, launcher Activity (`MainActivity`),
  foreground service (`StepCounterService`), boot receiver
  (`BootReceiver`), periodic worker (`StepSyncWorker`), widget
  provider (`StepWidgetProvider`). Plus `HealthConnectPermissionActivity`
  as a system-initiated rationale surface.
- **Three threads of meaningful work**: main/UI (Compose), a
  supervised coroutine scope inside the foreground service
  (`SupervisorJob + Dispatchers.Default`), and the dedicated
  `Thread("GameLoop")` during battle. WorkManager threads appear
  during the periodic sync window.
- **In-process state**: all game state in Room (single source of
  truth), small durable state in eight separate SharedPreferences
  files, transient battle state in `@Volatile` fields on `GameEngine`.
  No Jetpack DataStore, no HTTP client, no sockets, no filesystem I/O
  outside `Context.filesDir` (`steps_of_babylon.db`).
- **Native dependency**: one `.so` — `libsqlcipher` — loaded at
  `StepsOfBabylonApp.onCreate` via `System.loadLibrary("sqlcipher")`.

### Delivery

- **Artefact**: one signed Android App Bundle (`.aab`) produced by
  `./gradlew bundleRelease`, optionally signed via
  `keystore.properties` (gitignored; release signing is opt-in at
  build time).
- **Distribution channel**: Google Play Store (publisher Whitefang
  Games, per `docs/release/play-store-listing.md`). No other
  distribution is wired — no F-Droid metadata, no sideload installer,
  no auto-update server, no in-app update API usage.
- **Update model**: user-initiated Play Store updates. There is no
  in-app update prompt, no dynamic feature module, no downloadable
  content bundle, no remote config. Every change reaches users by
  shipping a new `versionCode`.
- **Migration model**: Room explicit `Migration` objects in
  `data/local/Migrations.kt` for upgrades;
  `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`
  for downgrades (only expected during dev/QA).
  `AppMigrations.ALL` currently contains one entry
  (`MIGRATION_7_8`, adds `battleStepsEarned` per ADR-0003).
- **Build verification**: manual — `./run-gradle.sh test` (412 JVM
  tests) plus `assembleRelease` must succeed. No CI, no staged
  rollout automation, no feature flagging.
- **Ops surface**: once installed, the app has no operator — it
  cannot be configured, updated, or throttled from outside the Play
  Store update channel. The only runtime levers are user-controlled
  preferences (notifications, sound mute/volume).

## 6. Explicit unknowns (things code alone does not answer)

- **Exact supported device range inside minSdk 34.** The manifest
  requires `TYPE_STEP_COUNTER` at runtime (via permission +
  sensor-lookup) but does not declare a
  `<uses-feature android:required="true">` for the step counter, so
  the app installs on step-counter-less devices. The Play Console
  would need a feature filter; that configuration is not in the
  repo.
- **Final monetisation pricing per region.** `BillingProduct` hardcodes
  USD display strings (`"$0.99"` etc.) — Play Billing would substitute
  localised prices at runtime, but only if / when the stub is
  replaced.
- **Final audio content.** `res/raw/sfx_*.ogg` files exist but are
  described in project memory as placeholders; code does not disclose
  whether they are final.
- **Which real SDKs will replace the stubs.** `BillingModule` +
  `AdModule` bind stubs today; whether the replacements will be
  Google Play Billing + AdMob specifically (as Plan 31 suggests) or
  some mediated alternative is not determinable from code alone.
- **Battery cost of the foreground service in practice.** Code does
  what it needs to — `SENSOR_DELAY_NORMAL`, 30 s notification update
  throttle, coroutine scope scoped to the service — but actual
  measured draw is absent from the repo.
- **Intent of `PlaceholderScreen` in `MainActivity.kt`.** A
  `@Composable fun PlaceholderScreen(...)` is defined but referenced
  by no NavHost route. Either dead code or a reserved hook —
  unknowable from source.
