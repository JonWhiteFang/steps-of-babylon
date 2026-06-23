package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #237: guards [AppMigrations.validateChain] (pure) and pins the live migration chain against the running
 * [AppDatabase] version. The pure-logic cases need no Android; one Robolectric case reads the authoritative
 * version from a built DB (the `@Database` annotation is `@Retention(CLASS)`, so runtime annotation
 * reflection returns null — `db.openHelper.readableDatabase.version` == `PRAGMA user_version` is the real
 * source of truth, baked into Room's generated code).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationChainTest {
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    /** A trivial no-op migration with the given version edge, for chain-shape tests. */
    private fun mig(
        from: Int,
        to: Int,
    ): Migration =
        object : Migration(from, to) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op */ }
        }

    // ---- The real, shipped chain ----

    @Test
    fun `live migration chain is contiguous and tops out at AppDatabase version`() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        // Force the DB open so user_version is initialized, then read the authoritative live version.
        val liveVersion = db!!.openHelper.readableDatabase.version

        val problems = AppMigrations.validateChain(AppMigrations.ALL, liveVersion)

        assertTrue(
            "Live migration chain has problems: $problems",
            problems.isEmpty(),
        )
        // Sanity: the version we read matches the entity-declared version (12 today).
        assertEquals(12, liveVersion)
    }

    // ---- Pure-logic cases (no Android needed, but run under the same runner for simplicity) ----

    @Test
    fun `valid contiguous chain from floor yields no problems`() {
        val chain = arrayOf(mig(7, 8), mig(8, 9), mig(9, 10))
        assertTrue(AppMigrations.validateChain(chain, liveVersion = 10).isEmpty())
    }

    @Test
    fun `missing top migration is detected (the forgotten-registration case)`() {
        // AppDatabase bumped to v11 but MIGRATION_10_11 never added/registered.
        val chain = arrayOf(mig(7, 8), mig(8, 9), mig(9, 10))
        val problems = AppMigrations.validateChain(chain, liveVersion = 11)
        assertTrue("Expected a top-of-chain problem, got $problems", problems.any { it.contains("tops out") })
    }

    @Test
    fun `gap in the middle of the chain is detected`() {
        val chain = arrayOf(mig(7, 8), mig(9, 10)) // 8->9 missing
        val problems = AppMigrations.validateChain(chain, liveVersion = 10)
        assertTrue("Expected a gap problem, got $problems", problems.any { it.contains("Gap or overlap") })
    }

    @Test
    fun `multi-version-step migration is rejected`() {
        val chain = arrayOf(mig(7, 9)) // skips a version in one object
        val problems = AppMigrations.validateChain(chain, liveVersion = 9)
        assertTrue("Expected a +1-step problem, got $problems", problems.any { it.contains("+1 step") })
    }

    @Test
    fun `chain not starting at the floor is detected`() {
        val chain = arrayOf(mig(8, 9), mig(9, 10))
        val problems = AppMigrations.validateChain(chain, liveVersion = 10, floor = 7)
        assertTrue("Expected a floor problem, got $problems", problems.any { it.contains("floor") })
    }

    @Test
    fun `empty migration set is rejected`() {
        val problems = AppMigrations.validateChain(emptyArray(), liveVersion = 12)
        assertTrue(problems.any { it.contains("empty") })
    }
}
