package com.whitefang.stepsofbabylon.data.sensor

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects unnatural step patterns (phone shakers, spoofing) by analyzing
 * step rate variance and acceleration over a rolling window.
 * Returns a penalty multiplier (0.0–1.0) applied to credited steps.
 */
@Singleton
class StepVelocityAnalyzer
    @Inject
    constructor() {
        private data class Entry(
            val timestampMs: Long,
            val steps: Long,
        )

        private val window = ArrayDeque<Entry>()

        companion object {
            private const val WINDOW_MS = 15 * 60_000L // 15-minute rolling window
            private const val MIN_ENTRIES = 5 // need enough data to judge
            private const val CONSTANT_WINDOW_MS = 10 * 60_000L // 10 min for variance check
            private const val CV_THRESHOLD = 0.05 // coefficient of variation < 5% = suspicious
            private const val JUMP_LOW_RATE = 20.0 // steps/min considered idle
            private const val JUMP_HIGH_RATE = 150.0 // steps/min considered sudden spike
        }

        /**
         * @return multiplier: 1.0 = normal, 0.5 = one flag, 0.0 = both flags
         */
        fun analyze(
            rawDelta: Long,
            timestampMs: Long,
        ): Double {
            if (rawDelta <= 0) return 1.0

            // Evict old entries
            val cutoff = timestampMs - WINDOW_MS
            while (window.isNotEmpty() && window.peekFirst()!!.timestampMs < cutoff) {
                window.pollFirst()
            }

            window.addLast(Entry(timestampMs, rawDelta))

            if (window.size < MIN_ENTRIES) return 1.0

            val jumpFlag = checkInstantJump()
            val constantFlag = checkConstantRate(timestampMs)

            return when {
                jumpFlag && constantFlag -> 0.0
                jumpFlag || constantFlag -> 0.5
                else -> 1.0
            }
        }

        private fun checkInstantJump(): Boolean {
            if (window.size < 2) return false
            val entries = window.toList()
            // Check last 3 pairs for a jump (persists flag for ~3 minutes after spike)
            val checkCount = minOf(3, entries.size - 1)
            for (i in entries.size - checkCount until entries.size) {
                val prev = entries[i - 1]
                val curr = entries[i]
                val dtMin = (curr.timestampMs - prev.timestampMs) / 60_000.0
                if (dtMin <= 0) continue
                val prevRate = prev.steps / dtMin
                val currRate = curr.steps / dtMin
                if (prevRate < JUMP_LOW_RATE && currRate > JUMP_HIGH_RATE) return true
            }
            return false
        }

        private fun checkConstantRate(nowMs: Long): Boolean {
            val cutoff = nowMs - CONSTANT_WINDOW_MS
            val recent = window.filter { it.timestampMs >= cutoff }
            if (recent.size < MIN_ENTRIES) return false

            // Compute per-entry rates (steps per interval)
            val rates = mutableListOf<Double>()
            for (i in 1 until recent.size) {
                val dt = (recent[i].timestampMs - recent[i - 1].timestampMs) / 60_000.0
                if (dt > 0) rates.add(recent[i].steps / dt)
            }
            if (rates.size < 4) return false

            val mean = rates.average()
            if (mean < 30) return false // too slow to be suspicious
            val variance = rates.sumOf { (it - mean) * (it - mean) } / rates.size
            val stdDev = kotlin.math.sqrt(variance)
            val cv = stdDev / mean

            return cv < CV_THRESHOLD
        }
    }
