package com.whitefang.stepsofbabylon.data.integration

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.healthconnect.HealthConnectStepReader
import com.whitefang.stepsofbabylon.data.healthconnect.StepCrossValidator
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EscrowLifecycleTest {
    private val stepReader: HealthConnectStepReader = mock()
    private val antiCheatPrefs: AntiCheatPreferences = mock()
    private val appDatabase: AppDatabase = mock()

    /**
     * Builds a validator with the `runInTransaction` seam replaced by a pass-through so
     * the atomic multi-write branches execute their bodies directly (the behaviour under
     * test is the escrow-lifecycle state machine, not Room's transaction machinery).
     */
    private fun makeValidator(
        stepRepo: FakeStepRepository,
        playerRepo: FakePlayerRepository,
    ) = StepCrossValidator(stepReader, stepRepo, playerRepo, antiCheatPrefs, appDatabase).apply {
        runInTransaction = { block -> block() }
    }

    @Test
    fun `full lifecycle - escrow deducts then release restores balance`() =
        runTest {
            val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 10000))
            val stepRepo = FakeStepRepository()

            // Player walked 10000 sensor steps, credited to balance
            stepRepo.updateDailySteps("2026-03-12", sensorSteps = 10000, creditedSteps = 10000)

            // HC reports only 8000 — 25% discrepancy triggers escrow
            whenever(stepReader.getStepsForDate(any())).thenReturn(8000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            val validator = makeValidator(stepRepo, playerRepo)

            // First validation: escrow deducts excess (2000) from balance
            validator.validate("2026-03-12")
            assertEquals(8000L, playerRepo.profile.value.stepBalance, "Balance should be reduced by escrow")

            val record = stepRepo.getDailyRecord("2026-03-12")!!
            assertEquals(2000L, record.escrowSteps, "Escrow should hold 2000 steps")

            // HC now agrees with sensor — discrepancy resolved, release escrow
            whenever(stepReader.getStepsForDate(any())).thenReturn(10000L)
            validator.validate("2026-03-12")
            assertEquals(10000L, playerRepo.profile.value.stepBalance, "Balance should be fully restored after release")
        }

    @Test
    fun `full lifecycle - escrow deducts then discard keeps deduction`() =
        runTest {
            val playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 10000))
            val stepRepo = FakeStepRepository()

            stepRepo.updateDailySteps("2026-03-12", sensorSteps = 10000, creditedSteps = 10000)
            whenever(stepReader.getStepsForDate(any())).thenReturn(8000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            val validator = makeValidator(stepRepo, playerRepo)

            // Sync 1: escrow deducts
            validator.validate("2026-03-12")
            assertEquals(8000L, playerRepo.profile.value.stepBalance)

            // Sync 2: still discrepant, metadata updated
            validator.validate("2026-03-12")

            // Sync 3: max syncs reached, discard — deduction stays
            validator.validate("2026-03-12")
            assertEquals(8000L, playerRepo.profile.value.stepBalance, "Balance stays reduced after discard")

            val record = stepRepo.getDailyRecord("2026-03-12")!!
            assertEquals(0L, record.escrowSteps, "Escrow metadata cleared after discard")
        }
}
