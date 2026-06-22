# Tech Stack

## Core

- **Language:** Kotlin (JVM target 17)
- **Min SDK:** 34 (Android 14) / Compile SDK 37 / Target SDK 36
- **Architecture:** MVVM + Clean Architecture
- **Build:** Gradle 9.6.0 with Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- **Database encryption:** SQLCipher via Android Keystore-managed passphrase

## Key Libraries & Versions

All versions managed in `gradle/libs.versions.toml`. Never hardcode versions in build files.

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.3.0 | Language |
| AGP | 9.2.1 | Android Gradle Plugin |
| KSP | 2.3.9 | Annotation processing (replaces kapt) |
| Compose BOM | 2026.06.00 | Jetpack Compose UI |
| Hilt | 2.59.2 | Dependency injection |
| Room | 2.8.4 | Local SQLite database |
| Google Play Billing | 9.1.0 | IAP via `billing-ktx`. `BillingManagerImpl` is the sole `BillingManager` binding for debug + release as of C.5 PR 3 (`StubBillingManager` deleted; `BuildConfig.USE_REAL_BILLING` removed). |
| Google Mobile Ads SDK | 25.4.0 | Reward ads via `play-services-ads`. `RewardAdManagerImpl` is the sole `RewardAdManager` binding for debug + release as of C.6 PR 3 (`StubRewardAdManager` deleted). `BuildConfig.USE_REAL_ADS` is retained only to gate the `MainActivity` UMP consent prefetch on debug emulators. |
| User Messaging Platform | 4.0.0 | GDPR/DSA consent via `user-messaging-platform`; paired with AdMob (C.6 PR 1) |
| Navigation Compose | 2.9.8 | Compose navigation |
| Lifecycle | 2.11.0 | ViewModel, StateFlow integration |
| WorkManager | 2.11.2 | Background step sync |
| JUnit (Jupiter) | 6.1.0 | Unit testing framework — junit-jupiter 6.x (catalog key still `junit5`) |
| kotlinx-coroutines (-android / -test) | 1.11.0 | Async runtime + test utilities. #257: runtime (`-android`) is now pinned explicitly (shared `coroutines` ref) — was floating transitively at 1.9.0 while tests ran 1.10.1 |
| SQLCipher | 4.16.0 | Database encryption |
| Health Connect | 1.1.0 (stable) | Step cross-validation, Activity Minute Parity (off alpha per audit #33; compileSdk now 37 so that gate is cleared, but HC 1.2.x is still alpha-only — held until beta/stable) |
| SQLite KTX | 2.6.2 | SQLite support library |
| Core KTX | 1.19.0 | Kotlin extensions for Android (unblocked by compileSdk 37; closes #199) |
| Activity Compose | 1.13.0 | Compose Activity integration (direct core-ktx pin 1.19.0 now governs the resolved version) |
| Hilt Work | 1.3.0 | Hilt WorkManager + Navigation Compose integration |
| Compose Material Icons | (BOM) | Material icon set for Compose. Both `material-icons-core` (small built-in set) and `material-icons-extended` (full Material catalogue) are included; R8 shrinks unused icons in release builds. Extended set added in R4-04 for `Icons.Filled.Upgrade`; R4-05 will use `Icons.Filled.Help`. |
| Mockito Kotlin | 5.4.0 | Kotlin-friendly mocking for tests |
| Robolectric | 4.16.1 | Android framework simulation for JVM tests (tests pin `@Config(sdk=[34])`, so the 4.16 "JDK 21 for SDK 36" requirement is not triggered) |
| AndroidX Test Core | 1.7.0 | Test utilities for Android components |
| WorkManager Testing | 2.11.2 | `WorkManagerTestInitHelper` for tests that exercise `WorkManager.cancelAllWork` (added by V1X-01 `DataDeletionManagerTest`). Same version as the main WorkManager dep. |
| Compose UI Test (`ui-test-junit4`, `ui-test-manifest`) | (BOM) | (#253) Compose UI tests on the JVM/Robolectric lane via `createComposeRule()` (`@GraphicsMode(NATIVE)`). BOM-managed (version-less); the BOM is re-applied to the `test` classpath. `ui-test-manifest` is on `debugImplementation` (NOT test) — it supplies the host `ComponentActivity` the rule launches. See `CardsScreenTest`/`OnboardingScreenTest`. |
| AndroidX Test Runner | 1.6.2 | `AndroidJUnitRunner` for instrumented tests; subclassed by `HiltTestRunner` (V1X-08 Phase 1A). |
| AndroidX Test Ext JUnit | 1.3.0 | `AndroidJUnit4` runner for instrumented `@RunWith` annotation (V1X-08 Phase 1A). |
| Hilt Android Testing | 2.59.2 | `HiltTestApplication`, `@HiltAndroidTest`, `HiltAndroidRule` for instrumented Hilt DI (V1X-08 Phase 1A). Pinned to the same `hilt` version as the rest of the graph. |

## Gradle Plugins

`android.application`, `kotlin.compose`, `kotlin.parcelize`, `hilt`, `ksp`, `room` — all aliased from version catalog. (`kotlin.parcelize` added in #234 for the presentation-layer `PackRevealState` Parcelable DTO; applied in `:app` via `kotlin("plugin.parcelize")` since it's bundled with the AGP-9 Kotlin distribution, declared `apply false` at the root.)

## Kotlin Lint Tooling (ADR-0037)

| Tool | Version | Integration | Purpose |
|---|---|---|---|
| detekt | 2.0.0-alpha.5 | Gradle plugin (`dev.detekt`) | Code-smell / complexity analysis (`:app:detekt`) |
| ktlint | 1.8.0 | CLI (`lint-kotlin.sh`, SHA-pinned) | Formatting enforcement (EditorConfig-driven) |

**Alpha rationale:** no stable detekt supports Kotlin 2.3.0 (stable 1.23.x targets ≤ 2.0); ktlint 1.8.0 embeds a 2.2.x Kotlin parser (no actual parse failures in this codebase). The alpha is dev-tooling only — never shipped in the AAB.

**Baseline approach:** both tools use committed baselines (`config/detekt/baseline.xml`, `config/ktlint/baseline.xml`) so existing violations are grandfathered. Only NEW violations fail the build. Baselines shrink as violations are fixed.

**CI enforcement:** two steps in the `build-and-test` job, code-gated behind the docs-only fast path. `connected` job untouched.

**Local usage:**
```bash
./run-gradle.sh :app:detekt      # Code-smell / complexity check
./lint-kotlin.sh                  # Formatting check (baseline-gated)
./lint-kotlin.sh --format         # Auto-fix formatting (no baseline)
```

## Dependency Verification (#256)

`gradle/verification-metadata.xml` holds SHA-256 checksums for every resolved artifact. Enforcement is
global (`dependency-verification=strict` in `gradle.properties`) — a missing or mismatched checksum fails
the build immediately (local + CI). Platform-specific artifacts (aapt2 linux/osx/windows) are all included
so builds pass on any OS.

**Regenerate after adding or bumping a dependency:**
```bash
./gradlew --write-verification-metadata sha256 --refresh-dependencies \
  assembleDebug testDebugUnitTest lintDebug :app:detekt \
  :app:assembleDebugAndroidTest :baselineprofile:assemble :macrobenchmark:assemble
```

`--refresh-dependencies` forces all repository variants into the metadata (prevents cache-locality gaps
where e.g. a POM resolves via Google's mirror locally but MavenCentral in CI).

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

In non-TTY environments (CI, etc.), use `./run-gradle.sh <task>` instead of `./gradlew` to avoid output buffering. See `README.md` for the script.

## Continuous Integration

GitHub Actions (Plan 32 / ADR-0018). Workflows under `.github/workflows/`, all third-party actions SHA-pinned (Dependabot-maintained):

- `ci.yml` — PR + push:main gate: a `gradle/actions/wrapper-validation` step first (#212), then `./gradlew testDebugUnitTest lintDebug assembleDebug` + a Room schema-drift guard. The drift guard catches both modified AND new-untracked schema JSON (#254: `git add -N` + `git diff` + a `git status --porcelain` belt). Secret-free.
- `instrumented.yml` — `connectedDebugAndroidTest` on an API-34 KVM emulator (AVD-cached); blocking on PRs to `main` + nightly.
- `release.yml` — `v*` tag → a `gradle/actions/wrapper-validation` step (#212), then signed `bundleRelease` → Play internal track (`r0adkll/upload-google-play`). Play "What's new" notes are written from the annotated tag message (`en-US`, capped at Play's 500-char limit; falls back to a generic line for lightweight tags / manual dispatch).
- `pages.yml` — push:main touching `site/**` (+ `workflow_dispatch`) → Jekyll-builds + deploys **only the top-level `site/` folder** to GitHub Pages (the hosted privacy policy at `https://jonwhitefang.github.io/steps-of-babylon/` + the `#delete-data` anchor — the URLs Play Console's privacy-policy + Data-safety declarations point at). Replaced the legacy "Deploy from branch → `/docs`" Pages source, which served the entire internal `docs/` tree publicly. Pages `build_type` = `workflow`.

Plus `dependency-submission.yml` (Gradle dependency graph, scoped via `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS` to a regex anchored on `releaseRuntimeClasspath` so the security graph reflects only production-shipped deps — excludes the build-tool/plugin classpath and the debug/test runtime classpaths) and `dependabot.yml` (gradle + github-actions; #255: bumps are grouped — `all-gradle` + a separate `gradle-wrapper` group + `all-actions` — so interacting versions land in one CI-verified PR). The Gradle wrapper distribution is checksum-pinned via `distributionSha256Sum` in `gradle-wrapper.properties` (#212). CI invokes `./gradlew` directly — runners have a PTY, so `run-gradle.sh` is not needed there.

## Notes

- All annotation processing uses KSP (not kapt)
- Room schema exports to `app/schemas/` — commit these files
- Database uses SQLCipher encryption; future schema changes require proper Migration objects
- All new dependencies must be added to `gradle/libs.versions.toml`, not hardcoded in build files
