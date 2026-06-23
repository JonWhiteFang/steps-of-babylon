package com.whitefang.stepsofbabylon.presentation.battle.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

class HealthBarRenderer {
    private val bgPaint = Paint().apply { color = 0xFF2A1A10.toInt() }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF8E7.toInt() // Ivory
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

    fun render(
        canvas: Canvas,
        currentHp: Double,
        maxHp: Double,
        screenWidth: Float,
    ) {
        val barMargin = screenWidth * 0.05f
        val barTop = 40f
        val barHeight = 32f
        val barWidth = screenWidth - barMargin * 2

        // Background
        canvas.drawRoundRect(RectF(barMargin, barTop, barMargin + barWidth, barTop + barHeight), 8f, 8f, bgPaint)

        // Fill
        val ratio = (currentHp / maxHp).coerceIn(0.0, 1.0).toFloat()
        val fillColor =
            when {
                ratio > 0.6f -> 0xFF4CAF50.toInt()

                // green
                ratio > 0.3f -> 0xFFFFEB3B.toInt()

                // yellow
                else -> 0xFFF44336.toInt() // red
            }
        val fillPaint = Paint().apply { color = fillColor }
        canvas.drawRoundRect(
            RectF(barMargin, barTop, barMargin + barWidth * ratio, barTop + barHeight),
            8f,
            8f,
            fillPaint,
        )

        // Text
        val hpText = "${currentHp.toLong()} / ${maxHp.toLong()}"
        canvas.drawText(hpText, screenWidth / 2f, barTop + barHeight - 6f, textPaint)
    }
}
