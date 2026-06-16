package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.UpgradeType

/**
 * One ranked combat upgrade's decision-support data for the Workshop screen (#29, spec §3-§4).
 *
 * @property valuePerStep ranking key — Δcombat-power ÷ step-cost for one workshop level.
 * @property percentPerKSteps display value — Δpower as a percentage of the player's *current* combat
 *   power, scaled per 1,000 steps. Always > 0 (only Δpower>0 upgrades are returned). Dividing by the
 *   shared current power doesn't change the ranking (spec §3.2), so the bar and the badge agree.
 * @property barFraction 0f..1f — [percentPerKSteps] normalised against the max in the returned set.
 * @property isBestBuy true on exactly one upgrade (the single Best Buy).
 * @property bestBuyAffordable meaningful only on the Best Buy row: true when the Best Buy is currently
 *   affordable; false when no candidate was affordable and it fell back to the highest-value overall
 *   (the UI greys it as "save up for this").
 */
data class UpgradeValue(
    val type: UpgradeType,
    val valuePerStep: Double,
    val percentPerKSteps: Double,
    val barFraction: Float,
    val isBestBuy: Boolean,
    val bestBuyAffordable: Boolean,
)

/**
 * Computes the value-per-step of each combat upgrade and flags the single Best Buy (#29, spec §3-§4).
 *
 * Coverage (spec §3.3): an upgrade is a candidate only when buying one workshop level raises the
 * [CombatPower] index (`Δpower > 0`). That one rule excludes Range / Damage-per-meter / Rapid Fire /
 * all Defense / all Utility (not in the index) and Critical Factor while crit chance is 0 (the synergy
 * collapses the crit factor to 1). Maxed and cost-0 upgrades are excluded defensively.
 *
 * Best Buy (spec §3.4): the highest-`valuePerStep` upgrade the player can afford; if none is
 * affordable, the highest-value overall, flagged [UpgradeValue.bestBuyAffordable] = false. Exactly one
 * upgrade is flagged; ties break by [UpgradeType] declaration order so the badge never flickers.
 *
 * Increments the **workshop** dimension via [WorkshopLevels] (spec §5.1). Pure Kotlin — guarded by
 * `architecture/DomainPurityTest`.
 */
class EvaluateUpgradeValue(
    private val resolveStats: ResolveStats = ResolveStats(),
    private val combatPower: CombatPower = CombatPower(),
    private val calculateCost: CalculateUpgradeCost = CalculateUpgradeCost(),
) {

    private data class Scored(val type: UpgradeType, val value: Double, val pct: Double)

    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        stepBalance: Long,
        candidates: Collection<UpgradeType>,
    ): List<UpgradeValue> {
        val currentPower = combatPower(resolveStats(workshopLevels))

        val scored = candidates.mapNotNull { type ->
            if (WorkshopLevels.isAtMax(workshopLevels, type)) return@mapNotNull null
            val cost = calculateCost(type, WorkshopLevels.levelOf(workshopLevels, type))
            if (cost <= 0L) return@mapNotNull null
            val nextPower = combatPower(resolveStats(WorkshopLevels.withIncremented(workshopLevels, type)))
            val delta = nextPower - currentPower
            if (delta <= 0.0) return@mapNotNull null
            val value = delta / cost
            val pct = if (currentPower > 0.0) value / currentPower * 1000.0 * 100.0 else 0.0
            Scored(type, value, pct)
        }
        if (scored.isEmpty()) return emptyList()

        // Best Buy: highest value affordable; else highest value overall (greyed). Ties -> lowest ordinal.
        val byValueThenOrdinal = compareByDescending<Scored> { it.value }.thenBy { it.type.ordinal }
        val costOf = { s: Scored -> calculateCost(s.type, WorkshopLevels.levelOf(workshopLevels, s.type)) }
        val affordable = scored.filter { costOf(it) <= stepBalance }
        val bestBuyIsAffordable = affordable.isNotEmpty()
        val bestBuyType = (if (bestBuyIsAffordable) affordable else scored)
            .minWithOrNull(byValueThenOrdinal)!!.type

        val maxPct = scored.maxOf { it.pct }
        return scored.map { s ->
            UpgradeValue(
                type = s.type,
                valuePerStep = s.value,
                percentPerKSteps = s.pct,
                barFraction = if (maxPct > 0.0) (s.pct / maxPct).toFloat() else 0f,
                isBestBuy = s.type == bestBuyType,
                // True only on the flagged Best Buy row (the field is meaningful only there).
                bestBuyAffordable = bestBuyIsAffordable && s.type == bestBuyType,
            )
        }
    }
}
