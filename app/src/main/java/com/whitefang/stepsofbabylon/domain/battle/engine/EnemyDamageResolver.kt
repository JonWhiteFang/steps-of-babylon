package com.whitefang.stepsofbabylon.domain.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.entity.DamageableEnemy

/**
 * Pure-domain resolution of a hit against an enemy (#306, ADR-0012 Phase 5 Slice 2). Lifted verbatim from
 * `presentation/battle/entities/EnemyEntity.takeDamage`: corpse guard (#146) → armor absorb (#17) → HP
 * subtraction (NO floor — enemy HP may go negative, unlike the ziggurat's `coerceAtLeast(0.0)`) → death
 * detection (`currentHp <= 0.0`). Mutates the [DamageableEnemy] target's HP/armor; returns the [Outcome]
 * the presentation adapter needs: [Outcome.dealt] (damage actually dealt — 0.0 when absorbed/guarded — so
 * callers gate lifesteal/knockback on a positive value) and [Outcome.died] (the adapter flips `isAlive`
 * and fires `onDeath`). Stateless + pure — a single shared instance is safe. No Android imports; holds no
 * monitor — the caller invokes it inside the engine's held `entitiesLock`.
 */
class EnemyDamageResolver {
    /**
     * @property dealt damage actually applied to HP (0.0 when the enemy was already dead or the hit was
     *   fully armor-absorbed).
     * @property died the hit was lethal (HP crossed to <= 0.0 this call).
     */
    data class Outcome(
        val dealt: Double,
        val died: Boolean,
    )

    fun resolve(
        target: DamageableEnemy,
        amount: Double,
        isAlive: Boolean,
    ): Outcome {
        if (!isAlive) return Outcome(dealt = 0.0, died = false)
        if (target.armorHits > 0) {
            target.armorHits--
            return Outcome(dealt = 0.0, died = false)
        }
        target.currentHp -= amount
        return Outcome(dealt = amount, died = target.currentHp <= 0.0)
    }
}
