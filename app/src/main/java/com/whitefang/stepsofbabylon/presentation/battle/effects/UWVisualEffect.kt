package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import android.graphics.Paint
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class UWVisualEffect(
    private val type: UltimateWeaponType,
    private val pool: ParticlePool,
    private val cx: Float,
    private val cy: Float,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val duration: Float,
    private val reducedMotion: Boolean = false,
) : Effect {
    private var age = 0f
    private var spawnTimer = 0f
    // Fallback paint for reduced motion
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override val isFinished: Boolean get() = age >= duration

    override fun update(dt: Float) {
        age += dt
        if (reducedMotion) return
        spawnTimer += dt
        val interval = 0.03f
        while (spawnTimer >= interval && age < duration) {
            spawnTimer -= interval
            spawnParticles()
        }
    }

    private fun spawnParticles() {
        when (type) {
            UltimateWeaponType.DEATH_WAVE -> {
                val progress = age / duration
                val radius = progress * screenWidth * 0.8f
                repeat(8) {
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val p = pool.acquire()
                    p.x = cx + cos(angle) * radius; p.y = cy + sin(angle) * radius
                    p.vx = cos(angle) * 80f; p.vy = sin(angle) * 80f
                    p.color = 0xFFE53935.toInt(); p.size = 7f; p.lifetime = 0.6f
                }
            }
            UltimateWeaponType.CHAIN_LIGHTNING -> {
                repeat(4) {
                    val p = pool.acquire()
                    p.x = cx + Random.nextFloat() * 100f - 50f; p.y = cy + Random.nextFloat() * 100f - 50f
                    p.vx = Random.nextFloat() * 200f - 100f; p.vy = Random.nextFloat() * 200f - 100f
                    p.color = 0xFFE0E0FF.toInt(); p.size = 3f; p.lifetime = 0.15f
                }
            }
            UltimateWeaponType.BLACK_HOLE -> {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val dist = 80f + Random.nextFloat() * 40f
                val p = pool.acquire()
                p.x = cx + cos(angle) * dist; p.y = cy + sin(angle) * dist
                p.vx = -cos(angle) * 60f; p.vy = -sin(angle) * 60f
                p.color = 0xFF6A0DAD.toInt(); p.size = 3f + Random.nextFloat() * 2f; p.lifetime = 0.5f
            }
            UltimateWeaponType.CHRONO_FIELD -> {
                val p = pool.acquire()
                p.x = Random.nextFloat() * screenWidth; p.y = Random.nextFloat() * screenHeight
                p.vx = Random.nextFloat() * 10f - 5f; p.vy = Random.nextFloat() * 10f - 5f
                p.color = 0xFF2196F3.toInt(); p.size = 2f + Random.nextFloat() * 2f; p.lifetime = 1f
            }
            UltimateWeaponType.POISON_SWAMP -> {
                val p = pool.acquire()
                p.x = cx + Random.nextFloat() * 160f - 80f; p.y = cy + Random.nextFloat() * 20f
                p.vx = Random.nextFloat() * 10f - 5f; p.vy = -20f - Random.nextFloat() * 30f
                p.color = 0xFF4CAF50.toInt(); p.size = 3f + Random.nextFloat() * 3f; p.lifetime = 0.6f
            }
            UltimateWeaponType.GOLDEN_ZIGGURAT -> {
                val p = pool.acquire()
                p.x = cx + Random.nextFloat() * 120f - 60f; p.y = cy - 60f - Random.nextFloat() * 40f
                p.vx = Random.nextFloat() * 20f - 10f; p.vy = 30f + Random.nextFloat() * 20f
                p.color = 0xFFFFD700.toInt(); p.size = 2f + Random.nextFloat() * 3f; p.lifetime = 0.5f
            }
        }
    }

    override fun render(canvas: Canvas) {
        if (!reducedMotion) return
        // Reduced motion fallback: simple geometric shapes
        val progress = age / duration
        val alpha = ((1f - progress) * 150).toInt().coerceIn(0, 255)
        fallbackPaint.alpha = alpha
        when (type) {
            UltimateWeaponType.DEATH_WAVE -> {
                fallbackPaint.color = 0xFFE53935.toInt(); fallbackPaint.style = Paint.Style.STROKE; fallbackPaint.strokeWidth = 6f
                canvas.drawCircle(cx, cy, progress * screenWidth * 0.8f, fallbackPaint)
            }
            UltimateWeaponType.BLACK_HOLE -> {
                fallbackPaint.color = 0xFF6A0DAD.toInt(); fallbackPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, 60f, fallbackPaint)
            }
            UltimateWeaponType.CHRONO_FIELD -> {
                fallbackPaint.color = 0x332196F3; fallbackPaint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, screenWidth, screenHeight, fallbackPaint)
            }
            UltimateWeaponType.POISON_SWAMP -> {
                fallbackPaint.color = 0xFF4CAF50.toInt(); fallbackPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, 100f, fallbackPaint)
            }
            else -> {} // Chain Lightning, Golden Ziggurat — no simple fallback needed
        }
    }
}
