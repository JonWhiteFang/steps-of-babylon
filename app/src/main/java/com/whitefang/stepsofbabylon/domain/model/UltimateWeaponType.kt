package com.whitefang.stepsofbabylon.domain.model

/**
 * The 6 Ultimate Weapons. Pre-R4-06 each UW had a single [unlockCost] /
 * [baseCooldownSeconds] / [effectDurationSeconds] triple and a single shared `level`
 * axis driving all gameplay numbers. R4-06 redesigns this into 3 independent upgrade
 * paths per UW (see [UWPath]); each path's L1 and L10 endpoints are stored on the
 * enum and the per-level value is a linear interpolation between them.
 *
 * Ranges for each path are defined in pairs (L1, L10) so the spec table from
 * `docs/plans/plan-R4-feedback-bundle.md` §R4-06 reads directly into the constructor:
 *
 * | Field | Meaning |
 * |---|---|
 * | [damageL1] / [damageL10] | DAMAGE path endpoints. UW-specific units (raw damage / DPS / % MaxHP/sec / slow factor / cash multiplier). |
 * | [secondaryL1] / [secondaryL10] | SECONDARY path endpoints. UW-specific (chain length / radius fraction / pull strength / duration / area fraction / damage multiplier). |
 * | [cooldownL1] / [cooldownL10] | COOLDOWN path endpoints, in seconds. [cooldownL10] is the lower (better) value. |
 * | [effectDurationSeconds] | Flat effect duration. CHRONO_FIELD overrides via the SECONDARY path; this constant is the L1-equivalent fallback. |
 *
 * The legacy [cooldownAtLevel] / [upgradeCost] helpers are preserved as level=1
 * shortcuts for [UWBalanceTest] + the UI cooldown-ring math; per-path values are
 * computed via [valueAtLevel] and [costForPath].
 */
enum class UltimateWeaponType(
    val unlockCost: Int,
    val description: String,
    /**
     * Effect duration in seconds. Flat for BLACK_HOLE (5s) / POISON_SWAMP (6s) /
     * GOLDEN_ZIGGURAT (10s). Zero for instant-effect UWs (DEATH_WAVE / CHAIN_LIGHTNING).
     * CHRONO_FIELD's duration is the SECONDARY path itself; this constant is the L1
     * value (5s) used when no override is needed.
     */
    val effectDurationSeconds: Float,
    val damageL1: Double,
    val damageL10: Double,
    val secondaryL1: Double,
    val secondaryL10: Double,
    val cooldownL1: Float,
    val cooldownL10: Float,
) {
    DEATH_WAVE(
        unlockCost = 50,
        description = "Massive damage pulse radiating outward, damages enemies in radius",
        effectDurationSeconds = 0f,
        // Damage: 500 → 3,000 raw damage
        damageL1 = 500.0, damageL10 = 3_000.0,
        // Secondary: 0.50 → 1.00 of screen radius
        secondaryL1 = 0.50, secondaryL10 = 1.00,
        // Cooldown: 60s → 20s
        cooldownL1 = 60f, cooldownL10 = 20f,
    ),
    CHAIN_LIGHTNING(
        unlockCost = 75,
        description = "Arcing electrical damage chaining between enemies",
        effectDurationSeconds = 0f,
        // Damage: 500 → 2,000 per-target
        damageL1 = 500.0, damageL10 = 2_000.0,
        // Secondary: 3 → 12 chain targets
        secondaryL1 = 3.0, secondaryL10 = 12.0,
        // Cooldown: 30s → 6s
        cooldownL1 = 30f, cooldownL10 = 6f,
    ),
    BLACK_HOLE(
        unlockCost = 100,
        description = "Gravity well pulling enemies inward with sustained damage",
        effectDurationSeconds = 5f,
        // Damage: 50 → 250 DPS
        damageL1 = 50.0, damageL10 = 250.0,
        // Secondary: 30 → 200 px/sec pull
        secondaryL1 = 30.0, secondaryL10 = 200.0,
        // Cooldown: 90s → 30s
        cooldownL1 = 90f, cooldownL10 = 30f,
    ),
    CHRONO_FIELD(
        unlockCost = 75,
        description = "Slows all enemies for duration; lower factor = stronger slow",
        effectDurationSeconds = 5f, // L1 baseline; actual is secondaryAtLevel
        // Damage path = slow factor: 0.50 → 0.05 (smaller is stronger). Replaces the
        // pre-R4-06 CHRONO_SLOW_FACTOR = 0.10f companion constant.
        damageL1 = 0.50, damageL10 = 0.05,
        // Secondary = duration: 5s → 14s
        secondaryL1 = 5.0, secondaryL10 = 14.0,
        // Cooldown: 75s → 25s
        cooldownL1 = 75f, cooldownL10 = 25f,
    ),
    POISON_SWAMP(
        unlockCost = 60,
        description = "Toxic area dealing % max-health damage per second",
        effectDurationSeconds = 6f,
        // Damage path = DoT % MaxHP/sec: 0.01 → 0.08
        damageL1 = 0.01, damageL10 = 0.08,
        // Secondary = area fraction: 0.50 → 1.00
        secondaryL1 = 0.50, secondaryL10 = 1.00,
        // Cooldown: 60s → 20s
        cooldownL1 = 60f, cooldownL10 = 20f,
    ),
    GOLDEN_ZIGGURAT(
        unlockCost = 80,
        description = "Cash bonus + damage boost for duration",
        effectDurationSeconds = 10f,
        // Damage path = cash multiplier: 2× → 8×
        damageL1 = 2.0, damageL10 = 8.0,
        // Secondary = damage multiplier: 1.2× → 3.0×
        secondaryL1 = 1.2, secondaryL10 = 3.0,
        // Cooldown: 90s → 30s
        cooldownL1 = 90f, cooldownL10 = 30f,
    ),
    ;

    /**
     * Per-path linear interpolation. L1 maps to the L1 endpoint, L10 to the L10
     * endpoint, with 9 equal-spaced segments between. L0 extrapolates one step below
     * L1 (the unpurchased "just unlocked" baseline) and is intentionally weaker than
     * L1 so investment is always rewarded. Levels above L10 also extrapolate but are
     * never reached in production because of [MAX_PATH_LEVEL].
     */
    fun valueAtLevel(path: UWPath, level: Int): Double {
        val (l1, l10) = when (path) {
            UWPath.DAMAGE -> damageL1 to damageL10
            UWPath.SECONDARY -> secondaryL1 to secondaryL10
            UWPath.COOLDOWN -> cooldownL1.toDouble() to cooldownL10.toDouble()
        }
        val perLevel = (l10 - l1) / 9.0
        return l1 + perLevel * (level - 1)
    }

    /** Convenience accessor for the COOLDOWN path returning Float seconds. */
    fun cooldownAtLevel(level: Int): Float = valueAtLevel(UWPath.COOLDOWN, level).toFloat()

    /** Convenience accessor for the DAMAGE path. */
    fun damageAtLevel(level: Int): Double = valueAtLevel(UWPath.DAMAGE, level)

    /** Convenience accessor for the SECONDARY path. */
    fun secondaryAtLevel(level: Int): Double = valueAtLevel(UWPath.SECONDARY, level)

    /**
     * Per-path upgrade cost: `unlockCost × 2 × currentPathLevel`. Mirrors the pre-R4-06
     * single-level formula. At currentPathLevel=0 the cost is 0 (the L0→L1 transition
     * is free, treating it as a freebie included in the unlock fee). At
     * currentPathLevel=9 (L9→L10) the cost is `18 × unlockCost`. The total cost across
     * a single path L0→L10 is `unlockCost × 2 × (0+1+…+9) = 90 × unlockCost`; across
     * all three paths it's `270 × unlockCost`.
     */
    fun costForPath(currentPathLevel: Int): Int = unlockCost * 2 * currentPathLevel

    companion object {
        /** The maximum value any single path can reach. */
        const val MAX_PATH_LEVEL = 10
    }
}
