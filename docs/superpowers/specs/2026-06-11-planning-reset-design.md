# Planning Reset & Closed-Test Readiness Gate — Design

**Date:** 2026-06-11
**Status:** Approved (brainstorming complete; awaiting spec review → writing-plans)
**Author:** Claude (with Jon White)

## Problem

The project's planning corpus has two issues:

1. **Pre-Claude planning work is mixed in with live planning.** A pre-Claude codebase
   reverse-engineering + forward-planning exercise (`devdocs/` — 38 files across `archaeology/`,
   `evolution/`, `foundations/`; plus its sibling `smoke_tests/`), frozen at HEAD `a9d0386`
   (git-dated 2026-05-07), sits alongside the live `docs/plans/` spine. CLAUDE.md already
   classifies `devdocs/*` + `smoke_tests/*` as "historical artifacts — NEVER modify." The
   ~38 numbered/remediation plan files in `docs/plans/` (plan-01…30, 10b, R, R2, R3, R4, RO-09/11/12)
   describe work that is 100% shipped — also historical, but cluttering the live planning directory.

2. **The forward plan is built around the wrong milestone.** `master-plan.md`, `STATE.md`, and
   `plan-V1X-roadmap.md` all assume a linear march to **production launch** gated by a tester-count
   clock (≥12 testers + ≥14-day closed-track soak). The V1X roadmap explicitly sequences all its work
   "post-v1.0.0 production rollout." But the developer is **staying in internal testing** and will not
   promote to closed testing until **he judges the game good enough** — a quality gate that does not
   currently exist as a first-class artifact. Much of the "make it good" work (audio, onboarding,
   polish, audit Lows) is buried in V1X as post-launch work when it actually gates the next step.

## Goals

- Physically separate historical planning artifacts from live ones, preserving git history and
  discoverability.
- Introduce an explicit, written **Closed-Test Readiness Gate** — a quality checklist that defines
  "the game is good enough to put in front of closed testers."
- Re-organize all candidate forward work into three phases keyed to that gate, with the V1X roadmap
  demoted to a referenced backlog the new plan draws from.

## Non-goals

- No production code, test, or schema change — this is a documentation/planning reset only.
- Not deleting any planning content — everything is archived (moved) or re-referenced, never lost.
- Not authoring the detailed sub-plans for gate items in this pass — the forward plan enumerates and
  sequences them; each item keeps pointing at its existing V1X sub-plan / GitHub issue for detail.
- Not touching historical artifacts' *content* (per the checkpoint protocol) — only moving them and
  fixing inbound links.

## Decisions locked (brainstorming, 2026-06-11)

1. **Archive scope:** `devdocs/` + `smoke_tests/` + the completed v1.0 plan files.
2. **Gate definition:** a concrete written quality checklist.
3. **V1X relationship:** new forward plan re-buckets V1X into three gate-keyed phases; V1X → referenced backlog.
4. **Archive mechanism:** move into dated `archive/` trees + pointers; repoint the ~21 inbound `devdocs/` refs; git history preserved.
5. **`plan-31-play-console.md`:** kept live (the internal→closed→production release journey is still active).

## Design

### 1. Archive structure

```
docs/archive/
├── README.md                       # what's here, why frozen, "git log --follow" note, when archived
├── pre-claude-devdocs/             # ← entire devdocs/ tree (archaeology + evolution + foundations, 38 files)
├── smoke_tests/                    # ← entire smoke_tests/ tree
└── completed-plans-v1.0/
    ├── README.md                   # index: plan → what shipped → where the live doc/code is now
    └── <moved plan files>          # plan-01..30, 10b, R, R2, R3, R4, RO-09, RO-11, RO-12
```

**Archived (moved):**
- `devdocs/` → `docs/archive/pre-claude-devdocs/`
- `smoke_tests/` → `docs/archive/smoke_tests/`
- Completed plan files → `docs/archive/completed-plans-v1.0/`:
  `plan-01-domain-models` … `plan-30-release`, `plan-10b-advanced-combat`,
  `plan-R-remediation`, `plan-R2-remediation`, `plan-R3-remediation-3`,
  `plan-R4-feedback-bundle`, `plan-RO-09-pre-closed-test-fixes`,
  `plan-RO-11-labs-wiring`, `plan-RO-12-in-round-stat-drift`.

**Stays live in `docs/plans/`:**
- `master-plan.md` — updated: status tracker stays as the historical completion record, plus a new
  pointer to the forward plan and a note that completed plan *files* now live under `docs/archive/`.
- `plan-V1X-roadmap.md` — demoted to **backlog of record** (a header note clarifies its new role; its
  internal post-launch sequencing is superseded by the forward plan's phasing).
- `plan-31-play-console.md` — kept (active release journey).
- `plan-32-ci.md` — kept (documents the running CI pipeline; reference, not history).
- **`plan-FORWARD.md`** (new) — the three-phase forward plan (see §2).

Moves use `git mv` so history is preserved; `git log --follow` traces any file across the move.

### 2. Forward plan (`docs/plans/plan-FORWARD.md`)

Three phases keyed to the gate:

| Phase | Contains | Exit criterion |
|---|---|---|
| **Phase 1 — Closed-Test Readiness** | Every work item the quality checklist (§3) demands, drawn from unshipped V1X sub-plans + open GitHub issues + open audit Lows. Each item links to its V1X sub-plan / issue for detail. | **Quality checklist fully ticked → developer decides to promote to closed test.** |
| **Phase 2 — Closed Test** | Promote internal → closed track; recruit ≥12 testers; ≥14-day soak; triage feedback; apply for production access. | Production access granted; staged rollout begins. |
| **Phase 3 — Post-Launch (v1.1+)** | Work that does NOT gate "the game is good": cloud save (V1X-12), i18n phase 2+ (V1X-13), meta-progression (V1X-25), modularisation (V1X-24), telemetry (V1X-21), balancing tooling (V1X-27), and the strategic proposals V1X-20/23/26/28. | Ongoing. |

The forward plan is the **single entry point** for "what's between here and a good game." It owns the
phase sequencing; V1X owns the per-sub-plan detail.

### 3. Closed-Test Readiness Gate (the checklist)

A checklist of categories, each with concrete checkbox items and a "satisfied-by" link to a V1X
sub-plan / GitHub issue / known-issues debt item. The developer's judgment is the final gate; the
checklist makes that judgment explicit and reviewable ("what's still unchecked?").

Categories (confirmed in brainstorming):

- **A. Audio & game feel** — the audio overhaul **already shipped** (V1X-04/05/06, PRs #67/#68/#69;
  issues #38/#46/#39 CLOSED): synthesized SFX, attack-speed-scaled throttle, `MusicManager` with audio
  focus + volume control. The gate item is therefore an **in-play feel assessment** (do the synthesized
  SFX/music actually sound good; any throttle/ducking glitches), not delivery — reopen V1X-04 for real
  recorded assets only if the synthesized SFX disappoint.
- **B. Content honesty** — no misleading "Coming Soon" in core flows; AUTO_UPGRADE_AI resolved
  (#44); cosmetic set coherent (remaining "Coming Soon" skins either ship or are clearly framed).
- **C. First-session UX** — onboarding / first-walk celebration / a brand-new player understands the
  walk→spend→battle loop (#24).
- **D. Correctness & stability** — open audit Lows cleared (#124 billing signature, #127 duplicate
  daily missions, #128 30-Low tracker); no known crashes; clean fresh-install run.
- **E. Balance & progression feel** — early tiers (1–5) feel right; economy neither grindy nor
  trivial; STEP_MULTIPLIER curve sane (V1X-18 already shipped — verify in-feel).
- **F. Upgrade decision support** — Workshop / Cards readability and "is this a good buy?" legibility (#29).
- **G. Performance & battery** — acceptable on a low-end device; foreground-service + Health Connect
  polling battery cost sane (#26).

Each category resolves to specific checkboxes when the forward plan is written. The set A–G is the
agreed scope; items can be marked "deferred — not a blocker" with a one-line rationale rather than
silently dropped, so the gate stays honest.

### 4. Mechanics (doc-only, on a branch)

- `git mv` the archived trees/files into `docs/archive/`.
- Write `docs/archive/README.md` + `docs/archive/completed-plans-v1.0/README.md`.
- Repoint the ~21 inbound `devdocs/` references (CLAUDE.md, CHANGELOG, several ADRs, RUN_LOG,
  `.claude/skills/checkpoint/SKILL.md`, plan-32, the kiro-conversion spec) to the new
  `docs/archive/pre-claude-devdocs/` paths. Internal `devdocs/`→`devdocs/` cross-links move together
  so they stay valid.
- Update CLAUDE.md's "Historical artifacts — NEVER modify" path list to the new archive paths, and add
  the completed-plan-files archive to it.
- Update `master-plan.md` (pointer to forward plan + note that completed plan files moved).
- Update `STATE.md` references block + `docs/steering/structure.md` "Development Plans" section to
  reflect the new `docs/plans/` shape and the archive.
- Add the V1X "backlog of record" header note.

## Components & boundaries

- **`docs/archive/`** — frozen historical store. One purpose: preserve pre-Claude + completed-v1.0
  planning, discoverable behind READMEs. Never modified after archival.
- **`docs/plans/plan-FORWARD.md`** — the live forward plan. One purpose: phase-sequence the path from
  "internal testing now" to "good enough → closed test → production." Draws items from V1X + issues.
- **`docs/plans/plan-V1X-roadmap.md`** — the backlog of record. One purpose: hold per-sub-plan detail.
  Referenced by the forward plan; no longer owns sequencing.
- **The Gate (a section inside plan-FORWARD.md)** — the quality checklist. One purpose: define and
  track "happy to promote." The single source of truth for the readiness decision.

## Testing / validation

Doc-only change, so validation is link- and consistency-checking, not build/test:
- No broken inbound links after the move (grep for `devdocs/` and stale `docs/plans/plan-0*` /
  `plan-R*` / `plan-RO-*` references; confirm each resolves to the new archive path or is intentionally
  historical).
- CLAUDE.md "NEVER modify" list names the new paths.
- `git log --follow` resolves a sample moved file (history-preservation check).
- STATE.md + master-plan.md + structure.md agree on the new `docs/plans/` shape.

## Risks

- **Broken cross-references** — inbound refs to moved paths exist in LIVE docs (CLAUDE.md + the
  checkpoint skill historical lists), live plan files (`master-plan.md` 35 links, `plan-V1X-roadmap.md`,
  `plan-32-ci.md`), and even two live source-code KDoc comments (`UltimateWeaponType.kt`,
  `Migrations.kt`) + one CI comment (`ci.yml`). Mitigation: the plan repoints every LIVE ref (Tasks 5,
  6, 8, 9) and runs a repo-wide sweep with an explicit frozen-exemption list before commit. (The
  adversarial review of the implementation plan, 2026-06-11, surfaced the live-plan-file and
  source-comment refs that an initial markdown-only scan missed.)
- **Archived-but-still-needed** — a completed plan file might be referenced as live context. Mitigation:
  the completed-plans README is an index, and LIVE refs are repointed rather than dropped. **Accepted
  exception:** 7 markdown links to `plan-R4-feedback-bundle.md` inside FROZEN `CHANGELOG.md` entries
  will 404 after the move — editing frozen history to fix link targets would violate the "never modify
  historical artifacts" rule, and the archived file itself stays reachable + indexed, so the only
  impact is dead in-doc hyperlinks in dated history. Not "nothing becomes unreachable" — the *files*
  stay reachable; a handful of *historical hyperlinks* go stale by design.
- **Gate scope creep** — the checklist could balloon into "make it perfect." Mitigation: A–G is the
  agreed ceiling; items get "deferred — not a blocker" rather than the gate growing new categories
  without a decision.

## Out of scope / deferred

- Authoring detailed implementation sub-plans for individual gate items (they keep pointing at V1X).
- Any production code / balance / asset work — the gate enumerates it; doing it is the next plans.
- Reorganizing `docs/steering/` or the ADR set.
