package com.whitefang.stepsofbabylon.domain.model

/**
 * Lab research types. 10 enums, all consumed by combat-path / step-credit / cash-economy /
 * UW-cooldown wiring as of RO-11 (pre-RO-11 the entire system was dead — declared with
 * effect descriptions and costing Steps + real-time + Gems but never read by any gameplay
 * class). Phase A wired the 7 simple multipliers; Phase B wired [WAVE_SKIP]; the remaining
 * two ([AUTO_UPGRADE_AI], [ENEMY_INTEL]) are gated as [isComingSoon] = true and deferred
 * to v1.x — their descriptions are updated to surface the deferral and the Labs screen
 * disables the Start Research button so testers don't spend Steps on something that won't
 * land before production rollout. Research progress on the deferred two is preserved so
 * any pre-deferral research carries over once v1.x ships.
 */
enum class ResearchType(
    val baseCostSteps: Long,
    val baseTimeHours: Double,
    val maxLevel: Int,
    val effectPerLevel: Double,
    val description: String,
    val costScaling: Double = 1.15,
    val timeScaling: Double = 1.10,
    /**
     * `true` for the two enums whose gameplay implementation is deferred to v1.x
     * (RO-11 #B.2). Pre-RO-11 these were dead like the other 8; rather than ship a real
     * implementation that would require ~2 days of design + UI work each (auto-purchase
     * coroutine + optimal-upgrade definition for [AUTO_UPGRADE_AI]; HP-bar gating + wave
     * preview + boss telegraph UI for [ENEMY_INTEL]), Phase B gates them in the Labs UI
     * with a Coming Soon badge and disables the Start Research button. Default `false`
     * for the 8 wired enums.
     */
    val isComingSoon: Boolean = false,
) {
    DAMAGE_RESEARCH(2_000, 4.0, 20, 5.0, "+5% base damage multiplier"),
    HEALTH_RESEARCH(2_000, 4.0, 20, 5.0, "+5% max health multiplier"),
    CASH_RESEARCH(1_500, 3.0, 20, 5.0, "+5% cash earned multiplier"),
    STEP_EFFICIENCY(5_000, 8.0, 10, 2.0, "+2% bonus steps from walking"),
    WAVE_SKIP(10_000, 24.0, 10, 1.0, "Start rounds at wave X instead of wave 1"),
    AUTO_UPGRADE_AI(
        8_000, 12.0, 5, 1.0,
        "Reserved for v1.x — research progress preserved",
        isComingSoon = true,
    ),
    UW_COOLDOWN(4_000, 6.0, 15, 3.0, "-3% Ultimate Weapon cooldown"),
    CRITICAL_RESEARCH(3_000, 5.0, 15, 3.0, "+3% critical damage multiplier"),
    REGEN_RESEARCH(2_500, 4.5, 15, 4.0, "+4% health regen multiplier"),
    ENEMY_INTEL(
        3_000, 6.0, 3, 1.0,
        "Reserved for v1.x — research progress preserved",
        isComingSoon = true,
    ),
}
