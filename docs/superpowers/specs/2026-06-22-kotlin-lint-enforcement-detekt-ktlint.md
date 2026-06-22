# Design Spec — Kotlin lint enforcement: detekt (Gradle plugin) + ktlint (CLI gate)

**Date:** 2026-06-22
**Issue:** none filed yet (developer request — make the CLAUDE.md "Preferred CLI tooling" `detekt`/`ktlint`
entry real: wired + CI-enforced, not a vague "run them" suggestion). Companion to PR #311 (the sg/fd/delta
guidance + nudge hook), which fixed the *search-tool* half; this fixes the *static-analysis* half.
**Status:** Draft — pending Adversarial Review Gate (spec stage)
**Scope decision (developer):** "detekt Gradle plugin + ktlint CLI gate" — each tool routed through its
**lowest-risk** integration on this toolchain, CI-enforced via the PR gate, behind the docs-only fast path.

---

## 1. Problem

`CLAUDE.md` lists `detekt`/`ktlint` under "Preferred CLI tooling", but they are **not wired into the build
and never run** — no Gradle plugin, no committed config (`config/detekt/*`, `.editorconfig` all absent), no
CI step. So Kotlin static-analysis + formatting are unenforced; the guidance is aspirational. The companion
PR #311 already corrected the *doc* to say "manual CLI for now, wiring in progress" — this spec lands the
wiring so the claim becomes true.

**Why this is non-trivial (the real constraint):** the toolchain is bleeding-edge — **Kotlin 2.3.0 / AGP
9.2.1 / Gradle 9.6.0**, with **AGP-9 built-in Kotlin** (the project applies NO `org.jetbrains.kotlin.android`
plugin; doing so on a `com.android.*` module is an apply-time ERROR under AGP 9 — see `build.gradle.kts`
lines 11-12). Kotlin 2.3.0 has **no stable lint tooling yet** (research, 2026-06-22):
- **detekt:** no stable release supports Kotlin 2.3.0. Only the `2.0.0-alpha` line does. Latest published =
  `2.0.0-alpha.5` (Maven `dev.detekt:detekt-gradle-plugin`, plugin id **`dev.detekt`** — the legacy
  `io.gitlab.arturbosch.detekt` id is the dead 1.x line). alpha.4 was **built against AGP 9.2.1 / Gradle
  9.5.1** (exact AGP match) and declares config-cache compatibility; alpha.5 is a dep-leak/POM hotfix on top.
  The locally-installed `detekt` CLI is **1.23.8** (Kotlin 2.0.21 engine — too old; irrelevant to the plugin
  path, which downloads its own engine).
- **ktlint:** **no** ktlint release embeds a Kotlin 2.3.x parser yet — latest **1.8.0** (the locally-installed
  CLI) embeds a **2.2.21** parser. It parses the vast majority of 2.3.0 source fine; the only risk is a file
  using net-new 2.3 syntax the 2.2.x PSI parser rejects. The JLLeitschuh **ktlint-gradle plugin's**
  AGP-9-built-in-Kotlin source-set detection is **brand new (v14.1.0, Mar 2026) and unproven on AGP 9.2.1**;
  its default engine (1.5.0) is far too old and must be overridden. → the **standalone CLI is lower-risk**
  (zero AGP/Gradle integration surface; same parser caveat either way).

**Grounded facts (verified this session):** `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` with
`google()` + `mavenCentral()` in both `pluginManagement` and `dependencyResolutionManagement` (so the detekt
plugin + its engine resolve without a per-project repo). ~**500 `.kt` files** in `app/src/{main,test}` →
existing violations are certain → **baselines are mandatory** (only NEW findings may fail). No `config/` dir,
no `.editorconfig` today. CI is `ci.yml` (`build-and-test` job, the required PR-gate check) + `instrumented.yml`
(`connected`); both now have the docs-only fast path (the `changes` classifier gating heavy steps on
`needs.changes.outputs.code == 'true'`, PR #310).

## 2. Goal & non-goals

**Goal:** make `detekt` + `ktlint` real and CI-enforced —
- **detekt** as a **Gradle plugin** (`dev.detekt` `2.0.0-alpha.5`), the **plain `detekt` task (no type
  resolution)** initially, with a committed `config/detekt/detekt.yml` + `config/detekt/baseline.xml`, folded
  into the `build-and-test` CI job (gated behind the docs-only fast path).
- **ktlint** via the **standalone CLI `1.8.0`**, invoked by a committed `lint-kotlin.sh` (a `run-gradle.sh`
  sibling) with a committed `.editorconfig` + `ktlint-baseline.xml`, run as a CI step. CI obtains ktlint via
  a **pinned, checksum-verified** download (runners don't ship it) — matching the repo's wrapper-validation
  supply-chain posture.
- Both run **only on code changes** (the docs-only fast path skips them), so PR #311's checkpoint speed-up is
  preserved.
- A one-time **baseline** of the current ~500-file violation set so the gate fails only on NEW violations.

**Non-goals (explicit):**
- **detekt type resolution** (`detektMain`/`detekt<Variant>`) — deferred. It needs the AGP-variant compile
  classpath wired under AGP-9 built-in Kotlin, which is the unproven path (research §2 caveat). Start with the
  plain `detekt` task (catches the bulk of style/complexity smells, no classpath dependency); a follow-up may
  add type resolution after a proven smoke-test.
- **ktlint-gradle plugin** (JLLeitschuh) — rejected for this wave (unproven AGP-9 source-set detection); the
  CLI is the chosen integration.
- **Auto-formatting in CI** (`ktlint -F` / detekt `--auto-correct`) — the gate is **check-only** (read-only,
  fails on new violations). `ktlint -F` is a documented *local* convenience, never a CI mutation.
- **Linting the `:baselineprofile` / `:macrobenchmark` modules** — out of scope; they're tiny non-shipping
  dev-tooling. detekt applies to `:app` only.
- **Zero-violation cleanup** — NOT this wave. We baseline existing violations, not fix them. (A later cleanup
  pass can burn down the baseline.)
- **Bumping the local Homebrew detekt CLI** — irrelevant; the Gradle plugin owns detekt's engine.
- **Failing the build on the alpha/parser caveats** — if specific files can't be parsed by the ktlint 2.2.x
  engine or trip the detekt alpha, they're excluded (documented) rather than blocking the gate.

## 3. Invariants & constraints

1. **Do NOT apply `org.jetbrains.kotlin.android`** to satisfy either tool — apply-time ERROR under AGP 9
   (`build.gradle.kts:11-12`). detekt keys off AGP, not the Kotlin plugin; ktlint CLI needs no Gradle plugin
   at all.
2. **Version-catalog single source.** The detekt plugin version pins in `gradle/libs.versions.toml`
   (`[versions]` + `[plugins]`), declared `apply false` on the root `build.gradle.kts`, applied by alias in
   `app/build.gradle.kts` — matching the established `benchmark`/`baselineprofile` alpha-pin pattern. The
   ktlint version pins as a constant in `lint-kotlin.sh` + the CI workflow (single source: the script; CI
   calls the script).
3. **Alpha/pre-release pins are documented** in the catalog comment with the same rigor as
   `benchmark = "1.5.0-alpha06"` — why alpha is unavoidable (no stable Kotlin-2.3 line), that it's dev/CI
   tooling only (never enters the AAB), and the revisit condition.
4. **Behavior-preserving:** no production Kotlin changes, no schema/economy/engine change. New files only
   (config + script + CI steps + catalog/build-glue). Existing `testDebugUnitTest lintDebug assembleDebug`
   gate stays green and unchanged.
5. **Baseline-gated:** the ~500-file existing-violation set is captured in baselines so the gate is RED only
   on NEW violations. Baselines are committed and regenerated deliberately (never auto-regenerated in CI —
   that would mask new violations).
6. **Docs-only fast path preserved:** detekt + ktlint steps live inside the `build-and-test` job's
   code-gated region (`if: needs.changes.outputs.code == 'true'`), so a docs-only PR skips them (green in
   seconds, per PR #310).
7. **Supply-chain:** the CI ktlint download is **version-pinned + SHA-256-verified** before execution
   (mirrors the `gradle/actions/wrapper-validation` + `distributionSha256Sum` posture). The detekt plugin +
   engine resolve from `mavenCentral()` (already trusted; SHA-pinned actions unaffected).
8. **Gradle must stay green first.** detekt-as-plugin perturbs the `:app` configuration; the implementation
   MUST verify a full `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug` + `./run-gradle.sh :app:detekt`
   build is green on the real toolchain BEFORE committing (the research §2 AGP-9-built-in-Kotlin smoke-test).
   If the detekt plugin breaks the build, fall back to the detekt **CLI-via-`JavaExec`** path (download the
   `dev.detekt:detekt-cli:2.0.0-alpha.5` jar — main class `dev.detekt.cli.Main`) and record the fallback.

## 4. Component design

### A. detekt — Gradle plugin (`dev.detekt` 2.0.0-alpha.5), plain task

**Catalog (`gradle/libs.versions.toml`):**
```toml
# [versions]
# detekt 2.0.0-alpha line: the ONLY line supporting Kotlin 2.3.x (stable 1.23.8 caps at Kotlin 2.0.21).
# alpha.4 built against AGP 9.2.1 / Gradle 9.5.1 + declares config-cache compat; alpha.5 = dep-leak/POM
# hotfix. Alpha is unavoidable on this toolchain (cf. benchmark=1.5.0-alpha06) and is DEV/CI-ONLY — detekt
# never enters the AAB. Plugin id is `dev.detekt` (2.0 line); legacy `io.gitlab.arturbosch.detekt` is 1.x.
# Revisit when a stable 2.0.0 supporting Kotlin 2.3+ ships.
detekt = "2.0.0-alpha.5"
# [plugins]
detekt = { id = "dev.detekt", version.ref = "detekt" }
```
**Root `build.gradle.kts`:** `alias(libs.plugins.detekt) apply false` (pins the version once on the root
classpath, matching the existing `apply false` block).
**`app/build.gradle.kts`:** apply `alias(libs.plugins.detekt)` + a `detekt { }` block:
```kotlin
detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true   // layer the committed config on detekt's defaults
    parallel = true
}
```
**Config:** `config/detekt/detekt.yml` — start from `./run-gradle.sh detektGenerateConfig` (or the documented
default), trimmed to a sane house ruleset. Disable rules that fight the existing style (e.g.
`MagicNumber`/`LongMethod` thresholds tuned, formatting rules off if they overlap ktlint — detekt + ktlint
must NOT both own formatting; ktlint owns formatting, detekt owns code-smell/complexity). The implementer
tunes against the real violation surface.
**Baseline:** `./run-gradle.sh :app:detektBaseline` → `config/detekt/baseline.xml` (committed). Records all
current violations; only NEW ones fail thereafter.
**Task used by CI:** the plain `:app:detekt` (no type resolution) — `./run-gradle.sh :app:detekt`.
**Generated sources:** detekt 2.0.0-alpha excludes generated sources by default (#9251); keep a belt
`.*/build/.*` exclude in the config.

### B. ktlint — standalone CLI 1.8.0 via `lint-kotlin.sh` + CI download

**`.editorconfig` (repo root, new):** ktlint's config source.
```editorconfig
root = true
[*]
charset = utf-8
insert_final_newline = true
[{*.kt,*.kts}]
ktlint_code_style = ktlint_official     # or intellij_idea — implementer picks the lower-churn baseline
max_line_length = 120
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
# Disable rules that fight the codebase / overlap detekt as needed, e.g.:
# ktlint_standard_no-wildcard-imports = disabled
```
(Risk: a root `.editorconfig` ALSO influences IDEs/other tools. Scope ktlint rules under the `[{*.kt,*.kts}]`
section so non-Kotlin files are unaffected beyond charset/final-newline, which are benign + near-universal.)
**Baseline:** `ktlint --baseline=config/ktlint/baseline.xml "app/src/**/*.kt"` (committed) — records current
violations; only NEW fail. (`ktlint -F` format IGNORES the baseline by design — local convenience only.)
**`lint-kotlin.sh` (repo root, new — `run-gradle.sh` sibling, gitignored? NO — committed, unlike run-gradle.sh):**
a thin wrapper that runs the pinned ktlint against `app/src` with the baseline. Pins `KTLINT_VERSION=1.8.0`.
Locates ktlint: prefer a repo-cached `./.ktlint/ktlint-1.8.0` (CI-downloaded), else a PATH `ktlint` whose
`--version` matches, else instructs how to get it. Check-only by default; `lint-kotlin.sh --format` runs `-F`.
**CI obtains ktlint (the runner has none):** a `build-and-test` step (code-gated) downloads the pinned ktlint
`1.8.0` self-executing jar from its GitHub release, **verifies the SHA-256** against a committed constant,
chmod +x, caches it, then runs `lint-kotlin.sh`. SHA-pinned + checksum-verified = consistent with #212.

### C. CI integration (`.github/workflows/ci.yml`, `build-and-test` job)

Add two steps to `build-and-test`, BOTH carrying the existing `if: needs.changes.outputs.code == 'true'`
guard (so docs-only PRs skip them):
1. **detekt** — `./gradlew :app:detekt` (after the existing lint/test/build step; reuses the already-set-up
   Gradle + JDK from the job). Or fold into the existing one-call line; keep separate for a clear failure
   signal.
2. **ktlint** — download+verify pinned ktlint (per §B) → `./lint-kotlin.sh`.
Both are check-only and fail the job (and thus the required `build-and-test` check) on a NEW violation.
The `connected` (instrumented) job is **untouched** — lint belongs on the cheap JVM lane, not the emulator.

## 5. Testing / verification strategy

This is a build/CI change — "tests" = build-green proofs, not JUnit:
1. **detekt plugin smoke-test (the central risk):** `./run-gradle.sh :app:detekt` configures + runs green on
   the real AGP-9-built-in-Kotlin build; a full `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`
   stays green with the plugin applied (no configuration breakage). If it fails → the §3.8 CLI-via-JavaExec
   fallback.
2. **Baselines suppress existing violations:** immediately after generating each baseline, `:app:detekt` and
   `lint-kotlin.sh` both exit 0 on the unchanged tree (proves the gate is quiet on the status quo).
3. **The gate actually catches a NEW violation (mutation test):** introduce a deliberate violation (e.g. a
   wildcard import / an over-long line / a detekt smell) in a scratch file → confirm the respective tool exits
   non-zero → revert. Proves the gate isn't a no-op.
4. **ktlint parser caveat:** run ktlint over all of `app/src` once; if any file fails to PARSE (not lint) due
   to the 2.2.x-vs-2.3.0 gap, exclude it (documented) — don't let it block.
5. **Docs-only fast path intact:** the lint steps sit inside the code-gated region → a docs-only PR still
   skips them (re-confirm the `changes` classifier verdict; cf. PR #310).
6. **CI dry-run:** the change touches `.github/**` + build files, so its own PR classifies as CODE and runs
   the full gate incl. the new detekt + ktlint steps — self-validating on the PR.

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| detekt alpha plugin breaks the AGP-9-built-in-Kotlin `:app` configuration | §5.1 smoke-test BEFORE commit; §3.8 CLI-via-JavaExec fallback (download `dev.detekt:detekt-cli:2.0.0-alpha.5`, main `dev.detekt.cli.Main`) if the plugin won't apply. |
| detekt is an ALPHA (instability/behavior drift) | Dev/CI-only (never shipped — same acceptance as `benchmark=1.5.0-alpha06`); pinned exactly; documented in catalog; baseline absorbs current state so an alpha quirk can't retroactively fail unchanged code. |
| ktlint 1.8.0's 2.2.x parser rejects a net-new-2.3-syntax file | §5.4 full-tree parse pass; exclude the offending file via `.editorconfig`/glob (documented) until a 2.3-parser ktlint ships. Low exposure (plain MVVM/Compose Kotlin). |
| CI runner has no ktlint | §B pinned + SHA-256-verified download (cached); script locates it deterministically. |
| detekt + ktlint both "fixing" formatting → conflicting rules | Clear ownership: **ktlint owns formatting**, **detekt owns code-smell/complexity**; disable detekt's formatting ruleset overlap in `detekt.yml`. |
| Baseline masks NEW violations (gate becomes a no-op) | §5.3 mutation test proves a NEW violation fails; baselines are committed + only regenerated deliberately, never in CI. |
| Root `.editorconfig` leaks rules to non-Kotlin files / IDEs | Kotlin rules scoped under `[{*.kt,*.kts}]`; top-level keys limited to benign charset/final-newline. |
| Lint slows the PR gate | Both on the cheap JVM `build-and-test` lane (not the emulator), code-gated (docs PRs skip), parallel detekt; ktlint over `app/src` is fast (~500 files). |
| Wildcard `app/src/**/*.kt` glob in CI shell vs local shell expansion | Quote the glob so ktlint (not the shell) expands it; verify in CI dry-run. |

## 7. Acceptance criteria

- `config/detekt/detekt.yml` + `config/detekt/baseline.xml` committed; detekt `dev.detekt:2.0.0-alpha.5`
  wired via the version catalog (`apply false` root + `:app` apply); `./run-gradle.sh :app:detekt` green on
  the unchanged tree; a full `testDebugUnitTest lintDebug assembleDebug` stays green.
- `.editorconfig` + `config/ktlint/baseline.xml` + committed `lint-kotlin.sh` (pinned ktlint 1.8.0,
  check-only default, `--format` opt-in); `./lint-kotlin.sh` green on the unchanged tree.
- `ci.yml` `build-and-test` runs both, code-gated behind the docs-only fast path; CI obtains ktlint via a
  pinned SHA-256-verified download; the `connected` job is untouched.
- Mutation test confirms a NEW detekt smell AND a NEW ktlint violation each fail the gate; reverted.
- A docs-only PR still skips both lint steps (fast path intact).
- `CLAUDE.md` detekt/ktlint line updated from "manual CLI for now" → "wired + CI-enforced" (with the exact
  invocations: `./run-gradle.sh :app:detekt`, `./lint-kotlin.sh [--format]`). ADR added (the alpha/CLI
  integration decision + the AGP-9 constraint + the deferred type-resolution boundary). source-files.md /
  tech.md / structure.md synced for the new config/script files.
- All changes are net-new files + build-glue + CI; zero production Kotlin change; no schema/economy/engine
  change.
