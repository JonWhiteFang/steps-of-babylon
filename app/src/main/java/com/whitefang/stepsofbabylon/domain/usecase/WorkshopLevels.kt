package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType

/**
 * Shared workshop-dimension level helpers (#29, spec §5.1 / INV-6).
 *
 * The Workshop screen is about **permanent** (workshop) levels. Both the Now→Next preview
 * ([DescribeUpgradeEffect.workshopPreview]) and the value-per-step delta ([EvaluateUpgradeValue])
 * must increment the *workshop* dimension — not the in-round dimension — because for multiplicative
 * stats `ResolveStats` gives different "Next" numbers depending on which dimension is bumped. Routing
 * both through these helpers guarantees they agree on (a) the next-level map and (b) the cap test.
 *
 * The cap test gates on the **workshop-only** level, matching `PurchaseUpgrade` / `WorkshopViewModel`.
 * Pure Kotlin — guarded by `architecture/DomainPurityTest`.
 */
object WorkshopLevels {
    /** The workshop level of [type] (0 when absent). */
    fun levelOf(
        levels: Map<UpgradeType, Int>,
        type: UpgradeType,
    ): Int = levels[type] ?: 0

    /** True when [type] is at (or beyond) its workshop-level cap; always false for uncapped upgrades. */
    fun isAtMax(
        levels: Map<UpgradeType, Int>,
        type: UpgradeType,
    ): Boolean {
        val maxLevel = type.config.maxLevel ?: return false
        return levelOf(levels, type) >= maxLevel
    }

    /**
     * [levels] with [type]'s workshop level incremented by one. No clamp here — `ResolveStats`
     * applies the per-stat caps, so a level past the cap simply yields the same resolved value
     * (Δpower = 0), which the caller then excludes.
     */
    fun withIncremented(
        levels: Map<UpgradeType, Int>,
        type: UpgradeType,
    ): Map<UpgradeType, Int> = levels + (type to levelOf(levels, type) + 1)
}
