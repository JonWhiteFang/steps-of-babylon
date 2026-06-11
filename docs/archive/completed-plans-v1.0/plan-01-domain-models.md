# Plan 01 — Domain Models & Currency System

**Status:** Complete
**Dependencies:** Project Scaffold (complete)
**Layer:** `domain/` — Pure Kotlin, zero Android dependencies

---

## Objective

Define all core domain models, enums, and the cost calculation engine that the entire game is built on. Everything in this plan lives in `app/src/main/java/com/whitefang/stepsofbabylon/domain/model/` and `domain/usecase/`. No Room, no Android, no UI — just the game's data structures and math.

---

## Task Breakdown

### Task 1: Currency Models

Create `domain/model/Currency.kt`

Define the four currency types and a wallet data class:

```
enum Currency { STEPS, CASH, GEMS, POWER_STONES }
```

Create `domain/model/PlayerWallet.kt` — holds current balances:
- `stepBalance: Long`
- `cash: Long` (transient, per-round)
- `gems: Long`
- `powerStones: Long`

Demo: Currency enum and PlayerWallet exist and can be instantiated.

---

### Task 2: Workshop Upgrade Definitions

Create `domain/model/UpgradeCategory.kt`:

```
enum UpgradeCategory { ATTACK, DEFENSE, UTILITY }
```

Create `domain/model/UpgradeType.kt` — enum with all 23 Workshop upgrades, each carrying its config:

**Attack (8):** DAMAGE, ATTACK_SPEED, CRITICAL_CHANCE, CRITICAL_FACTOR, RANGE, MULTISHOT, BOUNCE_SHOT, DAMAGE_PER_METER

**Defense (9):** HEALTH, HEALTH_REGEN, DEFENSE_PERCENT, DEFENSE_ABSOLUTE, KNOCKBACK, THORN_DAMAGE, ORBS, LIFESTEAL, DEATH_DEFY

**Utility (6):** CASH_BONUS, CASH_PER_WAVE, INTEREST, FREE_UPGRADES, RECOVERY_PACKAGES, STEP_MULTIPLIER

Each enum entry stores:
- `category: UpgradeCategory`
- `baseCost: Long`
- `scaling: Double`
- `maxLevel: Int?` (null = unlimited)
- `effectPerLevel: Double`
- `description: String`

Demo: Can iterate all upgrades, filter by category, access their config values.

---

### Task 3: Cost Calculation Engine

Create `domain/usecase/CalculateUpgradeCost.kt`:

Core formula: `cost = baseCost * (scaling ^ currentLevel)`

Returns `Long` (rounded up). This is used for both Workshop (Step-funded) and in-round (Cash-funded) upgrades.

Also create `domain/usecase/CanAffordUpgrade.kt` — checks if a wallet has enough of the relevant currency for a given upgrade at a given level.

Demo: `CalculateUpgradeCost(UpgradeType.DAMAGE, level=10)` returns `50 * (1.12 ^ 10) = 156`. `CanAffordUpgrade` returns true/false correctly.

---

### Task 4: Tier & Biome Models

Create `domain/model/Tier.kt` — data class:
- `number: Int`
- `unlockWaveRequirement: Int`
- `unlockTierRequirement: Int` (which tier the wave must be achieved on)
- `cashMultiplier: Double`
- `battleConditions: List<BattleCondition>`

Create `domain/model/BattleCondition.kt`:

```
enum BattleCondition {
    ORB_RESISTANCE, KNOCKBACK_RESISTANCE, ARMORED_ENEMIES,
    THORN_RESISTANCE, MORE_BOSSES, ENEMY_SPEED, ENEMY_ATTACK_SPEED
}
```

Create `domain/model/Biome.kt`:

```
enum Biome {
    HANGING_GARDENS,   // Tiers 1-3
    BURNING_SANDS,     // Tiers 4-6
    FROZEN_ZIGGURATS,  // Tiers 7-8
    UNDERWORLD_OF_KUR, // Tiers 9-10
    CELESTIAL_GATE     // Tiers 11+
}
```

With a function `Biome.forTier(tier: Int): Biome`.

Create `domain/model/TierConfig.kt` — object holding the full tier table (tiers 1–10) as a list of `Tier` instances, matching the GDD values exactly.

Demo: `TierConfig.forTier(6).cashMultiplier` returns `5.0`. `Biome.forTier(7)` returns `FROZEN_ZIGGURATS`.

---

### Task 5: Enemy Type Models

Create `domain/model/EnemyType.kt`:

```
enum EnemyType {
    BASIC, FAST, TANK, RANGED, BOSS, SCATTER
}
```

Each entry stores:
- `speedMultiplier: Double` (relative to base speed)
- `healthMultiplier: Double` (relative to base health)
- `damageMultiplier: Double`
- `description: String`

Demo: `EnemyType.FAST.speedMultiplier` returns `2.0`. `EnemyType.TANK.healthMultiplier` is significantly > 1.0.

---

### Task 6: Ultimate Weapon Models

Create `domain/model/UltimateWeaponType.kt`:

```
enum UltimateWeaponType {
    DEATH_WAVE, CHAIN_LIGHTNING, BLACK_HOLE,
    CHRONO_FIELD, POISON_SWAMP, GOLDEN_ZIGGURAT
}
```

Each entry stores:
- `unlockCost: Int` (Power Stones)
- `description: String`

Create `domain/model/UltimateWeaponLoadout.kt` — holds up to 3 equipped UWs.

Demo: Can create a loadout of 3 UWs, attempting to add a 4th is rejected.

---

### Task 7: Overdrive Models

Create `domain/model/OverdriveType.kt`:

```
enum OverdriveType {
    ASSAULT,  // 500 Steps — 2x Attack Speed + 1.5x Damage
    FORTRESS, // 500 Steps — 2x Health Regen + 50% Damage Reduction
    FORTUNE,  // 300 Steps — 3x Cash earned
    SURGE     // 750 Steps — All UW cooldowns reset
}
```

Each entry stores:
- `stepCost: Long`
- `durationSeconds: Int` (60 for all)
- `description: String`

Demo: `OverdriveType.FORTUNE.stepCost` returns `300`.

---

### Task 8: Lab Research Models

Create `domain/model/ResearchType.kt`:

```
enum ResearchType {
    DAMAGE_RESEARCH, HEALTH_RESEARCH, CASH_RESEARCH,
    STEP_EFFICIENCY, WAVE_SKIP, AUTO_UPGRADE_AI,
    UW_COOLDOWN, CRITICAL_RESEARCH, REGEN_RESEARCH, ENEMY_INTEL
}
```

Each entry stores:
- `baseCostSteps: Long`
- `baseTimeHours: Double`
- `maxLevel: Int`
- `effectPerLevel: Double`
- `description: String`

Demo: `ResearchType.WAVE_SKIP.baseCostSteps` returns `10_000`. `ResearchType.ENEMY_INTEL.maxLevel` returns `3`.

---

### Task 9: Card Models

Create `domain/model/CardRarity.kt`:

```
enum CardRarity { COMMON, RARE, EPIC }
```

Create `domain/model/CardType.kt` — enum with all 9 cards from the GDD:

Each entry stores:
- `rarity: CardRarity`
- `effectLv1: String`
- `effectLv5: String`
- `maxLevel: Int` (5 for all)

Create `domain/model/CardLoadout.kt` — holds up to 3 equipped cards.

Demo: Can filter cards by rarity. Loadout enforces max 3.

---

### Task 10: Round State Model

Create `domain/model/RoundState.kt` — transient state during an active battle:
- `currentWave: Int`
- `cash: Long`
- `tempUpgrades: Map<UpgradeType, Int>`
- `towerCurrentHp: Double`
- `towerMaxHp: Double`
- `overdriveUsed: Boolean`
- `overdriveType: OverdriveType?`
- `tier: Int`

Demo: Can create a fresh RoundState for tier 1, wave 1, with zero cash and no temp upgrades.

---

## File Summary

All files go in `domain/model/` or `domain/usecase/`:

```
domain/
├── model/
│   ├── Currency.kt
│   ├── PlayerWallet.kt
│   ├── UpgradeCategory.kt
│   ├── UpgradeType.kt
│   ├── Tier.kt
│   ├── TierConfig.kt
│   ├── BattleCondition.kt
│   ├── Biome.kt
│   ├── EnemyType.kt
│   ├── UltimateWeaponType.kt
│   ├── UltimateWeaponLoadout.kt
│   ├── OverdriveType.kt
│   ├── ResearchType.kt
│   ├── CardRarity.kt
│   ├── CardType.kt
│   ├── CardLoadout.kt
│   └── RoundState.kt
└── usecase/
    ├── CalculateUpgradeCost.kt
    └── CanAffordUpgrade.kt
```

## Completion Criteria

- All models compile as pure Kotlin (no Android imports)
- Cost calculation formula matches GDD: `baseCost * (scaling ^ level)`
- Tier table matches GDD values exactly
- All enum entries match GDD specifications
- Loadouts enforce max capacity (3 UWs, 3 Cards)
