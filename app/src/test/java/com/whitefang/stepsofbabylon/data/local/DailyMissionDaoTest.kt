package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #127: validates the daily-mission uniqueness guard against a real (in-memory) SQLite DB. The
 * fakes can't model the `(date, missionType)` unique index or the `onConflict = IGNORE` insert
 * semantics, so these prove the actual schema + DAO prevent the duplicate-mission defect: two
 * concurrent generations both passing the `getByDateOnce().isEmpty()` check (TOCTOU) used to
 * insert 6 independently-claimable rows for one day, inflating Gem/Power-Stone payouts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DailyMissionDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var missionDao: DailyMissionDao

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        missionDao = db.dailyMissionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `inserting a duplicate date and missionType keeps only one row`() =
        runTest {
            val date = "2026-06-10"
            missionDao.insert(DailyMissionEntity(date = date, missionType = "WALK_5000", target = 5000))
            // A second insert of the same (date, missionType) — e.g. a concurrent re-generation — must
            // be ignored by the unique index rather than producing a second claimable row.
            missionDao.insert(DailyMissionEntity(date = date, missionType = "WALK_5000", target = 5000))

            assertEquals(
                "the (date, missionType) unique index must collapse a duplicate insert to one row",
                1,
                missionDao.getByDateOnce(date).size,
            )
        }

    @Test
    fun `distinct missionTypes for the same date all persist`() =
        runTest {
            val date = "2026-06-10"
            missionDao.insert(DailyMissionEntity(date = date, missionType = "WALK_5000", target = 5000))
            missionDao.insert(DailyMissionEntity(date = date, missionType = "REACH_WAVE_30", target = 30))
            missionDao.insert(DailyMissionEntity(date = date, missionType = "SPEND_5000_WORKSHOP", target = 5000))

            // The index is on (date, missionType) — the three legitimate distinct daily missions must
            // all survive; only same-tuple duplicates are rejected.
            assertEquals(3, missionDao.getByDateOnce(date).size)
        }

    @Test
    fun `two raced generation batches for the same date yield three missions not six`() =
        runTest {
            val date = "2026-06-10"

            // Reproduce the TOCTOU outcome at the layer where it actually bites — the DAO. Two
            // generators that both passed `getByDateOnce().isEmpty()` before either committed each
            // insert their full batch. Because GenerateDailyMissions seeds its RNG from the date, both
            // batches are byte-identical per category, so the second batch is three exact-duplicate
            // (date, missionType) rows. Inserting both batches via the DAO directly (bypassing the
            // use-case read-guard, which is exactly what the race defeats) must still leave one row per
            // category — the DB-level uniqueness guarantee, not the unreliable read-then-write check.
            repeat(2) {
                missionDao.insert(DailyMissionEntity(date = date, missionType = "WALK_5000", target = 5000))
                missionDao.insert(DailyMissionEntity(date = date, missionType = "REACH_WAVE_30", target = 30))
                missionDao.insert(DailyMissionEntity(date = date, missionType = "SPEND_5000_WORKSHOP", target = 5000))
            }

            assertEquals(
                "a raced duplicate batch must not inflate the mission set beyond one per missionType",
                3,
                missionDao.getByDateOnce(date).size,
            )
        }

    @Test
    fun `generateForDate is idempotent — a second call adds no duplicate rows`() =
        runTest {
            val date = "2026-06-10"
            // Two byte-identical batches (the date-seeded generator produces the same tuples on a
            // raced re-generation). generateForDate's early-return guards the common case; the unique
            // index + IGNORE backs the raced case. Either way a second full batch must add nothing.
            val batch =
                listOf(
                    DailyMissionEntity(date = date, missionType = "WALK_5000", target = 5000),
                    DailyMissionEntity(date = date, missionType = "REACH_WAVE_30", target = 30),
                    DailyMissionEntity(date = date, missionType = "SPEND_5000_WORKSHOP", target = 5000),
                )

            missionDao.generateForDate(date, batch)
            missionDao.generateForDate(date, batch)

            assertEquals(
                "generateForDate must not duplicate an already-generated day",
                3,
                missionDao.getByDateOnce(date).size,
            )
        }
}
