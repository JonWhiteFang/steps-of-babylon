package com.whitefang.stepsofbabylon.data.healthconnect

import android.util.Log
import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filters suspicious exercise sessions before they reach ActivityMinuteConverter.
 * - Truncates sessions >4 hours
 * - Discards micro-sessions <2 minutes
 * - Rejects sessions beyond 5 distinct activity types per day
 */
@Singleton
class ActivityMinuteValidator
    @Inject
    constructor(
        private val antiCheatPrefs: AntiCheatPreferences,
    ) {
        companion object {
            private const val TAG = "AntiCheat"
            private const val MAX_SESSION_MINUTES = 240
            private const val MIN_SESSION_MINUTES = 2
            private const val MAX_ACTIVITY_TYPES = 5
        }

        fun validate(sessions: List<ExerciseSessionInfo>): List<ExerciseSessionInfo> {
            val seenTypes = mutableSetOf<Int>()
            val result = mutableListOf<ExerciseSessionInfo>()

            for (session in sessions) {
                // Micro-session filter
                if (session.durationMinutes < MIN_SESSION_MINUTES) {
                    antiCheatPrefs.incrementActivityMinutesRejected(session.durationMinutes.toLong())
                    Log.d(TAG, "Rejected micro-session: ${session.durationMinutes}min (type=${session.exerciseType})")
                    continue
                }

                // Type diversity cap
                seenTypes.add(session.exerciseType)
                if (seenTypes.size > MAX_ACTIVITY_TYPES) {
                    antiCheatPrefs.incrementActivityMinutesRejected(session.durationMinutes.toLong())
                    Log.d(TAG, "Rejected session: >$MAX_ACTIVITY_TYPES activity types (type=${session.exerciseType})")
                    continue
                }

                // Truncate extreme duration
                if (session.durationMinutes > MAX_SESSION_MINUTES) {
                    val rejected = session.durationMinutes - MAX_SESSION_MINUTES
                    antiCheatPrefs.incrementActivityMinutesRejected(rejected.toLong())
                    Log.d(TAG, "Truncated session: ${session.durationMinutes}min → ${MAX_SESSION_MINUTES}min")
                    val truncatedEnd = session.startTime.plus(Duration.ofMinutes(MAX_SESSION_MINUTES.toLong()))
                    result.add(session.copy(endTime = truncatedEnd, durationMinutes = MAX_SESSION_MINUTES))
                    continue
                }

                result.add(session)
            }

            return result
        }
    }
