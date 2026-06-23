package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AwardWaveMilestoneTest {
    private val playerRepo = FakePlayerRepository()
    private val sut = AwardWaveMilestone(playerRepo)

    @Test
    fun `wave 1 awards 1 PS`() =
        runTest {
            assertEquals(1, sut(1))
            assertEquals(1L, playerRepo.observeProfile().first().powerStones)
        }

    @Test
    fun `wave 7 awards 1 PS`() =
        runTest {
            assertEquals(1, sut(7))
        }

    @Test
    fun `wave 10 awards 2 PS`() =
        runTest {
            assertEquals(2, sut(10))
            assertEquals(2L, playerRepo.observeProfile().first().powerStones)
        }

    @Test
    fun `wave 20 awards 2 PS`() =
        runTest {
            assertEquals(2, sut(20))
        }

    @Test
    fun `wave 30 awards 2 PS`() =
        runTest {
            assertEquals(2, sut(30))
        }

    @Test
    fun `wave 25 awards 5 PS`() =
        runTest {
            assertEquals(5, sut(25))
            assertEquals(5L, playerRepo.observeProfile().first().powerStones)
        }

    @Test
    fun `wave 50 awards 5 PS — multiple of 25 takes priority over 10`() =
        runTest {
            assertEquals(5, sut(50))
        }

    @Test
    fun `wave 75 awards 5 PS`() =
        runTest {
            assertEquals(5, sut(75))
        }

    @Test
    fun `wave 100 awards 5 PS`() =
        runTest {
            assertEquals(5, sut(100))
        }
}
