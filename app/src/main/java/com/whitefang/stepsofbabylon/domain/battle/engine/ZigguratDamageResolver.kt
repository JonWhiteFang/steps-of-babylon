package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.Damageable
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDefense
import kotlin.random.Random

/**
 * Pure-domain resolution of a hit against the ziggurat (#306, ADR-0012 Phase 5 Slice 1). Lifted from
 * `presentation/battle/engine/CombatResolver.applyDamageToZiggurat` (defense mitigation → death-defy →
 * second-wind → normal damage with a 0.0 HP floor → <25% shake-threshold crossing). Mutates the
 * [Damageable] target's HP directly; returns only the [DamageOutcome] the presentation adapter needs to
 * decide side-effects (screen shake). Thorn reflection stays in the adapter (it calls a presentation
 * entity's takeDamage). No Android imports; holds no monitor — the caller invokes it inside the engine's
 * held `entitiesLock`.
 */
class ZigguratDamageResolver(
    private val calculateDefense: CalculateDefense = CalculateDefense(),
    private val random: Random = Random.Default,
) {
    /** @property crossedShakeThreshold the HP% dropped through 25% this hit (adapter fires screen shake). */
    data class DamageOutcome(
        val crossedShakeThreshold: Boolean,
    )

    /**
     * Applies [rawDamage] to [target], mutating [Damageable.currentHp]. [consumeSecondWind] is the
     * caller's one-shot test-and-set (invoked at most once, only on a lethal hit when death-defy did not
     * already save the ziggurat) — matching the pre-hoist inline order exactly.
     *
     * `@Suppress("ReturnCount")`: the guarded death-defy / second-wind early-returns are lifted verbatim
     * from the (already detekt-baselined) `CombatResolver.applyDamageToZiggurat` control flow — kept
     * faithful, not flattened.
     */
    @Suppress("ReturnCount")
    fun resolve(
        target: Damageable,
        rawDamage: Double,
        stats: ResolvedStats,
        secondWindHpPercent: Double,
        consumeSecondWind: () -> Boolean,
    ): DamageOutcome {
        val mitigated = calculateDefense(rawDamage, stats)
        if (target.currentHp - mitigated <= 0.0 && stats.deathDefyChance > 0) {
            if (random.nextDouble() < stats.deathDefyChance) {
                target.currentHp = 1.0
                return DamageOutcome(crossedShakeThreshold = false)
            }
        }
        if (target.currentHp - mitigated <= 0.0 && secondWindHpPercent > 0.0 && consumeSecondWind()) {
            target.currentHp = target.maxHp * secondWindHpPercent
            return DamageOutcome(crossedShakeThreshold = false)
        }
        val prevHpRatio = target.currentHp / target.maxHp
        target.currentHp = (target.currentHp - mitigated).coerceAtLeast(0.0)
        val newHpRatio = target.currentHp / target.maxHp
        return DamageOutcome(crossedShakeThreshold = prevHpRatio > 0.25 && newHpRatio <= 0.25)
    }
}
