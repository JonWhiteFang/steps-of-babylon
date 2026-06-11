package com.whitefang.stepsofbabylon.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations for [AppDatabase]. Add a new [Migration] object
 * for every schema version bump and register it in [AppDatabase.Migrations.ALL].
 *
 * Schema exports live in `app/schemas/` — always commit them after bumping
 * the database version.
 */
object AppMigrations {

    /**
     * v7 → v8: Adds [DailyStepRecordEntity.battleStepsEarned] to track the
     * per-day count of Steps awarded by in-battle enemy kills. Defaults to 0
     * for all existing rows.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE daily_step_record " +
                    "ADD COLUMN battleStepsEarned INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v8 → v9: Adds the `billing_receipt` table — the local Play Billing idempotency store
     * keyed by `purchaseToken`. Introduced by C.5 PR 1 / ADR-0005 to guarantee wallet credits
     * run exactly once per purchase across crash/retry boundaries. No existing rows to migrate;
     * the table is created empty.
     *
     * Schema mirrors [BillingReceiptEntity] — any field change requires a new migration and a
     * schema version bump.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `billing_receipt` (
                    `purchaseToken` TEXT NOT NULL,
                    `orderId` TEXT DEFAULT NULL,
                    `productId` TEXT NOT NULL,
                    `purchaseTime` INTEGER NOT NULL,
                    `granted` INTEGER NOT NULL DEFAULT 0,
                    `grantedAt` INTEGER DEFAULT NULL,
                    `acknowledged` INTEGER NOT NULL DEFAULT 0,
                    `acknowledgedAt` INTEGER DEFAULT NULL,
                    `consumed` INTEGER NOT NULL DEFAULT 0,
                    `consumedAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`purchaseToken`)
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * v9 → v10: R4-06 redesigns the [UltimateWeaponStateEntity] schema from a single
     * `level: Int` column into three independent path-level columns ([damageLevel],
     * [secondaryLevel], [cooldownLevel]) plus an explicit [isUnlocked] flag. See ADR-0008
     * (UW per-path upgrades) for the full design rationale.
     *
     * Redistribution rule: each existing row's `level` value is split across the three
     * new path columns via integer division so the sum of the three new levels equals
     * the old level. The split is `damageLevel = (L+2)/3, secondaryLevel = (L+1)/3,
     * cooldownLevel = L/3` — for the canonical mid-level case L=5 this produces 2/2/1
     * (sum 5), matching the example in `docs/archive/completed-plans-v1.0/plan-R4-feedback-bundle.md`. Rows
     * with `level >= 1` are seeded `isUnlocked = 1` (the pre-R4-06 schema only created
     * a row when the player had paid the unlock cost, so any existing row is unlocked).
     *
     * Implementation: SQLite `ALTER TABLE DROP COLUMN` was added in 3.35 and is risky
     * for Room schema-hash compatibility. We use the recreate-table dance — create the
     * v10-shaped table, copy + transform data, drop the old table, rename. The CREATE
     * TABLE statement here mirrors what Room generates from the new entity (column
     * order + types + NOT NULL constraints) so the post-migration schema hash matches
     * the v10 export in `app/schemas/`.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create the v10-shaped table under a temporary name.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ultimate_weapon_state_new` (
                    `weaponType` TEXT NOT NULL,
                    `damageLevel` INTEGER NOT NULL,
                    `secondaryLevel` INTEGER NOT NULL,
                    `cooldownLevel` INTEGER NOT NULL,
                    `isUnlocked` INTEGER NOT NULL,
                    `isEquipped` INTEGER NOT NULL,
                    PRIMARY KEY(`weaponType`)
                )
                """.trimIndent(),
            )

            // 2. Copy + transform legacy rows. Integer division produces the canonical
            //    redistribution: L=5 → 2/2/1; L=10 → 4/3/3; L=1 → 1/0/0.
            db.execSQL(
                """
                INSERT INTO `ultimate_weapon_state_new`
                    (`weaponType`, `damageLevel`, `secondaryLevel`, `cooldownLevel`,
                     `isUnlocked`, `isEquipped`)
                SELECT
                    `weaponType`,
                    (`level` + 2) / 3,
                    (`level` + 1) / 3,
                    `level` / 3,
                    CASE WHEN `level` >= 1 THEN 1 ELSE 0 END,
                    `isEquipped`
                FROM `ultimate_weapon_state`
                """.trimIndent(),
            )

            // 3. Drop legacy table and rename the new one into place.
            db.execSQL("DROP TABLE `ultimate_weapon_state`")
            db.execSQL(
                "ALTER TABLE `ultimate_weapon_state_new` RENAME TO `ultimate_weapon_state`",
            )

            // 4. R4-07: Add bossPsEarnedToday column to daily_step_record for boss-drop
            //    Power Stone daily cap tracking.
            db.execSQL(
                "ALTER TABLE daily_step_record " +
                    "ADD COLUMN bossPsEarnedToday INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v10 → v11: R4-08 rewrites the card system from dust-based to copy-based progression.
     * Adds `copyCount` column, aggregates duplicate rows by `cardType` into single rows
     * with `copyCount = COUNT(*)`, and adds a unique index on `cardType`. See ADR-0010.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create the v11-shaped table with unique index on cardType.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_inventory_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `cardType` TEXT NOT NULL,
                    `level` INTEGER NOT NULL,
                    `isEquipped` INTEGER NOT NULL,
                    `copyCount` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )

            // 2. Aggregate duplicate rows: group by cardType, take MAX(level) and MAX(isEquipped),
            //    count duplicates as copyCount.
            db.execSQL(
                """
                INSERT INTO `card_inventory_new` (`cardType`, `level`, `isEquipped`, `copyCount`)
                SELECT `cardType`, MAX(`level`), MAX(`isEquipped`), COUNT(*)
                FROM `card_inventory`
                GROUP BY `cardType`
                """.trimIndent(),
            )

            // 3. Drop old table and rename.
            db.execSQL("DROP TABLE `card_inventory`")
            db.execSQL("ALTER TABLE `card_inventory_new` RENAME TO `card_inventory`")

            // 4. Create unique index on cardType.
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_card_inventory_cardType` ON `card_inventory` (`cardType`)",
            )

            // 5. Zero out cardDust balance (only developer's own install affected).
            db.execSQL("UPDATE player_profile SET cardDust = 0 WHERE id = 1")
        }
    }

    /**
     * v11 → v12: #127 adds a unique index on `daily_mission(date, missionType)` to stop duplicate
     * daily missions (the check-then-insert generator had no DB-level uniqueness, so two concurrent
     * generations could each insert a full batch → 6 independently-claimable rows for one day). Any
     * pre-existing duplicates must be deduped before the unique index can be created, so this uses
     * the same recreate-table dance as the v10→v11 card migration: create the table, collapse to one
     * row per `(date, missionType)` via `GROUP BY`, drop + rename, then add the unique index.
     *
     * Dedup rule: aggregate each `(date, missionType)` group with `MAX()` per state column (mirroring
     * v10→v11's `MAX(level)/MAX(isEquipped)`), keeping `MIN(id)` as the surviving PK. `MAX(claimed)`
     * is load-bearing: if ANY duplicate of a mission was already claimed (each was independently
     * claimable — that was the defect), the survivor stays claimed so the migration can't *resurrect*
     * a claim and re-credit Gems/Power-Stones. `MAX(progress)/MAX(completed)` keep the furthest
     * progress; `target`/reward columns are per-missionType constants so `MAX` is a no-op for them.
     * The CREATE TABLE mirrors what Room generates from [DailyMissionEntity] (column order + types +
     * NOT NULL) so the post-migration schema hash matches the v12 export in `app/schemas/`.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create the v12-shaped table (same columns as v11 — the index is added separately).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_mission_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date` TEXT NOT NULL,
                    `missionType` TEXT NOT NULL,
                    `target` INTEGER NOT NULL,
                    `progress` INTEGER NOT NULL,
                    `rewardGems` INTEGER NOT NULL,
                    `rewardPowerStones` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `claimed` INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // 2. Collapse each (date, missionType) group to one row: MIN(id) survives as the PK,
            //    every state column carried forward as the group MAX so the strongest progress /
            //    completed / claimed wins — MAX(claimed) guarantees an already-claimed duplicate is
            //    never resurrected into a re-claimable row (no Gem/PS re-credit through the upgrade).
            db.execSQL(
                """
                INSERT INTO `daily_mission_new`
                    (`id`, `date`, `missionType`, `target`, `progress`,
                     `rewardGems`, `rewardPowerStones`, `completed`, `claimed`)
                SELECT MIN(`id`), `date`, `missionType`, MAX(`target`), MAX(`progress`),
                       MAX(`rewardGems`), MAX(`rewardPowerStones`), MAX(`completed`), MAX(`claimed`)
                FROM `daily_mission`
                GROUP BY `date`, `missionType`
                """.trimIndent(),
            )

            // 3. Drop the old table and rename the deduped one into place.
            db.execSQL("DROP TABLE `daily_mission`")
            db.execSQL("ALTER TABLE `daily_mission_new` RENAME TO `daily_mission`")

            // 4. Create the unique index Room expects from the v12 entity.
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_mission_date_missionType` " +
                    "ON `daily_mission` (`date`, `missionType`)",
            )
        }
    }

    /** All migrations in version order. Wire this into the Room builder. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
    )
}
