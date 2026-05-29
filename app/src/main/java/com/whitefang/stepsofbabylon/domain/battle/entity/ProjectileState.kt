package com.whitefang.stepsofbabylon.domain.battle.entity

import kotlin.math.hypot

/**
 * Pure projectile-motion state extracted from `presentation/battle/entities/ProjectileEntity`
 * as the first per-entity step of the V1X-09 Phase 2 simulation extraction (ADR-0012).
 *
 * No Android imports. Owns the projectile's position + alive flag and the homing-toward-target
 * step; the presentation [ProjectileEntity] delegates its `update()` here and keeps only the
 * Canvas `render()` plus the collision/bounce fields. Establishes the
 * `domain/battle/entity/<Name>State` pattern for the remaining entities.
 */
class ProjectileState(
    var x: Float,
    var y: Float,
    private val targetX: Float,
    private val targetY: Float,
    private val speed: Float,
) {
    /** False once the projectile reaches (or overshoots in one step) its target. */
    var isAlive: Boolean = true
        private set

    /**
     * Advances the projectile toward its fixed target by `speed × deltaTime`. If the
     * remaining distance is within one step's reach, the projectile is marked dead
     * (position is left at the pre-arrival point, matching the original entity).
     */
    fun update(deltaTime: Float) {
        val dx = targetX - x
        val dy = targetY - y
        val dist = hypot(dx, dy)
        if (dist < speed * deltaTime) {
            isAlive = false
            return
        }
        val ratio = speed * deltaTime / dist
        x += dx * ratio
        y += dy * ratio
    }
}
