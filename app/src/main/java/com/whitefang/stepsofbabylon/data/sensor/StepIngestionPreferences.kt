package com.whitefang.stepsofbabylon.data.sensor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state for coordinating step ingestion between StepCounterService and StepSyncWorker.
 * Prevents double-crediting by providing a service heartbeat and day-start counter baseline.
 */
@Singleton
class StepIngestionPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("step_ingestion", Context.MODE_PRIVATE)

        companion object {
            private const val KEY_SERVICE_HEARTBEAT = "service_heartbeat"
            private const val KEY_DAY_START_COUNTER = "day_start_counter"
            private const val KEY_DAY_START_DATE = "day_start_date"

            // #123: Room sensorSteps captured at the moment the day-start baseline was (re)established,
            // so the worker's gap math measures credited-steps-since-baseline rather than the absolute
            // same-day Room cumulative. On a mid-day reboot the baseline is re-anchored to the
            // post-reboot counter AND this offset to the current Room cumulative, so post-reboot steps
            // credit correctly instead of being swallowed by a stale subtraction.
            private const val KEY_SENSOR_STEPS_AT_DAY_START = "sensor_steps_at_day_start"
            const val HEARTBEAT_THRESHOLD_MS = 2 * 60 * 1000L // 2 minutes
        }

        fun updateServiceHeartbeat(timestampMs: Long) {
            prefs.edit().putLong(KEY_SERVICE_HEARTBEAT, timestampMs).apply()
        }

        fun getServiceHeartbeat(): Long = prefs.getLong(KEY_SERVICE_HEARTBEAT, 0L)

        fun isServiceAlive(nowMs: Long): Boolean = nowMs - getServiceHeartbeat() < HEARTBEAT_THRESHOLD_MS

        /**
         * Records the day-start sensor baseline. [sensorStepsAtDayStart] (#123) is the Room
         * `sensorSteps` cumulative captured at this same instant — `0` for the normal first-of-day
         * baseline, or the current cumulative when re-anchoring after a mid-day reboot so the worker's
         * gap math stays relative to the new baseline.
         */
        fun setCounterAtDayStart(
            date: String,
            counterValue: Long,
            sensorStepsAtDayStart: Long = 0L,
        ) {
            prefs
                .edit()
                .putString(KEY_DAY_START_DATE, date)
                .putLong(KEY_DAY_START_COUNTER, counterValue)
                .putLong(KEY_SENSOR_STEPS_AT_DAY_START, sensorStepsAtDayStart)
                .apply()
        }

        fun getCounterAtDayStart(date: String): Long? {
            val storedDate = prefs.getString(KEY_DAY_START_DATE, null)
            if (storedDate != date) return null
            return prefs.getLong(KEY_DAY_START_COUNTER, -1L).takeIf { it >= 0 }
        }

        /**
         * Room `sensorSteps` cumulative captured when the current day-start baseline was set (#123).
         * Returns `0` when unset / for a different date. The worker subtracts this from Room's
         * `sensorSteps` to get "already-credited since the baseline", so a re-anchored baseline after a
         * reboot doesn't subtract the pre-reboot cumulative.
         */
        fun getSensorStepsAtDayStart(date: String): Long {
            val storedDate = prefs.getString(KEY_DAY_START_DATE, null)
            if (storedDate != date) return 0L
            return prefs.getLong(KEY_SENSOR_STEPS_AT_DAY_START, 0L)
        }
    }
