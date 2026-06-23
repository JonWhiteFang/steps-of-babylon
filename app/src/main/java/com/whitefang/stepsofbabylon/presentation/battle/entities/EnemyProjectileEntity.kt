package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import com.whitefang.stepsofbabylon.domain.battle.entity.ProjectileState
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity

/**
 * Ranged enemy projectile homing toward the ziggurat. V1X-09 Phase 2 (ADR-0012): the
 * homing motion is identical to [ProjectileEntity], so it reuses the same pure-domain
 * [ProjectileState]; this class keeps the Canvas `render()` plus the `damage` / `shooter`
 * fields used by `CollisionSystem` (ziggurat damage + thorn reflection). Constructor unchanged.
 */
class EnemyProjectileEntity(
    startX: Float,
    startY: Float,
    targetX: Float,
    targetY: Float,
    speed: Float = 300f,
    val damage: Double,
    val shooter: EnemyEntity? = null,
) : Entity(x = startX, y = startY, width = 6f, height = 6f) {
    private val state = ProjectileState(startX, startY, targetX, targetY, speed)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }

    override fun update(deltaTime: Float) {
        state.update(deltaTime)
        x = state.x
        y = state.y
        isAlive = state.isAlive
    }

    override fun render(canvas: Canvas) {
        canvas.drawCircle(x, y, width / 2f, paint)
    }
}
