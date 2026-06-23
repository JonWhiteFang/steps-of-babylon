package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #122: validates the guarded-claim SQL against a real (in-memory) SQLite DB — the fakes can
 * only approximate the `AND claimed = 0` + rows-affected semantics, so these tests prove the
 * actual queries behave idempotently. Covers the two claim DAOs added in #122 plus the
 * pre-existing guarded currency deducts the economy fix now relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GuardedClaimDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var encounterDao: WalkingEncounterDao
    private lateinit var missionDao: DailyMissionDao
    private lateinit var playerDao: PlayerProfileDao

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        encounterDao = db.walkingEncounterDao()
        missionDao = db.dailyMissionDao()
        playerDao = db.playerProfileDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `walking encounter markClaimed returns 1 then 0 on a repeat claim`() =
        runTest {
            val id =
                encounterDao
                    .insert(
                        WalkingEncounterEntity(
                            triggerType = "RANDOM",
                            rewardType = "GEMS",
                            rewardAmount = 25,
                            createdAt = 1000,
                        ),
                    ).toInt()

            assertEquals("first claim must affect the row", 1, encounterDao.markClaimed(id, claimedAt = 2000))
            assertEquals("second claim must affect no row", 0, encounterDao.markClaimed(id, claimedAt = 3000))
        }

    @Test
    fun `daily mission markClaimed returns 1 then 0 on a repeat claim`() =
        runTest {
            missionDao.insert(
                DailyMissionEntity(
                    date = "2026-06-10",
                    missionType = "WALK_5000",
                    target = 5000,
                    progress = 5000,
                    completed = true,
                ),
            )
            val id = missionDao.getByDateOnce("2026-06-10").first().id

            assertEquals("first claim must affect the row", 1, missionDao.markClaimed(id))
            assertEquals("second claim must affect no row", 0, missionDao.markClaimed(id))
        }

    @Test
    fun `guarded currency deducts return rows-affected reflecting sufficiency`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(currentStepBalance = 100, gems = 100, powerStones = 5))

            // Sufficient → 1 and the balance moves.
            assertEquals(1, playerDao.adjustStepBalanceIfSufficient(100))
            assertEquals(1, playerDao.spendGemsAtomic(40))
            assertEquals(1, playerDao.spendPowerStonesAtomic(5))

            // Insufficient → 0 and no mutation.
            assertEquals(0, playerDao.adjustStepBalanceIfSufficient(1))
            assertEquals(0, playerDao.spendGemsAtomic(61))
            assertEquals(0, playerDao.spendPowerStonesAtomic(1))

            val profile = playerDao.get().first()
            assertEquals(0L, profile!!.currentStepBalance)
            assertEquals(60L, profile.gems)
            assertEquals(0L, profile.powerStones)
        }
}
