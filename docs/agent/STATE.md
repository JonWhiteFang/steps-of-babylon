# Project State

## Current objective
- **RO-02 is COMPLETE.** Phase B.2 PR 5 (site #5) landed — `runEndRoundPersistence` in `BattleViewModel` now runs all 5 SQLite writes inside a single `AppDatabase.withTransaction { }`, with the milestone notification and UI push deliberately moved outside the transaction. All 5 RO-02 atomic sites done (3 DAO-level `@Transaction` + 2 repo-level `withTransaction`).
- Plan 31: Play Console & Store Publication — still the only release-blocker; Phase C.5/C.6 (real Billing/Ad SDKs) are its prerequisites.

## What works
- Plans 01–30 + 10b + R (R01–R12) + R2 (R2-01–R2-12) complete.
- Battle Step Rewards (ADR-0003): per-enemy flat reward, 2k/day cap, partial credit, capped-kill FloatingText suppression (A.7).
- DB version 8: 12 entities, first explicit Room Migration (v7→v8) registered. DB-file wipe recovery on decrypt failure (A.3).
- Phase A foundation: junit-vintage-engine on classpath recovering 9 previously-hidden Robolectric tests (A.2); Season Pass bonus now paid from background ingestion (A.6); `Screen.fromRoute` + whitelist covers all 12 deep-link routes (A.5); `FakeBillingManager`/`FakeRewardAdManager` scriptable via `resultQueue` (A.4); dead `PlaceholderScreen` + `SupplyDropTrigger.STEP_BURST` removed (A.8, A.9); docs synced to schema v8 + 453-test state (A.1).
- Phase B.1 foundation: `TimeProvider` abstraction landed with 3 narrow-migration sites (`AwardBattleSteps`, `BattleViewModel`, `MissionsViewModel`), `FakeTimeProvider` test double, and 2 midnight-boundary tests that were previously impossible against the real clock. ADR-0004 stub for FollowOnPipeline recorded.
- Phase B.2 PR 1 (RO-02 pattern-proving) landed: atomic `@Transaction` DAO method for workshop purchases. `PlayerProfileDao.adjustStepBalanceIfSufficient` (SQL-guarded deduct, `WHERE balance >= :cost`) + `WorkshopDao.purchaseUpgradeAtomic` (default `@Transaction` method, takes `PlayerProfileDao` as param). `PurchaseUpgrade` use case dropped its `PlayerRepository` dep and delegates to the atomic path. Closes the partial-failure window between `spendSteps` and `setUpgradeLevel`, and the double-tap race where two concurrent purchases could both see the same balance. First `@Transaction` marker in `app/src/main`.
- Phase B.3 PR 1 (RO-03 pattern-proving) landed: `BattleViewModel.endRound` extracted to `runEndRoundPersistence` with every write / notification wrapped in `runCatching { }.onFailure { Log.w }`. Writes 1–3 (updateBestWave / awardWaveMilestone / updateHighestUnlockedTier) use `.getOrNull()` / `.getOrDefault(0)` fallbacks so the `_uiState.update` push always runs; writes 4–5 (incrementBattleStats / dailyMissionDao progress) moved from ad-hoc try/catch swallows to `runCatching + Log.w` for consistency. `quitRound()` + polling-loop call site unchanged. `onCleared` mid-nav round-loss fix deferred to PR 2 per spec. `FakePlayerRepository` opened up to allow per-method throwing overrides for failure-isolation tests.
- Phase B.2 PR 2 (RO-02 site #1) landed: atomic `@Transaction` DAO method for battle-step rewards. `DailyStepDao.creditBattleStepsAtomic` wraps cap check + `incrementBattleSteps` + `playerProfileDao.adjustStepBalance` in one SQLite transaction. `AwardBattleSteps` dropped its `PlayerRepository` dep; body shrank to a single delegation. `BattleViewModel` gained a Hilt-injected `PlayerProfileDao`. Closes the partial-failure window between the two writes and the concurrent-kill race where two kills with 1 headroom could double-credit the wallet.
- Phase B.2 PR 3 (RO-02 site #2) landed: `StepCrossValidator`'s 5 multi-write branches (Level 3/2 cap-excess, Level 1/0 first-escrow, reconciliation release) now commit inside `AppDatabase.withTransaction { }`. Different idiom from PRs 1–2 (repo-level not DAO-level) because the validator lives in `data/healthconnect/` and needs parallel transaction scopes; RO-02 explicitly licenses the cross-layer `AppDatabase` import here. Introduced `@VisibleForTesting internal var runInTransaction` seam so existing Mockito-based tests keep working without a real Room DB. SharedPreferences anti-cheat writes (`recordCvOffense`, `decayCvOffenses`) deliberately stay outside the transaction (not SQLite-backed).
- Phase B.2 PR 4 (RO-02 site #4) landed: atomic `@Transaction` DAO method for milestone claims. `MilestoneDao.claimMilestoneAtomic` wraps the already-claimed check + `upsert(... claimed = true)` + wallet credits (`playerProfileDao.adjustGems` + `incrementGemsEarned`; equivalent for Power Stones) in one SQLite transaction. `ClaimMilestone` use case still reads `totalStepsEarned` through `PlayerRepository` (monotonic read, safe outside the tx) but drops the reward-iteration credit loop and delegates to the atomic DAO method. `MissionsViewModel` gained a Hilt-injected `PlayerProfileDao`. Closes the partial-failure window between the reward credits and the mark-claimed write (crash between them would enable double-credit on retry) and the double-claim race (two concurrent clicks could both see `claimed = false` and both credit the reward).
- Phase B.2 PR 5 (RO-02 site #5, FINAL) landed: `BattleViewModel.runEndRoundPersistence` now commits its 5 SQLite writes inside a single `AppDatabase.withTransaction { }` block. Constructor grew to 12 params (added `AppDatabase`). Introduced `@VisibleForTesting internal var runInTransaction` seam matching `StepCrossValidator`'s B.2 PR 3 idiom so existing Mockito-based tests keep working without a real Room DB. Non-SQLite side effects (milestone notification, `_uiState.update`) deliberately moved to *after* the tx so the DB lock isn't held across Android system calls or UI pushes. Outer `runCatching { runInTransaction { ... } }` preserves RO-03 resilience — Room infrastructure failures (disk full, SQLCipher decrypt failure) still let the post-round overlay appear. **RO-02 family complete: 5/5 sites landed.**
- **470 JVM tests** green (+58 vs pre-Phase-A 412 baseline; +15 vs pre-B.2 455 baseline).

## Known issues / debt
- Billing/ads still use stub implementations — real SDK integration pending Phase C.5/C.6.
- Cosmetic visual application not implemented (purchases disabled via R2-11 guard).
- Sound assets are placeholder sine wave tones.
- No app icon resources.
- Phase B core refactors (@Transaction for 5 multi-write sites, resilient endRound, FollowOnPipeline extraction, UpdateMissionProgress use case) are debt, not blockers. B.1 TimeProvider landed. **B.2 PRs 1–5 all landed — RO-02 complete.** B.3 PR 1 landed; B.3 PR 2 (`onCleared` guard) remains.

## Top priorities (next 5)
1. Phase B.3 PR 2 — `onCleared` guard using `ProcessLifecycleOwner.lifecycleScope` so mid-battle deep-link navigation no longer silently discards round progress. Closes the RO-03 family.
2. Phase C.2 — Cosmetic rendering pipeline PRs 1–2 (ship one cosmetic end-to-end). Release critical path.
3. Phase C.5 + C.6 — Real Billing SDK and Ad SDK swaps (each gated on its ADR stub).
4. Phase B.4 — FollowOnPipeline extraction from `DailyStepManager` (debt, not blocker).
5. Phase D — Plan 31 Play Console setup, AAB upload, Firebase pre-launch.

## Next actions (explicit order)
1. B.3 PR 2 — `onCleared` guard so mid-nav deep-links no longer silently lose round progress. Uses `ProcessLifecycleOwner.lifecycleScope` to outlive VM cleanup. Closes RO-03.
2. Open ADR-0005 (Billing SDK) and ADR-0006 (Ad SDK) stubs, then land C.5 + C.6.
3. C.2 cosmetic pipeline can land anywhere after B.1 — pick first cosmetic (gap_analysis §5.2 proposes jade-ziggurat recolour).
4. Finish with Phase D (Plan 31 Play Console setup, AAB upload, Firebase pre-launch).

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
- Critical path: 01→…→30→R→R2→ Battle Step Rewards → **Phase A done** → B.1 done → **B.2 done (RO-02 complete)** → B.3 PR 1 done → B.3 PR 2 + B.4–B.5 → C → D → 31
- Last run: 2026-05-08 (Phase B.2 PR 5 — BattleViewModel.runEndRoundPersistence wrapped in AppDatabase.withTransaction; 468 → 470 tests, all green; lintDebug green; **RO-02 COMPLETE — 5/5 atomic sites landed**)
