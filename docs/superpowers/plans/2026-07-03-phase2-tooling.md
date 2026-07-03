# Phase-2 Tooling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** Ship the four Phase-2 "developer-experience" findings of tracker #389 — pin the JDK toolchain
(#378), trim `STATE.md` (#388), have `/checkpoint` emit a mechanical `BACKLOG.md` (#387), and add a thin
`AGENTS.md` pointer (#386) — as two PRs split by build-impact.

**Architecture:** PR-1 (branch `docs/phase2-tooling-dx`, already created + carries the gate-passed spec)
is docs/skill only, no build impact: new `AGENTS.md`, new seed `docs/agent/BACKLOG.md`, a new checkpoint
step, and the `STATE.md` trim. PR-2 (a separate branch off `main`) is the build-config change: a JVM-17
Gradle toolchain on `:app` + both benchmark modules, plus README + ADR-0039. The two PRs are independent
**except** that `docs/agent/BACKLOG.md` + the checkpoint step-6 change are introduced by **PR-1 only**;
PR-2 refreshes `BACKLOG.md` solely if PR-1 has already merged (otherwise it leaves that file to PR-1 —
Task 12 Step 4). Ordering is otherwise free; PR-1 is lower-risk and expected first (see the two-PR
ordering note).

**Tech Stack:** Kotlin/Gradle 9.6 (Kotlin DSL, AGP 9 built-in Kotlin, version catalog), `gh` CLI,
markdown docs, the `.claude/skills/checkpoint` skill.

**Spec:** `docs/superpowers/specs/2026-07-03-phase2-tooling-design.md` (gate-passed 2026-07-03: 9 raised /
9 surviving / 0 refuted; amendments committed).

**Source of scope:** tracker #389 (Phase-2 boxes: #378/#388/#387/#386) + `docs/reviews/tooling-gap-assessment.md`.

---

## Ground truth (verified at HEAD, post-#397 / v1.0.12 / vc 28)

- `:app` applies `kotlin.compose` + `plugin.parcelize` (so the `kotlin {}` extension **is** registered);
  the two benchmark modules apply only `com.android.test` (+ baselineprofile on `:baselineprofile`) — no
  Kotlin plugin at all. **None** applies the Gradle `java`/`java-base` plugin. (`app/build.gradle.kts:3-13`,
  `baselineprofile/build.gradle.kts:1-5`, `macrobenchmark/build.gradle.kts:1-4`.)
- All three modules have `compileOptions { sourceCompatibility = targetCompatibility = JavaVersion.VERSION_17 }`
  (`app/build.gradle.kts:206-209`, `baselineprofile/…:17-20`, `macrobenchmark/…:16-19`). **Keep it** — it is
  the bytecode target, orthogonal to the toolchain.
- No foojay resolver in `settings.gradle.kts`. The local machine has JDK 17 and 25 installed.
- `#124` fail-closed license-key guard is at `app/build.gradle.kts:170-198` (`gradle.taskGraph.whenReady`);
  the toolchain is a compile setting, not a task-graph change — do not touch this block.
- CI (`.github/workflows/ci.yml`) runs on temurin JDK-17 (`setup-java`, line 86) and, on every code PR,
  runs `assembleRelease` (line 118-120) + `:baselineprofile:assemble :macrobenchmark:assemble` (line 130-132).
- Checkpoint skill (`.claude/skills/checkpoint/SKILL.md`) has numbered steps 1–5; step 4 = RUN_LOG append
  (line 44), step 5 = ADR (line 49); the `## Historical artifacts — NEVER modify` section is at line 53.
- `docs/agent/STATE.md` is 413 lines. Section headers (from `grep -n '^## '`): `## Current objective` (42),
  `## Recently shipped (newest first — see RUN_LOG for detail)` (72), `## What works (current capabilities)`
  (110), `## Known issues / debt` (126), `## Top priorities / next actions` (163), `## Do-not-touch / fragile
  zones` (189), `## References` (402).
- Latest ADR is `ADR-0038`; **ADR-0039** is the next free number.
- README: `- JDK 17` at line 29 (under `## Prerequisites`).
- The true current test count is **1302 JVM** (ladder …→1294→1301→1302; canonical in `CLAUDE.md:360`).
  **STATE.md's `**Headline:**` block is STALE at `1294 JVM` (line 22)** — PR-C's 1301→1302 landed in
  CHANGELOG/CLAUDE.md but never propagated to the STATE headline. Task 5 Step 2 corrects it to 1302 as
  part of the trim. This plan itself adds/removes no test.

---

## Order of work

PR-1 first (lower risk, and the spec already lives on its branch), then PR-2. They are independent, but
doing PR-1 first means PR-2's end-of-session `/checkpoint` demotes the PR-1 objective to a single
`Previous objective` line (within #388's budget — see Task 12).

---

# PR-1 — Docs & DX (branch `docs/phase2-tooling-dx`)

> This branch already exists and carries the gate-passed spec, its amendments, and this plan. All PR-1
> tasks commit onto it. **Confirm you are on it before starting:** `git branch --show-current` → `docs/phase2-tooling-dx`.

## Task 1 — #386: add the thin `AGENTS.md` pointer

**Files:**
- Create: `AGENTS.md` (repo root)

- [ ] **Step 1: Create `AGENTS.md`** with exactly this content (pure redirect, ~18 lines, zero rule
  duplication — every invariant is a POINTER, not a restatement):

```markdown
# AGENTS.md

This repo's agent guidance is **not** duplicated here. This file is a redirect so a non-Claude agent
can find the authority.

- **Start here:** [`docs/agent/START_HERE.md`](docs/agent/START_HERE.md) — the agent contract
  (constraints + build commands + where the memory spine lives).
- **Full operating guide:** [`CLAUDE.md`](CLAUDE.md) — protocol, architecture, conventions, domain model.
- **Live state:** [`docs/agent/STATE.md`](docs/agent/STATE.md) — current objective, priorities, fragile zones.

## Before you touch code

1. Read the memory spine (`docs/agent/START_HERE.md` → `STATE.md` → `CONSTRAINTS.md`) — never rely on chat history.
2. Every spec and plan passes the **Adversarial Review Gate** (see `CLAUDE.md`) before the next stage.
3. Build/test via the wrappers, not bare Gradle: `./run-gradle.sh <task>` (JVM tests:
   `./run-gradle.sh testDebugUnitTest`) and `./lint-kotlin.sh` (formatting).
4. The three hardest invariants (details live at the pointers — **do not restate them here**):
   Steps are never generated in-game bar one bounded exception → ADR-0003; the battle engine's acyclic
   lock order → ADR-0038; currency moves via the atomic guarded-deduct pattern → ADR-0020.

**Do not duplicate `CLAUDE.md` into this file — it only redirects.**
```

- [ ] **Step 2: Verify it is a pure redirect** (no duplicated rule bodies; every invariant is a pointer):

Run: `grep -c '\[\[' AGENTS.md; wc -l AGENTS.md`
Expected: `0` wiki-links (this file uses markdown links, not `[[…]]`); ~20 lines. Eyeball that no line
*explains* an invariant — each only names it + points to the ADR.

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md
git commit -m "docs(#386): add thin AGENTS.md redirect pointer to CLAUDE.md/START_HERE.md"
```

## Task 2 — #387: add the BACKLOG-generation step to the checkpoint skill

**Files:**
- Modify: `.claude/skills/checkpoint/SKILL.md` (insert a new step 6 after step 5, before `## Historical artifacts`)

- [ ] **Step 1: Read the insertion region** to confirm the anchor is unchanged:

Run: `sed -n '49,54p' .claude/skills/checkpoint/SKILL.md`
Expected: step 5 (`### 5. Add/update an ADR …`) followed by a blank line and `## Historical artifacts — NEVER modify`.

- [ ] **Step 2: Insert the new step 6** immediately before the `## Historical artifacts — NEVER modify`
  line. The new content (note: the skill fills the date at run time — **do not** bake a literal date):

```markdown
### 6. Regenerate `docs/agent/BACKLOG.md`
Mechanically refresh the open-issue backlog snapshot so the spine — not chat — is the agent's source of
truth. This is a generated file; do not hand-edit its body.

Run:
```bash
gh issue list --state open --limit 200 --json number,title,labels \
  --jq '.[] | "- #\(.number) — \(.title) — [\([.labels[].name] | join(", "))]"'
```
Write the result into `docs/agent/BACKLOG.md` under a **GENERATED — do not hand-edit** header that names
this exact command and a "last generated: <today's date>" line (stamp the current date at run time).
Group the lines by phase/label where it is obvious (e.g. the `tooling` phases, `severity:*`), otherwise
list newest-issue-number first.

**Graceful degradation:** if `gh` is absent or unauthenticated (the command errors or returns nothing),
**log a one-line skip and leave any existing `docs/agent/BACKLOG.md` untouched** — never write a
truncated or empty file.
```

- [ ] **Step 3: Verify the step landed in the right slot:**

Run: `grep -n '^### \|^## Historical' .claude/skills/checkpoint/SKILL.md`
Expected: `### 6. Regenerate` appears after `### 5.` and before `## Historical artifacts — NEVER modify`.

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/checkpoint/SKILL.md
git commit -m "docs(#387): /checkpoint regenerates docs/agent/BACKLOG.md from gh issue list"
```

## Task 3 — #387: generate and commit the seed `BACKLOG.md`

**Files:**
- Create: `docs/agent/BACKLOG.md`

- [ ] **Step 1: Generate the issue lines** with the exact command the skill bakes:

Run:
```bash
gh issue list --state open --limit 200 --json number,title,labels \
  --jq '.[] | "- #\(.number) — \(.title) — [\([.labels[].name] | join(", "))]"'
```
Expected: ~30 lines (the current open set; the count is whatever `gh` returns — do not hard-code it).
If `gh` errors, STOP and report — the seed cannot be generated without it.

- [ ] **Step 2: Write `docs/agent/BACKLOG.md`** with a generated-file header + the Step-1 output. Header
  template (stamp the real current date in the "last generated" line):

```markdown
# Open-Issue Backlog (GENERATED — do not hand-edit)

> Mechanical snapshot of open GitHub issues, regenerated by the `/checkpoint` skill (step 6). The spine,
> not chat, is an agent's source of truth. Do not hand-edit the body — re-run the command to refresh.
>
> Regen command:
> ```
> gh issue list --state open --limit 200 --json number,title,labels \
>   --jq '.[] | "- #\(.number) — \(.title) — [\([.labels[].name] | join(", "))]"'
> ```
> Last generated: <YYYY-MM-DD at commit time>

<the Step-1 output — grouped by phase/label where obvious, else newest-number-first>
```

- [ ] **Step 3: Verify the file is well-formed and non-empty:**

Run: `head -20 docs/agent/BACKLOG.md; echo '---'; grep -c '^- #' docs/agent/BACKLOG.md`
Expected: the header renders; the `- #NNN` line count matches the Step-1 line count (~30).

- [ ] **Step 4: Commit**

```bash
git add docs/agent/BACKLOG.md
git commit -m "docs(#387): seed generated docs/agent/BACKLOG.md (open-issue snapshot)"
```

## Task 4 — #388: trim `STATE.md` (the `## Recently shipped` narrative block)

**Files:**
- Modify: `docs/agent/STATE.md` — replace the body of `## Recently shipped (newest first — see RUN_LOG for detail)`
  (starts line 72) up to the next section `## What works (current capabilities)` (line 110) with a short pointer.

> The detail in that block already lives per-PR in `RUN_LOG.md` and `CHANGELOG.md` — this is deletion of
> duplicated history, not a move of unique content. **Verify that before deleting** (Step 1).

- [ ] **Step 1: Confirm the narrative is duplicated in RUN_LOG/CHANGELOG** (so nothing unique is lost):

Run: `sed -n '72,109p' docs/agent/STATE.md`
Then spot-check that the block's underlying PRs are recorded downstream by a STABLE token (a PR number —
immune to prose casing/wording drift; do NOT grep the STATE-only dated headline text, which by definition
lives only in the block being deleted). Note the canonical CHANGELOG is the repo-root `CHANGELOG.md`
(there is no `docs/CHANGELOG.md`):
`grep -n '#252\|#298\|#299\|#300' docs/agent/RUN_LOG.md CHANGELOG.md`
Expected: these per-PR entries appear in BOTH files (e.g. `#252` — RUN_LOG has ~7 hits, CHANGELOG ~3;
`#298/#299/#300` in RUN_LOG's test-integrity/architecture wave) — confirming the `## Recently shipped`
block only SUMMARIZES history that is recorded per-PR downstream, so deleting it loses no unique fact. If
some fact is genuinely found ONLY in STATE, preserve it in the pointer (none expected).

- [ ] **Step 2: Replace the section body** — keep the `## Recently shipped (newest first — see RUN_LOG for
  detail)` header, delete its stacked bullet narrative, and substitute this pointer:

```markdown
## Recently shipped (newest first — see RUN_LOG for detail)

Per-PR history lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md` (per-PR) — not
duplicated here (per the one-page rule). For the current objective and what's in-flight, see
`## Current objective` above.
```

- [ ] **Step 3: Verify the section shrank and the KEEP sections are intact:**

Run: `grep -n '^## ' docs/agent/STATE.md; wc -l docs/agent/STATE.md`
Expected: `## Do-not-touch / fragile zones`, `## What works (current capabilities)`, `## Known issues /
debt`, `## Top priorities / next actions`, `## References` all still present; total line count materially
lower than 413.

- [ ] **Step 4: Commit**

```bash
git add docs/agent/STATE.md
git commit -m "docs(#388): trim STATE.md — replace duplicated per-PR narrative with a RUN_LOG/CHANGELOG pointer"
```

## Task 5 — #388: trim the test-count ladder / stale stacked objectives from the headline + objective prose

**Files:**
- Modify: `docs/agent/STATE.md` — the `**Headline:**` block (top) and the stacked `Previous/Prior objective`
  bullets under `## Current objective`.

- [ ] **Step 1: Locate the headline count + stacked-objective bullets:**

Run: `grep -n 'JVM + 9 instrumented\|1294\|Previous objective\|Prior objectives' docs/agent/STATE.md`
Expected: the `**Headline:**` count clause (currently `**1294 JVM + 9 instrumented tests**` at line 22 —
STALE, see Step 2) + the multi-level `Previous objective (DONE…)` / `Prior objectives (all DONE)` bullets.
(Note: the older per-wave deltas like `1167`/`1152`/`+15 JVM` live inside the `## Recently shipped` block
that Task 4 already deleted, so they no longer appear here — don't grep for them.)

- [ ] **Step 2: CORRECT the stale headline count, then drop the ladder.** The `**Headline:**` block's
  count clause is **currently STALE** — it reads `**1294 JVM + 9 instrumented tests**` at STATE.md:22, but
  PR-C's 1301→1302 never propagated here (canonical `1302 JVM` is in `CLAUDE.md:360`). **Change that
  clause in place `1294` → `1302`** so it reads `**1302 JVM + 9 instrumented tests**`. Keep the
  schema/version facts and the release-status prose. Remove the per-wave count-ladder mentions that live
  in the objective prose (e.g. `1294→1301` at line 50, and the `1294 JVM` at line 66 — the latter is
  removed anyway by Step 3's `Prior objectives` deletion) — that history is in CHANGELOG. Do NOT delete
  the headline count itself while stripping the ladder.

- [ ] **Step 3: Collapse the stacked objectives.** Under `## Current objective`, keep the CURRENT bullet
  and AT MOST ONE `Previous objective` line; delete the deeper `Prior objectives (all DONE)` stack (it
  duplicates RUN_LOG). (The CURRENT bullet is rewritten to the Phase-2 objective at checkpoint, Task 6 —
  here just remove the deep stack.)

- [ ] **Step 4: Verify:**

Run: `grep -c 'Previous objective\|Prior objectives' docs/agent/STATE.md; grep -c '1302 JVM + 9 instrumented' docs/agent/STATE.md; grep -c '1294 JVM' docs/agent/STATE.md`
Expected: at most one `Previous objective`, zero `Prior objectives`; exactly `1` match for `1302 JVM + 9
instrumented` (the corrected headline); `0` for `1294 JVM` (the old headline clause is now 1302 and the
line-66 ladder mention is gone with the `Prior objectives` stack).

- [ ] **Step 5: Commit**

```bash
git add docs/agent/STATE.md
git commit -m "docs(#388): trim STATE.md — drop test-count ladder + deep objective stack (detail in CHANGELOG/RUN_LOG)"
```

## Task 6 — PR-1 doc sync + checkpoint (CLAUDE.md PR Task-List Convention)

> Per CLAUDE.md, the doc sync (this task) runs immediately before the commit step. #388's trim IS part of
> the STATE update, so this task folds them together.

**Files:**
- Modify: `CHANGELOG.md` (add an `[Unreleased]` entry), `docs/agent/STATE.md` (rotate objective + priorities),
  `docs/agent/RUN_LOG.md` (append), and `docs/steering/structure.md` (one-line note for the two new top-level/spine files).

- [ ] **Step 1: Add a `CHANGELOG.md` `[Unreleased]` entry** noting the three PR-1 findings (docs/tooling
  only; **test count unchanged at 1302**; no schema/economy/engine change): new `AGENTS.md` (#386), new
  generated `docs/agent/BACKLOG.md` + checkpoint step (#387), `STATE.md` trim (#388).

- [ ] **Step 2: Rotate `## Current objective` in `STATE.md`** to the Phase-2 tooling objective (PR-1
  landed: #386/#387/#388; PR-2 = #378 next), demoting the prior objective to a single `Previous objective`
  line. Update `## Top priorities / next actions` if the Phase-2 items shifted. Add `docs/agent/BACKLOG.md`
  + `AGENTS.md` to `## References` if warranted.

- [ ] **Step 3: Append a `RUN_LOG.md` entry** (newest-first) covering PR-1: goal, what changed (the 3
  findings), verification (doc integrity + BACKLOG generation), doc-sync list, what remains (PR-2 = #378).
  Never edit prior entries.

- [ ] **Step 4: Add a one-line note to `docs/steering/structure.md`** for the two new spine/root files
  (`AGENTS.md` redirect; `docs/agent/BACKLOG.md` generated snapshot). Skip if structure.md doesn't index
  top-level docs — check first (`grep -n 'AGENTS\|top-level\|BACKLOG' docs/steering/structure.md`).

- [ ] **Step 5: Regenerate `docs/agent/BACKLOG.md`** per the new checkpoint step 6 (this is the skill's
  own step exercising itself; the open set will differ slightly after this session).

- [ ] **Step 6: Verify doc integrity** (no broken links introduced, nothing left half-written):

Run: `grep -rn ']((\|](docs/\|](CLAUDE' AGENTS.md docs/agent/BACKLOG.md | head; wc -l docs/agent/STATE.md`
Expected: links well-formed; STATE.md shorter than 413.

- [ ] **Step 7: Commit**

```bash
git add CHANGELOG.md docs/agent/STATE.md docs/agent/RUN_LOG.md docs/steering/structure.md docs/agent/BACKLOG.md
git commit -m "docs(#386,#387,#388): PR-1 doc sync — CHANGELOG + STATE rotate/trim + RUN_LOG + BACKLOG refresh"
```

## Task 7 — open PR-1

- [ ] **Step 1: Push and open the PR:**

```bash
git push -u origin docs/phase2-tooling-dx
gh pr create --title "Phase-2 tooling (docs/DX): AGENTS.md + BACKLOG.md + STATE trim (#386/#387/#388)" \
  --body "$(cat <<'EOF'
Phase-2 tracker #389 (docs/DX slice). No build/schema/economy/engine change; JVM test count unchanged at 1302.

- #386 (ai-1): thin AGENTS.md redirect pointer (zero content duplication).
- #387 (pm-1): /checkpoint step 6 regenerates docs/agent/BACKLOG.md from `gh issue list`; seed committed.
- #388 (docs-2): STATE.md trimmed — duplicated per-PR narrative + test-count ladder relocated to RUN_LOG/CHANGELOG; fragile-zones + live reference kept.

Spec (gate-passed 2026-07-03, 9/9/0): docs/superpowers/specs/2026-07-03-phase2-tooling-design.md
EOF
)"
```

- [ ] **Step 2: Report the PR URL to the developer.** PR-1 does not merge itself — the developer merges.

---

# PR-2 — JDK toolchain (#378) — a NEW branch off `main`

> Do PR-2 on its own branch so it can land independently of PR-1. Start from an up-to-date `main`.

## Task 8 — spike: confirm the toolchain DSL on `:app`

**Files:**
- Modify (spike): `app/build.gradle.kts` — add a `kotlin { jvmToolchain(17) }` block.

- [ ] **Step 1: Create the branch off main:**

```bash
git checkout main && git pull --ff-only
git checkout -b build/phase2-jvm-toolchain-378
```

- [ ] **Step 2: Add the toolchain block to `:app`.** Insert a top-level `kotlin { }` block (`:app` applies
  `kotlin.compose`, so the `kotlin {}` extension is registered). Place it right after the `android { … }`
  block closes. Add:

```kotlin
kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 3: Verify the DSL resolves and compiles:**

Run: `./run-gradle.sh :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` — the primary `kotlin { jvmToolchain(17) }` is expected to resolve (AGP-9's
built-in Kotlin registers the `kotlin {}` extension; empirically confirmed even on the plugin-less
benchmark modules), so this spike should confirm the primary path and the fallback should NOT fire. Only
if it fails with an unresolved-reference on `kotlin`/`jvmToolchain`, fall back to the KGP compile-task
route from spec §3.2, using the **fully-qualified** names so no imports are needed:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
```

Re-run, then confirm the compiler JDK is 17 (a true toolchain pin), not merely the bytecode target.
Record which mechanism worked for the ADR.

- [ ] **Step 4: Do NOT commit yet** — the benchmark modules (Task 9) must use the identical mechanism;
  commit all three together in Task 10 after they all compile.

## Task 9 — apply the same toolchain to both benchmark modules

**Files:**
- Modify: `baselineprofile/build.gradle.kts`, `macrobenchmark/build.gradle.kts`

- [ ] **Step 1: Add the identical block to each benchmark module.** The benchmark modules apply NO Kotlin
  plugin (only `com.android.test`) — but AGP-9's built-in Kotlin still registers the `kotlin {}` extension
  (empirically confirmed), so the primary form is expected to work here too. Add the **same** mechanism
  Task 8 settled on. If Task 8 used `kotlin { jvmToolchain(17) }`, add that; if it fell back to the
  KGP-task route, add the same fully-qualified
  `tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }` block (no imports
  needed). Place it after each `android { … }` block. For the `kotlin {}` form:

```kotlin
kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 2: Verify both benchmark modules configure and compile under the toolchain:**

Run: `./run-gradle.sh :baselineprofile:assemble :macrobenchmark:assemble`
Expected: `BUILD SUCCESSFUL`. If the `kotlin {}` extension is unresolved on a benchmark module (no Kotlin
plugin), use the KGP-task fallback there too (keeping all three modules consistent) and re-run.

- [ ] **Step 3: Do NOT commit yet** — commit all three module edits together in Task 10.

## Task 10 — verify the full build + the #124 guard, then commit the toolchain

**Files:** (none new — verification + commit of Tasks 8–9)

- [ ] **Step 1: Full app compile** (KSP/Hilt/Room path under the toolchain):

Run: `./run-gradle.sh :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm the #124 fail-closed license-key guard is unperturbed** (read-only — the
  toolchain is a compile setting, not a task-graph change, so this block is untouched).

  IMPORTANT: do **not** run a bare `./run-gradle.sh assembleRelease` locally to "prove" it — the guard's
  regex `^(bundle|assemble|package).*Release$` **matches** `assembleRelease`, so with a blank
  `play.licenseKey` it correctly throws (`app/build.gradle.kts:190-196`). CI passes it only because it
  seeds a throwaway `play.licenseKey` first (`ci.yml:114-120`). A local blank-key failure would be the
  guard working as designed, NOT a toolchain regression — so this command would mislead.

  Instead, confirm two things by inspection + a keyed build:
  - `git diff app/build.gradle.kts` shows the ONLY change is the added `kotlin { }` block — the
    `gradle.taskGraph.whenReady` guard (lines ~170-197) is byte-identical.
  - Optionally reproduce CI's keyed path to exercise R8 under the new toolchain:
    ```bash
    echo 'play.licenseKey=local-nonpublishing-placeholder' >> local.properties
    ./run-gradle.sh assembleRelease
    # then remove that line again (local.properties is gitignored; keep it clean)
    ```
    Expected: `BUILD SUCCESSFUL` (unsigned — no keystore in a dev clone). If it fails on the license key
    even WITH the placeholder line, STOP — the toolchain edit perturbed the guard. Skip this optional
    sub-step if `local.properties` already carries a real key you don't want to touch; CI covers the R8
    path regardless.

- [ ] **Step 3: Run the JVM test suite to confirm no regression** (count must stay 1302):

Run: `./run-gradle.sh testDebugUnitTest 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`, tests green.

- [ ] **Step 4: Commit all three module edits together:**

```bash
git add app/build.gradle.kts baselineprofile/build.gradle.kts macrobenchmark/build.gradle.kts
git commit -m "build(#378): pin JVM-17 toolchain on :app + both benchmark modules

Local-detection-only (no foojay). Bytecode compileOptions retained (orthogonal). Mechanism: <kotlin { jvmToolchain(17) } | KGP-task fallback> — decided by the Task-8 spike."
```

## Task 11 — #378 docs: README + ADR-0039

**Files:**
- Modify: `README.md:29` (the `- JDK 17` prerequisite line)
- Create: `docs/agent/DECISIONS/ADR-0039-jdk-toolchain-pin.md`
- Modify: `docs/steering/tech.md` (only if it documents the JDK/build setup) + `CLAUDE.md` (verify the
  "JVM target 17" line — amend only if it now misstates the setup)

- [ ] **Step 1: Upgrade the README prerequisite line.** Replace `- JDK 17` (line 29) with:

```markdown
- JDK 17 — the build pins a Gradle **JVM toolchain** to 17 (local-detection only, no auto-download), so
  a locally-installed JDK 17 is required. A too-new ambient JDK (21/25) will not be silently used; the
  build fails with a clear toolchain error instead of an opaque KSP failure.
```

- [ ] **Step 2: Create `ADR-0039`** from the template (`docs/agent/DECISIONS/ADR-0001-template.md`).
  Content — Decision: pin JVM to 17 via a Gradle toolchain on all three modules. Context: bytecode-only
  targets meant the local JDK was unpinned; a too-new ambient JDK failed opaquely on first clone (#378 /
  `devenv-1`). Decision detail: **local-detection-only, no foojay auto-download** (preserves the strict
  `verification-metadata.xml` supply-chain posture — an unpinned third-party JDK download would contradict
  it); the shipped mechanism is `<kotlin { jvmToolchain(17) } | KGP-task fallback>` (record which — the
  Task-8 spike decided); the `java { toolchain }` block is NOT usable here because `com.android.*` modules
  apply no `java`/`java-base` plugin (no `JavaPluginExtension`); the bytecode `compileOptions` are retained
  as orthogonal. Consequences: clear first-clone error instead of opaque KSP failure; a fresh machine now
  requires JDK 17 installed. Note the **deferred config-drift guard** (a `StepCreditAllowlistTest`-style
  build.gradle.kts scan is feasible but deferred at `devenv-1`'s Low priority — mirrors ADR-0038's
  deferred detekt rule / follow-up #396).

- [ ] **Step 3: Add a toolchain note to `docs/steering/tech.md`; leave `CLAUDE.md`.** `tech.md` IS the
  Tech-Stack doc and documents the build setup, so append a one-line toolchain note to its
  `- **Build:** Gradle 9.6.0 …` line (the JDK/build-setup line — around `tech.md:8`), e.g.
  `(build pins a JVM-17 Gradle toolchain, local-detection only — #378/ADR-0039)`. Do **NOT** modify the
  `- **Language:** Kotlin (JVM target 17)` line — that is a bytecode-target statement, orthogonal to the
  toolchain. `CLAUDE.md`'s "Kotlin (JVM target 17)" is likewise a bytecode-target statement and stays
  accurate — do not amend it (it does not describe toolchain/JDK-selection). Do not touch the headline
  test count (unchanged).

- [ ] **Step 4: Commit** (stage `tech.md` since Step 3 edited it):

```bash
git add README.md docs/agent/DECISIONS/ADR-0039-jdk-toolchain-pin.md docs/steering/tech.md
git status --porcelain   # confirm no unstaged doc edit remains before committing
git commit -m "docs(#378): README + tech.md JDK-17 toolchain note + ADR-0039 (local-detection, no foojay)"
```

## Task 12 — PR-2 doc sync + checkpoint

**Files:**
- Modify: `CHANGELOG.md` (`[Unreleased]` build-config entry), `docs/agent/STATE.md` (rotate objective +
  trim per the two-PR rule), `docs/agent/RUN_LOG.md` (append), `docs/agent/BACKLOG.md`
  (regenerate — **only if PR-1 has already merged**; see Step 4)

- [ ] **Step 1: Add a `CHANGELOG.md` `[Unreleased]` entry** for #378 (build-config: JVM-17 toolchain on
  all three modules; local-detection, no foojay; ADR-0039). Note **test count unchanged at 1302**.

- [ ] **Step 2: Rotate `## Current objective` in `STATE.md`** to reflect #378 done. Per the two-PR rule,
  keep CURRENT + AT MOST ONE `Previous objective` line (if PR-1 landed first, its objective becomes that
  single prior line) — **do not** re-stack a deeper `Prior objectives` list (that would undo Task 5's trim).

- [ ] **Step 3: Append a `RUN_LOG.md` entry** for PR-2 (goal, what changed, verification: compile across
  all three modules + assembleRelease guard-intact + 1302 green, ADR-0039, doc-sync list).

- [ ] **Step 4: Regenerate `docs/agent/BACKLOG.md` — CONDITIONALLY.** `docs/agent/BACKLOG.md` AND the
  checkpoint step-6 skill change are introduced by **PR-1 only**; PR-2 branches off plain `main` (Task 8
  Step 1), so on the default PR-1-still-unmerged path they do not exist on this base. Check first:

Run: `git ls-files docs/agent/BACKLOG.md; grep -c '^### 6\.' .claude/skills/checkpoint/SKILL.md`
  - **If both are present** (PR-1 already merged to main): follow checkpoint step 6 to regenerate
    `docs/agent/BACKLOG.md` (open set shifts as #378 closes), and include it in the Step-6 commit.
  - **If NOT** (PR-1 unmerged — the default): **SKIP this step entirely.** Do NOT hand-create
    `BACKLOG.md` here — PR-1 is its sole introducer; creating a second copy causes an add/add merge
    conflict when both PRs land. Leave it to PR-1.

- [ ] **Step 5: Verify STATE.md stayed trim:**

Run: `grep -c 'Prior objectives' docs/agent/STATE.md; wc -l docs/agent/STATE.md`
Expected: `0` `Prior objectives`; line count still materially below 413.

- [ ] **Step 6: Commit** (add `docs/agent/BACKLOG.md` ONLY if Step 4 actually regenerated it):

```bash
git add CHANGELOG.md docs/agent/STATE.md docs/agent/RUN_LOG.md
# add docs/agent/BACKLOG.md ONLY if PR-1 already merged and Step 4 regenerated it:
# git add docs/agent/BACKLOG.md
git commit -m "docs(#378): PR-2 doc sync — CHANGELOG + STATE rotate + RUN_LOG (+ BACKLOG if PR-1 merged)"
```

## Task 13 — open PR-2

- [ ] **Step 1: Push and open the PR:**

```bash
git push -u origin build/phase2-jvm-toolchain-378
gh pr create --title "Phase-2 tooling (build): pin JVM-17 Gradle toolchain (#378)" \
  --body "$(cat <<'EOF'
Phase-2 tracker #389 (build-config slice). Pins a JVM-17 Gradle toolchain on :app + both benchmark modules.

- Local-detection only (no foojay auto-download) — preserves the strict verification-metadata.xml supply-chain posture.
- Bytecode compileOptions retained (orthogonal to the toolchain).
- A too-new ambient JDK now yields a clear toolchain error, not an opaque KSP failure (the #378 gap).
- ADR-0039 records the decision + the deferred config-drift guard (ADR-0038/#396 precedent).
- No schema/economy/engine change; JVM test count unchanged at 1302. #124 license-key guard confirmed unperturbed.

Spec (gate-passed 2026-07-03, 9/9/0): docs/superpowers/specs/2026-07-03-phase2-tooling-design.md
EOF
)"
```

- [ ] **Step 2: Report the PR URL to the developer.** PR-2 does not merge itself.

## Task 14 — close out tracker #389 Phase-2

- [ ] **Step 1: After both PRs merge**, tick the Phase-2 boxes on tracker #389 (#378/#388/#387/#386) and
  comment that Phase-2 is complete; **do not close #389** (Phases 3–4 remain: #373/#375/#382/#381/#384/#396,
  #379/#383/#385/#377).

Run: `gh issue comment 389 --body "Phase 2 (developer experience) complete: #378 (jvmToolchain, ADR-0039), #388 (STATE trim), #387 (/checkpoint→BACKLOG.md), #386 (AGENTS.md) all merged. Tracker stays open for Phases 3–4."`

---

## Self-review notes (author)

- **Spec coverage:** #378 → Tasks 8–11; #388 → Tasks 4–5 (+ rotation in 6/12); #387 → Tasks 2–3 (+ self-exercise
  in 6/12); #386 → Task 1. Two-PR ordering/commutativity → Task 12 Step 2. Deferred drift-guard → Task 11 ADR.
  CI cross-module coverage is a fact the plan verifies locally (Tasks 9–10) and CI re-checks on the PR.
- **No baked date/count:** BACKLOG date + count are run-time (Tasks 3/6/12); no literal date in the skill.
- **Mechanism consistency:** the `kotlin { jvmToolchain(17) }` vs KGP-task decision is made once (Task 8)
  and applied identically to all three modules (Task 9) + recorded in ADR-0039 (Task 11).
- **Fragile zones:** #124 guard read-only-confirmed (Task 10 Step 2), not modified; STATE fragile-zones
  section kept verbatim (Task 4 Step 3 verify).
