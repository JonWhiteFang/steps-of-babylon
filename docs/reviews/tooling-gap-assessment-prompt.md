# Steps of Babylon — Tooling Gap Assessment (audit prompt)

> Reusable prompt for a read-only tooling/automation audit of this repo. Adapted from a generic
> "software engineering tooling auditor" template to Steps of Babylon's real context: a
> solo-developer, offline-first Android game (Kotlin + Gradle + Jetpack Compose), released to the
> Play internal track, developed heavily with Claude Code / ultracode.

---

You are working as a senior software-engineering tooling auditor for **Steps of Babylon**, using ultracode.

Your task is to assess this repository and identify gaps in tooling, automation, developer workflow, quality control, security, testing, documentation, deployment, observability, and maintainability — **for its actual context: a solo-developer, offline-first Android game built with Kotlin + Gradle + Jetpack Compose, released to the Play internal track, and developed heavily with Claude Code / ultracode.**

Do **not** make changes. This is an audit and recommendation pass only. Do not install packages, run destructive commands, mutate infrastructure, or create commits.

## Guiding constraints (read first — this repo is not generic)

- **It is a mobile app, not a service.** There is no server backend, no API surface, no container/K8s/Terraform layer, no `.env`, no npm/pip package manager. **Do not invent gaps in those areas** — either mark them *Not applicable* with one line of justification, or omit them. Recommending Docker Compose, OpenAPI, Storybook, or `.nvmrc` here is a failure of the audit.
- **It is a solo project.** Onboarding-for-a-team, branch-protection-for-many, backlog-hygiene-for-a-team recommendations must be justified against a single maintainer + AI agents, not a hypothetical team. Prefer lightweight over process-heavy. Explicitly avoid over-engineering.
- **The tooling baseline is already high.** Before flagging anything as "missing", confirm it against the actual files — this repo already has CI, detekt, ktlint, Dependabot, OSV scanning, a signed-AAB release lane, ADRs, a committed "memory spine", and a `CLAUDE.md`. Credit what exists; challenge it where it's weak; only flag genuine gaps.
- **The Amazon/Brazil global rules in the session context do not apply to this repo.** This is a public GitHub Android project built with Gradle, not a Brazil package. Ignore Brazil/Coral/Apollo/Pipelines guidance for this audit.
- Every recommendation must be justified by a **real, demonstrated need in this repo**. No tool recommended for popularity.

## Working method

Inspect actual files, not just the README or CLAUDE.md. Ground every claim in a real path. Start by confirming the stack and the existing tooling, then work in passes.

**Orient using the project's own sources of truth** (do not restate them — verify against them):
- `CLAUDE.md`, `docs/agent/START_HERE.md`, `docs/agent/STATE.md`, `docs/agent/CONSTRAINTS.md`
- `docs/plans/master-plan.md`, `docs/agent/DECISIONS/` (ADRs), `docs/steering/` (tech/structure/source-files/security-model)
- Build & config: `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `.editorconfig`, `config/detekt/`, `config/ktlint/`, `run-gradle.sh`, `lint-kotlin.sh`
- CI/CD: `.github/workflows/` (`ci.yml`, `instrumented.yml`, `osv-scan.yml`, `dependency-submission.yml`, `release.yml`, `pages.yml`), `.github/dependabot.yml`
- Release/security: `keystore.properties`, `local.properties`, `app/schemas/`, network security config, `site/` (public privacy policy)
- Tests: `app/src/test/…` (JVM lane) and `app/src/androidTest/…` (instrumented lane), the architecture guards (`DomainPurityTest`, `PresentationPurityTest`), `SimulationTest`, `AtomicDaoConcurrencyTest`

Passes:
1. Project overview & stack confirmation
2. Tooling inventory (what genuinely exists, verified in-file)
3. Quality & testing
4. Security & dependencies
5. Developer experience (solo + AI-agent)
6. Deployment & release operations (Play internal track, signing, release lane)
7. AI-agent readiness (the memory spine, guardrails, purity tests, fragile-zone docs)
8. Final prioritised recommendations

**Use ultracode** to run this as a multi-agent audit: fan out one reviewer per facet, have each cite the real `file:line`/config it inspected, then run an adversarial verify pass that tries to refute each claimed gap against the actual repo (default-to-refuted — many "gaps" here are already covered), and synthesize only surviving findings. Scale the fan-out to audit-grade thoroughness.

## Audit scope (Android/Kotlin-specific)

Assess these areas. For each, first state what exists, then what's weak or missing, then whether a fix is worth it *for a solo Android game*.

1. **Repository & module structure** — the `:app` / `:baselineprofile` / `:macrobenchmark` split; Clean-Architecture layering (`presentation → domain ← data`); generated/build-artefact handling (`build/`, `app/schemas/`); config layout (`config/`, `gradle/libs.versions.toml`); doc placement (`docs/` internal vs `site/` public). Is it navigable for a new agent?
2. **Development environment** — README setup, JDK/AGP/Gradle/SDK version pinning (`libs.versions.toml`, `gradle.properties`, `.tool-versions`?), `local.properties`/`keystore.properties` handling, the `run-gradle.sh` / `lint-kotlin.sh` helper scripts (note they're gitignored — is recreation documented?), cross-platform (`gradlew`/`gradlew.bat`). Consider only what fits: e.g. a `.sdkmanager`/JDK pin, a documented setup script, an `.editorconfig` audit. Do **not** recommend Docker/devcontainers unless you can justify it for Android builds.
3. **Build & run workflow** — Gradle task clarity (assembleDebug/Release, the two lint scripts, detekt, test tasks), duplicate/confusing commands, whether a task-runner façade (Makefile/Justfile over Gradle) would actually reduce friction or just add a layer.
4. **Code quality tooling** — ktlint + detekt (baseline-gated), `.editorconfig`, the architecture purity tests. Gaps to weigh: dead-code/unused-resource detection, complexity thresholds, Compose-specific lint rules, Android Lint (`lint`) usage, pre-commit hooks. Kotlin-relevant tools only (ktlint, detekt, Android Lint, Compose lint, Konsist); ignore Prettier/ESLint/Ruff/etc.
5. **Testing strategy** — the JVM + instrumented split; fakes in `test/fakes/`; Robolectric Compose UI tests; the concurrency/atomic-DAO guards; coverage reporting (is there any — Kover/JaCoCo?); mutation testing worth it? Assess specifically whether coverage is sufficient to make **AI-assisted changes safe** in the fragile zones (battle game-loop concurrency, currency-spend atomicity).
6. **CI/CD & automation** — the six workflows above. Assess required-status-check coverage, the docs/tooling fast-path classifier (kept in sync across ci/instrumented?), release-lane safety (versionCode handling, signing-secret hygiene), changelog/versioning automation. Prioritise a *minimal useful* pipeline; flag over-engineering too.
7. **Dependency management** — `libs.versions.toml` as the single catalog, Dependabot config, OSV scan + dependency-submission scoping, lockfile presence (Gradle dependency locking?), unused/duplicate deps, licence risk (`licenses/`).
8. **Security tooling** — SQLCipher/Keystore/R8/network-security-config as implemented; secret handling (`keystore.properties`/`local.properties` gitignored?), secret scanning (Gitleaks/GitHub secret scanning), SAST for Android (Semgrep Kotlin rules? MobSF?), the OSV/Dependabot supply-chain coverage. Recommend only what a solo shipped-to-Play app needs.
9. **Documentation** — README, the memory spine, ADRs, `docs/steering/`, CHANGELOG. Identify gaps that slow work or make AI edits riskier. Note absence of `CONTRIBUTING`, issue/PR templates — but judge whether they matter for a solo repo.
10. **Architecture & design support** — ADRs, purity tests as executable architecture enforcement, domain-model docs, the fragile-zone documentation. This is a strength; assess whether the guardrails cover all invariants the code actually relies on.
11. **Database & data tooling** — Room + SQLCipher; `app/schemas/` exported schemas + the CI schema-drift check; migrations; test DB isolation (`AtomicDaoConcurrencyTest` file-backed Room). Any migration/backup gaps for local-only game state?
12. **Mobile tooling** (the relevant "platform" section) — emulator/AVD setup (instrumented lane), build variants, signing config, UI/instrumented testing, **crash reporting** (is there any real crash telemetry beyond the in-app `CrashBreadcrumb`?), Play release automation, screenshot/store-asset automation. This is where real gaps likely live.
13. **Deployment & release operations** — the `v*`-tag → signed-AAB → Play-internal flow, rollback story (staged rollout? previous-version fallback?), the manual prerequisites, versionCode collision safety.
14. **Observability & operations** — for an offline single-player app: crash/ANR reporting, opt-in analytics, the `CrashBreadcrumbStore` game-loop crash path, the `onLoopError` UI state. What's the production-visibility story once it ships? Runbook for a Play crash spike?
15. **Performance tooling** — Baseline Profiles + Macrobenchmark modules (exist — assess whether they're actually run/tracked), game-loop frame-timing, memory/leak detection (LeakCanary?), APK/AAB size tracking. Recommend only where the game loop or startup genuinely needs it.
16. **AI-assisted development support** — the strongest area: `CLAUDE.md`, `docs/agent/*`, the Adversarial Review Gate, purity tests, fragile-zone pointers, `run-gradle.sh` non-TTY guidance, subagent tooling rules. Assess: is there an `AGENTS.md` for non-Claude agents? Are "do-not-touch" zones machine-enforced vs prose-only? Are the verification commands unambiguous? Where could an agent still do damage the guardrails wouldn't catch?
17. **Project management & planning** — master-plan status tracker, ADRs, CHANGELOG, RUN_LOG. Lightweight solo improvements only (e.g. release-notes automation, issue templates *if* they'd help triage the `severity:major` backlog).
18. **Risk assessment** — rank the highest-risk *actual* gaps (e.g. no crash reporting in production, no coverage gate on concurrency-critical code, release rollback story, secret-scanning absence). For each, state the concrete consequence for *this* app.

## Output format

Produce the report in this structure. Keep it grounded in real paths; do not restate CLAUDE.md back at me.

# Steps of Babylon — Tooling Gap Assessment

## Executive summary
- Overall maturity rating: Low / Medium / High (with one-line justification)
- Main strengths (what's genuinely well-tooled)
- Main weaknesses (real gaps only)
- Top 5 recommended improvements

## Detected stack
Languages, frameworks, build tools, test frameworks, CI/CD, release/deploy tooling, doc tooling — as actually found.

## Existing tooling inventory
| Area | Existing tooling | Notes |
|---|---|---|
| Formatting | | |
| Linting / static analysis | | |
| Architecture enforcement | | |
| Type checking (Kotlin compiler/strictness) | | |
| Testing (JVM + instrumented) | | |
| Coverage | | |
| CI/CD | | |
| Security (crypto + scanning) | | |
| Dependencies | | |
| Documentation / memory spine | | |
| Release / Play deployment | | |
| Observability / crash reporting | | |
| Performance (baseline profile / benchmark) | | |
| AI-agent support | | |

## Gap analysis
| Area | Gap | Impact (for this app) | Recommended fix | Priority (High/Med/Low) |
|---|---|---|---|---|

## Prioritised recommendations

### Immediate improvements
For each: why it matters *here* · suggested tool/approach · files likely affected · implementation difficulty (Low/Med/High) · risk (Low/Med/High) · **verification command** (real Gradle/script command for this repo).

### Next improvements
### Optional improvements

## AI-assisted development recommendations
Specific to developing this repo with Claude/ultracode: whether to add/improve `AGENTS.md`, machine-enforced "do-not-modify" zones vs prose, the verification checklist, coverage gates protecting fragile zones, task/PR templates. Credit the existing memory spine + review gate; only recommend real additions.

## Suggested implementation roadmap
- **Phase 1 — Safety baseline** (e.g. crash reporting, coverage gate on concurrency/economy code, secret scanning)
- **Phase 2 — Developer experience** (workflow/setup-script/task-runner improvements that actually reduce friction)
- **Phase 3 — Quality & reliability** (coverage, mutation, Compose lint, benchmark tracking)
- **Phase 4 — Release & operations** (rollback story, release-notes automation, production observability)

## Proposed file additions or changes
Only files that make sense here — e.g. `AGENTS.md`, `.github/ISSUE_TEMPLATE/`, a coverage config (Kover), a Gitleaks config/workflow, a LeakCanary debug dependency, a documented setup script. Explicitly note which conventional files (`.env.example`, Dockerfile, OpenAPI, etc.) are **deliberately N/A** and why.

## Commands to verify the project
List the **real** discovered commands (mark any missing): install/setup, build (debug + release AAB), run, test (JVM lane + instrumented lane), lint (`./lint-kotlin.sh`), detekt (`./run-gradle.sh :app:detekt`), schema-drift check, security/dependency audit (OSV/Dependabot). Recommend what's missing.

## Final judgement
- Is this project safe to develop quickly today?
- Is it safe for AI-assisted coding?
- Single most important tooling gap?
- What to do first?

Be direct. Challenge weak existing practices. Do not pad the report with generic best-practices that don't apply to a solo offline Android game.
