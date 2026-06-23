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
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `player profile round-trip`() =
        runTest {
            val entity = PlayerProfileEntity(gems = 500, powerStones = 25, currentTier = 3)
            db.playerProfileDao().upsert(entity)
            val loaded = db.playerProfileDao().get().first()
            assertNotNull(loaded)
            assertEquals(500L, loaded!!.gems)
            assertEquals(25L, loaded.powerStones)
            assertEquals(3, loaded.currentTier)
        }

    @Test
    fun `daily step record round-trip with escrow fields`() =
        runTest {
            val entity =
                DailyStepRecordEntity(
                    date = "2026-03-12",
                    sensorSteps = 8000,
                    creditedSteps = 7500,
                    escrowSteps = 500,
                    escrowSyncCount = 1,
                )
            db.dailyStepDao().upsert(entity)
            val loaded = db.dailyStepDao().getByDateOnce("2026-03-12")
            assertNotNull(loaded)
            assertEquals(500L, loaded!!.escrowSteps)
            assertEquals(1, loaded.escrowSyncCount)
        }

    @Test
    fun `workshop upgrade round-trip`() =
        runTest {
            val entity = WorkshopUpgradeEntity(upgradeType = "DAMAGE", level = 5)
            db.workshopDao().upsert(entity)
            val all = db.workshopDao().getAll().first()
            val loaded = all.find { it.upgradeType == "DAMAGE" }
            assertNotNull(loaded)
            assertEquals(5, loaded!!.level)
        }

    @Test
    fun `billing receipt round-trip with all optional fields`() =
        runTest {
            // Exercises the v8→v9 schema added by C.5 PR 1 / MIGRATION_8_9. Hits every column
            // including the nullable orderId / grantedAt / acknowledgedAt / consumedAt so a later
            // DDL drift (e.g. a forgotten `@ColumnInfo(defaultValue = ...)`) regression-fails here.
            val entity =
                BillingReceiptEntity(
                    purchaseToken = "GPB.token.0001",
                    orderId = "GPA.1234-5678-9012-3456",
                    productId = "gem_pack_medium",
                    purchaseTime = 1_720_000_000L,
                    granted = true,
                    grantedAt = 1_720_000_001L,
                    acknowledged = true,
                    acknowledgedAt = 1_720_000_002L,
                    consumed = true,
                    consumedAt = 1_720_000_003L,
                )
            db.billingReceiptDao().upsert(entity)

            val loaded = db.billingReceiptDao().getByToken("GPB.token.0001")
            assertNotNull(loaded)
            assertEquals("GPA.1234-5678-9012-3456", loaded!!.orderId)
            assertEquals("gem_pack_medium", loaded.productId)
            assertEquals(1_720_000_000L, loaded.purchaseTime)
            assertEquals(true, loaded.granted)
            assertEquals(1_720_000_001L, loaded.grantedAt)
            assertEquals(true, loaded.acknowledged)
            assertEquals(1_720_000_002L, loaded.acknowledgedAt)
            assertEquals(true, loaded.consumed)
            assertEquals(1_720_000_003L, loaded.consumedAt)
        }
}
