# Project State

## Current objective
- Phase A (Foundation) of the Phase-14 implementation roadmap is complete — 9 tactical PRs landed on main. Low-risk cleanup done; core debt remaining for Phase B, release blockers remaining for Phase C/D.
- Plan 31: Play Console & Store Publication — still the only release-blocker; Phase C.5/C.6 (real Billing/Ad SDKs) are its prerequisites.

## What works
- Plans 01–30 + 10b + R (R01–R12) + R2 (R2-01–R2-12) complete.
- Battle Step Rewards (ADR-0003): per-enemy flat reward, 2k/day cap, partial credit, capped-kill FloatingText suppression (A.7).
- DB version 8: 12 entities, first explicit Room Migration (v7→v8) registered. DB-file wipe recovery on decrypt failure (A.3).
- Phase A foundation: junit-vintage-engine on classpath recovering 9 previously-hidden Robolectric tests (A.2); Season Pass bonus now paid from background ingestion (A.6); `Screen.fromRoute` + whitelist covers all 12 deep-link routes (A.5); `FakeBillingManager`/`FakeRewardAdManager` scriptable via `resultQueue` (A.4); dead `PlaceholderScreen` + `SupplyDropTrigger.STEP_BURST` removed (A.8, A.9); docs synced to schema v8 + 453-test state (A.1).
- Phase B.1 foundation: `TimeProvider` abstraction landed with 3 narrow-migration sites (`AwardBattleSteps`, `BattleViewModel`, `MissionsViewModel`), `FakeTimeProvider` test double, and 2 midnight-boundary tests that were previously impossible against the real clock. ADR-0004 stub for FollowOnPipeline recorded.
- **455 JVM tests** green (+43 vs pre-Phase-A 412 baseline).

## Known issues / debt
- Billing/ads still use stub implementations — real SDK integration pending Phase C.5/C.6.
- Cosmetic visual application not implemented (purchases disabled via R2-11 guard).
- Sound assets are placeholder sine wave tones.
- No app icon resources.
- Phase B core refactors (@Transaction for 5 multi-write sites, resilient endRound, FollowOnPipeline extraction, UpdateMissionProgress use case) are debt, not blockers. B.1 TimeProvider landed.

## Top priorities (next 5)
1. Phase B.2 PR 1 — `@Transaction` pattern proven on PurchaseUpgrade (RO-02). Pattern-proving PR for the other four multi-write sites.
2. Phase B.3 — Resilient `BattleViewModel.endRound` (RO-03). Composes with B.2 PR 5.
3. Phase C.2 — Cosmetic rendering pipeline PRs 1–2 (ship one cosmetic end-to-end). On the release critical path.
4. Phase C.5 — Real Billing SDK swap (requires ADR-0005 first).
5. Phase C.6 — Real Ad SDK swap (requires ADR-0006 first).

## Next actions (explicit order)
1. B.2 PR 1 — add an atomic @Transaction DAO method for PurchaseUpgrade (cost-check + deduct + increment in one call). Proves the pattern.
2. B.3 PR 1 — extract `runEndRoundPersistence` from BattleViewModel and wrap each of the 3 writes in `runCatching { }.onFailure { Log.w }`. Composes with B.2 PR 5.
3. B.2 PRs 2–4 (AwardBattleSteps, StepCrossValidator, ClaimMilestone @Transaction) — parallelisable once pattern lands.
4. Open ADR-0005 (Billing SDK) and ADR-0006 (Ad SDK) stubs, then land C.5 + C.6.
5. C.2 cosmetic pipeline can land anywhere after B.1 — pick first cosmetic (gap_analysis §5.2 proposes jade-ziggurat recolour).
6. Finish with Phase D (Plan 31 Play Console setup, AAB upload, Firebase pre-launch).

## Do-not-touch / fragile zones
- `domain/model/` — stable, all constants validated by balance tests.
- `domain/usecase/` — all 32 use cases stable.
- Balance constants in UpgradeType, TierConfig, EnemyScaler, EnemyType — validated by 39 regression tests.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions.
- `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config, version 1.0.0.
- `Screen.items by lazy` + new `argumentFreeRoutes by lazy` — both guard against sealed-class init-order NPE (commit 1872af9).

## References
- ADR-0003 (Battle Step Rewards): docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md
- Remediation plan (1st review): docs/plans/plan-R-remediation.md
- Remediation plan (2nd review): docs/plans/plan-R2-remediation.md
- External review (1st): docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX.md
- External review (2nd): docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX_2.md
- Master plan: docs/plans/master-plan.md
- Balance report: docs/balance/balance-report.md
- Release docs: docs/release/
- Code archaeology (Phase 1, user overview): devdocs/archaeology/small_summary.md
- Code archaeology (Phase 2, architecture + deployment): devdocs/archaeology/intro2codebase.md, intro2deployment.md
- Code archaeology (Phase 3, per-boundary traces): devdocs/archaeology/traces/ (13 traces + README)
- Code archaeology (Phase 4, 5-things improvement list): devdocs/archaeology/5_things_or_not.md
- Code archaeology (Phase 5, concept inventory): devdocs/archaeology/concepts/ (technical, design, business, missing)
- Code archaeology (Phase 6, foundations): devdocs/archaeology/foundations/ (project_description, philosophy, known_requirements)
- Doc-inferred foundations (Phase 7): devdocs/foundations/ (project_description, philosophy, known_requirements) — built from docs only, pairs with Phase 6
- Code archaeology (Phase 8, reconstruction): devdocs/archaeology/architecture_analysis.md + module_discovery.md — architectural critique + module-boundary analysis from code
- Code archaeology (Phase 9, concept mappings): devdocs/archaeology/concept_mappings.md — 25-concept map with coverage %, divergence rationale, alternatives, edge cases, tests/config pointers, risks; plus cross-concept risk appendix + coverage roll-up
- Evolution (Phase 10, gap analysis): devdocs/evolution/gap_analysis.md — compares current state to desired state; separates known/inferred gaps, marks release blockers vs incremental improvements; argues no rewrite needed; names cosmetic rendering pipeline as the one structural refactor blocking a shipped-but-disabled feature
- Evolution (Phase 11, gap closure plan): devdocs/evolution/gap_closure_plan.md — phased execution plan (Q1–Q8 quick wins, I1–I7 incremental subsystem work, M1–M4 + MR1 major refactor, §4 rewrites rejected with revisit triggers, §5 explicit non-goals, §6 critical path)
- Smoke tests (Phase 12, baseline): smoke_tests/check_what_is_working/ — README (strategy/commands/prerequisites), test_plan.md (5 areas × 5 cases mapped to existing tests), report.md (live run results)
- Codebase cleanup inventory (Phase 13): devdocs/archaeology/cleanup_inventory.md — removal/consolidation/quarantine candidates; Dynamic-risk register §F pins classes invisible to grep
- Evolution (Phase 14, Part 1): devdocs/evolution/refactoring_opportunities.md — top-10 highest-ROI refactors (RO-01..RO-10) with current pattern, proposed abstraction, benefits, effort, risk+mitigation, ROI, first safe step, verification, rollback, non-goals
- Evolution (Phase 14, Part 2): devdocs/evolution/implementation_roadmap.md — phased plan (A Foundation, B Core Refactoring, C Gap Filling, D Integration & Polish); each item has files / dependencies / success criteria / risk / verification / PR size / rollback / owner role
- Critical path: 01→…→30→R→R2→ Battle Step Rewards → **Phase A done** → Phase B/C/D → 31
- Last run: 2026-05-07 (Phase A Foundation + Phase B.1 TimeProvider — 10 Phase A commits pushed, 3 Phase B.1 commits + ADR-0004 stub pushed; test count 412 → 455, all green; Phase B.2 onwards pending)
