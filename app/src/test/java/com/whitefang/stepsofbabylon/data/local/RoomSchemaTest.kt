package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoomSchemaTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `player profile round-trip`() = runTest {
        val entity = PlayerProfileEntity(gems = 500, powerStones = 25, currentTier = 3)
        db.playerProfileDao().upsert(entity)
        val loaded = db.playerProfileDao().get().first()
        assertNotNull(loaded)
        assertEquals(500L, loaded!!.gems)
        assertEquals(25L, loaded.powerStones)
        assertEquals(3, loaded.currentTier)
    }

    @Test
    fun `daily step record round-trip with escrow fields`() = runTest {
        val entity = DailyStepRecordEntity(
            date = "2026-03-12", sensorSteps = 8000, creditedSteps = 7500,
            escrowSteps = 500, escrowSyncCount = 1,
        )
        db.dailyStepDao().upsert(entity)
        val loaded = db.dailyStepDao().getByDateOnce("2026-03-12")
        assertNotNull(loaded)
        assertEquals(500L, loaded!!.escrowSteps)
        assertEquals(1, loaded.escrowSyncCount)
    }

    @Test
    fun `workshop upgrade round-trip`() = runTest {
        val entity = WorkshopUpgradeEntity(upgradeType = "DAMAGE", level = 5)
        db.workshopDao().upsert(entity)
        val all = db.workshopDao().getAll().first()
        val loaded = all.find { it.upgradeType == "DAMAGE" }
        assertNotNull(loaded)
        assertEquals(5, loaded!!.level)
    }
}
