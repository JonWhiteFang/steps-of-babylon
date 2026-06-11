package com.whitefang.stepsofbabylon.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #127: drives [AppMigrations.MIGRATION_11_12] directly against a real SQLite DB built in the v11
 * `daily_mission` shape (no unique index), proving the migration both **dedups pre-existing
 * duplicate rows** and **installs the `(date, missionType)` unique index**. This is the first
 * migration test in the project (also closes the migration-test gap tracked as audit Low #23 for
 * this schema wave) — it exercises the upgrade path the live DAO tests can't, since those build a
 * fresh v12 DB rather than migrating a populated v11 one.
 *
 * Rather than wire `MigrationTestHelper` + schema assets, it opens a plain
 * [FrameworkSQLiteOpenHelper] at version 11, seeds the legacy table, then invokes the migration's
 * `migrate(db)` exactly as Room would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration11To12Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    /** Creates the v11-shape `daily_mission` table (the pre-#127 schema — no unique index). */
    private val v11Callback = object : SupportSQLiteOpenHelper.Callback(11) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_mission` (
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
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    @Before
    fun setup() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext(),
        ).name(null) // in-memory
            .callback(v11Callback)
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @After
    fun tearDown() = helper.close()

    private fun insertV11(
        db: SupportSQLiteDatabase,
        date: String,
        missionType: String,
        progress: Int = 0,
        completed: Int = 0,
        claimed: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO daily_mission " +
                "(date, missionType, target, progress, rewardGems, rewardPowerStones, completed, claimed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(date, missionType, 5000, progress, 5, 0, completed, claimed),
        )
    }

    @Test
    fun `migration collapses duplicate date-missionType rows to one and adds the unique index`() {
        val db = helper.writableDatabase
        val date = "2026-06-10"

        // Seed the exact defect: a full 3-mission day PLUS a duplicate generation batch (6 rows).
        insertV11(db, date, "WALK_5000")
        insertV11(db, date, "REACH_WAVE_30")
        insertV11(db, date, "SPEND_5000_WORKSHOP")
        insertV11(db, date, "WALK_5000")           // duplicate
        insertV11(db, date, "REACH_WAVE_30")       // duplicate
        insertV11(db, date, "SPEND_5000_WORKSHOP") // duplicate

        // A different date must be untouched by the dedup.
        insertV11(db, "2026-06-11", "WALK_5000")

        AppMigrations.MIGRATION_11_12.migrate(db)

        // Exactly 3 rows survive for the duplicated day, 1 for the other day.
        db.query("SELECT COUNT(*) FROM daily_mission WHERE date = ?", arrayOf(date)).use {
            it.moveToFirst()
            assertEquals("the duplicated day must collapse to one row per missionType", 3, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM daily_mission WHERE date = ?", arrayOf("2026-06-11")).use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }

        // The unique index Room expects now exists.
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf("index_daily_mission_date_missionType"),
        ).use {
            it.moveToFirst()
            assertEquals("the (date, missionType) unique index must exist post-migration", 1, it.getInt(0))
        }

        // And it actually enforces uniqueness — a post-migration duplicate insert is rejected.
        var rejected = false
        try {
            insertV11(db, date, "WALK_5000")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("the unique index must reject a duplicate (date, missionType) insert", rejected)
    }

    @Test
    fun `migration keeps the earliest row's progress when collapsing duplicates`() {
        val db = helper.writableDatabase
        val date = "2026-06-10"

        // The first (MIN id) row carries progress; a later duplicate is fresh. Dedup must keep the
        // earliest row's state so in-progress/claimed work isn't lost when duplicates collapse.
        insertV11(db, date, "WALK_5000", progress = 4200, completed = 0, claimed = 0)
        insertV11(db, date, "WALK_5000", progress = 0, completed = 0, claimed = 0)

        AppMigrations.MIGRATION_11_12.migrate(db)

        db.query(
            "SELECT progress FROM daily_mission WHERE date = ? AND missionType = ?",
            arrayOf(date, "WALK_5000"),
        ).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("the surviving row must retain the furthest progress", 4200, it.getInt(0))
        }
    }

    @Test
    fun `migration does not resurrect a claim taken on a higher-id duplicate`() {
        val db = helper.writableDatabase
        val date = "2026-06-10"

        // The reverse direction: the EARLIEST (MIN id) row is fresh, but the user already claimed
        // the higher-id duplicate before upgrading (both were independently claimable — that was the
        // #127 defect). A naive "keep MIN(id)'s columns" dedup would carry claimed=0 forward and let
        // the mission be claimed AGAIN post-migration (an extra Gem/PS credit surviving the very fix
        // meant to neutralize it). The MAX(claimed) group-aggregate must keep the survivor claimed.
        insertV11(db, date, "WALK_5000", progress = 0, completed = 0, claimed = 0)        // MIN id, fresh
        insertV11(db, date, "WALK_5000", progress = 5000, completed = 1, claimed = 1)     // higher id, claimed

        AppMigrations.MIGRATION_11_12.migrate(db)

        db.query(
            "SELECT claimed, completed, progress FROM daily_mission WHERE date = ? AND missionType = ?",
            arrayOf(date, "WALK_5000"),
        ).use {
            assertEquals("duplicates must collapse to one row", 1, it.count)
            it.moveToFirst()
            assertEquals("an already-claimed duplicate must keep the survivor claimed (no re-credit)", 1, it.getInt(0))
            assertEquals("completed state must carry forward", 1, it.getInt(1))
            assertEquals("the furthest progress must carry forward", 5000, it.getInt(2))
        }
    }
}
