package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric in-memory Room test for [BillingReceiptDao]. Covers the full grant + finalize
 * lifecycle, including the atomic idempotency guarantee of [BillingReceiptDao.grantOnceAtomic].
 *
 * C.5 PR 1 / ADR-0005.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BillingReceiptDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var receiptDao: BillingReceiptDao
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
        receiptDao = db.billingReceiptDao()
        playerDao = db.playerProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert and getByToken round-trip`() =
        runTest {
            val entity =
                BillingReceiptEntity(
                    purchaseToken = "tok_1",
                    orderId = "GPA.1234",
                    productId = "gem_pack_small",
                    purchaseTime = 1_700_000_000L,
                )
            receiptDao.upsert(entity)

            val loaded = receiptDao.getByToken("tok_1")
            assertNotNull(loaded)
            assertEquals("GPA.1234", loaded!!.orderId)
            assertEquals("gem_pack_small", loaded.productId)
            assertFalse("newly inserted rows default to granted=false", loaded.granted)
            assertNull(loaded.grantedAt)
        }

    @Test
    fun `getByToken returns null for unknown token`() =
        runTest {
            assertNull(receiptDao.getByToken("never_existed"))
        }

    @Test
    fun `grantOnceAtomic flips granted and runs walletCredit`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(id = 1))
            val entity =
                BillingReceiptEntity(
                    purchaseToken = "tok_grant",
                    productId = "gem_pack_medium",
                    purchaseTime = 0L,
                )

            var walletHits = 0
            val granted =
                receiptDao.grantOnceAtomic(
                    receipt = entity,
                    grantedAt = 9_999L,
                    walletCredit = {
                        walletHits++
                        playerDao.adjustGems(300)
                        playerDao.incrementGemsEarned(300)
                    },
                )

            assertTrue("first grant returns true", granted)
            assertEquals("wallet lambda ran exactly once", 1, walletHits)

            val loaded = receiptDao.getByToken("tok_grant")!!
            assertTrue(loaded.granted)
            assertEquals(9_999L, loaded.grantedAt)

            val profile = playerDao.get().firstOrNull()!!
            assertEquals(300L, profile.gems)
            assertEquals(300L, profile.totalGemsEarned)
        }

    @Test
    fun `grantOnceAtomic is idempotent - second call returns false and skips walletCredit`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(id = 1))
            val entity =
                BillingReceiptEntity(
                    purchaseToken = "tok_dedup",
                    productId = "gem_pack_small",
                    purchaseTime = 0L,
                )

            var firstWalletHits = 0
            val first =
                receiptDao.grantOnceAtomic(
                    receipt = entity,
                    grantedAt = 1L,
                    walletCredit = {
                        firstWalletHits++
                        playerDao.adjustGems(50)
                    },
                )
            var secondWalletHits = 0
            val second =
                receiptDao.grantOnceAtomic(
                    receipt = entity,
                    grantedAt = 2L,
                    walletCredit = { secondWalletHits++ },
                )

            assertTrue("first grant returns true", first)
            assertFalse("second grant returns false (idempotent short-circuit)", second)
            assertEquals("first wallet lambda ran", 1, firstWalletHits)
            assertEquals("second wallet lambda did NOT run", 0, secondWalletHits)

            val profile = playerDao.get().firstOrNull()!!
            assertEquals("wallet credited exactly once despite two grant attempts", 50L, profile.gems)

            // grantedAt reflects the FIRST call; the second did not overwrite.
            val row = receiptDao.getByToken("tok_dedup")!!
            assertEquals(1L, row.grantedAt)
        }

    @Test
    fun `markConsumed and markAcknowledged update only the target token`() =
        runTest {
            receiptDao.upsert(
                BillingReceiptEntity(
                    purchaseToken = "tok_c",
                    productId = "gem_pack_small",
                    purchaseTime = 0L,
                    granted = true,
                    grantedAt = 1L,
                ),
            )
            receiptDao.upsert(
                BillingReceiptEntity(
                    purchaseToken = "tok_a",
                    productId = "ad_removal",
                    purchaseTime = 0L,
                    granted = true,
                    grantedAt = 1L,
                ),
            )

            receiptDao.markConsumed("tok_c", 555L)
            receiptDao.markAcknowledged("tok_a", 666L)

            val c = receiptDao.getByToken("tok_c")!!
            val a = receiptDao.getByToken("tok_a")!!
            assertTrue(c.consumed)
            assertEquals(555L, c.consumedAt)
            assertFalse(c.acknowledged)
            assertTrue(a.acknowledged)
            assertEquals(666L, a.acknowledgedAt)
            assertFalse(a.consumed)
        }

    @Test
    fun `getGrantedButUnresolved returns only granted rows missing both consume and ack`() =
        runTest {
            // Granted but unresolved — should match.
            receiptDao.upsert(
                BillingReceiptEntity(
                    purchaseToken = "unresolved",
                    productId = "gem_pack_small",
                    purchaseTime = 0L,
                    granted = true,
                    grantedAt = 1L,
                ),
            )
            // Granted and consumed — should NOT match.
            receiptDao.upsert(
                BillingReceiptEntity(
                    purchaseToken = "resolved_consume",
                    productId = "gem_pack_small",
                    purchaseTime = 0L,
                    granted = true,
                    grantedAt = 1L,
                    consumed = true,
                    consumedAt = 2L,
                ),
            )
            // Granted and acknowledged — should NOT match.
            receiptDao.upsert(
                BillingReceiptEntity(
                    purchaseToken = "resolved_ack",
                    productId = "ad_removal",
                    purchaseTime = 0L,
                    granted = true,
                    grantedAt = 1L,
                    acknowledged = true,
                    acknowledgedAt = 2L,
                ),
            )
            // Not granted (pending) — should NOT match.
            receiptDao.upsert(
                BillingReceiptEntity(
                    purchaseToken = "pending",
                    productId = "gem_pack_large",
                    purchaseTime = 0L,
                    granted = false,
                ),
            )

            val unresolved = receiptDao.getGrantedButUnresolved()
            assertEquals(1, unresolved.size)
            assertEquals("unresolved", unresolved.single().purchaseToken)
        }

    @Test
    fun `getAll orders by purchaseTime DESC`() =
        runTest {
            receiptDao.upsert(BillingReceiptEntity("a", null, "gem_pack_small", purchaseTime = 100L))
            receiptDao.upsert(BillingReceiptEntity("b", null, "gem_pack_small", purchaseTime = 300L))
            receiptDao.upsert(BillingReceiptEntity("c", null, "gem_pack_small", purchaseTime = 200L))

            val all = receiptDao.getAll()
            assertEquals(listOf("b", "c", "a"), all.map { it.purchaseToken })
        }
}
