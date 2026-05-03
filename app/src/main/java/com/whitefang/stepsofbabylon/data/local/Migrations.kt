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

    /** All migrations in version order. Wire this into the Room builder. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_7_8)
}
