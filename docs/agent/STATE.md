# Project State

## Current objective
- Battle Step Rewards feature complete — kills grant Steps with a 2k/day cap (ADR-0003). DB schema v8.
- Plan 31: Play Console & Store Publication — unblocked, ready to start.

## What works
- Plans 01–30 + 10b: All foundation layers, battle system, full round lifecycle, tier/biome progression, all progression systems, notifications & widget, anti-cheat, monetization (stub), polish & VFX, balancing, testing, release prep complete.
- Plan R (Remediation): All 12 sub-plans (R01–R12) complete.
- Plan R2 (Remediation 2): All 12 sub-plans (R2-01–R2-12) complete.
- Battle Step Rewards (ADR-0003): enemy kills grant flat per-type Steps (BASIC/FAST/SCATTER=1, RANGED=2, TANK=3, BOSS=10), capped at 2,000/day via `AwardBattleSteps` against `DailyStepRecordEntity.battleStepsEarned`. Wired through `GameEngine.onStepReward` callback, surfaced as HUD counter + green '+N Step' FloatingText + Round End line item.
- DB version 8: 12 entities, first explicit Room Migration (v7→v8) registered. **412 JVM tests**, all green. Release APK builds (26MB).

## Known issues / debt
- Billing/ads use stub implementations — real SDK integration deferred to Plan 31.
- Cosmetic visual application not implemented (purchases disabled via R2-11 guard).
- Sound assets are placeholder sine wave tones.
- No app icon resources.

## Top priorities (next 5)
1. Plan 31: Play Console & Store Publication
2. Real billing SDK integration (Google Play Billing Library)
3. Real ad SDK integration (AdMob)
4. App icon and store listing assets
5. Accessibility (Plan 24, deferred)

## Next actions (explicit order)
1. Begin Plan 31: Play Console & Store Publication
2. Set up Google Play Console, create app listing
3. Integrate real billing SDK (replace StubBillingManager)
4. Integrate real ad SDK (replace StubRewardAdManager)
5. Upload AAB to internal test track

## Do-not-touch / fragile zones
- `domain/model/` — stable, all constants validated by balance tests.
- `domain/usecase/` — all 32 use cases stable.
- Balance constants in UpgradeType, TierConfig, EnemyScaler, EnemyType — validated by 39 regression tests.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions.
- `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config, version 1.0.0.

## References
- ADR-0003 (Battle Step Rewards): docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md
- Remediation plan (1st review): docs/plans/plan-R-remediation.md
- Remediation plan (2nd review): docs/plans/plan-R2-remediation.md
- External review (1st): docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX.md
- External review (2nd): docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX_2.md
- Master plan: docs/plans/master-plan.md
- Balance report: docs/balance/balance-report.md
- Release docs: docs/release/
- Critical path: 01→…→30→R→R2→ Battle Step Rewards →31
- Last run: 2026-05-03 (Battle Step Rewards feature complete, DB v8)
