# Project State

## Current objective
- **C.6 PR 3 landed (2026-05-12)** — `StubRewardAdManager` deleted, `AdModule` collapsed from the C.6 PR 2 Provider-switch to a plain `@Binds RewardAdManagerImpl`, `RewardAdManagerParityTest` dropped (nothing left to compare). `BuildConfig.USE_REAL_ADS` retained only to gate the `MainActivity.onResume` UMP consent prefetch so debug emulators without Play Services don't pay UMP init cost. Tests 531 → 527 (-4, exactly the parity test's 4 cases). `assembleDebug` green. C.6 PR 3 is the final ad-SDK code-loop item before Phase D; C.5 PR 3 (symmetric billing stub deletion) is now the only code gate still waiting on Plan 31 Play Console device-track verification.
- **App launcher icon landed (2026-05-12)** — vector adaptive icon: solid deep-lapis `#0E2247` background + 5-tier stepped-ziggurat foreground with vertical Gold → SandStone → lightened-DeepBronze gradient (all brand colors from `Color.kt`). Resources in `res/drawable/ic_launcher_{background,foreground}.xml` + `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml`. `AndroidManifest.xml` wired with `android:icon` + `android:roundIcon` on `<application>`. minSdk=34 — no raster fallbacks needed; adaptive XML is the single source of truth. Closes the "No app icon resources" debt item. Still pending for Play Store upload: 512×512 hi-res PNG (exportable from the vector), 1024×500 feature graphic, screenshots.
- **Hotfix (2026-05-12 earlier)** — fresh-install first-kill crash in `DailyStepDao.incrementBattleSteps` fixed by expanding the UPSERT's INSERT half to supply all 9 NOT NULL columns explicitly. Root cause was SQLite evaluating NOT NULL before ON CONFLICT could resolve UNIQUE — a first-attempt `insertIfAbsent` pre-seed couldn't help because NOT NULL raises on every INSERT attempt regardless of whether ON CONFLICT would route to UPDATE. Bug was latent since B.2 PR 2. Device-track verified: fresh-install battle runs without crash.
- **C.6 PR 2 device-track verification (2026-05-12 earlier): PASS.** Two sessions, two placements (`DAILY_FREE_CARD_PACK` → AdMob `NO_FILL` code 3; `POST_ROUND_GEM` → DNS failure code 0) exercised the full real-SDK pipeline. The only un-exercised-live branch is `AdResult.Rewarded`, mechanistically symmetric to `Error` in our code.
- Plan 31: Play Console & Store Publication — still the only release-blocker; `StubBillingManager` deletion (C.5 PR 3) is the last code item before Phase D, blocked on Plan 31 Play Console setup (signed upload + SKUs defined + internal test track).

## What works
- Plans 01–30 + 10b + R (R01–R12) + R2 (R2-01–R2-12) complete.
- Battle Step Rewards (ADR-0003): per-enemy flat reward, 2k/day cap, partial credit, capped-kill FloatingText suppression (A.7).
- DB version 9: 13 entities (billing_receipt added in C.5 PR 1), Room Migrations v7→8 and v8→9 registered. DB-file wipe recovery on decrypt failure (A.3).
- Phase A foundation: junit-vintage-engine on classpath recovering 9 previously-hidden Robolectric tests (A.2); Season Pass bonus now paid from background ingestion (A.6); `Screen.fromRoute` + whitelist covers all 12 deep-link routes (A.5); `FakeBillingManager`/`FakeRewardAdManager` scriptable via `resultQueue` (A.4); dead `PlaceholderScreen` + `SupplyDropTrigger.STEP_BURST` removed (A.8, A.9); docs synced to schema v8 + 453-test state (A.1).
- Phase B.1 foundation: `TimeProvider` abstraction landed with 3 narrow-migration sites (`AwardBattleSteps`, `BattleViewModel`, `MissionsViewModel`), `FakeTimeProvider` test double, and 2 midnight-boundary tests that were previously impossible against the real clock. ADR-0004 stub for FollowOnPipeline recorded.
- Phase B.2 PRs 1–5 all landed — **RO-02 complete (5/5 sites)**: `WorkshopDao.purchaseUpgradeAtomic`, `DailyStepDao.creditBattleStepsAtomic`, `StepCrossValidator` multi-write branches inside `AppDatabase.withTransaction { }`, `MilestoneDao.claimMilestoneAtomic`, and `BattleViewModel.runEndRoundPersistence` wrapped in `AppDatabase.withTransaction { }`.
- Phase B.3 PRs 1–2 all landed — **RO-03 complete**: `runEndRoundPersistence` with per-write `runCatching { }.onFailure { Log.w }` isolation + `.getOrNull()` fallbacks; `@ApplicationScope CoroutineScope` via `di/CoroutineScopeModule.kt` so `onCleared` mid-nav round persistence outlives VM cancellation; `GameEngine.hasWaveProgress()` skips persistence for no-progress bounce-throughs.
- Phase C.2 PRs 1+2+3+3b+3c + C.4 + `ensureSeedData` fix all landed — **RO-07 complete**: cosmetic renderer override pipeline end-to-end for 4 cosmetics (zig_jade store + 3 milestone rewards: lapis_lazuli_skin / garden_ziggurat_skin / sandals_of_gilgamesh). `ClaimMilestoneResult` sealed type replaces `Boolean`; `CosmeticRepository.idExists` pre-flight rejects unknown cosmetic ids before the atomic DAO call. All 6 Milestones claim cleanly end-to-end; monetization gap tracked since Plan R2-11 is closed.
- Phase C.5 PRs 1+2 landed: real Play Billing v8 `BillingManagerImpl` + adapter seam + `billing_receipt` Room table with `grantOnceAtomic @Transaction`; `BuildConfig.USE_REAL_BILLING` flag-gated `@Provides` + `Provider<Stub>` + `Provider<Real>` in `BillingModule` (debug=stub, release=real); MainActivity `ActivityProvider` lifecycle wiring; `StoreViewModel.init` reconcile hook.
- Phase C.6 PRs 1+2+3 landed: real AdMob `RewardAdManagerImpl` + UMP `RealConsentManager` + adapter seam + MainActivity consent prefetch gated on `BuildConfig.USE_REAL_ADS`. **C.6 PR 3 (this session) deleted `StubRewardAdManager` and collapsed `AdModule` to `@Binds RewardAdManagerImpl`** — `RewardAdManagerImpl` is now the sole binding for both debug and release.
- Fresh-install first-kill crash hotfix landed (2026-05-12) — `DailyStepDao.incrementBattleSteps` UPSERT INSERT half now supplies all 9 NOT NULL columns.
- **527 JVM tests** green (was 531 pre-PR-3; exactly the parity test's 4 cases dropped).

## Known issues / debt
- **Pre-existing UX gap (now slightly more visible in debug post-C.6 PR 3):** `CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, and `BattleViewModel.watchPsAd` all silently swallow `AdResult.Error` and `AdResult.Cancelled`. Previously masked in debug by the stub's always-`Rewarded` behaviour; post-PR 3 debug builds bind the real impl so a bare emulator now shows the same "nothing happens on ad tap" path as a release `NO_FILL`. Worth a small snackbar plumbing pass before public launch — mirror the `userMessage: StateFlow<String?>` pattern from `MissionsViewModel`. Affects 3 call sites. Not a release-blocker.
- `StubBillingManager` still ships in release builds even though the `@Binds` swap in C.5 PR 2 moved the release binding to `BillingManagerImpl`. Deletion is the C.5 PR 3 gate, which is itself gated on Plan 31 Play Console setup (device-track purchase verification requires real SKUs + internal test track install).
- Cosmetic visual application plumbed end-to-end for 4 cosmetics; 3 non-milestone ziggurat skins (zig_obsidian, zig_crystal, zig_golden) + 4 non-ziggurat seeds (proj_fire, proj_lightning, enemy_shadow, enemy_neon) still show "Coming Soon" in the Store pending their visual content.
- Sound assets are placeholder sine wave tones.
- Phase B debt remaining: B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case. Not blockers.
- `BuildConfig.USE_REAL_BILLING` + `USE_REAL_ADS` branches in their respective DI modules / MainActivity consent prefetch are still not covered by JVM tests — release-build DI wiring + first-resume UMP prefetch need internal-track verification. C.5 PR 2 covered via `BillingManagerParityTest`; C.6 PR 2 device-verified this session. Post-C.6 PR 3 the `USE_REAL_ADS` Provider-switch branch is gone, simplifying this story.

## Top priorities (next 5)
1. **Plan 31 Play Console setup** — signing keystore, Play Console listing, SKUs matching `BillingProduct` enum, license testers, signed AAB upload to internal test track. Unblocks C.5 PR 2 device verification, which unblocks C.5 PR 3.
2. **C.5 PR 3** — delete `StubBillingManager` + collapse the `BillingModule` Provider-switch to `@Binds BillingManagerImpl`. Symmetric with C.6 PR 3 landed this session. Single-file-ish deletion PR, mechanically identical to today's work once Play Console device-track confirms a real Play Billing test purchase credits the wallet.
3. **Ad-error UX gap** — snackbar / user message for `AdResult.Error` and `AdResult.Cancelled` in `CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd`. Three call sites, mirror the existing `userMessage: StateFlow<String?>` pattern from `MissionsViewModel`. Slightly more pressing post-C.6 PR 3 since debug no longer masks it. Not a release-blocker.
4. **B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case** — opportunistic debt cleanup.
5. **Phase D** — AAB upload, Firebase pre-launch report, store listing. Depends on Plan 31.

## Next actions (explicit order)
1. **Plan 31 Play Console setup** — required to unblock C.5 PR 2 device verification. Sign release keystore, create Play Console listing, define SKUs matching `BillingProduct` enum, add license tester accounts, upload signed AAB to internal test track.
2. **C.5 PR 3** (stub deletion) — unblocked after step 1 confirms a real Play Billing test purchase credits the wallet. Mechanically identical to C.6 PR 3 (single-file-ish deletion, collapse Provider-switch to `@Binds`, drop `BillingManagerParityTest`, sync docs).
3. **Ad-error UX gap fix** (3 call sites) — opportunistic; can land before or after step 2.
4. B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case — debt cleanup; land opportunistically.
5. **Phase D** (AAB upload, Firebase pre-launch).

## Do-not-touch / fragile zones
- `domain/model/` — stable, all constants validated by balance tests.
- `domain/usecase/` — all 32 use cases stable.
- Balance constants in UpgradeType, TierConfig, EnemyScaler, EnemyType — validated by 39 regression tests.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions.
- `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config, version 1.0.0.
- `Screen.items by lazy` + `argumentFreeRoutes by lazy` — both guard against sealed-class init-order NPE (commit 1872af9).

## References
- ADR-0003 (Battle Step Rewards): docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md
- ADR-0005 (Billing SDK, Accepted): docs/agent/DECISIONS/ADR-0005-billing-sdk.md
- ADR-0006 (Ad SDK, Accepted): docs/agent/DECISIONS/ADR-0006-ad-sdk.md
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
- Critical path: 01→…→30→R→R2→ Battle Step Rewards → **Phase A done** → B.1 done → **B.2 done (RO-02 complete)** → **B.3 done (RO-03 complete)** → B.4–B.5 → **C.2 PRs 1+2+3+3b+3c + C.4 + ensureSeedData fix done** → **C.5 PRs 1+2 done** → **C.6 PRs 1+2+3 done (PR 2 device-track PASS; PR 3 stub deleted)** → **battle-step-credit hotfix done** → Plan 31 → C.5 PR 3 → D
- Last run: 2026-05-12 (app launcher icon: vector adaptive icon landed — 4 XML resources + manifest wiring, closes "No app icon resources" debt item; assembleDebug clean; Play Store still needs 512×512 / 1024×500 / screenshots which require raster tooling outside my capability).
