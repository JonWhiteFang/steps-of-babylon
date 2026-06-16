# Plan 32 — CI/CD Pipeline (GitHub Actions)

**Status:** ✅ Merged to `main` via PR #100 (2026-06-03)
**Dependencies:** Plan 29 (Testing & QA — the suites CI runs), Plan 30 (Release Prep — signing config + AAB), Plan 31 (Play Console — the internal track + service account the release lane uploads to)
**Layer:** Repository infrastructure (`.github/`) — no app code change
**ADR:** [ADR-0018](../agent/DECISIONS/ADR-0018-ci-github-actions.md)

---

## Objective

Stand up automated Continuous Integration and a release lane on GitHub Actions, replacing the current "verified on the submitter's laptop only" gate. Make the documented manual gate (`testDebugUnitTest` + `lintDebug` + `assembleDebug`) machine-enforced on every PR and push to `main`, run the instrumented suite on a real emulator (blocking on PRs + nightly), and automate signed-AAB build + upload to the Play **internal** track on a version tag.

This closes the long-standing "No CI" gap noted across the project memory (the `#27` `DomainPurityTest` becomes machine-blocking for the first time, and the Room schema export gets a drift guard).

### Locked decisions (2026-06-03)

| # | Decision |
|---|---|
| 1 | New top-level **Plan 32** (not a V1X sub-plan) |
| 2 | Instrumented tests run **blocking on PRs to `main` + nightly schedule** |
| 3 | Release lane **auto-uploads to the Play internal track** |
| 4 | Decision recorded as **ADR-0018** |
| 5 | All third-party actions are **SHA-pinned** (Dependabot-maintained) |

---

## Grounding facts (current build)

| Aspect | Value | CI consequence |
|---|---|---|
| Toolchain | Gradle + AGP + Kotlin + KSP per `gradle/libs.versions.toml` / the wrapper (the catalog is the single source — don't pin versions here), JDK 17 | `setup-java` temurin 17 + `setup-gradle` cache |
| SDK | compileSdk/targetSdk 36, **minSdk 34** | emulator floor **API 34**; install platform 36 via `setup-android` |
| JVM gate | `testDebugUnitTest` (JUnit5; live count in STATE.md/CLAUDE.md), `lintDebug`, `assembleDebug` | one ubuntu job, **no secrets** |
| Instrumented | `connectedDebugAndroidTest` (9 tests, `HiltTestRunner`) | emulator + KVM |
| Debug build secrets | **none** — AdMob falls back to Google test IDs; no `google-services` plugin | PRs build on a clean clone with zero config |
| Release build | `bundleRelease` needs `keystore.properties` + `*.jks` (+ optional `local.properties` AdMob IDs) | inject from GH Secrets |
| Room schema | exported to `app/schemas/` (committed) | drift guard via `git diff --exit-code` |
| `run-gradle.sh` | gitignored, non-TTY only | **CI calls `./gradlew` directly** (runner has a PTY) |
| versionCode | manual, committed (Play rejected a reused code once — v13) | CI **does not** auto-bump; it builds the committed code |

---

## Task Breakdown

### Task 1: Core PR gate — `.github/workflows/ci.yml`

The fast, secret-free, always-blocking lane.

- **Triggers:** `pull_request` (all branches) + `push` to `main`.
- **Hardening:** top-level `permissions: contents: read`; `concurrency` group keyed on ref with `cancel-in-progress: true`; job `timeout-minutes: 30`.
- **Runner:** `ubuntu-latest`, single job `build-and-test`.
- **Steps:** checkout → `setup-java` (temurin 17) → `setup-android` (ensure platform-36 + build-tools) → `setup-gradle` (cache read/write) → run the documented one-call gate:
  ```bash
  ./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
  ```
- **Artifacts (`if: always()`):** JUnit XML (`**/build/test-results/testDebugUnitTest/**`), lint report (`**/build/reports/lint-results-debug.*`), debug APK (`**/build/outputs/apk/debug/*.apk`).
- Single Gradle invocation keeps the configuration cache coherent and is faster than three parallel jobs that each cold-start Gradle.

### Task 2: Instrumented tests — `.github/workflows/instrumented.yml`

Blocking on PRs to `main` + nightly (decision #2).

- **Triggers:** `pull_request` with `branches: [main]` **+** `schedule` (nightly cron, e.g. `0 3 * * *`) **+** `workflow_dispatch`.
- **Runner:** `ubuntu-latest` with **KVM** hardware acceleration enabled:
  ```bash
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
    | sudo tee /etc/udev/rules.d/99-kvm4all.rules
  sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
  ```
- **Emulator:** `reactivecircus/android-emulator-runner@<sha>` — **api-level 34**, `target: google_apis`, `arch: x86_64`, `disable-animations: true`, `emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none`.
- **Caching:** `actions/cache` for the AVD snapshot (key on api-level/target/arch) + a `force-avd-creation: false` warm-snapshot step, plus the `setup-gradle` cache. Keeps a run to ~8–12 min.
- **Script:** `./gradlew connectedDebugAndroidTest`.
- **Robustness:** `concurrency` cancel-in-progress; one retry of the emulator step to absorb boot flakiness; upload `**/build/outputs/androidTest-results/**` on failure. The nightly run is the canary — if a flake blocks a PR, re-run the single job rather than disabling the gate.

### Task 3: Release automation — `.github/workflows/release.yml`

Signed AAB → Play internal track (decision #3).

- **Triggers:** `push` tag `v*` (matches the planned `git tag v1.0.0` step) + `workflow_dispatch`.
- **Hardening:** `permissions: contents: write` (for the GitHub Release); `environment: release` (gates secrets behind an environment with optional manual approval).
- **Pre-build guard:** run `./gradlew testDebugUnitTest` first — never ship a signed build that fails unit tests.
- **Signing material from secrets:**
  ```bash
  echo "$UPLOAD_KEYSTORE_BASE64" | base64 -d > release/upload-keystore.jks
  cat > keystore.properties <<EOF
  storeFile=release/upload-keystore.jks
  storePassword=$KEYSTORE_STORE_PASSWORD
  keyAlias=$KEYSTORE_KEY_ALIAS
  keyPassword=$KEYSTORE_KEY_PASSWORD
  EOF
  ```
  AdMob production IDs written to `local.properties` from secrets (absent ⇒ build falls back to test IDs, so a misconfigured run never mints revenue — existing `build.gradle.kts` behaviour).
- **Build + verify:** `./gradlew bundleRelease` → `jarsigner -verify` (mirrors the manual release check; the live `release.yml` step uses `-verify` without `-strict`).
- **Upload:** `r0adkll/upload-google-play@<sha>` — `packageName: com.whitefang.stepsofbabylon`, `releaseFiles: app/build/outputs/bundle/release/app-release.aab`, `tracks: internal`, `status: completed`, plus `mappingFile` (R8). (No `debugSymbols` input: the SQLCipher / `androidx.graphics.path` prebuilts ship pre-stripped `.so` files, so `debugSymbolLevel = "FULL"` produces zero symbols to upload — passing a `debugSymbols` path would just fail the step. See the "native debug symbols warning" note in `docs/release/plan-31-walkthrough.md`.)
- **GitHub Release:** attach the AAB + mapping as a release artifact via `softprops/action-gh-release@<sha>`.
- **versionCode discipline:** CI builds the **committed** `versionCode`; it does not auto-bump (Play rejects reused codes — see the v13 rejection). Bump + commit before tagging.
- **Prerequisites (one-time, manual):** app shell created in Play Console; the **first** AAB uploaded manually (Play requires it); a Play service-account JSON with "Release to testing tracks" permission; `release` environment + secrets configured.

### Task 4: Supply-chain & robustness hardening

- **`.github/dependabot.yml`** — two ecosystems, weekly: `gradle` (keeps `libs.versions.toml` fresh) and `github-actions` (bumps the SHA pins with the human-readable version comment).
- **Dependency graph** — `gradle/actions/dependency-submission@<sha>` on `push` to `main` so GitHub security alerts see the resolved Gradle graph.
- **Room schema-drift guard** — after `assembleDebug` in `ci.yml`, fail if KSP regenerated a schema that wasn't committed:
  ```bash
  git diff --exit-code app/schemas || { echo "::error::Room schema drift — commit app/schemas changes"; exit 1; }
  ```
- **SHA-pinning (decision #5)** — every non-`actions/*`-and-even-`actions/*` third-party action pinned to a full 40-char commit SHA with a trailing `# vX.Y.Z` comment. Exact SHAs are resolved at YAML-authoring time and thereafter maintained by Dependabot. Resolution helper:
  ```bash
  gh api repos/<owner>/<repo>/commits/<tag> --jq .sha
  ```

### Task 5: Repository secrets & variables

Configure under **Settings → Secrets and variables → Actions** (and the `release` environment for the signing set):

| Secret | Lane | Purpose |
|---|---|---|
| `UPLOAD_KEYSTORE_BASE64` | release | base64 of `upload-keystore.jks` |
| `KEYSTORE_STORE_PASSWORD` | release | keystore store password |
| `KEYSTORE_KEY_ALIAS` | release | signing key alias |
| `KEYSTORE_KEY_PASSWORD` | release | signing key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | release | Play Developer API service-account JSON |
| `ADMOB_APP_ID` + `ADMOB_AD_UNIT_*` | release | optional; absent ⇒ test-ID fallback |
| `PLAY_LICENSE_KEY` | release | **required** — Base64 Play "Licensing" RSA public key for #124 purchase-signature verification. Written to `local.properties` as `play.licenseKey`. **NOT optional**: unlike AdMob (test-ID fallback), a blank key makes verification fail-open, so the release lane hard-fails (the `Write Play license key` step exits 1) and a Gradle guard fails `bundleRelease`/`assembleRelease`. Source: Play Console → Monetise → Monetisation setup → "Licensing" → base64-encoded RSA public key. **Paste the WHOLE key** — the blank-only guard does not validate key shape, so a truncated/placeholder value (e.g. `TODO`) builds fine but fails *every* purchase closed (a monetisation outage caught in internal testing, not a fraud hole). |

CI/instrumented lanes need **no** secrets (the `play.licenseKey` guard is scoped to `bundleRelease`/`assembleRelease`, so the PR gate's `assembleDebug` is unaffected).

### Task 6: Branch protection & required checks

- Require the `ci.yml` job + the `instrumented.yml` job as status checks before merging to `main` (RUN_LOG currently records `statusCheckRollup: []` — no required checks).
- Require branches up-to-date before merge; keep the existing squash/merge style.

### Task 7: Docs sync (current-state only — per agent protocol)

Touch only docs the change invalidates. **Do NOT edit `docs/archive/pre-claude-devdocs/archaeology/*` or other archived historical artifacts** — their "No CI" statements were true at authoring date.

- `docs/plans/master-plan.md` — Plan 32 row + dependency-graph node + status line (done in this authoring run).
- `AGENTS.md` — Plan 32 in the index + status checklist (done in this run); on implementation, update the test-run guidance.
- On implementation: `README.md` (CI badge + adjust "no CI" framing), `.kiro/steering/tech.md` (CI tooling table), `.kiro/steering/source-files.md` + `structure.md` (the new `.github/` files).
- `STATE.md` + `RUN_LOG.md` each run.

### Task 8: Verification & rollout

- Land `ci.yml` first on a branch; confirm the gate runs green against the full JVM test suite (867 tests at authoring, 2026-06-03) before wiring required checks.
- Land `instrumented.yml`; confirm a full emulator run green; cache warm on the second run.
- Land `release.yml` last; dry-run via `workflow_dispatch` with `status: draft` (or a throwaway tag) before trusting `tracks: internal, status: completed`.
- Flip on branch protection once the first two lanes are proven.

---

## File Summary

```
.github/
├── workflows/
│   ├── ci.yml              (new — lint + unit + assembleDebug + schema-drift guard; PR + push:main)
│   ├── instrumented.yml    (new — connectedDebugAndroidTest on API-34 emulator; PR:main + nightly + dispatch)
│   └── release.yml         (new — signed bundleRelease → Play internal; tag v* + dispatch)
└── dependabot.yml          (new — gradle + github-actions, weekly)

docs/plans/
├── plan-32-ci.md           (new — this file)
└── master-plan.md          (update — index row, graph node, status)

docs/agent/
├── DECISIONS/ADR-0018-ci-github-actions.md   (new)
├── STATE.md                (update)
└── RUN_LOG.md              (append)

AGENTS.md                   (update — plan index + status)
```

On implementation, also: `README.md`, `.kiro/steering/tech.md`, `.kiro/steering/source-files.md`, `.kiro/steering/structure.md`.

---

## Completion Criteria

- `ci.yml` runs green on every PR and push to `main`; `testDebugUnitTest` + `lintDebug` + `assembleDebug` all pass; schema-drift guard active.
- `instrumented.yml` runs `connectedDebugAndroidTest` green on an API-34 emulator, blocking on PRs to `main` and nightly, with AVD + Gradle caching.
- `release.yml` produces a signed, `jarsigner`-verified AAB on a `v*` tag and uploads it to the Play internal track; GitHub Release attached.
- All third-party actions SHA-pinned; Dependabot configured for `gradle` + `github-actions`.
- Branch protection requires the CI + instrumented checks before merge to `main`.
- Current-state docs synced; ADR-0018 Accepted; no historical artifact edited.

---

## Implementation status (2026-06-03)

Merged to `main` via PR #100 (2026-06-03); both CI checks green on GitHub runners. All 5 `.github` files written with the live-resolved SHA pins (commit-pinned + `# vX.Y.Z` comment), YAML-validated, and the `ci.yml` gate (`./gradlew testDebugUnitTest lintDebug assembleDebug`) verified green locally (867 tests).

The gate immediately earned its keep: enforcing `lintDebug` surfaced a latent error on `main` that no prior gate caught — `NotificationSettingsScreen.kt` cast `LocalContext.current` to `Activity` (`ContextCastToActivity`). Fixed in the same commit by switching to `androidx.activity.compose.LocalActivity.current` (available since activity-compose 1.9.0; project on 1.12.3) and dropping the now-unused `android.app.Activity` / `LocalContext` imports. Behaviour unchanged.

Post-merge setup completed 2026-06-04: `release` environment + all 9 secrets (incl. `PLAY_SERVICE_ACCOUNT_JSON` for the `play-ci-upload` service account), branch protection on `main` requiring `build-and-test` + `connected`, and the Play service account granted release-to-testing-tracks (the first manual AAB upload was already satisfied by the v3–v16 manual uploads). `versionCode` bumped 16 → 17 so the first `v*` tag won't collide with v16 on internal. **Pipeline fully live** — a `v*` tag now builds a signed AAB and uploads to the Play internal track.
