# Tech Stack

## Core

- **Language:** Kotlin (JVM target 17)
- **Min SDK:** 34 (Android 14) / Compile SDK 37 / Target SDK 36
- **Architecture:** MVVM + Clean Architecture
- **Build:** Gradle 9.6.0 with Kotlin DSL, version catalog at `gradle/libs.versions.toml`; JVM-17 toolchain pinned (local-detection only, #378/ADR-0039)
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
| Kover | 0.9.8 | Gradle plugin (`org.jetbrains.kotlinx.kover`, `:app`) | Whole-app JVM coverage report (`:app:koverXmlReport`/`koverHtmlReport`) — **informational** (#218). **Plus a scoped GATING ratchet (#373):** `:app:koverVerifyDebug` fails the build if the fragile concurrency/economy zones (`data.repository`/`domain.usecase`/`presentation.battle.engine`/`domain.battle.*`) drop below a blended LINE floor 85 or per-package floor 54. Filtered `variant("debug")` set, so the whole-app report stays unfiltered |
| LeakCanary | 2.14 | `debugImplementation` (`:app`) | **Debug-only** leak detection for the loop-thread/FGS/`SurfaceView` retention topology (#375). Auto-installs via ContentProvider; NEVER in the release AAB |
| OSV-Scanner | pinned binary (SHA-256 verified) | the `osv-scan` job in `.gitlab-ci.yml` | Supply-chain vuln scan of the full dependency set → **SARIF artifact** (no Code-Scanning dashboard equivalent; ADR-0044). **Non-gating** via `allow_failure`, weekly + on `main` (L77) |

**Alpha rationale:** no stable detekt supports Kotlin 2.3.0 (stable 1.23.x targets ≤ 2.0); ktlint 1.8.0 embeds a 2.2.x Kotlin parser (no actual parse failures in this codebase). The alpha is dev-tooling only — never shipped in the AAB.

**Baseline approach:** both tools use committed baselines (`config/detekt/baseline.xml`, `config/ktlint/baseline.xml`) so existing violations are grandfathered. Only NEW violations fail the build. Baselines shrink as violations are fixed.

**CI enforcement:** `:app:detekt` is a step in the `core-gate` job and ktlint is its own `ktlint` job, both code-gated behind the docs-only fast path. There is no `connected` job — the instrumented suite is a local pre-release device run.

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
./gradlew connectedDebugAndroidTest # Instrumented tests (device/emulator)
./gradlew lint                # Lint check
./gradlew clean               # Clean build
```

In non-TTY environments (CI, etc.), use `./run-gradle.sh <task>` instead of `./gradlew` to avoid output buffering. See `README.md` for the script.

## Continuous Integration

**GitLab CI** — one `.gitlab-ci.yml` at the repo root (ADR-0044; supersedes the GitHub Actions setup of Plan 32 / ADR-0018). Every external image, downloaded binary and Ruby gem is **digest- or checksum-pinned** — no `:latest` or bare tag survived the Phase-1 proof, and Renovate maintains the pins. Merge is gated on the **whole pipeline**, so every job in an MR pipeline blocks merge; there is no named-checks list in which a job can be forgotten.

The 10 jobs:

- `classify` — the docs-only fast path. Runs `ci/classify-diff.sh`, which emits a dotenv `CODE=true|false` and is a **fail-safe inversion**: the heavy gate is skipped only when EVERY changed path is allowlisted (`docs/**`, `*.md`, `.claude/**`, `.mcp.json`); any unknown path *or an unknown diff base* ⇒ full gate. This cannot be expressed as `rules:changes`, which is why it stays a scripted classifier. It is absent on schedule/web pipelines, so downstream jobs need it `optional: true` and fall back to `CODE=true`.
- `core-gate` — MR + push:main. `ci/validate-wrapper.sh` first (#212 — GitLab has no first-party wrapper-validation action), then `testDebugUnitTest lintDebug lintRelease assembleDebug`, an **unsigned** `assembleRelease` behind a placeholder licence key (#370 — proves R8 actually runs), `:app:koverXmlReport` + `:app:koverHtmlReport` (informational, #218) plus **`:app:koverVerifyDebug`** (the gating fragile-zone ratchet, #373), `:baselineprofile:assemble` + `:macrobenchmark:assemble` (type-check), the Room schema-drift guard (#254: `git add -N` + `git diff` + a `git status --porcelain` belt, catching modified AND new-untracked schema JSON), and `:app:detekt`. Secret-free.
- `ktlint` — standalone SHA-pinned ktlint binary on a JRE image, parallel to `core-gate`.
- **No `instrumented` job.** gitlab.com shared runners have no `/dev/kvm` (Phase-0 spike) and a self-hosted runner was declined, so `:app:connectedDebugAndroidTest` is a **human pre-release device run** — the one accepted regression with no CI backstop (ADR-0044).
- **`release-build` → `release-publish` → `release-object`** — fired only by a **protected** `v*` tag push (`$CI_COMMIT_TAG =~ /^v/ && $CI_COMMIT_REF_PROTECTED == "true"`), serialized by `resource_group: play-release`. `release-build` runs `ci/validate-wrapper.sh`, the tag↔versionName and versionCode-bump guards (#379), hard-fails on a blank `PLAY_LICENSE_KEY` (#124), builds + signs the AAB, asserts the signer identity matches the upload key, and uploads a **durable** copy to the Generic Package Registry. `release-publish` runs **Fastlane `supply`** in its own pinned container → Play track `internal`, status `completed` (chosen because no Gradle Play Publisher plugin is wired in the build). `release-object` creates the GitLab Release linking the durable AAB asset. Play "What's new" comes from `ci/prepare-whatsnew.sh` → `distribution/<locale>/changelogs/<versionCode>.txt`, capped at 500 Unicode chars and read **only** from a genuinely annotated tag — a lightweight tag's `%(contents)` is the commit message and must never reach store metadata, so it falls back to a generic line.
- `pages` — push:main touching `site/**`/`Gemfile`/`Gemfile.lock`/`.gitlab-ci.yml`, serialized via `resource_group: pages` (never cancel a half-deploy). Pinned Jekyll+minima build of **only the top-level `site/` folder** → `public/`, with three pre-publish assertions: `index.html` exists, the privacy heading is present, and the **`#delete-data` anchor** survives. The internal `docs/` tree is never published. **The URL Play points at is served by the website deployment, not by this job** (ADR-0044 — serving is deliberately forge-independent); the old `https://jonwhitefang.github.io/steps-of-babylon/` keeps serving a full copy indefinitely for already-installed builds.

- `gitleaks` — full-history secret scan (`GIT_DEPTH: 0`; a shallow clone silently weakens it), SARIF artifact.
- `osv-scan` — weekly + push:main + manual. **`allow_failure: true`** rather than `|| true` on the gating line, so the job's status still reflects the scanner's exit code instead of being unconditionally masked. Be precise about what that buys: `allow_failure` permits **any** non-zero result, so a *crashed* scanner is yellow too — visible in the pipeline, not red. Separating "vulns found" from "tool broke" would need `allow_failure: exit_codes:`, which is not wired today. SARIF artifact only — GitHub's Code-Scanning dashboard has no GitLab equivalent on our tier (ADR-0044), and the dependency-submission API is likewise retired.
- `renovate` — weekly self-hosted Renovate carrying the old Dependabot grouping policy (`all-gradle`, a separate `gradle-wrapper` group, and `ci-images`), so interacting versions land in one CI-verified MR (#255). **Replaces Dependabot, which does not exist on GitLab.** Note there is also no Dependabot-style *alert inbox* — cutover step 1 says resolve-or-record open alerts, because they vanish silently.

Schedule and web pipelines are disambiguated by a `PIPELINE_KIND=osv|renovate|pages` variable, and every such job gates on its exact value so one manual run cannot fire all three lanes. The Gradle wrapper distribution is checksum-pinned via `distributionSha256Sum` in `gradle-wrapper.properties` (#212). CI invokes `./gradlew` directly — runners have a PTY, so `run-gradle.sh` is not needed there.

## Notes

- All annotation processing uses KSP (not kapt)
- Room schema exports to `app/schemas/` — commit these files
- Database uses SQLCipher encryption; future schema changes require proper Migration objects
- All new dependencies must be added to `gradle/libs.versions.toml`, not hardcoded in build files
