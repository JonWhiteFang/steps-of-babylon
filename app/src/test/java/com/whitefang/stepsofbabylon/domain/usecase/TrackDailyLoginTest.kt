package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeDailyLoginRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrackDailyLoginTest {
    private lateinit var loginRepo: FakeDailyLoginRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var useCase: TrackDailyLogin

    @BeforeEach
    fun setup() {
        loginRepo = FakeDailyLoginRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 0, gems = 0))
        useCase = TrackDailyLogin(loginRepo, playerRepo)
    }

    @Test
    fun `awards 1 PS when steps at least 1000`() =
        runTest {
            useCase.checkAndAward("2026-03-09", 1500)
            assertEquals(1, playerRepo.profile.value.powerStones)
        }

    @Test
    fun `no PS when steps below 1000`() =
        runTest {
            useCase.checkAndAward("2026-03-09", 500)
            assertEquals(0, playerRepo.profile.value.powerStones)
        }

    @Test
    fun `no double PS claim`() =
        runTest {
            useCase.checkAndAward("2026-03-09", 2000)
            useCase.checkAndAward("2026-03-09", 3000)
            assertEquals(1, playerRepo.profile.value.powerStones)
        }

    @Test
    fun `streak increments on consecutive days`() =
        runTest {
            playerRepo.profile.value = PlayerProfile(currentStreak = 1, lastLoginDate = "2026-03-08")
            useCase.checkAndAward("2026-03-09", 100)
            assertEquals(2, playerRepo.profile.value.currentStreak)
        }

    @Test
    fun `streak resets on gap`() =
        runTest {
            playerRepo.profile.value = PlayerProfile(currentStreak = 5, lastLoginDate = "2026-03-07")
            useCase.checkAndAward("2026-03-09", 100)
            assertEquals(1, playerRepo.profile.value.currentStreak)
        }

    @Test
    fun `season pass adds 10 gems`() =
        runTest {
            useCase.checkAndAward("2026-03-09", 100, seasonPassActive = true, seasonPassExpiry = Long.MAX_VALUE)
            // Streak 1 = 1 gem + 10 season pass = 11
            assertEquals(11, playerRepo.profile.value.gems)
        }

    @Test
    fun `R211 rollback verdict writes nothing for the tampered date`() =
        runTest {
            val gemsBefore = playerRepo.observeProfile().first().gems
            useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = true)
            assertNull(loginRepo.getByDate("2026-03-09")) // no DailyLogin row
            assertEquals(gemsBefore, playerRepo.observeProfile().first().gems) // no gems credited
        }

    @Test
    fun `R211 trusted verdict credits normally (regression)`() =
        runTest {
            useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = false)
            assertNotNull(loginRepo.getByDate("2026-03-09")) // row written + streak/gems advance
        }

    @Test
    fun `R211 latched future clock then legit date is still credited`() =
        runTest {
            // a rolled-back pass for date D writes nothing (no stale gemsClaimed=true latched)...
            useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = true)
            assertNull(loginRepo.getByDate("2026-03-09"))
            // ...so when D legitimately arrives the credit is NOT pre-suppressed
            useCase.checkAndAward("2026-03-09", todayCreditedSteps = 0, isRollback = false)
            assertNotNull(loginRepo.getByDate("2026-03-09"))
        }
}
