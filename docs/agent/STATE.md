# Project State

## Current objective
- **Build v6 (versionCode 6) and re-upload to internal track for smoke test.** RO-12 fix bundle landed on `main` 2026-05-19 morning closing the in-round stat drift discovered in the v5 smoke test (Wave 4 screenshot at 06:21 BST). Four bugs fixed in a single PR: (1) `BattleViewModel.purchaseInRoundUpgrade` was stripping lab research multipliers because the `labLevels` arg was missing from the `resolveStats` call — RO-11 wiring miss; (2) same site was also stripping card effects because `applyCardEffects` was never re-applied after recompute — pre-existing since Plan 17 but unmasked by RO-11 stacking lab on top; (3) `DescribeUpgradeEffect` wasn't threading `equippedCards` so the readout drifted from the live engine when stat-modifying cards were equipped; (4) `HEALTH_REGEN` readout used `%.1f/s` and showed "Now: 1.3/s → 1.3/s" for a real Lv 0 → Lv 1 upgrade. Fix: extracted `BattleViewModel.resolveCurrentStats(inRound)` private helper running the full live-engine `resolveStats → applyCardEffects` pipeline; threaded `equippedCards` into `DescribeUpgradeEffect.invoke` as an optional `emptyList()`-default arg; bumped HEALTH_REGEN format to `%.2f/s`. Test count 609 → 615 (+6 regression tests). Next: bump versionCode 5 → 6, `./run-gradle.sh bundleRelease`, re-upload to Play Console internal track, run the 8 RO-11 acceptance checks plus 5 RO-12 verification checks per `docs/plans/plan-RO-12-in-round-stat-drift.md` § 8 on a physical device, then promote internal v6 → closed.
- **Previous objective (v5 smoke test, surfaced RO-12 bugs):** v5 (versionCode 5) installed on physical device 2026-05-19 morning. RO-11 #C "Now → Next" readout rendered correctly on DEFENSE tab — acceptance check #8 partially passed — but Wave 4 screenshot at 06:21 BST showed a ~5 % drift between the HEALTH "Now" readout (1647 HP, with lab research) and the live ziggurat top HP bar (1568 HP, without lab). Root-cause investigation surfaced the RO-12 bug bundle above. v5 superseded by v6 before closed-track promotion.
- **Previous objective (RO-11 implementation, complete):** 3-phase Labs wiring + in-round visibility bundle. All 10 ResearchType enums now have correct gameplay impact (8 wired, 2 explicitly gated as v1.x deferred) and every visible upgrade row shows live "Now → Next" readout. Closes the dead-enum gap that would have surfaced as "research does nothing" closed-test feedback within the first round of any tester at level 3+ DAMAGE_RESEARCH.
- **Previous objective (v4 upload, complete):** AAB v4 (versionCode 4) uploaded to Play Console internal track 2026-05-18, includes RO-08 + RO-09 fix bundles. Smoke test was deferred pending RO-11; v5 supersedes v4 before closed-track promotion.
- **Previous objective (RO-09, complete):** 3-fix bundle for pre-closed-test audit findings. **#1 CHRONO_FIELD UW** now actually slows enemies via `CHRONO_SLOW_FACTOR=0.10f` per-entity `deltaTime` scaling (was render-overlay-only, 75 PS for zero gameplay benefit). **#2 GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` stacking** — 3 symmetric edits in `GameEngine.kt` (FORTUNE activate `coerceAtLeast(3.0)`, `expireOverdrive` `if (goldenZigActive) 5.0 else 1.0`, GOLDEN expire `if (activeOverdrive == FORTUNE) 3.0 else 1.0`) close the 5.0×-leak-across-overdrive-expiry exploit. **#7 LabsScreen dead expression** — drive-by delete. Commits `fcb282e` … `fdc34d3` on `main`. Test count 565 → 572. v1.x deferred: #3 STEP_MULTIPLIER × CV unit mismatch, #4 lifetime-counter desync, #5 TOCTOU on gem/PS spend, #6 per-kill credit on `viewModelScope` — all bounded-impact, no closed-test exposure.
- **Previous objective (RO-08, complete):** 4-fix bundle for upgrade wiring (STEP_MULTIPLIER + RECOVERY_PACKAGES + ZigguratEntity stale stats + ResolveStats coverage + STEP_SURGE gem multiplier). Commits `5c2baca` … `b7b8824` on `main`. Test count 535 → 565.

## What works
- Plans 01–30 + 10b + R (R01–R12) + R2 (R2-01–R2-12) complete.
- Battle Step Rewards (ADR-0003): per-enemy flat reward, 2k/day cap, partial credit, capped-kill FloatingText suppression (A.7).
- DB version 9: 13 entities (billing_receipt added in C.5 PR 1), Room Migrations v7→8 and v8→9 registered. DB-file wipe recovery on decrypt failure (A.3).
- Phase A foundation, Phase B.1 (TimeProvider seam), Phase B.2 PRs 1–5 (RO-02 atomic transactions, 5/5 sites complete), Phase B.3 PRs 1–2 (RO-03 resilient endRound complete), Phase C.2 PRs 1+2+3+3b+3c + ensureSeedData fix (RO-07 cosmetic renderer override pipeline complete), Phase C.4 (`ClaimMilestone` UnknownCosmetic detection), Phase C.5 PRs 1+2+3 complete (real Play Billing v8 BillingManagerImpl + lifecycle wiring + reconcile hook + `StubBillingManager` deletion after on-device PASS), Phase C.6 PRs 1+2+3 (real AdMob RewardAdManagerImpl + UMP consent + `StubRewardAdManager` deletion).
- Fresh-install first-kill crash hotfix landed (2026-05-12).
- App launcher icon + Play Store 512×512 hi-res PNG + 1024×500 feature graphic + 5 phone screenshots all landed.
- Play Console: developer account verified, app `com.whitefang.stepsofbabylon` created in Draft, package registered via ADV (debug-keystore path). Listing populated end-to-end. Internal track v3 (versionCode 3) live, on-device-verified. 5 SKUs created and active.
- Real Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end and verified on a real device.
- **Pre-closed-testing UX polish (PRs A + B):** Ad-failure modes surface as snackbars in Battle + Cards; Store screen displays live Play-Console prices via `ProductDetails.priceDisplay` with static-constant fallback. Walkthrough doc reflects the lessons learned during the live walk-through.
- **615 JVM tests** green (572 pre-RO-11 → 583 post-Phase-B-commit-4 → 584 post-Phase-B-commit-5 → 609 post-Phase-C → 615 post-RO-12; +6 new tests for the in-round stat drift bugfix bundle).
- **RO-12 in-round stat drift fixes complete:** `BattleViewModel.purchaseInRoundUpgrade` now routes through a new `resolveCurrentStats(inRound)` private helper running the full live-engine `resolveStats → applyCardEffects` pipeline; `DescribeUpgradeEffect` accepts an optional `equippedCards` arg and post-applies card effects to mirror the engine; `HEALTH_REGEN` readout format bumped to `%.2f/s`. Closes the closed-test-blocker drift discovered in the v5 on-device smoke test where every in-round purchase silently stripped lab + card multipliers for the rest of the round.
- **RO-11 Labs wiring complete:** 8 of 10 ResearchType enums consumed by gameplay (DAMAGE / HEALTH / CASH / CRITICAL / REGEN / STEP_EFFICIENCY / UW_COOLDOWN as outer multipliers; WAVE_SKIP as `WaveSpawner.startWave`); 2 enums (AUTO_UPGRADE_AI, ENEMY_INTEL) explicitly gated as v1.x deferred via `ResearchType.isComingSoon` + Labs UI badge + VM-level guard. In-round upgrade menu now renders a per-row "Now → Next" readout (`DescribeUpgradeEffect` use case, `Locale.ROOT`-pinned formatters) so players can see numerical effect of each purchase before committing cash.

## Known issues / debt
- **Closed-testing prerequisite for production launch (new Google policy).** Dashboard mandates ≥12 testers opted-in, ≥14 days of closed testing, before production access can be applied for. Adds ≥14 days to launch timeline.
- Cosmetic visual application plumbed end-to-end for 4 cosmetics; 3 non-milestone ziggurat skins (zig_obsidian, zig_crystal, zig_golden) + 4 non-ziggurat seeds (proj_fire, proj_lightning, enemy_shadow, enemy_neon) still show "Coming Soon" in the Store pending their visual content.
- Sound assets are placeholder sine wave tones.
- Phase B debt remaining: B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case. Per ADR-0004 this is a 4-PR / ~1-week refactor with zero user-visible benefit — deferred to post-launch.
- `BuildConfig.USE_REAL_ADS` branch (release-only consent prefetch in MainActivity) is not covered by JVM tests — device-verified 2026-05-12 + 2026-05-18.
- Live-price feature has two intentional v1.x deferrals (PR B): no refresh on app resume / locale change; no retry on transient network failure. Static `BillingProduct.priceDisplay` fallback covers both for v1.
- The Play Console "no debug symbols" warning will persist on every upload (SQLCipher + androidx.graphics.path .so files ship pre-stripped). Informational, not a release blocker. Documented in walkthrough.
- **RO-09 deferred findings (v1.x patch backlog):** #3 STEP_MULTIPLIER × cross-validator unit mismatch (needs schema migration to track multiplier-bonus separately); #4 currency lifetime counter desync (display-only drift on crash); #5 TOCTOU race on gem/PS spend (lifetime drift, wallet stays correct); #6 per-kill battle-step credit on `viewModelScope` (≤1 step per pending callback lost on mid-round nav-away).

## Top priorities (next 5)
1. **Build v6 + re-upload to internal track — immediate.** Bump versionCode 5 → 6 in `app/build.gradle.kts`, `./run-gradle.sh bundleRelease`, sign the AAB, upload to Play Console internal track. Then run the 8 RO-11 acceptance checks per `docs/plans/plan-RO-11-labs-wiring.md` § 8 plus 5 RO-12-specific checks per `docs/plans/plan-RO-12-in-round-stat-drift.md` § 8: HP bar matches HEALTH "Now" with HEALTH_RESEARCH owned, HP bar still matches after any in-round purchase, HP bar matches with WALKING_FORTRESS equipped, HEALTH_REGEN row shows 2-decimal readout that visibly changes Lv 0 → Lv 1, RO-11 acceptance check #1 still passes after multiple in-round purchases.
2. **Plan 31 Phase G2 — closed testing track.** If smoke green, promote internal v6 → closed. Recruit ≥12 testers (Gmail addresses), distribute opt-in URL, wait ≥14 calendar days.
3. **Plan 31 Phase H — Pre-launch report review.** Auto-runs Firebase Test Lab on every internal-track AAB upload. Review + fix anything critical (bump versionCode → bundleRelease → re-upload to closed track).
4. **Plan 31 Phase I — production access application + rollout.** After ≥14 days closed testing with ≥12 testers, apply for production access. Google review 1–3 days. Then promote closed → production with staged rollout (5–10 % → 100 %). Tag v1.0.0 in git post-rollout.
5. **(v1.x patch backlog)** RO-09 deferred findings #3–#6, RO-11 deferred items (AUTO_UPGRADE_AI / ENEMY_INTEL real impl), B.4 / B.5 refactor, live-price retry-on-failure, deferred RO-12 polish (per-format-string audit across remaining 25 readout types).

## Next actions (explicit order)
1. **(Immediate)** Bump versionCode 5 → 6 in `app/build.gradle.kts`. `./run-gradle.sh bundleRelease`. Sign + upload AAB v6 to Play Console internal track.
2. **(Smoke test)** Install v6 from internal track on a physical device. Run the 8 RO-11 acceptance checks plus 5 RO-12 verification checks. If anything fails, file follow-up before promoting to closed.
3. **(External)** Promote internal v6 → closed testing in Play Console. Recruit ≥12 testers (Gmail addresses), distribute opt-in URL.
4. **(External)** Wait ≥14 calendar days while collecting feedback. Address any critical Pre-launch report findings via versionCode → bundleRelease → re-upload to closed track.
5. **(External)** After ≥14 days closed testing, apply for production access. Google review 1–3 days.
6. **(External)** Promote closed → production with staged rollout. Tag v1.0.0 in git.
7. **(v1.x patch backlog)** RO-09 deferred findings #3–#6 (cross-validator unit fix, currency lifetime atomicity, TOCTOU spend race, per-kill credit on applicationScope); RO-11 deferred items (AUTO_UPGRADE_AI real impl, ENEMY_INTEL real impl); B.4 / B.5 refactor; live-price retry-on-failure.

## Do-not-touch / fragile zones
- `domain/model/` — stable, all constants validated by balance tests. `BillingProduct.skuId()` is now a public method; treat as a stable public API.
- `domain/usecase/` — all 32 use cases stable.
- Balance constants — validated by 39 regression tests.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions.
- `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config, AdMob production-ID wiring (don't break the test-ID fallback path), `ndk { debugSymbolLevel = "FULL" }`.
- `Screen.items by lazy` + `argumentFreeRoutes by lazy` — both guard against sealed-class init-order NPE (commit 1872af9).
- `release/` directory — all gitignored. `release/upload-keystore.jks` is irreplaceable; losing it before Play App Signing enrollment = no app updates ever (already enrolled, so this is now mostly historical).
- **Live-price wiring (PR B)** — stable. Don't add price refresh on app resume / locale change without re-deriving the cache invalidation rules; the current "fetch once on Store entry" is intentionally simple for v1.
- **GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` stacking (RO-09 #2)** — the 3-site invariant ("higher buff wins; lower restores cleanly when one ends") is regression-guarded by 4 GameEngineTest entries. Don't add a fifth fortune source without extending those tests.

## References
- ADR-0003 (Battle Step Rewards): docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md
- ADR-0004 (FollowOnPipeline, Proposed — deferred to post-launch): docs/agent/DECISIONS/ADR-0004-follow-on-pipeline.md
- ADR-0005 (Billing SDK, Accepted; decision #6 refined to lowercase wire format 2026-05-14): docs/agent/DECISIONS/ADR-0005-billing-sdk.md
- ADR-0006 (Ad SDK, Accepted): docs/agent/DECISIONS/ADR-0006-ad-sdk.md
- ADR-0007 (ADV via debug keystore, Accepted): docs/agent/DECISIONS/ADR-0007-adv-debug-keystore.md
- Plan 31 walk-through (revised 2026-05-18): docs/release/plan-31-walkthrough.md
- Privacy policy (canonical, in repo): docs/release/privacy-policy.md
- Privacy policy (hosted, GitHub Pages): docs/index.md → https://jonwhitefang.github.io/steps-of-babylon/
- Delete-data URL: https://jonwhitefang.github.io/steps-of-babylon/#delete-data
- Play Store listing copy: docs/release/play-store-listing.md
- Master plan: docs/plans/master-plan.md
- Plan RO-09 (complete, pre-closed-test fix bundle): docs/plans/plan-RO-09-pre-closed-test-fixes.md
- Plan RO-11 (complete, Labs wiring + in-round visibility): docs/plans/plan-RO-11-labs-wiring.md
- Plan RO-12 (complete, in-round stat drift bugfix bundle): docs/plans/plan-RO-12-in-round-stat-drift.md
- Critical path: 01→…30→R→R2→ Battle Step Rewards → Phase A done → B.1 done → B.2 done (RO-02 complete) → B.3 done (RO-03 complete) → B.4–B.5 (deferred post-launch) → C.2 PRs done → C.4 done → C.5 PRs 1+2+3 done → C.6 PRs 1+2+3 done → battle-step-credit hotfix done → RO-08 done (4-fix upgrade-wiring bundle) → RO-09 done (chrono fix + fortune stacking + drive-by) → RO-11 done (Phase A 7 simple Labs multipliers + Phase B WAVE_SKIP + Coming Soon gate + Phase C in-round readout, 572 → 609 tests) → RO-12 done (in-round stat drift bundle: lab + card preservation + DescribeUpgradeEffect card threading + HEALTH_REGEN precision, 609 → 615 tests) → Plan 31 (Phases A–G done; smoke test PASSED 2026-05-18; v4 superseded by v5; v5 uploaded to internal track 2026-05-19; v5 smoke test surfaced RO-12; **v6 build pending**) → **smoke-test v6 + Phase G2 closed track** (≥14 days, ≥12 testers) → Phases H+I production access → tag v1.0.0
- Last run: 2026-05-19 morning — **RO-12 in-round stat drift bugfix bundle** discovered + fixed + tested in a single morning in response to the v5 smoke test screenshot. 4 bugs fixed (lab strip + card strip on in-round purchase + DescribeUpgradeEffect card drift + HEALTH_REGEN precision); test count 609 → 615 (+6 regression tests); BUILD SUCCESSFUL. Doc sync complete: AGENTS.md, source-files.md, CHANGELOG.md, STATE.md, RUN_LOG.md. Pending: versionCode bump 5 → 6, bundleRelease, re-upload to Play Console internal track, on-device smoke test of v6 against the 8 RO-11 + 5 RO-12 acceptance checks.
