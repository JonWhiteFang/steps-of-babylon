# Plan 02 — Room Database & DAOs

**Status:** Complete
**Dependencies:** Plan 01 (Domain Models — complete)
**Layer:** `data/local/` — Android/Room layer

---

## Objective

Create all Room entities, DAOs, type converters, and wire them into `AppDatabase`. This plan transforms the domain models from Plan 01 into persistable database records. After this plan, all game state can be saved and queried locally.

Reference: `docs/database-schema.md` for the full schema specification.

---

## Task Breakdown

### Task 1: Type Converters

Create `data/local/Converters.kt`:

- `String ↔ Map<Int, Int>` — for `bestWavePerTier` (JSON serialization)
- `String ↔ Map<String, Int>` — for `activityMinutes` (JSON serialization)
- Enum name `String ↔` enum converters are handled by storing enum `.name` directly

Register converters on `AppDatabase` via `@TypeConverters`.

---

### Task 2: Update PlayerProfileEntity

Update existing `data/local/PlayerProfileEntity.kt` to match the full schema:

| Column | Type | Notes |
|---|---|---|
| id | Int (PK) | Always 1 |
| totalStepsEarned | Long | Default 0 |
| currentStepBalance | Long | Default 0 |
| gems | Long | Default 0 |
| powerStones | Long | Default 0 |
| currentTier | Int | Default 1 |
| bestWavePerTier | String (JSON) | Map<Int, Int> via converter |
| createdAt | Long | Epoch millis |
| lastActiveAt | Long | Epoch millis |

---

### Task 3: WorkshopUpgradeEntity & DAO

Create `data/local/WorkshopUpgradeEntity.kt`:

| Column | Type | Notes |
|---|---|---|
| upgradeType | String (PK) | UpgradeType enum name |
| level | Int | Default 0 |

Create `data/local/WorkshopDao.kt`:
- `getAll(): Flow<List<WorkshopUpgradeEntity>>`
- `getByType(upgradeType: String): Flow<WorkshopUpgradeEntity?>`
- `getByCategory(types: List<String>): Flow<List<WorkshopUpgradeEntity>>`
- `upsert(entity: WorkshopUpgradeEntity)`
- `upsertAll(entities: List<WorkshopUpgradeEntity>)`

---

### Task 4: LabResearchEntity & DAO

Create `data/local/LabResearchEntity.kt`:

| Column | Type | Notes |
|---|---|---|
| researchType | String (PK) | ResearchType enum name |
| level | Int | Default 0 |
| startedAt | Long? | Null if idle |
| completesAt | Long? | Null if idle |

Create `data/local/LabDao.kt`:
- `getAll(): Flow<List<LabResearchEntity>>`
- `getByType(researchType: String): Flow<LabResearchEntity?>`
- `getActive(): Flow<List<LabResearchEntity>>` — where `startedAt IS NOT NULL`
- `upsert(entity: LabResearchEntity)`

---

### Task 5: CardInventoryEntity & DAO

Create `data/local/CardInventoryEntity.kt`:

| Column | Type | Notes |
|---|---|---|
| id | Int (PK, autoGenerate) | |
| cardType | String | CardType enum name |
| level | Int | 1–5 |
| isEquipped | Boolean | Max 3 equipped |

Create `data/local/CardDao.kt`:
- `getAll(): Flow<List<CardInventoryEntity>>`
- `getEquipped(): Flow<List<CardInventoryEntity>>` — where `isEquipped = 1`
- `insert(entity: CardInventoryEntity): Long`
- `update(entity: CardInventoryEntity)`
- `delete(entity: CardInventoryEntity)`
- `countEquipped(): Flow<Int>`

---

### Task 6: UltimateWeaponStateEntity & DAO

Create `data/local/UltimateWeaponStateEntity.kt`:

| Column | Type | Notes |
|---|---|---|
| weaponType | String (PK) | UltimateWeaponType enum name |
| level | Int | Default 1 |
| isEquipped | Boolean | Max 3 equipped |

Create `data/local/UltimateWeaponDao.kt`:
- `getAll(): Flow<List<UltimateWeaponStateEntity>>`
- `getEquipped(): Flow<List<UltimateWeaponStateEntity>>`
- `upsert(entity: UltimateWeaponStateEntity)`
- `countEquipped(): Flow<Int>`

---

### Task 7: DailyStepRecordEntity & DAO

Create `data/local/DailyStepRecordEntity.kt`:

| Column | Type | Notes |
|---|---|---|
| date | String (PK) | ISO date yyyy-MM-dd |
| sensorSteps | Long | Default 0 |
| googleFitSteps | Long | Default 0 |
| creditedSteps | Long | Default 0 |
| activityMinutes | String (JSON) | Map<String, Int> via converter |
| stepEquivalents | Long | Default 0 |

Create `data/local/DailyStepDao.kt`:
- `getByDate(date: String): Flow<DailyStepRecordEntity?>`
- `getRange(startDate: String, endDate: String): Flow<List<DailyStepRecordEntity>>`
- `upsert(entity: DailyStepRecordEntity)`

---

### Task 8: WalkingEncounterEntity & DAO

Create `data/local/WalkingEncounterEntity.kt`:

| Column | Type | Notes |
|---|---|---|
| id | Int (PK, autoGenerate) | |
| triggerType | String | What triggered the drop |
| rewardType | String | Steps/Gems/PowerStones/CardDust |
| rewardAmount | Int | |
| claimed | Boolean | Default false |
| createdAt | Long | Epoch millis |
| claimedAt | Long? | Null until claimed |

Create `data/local/WalkingEncounterDao.kt`:
- `getUnclaimed(): Flow<List<WalkingEncounterEntity>>`
- `getHistory(limit: Int): Flow<List<WalkingEncounterEntity>>`
- `insert(entity: WalkingEncounterEntity): Long`
- `markClaimed(id: Int, claimedAt: Long)`
- `countUnclaimed(): Flow<Int>`

---

### Task 9: PlayerProfileDao

Create `data/local/PlayerProfileDao.kt`:
- `get(): Flow<PlayerProfileEntity?>` — single player, id = 1
- `upsert(entity: PlayerProfileEntity)`
- `updateStepBalance(balance: Long)`
- `updateGems(gems: Long)`
- `updatePowerStones(powerStones: Long)`
- `updateTier(tier: Int)`
- `updateBestWavePerTier(bestWavePerTier: String)`
- `updateLastActiveAt(lastActiveAt: Long)`

---

### Task 10: Update AppDatabase

Update `data/local/AppDatabase.kt`:
- Bump version to 2
- Register all entities: `PlayerProfileEntity`, `WorkshopUpgradeEntity`, `LabResearchEntity`, `CardInventoryEntity`, `UltimateWeaponStateEntity`, `DailyStepRecordEntity`, `WalkingEncounterEntity`
- Register `@TypeConverters(Converters::class)`
- Add abstract DAO accessors for all 7 DAOs
- Add auto-migration from version 1 → 2

---

### Task 11: Update DatabaseModule

Update `di/DatabaseModule.kt`:
- Add `@Provides` functions for each new DAO (WorkshopDao, LabDao, CardDao, UltimateWeaponDao, DailyStepDao, WalkingEncounterDao, PlayerProfileDao)

---

## File Summary

```
data/local/
├── AppDatabase.kt              (update)
├── Converters.kt               (new)
├── PlayerProfileEntity.kt      (update)
├── PlayerProfileDao.kt         (new)
├── WorkshopUpgradeEntity.kt    (new)
├── WorkshopDao.kt              (new)
├── LabResearchEntity.kt        (new)
├── LabDao.kt                   (new)
├── CardInventoryEntity.kt      (new)
├── CardDao.kt                  (new)
├── UltimateWeaponStateEntity.kt (new)
├── UltimateWeaponDao.kt        (new)
├── DailyStepRecordEntity.kt    (new)
├── DailyStepDao.kt             (new)
├── WalkingEncounterEntity.kt   (new)
└── WalkingEncounterDao.kt      (new)

di/
└── DatabaseModule.kt           (update)
```

## Completion Criteria

- All 7 entities compile and are registered in AppDatabase
- All 7 DAOs compile with correct Room annotations
- Type converters handle JSON map serialization
- Auto-migration from v1 → v2 is defined
- DatabaseModule provides all DAOs via Hilt
- Schema exported to `app/schemas/`
