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
}
