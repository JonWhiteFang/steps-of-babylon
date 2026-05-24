# Database Schema

Room is the single source of truth for all game state. Offline-first — no server required.

## Entities

### PlayerProfile

Primary player record. One row per player (single-player game).

| Column | Type | Notes |
|---|---|---|
| id | Int (PK) | Always 1 |
| totalStepsEarned | Long | Lifetime steps earned |
| currentStepBalance | Long | Spendable step balance |
| gems | Long | Premium currency |
| powerStones | Long | UW currency |
| cardDust | Long | Card Dust currency |
| currentTier | Int | Selected play tier |
| highestUnlockedTier | Int | Highest tier unlocked (default 1) |
| labSlotCount | Int | Lab slots unlocked (default 1, max 4) |
| bestWavePerTier | String (JSON) | Map<Int, Int> serialized |
| currentStreak | Int | Daily login streak count |
| lastLoginDate | String | ISO date of last login |
| totalGemsEarned | Long | Lifetime Gems earned |
| totalGemsSpent | Long | Lifetime Gems spent |
| totalPowerStonesEarned | Long | Lifetime Power Stones earned |
| totalPowerStonesSpent | Long | Lifetime Power Stones spent |
| totalRoundsPlayed | Long | Lifetime battle rounds |
| totalEnemiesKilled | Long | Lifetime enemies killed |
| totalCashEarned | Long | Lifetime in-round Cash earned |
| adRemoved | Boolean | Ad removal purchased |
| seasonPassActive | Boolean | Season Pass currently active |
| seasonPassExpiry | Long | Season Pass expiry epoch millis |
| freeLabRushUsedToday | String | ISO date of last free Lab rush |
| freeCardPackAdUsedToday | String | ISO date of last free Card Pack ad |
| createdAt | Long | Epoch millis |
| lastActiveAt | Long | Epoch millis |

### WorkshopUpgrade

One row per upgrade type (23 rows total).

| Column | Type | Notes |
|---|---|---|
| upgradeType | String (PK) | UpgradeType enum name |
| level | Int | Current level (0 = not purchased) |

### LabResearch

One row per research type (10 rows total).

| Column | Type | Notes |
|---|---|---|
| researchType | String (PK) | ResearchType enum name |
| level | Int | Completed level |
| startedAt | Long? | Epoch millis, null if idle |
| completesAt | Long? | Epoch millis, null if idle |

### CardInventory

One row per owned card.

| Column | Type | Notes |
|---|---|---|
| id | Int (PK, auto) | |
| cardType | String | CardType enum name |
| level | Int | 1–5 |
| isEquipped | Boolean | Max 3 equipped |

### UltimateWeaponState

One row per UW (6 rows total, one per UltimateWeaponType).

| Column | Type | Notes |
|---|---|---|
| weaponType | String (PK) | UltimateWeaponType enum name |
| damageLevel | Int | DAMAGE path upgrade level (0–10) |
| secondaryLevel | Int | SECONDARY path upgrade level (0–10) |
| cooldownLevel | Int | COOLDOWN path upgrade level (0–10) |
| isUnlocked | Boolean | Whether the weapon has been unlocked with Power Stones |
| isEquipped | Boolean | Max 3 equipped |

### DailyStepRecord

Historical step data, one row per day.

| Column | Type | Notes |
|---|---|---|
| date | String (PK) | ISO date (yyyy-MM-dd) |
| sensorSteps | Long | Raw TYPE_STEP_COUNTER |
| healthConnectSteps | Long | Health Connect reported |
| creditedSteps | Long | After anti-cheat validation |
| escrowSteps | Long | Steps held pending cross-validation |
| escrowSyncCount | Int | Number of sync attempts for escrow resolution |
| activityMinutes | String (JSON) | Map<ActivityType, Int> |
| stepEquivalents | Long | From Activity Minute Parity |
| battleStepsEarned | Long | Steps credited from enemy kills today; capped at 2,000/day. Separate from the 50k walking ceiling. See ADR-0003. |

### WalkingEncounter

Unclaimed and historical supply drops.

| Column | Type | Notes |
|---|---|---|
| id | Int (PK, auto) | |
| triggerType | String | What triggered the drop |
| rewardType | String | Steps/Gems/PowerStones/CardDust |
| rewardAmount | Int | |
| claimed | Boolean | |
| createdAt | Long | Epoch millis |
| claimedAt | Long? | Epoch millis |

### WeeklyChallenge

Weekly step challenge tracking, one row per week.

| Column | Type | Notes |
|---|---|---|
| weekStart | String (PK) | ISO date of week start |
| stepsRecorded | Long | Steps accumulated this week |
| tier1Claimed | Boolean | 50k tier claimed |
| tier2Claimed | Boolean | 75k tier claimed |
| tier3Claimed | Boolean | 100k tier claimed |

### DailyLogin

Daily login tracking for streak rewards.

| Column | Type | Notes |
|---|---|---|
| date | String (PK) | ISO date |
| claimed | Boolean | Whether reward was claimed |

### Milestone

Walking milestone claim state.

| Column | Type | Notes |
|---|---|---|
| milestoneId | String (PK) | Milestone identifier |
| claimed | Boolean | Whether reward was claimed |
| claimedAt | Long? | Epoch millis |

### DailyMission

Daily mission tracking, refreshed at midnight.

| Column | Type | Notes |
|---|---|---|
| id | Int (PK, auto) | |
| missionType | String | DailyMissionType enum name |
| date | String | ISO date generated |
| progress | Int | Current progress toward target |
| completed | Boolean | Whether target was reached |
| claimed | Boolean | Whether reward was claimed |

### Cosmetic

Cosmetic store items (ziggurat skins, projectile effects, enemy skins).

| Column | Type | Notes |
|---|---|---|
| id | Int (PK, auto) | |
| cosmeticId | String | Unique cosmetic identifier |
| category | String | CosmeticCategory enum name |
| name | String | Display name |
| description | String | Description text |
| priceGems | Long | Gem cost |
| isOwned | Boolean | Whether player owns this item |
| isEquipped | Boolean | Whether currently equipped |

### BillingReceipt

Play Billing idempotency record. One row per Play-recorded purchase. Keyed by `purchaseToken` (non-null, unique per Play Billing docs; `orderId` is nullable on pending purchases). Introduced by C.5 PR 1 / ADR-0005.

| Column | Type | Notes |
|---|---|---|
| purchaseToken | String (PK) | Play Billing-issued token; stable across re-queries |
| orderId | String? | Play Billing `orderId`; null while pending |
| productId | String | Uppercase `BillingProduct.name` (e.g. `GEM_PACK_SMALL`) |
| purchaseTime | Long | Epoch millis when Play Services recorded the purchase |
| granted | Boolean | `true` once wallet credit committed inside `grantOnceAtomic` |
| grantedAt | Long? | Epoch millis when `granted` flipped true |
| acknowledged | Boolean | `true` once `acknowledgePurchaseAsync` succeeded (non-consumable + subscription path) |
| acknowledgedAt | Long? | Epoch millis when `acknowledged` flipped true |
| consumed | Boolean | `true` once `consumeAsync` succeeded (consumable path) |
| consumedAt | Long? | Epoch millis when `consumed` flipped true |

The DAO exposes `grantOnceAtomic(receipt, grantedAt, walletCredit)` — a `@Transaction` default method that flips `granted = true` and runs the wallet-credit lambda inside one SQLite transaction, returning `true` only if this call transitioned the row from un-granted (idempotent short-circuit otherwise). Consume/acknowledge RPCs deliberately run OUTSIDE the transaction; `getGrantedButUnresolved()` drives the retry path.

## Relationships

```
PlayerProfile (1) ──── (*) WorkshopUpgrade
PlayerProfile (1) ──── (*) LabResearch
PlayerProfile (1) ──── (*) CardInventory
PlayerProfile (1) ──── (*) UltimateWeaponState
PlayerProfile (1) ──── (*) DailyStepRecord
PlayerProfile (1) ──── (*) WalkingEncounter
PlayerProfile (1) ──── (*) WeeklyChallenge
PlayerProfile (1) ──── (*) DailyLogin
PlayerProfile (1) ──── (*) Milestone
PlayerProfile (1) ──── (*) DailyMission
PlayerProfile (1) ──── (*) Cosmetic
```

All relationships are implicit (single player, no foreign keys needed). Queries filter by type/date.

## DAOs

Each entity gets its own DAO:

- `PlayerProfileDao` — CRUD + balance updates
- `WorkshopDao` — get/update levels, bulk query by category
- `LabDao` — active research queries, completion checks
- `CardDao` — inventory, equipped loadout, dust operations
- `UltimateWeaponDao` — unlocked list, equipped loadout
- `DailyStepDao` — insert/update daily, history range queries
- `WalkingEncounterDao` — unclaimed list, claim, history
- `WeeklyChallengeDao` — weekly challenge tracking, tier claims
- `DailyLoginDao` — daily login tracking, streak queries
- `MilestoneDao` — walking milestone tracking, atomic claim (`claimMilestoneAtomic` @Transaction; B.2 PR 4)
- `DailyMissionDao` — daily mission queries, progress updates, completion tracking
- `CosmeticDao` — cosmetic queries, purchase, equip/unequip
- `BillingReceiptDao` — Play Billing idempotency (C.5 PR 1): `getByToken`, `upsert`, `markConsumed`/`markAcknowledged`, `getGrantedButUnresolved` filter for reconciliation, `grantOnceAtomic` @Transaction combining receipt flip + wallet-credit lambda

## Migration Strategy

- Export schemas to `app/schemas/` (commit these files)
- Use Room auto-migrations where possible
- Write manual migrations for complex changes (column renames, data transforms)
- Version numbering: increment by 1 per plan that touches the schema
- Test migrations with `MigrationTestHelper` in instrumented tests
- Current schema version: 10
- Active migrations: `MIGRATION_7_8` (adds `battleStepsEarned`), `MIGRATION_8_9` (adds `billing_receipt` table, C.5 PR 1), `MIGRATION_9_10` (recreates `ultimate_weapon_state` table with per-path columns, R4-06 / ADR-0008)
- v1→v2: Added `highestUnlockedTier` column to `player_profile` (Plan 13). Uses `fallbackToDestructiveMigration` during development.
- v2→v3: Added `labSlotCount` column to `player_profile` (Plan 16). Uses `fallbackToDestructiveMigration` during development.
- v3→v4: Added `WeeklyChallengeEntity`, `DailyLoginEntity`, streak fields on `player_profile` (Plan 20). Uses `fallbackToDestructiveMigration`.
- v4→v5: Added `MilestoneEntity`, `DailyMissionEntity` (Plan 21). Uses `fallbackToDestructiveMigration`.
- v5→v6: Added lifetime currency counters and battle stats to `player_profile` (Plan 22). Uses `fallbackToDestructiveMigration`.
- v6→v7: Added `CosmeticEntity`, monetization fields on `player_profile` (adRemoved, seasonPassActive, seasonPassExpiry, freeLabRushUsedToday, freeCardPackAdUsedToday) (Plan 26). Uses `fallbackToDestructiveMigration`.
- v7→v8: Added `battleStepsEarned` column to `daily_step_record` (Battle Step Rewards, ADR-0003). First explicit `Migration` object (`MIGRATION_7_8` in `data/local/Migrations.kt`); `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` retained for dev/QA downgrades only.
- v8→v9: Added `billing_receipt` table (Play Billing idempotency store, C.5 PR 1 / ADR-0005). `MIGRATION_8_9` creates the table with all 10 columns.
- v9→v10: Recreated `ultimate_weapon_state` table (R4-06 / ADR-0008). Recreate-table dance: create `_new` table with per-path columns (`damageLevel`, `secondaryLevel`, `cooldownLevel`, `isUnlocked`), copy existing rows mapping old `level` → `damageLevel` and `level > 0` → `isUnlocked = 1`, drop old table, rename `_new` to `ultimate_weapon_state`. `MIGRATION_9_10` in `data/local/Migrations.kt`.

## Type Converters

- JSON maps (bestWavePerTier, activityMinutes): `org.json.JSONObject` (Android SDK built-in)
- Enums: stored as String (enum name), no converter needed — entities use String columns
- Dates: stored as Long (epoch millis)

## Notes

- `RoundState` is NOT persisted — it's transient, held in ViewModel during battle
- `Cash` is NOT persisted — it resets each round
- `cardDust: Long` is stored on `PlayerProfile`

## Security

- Database is encrypted at rest using SQLCipher (`net.zetetic:sqlcipher-android`)
- Encryption passphrase is generated randomly on first run, encrypted with an Android Keystore AES-256-GCM key, and stored in SharedPreferences
- On decryption failure (e.g., keystore mismatch after device restore), `DatabaseKeyManager` wipes the stale passphrase and generates a fresh key (database resets)
- Backup is disabled (`allowBackup="false"`) — local-only game, no valuable state to restore across devices
- Uses `fallbackToDestructiveMigration()` during pre-release development
