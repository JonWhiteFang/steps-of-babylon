# Plan 17 — Cards System

**Status:** Complete
**Dependencies:** Plan 07 (Workshop Screen & Upgrades)
**Layer:** `presentation/` + `domain/usecase/`

---

## Objective

Implement the Card system: 9 card types across 3 rarities, acquired from Gem-purchased Card Packs. Duplicates convert to Card Dust for upgrades (5 levels each). Players equip a loadout of up to 3 Cards that provide per-round bonuses.

Reference: GDD §9 for card types and effects.

---

## Task Breakdown

### Task 1: Open Card Pack Use Case

Create `domain/usecase/OpenCardPack.kt`:
- Three pack tiers: Common (50 Gems), Rare (150 Gems), Epic (500 Gems)
- Each pack yields 3 cards with rarity distribution:
  - Common pack: mostly Common, small Rare chance
  - Rare pack: guaranteed 1+ Rare
  - Epic pack: guaranteed 1 Epic
- Duplicate cards convert to Card Dust (amount by rarity)
- Deducts Gems, adds cards to `CardRepository`

---

### Task 2: Upgrade Card Use Case

Create `domain/usecase/UpgradeCard.kt`:
- Spends Card Dust to upgrade a card (levels 1–5)
- Dust cost scales per level and rarity
- Upgrades improve card effect (from `effectLv1` toward `effectLv5`)

---

### Task 3: Card Loadout Management

Create `domain/usecase/ManageCardLoadout.kt`:
- Equip/unequip cards (max 3 equipped)
- Validates capacity via `CardLoadout` model
- Persists via `CardRepository`

---

### Task 4: Card Effect Application

Create `domain/usecase/ApplyCardEffects.kt`:
- Given equipped cards and their levels, returns stat modifiers
- Each card applies its effect as a bonus to resolved stats:
  - `IRON_SKIN`: +X% Defense Absolute
  - `SHARP_SHOOTER`: +X% Critical Chance
  - `CASH_GRAB`: +X% Cash from kills
  - `VAMPIRIC_TOUCH`: +X% Lifesteal
  - `CHAIN_REACTION`: +X Bounce Shot targets
  - `SECOND_WIND`: revive once at X% HP
  - `WALKING_FORTRESS`: +X% Health, -Y% Attack Speed
  - `GLASS_CANNON`: +X% Damage, -Y% Health
  - `STEP_SURGE`: X× Gems earned this round
- Effects applied at round start, active for entire round

---

### Task 5: Card Dust Tracking

Update `PlayerProfileEntity`:
- Add `cardDust: Long` (default 0)
- Migration for new column

---

### Task 6: CardsViewModel

Create `presentation/cards/CardsViewModel.kt`:
- `@HiltViewModel` injecting `CardRepository`, `PlayerRepository`, use cases
- Exposes `StateFlow<CardsUiState>`:
  - `ownedCards: List<OwnedCard>` grouped by type
  - `equippedCards: List<OwnedCard>` (max 3)
  - `cardDust: Long`, `gems: Long`
- Actions: `openPack()`, `upgradeCard()`, `equipCard()`, `unequipCard()`

---

### Task 7: Cards Screen UI

Create `presentation/cards/CardsScreen.kt`:
- Card collection grid with rarity-colored borders (gray/blue/purple)
- Each card shows: name, rarity, level, effect at current level
- Equipped cards highlighted with loadout slot indicator
- Tap card → detail view with equip/upgrade options
- "Open Pack" buttons for each pack tier with Gem cost
- Card Dust balance display

---

### Task 8: Wire Cards to Battle

Update `BattleViewModel`:
- On round start: load equipped cards, apply effects via `ApplyCardEffects`
- Pass card modifiers to `ResolveStats`
- `SECOND_WIND` card: hook into death detection for revive mechanic

---

## File Summary

```
domain/usecase/
├── OpenCardPack.kt             (new)
├── UpgradeCard.kt              (new)
├── ManageCardLoadout.kt        (new)
└── ApplyCardEffects.kt         (new)

presentation/cards/
├── CardsViewModel.kt           (new)
├── CardsScreen.kt              (new)
└── CardsUiState.kt             (new)

presentation/battle/
└── BattleViewModel.kt          (update — card effects on round start)

data/local/
├── PlayerProfileEntity.kt      (update — add cardDust)
└── AppDatabase.kt              (update — migration)
```

## Completion Criteria

- All 9 card types acquirable from Card Packs
- Three pack tiers with correct Gem costs and rarity distributions
- Duplicates convert to Card Dust
- Cards upgradable from level 1–5 using Card Dust
- Loadout enforces max 3 equipped
- Card effects apply correctly at round start
- Walking Fortress and Glass Cannon apply both buff and debuff
- Second Wind revive mechanic works once per round
- Cards screen shows collection, loadout, and pack opening
