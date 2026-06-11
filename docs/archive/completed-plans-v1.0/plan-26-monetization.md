# Plan 26 — Monetization & Ads

**Status:** Complete (Stub Implementation)
**Dependencies:** Plan 17 (Cards System)
**Layer:** `presentation/` + `data/` + `domain/`

---

## Objective

Implement all monetization features using a stub/mock layer: optional reward ads (post-round Gem, double Power Stones, free Card Pack), ad removal IAP, Gem pack IAPs, Season Pass subscription, and cosmetic store. All monetization is cosmetic or convenience — Steps are never purchasable.

Reference: `docs/monetization.md` for full spec.

---

## Implementation Approach

**Stub-first architecture:** All billing and ad functionality uses interface-based abstractions (`BillingManager`, `RewardAdManager`) with local stub implementations (`StubBillingManager`, `StubRewardAdManager`). This allows full UI/UX development and testing without real SDK dependencies. Swap to real implementations by replacing the DI bindings.

---

## What Was Implemented

### Database & Profile (Task 1)
- Added 5 monetization fields to `PlayerProfileEntity`: `adRemoved`, `seasonPassActive`, `seasonPassExpiry`, `freeLabRushUsedToday`, `freeCardPackAdUsedToday`
- Created `CosmeticEntity` + `CosmeticDao` for cosmetic store
- DB bumped to version 7 (12 entities)

### Billing Manager Stub (Task 2)
- `domain/model/BillingProduct.kt` — 5 products (3 Gem packs, Ad Removal, Season Pass)
- `domain/repository/BillingManager.kt` — pure Kotlin interface
- `data/billing/StubBillingManager.kt` — simulates purchases with 500ms delay
- `di/BillingModule.kt` — DI binding

### Store UI (Tasks 3, 4, 9)
- `presentation/store/StoreScreen.kt` — Gem packs, Ad Removal, Season Pass, Cosmetics sections
- `presentation/store/StoreViewModel.kt` — purchase actions for all product types
- `Screen.Store` route wired in NavHost

### Season Pass (Task 5)
- `TrackDailyLogin` awards 10 bonus Gems/day when Season Pass active
- `LabsViewModel` shows "Free Rush (Season Pass)" button (1/day)
- Season Pass badge on HomeScreen

### Reward Ad Stub (Task 6)
- `domain/model/AdPlacement.kt` — 3 placements
- `domain/repository/RewardAdManager.kt` — pure Kotlin interface
- `data/ads/StubRewardAdManager.kt` — simulates ad view with 1s delay
- `di/AdModule.kt` — DI binding

### Post-Round Ads (Task 7)
- "Watch Ad for +1 Gem" and "Watch Ad to Double PS" buttons on PostRoundOverlay
- Hidden when ad removal purchased, disabled after use (once per round)

### Free Card Pack Ad (Task 8)
- "🎬 Free Pack (Ad)" button on CardsScreen
- Once per day, opens Common pack without Gem cost
- `OpenCardPack` updated with `isFree` parameter (backward-compatible default)

### Cosmetic Store (Task 9)
- `CosmeticCategory` enum, `CosmeticItem` domain model, `CosmeticRepository` interface
- `CosmeticRepositoryImpl` with 7 placeholder items (3 ziggurat skins, 2 projectile effects, 2 enemy skins)
- Purchase with Gems, equip/unequip (one per category)

### Integration (Task 10)
- Store button on HomeScreen and Economy screen
- All ad UI gated on `adRemoved` flag
- Season Pass badge on HomeScreen

---

## Deferred Items (Real SDK Integration)

These items require real SDK dependencies and Play Console configuration:

1. **Google Play Billing Library v7** — Replace `StubBillingManager` with real `BillingClient` integration
2. **AdMob SDK** — Replace `StubRewardAdManager` with real rewarded video ads
3. **Purchase verification** — Server-side receipt validation (or local verification for offline game)
4. **Subscription renewal** — Handle grace periods, billing retries, subscription expiry
5. **Real cosmetic content** — Actual visual skins/effects (visual application in Plan 27)
6. **Play Console products** — Configure IAP products, subscription plans, test tracks
7. **Ad mediation** — Multiple ad networks for fill rate optimization

---

## File Summary

```
domain/model/
├── BillingProduct.kt          (new — 5 products + PurchaseResult)
├── AdPlacement.kt             (new — 3 placements + AdResult)
├── CosmeticCategory.kt        (new — 3 categories)
└── CosmeticItem.kt            (new — cosmetic domain model)

domain/repository/
├── BillingManager.kt          (new — billing interface)
├── RewardAdManager.kt         (new — ad interface)
└── CosmeticRepository.kt      (new — cosmetic interface)

data/billing/
└── StubBillingManager.kt      (new — stub impl)

data/ads/
└── StubRewardAdManager.kt     (new — stub impl)

data/local/
├── CosmeticEntity.kt          (new)
├── CosmeticDao.kt             (new)
├── PlayerProfileEntity.kt     (update — 5 monetization fields)
├── PlayerProfileDao.kt        (update — 4 new queries)
└── AppDatabase.kt             (update — version 7, 12 entities)

data/repository/
├── CosmeticRepositoryImpl.kt  (new — Room-backed + seed data)
└── PlayerRepositoryImpl.kt    (update — new methods)

di/
├── BillingModule.kt           (new)
├── AdModule.kt                (new)
├── DatabaseModule.kt          (update — CosmeticDao)
└── RepositoryModule.kt        (update — CosmeticRepository)

presentation/store/
├── StoreScreen.kt             (new)
├── StoreViewModel.kt          (new)
└── StoreUiState.kt            (new)

presentation/battle/
├── BattleViewModel.kt         (update — ad methods)
├── BattleUiState.kt           (update — ad fields)
└── ui/PostRoundOverlay.kt     (update — ad buttons)

presentation/cards/
├── CardsViewModel.kt          (update — free pack ad)
├── CardsUiState.kt            (update — free pack fields)
└── CardsScreen.kt             (update — free pack button)

presentation/labs/
├── LabsViewModel.kt           (update — free rush)
├── LabsUiState.kt             (update — free rush field)
└── LabsScreen.kt              (update — free rush button)

presentation/home/
├── HomeScreen.kt              (update — Store button, Season Pass badge)
├── HomeUiState.kt             (update — seasonPassActive)
└── HomeViewModel.kt           (update — Season Pass in login)

presentation/economy/
└── CurrencyDashboardScreen.kt (update — Store button)

domain/usecase/
├── PurchaseGemPack.kt         (new)
├── TrackDailyLogin.kt         (update — Season Pass bonus)
└── OpenCardPack.kt            (update — isFree param)
```

## Completion Criteria

- [x] Gem packs purchasable via stub billing (3 tiers)
- [x] Ad removal one-time purchase hides all ad UI
- [x] Season Pass grants daily Gems and free Lab rush
- [x] Reward ads play (stub) and grant rewards on completion
- [x] Ads never interrupt gameplay — always opt-in
- [x] Cosmetic store displays and sells placeholder skins
- [x] Purchased cosmetics persist and can be equipped
- [x] Steps are never purchasable (hard rule enforced)
- [ ] Real Google Play Billing integration (deferred)
- [ ] Real AdMob integration (deferred)
- [ ] Cosmetic visual application (deferred to Plan 27)
