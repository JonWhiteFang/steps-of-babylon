# ktlint Repo-Wide Format — Staged Design

## Goal

Auto-fix the **mechanical** ktlint violations across all of `app/src` (the
wrapping/indent/signature noise that ktlint can `-F` autocorrect), regenerating
a steadily-shrinking baseline that retains **only** the violations ktlint cannot
auto-fix. The end state: a clean, low-churn formatting floor where future diffs
aren't polluted by reformatting, and the CI ktlint gate (`./lint-kotlin.sh`)
keeps passing at every step.

This is a **zero-behaviour-change** effort: no production logic, no test logic,
no public API shape changes. Equivalence is proven mechanically (whitespace diff
+ full test suite green) at every stage.

## Background — the two buckets

The current `config/ktlint/baseline.xml` holds **9,256 violations across 441
files / 44 rules**. They split cleanly:

- **Bucket A — auto-fixable mechanical formatting (~95%).** `-F` rewrites these
  and they vanish from the baseline. Top contributors: `argument-list-wrapping`
  (2034), `function-signature` (1545), `multiline-expression-wrapping` (1128),
  `statement-wrapping` (760), `indent` (673), `chain-method-continuation` (396),
  `class-signature` (237), `wrapping` (217), plus the long tail of blank-line /
  spacing rules.
- **Bucket B — NOT auto-fixable, several intentional (~445).** ktlint literally
  cannot `-F` these, so they survive untouched and stay in the baseline:
  - `function-naming` (54) — Compose `@Composable fun HomeScreen()` PascalCase is
    idiomatic and intended.
  - `backing-property-naming` (22) — the ViewModel `_state` / `state` StateFlow
    pattern.
  - `no-wildcard-imports` (61), `max-line-length` (301), `kdoc` (1),
    `filename` (1), and a few `property-naming` (5).

**Decision (confirmed with developer):** format Bucket A; keep Bucket B
baselined. No `.editorconfig` rule-disabling is needed — because `-F` leaves
Bucket B untouched, "format A / keep B" falls out automatically.

## The baseline-regeneration gotcha (the reason staging is mandatory)

`baseline.xml` matches a suppressed violation by **file + line + column + rule**.
When `-F` reflows a file, the *surviving* Bucket B violations shift to **new line
numbers**, so their old baseline entries stop matching — and the CI check
(`ktlint --baseline`) would then report them as **new** violations and fail.

Therefore **regenerating `config/ktlint/baseline.xml` after each stage's sweep is
mandatory**, not optional. This is also what shrinks the baseline monotonically:
after stage N, the baseline holds only (surviving Bucket B in swept layers) +
(everything in not-yet-swept layers).

> ⚠️ `lint-kotlin.sh --format` runs `-F` over **all of `app/src` with no
> baseline** — a single 436-file / ~20k-line sweep. That is exactly what this
> staged plan exists to avoid. Stages call `ktlint -F <scoped-path>` directly,
> never `lint-kotlin.sh --format`.

## Staging axis — by layer/directory

Chosen over by-rule and hybrid. Rationale: **merge-conflict surface.** By-rule
means every one of ~10-15 PRs touches all 436 files and must rebase on the last,
colliding with concurrent feature work (#34 i18n, #233 Simulation-hoist).
By-layer scopes each PR to one bounded subtree, localizes conflicts, lets each
stage be sequenced for when that area is quiet, and uses one mechanism
(`ktlint -F <path>`). The "each PR mixes rule types" knock is a non-cost: nobody
reads a pure-format diff line-by-line — verification is *"purely mechanical?
builds? all tests pass?"*, which rule-diversity doesn't affect.

## Run mode — sequential, human-gated, one PR per stage

**Not** a parallel Workflow. The developer's hard requirement — *each PR is
monitored and merged before the next begins* — makes the stages a strict
dependency chain (stage N+1 must rebase on N's merged, regenerated baseline or CI
flags shifted Bucket-B line numbers). There is no parallelism to exploit, so this
runs as a sequential pipeline driven PR by PR.

## Stage table

File counts and baseline-error churn are real (measured 2026-06-23):

| Stage | Scope | Files | Bucket-A churn (approx) | Rationale |
|---|---|---|---|---|
| **1 (pilot)** | `domain/` | 104 | ~1364 | Pure Kotlin, best-tested, zero Android. Proves the full pipeline + nails the exact baseline-regen command before touching anything fragile. |
| **2** | `data/` | 78 | ~1760 | Bounded, well-tested repos/sensors/health-connect. |
| **3** | `service/` + `di/` + top-level (`StepsOfBabylonApp.kt`) | 19 | ~228 | Small isolated remainder of non-presentation main. |
| **4** | `presentation/` *(excl. `battle/`)* | 64 | ~3301 | Largest block. Do before #34 i18n edits these strings. **Contingency:** if the diff is unwieldy in review, split by screen-group into 4a / 4b — decided at execution time. |
| **5** | `presentation/battle/` | 40 | ~2037 | **Fragile zone** (custom SurfaceView game loop, thread-safety invariants). Isolated PR, extra-careful verify, sequenced *before* #233 Simulation-hoist starts. |
| **6** | `src/test/` + `src/androidTest/` | 207 | ~557 | Highest file count, lowest risk — last. |

Total: **6 sequential PRs** (Stage 4 may become 7 if split). The `?? other` (9
errors) are top-level / stray files folded into the nearest stage.

## Per-stage protocol (identical every stage)

1. **Sweep:** `ktlint -F app/src/.../<scope>` — fix only that subtree. (Use the
   repo-pinned ktlint 1.8.0; `.ktlint/ktlint` or version-matched PATH binary, as
   `lint-kotlin.sh` resolves it.)
2. **Mechanical-equivalence check:**
   - `git diff -w --stat` should be **near-empty** (whitespace-only reflow is the
     overwhelming majority).
   - For every **non-whitespace** hunk (import reordering, added trailing commas,
     param reflow under `function-signature`), confirm it is a known-safe ktlint
     transform — a focused adversarial review verifies *no semantic change*.
3. **Full test suite:** `./run-gradle.sh testDebugUnitTest` — all green (headline
   1254 JVM tests). Stage 5/6 additionally exercise the relevant instrumented
   guards if a battle-path or manifest file changed.
4. **Regenerate baseline:** rebuild `config/ktlint/baseline.xml`, then confirm
   `./lint-kotlin.sh` (check mode) passes **and** `./run-gradle.sh :app:detekt`
   passes.
5. **Doc sweep (per CLAUDE.md PR Task-List Convention):** audit current-state
   docs; for a pure-format PR the only real touch is appending the stage's
   `RUN_LOG.md` entry. Test count, architecture, schema, conventions all
   unchanged → those docs are a no-op audit. STATE.md is updated on Stage 1
   (kickoff) and Stage 6 (completion).
6. **PR + gate:** branch → push → open PR → **watch checks (`gh pr checks
   --watch`) → squash-merge → delete branch → `git checkout main && git pull`** →
   only then begin the next stage. (Protected-`main` flow, as already used.)

## Verification strategy

Equivalence for a pure-format change has a stronger proof than reviewer opinion:

- **Whitespace-diff emptiness** — `git diff -w` near-empty proves the change is
  overwhelmingly reflow.
- **Full test suite green** — behaviour unchanged.
- **ktlint check + detekt pass** — the gate that CI runs.

The single judgement call is the **non-whitespace hunks** (a minority of ktlint
fixes add/reorder tokens: `import-ordering`, `trailing-comma-*`,
`function-signature` param reflow). Those — and only those — get an adversarial
review confirming each is a benign, well-known ktlint transform with no semantic
effect. This is the one place an agent check earns its keep; the rest is
deterministic.

## Out of scope

- Emptying the baseline / enforcing Bucket B (Compose naming, backing props,
  wildcard imports). Those are intentional or policy decisions for a separate
  effort.
- Any change to the ktlint version, `.editorconfig` rules, or `lint-kotlin.sh`
  itself.
- Any `detekt` rule changes (detekt has its own baseline; unaffected here beyond
  confirming it still passes).
- The two `com.android.test` dev-tooling modules (`:baselineprofile`,
  `:macrobenchmark`) — `lint-kotlin.sh` scopes ktlint to `app/src` only.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| `-F` introduces a semantic change via a token-level fix | Non-whitespace hunks get adversarial review; full test suite must pass. |
| Baseline regen forgotten → CI fails on shifted Bucket-B lines | Step 4 is mandatory and gated by `./lint-kotlin.sh` passing before PR. |
| Concurrent feature work conflicts with a stage | Stages are bounded by layer; sequence each for when its area is quiet (battle before #233, presentation before #34). |
| Stage 4 diff too large to review | Pre-planned 4a/4b split by screen-group. |
| Fragile battle code (#5) | Isolated PR, careful verify, sequenced before #233; thread-safety is structural and untouched by formatting. |
