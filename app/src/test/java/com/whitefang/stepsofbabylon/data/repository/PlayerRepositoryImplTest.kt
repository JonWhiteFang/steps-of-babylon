package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [PlayerRepositoryImpl] — repository-layer behavior including entity→domain
 * mapping, multi-call delegation patterns (e.g. addGems = adjustGems + incrementGemsEarned),
 * read-modify-write paths (updateBestWave), and conditional logic (ensureProfileExists).
 *
 * Uses mockito-kotlin to verify DAO call patterns without requiring Robolectric.
 * SQL semantics of atomic methods (spendGemsAtomic, etc.) are tested separately via
 * Room DAO tests (Robolectric + in-memory DB).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerRepositoryImplTest {

    private fun makeRepoWithEntity(entity: PlayerProfileEntity?): Pair<PlayerProfileDao, PlayerRepositoryImpl> {
        val dao = mock<PlayerProfileDao>()
        whenever(dao.get()).thenReturn(MutableStateFlow(entity))
        return dao to PlayerRepositoryImpl(dao)
    }

    @Test
    fun `observeProfile maps entity to domain model`() = runTest {
        val entity = PlayerProfileEntity(
            id = 1, currentStepBalance = 5000, gems = 200, powerStones = 50,
            currentTier = 3, highestUnlockedTier = 5,
        )
        val (_, repo) = makeRepoWithEntity(entity)

        val profile = repo.observeProfile().first()

        assertEquals(5000L, profile.stepBalance)
        assertEquals(200L, profile.gems)
        assertEquals(50L, profile.powerStones)
        assertEquals(3, profile.currentTier)
        assertEquals(5, profile.highestUnlockedTier)
    }

    @Test
    fun `observeWallet derives wallet currencies from profile`() = runTest {
        val entity = PlayerProfileEntity(currentStepBalance = 1000, gems = 50, powerStones = 10, cardDust = 25)
        val (_, repo) = makeRepoWithEntity(entity)

        val wallet = repo.observeWallet().first()

        assertEquals(1000L, wallet.stepBalance)
        assertEquals(50L, wallet.gems)
        assertEquals(10L, wallet.powerStones)
    }

    @Test
    fun `observeTier extracts currentTier from profile`() = runTest {
        val entity = PlayerProfileEntity(currentTier = 7)
        val (_, repo) = makeRepoWithEntity(entity)

        val tier = repo.observeTier().first()

        assertEquals(7, tier)
    }

    @Test
    fun `addGems calls both adjustGems and incrementGemsEarned`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())

        repo.addGems(100L)

        verify(dao).adjustGems(100L)
        verify(dao).incrementGemsEarned(100L)
    }

    @Test
    fun `addPowerStones calls both adjustPowerStones and incrementPowerStonesEarned`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())

        repo.addPowerStones(5L)

        verify(dao).adjustPowerStones(5L)
        verify(dao).incrementPowerStonesEarned(5L)
    }

    @Test
    fun `V1X10 - spendGems delegates to atomic DAO method`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())
        whenever(dao.spendGemsAtomic(50L)).thenReturn(1)

        repo.spendGems(50L)

        verify(dao).spendGemsAtomic(50L)
        // The non-atomic methods must NOT be called (V1X-10 contract)
        verify(dao, never()).adjustGems(any())
        verify(dao, never()).incrementGemsSpent(any())
    }

    // #122: spendGems must propagate the atomic DAO's rows-affected as a Boolean so callers can
    // gate the grant on the deduct actually happening.
    @Test
    fun `R122 - spendGems returns true when the guarded deduct affects a row`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())
        whenever(dao.spendGemsAtomic(50L)).thenReturn(1)
        assertTrue(repo.spendGems(50L))
    }

    @Test
    fun `R122 - spendGems returns false when the guarded deduct affects no row`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())
        whenever(dao.spendGemsAtomic(50L)).thenReturn(0)
        assertFalse(repo.spendGems(50L))
    }

    @Test
    fun `V1X10 - spendPowerStones delegates to atomic DAO method`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())
        whenever(dao.spendPowerStonesAtomic(3L)).thenReturn(1)

        repo.spendPowerStones(3L)

        verify(dao).spendPowerStonesAtomic(3L)
        verify(dao, never()).adjustPowerStones(any())
        verify(dao, never()).incrementPowerStonesSpent(any())
    }

    @Test
    fun `R122 - spendPowerStones returns false when the guarded deduct affects no row`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())
        whenever(dao.spendPowerStonesAtomic(3L)).thenReturn(0)
        assertFalse(repo.spendPowerStones(3L))
    }

    // #122: spendStepsIfSufficient is the new guarded Step deduct (distinct from the clamping
    // spendSteps). It must report the guarded DAO's rows-affected as a Boolean.
    @Test
    fun `R122 - spendStepsIfSufficient propagates the guarded DAO result`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())
        whenever(dao.adjustStepBalanceIfSufficient(500L)).thenReturn(1)
        assertTrue(repo.spendStepsIfSufficient(500L))
        whenever(dao.adjustStepBalanceIfSufficient(999L)).thenReturn(0)
        assertFalse(repo.spendStepsIfSufficient(999L))
    }

    @Test
    fun `updateBestWave merges new wave into existing bestWavePerTier map`() = runTest {
        val entity = PlayerProfileEntity(bestWavePerTier = mapOf(1 to 10, 2 to 5))
        val (dao, repo) = makeRepoWithEntity(entity)

        repo.updateBestWave(tier = 3, wave = 8)

        verify(dao).updateBestWavePerTier(eq(mapOf(1 to 10, 2 to 5, 3 to 8)))
    }

    @Test
    fun `updateBestWave is no-op when no profile exists`() = runTest {
        val (dao, repo) = makeRepoWithEntity(null)

        repo.updateBestWave(tier = 1, wave = 5)

        verify(dao, never()).updateBestWavePerTier(any())
    }

    @Test
    fun `ensureProfileExists creates profile when none exists`() = runTest {
        val (dao, repo) = makeRepoWithEntity(null)

        repo.ensureProfileExists()

        verify(dao).upsert(any())
    }

    @Test
    fun `ensureProfileExists is no-op when profile exists`() = runTest {
        val (dao, repo) = makeRepoWithEntity(PlayerProfileEntity())

        repo.ensureProfileExists()

        verify(dao, never()).upsert(any())
    }

    @Test
    fun `getStepBalance returns balance from profile`() = runTest {
        val (_, repo) = makeRepoWithEntity(PlayerProfileEntity(currentStepBalance = 12345))

        assertEquals(12345L, repo.getStepBalance())
    }

    @Test
    fun `getStepBalance returns 0 when no profile exists`() = runTest {
        val (_, repo) = makeRepoWithEntity(null)

        assertEquals(0L, repo.getStepBalance())
    }
}
