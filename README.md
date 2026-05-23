# Steps of Babylon

![Steps of Babylon](docs/release/store-assets/play-store-feature-graphic-1024x500.png)

An Android idle tower defense game where real-world walking drives all progression. Players earn **Steps** by physically walking, then spend them to upgrade an ancient ziggurat that fights wave-based battles against mythic enemies.

> **Every Step Builds the Tower.**

## Status

Version 1.0.0 (versionCode 9) — pre-launch. AAB v9 (Plan R4 Wave 1 bundle: R4-01 remove Overdrive + R4-02 Multishot/Bounce per-level scaling + R4-02b Labs research path + R4-04 upgrade-button icon) uploaded to Play Console internal track 2026-05-23, on-device smoke test PASSED 2026-05-23. **Plan R4 Wave 2 in progress** — R4-03 (Rapid Fire upgrade) landed on branch `feat/R4-03-rapid-fire` 2026-05-23. R4-06 (UW auto-trigger + per-path upgrades) and R4-07 (boss-drop Power Stones) next; combined Room migration v9→v10; 2 new ADRs. Waves 3–4 close out before closed-track promotion (R4-08 cards rewrite, R4-05 Help screen). 626 JVM unit tests green.

For the live current state see [docs/agent/STATE.md](docs/agent/STATE.md). For recent changes see [CHANGELOG.md](CHANGELOG.md).

## Privacy

The app collects step counts, exercise sessions, and purchase history locally. The Room database is encrypted at rest with SQLCipher; data is never transmitted off-device beyond Google Play Billing and Google Mobile Ads. Users can delete all local data via Settings → Storage → Clear data.

- Privacy policy (hosted): <https://jonwhitefang.github.io/steps-of-babylon/>
- Data deletion: <https://jonwhitefang.github.io/steps-of-babylon/#delete-data>
- Canonical source: [docs/release/privacy-policy.md](docs/release/privacy-policy.md)

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

# Unit tests (626 JVM tests)
./gradlew test

# Lint
./gradlew lint

# Clean
./gradlew clean

# Release AAB (requires keystore — see Setup)
./gradlew bundleRelease
```

Instrumented tests (`connectedAndroidTest`) are not yet implemented; all current coverage is JVM unit tests under `app/src/test/`. See [AGENTS.md](AGENTS.md) for the full coverage breakdown.

### Non-TTY Environments (Kiro CLI, CI, etc.)

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
│   ├── local/         # Room entities, DAOs, SQLCipher key manager (13 entities, schema v9)
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
| [AGENTS.md](AGENTS.md) | Full tech stack, conventions, status checklist, test coverage |
| [CHANGELOG.md](CHANGELOG.md) | All notable changes, dated |
| [Master Plan](docs/plans/master-plan.md) | 34-entry development roadmap |
| [Battle Formulas](docs/battle-formulas.md) | All combat and economy math (incl. Lab outer multipliers) |
| [Database Schema](docs/database-schema.md) | Room entities and migration strategy |
| [Step Tracking](docs/step-tracking.md) | Sensor stack, anti-cheat, background service |
| [Monetization](docs/monetization.md) | IAP, ads, and economy rules |
| [Privacy Policy](docs/release/privacy-policy.md) | Canonical privacy policy |

## Tech Stack

Kotlin · Jetpack Compose · Hilt · Room · SQLCipher (database encryption) · WorkManager · Android Sensor API + Health Connect · Custom SurfaceView battle renderer · Google Play Billing v8 · Google Mobile Ads SDK v25 · UMP v4 (consent)

See [AGENTS.md](AGENTS.md) for the full tech stack with versions and conventions, and [.kiro/steering/tech.md](.kiro/steering/tech.md) for the canonical version table.

## License

Proprietary. Copyright © 2026 Jon White Fang. All rights reserved. See [LICENSE](LICENSE).
