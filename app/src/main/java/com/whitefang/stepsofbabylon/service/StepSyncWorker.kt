package com.whitefang.stepsofbabylon.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whitefang.stepsofbabylon.data.healthconnect.ActivityMinuteConverter
import com.whitefang.stepsofbabylon.data.healthconnect.ActivityMinuteValidator
import com.whitefang.stepsofbabylon.data.healthconnect.ExerciseSessionReader
import com.whitefang.stepsofbabylon.data.healthconnect.StepCrossValidator
import com.whitefang.stepsofbabylon.data.healthconnect.StepGapFiller
import com.whitefang.stepsofbabylon.data.sensor.DailyStepManager
import com.whitefang.stepsofbabylon.data.sensor.StepIngestionPreferences
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import com.whitefang.stepsofbabylon.domain.repository.StepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

@HiltWorker
class StepSyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val dailyStepManager: DailyStepManager,
        private val sensorManager: SensorManager,
        private val stepGapFiller: StepGapFiller,
        private val stepCrossValidator: StepCrossValidator,
        private val exerciseSessionReader: ExerciseSessionReader,
        private val activityMinuteConverter: ActivityMinuteConverter,
        private val activityMinuteValidator: ActivityMinuteValidator,
        private val smartReminderManager: SmartReminderManager,
        private val stepIngestionPrefs: StepIngestionPreferences,
        private val stepRepository: StepRepository,
        private val billingManager: BillingManager,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val today = LocalDate.now().toString()

            // 1. Sensor catch-up (only when foreground service is not alive)
            sensorCatchUp(today)

            // 2. Health Connect operations (best-effort)
            try {
                stepGapFiller.fillGaps(today)
                stepCrossValidator.validate(today)

                val sessions = exerciseSessionReader.getSessionsForDate(today)
                val validSessions = activityMinuteValidator.validate(sessions)
                if (validSessions.isNotEmpty()) {
                    val sensorStepsPerMinute = dailyStepManager.getSensorStepsPerMinute()
                    val result = activityMinuteConverter.convert(validSessions, sensorStepsPerMinute)
                    dailyStepManager.recordActivityMinutes(result.activityMinutes, result.stepEquivalents)
                }
            } catch (e: Exception) {
                android.util.Log.w("StepSyncWorker", "HC sync failed", e)
            }

            // Smart reminders
            try {
                smartReminderManager.checkAndNotify()
            } catch (e: Exception) {
                android.util.Log.w("StepSyncWorker", "Smart reminder failed", e)
            }

            // 3. #250: background safety net — sweep pending/unresolved Play Billing purchases so an
            // entitlement bought on a flaky connection (ack RPC failed) is reconciled before Play's
            // 3-day auto-refund window, even if the user never re-opens the Store.
            reconcileBillingSafely(billingManager)

            return Result.success()
        }

        private suspend fun sensorCatchUp(today: String) {
            // Skip if the foreground service is alive and handling ingestion
            val now = System.currentTimeMillis()
            if (stepIngestionPrefs.isServiceAlive(now)) return

            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
            val currentCounter = readCurrentCounter(sensor) ?: return

            val dayStart = stepIngestionPrefs.getCounterAtDayStart(today)
            // Read Room's same-day sensor cumulative up front so the pure decision can subtract the
            // baseline-relative offset (#123).
            val alreadyCredited = stepRepository.getDailyRecord(today)?.sensorSteps ?: 0L
            val sensorStepsAtDayStart = stepIngestionPrefs.getSensorStepsAtDayStart(today)

            when (val decision = computeCatchUp(dayStart, currentCounter, alreadyCredited, sensorStepsAtDayStart)) {
                is CatchUpDecision.Establish -> {
                    // First read today — anchor the baseline; offset is the current Room cumulative
                    // (0 on a clean day) so future gaps are measured from here.
                    stepIngestionPrefs.setCounterAtDayStart(today, decision.counter, alreadyCredited)
                }

                is CatchUpDecision.Rebaseline -> {
                    // #123: device reboot detected (counter reset below the baseline). Re-anchor to the
                    // post-reboot counter AND capture the current Room cumulative as the new offset, so
                    // post-reboot steps credit from here instead of being swallowed by the stale
                    // pre-reboot cumulative. Credit nothing for the discontinuity itself.
                    stepIngestionPrefs.setCounterAtDayStart(today, decision.counter, alreadyCredited)
                }

                is CatchUpDecision.Credit -> {
                    dailyStepManager.recordSteps(decision.gap, now)
                }

                CatchUpDecision.Skip -> {
                    Unit
                }
            }
        }

        /**
         * The pure decision for [Sensor.TYPE_STEP_COUNTER] catch-up (#123). Kept side-effect-free so it
         * is JVM-testable against the REAL production arithmetic (no mirror-drift copy).
         *
         * @param dayStart the stored day-start baseline (null when not yet set today).
         * @param currentCounter the current hardware counter reading.
         * @param alreadyCredited Room's same-day `sensorSteps` cumulative.
         * @param sensorStepsAtDayStart Room's `sensorSteps` cumulative captured when the baseline was set.
         */
        sealed interface CatchUpDecision {
            /** No baseline yet today — establish one at [counter], credit nothing. */
            data class Establish(
                val counter: Long,
            ) : CatchUpDecision

            /** Counter reset (reboot) below the baseline — re-anchor to [counter], credit nothing. */
            data class Rebaseline(
                val counter: Long,
            ) : CatchUpDecision

            /** Credit [gap] uncredited steps walked since the baseline. */
            data class Credit(
                val gap: Long,
            ) : CatchUpDecision

            /** Nothing to do (no new steps since the last credit). */
            data object Skip : CatchUpDecision
        }

        companion object {
            fun computeCatchUp(
                dayStart: Long?,
                currentCounter: Long,
                alreadyCredited: Long,
                sensorStepsAtDayStart: Long,
            ): CatchUpDecision {
                if (dayStart == null) return CatchUpDecision.Establish(currentCounter)
                // currentCounter < dayStart can ONLY mean the hardware counter restarted (reboot) —
                // dayStart was itself a same-day reading from the same monotonic sensor.
                if (currentCounter < dayStart) return CatchUpDecision.Rebaseline(currentCounter)

                val rawSinceBaseline = currentCounter - dayStart
                // Steps already credited SINCE this baseline (excludes the pre-baseline cumulative that
                // survives a reboot in Room's sensorSteps).
                val creditedSinceBaseline = alreadyCredited - sensorStepsAtDayStart
                val gap = rawSinceBaseline - creditedSinceBaseline
                return if (gap > 0) CatchUpDecision.Credit(gap) else CatchUpDecision.Skip
            }
        }

        private fun readCurrentCounter(sensor: Sensor): Long? {
            var value: Long? = null
            val latch = java.util.concurrent.CountDownLatch(1)

            val listener =
                object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
                        value = event.values[0].toLong()
                        sensorManager.unregisterListener(this)
                        latch.countDown()
                    }

                    override fun onAccuracyChanged(
                        s: Sensor?,
                        accuracy: Int,
                    ) {}
                }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            sensorManager.unregisterListener(listener)
            return value
        }
    }

/** Timeout for a background billing reconcile sweep (#250). */
private const val BILLING_RECONCILE_TIMEOUT_MS = 20_000L

/**
 * #250: best-effort, time-bounded background reconcile of pending/unresolved Play Billing purchases.
 * Extracted as a top-level suspend fun so it is JVM-unit-testable with a fake [BillingManager]
 * (the full [StepSyncWorker] has 11 injected deps + needs a Robolectric `Context`, so the worker
 * itself is not cheaply unit-testable). [reconcilePendingPurchases] is already idempotent,
 * mutex-serialised and connect-guarded, but `connect()` has no internal timeout (its
 * disconnect callback never resumes), so a stalled Play Services / offline device could otherwise
 * hang the worker until WorkManager's execution cap — hence the [withTimeoutOrNull] bound and the
 * catch-all (a background safety net must never crash the worker).
 */
internal suspend fun reconcileBillingSafely(billingManager: BillingManager) {
    try {
        withTimeoutOrNull(BILLING_RECONCILE_TIMEOUT_MS) {
            billingManager.reconcilePendingPurchases()
        }
    } catch (e: Exception) {
        android.util.Log.w("StepSyncWorker", "Billing reconcile failed", e)
    }
}
