# ADR-0009: Boss-Drop Power Stones

**Status:** Accepted  
**Date:** 2026-05-24  
**Context:** R4-07 (Plan R4 Wave 2)

## Context

Internal-soak feedback (2026-05-22) identified that Power Stones were too scarce for the
expanded UW upgrade system (R4-06: 3 paths × 10 levels × 6 UWs = 180 total upgrades).
The existing PS sources (weekly challenges, wave milestones, daily login) couldn't keep
pace with the new demand. Boss kills — already a climactic moment — were the natural
reward hook.

## Decision

### 1. Reward formula

Each boss kill awards `tier` Power Stones (T1=1 PS, T2=2 PS, … T10=10 PS). The reward
scales with difficulty so higher-tier players earn proportionally more.

### 2. Daily cap

100 PS/day hard cap with silent suppression (no FloatingText when cap is reached). This
prevents farming exploits while still providing meaningful daily income. At T10 that's
10 boss kills/day to cap — roughly 2–3 full sessions.

### 3. Atomic DAO pattern

`DailyStepDao.creditBossPowerStonesAtomic` mirrors the `creditBattleStepsAtomic` shape
(RO-02 B.2 PR 2): cap-check + counter-increment + wallet-credit in a single Room
`@Transaction`. Closes the partial-failure gap and concurrent-kill race.

### 4. Cap tracking column

`bossPsEarnedToday: Long` column on `DailyStepRecordEntity`, added to the existing
v9→v10 migration (R4-06) via `ALTER TABLE ADD COLUMN`. Resets naturally on day rollover
(new date = new row).

### 5. Engine callback

`GameEngine.onBossKilled: ((tier: Int, x: Float, y: Float) -> Unit)?` fires in
`handleEnemyDeath` when `enemy.enemyType == EnemyType.BOSS`. BattleViewModel subscribes,
calls `AwardBossPowerStones`, and emits a purple `FloatingText("+N PS")` on non-zero
credit.

## Consequences

- PS income increases by up to 100/day for active players, making the 180-upgrade UW
  system achievable within a reasonable timeframe.
- No UI changes beyond the in-battle FloatingText — the Economy dashboard already shows
  PS balance.
- The cap prevents degenerate farming (repeatedly quitting at wave 5 boss to farm T1 PS).
- Silent suppression at cap avoids confusing "why did I get 0?" moments — the player
  simply stops seeing purple floats.

## Alternatives Considered

1. **Uncapped boss PS** — rejected; trivially farmable at low tiers.
2. **Fixed PS per boss regardless of tier** — rejected; removes incentive to push higher.
3. **PS only from wave-milestone bosses** — rejected; too infrequent for the expanded UW
   system's demand.
