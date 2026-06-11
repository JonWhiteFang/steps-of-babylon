# Plan 13 — Tier System & Progression

**Status:** Not Started
**Dependencies:** Plan 12 (Round Lifecycle & Post-Round)
**Layer:** `domain/usecase/` + `presentation/`

---

## Objective

Implement tier unlock logic, cash multipliers per tier, and battle conditions that activate at Tier 6+. Players advance through tiers by reaching wave milestones, and higher tiers introduce modifiers that counter specific upgrade strategies.

Reference: GDD §6 for tier table and battle conditions.

---

## Task Breakdown

### Task 1: Tier Unlock Use Case

Create `domain/usecase/CheckTierUnlock.kt`:
- Given `bestWavePerTier` map, checks if the next tier is unlockable
- Tier 2: Wave 50 on Tier 1. Tier 3: Wave 50 on Tier 2. etc.
- Uses `TierConfig` from Plan 01 for requirements
- Returns `TierUnlockResult`: unlockable tier number or null

Create `domain/usecase/UnlockTier.kt`:
- Advances player to the next tier via `PlayerRepository.updateTier()`
- Only if `CheckTierUnlock` confirms eligibility

---

### Task 2: Tier Selection UI

Create `presentation/home/TierSelector.kt`:
- Composable showing current tier and available tiers
- Unlocked tiers selectable; locked tiers show requirements
- Player can choose to play on any unlocked tier (not forced to highest)
- Displays cash multiplier for each tier

---

### Task 3: Battle Condition Application

Create `domain/usecase/ApplyBattleConditions.kt`:
- Given a tier number, returns active `List<BattleCondition>` from `TierConfig`
- Each condition has a severity level (from GDD §6.1):
  - `ENEMY_SPEED`: +10% (T6), +15% (T7)
  - `ORB_RESISTANCE`: Lv20 (T7)
  - `KNOCKBACK_RESISTANCE`: Lv30 (T8)
  - `ARMORED_ENEMIES`: Lv5 (T8)
  - `THORN_RESISTANCE`: Lv30 (T9)
  - `MORE_BOSSES`: every 7 waves (T9)
  - Full conditions at T10

---

### Task 4: Battle Condition Effects in Engine

Update `GameEngine` and combat systems:
- `ENEMY_SPEED`: multiply all enemy speeds by condition factor
- `ORB_RESISTANCE`: reduce orb damage by condition percentage
- `KNOCKBACK_RESISTANCE`: reduce knockback force by condition percentage
- `ARMORED_ENEMIES`: enemies block first X hits (armor counter)
- `THORN_RESISTANCE`: reduce reflected thorn damage
- `MORE_BOSSES`: boss spawn interval changes from 10 to 7 waves
- `ENEMY_ATTACK_SPEED`: enemies attack faster once in range

---

### Task 5: Cash Multiplier Integration

Update `CashSystem`:
- Apply tier cash multiplier from `TierConfig` to all cash earned
- Tier 1 = 1.0×, Tier 10 = 10.0×
- Higher tiers are harder but more rewarding for in-round economy

---

### Task 6: Post-Round Tier Unlock Check

Update `PostRoundScreen` and `BattleViewModel`:
- After round ends, check if wave reached unlocks a new tier
- If yes: show tier unlock notification on post-round screen
- "New Tier Unlocked! Tier X is now available."
- Auto-call `UnlockTier` to persist

---

## File Summary

```
domain/usecase/
├── CheckTierUnlock.kt          (new)
├── UnlockTier.kt               (new)
└── ApplyBattleConditions.kt    (new)

presentation/
├── home/
│   └── TierSelector.kt         (new)
├── battle/
│   ├── BattleViewModel.kt      (update — tier conditions)
│   ├── PostRoundScreen.kt      (update — tier unlock notification)
│   └── engine/
│       ├── GameEngine.kt       (update — apply conditions)
│       ├── CashSystem.kt       (update — tier multiplier)
│       └── EnemyScaler.kt      (update — condition modifiers)
```

## Completion Criteria

- Tier unlock requirements match GDD §6.1 exactly
- Players can select any unlocked tier to play on
- Cash multipliers apply correctly per tier (1.0× to 10.0×)
- Battle conditions activate at Tier 6+ with correct effects
- Orb/knockback/thorn resistance reduces respective mechanics
- Armored enemies block initial hits
- Boss spawn interval changes with MORE_BOSSES condition
- Enemy speed/attack speed modifiers apply
- New tier unlocks detected and persisted after rounds
