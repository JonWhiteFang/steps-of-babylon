package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity
import kotlin.math.hypot

class EnemyProjectileEntity(
    startX: Float,
    startY: Float,
    private val targetX: Float,
    private val targetY: Float,
    private val speed: Float = 300f,
    val damage: Double,
    val shooter: EnemyEntity? = null,
) : Entity(x = startX, y = startY, width = 6f, height = 6f) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }

    override fun update(deltaTime: Float) {
        val dx = targetX - x
        val dy = targetY - y
        val dist = hypot(dx, dy)
        if (dist < speed * deltaTime) { isAlive = false; return }
        val ratio = speed * deltaTime / dist
        x += dx * ratio
        y += dy * ratio
    }

    override fun render(canvas: Canvas) {
        canvas.drawCircle(x, y, width / 2f, paint)
    }
}
