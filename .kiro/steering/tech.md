# Tech Stack

## Core

- **Language:** Kotlin (JVM target 17)
- **Min SDK:** 34 (Android 14) / Target & Compile SDK: 36
- **Architecture:** MVVM + Clean Architecture
- **Build:** Gradle 9.3.1 with Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- **Database encryption:** SQLCipher via Android Keystore-managed passphrase

## Key Libraries & Versions

All versions managed in `gradle/libs.versions.toml`. Never hardcode versions in build files.

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.3.0 | Language |
| AGP | 9.0.1 | Android Gradle Plugin |
| KSP | 2.3.6 | Annotation processing (replaces kapt) |
| Compose BOM | 2026.02.00 | Jetpack Compose UI |
| Hilt | 2.59.2 | Dependency injection |
| Room | 2.8.4 | Local SQLite database |
| Google Play Billing | 8.3.0 | IAP via `billing-ktx`. `BillingManagerImpl` is the sole `BillingManager` binding for debug + release as of C.5 PR 3 (`StubBillingManager` deleted; `BuildConfig.USE_REAL_BILLING` removed). |
| Google Mobile Ads SDK | 25.0.0 | Reward ads via `play-services-ads`. `RewardAdManagerImpl` is the sole `RewardAdManager` binding for debug + release as of C.6 PR 3 (`StubRewardAdManager` deleted). `BuildConfig.USE_REAL_ADS` is retained only to gate the `MainActivity` UMP consent prefetch on debug emulators. |
| User Messaging Platform | 4.0.0 | GDPR/DSA consent via `user-messaging-platform`; paired with AdMob (C.6 PR 1) |
| Navigation Compose | 2.9.7 | Compose navigation |
| Lifecycle | 2.9.0 | ViewModel, StateFlow integration |
| WorkManager | 2.11.0 | Background step sync |
| JUnit 5 | 5.11.4 | Unit testing framework |
| kotlinx-coroutines-test | 1.10.1 | Coroutine test utilities |
| SQLCipher | 4.13.0 | Database encryption |
| Health Connect | 1.2.0-alpha02 | Step cross-validation, Activity Minute Parity |
| SQLite KTX | 2.4.0 | SQLite support library |
| Core KTX | 1.17.0 | Kotlin extensions for Android |
| Activity Compose | 1.12.3 | Compose Activity integration |
| Hilt Work | 1.3.0 | Hilt WorkManager + Navigation Compose integration |
| Compose Material Icons | (BOM) | Material icon set for Compose. Both `material-icons-core` (small built-in set) and `material-icons-extended` (full Material catalogue) are included; R8 shrinks unused icons in release builds. Extended set added in R4-04 for `Icons.Filled.Upgrade`; R4-05 will use `Icons.Filled.Help`. |
| Mockito Kotlin | 5.4.0 | Kotlin-friendly mocking for tests |
| Robolectric | 4.14.1 | Android framework simulation for JVM tests |
| AndroidX Test Core | 1.6.1 | Test utilities for Android components |
| WorkManager Testing | 2.11.0 | `WorkManagerTestInitHelper` for tests that exercise `WorkManager.cancelAllWork` (added by V1X-01 `DataDeletionManagerTest`). Same version as the main WorkManager dep. |
| AndroidX Test Runner | 1.6.2 | `AndroidJUnitRunner` for instrumented tests; subclassed by `HiltTestRunner` (V1X-08 Phase 1A). |
| AndroidX Test Ext JUnit | 1.2.1 | `AndroidJUnit4` runner for instrumented `@RunWith` annotation (V1X-08 Phase 1A). |
| Hilt Android Testing | 2.59.2 | `HiltTestApplication`, `@HiltAndroidTest`, `HiltAndroidRule` for instrumented Hilt DI (V1X-08 Phase 1A). Pinned to the same `hilt` version as the rest of the graph. |

## Gradle Plugins

`android.application`, `kotlin.compose`, `hilt`, `ksp`, `room` — all aliased from version catalog.

## Architecture Layers

- **presentation** — ViewModels (expose `StateFlow`), Compose screens, SurfaceView battle renderer
- **domain** — Use cases, repository interfaces, pure Kotlin models. Zero Android imports.
- **data** — Room entities, DAOs, repository implementations, sensor/Health Connect data sources

Data flow: `presentation → domain ← data`. Domain has no Android dependencies.

## UI Approach

- Jetpack Compose for all menus and screens
- Custom `SurfaceView` with dedicated game loop thread for the battle renderer (not Compose)
- Fixed timestep game loop, entity system for ziggurat/enemies/projectiles
- Edge-to-edge rendering via `enableEdgeToEdge()`

## Async

- Kotlin coroutines and `Flow` for all async operations
- Room exposes queries as `Flow`
- ViewModels collect flows and expose `StateFlow` to Compose

## Step Tracking

- Android Sensor API (`TYPE_STEP_COUNTER`) as primary source
- Health Connect SDK for cross-validation and Activity Minute Parity
- WorkManager + Foreground Service for reliable background counting

## Build Commands

```bash
./gradlew assembleDebug       # Debug APK
./gradlew assembleRelease     # Release APK
./gradlew test                # Unit tests
./gradlew connectedAndroidTest # Instrumented tests (device/emulator)
./gradlew lint                # Lint check
./gradlew clean               # Clean build
```

In non-TTY environments (Kiro CLI, CI), use `./run-gradle.sh <task>` instead of `./gradlew` to avoid output buffering. See `README.md` for the script.

## Continuous Integration

GitHub Actions (Plan 32 / ADR-0018). Workflows under `.github/workflows/`, all third-party actions SHA-pinned (Dependabot-maintained):

- `ci.yml` — PR + push:main gate: `./gradlew testDebugUnitTest lintDebug assembleDebug` + a Room schema-drift guard. Secret-free.
- `instrumented.yml` — `connectedDebugAndroidTest` on an API-34 KVM emulator (AVD-cached); blocking on PRs to `main` + nightly.
- `release.yml` — `v*` tag → signed `bundleRelease` → Play internal track (`r0adkll/upload-google-play`).

Plus `dependency-submission.yml` (Gradle dependency graph) and `dependabot.yml` (gradle + github-actions). CI invokes `./gradlew` directly — runners have a PTY, so `run-gradle.sh` is not needed there.

## Notes

- All annotation processing uses KSP (not kapt)
- Room schema exports to `app/schemas/` — commit these files
- Database uses SQLCipher encryption; future schema changes require proper Migration objects
- All new dependencies must be added to `gradle/libs.versions.toml`, not hardcoded in build files
