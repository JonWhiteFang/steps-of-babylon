package com.whitefang.stepsofbabylon.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #222 (DATA-1 / TEST-4): characterization tests for the two **data-transforming** recreate-table
 * migrations that previously had no upgrade-path coverage (only [Migration11To12Test] existed):
 *
 *  - **9 → 10** — the Ultimate-Weapon single `level` is split across three per-path levels
 *    (`damageLevel`/`secondaryLevel`/`cooldownLevel`) via integer division (`L=5 → 2/2/1`,
 *    `L=10 → 4/3/3`, `L=1 → 1/0/0`); `isUnlocked` is derived `level >= 1`.
 *  - **10 → 11** — the dust-based card system collapses duplicate `cardType` rows into one
 *    copy-counted row (`MAX(level)`/`MAX(isEquipped)`/`COUNT(*) as copyCount`), per ADR-0010.
 *
 * Live users only ever ran 11→12 (the first public release shipped at v11), so these are insurance
 * against a future regression + validation of the historical transforms — exactly the #222 ask.
 *
 * Follows [Migration11To12Test]'s direct-`migrate()` approach (open a plain [SupportSQLiteOpenHelper]
 * in the pre-migration shape, seed it, invoke the migration's `migrate(db)` as Room would) rather
 * than wiring `MigrationTestHelper` + schema assets — the same pattern already proven in this module.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DataTransformMigrationsTest {
    private lateinit var helper: SupportSQLiteOpenHelper

    @After
    fun tearDown() = helper.close()

    private fun open(
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteDatabase {
        val callback =
            object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {}
            }
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(
                    ApplicationProvider.getApplicationContext(),
                ).name(null) // in-memory
                .callback(callback)
                .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    // ---------------- 9 → 10: Ultimate-Weapon level split ----------------

    /** The v9 shapes the 9→10 migration reads/alters. */
    private fun createV9(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ultimate_weapon_state` (
                `weaponType` TEXT NOT NULL,
                `level` INTEGER NOT NULL,
                `isEquipped` INTEGER NOT NULL,
                PRIMARY KEY(`weaponType`)
            )
            """.trimIndent(),
        )
        // The migration also ALTERs daily_step_record (adds bossPsEarnedToday); it must exist or the
        // migration throws. Minimal v9 shape (column set isn't asserted here — only that the ALTER runs).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_step_record` (
                `date` TEXT NOT NULL,
                `creditedSteps` INTEGER NOT NULL,
                PRIMARY KEY(`date`)
            )
            """.trimIndent(),
        )
    }

    private fun insertV9Uw(
        db: SupportSQLiteDatabase,
        type: String,
        level: Int,
        equipped: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO ultimate_weapon_state (weaponType, level, isEquipped) VALUES (?, ?, ?)",
            arrayOf<Any>(type, level, equipped),
        )
    }

    @Test
    fun `migration 9 to 10 splits UW level across the three paths by integer division`() {
        val db = open(9, ::createV9)
        insertV9Uw(db, "METEOR_STRIKE", level = 5, equipped = 1) // → 2 / 2 / 1
        insertV9Uw(db, "TIME_WARP", level = 10) // → 4 / 3 / 3
        insertV9Uw(db, "DIVINE_SHIELD", level = 1) // → 1 / 0 / 0

        AppMigrations.MIGRATION_9_10.migrate(db)

        data class Row(
            val dmg: Int,
            val sec: Int,
            val cd: Int,
            val unlocked: Int,
            val equipped: Int,
        )

        fun read(type: String): Row =
            db
                .query(
                    "SELECT damageLevel, secondaryLevel, cooldownLevel, isUnlocked, isEquipped " +
                        "FROM ultimate_weapon_state WHERE weaponType = ?",
                    arrayOf(type),
                ).use {
                    it.moveToFirst()
                    Row(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4))
                }

        assertEquals(Row(2, 2, 1, 1, 1), read("METEOR_STRIKE"))
        assertEquals(Row(4, 3, 3, 1, 0), read("TIME_WARP"))
        assertEquals(Row(1, 0, 0, 1, 0), read("DIVINE_SHIELD"))

        // The bossPsEarnedToday column must have been added to daily_step_record.
        db.query("PRAGMA table_info(daily_step_record)").use {
            val cols = mutableSetOf<String>()
            while (it.moveToNext()) cols += it.getString(it.getColumnIndexOrThrow("name"))
            assertTrue("9→10 must add bossPsEarnedToday", "bossPsEarnedToday" in cols)
        }
    }

    // ---------------- 10 → 11: card dust → copy-count dedup ----------------

    private fun createV10(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `card_inventory` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `cardType` TEXT NOT NULL,
                `level` INTEGER NOT NULL,
                `isEquipped` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        // The migration ends with `UPDATE player_profile SET cardDust = 0 WHERE id = 1`; the table
        // must exist with id + cardDust or the migration throws. Minimal shape (rest unused here).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `player_profile` (
                `id` INTEGER PRIMARY KEY NOT NULL,
                `cardDust` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO player_profile (id, cardDust) VALUES (1, 250)")
    }

    private fun insertV10Card(
        db: SupportSQLiteDatabase,
        type: String,
        level: Int,
        equipped: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO card_inventory (cardType, level, isEquipped) VALUES (?, ?, ?)",
            arrayOf<Any>(type, level, equipped),
        )
    }

    @Test
    fun `migration 10 to 11 collapses duplicate cards into copy-counted rows and adds the unique index`() {
        val db = open(10, ::createV10)
        // Three copies of CASH_GRAB at varying level/equipped; one lone STEP_SURGE.
        insertV10Card(db, "CASH_GRAB", level = 1, equipped = 0)
        insertV10Card(db, "CASH_GRAB", level = 3, equipped = 1) // MAX level + equipped survive
        insertV10Card(db, "CASH_GRAB", level = 2, equipped = 0)
        insertV10Card(db, "STEP_SURGE", level = 1, equipped = 0)

        AppMigrations.MIGRATION_10_11.migrate(db)

        db
            .query(
                "SELECT level, isEquipped, copyCount FROM card_inventory WHERE cardType = ?",
                arrayOf("CASH_GRAB"),
            ).use {
                assertEquals("duplicates must collapse to one row", 1, it.count)
                it.moveToFirst()
                assertEquals("MAX(level) survives", 3, it.getInt(0))
                assertEquals("MAX(isEquipped) survives", 1, it.getInt(1))
                assertEquals("copyCount == number of duplicates", 3, it.getInt(2))
            }
        db.query("SELECT copyCount FROM card_inventory WHERE cardType = ?", arrayOf("STEP_SURGE")).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("a lone card keeps copyCount 1", 1, it.getInt(0))
        }

        // The unique index Room expects now exists and enforces uniqueness.
        db
            .query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_card_inventory_cardType"),
            ).use {
                it.moveToFirst()
                assertEquals("the cardType unique index must exist post-migration", 1, it.getInt(0))
            }
        var rejected = false
        try {
            insertV10Card(db, "CASH_GRAB", level = 1)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("the unique index must reject a duplicate cardType insert", rejected)

        // cardDust is zeroed for the developer's own install.
        db.query("SELECT cardDust FROM player_profile WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }
}
