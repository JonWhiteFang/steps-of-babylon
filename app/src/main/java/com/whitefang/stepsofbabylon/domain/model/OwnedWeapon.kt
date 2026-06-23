package com.whitefang.stepsofbabylon.domain.model

/**
 * Player-owned Ultimate Weapon state. Pre-R4-06 a single `level` axis drove all
 * gameplay; R4-06 splits this into 3 independent path levels (see [UWPath]) plus an
 * explicit [isUnlocked] flag. The flag is necessary because all three path levels can
 * coexist with `isUnlocked = false` mid-migration (the data layer ensures once a row
 * exists, [isUnlocked] is true) — surfacing the flag here keeps the domain model
 * self-describing.
 *
 * `level == 0` semantics: the path was never upgraded after unlock. The UW still
 * functions at a slightly weaker-than-L1 baseline (linear extrapolation in
 * [UltimateWeaponType.valueAtLevel]) so the freshly-unlocked UW is immediately useful
 * but rewards investment.
 */
data class OwnedWeapon(
    val type: UltimateWeaponType,
    val damageLevel: Int = 0,
    val secondaryLevel: Int = 0,
    val cooldownLevel: Int = 0,
    val isUnlocked: Boolean = false,
    val isEquipped: Boolean = false,
) {
    /** Returns the level for the given path. */
    fun levelOf(path: UWPath): Int =
        when (path) {
            UWPath.DAMAGE -> damageLevel
            UWPath.SECONDARY -> secondaryLevel
            UWPath.COOLDOWN -> cooldownLevel
        }
}
