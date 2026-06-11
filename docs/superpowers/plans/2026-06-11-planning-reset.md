# Planning Reset & Closed-Test Readiness Gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Archive the pre-Claude planning corpus and completed-v1.0 plan files into a dated `docs/archive/` tree, then author a new three-phase forward plan anchored on an explicit Closed-Test Readiness Gate, demoting the V1X roadmap to a referenced backlog.

**Architecture:** Doc-only reorganization. `git mv` moves historical artifacts (history-preserving); new READMEs index each archive area; the small live cross-reference surface (CLAUDE.md, the checkpoint skill, master-plan's 35 internal plan links) is repointed; a new `plan-FORWARD.md` becomes the single entry point for "what's between here and closed-test-ready." No production code, test, or schema change.

**Tech Stack:** Markdown, `git mv`, grep-driven link verification. Branch: `docs/planning-reset` (already created; the design spec `docs/superpowers/specs/2026-06-11-planning-reset-design.md` is already committed there at `c691f0e`).

**Validation model:** Because this is doc-only, "tests" are grep-based link/consistency checks, not unit tests. Each task's verify step is a grep or `git log --follow` whose expected output is stated exactly.

**Scope note on cross-references (revised after the 2026-06-11 adversarial review of this plan).** LIVE (non-frozen) references to the moved paths exist in MORE places than an initial markdown-only scan suggested. The complete set this plan repoints:
- `CLAUDE.md` + `.claude/skills/checkpoint/SKILL.md` — the identical 3-line "Historical artifacts — NEVER modify" list (incl. a `plan-R*` line). **Task 6.**
- `docs/plans/master-plan.md` — 35 internal links to completed plan files. **Task 5.**
- `docs/plans/plan-V1X-roadmap.md` (line ~1552, plan-R4 ref) + `docs/plans/plan-32-ci.md` (line ~135, devdocs ref) — kept-live plan files. **Task 8.**
- `app/src/.../UltimateWeaponType.kt` + `app/src/.../Migrations.kt` (KDoc comments) + `.github/workflows/ci.yml` (comment) — live source/CI references. **Task 9.**

Left FROZEN (historical-at-authoring-date per CLAUDE.md's own rule, NOT repointed): `docs/archive/**` internal cross-links (they move together and stay valid), `CHANGELOG.md`, `docs/agent/RUN_LOG.md`, `docs/agent/DECISIONS/ADR-*.md`, and the prior `2026-06-10-*` kiro-conversion spec/plan. **Accepted consequence:** 7 `plan-R4` markdown links inside frozen CHANGELOG entries will 404 (the file stays reachable + indexed; only the historical hyperlinks go stale). Task 9 Step 3 is a repo-wide sweep with this exact exemption list so no live break hides in an un-scanned file.

---

## File Structure

**New files:**
- `docs/archive/README.md` — top-level archive index (what's here, why frozen, history note).
- `docs/archive/completed-plans-v1.0/README.md` — index mapping each archived plan → what shipped.
- `docs/plans/plan-FORWARD.md` — the new three-phase forward plan + the Closed-Test Readiness Gate.

**Moved (via `git mv`, history preserved):**
- `devdocs/` → `docs/archive/pre-claude-devdocs/`
- `smoke_tests/` → `docs/archive/smoke_tests/`
- 38 completed plan files → `docs/archive/completed-plans-v1.0/` (list in Task 4).

**Modified:**
- `CLAUDE.md` — repoint the historical-artifacts path list; add the completed-plans archive; add a pointer to `plan-FORWARD.md`.
- `.claude/skills/checkpoint/SKILL.md` — repoint the same historical-artifacts path list.
- `docs/plans/master-plan.md` — repoint 35 internal completed-plan links to `../archive/completed-plans-v1.0/`; add a forward-plan pointer + a note that completed plan files moved.
- `docs/plans/plan-V1X-roadmap.md` — add a "backlog of record" header note; repoint the stale plan-R4 ref (line ~1552).
- `docs/plans/plan-32-ci.md` — repoint the stale devdocs ref in the doc-edit instruction (line ~135).
- `docs/agent/STATE.md` — references block: add `plan-FORWARD.md` as the primary forward pointer; note the archive.
- `docs/steering/structure.md` — "Development Plans" section: reflect the new `docs/plans/` shape + the archive.
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/UltimateWeaponType.kt` + `.../data/local/Migrations.kt` — repoint plan-R4 KDoc-comment refs (comment-only, no behaviour change).
- `.github/workflows/ci.yml` — repoint the smoke_tests comment.
- `CHANGELOG.md` — new Docs section.
- `docs/agent/RUN_LOG.md` — new entry.

---

## Task 1: Create the archive scaffold + top-level README

**Files:**
- Create: `docs/archive/README.md`

- [ ] **Step 1: Create the archive directory and its README**

Create `docs/archive/README.md` with this exact content:

```markdown
# Archive

Frozen historical planning artifacts. **Do not modify anything under this directory** — these
are point-in-time records, valuable only as written. New planning lives in `docs/plans/`
(see `docs/plans/plan-FORWARD.md` for the current forward plan).

`git log --follow <path>` traces any archived file's full history across the move into this tree.

## Contents

| Path | What it is | Frozen at |
|---|---|---|
| `pre-claude-devdocs/` | Pre-Claude codebase reverse-engineering + forward-planning exercise (archaeology / evolution / foundations). Produced by an earlier tool against HEAD `a9d0386`. | 2026-05-07 |
| `smoke_tests/` | Sibling of the above — a manual "what is working" smoke report against the same HEAD. | 2026-05-07 |
| `completed-plans-v1.0/` | The numbered + remediation plan files (Plans 01–30, 10b, R, R2, R3, R4, RO-09/11/12) whose work shipped in v1.0. See its own README for the per-plan index. | 2026-06-11 |

Archived 2026-06-11 as part of the planning reset (see
`docs/superpowers/specs/2026-06-11-planning-reset-design.md`).
```

- [ ] **Step 2: Verify the file exists**

Run: `cat docs/archive/README.md | head -3`
Expected: prints `# Archive` and the first lines.

- [ ] **Step 3: Commit**

```bash
git add docs/archive/README.md
git commit -m "docs(archive): scaffold archive tree + top-level README"
```

---

## Task 2: Move `devdocs/` into the archive

**Files:**
- Move: `devdocs/` → `docs/archive/pre-claude-devdocs/`

- [ ] **Step 1: Move the tree with git (history-preserving)**

Run:
```bash
git mv devdocs docs/archive/pre-claude-devdocs
```

- [ ] **Step 2: Verify the move and that internal links still resolve**

Run: `ls docs/archive/pre-claude-devdocs/ && test ! -e devdocs && echo "OLD PATH GONE"`
Expected: lists `archaeology evolution foundations` then prints `OLD PATH GONE`.

Run (internal cross-links move together, so a sampled internal link target still exists):
```bash
test -f docs/archive/pre-claude-devdocs/archaeology/5_things_or_not.md && echo "INTERNAL TARGET OK"
```
Expected: `INTERNAL TARGET OK`.

- [ ] **Step 3: Verify history is preserved**

Run: `git log --follow --oneline docs/archive/pre-claude-devdocs/evolution/implementation_roadmap.md | tail -1`
Expected: prints the original creation commit (a pre-2026-05-10 hash) — proves `--follow` traces across the move.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs(archive): move pre-Claude devdocs/ → docs/archive/pre-claude-devdocs/"
```

---

## Task 3: Move `smoke_tests/` into the archive

**Files:**
- Move: `smoke_tests/` → `docs/archive/smoke_tests/`

- [ ] **Step 1: Move the tree with git**

Run:
```bash
git mv smoke_tests docs/archive/smoke_tests
```

- [ ] **Step 2: Verify**

Run: `ls docs/archive/smoke_tests/ && test ! -e smoke_tests && echo "OLD PATH GONE"`
Expected: lists the smoke_tests contents (e.g. `check_what_is_working`) then `OLD PATH GONE`.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs(archive): move smoke_tests/ → docs/archive/smoke_tests/"
```

---

## Task 4: Move the completed v1.0 plan files + write their index

**Files:**
- Move: 38 plan files from `docs/plans/` → `docs/archive/completed-plans-v1.0/`
- Create: `docs/archive/completed-plans-v1.0/README.md`

- [ ] **Step 1: Move all completed plan files with git**

Run (each is a shipped plan; `plan-31`, `plan-32`, `plan-V1X-roadmap`, and `master-plan` stay in `docs/plans/`). **The `mkdir -p` is required first** — `git mv <many files> <dir>/` fails with `fatal: destination ... is not a directory` (exit 128) if the leaf directory doesn't already exist (Task 1 only created `docs/archive/`, not this leaf):
```bash
mkdir -p docs/archive/completed-plans-v1.0
cd docs/plans
git mv plan-01-domain-models.md plan-02-database.md plan-03-repositories.md \
  plan-04-step-counter.md plan-05-health-connect.md plan-06-home-navigation.md \
  plan-07-workshop.md plan-08-battle-renderer.md plan-09-enemies-waves.md \
  plan-10-stats-combat.md plan-10b-advanced-combat.md plan-11-in-round-upgrades.md \
  plan-12-round-lifecycle.md plan-13-tier-system.md plan-14-step-overdrive.md \
  plan-15-ultimate-weapons.md plan-16-labs.md plan-17-cards.md plan-18-biomes.md \
  plan-19-walking-encounters.md plan-20-premium-currencies.md plan-21-milestones-missions.md \
  plan-22-stats-history.md plan-23-notifications-widget.md plan-24-accessibility.md \
  plan-25-anti-cheat.md plan-26-monetization.md plan-27-polish-vfx.md \
  plan-28-balancing.md plan-29-testing.md plan-30-release.md \
  plan-R-remediation.md plan-R2-remediation.md plan-R3-remediation-3.md \
  plan-R4-feedback-bundle.md plan-RO-09-pre-closed-test-fixes.md \
  plan-RO-11-labs-wiring.md plan-RO-12-in-round-stat-drift.md \
  ../archive/completed-plans-v1.0/
cd ../..
```

- [ ] **Step 2: Verify exactly the right files moved (3 plan files + master-plan remain live)**

Run: `ls docs/plans/*.md`
Expected (exactly these four): `master-plan.md  plan-31-play-console.md  plan-32-ci.md  plan-V1X-roadmap.md`

Run: `ls docs/archive/completed-plans-v1.0/*.md | wc -l`
Expected: `38`

- [ ] **Step 3: Write the completed-plans index README**

Create `docs/archive/completed-plans-v1.0/README.md` with this exact content:

```markdown
# Completed v1.0 Plans (archived)

These plan files drove Steps of Babylon from scaffold to a complete, shippable v1.0 (live on the
Play Console internal track). Their work is 100% merged to `main`. They are **frozen** — kept for
historical traceability, not maintained. The completion record lives in `docs/plans/master-plan.md`
(status tracker) and the per-PR history in `CHANGELOG.md` / `docs/agent/RUN_LOG.md`.

Forward planning is in `docs/plans/plan-FORWARD.md`.

## Index

| Plan | Scope | Status |
|---|---|---|
| 01–03 | Domain models, Room database, repositories | shipped |
| 04–05 | Step counter service, Health Connect integration | shipped |
| 06–07 | Home/navigation, Workshop & upgrades | shipped |
| 08–10, 10b | Battle renderer, enemies/waves, stats/combat, advanced combat (orbs/multishot/bounce) | shipped |
| 11–13 | In-round upgrades & cash, round lifecycle, tier system | shipped |
| 14 | Step Overdrive *(later removed in R4-01)* | shipped then removed |
| 15–17 | Ultimate Weapons, Labs, Cards | shipped |
| 18–23 | Biomes, walking encounters, premium currencies, milestones/missions, stats/history, notifications/widget | shipped |
| 24 | Accessibility *(deferred — post-v1.0)* | deferred |
| 25–30 | Anti-cheat, monetization, polish/VFX, balancing, testing, release prep | shipped |
| R, R2, R3, R4 | Remediation waves (external review, smoke-test fixes, internal-soak feedback bundle) | shipped |
| RO-09 | Pre-closed-test fix bundle (chrono, fortune stacking) | shipped |
| RO-11 | Labs wiring + in-round visibility | shipped |
| RO-12 | In-round stat-drift bugfix bundle | shipped |

Archived 2026-06-11 (planning reset — see `docs/superpowers/specs/2026-06-11-planning-reset-design.md`).
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs(archive): move completed v1.0 plan files + index README"
```

---

## Task 5: Repoint master-plan.md's internal links to the archived plans

**Files:**
- Modify: `docs/plans/master-plan.md`

- [ ] **Step 1: Verify the broken-link count before the fix**

Run: `grep -c "(\./plan-" docs/plans/master-plan.md`
Expected: `38` (35 completed + plan-31 + plan-32 + plan-V1X — the 35 completed ones now 404).

- [ ] **Step 2: Rewrite the completed-plan links to the archive path**

Apply a sed rewrite that retargets only the completed-plan links (the three kept-live ones — `plan-31`, `plan-32`, `plan-V1X-roadmap` — must NOT be rewritten). Run:
```bash
cd docs/plans
sed -i.bak -E 's#\(\./(plan-(0[1-9]|10b?|1[1-9]|2[0-9]|30|R[0-9]?|R|RO-[0-9]+)[a-z0-9-]*\.md)#(../archive/completed-plans-v1.0/\1#g' master-plan.md
rm master-plan.md.bak
cd ../..
```

- [ ] **Step 3: Verify the three kept-live links are untouched and the completed ones now point to the archive**

Run: `grep -nE "\(\./plan-(31|32|V1X)" docs/plans/master-plan.md | wc -l`
Expected: `3` (plan-31, plan-32, plan-V1X-roadmap still use `./`).

Run: `grep -c "../archive/completed-plans-v1.0/plan-" docs/plans/master-plan.md`
Expected: `35`.

Run (no completed-plan link still uses the bare `./` form):
```bash
grep -nE "\(\./plan-(0|1[0-9]|2|30|10b|R|RO)" docs/plans/master-plan.md
```
Expected: no output (exit 1).

- [ ] **Step 4: Verify every retargeted link resolves to a real file**

Run:
```bash
grep -oE "\.\./archive/completed-plans-v1\.0/plan-[a-z0-9-]+\.md" docs/plans/master-plan.md \
  | sed 's#\.\./#docs/#' | sort -u | while read p; do test -f "$p" || echo "MISSING: $p"; done
echo "link-check done"
```
Expected: prints only `link-check done` (no `MISSING:` lines).

- [ ] **Step 5: Commit**

```bash
git add docs/plans/master-plan.md
git commit -m "docs(archive): repoint master-plan completed-plan links to archive"
```

---

## Task 6: Repoint the live "historical artifacts" lists (CLAUDE.md + checkpoint skill)

**Files:**
- Modify: `CLAUDE.md` (the "Historical artifacts — NEVER modify" list, ~lines 79–82)
- Modify: `.claude/skills/checkpoint/SKILL.md` (the identical list, ~line 58)

- [ ] **Step 1: Read the current CLAUDE.md list to get exact strings**

Run: `grep -n "plan-R\*\|plan-R2\|external-reviews\|devdocs/\|smoke_tests" CLAUDE.md`
Expected: shows the 3 historical-artifact bullet lines (plan-R*, external-reviews, devdocs/smoke_tests).

- [ ] **Step 2: Update the CLAUDE.md historical-artifacts bullets**

In `CLAUDE.md`, replace the two bullet lines:

Old:
```
- `docs/plans/plan-R*.md`, `docs/plans/plan-R2*.md` — historical at authoring date.
- `docs/external-reviews/*` — historical at review date.
- `devdocs/*`, `smoke_tests/*` — historical per HEAD pin.
```
New:
```
- `docs/archive/completed-plans-v1.0/*` — the shipped v1.0 plan files (Plans 01–30, 10b, R, R2, R3, R4, RO-*); historical at authoring date.
- `docs/external-reviews/*` — historical at review date.
- `docs/archive/pre-claude-devdocs/*`, `docs/archive/smoke_tests/*` — pre-Claude analysis corpus; historical per HEAD pin.
```

- [ ] **Step 3: Update the identical list in the checkpoint skill**

`.claude/skills/checkpoint/SKILL.md` carries the SAME 3-line "Historical artifacts — NEVER modify" block (lines 56–58), including a `plan-R*` line that Task 4 makes dangling. Rewrite all three lines to match the CLAUDE.md edit. Find:
```
- `docs/plans/plan-R*.md`, `docs/plans/plan-R2*.md` and other dated plan detail blocks.
- `docs/external-reviews/*` — historical at review date.
- `devdocs/*`, `smoke_tests/*` — historical per HEAD pin.
```
Replace with:
```
- `docs/archive/completed-plans-v1.0/*` — the shipped v1.0 plan files (Plans 01–30, 10b, R, R2, R3, R4, RO-*); historical at authoring date.
- `docs/external-reviews/*` — historical at review date.
- `docs/archive/pre-claude-devdocs/*`, `docs/archive/smoke_tests/*` — pre-Claude analysis corpus; historical per HEAD pin.
```

- [ ] **Step 4: Verify neither file still names a pre-move path as current**

The verify MUST use `grep -E` (ERE) — a BRE pattern with `\|` alternation here would match literally nothing and give a false pass. The pattern below matches the **bare top-level** paths only (preceded by a backtick, space, or start-of-line) so the legitimate post-edit `docs/archive/pre-claude-devdocs/` and `docs/archive/smoke_tests/` substrings do NOT false-positive, and it also catches the now-moved `plan-R` reference. (This exact pattern was validated to return clean on the post-edit content and to fire on the unfixed content.) Run:
```bash
grep -rEn "(^|[ \`(])(devdocs/|smoke_tests/)|docs/plans/plan-R" CLAUDE.md .claude/skills/checkpoint/SKILL.md
```
Expected: no output (exit 1) — both files now reference `docs/archive/...` only.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md .claude/skills/checkpoint/SKILL.md
git commit -m "docs(archive): repoint historical-artifact path lists to docs/archive/"
```

---

## Task 7: Author the forward plan + Closed-Test Readiness Gate

**Files:**
- Create: `docs/plans/plan-FORWARD.md`

- [ ] **Step 1: Write the forward plan**

Create `docs/plans/plan-FORWARD.md` with this exact content:

```markdown
# Plan FORWARD — Path to Closed Test & Beyond

This is the single entry point for "what's between here and a game good enough to put in front of
closed testers." It supersedes the post-launch sequencing in `plan-V1X-roadmap.md` (now a **backlog
of record** that this plan draws sub-plans from) and is keyed to an explicit quality gate rather than
a tester-count clock.

**Current position:** v1.0 is complete and live on the Play Console **internal** track. We are
deliberately staying in internal testing. Promotion to the **closed** track is gated on the
developer's judgment that the game is good enough — made concrete by the Closed-Test Readiness Gate
below.

See `docs/plans/master-plan.md` for the v1.0 completion record and `docs/agent/STATE.md` for the live
snapshot.

---

## Phases

| Phase | Contains | Exit criterion |
|---|---|---|
| **Phase 1 — Closed-Test Readiness** | Every work item the Readiness Gate demands (below), drawn from unshipped V1X sub-plans + open GitHub issues + open audit Lows. | Gate fully ticked (or items explicitly deferred-not-a-blocker) → developer decides to promote. |
| **Phase 2 — Closed Test** | Promote internal → closed track; recruit ≥12 testers; ≥14-day soak; triage feedback; apply for production access. | Production access granted; staged rollout begins. |
| **Phase 3 — Post-Launch (v1.1+)** | Work that does NOT gate "the game is good." | Ongoing. |

---

## Phase 1 — Closed-Test Readiness Gate

The gate is the developer's judgment, made explicit. Each item is satisfied-by a specific V1X
sub-plan / GitHub issue, or is marked **deferred — not a blocker** with a one-line rationale (never
silently dropped). Promotion to closed test happens when the developer is satisfied with the ticked
state — the checklist informs that call, it does not replace it.

### A. Audio & game feel
*Implementation shipped (V1X-04/05/06, PRs #67/#68/#69; issues #38/#46/#39 CLOSED): synthesized SFX replaced the sine tones, the SHOOT throttle is attack-speed-scaled, and `MusicManager` plays walking/battle tracks with audio focus + a Settings volume slider. The remaining gate question is **in-play feel**, not delivery:*
- [ ] In-play audio assessment: SFX/music actually feel good (not just present) — synthesized SFX may still warrant real recorded assets if they sound cheap — *verify in play; reopen V1X-04 only if the synthesized SFX disappoint*
- [ ] No audio glitches: throttle holds up at RAPID_FIRE L10 + MULTISHOT; music ducks/resumes correctly on interruptions

### B. Content honesty
- [ ] AUTO_UPGRADE_AI resolved (shipped or clearly framed as deferred) — *satisfied-by #44*
- [ ] No misleading "Coming Soon" in core flows; remaining locked cosmetics clearly framed — *satisfied-by known-issues cosmetic debt*

### C. First-session UX
- [ ] A brand-new player understands the walk → spend → battle loop (onboarding / tutorial / first-walk moment) — *satisfied-by #24 (V1X-22)*

### D. Correctness & stability
- [ ] Audit Low #124 (billing signature verification) — fixed or accepted-with-rationale
- [ ] Audit Low #127 (duplicate daily missions) — fixed (needs schema v11→v12 bump) or accepted
- [ ] Audit Low #128 (30-Low tracker) — triaged; blockers fixed, rest logged
- [ ] Clean fresh-install run; no known crashes

### E. Balance & progression feel
- [ ] Early tiers (1–5) feel right; economy neither grindy nor trivial — *manual play assessment*
- [ ] STEP_MULTIPLIER curve feels sane in play (V1X-18 shipped — verify in-feel)

### F. Upgrade decision support
- [ ] Workshop / Cards readability — a player can tell whether an upgrade is worth buying — *satisfied-by #29 (V1X-26)*

### G. Performance & battery
- [ ] Acceptable frame rate on a low-end device at 2×/4× speed
- [ ] Foreground-service + Health Connect polling battery cost is sane — *satisfied-by #26 (V1X-23)*

> Gate maintenance: tick items as they land; when deferring, replace the checkbox line with
> `- [deferred] <item> — <one-line rationale>`. Keep this list honest — it is the promotion decision aid.

---

## Phase 2 — Closed Test

1. Promote the internal build to the closed track in Play Console.
2. Recruit ≥12 testers; distribute the closed-track opt-in URL.
3. Collect ≥14 days of closed-track feedback; triage issues filed against `JonWhiteFang/steps-of-babylon`.
4. Apply for production access (Google review 1–3 days).
5. Promote closed → production with a staged rollout; tag `v1.0.0` in git after rollout reaches 100%.

(Release mechanics — signing, CI release lane, "What's new" automation — are all live; see
`docs/plans/plan-31-play-console.md` and `docs/plans/plan-32-ci.md`.)

---

## Phase 3 — Post-Launch (v1.1+)

Drawn from `plan-V1X-roadmap.md`; sequenced after production rollout. Not gating closed test:

- **Retention / trust:** Snapshots cloud save (V1X-12, #36); i18n phase 2+ (V1X-13, #34).
- **Depth:** long-term meta-progression / prestige (V1X-25, #28).
- **Engineering:** modularisation + architectural constraints (V1X-24, #27); privacy-safe telemetry (V1X-21, #23); balancing/economy simulation tooling (V1X-27, #30); deterministic replay golden files (#25).
- **Cross-cutting:** thematic identity + accessibility + monetisation pass (V1X-28, #31); anti-exploit heuristics expansion (V1X-20, #22).

---

## Relationship to other plans

- `plan-V1X-roadmap.md` — **backlog of record.** Holds per-sub-plan detail; this plan owns phase
  sequencing and pulls items into the phases above.
- `master-plan.md` — the v1.0 completion record (Plans 01–32). Completed plan *files* are archived
  under `docs/archive/completed-plans-v1.0/`.
- `plan-31-play-console.md` / `plan-32-ci.md` — live release-journey + CI references.
```

- [ ] **Step 2: Verify the file exists and its phase headers are present**

Run: `grep -nE "^## (Phase|Phase 1 — Closed-Test Readiness Gate)" docs/plans/plan-FORWARD.md`
Expected: shows the `## Phases`, `## Phase 1 — Closed-Test Readiness Gate`, `## Phase 2 — Closed Test`, `## Phase 3 — Post-Launch (v1.1+)` headers.

- [ ] **Step 3: Commit**

```bash
git add docs/plans/plan-FORWARD.md
git commit -m "docs(plans): author forward plan + Closed-Test Readiness Gate"
```

---

## Task 8: Demote V1X to backlog-of-record + wire pointers into the live spine

**Files:**
- Modify: `docs/plans/plan-V1X-roadmap.md` (add header note)
- Modify: `docs/plans/master-plan.md` (forward-plan pointer + archived-files note)
- Modify: `docs/agent/STATE.md` (references block)
- Modify: `docs/steering/structure.md` ("Development Plans" section)

- [ ] **Step 1: Add the backlog-of-record note to the top of the V1X roadmap**

In `docs/plans/plan-V1X-roadmap.md`, immediately after the H1 title line (`# Roadmap Plan V1X — v1.x Patch Sequence`), insert:
```markdown

> **Role (as of 2026-06-11):** this roadmap is now the **backlog of record** — it holds per-sub-plan
> detail. Phase sequencing toward closed test + launch lives in `docs/plans/plan-FORWARD.md`, which
> pulls sub-plans from here. The "post-v1.0.0 production rollout" sequencing below is superseded by
> plan-FORWARD's gate-keyed phases; treat the sub-plan specs as the live content and the wave ordering
> as historical context.
```

- [ ] **Step 2: Add a forward-plan pointer + archived-files note to master-plan.md**

In `docs/plans/master-plan.md`, immediately after the intro paragraph that ends `Plans should be executed in order.`, insert:
```markdown

> **Forward planning (2026-06-11):** v1.0 is complete; this document is the completion record. The
> path from here (internal testing → closed test → launch) lives in `docs/plans/plan-FORWARD.md`,
> keyed to a Closed-Test Readiness Gate. The completed plan *files* (Plans 01–30, 10b, R, R2, R3, R4,
> RO-*) are archived under `docs/archive/completed-plans-v1.0/` — the links in the index below point
> there. `plan-31`, `plan-32`, and `plan-V1X-roadmap` remain live in `docs/plans/`.
```

- [ ] **Step 3: Update STATE.md references block**

In `docs/agent/STATE.md`, in the `## References` section, find the `**Plans:**` line:
```
- **Plans:** `docs/plans/master-plan.md` (38-entry index + status) · `docs/plans/plan-V1X-roadmap.md` (post-launch).
```
Replace with:
```
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
```

- [ ] **Step 4: Update structure.md "Development Plans" section**

In `docs/steering/structure.md`, find the `## Development Plans` section body:
```
Plans live in `docs/plans/` as `plan-NN-name.md`. The master plan is at `docs/plans/master-plan.md` (38 entries: Plans 01–32 plus 10b/R/R2/R3/R4/V1X). Always check the relevant plan file before implementing a feature.
```
Replace with:
```
Forward planning is in `docs/plans/plan-FORWARD.md` (path to closed test + launch, keyed to the Closed-Test Readiness Gate). `docs/plans/master-plan.md` is the v1.0 completion record (38 entries). `docs/plans/plan-V1X-roadmap.md` is the post-launch backlog of record. The completed v1.0 plan files (Plans 01–30, 10b, R*, RO-*) are archived under `docs/archive/completed-plans-v1.0/`. Live plan files in `docs/plans/`: master-plan, plan-FORWARD, plan-31-play-console, plan-32-ci, plan-V1X-roadmap.
```

- [ ] **Step 5: Repoint the stale moved-path reference inside plan-V1X-roadmap.md (kept-live doc)**

`plan-V1X-roadmap.md` stays live but references a plan file Task 4 archived — a real stale ref the plan must fix here (it is NOT a frozen historical doc). Find (line ~1552):
```
- Plan R4 (predecessor — feedback bundle): `docs/plans/plan-R4-feedback-bundle.md`
```
Replace with:
```
- Plan R4 (predecessor — feedback bundle): `docs/archive/completed-plans-v1.0/plan-R4-feedback-bundle.md`
```

- [ ] **Step 6: Repoint the stale moved-path reference inside plan-32-ci.md (kept-live doc)**

`plan-32-ci.md` stays live and contains an instruction naming a path Task 2 archived — repoint it so the instruction still resolves. Find (line ~135):
```
Touch only docs the change invalidates. **Do NOT edit `devdocs/archaeology/*`, `philosophy.md`, or other historical artifacts** — their "No CI" statements were true at authoring date.
```
Replace with:
```
Touch only docs the change invalidates. **Do NOT edit `docs/archive/pre-claude-devdocs/archaeology/*` or other archived historical artifacts** — their "No CI" statements were true at authoring date.
```

- [ ] **Step 7: Verify the pointers are in place AND no live plan file retains a moved-path ref**

Run: `grep -l "plan-FORWARD.md" docs/plans/master-plan.md docs/agent/STATE.md docs/steering/structure.md docs/plans/plan-V1X-roadmap.md`
Expected: lists all 4 files (the V1X file points to plan-FORWARD via Step 1's note).

Run: `grep -c "backlog of record" docs/plans/plan-V1X-roadmap.md`
Expected: `1` or more.

Run (no LIVE plan file still references a moved path — the bare-path + completed-plan-link forms):
```bash
grep -rnE "docs/plans/plan-(0[1-9]|10b?|1[1-9]|2[0-9]|30|R|RO)|(\`| )devdocs/|(\`| )smoke_tests/" \
  docs/plans/plan-V1X-roadmap.md docs/plans/plan-31-play-console.md docs/plans/plan-32-ci.md docs/plans/plan-FORWARD.md
```
Expected: no output (exit 1). (master-plan.md is excluded here — its archive-path links are intentional and checked in Task 5.)

- [ ] **Step 8: Commit**

```bash
git add docs/plans/plan-V1X-roadmap.md docs/plans/master-plan.md docs/agent/STATE.md docs/steering/structure.md docs/plans/plan-32-ci.md
git commit -m "docs(plans): demote V1X to backlog-of-record; wire plan-FORWARD pointers"
```

---

## Task 9: Repoint remaining live references + comprehensive link-integrity sweep

This task repoints the live references the move leaves stale OUTSIDE the docs already handled (two
source-code KDoc comments + one CI comment), then runs a single repo-wide sweep with an **explicit
frozen-exemption list** so the verification cannot give a false pass by scoping a real break out of
view (the failure mode of a fixed-file-list sweep).

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/UltimateWeaponType.kt` (KDoc comment, line ~11)
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/local/Migrations.kt` (KDoc comment, line ~70)
- Modify: `.github/workflows/ci.yml` (comment, line ~31)

> **Frozen-exemption list (intentionally NOT repointed — historical at authoring date per CLAUDE.md's
> own rule):** `docs/archive/**`, `CHANGELOG.md`, `docs/agent/RUN_LOG.md`, `docs/agent/DECISIONS/ADR-*.md`,
> and the prior `docs/superpowers/specs|plans/2026-06-10-*` (kiro-conversion) files. These contain
> references to the moved paths inside dated entries that record what was true when written; rewriting
> them would falsify history. **Consequence (accepted):** 7 markdown links to `plan-R4-feedback-bundle.md`
> in frozen CHANGELOG entries will 404 — the file itself stays reachable (archived + indexed in the
> completed-plans README), so the impact is a dead in-doc hyperlink in frozen history, not a lost
> document. (The design spec's "nothing becomes unreachable" wording is corrected to reflect this.)

- [ ] **Step 1: Repoint the two live source-code KDoc comments**

In `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/UltimateWeaponType.kt`, the KDoc references the moved plan. Find:
```
 * `docs/plans/plan-R4-feedback-bundle.md` §R4-06 reads directly into the constructor:
```
Replace with:
```
 * `docs/archive/completed-plans-v1.0/plan-R4-feedback-bundle.md` §R4-06 reads directly into the constructor:
```

In `app/src/main/java/com/whitefang/stepsofbabylon/data/local/Migrations.kt`, find:
```
     * (sum 5), matching the example in `docs/plans/plan-R4-feedback-bundle.md`. Rows
```
Replace with:
```
     * (sum 5), matching the example in `docs/archive/completed-plans-v1.0/plan-R4-feedback-bundle.md`. Rows
```

(These are comment-only edits — no behavioural change. The PR is therefore "no production code *behaviour* change," with two KDoc path-fixes.)

- [ ] **Step 2: Repoint the CI comment**

In `.github/workflows/ci.yml`, find:
```
      # The documented one-call gate (smoke_tests/.../README.md): lint + unit + debug build.
```
Replace with:
```
      # The documented one-call gate (docs/archive/smoke_tests/.../README.md): lint + unit + debug build.
```

- [ ] **Step 3: Comprehensive repo-wide sweep for moved-path references in LIVE files**

This greps the WHOLE repo for any bare/backtick reference to a moved path, then subtracts the
frozen-exemption set and the (correct) `docs/archive/` hits. Anything left is a real stale ref in a
live file. Run:
```bash
grep -rnE "(^|[ \`(])(devdocs/|smoke_tests/)|docs/plans/plan-(0[1-9]|10b?|1[1-9]|2[0-9]|30|R|RO)" . 2>/dev/null \
  | grep -vE "/\.git/|node_modules" \
  | grep -vE "(^|/)docs/archive/" \
  | grep -vE "(^|/)CHANGELOG\.md:" \
  | grep -vE "(^|/)docs/agent/RUN_LOG\.md:" \
  | grep -vE "(^|/)docs/agent/DECISIONS/ADR-" \
  | grep -vE "(^|/)docs/superpowers/(specs|plans)/2026-06-1[01]-" \
  | grep -vE "docs/plans/plan-(31|32|V1X)" \
  | grep -vE "(^|/)docs/plans/master-plan\.md:.*archive/completed-plans-v1\.0"
echo "comprehensive sweep done"
```
Expected: only `comprehensive sweep done`. Any other line is a live stale reference that must be repointed in Step 5. (The `2026-06-1[01]-` exemption covers both the frozen kiro-conversion docs AND this plan/spec, which legitimately describe the move using the old paths.)

- [ ] **Step 4: Confirm the five kept-live plan files are the only `.md` in docs/plans/**

Run: `ls docs/plans/*.md`
Expected exactly: `master-plan.md  plan-31-play-console.md  plan-32-ci.md  plan-FORWARD.md  plan-V1X-roadmap.md`

- [ ] **Step 5: If Step 3 printed any line beyond the "done" marker, repoint it**

For each stray, repoint to the correct `docs/archive/...` path. If it is genuinely inside a
frozen-exemption file that the grep filters missed, widen the exemption filter consciously (and note
why). Re-run Step 3 until it prints only the marker. If nothing was found, skip.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/model/UltimateWeaponType.kt \
  app/src/main/java/com/whitefang/stepsofbabylon/data/local/Migrations.kt \
  .github/workflows/ci.yml
git commit -m "docs(archive): repoint live source/CI comments to archived plan path"
```
(If Step 5 found and repointed any additional stray, include those files in this commit.)

---

## Task 10: Doc-sync (CHANGELOG + RUN_LOG) per the checkpoint protocol

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Add a CHANGELOG section under `## [Unreleased]`**

In `CHANGELOG.md`, immediately after the `## [Unreleased]` line, insert a new section:
```markdown

### Docs — planning reset: archive pre-Claude work + forward plan with Closed-Test Readiness Gate (2026-06-11)

- Docs-only; no production code / test / schema change (908 JVM + 9 instrumented unchanged).
- **Archived** the pre-Claude planning corpus and the shipped-v1.0 plan files into a frozen
  `docs/archive/` tree (history-preserving `git mv`): `devdocs/` → `docs/archive/pre-claude-devdocs/`,
  `smoke_tests/` → `docs/archive/smoke_tests/`, and 38 completed plan files (Plans 01–30, 10b, R, R2,
  R3, R4, RO-09/11/12) → `docs/archive/completed-plans-v1.0/`. Each archive area has a README index.
- **New forward plan** `docs/plans/plan-FORWARD.md` — three phases keyed to a **Closed-Test Readiness
  Gate** (the explicit quality checklist that decides when to promote internal → closed test, since
  promotion is judgment-gated, not tester-count-gated). `plan-V1X-roadmap.md` demoted to the backlog of
  record it draws from.
- **Repointed** the live cross-reference surface: CLAUDE.md + the checkpoint skill's historical-artifact
  lists, master-plan's 35 internal completed-plan links, and the STATE / structure forward pointers.
  Frozen historical docs (RUN_LOG/CHANGELOG past entries, ADRs, prior specs) left untouched.
```

- [ ] **Step 2: Add a RUN_LOG entry at the top**

In `docs/agent/RUN_LOG.md`, insert at the very top (above the current first `## ` entry):
```markdown
## 2026-06-11 — Planning reset: archive pre-Claude work + forward plan with Closed-Test Readiness Gate (branch docs/planning-reset)

- **Goal:** user asked to redo project planning — archive the pre-Claude planning work and map the way
  forward, reframed around staying in internal testing until the game is judged good enough for closed
  test (a quality gate, not the tester-count clock the old plan assumed). Brainstormed → spec
  (`docs/superpowers/specs/2026-06-11-planning-reset-design.md`) → this plan.
- **Archived (git mv, history preserved):** `devdocs/` → `docs/archive/pre-claude-devdocs/`;
  `smoke_tests/` → `docs/archive/smoke_tests/`; 38 shipped plan files → `docs/archive/completed-plans-v1.0/`.
  Top-level + completed-plans README indexes written. `master-plan`, `plan-31`, `plan-32`,
  `plan-V1X-roadmap` stay live in `docs/plans/`.
- **New `docs/plans/plan-FORWARD.md`:** Phase 1 Closed-Test Readiness (the A–G quality gate), Phase 2
  Closed Test, Phase 3 Post-Launch. V1X roadmap demoted to backlog of record (header note added).
- **Repointed cross-refs:** CLAUDE.md + checkpoint-skill historical-artifact lists → `docs/archive/**`;
  master-plan's 35 completed-plan links → `../archive/completed-plans-v1.0/`; STATE + structure forward
  pointers → plan-FORWARD. Final grep sweep confirmed no live doc links a pre-move path.
- **Verification:** doc-only — link-integrity sweeps (Task 9) clean; `git log --follow` confirmed
  history preserved across the moves. No build run (no code/test/schema touched).
- **Doc sync:** CHANGELOG new Docs section + this entry. No ADR (planning-process reorganization, not an
  architecture decision; the design spec captures the rationale).
- **Next:** open a PR for the branch (doc-only). Then Phase 1 work begins against the Readiness Gate —
  first candidate pickups: audit Lows (#124/#127/#128), the first-session UX gap (#24), and an in-play
  audio/balance/perf assessment (the audio code already shipped via V1X-04/05/06 — what remains is
  judging whether it feels good).
```

- [ ] **Step 3: Verify both files updated**

Run: `grep -c "planning reset\|Planning reset\|Closed-Test Readiness" CHANGELOG.md docs/agent/RUN_LOG.md`
Expected: each file ≥ 1.

- [ ] **Step 4: Commit**

```bash
git add CHANGELOG.md docs/agent/RUN_LOG.md
git commit -m "docs: changelog + run-log for the planning reset"
```

---

## Task 11: Open the PR

- [ ] **Step 1: Push the branch**

Run: `git push -u origin docs/planning-reset`
Expected: branch pushed; PR-create hint printed.

- [ ] **Step 2: Open the PR**

Run:
```bash
gh pr create --base main --head docs/planning-reset \
  --title "docs: planning reset — archive pre-Claude work + forward plan with Closed-Test Readiness Gate" \
  --body "Doc-only. Archives the pre-Claude planning corpus (devdocs/, smoke_tests/) and the 38 shipped v1.0 plan files into a frozen docs/archive/ tree (history-preserving git mv), and adds docs/plans/plan-FORWARD.md — a three-phase forward plan keyed to a Closed-Test Readiness Gate (the explicit quality checklist that gates promotion internal → closed test). plan-V1X-roadmap demoted to backlog of record. Live cross-refs (CLAUDE.md, checkpoint skill, master-plan's 35 internal links, STATE/structure pointers) repointed; frozen historical docs untouched. No production code / test / schema change. Design spec: docs/superpowers/specs/2026-06-11-planning-reset-design.md."
```
Expected: prints the PR URL.

- [ ] **Step 3: Report the PR URL to the user**

This is a doc-only branch with no required CI code gates beyond what runs on every PR; the merge decision is the user's (the doc-sync-to-main precedent and the planning-reset nature mean this can also be discussed before merge).

---

## Self-Review

**Spec coverage** (checked against `docs/superpowers/specs/2026-06-11-planning-reset-design.md`):
- §1 Archive structure → Tasks 1–4 (scaffold + 3 moves + 2 READMEs). ✓
- §2 Forward plan (3 phases) → Task 7. ✓
- §3 Readiness Gate (A–G) → Task 7 (gate embedded in plan-FORWARD; Category A reframed as in-play assessment since the audio code shipped). ✓
- §4 Mechanics (repoint refs, master-plan/STATE/structure updates, V1X note) → Tasks 5, 6, 8, 9. ✓
- Testing/validation (repo-wide sweep with frozen-exemption list, git-history check) → Task 2 step 3 + Task 9. ✓
- `plan-31` kept live (locked decision #5) → Task 4 step 2 asserts it stays. ✓

**Placeholder scan:** No TBD/TODO; every doc body is given in full; every command has exact expected output. ✓

**Type/name consistency:** Path names consistent throughout — `docs/archive/pre-claude-devdocs/`, `docs/archive/smoke_tests/`, `docs/archive/completed-plans-v1.0/`, `docs/plans/plan-FORWARD.md`. The 38-file count is consistent (Problem, Task 4 step 2, README, CHANGELOG). The "35 internal master-plan links" figure is consistent (Task 5).

**Adversarial-review fixes applied (2026-06-11).** A multi-agent adversarial review of this plan (28 findings raised, 10 confirmed) surfaced issues since fixed in-line, each re-verified against the live repo:
- **BLOCKER** — Task 4 `git mv` would abort (exit 128) because the leaf dir didn't exist. Fixed: `mkdir -p docs/archive/completed-plans-v1.0` added as the first action of Task 4 Step 1. (Reproduced in a scratch git repo: fails without the mkdir, succeeds with it.)
- **MAJOR** — live kept-files referenced moved paths the "only 3 files" scope claim missed: `plan-V1X-roadmap.md:1552` + `plan-32-ci.md:135` (now repointed in Task 8) and, found in a deeper scan, two source-code KDoc comments + `ci.yml` (now repointed in Task 9). The scope note + spec risks section were corrected.
- **MINOR** — Task 6 Step 4 verify grep was a guaranteed false-pass (BRE alternation matched nothing). Replaced with a validated ERE pattern `(^|[ \`(])(devdocs/|smoke_tests/)|docs/plans/plan-R` (tested: clean exit on fixed content, fires on unfixed; does NOT false-positive on the `pre-claude-devdocs/`/`archive/smoke_tests/` substrings).
- **MINOR** — the checkpoint skill's parallel `plan-R*` line was left dangling. Task 6 Step 3 now rewrites all three SKILL.md lines to match the CLAUDE.md edit.
- **MINOR** — Gate Category A presented shipped audio (V1X-04/05/06, issues CLOSED) as unchecked blockers. Reframed as an in-play feel assessment in both plan and spec; the RUN_LOG "next pickups" text corrected.
- **MINOR (accepted)** — 7 `plan-R4` links in frozen CHANGELOG entries 404 by design (editing frozen history is disallowed; the file stays reachable + indexed). Documented in the scope note, spec risks, and Task 9's frozen-exemption block rather than "fixed."

**Validated mechanics:** the Task 5 sed regex and the Task 6/9 verify greps were each dry-run against real repo content (or scratch copies) during the review — 35 links retarget, 3 kept-live untouched, 0 left on bare `./`, every produced path resolves; the Task 9 repo-wide sweep with the frozen-exemption list was confirmed to surface exactly the live refs the plan repoints and nothing from the frozen set.
