package com.whitefang.stepsofbabylon.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import javax.inject.Inject
import javax.inject.Singleton

data class ActivityMinuteResult(
    val activityMinutes: Map<String, Int>,
    val stepEquivalents: Long,
)

/**
 * Converts exercise session minutes to step-equivalents.
 * Only credits minutes where sensor recorded <50 steps/min (double-counting prevention).
 * Enforces per-activity daily caps.
 */
@Singleton
class ActivityMinuteConverter
    @Inject
    constructor() {
        private data class ConversionRule(
            val stepEqPerMin: Int,
            val dailyCap: Long,
            val label: String,
        )

        private val rules =
            mapOf(
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to ConversionRule(100, 10_000, "cycling"),
                ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to ConversionRule(100, 10_000, "rowing"),
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to ConversionRule(120, 12_000, "swimming"),
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to ConversionRule(120, 12_000, "swimming"),
                ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR to ConversionRule(110, 11_000, "wheelchair"),
                ExerciseSessionRecord.EXERCISE_TYPE_YOGA to ConversionRule(50, 5_000, "yoga"),
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING to ConversionRule(50, 5_000, "yoga"),
            )

        companion object {
            private const val MAX_SENSOR_STEPS_PER_MIN = 50L
        }

        /**
         * @param sessions exercise sessions for the day
         * @param sensorStepsPerMinute map of epoch-minute → sensor steps in that minute (for double-counting prevention)
         */
        fun convert(
            sessions: List<ExerciseSessionInfo>,
            sensorStepsPerMinute: Map<Long, Long>,
        ): ActivityMinuteResult {
            val minutesByActivity = mutableMapOf<String, Int>()
            var totalStepEq = 0L

            for (session in sessions) {
                val rule = rules[session.exerciseType] ?: continue
                val label = rule.label

                // Check each minute of the session for double-counting
                var eligibleMinutes = 0
                val startEpochMin = session.startTime.epochSecond / 60
                for (min in 0 until session.durationMinutes) {
                    val epochMin = startEpochMin + min
                    val sensorSteps = sensorStepsPerMinute[epochMin] ?: 0
                    if (sensorSteps < MAX_SENSOR_STEPS_PER_MIN) {
                        eligibleMinutes++
                    }
                }

                val currentForActivity = minutesByActivity.getOrDefault(label, 0)
                minutesByActivity[label] = currentForActivity + eligibleMinutes

                val uncapped = eligibleMinutes.toLong() * rule.stepEqPerMin
                val currentStepEq = (minutesByActivity[label]?.toLong() ?: 0) * rule.stepEqPerMin
                val capped = currentStepEq.coerceAtMost(rule.dailyCap)
                val previousStepEq = currentForActivity.toLong() * rule.stepEqPerMin
                val delta = capped - previousStepEq.coerceAtMost(rule.dailyCap)

                if (delta > 0) totalStepEq += delta
            }

            return ActivityMinuteResult(minutesByActivity, totalStepEq)
        }
    }
