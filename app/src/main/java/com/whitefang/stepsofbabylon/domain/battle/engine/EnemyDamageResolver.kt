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

    /**
     * Branch order is load-bearing and matches the pre-hoist `takeDamage` exactly: corpse guard first, then
     * armor, then HP. Written as a `when` expression rather than guard clauses because three early returns
     * trip detekt's `ReturnCount` limit of 2 — the semantics are identical.
     */
    fun resolve(
        target: DamageableEnemy,
        amount: Double,
        isAlive: Boolean,
    ): Outcome =
        when {
            // #146: a dead enemy leaves `entities` only at end of frame, so a later hit in the same
            // collision sweep must be a no-op rather than re-triggering death.
            !isAlive -> {
                Outcome(dealt = 0.0, died = false)
            }

            // #17: an armor charge absorbs the whole hit — no HP lost, and `dealt = 0.0` so the caller
            // grants no lifesteal or knockback for it.
            target.armorHits > 0 -> {
                target.armorHits--
                Outcome(dealt = 0.0, died = false)
            }

            // No HP floor: overkill drives HP negative, unlike the ziggurat's coerceAtLeast(0.0).
            else -> {
                target.currentHp -= amount
                Outcome(dealt = amount, died = target.currentHp <= 0.0)
            }
        }
}
