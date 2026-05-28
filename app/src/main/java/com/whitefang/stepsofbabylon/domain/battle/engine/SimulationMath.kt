package com.whitefang.stepsofbabylon.domain.battle.engine

import kotlin.math.floor

/**
 * Pure mathematical helpers extracted from `presentation/battle/engine/GameEngine.kt`
 * as the first phase of the V1X-09 simulation extraction (ADR-0012).
 *
 * No Android imports. All functions are deterministic, side-effect-free, and
 * directly testable without Robolectric. The presentation-layer GameEngine
 * delegates to these for its pure-math operations; entity rendering,
 * collision system, and Canvas-using paths remain in the presentation layer
 * pending the full Phase 2 extraction.
 *
 * Functions here represent the "core simulation math" — combat damage
 * modifiers, HP healing pulses, periodic-effect scheduling, and effect-stacking
 * formulas — that is pure-business-logic and should never have been coupled to
 * Canvas/SurfaceView in the first place.
 */
object SimulationMath {

    // ---- Recovery Packages (RO-08) ----

    /** Each interval of active SPAWNING phase grants one heal pulse. */
    const val RECOVERY_INTERVAL_SECONDS = 30f

    /** Per-level heal amount as a fraction of max HP. */
    const val RECOVERY_PERCENT_PER_LEVEL = 0.01

    /** Hard cap per pulse: high levels can't one-shot a near-empty tower to full. */
    const val RECOVERY_PERCENT_PER_PULSE_CAP = 0.50

    /**
     * Recovery Packages heal amount for one pulse.
     *
     * @param level Player's RECOVERY_PACKAGES upgrade level (≥0).
     * @param maxHp Ziggurat max HP at time of pulse.
     * @return HP amount to heal (≥1 if level > 0 and maxHp > 0; 0 if level == 0).
     */
    fun recoveryPulseAmount(level: Int, maxHp: Double): Double {
        if (level <= 0 || maxHp <= 0) return 0.0
        val percent = (level * RECOVERY_PERCENT_PER_LEVEL).coerceAtMost(RECOVERY_PERCENT_PER_PULSE_CAP)
        return (maxHp * percent).coerceAtLeast(1.0)
    }

    // ---- Chrono Field UW (RO-09 #1) ----

    /**
     * Per-entity `deltaTime` scaling factor when CHRONO_FIELD UW is active.
     * Applied only to enemy entities; projectiles/orbs/ziggurat keep full dt.
     * 0.10 = enemies move at 10 % of normal speed; 1.0 = unchanged.
     */
    const val CHRONO_SLOW_FACTOR_DEFAULT = 0.10f

    /**
     * Resolved chrono multiplier for an enemy's `deltaTime`. Returns the
     * configured slow factor when active, 1.0f otherwise. The
     * configurability (vs the pre-R4-06 fixed constant) supports per-cooldown-level
     * variation in Chrono Field's secondary path.
     */
    fun chronoMultiplier(active: Boolean, slowFactor: Float = CHRONO_SLOW_FACTOR_DEFAULT): Float =
        if (active) slowFactor else 1f

    // ---- Thorn Damage (R3-02) ----

    /**
     * Reflected thorn damage applied to a melee attacker. The damage that the
     * ziggurat *would have* taken (`rawDamage`) is multiplied by the player's
     * `thornPercent` and the tier-condition modifier.
     *
     * @param rawDamage Pre-defense damage the ziggurat received (Double, conserves
     *                  fractional values for low-level multipliers).
     * @param thornPercent Player's THORN_DAMAGE upgrade level translated to a fraction
     *                     (e.g. Lv 5 = 0.10).
     * @param conditionMultiplier Tier battle-condition multiplier (typically 1.0).
     * @return Damage amount to apply to the attacker. 0 if either factor is 0.
     */
    fun thornReflectionDamage(
        rawDamage: Double,
        thornPercent: Double,
        conditionMultiplier: Double = 1.0,
    ): Double {
        if (rawDamage <= 0 || thornPercent <= 0) return 0.0
        return rawDamage * thornPercent * conditionMultiplier
    }

    // ---- Lifesteal (R3-02) ----

    /** Maximum lifesteal percent (0.15 = 15%). Per Plan 10b balance lock. */
    const val LIFESTEAL_CAP = 0.15

    /**
     * Lifesteal heal amount for one hit. Formula: `damage × lifestealPercent`,
     * with `lifestealPercent` clamped at [LIFESTEAL_CAP].
     *
     * @param damageDealt Damage dealt to the enemy this hit.
     * @param lifestealPercent Player's LIFESTEAL upgrade level translated to a fraction.
     * @return HP amount to heal (Double, conserves sub-1 fractions across hits).
     */
    fun lifestealHealAmount(damageDealt: Double, lifestealPercent: Double): Double {
        if (damageDealt <= 0 || lifestealPercent <= 0) return 0.0
        return damageDealt * lifestealPercent.coerceAtMost(LIFESTEAL_CAP)
    }

    /**
     * Result of crossing an integer-HP threshold via accumulator.
     * @property newAccumulator The accumulator value after consuming the visible HP.
     * @property visibleHp The integer HP amount that crossed the threshold (0 if none).
     */
    data class LifestealAccumulatorTick(val newAccumulator: Double, val visibleHp: Int)

    /**
     * Updates the lifesteal accumulator with a new heal amount and returns the new state
     * plus the visible HP burst (if any) for the floating-text feedback indicator.
     *
     * The accumulator pattern conserves sub-1 HP heals across many hits so low-level
     * lifesteal still produces visible feedback every ~50 hits instead of being
     * sub-pixel-imperceptible.
     *
     * @param accumulator Current accumulator value.
     * @param healAmount Heal amount from [lifestealHealAmount].
     * @return [LifestealAccumulatorTick] with the new accumulator and the integer HP
     *         that crossed the threshold (used to spawn `+X HP` feedback).
     */
    fun tickLifestealAccumulator(accumulator: Double, healAmount: Double): LifestealAccumulatorTick {
        val newTotal = accumulator + healAmount
        if (newTotal < 1.0) return LifestealAccumulatorTick(newTotal, 0)
        val visibleHp = floor(newTotal).toInt()
        return LifestealAccumulatorTick(newTotal - visibleHp, visibleHp)
    }

    // ---- HP Clamping (defensive helper) ----

    /**
     * Clamps a candidate HP value to `[0, maxHp]`. Used by the various healing
     * pulses (recovery, lifesteal) and damage-application paths to prevent
     * over-healing past the cap or going negative on a one-shot kill.
     */
    fun clampHp(candidateHp: Double, maxHp: Double): Double =
        candidateHp.coerceIn(0.0, maxHp.coerceAtLeast(0.0))
}
