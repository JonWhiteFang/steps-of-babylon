# Steps of Babylon

![Steps of Babylon](docs/release/store-assets/play-store-feature-graphic-1024x500.png)

[![CI](https://github.com/JonWhiteFang/steps-of-babylon/actions/workflows/ci.yml/badge.svg)](https://github.com/JonWhiteFang/steps-of-babylon/actions/workflows/ci.yml)

An Android idle tower defense game where real-world walking drives all progression. Players earn **Steps** by physically walking, then spend them to upgrade an ancient ziggurat that fights wave-based battles against mythic enemies.

> **Every Step Builds the Tower.**

## Status

Version 1.0.10 (versionCode 26) · 1110 JVM + 9 instrumented tests green · live on the Play Console internal track. The CI pipeline (Plan 32) and `v*`-tag release lane are live; promotion to the closed track is judgment-gated on the Closed-Test Readiness Gate (see [plan-FORWARD.md](docs/plans/plan-FORWARD.md)) — the tester soak is a Phase 2 concern that begins after promotion.

For the live current state (objective, priorities, fragile zones) see [docs/agent/STATE.md](docs/agent/STATE.md). For the dated change history see [CHANGELOG.md](CHANGELOG.md). These move every session; this section stays deliberately brief to avoid drift.

## Privacy

The app collects step counts, exercise sessions, and purchase history locally. The Room database is encrypted at rest with SQLCipher; data is never transmitted off-device beyond Google Play Billing and Google Mobile Ads. Users can delete all local data via **Settings → Delete All Data** (V1X-01 in-app deletion UI) or via system Settings → Storage → Clear data.

The app does NOT request location permissions; GPS / Exploration Mode was dropped from v1.x scope per ADR-0016.

- Privacy policy (hosted): <https://jonwhitefang.github.io/steps-of-babylon/>
- Data deletion: <https://jonwhitefang.github.io/steps-of-babylon/#delete-data>
- Canonical source: [site/index.md](site/index.md) — the published Pages page IS the single source of truth (built from `site/` by `.github/workflows/pages.yml`; internal `docs/` is not published)

## Prerequisites

- JDK 17
- Android SDK 36 (compile/target), min SDK 34 (Android 14)
- Android Studio (latest stable recommended)

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle (version catalog at `gradle/libs.versions.toml`)
4. Connect a device or start an emulator (API 34+)

A debug build needs no extra config. **Release builds (`assembleRelease` / `bundleRelease`) require a signing keystore at `release/upload-keystore.jks` plus `keystore.properties` at the project root** — both gitignored. See [docs/release/plan-31-walkthrough.md](docs/release/plan-31-walkthrough.md) for the keystore setup.

## Build & Run

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests (1010 JVM tests)
./gradlew test

# Lint
./gradlew lint

# Clean
./gradlew clean

# Release AAB (requires keystore — see Setup)
./gradlew bundleRelease
```

Instrumented tests live under `app/src/androidTest/`. The harness was stood up in V1X-08 Phase 1A with one infrastructure smoke test (`InfrastructureSmokeTest.harnessBoots`); `BattleSurfaceLifecycleTest` (4 R3-01 lifecycle regression guards) + `DeepLinkIntentTest` (4 `navigate_to` deep-link contract guards incl. a real Parcel round-trip) layered on 2026-05-29; the third planned suite — `StoreIapFlowTest` — is formally deferred (no real-framework-only gap; see CLAUDE.md). Run via `./gradlew connectedDebugAndroidTest` on a connected emulator (API 34+). All other coverage is JVM unit tests under `app/src/test/`. See [CLAUDE.md](CLAUDE.md) for the full coverage breakdown.

### Non-TTY Environments (CI, etc.)

Gradle buffers output when stdout isn't a terminal, which can cause builds to appear hung. Use the wrapper script instead:

```bash
./run-gradle.sh assembleDebug
./run-gradle.sh test
```

`run-gradle.sh` runs Gradle in the background and captures output to a temp file, avoiding the buffering issue. It's gitignored — recreate it if needed:

```bash
#!/bin/bash
# Wrapper to run Gradle tasks without output buffering issues in non-TTY environments
cd "$(dirname "$0")"
./gradlew "$@" > /tmp/gradle_out.txt 2>&1 &
wait $!
EXIT_CODE=$?
cat /tmp/gradle_out.txt
exit $EXIT_CODE
```

## Project Structure

```
app/src/main/java/com/whitefang/stepsofbabylon/
├── data/
│   ├── local/         # Room entities, DAOs, SQLCipher key manager (13 entities, schema v12)
│   ├── repository/    # Repository implementations
│   ├── sensor/        # Step sensor data source, rate limiter, daily step manager
│   ├── healthconnect/ # Health Connect client, cross-validator, activity-minute parity
│   ├── billing/       # Real Play Billing v8 (sole binding post-C.5 PR 3)
│   └── ads/           # Real AdMob v25 + UMP v4 consent (sole binding post-C.6 PR 3)
├── domain/            # Pure Kotlin: models, use cases, repository interfaces (no Android imports)
├── presentation/      # ViewModels, Compose screens, SurfaceView battle renderer
├── di/                # Hilt modules (Database, Repository, Step, HealthConnect, Billing, Ad, Time, CoroutineScope)
└── service/           # Foreground step-counting service, WorkManager workers, widget
```

See [docs/architecture.md](docs/architecture.md) for layer rules and data flow.

## Where to start

If you're picking this up cold, read [docs/agent/START_HERE.md](docs/agent/START_HERE.md) for the agent contract, then [docs/agent/STATE.md](docs/agent/STATE.md) for current state, then dive into the relevant plan in [docs/plans/](docs/plans/).

## Key Documentation

| Document | Description |
|---|---|
| [Game Design Document](docs/StepsOfBabylon_GDD.md) | Full game design spec |
| [Architecture](docs/architecture.md) | Clean Architecture layers and conventions |
| [CLAUDE.md](CLAUDE.md) | Operating guide: architecture, conventions, domain concepts, fragile zones |
| [CHANGELOG.md](CHANGELOG.md) | All notable changes, dated |
| [Master Plan](docs/plans/master-plan.md) | 38-entry development roadmap |
| [Battle Formulas](docs/battle-formulas.md) | All combat and economy math (incl. Lab outer multipliers) |
| [Database Schema](docs/database-schema.md) | Room entities and migration strategy |
| [Step Tracking](docs/step-tracking.md) | Sensor stack, anti-cheat, background service |
| [Monetization](docs/monetization.md) | IAP, ads, and economy rules |
| [Security Model](docs/steering/security-model.md) | Consolidated view: encryption, anti-cheat, economy atomicity, purchase verification |
| [Privacy Policy](site/index.md) | Canonical privacy policy (published to GitHub Pages) |

## Tech Stack

Kotlin · Jetpack Compose · Hilt · Room · SQLCipher (database encryption) · WorkManager · Android Sensor API + Health Connect · Custom SurfaceView battle renderer · Google Play Billing v8 · Google Mobile Ads SDK v25 · UMP v4 (consent)

See [CLAUDE.md](CLAUDE.md) for the full tech stack with versions and conventions, and [docs/steering/tech.md](docs/steering/tech.md) for the canonical version table.

## License

Proprietary. Copyright © 2026 Jon White Fang. All rights reserved. See [LICENSE](LICENSE).
