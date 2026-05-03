package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import android.graphics.Paint

class FloatingText(
    private var x: Float,
    private var y: Float,
    private val text: String,
    private val duration: Float = 0.8f,
    color: Int = DEFAULT_COLOR,
) : Effect {
    private var age = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = 28f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    override val isFinished: Boolean get() = age >= duration

    override fun update(dt: Float) { age += dt; y -= 50f * dt }

    override fun render(canvas: Canvas) {
        val alpha = ((1f - age / duration) * 255).toInt().coerceIn(0, 255)
        paint.alpha = alpha
        canvas.drawText(text, x, y, paint)
    }

    companion object {
        /** Yellow-gold, used by cash-drop floats. */
        const val DEFAULT_COLOR: Int = 0xFFD4A843.toInt()
        /** Green, used by battle-Step reward floats. */
        const val STEP_COLOR: Int = 0xFF4CAF50.toInt()
    }
}
