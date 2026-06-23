package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import android.graphics.Paint

class Particle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var alpha = 1f
    var size = 4f
    var color = 0xFFFFFFFF.toInt()
    var lifetime = 1f
    var age = 0f
    var active = false

    fun reset() {
        x = 0f
        y = 0f
        vx = 0f
        vy = 0f
        alpha = 1f
        size = 4f
        color = 0xFFFFFFFF.toInt()
        lifetime = 1f
        age =
            0f
        active = false
    }

    fun update(dt: Float): Boolean {
        age += dt
        if (age >= lifetime) {
            active = false
            return false
        }
        x += vx * dt
        y += vy * dt
        val progress = age / lifetime
        alpha = (1f - progress).coerceIn(0f, 1f)
        size *= (1f - dt * 2f).coerceAtLeast(0.1f)
        return true
    }

    fun render(
        canvas: Canvas,
        paint: Paint,
    ) {
        if (!active || alpha <= 0f) return
        paint.color = color
        paint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(x, y, size, paint)
    }
}

class ParticlePool(
    private val capacity: Int = 200,
) {
    private val particles = Array(capacity) { Particle() }
    private var nextIndex = 0

    fun acquire(): Particle {
        // Try to find an inactive particle first
        repeat(capacity) {
            val idx = (nextIndex + it) % capacity
            if (!particles[idx].active) {
                nextIndex = (idx + 1) % capacity
                particles[idx].reset()
                particles[idx].active = true
                return particles[idx]
            }
        }
        // Pool exhausted — recycle oldest (nextIndex)
        val p = particles[nextIndex]
        nextIndex = (nextIndex + 1) % capacity
        p.reset()
        p.active = true
        return p
    }

    fun updateAll(dt: Float) {
        for (p in particles) if (p.active) p.update(dt)
    }

    fun renderAll(
        canvas: Canvas,
        paint: Paint,
    ) {
        for (p in particles) if (p.active) p.render(canvas, paint)
    }

    fun activeCount(): Int = particles.count { it.active }

    fun clear() {
        for (p in particles) {
            p.active = false
        }
    }
}
