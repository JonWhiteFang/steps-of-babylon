# Staged Repo-Wide ktlint Format — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-fix the mechanical (Bucket A) ktlint violations across all of `app/src` in 6 sequential, layer-scoped PRs, regenerating a shrinking committed baseline at each stage so the CI ktlint gate keeps passing — with zero behaviour change.

**Architecture:** Each stage runs `ktlint -F` over **one bounded subtree only**, proves equivalence (near-empty `git diff -w` + full 1254-test suite green + targeted review of any non-whitespace hunks), regenerates `config/ktlint/baseline.xml` over the **full** `app/src` scope (delete-then-create — ktlint only auto-creates a *missing* baseline), confirms `./lint-kotlin.sh` (check mode) + `:app:detekt` pass, then opens one PR that is monitored and merged before the next stage begins.

**Tech Stack:** ktlint 1.8.0 (PATH binary, version-matched to the repo pin), the repo's `lint-kotlin.sh` runner, `./run-gradle.sh` for Gradle (non-TTY safe), `gh` for the protected-`main` PR flow.

---

## Critical mechanics (verified 2026-06-23 — read before any stage)

These were proven empirically on the unchanged tree this session; the plan depends on them:

1. **`ktlint -F <path>` scopes the fix to `<path>`.** Stages pass a subtree path
   (e.g. `app/src/main/java/com/whitefang/stepsofbabylon/domain`), never all of
   `app/src`. Do **NOT** use `./lint-kotlin.sh --format` — that sweeps all of
   `app/src` baseline-free in one 436-file shot, which is exactly what this plan
   avoids.
2. **Baseline regen = delete-then-create.** ktlint only auto-creates the baseline
   when the file is **missing**. If it exists, ktlint *uses* it for suppression
   and does **not** rewrite it. So regen is:
   `rm config/ktlint/baseline.xml && ktlint --baseline=config/ktlint/baseline.xml app/src`
   over the **full app/src scope** (not the swept subtree — the baseline must
   still cover the not-yet-swept layers).
3. **`ktlint --baseline=<missing-file> app/src` exits 1** while creating the
   baseline (it reports the violations it's recording). This is **expected, not a
   failure** — the regen step must not treat that exit code as an error. The gate
   that matters is the *next* run: `./lint-kotlin.sh` (check mode, baseline now
   present) must exit 0.
4. **Regen is deterministic** — byte-identical output across runs on the same
   tree (verified: 9256 errors / 441 files reproduced exactly).
5. **ktlint binary:** PATH `ktlint version 1.8.0` is present and version-matches
   the pin, so `lint-kotlin.sh`'s `locate_ktlint` resolves it. `.ktlint/ktlint`
   is absent locally (that's the CI download path) — fine.

## Per-stage definition of done

A stage's PR is mergeable when ALL of these hold:

- `git diff -w --stat` against the stage's pre-sweep state is **near-empty**
  (whitespace-only reflow dominates; only token-level ktlint fixes show).
- Every **non-whitespace** hunk is confirmed a known-safe ktlint transform
  (import reorder, trailing comma, signature reflow) with no semantic change.
- `./run-gradle.sh testDebugUnitTest` → **all green** (headline 1254 JVM tests; a
  pure-format change must not move that number).
- `config/ktlint/baseline.xml` regenerated; `./lint-kotlin.sh` (check mode) → exit 0.
- `./run-gradle.sh :app:detekt` → exit 0.
- `RUN_LOG.md` stage entry appended (+ STATE.md updated on Stage 1 and Stage 6 only).
- PR opened, **checks watched to green, squash-merged, branch deleted, `main`
  pulled** — before the next stage starts.

---

## Stage 1 (pilot): `domain/`

Pure Kotlin, best-tested, zero Android — the safest place to validate the whole
pipeline and lock in the exact commands. ~104 files, ~1364 Bucket-A violations.

**Files:**
- Modify (format-only): `app/src/main/java/com/whitefang/stepsofbabylon/domain/**/*.kt`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`
- Modify: `docs/agent/STATE.md` (kickoff note — Stage 1 only)

- [ ] **Step 1: Branch from fresh `main`.**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-1-domain
```

- [ ] **Step 2: Confirm a clean pre-sweep tree.**

Run: `git status --porcelain`
Expected: empty (no output). If not empty, stop — the diff checks below need a clean base.

- [ ] **Step 3: Sweep ONLY `domain/` with ktlint -F.**

```bash
ktlint -F app/src/main/java/com/whitefang/stepsofbabylon/domain
```

Expected: ktlint prints the files it fixed; exit code may be non-zero if any
Bucket-B (non-auto-fixable) violation remains in `domain/` — that's fine, those
stay baselined. The point is the working tree now has the Bucket-A fixes applied
to `domain/` only.

- [ ] **Step 4: Confirm the sweep stayed in scope.**

Run: `git diff --name-only | grep -v '^app/src/main/java/com/whitefang/stepsofbabylon/domain/' || echo "IN SCOPE"`
Expected: `IN SCOPE` (no files outside `domain/` changed). If any other path
appears, stop and investigate.

- [ ] **Step 5: Whitespace-equivalence check.**

```bash
git diff -w --stat | tail -1          # overall churn
git diff -w --stat | wc -l            # number of files with NON-whitespace changes
```

Expected: the `-w` (ignore-whitespace) diff is near-empty — most files show no
change under `-w` because the fixes were pure reflow. A handful may show
non-whitespace hunks (import reorder / trailing comma / signature reflow).

- [ ] **Step 6: Review every non-whitespace hunk.**

```bash
git diff -w > /tmp/ktlint-s1-nonws.diff
wc -l /tmp/ktlint-s1-nonws.diff
```

Read `/tmp/ktlint-s1-nonws.diff`. Confirm EVERY hunk is one of the known-safe
ktlint transforms: import re-ordering (`import-ordering`), added trailing commas
(`trailing-comma-*`), or parameter reflow (`function-signature`). There must be
NO change to a literal, operator, identifier, control-flow, or string. If
anything looks semantic, stop and investigate that file.

> When run under subagent-driven-development, this step is the adversarial-verify
> gate: a fresh reviewer subagent is given `/tmp/ktlint-s1-nonws.diff` and the
> instruction to *refute* "this diff is purely mechanical, no semantic change",
> defaulting to refuted if any hunk is ambiguous.

- [ ] **Step 7: Full test suite — prove behaviour unchanged.**

```bash
./run-gradle.sh testDebugUnitTest > /tmp/s1-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s1-test.log
```

Expected: exit 0, BUILD SUCCESSFUL, 1254 tests (unchanged count). If any test
fails, the format introduced a semantic change — stop, diff the failing file,
revert if needed.

- [ ] **Step 8: Regenerate the baseline (full app/src scope).**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s1-regen.log 2>&1
echo "regen exit=$? (exit 1 is EXPECTED — ktlint reports while creating a missing baseline)"
echo "baseline errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
```

Expected: baseline error count **drops** from 9256 (the `domain/` Bucket-A
entries are gone; only `domain/` Bucket-B + all other layers remain).

- [ ] **Step 9: Verify the check gate passes on the regenerated baseline.**

```bash
./lint-kotlin.sh > /tmp/s1-check.log 2>&1; echo "ktlint check exit=$?"; tail -15 /tmp/s1-check.log
```

Expected: exit 0 (the regenerated baseline suppresses every remaining violation;
nothing reads as NEW).

- [ ] **Step 10: detekt still passes.**

```bash
./run-gradle.sh :app:detekt > /tmp/s1-detekt.log 2>&1; echo "exit=$?"; tail -15 /tmp/s1-detekt.log
```

Expected: exit 0.

- [ ] **Step 11: Doc sync — append RUN_LOG + STATE kickoff.**

Append a RUN_LOG entry (use the file's existing format) recording: Stage 1 of the
staged ktlint format, `domain/` swept, baseline 9256 → N errors, 1254 tests
green, zero behaviour change. Update `docs/agent/STATE.md` CURRENT section to note
the staged-format effort is in flight (Stage 1 of 6). No other current-state doc
changes — test count, architecture, schema, conventions all unchanged.

- [ ] **Step 12: Commit.**

```bash
git add -A
git commit -m "style(ktlint): format domain/ — staged repo-wide format 1/6

Mechanical ktlint -F over domain/ only (Bucket A: wrapping/indent/signature).
Baseline regenerated (9256 -> N). Zero behaviour change; 1254 tests green."
```

- [ ] **Step 13: Push + open PR.**

```bash
git push -u origin chore/ktlint-format-1-domain
gh pr create --base main --head chore/ktlint-format-1-domain \
  --title "style(ktlint): format domain/ — staged repo-wide format 1/6" \
  --body "Stage 1 of 6 of the staged repo-wide ktlint format (spec: docs/superpowers/specs/2026-06-23-ktlint-repo-wide-format-staged-design.md).

Mechanical \`ktlint -F\` over \`domain/\` only. Bucket A (wrapping/indent/signature) auto-fixed; Bucket B (Compose naming, backing props, wildcard imports, long lines) stays baselined. Baseline regenerated. Zero behaviour change — 1254 JVM tests green, detekt + ktlint check pass."
```

- [ ] **Step 14: Monitor checks → merge → return to main.**

```bash
gh pr checks --watch                    # wait for green (use the PR number gh prints if needed)
gh pr merge --squash --delete-branch
git checkout main && git pull --ff-only
```

Expected: all checks green, squash-merge succeeds, local `main` fast-forwards.
**Do not start Stage 2 until this completes.**

---

## Stage 2: `data/`

Bounded, well-tested repos/sensors/health-connect/billing/ads. ~78 files, ~1760 violations.

**Files:**
- Modify (format-only): `app/src/main/java/com/whitefang/stepsofbabylon/data/**/*.kt`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Branch from fresh `main`.**

```bash
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-2-data
git status --porcelain   # expect empty
```

- [ ] **Step 2: Sweep ONLY `data/`.**

```bash
ktlint -F app/src/main/java/com/whitefang/stepsofbabylon/data
git diff --name-only | grep -v '^app/src/main/java/com/whitefang/stepsofbabylon/data/' || echo "IN SCOPE"
```

Expected: `IN SCOPE`.

- [ ] **Step 3: Whitespace-equivalence + non-whitespace review.**

```bash
git diff -w --stat | tail -1
git diff -w > /tmp/ktlint-s2-nonws.diff; wc -l /tmp/ktlint-s2-nonws.diff
```

Read `/tmp/ktlint-s2-nonws.diff`; confirm every hunk is a known-safe transform
(import reorder / trailing comma / signature reflow), no semantic change. (Under
subagent-driven-development, dispatch the refute-it reviewer on this diff.)

- [ ] **Step 4: Full test suite.**

```bash
./run-gradle.sh testDebugUnitTest > /tmp/s2-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s2-test.log
```

Expected: exit 0, 1254 tests.

- [ ] **Step 5: Regenerate baseline (full scope) + verify gate.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s2-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s2-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s2-check.log
./run-gradle.sh :app:detekt > /tmp/s2-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s2-detekt.log
```

Expected: baseline error count drops further; check exit 0; detekt exit 0.

- [ ] **Step 6: Append RUN_LOG entry** (Stage 2, `data/` swept, baseline N → M, 1254 green). No STATE.md change.

- [ ] **Step 7: Commit + push + PR.**

```bash
git add -A
git commit -m "style(ktlint): format data/ — staged repo-wide format 2/6

Mechanical ktlint -F over data/ only. Baseline regenerated. Zero behaviour change; 1254 tests green."
git push -u origin chore/ktlint-format-2-data
gh pr create --base main --head chore/ktlint-format-2-data \
  --title "style(ktlint): format data/ — staged repo-wide format 2/6" \
  --body "Stage 2 of 6. Mechanical \`ktlint -F\` over \`data/\` only. Baseline regenerated. Zero behaviour change — 1254 tests green, detekt + ktlint check pass."
```

- [ ] **Step 8: Monitor → merge → return to main.**

```bash
gh pr checks --watch
gh pr merge --squash --delete-branch
git checkout main && git pull --ff-only
```

**Do not start Stage 3 until this completes.**

---

## Stage 3: `service/` + `di/` + top-level

Small isolated remainder of non-presentation main: `service/` (10), `di/` (8),
and the top-level `StepsOfBabylonApp.kt` (1). ~19 files, ~228 violations + the 9
stray top-level entries.

**Files:**
- Modify (format-only): `app/src/main/java/com/whitefang/stepsofbabylon/service/**/*.kt`, `.../di/**/*.kt`, `.../StepsOfBabylonApp.kt`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Branch from fresh `main`.**

```bash
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-3-service-di
git status --porcelain   # expect empty
```

- [ ] **Step 2: Sweep service/, di/, and the top-level file.**

```bash
B=app/src/main/java/com/whitefang/stepsofbabylon
ktlint -F "$B/service" "$B/di" "$B/StepsOfBabylonApp.kt"
git diff --name-only | grep -vE "^$B/(service|di)/|^$B/StepsOfBabylonApp.kt" || echo "IN SCOPE"
```

Expected: `IN SCOPE`. (If a stray top-level `.kt` other than `StepsOfBabylonApp`
exists and shows up, add it to the `ktlint -F` arg list and re-run — the depth-1
count was 1, so this is unlikely.)

- [ ] **Step 3: Whitespace-equivalence + non-whitespace review.**

```bash
git diff -w --stat | tail -1
git diff -w > /tmp/ktlint-s3-nonws.diff; wc -l /tmp/ktlint-s3-nonws.diff
```

Read it; confirm all hunks are known-safe transforms.

- [ ] **Step 4: Full test suite.**

```bash
./run-gradle.sh testDebugUnitTest > /tmp/s3-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s3-test.log
```

Expected: exit 0, 1254 tests.

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s3-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s3-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s3-check.log
./run-gradle.sh :app:detekt > /tmp/s3-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s3-detekt.log
```

- [ ] **Step 6: Append RUN_LOG entry.**

- [ ] **Step 7: Commit + push + PR.**

```bash
git add -A
git commit -m "style(ktlint): format service/ + di/ — staged repo-wide format 3/6

Mechanical ktlint -F over service/, di/, top-level. Baseline regenerated. Zero behaviour change; 1254 tests green."
git push -u origin chore/ktlint-format-3-service-di
gh pr create --base main --head chore/ktlint-format-3-service-di \
  --title "style(ktlint): format service/ + di/ — staged repo-wide format 3/6" \
  --body "Stage 3 of 6. Mechanical \`ktlint -F\` over \`service/\`, \`di/\`, and top-level. Baseline regenerated. Zero behaviour change — 1254 tests green, detekt + ktlint check pass."
```

- [ ] **Step 8: Monitor → merge → return to main.**

```bash
gh pr checks --watch
gh pr merge --squash --delete-branch
git checkout main && git pull --ff-only
```

**Do not start Stage 4 until this completes.**

---

## Stage 4: `presentation/` (excluding `battle/`)

The largest block: ~64 files, ~3301 violations. Do this BEFORE #34 i18n edits
these screen strings.

**Split contingency:** after Step 2, inspect `git diff --stat | tail -1`. If the
diff is too large to review confidently in one PR, split by screen-group into 4a
and 4b (e.g. 4a = `home/ workshop/ weapons/ labs/ cards/ supplies/`; 4b =
`economy/ missions/ settings/ stats/ store/ help/ onboarding/ navigation/ audio/ ui/`).
Each sub-stage is a full copy of the per-stage protocol with its own branch/PR.
The decision is made at execution time from the actual diff size; default is a
single PR.

**Files:**
- Modify (format-only): everything under `app/src/main/java/com/whitefang/stepsofbabylon/presentation/` EXCEPT `presentation/battle/`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Branch from fresh `main`.**

```bash
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-4-presentation
git status --porcelain   # expect empty
```

- [ ] **Step 2: Sweep presentation EXCEPT battle.**

ktlint has no exclude flag here, so pass every non-battle child directory
explicitly. List the current non-battle children first, then sweep them:

```bash
B=app/src/main/java/com/whitefang/stepsofbabylon/presentation
fd -t d --max-depth 1 . "$B" | grep -v '/battle$'    # inspect the dir list
# Sweep every non-battle subdir + any top-level presentation/*.kt files:
ktlint -F $(fd -t d --max-depth 1 . "$B" | grep -v '/battle$') $(fd -e kt --max-depth 1 . "$B")
git diff --name-only | grep '/presentation/battle/' && echo "ERROR: battle touched — STOP" || echo "BATTLE UNTOUCHED"
git diff --name-only | grep -v '^app/src/main/java/com/whitefang/stepsofbabylon/presentation/' && echo "ERROR: out of scope — STOP" || echo "IN SCOPE"
git diff --stat | tail -1    # size check for split decision
```

Expected: `BATTLE UNTOUCHED` and `IN SCOPE`. Decide single-PR vs 4a/4b split here.

- [ ] **Step 3: Whitespace-equivalence + non-whitespace review.**

```bash
git diff -w --stat | tail -1
git diff -w > /tmp/ktlint-s4-nonws.diff; wc -l /tmp/ktlint-s4-nonws.diff
```

Read it; confirm all hunks are known-safe transforms. (Compose `@Composable`
PascalCase function names are Bucket B — ktlint won't touch them, so they should
NOT appear as renames in this diff. If a function name changed, STOP — that would
be a semantic break.)

- [ ] **Step 4: Full test suite** (this stage moves the most Compose-screen code;
  the #253 Compose UI tests run on the JVM lane and will catch a broken screen).

```bash
./run-gradle.sh testDebugUnitTest > /tmp/s4-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s4-test.log
```

Expected: exit 0, 1254 tests.

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s4-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s4-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s4-check.log
./run-gradle.sh :app:detekt > /tmp/s4-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s4-detekt.log
```

- [ ] **Step 6: Append RUN_LOG entry.**

- [ ] **Step 7: Commit + push + PR.**

```bash
git add -A
git commit -m "style(ktlint): format presentation/ (excl battle) — staged repo-wide format 4/6

Mechanical ktlint -F over presentation screens (battle excluded). Baseline regenerated. Zero behaviour change; 1254 tests green."
git push -u origin chore/ktlint-format-4-presentation
gh pr create --base main --head chore/ktlint-format-4-presentation \
  --title "style(ktlint): format presentation/ (excl battle) — staged repo-wide format 4/6" \
  --body "Stage 4 of 6. Mechanical \`ktlint -F\` over \`presentation/\` excluding \`battle/\`. Baseline regenerated. Zero behaviour change — 1254 tests green (incl. #253 Compose UI tests), detekt + ktlint check pass."
```

- [ ] **Step 8: Monitor → merge → return to main.**

```bash
gh pr checks --watch
gh pr merge --squash --delete-branch
git checkout main && git pull --ff-only
```

**Do not start Stage 5 until this completes.** (If split into 4a/4b, complete
BOTH — each monitored and merged — before Stage 5.)

---

## Stage 5: `presentation/battle/` (FRAGILE ZONE)

~40 files, ~2037 violations. This is the custom SurfaceView game loop with
thread-safety invariants (`entitiesLock`/`effectsLock`, `GameLoopThread`
try/catch). Formatting is purely textual and does not touch lock structure or
control flow — but treat this PR with extra care and sequence it BEFORE the #233
Simulation-hoist starts.

**Files:**
- Modify (format-only): `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/**/*.kt`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Branch from fresh `main`.**

```bash
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-5-battle
git status --porcelain   # expect empty
```

- [ ] **Step 2: Sweep ONLY `presentation/battle/`.**

```bash
ktlint -F app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle
git diff --name-only | grep -v '^app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/' || echo "IN SCOPE"
```

Expected: `IN SCOPE`.

- [ ] **Step 3: Whitespace-equivalence + EXTRA-careful non-whitespace review.**

```bash
git diff -w --stat | tail -1
git diff -w > /tmp/ktlint-s5-nonws.diff; wc -l /tmp/ktlint-s5-nonws.diff
```

Read `/tmp/ktlint-s5-nonws.diff` line by line. Beyond the usual transform check,
confirm specifically: NO `synchronized`/lock block boundary moved semantically,
NO `@Volatile`/field modifier changed, NO statement reordered across a lock
acquire/release. Reflow inside a `synchronized(entitiesLock) { ... }` body is
fine; moving a statement INTO or OUT of one is not (and ktlint never does that —
this is a paranoia check for the fragile zone). (Under subagent-driven-dev,
dispatch the refute-it reviewer with these battle-specific invariants in the
prompt.)

- [ ] **Step 4: Full test suite + the battle pure-domain core.**

```bash
./run-gradle.sh testDebugUnitTest > /tmp/s5-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s5-test.log
```

Expected: exit 0, 1254 tests (includes `SimulationTest`, the extracted
pure-domain game-loop core, plus the engine/collaborator tests).

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s5-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s5-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s5-check.log
./run-gradle.sh :app:detekt > /tmp/s5-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s5-detekt.log
```

- [ ] **Step 6: Append RUN_LOG entry** (note the fragile-zone care taken).

- [ ] **Step 7: Commit + push + PR.**

```bash
git add -A
git commit -m "style(ktlint): format presentation/battle/ — staged repo-wide format 5/6

Mechanical ktlint -F over the battle renderer (fragile zone). Reviewed for lock/control-flow integrity. Baseline regenerated. Zero behaviour change; 1254 tests green."
git push -u origin chore/ktlint-format-5-battle
gh pr create --base main --head chore/ktlint-format-5-battle \
  --title "style(ktlint): format presentation/battle/ — staged repo-wide format 5/6" \
  --body "Stage 5 of 6 (FRAGILE ZONE — battle renderer). Mechanical \`ktlint -F\` over \`presentation/battle/\` only; diff reviewed for lock/control-flow integrity. Baseline regenerated. Zero behaviour change — 1254 tests green (incl. SimulationTest), detekt + ktlint check pass."
```

- [ ] **Step 8: Monitor → merge → return to main.**

```bash
gh pr checks --watch
gh pr merge --squash --delete-branch
git checkout main && git pull --ff-only
```

**Do not start Stage 6 until this completes.**

---

## Stage 6: `src/test/` + `src/androidTest/`

Highest file count (~207), lowest risk: ~557 violations in tests. The test code
itself is the safety net, so equivalence here is verified by the tests still
compiling and passing.

**Files:**
- Modify (format-only): `app/src/test/**/*.kt`, `app/src/androidTest/**/*.kt`
- Modify (regenerated): `config/ktlint/baseline.xml` (should reach its minimum — only Bucket B left)
- Modify (append): `docs/agent/RUN_LOG.md`
- Modify: `docs/agent/STATE.md` (completion note — Stage 6)

- [ ] **Step 1: Branch from fresh `main`.**

```bash
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-6-tests
git status --porcelain   # expect empty
```

- [ ] **Step 2: Sweep both test source roots.**

```bash
ktlint -F app/src/test app/src/androidTest
git diff --name-only | grep -vE '^app/src/(test|androidTest)/' || echo "IN SCOPE"
```

Expected: `IN SCOPE`.

- [ ] **Step 3: Whitespace-equivalence + non-whitespace review.**

```bash
git diff -w --stat | tail -1
git diff -w > /tmp/ktlint-s6-nonws.diff; wc -l /tmp/ktlint-s6-nonws.diff
```

Read it; confirm all hunks are known-safe transforms.

- [ ] **Step 4: Full test suite — tests must still compile + pass after reformat.**

```bash
./run-gradle.sh testDebugUnitTest > /tmp/s6-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s6-test.log
```

Expected: exit 0, 1254 tests. (A reformat that broke a test's structure would
fail to compile here — this is the safety net for the test-code sweep.)

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s6-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
echo "remaining rules in baseline:"; grep -oE 'source="standard:[a-z-]+"' config/ktlint/baseline.xml | sort | uniq -c | sort -rn
./lint-kotlin.sh > /tmp/s6-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s6-check.log
./run-gradle.sh :app:detekt > /tmp/s6-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s6-detekt.log
```

Expected: baseline reaches its minimum — only Bucket B rules remain
(`function-naming`, `backing-property-naming`, `no-wildcard-imports`,
`max-line-length`, `kdoc`, `filename`, `property-naming`). NO mechanical
wrapping/indent/signature rules should remain. If any Bucket-A rule is still
present, a layer was missed — investigate before merging.

- [ ] **Step 6: Doc sync — append RUN_LOG + STATE completion.**

Append the final RUN_LOG entry (Stage 6, tests swept, baseline at minimum N
errors / only Bucket B, 1254 green). Update `docs/agent/STATE.md` to mark the
staged repo-wide ktlint format COMPLETE (all 6 stages merged), recording the
final baseline size and that only intentional/non-auto-fixable rules remain. This
is the right place to also note (if desired) a future follow-up: empty the
baseline by addressing Bucket B (out of scope here).

- [ ] **Step 7: Commit + push + PR.**

```bash
git add -A
git commit -m "style(ktlint): format test sources — staged repo-wide format 6/6

Mechanical ktlint -F over src/test + src/androidTest. Baseline at minimum (Bucket B only). Zero behaviour change; 1254 tests green."
git push -u origin chore/ktlint-format-6-tests
gh pr create --base main --head chore/ktlint-format-6-tests \
  --title "style(ktlint): format test sources — staged repo-wide format 6/6" \
  --body "Stage 6 of 6 (final). Mechanical \`ktlint -F\` over \`src/test\` + \`src/androidTest\`. Baseline regenerated to its minimum — only Bucket B (Compose naming, backing props, wildcard imports, long lines) remains. Zero behaviour change — 1254 tests green, detekt + ktlint check pass."
```

- [ ] **Step 8: Monitor → merge → return to main.**

```bash
gh pr checks --watch
gh pr merge --squash --delete-branch
git checkout main && git pull --ff-only
```

Expected: all 6 stages merged; `git diff` future feature PRs no longer carry
reformat noise. Done.

---

## Notes & guardrails

- **Never use `./lint-kotlin.sh --format`** in any stage — it sweeps all of
  `app/src` baseline-free. Always `ktlint -F <scoped-path>`.
- **Baseline regen is always full-scope** (`ktlint --baseline=... app/src`), even
  though the sweep is scoped — the baseline must keep covering not-yet-swept
  layers.
- **`ktlint --baseline=<missing> app/src` exiting 1 is expected** (creating the
  baseline); the gate is the subsequent `./lint-kotlin.sh` exit 0.
- **If `testDebugUnitTest` ever fails after a sweep:** a formatting fix exposed or
  caused a semantic issue. Stop, identify the file from the failure, inspect its
  `git diff -w` hunk, and revert that file's sweep (`git checkout -- <file>`) — do
  NOT push a stage with a failing test.
- **Concurrent feature work:** sequence Stage 4 before #34 (i18n) and Stage 5
  before #233 (Simulation-hoist) to avoid conflicts in those subtrees.
- **Protected `main`:** every stage uses the branch → PR → checks → squash-merge
  flow; direct pushes to `main` are rejected.
```
