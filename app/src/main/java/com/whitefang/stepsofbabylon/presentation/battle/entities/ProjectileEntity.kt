package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import com.whitefang.stepsofbabylon.domain.battle.entity.ProjectileState
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity

/**
 * Moves toward a target, self-destructs on arrival. V1X-09 Phase 2 (ADR-0012): the homing
 * motion now lives in the pure-domain [ProjectileState]; this class delegates `update()` to
 * it and retains only the Canvas `render()` and the collision/bounce fields
 * (`damage`, `bouncesRemaining`, `hitEnemies`) that `CollisionSystem` / `GameEngine` read.
 */
class ProjectileEntity(
    startX: Float,
    startY: Float,
    targetX: Float,
    targetY: Float,
    speed: Float,
    val damage: Double = 0.0,
    var bouncesRemaining: Int = 0,
    val hitEnemies: MutableSet<EnemyEntity> = mutableSetOf(),
) : Entity(x = startX, y = startY, width = 8f, height = 8f) {
    private val state = ProjectileState(startX, startY, targetX, targetY, speed)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4A843.toInt() }

    /**
     * #243: accumulates sim-time toward the next trail-particle emission (see `advanceTrail`).
     * Loop-thread-only — advanced inside `GameEngine.update()` under `entitiesLock`; this entity is
     * constructed fresh per shot and never pooled, so a `0f` start needs no reset.
     */
    var trailTimer: Float = 0f

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
