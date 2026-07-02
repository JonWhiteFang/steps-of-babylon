# Phase 1 Tooling-Gap "Safety Baseline" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the six Phase-1 "Safety baseline" tooling-gap findings (GitHub #370, #376, #371, #372, #374, #380) so no path lets broken code ship green.

**Architecture:** Three independent PRs off `main`, grouped by kind. **PR-A** hardens CI/supply-chain (build the R8 release variant in the PR gate; add gitleaks). **PR-B** adds two AI-safety tripwires (a source-scanning step-credit allowlist test; a mandatory concurrency-reviewer trigger via the PreToolUse hook + protocol, with a comment-stripped lock-scan fallback test). **PR-C** gives the crash breadcrumb an email exit path (with the required `<queries>` manifest entry) and a post-release monitoring runbook. Order: A → B → C (front-loads the only `severity:major`, #370).

**Tech Stack:** GitHub Actions (SHA-pinned), Gradle 9.6 Kotlin DSL, AGP 9, JUnit Jupiter (JVM lane) modeled on `DomainPurityTest`, Robolectric Compose lane, Jetpack Compose `MainActivity`, `gitleaks-action`, bash PreToolUse hook.

**Source spec:** `docs/superpowers/specs/2026-07-02-phase1-tooling-gap-design.md` (adversarial-review gate PASSED: 19 raised · 15 applied · 4 refuted).

**Global conventions (apply to every PR):**
- Branch off `main` first (repo rule: never commit directly to `main`). Branch names below.
- Build/test via `./run-gradle.sh <task>` (non-TTY safe), never bare `./gradlew` in this harness.
- Structural Kotlin search: `sg -l kotlin -p '<pattern>'`; file discovery: `fd`. Formatting gate: `./lint-kotlin.sh` (`--format` to fix); code-smell gate: `./run-gradle.sh :app:detekt`.
- Every PR ends with the PR Task-List Convention steps: **(1) sync current-state docs, (2) update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`**, immediately before the commit/PR step.
- Tick the matching checkbox in tracker issue **#389** as each PR merges.

---

## File Structure

**PR-A (branch `ci/phase1-release-variant-and-gitleaks`)**
- Modify: `.github/workflows/ci.yml` — add a dummy-license-key step + `assembleRelease` to `build-and-test`.
- Create: `.github/workflows/gitleaks.yml` — SHA-pinned gitleaks scan on PR + push-main.
- Create: `.gitleaks.toml` — extend defaults with `*.jks` / `storePassword=` rules + allowlist for the CI placeholder.
- Modify: `docs/steering/security-model.md` — record the secret-scanning posture.
- Modify: `CHANGELOG.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`.
- Repo settings (via `gh api`, one-time): enable `secret_scanning_non_provider_patterns`.

**PR-B (branch `test/phase1-ai-safety-tripwires`)**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt` — the #371 guard (4 assertions + negative fixture).
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/architecture/BattleEngineLockScanTest.kt` — the #372 fallback lock-scan (comment-stripped, collaborator-scoped).
- Modify: `.claude/hooks/guard-sensitive-edits.sh` — add advisory tier 4 (concurrency-reviewer trigger surface).
- Modify: `CLAUDE.md` — Adversarial Review Gate wiring (mandatory concurrency-reviewer lane) + Testing headline count.
- Create: `docs/agent/DECISIONS/ADR-0038-concurrency-reviewer-enforcement.md`.
- Modify: `docs/steering/source-files.md` (new test files), `CHANGELOG.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`.

**PR-C (branch `feat/phase1-crash-report-and-runbook`)**
- Modify: `app/src/main/AndroidManifest.xml` — add the `<queries>` SENDTO/mailto element.
- Modify: `app/src/main/res/values/strings.xml` — Report action label + email subject/body strings.
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt` — extract a top-level `sendCrashReport(...)` fn; add the snackbar Report action; update the #190 comment.
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/CrashReportIntentTest.kt` — Robolectric test of the extracted intent builder.
- Modify: `docs/release/release-checklist.md` — Post-release monitoring section.
- Modify: `CLAUDE.md` (test count, if the Robolectric test lands), `docs/steering/source-files.md`, `CHANGELOG.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`.

---

# PR-A — CI & supply-chain hardening (#370 + #376)

**Branch:** `ci/phase1-release-variant-and-gitleaks`

### Task A0: Branch

- [ ] **Step 1: Create the branch**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git checkout main && git pull
git checkout -b ci/phase1-release-variant-and-gitleaks
```

### Task A1: Prove `assembleRelease` builds locally with a dummy license key (#370)

This is the local pre-flight for the CI change: confirm the fail-closed guard tolerates a dummy key and R8 runs.

- [ ] **Step 1: Add a throwaway license key to `local.properties`**

`local.properties` is gitignored. Append a placeholder (create the file if absent):

```bash
grep -q '^play.licenseKey=' local.properties 2>/dev/null || echo 'play.licenseKey=ci-nonpublishing-placeholder' >> local.properties
```

- [ ] **Step 2: Run `assembleRelease` and capture output**

Run: `./run-gradle.sh assembleRelease > build.log 2>&1; tail -n 20 build.log`
Expected: `BUILD SUCCESSFUL`. If it fails on the license-key guard, the placeholder line is missing — re-check Step 1. If it fails on NDK/`debugSymbolLevel`, note the error (the release buildType has `ndk { debugSymbolLevel = "FULL" }`, `app/build.gradle.kts:~128`) — on a local dev machine an NDK may need installing; this is the "known first-run risk" the spec flags.

- [ ] **Step 3: Confirm R8 ran and an APK exists**

Run:
```bash
grep -c 'minifyReleaseWithR8' build.log
ls app/build/outputs/apk/release/*.apk
```
Expected: the grep count is ≥1 (R8 task ran) and at least one `*.apk` exists (name is AGP-dependent — `app-release-unsigned.apk` for the no-keystore case). Do not hardcode the filename anywhere.

### Task A2: Add the release-variant build to the PR gate (#370)

**Files:** Modify `.github/workflows/ci.yml` (the `build-and-test` job, after the existing lint/test/assembleDebug step at line 101-103).

- [ ] **Step 1: Add the dummy-license-key step + `assembleRelease` step**

In `.github/workflows/ci.yml`, immediately **after** the existing step:

```yaml
      - name: Lint (debug + release), unit tests, debug build
        if: needs.changes.outputs.code == 'true'
        run: ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug --stacktrace
```

insert these two new steps (the dummy-key step MUST be separate and run before Gradle configures, because `local.properties` is loaded once at configuration time in `app/build.gradle.kts:31-34` and read by the `whenReady` guard at ~line 190):

```yaml
      # #370 (cicd-1): build the MINIFIED release variant so an R8/shrink keep-rule regression fails
      # HERE, on every code PR, instead of reaching Play internal when a v* tag auto-publishes.
      # The play.licenseKey guard (app/build.gradle.kts whenReady) hard-fails a blank key on any
      # *Release assemble/bundle, so inject a THROWAWAY non-publishing placeholder into local.properties
      # FIRST (separate step — local.properties is read at Gradle configuration time). This is NOT a
      # real key: assembleRelease produces an UNSIGNED APK (release signingConfig is applied only
      # `if (keystorePropertiesFile.exists())`, which is false in the secret-free PR gate), so no
      # signing/publishing can occur. The real release.yml publish lane still injects the real key
      # and keeps the guard fail-closed.
      - name: Seed non-publishing release license key
        if: needs.changes.outputs.code == 'true'
        run: echo 'play.licenseKey=ci-nonpublishing-placeholder' >> local.properties

      - name: Assemble release variant (R8/shrink guard; unsigned, non-publishing)
        if: needs.changes.outputs.code == 'true'
        run: ./gradlew assembleRelease --stacktrace
```

- [ ] **Step 2: Ensure the seeded key is cleaned up (defensive)**

Add, at the end of the `build-and-test` job's steps (after "Upload reports"), a cleanup step so a future artifact-capture step can't archive it:

```yaml
      - name: Clean up seeded license key
        if: always() && needs.changes.outputs.code == 'true'
        run: rm -f local.properties
```

- [ ] **Step 3: Validate the workflow YAML locally**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('ci.yml OK')"`
Expected: `ci.yml OK` (no YAML parse error).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(#370): build the minified release/R8 variant in the PR gate

Adds assembleRelease to build-and-test so an R8/shrink keep-rule regression
fails on every code PR instead of reaching Play internal when a v* tag
auto-publishes. Seeds a throwaway non-publishing play.licenseKey (separate
step, before Gradle config) to satisfy the fail-closed guard; the build is
unsigned (no keystore in the PR gate) so nothing can publish. Cleans up the
seeded key after the job."
```

### Task A3: Add the gitleaks config (#376)

**Files:** Create `.gitleaks.toml`.

- [ ] **Step 1: Write `.gitleaks.toml`**

Create `/Users/jpawhite/Documents/Claude/steps-of-babylon/.gitleaks.toml`:

```toml
# gitleaks config (#376 / sectooling-1). Extends the built-in default ruleset (which misses
# binary keystores and .properties password lines) with app-specific rules. The CI
# non-publishing placeholder and any test/dummy values are allowlisted so the gate isn't
# self-tripping.
title = "Steps of Babylon gitleaks config"

[extend]
useDefault = true

[[rules]]
id = "android-keystore-file"
description = "Committed Android keystore (binary signing material)"
# Binary keystores have no reliable text signature — match by path/extension.
path = '''.*\.(jks|keystore)$'''

[[rules]]
id = "keystore-properties-password"
description = "Signing/keystore password in a .properties file"
regex = '''(?i)(storePassword|keyPassword)\s*=\s*.+'''

[[rules]]
id = "play-license-key"
description = "Play licensing RSA public key committed to source"
regex = '''(?i)play\.licenseKey\s*=\s*.+'''

[allowlist]
description = "Non-secret placeholders and test fixtures"
regexes = [
  '''play\.licenseKey\s*=\s*ci-nonpublishing-placeholder''',
]
paths = [
  '''\.gitleaks\.toml''',
  '''docs/.*''',
]
```

- [ ] **Step 2: Sanity-check the TOML parses**

Run: `python3 -c "import tomllib; tomllib.load(open('.gitleaks.toml','rb')); print('gitleaks.toml OK')"`
Expected: `gitleaks.toml OK`.

- [ ] **Step 3: Commit**

```bash
git add .gitleaks.toml
git commit -m "ci(#376): add gitleaks config with keystore + .properties password rules

Extends the default ruleset with rules the built-ins miss: binary *.jks/
*.keystore files, storePassword=/keyPassword= .properties lines, and a
committed play.licenseKey. Allowlists the CI non-publishing placeholder and
docs so the gate isn't self-tripping."
```

### Task A4: Add the gitleaks workflow (#376)

**Files:** Create `.github/workflows/gitleaks.yml`.

- [ ] **Step 1: Write the workflow (SHA-pinned, matching repo convention)**

Create `/Users/jpawhite/Documents/Claude/steps-of-babylon/.github/workflows/gitleaks.yml`:

```yaml
# #376 (sectooling-1): scan diffs + history for committed secrets. Complements GitHub-native
# secret-scanning + push-protection (already enabled) with a repo-committed gate that catches
# what the native patterns miss (binary keystores, .properties password lines) via .gitleaks.toml.
# SHA-pinned per repo convention (Dependabot maintains the pin).
name: gitleaks

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  gitleaks:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
        with:
          fetch-depth: 0 # full history so the scan sees every commit on the PR/branch
      - name: gitleaks
        uses: gitleaks/gitleaks-action@e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e # v3.0.0
        env:
          GITLEAKS_CONFIG: .gitleaks.toml
```

- [ ] **Step 2: Validate the workflow YAML**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/gitleaks.yml')); print('gitleaks.yml OK')"`
Expected: `gitleaks.yml OK`.

- [ ] **Step 3: (Optional local dry-run) plant a fake secret and confirm the rules would catch it**

If `gitleaks` is installed locally (`command -v gitleaks`), verify the custom rules fire:
```bash
printf 'storePassword=hunter2\n' > /tmp/fake.properties
gitleaks detect --no-git --source /tmp/fake.properties --config .gitleaks.toml || echo "gitleaks flagged it (expected non-zero)"
rm -f /tmp/fake.properties
```
Expected: gitleaks reports a finding (non-zero exit). If `gitleaks` isn't installed, skip — the workflow validates in CI.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/gitleaks.yml
git commit -m "ci(#376): add SHA-pinned gitleaks workflow (PR + push-main)

Repo-committed secret scan using .gitleaks.toml, complementing the already-on
GitHub-native secret-scanning + push protection. Full history checkout so the
scan sees every commit on the branch."
```

### Task A5: Enable `secret_scanning_non_provider_patterns` (#376, repo settings)

**Files:** none (GitHub repo setting via `gh api`).

- [ ] **Step 1: Confirm current state**

Run: `gh api repos/JonWhiteFang/steps-of-babylon --jq '.security_and_analysis.secret_scanning_non_provider_patterns.status'`
Expected: `disabled` (secret_scanning + push_protection are already `enabled`).

- [ ] **Step 2: Enable non-provider patterns**

⚠️ This is an outward-facing repo-settings change — confirm with the developer before running. Then:
```bash
gh api -X PATCH repos/JonWhiteFang/steps-of-babylon \
  -f 'security_and_analysis[secret_scanning_non_provider_patterns][status]=enabled'
```

- [ ] **Step 3: Verify**

Run: `gh api repos/JonWhiteFang/steps-of-babylon --jq '.security_and_analysis.secret_scanning_non_provider_patterns.status'`
Expected: `enabled`.

### Task A6: Document the secret-scanning posture (#376 doc-sync)

**Files:** Modify `docs/steering/security-model.md`.

- [ ] **Step 1: Read the file and find the right section**

Run: `rg -n 'secret|scanning|gitignore|keystore' docs/steering/security-model.md | head`
Read the surrounding section so the new content matches the doc's structure/tone.

- [ ] **Step 2: Add a "Secret scanning" subsection**

Add (adapt the heading level to the file's existing structure):

```markdown
### Secret scanning (#376)

Committed-secret defense is layered:

- **GitHub-native secret scanning + push protection** — enabled at the repo level (public repo).
  Push protection blocks a push that introduces a recognized secret; non-provider patterns are
  also enabled. Alerts: `gh api repos/JonWhiteFang/steps-of-babylon/secret-scanning/alerts`.
- **gitleaks CI gate** (`.github/workflows/gitleaks.yml` + `.gitleaks.toml`) — repo-committed,
  runs on every PR and push to `main` over full history. Extends the default ruleset with rules
  the built-ins miss: binary `*.jks`/`*.keystore` files and `storePassword=`/`keyPassword=`/
  `play.licenseKey=` `.properties` lines. The CI non-publishing placeholder is allowlisted.
- **`.gitignore`** — the first line of defense (keystore.properties/local.properties/keystores),
  now backstopped by the two scanners above rather than being the only guard.
```

- [ ] **Step 3: Commit**

```bash
git add docs/steering/security-model.md
git commit -m "docs(#376): record the layered secret-scanning posture in security-model.md"
```

### Task A7: PR-A doc-sync + STATE/RUN_LOG (Task-List Convention) and PR

- [ ] **Step 1: Add the CHANGELOG entry**

In `CHANGELOG.md`, under `## [Unreleased]`, add a new section (above the existing docs sections):

```markdown
### CI/CD — release-variant build + secret scanning (#370, #376)

- **#370 (cicd-1, severity:major).** The minified `release`/R8 variant is now assembled in the PR
  gate (`ci.yml` `build-and-test`), so an R8/shrink keep-rule regression fails on every code PR
  instead of reaching Play internal when a `v*` tag auto-publishes. A throwaway non-publishing
  `play.licenseKey` is seeded (separate step) so the fail-closed guard tolerates the build; the
  build is unsigned (no keystore in the PR gate). Seeded key is cleaned up after the job.
- **#376 (sectooling-1).** Added a SHA-pinned gitleaks workflow (`gitleaks.yml` + `.gitleaks.toml`)
  with custom rules for `*.jks`/`*.keystore` and `storePassword=`/`keyPassword=`/`play.licenseKey=`
  `.properties` lines (built-ins miss these). Enabled GitHub non-provider secret-scanning patterns
  (native scanning + push protection were already on). Posture documented in `security-model.md`.
```

- [ ] **Step 2: Update STATE.md**

In `docs/agent/STATE.md`, update the CURRENT/NEXT lines to note PR-A of the Phase-1 tooling-gap work is up (release-variant CI + gitleaks), and PR-B (AI tripwires) / PR-C (crash report) are next. Keep it to the existing one-page shape.

- [ ] **Step 3: Append RUN_LOG.md**

Append a new dated entry to `docs/agent/RUN_LOG.md` summarizing PR-A: what changed (#370 + #376), the design/review provenance (spec + adversarial gate), and what remains (PR-B, PR-C).

- [ ] **Step 4: Commit the doc sync**

```bash
git add CHANGELOG.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#370,#376): CHANGELOG + STATE + RUN_LOG for PR-A (release CI + gitleaks)"
```

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin ci/phase1-release-variant-and-gitleaks
gh pr create --title "ci: build release/R8 variant in PR gate + gitleaks (#370, #376)" \
  --body "Phase 1 tooling-gap PR-A. Closes #370, closes #376. Spec: docs/superpowers/specs/2026-07-02-phase1-tooling-gap-design.md (adversarial-review gate passed). Tracker: #389."
```

- [ ] **Step 6: Confirm CI is green, then tick #389**

Watch: `gh pr checks --watch`. When green (note the new `assembleRelease` + `gitleaks` steps ran), tick the #370 and #376 boxes in tracker #389 (edit the issue body via `gh issue edit 389`).

---

# PR-B — AI-safety tripwires (#371 + #372)

**Branch:** `test/phase1-ai-safety-tripwires`

### Task B0: Branch

- [ ] **Step 1: Create the branch**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git checkout main && git pull
git checkout -b test/phase1-ai-safety-tripwires
```

### Task B1: Write `StepCreditAllowlistTest` — Assertion 1 (`.addSteps(` callers) (#371)

**Files:** Create `app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt`.

This test walks `src/main`, comment-strips each file (reusing the `DomainPurityTest.stripComments` approach), and pins the credit surface. Build it assertion-by-assertion. Note: unit tests run with the `app/` module dir as the working directory, so the source root is `File("src/main/java/...")` (see `DomainPurityTest.kt:50`).

- [ ] **Step 1: Write the file with Assertion 1 + the shared helpers**

Create the file:

```kotlin
package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Machine-enforces the #1 hard design rule (`CLAUDE.md` Key Domain Concepts; ADR-0003):
 * **"Steps are never generated in-game"** — the only sanctioned exception is the bounded
 * battle-step reward (`AwardBattleSteps`, `DAILY_BATTLE_STEP_CAP`), which terminates at
 * `DailyStepDao.creditBattleStepsAtomic` (on the allowlist below). Closes GitHub #371 (`ai-3`).
 *
 * The invariant boundary is the set of `PlayerProfileDao` methods that can raise
 * `currentStepBalance`. There are THREE writers (verified against `PlayerProfileDao.kt`):
 *  - `adjustStepBalance(delta)`   — relative credit/debit (`SET … MAX(0, … + :delta)`)
 *  - `updateStepBalance(balance)` — absolute setter (`SET currentStepBalance = :balance`); ZERO
 *                                   production callers today — a latent landmine.
 *  - `upsert(PlayerProfileEntity)` — full-row write incl. the balance.
 * `adjustStepBalanceIfSufficient(cost)` is the guarded SPEND primitive (a debit) — and note the
 * substring `.adjustStepBalance(` is NOT contained in `.adjustStepBalanceIfSufficient(` (the `I`
 * breaks the token), so no special-casing is needed to avoid a false collision.
 *
 * Dependency-free (mirrors `DomainPurityTest`): walks `src/main`, comment-strips, scans lines. Any
 * NEW credit site fails the build with the offending file. The pure line-matching predicates are
 * isolated from the file walk so they are unit-testable directly (see the negative-fixture test).
 */
class StepCreditAllowlistTest {
    private val srcMain = File("src/main/java/com/whitefang/stepsofbabylon")

    private fun kotlinFiles(): List<File> {
        assertTrue(srcMain.isDirectory) {
            "src/main root not found at ${srcMain.absolutePath} (working dir = ${File(".").absolutePath})"
        }
        return srcMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Removes `/* … */` (incl. KDoc) block comments and `//` line comments so scans see executable
     * code only. Same pragmatic approach as `DomainPurityTest.stripComments` (no string-literal
     * awareness — a credit call never legitimately hides inside a string literal).
     */
    private fun stripComments(source: String): String {
        val noBlock =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                "\n".repeat(m.value.count { it == '\n' })
            }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    /** Files (by name) that contain a qualified `.addSteps(` CALL. */
    internal fun addStepsCallerFiles(files: List<File>): Set<String> =
        files.filter { stripComments(it.readText()).contains(".addSteps(") }
            .map { it.name }
            .toSet()

    @Test
    fun `addSteps is called only from the sanctioned use cases`() {
        val expected = setOf("ClaimSupplyDrop.kt", "StepCrossValidator.kt", "DailyStepManager.kt")
        val actual = addStepsCallerFiles(kotlinFiles())
        assertEquals(expected, actual) {
            "Unexpected .addSteps( caller set. Steps may only be credited from the sanctioned " +
                "sites (ADR-0003). Expected=$expected Actual=$actual. A new credit site is a " +
                "'Steps generated in-game' invariant break — see CLAUDE.md Key Domain Concepts."
        }
    }
}
```

- [ ] **Step 2: Run Assertion 1 — expect PASS at HEAD**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCreditAllowlistTest*' > build.log 2>&1; tail -n 25 build.log`
Expected: PASS. If the actual set differs, re-verify callers with `sg -l kotlin -p '$X.addSteps($$$)' app/src/main` and reconcile the `expected` set (do not silently widen it — a new caller is the thing the test exists to catch).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt
git commit -m "test(#371): StepCreditAllowlistTest assertion 1 — pin .addSteps( callers"
```

### Task B2: Add Assertion 2 (positive `.adjustStepBalance(` call sites) (#371)

**Files:** Modify `StepCreditAllowlistTest.kt`.

- [ ] **Step 1: Add the predicate + test**

Add these members to the class (below `addStepsCallerFiles`):

```kotlin
    /**
     * Files containing a qualified positive-credit `.adjustStepBalance(` CALL — i.e. NOT a negated
     * debit `.adjustStepBalance(-…)`. The leading `.` excludes the interface DECLARATION in
     * `PlayerProfileDao.kt` (`suspend fun adjustStepBalance(delta: Long)`), and the `-` check
     * excludes `PlayerRepositoryImpl.spendSteps` (`dao.adjustStepBalance(-amount)`).
     */
    internal fun positiveAdjustStepBalanceFiles(files: List<File>): Set<String> =
        files.filter { file ->
            stripComments(file.readText())
                .lineSequence()
                .any { line ->
                    val idx = line.indexOf(".adjustStepBalance(")
                    if (idx < 0) {
                        false
                    } else {
                        // char right after the "(" — a debit starts with '-'
                        val argStart = idx + ".adjustStepBalance(".length
                        line.getOrNull(argStart) != '-'
                    }
                }
        }.map { it.name }.toSet()

    @Test
    fun `positive adjustStepBalance calls are confined to the sanctioned wallet-credit sites`() {
        val expected = setOf("PlayerRepositoryImpl.kt", "DailyStepDao.kt")
        val actual = positiveAdjustStepBalanceFiles(kotlinFiles())
        assertEquals(expected, actual) {
            "Unexpected positive .adjustStepBalance( site. Only PlayerRepositoryImpl.addSteps and " +
                "DailyStepDao.creditBattleStepsAtomic may credit Steps (ADR-0003). " +
                "Expected=$expected Actual=$actual."
        }
    }
```

- [ ] **Step 2: Run — expect PASS at HEAD**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCreditAllowlistTest*' > build.log 2>&1; tail -n 25 build.log`
Expected: PASS (both assertions). If `PlayerProfileDao.kt` appears in `actual`, the leading-`.` guard failed — confirm the scan uses `.adjustStepBalance(` (with the dot), not the bare token.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt
git commit -m "test(#371): assertion 2 — confine positive .adjustStepBalance( to credit sites"
```

### Task B3: Add Assertion 3 (`updateStepBalance` + `upsert`) & Assertion 4 (DAO write-surface pin) (#371)

**Files:** Modify `StepCreditAllowlistTest.kt`.

- [ ] **Step 1: Add Assertion 3 (the landmine paths)**

Add to the class:

```kotlin
    /** Files with a qualified `.updateStepBalance(` CALL (the absolute setter — zero callers today). */
    internal fun updateStepBalanceCallerFiles(files: List<File>): Set<String> =
        files.filter { stripComments(it.readText()).contains(".updateStepBalance(") }
            .map { it.name }.toSet()

    /** Files that construct a `PlayerProfileEntity(` (which `upsert` writes as a full row). */
    internal fun playerProfileEntityConstructionFiles(files: List<File>): Set<String> =
        files.filter { stripComments(it.readText()).contains("PlayerProfileEntity(") }
            .map { it.name }.toSet()

    @Test
    fun `absolute step-balance setter has no production caller`() {
        val actual = updateStepBalanceCallerFiles(kotlinFiles())
        assertTrue(actual.isEmpty()) {
            "PlayerProfileDao.updateStepBalance(balance) is an ABSOLUTE step-balance setter with no " +
                "sanctioned caller — a new caller (e.g. updateStepBalance(current + reward)) would " +
                "generate Steps in-game (ADR-0003). Offending files: $actual."
        }
    }

    @Test
    fun `PlayerProfileEntity construction is confined to the profile-creation seam`() {
        // ensureProfileExists (PlayerRepositoryImpl) constructs a zeroed default row via upsert.
        val expected = setOf("PlayerRepositoryImpl.kt")
        val actual = playerProfileEntityConstructionFiles(kotlinFiles())
        assertEquals(expected, actual) {
            "PlayerProfileEntity is constructed outside the profile-creation seam. An upsert of an " +
                "entity with a raised currentStepBalance would generate Steps (ADR-0003). " +
                "Expected=$expected Actual=$actual."
        }
    }
```

- [ ] **Step 2: Verify the Assertion-3 expectations against HEAD before running**

Run:
```bash
sg -l kotlin -p '$X.updateStepBalance($$$)' app/src/main   # expect: no matches
rg -n 'PlayerProfileEntity\(' app/src/main --glob '*.kt'   # expect: only PlayerRepositoryImpl.kt
```
Expected: `updateStepBalance` has zero call sites; `PlayerProfileEntity(` appears only in `PlayerRepositoryImpl.kt`. If `PlayerProfileEntity(` appears elsewhere (e.g. a mapper), add that file to `expected` with a code comment explaining why it's a safe construction (not a balance-raising upsert), and note it in the KDoc.

- [ ] **Step 3: Add Assertion 4 (DAO write-surface pin)**

Add to the class:

```kotlin
    @Test
    fun `only sanctioned PlayerProfileDao queries write currentStepBalance`() {
        val daoFile =
            kotlinFiles().firstOrNull { it.name == "PlayerProfileDao.kt" }
                ?: error("PlayerProfileDao.kt not found under $srcMain")
        val stripped = stripComments(daoFile.readText())
        // Every @Query whose SQL writes the balance must be one of the sanctioned methods. We detect
        // a "write" as the SQL fragment `currentStepBalance =` (an assignment target in an UPDATE).
        // Sanctioned methods (their SQL contains that fragment): adjustStepBalance,
        // adjustStepBalanceIfSufficient, updateStepBalance. If a NEW @Query introduces the fragment,
        // the count rises and this test fails — forcing a review of the new balance writer.
        val writeFragmentCount =
            Regex("""currentStepBalance\s*=""").findAll(stripped).count()
        // 3 sanctioned writers at HEAD (see class KDoc). Assert exact count so a new writer trips it.
        assertEquals(3, writeFragmentCount) {
            "PlayerProfileDao has an unexpected number of `currentStepBalance =` write sites " +
                "($writeFragmentCount, expected 3: adjustStepBalance, adjustStepBalanceIfSufficient, " +
                "updateStepBalance). A NEW query writing the balance must be reviewed against the " +
                "'Steps never generated in-game' invariant (ADR-0003) before this count is updated."
        }
    }
```

- [ ] **Step 4: Verify the write-fragment count against HEAD before running**

Run: `rg -c 'currentStepBalance\s*=' app/src/main/java/com/whitefang/stepsofbabylon/data/local/PlayerProfileDao.kt`
Expected: `3` (lines 16, 38, 52 — the three writers; the `MAX(0, currentStepBalance + …)` on line 38 has a single `currentStepBalance =` assignment target). If the count differs, reconcile the literal `3` in the test and document why in the KDoc.

- [ ] **Step 5: Run all four assertions — expect PASS at HEAD**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCreditAllowlistTest*' > build.log 2>&1; tail -n 30 build.log`
Expected: PASS (4 tests). Debug any count/set mismatch by re-running the verify commands above.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt
git commit -m "test(#371): assertions 3+4 — pin updateStepBalance/upsert + DAO write surface"
```

### Task B4: Add the baked negative fixture (#371)

**Files:** Modify `StepCreditAllowlistTest.kt`. Proves the matcher goes red on a rogue site (guards against the #228 "guard that never fails" rot).

- [ ] **Step 1: Add the negative-fixture test**

The predicates take a `List<File>`; write synthetic files to a JUnit temp dir and assert the predicates flag them. Add the import and test:

```kotlin
    // add to imports at top of file:
    // import org.junit.jupiter.api.io.TempDir

    @Test
    fun `matchers flag a rogue credit site (negative fixture)`(@org.junit.jupiter.api.io.TempDir tmp: File) {
        val rogueAddSteps = File(tmp, "RogueReward.kt").apply {
            writeText("class RogueReward { suspend fun go() { repo.addSteps(500L) } }")
        }
        val roguePositiveAdjust = File(tmp, "RogueDao.kt").apply {
            writeText("class RogueDao { suspend fun go() { dao.adjustStepBalance(500L) } }")
        }
        val rogueUpdate = File(tmp, "RogueSetter.kt").apply {
            writeText("class RogueSetter { suspend fun go() { dao.updateStepBalance(999L) } }")
        }
        val files = listOf(rogueAddSteps, roguePositiveAdjust, rogueUpdate)

        assertTrue("RogueReward.kt" in addStepsCallerFiles(files)) {
            "matcher failed to flag a rogue .addSteps( site — the guard would be a no-op"
        }
        assertTrue("RogueDao.kt" in positiveAdjustStepBalanceFiles(files)) {
            "matcher failed to flag a rogue positive .adjustStepBalance( site"
        }
        assertTrue("RogueSetter.kt" in updateStepBalanceCallerFiles(files)) {
            "matcher failed to flag a rogue .updateStepBalance( site"
        }
        // And a NEGATED debit must NOT be flagged as a positive credit:
        val debit = File(tmp, "Spend.kt").apply {
            writeText("class Spend { suspend fun go() { dao.adjustStepBalance(-500L) } }")
        }
        assertTrue("Spend.kt" !in positiveAdjustStepBalanceFiles(listOf(debit))) {
            "negated .adjustStepBalance(-…) debit was wrongly flagged as a positive credit"
        }
    }
```

- [ ] **Step 2: Run — expect PASS**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCreditAllowlistTest*' > build.log 2>&1; tail -n 30 build.log`
Expected: PASS (5 tests). The negative fixture proves each matcher fires on a rogue site and that the sign-check excludes debits.

- [ ] **Step 3: Run detekt + ktlint on the new test**

Run: `./lint-kotlin.sh && ./run-gradle.sh :app:detekt > build.log 2>&1; tail -n 15 build.log`
Expected: both green (no NEW violations). If ktlint flags formatting, run `./lint-kotlin.sh --format`.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt
git commit -m "test(#371): baked negative fixture — prove the matchers go red on rogue sites"
```

### Task B5: Write the #372 fallback lock-scan test

**Files:** Create `app/src/test/java/com/whitefang/stepsofbabylon/architecture/BattleEngineLockScanTest.kt`.

This is the default #372 build tripwire (the detekt custom rule is deferred — see ADR in Task B7). It's comment-stripped and scoped to the collaborator allowlist, excluding `GameEngine.kt` (the sanctioned lock owner) and `BattleHosts.kt` (KDoc mentions the lock).

- [ ] **Step 1: Confirm collaborators are clean at HEAD**

Run:
```bash
for c in UWController CombatResolver BuffTickers BattleRenderer; do
  echo "--- $c ---"
  rg -n 'synchronized\(|ReentrantLock|= Any\(\)|= Object\(\)' \
    app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/$c.kt || echo "(clean)"
done
```
Expected: all four `(clean)`.

- [ ] **Step 2: Write the test**

Create the file:

```kotlin
package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Machine-enforces the battle-engine invariant that the engine's collaborators **hold no monitor of
 * their own** (`CLAUDE.md` Battle Renderer note): they run inside `GameEngine`'s single held
 * `entitiesLock`, so a collaborator that declares its own `synchronized`/`ReentrantLock`/monitor
 * field reintroduces the lock-order / nested-lock hazard the #118/#191 fixes closed. Closes the
 * fallback path of GitHub #372 (`ai-2`); the detekt custom-rule alternative is deferred (ADR-0038).
 *
 * Scoped to the explicit collaborator allowlist and EXCLUDES:
 *  - `GameEngine.kt` — the SOLE sanctioned `entitiesLock` owner (it legitimately declares + uses it).
 *  - `BattleHosts.kt` — its `synchronized(entitiesLock)` reference lives in a KDoc (also handled by
 *    comment-stripping, but excluded by name for clarity).
 * Comment-stripped (reuses the `DomainPurityTest`/`StepCreditAllowlistTest` approach) so a KDoc
 * reference to a lock never false-fires.
 */
class BattleEngineLockScanTest {
    private val engineDir =
        File("src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine")

    private val collaborators =
        setOf("UWController.kt", "CombatResolver.kt", "BuffTickers.kt", "BattleRenderer.kt")

    private val monitorPatterns =
        listOf("synchronized(", "ReentrantLock", "= Any()", "= Object()")

    private fun stripComments(source: String): String {
        val noBlock =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                "\n".repeat(m.value.count { it == '\n' })
            }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `engine collaborators declare no monitor of their own`() {
        assertTrue(engineDir.isDirectory) {
            "engine dir not found at ${engineDir.absolutePath} (working dir = ${File(".").absolutePath})"
        }
        val files = engineDir.walkTopDown().filter { it.isFile && it.name in collaborators }.toList()
        // Guard against the allowlist silently matching nothing (a rename would make this a no-op).
        assertTrue(files.map { it.name }.toSet() == collaborators) {
            "Collaborator file set drifted. Expected=$collaborators Found=${files.map { it.name }}. " +
                "If a collaborator was renamed/added, update this allowlist (and re-confirm it holds " +
                "no monitor)."
        }
        val offenders =
            files.flatMap { file ->
                stripComments(file.readText())
                    .lineSequence()
                    .mapIndexedNotNull { idx, line ->
                        if (monitorPatterns.any { line.contains(it) }) "${file.name}:${idx + 1}: ${line.trim()}" else null
                    }
            }
        assertTrue(offenders.isEmpty()) {
            "A battle-engine collaborator declares its own monitor — collaborators must run inside " +
                "GameEngine's held entitiesLock, NOT take a lock of their own (CLAUDE.md Battle " +
                "Renderer note; #118/#191). Offenders:\n" + offenders.joinToString("\n")
        }
    }
}
```

- [ ] **Step 3: Run — expect PASS at HEAD**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*BattleEngineLockScanTest*' > build.log 2>&1; tail -n 25 build.log`
Expected: PASS. If it fails on the "collaborator file set drifted" assertion, a collaborator was renamed — reconcile the `collaborators` set. If it fails on an offender, verify with the Step-1 command (a real new monitor is a genuine invariant break, not a test bug).

- [ ] **Step 4: detekt + ktlint, then commit**

Run: `./lint-kotlin.sh && ./run-gradle.sh :app:detekt > build.log 2>&1; tail -n 10 build.log`
Expected: green. Then:

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/architecture/BattleEngineLockScanTest.kt
git commit -m "test(#372): fallback lock-scan — collaborators hold no monitor of their own"
```

### Task B6: Add the concurrency-reviewer trigger to the PreToolUse hook (#372)

**Files:** Modify `.claude/hooks/guard-sensitive-edits.sh` (add a new advisory tier). No `settings.json` change — the hook is already wired.

- [ ] **Step 1: Add tier 4 (advisory) before the final fall-through**

In `.claude/hooks/guard-sensitive-edits.sh`, after the Tier 3 (`Migrations.kt`) `case … esac` block and before the end of the script, add:

```bash
# --- Tier 4: battle-engine / effects / DAO / currency surface → advisory (#372, ai-2) ----------
# The lock-order invariant (entitiesLock → effectsLock; collaborators hold no monitor) and the
# currency-move surface are the two bug classes the project was burned by (#118/#191) and the
# ones an agent is most likely to trip. The concurrency-reviewer subagent is MANDATORY on these
# diffs (CLAUDE.md Adversarial Review Gate). Advisory nudge (matches Tiers 2/3 house style) — the
# edit proceeds, but flags that the mandatory concurrency-review lane applies.
case "$file" in
  */presentation/battle/engine/*|*/presentation/battle/effects/*|*/data/local/*Dao.kt|*/data/repository/PlayerRepositoryImpl.kt)
    jq -cn --arg ctx "Advisory (#372 / CLAUDE.md Adversarial Review Gate): this edits the battle-engine/effects, a Room DAO, or the currency-moving PlayerRepositoryImpl. The concurrency-reviewer subagent is a MANDATORY review lane for this diff (thread-safety: entitiesLock→effectsLock order, collaborators hold no monitor; atomic guarded-deduct economy). Run it before committing. The edit proceeds." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:$ctx}}' 2>/dev/null || true
    exit 0
    ;;
esac
```

- [ ] **Step 2: Syntax-check the hook**

Run: `bash -n .claude/hooks/guard-sensitive-edits.sh && echo "hook syntax OK"`
Expected: `hook syntax OK`.

- [ ] **Step 3: Functional smoke test (simulate a PreToolUse payload)**

Run:
```bash
echo '{"tool_input":{"file_path":"app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CombatResolver.kt"}}' \
  | .claude/hooks/guard-sensitive-edits.sh
```
Expected: a JSON line containing `additionalContext` mentioning the concurrency-reviewer. Then confirm a non-matching path is silent:
```bash
echo '{"tool_input":{"file_path":"README.md"}}' | .claude/hooks/guard-sensitive-edits.sh; echo "(exit $?)"
```
Expected: no output, `(exit 0)`.

- [ ] **Step 4: Commit**

```bash
git add .claude/hooks/guard-sensitive-edits.sh
git commit -m "chore(#372): PreToolUse advisory on engine/effects/DAO/currency edits

Adds tier 4 to guard-sensitive-edits.sh nudging that the concurrency-reviewer
is a mandatory review lane on battle-engine/effects, Room DAO, and
PlayerRepositoryImpl diffs. Deterministic (fires regardless of ultracode state)."
```

### Task B7: Wire the mandatory lane into CLAUDE.md + write ADR-0038 (#372 doc-sync)

**Files:** Modify `CLAUDE.md` (Adversarial Review Gate section); create `docs/agent/DECISIONS/ADR-0038-concurrency-reviewer-enforcement.md`.

- [ ] **Step 1: Add the mandatory-lane paragraph to CLAUDE.md**

In `CLAUDE.md`, under `### Adversarial Review Gate`, after the numbered review shape, add:

```markdown

**Mandatory concurrency-reviewer lane (#372, ADR-0038).** Any diff touching
`presentation/battle/engine/**`, `presentation/battle/effects/**`, a Room DAO (`data/local/*Dao.kt`),
`data/repository/PlayerRepositoryImpl`, the domain spend/claim use cases, or anything that
structurally mutates a shared engine collection or moves a currency balance MUST include the
`concurrency-reviewer` subagent as a review lane (it scopes exactly this surface). This is enforced
deterministically by the `guard-sensitive-edits.sh` PreToolUse advisory (fires regardless of
ultracode state); the protocol wording here is the human/agent contract. The build-gated tripwires
are `architecture/StepCreditAllowlistTest` (Steps-generation) and `architecture/BattleEngineLockScanTest`
(collaborators-hold-no-monitor); a detekt nested-lock rule is a deferred follow-up (ADR-0038).
```

- [ ] **Step 2: Write ADR-0038**

Create `docs/agent/DECISIONS/ADR-0038-concurrency-reviewer-enforcement.md` (match the format of an existing ADR — check one first with `ls docs/agent/DECISIONS/ | tail`):

```markdown
# ADR-0038 — Enforcing the concurrency/economy review invariants (#372, ai-2)

**Status:** Accepted · **Date:** 2026-07-02

## Context
The acyclic lock order (`entitiesLock` → `effectsLock`) and "collaborators hold no monitor of their
own", plus the "Steps never generated in-game" economy rule, were **prose-only** — an agent could
ship a lock-order inversion (the #118/#191 CME class) or a Steps-generation break with a fully green
`testDebugUnitTest` + detekt + ktlint + lint. GitHub #372 (`ai-2`) and #371 (`ai-3`) asked to make
these tripwires real.

## Decision
Enforce in three layers, strongest-first:
1. **Build-gated source-scan tests** (JVM lane, dependency-free, modeled on `DomainPurityTest`):
   `StepCreditAllowlistTest` (#371) pins the full `currentStepBalance` write surface;
   `BattleEngineLockScanTest` (#372) asserts engine collaborators declare no monitor.
2. **Deterministic PreToolUse advisory** (`guard-sensitive-edits.sh` tier 4): nudges that the
   `concurrency-reviewer` lane is mandatory on engine/effects/DAO/currency edits — fires regardless
   of ultracode state, unlike the CLAUDE.md protocol.
3. **Protocol** (CLAUDE.md Adversarial Review Gate): the mandatory `concurrency-reviewer` lane.

## Rejected / deferred
- **detekt custom rule for nested lock acquisition.** The repo has no detekt custom-rule module and
  detekt is `2.0.0-alpha.5` (unstable RuleSetProvider API). Authoring one needs a new Gradle module +
  `detektPlugins` dependency. Deferred to a follow-up issue; the `BattleEngineLockScanTest` fallback
  covers the "collaborator takes its own monitor" case build-gated today.

## Consequences
- A new Steps-credit site or a collaborator monitor now fails the build.
- The lock-*order* (as opposed to collaborator-monitor) check remains review-driven, not build-gated —
  the detekt rule would close that; tracked as the deferred follow-up.
```

- [ ] **Step 3: Update the CLAUDE.md test count**

In `CLAUDE.md`, find the headline count line (`rg -n 'Headline count' CLAUDE.md`) and update it. `StepCreditAllowlistTest` adds 5 `@Test` methods and `BattleEngineLockScanTest` adds 1 → **+6**. Change `1294 JVM tests` to `1300 JVM tests`.

- [ ] **Step 4: Verify the count against the actual suite**

Run: `./run-gradle.sh :app:testDebugUnitTest > build.log 2>&1; rg -n 'Tests:|tests (completed|passed)|BUILD' build.log | tail`
Expected: BUILD SUCCESSFUL. Cross-check the total moved by +6 vs the last known 1294 (the exact reported number is the source of truth — if it's not exactly 1300, set the CLAUDE.md line to the real number and note the delta in the commit).

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md docs/agent/DECISIONS/ADR-0038-concurrency-reviewer-enforcement.md
git commit -m "docs(#372): mandatory concurrency-reviewer lane in CLAUDE.md + ADR-0038 + test count"
```

### Task B8: PR-B doc-sync (CHANGELOG, source-files, STATE/RUN_LOG) + PR

- [ ] **Step 1: source-files.md**

In `docs/steering/source-files.md`, add entries for the two new test files (`StepCreditAllowlistTest.kt`, `BattleEngineLockScanTest.kt`) under the architecture-tests area (match the existing `DomainPurityTest`/`PresentationPurityTest` entry style).

- [ ] **Step 2: CHANGELOG entry**

In `CHANGELOG.md` under `## [Unreleased]`, add:

```markdown
### Testing / AI-safety — invariant tripwires (#371, #372)

- **#371 (ai-3).** New `StepCreditAllowlistTest` machine-enforces "Steps are never generated in-game"
  (ADR-0003) across the FULL `currentStepBalance` write surface: `.addSteps(` callers,
  positive `.adjustStepBalance(` sites, the zero-caller absolute `updateStepBalance` setter,
  `PlayerProfileEntity` construction, and a DAO write-surface count pin — plus a baked negative
  fixture proving the matchers go red on a rogue site. (+5 tests.)
- **#372 (ai-2).** New `BattleEngineLockScanTest` asserts battle-engine collaborators hold no monitor
  of their own (comment-stripped, collaborator-scoped, excludes GameEngine/BattleHosts). (+1 test.)
  Made the `concurrency-reviewer` lane mandatory via a `guard-sensitive-edits.sh` PreToolUse advisory
  + CLAUDE.md Review-Gate wiring; the detekt nested-lock rule is deferred (ADR-0038).
```

If a current-state block in CHANGELOG tracks the test count, bump it by +6.

- [ ] **Step 3: STATE.md + RUN_LOG.md**

Update `docs/agent/STATE.md` (PR-B up; PR-C next) and append a `docs/agent/RUN_LOG.md` entry for PR-B.

- [ ] **Step 4: Full gate before pushing**

Run: `./run-gradle.sh testDebugUnitTest lintDebug lintRelease assembleDebug > build.log 2>&1; tail -n 20 build.log` then `./lint-kotlin.sh` and `./run-gradle.sh :app:detekt`.
Expected: all green.

- [ ] **Step 5: Commit + push + PR**

```bash
git add CHANGELOG.md docs/steering/source-files.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#371,#372): CHANGELOG + source-files + STATE + RUN_LOG for PR-B"
git push -u origin test/phase1-ai-safety-tripwires
gh pr create --title "test: AI-safety tripwires — step-credit allowlist + engine lock scan (#371, #372)" \
  --body "Phase 1 tooling-gap PR-B. Closes #371, closes #372. Spec: docs/superpowers/specs/2026-07-02-phase1-tooling-gap-design.md (adversarial-review gate passed). ADR-0038. Tracker: #389."
```

- [ ] **Step 6: CI green → tick #389 (#371, #372).**

### Task B9: (Deferred, optional) file the detekt nested-lock-rule follow-up

- [ ] **Step 1: Open a follow-up issue** (only if not already tracked)

```bash
gh issue create --title "[Tooling] detekt custom rule: flag nested lock acquisition in the battle engine (deferred from #372)" \
  --label tooling --label area:battle \
  --body "Deferred from #372 / ADR-0038. Author a detekt RuleSetProvider flagging a synchronized/ReentrantLock acquired while another is held, to build-gate the lock-ORDER invariant (the BattleEngineLockScanTest fallback only catches a collaborator taking its OWN monitor). Blocked on detekt 2.0.0-alpha.5 RuleSetProvider API stability + a new custom-rule Gradle module + detektPlugins dependency."
```

---

# PR-C — Crash observability (#374 + #380)

**Branch:** `feat/phase1-crash-report-and-runbook`

### Task C0: Branch

- [ ] **Step 1: Create the branch**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git checkout main && git pull
git checkout -b feat/phase1-crash-report-and-runbook
```

### Task C1: Add the `<queries>` manifest element (#374)

**Files:** Modify `app/src/main/AndroidManifest.xml`.

Without this, `ACTION_SENDTO`/`mailto:` `resolveActivity()` returns null under targetSdk-36 package visibility even when a mail client exists — the Report button would be a silent no-op.

- [ ] **Step 1: Add `<queries>` after the `<uses-permission>` block, before `<application>`**

In `app/src/main/AndroidManifest.xml`, after the last `<uses-permission …/>` line (the health permissions ~line 18-19) and before `<application`, insert:

```xml

    <!--
        #374: package-visibility declaration so the crash-report "Report" action can resolve a mail
        app via ACTION_SENDTO (mailto:). Under targetSdk 30+ package filtering, resolveActivity()
        for a SENDTO/mailto intent returns null WITHOUT this <queries> entry even when a mail client
        is installed (unlike ACTION_VIEW http, which is auto-visible). See CrashReportIntent.
    -->
    <queries>
        <intent>
            <action android:name="android.intent.action.SENDTO" />
            <data android:scheme="mailto" />
        </intent>
    </queries>
```

- [ ] **Step 2: Verify the manifest still assembles**

Run: `./run-gradle.sh :app:processDebugManifest > build.log 2>&1; tail -n 15 build.log`
Expected: BUILD SUCCESSFUL (no manifest-merge error).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(#374): add <queries> SENDTO/mailto so the crash-report action can resolve mail"
```

### Task C2: Add the string resources (#374)

**Files:** Modify `app/src/main/res/values/strings.xml`.

- [ ] **Step 1: Add the Report action label + email subject/body strings**

Near the existing `crash_notice_last_session` string (`strings.xml:122`), add:

```xml
    <string name="crash_notice_report_action">Report</string>
    <string name="crash_report_email_subject">Steps of Babylon crash report</string>
    <!-- %1$s exceptionClass, %2$s message, %3$s stackPreview, %4$s app+device metadata -->
    <string name="crash_report_email_body">A crash was recorded on the previous session.\n\nException: %1$s\nMessage: %2$s\n\nStack:\n%3$s\n\n%4$s</string>
```

- [ ] **Step 2: Verify no HardcodedText / build break**

Run: `./run-gradle.sh :app:lintDebug > build.log 2>&1; rg -n 'HardcodedText|Error:' build.log | head`
Expected: no `HardcodedText` errors (these are string resources, not literals).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(#374): add crash-report action + email subject/body string resources"
```

### Task C3: Extract the testable `buildCrashReportIntent(...)` function + write its test (#374, TDD)

**Files:** Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/CrashReportIntentTest.kt`; modify `MainActivity.kt` (add a top-level fn).

The `createComposeRule()` JVM lane launches ui-test-manifest's own host activity, never `MainActivity`, so the `LaunchedEffect` body isn't drivable there. The automatable seam is the intent BUILDER. Write the test first.

- [ ] **Step 1: Write the failing Robolectric test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/CrashReportIntentTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CrashReportIntentTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `builds an ACTION_SENDTO mailto intent with the breadcrumb in subject and body`() {
        val intent =
            buildCrashReportIntent(
                context = context,
                exceptionClass = "java.lang.IllegalStateException",
                message = "boom",
                stackPreview = "at Foo.bar(Foo.kt:1)",
            )

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertTrue(
            "recipient must be the support address",
            intent.data?.schemeSpecificPart?.contains("jonwhitefang@gmail.com") == true,
        )
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue("body carries exception class", body.contains("java.lang.IllegalStateException"))
        assertTrue("body carries message", body.contains("boom"))
        assertTrue("body carries stack preview", body.contains("at Foo.bar(Foo.kt:1)"))
        assertTrue(
            "subject set",
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.isNotBlank() == true,
        )
    }
}
```

- [ ] **Step 2: Run — expect FAIL (unresolved reference `buildCrashReportIntent`)**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*CrashReportIntentTest*' > build.log 2>&1; tail -n 25 build.log`
Expected: compile FAIL — `unresolved reference: buildCrashReportIntent`.

- [ ] **Step 3: Add the top-level `buildCrashReportIntent(...)` fn to MainActivity.kt**

In `MainActivity.kt`, alongside the existing top-level helpers (`openPrivacyPolicy` at ~557, `requestBatteryExemption` at ~531), add. Add `import com.whitefang.stepsofbabylon.BuildConfig` — already imported at line 41 — and `import android.os.Build` at the top if not present.

```kotlin
/**
 * #374: build the crash-report email intent (ACTION_SENDTO mailto:) pre-filled with the crash
 * breadcrumb. Extracted as a top-level fn (mirrors [openPrivacyPolicy]) so it is unit-testable
 * without launching MainActivity — the createComposeRule JVM lane never instantiates this Activity.
 * The manifest <queries> SENDTO/mailto entry makes this resolvable under package-visibility filtering.
 */
internal fun buildCrashReportIntent(
    context: android.content.Context,
    exceptionClass: String,
    message: String?,
    stackPreview: String,
): Intent {
    val metadata =
        "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}"
    val subject = context.getString(R.string.crash_report_email_subject)
    val body =
        context.getString(
            R.string.crash_report_email_body,
            exceptionClass,
            message ?: "(none)",
            stackPreview,
            metadata,
        )
    return Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:jonwhitefang@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*CrashReportIntentTest*' > build.log 2>&1; tail -n 25 build.log`
Expected: PASS. If Robolectric can't resolve strings, confirm Task C2 landed on this branch (`git log --oneline | head`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/CrashReportIntentTest.kt
git commit -m "feat(#374): extract testable buildCrashReportIntent + Robolectric test"
```

### Task C4: Wire the Report action into the crash snackbar (#374)

**Files:** Modify `MainActivity.kt` (the crash `LaunchedEffect`, ~277-283).

- [ ] **Step 1: Resolve the action label string outside the effect + rework the effect**

Replace the existing crash-notice block (`MainActivity.kt:276-283`):

```kotlin
                    val crashNotice = stringResource(R.string.crash_notice_last_session)
                    LaunchedEffect(Unit) {
                        val crash = crashBreadcrumbStore.peek()
                        if (crash != null) {
                            snackbarHostState.showSnackbar(crashNotice)
                            crashBreadcrumbStore.clear()
                        }
                    }
```

with (resolve the label outside the coroutine, add the action, fire the intent before the existing `clear()`):

```kotlin
                    val crashNotice = stringResource(R.string.crash_notice_last_session)
                    val crashReportAction = stringResource(R.string.crash_notice_report_action)
                    LaunchedEffect(Unit) {
                        val crash = crashBreadcrumbStore.peek()
                        if (crash != null) {
                            val result =
                                snackbarHostState.showSnackbar(
                                    message = crashNotice,
                                    actionLabel = crashReportAction,
                                )
                            if (result == SnackbarResult.ActionPerformed) {
                                val reportIntent =
                                    buildCrashReportIntent(
                                        context = context,
                                        exceptionClass = crash.exceptionClass,
                                        message = crash.message,
                                        stackPreview = crash.stackPreview,
                                    )
                                // Guarded launch (mirrors openPrivacyPolicy): a device with no mail
                                // client is a safe no-op. The manifest <queries> entry makes SENDTO
                                // resolvable when a mail client IS present.
                                if (reportIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(reportIntent)
                                }
                            }
                            // clear() AFTER the awaited showSnackbar (unchanged ordering): the
                            // breadcrumb survives until the notice is dismissed/actioned, so a Report
                            // tap fires the intent above first. (#190 REL-1 now HAS a report channel.)
                            crashBreadcrumbStore.clear()
                        }
                    }
```

`SnackbarResult` is already imported (`MainActivity.kt:24`). `context` is the `LocalContext.current` already in scope.

- [ ] **Step 2: Update the #190 REL-1 comment above the block**

The comment at `MainActivity.kt:271-275` says "there is no in-app report channel to wire an action to." Replace that sentence with:

```kotlin
                    // #190 REL-1 / #374: surface a one-time notice if the previous session crashed,
                    // with a "Report" action that emails the breadcrumb (buildCrashReportIntent).
                    // Resolve the strings via stringResource OUTSIDE the LaunchedEffect (a coroutine
                    // can't call @Composable; context.getString trips the Compose lint check).
```

- [ ] **Step 3: Build + lint**

Run: `./run-gradle.sh :app:assembleDebug > build.log 2>&1; tail -n 15 build.log` then `./lint-kotlin.sh && ./run-gradle.sh :app:detekt`.
Expected: all green.

- [ ] **Step 4: Run the full JVM suite (nothing regressed)**

Run: `./run-gradle.sh :app:testDebugUnitTest > build.log 2>&1; tail -n 15 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt
git commit -m "feat(#374): crash snackbar gains a Report action that emails the breadcrumb"
```

### Task C5: Add the post-release monitoring runbook (#380)

**Files:** Modify `docs/release/release-checklist.md` (currently ends at "Build Outputs", line ~54-57).

- [ ] **Step 1: Append the section at the end of the file**

Add after the "Build Outputs" section:

```markdown

## Post-release monitoring (#380)

After a `v*` tag ships an AAB to the Play internal track (`release.yml`), monitor Play Console
**Vitals** on a cadence — time-to-detect a field regression otherwise depends on remembering to look.

- **At ~24h and ~72h post-release**, check Play Console → Quality → **Android vitals**:
  - **Crash rate** (user-perceived crash rate) — investigate any rise vs the previous release's baseline.
  - **ANR rate** — same.
  - The `#190` in-app crash breadcrumb also gives a tap-to-email **Report** path (#374) for testers.
- **Roll-back trigger:** if the crash or ANR rate rises materially above the prior release on the
  internal track, halt promotion and ship a fixed build (new `versionCode`) — do not promote the
  regressed build to a wider track.
- **De-obfuscation:** `mapping.txt` upload is already automated in `release.yml` (the
  `upload-google-play` step's `mappingFile`), so Vitals stack traces are de-obfuscated automatically —
  no manual upload step needed.
- See `docs/release/plan-31-walkthrough.md` for the original monitoring walkthrough this cadence
  distills.
```

- [ ] **Step 2: Verify the doc renders / cross-refs resolve**

Run: `rg -n 'plan-31-walkthrough' docs/release/release-checklist.md && ls docs/release/plan-31-walkthrough.md`
Expected: the cross-ref exists and the target file is present.

- [ ] **Step 3: Commit**

```bash
git add docs/release/release-checklist.md
git commit -m "docs(#380): add post-release Vitals monitoring runbook to release-checklist"
```

### Task C6: PR-C doc-sync (CLAUDE.md count, source-files, CHANGELOG, STATE/RUN_LOG) + PR

- [ ] **Step 1: CLAUDE.md test count (+1 for the Robolectric test)**

Update the headline count line: the PR-B change set it to 1300; PR-C's `CrashReportIntentTest` adds 1 → **1301 JVM tests**. (Re-derive from the actual suite total if PR-B's real number differed.)

- [ ] **Step 2: source-files.md**

Add `CrashReportIntentTest.kt` (and note the new top-level `buildCrashReportIntent` in the `MainActivity.kt` entry if that file is itemized).

- [ ] **Step 3: CHANGELOG entry**

```markdown
### Observability — crash-report exit path + monitoring runbook (#374, #380)

- **#374 (obs-2).** The one-time crash notice now offers a **Report** action that opens a pre-filled
  email (`ACTION_SENDTO` to the support address) with the breadcrumb (exception class, message, stack
  preview, app/device metadata). Required a `<queries>` SENDTO/mailto manifest entry (targetSdk-36
  package visibility) and a testable extracted `buildCrashReportIntent` (+1 Robolectric test). The
  breadcrumb `clear()` stays after the awaited snackbar so a Report tap sends it first.
- **#380 (obs-1).** Added a "Post-release monitoring" section to `release-checklist.md` (24h/72h Play
  Vitals cadence, crash/ANR roll-back trigger; notes `mapping.txt` upload is already automated).
```

Bump any CHANGELOG current-state test count by +1.

- [ ] **Step 4: STATE.md + RUN_LOG.md**

Update `docs/agent/STATE.md` (Phase-1 tooling-gap complete: PR-A/B/C merged or in-flight) and append a `docs/agent/RUN_LOG.md` entry for PR-C.

- [ ] **Step 5: Full gate**

Run: `./run-gradle.sh testDebugUnitTest lintDebug lintRelease assembleDebug > build.log 2>&1; tail -n 20 build.log` then `./lint-kotlin.sh && ./run-gradle.sh :app:detekt`.
Expected: all green.

- [ ] **Step 6: Commit + push + PR**

```bash
git add CLAUDE.md docs/steering/source-files.md CHANGELOG.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#374,#380): CLAUDE.md count + source-files + CHANGELOG + STATE + RUN_LOG for PR-C"
git push -u origin feat/phase1-crash-report-and-runbook
gh pr create --title "feat: crash-report exit path + post-release monitoring runbook (#374, #380)" \
  --body "Phase 1 tooling-gap PR-C. Closes #374, closes #380. Spec: docs/superpowers/specs/2026-07-02-phase1-tooling-gap-design.md (adversarial-review gate passed). Tracker: #389."
```

- [ ] **Step 7: CI green → tick #389 (#374, #380). If all Phase-1 boxes are ticked, note Phase 1 complete on #389.**

---

## Self-Review (completed by plan author)

**Spec coverage** — every spec item maps to a task:
- #370 → A1 (local proof) + A2 (CI change, incl. dummy-key-before-Gradle + R8-task verify + NDK note).
- #376 → A3 (`.gitleaks.toml` custom rules) + A4 (workflow, SHA-pinned) + A5 (non-provider patterns) + A6 (doc).
- #371 → B1–B4: Assertion 1 (`.addSteps(`), Assertion 2 (positive `.adjustStepBalance(`, sign-checked, dot-qualified so the declaration is excluded), Assertion 3 (`updateStepBalance` empty allowlist + `PlayerProfileEntity` construction), Assertion 4 (DAO write-surface count pin), baked negative fixture.
- #372 → B5 (comment-stripped, collaborator-scoped lock-scan excluding GameEngine/BattleHosts) + B6 (hook advisory, verbatim trigger scope incl. currency) + B7 (CLAUDE.md mandatory lane + ADR-0038 + count) + B9 (deferred detekt follow-up).
- #374 → C1 (`<queries>` — the HIGH finding) + C2 (strings) + C3 (extracted testable fn + Robolectric test) + C4 (snackbar action, clear() after awaited snackbar).
- #380 → C5 (monitoring runbook).
- Doc-sync + STATE/RUN_LOG present in every PR (A7, B8, C6), before the commit/PR step, per the PR Task-List Convention. #389 ticked per PR.

**Placeholder scan** — no TBD/TODO; every code step has complete code; every command has expected output. The one intentional variable is the exact test-count number (`+6`, `+1`) — the plan instructs re-deriving from the real suite total, because the count is a live number.

**Type/name consistency** — `buildCrashReportIntent(context, exceptionClass, message, stackPreview)` is defined in C3 and called identically in C4 and the C3 test. `CrashBreadcrumb` fields (`exceptionClass`, `message`, `stackPreview`) match the data class. Predicate helper names (`addStepsCallerFiles`, `positiveAdjustStepBalanceFiles`, `updateStepBalanceCallerFiles`, `playerProfileEntityConstructionFiles`) are defined once and reused consistently in the negative-fixture test. `stripComments` mirrors `DomainPurityTest`. Hook tier-4 glob paths match the CLAUDE.md trigger scope and the concurrency-reviewer agent scope.
