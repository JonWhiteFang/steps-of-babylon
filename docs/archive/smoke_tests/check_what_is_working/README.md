# Check What Is Working — Smoke Tests

Baseline smoke-test suite for Steps of Babylon. This directory documents *what
is already verified by the existing test harness* and the single-command
routes for reproducing that verification. It does **not** introduce a new test
framework, a new top-level module, or new dependencies — all smoke cases map
to tests that already exist under `app/src/test/java/com/whitefang/stepsofbabylon/`.

## Strategy

The project already owns a substantial JVM-only test harness (≈412 tests per
`docs/agent/STATE.md`). The smoke suite is a **curated subset** of those tests
plus the standard Gradle build/lint tasks — chosen so that a fresh environment
can answer "does the code still basically work?" in one short run.

Guiding principles:

1. **Reuse, do not duplicate.** Every smoke case points at an existing test
   file or an existing Gradle task. Nothing new was added under `app/src/test/`.
2. **Real components over mocks.** Where feasible the suite exercises real
   domain use cases, real Room via `Room.inMemoryDatabaseBuilder`, real
   SharedPreferences via Robolectric, and the project's own in-memory fakes
   under `app/src/test/java/com/whitefang/stepsofbabylon/fakes/`. Mockito is
   used only where the existing tests already use it (e.g. collaborators
   without an in-memory fake).
3. **Offline and deterministic.** No paid services, no production credentials,
   no network, no device/emulator required. All checks run on the JVM.
4. **Isolated and removable.** This directory contains only documentation. If
   the smoke-test concept is later rejected, deleting `smoke_tests/` leaves
   the rest of the tree untouched.

## How the existing harness is organised

```
app/src/test/java/com/whitefang/stepsofbabylon/
├── balance/          # 8 regression tests against GDD balance targets
├── data/
│   ├── healthconnect/  # StepCrossValidator, ActivityMinuteValidator
│   ├── integration/    # EscrowLifecycleTest (real Room via Robolectric)
│   ├── local/          # RoomSchemaTest (real Room via Robolectric)
│   └── sensor/         # StepRateLimiter, StepVelocityAnalyzer, StepIngestion,
│                       # StepIngestionPreferences, DailyStepManager
├── domain/
│   ├── model/        # 9 invariant tests (TierConfig, Biome, Loadouts, ...)
│   └── usecase/      # 33 use-case tests (formulas, affordability, progression)
├── fakes/            # 15 in-memory, StateFlow-backed fakes for repositories/DAOs
├── presentation/     # ViewModel tests, UX (currency/feedback), deep-link routing
└── service/          # StepWidgetProviderTest (SharedPreferences round-trip)
```

The test framework is **JUnit 5 (Jupiter)** with kotlinx-coroutines-test for
flow/coroutine assertions, Mockito-Kotlin for spies/stubs, Robolectric for
Android-framework classes (Room, SharedPreferences, SensorManager), and
`androidx.room:room-testing` for in-memory `RoomDatabase` setup.

Build config pointer: `app/build.gradle.kts` → `testOptions { unitTests.all
{ it.useJUnitPlatform() }; unitTests.isReturnDefaultValues = true;
unitTests.isIncludeAndroidResources = true }`.

## Prerequisites

The smoke suite is intentionally low-ceremony. You need:

| Requirement | Notes |
|---|---|
| **JDK 17** | Matches `compileOptions.sourceCompatibility = VERSION_17`. Check with `java -version`. |
| **Android SDK 36** | Compile/target SDK (per `app/build.gradle.kts`). The Gradle wrapper and AGP 9.0.1 drive SDK management. Path is read from `local.properties` (`sdk.dir=...`). |
| **Gradle** | Not a separate install — the repo ships `gradlew` and the non-TTY wrapper `run-gradle.sh`. |
| **Health Connect APK on device** | Not required for smoke tests (they are JVM-only). Only needed for on-device runs, which this suite does not perform. |
| **SQLCipher native libs** | Pulled in transitively by `net.zetetic:sqlcipher-android:4.13.0`. Not required for smoke tests — in-memory Room in the existing `RoomSchemaTest` / `EscrowLifecycleTest` uses an unencrypted database, matching the project's test convention. |
| **Keystore for release builds** | Optional. Release signing config is active only when `keystore.properties` exists (`app/build.gradle.kts`). Smoke suite uses debug builds only. |

No environment variables, containers, databases, emulators, or browsers are
required for the smoke subset.

## Commands

All commands must be run from the repository root. Use the `run-gradle.sh`
wrapper in non-TTY environments (Kiro CLI, CI) to avoid Gradle output
buffering — this is documented in `README.md` and `AGENTS.md`.

```bash
# 1) Fastest: run the full JVM unit-test suite (~412 tests).
#    Covers smoke areas 2–5 in full. Expected: all green.
./run-gradle.sh testDebugUnitTest

# 2) Compile-only sanity (fast feedback when tests are iterating).
./run-gradle.sh compileDebugKotlin compileDebugUnitTestKotlin

# 3) Static analysis / lint.
./run-gradle.sh lintDebug

# 4) Debug APK build (covers KSP / Hilt / Room codegen end-to-end).
./run-gradle.sh assembleDebug

# 5) Everything the CI gates on (test + lint + build) in one call.
./run-gradle.sh testDebugUnitTest lintDebug assembleDebug
```

Shortcut targeted runs (useful for drilling into a specific smoke area):

```bash
# Area 2: Domain formulas only
./run-gradle.sh testDebugUnitTest --tests 'com.whitefang.stepsofbabylon.domain.*'

# Area 3: Anti-cheat + ingestion
./run-gradle.sh testDebugUnitTest \
    --tests 'com.whitefang.stepsofbabylon.data.sensor.*' \
    --tests 'com.whitefang.stepsofbabylon.data.healthconnect.*'

# Area 4: Persistence round-trip
./run-gradle.sh testDebugUnitTest \
    --tests 'com.whitefang.stepsofbabylon.data.local.*' \
    --tests 'com.whitefang.stepsofbabylon.data.integration.*' \
    --tests 'com.whitefang.stepsofbabylon.service.StepWidgetProviderTest'

# Area 5: Presentation / ViewModel wiring
./run-gradle.sh testDebugUnitTest --tests 'com.whitefang.stepsofbabylon.presentation.*'
```

## Outputs

After a test run, human-readable results land here:

| Artifact | Location |
|---|---|
| JUnit HTML report | `app/build/reports/tests/testDebugUnitTest/index.html` |
| JUnit XML (per class) | `app/build/test-results/testDebugUnitTest/*.xml` |
| Lint HTML / XML | `app/build/reports/lint-results-debug.html` + `.xml` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |

## Files

- `README.md` — this file. Strategy, prerequisites, commands.
- `test_plan.md` — 5 areas × 5 cases (25 smoke cases total), each mapped to
  the existing test file / Gradle task that backs it.
- `report.md` — results of the most recent smoke run on this checkout. What
  works, what is broken, what could not be run, exact commands, error
  excerpts.

## Non-goals

- The smoke suite does not re-test every ADR-level invariant — the full
  regression tests under `balance/` already cover GDD balance targets. Rerun
  the full `testDebugUnitTest` task when changing balance constants.
- The smoke suite does not exercise the foreground step-counter service on a
  real device. That requires `connectedAndroidTest` and a physical device
  with hardware `TYPE_STEP_COUNTER` — out of scope for JVM smoke checks.
- The smoke suite does not install or touch the Google Play Store, AdMob, or
  Google Play Billing. Those depend on real SDKs that `BillingModule` /
  `AdModule` currently bind to stub implementations (see
  `data/billing/StubBillingManager.kt`, `data/ads/StubRewardAdManager.kt`).
