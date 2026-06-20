package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * #252 — the guarded-deduct / one-shot-claim atomic DAOs were only ever tested *serially*
 * ([GuardedClaimDaoTest]). A serial test passes even if a guarded query were rewritten into a
 * non-atomic read-then-write, because the race window never opens single-threaded.
 *
 * This fires N concurrent workers (real threads, [CountDownLatch] start-gate so they collide) at a
 * balance/claim that affords **exactly one** success, and asserts the atomic invariant under
 * contention: exactly one winner, the rest rejected, and the final state never over-spent or
 * double-credited.
 *
 * Why a **file-based** DB and not `inMemoryDatabaseBuilder` (wave spec F1): a `:memory:` SQLite DB
 * is a single private connection with no WAL / no connection pool, so Room serializes all access and
 * N threads cannot physically contend. A file-backed DB gets Room's real connection pool, so the
 * concurrent writers genuinely race. The DB is plain Room (SQLCipher is wired only in DI), so this
 * opens unencrypted framework SQLite with no passphrase — exactly as [GuardedClaimDaoTest] does.
 *
 * The assertions are **invariant-based, never timing-based** (`successes == 1 && balance == expected
 * && balance >= 0`), so they are deterministic regardless of how the scheduler interleaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AtomicDaoConcurrencyTest {

    private lateinit var db: AppDatabase
    private lateinit var dbFile: File
    private lateinit var playerDao: PlayerProfileDao
    private lateinit var encounterDao: WalkingEncounterDao
    private lateinit var missionDao: DailyMissionDao
    private lateinit var milestoneDao: MilestoneDao
    private lateinit var workshopDao: WorkshopDao

    private val workerCount = 12

    @Before
    fun setup() {
        dbFile = File.createTempFile("sob-concurrency", ".db")
        db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbFile.absolutePath,
        ).build()
        playerDao = db.playerProfileDao()
        encounterDao = db.walkingEncounterDao()
        missionDao = db.dailyMissionDao()
        milestoneDao = db.milestoneDao()
        workshopDao = db.workshopDao()
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    /**
     * Runs [block] on [workerCount] threads released simultaneously by a start-gate latch, and
     * returns every worker's result. Each worker bridges to the suspend DAO call via [runBlocking].
     */
    private fun <T> runContended(block: suspend () -> T): List<T> {
        val startGate = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<T>()
        val threads = (0 until workerCount).map {
            Thread {
                startGate.await()
                results += runBlocking { block() }
            }.also { it.start() }
        }
        startGate.countDown()
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }
        return results.toList()
    }

    @Test
    fun `adjustStepBalanceIfSufficient lets exactly one of N concurrent spends win`() {
        val cost = 100L
        runBlocking { playerDao.upsert(PlayerProfileEntity(id = 1, currentStepBalance = cost)) }

        val rows = runContended { playerDao.adjustStepBalanceIfSufficient(cost) }

        assertEquals("exactly one spend may succeed", 1, rows.count { it == 1 })
        assertEquals("all others must be rejected", workerCount - 1, rows.count { it == 0 })
        val balance = runBlocking { playerDao.get().first()!!.currentStepBalance }
        assertEquals("balance debited exactly once", 0L, balance)
        assertTrue("balance must never go negative", balance >= 0L)
    }

    @Test
    fun `spendGemsAtomic lets exactly one win and increments totalGemsSpent once`() {
        val amount = 50L
        runBlocking { playerDao.upsert(PlayerProfileEntity(id = 1, gems = amount)) }

        val rows = runContended { playerDao.spendGemsAtomic(amount) }

        assertEquals(1, rows.count { it == 1 })
        val profile = runBlocking { playerDao.get().first()!! }
        assertEquals("gems debited exactly once", 0L, profile.gems)
        assertEquals("totalGemsSpent incremented exactly once", amount, profile.totalGemsSpent)
        assertTrue(profile.gems >= 0L)
    }

    @Test
    fun `spendPowerStonesAtomic lets exactly one win and increments totalPowerStonesSpent once`() {
        val amount = 5L
        runBlocking { playerDao.upsert(PlayerProfileEntity(id = 1, powerStones = amount)) }

        val rows = runContended { playerDao.spendPowerStonesAtomic(amount) }

        assertEquals(1, rows.count { it == 1 })
        val profile = runBlocking { playerDao.get().first()!! }
        assertEquals("power stones debited exactly once", 0L, profile.powerStones)
        assertEquals(amount, profile.totalPowerStonesSpent)
        assertTrue(profile.powerStones >= 0L)
    }

    @Test
    fun `WalkingEncounter markClaimed credits exactly one concurrent claimer`() {
        val id = runBlocking {
            encounterDao.insert(
                WalkingEncounterEntity(
                    triggerType = "WALK", rewardType = "GEMS", rewardAmount = 10, createdAt = 1_000L,
                ),
            ).toInt()
        }

        val rows = runContended { encounterDao.markClaimed(id, claimedAt = 2_000L) }

        assertEquals("one-shot claim: exactly one success", 1, rows.count { it == 1 })
        assertEquals(workerCount - 1, rows.count { it == 0 })
    }

    @Test
    fun `DailyMission markClaimed credits exactly one concurrent claimer`() {
        runBlocking {
            missionDao.insert(
                DailyMissionEntity(date = "2026-06-20", missionType = "WALK", target = 100, completed = true),
            )
        }
        val missionId = runBlocking { missionDao.getByDateOnce("2026-06-20").first().id }

        val rows = runContended { missionDao.markClaimed(missionId) }

        assertEquals(1, rows.count { it == 1 })
        assertEquals(workerCount - 1, rows.count { it == 0 })
    }

    @Test
    fun `claimMilestoneAtomic credits the wallet exactly once under contention`() {
        runBlocking { playerDao.upsert(PlayerProfileEntity(id = 1)) }
        val gemsReward = 100L

        val results = runContended {
            milestoneDao.claimMilestoneAtomic(
                milestoneId = "FIRST_BLOOD", gems = gemsReward, powerStones = 0L,
                claimedAt = 3_000L, playerDao = playerDao,
            )
        }

        assertEquals("@Transaction composite: exactly one claim transitions unclaimed→claimed",
            1, results.count { it })
        val profile = runBlocking { playerDao.get().first()!! }
        assertEquals("wallet credited exactly once (no double-credit race)", gemsReward, profile.gems)
        assertEquals(gemsReward, profile.totalGemsEarned)
    }

    @Test
    fun `purchaseUpgradeAtomic debits Steps exactly once under contention`() {
        val cost = 250L
        runBlocking { playerDao.upsert(PlayerProfileEntity(id = 1, currentStepBalance = cost)) }

        val results = runContended {
            workshopDao.purchaseUpgradeAtomic(
                type = "ATTACK_DAMAGE", newLevel = 1, cost = cost, playerDao = playerDao,
            )
        }

        assertEquals("exactly one purchase may afford the single-cost balance", 1, results.count { it })
        val balance = runBlocking { playerDao.get().first()!!.currentStepBalance }
        assertEquals("Steps debited exactly once", 0L, balance)
        assertTrue("balance must never go negative", balance >= 0L)
    }
}
