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
| **Phase 1 — Closed-Test Readiness** | Every work item the Readiness Gate demands (below), drawn from unshipped V1X sub-plans + open GitHub issues + open audit Lows + the 2026-06-17 complete-app review's promotion blockers (Gate H / #190–#192). | Gate fully ticked (or items explicitly deferred-not-a-blocker) → developer decides to promote. |
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
- [x] AUTO_UPGRADE_AI resolved — *satisfied-by #44 (MERGED, PR #186 `952a8bf`): the type has been hidden from Labs since V1X-15 (`surfacedInLabs()` filter); #44 made the code match reality (deleted dead UI branches) + added a regression guard. Implementation deferred to v1.x; nothing half-built is shown.*
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
- [x] Clean fresh-install run; no known crashes — *verified on-device 2026-06-17 (Pixel_6/API36, fresh
  install of HEAD `952a8bf`): full core loop (onboarding → 3 permission flows → Home → Workshop → Battle
  @4× → Round Over → reward persistence → Settings) with zero crashes/ANRs/FATALs. The pass surfaced a
  layout defect (#187, Settings didn't scroll → "Replay tutorial"/"Delete All Data" unreachable), now fixed
  + MERGED (PR #188, `af30e96`; #187 closed) + on-device re-verified.*
- [x] **Audit-H (2026-06-17 complete-app review) — promotion blockers, see §H below.** A full
  code-grounded, adversarially-verified audit (`docs/reviews/2026-06-17-complete-app-review.md`) surfaced three
  **promotion blockers** beyond the fresh-install pass: no crash visibility + unguarded game-loop thread
  (#190), two reachable battle crashes (#191), privacy-policy/Data-Safety accuracy (#192). These reopen the
  "no known crashes" item in spirit — the fresh-install pass found no *observed* crash, but the audit found
  *reachable* ones the manual pass couldn't trigger. **All three §H code blockers (#190/#191/#192) and the
  three soak majors (#193/#194/#195) are now MERGED and shipped in v1.0.10 (versionCode 26)** — the only
  residual §H item is the manual Play Console Data-Safety form for #192 (external, pre-promotion; see §H ticks).

### E. Balance & progression feel
- [ ] Early tiers (1–5) feel right; economy neither grindy nor trivial — *manual play assessment*
- [ ] STEP_MULTIPLIER curve feels sane in play (V1X-18 shipped — verify in-feel)

### F. Upgrade decision support
- [ ] Workshop / Cards readability — a player can tell whether an upgrade is worth buying — *satisfied-by #29 (V1X-26)*

### G. Performance & battery
- [ ] Acceptable frame rate on a low-end device at 2×/4× speed — *in-repo contribution shipped by #26:
  the `:macrobenchmark` `FrameTimingMetric` journey benchmark (jank visibility, run locally) + the A28
  (per-frame collision list allocs) and A31 (per-frame CHRONO_FIELD Paint alloc) GC-churn fixes. The
  low-end-device 2×/4× frame-rate verdict itself is a device-only manual pass, not closed in-repo.*
- [deferred] Foreground-service + Health Connect polling battery cost is sane — physical-device,
  developer-judgment slice (overnight idle-drain + OEM matrix Samsung/Xiaomi/OnePlus/Pixel). #26 shipped
  the in-repo half: Baseline Profile (generated + committed, 18,804 rules) + the `:macrobenchmark`
  harness + the battery-audit doc (`docs/performance/battery-audit.md`) with candidate cadence tunings
  (notification 30→60 s, HC sync 15→30 min) specced as hypotheses but **not applied** (each needs a
  device measurement first). Cannot close from the repo alone. (Startup-timing *numbers* are likewise
  deferred to this pass — needs a non-debuggable `benchmark` build type + a physical device; see
  `docs/performance/startup-baseline.md`.) *satisfied-in-repo-by #26 (V1X-23); device half deferred.*

### H. Complete-app review (2026-06-17) — closed-track promotion blockers + soak hardening

Source: `docs/reviews/2026-06-17-complete-app-review.md` (51-agent code-grounded audit; every material finding
adversarially verified). The 2026-06-17 fresh-install pass (Gate D) confirmed no *observed* crash, but a
soak puts ≥12 testers × ≥14 days against the build — exactly the regime these *reachable* defects (which a
manual pass can't trigger) bite. Split into **promotion blockers** (must fix before promoting internal →
closed: gathering soak signal or exposing testers is the whole point of closed test) and **soak hardening**
(fix before/during the soak; not a hard promotion gate but degrades tester experience or feedback quality).

**Promotion blockers (`severity:blocker` — gate internal → closed):**
- [x] **#190 — crash visibility (REL-1/REL-2).** No global uncaught-exception handler + no crash reporting,
  and `GameLoopThread.run()` wraps `update()/render()` in no `try/catch` → a sim exception is silent
  process death. *We would soak blind.* Fix: per-tick `try/catch` (skip-frame / surface error state) + a
  `setDefaultUncaughtExceptionHandler` breadcrumb. Effort **S**. **MERGED** (PR #204, `d673386`; ADR-0026; shipped v1.0.10).
- [x] **#191 — two reachable battle crashes (CONC-1/CONC-2).** `EffectEngine` effect lists mutated
  cross-thread on every boss kill / step reward (High); `uwStates` mutated off-loop-thread on replay
  (Medium). Same class as #118, on lists the `entitiesLock` sweep missed. Fix: mirror the `entitiesLock`
  monitor pattern. Effort **S**. **MERGED** (PR #204, `d673386`; ADR-0026; shipped v1.0.10).
- [x] **#192 — privacy-policy / Data-Safety accuracy (PRIV-1/SEC-1).** Policy says data "never uploaded" /
  AdMob is a "future" integration, but the live build ships AdMob + UMP + collects the advertising ID.
  Play-compliance accuracy on the path to the production-access application the soak feeds. Fix: present-tense
  policy rewrite (`HealthConnectPermissionActivity.kt` + `site/index.md`) + ad-ID
  disclosure + reconcile the Console Data-Safety form (Console step **needs external verification**). Effort
  **S** + Console. **In-repo code MERGED** (PR #205, `0019217`; shipped v1.0.10); **residual = the manual Play
  Console Data-Safety form** per `docs/release/data-safety-form.md` (external, pre-promotion — not a repo change).

**Soak hardening (`severity:major` — fix before/during soak):**
- [x] **#193 — no-sensor silent dead-end (REL-3).** A device without `TYPE_STEP_COUNTER` accrues zero Steps
  while the foreground notification implies it works. Detect at first-launch → message + Health Connect
  steer. Effort **M**. **MERGED** (PR #270, `ebf588a`; shipped v1.0.10).
- [x] **#194 — no error states anywhere (UX-1).** A failed data load spins forever (no error field on any
  `UiState`; `isLoading` cleared only on `combine` success). Add a shared `ScreenStateHost(isLoading, error,
  retry)` + `.catch` on each `combine`. Effort **M**. **MERGED** (PR #272, `1811617`; ADR-0028; shipped v1.0.10).
- [x] **#195 — Missions day-rollover stale query (STATE-1).** `MissionsViewModel` never re-subscribes
  `getByDate(today)` at midnight (the only date-screen missing the `flatMapLatest` pattern Home/Stats use).
  Effort **S**. **MERGED** (PR #270, `ebf588a`; shipped v1.0.10).

> Lower-severity audit findings (architecture seam, A11Y contrast, no-Compose-UI-tests, Gradle-wrapper
> validation, clock-tamper TIME-1, i18n, etc.) are **before-public / post-launch**, not closed-track
> blockers — see `docs/reviews/2026-06-17-complete-app-review.md` §18 Tiers 2–5. Audit verdict: **continue building** —
> do the blocker pass, then resume features.

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
