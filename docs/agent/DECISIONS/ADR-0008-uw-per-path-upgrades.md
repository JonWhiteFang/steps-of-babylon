# ADR-0008: UW Per-Path Upgrades + Auto-Trigger

**Status:** Accepted  
**Date:** 2026-05-24  
**Context:** R4-06 (Plan R4 Wave 2)  
**Supersedes:** The pre-R4-06 single-`level` UW upgrade model (Plan 15)

## Context

Internal-soak feedback (2026-05-22) identified the single-level UW upgrade axis as
uninteresting: "How to trigger UWs? They should be auto starting and one of the upgrade
paths should be reducing the time." The player wanted per-UW specialisation choices and
passive activation so UWs feel like a strategic investment rather than a tap-timing
mechanic.

## Decision

### 1. Three independent upgrade paths per UW

Each of the 6 Ultimate Weapons now has 3 paths (DAMAGE / SECONDARY / COOLDOWN), each
advancing independently from L0 (just unlocked) to L10 (max). The path's gameplay
meaning varies per UW:

| UW | DAMAGE path | SECONDARY path | COOLDOWN path |
|---|---|---|---|
| DEATH_WAVE | Raw damage (500→3000) | Radius fraction (50%→100%) | 60s→20s |
| CHAIN_LIGHTNING | Per-target damage (500→2000) | Chain length (3→12) | 30s→6s |
| BLACK_HOLE | DPS (50→250) | Pull strength (30→200 px/s) | 90s→30s |
| CHRONO_FIELD | Slow factor (50%→5%) | Duration (5s→14s) | 75s→25s |
| POISON_SWAMP | DoT %MaxHP/sec (1%→8%) | Area fraction (50%→100%) | 60s→20s |
| GOLDEN_ZIGGURAT | Cash multiplier (2×→8×) | Damage multiplier (1.2×→3×) | 90s→30s |

All values are linearly interpolated between L1 and L10 endpoints.

### 2. Auto-trigger

UWs fire automatically when their cooldown reaches 0 AND at least one enemy is alive.
The `UltimateWeaponBar` becomes a passive cooldown indicator (no tap interaction).
`BattleViewModel.activateUW` is removed; the engine's `updateUWs` loop is the sole
activator.

### 3. Cost formula

Per-path: `unlockCost × 2 × currentPathLevel`. L0→L1 is free (cost = 0). Total cost
to max one path (L0→L10) = `90 × unlockCost`. Total across all 3 paths = `270 × unlockCost`.

### 4. Schema migration v9→v10

The `ultimate_weapon_state` table's single `level` column is replaced by `damageLevel`,
`secondaryLevel`, `cooldownLevel` (all INTEGER NOT NULL DEFAULT 0) plus `isUnlocked`
(INTEGER NOT NULL DEFAULT 0). Migration uses the recreate-table dance (CREATE new →
INSERT SELECT with redistribution → DROP old → RENAME) because SQLite's ALTER TABLE
DROP COLUMN is unreliable for Room schema-hash compatibility.

Redistribution formula: `damageLevel = (L+2)/3, secondaryLevel = (L+1)/3, cooldownLevel = L/3`.
For L=5 this produces 2/2/1 (sum 5). For L=10 this produces 4/3/3 (sum 10).

### 5. CHRONO_SLOW_FACTOR removal

The `GameEngine.CHRONO_SLOW_FACTOR = 0.10f` companion constant is removed. The slow
factor is now per-UW-state, read from the DAMAGE path at activation time and stored in
a `chronoSlowFactor` field on the engine. This allows CHRONO_FIELD to scale from 50%
speed (L1) to 5% speed (L10).

## Consequences

- 180 total upgrade purchases across 6 UWs (3 paths × 10 levels × 6 UWs).
- Players can specialise (e.g. max cooldown for uptime, or max damage for burst).
- The `UltimateWeaponScreen` shows 3 per-path Upgrade buttons per unlocked UW.
- The `UltimateWeaponBar` in battle is now purely informational.
- The RO-09 #2 GOLDEN_ZIGGURAT fortune-multiplier tests collapse from 4 to 2 (R4-01
  already removed Overdrive; R4-06 changes the activation value from hard-coded 5.0×
  to `damageAtLevel(damageLevel)` via `coerceAtLeast`).
- Boss-drop Power Stones (R4-07) will share the same v9→v10 migration by adding a
  `bossPsEarnedToday` column to `DailyStepRecordEntity` in the same migration object.
