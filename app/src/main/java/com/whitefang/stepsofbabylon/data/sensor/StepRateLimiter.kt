package com.whitefang.stepsofbabylon.data.sensor

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rate-limits step credits to prevent cheating.
 * - Normal cap: 200 steps per rolling 1-minute window
 * - Burst cap: 250 steps per minute allowed when activity window < 5 minutes (running)
 */
@Singleton
class StepRateLimiter
    @Inject
    constructor() {
        private data class Entry(
            val timestampMs: Long,
            val steps: Long,
        )

        private val window = ArrayDeque<Entry>()
        private var firstEntryMs: Long = 0L

        companion object {
            private const val WINDOW_MS = 60_000L
            private const val NORMAL_CAP = 200L
            private const val BURST_CAP = 250L
            private const val BURST_WINDOW_MS = 5 * 60_000L
        }

        /**
         * Returns the number of steps to credit after rate limiting.
         * @param rawDelta raw step count delta from sensor
         * @param timestampMs current time in millis
         * @return credited steps (0..rawDelta)
         */
        fun credit(
            rawDelta: Long,
            timestampMs: Long,
        ): Long {
            if (rawDelta <= 0) return 0

            // Evict entries older than 1 minute
            val cutoff = timestampMs - WINDOW_MS
            while (window.isNotEmpty() && window.peekFirst()!!.timestampMs < cutoff) {
                window.pollFirst()
            }

            if (window.isEmpty()) firstEntryMs = timestampMs

            val windowTotal = window.sumOf { it.steps }
            val cap = if (timestampMs - firstEntryMs < BURST_WINDOW_MS) BURST_CAP else NORMAL_CAP
            val allowed = (cap - windowTotal).coerceAtLeast(0)
            val credited = rawDelta.coerceAtMost(allowed)

            if (credited > 0) {
                window.addLast(Entry(timestampMs, credited))
            }

            return credited
        }
    }
