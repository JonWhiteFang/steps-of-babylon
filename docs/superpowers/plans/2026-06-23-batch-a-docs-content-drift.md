# Plan — Batch A: Docs & Content Drift (audit findings, 2026-06-23)

**Status:** REVIEWED (adversarial review gate passed 2026-06-23 — see "Review outcome" at end)
**Scope:** Documentation + content drift only. **Zero production-code behavior change.**
**Source:** Triage of audit trackers #262 / #128 against HEAD `617babd` (this session's verification
workflow). Batch A is the lowest-risk cluster: docs-only, no Kotlin logic, no schema, no build behavior.
**Findings closed:** #262 L79, L81, L82, L83, L84, L85, L86, L93, L94, L95 (+ the L70-internal README:48
test-count residue, + the code-side of the L78-stale billing-version doc). All were **code-grounded at
HEAD** and confirmed LIVE/accurate by the verify→skeptic fan-out.

## Decisions (developer-approved, this session)
- **GDD design-vs-reality drift (L93/L94/L95):** *Annotate as deferred/future* — keep the design intent,
  tag each claim with a "(deferred — not in v1.0)" note so the GDD stays a vision doc without contradicting
  the code. Do **not** rewrite the vision to match code.
- **Stale binary GDD `.docx` (L81):** *Delete it.* The `.md` is canonical; the binary is unmaintainable and
  actively misleading (still describes Overdrive / Card Dust / GPS-Exploration as current).
- **STATE.md bloat (L84):** *Collapse to current + pointer* — keep the CURRENT objective, replace the
  ~460-line stack of "Previous objective (DONE…)" entries with a one-line pointer to RUN_LOG / the
  "Recently shipped" section (the detail is already preserved there). Tighten the headline too.

## Non-goals / out of scope
- No code-behavior change. The only non-doc file touched is `app/build.gradle.kts` and **only** the
  human-readable `because(...)` justification string of the guava constraint (L79) — version unchanged,
  zero build impact.
- Not touching the other LIVE batches (B dead-code, C i18n, D CI, E VM/a11y, F sensor, G security).
- Not rewriting GDD vision, not regenerating the `.docx`, not trimming STATE.md's reference sections
  (What works / Known issues / fragile zones) — only the redundant objective stack.
- **Historical artifacts stay untouched** per CLAUDE.md: `docs/archive/**`, `docs/external-reviews/**`,
  prior RUN_LOG entries, completed-plan files.

## Edit list (each grounded at HEAD `617babd`)

### Docs — steering & root
1. **L82 — `docs/steering/tech.md:121`**: `./gradlew connectedAndroidTest` → `./gradlew
   connectedDebugAndroidTest` (the bare task fails post multi-module split; line 133 already uses the
   correct form). One-token fix.
2. **L83 — `CLAUDE.md` architecture tree (data/ block opens at line 148 `├── data/`, subpackages
   149–157)**: add the missing
   `│   ├── diagnostics/   # CrashBreadcrumb + CrashBreadcrumbStore (#190 crash visibility)` line. The
   package exists (`CrashBreadcrumb.kt`, `CrashBreadcrumbStore.kt`) but is omitted from the tree.
3. **Billing version drift — code-side of L78-stale (REVIEW: scope widened from 1 site to 9).** The app
   ships `billingPlay = "9.1.0"` (catalog) but **9 live current-state docs** still say "Play Billing v8".
   Fix `Play Billing v8` → `Play Billing v9` (or "Google Play Billing v9") at **all** of these:
   `CLAUDE.md:155`, `README.md:106`, `README.md:138`, `docs/steering/source-files.md:85`,
   `docs/steering/structure.md:33`, `docs/monetization.md:94`, `:103`, `:104`, `:105`, and
   `docs/agent/STATE.md:567`. **data-safety-form.md is a careful case:** line 8 ("the **v1.0.8 release**
   ships … Play Billing v8") is *historically anchored* to v1.0.8 — **leave it**; but line 21 sits in the
   "**Ground truth (what the app actually does — verified in code)**" table — that row IS current-state
   drift → update to v9. AdMob "v25" (25.4.0) is correct everywhere — leave it. Re-`rg "Play Billing v8"`
   after editing; the only remaining hits must be historical (CHANGELOG, RUN_LOG, ADR-0005, docs/reviews/,
   docs/archive/, master-plan:149, plan-31, data-safety-form.md:8) — all correctly stating what was true
   at their authoring date.
4. **L86 — `docs/steering/source-files.md` (~line 238–239)**: add a main-source entry for
   `domain/usecase/AwardBattleSteps.kt` (it appears only as a test entry at line 549). Place it beside its
   sibling `Award*` use cases, with a one-line responsibility descriptor matching their style.
5. **L85 — `README.md:73`**: the broken cross-reference "…formally deferred (no real-framework-only gap;
   **see CLAUDE.md**)" points to a rationale CLAUDE.md does not contain. **Fix by making README
   self-contained** — drop the dangling "see CLAUDE.md" (the rationale "no real-framework-only gap" is
   already stated inline). Lowest-bloat fix; avoids adding StoreIapFlowTest prose to the bloat-guarded
   CLAUDE.md.
6. **L70-residue — `README.md:48`**: comment `# Unit tests (1126 JVM tests)` → `1254` (README:13 was
   synced to 1254 in the v1.0.11 release; this second occurrence was missed). Matches the canonical
   headline in CLAUDE.md:343 / STATE / README:13.

### Build-file comment (no behavior change)
7. **L79 — `app/build.gradle.kts` guava constraint `because(...)` (block lines 308–312, `because()` on
   310)**: the justification string says "Force transitive guava **>=32-android**…" while the enforced pin
   is `33.6.0-android`. Update the string to the **precise** `>=33.6.0-android` (NOT "≥33-android" — that
   re-introduces a smaller version-vs-string mismatch, the same drift class). **String only — the version
   ref `libs.guava` is untouched**, so the dependency graph and build are byte-identical.
7b. **L79 companion (REVIEW: missed duplicate)** — `gradle/libs.versions.toml:42` carries the *identical*
    stale rationale comment ("…at 31.1-android; force **>=32** to clear CVE-2023-2976 / CVE-2020-8908.")
    directly above `guava = "33.6.0-android"` (:43). Fix the comment the same way (`>=33.6.0`) so the two
    human-readable justifications don't diverge. Comment-only, zero build impact.

### GDD — annotate as deferred (L93/L94/L95)
> ⚠️ **REVIEW: edits 8 and 9 both touch the SAME physical line `GDD.md:220`** (it carries both the
> "cinematic…ascending" clause AND the "…unlocks a cosmetic ziggurat skin" clause). Apply them as **one
> combined edit to line 220** to avoid a stale-match second edit — a single appended marker can cover both
> deferred clauses, e.g. `*(deferred — v1.0 ships a static text/gradient overlay (BiomeTransitionOverlay),
> no animated cinematic and no biome→cosmetic unlock; skins are Store-purchase only)*`. Edit 9's `:362`
> marker is a separate line.
8. **L93 — `docs/StepsOfBabylon_GDD.md:220`** (combined with edit 9): annotate the "…unlocks a cosmetic
   ziggurat skin" clause as deferred (no biome→cosmetic unlock in v1.0; skins are Store-purchase only).
9. **L94 — `docs/StepsOfBabylon_GDD.md:220` (combined) + `:362`**: "a cinematic showing the ziggurat
   'ascending'" / "Biome unlock cinematics" — annotate deferred (v1.0 ships a static text/gradient overlay,
   `BiomeTransitionOverlay`; animated cinematic is post-v1.0). The `:362` "Biome unlock cinematics:" line
   gets its own deferred marker.
10. **L95 — `docs/StepsOfBabylon_GDD.md:303`**: the `TYPE_STEP_DETECTOR` "Tertiary" sensor line — append
    `*(deferred — not implemented in v1.0; only `TYPE_STEP_COUNTER` is used. See `docs/steering/` deferral
    note)*`.

### Deletion
11. **L81 — delete `docs/StepsOfBabylon_GDD.docx`** (`git rm`). 38 KB binary; canonical is the `.md`. Only
    reference is in `docs/archive/pre-claude-devdocs/.../cleanup_inventory.md` (historical, never-modify —
    and that entry already flags it as cleanup-eligible). Pages publishes from `site/` only, so no publish
    impact.

### STATE.md trim (L84)
12. **L84 — `docs/agent/STATE.md`**: collapse the "## Current objective" section — keep the **CURRENT**
    entry (the v1.0.11-shipped block) and replace the entire stack of "Previous objective (DONE…)" bullets
    (~lines 59–519) with a single pointer line:
    `> Prior objectives (all DONE, `[Unreleased]` unless noted) are recorded per-PR in
    `docs/agent/RUN_LOG.md` and summarized under "Recently shipped" below — not duplicated here.`
    **Also tighten the headline (lines ~5–35): DO remove the verbatim 16-step per-wave test-count running
    tally at STATE.md:19–21** (1110→1118→…→1254) — that detail is already preserved in RUN_LOG.md:728 and
    CHANGELOG.md:24 ("1110 → 1254 JVM tests across the accumulated waves"); replace it with the canonical
    headline `1254 JVM + 9 instrumented tests`. (REVIEW: the prior "if it still carries" hedge was
    non-committal — this is now a firm instruction.) **Do NOT touch** "Recently shipped", "What works",
    "Known issues / debt", "Top priorities", "Do-not-touch / fragile zones", or "References" — those are
    load-bearing and in scope only for their factual accuracy, not trimming.

## Verification
- **No code change → no test run strictly required**, but per repo discipline run
  `./run-gradle.sh testDebugUnitTest` once at the end to confirm the build is unperturbed (the build.gradle
  `because()` string edit is the only build-file touch; assert **1254 JVM, 0 failures** unchanged).
- **Markdown sanity:** re-render-check the three edited docs (STATE.md still has its required section
  headers; CLAUDE.md tree still parses as a code block; GDD anchors intact).
- **Link integrity:** confirm no internal doc links break from the README:73 edit or the `.docx` deletion
  (`rg "GDD\.docx"` over non-archive paths returns nothing after deletion).
- **STATE.md is meaningfully shorter** (target: well under the prior 846 lines; the one-page mandate) while
  CURRENT objective + all reference sections survive intact.

## PR Task-List (mandatory convention — sync-docs BEFORE STATE/RUN_LOG, then commit)
1. Apply edits 1–11 (steering/README/CLAUDE/GDD/build-comment + `.docx` deletion).
2. Apply edit 12 (STATE.md trim) — this *is* the current-state-doc sync for this PR.
3. Run `testDebugUnitTest`; confirm 1254 JVM / 0 failures unchanged.
4. **Sync current-state docs:** CHANGELOG.md — add an `[Unreleased]` entry for the doc-drift batch
   (findings closed). No other current-state doc is invalidated by this batch (CLAUDE.md/source-files.md are
   themselves edited above; no schema/structure/tech-version change beyond the billing-doc correction).
5. **Update `docs/agent/STATE.md`** (already trimmed in step 2 — also flip the CURRENT objective to this
   batch) **+ append `docs/agent/RUN_LOG.md`** with the batch summary (findings closed, decisions, that it
   was code-grounded at `617babd`).
6. Commit on a branch (e.g. `docs/batch-a-doc-drift`), open PR, reference the tracker findings; check off
   L79/L81/L82/L83/L84/L85/L86/L93/L94/L95 in #262 once merged.

## Risk
**Low.** Docs + one build-comment string + one binary deletion. The only ways this bites:
(a) the build.gradle `because()` edit accidentally changing the version ref — mitigated by string-only edit;
(b) deleting a `.docx` that something publishes — ruled out (Pages = `site/` only, sole ref is archival);
(c) over-trimming STATE.md load-bearing detail — mitigated by trimming ONLY the redundant objective stack,
which is **fully preserved in RUN_LOG.md** (the "Recently shipped" STATE section is itself already trimmed
to ~2 dates, so RUN_LOG — not "Recently shipped" — is the complete backstop; REVIEW nit).

## Review outcome (adversarial gate, 2026-06-23)

Reviewed via a multi-agent `Workflow` (3 dimensions — grounding / scope-completeness / risk-consistency —
each code-grounded then adversarially skeptic-verified at HEAD `617babd`). **14 findings, all CONFIRMED
(0 refuted, 0 partial).** No `critical`. Applied to this plan:

- **MAJOR — billing-v8 scope gap:** plan fixed only CLAUDE.md:155; the same stale "Play Billing v8" lives in
  **9 live current-state docs** (billing is now 9.1.0). → **Edit 3 widened to all 9** (with the
  data-safety-form.md line-8-historical / line-21-current nuance). *This was the one finding that would have
  shipped relocated drift.*
- **MINOR — guava comment duplicate:** the `>=32-android` stale string also in `libs.versions.toml:42`. →
  **Added edit 7b.**
- **MINOR — STATE headline hedge:** "if it still carries…" was non-committal; the 16-step tally IS at
  STATE.md:19–21. → **Edit 12 now firmly removes it.**
- **MINOR — GDD same-line:** edits 8 & 9 both target `GDD.md:220`. → **Merged into one combined edit** to
  avoid a stale second match.
- **NITs (folded in):** edit-2 data/ block opens at line 148 (not 149); edit-7 use the precise
  `>=33.6.0-android` (not "≥33-android"); STATE over-trim backstop is RUN_LOG, not "Recently shipped".
- **CONFIRMED-SOUND (no change needed):** all other citations accurate at HEAD; `.docx` deletion safe (Pages
  publishes `site/` only); build.gradle `because()` edit is genuinely string-only; PR task-list ordering
  satisfies the sync-before-memory convention; no historical-artifact path is touched. L82/L70/L86/L93/L94/
  L95 targets are complete (no missed live instances — other `1126`/`connectedAndroidTest` hits are all
  historical and correctly stay untouched).
