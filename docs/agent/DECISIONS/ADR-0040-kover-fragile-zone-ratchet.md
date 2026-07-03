# ADR-0040: Scoped Kover coverage ratchet on the fragile concurrency/economy zones

## Context
- Kover was added report-only (#218, ADR-0037 wave): CI ran `koverXmlReport`/`koverHtmlReport` with **no
  threshold**. A refactor that guts test assertions on the costliest-to-break code (the battle game-loop
  engine + the currency/economy repositories & use cases) would fail **no** check and ship green.
- Finding **#373 (`testing-1`)** in the 2026-07-02 tooling-gap assessment asked for a **scoped**
  `koverVerify` floor on those fragile zones only — a coverage regression backstop, not a whole-app target.
- The freshly-measured LINE coverage of the four target packages spans a wide band:
  `data.repository` **56.94%**, `presentation.battle.engine` **92.15%**, `domain.usecase` **98.73%**,
  `domain.battle.{engine 100, entity 96.10}`. So a single blended floor would let a high-coverage package
  erode a long way before tripping.

## Decision
Add a **filtered `reports.variant("debug")` set** in `app/build.gradle.kts` that gates the
auto-generated **`koverVerifyDebug`** task, scoped (via `filters { includes { classes(...) } }`) to the
four fragile packages, with **two complementary rules**:
- **Rule A — `groupBy = APPLICATION`, LINE floor 85** (measured blended 87.18%): resists slow
  *multi-package* erosion across the whole scoped zone.
- **Rule B — `groupBy = PACKAGE`, LINE floor 54** (lowest package `data.repository` 56.94%): resists any
  *single* package collapsing (which the blend could mask).

Floors are a **ratchet** = measured − a small (~2pt) churn margin; raise them as coverage climbs, never
silently lower. CI runs `:app:koverVerifyDebug` as a **separate step after** the informational report step.

## Alternatives considered
- **A: four rules, each with its own per-rule `filters` scope + its own measured floor** (the first plan
  draft) — **REJECTED / impossible in Kover 0.9.8.** Verified by disassembling `kover-gradle-plugin-0.9.8.jar`
  (and a probe build): `KoverVerifyRule` exposes only `groupBy` + `bound`/`minBound` — **there is no
  `filters` on a verify rule.** `filters {}` exists only on the report *set* (`KoverReportSetConfig`:
  `total` / `variant(...)`), where it is shared by that set's report AND verify tasks. A `rule { filters {} }`
  is a configuration-time compile error. This was the CRITICAL finding of the plan's adversarial review — it
  would have shipped a non-compiling build file.
- **B: filter the `total` set + one blended rule** — **REJECTED.** It compiles and scopes correctly, but the
  `total` filter also **narrows the #218 informational `koverXmlReport`** (probed: 49 → 5 packages),
  defeating #218's stated purpose of surfacing which *whole-app* surfaces are uncovered. A **variant** set
  isolates the gate: `koverVerifyDebug` sees only the fragile packages while `total`/`koverXmlReport` stays
  whole-app (probed: still 49 packages).
- **C: top-level `reports.verify` with `groupBy = PACKAGE`** — **REJECTED.** It is whole-app (probed: it
  evaluated all 47 packages, incl. the many 0%-covered generated Hilt/DI/settings packages), so any
  per-package floor is unshippable there.
- **D: one PACKAGE-grouped rule at a single floor** — **REJECTED as too weak.** One floor over packages
  spanning 57→100% must sit below the lowest (~54), barely protecting the 92–100% zones. Two rules (A+B) in
  the one variant set were probed to both fire, giving both aggregate-erosion and single-package protection.

## Consequences
- **Positive:** a refactor that silently drops assertions on the battle-engine or economy code now fails
  `koverVerifyDebug` on every code PR. #218's whole-app informational report is untouched. Machine-enforces
  a regression floor on exactly the surface the concurrency/economy invariants live on — complements the
  prose/build-gated guards (`DomainPurityTest`, `StepCreditAllowlistTest`, `BattleEngineLockScanTest`).
- **Negative / tradeoffs:** the protection is **coarser than per-package-distinct floors** (Rule B applies
  one floor to all four packages) — a deliberate acceptance forced by the 0.9.8 DSL, mitigated by Rule A's
  blended floor. The floor is measured − ~2pt (a **deviation from #373's literal "floor = current measured
  %"**, flagged for and accepted at sign-off) so trivial churn doesn't red the build. CI must call
  `koverVerifyDebug` (the scoped task), **not** the aggregate `koverVerify` (whole-app → fails on the 0%
  generated packages) — a comment in `ci.yml` + the fragile-zone note in STATE.md records this.
- **Follow-ups:** raise the floors as coverage climbs (ratchet). If any scoped package proves churny in
  practice, widen just that floor. A stricter per-package-distinct-floor design would need a Kover line that
  supports per-rule scoping (or a two-variant split, deemed not worth the fragility now).

## Links
- Commit(s): `98f8aa2` (PR-1 implementation — `kover {}` block + `ci.yml` wiring), merged in PR #402
  (`aa2b50c`). Plan + review: `docs/superpowers/plans/2026-07-03-phase3-nonfragile-guards.md`.
- Related ADRs: ADR-0037 (lint tooling — Kover introduced report-only), ADR-0038 (deferred build-gated
  invariant precedent), ADR-0018 (CI). Issue: #373 (follow-up to #218).
