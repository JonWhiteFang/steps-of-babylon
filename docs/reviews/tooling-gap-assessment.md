# Steps of Babylon — Tooling Gap Assessment

> Read-only tooling/automation audit. Produced per `docs/reviews/tooling-gap-assessment-prompt.md`.
> Method: 15-facet multi-agent fan-out (one reviewer per facet, each citing real `file:line`) →
> adversarial refute-verify per finding (default-to-refuted) → synthesis of survivors only.
> **25 findings raised · 24 survived (12 confirmed / 12 partial) · 1 refuted.** No code was changed.
> Anchors verified against HEAD (v1.0.12 / versionCode 28, schema v12).

---

## Executive summary

- **Overall maturity: High.** This is an unusually well-tooled solo repo: SHA-pinned CI with a
  docs-only fast-path, strict per-artifact dependency verification (`verification-metadata.xml`,
  not just a lockfile), a signed-AAB release lane with a signer-cert **identity** assertion and a
  fail-*closed* license-key guard, executable architecture tests, Dependabot grouping, OSV scanning,
  and a committed agent "memory spine." The gaps are refinements at the edges, not foundational holes.
- **Main strengths (genuinely well-tooled):** release-lane safety (`release.yml` tag↔versionName
  guard + signer-identity check + secret-file cleanup); supply-chain integrity
  (`dependency-verification=strict` + `verification-metadata.xml`); executable architecture
  enforcement (`DomainPurityTest` / `PresentationPurityTest`); the CI docs/tooling classifier;
  Room schema-drift guard (`git add -N` catches *new* untracked schemas); grouped Dependabot.
- **Main weaknesses (real gaps only):**
  1. The **minified RELEASE variant is never built in CI** until a `v*` tag *auto-publishes* it to
     Play internal — an R8/shrink break reaches testers as a runtime crash (`cicd-1`).
  2. **Zero production crash visibility path** — the in-app `CrashBreadcrumb` is shown once then
     `clear()`-ed with no report channel, and no post-release monitoring runbook exists
     (`obs-2`, `obs-1`).
  3. The two most safety-critical invariants an **AI agent** relies on — the acyclic lock order and
     "Steps are never generated in-game" — are **prose-only, not build-gated** (`ai-2`, `ai-3`).
  4. **No coverage ratchet** on the fragile concurrency/economy zones — Kover runs report-only
     (`testing-1`).
  5. **No memory-leak detection** (no LeakCanary) for the long-running loop-thread + foreground
     service topology (`perf-3`).
- **Top 5 recommended improvements:**
  1. Add `assembleRelease` to the PR gate (or push-to-main) so R8/shrink breaks fail *before* the
     tag auto-publishes to Play. `cicd-1`
  2. Give the crash breadcrumb an exit path (tap-gated `ACTION_SENDTO`) + a short post-release
     monitoring section in the release checklist. `obs-2` / `obs-1`
  3. Turn the two prose invariants into tripwires: a `DomainPurityTest`-style step-credit
     allowlist test, and make the `concurrency-reviewer` mandatory on engine/effects diffs. `ai-3` / `ai-2`
  4. Add `LeakCanary` as `debugImplementation` (zero release cost). `perf-3`
  5. Add a scoped Kover `verify` **ratchet** on the two fragile package globs (floor = current %). `testing-1`

---

## Detected stack

| Dimension | As actually found |
|---|---|
| Language | Kotlin (JVM target 17), Kotlin language version 2.3.0 (`libs.versions.toml`) |
| UI | Jetpack Compose (BOM) + custom `SurfaceView` battle renderer |
| Build | Gradle 9.6.0 (wrapper, `distributionSha256Sum` pinned), AGP 9 (`android.application`), KSP, single version catalog `gradle/libs.versions.toml` |
| Modules | `:app` (shipped) + `:baselineprofile` + `:macrobenchmark` (`com.android.test`, dev-tooling); `settings.gradle.kts` |
| DI / DB | Hilt (KSP), Room + SQLCipher, WorkManager, Health Connect |
| Tests | JVM lane: JUnit Jupiter + kotlinx-coroutines-test + Robolectric (Compose UI) + Mockito-Kotlin (~1294 `@Test`); instrumented lane: 9 tests (AndroidJUnit4 + Hilt, API-34 emulator) |
| Static analysis | detekt (`config/detekt/`, baseline-gated), ktlint 1.8.0 (`lint-kotlin.sh`, SHA-pinned), Android Lint (`HardcodedText` promoted to error), `DomainPurityTest`/`PresentationPurityTest` |
| Coverage | Kover plugin applied; CI generates `koverXmlReport`/`koverHtmlReport` — **informational, non-gating** |
| CI/CD | GitHub Actions: `ci.yml`, `instrumented.yml`, `osv-scan.yml`, `dependency-submission.yml`, `release.yml`, `pages.yml` (all actions SHA-pinned; Dependabot maintains pins) |
| Release/deploy | `v*` tag → `bundleRelease` → signed AAB → Play **internal** track (`r0adkll/upload-google-play`, `status: completed`) |
| Security | SQLCipher, Android Keystore (`DatabaseKeyManager`), R8 (`isMinifyEnabled`+`isShrinkResources`), network-security config, gitignored `keystore.properties`/`local.properties`, guava CVE constraint |
| Supply chain | `dependency-verification=strict` + `gradle/verification-metadata.xml`; OSV scan; GitHub dependency-graph submission (scoped `^releaseRuntimeClasspath$`) |
| Observability | In-app `CrashBreadcrumb`/`CrashBreadcrumbStore` + `GameLoopThread.onLoopError` UI state only. **No third-party crash/ANR SDK.** |
| Perf | Baseline Profile + Macrobenchmark modules (assemble type-checked in CI; benchmarks run locally, un-run for numbers) |
| Docs / memory | `CLAUDE.md`, `README.md`, `CHANGELOG.md`, `docs/agent/*` spine, 35 ADRs (`ADR-0011`/`0013` reserved), `docs/steering/*`, public `site/` privacy policy |
| AI-agent support | `CLAUDE.md` + `docs/agent/*` + `.claude/` (agents/hooks/skills/settings) + Adversarial Review Gate. **No `AGENTS.md`.** |

---

## Existing tooling inventory

| Area | Existing tooling | Notes |
|---|---|---|
| Formatting | ktlint 1.8.0 (`lint-kotlin.sh`), `.editorconfig` (`ktlint_official`, 120 cols) | SHA-pinned binary; baseline-gated (fails on NEW only); own CI job (~30s, no JDK/Gradle) |
| Linting / static analysis | detekt (`config/detekt/detekt.yml` + baseline), Android Lint (`HardcodedText`→error) | `buildUponDefaultConfig=true`; 259-entry baseline proves defaults fire. **Compose `Text("literal")` NOT linted** (`cqt-1`) |
| Architecture enforcement | `DomainPurityTest`, `PresentationPurityTest` | Executable; enforce layer/import rules. **Lock-order + step-generation invariants NOT gated** (`ai-2`,`ai-3`) |
| Type checking (Kotlin strictness) | `sourceCompatibility`/`targetCompatibility`=17 | Bytecode target only; **no JVM toolchain** → local JDK not pinned (`devenv-1`) |
| Testing (JVM + instrumented) | ~1294 JVM (JUnit Jupiter + Robolectric + fakes) + 9 instrumented; `SimulationTest`, `AtomicDaoConcurrencyTest` | Strong. Migration tests assert data, not full schema shape (`db-1`) |
| Coverage | Kover (`koverXmlReport`/`koverHtmlReport`) | **Report-only, no `verify`/threshold** (`testing-1`) |
| CI/CD | 6 workflows, SHA-pinned, docs fast-path classifier; branch protection enforced (ADR-0018 amendment 2026-06-16) | PR gate builds `assembleDebug` only — **release variant untested until tag** (`cicd-1`) |
| Security (crypto + scanning) | SQLCipher, Keystore, R8, network-security config, OSV scan, dependency-submission | **No secret-scanning / push-protection / gitleaks** (`sectooling-1`) |
| Dependencies | `libs.versions.toml` (catalog), grouped Dependabot, `verification-metadata.xml` (strict) | Equivalent-or-stronger than a lockfile; no dead catalog entries; **no OSS attribution surface** (`depmgmt-1`) |
| Documentation / memory spine | `CLAUDE.md`, `docs/agent/*`, 35 ADRs, `docs/steering/*`, `CHANGELOG` | Excellent. `STATE.md` over its own one-page rule (`docs-2`); `README` test count stale (`structure-1`/`docs-1`) |
| Release / Play deployment | `release.yml` (tag→signed AAB→Play internal) | Best-in-class guards; **no versionCode-collision guard** (`releaseops-1`), **no scripted rollback** (`releaseops-2`) |
| Observability / crash reporting | In-app `CrashBreadcrumb` + `onLoopError`; Play Console Vitals (implicit) | Breadcrumb has **no exit path** (`obs-2`); **no monitoring runbook** (`obs-1`) |
| Performance (baseline profile / benchmark) | `:baselineprofile` + `:macrobenchmark` modules | Type-checked in CI; **numbers never captured** (`perf-1`); frame time discarded (`perf-2`); **no LeakCanary** (`perf-3`) |
| AI-agent support | `CLAUDE.md` + spine + `.claude/*` + Review Gate + `run-gradle.sh` | Strongest area. **No `AGENTS.md`** for non-Claude tools (`ai-1`); safety invariants prose-only (`ai-2`/`ai-3`) |

---

## Gap analysis

Deduped (2 pairs of facets found the same gap). Priority reflects the **adversarial verifier's**
adjusted priority where it differs from the reviewer's.

| ID | Area | Gap | Impact (this app) | Recommended fix | Priority |
|---|---|---|---|---|---|
| **cicd-1** | Release/R8 CI coverage | Minified `release` variant never assembled in CI until a `v*` tag *auto-publishes* the AAB. PR gate builds only `assembleDebug`; `lintRelease` doesn't run R8 | An R8/shrink keep-rule regression (WorkManager/receivers/Room/Hilt reflective surface; only 17 `-keep` rules) ships straight to Play internal as a runtime crash | Add `assembleRelease` to PR gate (or push-to-main); R8 runs without signing. Confirm the `play.licenseKey` guard tolerates a non-publishing release build | **High** |
| **obs-2** | Crash breadcrumb dead-ends | `CrashBreadcrumb` peeked → generic snackbar → immediate `clear()`; no report channel; never leaves device | The one useful local diagnostic (exception class + 4KB stack) is discarded; field crashes never reach the maintainer | Add a tap-gated `ACTION_SENDTO`/Play-feedback "Report" action pre-filled with breadcrumb; defer `clear()` until dismiss | **Med** |
| **ai-2** | Prose-only concurrency invariants | Acyclic lock order (`entitiesLock`→`effectsLock`) + "collaborators hold no monitor" are prose + an opt-in subagent, not build-gated | An agent can ship a lock-order inversion (the #118/#191 CME class) with a fully green `testDebugUnitTest`+detekt+ktlint+lint | Wire `concurrency-reviewer` as a MANDATORY fan-out on `presentation/battle/engine/**` + `effects/**` diffs; optionally a detekt rule flagging nested lock acquisition | **Med** |
| **ai-3** | "Steps never generated" not enforced | The #1 design rule has no direct test; nothing stops a new use case calling `adjustStepBalance` with a positive in-round delta | A new card/UW/mission reward path could credit steps off in-round state and ship green — breaking the core monetization/fairness invariant | Add a `DomainPurityTest`-style test enumerating credit call sites and asserting the allowlist = {DailyStepManager, StepCrossValidator, ClaimSupplyDrop, AwardBattleSteps} | **Med** |
| **testing-1** | No coverage ratchet on fragile zones | Kover is report-only; no `koverVerify`. A refactor that guts tests fails no check | An AI refactor of `GameEngine`/a repo impl can silently drop assertions on the costliest-to-break code | Add a **scoped** `koverVerify` bound on the two fragile package globs only, floor = current measured %; leave the rest informational | **Med** |
| **perf-3** | No leak detection | No LeakCanary anywhere; long-lived `GameLoopThread`+engine+`SurfaceHolder` + foreground service = classic retention topology | A leaked SurfaceView/Activity across battle enter/exit → gradual OOM with no diagnostic (no crash SDK either) | Add `LeakCanary` as `debugImplementation` (never ships, zero config) | **Med** |
| **sectooling-1** | Secret scanning absent | Real secrets kept out of git only by `.gitignore` (a manual denylist); no gitleaks/push-protection/pre-commit | A gitignore miss / `git add -f` commits a keystore or license key silently; a leaked signing key is the highest-cost incident | Enable GitHub secret-scanning + push protection; add a gitleaks action with a custom rule for `*.jks` / `storePassword=` (built-in patterns miss those) | **Med** |
| **depmgmt-1** | OSS attribution surface | Proprietary AAB bundles Apache-2.0/permissive libs (Guava, Play Services, Health Connect, Billing) with no in-app notices / NOTICE file | Apache-2.0 §4(d) attribution obligation attaches to the redistributed **binary**; the shipped app preserves none | Apply `com.google.android.gms.oss-licenses-plugin` + link from `SettingsScreen`, or a static build-time notices asset in `HelpScreen` | **Med** |
| **releaseops-1** (≡`cicd-3`) | versionCode collision | Release guard checks only `versionName↔tag`; no check that `versionCode` was bumped past the last published build | A forgotten bump wastes a full signed run and fails only at Play upload (the documented v13 rejection). No bad artifact ships | Optional: fail-fast bash step asserting `versionCode` > previous `v*` tag's committed code (symmetric with the versionName guard). Play is the backstop | **Low–Med** |
| **obs-1** | No post-release runbook | `release-checklist.md` ends at "Build Outputs"; monitoring guidance lives only in the one-time `plan-31-walkthrough.md`; no cadence/threshold | Time-to-detect a field regression depends on the maintainer remembering to open Vitals; no "check at 24h/72h, roll back if…" captured | Add a short "Post-release monitoring" section to `release-checklist.md` cross-referencing the plan-31 guidance + a 24h/72h cadence. (Mapping upload is already automated) | **Low** |
| **db-1** | Migration test depth | No end-to-end `MigrationTestHelper` test drives v7→v12 and validates output schema vs `app/schemas`; recreate-table CREATEs are unchecked for shape | A recreate-table migration whose CREATE drifts from the entity crashes on a real user's upgrade; the data-only migrate() tests still pass | Add one `MigrationTestHelper` insurance test running the full chain against the committed schema JSON; keep existing data tests | **Low** |
| **cqt-1** | Compose string discipline | `lint{}` comment claims ~110 hardcoded Compose strings (now ~6, all numeric interpolations); `HardcodedText` is XML-only, no guard on new Compose prose literals | A future screen could add a hardcoded user-facing string with no CI catch; stale comment misleads | Refresh the comment; optionally add a tiny `PresentationPurityTest`-style scan for `Text("…")` prose. No 3rd-party Compose-lint dep | **Low** |
| **devenv-1** | Local JDK not pinned | No Gradle JVM toolchain; only bytecode-level `VERSION_17`. README names "JDK 17" as prose | A too-new ambient JDK (21/24) on first-clone/local builds fails with opaque toolchain/KSP errors CI won't reproduce | Add `kotlin { jvmToolchain(17) }` to `:app` + both benchmark modules (single-source, IDE+CLI consistent) | **Med** |
| **releaseops-2** | Rollout/rollback story | Upload is `status: completed` at 100% to internal, no `userFraction`, no scripted halt/rollback | Fine for internal today, but the automation stops at internal — every production rollout/rollback becomes manual the moment production is reached | Doc-only now: flag in `release-checklist.md` that the lane is internal-only + production is manual. When production lands, add `status: inProgress` + `userFraction` dispatch input | **Low** |
| **perf-2** | Frame time discarded | `GameLoopThread` computes per-frame `frameTime` only to derive sleep; no min/avg/max, dropped-frame, or UPS-vs-60 signal | On a low-end device the battle loop could silently drop below 60 UPS with no dev-build signal | Accumulate rolling min/avg/max + dropped-frame count behind a `BuildConfig.DEBUG` overlay, reusing the computed `frameTime` (zero release cost) | **Low** |
| **perf-1** | Benchmark numbers never captured | Macrobenchmark harness can't run: no non-debuggable `benchmark` build type on `:app`; no startup/frame numbers ever recorded (self-documented deferral) | For a 60fps game, zero measured startup/frame baseline — regressions ship undetected | Close the documented follow-up (`startup-baseline.md`): add a `benchmark` build type via the fragile-zone review, run on a device once. Do NOT CI-gate | **Low** |
| **structure-1** (≡`docs-1`) | README test-count drift | `README.md:13,52` say "1277 JVM tests"; canonical is 1294 (STATE.md/CLAUDE.md) | Cold-start onboarding doc shows a stale number, contradicting its own "stays brief to avoid drift" note | Replace both with a pointer to STATE.md/CLAUDE.md (or update to 1294) | **Low** |
| **structure-2** | README structure tree | README `data/` tree omits `anticheat/`, `time/`, `diagnostics/`, `onboarding/` (present on disk; in `structure.md`/`CLAUDE.md`) | Human first-glance orientation nit; agents get the full tree from auto-loaded CLAUDE.md | Add the four packages, or add a real link to `structure.md` (README currently links `architecture.md`, not `structure.md`) | **Low** |
| **devenv-2** | Build-command doc consistency | README "Build & Run" uses bare `./gradlew`; the non-TTY `run-gradle.sh` mandate is 4 lines below with no inline cross-ref | Minor: an agent copying the top block in a non-TTY harness could hit the documented buffering hang | One-line cross-reference at the top block. Trivial | **Low** |
| **docs-2** | STATE.md size discipline | `STATE.md` is 490 lines / 60K vs its own "keep to one page" rule; ~150 lines of stacked completed-objective narrative + a test-count ladder that belongs in RUN_LOG/CHANGELOG | It's auto-injected every session (SessionStart preflight) — bloat burns context budget each session | Relocate the ~150 lines of per-PR narrative + the test-count ladder to RUN_LOG/CHANGELOG (fragile-zones section is legit live reference — leave it) | **Low** |
| **ai-1** | No `AGENTS.md` | All agent guidance lives in the Claude-specific `CLAUDE.md`; a non-Claude agent arrives blind to the hard invariants | Speculative-portability insurance — repo shows zero non-Claude-agent usage today | Thin ~15-line `AGENTS.md` redirecting to `CLAUDE.md` + `START_HERE.md` (no content duplication) | **Low** |
| **pm-1** | Open-backlog reconciliation | No single doc reconciles the 15 open GitHub issues vs the spine; unlabelled proposals #21–#31 live only in the self-flagged-stale V1X roadmap | An agent must run `gh issue list` + cross-ref 3 prose locations; the unlabelled items have no canonical pointer | Have `/checkpoint` emit a mechanical `docs/agent/BACKLOG.md` from `gh issue list --state open --json …` (one line + "tracked at" per issue) | **Low** |

**Refuted (1) — `cicd-2`, branch-protection visibility:** the reviewer claimed no in-repo record of
required checks. **Refuted:** `ADR-0018` "Amendment — 2026-06-16: branch protection actually enforced"
already documents the required checks (`build-and-test` + `connected`), `enforce_admins: true`,
`required_conversation_resolution: true`, and even the historical drift — corroborated by
`master-plan.md:160` and `plan-32-ci.md:146-165`. No gap.

---

## Prioritised recommendations

### Immediate improvements

**1. Build the RELEASE variant in CI before it can auto-publish (`cicd-1`) — High**
- *Why here:* `release.yml` runs `bundleRelease` (first R8 run) then uploads to Play internal with no
  human gate; the PR gate only builds `assembleDebug`. An R8/shrink keep-rule regression against the
  app's reflective surface (WorkManager workers, `BootReceiver`, `StepWidgetProvider`, notifications,
  Room/Hilt — only 17 `-keep` rules) surfaces as a runtime crash on testers, not a build error.
- *Approach:* add `assembleRelease` to `ci.yml` (R8 needs no keystore — `signingConfig` is
  conditional). If build time matters, gate it on push-to-main only. Confirm the `play.licenseKey`
  fail-closed guard (`gradle.taskGraph.whenReady`) doesn't trip a non-publishing `assembleRelease`.
- *Files:* `.github/workflows/ci.yml`, (verify) `app/build.gradle.kts` guard scope.
- *Difficulty:* Low · *Risk:* Low · **Verify:** `./run-gradle.sh assembleRelease`

**2. Give the crash breadcrumb an exit path + a monitoring runbook (`obs-2`, `obs-1`) — Med**
- *Why here:* `MainActivity.kt:277-283` shows a generic snackbar then unconditionally `clear()`s the
  breadcrumb (`CrashBreadcrumb` = thread + exception class + 4KB stack). With no crash SDK, field
  crashes are invisible; the release checklist has no post-release step.
- *Approach:* tap-gated `ACTION_SENDTO` (or Play in-app feedback) pre-filled with
  `exceptionClass`+`stackPreview`; defer `clear()` until dismiss. Add a "Post-release monitoring"
  section to `release-checklist.md` (24h/72h Vitals cadence; note `mapping.txt` upload is already
  automated in `release.yml`).
- *Files:* `presentation/MainActivity.kt`, `docs/release/release-checklist.md`.
- *Difficulty:* Low · *Risk:* Low · **Verify:** manual (crash → relaunch → tap Report)

**3. Turn the two prose invariants into tripwires (`ai-3`, `ai-2`) — Med**
- *Why here:* the highest-value AI-safety additions. Both are semantic rules no test catches today.
- *Approach:* (a) a `DomainPurityTest`-style test enumerating step-credit call sites, asserting the
  allowlist = {DailyStepManager, StepCrossValidator, ClaimSupplyDrop, AwardBattleSteps} — fail on any
  new credit site. (b) Make `concurrency-reviewer` a **mandatory** fan-out lane on any diff under
  `presentation/battle/engine/**` or `effects/**` (its own description already scopes this).
- *Files:* new `app/src/test/.../architecture/StepCreditAllowlistTest.kt`; the Review-Gate wiring
  in `CLAUDE.md` / a pre-commit path.
- *Difficulty:* Med · *Risk:* Low · **Verify:** `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCredit*'`

**4. Add LeakCanary (`perf-3`) — Med**
- *Why here:* single highest-leverage, lowest-cost perf add; directly targets the SurfaceView/engine
  lifecycle and foreground service, with no crash SDK as a fallback.
- *Approach:* `debugImplementation(libs.leakcanary)` — never ships, zero config.
- *Files:* `gradle/libs.versions.toml`, `app/build.gradle.kts`.
- *Difficulty:* Low · *Risk:* Low · **Verify:** `./run-gradle.sh :app:assembleDebug` (LeakCanary auto-installs in debug)

**5. Scoped Kover verify ratchet (`testing-1`) — Med**
- *Why here:* the AI-refactor silent-regression guard; a whole-app threshold *would* be
  over-engineering, so scope it.
- *Approach:* `koverVerify` bound on `data.repository`+`domain.usecase` (economy) and
  `presentation.battle.engine`+`domain.battle` (loop) globs only, floor = current measured %; wire
  into the existing CI Kover step. Leave the rest informational.
- *Files:* `app/build.gradle.kts` (kover DSL), `.github/workflows/ci.yml`.
- *Difficulty:* Low (finicky DSL) · *Risk:* Low · **Verify:** `./run-gradle.sh :app:koverVerify`

**6. Enable secret scanning + push protection (`sectooling-1`) — Med**
- *Approach:* turn on GitHub secret-scanning + push protection (free, zero maintenance); add a
  gitleaks action with a custom rule for `*.jks` / `storePassword=` (built-in patterns miss binary
  keystores and `.properties` password lines).
- *Files:* repo settings + optional `.github/workflows/gitleaks.yml`.
- *Difficulty:* Low · *Risk:* Low · **Verify:** `gh api repos/JonWhiteFang/steps-of-babylon/secret-scanning/alerts`

### Next improvements

- **`devenv-1` — `kotlin { jvmToolchain(17) }`** on `:app` + both benchmark modules. Removes opaque
  local first-clone JDK errors. *Verify:* `./run-gradle.sh :app:compileDebugKotlin`.
- **`depmgmt-1` — OSS attribution screen.** `oss-licenses-plugin` linked from `SettingsScreen`, or a
  static notices asset in `HelpScreen`. License-compliance on the shipped binary.
- **`db-1` — one `MigrationTestHelper` chain test** (v7→v12 vs `app/schemas`). *Verify:*
  `./run-gradle.sh :app:testDebugUnitTest --tests '*Migration*'`.
- **`cqt-1` — refresh the stale `lint{}` comment** + optional `Text("…")` prose scan test.
- **`obs-1`/`releaseops-2` — release-checklist doc additions** (monitoring cadence; internal-only /
  manual-production flag).

### Optional improvements

- **`releaseops-1`/`cicd-3` — local versionCode-increment guard** in `release.yml`. Play already
  rejects reused codes, so this only saves a wasted run — a convenience, not safety.
- **`perf-2` — DEBUG frame-stats overlay** reusing `GameLoopThread`'s computed `frameTime`.
- **`perf-1` — capture macrobenchmark numbers once** on a physical device (close the documented
  deferral). Do NOT CI-gate (emulator timings unreliable).
- **`ai-1` — thin `AGENTS.md`** pointer. Portability insurance only.
- **`pm-1` — `/checkpoint`-emitted `docs/agent/BACKLOG.md`** snapshot from `gh issue list`.
- **`docs-2` — trim `STATE.md`** back toward one page (relocate per-PR narrative to RUN_LOG/CHANGELOG).
- **`structure-1`/`structure-2`/`devenv-2` — README hygiene** (test count → pointer; add 4 data
  packages; cross-ref `run-gradle.sh`).

---

## AI-assisted development recommendations

The AI-support tooling is the repo's strongest facet and is credited: `CLAUDE.md` + the `docs/agent/*`
memory spine + the Adversarial Review Gate + executable purity tests + `run-gradle.sh` non-TTY
guidance + the subagent tooling rules. Real additions, in priority order:

1. **Machine-enforce the two prose invariants that an agent can violate green** (`ai-3`, `ai-2`).
   Purity tests already gate *imports*; they do not gate the step-generation allowlist or lock order.
   These are the exact bug classes the project was burned by (#118/#191) and the exact rules an agent
   is most likely to trip. This is the single most important AI-safety improvement.
2. **Coverage ratchet on the fragile zones** (`testing-1`) — the defense against an agent silently
   dropping assertions during a refactor.
3. **A thin `AGENTS.md`** (`ai-1`) — cheap cross-tool portability insurance; Low priority given no
   non-Claude usage exists today. Do not duplicate `CLAUDE.md`; redirect to it.
4. **Backlog self-index** (`pm-1`) — an agent's source of truth is the spine, not chat; a mechanical
   `BACKLOG.md` from `gh issue list` closes the drift the V1X roadmap already warns about.
5. **Verification-command discoverability** — commands are unambiguous but the README's bare `./gradlew`
   block should cross-reference `run-gradle.sh` (`devenv-2`) so a non-TTY agent doesn't hit the hang.

**Where an agent could still do damage the guardrails would not catch:** a new reward path crediting
Steps off in-round state (green build, breaks core economy — `ai-3`); a lock-order inversion or a
collaborator taking its own monitor in the battle engine (green build, latent deadlock/CME — `ai-2`);
gutting test assertions during a refactor (no coverage floor — `testing-1`); an R8 keep-rule
regression (no release-variant build until auto-publish — `cicd-1`). Recommendations 1, 2, and 5 above
plus `cicd-1` close these.

---

## Suggested implementation roadmap

- **Phase 1 — Safety baseline:** `cicd-1` (release variant in CI) · `obs-2`+`obs-1` (crash exit path +
  runbook) · `ai-3`+`ai-2` (invariant tripwires) · `sectooling-1` (secret scanning).
- **Phase 2 — Developer experience:** `devenv-1` (jvmToolchain) · `devenv-2`+`structure-1`+`structure-2`
  (README) · `docs-2` (trim STATE.md) · `pm-1` (BACKLOG.md) · `ai-1` (AGENTS.md).
- **Phase 3 — Quality & reliability:** `testing-1` (Kover ratchet) · `perf-3` (LeakCanary) ·
  `cqt-1` (Compose literal guard) · `db-1` (migration chain test) · `perf-2` (frame overlay).
- **Phase 4 — Release & operations:** `releaseops-1` (versionCode guard) · `releaseops-2` (rollout doc) ·
  `perf-1` (capture benchmark numbers) · `depmgmt-1` (OSS licenses).

---

## Proposed file additions or changes

**Worth adding (justified by a real need):**
- `.github/workflows/gitleaks.yml` (+ repo secret-scanning/push-protection setting) — `sectooling-1`.
- `AGENTS.md` (~15-line redirect to `CLAUDE.md`) — `ai-1`.
- `app/src/test/.../architecture/StepCreditAllowlistTest.kt` — `ai-3`.
- Kover `verify` block in `app/build.gradle.kts` + CI wiring — `testing-1`.
- `LeakCanary` entry in `libs.versions.toml` + `debugImplementation` in `app/build.gradle.kts` — `perf-3`.
- `docs/agent/BACKLOG.md` (generated by `/checkpoint`) — `pm-1`.
- `assembleRelease` step in `ci.yml` — `cicd-1`.
- Edits: `MainActivity.kt` (crash report action), `release-checklist.md` (monitoring section),
  `README.md` (test count / structure tree / non-TTY cross-ref), `STATE.md` (trim).

**Deliberately N/A (do NOT add — confirmed by the audit's N/A findings):**
- `Dockerfile` / devcontainer — Android/Gradle builds need the SDK+emulator; the wrapper + catalog +
  `verification-metadata.xml` already pin the toolchain. No reproducibility gain.
- `.env.example` / OpenAPI / Storybook / `.nvmrc` — no server, no API, no web frontend, no npm.
- `gradle.lockfile` — `verification-metadata.xml` (strict) byte-pins every artifact's sha256, which is
  stronger than resolved-version locking.
- Makefile/Justfile — the task surface is small and already fronted by `run-gradle.sh` + `lint-kotlin.sh`.
- `CONTRIBUTING.md` / `ISSUE_TEMPLATE` / `PULL_REQUEST_TEMPLATE` / CODEOWNERS — solo repo; the
  contribution contract is `CLAUDE.md`'s Agent Protocol.
- Semgrep/MobSF/CodeQL, cert-pinning, root/tamper detection — no server/API/network attack surface
  (cleartext blocked; SDKs handle their own TLS); CodeQL is a documented later-hardening option
  (ADR-0018:82).
- Third-party crash/analytics SDK (Crashlytics/Sentry/Mixpanel) — contradicts the offline-first,
  minimal-data-collection posture; Play Console Vitals is the crash/ANR backstop. The gap is the
  *runbook* (`obs-1`) and the local breadcrumb's *exit path* (`obs-2`), not a network SDK.
- Cloud save / server backup — offline single-player by design; `allowBackup=false` is deliberate.

---

## Commands to verify the project

Real, discovered commands (missing ones flagged):

```bash
# Setup — no install step; open in Android Studio, JDK 17, SDK API 34+ (README "Setup")
#   MISSING: no jvmToolchain pins the local JDK (devenv-1) → add kotlin { jvmToolchain(17) }

# Build (debug)              ./gradlew assembleDebug        # or ./run-gradle.sh assembleDebug (non-TTY)
# Build (release AAB)        ./gradlew bundleRelease         # requires keystore.properties + keystore
#   MISSING in CI: assembleRelease/bundleRelease not run until the v* tag (cicd-1)

# Run                        Android Studio / emulator (no CLI "run" for an app)

# Test (JVM lane)            ./run-gradle.sh testDebugUnitTest
# Test (instrumented lane)   ./run-gradle.sh :app:connectedDebugAndroidTest   # needs API 34+ emulator

# Lint (formatting)          ./lint-kotlin.sh        (--format to auto-fix)
# Lint (Android)             ./run-gradle.sh lintDebug lintRelease
# detekt                     ./run-gradle.sh :app:detekt

# Schema-drift check         ./run-gradle.sh assembleDebug   # re-exports schema; ci.yml git-diff guard
# Coverage (report-only)     ./run-gradle.sh :app:koverXmlReport :app:koverHtmlReport
#   MISSING: no koverVerify gate (testing-1) → add scoped verify

# Security / dependency audit
#   OSV                      google/osv-scanner-action (weekly + push-main, non-gating)
#   Dependency graph         gradle/actions/dependency-submission (push-main, releaseRuntimeClasspath)
#   Supply-chain verify      ./gradlew --write-verification-metadata sha256 --refresh-dependencies … (README)
#   MISSING: secret scanning (sectooling-1) → gh secret-scanning + gitleaks
```

---

## Final judgement

- **Safe to develop quickly today?** Yes. The build/test/lint/CI loop is fast (docs fast-path,
  parallel ktlint job), well-documented, and the executable guards catch the common architectural
  mistakes. First-clone friction is the only real snag (`devenv-1`).
- **Safe for AI-assisted coding?** Mostly — better than almost any solo repo — but with two real
  blind spots: the safety-critical invariants an agent is most likely to break (Steps-generation
  allowlist, battle-engine lock order) are **prose-only**, and there is **no coverage floor** to catch
  an agent silently dropping assertions. Close `ai-3`, `ai-2`, and `testing-1` and it becomes safe.
- **Single most important tooling gap:** **`cicd-1`** — the minified release variant is never
  exercised in CI before the `v*` tag *auto-publishes* it to Play internal, so an R8/shrink break
  reaches testers as a runtime crash with a several-minute feedback loop. (For AI-assisted-coding
  safety specifically, the top gap is `ai-3`/`ai-2` — machine-enforcing the prose invariants.)
- **What to do first:** add `assembleRelease` to the PR gate (`cicd-1`) and the step-credit allowlist
  test (`ai-3`) — both are Low-difficulty, Low-risk, and each closes a path where broken code reaches
  users/ships green today.
