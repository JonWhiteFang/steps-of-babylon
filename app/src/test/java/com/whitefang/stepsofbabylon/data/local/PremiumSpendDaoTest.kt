package com.whitefang.stepsofbabylon.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #236: validates the new premium-currency spend+grant `@Transaction` methods against a real
 * (in-memory) SQLite DB. The fakes can only approximate cross-DAO atomicity, so these prove the
 * actual queries (a) deduct + grant in a single transaction, (b) leave the DB untouched when the
 * guarded deduct finds insufficient balance (the atomicity guarantee), and (c) preserve the
 * lifetime spent-counters. Covers [CardDao.openCardPackAtomic] (Gems → cards) and
 * [UltimateWeaponDao.unlockWeaponAtomic] (Power Stones → UW unlock).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PremiumSpendDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var cardDao: CardDao
    private lateinit var uwDao: UltimateWeaponDao
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
        cardDao = db.cardDao()
        uwDao = db.ultimateWeaponDao()
        playerDao = db.playerProfileDao()
    }

    @After
    fun tearDown() = db.close()

    // ---- openCardPackAtomic ----------------------------------------------------------------

    @Test
    fun `openCardPackAtomic deducts gems and writes all three cards`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(gems = 1000))
            val names = listOf("IRON_SKIN", "IRON_SKIN", "SHARP_SHOOTER")

            val isNew = cardDao.openCardPackAtomic(gemCost = 50, cardTypeNames = names, playerDao = playerDao)

            assertEquals(listOf(true, false, true), isNew)
            val cards = cardDao.getAll().first()
            assertEquals(2, cards.size) // two distinct types
            assertEquals(2, cards.first { it.cardType == "IRON_SKIN" }.copyCount) // inserted then incremented
            val profile = playerDao.get().first()!!
            assertEquals(950L, profile.gems)
            assertEquals(50L, profile.totalGemsSpent)
        }

    @Test
    fun `openCardPackAtomic with gemCost 0 is a free pack - no deduct`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(gems = 0))
            val isNew =
                cardDao.openCardPackAtomic(
                    gemCost = 0,
                    cardTypeNames = listOf("IRON_SKIN"),
                    playerDao = playerDao,
                )
            assertEquals(listOf(true), isNew)
            assertEquals(1, cardDao.getAll().first().size)
            assertEquals(0L, playerDao.get().first()!!.totalGemsSpent)
        }

    @Test
    fun `openCardPackAtomic rolls back entirely on insufficient gems`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(gems = 10))
            val result =
                cardDao.openCardPackAtomic(
                    gemCost = 50,
                    cardTypeNames = listOf("IRON_SKIN"),
                    playerDao = playerDao,
                )

            assertNull("insufficient gems must return null", result)
            assertTrue("no card rows may be written for a failed deduct", cardDao.getAll().first().isEmpty())
            assertEquals("balance must be unchanged", 10L, playerDao.get().first()!!.gems)
        }

    // ---- unlockWeaponAtomic ----------------------------------------------------------------

    @Test
    fun `unlockWeaponAtomic deducts stones and unlocks - fresh row`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(powerStones = 200))
            val ok = uwDao.unlockWeaponAtomic(weaponType = "DEATH_WAVE", powerStoneCost = 60, playerDao = playerDao)

            assertTrue(ok)
            assertTrue(uwDao.getByType("DEATH_WAVE")!!.isUnlocked)
            val profile = playerDao.get().first()!!
            assertEquals(140L, profile.powerStones)
            assertEquals(60L, profile.totalPowerStonesSpent)
        }

    @Test
    fun `unlockWeaponAtomic flips an existing locked row without duplicating`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(powerStones = 200))
            uwDao.upsert(UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = false))

            val ok = uwDao.unlockWeaponAtomic(weaponType = "DEATH_WAVE", powerStoneCost = 60, playerDao = playerDao)

            assertTrue(ok)
            assertEquals(1, uwDao.getAll().first().size) // not duplicated
            assertTrue(uwDao.getByType("DEATH_WAVE")!!.isUnlocked)
        }

    @Test
    fun `unlockWeaponAtomic rolls back entirely on insufficient stones`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(powerStones = 10))
            val ok = uwDao.unlockWeaponAtomic(weaponType = "DEATH_WAVE", powerStoneCost = 60, playerDao = playerDao)

            assertFalse("insufficient stones must return false", ok)
            assertNull("no UW row may be written for a failed deduct", uwDao.getByType("DEATH_WAVE"))
            assertEquals("balance must be unchanged", 10L, playerDao.get().first()!!.powerStones)
        }

    @Test
    fun `unlockWeaponAtomic on an already-unlocked weapon does not deduct again`() =
        runTest {
            playerDao.upsert(PlayerProfileEntity(powerStones = 200))
            uwDao.upsert(UltimateWeaponStateEntity(weaponType = "DEATH_WAVE", isUnlocked = true))

            val ok = uwDao.unlockWeaponAtomic(weaponType = "DEATH_WAVE", powerStoneCost = 60, playerDao = playerDao)

            assertFalse("re-unlock must be a no-op", ok)
            assertEquals("no second deduct", 200L, playerDao.get().first()!!.powerStones)
        }
}
