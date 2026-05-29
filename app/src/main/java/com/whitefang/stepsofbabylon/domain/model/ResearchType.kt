package com.whitefang.stepsofbabylon.domain.model

/**
 * Lab research types. 12 enums, all consumed by combat-path / step-credit / cash-economy /
 * UW-cooldown wiring as of RO-11 + R4-02b + V1X-15b (pre-RO-11 the entire system was dead —
 * declared with effect descriptions and costing Steps + real-time + Gems but never read by
 * any gameplay class). Phase A wired the 7 simple multipliers; Phase B wired [WAVE_SKIP];
 * R4-02b adds [MULTISHOT_RESEARCH] / [BOUNCE_RESEARCH] as the per-level Steps+time path
 * complementing the in-round Cash purchases for the same two upgrades; V1X-15b wires
 * [ENEMY_INTEL] (+2 %/lvl damage outer multiplier + UI overlays). The remaining one
 * ([AUTO_UPGRADE_AI]) stays gated as [isComingSoon] = true and deferred to v1.x — its
 * description is updated to surface the deferral and the Labs screen disables the Start
 * Research button so testers don't spend Steps on something that won't land before
 * production rollout. Research progress on the deferred enum is preserved so any
 * pre-deferral research carries over once v1.x ships.
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
     * `true` for the one enum whose gameplay implementation is deferred to v1.x
     * ([AUTO_UPGRADE_AI]; RO-11 #B.2). Pre-RO-11 this was dead like the others; rather than
     * ship a real implementation that would require ~2 days of design + UI work
     * (auto-purchase coroutine + optimal-upgrade definition), Phase B gates it in the Labs
     * UI with a Coming Soon badge and disables the Start Research button. [ENEMY_INTEL] was
     * flipped to wired in V1X-15b. Default `false` for the 11 wired enums.
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
        8_000, 4.0, 10, 2.0,
        "Tactical awareness. +2% damage per level. Reveals next wave at L1, enemy HP at L5, boss timing at L10.",
        costScaling = 1.5,
    ),
    /**
     * Permanent +1 multishot target per level. Stacks with the in-round Cash purchase of
     * [UpgradeType.MULTISHOT] and the baseline 1 target; the final stat is capped at 11
     * targets in [com.whitefang.stepsofbabylon.domain.usecase.ResolveStats]. Cost curve
     * (5,000 Steps base × 1.5× scaling) matches the in-round MULTISHOT Cash curve so the
     * two paths are directly comparable. Time at 6 h base / 1.10× scaling matches
     * [UW_COOLDOWN]'s tempo — a moderately impactful research that takes ~95 hours
     * cumulative to max from L0 → L10. (R4-02b, 2026-05-23.)
     */
    MULTISHOT_RESEARCH(5_000, 6.0, 10, 1.0, "+1 multishot target", costScaling = 1.5),
    /**
     * Permanent +1 projectile bounce per level. Stacks with the in-round Cash purchase of
     * [UpgradeType.BOUNCE_SHOT]; the final stat is capped at 10 bounces in
     * [com.whitefang.stepsofbabylon.domain.usecase.ResolveStats]. Cost curve and time match
     * [MULTISHOT_RESEARCH]; base cost differs (8,000 Steps) to mirror the in-round BOUNCE_SHOT
     * Cash curve. Card [com.whitefang.stepsofbabylon.domain.model.CardType.CHAIN_REACTION]
     * still stacks additively post-cap, so a fully-maxed combination produces the documented
     * extended bounces. (R4-02b, 2026-05-23.)
     */
    BOUNCE_RESEARCH(8_000, 6.0, 10, 1.0, "+1 projectile bounce", costScaling = 1.5),
}
