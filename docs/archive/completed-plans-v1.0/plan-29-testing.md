# Plan 29 — Testing & QA

**Status:** Complete
**Dependencies:** Plan 28 (Balancing & Tuning)
**Layer:** Cross-cutting — `test/` + `androidTest/`

---

## Objective

Comprehensive test coverage: unit tests for domain logic (cost calculations, damage formulas, tier progression, stat resolution), ViewModel tests with repository fakes, Room DAO instrumented tests, step sensor integration tests, and UI tests for critical flows.

---

## Task Breakdown

### Task 1: Domain Model Unit Tests

Create unit tests for all domain models:
- `CurrencyTest` — enum values
- `UpgradeTypeTest` — all 23 entries have valid configs, category filtering
- `TierConfigTest` — tier table matches GDD values exactly
- `BiomeTest` — `forTier()` returns correct biome for all tier ranges
- `EnemyTypeTest` — multiplier values match GDD
- `CardLoadoutTest` — max 3 enforced
- `UltimateWeaponLoadoutTest` — max 3 enforced

---

### Task 2: Cost Calculation Tests

Create `CalculateUpgradeCostTest`:
- Level 0: returns baseCost
- Level 10 Damage: `50 × 1.12^10 = 156`
- Level 50 Attack Speed: verify against manual calculation
- Large levels: no overflow (Long range)
- All 23 upgrade types produce positive costs

Create `CanAffordUpgradeTest`:
- Sufficient balance → true
- Insufficient balance → false
- Exact balance → true
- Max level reached → false

---

### Task 3: Battle Formula Tests

Create `ResolveStatsTest`:
- Workshop only: correct stat values
- In-round only: correct stat values
- Combined: multiplicative stacking verified
- Zero levels: base stats returned

Create `CalculateDamageTest`:
- Base damage without crit
- Crit damage with known seed/mock
- Damage/meter bonus at various distances
- Crit chance cap at 80%

Create `CalculateDefenseTest`:
- Defense % reduction (cap 75%)
- Flat block
- Combined reduction + block
- Zero defense: full damage taken
- Damage never goes below 0

---

### Task 4: Use Case Tests

Test all use cases with fake repositories:
- `PurchaseUpgradeTest` — deducts Steps, increments level, rejects if unaffordable
- `ActivateOverdriveTest` — deducts Steps, once-per-round, rejects if used
- `StartResearchTest` — deducts Steps, sets timer, rejects if no slot
- `OpenCardPackTest` — deducts Gems, generates cards with correct rarity distribution
- `CheckTierUnlockTest` — correct wave requirements per tier
- `UpdateBestWaveTest` — detects new records, ignores non-records
- `GenerateSupplyDropTest` — produces drops at expected rates
- `CheckMilestonesTest` — triggers at correct step thresholds

---

### Task 5: ViewModel Tests

Create ViewModel tests with fake repository implementations:
- `HomeViewModelTest` — loads profile data, exposes correct UI state
- `WorkshopViewModelTest` — purchase flow, category filtering, quick invest
- `BattleViewModelTest` — round start/end, stat resolution, overdrive
- `LabsViewModelTest` — start/complete/rush research, slot management
- `CardsViewModelTest` — pack opening, equip/unequip, upgrade

Create `test/fakes/` package with fake implementations of all repositories.

---

### Task 6: Room DAO Instrumented Tests

Create instrumented tests (require device/emulator):
- `PlayerProfileDaoTest` — CRUD, balance updates
- `WorkshopDaoTest` — upsert, query by category
- `LabDaoTest` — active research queries, completion
- `CardDaoTest` — inventory, equipped loadout, count
- `UltimateWeaponDaoTest` — unlock, equip, count
- `DailyStepDaoTest` — date range queries, upsert
- `WalkingEncounterDaoTest` — unclaimed list, claim

Test Room migrations with `MigrationTestHelper`.

---

### Task 7: Step Sensor Integration Tests

Create instrumented tests:
- `StepRateLimiterTest` — caps at 200/min, allows 250 burst
- `DailyStepManagerTest` — ceiling enforcement, day rollover
- `StepCrossValidatorTest` — escrow on >20% discrepancy, release/discard

---

### Task 8: UI Tests

Create Compose UI tests for critical flows:
- Home screen renders with correct data
- Workshop: navigate tabs, purchase upgrade
- Battle: start round, verify wave counter increments
- Navigation: bottom nav bar switches screens correctly

---

## File Summary

```
app/src/test/java/com/whitefang/stepsofbabylon/
├── domain/model/
│   ├── CurrencyTest.kt
│   ├── UpgradeTypeTest.kt
│   ├── TierConfigTest.kt
│   ├── BiomeTest.kt
│   ├── EnemyTypeTest.kt
│   ├── CardLoadoutTest.kt
│   └── UltimateWeaponLoadoutTest.kt
├── domain/usecase/
│   ├── CalculateUpgradeCostTest.kt
│   ├── CanAffordUpgradeTest.kt
│   ├── ResolveStatsTest.kt
│   ├── CalculateDamageTest.kt
│   ├── CalculateDefenseTest.kt
│   ├── PurchaseUpgradeTest.kt
│   ├── ActivateOverdriveTest.kt
│   ├── StartResearchTest.kt
│   ├── OpenCardPackTest.kt
│   ├── CheckTierUnlockTest.kt
│   ├── UpdateBestWaveTest.kt
│   ├── GenerateSupplyDropTest.kt
│   └── CheckMilestonesTest.kt
├── presentation/
│   ├── HomeViewModelTest.kt
│   ├── WorkshopViewModelTest.kt
│   ├── BattleViewModelTest.kt
│   ├── LabsViewModelTest.kt
│   └── CardsViewModelTest.kt
└── fakes/
    ├── FakePlayerRepository.kt
    ├── FakeWorkshopRepository.kt
    ├── FakeLabRepository.kt
    ├── FakeCardRepository.kt
    ├── FakeUltimateWeaponRepository.kt
    ├── FakeStepRepository.kt
    └── FakeWalkingEncounterRepository.kt

app/src/androidTest/java/com/whitefang/stepsofbabylon/
├── data/local/
│   ├── PlayerProfileDaoTest.kt
│   ├── WorkshopDaoTest.kt
│   ├── LabDaoTest.kt
│   ├── CardDaoTest.kt
│   ├── UltimateWeaponDaoTest.kt
│   ├── DailyStepDaoTest.kt
│   ├── WalkingEncounterDaoTest.kt
│   └── MigrationTest.kt
├── data/sensor/
│   ├── StepRateLimiterTest.kt
│   ├── DailyStepManagerTest.kt
│   └── StepCrossValidatorTest.kt
└── presentation/
    ├── HomeScreenTest.kt
    ├── WorkshopScreenTest.kt
    └── NavigationTest.kt
```

## Completion Criteria

- All domain model unit tests pass
- Cost calculation tests verify GDD formulas exactly
- Battle formula tests cover all combat mechanics
- All use case tests pass with fake repositories
- ViewModel tests verify UI state mapping and user actions
- Room DAO tests verify CRUD operations and queries
- Migration tests verify schema upgrades
- Step sensor tests verify rate limiting and ceiling
- UI tests verify critical user flows
- Code coverage: >80% for domain layer, >60% for data layer
