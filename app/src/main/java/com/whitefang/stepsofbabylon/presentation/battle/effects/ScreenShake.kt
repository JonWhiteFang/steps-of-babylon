package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.random.Random

class ScreenShake {
    private var intensity = 0f
    private var duration = 0f
    private var elapsed = 0f
    var dx = 0f
        private set
    var dy = 0f
        private set

    fun trigger(
        intensity: Float,
        duration: Float,
    ) {
        if (intensity > this.intensity) {
            this.intensity = intensity
            this.duration = duration
            elapsed = 0f
        }
    }

    fun update(dt: Float) {
        if (duration <= 0f) {
            dx = 0f
            dy = 0f
            return
        }
        elapsed += dt
        if (elapsed >= duration) {
            reset()
            return
        }
        val decay = 1f - elapsed / duration
        val amp = intensity * decay
        dx = sin(elapsed * 60f) * amp * (if (Random.nextBoolean()) 1f else -1f)
        dy = sin(elapsed * 47f) * amp * (if (Random.nextBoolean()) 1f else -1f)
    }

    fun apply(canvas: Canvas) {
        canvas.save()
        canvas.translate(dx, dy)
    }

    fun restore(canvas: Canvas) {
        canvas.restore()
    }

    fun reset() {
        intensity = 0f
        duration = 0f
        elapsed = 0f
        dx = 0f
        dy = 0f
    }

    val isActive: Boolean get() = duration > 0f && elapsed < duration
}
