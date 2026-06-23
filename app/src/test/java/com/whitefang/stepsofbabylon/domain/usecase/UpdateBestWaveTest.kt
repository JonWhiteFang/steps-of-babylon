package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateBestWaveTest {
    @Test
    fun `wave higher than best is new record`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(bestWavePerTier = mapOf(1 to 5)))
            val sut = UpdateBestWave(repo)
            val result = sut(tier = 1, waveReached = 10)
            assertTrue(result.isNewRecord)
            assertEquals(5, result.previousBest)
            assertEquals(10, repo.profile.value.bestWavePerTier[1])
        }

    @Test
    fun `wave equal to best is not new record`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(bestWavePerTier = mapOf(1 to 5)))
            val result = UpdateBestWave(repo)(tier = 1, waveReached = 5)
            assertFalse(result.isNewRecord)
        }

    @Test
    fun `wave lower than best is not new record`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile(bestWavePerTier = mapOf(1 to 10)))
            val result = UpdateBestWave(repo)(tier = 1, waveReached = 3)
            assertFalse(result.isNewRecord)
            assertEquals(10, repo.profile.value.bestWavePerTier[1])
        }

    @Test
    fun `no previous best is new record`() =
        runTest {
            val repo = FakePlayerRepository(PlayerProfile())
            val result = UpdateBestWave(repo)(tier = 1, waveReached = 1)
            assertTrue(result.isNewRecord)
            assertEquals(0, result.previousBest)
        }
}
