package com.whitefang.stepsofbabylon.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #381 (`db-1`): **full-chain schema-shape** insurance. Builds a real SQLite DB in the committed **v7**
 * shape, runs the ENTIRE registered migration chain ([AppMigrations.ALL], v7→v12) exactly as Room would,
 * then asserts the resulting live schema — every table's columns (name / affinity / NOT NULL) and every
 * index — matches the committed **`app/schemas/…/12.json`**.
 *
 * ## Why this is distinct from the existing migration tests (NOT a duplicate)
 *  - [Migration11To12Test] / [DataTransformMigrationsTest] (#127 / #222) assert **data transforms** for
 *    individual recreate-table migrations — they check row VALUES, not the terminal schema SHAPE, and only
 *    for the tables they touch.
 *  - [MigrationChainTest] (#237) asserts **version contiguity** of [AppMigrations.ALL] (pure, no DB).
 *  - **Neither** catches a recreate-table `CREATE` (or an `ALTER`) whose column order / type / nullability /
 *    index drifts from the entity: that ships a launch crash for every upgrading user, yet passes every
 *    current test. This test closes exactly that gap across the whole chain and EVERY v12 table.
 *
 * ## Why the manual `SupportSQLiteOpenHelper` idiom (not `MigrationTestHelper`)
 * `androidx.room.testing.MigrationTestHelper` requires a real `android.app.Instrumentation` and loads the
 * schema JSONs from the instrumentation asset path — neither is available on this fast JVM/Robolectric lane
 * (the module has always avoided it for that reason). Instead we drive the migrations directly against a
 * real SQLite DB, and read the committed schema JSON straight off disk (`app/schemas/`) as the source of
 * truth for BOTH the v7 build and the v12 assertions — no hand-transcribed DDL to drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FullChainMigrationSchemaTest {
    private lateinit var helper: SupportSQLiteOpenHelper

    @After
    fun tearDown() = helper.close()

    private val schemaDir =
        File("schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase")

    private fun readSchema(version: Int): JSONObject {
        val f = File(schemaDir, "$version.json")
        assertTrue(
            "schema JSON not found at ${f.absolutePath} (working dir = ${File(".").absolutePath})",
            f.isFile,
        )
        return JSONObject(f.readText()).getJSONObject("database")
    }

    /** Entities in the committed schema for [version], keyed by table name. */
    private fun entities(version: Int): Map<String, JSONObject> {
        val arr = readSchema(version).getJSONArray("entities")
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .associateBy { it.getString("tableName") }
    }

    /** Builds the v7 DB by executing every committed v7 `createSql` (+ any v7 indices) — no manual DDL. */
    private fun openV7(): SupportSQLiteDatabase {
        val v7 = entities(7)
        val callback =
            object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v7.values.forEach { entity ->
                        db.execSQL(
                            entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")),
                        )
                        val indices = entity.optJSONArray("indices") ?: return@forEach
                        for (i in 0 until indices.length()) {
                            db.execSQL(
                                indices
                                    .getJSONObject(i)
                                    .getString("createSql")
                                    .replace("\${TABLE_NAME}", entity.getString("tableName")),
                            )
                        }
                    }
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    // No-op: this test drives AppMigrations.ALL explicitly below, not via the open helper.
                }
            }
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null) // in-memory
                .callback(callback)
                .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    /** Actual `(columnName -> "affinity|notNull")` for a live table, from `PRAGMA table_info`. */
    private fun liveColumns(
        db: SupportSQLiteDatabase,
        table: String,
    ): Map<String, String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val out = mutableMapOf<String, String>()
            val nameIdx = c.getColumnIndexOrThrow("name")
            val typeIdx = c.getColumnIndexOrThrow("type")
            val notNullIdx = c.getColumnIndexOrThrow("notnull")
            while (c.moveToNext()) {
                // SQLite reports declared type; Room's "affinity" is the same token (INTEGER/TEXT/REAL/BLOB).
                out[c.getString(nameIdx)] = "${c.getString(typeIdx)}|${c.getInt(notNullIdx)}"
            }
            out
        }

    /**
     * Expected `(columnName -> "affinity|notNull")` for a table, from the committed schema JSON.
     * Room OMITS the `notNull` key for nullable columns (it only emits it when `true`), so a missing key
     * means nullable — read it with `optBoolean(..., false)`, never `getBoolean` (which throws on absence).
     */
    private fun expectedColumns(entity: JSONObject): Map<String, String> {
        val fields = entity.getJSONArray("fields")
        return (0 until fields.length()).associate {
            val f = fields.getJSONObject(it)
            f.getString("columnName") to "${f.getString("affinity")}|${if (f.optBoolean("notNull", false)) 1 else 0}"
        }
    }

    /** Actual index-name set for a table (excluding SQLite auto-indices), from `PRAGMA index_list`. */
    private fun liveIndices(
        db: SupportSQLiteDatabase,
        table: String,
    ): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            val out = mutableSetOf<String>()
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                val name = c.getString(nameIdx)
                if (!name.startsWith("sqlite_autoindex_")) out += name
            }
            out
        }

    private fun expectedIndices(entity: JSONObject): Set<String> {
        val arr = entity.optJSONArray("indices") ?: return emptySet()
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }.toSet()
    }

    @Test
    fun `full v7 to v12 chain produces the committed v12 schema shape for every table`() {
        val db = openV7()

        // Run the whole registered chain in version order, exactly as Room would.
        AppMigrations.ALL.sortedBy { it.startVersion }.forEach { it.migrate(db) }

        val expectedEntities = entities(12)

        // 1. Table SET matches (catches a table that a migration failed to create/rename, e.g. billing_receipt).
        val liveTables =
            db
                .query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                        "AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'room_master_table'",
                ).use { c ->
                    val out = mutableSetOf<String>()
                    while (c.moveToNext()) out += c.getString(0)
                    out
                }
        assertEquals(
            "post-migration table set must equal the committed v12 entity set",
            expectedEntities.keys,
            liveTables,
        )

        // 2. Per-table column shape + indices match the committed v12 JSON.
        expectedEntities.forEach { (table, entity) ->
            assertEquals(
                "column shape (name|affinity|notNull) of `$table` must match v12 schema",
                expectedColumns(entity),
                liveColumns(db, table),
            )
            assertEquals(
                "index set of `$table` must match v12 schema",
                expectedIndices(entity),
                liveIndices(db, table),
            )
        }
    }
}
