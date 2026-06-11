# Deployment & Infrastructure — Intro for New Engineers

*Archaeology Phase 2. Written from source (build files, manifests,
scripts, configs, resources), not from prior docs. For the architecture
side, read `intro2codebase.md`.*

## 1. What "deployment" means for this project

There is exactly **one deliverable**: an Android application bundle or
APK installed on the user's phone from the Google Play Store. No backend
is built or deployed from this repo. No containers, no cloud
infrastructure, no serverless, no CLIs, no data pipelines.

That said, several moving parts *do* get shipped inside the app package,
and a handful of runtime mechanisms update themselves after install.
This doc lists them explicitly.

## 2. Build system at a glance

Single Gradle project. Exactly one module: `:app` (from
`settings.gradle.kts`). The root `build.gradle.kts` only declares plugins
with `apply false`; all real build logic is in `app/build.gradle.kts`.

```
.
├── build.gradle.kts            ← plugins apply false (root aggregator)
├── settings.gradle.kts         ← include(":app"), repositoriesMode = FAIL_ON_PROJECT_REPOS
├── gradle/
│   ├── libs.versions.toml      ← single source of truth for all deps + plugin versions
│   └── wrapper/                ← gradle-wrapper.jar + .properties
├── gradle.properties           ← JVM heap 2GB, console=plain, AndroidX on
├── gradlew / gradlew.bat       ← Gradle wrapper scripts (committed)
├── local.properties            ← (gitignored) sdk.dir only
├── keystore.properties         ← (gitignored) optional release signing
└── app/
    ├── build.gradle.kts        ← applicationId, SDKs, plugins, deps, signing, tests
    ├── proguard-rules.pro      ← R8 keep rules for release builds
    └── schemas/                ← exported Room schemas (1.json..8.json)
```

Key versions (from `gradle/libs.versions.toml` + `app/build.gradle.kts`):

| Thing | Value | Notes |
|---|---|---|
| Kotlin | 2.3.0 | |
| Android Gradle Plugin | 9.0.1 | |
| KSP | 2.3.6 | No kapt anywhere |
| JVM target | 17 | `sourceCompatibility = VERSION_17`, `targetCompatibility = VERSION_17` |
| compileSdk / targetSdk | 36 | |
| minSdk | 34 (Android 14) | |
| `applicationId` | `com.whitefang.stepsofbabylon` | |
| `versionCode` / `versionName` | `1` / `"1.0.0"` | |
| Gradle wrapper | 9.3.1 | |
| Compose BOM | 2026.02.00 | Material3 |
| Hilt | 2.59.2 | |
| Room | 2.8.4 | `room { schemaDirectory("$projectDir/schemas") }` |
| WorkManager | 2.11.0 | |
| Health Connect | 1.2.0-alpha02 | |
| SQLCipher | 4.13.0 | `net.zetetic:sqlcipher-android` |
| JUnit 5 | 5.11.4 | `unitTests.all { it.useJUnitPlatform() }` |
| Robolectric | 4.14.1 | For widget / Room schema / deep-link JVM tests |

Hard rule: **never hardcode a version outside the catalog**. Even `compose-ui` and the other BOM-governed Compose libs are resolved through the catalog.

## 3. How a developer builds and runs locally

Prerequisites: JDK 17, Android SDK 36, an emulator or device on API 34+.
`local.properties` must exist with `sdk.dir=<absolute path>` (Android
Studio creates this; it is gitignored).

```bash
# Debug APK
./gradlew assembleDebug
# Release APK (unsigned unless keystore.properties exists)
./gradlew assembleRelease
# AAB for Play Store
./gradlew bundleRelease
# JVM unit tests (~412 tests as of last count)
./gradlew test
# Or: ./gradlew testDebugUnitTest
# Lint
./gradlew lint
# Clean
./gradlew clean
```

### `run-gradle.sh` — the non-TTY wrapper

If stdout is not a terminal, Gradle buffers output and appears to hang.
`run-gradle.sh` (in the repo root, gitignored — recreate from the README
snippet if missing) backgrounds Gradle, captures output to
`/tmp/gradle_out.txt`, waits, then flushes. Use this from Kiro CLI or
any CI that doesn't allocate a PTY:

```bash
./run-gradle.sh assembleDebug
./run-gradle.sh test
```

The script is trivially short:

```bash
#!/bin/bash
cd "$(dirname "$0")"
./gradlew "$@" > /tmp/gradle_out.txt 2>&1 &
wait $!; EXIT_CODE=$?; cat /tmp/gradle_out.txt; exit $EXIT_CODE
```

## 4. Continuous Integration

**There is no CI configured in this repo.** No `.github/workflows`, no
`.circleci`, no `.gitlab-ci.yml`, no Jenkinsfile, no fastlane, no
Dockerfile. All builds and tests today run on developer machines.

Practical implications:

- Any merged PR has only been verified on the submitter's laptop. The
  project memory (`docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`) is
  the de-facto test log. Recent entries (e.g. ADR-0003) quote
  "412 JVM tests, 0 failures" after manual local runs.
- Release signing is local-only (see §7).
- First priority for Plan 31 (Play Store publication) is enrolling in
  Firebase Test Lab's automated pre-launch report — today this is
  only documented, not wired.

If you are asked to "set up CI": the minimal pipeline should run
`./gradlew lint test assembleDebug` on push and PRs. A full release
pipeline would need access to `keystore.properties` (secret) and the
`.jks` file (artifact).

## 5. Build types and flavours

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        if (keystorePropertiesFile.exists()) {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

There is **no explicit `debug` block** — defaults apply (no minify, debug
keystore). There are **no product flavours**. There is **no
`buildConfigField` or `BuildConfig` reference** in Kotlin source. The
app is one configuration.

### R8 / ProGuard

Release builds run with `isMinifyEnabled = true` + `isShrinkResources = true`. Keep rules live in `app/proguard-rules.pro`:

- `* extends androidx.room.RoomDatabase`, `@Entity`, `@Dao` — Room needs reflection against entity/DAO classes.
- `dagger.hilt.**`, `* extends ViewComponentManager` — Hilt.
- `* extends androidx.work.ListenableWorker` — WorkManager needs to
  instantiate workers by class name.
- `net.zetetic.**` — SQLCipher JNI.
- `androidx.health.connect.**` — SDK uses reflection internally.
- `* implements android.hardware.SensorEventListener` (`onSensorChanged`,
  `onAccuracyChanged`) — called by framework reflection.
- `enum com.whitefang.stepsofbabylon.domain.model.**` — enum **names**
  are stored as Strings in Room (`UpgradeType.name` etc.); obfuscating
  them would break on upgrade.
- `org.json.**` — defensive keep since `Converters` uses `JSONObject`.

If you add a new framework that uses reflection (Moshi, Retrofit, Kotlin
serialization, etc.), add a keep rule alongside these and verify with
`./gradlew assembleRelease` that no required class is stripped.

## 6. Packaging: what ships inside the APK/AAB

The built artifact contains:

| Bundled asset | Path in source | How updated |
|---|---|---|
| Compiled Kotlin + Compose | `app/src/main/java/**` | On build |
| Jetpack Compose UI | generated from Kotlin | On build |
| Room schemas (reference, not runtime) | `app/schemas/*.json` (committed; not bundled into APK) | Committed on every DB version bump |
| Sound effects | `app/src/main/res/raw/sfx_*.ogg` (7 files, 2–17 KB each) | Replace file, rebuild |
| Network security config | `app/src/main/res/xml/network_security_config.xml` | `cleartextTrafficPermitted="false"` — blocks HTTP |
| Widget metadata | `app/src/main/res/xml/step_widget_info.xml` | `updatePeriodMillis=1800000` (30 min) |
| Widget layout | `app/src/main/res/layout/widget_step_counter.xml` | |
| App label | `app/src/main/res/values/strings.xml` | Only `app_name` exists |
| Native: SQLCipher `.so` | from the `sqlcipher-android` AAR | Bundled automatically; `System.loadLibrary("sqlcipher")` at app start |

**Not yet present (known gaps):**

- No launcher icon resources — `res/` has `raw/`, `xml/`, `layout/`,
  `values/` but no `mipmap-*` directories. `AndroidManifest.xml` omits
  `android:icon` entirely. Plan 31 must supply these before Play Store
  upload.
- No localised strings; `strings.xml` has only `app_name`.
- No assets directory.

**Not present and not planned:**

- No ML models, data packs, or downloaded content bundles.
- No JavaScript/WebView bridge.
- No backend migrations or server-side state.

## 7. Release signing

Signing is **opt-in at build time** via a gitignored `keystore.properties`:

```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

signingConfigs {
    if (keystorePropertiesFile.exists()) {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }
}
```

If the file exists, `assembleRelease` / `bundleRelease` sign with it.
If it does not exist, the release APK is unsigned — build still succeeds.

### Generating the upload keystore (one-time)

From `docs/release/signing-guide.md`:

```bash
mkdir -p release
keytool -genkeypair -v \
  -keystore release/upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Then create `keystore.properties` in the repo root:

```properties
storeFile=release/upload-keystore.jks
storePassword=<password>
keyAlias=upload
keyPassword=<password>
```

`.gitignore` excludes: `keystore.properties`, `*.jks`, `*.keystore`.

### Play App Signing

The repo documents (but has not yet completed) enrolling in
[Google Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).
Under Play App Signing, the locally-generated keystore becomes the
**upload key** only; Google holds the real signing key. This is the
recommended path for Plan 31.

### Build outputs

- `app/build/outputs/apk/release/app-release.apk` — for direct install
- `app/build/outputs/bundle/release/app-release.aab` — for Play Store
- `app/build/outputs/apk/debug/app-debug.apk` — debug signed
- `app/schemas/<db>/<version>.json` — Room schema snapshots (generated
  by the Room Gradle plugin; commit these)

## 8. Testing pipeline

All tests run on the JVM. No instrumented tests exist today.

```bash
./gradlew test                    # all variants
./gradlew testDebugUnitTest       # debug variant only (fastest)
```

Configuration in `app/build.gradle.kts`:

```kotlin
testOptions {
    unitTests.all { it.useJUnitPlatform() }   // JUnit 5
    unitTests.isReturnDefaultValues = true    // mock android.util.Log etc.
    unitTests.isIncludeAndroidResources = true // required for Robolectric
}
```

Test dependencies (via catalog):

- `junit-jupiter:5.11.4` + `junit-platform-launcher` at runtime
- `kotlinx-coroutines-test:1.10.1` — `StandardTestDispatcher`, `runTest`
- `mockito-kotlin:5.4.0` — for mocking Android framework classes in
  anti-cheat tests
- `robolectric:4.14.1` + `androidx.test:core:1.6.1` — widget, deep-link,
  Room schema, escrow-lifecycle integration tests
- `room-testing:2.8.4` — `MigrationTestHelper` support (not yet used in
  tests, reserved for future migration tests)

Test layout mirrors `main/` under `app/src/test/java/...`. Fakes for
every repository and the Room DAOs that need them live in
`test/fakes/`. See `.kiro/steering/source-files.md` for the full test
file index.

### Release verification

The manual pre-release checklist (`docs/release/release-checklist.md`)
expects:

- All JVM tests green
- Release APK installs on API 34 and API 36
- Step counting works in background
- HC permissions and reading work
- No ANRs or crashes in a 30-minute play session
- R8 has not broken any feature (all screens load, battle runs,
  notifications fire)
- Widget renders correctly
- Battery usage < 5% per day for step counting

None of these are automated today.

## 9. Runtime deployment: how new code reaches a user's phone

The user updates through the Play Store. There is no in-app update, no
DLC, no dynamic feature modules. Everything visible to the player after
install is either in the APK or in Room / SharedPreferences on disk.

Runtime mechanisms that refresh themselves:

| Mechanism | Trigger | Where |
|---|---|---|
| WorkManager periodic `StepSyncWorker` | Every 15 min (`PeriodicWorkRequestBuilder`); kept alive across reboots | `service/StepSyncScheduler.kt` scheduled once from `StepsOfBabylonApp.onCreate`; `ExistingPeriodicWorkPolicy.KEEP` |
| `StepCounterService` | `START_STICKY` foreground service | Launched from `MainActivity` (runtime permission granted) and from `BootReceiver` on `BOOT_COMPLETED` |
| Home screen widget | `updatePeriodMillis = 1_800_000` (30 min, framework-enforced minimum 30 min) + push updates via `WidgetUpdateHelper` (60 s throttled) | `res/xml/step_widget_info.xml` + `service/WidgetUpdateHelper.kt` + `StepWidgetProvider` |
| Persistent step notification | 30 s throttle after every sensor delta that credits steps | `service/StepNotificationManager.updateNotification` |
| Health Connect sync | Inside `StepSyncWorker.doWork` (gap fill + cross-validate + activity minutes) | `data/healthconnect/*` |
| Midnight day rollover | Detected by date comparison in `DailyStepManager.ensureInitialized` and in `HomeViewModel`/`MissionsViewModel`/`StatsViewModel` (ticker or lifecycle-resume refresh) | No `android.intent.action.DATE_CHANGED` listener |

## 10. Database migrations (shipped with the app)

Room schema is at **version 8** (see `app/src/main/java/.../data/local/AppDatabase.kt`).

- Schemas are exported to `app/schemas/<fqcn>/<version>.json` on every
  build (Room Gradle plugin, configured in `app/build.gradle.kts`).
- As of this writing: `1.json` through `8.json` — **commit these** on
  every version bump. They are the contract that `MigrationTestHelper`
  can use to verify future migrations.
- Explicit `Migration` objects live in
  `app/src/main/java/.../data/local/Migrations.kt`
  (`AppMigrations.ALL`). Currently only `MIGRATION_7_8` is defined
  (adds `DailyStepRecordEntity.battleStepsEarned` column per ADR-0003).
- `DatabaseModule` wires both:

  ```kotlin
  .addMigrations(*AppMigrations.ALL)
  .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
  ```

  Downgrades (only possible in dev/QA when reverting to an older
  build) wipe all tables. Upgrades **must** have an explicit
  `Migration`; the old `fallbackToDestructiveMigration()` was
  deliberately removed (R2-06) to force fail-fast if an engineer bumps
  the version without writing a migration.

### Versions earlier than 7

`docs/database-schema.md` lists the history (v1→v2 through v6→v7) — all
dev-time destructive migrations, consolidated into v7's schema. Only
v7→v8 has an explicit `Migration` object because everything before v7
predates the release build.

### Adding a new migration

1. Increment `@Database(version = ...)` in `AppDatabase.kt`.
2. Add a new column / table on the entity.
3. Write a `val MIGRATION_X_Y = object : Migration(X, Y) { ... }` in
   `Migrations.kt` with the corresponding DDL.
4. Add it to `AppMigrations.ALL`.
5. Rebuild — the Room processor will emit `schemas/<version>.json`.
6. Commit the new schema JSON.
7. Add a migration test (`room-testing`'s `MigrationTestHelper` is
   already a dependency).

## 11. Environment, permissions, secrets

### Android runtime permissions

Declared in `AndroidManifest.xml`:

- `ACTIVITY_RECOGNITION` — required; requested at launch.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH` — for
  `StepCounterService`.
- `RECEIVE_BOOT_COMPLETED` — for `BootReceiver`.
- `POST_NOTIFICATIONS` — for the persistent step counter and
  supply-drop notifications.
- `health.READ_STEPS`, `health.READ_EXERCISE` — for Health Connect.

All runtime prompts are handled in `MainActivity`'s `LaunchedEffect`
and through the Health Connect `PermissionController` contract.

### Secrets

- No API keys, no analytics tokens, no remote config — so nothing to
  inject at build time.
- The database encryption passphrase is **generated on-device** by
  `DatabaseKeyManager`: 32 random bytes via `SecureRandom`, encrypted
  with an Android Keystore AES-256-GCM key (alias
  `steps_of_babylon_db_key`), blob stored in
  `SharedPreferences("db_key_prefs")`. On decrypt failure (typical
  after a phone-to-phone restore that copies prefs but cannot copy
  keystore material) the blob is wiped and a fresh key is generated —
  effectively wiping the database since Room will no longer open it.
- The only file that *would* contain a secret is `keystore.properties`
  (release signing passwords). It is gitignored. `.jks` and
  `.keystore` files are gitignored.

### Environment templates

There are no `.env` files, no `.envrc`, no `direnv`, no
`.tool-versions`. `local.properties` is gitignored and only contains
`sdk.dir=<absolute path>`; Android Studio regenerates it.

## 12. Release process in practice (as defined by code + docs)

Per `docs/release/release-checklist.md` + `docs/plans/plan-31-play-console.md`:

1. Bump `versionCode` in `app/build.gradle.kts` (required by Play for
   every upload; `versionName` is optional but conventional).
2. Run `./run-gradle.sh test` — all JVM tests green.
3. Run `./run-gradle.sh bundleRelease` — produces a signed AAB if
   `keystore.properties` exists.
4. Test the AAB with `bundletool` (generates a universal APK) on at
   least API 34 and API 36.
5. Upload to Google Play Console internal testing track.
6. Fix anything that the Firebase Test Lab pre-launch report surfaces.
7. Promote through closed → open → production tracks.
8. Tag the release commit in git.

**Not set up yet:** IAP product configuration in Play Console, real
Billing library integration (currently `StubBillingManager`), real ad
SDK integration (currently `StubRewardAdManager`), Play App Signing
enrollment, launcher icon, store listing assets. See
`docs/plans/plan-31-play-console.md` for the explicit list.

## 13. One-page quick reference

```
Source of truth              app/src/main/java/com/whitefang/stepsofbabylon/
Version catalog              gradle/libs.versions.toml
Build config                 app/build.gradle.kts
Room schemas                 app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/*.json
R8 rules                     app/proguard-rules.pro
Manifest                     app/src/main/AndroidManifest.xml
Secrets (gitignored)         keystore.properties · *.jks · *.keystore
SDK location (gitignored)    local.properties
Build                        ./gradlew assembleDebug | assembleRelease | bundleRelease
Test                         ./gradlew test | testDebugUnitTest
Non-TTY wrapper              ./run-gradle.sh <task>  (gitignored; recreate from README)
CI                           None
Backend                      None
Delivery                     Google Play Store, user-initiated updates
Schema migrations            data/local/Migrations.kt → AppMigrations.ALL
In-app data migrations       None (state is locally-owned Room + SharedPrefs)
Scheduled work               StepSyncWorker, 15-min PeriodicWorkRequest
Foreground service           StepCounterService (health), START_STICKY
Auto-start on boot           BootReceiver → StepCounterService
Widget refresh               30 min framework + push via WidgetUpdateHelper (60 s throttle)
```
