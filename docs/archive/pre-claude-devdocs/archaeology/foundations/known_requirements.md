# Known Requirements — Code-Inferred Foundations

*Archaeology Phase 6 — requirements and constraints inferred from
implementation, not from plan docs or marketing. Every requirement is a
behaviour or invariant the code *already* upholds, with the file(s) that
make it so. Anything the code alone can't reveal is under "Explicit
unknowns" at the end. Companion docs: `project_description.md`,
`philosophy.md`.*

> These are what the code treats as non-negotiable today. Some are
> product decisions (Steps invariants); some are platform facts (Android
> 14+); some are consequences of other choices (offline → no network
> client). If a future change violates one of these, the behaviour
> shipping today will regress.

---

## 1. Platform requirements (inferred from build + manifest)

### Android 14+ only
`app/build.gradle.kts` sets `minSdk = 34` and `targetSdk = compileSdk =
36`. The foreground service uses
`foregroundServiceType="health"` (`AndroidManifest.xml` + `ServiceInfo.
FOREGROUND_SERVICE_TYPE_HEALTH` in `StepCounterService.onCreate`),
which requires Android 14.

### Kotlin 2.3.0 targeting JVM 17
Plugins declared via version catalog; `compileOptions` pin
`sourceCompatibility = VERSION_17` and `targetCompatibility = VERSION_17`
(`app/build.gradle.kts`). Annotation processing is **KSP only** —
`kapt` is explicitly forbidden (`CONSTRAINTS.md`, no `kapt` dependencies
in `gradle/libs.versions.toml`).

### Single Gradle module
`settings.gradle.kts` declares `include(":app")` and
`repositoriesMode = FAIL_ON_PROJECT_REPOS`. No multi-module split; no
transitive allowance for repos beyond what `dependencyResolutionManagement`
specifies.

### Single application package
`applicationId = "com.whitefang.stepsofbabylon"`. No flavours, no
BuildConfig fields, no per-variant code. One build == one configuration.

### Architectures: whatever SQLCipher supports
The app depends on `net.zetetic:sqlcipher-android:4.13.0` and loads it
at startup (`System.loadLibrary("sqlcipher")` in
`StepsOfBabylonApp.onCreate`). The effective architecture allow-list is
whatever that AAR provides (arm64-v8a / armeabi-v7a / x86_64 /
x86). The code does not restrict architectures in
`ndk.abiFilters`.

### Step-counting hardware implicitly required
`AndroidManifest.xml` declares `uses-permission ACTIVITY_RECOGNITION`
but **does not** declare
`<uses-feature android:name="android.hardware.sensor.stepcounter" android:required="true"/>`.
Runtime code looks up the sensor with
`sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)`
(`StepCounterService.initDayStartCounter`). Consequence: the app
installs on devices without a step counter and will degrade at runtime
rather than fail install.

---

## 2. Runtime and reliability requirements

### Step counting must work when the app is backgrounded, killed, or after reboot
Evidence (multiple redundant paths):

- **Foreground service** (`service/StepCounterService.kt`) returns
  `START_STICKY` from `onStartCommand`. Declared with
  `android:foregroundServiceType="health"` and started via
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH`.
- **Boot receiver** (`service/BootReceiver.kt`) listens for
  `android.intent.action.BOOT_COMPLETED` and restarts the service.
  `RECEIVE_BOOT_COMPLETED` permission declared.
- **Periodic worker** (`service/StepSyncWorker.kt`) every 15 min,
  scheduled from `StepsOfBabylonApp.onCreate` via
  `StepSyncScheduler`.
- **Service↔worker heartbeat** (`data/sensor/StepIngestionPreferences.kt`,
  2-minute threshold) lets the worker skip sensor catch-up while the
  service is alive, preventing double-credit.
- **Day-start counter bootstrap** (`StepCounterService.initDayStartCounter`)
  registers a one-shot sensor listener at service start so
  `StepSyncWorker` has a valid baseline if the service later dies mid-day.

### Game loop: 60 updates per second, fixed timestep, deterministic at 1× / 2× / 4×
`GameLoopThread.TICK_NS = 16_666_667L` (= 1e9 / 60). Accumulator is
scaled by `speedMultiplier: Float`, *not* `dt` — so physics step size
stays identical at every speed (`presentation/battle/GameLoopThread.kt`).
The thread yields via `sleep(sleepMs)` when ahead of the tick budget.

### Game loop must not block on IO
`GameEngine.onStepReward: ((Long) -> Unit)?` is called on the game
thread per kill. The callback's contract (documented in-code and
enforced by convention) is "hop off the loop". `BattleViewModel`
wraps every touch in `viewModelScope.launch { ... }` to satisfy this
(`BattleViewModel.wireStepRewardCallback`). Same rule covers
`onRoundEnd`, `AwardBattleSteps` invocation, etc.

### Notification cadence is bounded
- Persistent step notification: **30 s** throttle in
  `StepNotificationManager.updateNotification`.
- Widget updates: **60 s** throttle in
  `service/WidgetUpdateHelper.kt` plus the framework's own 30 min
  `updatePeriodMillis` floor (`res/xml/step_widget_info.xml`).
- `StepSyncWorker` periodicity: 15 min (Android WorkManager floor).

### Room writes must not produce negative currencies
`PlayerProfileDao` uses SQL like
`UPDATE player_profile SET currentStepBalance = MAX(0,
currentStepBalance + :delta) WHERE id = 1`. Non-negative clamp is a
required invariant (R10 currency guards, `presentation/ux/CurrencyGuardTest.kt`).

### Game-state writes must be atomic at the DAO level
Every currency mutation is a single SQL `UPDATE` on the single-row
`player_profile`. Read-modify-write at the repository level is
forbidden (`domain/repository/PlayerRepository.kt` exposes
`addSteps(delta)` / `spendSteps(delta)`, not set-balance).

### Schema upgrades must be explicit; downgrades may be destructive
`di/DatabaseModule.kt` wires
`.addMigrations(*AppMigrations.ALL)
 .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`.
`fallbackToDestructiveMigration()` (upgrades) was deliberately
removed (R2-06). Required follow-up on any `@Database(version)` bump:
write a `Migration` object in
`data/local/Migrations.kt`, register it in `AppMigrations.ALL`, commit
the newly-generated `app/schemas/<n>.json`.

---

## 3. Privacy, security, and data-handling requirements

### All durable game state is encrypted at rest
Room wraps the SQLite file with `SupportOpenHelperFactory(passphrase)`
where `passphrase` is generated by `DatabaseKeyManager.getPassphrase`
(`data/local/DatabaseKeyManager.kt`): 32 `SecureRandom` bytes,
encrypted with an Android Keystore AES-256-GCM key aliased
`steps_of_babylon_db_key`, blob stored in
`SharedPreferences("db_key_prefs")`. Native library
`libsqlcipher.so` is loaded at app start
(`StepsOfBabylonApp.onCreate`).

### The encryption key never leaves the device
Generated at first launch; never transmitted; never included in the
APK. On Keystore decrypt failure the encrypted blob is wiped and a
fresh passphrase is generated (known gap: the encrypted DB file is
*not* wiped alongside, so the new passphrase can't open the old DB;
see `missing_concepts_list.md` §2).

### No data leaves the device
- `network_security_config.xml` sets
  `cleartextTrafficPermitted="false"` at base config. Referenced via
  `android:networkSecurityConfig="@xml/network_security_config"` in
  the manifest.
- No HTTP / gRPC / WebSocket client is in `gradle/libs.versions.toml`
  (no Retrofit, OkHttp, Ktor, etc.).
- No analytics / crash-reporting SDK (no Firebase, Crashlytics,
  Sentry, Bugsnag, Datadog, etc.).
- `allowBackup="false"` in `AndroidManifest.xml` disables Android
  Auto Backup (R05).

### Health Connect data is read-only
Manifest declares `android.permission.health.READ_STEPS` and
`android.permission.health.READ_EXERCISE` — no `WRITE_*` permissions.
Consistent across `data/healthconnect/` — no writer class exists.

### Health Connect permission must be revocable through a visible rationale
`AndroidManifest.xml` declares
`HealthConnectPermissionActivity` with filter
`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` plus an
`<activity-alias>` (`ViewPermissionUsageActivity`) with
`android.intent.action.VIEW_PERMISSION_USAGE` +
`android.intent.category.HEALTH_PERMISSIONS` + permission
`START_VIEW_PERMISSION_USAGE`. The activity itself shows the scrollable
privacy policy (`presentation/HealthConnectPermissionActivity.kt`).

### Release builds are obfuscated
`app/build.gradle.kts` release block: `isMinifyEnabled = true`,
`isShrinkResources = true`, `proguardFiles(..., "proguard-rules.pro")`.
Specific keep rules cover Room entity/DAO reflection, Hilt internals,
WorkManager's `ListenableWorker` instantiation, SQLCipher JNI, Health
Connect SDK reflection, `SensorEventListener` callbacks invoked from
framework code, domain enums whose `.name` is persisted to Room, and
`org.json.**` (used by `Converters`).

### Release signing is opt-in, local, and gitignored
`if (keystorePropertiesFile.exists()) { ... }` in
`app/build.gradle.kts`. `.gitignore` excludes
`keystore.properties`, `*.jks`, `*.keystore`. Debug builds do not need
a keystore.

### No runtime secrets in the APK
No API keys, no analytics tokens, no remote-config secrets —
so nothing to inject at build time. `BuildConfig` fields exist only
at default values (applicationId, versionName) — no `buildConfigField`
calls are present.

---

## 4. Anti-cheat requirements (hard, enforced in code)

### Rate limit: 200 steps/min (250 burst)
`data/sensor/StepRateLimiter.kt`. Any excess above burst is discarded
and counted in `AntiCheatPreferences.incrementRateRejected`.

### Velocity analysis penalises unnatural patterns
`data/sensor/StepVelocityAnalyzer.kt` returns a multiplier in {1.0,
0.5, 0.0} based on a 15-minute rolling window. Shaker patterns
(coefficient of variation < 0.05 over 10 minutes) and instant jumps
(idle → spike) trigger partial or full credit loss.

### Daily walking ceiling: 50 000 steps
`data/sensor/DailyStepManager.DAILY_CEILING = 50_000L`. Enforced
uniformly for sensor steps and activity-minute conversions.

### Battle-step cap: 2 000 steps/day, separate bucket
`domain/usecase/AwardBattleSteps.DAILY_BATTLE_STEP_CAP = 2_000L`.
Tracked via `DailyStepRecordEntity.battleStepsEarned`; **not additive**
with the 50 k walking ceiling. Flat per-enemy-type; not multiplied by
any upgrade or buff (ADR-0003, `CONSTRAINTS.md`).

### Health Connect cross-validation: 4-level graduated response
`data/healthconnect/StepCrossValidator.kt`:
Level 0 (0 offences) → escrow, release after 3 syncs.
Level 1 (1–2 offences) → escrow, discard after 2 syncs.
Level 2 (3–5 offences) → cap at HC value.
Level 3 (6+ offences) → cap at HC minus 10 %.
Offences decay after 7 quiet days
(`AntiCheatPreferences.decayCvOffenses`).
Discrepancy threshold: **20 %** (`DISCREPANCY_THRESHOLD = 0.20`).

### Activity-minute validation is mandatory for HC exercise sessions
`ActivityMinuteValidator` drops sessions < 2 min, truncates > 4 hr,
caps at 5 distinct exercise types per day. Rejections counted in
`AntiCheatPreferences`.

### Per-minute overlap deduction prevents double-credit
`DailyStepManager.stepsPerMinute: MutableMap<Long, Long>` (keyed by
epoch minute, capped at 1 440 entries) lets
`ActivityMinuteConverter` subtract sensor-credited steps from the
minute's exercise-minute conversion — if a user walks on a treadmill
with the phone on their arm, the minute isn't counted twice.

### `battle_steps` bucket is for battle only; Steps cannot be minted outside these gates
Inferred from "no code path calls `playerRepository.addSteps` other
than `DailyStepManager`, `StepCrossValidator` (restore only), and
`AwardBattleSteps`". The gate is convention + grep-able; no compile
check.

---

## 5. Offline requirements

### No outbound network in v1.0
No HTTP client library is included. `cleartextTrafficPermitted="false"`
further blocks any accidentally-added cleartext call. Any addition
violates the offline-first contract.

### Step counting works with Health Connect unavailable
`HealthConnectClientWrapper.isAvailable()` must return true before any
HC call. `getClient()` can return `null`. When unavailable, the app
drops silently back to sensor-only crediting — no escrow, no HC
gap-fill, no activity-minute conversion.

### No cloud save / cross-device sync
`allowBackup="false"` disables Auto Backup. No GDS (Google Drive /
Play Games Save) integration. Consequence: re-install or device
change resets progress (accepted trade-off per R05).

### Ticker semantics tolerate offline
Daily rollover detection is date-string comparison
(`todayDate(): String = LocalDate.now().format(DATE_FMT)` in
`DailyStepManager`, and `DateTimeFormatter.ISO_LOCAL_DATE`) — no
network time. Weekly challenge and daily login use the same local
date. No NTP dependency.

---

## 6. Latency and performance budgets (inferred)

### Battle render budget: ≤ 16.67 ms per frame
`GameLoopThread.TICK_NS = 16_666_667L`. The render path allocates no
particles at steady state (`ParticlePool` pre-allocates 200 instances);
`ReducedMotionCheck` (`ANIMATOR_DURATION_SCALE = 0`) drops
non-essential effects. Sound effects are throttled
(`SoundManager.shoot()` rate-limits).

### UI ↔ game-thread latency: 200 ms
`BattleViewModel` polls `GameEngine` state every 200 ms
(`while(true) { delay(200); snapshot() }`). Not a hard requirement —
a coarser polling rate is the acceptable cost of avoiding a reactive
bridge.

### Notification update latency: 30 s throttle
`StepNotificationManager` will not fire more than once per 30 s. UX
accepts staleness up to that window.

### Widget update latency: 60 s throttle + 30 min framework floor
`WidgetUpdateHelper` rate-limits writes; framework's
`updatePeriodMillis` enforces a hard 30-min minimum even when we
don't push.

### Step-credit latency: near-realtime (within a sensor delivery interval)
`SensorManager` is registered with `SENSOR_DELAY_NORMAL`
(`StepSensorDataSource`). Each delta is processed synchronously
through `DailyStepManager.recordSteps` before the coroutine yields
for the next one. No internal batching beyond the rate limiter's
window.

### Background catch-up latency: 15 minutes
`StepSyncWorker` periodic work; Android's minimum for
`PeriodicWorkRequest`.

### `AwardBattleSteps` latency budget: one DAO round trip per kill
Acceptable because (a) the game thread does not wait, (b) Room is
local, (c) the call pair (`addSteps` + `incrementBattleSteps`) is
two atomic UPDATEs with no transaction wrapping them today (a known
correctness gap, tracked in `5_things_or_not.md` §2).

---

## 7. Scalability requirements

### Single user, single device, single process
The data model is single-row on `PlayerProfileEntity` (`@PrimaryKey val
id: Int = 1`) and `DropGeneratorState`. No tenancy, no account table,
no session table. Adding multi-user would be a substantial data-model
change.

### No horizontal scale dimension
There is no backend to scale; the app is a self-contained process.
"Scale" is vertical only — make the one device keep working while
the user accumulates years of step history.

### History retention: all of it, locally
`DailyStepRecordEntity` has one row per calendar day (keyed on date
string). No retention policy — the only pruning is via
`WalkingEncounterRepository.enforceInboxCap(GenerateSupplyDrop.MAX_INBOX)`
= 10 unclaimed drops max. Walking milestones go up to 5 million Steps;
daily records would accumulate without bound for a lifelong user. No
code-level concern today but worth naming.

---

## 8. Reproducibility and testability requirements

### Game rules are pure-Kotlin
`domain/` has zero Android imports. Unit tests run on a plain JVM.
Enforced by convention; violated files would fail compilation in a
hypothetical future `:domain` Gradle module but today are caught by
review.

### Stochastic logic is seedable
Three use cases take `private val random: Random = Random`:
`CalculateDamage`, `OpenCardPack`, `GenerateSupplyDrop`. Tests pass
`Random(seed)` and assert exact outcomes
(`CalculateDamageTest`, `OpenCardPackTest`, `GenerateSupplyDropTest`).

### Daily content is deterministic across devices
`GenerateDailyMissions` uses `Random(todayDate.hashCode())`. Every
device on the same calendar day gets the same three missions — this
is a product requirement (social equity) as much as a reproducibility
one.

### Time-dependent logic is parameterisable
Default-param pattern: `today: String = LocalDate.now().toString()`
(`AwardBattleSteps`, `TrackDailyLogin`, `StartResearch`) or
`now: Long = System.currentTimeMillis()`. Tests pass fixed values.
No global `Clock` exists (see Phase 4 proposal in
`5_things_or_not.md` §1).

### Tests are runnable without an emulator
412 JVM-only tests via JUnit 5 (`unitTests.all { it.useJUnitPlatform() }`
in `testOptions`). Robolectric covers the narrow slice of code that
needs the Android framework (widget, deep-link parsing, Room schema
round-trip, escrow integration). No `androidTest/` suite
(intentional for v1.0).

### Schema is versioned and exportable
`room { schemaDirectory("$projectDir/schemas") }` in
`app/build.gradle.kts`. `exportSchema = true` on
`@Database(version = 8, exportSchema = true, ...)`. Every version bump
produces a JSON snapshot; those files are committed
(`app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/{1..8}.json`).

### Build must be reproducible from the version catalog
`gradle/libs.versions.toml` is the single source of truth. Hardcoding
versions is forbidden (`CONSTRAINTS.md`). `FAIL_ON_PROJECT_REPOS` in
`settings.gradle.kts` prevents subproject repos from drifting.

---

## 9. Compatibility requirements

### Backwards-compatible DB migrations required on every version bump
See "Schema upgrades must be explicit" (§2). `MIGRATION_7_8` is the
only migration written so far; the contract is that future bumps add
new `Migration` objects to `AppMigrations.ALL`.

### Enum names must not obfuscate
ProGuard: `-keep enum com.whitefang.stepsofbabylon.domain.model.** { *; }`.
Row values store enum `.name` strings; obfuscating them would
invalidate saves. (See `philosophy.md` §1.)

### TypeConverter JSON format is forward-stable
`Converters.fromIntIntMap` / `fromStringIntMap` use `org.json.JSONObject`.
Any change to the serialised shape becomes a migration concern.

### Keystore passphrase scheme is backward-compatible by construction
`DatabaseKeyManager` stores a base64 blob and IV in prefs. Changing
the algorithm or key size would require a recovery path because the
current code wipes on decrypt failure rather than attempting to
re-wrap.

---

## 10. Security requirements (already enforced)

Summarised consolidation of §3, listed as requirements:

- **Encrypted database** (SQLCipher + Keystore).
- **Obfuscated release** (R8 + resource shrinking + keep rules).
- **No cleartext network** (network security config base-deny).
- **No backup off device** (`allowBackup="false"`).
- **Keystore passphrase never persisted in plaintext**.
- **Read-only Health Connect permissions**.
- **Runtime permission prompts before use** (not auto-granted even if
  manifest-declared).
- **Foreground service has explicit health type** (Android 14
  compliance).
- **Widget provider is `android:exported="true"` only because
  `AppWidgetManager` requires it** — no other exported components
  except `MainActivity` (launcher).
- **Health Connect rationale activities are public-exported per
  SDK requirement** (`ACTION_SHOW_PERMISSIONS_RATIONALE` +
  `VIEW_PERMISSION_USAGE`).

---

## 11. Observability requirements (deliberately minimal)

### Logging is local, unstructured, stdout-class only
`android.util.Log` is the only logger in the codebase. No Timber, no
SLF4J, no log shipping. Production crash data reaches the developer
via Play Console's ANR/crash reports, not via in-app pipes.

### Anti-cheat telemetry stays on device
`AntiCheatPreferences` holds counters (rate-rejected, velocity-
penalised, activity-rejected, CV offences) in a SharedPreferences
file. No export path. (User-visible surfacing is a known gap — see
`5_things_or_not.md` §5.)

### No performance monitoring
No FrameMetrics collection, no custom trace sections, no
`androidx.metrics.performance.JankStats` integration. Performance is
verified manually per the release checklist.

### Implication
If a user reports that step counts are suddenly low, the *only* data
to diagnose with is (a) their description, (b) Play Console
crash/ANR, and (c) whatever they can screenshot of the Stats screen.
Deliberate — the project traded observability for privacy (see
`philosophy.md` §4).

---

## 12. Deployment requirements

### Artefact: one AAB per release
`./gradlew bundleRelease` produces
`app/build/outputs/bundle/release/app-release.aab`. Also supports
`./gradlew assembleRelease` for APK. Release signing is opt-in via
`keystore.properties`.

### Distribution: Google Play Store only
No F-Droid metadata, no sideload mechanism, no auto-update service.
`docs/release/release-checklist.md` + `docs/plans/plan-31-play-console.md`
formalise the process.

### Build gate (manual)
`./run-gradle.sh test` (all 412 green) + `./run-gradle.sh assembleRelease`
(succeeds under R8). No CI. `run-gradle.sh` is gitignored; recreation
instructions in `README.md`.

### `versionCode` must be monotonic per Google Play rules
App is currently `versionCode 1 / versionName "1.0.0"`. Play Console
rejects uploads with a lower `versionCode` than the currently-active
track.

### Schema JSONs must ship with every release
`app/schemas/*.json` are committed. They are not bundled in the APK
but serve as reference material for writing migrations.

---

## 13. Integration requirements

### Android TYPE_STEP_COUNTER sensor
Must be present at runtime for sensor-source crediting. Code handles
absence by skipping the day-start baseline bootstrap
(`StepCounterService.initDayStartCounter` returns early if sensor is
null), but step counting then degrades to Health Connect only.

### Health Connect (optional, strongly recommended)
`androidx.health.connect:connect-client:1.2.0-alpha02`. Feature-detected
at runtime via `HealthConnectClient.SDK_AVAILABLE`. Required for:
- Cross-validation / anti-cheat escrow.
- Gap-filling when service dies.
- Activity Minute Parity (non-walking exercise → Steps).

Absent HC, all three degrade: no escrow means no fine-grained cheat
response, no gap-fill means service death lowers credit, no Activity
Minute Parity means non-walkers can't progress.

### Android Keystore
Hard requirement for DB encryption. No fallback — if Keystore ops
fail the app wipes the passphrase blob and generates a fresh key,
which (today) leaves the old DB unreadable (known gap).

### WorkManager
Supplied by Android (Jetpack). Configured via
`Configuration.Provider` on `StepsOfBabylonApp` with
`HiltWorkerFactory`; default initialiser disabled in manifest
(`InitializationProvider` with `tools:node="remove"`). Required for
step-sync periodic work.

### AppWidgetManager
Supplied by Android. Required for the 2×2 step counter widget.
`StepWidgetProvider` is declared in manifest as an
`<receiver android:exported="true">`.

### Google Play Billing (deferred — stub in place today)
Contract: `domain/repository/BillingManager.kt`. Today served by
`StubBillingManager`. Future real integration swaps the `@Binds` in
`di/BillingModule.kt`. Real integration requires Play Console IAP SKU
registration (manual, not code-captured).

### Google AdMob or equivalent (deferred — stub in place today)
Contract: `domain/repository/RewardAdManager.kt`. Today served by
`StubRewardAdManager`. Future real integration swaps the `@Binds` in
`di/AdModule.kt`.

---

## 14. Privacy-by-default requirements

### No analytics SDK permitted by default
Adding one requires updating `gradle/libs.versions.toml`, adding a
manifest entry, and (if network-using) relaxing
`network_security_config.xml`. Each of those is a reviewer red-flag
today.

### No PII leaves the device
Nothing in code reads or transmits user identifiers. The
`PlayerProfileEntity` doesn't even have a player name field.

### Per-user uninstall is the data-deletion path
With `allowBackup="false"` and no cloud save, uninstall removes all
data. No in-app "delete my data" flow is wired — this is an implicit
GDPR-style answer that isn't documented in app (see
`missing_concepts_list.md` §3).

### Health Connect access is always explicit
Every HC call is gated by a runtime permission check via the
`PermissionController.createRequestPermissionResultContract()`
flow in `MainActivity`. HC rationale activity is shown on first grant
and on permission revocation.

---

## 15. Concurrency and threading requirements

### `StepSyncWorker` and `StepCounterService` must not double-credit
Enforced by the 2-minute heartbeat protocol (see §2). Worker reads
`StepIngestionPreferences.isServiceAlive(now)`; if true, skips sensor
work.

### `DailyStepManager` is the single synchronisation point for step credit
`@Singleton`, so Hilt gives every caller the same instance.
`recordSteps` and `recordActivityMinutes` are `suspend` functions;
they are not individually reentrant-safe, but the combined call graph
(one service + one worker, never racing thanks to the heartbeat) keeps
them correct in practice.

### Room writes serialise at the DAO level
Room's internal threading handles serialisation of `suspend` DAO calls.
Code does not wrap DAO calls in `withContext(IO)`.

### Game loop has no shared mutable collection with the UI thread
`GameEngine.entities: MutableList<Entity>` is read and written only
on the game thread. New entities queue through `pendingAdd` and merge
in at the start of the next `update`. The ViewModel reads only
`@Volatile` scalar fields.

---

## 16. Compliance / legal constraints (code-visible)

### Play Store listing requirements (partially in repo)
`docs/release/play-store-listing.md` enumerates screenshots (phone +
7-inch tablet), feature graphic (1024×500), short/full descriptions,
privacy policy URL, content rating, target audience. Nothing in the
repo today provides the images; `strings.xml` only has `app_name`.

### Privacy policy copy exists, URL hosting does not
`docs/release/privacy-policy.md` is written and references a contact
email. Google Play requires an https-reachable URL for it; that
hosting is a manual Plan 31 step.

### Health Connect publisher requirements
Manifest declares the HC rationale activity and the Android 14
`ViewPermissionUsageActivity` alias. These are the code-level HC
compliance commitments (Google will review them before granting HC
permissions to the Play Store listing).

---

## 17. What the code *doesn't* require (explicit non-requirements)

Listed so contributors don't assume otherwise:

- No account system (no login, no registration, no email, no phone
  verification).
- No multiplayer, no social graph, no leaderboards.
- No server-side storage of any kind.
- No push-notification server (all notifications are locally
  generated by the app).
- No remote config or kill-switch.
- No A/B testing framework.
- No scheduled/serverless back-end for off-device processing.
- No content-download mechanism (no OBB, no dynamic feature modules,
  no on-the-fly asset fetching).
- No localisation beyond English.
- No accessibility audit-level compliance (baseline content
  descriptions only; Plan 24 deferred).
- No CI / automated build gate.
- No device-lab / emulator-matrix testing.
- No instrumented (`androidTest`) suite.

---

## 18. Explicit unknowns (things code cannot answer)

The code defines behaviour but cannot tell us:

- **Which specific Android hardware we target.** minSdk 34 + no
  `<uses-feature>` filter means "any Android 14+ device". Play
  Console may add feature filters at upload time; none are in the
  repo.
- **What the acceptable battery-drain envelope is.** The release
  checklist says "< 5 % per day for step counting" but there are no
  in-code assertions, no automated battery-profile runs.
- **Which real billing SDK will integrate** (Google Play Billing
  Library version, subscription grace-period handling, promo-code
  plan). The stub papers this over; the stub's suspend + simulated
  latency shape is what the real integration must match.
- **Which real ad SDK will integrate** (AdMob? AdMob with mediation?
  bespoke waterfall?). Stub is single-result, always-reward.
- **Final audio assets.** Placeholder `.ogg` files; no code check
  distinguishes them.
- **Final cosmetic visual pipeline.** Data layer + store UI exist;
  renderer hook does not. "How a cosmetic changes the screen" is
  completely undefined in code today.
- **Post-release support policy.** No in-app "send feedback" flow,
  no "report bug", no "contact support". The only contact surface
  is the email in the privacy-policy markdown.
- **Cross-device migration answer.** `allowBackup="false"` precludes
  Auto Backup; no cloud save exists; no "export progress" UI
  exists. Whether this is permanent or a v2.0 gap is not settled in
  code.
- **Localisation target list.** English-only today; no `values-*`
  directories.
- **Whether the intentional absence of `PlaceholderScreen` hookup is
  dead code or a reserved hook.** The composable exists; no route
  references it; only a human can say whether to delete or retain.
- **Monetisation elasticity.** The hardcoded USD price strings in
  `BillingProduct` are display-only under real Play Billing, but the
  code alone can't tell us the intended price list per region.
- **Retention policy for daily step records / historical data.**
  There isn't one. Whether that's a product decision or an
  oversight is not captured.
- **What "done" means for accessibility (Plan 24).** Code contains
  scattered `contentDescription` values and `ReducedMotionCheck`
  consumption — no checklist of screens covered.
