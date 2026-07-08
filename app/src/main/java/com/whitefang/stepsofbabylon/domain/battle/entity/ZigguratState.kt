package com.whitefang.stepsofbabylon.domain.battle.entity

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import kotlin.math.min

/**
 * Pure ziggurat simulation state extracted from
 * `presentation/battle/entities/ZigguratEntity` for V1X-09 Phase 2 (ADR-0012) — the final
 * per-entity extraction.
 *
 * No Android imports. Owns the live combat stats, HP, RAPID_FIRE multiplier, and the attack
 * cooldown, plus the derived `attackInterval` / `attackRange` reads and the HP-regen +
 * attack-readiness logic. The presentation [ZigguratEntity] delegates its public properties
 * (`currentHp`, `maxHp`, `stats`, `rapidFireMultiplier`, `attackRange`, `updateStats`) to this
 * state — so `GameEngine` / `BattleViewModel` are untouched — and keeps the layer geometry,
 * the nearest-enemy targeting / fire callback (which need presentation `EnemyEntity` refs),
 * and the Canvas `render()`. Math is identical to the pre-extraction entity.
 */
class ZigguratState(
    initialStats: ResolvedStats,
) : Damageable {
    /** Live combat stats; redirected by [updateStats] when an in-round upgrade / UW mutates them. */
    var stats: ResolvedStats = initialStats
        private set

    override var currentHp: Double = initialStats.maxHealth
    override var maxHp: Double = initialStats.maxHealth

    /** Transient RAPID_FIRE attack-speed multiplier (R4-03); `1f` means no burst active. */
    @Volatile
    var rapidFireMultiplier: Float = 1f

    private var attackCooldown: Float = 0f

    /** Reads the live `stats.range` so in-round RANGE upgrades propagate without a cached field. */
    val attackRange: Float get() = stats.range

    /** Computed per shot from the live `stats.attackSpeed` × [rapidFireMultiplier]. */
    val attackInterval: Float get() = (1.0 / (stats.attackSpeed * rapidFireMultiplier)).toFloat()

    /** Redirects every subsequent derived stat read at the new instance (RO-08). */
    fun updateStats(newStats: ResolvedStats) {
        stats = newStats
    }

    /** Applies one tick of health regeneration, clamped at [maxHp]. */
    fun regenHp(deltaTime: Float) {
        currentHp = min(currentHp + stats.healthRegen * deltaTime, maxHp)
    }

    /** Decrements the attack cooldown and returns `true` on the frame the ziggurat may fire. */
    fun tickAttackReady(deltaTime: Float): Boolean {
        attackCooldown -= deltaTime
        return attackCooldown <= 0f
    }

    /** Resets the cooldown to one full [attackInterval] after a shot fires at ≥1 target. */
    fun onFired() {
        attackCooldown = attackInterval
    }

    /** Clamps a negative cooldown overshoot back to 0 when ready-but-no-targets (fire ASAP next tick). */
    fun holdReady() {
        attackCooldown = 0f
    }
}
