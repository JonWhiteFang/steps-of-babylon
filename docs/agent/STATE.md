# Project State

## Current objective
- **Smoke-test v5 (versionCode 5) on internal track + promote to closed.** AAB v5 built via `./run-gradle.sh bundleRelease` (BUILD SUCCESSFUL, ~18 MB) and uploaded to Play Console internal track 2026-05-19. RO-11 implementation landed on `main` across 6 implementation commits + plan write-up + doc-sync + versionCode bump + release-notes commit (`d3dc4d6`, `a4eca72`, `14b0665`, `28337e5`, `6b754c9`, `93f6ae8`, `4bcb71c`, `734beaa`, `d9f48e3`). Test count 572 → 609 (+37, vs ~36 plan target). Phase A wired the 7 simple multipliers (DAMAGE / HEALTH / CASH / CRITICAL / REGEN / STEP_EFFICIENCY / UW_COOLDOWN); Phase B wired WAVE_SKIP and gated AUTO_UPGRADE_AI + ENEMY_INTEL with a "Coming Soon" UI badge + VM-level defensive guard; Phase C shipped `DescribeUpgradeEffect` use case + the in-round upgrade-menu "Now → Next" readout the user originally asked for. Next: install v5 on a physical device, run the 8 acceptance smoke checks per `docs/plans/plan-RO-11-labs-wiring.md` § 8, then promote internal v5 → closed.
- **Previous objective (v5 build + upload, complete):** versionCode bumped 4 → 5 (commit `734beaa`); `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL; signed AAB uploaded to Play Console internal track 2026-05-19; v5 release notes for Play Console + closed-track tester recruitment landed in `docs/release/` (commit `d9f48e3`).
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
- **609 JVM tests** green (572 pre-RO-11 → 583 post-Phase-B-commit-4 → 584 post-Phase-B-commit-5 → 609 post-Phase-C; +37 new tests for Labs research wiring + in-round upgrade-effect readout).
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
1. **On-device smoke test v5 — immediate.** Install v5 (versionCode 5) from the Play Console internal track on a physical device. 8 acceptance checks per `docs/plans/plan-RO-11-labs-wiring.md` § 8: DAMAGE_RESEARCH L5 visibly hits harder; HEALTH_RESEARCH L5 ≥+25 % HP; CASH_RESEARCH L5 ≥+25 % per-kill cash; CRITICAL_RESEARCH × CRITICAL_CHANCE crit damage ≥+30 %; STEP_EFFICIENCY L5 → 100 sensor steps credited as ≥110; UW_COOLDOWN L10 → ~70 % baseline cooldown; WAVE_SKIP L5 → HUD opens on Wave 6; in-round upgrade menu shows non-empty "Now → Next" line per row; AUTO_UPGRADE_AI + ENEMY_INTEL rows render "COMING SOON" badge with no Start button. Plus RO-08 + RO-09 regression checks.
2. **Plan 31 Phase G2 — closed testing track.** If smoke green, promote internal v5 → closed. Recruit ≥12 testers (Gmail addresses), distribute opt-in URL, wait ≥14 calendar days.
3. **Plan 31 Phase H — Pre-launch report review.** Auto-runs Firebase Test Lab on every internal-track AAB upload. Review + fix anything critical (bump versionCode → bundleRelease → re-upload to closed track).
4. **Plan 31 Phase I — production access application + rollout.** After ≥14 days closed testing with ≥12 testers, apply for production access. Google review 1–3 days. Then promote closed → production with staged rollout (5–10 % → 100 %). Tag v1.0.0 in git post-rollout.
5. **(v1.x patch backlog)** RO-09 deferred findings #3–#6, RO-11 deferred items (AUTO_UPGRADE_AI / ENEMY_INTEL real impl), B.4 / B.5 refactor, live-price retry-on-failure.

## Next actions (explicit order)
1. **(Smoke test, immediate)** Install v5 from Play Console internal track on a physical device. Run the 8 acceptance checks per plan-RO-11 § 8. If anything fails, file follow-up before promoting to closed.
2. **(External)** Promote internal v5 → closed testing in Play Console. Recruit ≥12 testers (Gmail addresses), distribute opt-in URL.
3. **(External)** Wait ≥14 calendar days while collecting feedback. Address any critical Pre-launch report findings via versionCode → bundleRelease → re-upload to closed track.
4. **(External)** After ≥14 days closed testing, apply for production access. Google review 1–3 days.
5. **(External)** Promote closed → production with staged rollout. Tag v1.0.0 in git.
6. **(v1.x patch backlog)** RO-09 deferred findings #3–#6 (cross-validator unit fix, currency lifetime atomicity, TOCTOU spend race, per-kill credit on applicationScope); RO-11 deferred items (AUTO_UPGRADE_AI real impl, ENEMY_INTEL real impl); B.4 / B.5 refactor; live-price retry-on-failure.

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
- Privacy policy (hosted, GitHub Pages): docs/index.md → https://jonwhitefang.github.io/steps-of-bablylon/
- Delete-data URL: https://jonwhitefang.github.io/steps-of-bablylon/#delete-data
- Play Store listing copy: docs/release/play-store-listing.md
- Master plan: docs/plans/master-plan.md
- Plan RO-09 (complete, pre-closed-test fix bundle): docs/plans/plan-RO-09-pre-closed-test-fixes.md
- Plan RO-11 (proposed, Labs wiring + in-round visibility): docs/plans/plan-RO-11-labs-wiring.md
- Critical path: 01→…30→R→R2→ Battle Step Rewards → Phase A done → B.1 done → B.2 done (RO-02 complete) → B.3 done (RO-03 complete) → B.4–B.5 (deferred post-launch) → C.2 PRs done → C.4 done → C.5 PRs 1+2+3 done → C.6 PRs 1+2+3 done → battle-step-credit hotfix done → RO-08 done (4-fix upgrade-wiring bundle) → RO-09 done (chrono fix + fortune stacking + drive-by) → RO-11 done (Phase A 7 simple Labs multipliers + Phase B WAVE_SKIP + Coming Soon gate + Phase C in-round readout, 572 → 609 tests) → Plan 31 (Phases A–G done; smoke test PASSED 2026-05-18; v4 superseded by v5; **v5 uploaded to internal track 2026-05-19**) → **smoke-test v5 + Phase G2 closed track** (≥14 days, ≥12 testers) → Phases H+I production access → tag v1.0.0
- Last run: 2026-05-19 morning — versionCode bumped 4 → 5 (`734beaa`), `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL, signed AAB v5 uploaded to Play Console internal track. v5 release notes for Play Console + closed-track tester recruitment landed in `docs/release/` (`d9f48e3`). Pending: on-device smoke test of v5 against the 8 RO-11 acceptance checks.
