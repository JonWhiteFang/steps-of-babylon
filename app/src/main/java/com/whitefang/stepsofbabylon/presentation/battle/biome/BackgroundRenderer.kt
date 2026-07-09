package com.whitefang.stepsofbabylon.presentation.battle.biome

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.random.Random

class BackgroundRenderer(
    private val width: Float,
    private val height: Float,
    private val theme: BiomeTheme,
) {
    private data class Particle(
        var x: Float,
        var y: Float,
        val alpha: Float,
        val size: Float,
    )

    private val skyPaint =
        Paint().apply {
            shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    height * 0.70f,
                    theme.skyColorTop,
                    theme.skyColorBottom,
                    Shader.TileMode.CLAMP,
                )
        }
    private val groundPaint = Paint().apply { color = theme.groundColor }

    // #424 (#391 C4): the ambient emitter reads the named ParticleConfig vocabulary from the biome theme.
    private val particleConfig = theme.particles
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = particleConfig.color }

    private val particles =
        List(particleConfig.count) {
            Particle(
                Random.nextFloat() * width,
                Random.nextFloat() * height,
                0.4f + Random.nextFloat() * 0.6f,
                2f + Random.nextFloat() * 3f,
            )
        }

    fun update(deltaTime: Float) {
        for (p in particles) {
            p.x += particleConfig.driftX * deltaTime
            p.y += particleConfig.driftY * deltaTime
            if (p.x > width) {
                p.x -= width
            } else if (p.x < 0) {
                p.x += width
            }
            if (p.y > height) {
                p.y -= height
            } else if (p.y < 0) {
                p.y += height
            }
        }
    }

    fun render(canvas: Canvas) {
        val groundY = height * 0.70f
        canvas.drawRect(0f, 0f, width, groundY, skyPaint)
        canvas.drawRect(0f, groundY, width, height, groundPaint)
        for (p in particles) {
            particlePaint.alpha = (p.alpha * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.size, particlePaint)
        }
    }
}
