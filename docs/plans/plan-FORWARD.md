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
- [x] A brand-new player understands the walk → spend → battle loop (onboarding / tutorial / first-walk moment) — *satisfied-by #24 (V1X-22), Gate-C slice shipped in v1.0.3 (PR #157): first-launch 4-slide carousel + permission primer + Settings replay; explain-only (no Steps grant). #24 stays open for the deferred retention scope.*

### D. Correctness & stability
- [x] Audit Low #124 (billing signature verification) — fixed (PR #148, ADR-0005 amendment)
- [x] Audit Low #127 (duplicate daily missions) — fixed (PR #152, schema v11→v12, `MIGRATION_11_12`, `(date, missionType)` unique index)
- [ ] Audit Low #128 (30-Low tracker) — triaged; blockers fixed, rest logged
- [x] Bug #146 (enemy counter drifts negative mid/late run) — fixed (PR #151): derived
  `GameEngine.aliveEnemyCount()` from the live `EnemyEntity` list (immune to both causes — untracked
  SCATTER children + `onDeath` re-fire on corpses — and any future bypass) plus the `EnemyEntity.takeDamage`
  guard `if (!isAlive) return 0.0` (also fixes the per-kill cash + battle-Steps double-credit). No schema.
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
5. Promote closed → production with a staged rollout; cut the production release tag after rollout reaches 100%. (Note: `v1.0.0` was never tagged — the versionName advanced past 1.0.0 to avoid a versionCode collision, and several `v1.0.x` tags already exist — see CHANGELOG; the production tag will be whatever versionName ships at GA.)

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
