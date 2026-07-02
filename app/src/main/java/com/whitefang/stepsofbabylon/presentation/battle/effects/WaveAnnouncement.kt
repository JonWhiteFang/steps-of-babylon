package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import android.graphics.Paint

class WaveAnnouncement(
    private val wave: Int,
    private val isBossWave: Boolean,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val reducedMotion: Boolean = false,
    private val bossLabel: String = "⚠ BOSS INCOMING",
    private val waveLabel: String = "Wave $wave",
) : Effect {
    private var age = 0f
    private val holdDuration = 1f
    private val fadeDuration = 0.5f
    private val totalDuration = holdDuration + fadeDuration
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 64f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    private val bossTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF44336.toInt()
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

    override val isFinished: Boolean get() = age >= totalDuration

    override fun update(dt: Float) {
        age += dt
    }

    override fun render(canvas: Canvas) {
        val alpha =
            if (age <
                holdDuration
            ) {
                255
            } else {
                ((1f - (age - holdDuration) / fadeDuration) * 255).toInt().coerceIn(0, 255)
            }
        val yOffset = if (reducedMotion || age > 0.15f) 0f else (1f - age / 0.15f) * -40f // Slide in from top

        textPaint.alpha = alpha
        val baseY = screenHeight * 0.3f + yOffset
        if (isBossWave) {
            bossTextPaint.alpha = alpha
            canvas.drawText(bossLabel, screenWidth / 2f, baseY - 50f, bossTextPaint)
        }
        canvas.drawText(waveLabel, screenWidth / 2f, baseY, textPaint)
    }
}

class WaveCooldownText(
    private val screenWidth: Float,
    private val nextWaveComposition: String? = null,
    private val nextWaveLabeler: ((Int) -> String)? = null,
    private val getTimeRemaining: () -> Float,
) : Effect {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
    private val compositionPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAB3E5FC.toInt()
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }

    override val isFinished: Boolean get() = getTimeRemaining() <= 0f

    override fun update(dt: Float) {}

    override fun render(canvas: Canvas) {
        val t = getTimeRemaining()
        if (t > 0f) {
            val label = nextWaveLabeler?.invoke(t.toInt()) ?: "Next Wave: ${t.toInt()}s"
            canvas.drawText(label, screenWidth / 2f, 60f, paint)
            // V1X-15b: ENEMY_INTEL L1+ next-wave composition line, drawn just below the timer.
            nextWaveComposition?.let { canvas.drawText(it, screenWidth / 2f, 90f, compositionPaint) }
        }
    }
}
