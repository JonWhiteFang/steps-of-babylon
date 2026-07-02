# Phase 1 Tooling-Gap "Safety Baseline" — Design

> **Status:** approved (brainstorming) · **Date:** 2026-07-02 · **Author:** agent session
> **Source:** `docs/reviews/tooling-gap-assessment.md` (2026-07-02 audit) · Tracker **#389**
> **Scope:** the 6 Phase-1 "Safety baseline" findings — close every path where broken code ships green.

## Goal

Close the six Phase-1 "Safety baseline" tooling-gap findings, structured as **3 PRs grouped by kind**.
Phase 1's theme is *"close the paths where broken code ships green"*: an R8 break that reaches Play
before any CI build exercises it (#370), a leaked keystore/secret (#376), the two prose-only invariants
an AI agent is most likely to trip (#371 Steps-generation, #372 lock order), and the crash diagnostic
that never leaves the device (#374 + #380).

Findings (from tracker #389):

| Issue | ID | Kind | One-line |
|---|---|---|---|
| #370 | `cicd-1` (**major**) | CI config | Build the minified `release`/R8 variant in CI before a `v*` tag auto-publishes it |
| #376 | `sectooling-1` | CI + repo settings | Secret scanning / gitleaks / push protection |
| #371 | `ai-3` | new test | Machine-enforce "Steps are never generated in-game" (step-credit allowlist test) |
| #372 | `ai-2` | process + config | Battle-engine lock-order invariant prose-only → make `concurrency-reviewer` mandatory |
| #374 | `obs-2` | prod code | Crash breadcrumb dead-ends → give it a tap-gated report exit path |
| #380 | `obs-1` | doc | Add a post-release monitoring runbook to `release-checklist.md` |

## Grounding facts established during brainstorming (verified against HEAD)

These three facts reshaped the design vs. the audit's verbatim recommendations:

1. **The `play.licenseKey` fail-closed guard WILL trip on a bare `assembleRelease` (#370).**
   `app/build.gradle.kts:170-193` — `gradle.taskGraph.whenReady` matches `Regex("^(bundle|assemble|package).*Release$")`,
   excludes only `Benchmark`/`NonMinified`, and **hard-fails** the build when `play.licenseKey` is blank.
   So a naive `assembleRelease` in the PR gate would fail. → We inject a **throwaway dummy** `play.licenseKey`
   into `local.properties` in CI, never touch the guard.
2. **GitHub-native secret-scanning + push protection are ALREADY enabled (#376).**
   `gh api repos/JonWhiteFang/steps-of-babylon` → `secret_scanning: enabled`,
   `secret_scanning_push_protection: enabled` (public repo). What is left: the gitleaks workflow with a
   custom `*.jks` / `storePassword=` rule, and optionally flipping on
   `secret_scanning_non_provider_patterns` (currently `disabled`).
3. **detekt is `2.0.0-alpha.5` (#372).** Custom-rule authoring on the 2.0 alpha line has an unstable
   API, so the detekt lock-rule is a **timeboxed best-effort**, with a documented fallback.

The real step-credit surface (#371), traced in code — the audit's proposed allowlist
`{DailyStepManager, StepCrossValidator, ClaimSupplyDrop, AwardBattleSteps}` does **not** map cleanly:

- The credit primitive is `PlayerProfileDao.adjustStepBalance(+delta)`.
- Reached two ways: **(a)** `PlayerRepository.addSteps(...)` → `dao.adjustStepBalance(amount)`
  (`PlayerRepositoryImpl.kt:41`); callers of `.addSteps(`: `ClaimSupplyDrop.kt:33`,
  `StepCrossValidator.kt:152`, `DailyStepManager.kt:222/274/310`.
  **(b)** the direct `DailyStepDao.creditBattleStepsAtomic` → `playerDao.adjustStepBalance(credited)`
  (`DailyStepDao.kt:188`) — the `AwardBattleSteps` battle path, which bypasses `addSteps` entirely.
- `spendSteps` calls `dao.adjustStepBalance(-amount)` (negated) — must NOT be flagged.

## PR-A — CI & supply-chain hardening (#370 + #376)

### #370 — assembleRelease in the PR gate
- Add an `assembleRelease` step to the `build-and-test` job in `.github/workflows/ci.yml`, gated on
  `needs.changes.outputs.code == 'true'` (same as every other heavy step), so R8/shrink runs on every
  **code** PR before a `v*` tag can auto-publish an unexercised release variant to Play internal.
- **Guard handling (do NOT modify the fail-closed guard):** immediately before the `assembleRelease`
  invocation, a CI step writes a **throwaway non-blank** `play.licenseKey` into `local.properties`
  (e.g. `play.licenseKey=ci-nonpublishing-placeholder`). This satisfies the `whenReady` guard for a
  non-publishing build while leaving the guard fail-closed for the real `release.yml` publish lane.
  `assembleRelease` needs **no keystore** (the release `signingConfig` is conditional → produces an
  unsigned APK), so no signing secrets are introduced into the PR gate.
- Placement: can share the existing `Lint … / assembleDebug` step or be its own step. Keep it in
  `build-and-test` (not a new job) to reuse the Java/Android/Gradle setup already there.
- Cost: ~2–4 min added to the code-PR gate. Acceptable; this is the audit's #1 gap.
- **Verify:** `./run-gradle.sh assembleRelease` locally (with a dummy `play.licenseKey` in
  `local.properties`) succeeds and produces `app/build/outputs/apk/release/app-release-unsigned.apk`.

### #376 — gitleaks workflow + finish the GitHub-native posture
- Add `.github/workflows/gitleaks.yml`: SHA-pinned `gitleaks/gitleaks-action` (matching the repo's
  SHA-pin convention; Dependabot maintains pins), running on `pull_request` + `push: [main]`.
- Ship a `.gitleaks.toml` custom config extending the defaults with rules that catch what built-ins
  miss: binary keystores (`*.jks` / `*.keystore` file paths) and `.properties` password lines
  (`storePassword=`, `keyPassword=`, `play.licenseKey=`). Allowlist the committed *dummy*/test values
  and the CI placeholder so the gate isn't self-tripping.
- Enable `secret_scanning_non_provider_patterns` via
  `gh api -X PATCH repos/JonWhiteFang/steps-of-babylon -f 'security_and_analysis[secret_scanning_non_provider_patterns][status]=enabled'`
  (confirm exact call with the user before running). Secret-scanning + push-protection are already on.
- Update `docs/steering/security-model.md` to record the now-active secret-scanning posture (native
  scanning + push protection + gitleaks in CI + the custom keystore/password rules).

## PR-B — AI-safety tripwires (#371 + #372)

### #371 — StepCreditAllowlistTest
- New `app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt`,
  modeled on `DomainPurityTest` (walks `src/main` source, scans lines, dependency-free, runs in the
  existing `testDebugUnitTest` suite). Enforces the **real** wallet-write surface, not the audit's
  literal allowlist:
  - **Assertion 1 — `addSteps` callers:** the set of files containing a `.addSteps(` call ==
    exactly `{ClaimSupplyDrop.kt, StepCrossValidator.kt, DailyStepManager.kt}`. Any new caller fails
    with file+line.
  - **Assertion 2 — positive `adjustStepBalance` sites:** a `adjustStepBalance(` call whose argument
    is a **non-negated positive** credit (i.e. not `adjustStepBalance(-…)`) appears only in
    `{PlayerRepositoryImpl.kt (addSteps), DailyStepDao.kt (creditBattleStepsAtomic)}`. The negated
    `spendSteps` site is explicitly allowed.
  - Rationale: this locks the true invariant boundary — the *wallet-credit* sites. The battle path
    (`Simulation.creditSteps` → `SimulationEvent` → `AwardBattleSteps` → `creditBattleStepsAtomic`)
    is the sanctioned bounded exception (ADR-0003) and terminates at `creditBattleStepsAtomic`, which
    is on the allowlist. A new reward path crediting Steps would have to add a new `addSteps`/positive
    `adjustStepBalance` site → the test fails.
  - KDoc cites `CLAUDE.md` Key Domain Concepts + ADR-0003, mirroring `DomainPurityTest`'s doc style.
- **Verify:** `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCredit*'` passes at HEAD; add a
  throwaway extra credit site locally to confirm it fails (then remove).

### #372 — concurrency-reviewer mandatory
- **Primary gate (reliable):** encode a **mandatory `concurrency-reviewer` fan-out lane** in
  `CLAUDE.md`'s Adversarial Review Gate section, triggered on any diff touching
  `presentation/battle/engine/**`, `presentation/battle/effects/**`, a Room DAO
  (`data/local/*Dao.kt`), or a currency-moving repo/use case. (The subagent's own description already
  scopes exactly this; we make it non-optional in the protocol wording.)
- **Build tripwire (best-effort, timeboxed):** attempt a detekt custom rule flagging **nested
  lock acquisition** (a `synchronized`/`ReentrantLock` acquired while another is held) in the engine.
  - **Fallback (if the detekt 2.0-alpha custom-rule API is impractical within the timebox):** ship a
    lightweight `DomainPurityTest`-style **source-scan test** instead — e.g. fail if a battle-engine
    *collaborator* (`UWController`/`CombatResolver`/`BuffTickers`/`BattleRenderer`) declares its own
    `synchronized(`/`ReentrantLock`/`Object()` monitor (the "collaborators hold no monitor of their
    own" invariant — `CLAUDE.md` Battle Renderer note). Document the detekt rule as a deferred
    follow-up issue. Report which path we landed on in the PR.

## PR-C — Crash observability (#374 + #380)

### #374 — crash-breadcrumb report exit path
- In `MainActivity.kt` (`LaunchedEffect` at ~271-283): the crash snackbar gains a **"Report" action**.
  - On `SnackbarResult.ActionPerformed` → fire an `ACTION_SENDTO` `mailto:jonwhitefang@gmail.com`
    intent pre-filled with subject + body containing `exceptionClass`, `message`, `stackPreview`, and
    app metadata (`versionName`/`versionCode`, Android release). Guarded-intent idiom (resolve before
    launch, mirroring `openPrivacyPolicy`/`requestBatteryExemption`) so a device with no mail client
    is a safe no-op.
  - **Defer `clear()`:** call `crashBreadcrumbStore.clear()` only after the snackbar is
    dismissed/actioned (i.e. after `showSnackbar` returns), not before — so a rotation while the
    notice is visible can't silently drop the breadcrumb. Follow the existing "reset AFTER await"
    reasoning already documented for the step-permission hint.
  - New string resources for the snackbar action label + email subject/body scaffold (localizable —
    the repo promotes `HardcodedText` to a lint error).
- Update the `#190 REL-1` inline comment (currently says "there is no in-app report channel to wire
  an action to" — that becomes false).

### #380 — post-release monitoring runbook
- Add a **"Post-release monitoring"** section to `docs/release/release-checklist.md` (currently ends
  at "Build Outputs"): 24h/72h Play Vitals cadence, crash-rate / ANR-rate thresholds, a roll-back
  trigger, and a cross-reference to the monitoring guidance in `plan-31-walkthrough.md`. Note that
  `mapping.txt` upload is already automated in `release.yml`.

## Cross-cutting

- **Doc sync (PR Task-List Convention — runs BEFORE the STATE/RUN_LOG update, in every PR):**
  - PR-A: `CHANGELOG.md`; `docs/steering/security-model.md` (#376 posture); STATE/RUN_LOG.
  - PR-B: `CLAUDE.md` (Testing headline count +N; Review-Gate wiring for #372);
    `docs/steering/source-files.md` (new test file); `CHANGELOG.md`; STATE/RUN_LOG. ADR for the #372
    enforcement decision (doc-protocol + detekt/fallback).
  - PR-C: `CHANGELOG.md`; STATE/RUN_LOG. (No schema/dep/structure change.)
  - Tick tracker **#389** checkboxes as each PR merges.
- **Adversarial Review Gate:** each PR's implementation plan passes the mandatory review gate before
  implementation; PR-B additionally exercises the new mandatory `concurrency-reviewer` lane it defines
  (dogfood) since it touches no engine code — the lane triggers only on engine/DAO diffs, so PR-B
  itself won't, but the wiring is validated by review.
- **Sequencing:** PR-A → PR-B → PR-C. Independent branches off `main`; this order front-loads the
  only `severity:major` finding (#370).

## Out of scope (explicit)

- All Phase-2/3/4 findings (tracker #389).
- Modifying the fail-closed `play.licenseKey` guard **logic** (we only feed it a dummy value in CI).
- A third-party crash/analytics SDK (audit-confirmed N/A — offline-first posture; Play Vitals is the
  backstop; the gap is the local breadcrumb's *exit path* + the *runbook*, not a network SDK).
- Any change to the `release.yml` publish lane behavior (it already builds the real signed AAB).

## Verification summary

| PR | Command / check |
|---|---|
| A #370 | `./run-gradle.sh assembleRelease` (dummy `play.licenseKey`) → unsigned release APK |
| A #376 | gitleaks action runs green on a clean tree; a planted `storePassword=` / `*.jks` test blob is caught locally; `gh api … secret-scanning/alerts` reachable |
| B #371 | `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCredit*'` green at HEAD; red on a planted extra credit site |
| B #372 | `./run-gradle.sh :app:detekt` green (rule active) OR the fallback source-scan test green; CLAUDE.md wiring present |
| C #374 | JVM build green; manual (crash → relaunch → tap Report → mail composer opens); optional Robolectric test on the action |
| C #380 | doc renders; cross-refs resolve |
