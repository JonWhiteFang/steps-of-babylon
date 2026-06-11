package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric in-memory Room test for [DailyStepDao].
 *
 * Primary purpose: regression guard for the fresh-install / first-kill crash where
 * [DailyStepDao.incrementBattleSteps] would throw `SQLiteConstraintException: NOT NULL
 * constraint failed: daily_step_record.sensorSteps` because its UPSERT SQL only supplied
 * `date + battleStepsEarned` and every other column in the table is `NOT NULL` with no SQL
 * `DEFAULT`. SQLite evaluates NOT NULL before the `ON CONFLICT(date)` clause can resolve a
 * UNIQUE conflict, so the ON CONFLICT path could never save the partial INSERT — the
 * INSERT half aborted first on the NOT NULL check.
 *
 * Fix: expand the INSERT half of the UPSERT to supply every NOT NULL column explicitly
 * (zeros for numeric / `'{}'` for the JSON-encoded `activityMinutes` map). The UPDATE half
 * on conflict still touches only `battleStepsEarned`, preserving existing sensor / HC /
 * escrow data.
 *
 * Bug was latent since B.2 PR 2 (battle-step-credit atomicity) — unit tests used
 * [com.whitefang.stepsofbabylon.fakes.FakeDailyStepDao], a plain in-memory Map with no
 * `NOT NULL` enforcement. Surfaced during C.6 PR 2 device-track verification when a
 * fresh-install emulator crashed on the first enemy kill, before any step sensor tick had
 * populated today's row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DailyStepDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DailyStepDao
    private lateinit var playerDao: PlayerProfileDao

    private val today = "2026-05-12"

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.dailyStepDao()
        playerDao = db.playerProfileDao()
    }

    @After
    fun tearDown() { db.close() }

    /**
     * Direct regression test for the underlying SQL: `incrementBattleSteps` must succeed
     * on an empty `daily_step_record` table. Before the fix, this threw
     * `SQLiteConstraintException: NOT NULL constraint failed: daily_step_record.sensorSteps`.
     */
    @Test
    fun `incrementBattleSteps succeeds on empty table`() = runTest {
        dao.incrementBattleSteps(today, 5L)

        assertEquals(5L, dao.getBattleStepsEarned(today))
        val row = dao.getByDateOnce(today)!!
        // INSERT branch took effect; all other NOT NULL columns populated via explicit zeros.
        assertEquals(5L, row.battleStepsEarned)
        assertEquals(0L, row.sensorSteps)
        assertEquals(0L, row.healthConnectSteps)
        assertEquals(0L, row.creditedSteps)
        assertEquals(0L, row.escrowSteps)
        assertEquals(0, row.escrowSyncCount)
        assertEquals(emptyMap<String, Int>(), row.activityMinutes)
        assertEquals(0L, row.stepEquivalents)
    }

    /**
     * Fresh-install first-kill happy path through the full atomic method.
     */
    @Test
    fun `creditBattleStepsAtomic credits successfully on empty table`() = runTest {
        playerDao.upsert(PlayerProfileEntity(id = 1))

        val credited = dao.creditBattleStepsAtomic(
            date = today,
            requested = 5L,
            dailyCap = 2_000L,
            playerDao = playerDao,
        )

        assertEquals(5L, credited)
        assertEquals(5L, dao.getBattleStepsEarned(today))
        val row = dao.getByDateOnce(today)
        assertNotNull("row for today must exist after first credit", row)
        assertEquals(5L, row!!.battleStepsEarned)
        assertEquals(0L, row.sensorSteps)
        assertEquals(emptyMap<String, Int>(), row.activityMinutes)

        val profile = playerDao.get().firstOrNull()!!
        assertEquals(5L, profile.currentStepBalance)
        assertEquals(5L, profile.totalStepsEarned)
    }

    /**
     * The ON CONFLICT(date) UPDATE branch must touch only `battleStepsEarned` — sensor
     * data populated earlier in the day by the step sensor path must survive intact.
     */
    @Test
    fun `creditBattleStepsAtomic preserves existing sensor data`() = runTest {
        playerDao.upsert(PlayerProfileEntity(id = 1))
        // Simulate a step sensor tick earlier in the day populating sensor / credited fields.
        dao.upsert(
            DailyStepRecordEntity(
                date = today,
                sensorSteps = 1_234L,
                creditedSteps = 1_234L,
                activityMinutes = mapOf("WALKING" to 12),
            ),
        )

        val credited = dao.creditBattleStepsAtomic(
            date = today,
            requested = 7L,
            dailyCap = 2_000L,
            playerDao = playerDao,
        )

        assertEquals(7L, credited)
        val row = dao.getByDateOnce(today)!!
        // Battle counter advanced.
        assertEquals(7L, row.battleStepsEarned)
        // Sensor data was NOT clobbered by the ON CONFLICT(date) DO UPDATE branch.
        assertEquals(1_234L, row.sensorSteps)
        assertEquals(1_234L, row.creditedSteps)
        assertEquals(mapOf("WALKING" to 12), row.activityMinutes)
    }

    /**
     * Cap enforcement path: when the request would exceed the daily cap, only the remaining
     * headroom is credited to both the counter and the wallet.
     */
    @Test
    fun `creditBattleStepsAtomic returns partial credit near the cap`() = runTest {
        playerDao.upsert(PlayerProfileEntity(id = 1))
        // Pre-fill the counter to 3 below the cap.
        dao.creditBattleStepsAtomic(today, requested = 1_997L, dailyCap = 2_000L, playerDao = playerDao)

        val credited = dao.creditBattleStepsAtomic(
            date = today,
            requested = 100L,
            dailyCap = 2_000L,
            playerDao = playerDao,
        )

        assertEquals("only 3 headroom left under the cap", 3L, credited)
        assertEquals(2_000L, dao.getBattleStepsEarned(today))
        val profile = playerDao.get().firstOrNull()!!
        assertEquals(2_000L, profile.currentStepBalance)
    }

    @Test
    fun `creditBattleStepsAtomic returns zero when cap already exhausted`() = runTest {
        playerDao.upsert(PlayerProfileEntity(id = 1))
        dao.creditBattleStepsAtomic(today, requested = 2_000L, dailyCap = 2_000L, playerDao = playerDao)

        val credited = dao.creditBattleStepsAtomic(
            date = today,
            requested = 50L,
            dailyCap = 2_000L,
            playerDao = playerDao,
        )

        assertEquals(0L, credited)
        assertEquals(2_000L, dao.getBattleStepsEarned(today))
    }

    // ---- #121: column-targeted daily-step upserts (lost-update fix) ----
    //
    // The per-field daily_step_record writers used to be a non-atomic read-copy-upsert
    // (getByDateOnce -> upsert(existing.copy(field=...))), so when two independent
    // components (sensor service vs HC validator vs worker) wrote different columns of the
    // same date row concurrently, the @Upsert rewrote ALL columns from each writer's stale
    // snapshot and the second writer clobbered the first writer's column. These guards pin
    // the column-targeted SQL: each writer touches only its own columns (ON CONFLICT(date)
    // DO UPDATE SET <those columns>), so disjoint writers can no longer clobber each other.

    @Test
    fun `setSensorAndCreditedSteps creates the row on an empty table`() = runTest {
        dao.setSensorAndCreditedSteps(today, sensorSteps = 500L, creditedSteps = 480L)
        val row = dao.getByDateOnce(today)!!
        assertEquals(500L, row.sensorSteps)
        assertEquals(480L, row.creditedSteps)
        // All other NOT NULL columns defaulted.
        assertEquals(0L, row.healthConnectSteps)
        assertEquals(emptyMap<String, Int>(), row.activityMinutes)
    }

    @Test
    fun `setHealthConnectSteps creates the row on an empty table`() = runTest {
        dao.setHealthConnectSteps(today, healthConnectSteps = 321L)
        val row = dao.getByDateOnce(today)!!
        assertEquals(321L, row.healthConnectSteps)
        assertEquals(0L, row.sensorSteps)
    }

    @Test
    fun `setActivityMinutes creates the row on an empty table`() = runTest {
        dao.setActivityMinutes(today, activityMinutes = mapOf("WALKING" to 30), stepEquivalents = 3_000L)
        val row = dao.getByDateOnce(today)!!
        assertEquals(mapOf("WALKING" to 30), row.activityMinutes)
        assertEquals(3_000L, row.stepEquivalents)
        assertEquals(0L, row.sensorSteps)
    }

    @Test
    fun `setEscrow creates the row on an empty table`() = runTest {
        dao.setEscrow(today, escrowSteps = 90L, escrowSyncCount = 2)
        val row = dao.getByDateOnce(today)!!
        assertEquals(90L, row.escrowSteps)
        assertEquals(2, row.escrowSyncCount)
        assertEquals(0L, row.creditedSteps)
    }

    @Test
    fun `setHealthConnectSteps preserves sensor and credited columns`() = runTest {
        dao.setSensorAndCreditedSteps(today, sensorSteps = 1_000L, creditedSteps = 950L)
        dao.setHealthConnectSteps(today, healthConnectSteps = 800L)
        val row = dao.getByDateOnce(today)!!
        // HC column updated...
        assertEquals(800L, row.healthConnectSteps)
        // ...without clobbering the sensor/credited columns the other writer set.
        assertEquals(1_000L, row.sensorSteps)
        assertEquals(950L, row.creditedSteps)
    }

    /**
     * The core #121 lost-update repro. Two disjoint-column writers each read the SAME stale
     * snapshot (no row yet) and then write. With the old read-copy-upsert the second
     * `upsert(existing.copy(...))` rewrote every column from its stale snapshot, so whichever
     * landed second clobbered the first writer's column back to 0. The column-targeted SQL
     * makes each writer touch only its own columns, so BOTH survive regardless of order.
     */
    @Test
    fun `disjoint-column writers from the same stale snapshot do not clobber each other`() = runTest {
        // Both writers observe an empty row for `today` (the interleaving the audit describes).
        // Writer A: the sensor path. Writer B: the HC validator path.
        dao.setSensorAndCreditedSteps(today, sensorSteps = 1_234L, creditedSteps = 1_234L)
        dao.setHealthConnectSteps(today, healthConnectSteps = 5_678L)
        dao.setActivityMinutes(today, activityMinutes = mapOf("RUNNING" to 15), stepEquivalents = 1_500L)
        dao.setEscrow(today, escrowSteps = 42L, escrowSyncCount = 1)

        val row = dao.getByDateOnce(today)!!
        // Every writer's column survives — no field was reset to its default by a later writer.
        assertEquals("sensor credit must survive the HC write", 1_234L, row.sensorSteps)
        assertEquals(1_234L, row.creditedSteps)
        assertEquals(5_678L, row.healthConnectSteps)
        assertEquals(mapOf("RUNNING" to 15), row.activityMinutes)
        assertEquals(1_500L, row.stepEquivalents)
        assertEquals(42L, row.escrowSteps)
        assertEquals(1, row.escrowSyncCount)
    }

    @Test
    fun `column-targeted writers preserve the battle and boss counters`() = runTest {
        // A battle kill + boss kill populate their counters early in the day.
        dao.incrementBattleSteps(today, 7L)
        dao.incrementBossPs(today, 3L)
        // Then the sensor/HC/activity/escrow writers run.
        dao.setSensorAndCreditedSteps(today, sensorSteps = 200L, creditedSteps = 200L)
        dao.setHealthConnectSteps(today, healthConnectSteps = 180L)
        dao.setActivityMinutes(today, activityMinutes = mapOf("WALKING" to 5), stepEquivalents = 500L)
        dao.setEscrow(today, escrowSteps = 10L, escrowSyncCount = 1)

        val row = dao.getByDateOnce(today)!!
        // The disjoint daily-step columns must not have reset the battle/boss counters.
        assertEquals(7L, row.battleStepsEarned)
        assertEquals(3L, row.bossPsEarnedToday)
        assertEquals(200L, row.sensorSteps)
        assertEquals(180L, row.healthConnectSteps)
    }

    @Test
    fun `repeated setSensorAndCreditedSteps overwrites (not increments) its own columns`() = runTest {
        // The daily-step writers persist cumulative totals, so the column is SET (overwrite),
        // not incremented — a second tick with a higher cumulative total replaces the first.
        dao.setSensorAndCreditedSteps(today, sensorSteps = 100L, creditedSteps = 100L)
        dao.setSensorAndCreditedSteps(today, sensorSteps = 250L, creditedSteps = 240L)
        val row = dao.getByDateOnce(today)!!
        assertEquals(250L, row.sensorSteps)
        assertEquals(240L, row.creditedSteps)
    }
}
