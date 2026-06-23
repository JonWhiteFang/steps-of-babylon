package com.whitefang.stepsofbabylon.domain.battle.entity

import kotlin.math.hypot

/**
 * Pure movement + attack-cooldown state extracted from
 * `presentation/battle/entities/EnemyEntity` for V1X-09 Phase 2 (ADR-0012).
 *
 * No Android imports. Owns the (x, y) position, the attack cooldown, and the captured
 * initial distance used by the RANGED stop-distance rule. The presentation [EnemyEntity]
 * delegates motion/attack here and keeps HP/armor/death/knockback wiring, the attack
 * callbacks (which need the entity reference), and the Canvas `render()`.
 *
 * Movement is a homing step toward the target; once within [stopDistance] the enemy stops
 * and [update] returns `true` each time the attack cooldown elapses (so the entity can fire
 * the appropriate melee/ranged callback). Math is identical to the pre-extraction entity.
 */
class EnemyState(
    private val targetX: Float,
    private val targetY: Float,
    private val speed: Float,
    private val isRanged: Boolean,
    private val attackInterval: Float,
) {
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    private var attackCooldown = 0f
    private var initialDist = 0f

    private val stopDistance: Float
        get() = if (isRanged) initialDist * RANGED_STOP_FACTOR else MELEE_RANGE

    /** Sets the spawn position and captures the initial distance for the RANGED stop rule. */
    fun spawn(
        spawnX: Float,
        spawnY: Float,
    ) {
        x = spawnX
        y = spawnY
        initialDist = hypot(targetX - x, targetY - y)
    }

    /**
     * Advances one tick. Moves toward the target while beyond [stopDistance]; otherwise ticks
     * the attack cooldown and returns `true` on the frame an attack should fire.
     */
    fun update(deltaTime: Float): Boolean {
        val dx = targetX - x
        val dy = targetY - y
        val dist = hypot(dx, dy)
        if (dist > stopDistance) {
            val ratio = speed * deltaTime / dist
            x += dx * ratio
            y += dy * ratio
            return false
        }
        attackCooldown -= deltaTime
        if (attackCooldown <= 0f) {
            attackCooldown = attackInterval
            return true
        }
        return false
    }

    /** Shifts the position by a knockback impulse (HP/armor stay on the entity). */
    fun applyKnockback(
        forceX: Float,
        forceY: Float,
    ) {
        x += forceX
        y += forceY
    }

    companion object {
        const val MELEE_RANGE = 40f
        const val RANGED_STOP_FACTOR = 0.4f
    }
}
