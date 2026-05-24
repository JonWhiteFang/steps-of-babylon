package com.whitefang.stepsofbabylon.domain.model

/**
 * Three independent upgrade paths per Ultimate Weapon (R4-06). Replaces the pre-R4-06
 * single `level` axis. Each path advances independently from L0 (just unlocked, no path
 * upgrades yet) to L10 (max). The path's gameplay meaning varies per UW — see
 * [UltimateWeaponType] for the per-UW per-path L1/L10 spec table:
 *
 * - [DAMAGE]: the UW's "main" damage axis. For CHAIN_LIGHTNING / DEATH_WAVE / BLACK_HOLE
 *   this is direct damage; for POISON_SWAMP it's % MaxHP/sec DoT; for CHRONO_FIELD it's
 *   the slow factor (smaller is more powerful); for GOLDEN_ZIGGURAT it's the cash
 *   multiplier.
 * - [SECONDARY]: the UW's novel mechanic axis. Chain length / radius / pull strength /
 *   duration / area / damage multiplier — UW-specific.
 * - [COOLDOWN]: time between auto-fires. Symmetric across all 6 UWs (lower is better).
 *   Multiplied by [com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine.uwCooldownMultiplier]
 *   at activation site so the UW_COOLDOWN lab research from RO-11 still stacks.
 *
 * All 3 paths share the same cost formula `unlockCost × 2 × currentPathLevel` per the
 * R4-06 plan (matches the pre-R4-06 single-level pattern). At L0 the cost to advance is
 * 0 — the first per-path purchase is effectively included in the unlock fee. At L9 the
 * cost is `18 × unlockCost` to reach L10.
 */
enum class UWPath {
    DAMAGE,
    SECONDARY,
    COOLDOWN,
    ;

    companion object {
        /** The three paths in display order (matches enum declaration). */
        val ALL: List<UWPath> = entries.toList()
    }
}
