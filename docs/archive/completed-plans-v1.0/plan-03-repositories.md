# Plan 03 — Repository Layer

**Status:** ✅ Complete
**Dependencies:** Plan 02 (Room Database & DAOs)
**Layer:** `domain/repository/` (interfaces) + `data/repository/` (implementations)

---

## Objective

Define repository interfaces in the domain layer and implement them in the data layer backed by Room DAOs. Repositories expose game state as Kotlin Flows, bridging the pure domain models from Plan 01 with the Room entities from Plan 02.

---

## Task Breakdown

### Task 1: PlayerRepository

Create `domain/repository/PlayerRepository.kt` (interface):
- `getProfile(): Flow<PlayerWallet>` — maps PlayerProfileEntity to PlayerWallet
- `getPlayerTier(): Flow<Int>`
- `getBestWavePerTier(): Flow<Map<Int, Int>>`
- `spendSteps(amount: Long)`
- `addSteps(amount: Long)`
- `addGems(amount: Long)`
- `spendGems(amount: Long)`
- `addPowerStones(amount: Long)`
- `spendPowerStones(amount: Long)`
- `updateTier(tier: Int)`
- `updateBestWave(tier: Int, wave: Int)`
- `ensureProfileExists()` — creates default profile if none exists

Create `data/repository/PlayerRepositoryImpl.kt`:
- Injects `PlayerProfileDao`
- Maps between `PlayerProfileEntity` and domain models
- `ensureProfileExists()` inserts a default row (id=1) if the table is empty

---

### Task 2: WorkshopRepository

Create `domain/repository/WorkshopRepository.kt` (interface):
- `getAllUpgrades(): Flow<Map<UpgradeType, Int>>` — type → current level
- `getUpgradeLevel(type: UpgradeType): Flow<Int>`
- `getUpgradesByCategory(category: UpgradeCategory): Flow<Map<UpgradeType, Int>>`
- `setUpgradeLevel(type: UpgradeType, level: Int)`
- `ensureUpgradesExist()` — seeds all 23 rows at level 0 if empty

Create `data/repository/WorkshopRepositoryImpl.kt`:
- Injects `WorkshopDao`
- Maps between entity `upgradeType` String and `UpgradeType` enum

---

### Task 3: LabRepository

Create `domain/repository/LabRepository.kt` (interface):
- `getAllResearch(): Flow<Map<ResearchType, Int>>` — type → completed level
- `getActiveResearch(): Flow<List<ActiveResearch>>` — domain model with type, startedAt, completesAt
- `startResearch(type: ResearchType, completesAt: Long)`
- `completeResearch(type: ResearchType)`
- `ensureResearchExists()` — seeds all 10 rows at level 0

Create domain model `domain/model/ActiveResearch.kt`:
- `type: ResearchType`
- `level: Int`
- `startedAt: Long`
- `completesAt: Long`

Create `data/repository/LabRepositoryImpl.kt`.

---

### Task 4: CardRepository

Create `domain/repository/CardRepository.kt` (interface):
- `getAllCards(): Flow<List<OwnedCard>>` — domain model
- `getEquippedCards(): Flow<List<OwnedCard>>`
- `addCard(type: CardType): Long`
- `upgradeCard(id: Int, newLevel: Int)`
- `equipCard(id: Int)`
- `unequipCard(id: Int)`
- `deleteCard(id: Int)`

Create domain model `domain/model/OwnedCard.kt`:
- `id: Int`
- `type: CardType`
- `level: Int`
- `isEquipped: Boolean`

Create `data/repository/CardRepositoryImpl.kt`.

---

### Task 5: UltimateWeaponRepository

Create `domain/repository/UltimateWeaponRepository.kt` (interface):
- `getUnlockedWeapons(): Flow<List<OwnedWeapon>>` — domain model
- `getEquippedWeapons(): Flow<List<OwnedWeapon>>`
- `unlockWeapon(type: UltimateWeaponType)`
- `upgradeWeapon(type: UltimateWeaponType, newLevel: Int)`
- `equipWeapon(type: UltimateWeaponType)`
- `unequipWeapon(type: UltimateWeaponType)`

Create domain model `domain/model/OwnedWeapon.kt`:
- `type: UltimateWeaponType`
- `level: Int`
- `isEquipped: Boolean`

Create `data/repository/UltimateWeaponRepositoryImpl.kt`.

---

### Task 6: StepRepository

Create `domain/repository/StepRepository.kt` (interface):
- `getTodayRecord(): Flow<DailyStepSummary?>` — domain model
- `getHistory(startDate: String, endDate: String): Flow<List<DailyStepSummary>>`
- `updateDailySteps(date: String, sensorSteps: Long, creditedSteps: Long)`
- `updateGoogleFitSteps(date: String, googleFitSteps: Long)`
- `updateActivityMinutes(date: String, activityMinutes: Map<String, Int>, stepEquivalents: Long)`

Create domain model `domain/model/DailyStepSummary.kt`:
- `date: String`
- `sensorSteps: Long`
- `googleFitSteps: Long`
- `creditedSteps: Long`
- `activityMinutes: Map<String, Int>`
- `stepEquivalents: Long`

Create `data/repository/StepRepositoryImpl.kt`.

---

### Task 7: WalkingEncounterRepository

Create `domain/repository/WalkingEncounterRepository.kt` (interface):
- `getUnclaimed(): Flow<List<SupplyDrop>>`
- `getHistory(limit: Int): Flow<List<SupplyDrop>>`
- `createDrop(triggerType: String, rewardType: String, rewardAmount: Int): Long`
- `claimDrop(id: Int)`
- `countUnclaimed(): Flow<Int>`

Create domain model `domain/model/SupplyDrop.kt`:
- `id: Int`
- `triggerType: String`
- `rewardType: String`
- `rewardAmount: Int`
- `claimed: Boolean`
- `createdAt: Long`
- `claimedAt: Long?`

Create `data/repository/WalkingEncounterRepositoryImpl.kt`.

---

### Task 8: RepositoryModule

Create `di/RepositoryModule.kt`:
- Hilt `@Module` with `@InstallIn(SingletonComponent::class)`
- `@Binds` each repository interface to its implementation
- All repositories scoped as `@Singleton`

---

## File Summary

```
domain/
├── model/
│   ├── ActiveResearch.kt       (new)
│   ├── OwnedCard.kt            (new)
│   ├── OwnedWeapon.kt          (new)
│   ├── DailyStepSummary.kt     (new)
│   └── SupplyDrop.kt           (new)
└── repository/
    ├── PlayerRepository.kt     (new)
    ├── WorkshopRepository.kt   (new)
    ├── LabRepository.kt        (new)
    ├── CardRepository.kt       (new)
    ├── UltimateWeaponRepository.kt (new)
    ├── StepRepository.kt       (new)
    └── WalkingEncounterRepository.kt (new)

data/repository/
├── PlayerRepositoryImpl.kt     (new)
├── WorkshopRepositoryImpl.kt   (new)
├── LabRepositoryImpl.kt        (new)
├── CardRepositoryImpl.kt       (new)
├── UltimateWeaponRepositoryImpl.kt (new)
├── StepRepositoryImpl.kt       (new)
└── WalkingEncounterRepositoryImpl.kt (new)

di/
└── RepositoryModule.kt         (new)
```

## Completion Criteria

- All 7 repository interfaces are pure Kotlin (no Android imports in `domain/`)
- All 7 implementations correctly map between Room entities and domain models
- All queries return `Flow` for reactive updates
- RepositoryModule binds all interfaces via Hilt
- `ensureProfileExists()`, `ensureUpgradesExist()`, `ensureResearchExists()` seed default data
