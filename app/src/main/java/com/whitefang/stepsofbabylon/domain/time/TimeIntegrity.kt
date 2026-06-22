package com.whitefang.stepsofbabylon.domain.time

/**
 * Pure-domain time-tamper decision core (#211, TIME-1). No Android imports (DomainPurityTest). Backs the
 * monotonic anti-rollback guard + the reboot-durable max-wall-clock floor described in the #211 spec.
 * The Android clock reads (SystemClock.elapsedRealtime / System.currentTimeMillis) live in the data
 * layer (AntiCheatPreferences); this object only reasons over the values.
 */

/** Persisted tamper baseline (FOUR Long slots in anti_cheat_prefs). */
data class TimeBaseline(
    val lastElapsedRealtime: Long,   // monotonic since-boot clock at last checkpoint
    val lastWallClock: Long,         // raw wall-clock at last checkpoint (to compute the next wallDelta)
    val maxWallClockSeen: Long,      // highest wall-clock ever observed — the reboot-durable rollback floor
    val trustedWallClock: Long,      // capped-accrual anchor — the trusted "now"; only ever advances by
                                     // min(wallDelta, elapsedDelta), so a forward jump's excess is never folded in
)

/** A fresh pair of readings taken together at one instant. */
data class TimeReading(val elapsedRealtime: Long, val wallClock: Long)

/** Classification of the current reading vs the persisted baseline. Always carries the advanced baseline. */
sealed interface TimeVerdict {
    val newBaseline: TimeBaseline
    data class Trusted(override val newBaseline: TimeBaseline) : TimeVerdict
    data class Rollback(override val newBaseline: TimeBaseline) : TimeVerdict
}

/** Read-side seam over the persisted baseline + a fresh reading. AntiCheatPreferences implements it; a
 *  fake backs the plain-JVM VM tests (they can't construct a Context-backed AntiCheatPreferences). #211. */
interface TimeBaselineSource {
    fun readTimeBaseline(): TimeBaseline?
    fun currentTimeReading(): TimeReading
}

object TimeIntegrity {

    /**
     * Update the baseline from a fresh reading and classify the time axis. `newBaseline.trustedWallClock`
     * IS the trusted "now" callers use to gate research.
     * - baseline == null (first run): Trusted; seed maxWallClockSeen = trustedWallClock = reading.wallClock.
     * - reading.wallClock < baseline.maxWallClockSeen: Rollback (backward jump — reboot-durable).
     * - else: Trusted.
     *
     * In ALL branches: lastWallClock/lastElapsedRealtime advance to "now"; maxWallClockSeen =
     * max(prev, reading.wallClock); trustedWallClock advances by the CAPPED delta (so an in-session
     * forward jump's excess is discarded). The capped-accrual anchor is order-independent — a read-only
     * consumer re-evaluating against an owner-advanced baseline still gets the capped value (it never
     * re-accepts the jump). Reboot (elapsedDelta < 0) → cappedDelta falls back to the full wallDelta
     * (accepted §2 forward-jump-across-reboot gap); the Rollback floor guards only the backward direction.
     */
    fun evaluate(baseline: TimeBaseline?, reading: TimeReading): TimeVerdict {
        if (baseline == null) {
            return TimeVerdict.Trusted(
                TimeBaseline(
                    lastElapsedRealtime = reading.elapsedRealtime,
                    lastWallClock = reading.wallClock,
                    maxWallClockSeen = reading.wallClock,
                    trustedWallClock = reading.wallClock,
                ),
            )
        }
        val wallDelta = reading.wallClock - baseline.lastWallClock
        val elapsedDelta = reading.elapsedRealtime - baseline.lastElapsedRealtime
        val cappedDelta =
            if (elapsedDelta < 0) wallDelta.coerceAtLeast(0)          // reboot (elapsedDelta < 0): no monotonic cap; full wallDelta accepted per §2 (Rollback floor still guards backward)
            else minOf(wallDelta, elapsedDelta).coerceAtLeast(0)
        val advanced = TimeBaseline(
            lastElapsedRealtime = reading.elapsedRealtime,
            lastWallClock = reading.wallClock,
            maxWallClockSeen = maxOf(baseline.maxWallClockSeen, reading.wallClock),
            trustedWallClock = baseline.trustedWallClock + cappedDelta,
        )
        return if (reading.wallClock < baseline.maxWallClockSeen) {
            TimeVerdict.Rollback(advanced)
        } else {
            TimeVerdict.Trusted(advanced)
        }
    }
}
