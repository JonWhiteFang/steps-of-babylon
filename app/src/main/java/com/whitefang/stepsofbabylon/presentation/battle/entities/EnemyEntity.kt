package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity
import kotlin.math.hypot

class EnemyEntity(
    val enemyType: EnemyType,
    var currentHp: Double,
    val maxHp: Double,
    val speed: Float,
    val damage: Double,
    private val targetX: Float,
    private val targetY: Float,
    private val onDeath: (EnemyEntity) -> Unit,
    /**
     * Invoked once per [attackInterval] when this enemy is at melee range of its target.
     * The first argument is `this` — the attacker reference — so consumers can react to
     * the attacker (e.g. apply THORN_DAMAGE reflection back at the source). Pre-R3-02 the
     * callback shape was `(Double) -> Unit` and dropped the attacker reference, which is
     * why THORN_DAMAGE silently never reflected damage despite being plumbed through
     * `ResolvedStats.thornPercent` and consumed by `GameEngine.applyThorn` — every call
     * site simply passed `attacker = null`. (R3-02 / GitHub issue #4)
     */
    private val onMeleeHit: ((EnemyEntity, Double) -> Unit)? = null,
    private val onFireProjectile: ((Float, Float, Float, Float, Double) -> Unit)? = null,
    private val attackInterval: Float = 1f,
    armorHits: Int = 0,
    enemyTint: Int = 0,
) : Entity() {

    var armorHits: Int = armorHits; private set
    private var attackCooldown = 0f
    private val meleeRange = 40f
    private var initialDist = 0f

    private val bodyPaint: Paint
    private val trianglePath = Path()

    init {
        val size = when (enemyType) {
            EnemyType.BOSS -> 40f; EnemyType.TANK -> 28f; EnemyType.FAST -> 16f; else -> 20f
        }
        width = size; height = size
        val baseColor = BASE_COLORS[enemyType] ?: 0xFFE53935.toInt()
        bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (enemyTint != 0) blendColor(baseColor, enemyTint, 0.3f) else baseColor }
    }

    fun initDistance() { initialDist = hypot(targetX - x, targetY - y) }

    override fun update(deltaTime: Float) {
        val dx = targetX - x; val dy = targetY - y; val dist = hypot(dx, dy)
        val stopDist = if (enemyType == EnemyType.RANGED) initialDist * 0.4f else meleeRange
        if (dist > stopDist) {
            val ratio = speed * deltaTime / dist; x += dx * ratio; y += dy * ratio
        } else {
            attackCooldown -= deltaTime
            if (attackCooldown <= 0f) {
                attackCooldown = attackInterval
                if (enemyType == EnemyType.RANGED) onFireProjectile?.invoke(x, y, targetX, targetY, damage)
                else onMeleeHit?.invoke(this, damage)
            }
        }
    }

    fun takeDamage(amount: Double) {
        if (armorHits > 0) { armorHits--; return }
        currentHp -= amount
        if (currentHp <= 0.0) { isAlive = false; onDeath(this) }
    }

    fun applyKnockback(forceX: Float, forceY: Float) { x += forceX; y += forceY }

    override fun render(canvas: Canvas) {
        val r = width / 2f
        when (enemyType) {
            EnemyType.BASIC, EnemyType.BOSS, EnemyType.SCATTER -> canvas.drawCircle(x, y, r, bodyPaint)
            EnemyType.FAST -> {
                trianglePath.reset()
                trianglePath.moveTo(x, y - r); trianglePath.lineTo(x - r, y + r); trianglePath.lineTo(x + r, y + r); trianglePath.close()
                canvas.drawPath(trianglePath, bodyPaint)
            }
            EnemyType.TANK -> canvas.drawRect(x - r, y - r, x + r, y + r, bodyPaint)
            EnemyType.RANGED -> {
                trianglePath.reset()
                trianglePath.moveTo(x, y - r); trianglePath.lineTo(x + r, y); trianglePath.lineTo(x, y + r); trianglePath.lineTo(x - r, y); trianglePath.close()
                canvas.drawPath(trianglePath, bodyPaint)
            }
        }
        if (armorHits > 0) canvas.drawCircle(x, y, r + 3f, ARMOR_PAINT)
        // Mini HP bar
        val barW = width * 1.2f; val barH = 4f; val barY = y - r - 8f
        canvas.drawRect(x - barW / 2, barY, x + barW / 2, barY + barH, HP_BG)
        val ratio = (currentHp / maxHp).coerceIn(0.0, 1.0).toFloat()
        HP_FILL.color = when { ratio > 0.6f -> 0xFF4CAF50.toInt(); ratio > 0.3f -> 0xFFFFEB3B.toInt(); else -> 0xFFF44336.toInt() }
        canvas.drawRect(x - barW / 2, barY, x - barW / 2 + barW * ratio, barY + barH, HP_FILL)
    }

    companion object {
        private val BASE_COLORS = mapOf(
            EnemyType.BASIC to 0xFFE53935.toInt(), EnemyType.FAST to 0xFFFF9800.toInt(),
            EnemyType.TANK to 0xFF8B0000.toInt(), EnemyType.RANGED to 0xFF9C27B0.toInt(),
            EnemyType.BOSS to 0xFF4A0000.toInt(), EnemyType.SCATTER to 0xFF4CAF50.toInt(),
        )
        private val ARMOR_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5500BCD4; style = Paint.Style.STROKE; strokeWidth = 2f }
        private val HP_BG = Paint().apply { color = 0xFF2A1A10.toInt() }
        private val HP_FILL = Paint()

        fun blendColor(base: Int, tint: Int, factor: Float): Int {
            val r = ((Color.red(base) * (1 - factor) + Color.red(tint) * factor)).toInt()
            val g = ((Color.green(base) * (1 - factor) + Color.green(tint) * factor)).toInt()
            val b = ((Color.blue(base) * (1 - factor) + Color.blue(tint) * factor)).toInt()
            return Color.argb(255, r, g, b)
        }
    }
}
