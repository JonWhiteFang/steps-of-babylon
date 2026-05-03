# ADR-0003: Battle Step Rewards

**Date:** 2026-05-03
**Status:** Accepted
**Context:** Before this change, Steps were earned exclusively via real-world walking (Step Counter Service + Health Connect). Battles rewarded in-round Cash and end-of-round Power Stones, but returning no Steps meant active play had no direct link to the meta-progression currency. Players could lose motivation to engage with the core gameplay loop between walks, and shorter sessions had no Step-denominated reward worth logging in for.

## Decision

Enemy kills during a battle now award a small, flat-rate **Step** reward, credited immediately to the wallet. Rewards are separate from the 50,000/day walking ceiling and subject to their own per-day cap of **2,000 battle-Steps/day**, tracked on `DailyStepRecordEntity.battleStepsEarned` (DB schema v8). Per-enemy rewards are:

| Enemy  | BASIC | FAST | SCATTER | RANGED | TANK | BOSS |
|--------|-------|------|---------|--------|------|------|
| Steps  | 1     | 1    | 1       | 2      | 3    | 10   |

A typical 20-wave round yields ~350–550 battle-Steps; a deep run maxes out at 2,000/day. The cap resets naturally on date rollover because `battleStepsEarned` is keyed by ISO date.

## Rationale

- **"Steps = walking" invariant is preserved.** Battle Steps are a small supplement, not the primary source. ~2,000 battle-Steps/day caps out at ~4% of the 50k walking ceiling, well below the level where players could ignore walking.
- **Separate cap, not additive.** Keeping the battle-Step pool distinct from the 50k walking ceiling means heavy walkers don't get penalised with an overflow, and battle farmers can't mask walking anomalies by claiming the gap as in-battle play. Anti-cheat (`StepCrossValidator`) reads `sensorSteps` and `healthConnectSteps` only, so battle Steps never confuse the Health Connect cross-validation pipeline.
- **Flat per-enemy-type rewards.** No wave scaling, no multiplication by Fortune overdrive, Cash Bonus upgrades, or the Golden Ziggurat UW. This keeps daily yield predictable for the cap logic and anti-cheat auditing, and stops late-wave runs from minting Steps at an accelerating rate.
- **Immediate credit per kill.** Running HUD counter gives the right feedback loop; accumulating and crediting only at round-end would hide the reward behind a quit/crash risk. Database churn is bounded (2,000 writes/day maximum).
- **Daily state lives on the existing daily step record.** Reuses the existing date-keyed row, inherits the natural rollover behaviour walking uses, and co-locates the two "Steps earned today" counters (walking + battle) for future analytics.
- **`addSteps` credit path deliberately chosen.** Battle Steps do bump `totalStepsEarned` in `PlayerProfileEntity`. This is intentional — the field represents "all Steps the player has ever collected" and battle Steps count toward that player-lifetime total. Daily anti-cheat counters are unaffected because those use `sensorSteps`/`healthConnectSteps`, not `totalStepsEarned`.

## Consequences

- New constant `AwardBattleSteps.DAILY_BATTLE_STEP_CAP = 2_000L` is authoritative. Changing it is balance-sensitive and should be treated as a hard tuning decision.
- `EnemyScaler.stepReward(EnemyType)` is the authoritative source for per-type rewards. Balance tests should add a row here if rewards are rebalanced.
- `GameEngine.onStepReward` callback runs on the game-loop thread and MUST NOT block. `BattleViewModel` hops to `viewModelScope` before touching the repository.
- `FloatingText` gained a `color` parameter (default unchanged) so kill floats can be visually distinguished (yellow cash vs. green Steps).
- Room DB is now at v8. All future schema changes must supply an explicit `Migration` object in `data/local/Migrations.kt`.
- `PlayerRepository.addSteps` is now called from two contexts (walking pipeline + battle): existing tests continue to cover walking; `AwardBattleStepsTest` + `BattleViewModelTest` cover battle.

## Non-goals / future work

- No cross-tier scaling of step rewards. If Tier 10+ battles need richer rewards, add a distinct `stepRewardForTier` multiplier or boost via the Season Pass feature rather than relaxing the cap.
- Balance tuning (whether 2,000/day is the right ceiling) should be revisited after telemetry is available post-Plan 31 launch.
