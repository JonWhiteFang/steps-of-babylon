# ADR-0037: Kotlin lint enforcement — detekt + ktlint CI gate (#311)

Status: Accepted (2026-06-22)

## Context
- `CLAUDE.md` listed `detekt`/`ktlint` as "preferred CLI tooling" and instructed "run on the Kotlin you
  changed before committing," but the guidance was **purely advisory** — no build task, no CI step, no
  baseline. New violations accumulated silently; the tools weren't wired into the project at all.
- **No stable lint tooling exists for Kotlin 2.3.0.** detekt's stable line (1.23.x) targets Kotlin ≤ 2.0;
  ktlint's built-in parser tracks KtLint's own Kotlin-compiler fork (2.2.x as of 1.8.0). The project is on
  Kotlin 2.3.0 (AGP-9 built-in distribution), so only pre-release / alpha tooling is compatible.
- The 2026-06-18 complete-app-review did not flag linting as a HIGH/BLOCKER, but consistent formatting and
  code-smell detection reduce review friction and catch issues earlier in the loop.

## Decision
- **detekt: `dev.detekt` 2.0.0-alpha.5 Gradle plugin** — the alpha line rebuilt for Kotlin 2.x. Applies
  plain `:app:detekt` (no type resolution — see below). Custom config at `config/detekt/detekt.yml`
  (disables `MagicNumber` and `WildcardImport`; no `formatting:` ruleset key — it doesn't exist in detekt
  2.0 without a separate plugin). Baseline at `config/detekt/baseline.xml` (502 lines) — existing
  violations are grandfathered; only NEW violations fail the build.
- **ktlint: 1.8.0 CLI via committed `lint-kotlin.sh`** — SHA-256-verified download in CI; runs CHECK mode
  with a baseline (`config/ktlint/baseline.xml`, 10141 lines) so existing formatting issues don't block;
  FORMAT mode (local `--format`) runs WITHOUT a baseline (fixes everything it can). `.editorconfig` at root
  configures ktlint's rules. No parser failures observed on the 2.2.x engine against this codebase despite
  the Kotlin-version gap.
- **CI wiring:** two new steps in the `build-and-test` job of `ci.yml`, code-gated behind
  `needs.changes.outputs.code == 'true'` (docs-only PRs still skip): (1) `./run-gradle.sh :app:detekt`;
  (2) curl/verify/chmod ktlint → `./lint-kotlin.sh`. The `connected` instrumented job is untouched.
- **Baseline-gated enforcement:** both tools fail only on NEW violations introduced after the baseline was
  captured. The baselines are committed and maintained — as violations are fixed, the baseline shrinks.
- **Mutation-tested:** a `LongParameterList` insertion exits non-zero on detekt; a `max-line-length`
  violation exits non-zero on ktlint. Both reverted after confirming enforcement.

## Alternatives considered
- **ktlint-gradle plugin (e.g. `org.jlleitschuh.gradle.ktlint`):** rejected — unproven compatibility with
  AGP 9.x source-set detection; the CLI is a known-good, version-pinned, SHA-verified artifact.
- **All-CLI approach (detekt CLI too):** rejected — the `dev.detekt` Gradle plugin proved compatible on
  first try after a config fix and is more ergonomic for local dev (`./gradlew :app:detekt`); no benefit
  to an extra download step.
- **Stable-only policy (no alpha deps):** not possible — no stable detekt supports Kotlin 2.3.0; the
  alpha is confined to a dev-tooling Gradle plugin (never shipped in the AAB) and is actively maintained.
- **Type resolution in detekt:** deferred — requires proven AGP-9-variant classpath wiring that is
  untested; plain detekt catches complexity/naming/style without it; revisit when detekt 2.0 stabilizes.
- **Lint `:baselineprofile` / `:macrobenchmark` modules:** deferred — they are dev-tooling only (never
  shipped); linting them adds CI time for minimal value; revisit if they grow.
- **Auto-format in CI:** rejected — CI should be a gate (fail on new violations), not a rewriter;
  auto-format is a local developer convenience (`./lint-kotlin.sh --format`).

## Consequences
- Positive: new code-smell and formatting violations now fail the PR gate immediately; the baseline
  approach avoids a "fix the world" prerequisite; local developers get `./run-gradle.sh :app:detekt` +
  `./lint-kotlin.sh [--format]` for fast feedback.
- Negative / tradeoffs: an **alpha dependency** (`dev.detekt` 2.0.0-alpha.5) that must be monitored for
  breaking changes on Kotlin/AGP bumps; baselines require periodic maintenance (shrink as violations are
  fixed, or they become stale allowlists); ktlint's 2.2.x parser is a known version gap vs Kotlin 2.3.0
  (no actual parse failures in this codebase, but new 2.3.0 syntax may trigger them — watch for CI
  regressions after Kotlin upgrades).
- Follow-ups: monitor detekt 2.0 for a stable release; consider type resolution once AGP-9 classpath
  wiring is documented; shrink baselines as tech-debt sprints retire violations.

## Links
- PR: #311 (this PR).
- Related: ADR-0018 (CI pipeline — Plan 32); the existing `ci.yml` `build-and-test` job this extends.
