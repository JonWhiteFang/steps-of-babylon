# Kotlin lint enforcement: detekt (Gradle plugin) + ktlint (CLI gate) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (or executing-plans) to implement task-by-task. Steps use `- [ ]` checkboxes.

**Goal:** Make the CLAUDE.md `detekt`/`ktlint` "Preferred CLI tooling" entry real + CI-enforced — detekt via the
`dev.detekt` 2.0.0-alpha.5 Gradle plugin (plain `detekt` task, baseline-gated), ktlint via the standalone
1.8.0 CLI driven by a committed `lint-kotlin.sh` + a pinned, SHA-256-verified CI download — both folded into
the `build-and-test` PR-gate job behind the docs-only fast path. No production Kotlin change; baselines absorb
the ~500-file existing-violation set so the gate fails only on NEW violations.

**Spec:** `docs/superpowers/specs/2026-06-22-kotlin-lint-enforcement-detekt-ktlint.md` (Adversarial-Review-Gate-passed: 23→12 surviving applied).

**Plan reviewed:** Adversarial Review Gate applied (16 raised → 9 surviving → 7 refuted).

**Toolchain (do not fight it):** Kotlin 2.3.0 / AGP 9.2.1 / Gradle 9.6.0, AGP-9 built-in Kotlin (NO
`org.jetbrains.kotlin.android` — apply-time error, root `build.gradle.kts:11-13`). Version catalog at
`gradle/libs.versions.toml`; build via `./run-gradle.sh`.

---

## Guiding rules

- Build wrapper is `./run-gradle.sh` (NOT ./gradlew). Output is large — redirect to a temp file, tail/grep.
- **Build-green-first discipline (the central risk):** detekt is an ALPHA on a bleeding-edge AGP-9/Gradle-9
  build. After wiring it, a FULL `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` AND
  `./run-gradle.sh :app:detekt` MUST be verified green BEFORE committing that task. If the plugin won't apply
  / breaks configuration, STOP and use the CLI-via-`JavaExec` fallback (Task 3 contingency) — do not force it.
- Prefer `sg`/`fd` for structural searches (CLAUDE.md). Use `delta --paging=never` or `git show` for diffs
  (non-TTY).
- Baselines are committed and only regenerated DELIBERATELY (never in CI — that would make the gate a no-op).
- Each task: make the change → verify green → commit. Already on branch
  `feat/lint-enforcement-detekt-ktlint` (the reviewed spec is committed there).
- Touch NO production Kotlin. New files = config + script + CI glue + docs only.

---

## File structure

| File | Change | Responsibility |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | detekt `[versions]` pin + `[plugins]` alias (documented alpha pin) |
| `build.gradle.kts` (root) | Modify | `alias(libs.plugins.detekt) apply false` |
| `app/build.gradle.kts` | Modify | apply detekt + `detekt { }` block (config + baseline + buildUponDefaultConfig + parallel + report dir) |
| `config/detekt/detekt.yml` | Create | house ruleset (formatting overlap with ktlint disabled; generated-src excluded) |
| `config/detekt/baseline.xml` | Create (generated) | suppress current detekt violations |
| `.editorconfig` | Create | ktlint config (Kotlin rules scoped under `[{*.kt,*.kts}]`) |
| `config/ktlint/baseline.xml` | Create (generated) | suppress current ktlint violations |
| `lint-kotlin.sh` | Create (committed, executable) | pinned ktlint 1.8.0 runner: check (WITH baseline) / `--format` (WITHOUT baseline); SHA-256-pinned; report under `app/build/reports/ktlint/` |
| `.github/workflows/ci.yml` | Modify | 2 code-gated steps in `build-and-test`: detekt + (download+verify ktlint → lint-kotlin.sh) |
| `docs/agent/DECISIONS/ADR-00NN-kotlin-lint-enforcement.md` | Create | the alpha/CLI integration decision + AGP-9 constraint + deferred type-resolution boundary |
| `CLAUDE.md` · `CHANGELOG.md` · `README.md` · `docs/steering/{source-files,tech,structure}.md` | Modify | current-state doc sync |
| `docs/agent/STATE.md` · `docs/agent/RUN_LOG.md` | Modify | checkpoint |

**Task order:** 1 detekt plugin wiring + config + baseline (build-green-first) → 2 ktlint `.editorconfig` +
baseline + `lint-kotlin.sh` → 3 CI wiring (both steps, code-gated, SHA-verified ktlint download) → 4
mutation-test both gates catch a NEW violation → 5 ADR + current-state doc sync → 6 STATE/RUN_LOG → 7 final
gate + push + PR.

---

### Task 1: detekt Gradle plugin (`dev.detekt` 2.0.0-alpha.5) + config + baseline — BUILD-GREEN-FIRST

**Files:** `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`,
`config/detekt/detekt.yml`, `config/detekt/baseline.xml`.

- [ ] **Step 1: Catalog pin.** In `gradle/libs.versions.toml` add to `[versions]`:
  ```toml
  # detekt 2.0.0-alpha line: the ONLY line supporting Kotlin 2.3.x (stable 1.23.8 caps at Kotlin 2.0.21).
  # alpha.4/.5 built against Kotlin 2.4.0 / AGP 9.2.1 / (approx Gradle 9.5.x) — the 2.4.0 frontend parses the
  # project's 2.3.0 source (backward-compatible; a Kotlin-2.3.0-built engine first shipped in alpha.2).
  # AGP 9.2.1 is the exact match that governs whether the plugin applies under AGP-9 built-in Kotlin.
  # alpha.5 = dep-leak/POM hotfix on alpha.4 + config-cache compat. Alpha is unavoidable here and is
  # DEV/CI-ONLY (detekt never enters the AAB; same acceptance as benchmark=1.5.0-alpha06). Plugin id is
  # `dev.detekt` (2.0 line); legacy `io.gitlab.arturbosch.detekt` is the dead 1.x id. Revisit when a
  # stable 2.0.0 supporting Kotlin 2.3+ ships.
  detekt = "2.0.0-alpha.5"
  ```
  and to `[plugins]`: `detekt = { id = "dev.detekt", version.ref = "detekt" }`.

- [ ] **Step 2: Root declare.** In root `build.gradle.kts` `plugins {}` add `alias(libs.plugins.detekt) apply false`
  (alongside the existing `apply false` aliases).

- [ ] **Step 3: Apply to :app.** In `app/build.gradle.kts` add `alias(libs.plugins.detekt)` to the `plugins {}`
  block (do NOT add any kotlin-android plugin), and add a top-level `detekt { }` block:
  ```kotlin
  detekt {
      config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
      baseline = file("$rootDir/config/detekt/baseline.xml")
      buildUponDefaultConfig = true
      parallel = true
  }
  ```
  (Reports default under `app/build/reports/detekt/` — captured by ci.yml's existing `**/build/reports/**`
  upload glob; no ci.yml report change needed.)

- [ ] **Step 4: Generate + trim config.** `./run-gradle.sh detektGenerateConfig` (writes
  `config/detekt/detekt.yml`), then trim to a sane house ruleset: keep code-smell/complexity rules; **disable
  the `formatting` ruleset** (ktlint owns formatting — avoid dual ownership); keep the default generated-source
  exclusion + a belt `.*/build/.*` exclude. If `detektGenerateConfig` isn't available on the alpha, hand-write a
  minimal `detekt.yml` — detekt 2.0 has NO `build:/maxIssues` key (removed in 2.0); its fail mechanism is the
  severity threshold `failOnSeverity` (default `Error`), and `buildUponDefaultConfig = true` already restores
  `failOnSeverity = Error` with detekt emitting findings at `error` severity by default, so the hand-written
  file needs NO explicit fail key — rely on `buildUponDefaultConfig = true` + default severity gating, with
  `buildUponDefaultConfig` doing the heavy lifting. Keep the file small + commented.

- [ ] **Step 5: Generate the baseline.** `./run-gradle.sh :app:detektBaseline > /tmp/dbase.log 2>&1; tail -20 /tmp/dbase.log`
  → writes `config/detekt/baseline.xml` (suppresses the current ~500-file violation set). If `:app:detektBaseline`
  isn't present on the alpha (mirroring the Step-4 hedge, and linking to the Step-6 contingency): either register a
  `DetektCreateBaselineTask` (detekt.dev documented: `val detektProjectBaseline by tasks.registering(DetektCreateBaselineTask::class) { baseline.set(file("$rootDir/config/detekt/baseline.xml")); config.setFrom(...); buildUponDefaultConfig.set(true); setSource(files("app/src")) }`) and run that, OR — if the plugin path is being abandoned per Step 6 — generate the baseline via the CLI-via-JavaExec path (`detekt-cli … -b config/detekt/baseline.xml`, which auto-creates a missing baseline). Re-verify.

- [ ] **Step 6: BUILD-GREEN-FIRST verification (do NOT skip).**
  ```
  ./run-gradle.sh :app:detekt > /tmp/d1.log 2>&1; echo "detekt exit=$?"; tail -25 /tmp/d1.log
  ./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/d2.log 2>&1; echo "full exit=$?"; tail -15 /tmp/d2.log
  ```
  BOTH must be BUILD SUCCESSFUL. `:app:detekt` exits 0 on the unchanged tree (baseline suppresses all).
  **CONTINGENCY (if the plugin won't apply / breaks configure under AGP-9 built-in Kotlin):** abandon the
  plugin; instead add a `JavaExec` task that runs the detekt CLI jar — declare a `detekt` configuration,
  `dependencies { detekt("dev.detekt:detekt-cli:2.0.0-alpha.5") }`, `tasks.register<JavaExec>("detektCli")`
  with `mainClass.set("dev.detekt.cli.Main")`, `classpath = configurations["detekt"]`, args
  `-i app/src -c config/detekt/detekt.yml -b config/detekt/baseline.xml -ex ".*/build/.*"`. Record the
  fallback in the commit message + ADR. Re-verify green.

- [ ] **Step 7: Commit.**
  ```
  git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts config/detekt/
  git commit -m "build(detekt): wire dev.detekt 2.0.0-alpha.5 Gradle plugin + baseline (#lint)"
  ```

---

### Task 2: ktlint — `.editorconfig` + baseline + `lint-kotlin.sh`

**Files:** `.editorconfig`, `config/ktlint/baseline.xml`, `lint-kotlin.sh`.

- [ ] **Step 1: `.editorconfig`** at repo root — Kotlin rules scoped under `[{*.kt,*.kts}]` so non-Kotlin
  files only get benign charset/final-newline:
  ```editorconfig
  root = true
  [*]
  charset = utf-8
  insert_final_newline = true
  [{*.kt,*.kts}]
  ktlint_code_style = ktlint_official
  max_line_length = 120
  ij_kotlin_allow_trailing_comma = true
  ij_kotlin_allow_trailing_comma_on_call_site = true
  # disable rules that fight the codebase / overlap detekt as the baseline burn-down reveals, e.g.:
  # ktlint_standard_no-wildcard-imports = disabled
  ```
  Pick the code style that yields the LOWER existing-violation churn (try `ktlint_official` vs
  `intellij_idea`; whichever baselines smaller is fine — the baseline absorbs the rest either way).

- [ ] **Step 2: `lint-kotlin.sh`** (repo root, committed + `chmod +x` — NOT gitignored, unlike run-gradle.sh).
  Requirements:
  - Pins `KTLINT_VERSION=1.8.0` and `KTLINT_SHA256=<filled in Step 3>` (single source of truth; CI sources it).
  - Locates ktlint: prefer a repo-cached `./.ktlint/ktlint` whose version matches; else a PATH `ktlint` whose
    version matches — **parse the prefixed output**: `installed=$(ktlint --version | awk '{print $NF}')` then
    `[ "$installed" = "$KTLINT_VERSION" ]` (the CLI prints `ktlint version 1.8.0`, not bare `1.8.0`); else
    print how to obtain it and exit non-zero.
  - **Path handling:** point ktlint at the `app/src` DIRECTORY (ktlint recursively discovers `.kt`/`.kts`) — no
    shell glob. (If a glob is ever needed, pass it QUOTED so ktlint's matcher expands it, never the shell.)
  - **CHECK path (default):** runs WITH `--baseline=config/ktlint/baseline.xml` +
    `--reporter=plain,output=app/build/reports/ktlint/ktlint.txt` → fails (exit≠0) only on NEW violations.
  - **`--format` path (`lint-kotlin.sh --format`):** runs `ktlint -F app/src` **WITHOUT `--baseline`** (ktlint
    1.8.0's `--baseline` suppresses violations even under `-F`, so passing it would stop autocorrect from
    fixing the baselined set — empirically verified). Format is a LOCAL convenience, never CI.
  - `.gitignore` the `./.ktlint/` cache dir + `app/build/` is already ignored.

- [ ] **Step 3: Obtain pinned ktlint + record its SHA, generate the baseline.**
  - Download the pinned 1.8.0 self-exec jar from its GitHub release into `./.ktlint/ktlint` (or use the
    PATH 1.8.0 already installed — `ktlint version 1.8.0` confirmed locally). Compute its SHA-256
    (`shasum -a 256 ./.ktlint/ktlint`) and write it into `lint-kotlin.sh`'s `KTLINT_SHA256=` with a provenance
    comment (computed from the GitHub-release `ktlint` jar; ktlint publishes no `.sha256` sidecar — assets are
    `ktlint`, `ktlint.asc`, `ktlint-1.8.0.zip`, `ktlint.bat`).
  - Generate the baseline (the CLI auto-discovers `.editorconfig`):
    `ktlint --baseline=config/ktlint/baseline.xml app/src` (run once; commit the XML). NOTE: ktlint
    auto-creates a missing baseline + reports (doesn't fail) on first run — that's expected here; the COMMITTED
    baseline is what makes future runs gate on NEW-only.
  - **Parser-caveat pass:** run `ktlint app/src` once; if any file fails to PARSE (not lint) on the 2.2.x
    engine vs 2.3.0 syntax, exclude it via `.editorconfig` (documented) — don't let it block.

- [ ] **Step 4: Verify green on the unchanged tree.**
  `./lint-kotlin.sh > /tmp/k1.log 2>&1; echo "ktlint check exit=$?"; tail -20 /tmp/k1.log` → exit 0 (baseline
  suppresses all current violations).

- [ ] **Step 5: Commit.**
  ```
  git add .editorconfig config/ktlint/ lint-kotlin.sh .gitignore
  git commit -m "build(ktlint): committed lint-kotlin.sh runner (pinned 1.8.0, SHA-verified) + baseline (#lint)"
  ```

---

### Task 3: CI wiring — detekt + ktlint in `build-and-test`, code-gated

**Files:** `.github/workflows/ci.yml`.

- [ ] **Step 1: Add two steps to the `build-and-test` job**, AFTER the existing "Lint, unit tests, debug build"
  step and BEFORE "Upload reports", each carrying the existing fast-path guard `if: needs.changes.outputs.code == 'true'`:
  1. **detekt** — `run: ./gradlew :app:detekt --stacktrace` (reuses the job's already-set-up JDK + Gradle).
  2. **ktlint** — a step that (a) downloads the pinned ktlint 1.8.0 jar from its GitHub release into `.ktlint/ktlint`,
     (b) **verifies the SHA-256** fail-closed: `echo "$KTLINT_SHA256  .ktlint/ktlint" | shasum -a 256 -c -`
     (sourcing the pin from `lint-kotlin.sh`, or re-declaring identically), (c) `chmod +x`, (d) `./lint-kotlin.sh`.
     The ktlint jar is a JVM tool → it depends on the `actions/setup-java` step already in the job; this step
     MUST be after it (it is, by placement in the work-step region).
  Keep both as distinct named steps (clear failure signal). Leave the `connected`/instrumented job UNTOUCHED.

- [ ] **Step 2: Confirm the fast path still holds** — the two steps are inside the code-gated region, so a
  docs-only PR (only `docs/**`/`*.md`) still skips them. (This PR touches `.github/` + build files → classifies
  as CODE → runs them, self-validating.)

- [ ] **Step 3: actionlint** the workflow: `actionlint .github/workflows/ci.yml` → clean.

- [ ] **Step 4: Commit.**
  ```
  git add .github/workflows/ci.yml
  git commit -m "ci: run detekt + ktlint in build-and-test (code-gated, SHA-verified ktlint) (#lint)"
  ```

---

### Task 4: Mutation-test both gates (prove they're not no-ops)

- [ ] **Step 1: detekt** — introduce a deliberate detekt smell of a severity the config FAILS on (verify the
  config's fail threshold first — a non-failing severity proves nothing). Confirm the active config fails the
  build at `error` severity (detekt 2.0 `failOnSeverity = Error`, restored by `buildUponDefaultConfig = true`);
  pick the deliberate smell from a STILL-ACTIVE ruleset (NOT the disabled `formatting` ruleset — ktlint owns
  that) and at a rule whose severity is `error`, else `:app:detekt` won't fail and the test proves nothing.
  Confirm `./run-gradle.sh :app:detekt` exits non-zero, then REVERT. (If the chosen ruleset only warns, tighten
  the config so at least one rule fails the build, else the gate is decorative.)

- [ ] **Step 2: ktlint** — introduce a NEW ktlint violation NOT in the baseline (e.g. a wildcard import or an
  over-long line in a scratch file under `app/src`). Confirm `./lint-kotlin.sh` exits non-zero, then REVERT.

- [ ] **Step 3:** confirm the tree is clean (`git status`) — both scratch violations reverted. No commit (this
  task only proves the gates bite).

---

### Task 5: ADR + current-state doc sync

**Files:** `docs/agent/DECISIONS/ADR-00NN-kotlin-lint-enforcement.md` (next free number — check
`ls docs/agent/DECISIONS/` for the next free number; the resolved number is currently **ADR-0037** (ADR-0036
is the highest committed, no branch claims 0037), and that resolved number MUST be substituted everywhere
`ADR-00NN` appears in Task 5 before acting), `CLAUDE.md`, `CHANGELOG.md`, `README.md`,
`docs/steering/source-files.md`, `docs/steering/tech.md`, `docs/steering/structure.md`.

- [ ] **Step 1: ADR** — the decision (detekt as `dev.detekt` alpha.5 Gradle plugin, plain task; ktlint as
  pinned 1.8.0 CLI via committed `lint-kotlin.sh`; both baseline-gated + CI-enforced behind the docs-only fast
  path), the **scope boundary** (alpha unavoidable on Kotlin 2.3.0 + dev/CI-only; type resolution deferred —
  needs proven AGP-9-variant classpath wiring; ktlint-gradle plugin rejected — unproven AGP-9 source-set
  detection; ktlint 2.2.x parser caveat), the build-green-first contingency (CLI-via-JavaExec if the plugin
  won't apply, note whichever path was actually taken), consequences, alternatives rejected. Match the house
  ADR format (`Status: Accepted (2026-06-22)`, Context/Decision/Alternatives/Consequences/Links).

- [ ] **Step 2: CLAUDE.md** — update the `detekt`/`ktlint` "Preferred CLI tooling" line from "manual CLI for
  now… not yet CI-enforced" to the real state: wired + CI-enforced; exact invocations `./run-gradle.sh :app:detekt`
  + `./lint-kotlin.sh [--format]`; baseline-gated (new violations only). Keep it terse + no volatile content.

- [ ] **Step 3: CHANGELOG.md** — new `[Unreleased]` entry for the lint wiring (build-infra + config only, no
  production-code/schema/test-count change; detekt alpha rationale; ktlint CLI + SHA pin; ADR number).

- [ ] **Step 4: README.md** — Build & Run section: document `./lint-kotlin.sh [--format]` + `./run-gradle.sh :app:detekt`
  alongside the existing `run-gradle.sh` test/build entries (new committed dev commands = user-facing). Do NOT
  touch the README JVM-test count — this PR adds no tests; any existing test-count drift is a separate
  pre-existing concern, out of scope here.

- [ ] **Step 5: steering docs** — `source-files.md`: add `lint-kotlin.sh`, `config/detekt/*`, `config/ktlint/*`,
  `.editorconfig` entries. `tech.md`: note detekt/ktlint versions + the alpha/CLI rationale (dependency/tooling
  convention). `structure.md`: add the `config/` dir + `lint-kotlin.sh` if it indexes top-level structure.

- [ ] **Step 6: Commit.**
  ```
  # NOTE: ADR-00NN below must be the RESOLVED filename (e.g. ADR-0037-kotlin-lint-enforcement.md), not the literal placeholder.
  git add docs/agent/DECISIONS/ADR-00NN-kotlin-lint-enforcement.md CLAUDE.md CHANGELOG.md README.md docs/steering/
  git commit -m "docs: ADR + sync for Kotlin lint enforcement (detekt + ktlint) (#lint)"
  ```

---

### Task 6: STATE.md + RUN_LOG.md

- [ ] **Step 1: STATE.md** — rotate Current objective (new lint-enforcement objective on top: what landed, the
  alpha/CLI integration, baseline-gated, the deferred type-resolution boundary; demote the prior objective).
  No headline test-count change (no JVM tests added).
- [ ] **Step 2: RUN_LOG.md** — new dated entry: goal, the spec→gate→plan→gate process, the detekt-plugin +
  ktlint-CLI design, the build-green-first verification (+ whether the contingency fired), the two
  mutation-tested gates, the accepted boundary, ADR number, doc-sync list, what remains.
- [ ] **Step 3: Commit.** `git add docs/agent/STATE.md docs/agent/RUN_LOG.md && git commit -m "docs(state): checkpoint Kotlin lint enforcement (#lint)"`

---

### Task 7: Final gate + push + PR

- [ ] **Step 1: Full gate** — `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/final.log 2>&1; echo $?; tail -15 /tmp/final.log` → BUILD SUCCESSFUL; plus `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` both exit 0; `actionlint` clean.
- [ ] **Step 2: Push + PR.** `git push -u origin feat/lint-enforcement-detekt-ktlint` then `gh pr create` with a
  summary (detekt plugin + ktlint CLI gate, baseline-gated, code-gated behind the fast path, alpha rationale,
  the deferred type-resolution boundary). The PR touches `.github/` + build files → it runs the FULL gate incl.
  the new detekt + ktlint steps (self-validating). Watch both CI lanes; merge when green.

---

## Self-review checklist (run before execution)

- **Spec coverage:** §4.A detekt plugin→Task 1; §4.B ktlint CLI/`.editorconfig`/baseline/`lint-kotlin.sh`→Task 2;
  §4.C CI→Task 3; §5 verification (smoke + mutation)→Tasks 1.6 + 4; §7 acceptance (ADR + CLAUDE/CHANGELOG/README
  /steering + STATE/RUN_LOG)→Tasks 5-6. ✓
- **Build-green-first** (the central alpha-on-AGP-9 risk): Task 1 Step 6 verifies BEFORE commit + has the
  CLI-via-JavaExec contingency. ✓
- **Baseline-gated, never-CI-regenerated:** Tasks 1.5 + 2.3 generate + commit; CI only RUNS the gate. ✓
- **Fast-path preserved:** Task 3 Step 2 confirms docs-only PRs skip both steps. ✓
- **ktlint `-F` ≠ baseline correction** (review VER-1): Task 2 Step 2 splits the two codepaths. ✓
- **SHA pin concrete** (review SUP-1): Task 2 Step 3 + Task 3 Step 1 pin+verify `KTLINT_SHA256`. ✓
- **No production Kotlin / no schema / no test-count change.** ✓
