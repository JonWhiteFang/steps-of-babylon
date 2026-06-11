# Plan 11 — In-Round Upgrades & Cash Economy

**Status:** Complete
**Dependencies:** Plan 10 (Stats & Combat)
**Layer:** `presentation/battle/` + `domain/usecase/`

---

## Objective

Implement the in-round cash economy: cash earned from kills and wave completions, the in-round upgrade menu (Attack/Defense/Utility tabs), cash cost scaling per purchase, interest mechanic, and free upgrade chance. These temporary upgrades stack multiplicatively with Workshop upgrades and reset each round.

Reference: GDD §5, `docs/battle-formulas.md` §Cash Economy.

---

## Task Breakdown

### Task 1: Cash Reward System

Create `presentation/battle/engine/CashSystem.kt`:
- Cash from kills: `baseKillCash × tierCashMultiplier × (1 + cashBonusLevel × 0.03)`
- Cash per wave completion: `baseCashPerWave + cashPerWaveLevel × flatBonusPerLevel`
- Interest between waves: `min(heldCash × interestLevel × 0.005, heldCash × 0.10)`
- Updates `RoundState.cash` on each kill and wave end

---

### Task 2: In-Round Upgrade Cost

Create `domain/usecase/CalculateInRoundUpgradeCost.kt`:
- Same formula as Workshop: `baseCost × scaling^level`
- But uses Cash instead of Steps
- In-round levels tracked separately in `RoundState.tempUpgrades`

---

### Task 3: Free Upgrade Chance

Update purchase logic:
- On each in-round purchase: roll against `freeUpgradeChance`
- `freeUpgradeChance = min(freeUpgradeLevel × 0.01, 0.25)` (cap 25%)
- If free: upgrade applied but no Cash deducted
- Visual feedback: "FREE!" flash on the upgrade card

---

### Task 4: In-Round Upgrade Menu

Create `presentation/battle/ui/InRoundUpgradeMenu.kt`:
- Compose overlay on battle screen (collapsible panel)
- Three tabs: Attack, Defense, Utility (same categories as Workshop)
- Each upgrade shows: name, current in-round level, cost in Cash, effect
- Affordable upgrades highlighted; tap to purchase
- Cash balance displayed prominently
- Menu accessible during cooldown phase between waves (and optionally during spawn)

---

### Task 5: In-Round Purchase Flow

Update `BattleViewModel`:
- `purchaseInRoundUpgrade(type: UpgradeType)`:
  - Check Cash affordability
  - Roll free upgrade chance
  - Deduct Cash (unless free)
  - Increment `RoundState.tempUpgrades[type]`
  - Re-resolve stats via `ResolveStats` with updated in-round levels
  - Push updated stats to `GameEngine`

---

### Task 6: Cash Display UI

Update `BattleScreen.kt` Compose overlay:
- Cash balance display (coin icon + amount)
- Cash earned animation on kills (floating "+X" text)
- Interest earned notification between waves

---

## File Summary

```
domain/usecase/
└── CalculateInRoundUpgradeCost.kt (new)

presentation/battle/
├── engine/
│   ├── CashSystem.kt           (new)
│   └── GameEngine.kt           (update)
├── ui/
│   └── InRoundUpgradeMenu.kt   (new)
├── BattleScreen.kt             (update — cash display, upgrade menu)
└── BattleViewModel.kt          (update — purchase flow)
```

## Completion Criteria

- Cash earned from kills scales with tier multiplier and cash bonus upgrade
- Cash per wave bonus applies at wave end
- Interest accrues between waves (cap 10%)
- In-round upgrades purchasable with Cash via three-tab menu
- Free upgrade chance rolls correctly (cap 25%)
- In-round upgrades stack multiplicatively with Workshop upgrades
- Stats re-resolve dynamically on each in-round purchase
- All temp upgrades reset when round ends
- Cash display updates in real-time during battle
