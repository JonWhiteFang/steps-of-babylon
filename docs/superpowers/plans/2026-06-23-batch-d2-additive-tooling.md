# Plan — Batch D2: Additive CI Tooling — Coverage + SCA (audit findings, 2026-06-23)

**Status:** REVIEWED (adversarial review gate passed 2026-06-23 — see "Review outcome" at end)
**Scope:** Add two NON-GATING informational CI capabilities. **No app/Kotlin/schema change; no change to
existing gate behavior.**
**Source:** Batch D of the audit-tracker triage (HEAD `617babd`; re-grounded at `13d19c2`).
**Findings closed:** #262 **L77** (build/test-time supply-chain unscanned) + **#218 / TEST-3** (no coverage tooling).
**Split from D1:** isolated because adding a Gradle plugin (Kover) + a new scanner can be flaky; keeping it
separate means a tooling hiccup can't block D1's 8 config-hardening fixes. **Sequenced AFTER D1 merges**
(both edit `ci.yml` — serial to avoid conflict).

## Decisions (developer-approved, this session)
- **D2 ships separately, after D1.** "Everything" scope was approved, split D1/D2 by risk.
- Both tools are **informational / non-gating** — they upload artifacts / annotate, they do NOT fail the PR
  on a threshold (a solo v1.0 must not be blocked by a coverage % or a transitive-dep advisory). #218's own
  text says "do not gate on a threshold."

## Edit list (grounded at HEAD `13d19c2`)

### #218 / TEST-3 — Kover coverage (informational artifact)
1. **Add Kover to the version catalog** `gradle/libs.versions.toml`: a `koverVersion` + a `[plugins]` entry
   `kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "koverVersion" }`. **Pick a Kover version
   compatible with Kotlin 2.3.0** (confirm against the Kover releases — likely the current 0.9.x line; do
   NOT guess — verify the Kotlin-version compat matrix). Kover is JVM-only (no emulator), measures the
   `testDebugUnitTest` JVM suite.
2. **Apply it:** root `build.gradle.kts` `alias(libs.plugins.kover) apply false`; `:app/build.gradle.kts`
   `alias(libs.plugins.kover)` in the `plugins {}` block. Default Kover config measures `debug` — confirm it
   binds to the `testDebugUnitTest` source set without extra config (the 1256-test JVM suite).
3. **CI step (informational):** in `ci.yml` build-and-test, after the existing test step, add
   **`./gradlew :app:koverXmlReport :app:koverHtmlReport`** (REVIEW: `:app:`-prefixed everywhere — the
   un-prefixed form could resolve the task across all modules incl. the benchmark modules; prefix pins it to
   `:app`). Gated on `code == 'true'`; add the Kover report dir to the existing "Upload reports" artifact
   paths (`**/build/reports/kover/**`). **No threshold, no gate** — a downloadable artifact only.
   **REVIEW (ci.yml placement vs D1):** this step lands in `build-and-test` AFTER the "Lint, unit tests,
   debug build" step (it needs the compiled+tested classes). D1 moves *ktlint* OUT of build-and-test into its
   own job — no conflict (different region), but **D2 rebases on D1's merged ci.yml** and adds the Kover step
   to the (D1-modified) build-and-test job + the (unchanged) "Upload reports" artifact paths.
   - **Scope to `:app`:** the alias is applied only in `:app/build.gradle.kts` (not the benchmark build
     files), and the CI task is `:app:`-prefixed — double-scoped away from the `com.android.test` modules.
   - **⚠️ BUILD-BREAK GATE (REVIEW — confirmed major): strict dependency-verification (#256) + Kover.**
     `gradle.properties:12 dependency-verification=strict` + `verification-metadata.xml` has
     `<verify-metadata>true</verify-metadata>` and ZERO `kover` entries. Applying the Kover plugin pulls new
     plugin-classpath + report artifacts → the build **fails at resolution on missing checksums**, before any
     report runs. So regenerating `verification-metadata.xml` is a **HARD, NON-OPTIONAL step**, not a "watch".
     **AND** the regen command documented in `gradle.properties:9-11` does **NOT** include the Kover tasks —
     running it verbatim would still miss Kover's artifacts. The regen invocation must be **extended to
     include the Kover report tasks**, e.g. append `:app:koverXmlReport :app:koverHtmlReport` to the
     `--write-verification-metadata sha256 --refresh-dependencies` task list, then **update the documented
     command in `gradle.properties:9-11`** to match so future regens capture Kover too. Diff the regenerated
     file — only Kover-related artifacts should be added.

### L77 — build/test-time supply-chain scanning (informational)
4. **Add an SCA scan as a separate non-gating CI job/step.** The current `dependency-submission.yml` is
   scoped to `releaseRuntimeClasspath` only (deliberately — the shipped graph), so build/test-time deps are
   unscanned. Add an **OSV-Scanner** (or Trivy) step that scans the full Gradle dependency set / lockfile.
   - **Approach (reviewer to pick):** (a) **OSV-Scanner GitHub Action** (SHA-pinned, matches the repo's
     pinning discipline — ADR-0018 decision #5) scanning the repo, OR (b) Trivy fs scan. Prefer OSV-Scanner
     (lighter, Google's OSV DB, good Gradle support via the lockfile/`verification-metadata.xml`).
   - **Non-gating:** `continue-on-error: true` or report-only output (SARIF upload to the Security tab is
     ideal — visible without blocking). Scoped to a new lightweight job (parallel, like the ktlint job from
     D1) or a step on `main`-only (like dependency-submission.yml). **Reviewer call:** on-PR (fast feedback,
     but noisy) vs on-`main`/scheduled (less noise). Default: **scheduled weekly + on-`main`**, SARIF to the
     Security tab, non-gating — surfaces build/test-time CVEs without PR friction.
   - SHA-pin the action; add to the SHA-pin set Dependabot maintains.

## Verification
- **No app/test change → test count unchanged (1256 JVM).**
- **Kover:** `./run-gradle.sh :app:koverXmlReport :app:koverHtmlReport` → BUILD SUCCESSFUL; a report appears
  under `app/build/reports/kover/`. Sanity-check the HTML opens and shows a plausible % (not 0%, not error).
- **verification-metadata regen (MANDATORY, not conditional):** the Kover plugin WILL trip strict
  verification. Regen with the **Kover-task-extended** command (the gradle.properties command + `:app:koverXmlReport
  :app:koverHtmlReport`), **update the documented command in gradle.properties:9-11** to include the Kover
  tasks, and **diff the result** (only Kover-related artifacts added — no unrelated churn). Then confirm
  `./run-gradle.sh testDebugUnitTest` AND `./run-gradle.sh :app:koverXmlReport` both pass with strict
  verification on.
- **SCA:** the scanner step runs and produces output/SARIF without failing the lane (non-gating). If it's a
  scheduled/main job it can't be PR-validated — note that, like D1's release-lane caveat.
- **`actionlint`** the new/edited YAML if available.

## PR Task-List
1. Apply edits 1–4.
2. Verify: Kover report generates; `testDebugUnitTest` (1256) still green under strict verification;
   `verification-metadata.xml` regenerated + diffed if needed; SCA step syntactically valid.
3. **Sync current-state docs:** CHANGELOG `[Unreleased]`; `docs/plans/plan-32-ci.md` + ADR-0018 (new CI
   capabilities); `docs/steering/tech.md` (Kover in the tooling list); gradle.properties comment if the
   verification-metadata command context changes. **No test-count change.**
4. **Update `docs/agent/STATE.md` + append `RUN_LOG.md`.**
5. Commit on branch `ci/batch-d2-tooling`; open PR; check off L77 + #218 in #262/#218 on merge.

## Risk
**Low–moderate.** Both additions are non-gating, so they can't block the existing pipeline. The real risk is
**build-graph perturbation:** (a) Kover + strict dependency-verification (#256) → must regen
`verification-metadata.xml` or the build fails — the single most likely break, explicitly verified in the
plan; (b) Kover version incompatibility with Kotlin 2.3.0 → confirm the compat matrix before pinning; (c)
Kover scoping into the `com.android.test` benchmark modules → scope to `:app`. The SCA step is isolated and
`continue-on-error`, so it's the safest piece. No app behavior, no schema.

## Review outcome (adversarial gate, 2026-06-23)

Reviewed concurrently with D1 (4 dimensions × verify→skeptic at HEAD `13d19c2`). The wiring pattern
(catalog + root `apply false` + `:app` alias), `:app`-scoping, Kotlin-2.3.0 compat deferral, and the SCA
non-gating/SHA-pinned/scheduled design were all **confirmed sound**. Two CONFIRMED majors materially
hardened the plan (both about the strict-verification interaction — the most likely break):

- **verification-metadata regen is now a HARD gating step, not a "watch"** — applying Kover under
  `dependency-verification=strict` fails at resolution on missing checksums before any report runs.
- **the documented regen command (gradle.properties:9-11) omits the Kover tasks** — running it verbatim
  would still miss Kover's artifacts. The plan now extends the regen invocation with
  `:app:koverXmlReport :app:koverHtmlReport` AND updates the documented command so future regens capture it.

Plus two consistency fixes (confirmed): the CI Kover task is now `:app:`-prefixed everywhere (un-prefixed
could pull the benchmark modules), and the ci.yml placement vs D1's ktlint-split is specified (D2 rebases on
D1's merged ci.yml; Kover step in build-and-test, ktlint already moved out — no conflict). **Sequencing
confirmed: D2 ships AFTER D1** (both edit ci.yml).
