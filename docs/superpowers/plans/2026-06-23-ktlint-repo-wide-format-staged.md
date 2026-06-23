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
- Every **non-whitespace** hunk is confirmed a **known-safe ktlint transform**
  (see the allowlist in "Known-safe ktlint transforms" below) with no semantic
  change.
- `./run-gradle.sh testDebugUnitTest --rerun-tasks` → **all green, 0 failures /
  0 errors**, and the report-derived total equals the headline JVM count
  (currently 1254). See "Verifying the test result" below — do NOT scrape the
  count from stdout (Gradle prints none).
- `config/ktlint/baseline.xml` regenerated; `./lint-kotlin.sh` (check mode) → exit 0.
- `./run-gradle.sh :app:detekt` → exit 0.
- `RUN_LOG.md` stage entry appended, `docs/agent/STATE.md` updated (CURRENT
  section: which stage of 6 + baseline error count), and a `## [Unreleased]`
  `CHANGELOG.md` entry added — **every stage** (per CLAUDE.md PR Task-List
  Convention; matches the #311/#256/#253 infra/test-only precedent).
- PR opened, **checks watched to green** (both required checks — `build-and-test`
  AND the `connected` emulator suite, ~8-15 min), **squash-merged, branch
  deleted, `main` pulled** — before the next stage starts.

## Known-safe ktlint transforms (the review allowlist)

When reviewing the non-whitespace hunks of a sweep, EVERY hunk must be one of
these — all are mechanical and **semantics-preserving** (verified present in the
baseline; ktlint 1.8.0 applies them under `-F`):

- **`import-ordering`** — re-orders import lines.
- **`trailing-comma-on-call-site` / `trailing-comma-on-declaration-site`** — adds a
  trailing comma to a multi-line argument/parameter list.
- **`function-signature` / `class-signature`** — reflows a function/class header
  (params onto separate lines), no parameter renamed or reordered.
- **`function-expression-body`** — converts a single-expression block body to an
  expression body: drops `return` + the function braces, adds `=`. **Removing
  `return` here is NOT a control-flow change.** (Present in `domain/`,
  `presentation/battle/engine/UWController.kt:103`, and several test files.)
- **`if-else-bracing` / `when-entry-bracing`** — inserts (or removes) `{ }` around
  a single-statement `if`/`else` branch or `when` arm. Added braces around an
  **unchanged** branch are NOT a control-flow change.
- **`if-else-wrapping` / `multiline-if-else`** — wraps an `if`/`else` onto multiple
  lines.

**STOP the stage only if** a hunk changes a literal, an operator, an identifier's
value/name, a string, OR alters the actual logic — a statement added / removed /
reordered, a condition changed, or a branch added / dropped. Added/removed braces
around an unchanged single branch and block↔expression-body conversions are
benign ktlint normalizations, not semantic changes — do not flag or revert them.

## Verifying the test result (read once, applies to every stage)

The project's Gradle config has **no `testLogging`** block, so
`testDebugUnitTest` prints **no "N tests" line** to stdout, and Gradle's
incremental cache will skip unchanged test tasks (a scoped sweep otherwise re-runs
only the touched layer). Therefore, every stage:

1. Runs with `--rerun-tasks` so the full suite actually executes (not a cached subset).
2. Confirms `exit=0` and that the tail shows `BUILD SUCCESSFUL` with no failures.
3. Derives the real total + failure count from the JUnit result XML, e.g.:

```bash
python3 - <<'PY'
import glob, re
tot = fail = err = 0
for f in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    s = open(f).read()
    tot  += int(re.search(r'tests="(\d+)"', s).group(1))
    fail += int(re.search(r'failures="(\d+)"', s).group(1))
    err  += int(re.search(r'errors="(\d+)"', s).group(1))
print(f"total={tot} failures={fail} errors={err}")
PY
```

Expected: `failures=0 errors=0` and `total` == the headline JVM count (currently
1254). A pure-format change must not move the total.

---

## Stage 1 (pilot): `domain/`

Pure Kotlin, best-tested, zero Android — the safest place to validate the whole
pipeline and lock in the exact commands. ~104 files, ~1364 Bucket-A violations.

**Files:**
- Modify (format-only): `app/src/main/java/com/whitefang/stepsofbabylon/domain/**/*.kt`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`
- Modify: `docs/agent/STATE.md` (CURRENT section — every stage)
- Modify: `CHANGELOG.md` (`## [Unreleased]` entry — every stage)

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

Read `/tmp/ktlint-s1-nonws.diff`. Confirm EVERY hunk is one of the **known-safe
ktlint transforms** listed in "Known-safe ktlint transforms" above (import
reorder, trailing comma, signature reflow, block↔expression-body, brace
insert/remove, if/else wrapping). STOP only on a change to a literal, operator,
identifier value/name, or string, or a change to the actual logic (statement
added/removed/reordered, condition changed, branch added/dropped). `domain/`
includes a `function-expression-body` fix (`GenerateSupplyDrop.kt:66` —
block→expression body, drops `return`); that is expected and benign, not a
control-flow change.

> When run under subagent-driven-development, this step is the adversarial-verify
> gate: a fresh reviewer subagent is given `/tmp/ktlint-s1-nonws.diff` and the
> instruction to *refute* "this diff is purely mechanical, no semantic change",
> defaulting to refuted if any hunk is ambiguous.

- [ ] **Step 7: Full test suite — prove behaviour unchanged.**

```bash
./run-gradle.sh testDebugUnitTest --rerun-tasks > /tmp/s1-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s1-test.log
# Then derive total + failures from the report XML (stdout prints no count) — see "Verifying the test result".
```

Expected: exit 0, BUILD SUCCESSFUL; the report XML shows `failures=0 errors=0`
and `total` == the headline JVM count (currently 1254, unchanged). If any test
fails, the format introduced a semantic change — stop, diff the failing file,
revert it (`git checkout -- <file>`).

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

- [ ] **Step 11: Doc sync — RUN_LOG + STATE + CHANGELOG (per CLAUDE.md convention).**

Per the CLAUDE.md PR Task-List Convention (every code-changing PR), in order:
1. Append a `RUN_LOG.md` entry (use the file's existing format): Stage 1 of the
   staged ktlint format, `domain/` swept, baseline 9256 → N errors, 1254 tests
   green, zero behaviour change.
2. Update `docs/agent/STATE.md` CURRENT section: staged-format effort in flight
   (Stage 1 of 6), baseline at N errors.
3. Add a `## [Unreleased]` `CHANGELOG.md` entry, e.g. "### Style — ktlint
   repo-wide format, stage 1/6 (`domain/`)" noting no production-logic change;
   baseline 9256 → N; 1254 JVM green. (Matches the #311/#256/#253 precedent that
   infra/build/test-only PRs still get a CHANGELOG section.)

No other current-state doc changes — architecture, schema, conventions, the
headline test count all unchanged. `master-plan.md` is untouched (ktlint is not a
master-plan entry).

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
- Modify: `docs/agent/STATE.md` (CURRENT section) + `CHANGELOG.md` (`## [Unreleased]`)

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
./run-gradle.sh testDebugUnitTest --rerun-tasks > /tmp/s2-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s2-test.log
# Then derive total + failures from the report XML — see "Verifying the test result".
```

Expected: exit 0; report XML `failures=0 errors=0`, `total` == 1254 (unchanged).

- [ ] **Step 5: Regenerate baseline (full scope) + verify gate.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s2-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s2-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s2-check.log
./run-gradle.sh :app:detekt > /tmp/s2-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s2-detekt.log
```

Expected: baseline error count drops further; check exit 0; detekt exit 0.

- [ ] **Step 6: Doc sync — RUN_LOG + STATE + CHANGELOG.** Append a `RUN_LOG.md`
  entry (Stage 2, `data/` swept, baseline N → M, 1254 green); update
  `docs/agent/STATE.md` CURRENT (Stage 2 of 6, baseline at M); add a `##
  [Unreleased]` `CHANGELOG.md` entry ("### Style — ktlint repo-wide format, stage
  2/6 (`data/`)"). Per CLAUDE.md PR Task-List Convention.

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
- Modify: `docs/agent/STATE.md` (CURRENT section) + `CHANGELOG.md` (`## [Unreleased]`)

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
./run-gradle.sh testDebugUnitTest --rerun-tasks > /tmp/s3-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s3-test.log
# Then derive total + failures from the report XML — see "Verifying the test result".
```

Expected: exit 0; report XML `failures=0 errors=0`, `total` == 1254 (unchanged).

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s3-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s3-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s3-check.log
./run-gradle.sh :app:detekt > /tmp/s3-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s3-detekt.log
```

- [ ] **Step 6: Doc sync — RUN_LOG + STATE + CHANGELOG.** Append `RUN_LOG.md`
  (Stage 3, `service/`+`di/`+top-level swept, baseline N → M, 1254 green); update
  `docs/agent/STATE.md` CURRENT (Stage 3 of 6, baseline at M); add a `##
  [Unreleased]` `CHANGELOG.md` entry ("### Style — ktlint repo-wide format, stage
  3/6 (`service/`+`di/`)"). Per CLAUDE.md PR Task-List Convention.

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
and 4b: 4a = `home/ workshop/ weapons/ labs/ cards/ supplies/`; 4b = `economy/
missions/ settings/ stats/ store/ help/ onboarding/ navigation/ audio/ ui/` **+ the
two top-level `presentation/*.kt` files (`MainActivity.kt`,
`HealthConnectPermissionActivity.kt` — together 125 baseline errors)**. The
top-level files MUST land in exactly one sub-stage — the single-PR command sweeps
them via `$(fd -e kt --max-depth 1 . "$B")`, so a split that lists only subdirs
would silently drop them (a Bucket-A leak Stage 6's audit catches two stages
later). Each sub-stage is a full copy of the per-stage protocol — including the
Step-2 `BATTLE UNTOUCHED` / `IN SCOPE` guards — with its own branch/PR. The
decision is made at execution time from the actual diff size; default is a single
PR.

**Files:**
- Modify (format-only): everything under `app/src/main/java/com/whitefang/stepsofbabylon/presentation/` EXCEPT `presentation/battle/`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`
- Modify: `docs/agent/STATE.md` (CURRENT section) + `CHANGELOG.md` (`## [Unreleased]`)

- [ ] **Step 1: Branch from fresh `main`.**

```bash
git checkout main && git pull --ff-only
git switch -c chore/ktlint-format-4-presentation
git status --porcelain   # expect empty
```

- [ ] **Step 2: Sweep presentation EXCEPT battle.**

ktlint has no exclude flag, so pass every non-battle child directory explicitly.
Use `fd`'s native `-E battle` exclude — **NOT** `grep -v '/battle$'`: `fd` emits
directory paths with a **trailing slash** (`.../presentation/battle/`), so a `$`
anchor after `battle` never matches and battle would leak into the sweep
(reformatting the fragile zone reserved for Stage 5). Assert the exclusion
**before** running `ktlint -F`, so a leak is caught before any mutation:

```bash
B=app/src/main/java/com/whitefang/stepsofbabylon/presentation
DIRS=$(fd -t d --max-depth 1 . "$B" -E battle)        # 16 non-battle subdirs
echo "$DIRS"                                          # inspect the dir list
echo "$DIRS" | grep -q '/battle/' && { echo "ERROR: battle in sweep list — STOP"; } || echo "BATTLE EXCLUDED"
test "$(echo "$DIRS" | wc -l | tr -d ' ')" -eq 16 || echo "ERROR: expected 16 non-battle dirs — STOP"
# Sweep every non-battle subdir + the 2 top-level presentation/*.kt files:
ktlint -F $DIRS $(fd -e kt --max-depth 1 . "$B")
git diff --name-only | grep '/presentation/battle/' && echo "ERROR: battle touched — STOP" || echo "BATTLE UNTOUCHED"
git diff --name-only | grep -v '^app/src/main/java/com/whitefang/stepsofbabylon/presentation/' && echo "ERROR: out of scope — STOP" || echo "IN SCOPE"
git diff --stat | tail -1    # size check for split decision
```

Expected: `BATTLE EXCLUDED` (pre-sweep), then `BATTLE UNTOUCHED` and `IN SCOPE`
(post-sweep). Decide single-PR vs 4a/4b split here.

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
./run-gradle.sh testDebugUnitTest --rerun-tasks > /tmp/s4-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s4-test.log
# Then derive total + failures from the report XML — see "Verifying the test result".
```

Expected: exit 0; report XML `failures=0 errors=0`, `total` == 1254 (unchanged).

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s4-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s4-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s4-check.log
./run-gradle.sh :app:detekt > /tmp/s4-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s4-detekt.log
```

- [ ] **Step 6: Doc sync — RUN_LOG + STATE + CHANGELOG.** Append `RUN_LOG.md`
  (Stage 4, `presentation/` excl battle swept, baseline N → M, 1254 green; note if
  split into 4a/4b); update `docs/agent/STATE.md` CURRENT (Stage 4 of 6, baseline
  at M); add a `## [Unreleased]` `CHANGELOG.md` entry ("### Style — ktlint
  repo-wide format, stage 4/6 (`presentation/` excl battle)"). Per CLAUDE.md PR
  Task-List Convention.

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
control flow — but treat this PR with extra care and sequence it BEFORE the
deferred **ADR-0012 clean Simulation-hoist** lands (the larger battle refactor
still open; issue #233's portrait-lock part already merged in PR #274). The goal
is to reformat the battle subtree while it is quiet, ahead of that next big battle
change.

**Files:**
- Modify (format-only): `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/**/*.kt`
- Modify (regenerated): `config/ktlint/baseline.xml`
- Modify (append): `docs/agent/RUN_LOG.md`
- Modify: `docs/agent/STATE.md` (CURRENT section) + `CHANGELOG.md` (`## [Unreleased]`)

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

Read `/tmp/ktlint-s5-nonws.diff` line by line. The usual allowlist applies (see
"Known-safe ktlint transforms"). **Expected in this sweep:** `UWController.kt:103`
(`relayerBaseStats`) is rewritten block→expression body — the `return` is dropped
and a `=` added (`function-expression-body`). That is benign and is test-guarded
by `UWControllerTest`'s "relayerBaseStats returns the base unchanged when GOLDEN
is inactive" + the GOLDEN activation/expiry test; the #119 KDoc's "preserve the
exact pre-decomposition shape" concerns NOT adding a lock, not block-vs-expression
body. Do not flag it. `ZigguratEntity.kt` / `BattleViewModel.kt` may gain
single-statement braces (`if-else-bracing`/`when-entry-bracing`) — also benign.

Beyond the allowlist, confirm the fragile-zone invariants specifically: NO
`synchronized`/lock block boundary moved semantically, NO `@Volatile`/field
modifier changed, NO statement reordered across a lock acquire/release. Reflow
inside a `synchronized(entitiesLock) { ... }` body is fine; moving a statement
INTO or OUT of one is not (ktlint never does that — this is a paranoia check for
the fragile zone). The named invariants were ground-verified: `GameEngine` 7×
`synchronized(entitiesLock)`, `EffectEngine` `effectsLock`, `GameLoopThread` 4×
`@Volatile` + per-tick try/catch, `GameSurfaceView` `pendingSpeed`/`pendingPaused`.
(Under subagent-driven-dev, dispatch the refute-it reviewer with these
battle-specific invariants in the prompt.)

- [ ] **Step 4: Full test suite + the battle pure-domain core.**

```bash
./run-gradle.sh testDebugUnitTest --rerun-tasks > /tmp/s5-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s5-test.log
# Then derive total + failures from the report XML — see "Verifying the test result".
```

Expected: exit 0; report XML `failures=0 errors=0`, `total` == 1254 (unchanged) —
includes `SimulationTest` (the extracted pure-domain game-loop core) plus the
engine/collaborator tests.

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s5-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
./lint-kotlin.sh > /tmp/s5-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s5-check.log
./run-gradle.sh :app:detekt > /tmp/s5-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s5-detekt.log
```

- [ ] **Step 6: Doc sync — RUN_LOG + STATE + CHANGELOG.** Append `RUN_LOG.md`
  (Stage 5, `presentation/battle/` swept, note the fragile-zone care taken,
  baseline N → M, 1254 green); update `docs/agent/STATE.md` CURRENT (Stage 5 of 6,
  baseline at M); add a `## [Unreleased]` `CHANGELOG.md` entry ("### Style —
  ktlint repo-wide format, stage 5/6 (`presentation/battle/`)"). Per CLAUDE.md PR
  Task-List Convention.

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
- Modify (regenerated): `config/ktlint/baseline.xml` (should reach its minimum — Bucket B + small non-autocorrectable residue)
- Modify (append): `docs/agent/RUN_LOG.md`
- Modify: `docs/agent/STATE.md` (completion note — effort COMPLETE) + `CHANGELOG.md` (`## [Unreleased]`)

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

- [ ] **Step 4: Full JVM suite — JVM tests must compile + pass after reformat.**

```bash
./run-gradle.sh testDebugUnitTest --rerun-tasks > /tmp/s6-test.log 2>&1; echo "exit=$?"; tail -25 /tmp/s6-test.log
# Then derive total + failures from the report XML — see "Verifying the test result".
```

Expected: exit 0; report XML `failures=0 errors=0`, `total` == 1254 (unchanged).
This compiles + runs the `src/test` sweep, but **NOT** `src/androidTest` —
`testDebugUnitTest` only builds the JVM unit-test source set. The 4 reformatted
androidTest files (`BattleSurfaceLifecycleTest`, `DeepLinkIntentTest`,
`HiltTestRunner`, `InfrastructureSmokeTest`) are compiled in the next step.

- [ ] **Step 4b: Compile-check the reformatted androidTest source set.**

```bash
./run-gradle.sh compileDebugAndroidTestKotlin > /tmp/s6-androidtest-compile.log 2>&1; echo "exit=$?"; tail -20 /tmp/s6-androidtest-compile.log
```

Expected: exit 0 — the reformatted androidTest files compile locally (no emulator
needed). Their full execution is the blocking `connected` CI lane
(`:app:connectedDebugAndroidTest`), which Step 8's `gh pr checks --watch` gates
the merge on. If this fails, a reformat broke an androidTest file — revert it
(`git checkout -- <file>`).

- [ ] **Step 5: Regenerate baseline + verify gates.**

```bash
rm config/ktlint/baseline.xml
ktlint --baseline=config/ktlint/baseline.xml app/src > /tmp/s6-regen.log 2>&1
echo "regen exit=$? (1 expected); errors now: $(grep -c '<error ' config/ktlint/baseline.xml)"
echo "remaining rules in baseline:"; grep -oE 'source="standard:[a-z-]+"' config/ktlint/baseline.xml | sort | uniq -c | sort -rn
./lint-kotlin.sh > /tmp/s6-check.log 2>&1; echo "check exit=$?"; tail -10 /tmp/s6-check.log
./run-gradle.sh :app:detekt > /tmp/s6-detekt.log 2>&1; echo "detekt exit=$?"; tail -10 /tmp/s6-detekt.log
```

Expected: baseline at its minimum — dominated by Bucket B (`function-naming`,
`backing-property-naming`, `no-wildcard-imports`, `max-line-length`, `kdoc`,
`filename`, `property-naming`). A **small** number of wrapping-family entries
(e.g. `multiline-expression-wrapping`, `binary-expression-wrapping`, `wrapping`,
or `max-line-length`-coupled cases) MAY legitimately remain — `ktlint -F`
corrects *most*, not all, violations; some are not autocorrectable (printed to
stderr during the sweep, not a missed layer).

Do NOT treat "any wrapping/indent rule present" as "a layer was missed." Use a
true **idempotence** check instead: re-run `ktlint -F` on an already-swept scope
and confirm zero further diff (a fully-fixed scope is stable; genuine residue
can't be shrunk):

```bash
ktlint -F app/src/test >/dev/null 2>&1   # already swept this stage
git diff --quiet && echo "IDEMPOTENT — residue is genuinely non-autocorrectable" || { echo "ERROR: -F still changes a swept scope — a layer was missed"; git diff --stat | tail -1; }
git checkout -- app/src 2>/dev/null   # discard the no-op re-run if it touched anything
```

Expected: `IDEMPOTENT`. If instead `-F` still mutates an already-swept scope, a
layer was genuinely missed — investigate before merging.

- [ ] **Step 6: Doc sync — RUN_LOG + STATE + CHANGELOG completion.**

Per the CLAUDE.md PR Task-List Convention, in order:
1. Append the final `RUN_LOG.md` entry (Stage 6, `src/test`+`src/androidTest`
   swept, baseline at minimum N errors / dominated by Bucket B, 1254 green).
2. Update `docs/agent/STATE.md` to mark the staged repo-wide ktlint format
   COMPLETE (all 6 stages merged), recording the final baseline size and that only
   intentional/non-auto-fixable rules (+ a small non-autocorrectable wrapping
   residue) remain. Note the future follow-up: empty the baseline by addressing
   Bucket B (out of scope here).
3. Add the final `## [Unreleased]` `CHANGELOG.md` entry ("### Style — ktlint
   repo-wide format, stage 6/6 (test sources) — effort complete; baseline 9256 →
   N, Bucket-B-only floor").

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
  before the deferred #233 clean Simulation-hoist (ADR-0012) — the larger battle
  refactor still open — to avoid conflicts in those subtrees.
- **Protected `main`:** every stage uses the branch → PR → checks → squash-merge
  flow; direct pushes to `main` are rejected.
- **CI cost per stage:** `gh pr checks --watch` waits on BOTH required checks —
  the fast `build-and-test` (JVM lint+unit+assemble) AND `connected`, the full
  `:app:connectedDebugAndroidTest` emulator suite (~8-12 min, required on every PR
  to `main`). The docs-only fast path does NOT apply (each stage changes `.kt`
  files + the baseline). Budget ~10-15 min CI wait per stage. If `connected` fails
  for an emulator-flake reason (boot timeout, AVD cache miss) unrelated to the
  format, re-run it (`gh run rerun --failed`) — the format diff cannot affect
  instrumented behaviour.
- **`app/src/release/` is not a scope gap:** it is a 4th source root holding only
  a generated `baseline-prof.txt` (0 Kotlin files). The by-layer partition (main:
  domain/data/service/di/presentation + `StepsOfBabylonApp.kt`; test/androidTest)
  covers all 512 `.kt` files; the full-scope baseline regen no-ops on `release/`.
```
