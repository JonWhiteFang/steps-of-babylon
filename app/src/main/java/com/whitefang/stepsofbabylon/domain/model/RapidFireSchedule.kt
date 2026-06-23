package com.whitefang.stepsofbabylon.domain.model

/**
 * Per-level interpolation table for the [UpgradeType.RAPID_FIRE] upgrade (R4-03).
 *
 * The Rapid Fire upgrade fires a periodic attack-speed burst during a wave's SPAWNING
 * phase: every `interval` seconds the ziggurat's attack speed is multiplied by
 * `multiplier` for `duration` seconds, then resets to 1.0× until the next pulse. As the
 * upgrade levels up, the interval shortens, the duration lengthens, and the multiplier
 * grows; at L10 the duration matches the interval (both 30 s) so the burst re-triggers
 * before the previous one expires — a permanent +3.0× attack-speed buff.
 *
 * Centralising the math means [com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine]
 * (which drives the timer) and [com.whitefang.stepsofbabylon.domain.usecase.DescribeUpgradeEffect]
 * (which renders the Now → Next readout) read identical numbers. Pure Kotlin so the
 * helper lives in `domain/model/` and is JVM-test-friendly.
 */
object RapidFireSchedule {
    /** Interval at L1 — 60 seconds between bursts. */
    const val INTERVAL_L1 = 60f

    /** Interval at L10 — converges with [DURATION_L10] for a permanent burst. */
    const val INTERVAL_L10 = 30f

    /** Burst duration at L1 — 5 seconds of active 2.0× attack speed. */
    const val DURATION_L1 = 5f

    /** Burst duration at L10 — converges with [INTERVAL_L10] for a permanent buff. */
    const val DURATION_L10 = 30f

    /** Attack-speed multiplier at L1 during the active window. */
    const val MULTIPLIER_L1 = 2f

    /** Attack-speed multiplier at L10 during the active window. */
    const val MULTIPLIER_L10 = 3f

    /** Interval (seconds between bursts) at the given level. Clamped to L1..L10. */
    fun interval(level: Int): Float = lerp(level, INTERVAL_L1, INTERVAL_L10)

    /** Burst duration (seconds the multiplier is active) at the given level. Clamped to L1..L10. */
    fun duration(level: Int): Float = lerp(level, DURATION_L1, DURATION_L10)

    /** Attack-speed multiplier during the active window at the given level. Clamped to L1..L10. */
    fun multiplier(level: Int): Float = lerp(level, MULTIPLIER_L1, MULTIPLIER_L10)

    /**
     * Returns `true` when [duration] ≥ [interval] at the given level — i.e. the next
     * pulse fires before the previous one expires, producing a continuous buff. The
     * Now → Next readout renders "permanent/{m}×" instead of "{i}s/{d}s/{m}×" in this
     * case so the player can see they've reached the cap.
     */
    fun isPermanent(level: Int): Boolean = duration(level) >= interval(level)

    /**
     * Linear interpolation between L1 and L10. Levels outside `1..10` are clamped — Kotlin
     * level types are non-negative integers and the `UpgradeConfig.maxLevel` for RAPID_FIRE
     * is 10, but defensive clamping protects against any future schema migration that
     * leaves a higher level value on disk.
     */
    private fun lerp(
        level: Int,
        l1: Float,
        l10: Float,
    ): Float {
        val clamped = level.coerceIn(1, 10)
        return l1 + (l10 - l1) * (clamped - 1) / 9f
    }
}
