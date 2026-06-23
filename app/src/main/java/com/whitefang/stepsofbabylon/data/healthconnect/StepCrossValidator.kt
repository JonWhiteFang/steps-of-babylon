package com.whitefang.stepsofbabylon.data.healthconnect

import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compares sensor steps vs Health Connect steps with graduated response.
 * Offense history tracked across days — repeat offenders face escalating penalties.
 *
 * When a discrepancy is detected, the excess steps are deducted from the player
 * balance (escrowed). If HC later confirms the steps, they are restored (released).
 * If the discrepancy persists, the deduction stays (discarded).
 *
 * Level 0 (0 offenses): escrow excess, release on reconciliation (3 syncs)
 * Level 1 (1–2 offenses): escrow with faster discard (2 syncs)
 * Level 2 (3–5 offenses): cap credited at HC value
 * Level 3 (6+ offenses): cap at HC value minus 10% penalty
 *
 * Post-RO-02 (B.2 PR 3): each multi-write branch (spendSteps + updateEscrow, or addSteps +
 * releaseEscrow) commits inside a single Room transaction via [runInTransaction]. This closes
 * the partial-failure window where a crash between the two writes could leave the wallet and
 * escrow counter out of sync (either the player was deducted without the escrow recording it,
 * or the reverse — escrow released metadata without the wallet being credited back). The
 * SharedPreferences writes in [AntiCheatPreferences.recordCvOffense] / [decayCvOffenses]
 * deliberately stay outside the transaction since they do not participate in SQLite.
 *
 * RO-02 licenses the cross-layer [AppDatabase] import here specifically because the validator
 * lives in `data/healthconnect/` and the graduated-response branches need parallel transaction
 * scopes; a per-DAO `@Transaction` default method (the pattern used by B.2 PRs 1 and 2) would
 * not fit as cleanly here.
 */
@Singleton
class StepCrossValidator
    @Inject
    constructor(
        private val stepReader: HealthConnectStepReader,
        private val stepRepository: StepRepository,
        private val playerRepository: PlayerRepository,
        private val antiCheatPrefs: AntiCheatPreferences,
        private val appDatabase: AppDatabase,
    ) {
        companion object {
            private const val DISCREPANCY_THRESHOLD = 0.20
            private const val MAX_ESCROW_SYNCS_DEFAULT = 3
            private const val MAX_ESCROW_SYNCS_LEVEL1 = 2
            private const val LEVEL2_PENALTY = 0.0
            private const val LEVEL3_PENALTY = 0.10
        }

        /**
         * Runs [block] inside a single Room transaction so each multi-write branch commits
         * atomically. Exposed as `@VisibleForTesting internal var` because the existing tests
         * construct this class with `mock<AppDatabase>()` — Mockito mocks of [AppDatabase] do not
         * support Room's `withTransaction` extension. Tests override this with a pass-through
         * lambda immediately after construction so the behaviour under test is the branch logic,
         * not Room's transaction machinery (which is validated by instrumented tests, not JVM).
         */
        @VisibleForTesting
        internal var runInTransaction: suspend (block: suspend () -> Unit) -> Unit = { block ->
            appDatabase.withTransaction { block() }
        }

        suspend fun validate(date: String) {
            val record = stepRepository.getDailyRecord(date) ?: return
            val hcSteps = stepReader.getStepsForDate(date) ?: return

            stepRepository.updateHealthConnectSteps(date, hcSteps)

            val sensorSteps = record.sensorSteps
            if (sensorSteps <= 0 || hcSteps <= 0) return

            val discrepancy = (sensorSteps - hcSteps).toDouble() / hcSteps
            val offenseCount = antiCheatPrefs.getCvOffenseCount()

            if (discrepancy > DISCREPANCY_THRESHOLD) {
                // Recording the offense is a SharedPreferences write — outside the transaction
                // on purpose. A transaction failure on the subsequent multi-write must not hide
                // the fact that a validation attempt detected a discrepancy.
                antiCheatPrefs.recordCvOffense(date)

                when {
                    // Level 3: cap at HC minus penalty
                    offenseCount >= 6 -> {
                        val capped = (hcSteps * (1.0 - LEVEL3_PENALTY)).toLong()
                        if (record.creditedSteps > capped) {
                            val excess = record.creditedSteps - capped
                            runInTransaction {
                                playerRepository.spendSteps(excess)
                                stepRepository.updateEscrow(date, excess, MAX_ESCROW_SYNCS_DEFAULT)
                            }
                        }
                    }

                    // Level 2: cap at HC value
                    offenseCount >= 3 -> {
                        if (record.creditedSteps > hcSteps) {
                            val excess = record.creditedSteps - hcSteps
                            runInTransaction {
                                playerRepository.spendSteps(excess)
                                stepRepository.updateEscrow(date, excess, MAX_ESCROW_SYNCS_DEFAULT)
                            }
                        }
                    }

                    // Level 1: escrow with faster discard
                    offenseCount >= 1 -> {
                        val excess = sensorSteps - hcSteps
                        val newSyncCount = record.escrowSyncCount + 1
                        if (newSyncCount >= MAX_ESCROW_SYNCS_LEVEL1 && record.escrowSteps > 0) {
                            // Already deducted on prior escrow — just discard metadata
                            stepRepository.discardEscrow(date)
                        } else if (record.escrowSteps == 0L) {
                            // First escrow — deduct from balance atomically with metadata update
                            runInTransaction {
                                playerRepository.spendSteps(excess)
                                stepRepository.updateEscrow(date, excess, newSyncCount)
                            }
                        } else {
                            // Already escrowed — metadata-only update (single write, no transaction needed)
                            stepRepository.updateEscrow(date, excess, newSyncCount)
                        }
                    }

                    // Level 0: default behavior
                    else -> {
                        val excess = sensorSteps - hcSteps
                        val newSyncCount = record.escrowSyncCount + 1
                        if (newSyncCount >= MAX_ESCROW_SYNCS_DEFAULT && record.escrowSteps > 0) {
                            // Already deducted on prior escrow — just discard metadata
                            stepRepository.discardEscrow(date)
                        } else if (record.escrowSteps == 0L) {
                            // First escrow — deduct from balance atomically with metadata update
                            runInTransaction {
                                playerRepository.spendSteps(excess)
                                stepRepository.updateEscrow(date, excess, newSyncCount)
                            }
                        } else {
                            // Already escrowed — metadata-only update (single write, no transaction needed)
                            stepRepository.updateEscrow(date, excess, newSyncCount)
                        }
                    }
                }
            } else if (record.escrowSteps > 0) {
                // Discrepancy resolved — restore escrowed steps atomically with escrow release.
                runInTransaction {
                    playerRepository.addSteps(record.escrowSteps)
                    stepRepository.releaseEscrow(date)
                }
                // SharedPreferences decay stays outside the transaction (not SQLite-backed).
                antiCheatPrefs.decayCvOffenses()
            }
        }
    }
