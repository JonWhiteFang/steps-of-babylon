package com.whitefang.stepsofbabylon.presentation.battle

/**
 * #384 (`perf-2`): a tiny, pure, **loop-thread-confined** accumulator for battle-loop frame timings — the
 * data behind the DEBUG frame-stats overlay ([FrameStatsOverlay]). It reuses the `frameTime` the loop
 * already computes for its sleep calculation (no new timing call), so it adds only a few arithmetic ops
 * per frame and is gated behind `BuildConfig.DEBUG` at the call site (zero release cost).
 *
 * ## Thread-confinement contract (load-bearing — do NOT break)
 * Every field is written and read **only on the game-loop thread** (inside `GameLoopThread.run()`). It is
 * NOT shared with the UI/main thread, so it deliberately uses plain `var`s with **no lock and no
 * `@Volatile`**. If a future change needs to read these stats off-thread (e.g. a ViewModel poll), that is
 * a new cross-thread hop that MUST add synchronization — do not just reach in.
 *
 * ## Windowing
 * Stats accumulate over a rolling window of [windowFrames] frames (~1s at 60 UPS). When the window fills,
 * [record] rolls the accumulated values into an immutable [Snapshot] (exposed via [snapshot]) and resets
 * the accumulators. So the overlay shows the *previous* window's stats — one window stale, standard for an
 * FPS/frame-time overlay.
 *
 * Pure Kotlin (no Android imports) so the accumulation math is directly JVM-unit-testable ([FrameStatsTest]).
 *
 * @param windowFrames number of frames per reporting window (default 60 ≈ 1s at 60 UPS).
 */
class FrameStats(
    private val windowFrames: Int = 60,
) {
    /** Immutable per-window readout for the overlay. Times are in milliseconds; [ups] is frames/sec estimate. */
    data class Snapshot(
        val minMs: Double,
        val avgMs: Double,
        val maxMs: Double,
        val dropped: Int,
        val ups: Double,
    )

    private var count = 0
    private var sumNs = 0L
    private var minNs = Long.MAX_VALUE
    private var maxNs = Long.MIN_VALUE
    private var droppedInWindow = 0

    /** Last completed window's readout; null until the first window fills. */
    var last: Snapshot? = null
        private set

    /**
     * Feed one frame's measured duration. [frameTimeNs] is the already-computed update+render cost;
     * [tickNs] is the target per-tick budget (a frame slower than this couldn't sustain the target rate →
     * counted as dropped). Rolls the window into [last] and resets when [windowFrames] frames have been seen.
     */
    fun record(
        frameTimeNs: Long,
        tickNs: Long,
    ) {
        count++
        sumNs += frameTimeNs
        if (frameTimeNs < minNs) minNs = frameTimeNs
        if (frameTimeNs > maxNs) maxNs = frameTimeNs
        if (frameTimeNs > tickNs) droppedInWindow++

        if (count >= windowFrames) {
            val avgNs = sumNs.toDouble() / count
            last =
                Snapshot(
                    minMs = minNs / 1_000_000.0,
                    avgMs = avgNs / 1_000_000.0,
                    maxMs = maxNs / 1_000_000.0,
                    dropped = droppedInWindow,
                    // UPS estimate from the average frame time (frames per second the loop could sustain
                    // at this average cost, capped by the target rate). avgNs is > 0 whenever count > 0.
                    ups = if (avgNs > 0.0) 1_000_000_000.0 / avgNs else 0.0,
                )
            count = 0
            sumNs = 0L
            minNs = Long.MAX_VALUE
            maxNs = Long.MIN_VALUE
            droppedInWindow = 0
        }
    }

    /** The most recent completed window's snapshot, or null before the first window fills. */
    fun snapshot(): Snapshot? = last
}
