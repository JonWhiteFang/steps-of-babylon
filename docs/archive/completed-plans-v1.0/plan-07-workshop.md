# Plan 07 — Workshop Screen & Upgrades

**Status:** Complete
**Dependencies:** Plan 06 (Home Screen & Navigation)
**Layer:** `presentation/` + `domain/usecase/`

---

## Objective

Build the Workshop screen where players spend Steps on permanent upgrades. Three-tab layout (Attack/Defense/Utility), each showing upgrade cards with level, effect, and cost. Players tap to purchase, deducting Steps from their balance. Includes a "Quick Invest" recommendation button.

Reference: GDD §4 for all 23 upgrades and their configs.

---

## Task Breakdown

### Task 1: PurchaseUpgrade Use Case

Create `domain/usecase/PurchaseUpgrade.kt`:
- Takes `UpgradeType`, current level, and `PlayerWallet`
- Calculates cost via `CalculateUpgradeCost`
- Checks affordability via `CanAffordUpgrade`
- If affordable: deducts Steps from balance, increments upgrade level
- Returns `Result<Unit>` (success or insufficient funds)
- Respects `maxLevel` cap — rejects if already at max

---

### Task 2: QuickInvest Use Case

Create `domain/usecase/QuickInvest.kt`:
- Given current wallet and all upgrade levels, recommends the best upgrade to buy
- Strategy: prioritize cheapest affordable upgrade, weighted by category balance
- Returns `UpgradeType?` (null if nothing affordable)

---

### Task 3: WorkshopViewModel

Create `presentation/workshop/WorkshopViewModel.kt`:
- `@HiltViewModel` injecting `WorkshopRepository`, `PlayerRepository`, `CalculateUpgradeCost`, `PurchaseUpgrade`, `QuickInvest`
- Exposes `StateFlow<WorkshopUiState>` with:
  - `upgrades: Map<UpgradeType, UpgradeDisplayInfo>` (level, cost, effect description, canAfford)
  - `stepBalance: Long`
  - `selectedCategory: UpgradeCategory`
- Actions: `selectCategory()`, `purchaseUpgrade()`, `quickInvest()`

---

### Task 4: WorkshopScreen UI

Create `presentation/workshop/WorkshopScreen.kt`:
- Three tabs: Attack, Defense, Utility
- Each tab shows a scrollable list of upgrade cards
- Each card displays: upgrade name, current level, effect at current level, cost for next level
- Affordable upgrades highlighted (green accent)
- Max-level upgrades shown as "MAX" with disabled state
- Tap card → purchase confirmation → deduct Steps
- "Quick Invest" FAB at bottom

---

### Task 5: UpgradeCard Composable

Create `presentation/workshop/UpgradeCard.kt`:
- Reusable card composable for a single upgrade
- Shows: icon/name, "Lv. X", effect text, cost with Step icon
- Visual states: affordable (green), too expensive (dimmed), maxed (gold "MAX" badge)

---

## File Summary

```
domain/usecase/
├── PurchaseUpgrade.kt          (new)
└── QuickInvest.kt              (new)

presentation/workshop/
├── WorkshopViewModel.kt        (new)
├── WorkshopScreen.kt           (new)
├── WorkshopUiState.kt          (new)
└── UpgradeCard.kt              (new)
```

## Completion Criteria

- Three-tab Workshop screen renders all 23 upgrades in correct categories
- Purchasing an upgrade deducts Steps and increments level in Room
- Cost formula matches GDD: `baseCost × scaling^level`
- Max-level upgrades cannot be purchased further
- Quick Invest recommends a reasonable next purchase
- UI updates reactively when balance or levels change
