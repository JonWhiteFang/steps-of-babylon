# Project State

## Current objective
- **Plan 31 Phase G smoke test PASSED on real device 2026-05-18.** Internal-track v3 AAB installed via opt-in URL. All 5 SKUs verified: 3 Gem packs credited 50/300/700 Gems via real Play Billing test card; `ad_removal` set the flag and hid reward-ad UI; `season_pass` activated with 30-day expiry + +10 Gems/day daily-login bonus; AdMob test ad served on post-round path. This closes the device-verification gate for C.5 PR 2 \u2014 receipt-table idempotency + atomic wallet credit works end-to-end on a real device.
- **C.5 PR 3 landed 2026-05-18.** `StubBillingManager` deleted; `BillingModule` collapsed from a flag-gated Provider-switch to a plain `@Binds BillingManager \u2192 BillingManagerImpl`; `BuildConfig.USE_REAL_BILLING` removed (no remaining readers); KDoc cleanup across 5 production files; `BillingManagerParityTest` deleted (3 tests); `docs/monetization.md` Implementation Status block fully refreshed (it had been stale since pre-C.5/C.6, still describing stubs as the target). Test count 527 \u2192 524.
- **Next external step:** closed-track recruitment. Promote internal v3 to closed testing, recruit \u226512 testers, run for \u226514 calendar days. Mandatory before applying for production access (new Google policy).

## What works
- Plans 01\u201330 + 10b + R (R01\u2013R12) + R2 (R2-01\u2013R2-12) complete.
- Battle Step Rewards (ADR-0003): per-enemy flat reward, 2k/day cap, partial credit, capped-kill FloatingText suppression (A.7).
- DB version 9: 13 entities (billing_receipt added in C.5 PR 1), Room Migrations v7\u21928 and v8\u21929 registered. DB-file wipe recovery on decrypt failure (A.3).
- Phase A foundation, Phase B.1 (TimeProvider seam), Phase B.2 PRs 1\u20135 (RO-02 atomic transactions, 5/5 sites complete), Phase B.3 PRs 1\u20132 (RO-03 resilient endRound complete), Phase C.2 PRs 1+2+3+3b+3c + ensureSeedData fix (RO-07 cosmetic renderer override pipeline complete), Phase C.4 (`ClaimMilestone` UnknownCosmetic detection), Phase C.5 PRs 1+2+3 complete (real Play Billing v8 BillingManagerImpl + lifecycle wiring + reconcile hook + `StubBillingManager` deletion after on-device PASS), Phase C.6 PRs 1+2+3 (real AdMob RewardAdManagerImpl + UMP consent + `StubRewardAdManager` deletion).
- Fresh-install first-kill crash hotfix landed (2026-05-12) \u2014 `DailyStepDao.incrementBattleSteps` UPSERT INSERT half now supplies all 9 NOT NULL columns.
- App launcher icon (vector adaptive icon) shipped 2026-05-12; Play Store 512\u00d7512 hi-res PNG + 1024\u00d7500 feature graphic landed 2026-05-13.
- Play Console: developer account verified, app `com.whitefang.stepsofbabylon` created in Draft state with package-name registered via Android Developer Verification (debug keystore path). Listing populated end-to-end. Internal track v3 (versionCode 3) is live and on-device-verified. 5 SKUs created and active.
- Real Play Billing v8 + AdMob v25 + UMP v4 wired end-to-end and verified on a real device. `BillingManagerImpl` is the sole `BillingManager` binding; `RewardAdManagerImpl` is the sole `RewardAdManager` binding.
- **524 JVM tests** green (down from 527 \u2014 3 obsoleted tests in `BillingManagerParityTest` removed).

## Known issues / debt
- **Closed-testing prerequisite for production launch (new Google policy).** Dashboard mandates \u226512 testers opted-in, \u226514 days of closed testing, before production access can be applied for. Adds \u226514 days to launch timeline.
- **Pre-existing UX gap:** `CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd` all silently swallow `AdResult.Error` and `AdResult.Cancelled`. Worth a snackbar plumbing pass before public launch \u2014 mirror the `userMessage: StateFlow<String?>` pattern from `MissionsViewModel`. Affects 3 call sites. Not a release-blocker.
- **Static priceDisplay constants in `BillingProduct`** drift if Play Console SKU prices change. Long-term proper fix: read formatted price from Play Billing's `ProductDetails.priceDisplay` so Play Console becomes the source of truth. v1.x candidate.
- Cosmetic visual application plumbed end-to-end for 4 cosmetics; 3 non-milestone ziggurat skins (zig_obsidian, zig_crystal, zig_golden) + 4 non-ziggurat seeds (proj_fire, proj_lightning, enemy_shadow, enemy_neon) still show "Coming Soon" in the Store pending their visual content.
- Sound assets are placeholder sine wave tones.
- Phase B debt remaining: B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case. Not blockers.
- `BuildConfig.USE_REAL_ADS` branch (release-only consent prefetch in MainActivity) is not covered by JVM tests \u2014 device-verified 2026-05-12 + 2026-05-18.
- Plan 31 walk-through doc (`docs/release/plan-31-walkthrough.md`) still shows uppercase SKU IDs and pre-dates Android Developer Verification + the closed-testing-before-production policy + the lowercase SKU rule + the native-debug-symbol limitation. Worth a docs revision pass once Plan 31 lands cleanly.
- The Play Console "no debug symbols" warning will persist on every upload \u2014 SQLCipher + androidx.graphics.path .so files ship pre-stripped. Informational only, not a release blocker.

## Top priorities (next 5)
1. **Plan 31 Phase G2 \u2014 closed testing track.** Promote internal v3 \u2192 closed. Recruit \u226512 testers. Wait \u226514 calendar days while collecting feedback.
2. **Plan 31 Phase H \u2014 pre-launch report review.** Auto-runs Firebase Test Lab on every internal-track AAB upload. Already triggered for v3; review findings (stability / security / performance / accessibility). If anything critical surfaces, fix \u2192 bump versionCode \u2192 bundleRelease \u2192 re-upload.
3. **Plan 31 Phase I \u2014 production access application + rollout.** After \u226514 days closed testing with \u226512 testers, apply for production access. Google review 1\u20133 days. Then promote closed \u2192 production with staged rollout (start at 5\u201310 %, monitor crash-free rate, raise to 100 %).
4. **Tag v1.0.0 in git** post-production rollout. Update STATE + RUN_LOG.
5. **Optional / opportunistic:** Ad-error UX snackbar fix (3 call sites). Add release upload keystore as additional ADV key. B.4/B.5 debt cleanup. Walkthrough doc revision pass. Live formatted-price-from-Play-Billing refactor (v1.x).

## Next actions (explicit order)
1. **(Commit, immediate)** Commit `feat(billing): C.5 PR 3 - delete StubBillingManager` on `main` and push.
2. **(External)** Promote internal v3 \u2192 closed testing in Play Console. Recruit \u226512 testers (Gmail addresses), distribute opt-in URL, monitor for \u226514 days.
3. **(External)** Review the auto-generated Pre-launch report on the internal-track v3 AAB. Address any critical findings \u2014 fix \u2192 bump versionCode 4 \u2192 5 \u2192 bundleRelease \u2192 re-upload \u2192 promote.
4. **(External)** After \u226514 days closed testing, apply for production access. Google review 1\u20133 days.
5. **(External)** Promote closed \u2192 production with staged rollout. Tag v1.0.0 in git.
6. **(Optional, opportunistic)** Ad-error UX snackbar fix; B.4/B.5 debt cleanup; walkthrough doc revision; live-price refactor.

## Do-not-touch / fragile zones
- `domain/model/` \u2014 stable, all constants validated by balance tests. `BillingProduct.skuId()` is now a public method; treat as a stable public API.
- `domain/usecase/` \u2014 all 32 use cases stable.
- Balance constants in UpgradeType, TierConfig, EnemyScaler, EnemyType \u2014 validated by 39 regression tests.
- `presentation/battle/effects/` \u2014 particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` \u2014 single source for all dependency versions.
- `app/proguard-rules.pro` \u2014 hardened R8 rules.
- `app/build.gradle.kts` \u2014 signing config, AdMob production-ID wiring (don't break the test-ID fallback path), `ndk { debugSymbolLevel = "FULL" }` (correct config even though SQLCipher pre-stripped means it bundles no symbols today).
- `Screen.items by lazy` + `argumentFreeRoutes by lazy` \u2014 both guard against sealed-class init-order NPE (commit 1872af9).
- `release/` directory contents \u2014 all gitignored, all locally-significant. Don't delete or move without backing up first. `release/upload-keystore.jks` is irreplaceable; losing it before Play App Signing enrollment = no app updates ever (already enrolled).

## References
- ADR-0003 (Battle Step Rewards): docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md
- ADR-0005 (Billing SDK, Accepted; decision #6 refined to lowercase wire format 2026-05-14): docs/agent/DECISIONS/ADR-0005-billing-sdk.md
- ADR-0006 (Ad SDK, Accepted): docs/agent/DECISIONS/ADR-0006-ad-sdk.md
- ADR-0007 (ADV via debug keystore, Accepted): docs/agent/DECISIONS/ADR-0007-adv-debug-keystore.md
- Plan 31 walk-through: docs/release/plan-31-walkthrough.md (note: predates ADV + closed-testing + lowercase SKUs + native-symbol findings; due for a refresh)
- Privacy policy (canonical, in repo): docs/release/privacy-policy.md
- Privacy policy (hosted, GitHub Pages): docs/index.md \u2192 https://jonwhitefang.github.io/steps-of-bablylon/
- Delete-data URL (referenced in Play Console data-safety form): https://jonwhitefang.github.io/steps-of-bablylon/#delete-data
- Play Store listing copy: docs/release/play-store-listing.md
- Master plan: docs/plans/master-plan.md
- Critical path: 01\u2192\u202630\u2192R\u2192R2\u2192 Battle Step Rewards \u2192 Phase A done \u2192 B.1 done \u2192 B.2 done (RO-02 complete) \u2192 B.3 done (RO-03 complete) \u2192 B.4\u2013B.5 \u2192 C.2 PRs done \u2192 C.4 done \u2192 C.5 PRs 1+2+3 done \u2192 C.6 PRs 1+2+3 done \u2192 battle-step-credit hotfix done \u2192 Plan 31 (Phases A\u2013G done; smoke test PASSED 2026-05-18) \u2192 Phase G2 closed track \u2192 Phases H+I production \u2192 D
- Last run: 2026-05-18 (Phase G smoke test PASS + C.5 PR 3 landed: `StubBillingManager` deleted, `BillingModule` collapsed, `USE_REAL_BILLING` removed, monetization doc refreshed; 524 tests green; AAB rebuilt at versionCode 4 but not uploaded \u2014 v3 is live and stable. Next: closed-track recruitment).
