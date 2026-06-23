package com.whitefang.stepsofbabylon.data.healthconnect

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import com.whitefang.stepsofbabylon.domain.model.DailyStepSummary
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StepCrossValidatorTest {
    private val stepReader: HealthConnectStepReader = mock()
    private val stepRepository: StepRepository = mock()
    private val playerRepository: PlayerRepository = mock()
    private val antiCheatPrefs: AntiCheatPreferences = mock()
    private val appDatabase: AppDatabase = mock()

    // Post-B.2 PR 3: the validator wraps each multi-write branch in a Room transaction via
    // its `runInTransaction` seam. Mockito cannot mock Room's `withTransaction` extension on a
    // bare `mock<AppDatabase>()`, so we replace the seam with a direct-invocation pass-through.
    // The assertions below verify branch logic; real transaction behaviour is out of JVM scope.
    private val validator =
        StepCrossValidator(
            stepReader,
            stepRepository,
            playerRepository,
            antiCheatPrefs,
            appDatabase,
        ).apply {
            runInTransaction = { block -> block() }
        }

    private fun record(
        sensor: Long = 1000,
        hc: Long = 0,
        credited: Long = 1000,
        escrow: Long = 0,
        escrowSync: Int = 0,
    ) = DailyStepSummary(
        date = "2026-03-09",
        sensorSteps = sensor,
        healthConnectSteps = hc,
        creditedSteps = credited,
        escrowSteps = escrow,
        escrowSyncCount = escrowSync,
    )

    @Test
    fun `level 0 - discrepancy escrows excess and deducts from balance`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(record(sensor = 1500, hc = 0))
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            validator.validate("2026-03-09")

            verify(playerRepository).spendSteps(500L)
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(500L), eq(1))
            verify(antiCheatPrefs).recordCvOffense("2026-03-09")
        }

    @Test
    fun `level 0 - subsequent sync updates metadata without double deduction`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 500, escrowSync = 1),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            validator.validate("2026-03-09")

            verify(playerRepository, never()).spendSteps(any())
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(500L), eq(2))
        }

    @Test
    fun `level 0 - discard after max syncs does not deduct again`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 500, escrowSync = 2),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            validator.validate("2026-03-09")

            verify(playerRepository, never()).spendSteps(any())
            verify(playerRepository, never()).addSteps(any())
            verify(stepRepository).discardEscrow("2026-03-09")
        }

    @Test
    fun `level 1 - faster discard after 2 syncs`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 500, escrowSync = 1),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(2)

            validator.validate("2026-03-09")

            verify(playerRepository, never()).addSteps(any())
            verify(stepRepository).discardEscrow("2026-03-09")
        }

    @Test
    fun `level 1 - first escrow deducts from balance`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 0, escrowSync = 0),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(1)

            validator.validate("2026-03-09")

            verify(playerRepository).spendSteps(500L)
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(500L), eq(1))
        }

    @Test
    fun `level 2 - caps at HC value and deducts excess`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, credited = 1500),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(4)

            validator.validate("2026-03-09")

            verify(playerRepository).spendSteps(500L)
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(500L), eq(3))
        }

    @Test
    fun `level 3 - caps at HC minus 10 percent and deducts excess`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, credited = 1500),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(7)

            validator.validate("2026-03-09")

            // capped = 1000 * 0.9 = 900, excess = 1500 - 900 = 600
            verify(playerRepository).spendSteps(600L)
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(600L), eq(3))
        }

    @Test
    fun `no discrepancy releases escrow and restores balance`() =
        runTest {
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1000, hc = 0, escrow = 200),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(2)

            validator.validate("2026-03-09")

            verify(playerRepository).addSteps(200L)
            verify(stepRepository).releaseEscrow("2026-03-09")
            verify(antiCheatPrefs).decayCvOffenses()
            verify(antiCheatPrefs, never()).recordCvOffense(any())
        }

    @Test
    fun `escrow then release is net zero balance change`() =
        runTest {
            // First call: discrepancy detected, escrow deducts 500
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 0, escrowSync = 0),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            validator.validate("2026-03-09")
            verify(playerRepository).spendSteps(500L)

            // Second call: discrepancy resolved, release restores 500
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1000, hc = 0, escrow = 500, escrowSync = 1),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)

            validator.validate("2026-03-09")
            verify(playerRepository).addSteps(500L)
            // Net: -500 + 500 = 0
        }

    @Test
    fun `escrow then discard keeps deduction`() =
        runTest {
            // First call: discrepancy detected, escrow deducts 500
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 0, escrowSync = 0),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            validator.validate("2026-03-09")
            verify(playerRepository).spendSteps(500L)

            // Subsequent syncs: still discrepant, escrow metadata updated
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 500, escrowSync = 2),
            )

            validator.validate("2026-03-09")
            // Discard — no addSteps, deduction stays
            verify(stepRepository).discardEscrow("2026-03-09")
            verify(playerRepository, never()).addSteps(any())
        }

    // -------- B.2 PR 3 atomicity tests --------

    @Test
    fun `RO-02 site 2 - multi-write branch invokes the transaction seam exactly once per write pair`() =
        runTest {
            // Level 0 first-escrow: spendSteps + updateEscrow is one atomic pair.
            var transactionCalls = 0
            val tracker =
                StepCrossValidator(
                    stepReader,
                    stepRepository,
                    playerRepository,
                    antiCheatPrefs,
                    appDatabase,
                ).apply {
                    runInTransaction = { block ->
                        transactionCalls++
                        block()
                    }
                }
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 0, escrowSync = 0),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            tracker.validate("2026-03-09")

            // Exactly one transaction for the spendSteps + updateEscrow pair. Before this PR the
            // two writes ran back-to-back without a shared atomic scope.
            assertEquals(1, transactionCalls, "first-escrow branch must open exactly one transaction")
            verify(playerRepository).spendSteps(500L)
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(500L), eq(1))
        }

    @Test
    fun `RO-02 site 2 - single-write branches bypass the transaction seam`() =
        runTest {
            // Subsequent-sync branch (already escrowed, metadata-only update) is a SINGLE write.
            // Wrapping it in a transaction would be dead weight; verify it does not.
            var transactionCalls = 0
            val tracker =
                StepCrossValidator(
                    stepReader,
                    stepRepository,
                    playerRepository,
                    antiCheatPrefs,
                    appDatabase,
                ).apply {
                    runInTransaction = { block ->
                        transactionCalls++
                        block()
                    }
                }
            whenever(stepRepository.getDailyRecord("2026-03-09")).thenReturn(
                record(sensor = 1500, hc = 0, escrow = 500, escrowSync = 1),
            )
            whenever(stepReader.getStepsForDate("2026-03-09")).thenReturn(1000L)
            whenever(antiCheatPrefs.getCvOffenseCount()).thenReturn(0)

            tracker.validate("2026-03-09")

            assertEquals(
                0,
                transactionCalls,
                "metadata-only updateEscrow must not open a transaction — single-write branch",
            )
            verify(playerRepository, never()).spendSteps(any())
            verify(stepRepository).updateEscrow(eq("2026-03-09"), eq(500L), eq(2))
        }
}
