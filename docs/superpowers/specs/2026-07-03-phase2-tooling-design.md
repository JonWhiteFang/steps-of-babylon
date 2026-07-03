# Phase-2 Tooling — Design Spec

> **Status:** design (pre-plan). Source: `docs/reviews/tooling-gap-assessment.md` (2026-07-02),
> tracker **#389**. Covers the four **Phase-2 "Developer experience"** findings:
> **#378 `devenv-1`**, **#388 `docs-2`**, **#387 `pm-1`**, **#386 `ai-1`**.
> Authored 2026-07-03 at HEAD (post-#397, v1.0.12 / vc 28 / schema v12; 1302 JVM + 9 instrumented tests).

## 1. Context & scope

Phase-1 of the tooling-gap remediation (the safety baseline — #370/#376/#371/#372/#374/#380) is
**fully merged** (PRs #393/#394/#395/#397). Phase-2 is the next slice of tracker #389: four
`severity:minor` developer-experience findings. None touches production app behaviour, schema,
economy, or the battle engine; three are docs/skill-only and one is a build-config change.

| # | ID | Finding | Surface | Build impact |
|---|---|---|---|---|
| #378 | `devenv-1` | Pin the local JDK with a Gradle JVM toolchain | `:app` + both benchmark `build.gradle.kts`, README | **yes** (compile) |
| #388 | `docs-2` | Trim `STATE.md` back toward one page | `docs/agent/STATE.md` (+ RUN_LOG/CHANGELOG already hold the detail) | no |
| #387 | `pm-1` | Have `/checkpoint` emit a mechanical `docs/agent/BACKLOG.md` | `.claude/skills/checkpoint/SKILL.md`, new `docs/agent/BACKLOG.md` | no |
| #386 | `ai-1` | Add a thin `AGENTS.md` pointer to `CLAUDE.md` | new top-level `AGENTS.md` | no |

**Explicitly out of scope** (Phase-3/4 of #389, filed separately): #373 Kover ratchet, #375 LeakCanary,
#382 Compose-literal guard, #381 migration-chain test, #384 frame overlay, #379 versionCode guard,
#383 rollback doc, #385 macrobenchmark numbers, #377 OSS attribution, #396 detekt nested-lock rule.

## 2. Packaging (2 PRs, split by build-impact)

Mirrors the Phase-1 precedent of splitting by verification surface.

- **PR-1 — Docs & DX (no build impact):** #386 + #388 + #387. Verify = doc integrity + a
  checkpoint-skill dry-run that regenerates `BACKLOG.md`. No Kotlin/test/config change → **test count
  stays 1302**.
- **PR-2 — JDK toolchain (touches the build):** #378. Verify = full compile across all three modules.

The two PRs are independent and may land in either order; PR-1 is lower-risk and expected first.

## 3. PR-2 — #378 `devenv-1`: pin the JDK via a JVM toolchain

### 3.1 Problem
The build declares only **bytecode-level** targets (`compileOptions { sourceCompatibility =
targetCompatibility = JavaVersion.VERSION_17 }` in all three modules). There is **no Gradle JVM
toolchain**, so the *toolchain that runs the compiler* is whatever ambient JDK Gradle picks. README
names "JDK 17" as prose only. On a first clone with a too-new ambient JDK (21/25), the build fails with
opaque toolchain/KSP errors that CI (which runs JDK 17) never reproduces. This is the audit's only real
"safe to develop quickly" first-clone snag.

### 3.2 Mechanism — **must be settled by an actual build**, not assumed
All three modules use **AGP-9 built-in Kotlin**. Critically, **none applies the standalone Kotlin JVM
plugin** (`org.jetbrains.kotlin.jvm` / `.android`) — applying `kotlin.android` to a `com.android.test`
module (or to `:app` under AGP 9) is a documented hard error (see the module comments + `#26`/ADR-0025).
`:app` applies only `kotlin.compose` + `plugin.parcelize`; the benchmark modules apply only
`com.android.test` (+ baselineprofile on `:baselineprofile`).

The idiomatic `kotlin { jvmToolchain(17) }` DSL is registered by the **Kotlin Gradle plugin's**
top-level `kotlin {}` extension. Whether AGP-9's *built-in* Kotlin registers that same extension is
version-specific and **not safe to assume**. Therefore:

- **Primary attempt:** `kotlin { jvmToolchain(17) }` in each module.
- **Fallback (if the `kotlin {}` DSL does not resolve under AGP-9 built-in Kotlin):** **not** a
  top-level `java { toolchain { … } }` block. That block is the `JavaPluginExtension`, which is
  registered by Gradle's `java`/`java-library`/`java-base` plugin — **none of the three modules applies
  it** (verified: `app/build.gradle.kts:3-13`, `baselineprofile/build.gradle.kts:1-5`,
  `macrobenchmark/build.gradle.kts:1-4`). Android modules configure Java via `android { compileOptions
  { } }` (which all three already have), and AGP does **not** register a `java {}` extension on
  `com.android.*` modules — so an unguarded `java { toolchain }` block fails at configuration time.
  Note also that `kotlin { jvmToolchain { languageVersion.set(…) } }` (the property form) is **not** an
  independent fallback — it is the same `kotlin {}` extension as the primary, so it fails identically if
  `kotlin {}` is unavailable. The genuinely-independent fallback is the KGP compile-task route:
  ```kotlin
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
      compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
  ```
  which exists whenever Kotlin compilation is present (incl. AGP-9 built-in Kotlin). **Caveat:** this
  pins the *bytecode target*, not necessarily the *JDK the compiler runs on* — so if the primary
  `kotlin { jvmToolchain(17) }` is unavailable, the spike MUST confirm the resulting compiler JDK is
  actually 17 (a true toolchain pin), not merely the bytecode target, before treating the fallback as
  equivalent.

The implementation plan's **first task is a spike** that determines whether the primary
`kotlin { jvmToolchain(17) }` DSL resolves, applied identically to all three modules for consistency.
The strong expectation is that it does (AGP-9 built-in Kotlin registers a `kotlin {}` extension); the
KGP-task fallback is the documented contingency only. Whichever mechanism ships — and the corrected
framework reasoning (`java {}` is unavailable on `com.android.*` without the java plugin) — is recorded
in the ADR.

**Keep** the existing `compileOptions { sourceCompatibility/targetCompatibility = 17 }` in every module
— that is the *bytecode target* and is orthogonal to the toolchain (and other tooling/tests read it).
Do **not** replace it with the toolchain.

### 3.3 No auto-download resolver (deliberate)
We do **not** add the `foojay-resolver-convention` plugin to `settings.gradle.kts`. Rationale:
- The repo runs `dependency-verification=strict` + `verification-metadata.xml` (byte-pins every
  artifact). Auto-downloading an **unpinned JDK from a third party** (Adoptium via foojay) is at odds
  with that posture.
- **Local detection only:** Gradle uses a locally-installed JDK 17 if present; if none exists the build
  fails with a **clear toolchain-not-found error naming JDK 17** — which is exactly the improvement the
  finding asks for (opaque KSP error → actionable toolchain error).
- README documents that a local JDK 17 install is required.

### 3.4 Files
- `app/build.gradle.kts` — add the toolchain block (mechanism per §3.2).
- `baselineprofile/build.gradle.kts` — same.
- `macrobenchmark/build.gradle.kts` — same.
- `README.md` — upgrade the bare "JDK 17" line: the build is toolchain-pinned to 17 and requires a
  local JDK 17 (won't silently use an ambient 21/25); no auto-download.
- `docs/agent/DECISIONS/ADR-0039-jdk-toolchain-pin.md` — new (see §6).

### 3.5 Verification
The listed `run-gradle.sh` commands are the **pre-push developer check**; the end-to-end proof is
already automated in CI (last bullet).
- `./run-gradle.sh :app:compileDebugKotlin` — the finding's own verify command.
- `./run-gradle.sh :app:assembleDebug` — full app compile (KSP/Hilt/Room path).
- Type-check both benchmark modules (`:baselineprofile:assembleDebug`-equivalent / recursive assemble)
  — they must still configure and compile under the toolchain.
- Confirm the `#124` fail-closed release license-key guard in `app/build.gradle.kts`
  (`gradle.taskGraph.whenReady`) is **unperturbed** — the toolchain is a compile setting, not a
  task-graph change, so this is a read-only confirmation, not a code change.
- **CI already covers all three modules on every code PR:** `ci.yml` (build-and-test, gated on the
  `code` classifier) runs `assembleRelease` **and** `:baselineprofile:assemble :macrobenchmark:assemble`
  — a `build.gradle.kts` toolchain edit trips that gate, so all three modules compile under the new
  toolchain in CI automatically. (CI runs on a temurin JDK-17 runner, so it verifies JDK-17
  *compilation*, not the *presence* of the pin — see §3.6 drift note.)
- **Non-goal:** no test-count change (build config only).

### 3.6 Risks & mitigations
- *Primary `kotlin { jvmToolchain(17) }` DSL unavailable under built-in Kotlin* → §3.2 KGP-task fallback
  (NOT the unresolvable `java { toolchain }` block). The spike settles which mechanism ships before any
  doc claims a specific one.
- *No JDK 17 on a future machine* → intended failure mode (clear error). Documented in README + ADR.
- *Benchmark modules regress* → they are type-checked in CI already; the plan verifies both locally.
- *Toolchain pin drifts / is deleted from a module* → NOT build-gated. CI runs on a temurin JDK-17
  runner (`ci.yml` `setup-java` 17), so a dropped `jvmToolchain(17)` block leaves every CI check green
  (`assembleRelease` + benchmark assembles verify JDK-17 *compilation*, not pin *presence*) — the
  first-clone protection would silently regress. A `StepCreditAllowlistTest`-style JVM test grepping
  each of the three `build.gradle.kts` for a JVM-17 toolchain block **is** feasible and would close
  this, but is **deliberately deferred**: `devenv-1` is Low-priority DX (a drift ships no broken code
  and breaks no invariant — a fresh clone hits an immediate clear toolchain error), so the cost of a
  config-parsing test outweighs the benefit. This mirrors ADR-0038's deferred detekt nested-lock rule
  (tracked as follow-up #396). Revisit if a module drift is ever observed; note the defer in ADR-0039.

## 4. PR-1 — the three docs/DX findings

### 4.1 #387 `pm-1`: `/checkpoint` emits `docs/agent/BACKLOG.md`

**Goal.** Give an agent a single spine-resident reconciliation of open GitHub issues, instead of
requiring `gh issue list` + cross-referencing ~3 prose locations (the exact drift the V1X roadmap warns
about).

**Skill change.** Add a new **final numbered step (step 6)** to `.claude/skills/checkpoint/SKILL.md`,
immediately after the ADR step (step 5) and before the `## Historical artifacts — NEVER modify`
section, that **regenerates** `docs/agent/BACKLOG.md` from:
```
gh issue list --state open --limit 200 --json number,title,labels
```

**Generated file format** (`docs/agent/BACKLOG.md`):
- A header marking it **GENERATED — do not hand-edit**, naming the exact regen command and stating it
  is refreshed by `/checkpoint`. No hand-maintained prose below the generated table.
- One line per open issue, grouped by a derived bucket where possible (e.g. the `tooling` label's
  phase, or `severity:*`), each line: `- #NNN — <title> — <labels>` with a link to the issue.
- A trailing "last generated: <the skill fills this at run time>" line. The **skill** stamps the date
  at run time (the current date is available to the running agent); the spec/plan MUST NOT bake a
  literal date into the skill text.

**Graceful degradation.** If `gh` is absent or unauthenticated, the step **logs a skip and leaves any
existing `BACKLOG.md` untouched** — it never writes a truncated/empty file. (Checkpoint already assumes
a working tree; `gh` is an additional soft dependency.)

**Seed.** PR-1 commits the **first generated `BACKLOG.md`** (whatever `gh issue list --state open`
returns at generation time — ~30 open at authoring; note PR-1 itself closes #386/#387/#388, so the
committed set is smaller) so the file exists immediately and the skill has a real artifact to keep
fresh. Do **not** treat any literal count as a spec constant — the seed is mechanically regenerated.

**Files:** `.claude/skills/checkpoint/SKILL.md` (add step), new `docs/agent/BACKLOG.md` (seed).

### 4.2 #386 `ai-1`: thin `AGENTS.md` pointer

**Goal.** Portability insurance for a non-Claude agent (zero non-Claude usage today — Low priority, but
cheap). A new `AGENTS.md` at the repo root, **~15 lines**, that **redirects only** — it names where the
authority lives and the hardest invariants **by pointer**, and duplicates **no content**.

**Content shape:**
- One sentence: authority lives in `CLAUDE.md`; the agent contract is `docs/agent/START_HERE.md`;
  current state is `docs/agent/STATE.md`.
- A short "before you touch code" list pointing to: the memory spine, the Adversarial Review Gate, the
  build/test commands (`./run-gradle.sh`, `./lint-kotlin.sh`), and the two or three hardest invariants
  named as pointers (Steps-never-generated-in-game → ADR-0003; battle-engine acyclic lock order →
  ADR-0038; atomic guarded-deduct economy → ADR-0020) — **each a one-line pointer, not a restatement.**
- An explicit "do not duplicate `CLAUDE.md` here — this file only redirects" note so a future edit
  doesn't turn it into a second drift surface.

**Files:** new `AGENTS.md`.

**Non-duplication is a hard requirement** (the finding is explicit). Any prose that restates a
`CLAUDE.md` rule instead of pointing to it is a defect.

### 4.3 #388 `docs-2`: trim `STATE.md` toward one page

**Goal.** `STATE.md` (413 lines) is auto-injected every session by the SessionStart preflight; bloat
burns the context budget each session. Trim it back toward one page.

**What to relocate (it already lives in RUN_LOG/CHANGELOG — this is deletion of duplicated history, not
a move of unique content):**
- The `## Recently shipped (newest first — see RUN_LOG for detail)` per-PR narrative block — the detail is per-PR in `RUN_LOG.md`
  and `CHANGELOG.md`. Replace with a 1–2 line pointer ("recent per-PR history → RUN_LOG / CHANGELOG").
- Any test-count ladder / per-wave counts embedded in the headline and objective prose — those belong
  in CHANGELOG/RUN_LOG. Keep the single current headline count.
- Stale stacked "Previous objective (DONE …)" bullets that duplicate RUN_LOG entries — collapse to the
  current objective + at most one prior line, per the checkpoint rotation rule.

**What to KEEP verbatim (do NOT trim — these are legit live reference):**
- `## Do-not-touch / fragile zones` — the finding explicitly says leave it.
- `## Current objective` (rotated to Phase-2 at checkpoint), `## Top priorities / next actions`,
  `## Known issues / debt`, `## What works (current capabilities)`, `## References`.

**Target.** Meaningfully back toward one page (the finding cites the "one page" rule; #367 got it from
491→403 and #368 pushed to 411). Exact final line count is a judgement call, not a hard number — the
test is "no duplicated per-PR history remains; live reference intact."

**Interaction with checkpoint.** This is best folded into the PR's `/checkpoint` pass (the finding says
so). The Phase-2-objective rotation and the trim happen together at end-of-session.

**Two-PR ordering.** Whichever PR lands second runs its own `/checkpoint`, which normally rotates
`## Current objective` (newest on top). That is expected and correct — but the second PR's checkpoint
MUST keep the section within #388's target of **the current objective + at most one prior line**
(demote, do not re-stack a third level). It must not resurrect the multi-level `Previous objective` /
`Prior objectives` stack this trim collapses. So the two PRs stay commutative: e.g. PR-1 first (trim +
sets Phase-2 CURRENT) → PR-2 second yields CURRENT = JDK-toolchain, one `Previous objective` =
Phase-2-docs — within budget, not a re-bloat.

**Files:** `docs/agent/STATE.md` (trim). RUN_LOG/CHANGELOG already hold the detail — no relocation write
needed unless a specific fact is found only in STATE (none expected; verify during the trim).

## 5. Cross-cutting: docs to sync (PR Task-List Convention)

Per CLAUDE.md's mandatory convention, each PR audits current-state docs and updates only what it
invalidates:
- **PR-1:** `docs/agent/STATE.md` (rotate objective + trim), `docs/agent/RUN_LOG.md` (append),
  `CHANGELOG.md` (add `[Unreleased]` entry — docs/tooling only, note test count **unchanged 1302**).
  New files `AGENTS.md` + `docs/agent/BACKLOG.md`; the checkpoint SKILL change is `.claude/`, not a
  steering doc. `docs/steering/structure.md` gets a one-line note if `AGENTS.md`/`BACKLOG.md` warrant a
  top-level/spine mention.
- **PR-2:** `README.md` (JDK-17 toolchain line), `CHANGELOG.md` (build-config entry), new **ADR-0039**,
  STATE/RUN_LOG. `docs/steering/tech.md` gets the toolchain note if it documents the JDK/build setup.
  CLAUDE.md's Tech-Stack "JVM target 17" line is verified — amend only if it now misstates the setup.

## 6. ADR

- **ADR-0039 — JDK toolchain pin (PR-2).** Records: pin JVM to 17 via a Gradle toolchain on all three
  modules; **local-detection-only, no foojay auto-download** (supply-chain posture rationale); the
  built-in-Kotlin DSL caveat + the corrected framework reasoning (`java {}` is unavailable on
  `com.android.*` without the java plugin → fallback is the KGP compile-task route, not `java {
  toolchain }`) + which mechanism actually shipped, decided by the spike; bytecode `compileOptions`
  retained as orthogonal; the deferred config-drift guard (§3.6) noted alongside the ADR-0038/#396
  precedent.
- **No ADR for PR-1** — docs/skill hygiene, no architectural decision. (If the BACKLOG-generation
  contract proves worth pinning, a one-liner can be added to the checkpoint skill's own header; not an
  ADR.)

## 7. Success criteria

- **#378:** all three modules compile under a JVM-17 toolchain; a too-new ambient JDK now yields a
  clear toolchain error, not opaque KSP failures; README + ADR-0039 accurate; #124 guard unperturbed.
- **#388:** `STATE.md` carries no duplicated per-PR narrative / test-count ladder; fragile-zones + live
  reference intact; meaningfully shorter.
- **#387:** `/checkpoint` regenerates `docs/agent/BACKLOG.md` from `gh issue list`; the seed file lists
  the current open issues; `gh`-absent degrades gracefully.
- **#386:** `AGENTS.md` exists, ~15 lines, pure redirect, zero content duplication.
- Build stays green; **JVM test count unchanged at 1302** (no test added/removed — this is
  tooling/docs, not a code-behaviour change).
- Tracker #389 Phase-2 boxes (#378/#388/#387/#386) ticked; close the tracker only when Phases 3–4 are
  also resolved/deferred.

## 8. Non-goals

- No production-code, schema, economy, or battle-engine change.
- No new JVM/instrumented test. A `build.gradle.kts` config-file drift guard for the toolchain pin is
  **feasible but deliberately deferred** (see §3.6) — `devenv-1` is Low-priority DX; the build itself
  is the verification for #378, and the rest is docs/skill.
- No foojay/auto-download; no Phase-3/4 findings; no content duplication into `AGENTS.md`/`BACKLOG.md`.
