package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity
import kotlin.math.min

class ZigguratEntity(
    private val screenWidth: Float,
    private val screenHeight: Float,
    initialStats: ResolvedStats,
    private val findNearestEnemies: (Int) -> List<EnemyEntity>,
    layerColors: List<Int> = DEFAULT_COLORS,
    private val onFireProjectile: (startX: Float, startY: Float, targetX: Float, targetY: Float) -> Unit,
) : Entity() {

    /**
     * Live combat stats. Mutable so the engine can propagate in-round upgrade / UW changes
     * mid-round (RO-08, R4-01). Pre-RO-08 this was a `val` captured at construction, which
     * silently dropped Overdrive ASSAULT's 2× attack speed, FORTRESS's 2× health regen, and
     * any in-round ATTACK_SPEED purchase. R4-01 deletes Overdrive entirely; the live-stats
     * propagation invariant remains for in-round purchases and the GOLDEN_ZIGGURAT UW.
     * Updated via [updateStats], called by `GameEngine.applyStats`.
     */
    var stats: ResolvedStats = initialStats
        private set

    var currentHp: Double = stats.maxHealth
    var maxHp: Double = stats.maxHealth

    /**
     * Computed property — reads the current `stats.range` so in-round RANGE upgrades and
     * future range-affecting effects propagate without the engine having to poke a cached
     * field. Pre-RO-08 this was `val attackRange = stats.range` (frozen at construction).
     */
    val attackRange: Float get() = stats.range

    private var attackCooldown: Float = 0f

    /**
     * Computed each shot from the current `stats.attackSpeed`. Pre-RO-08 this was a captured
     * `val` so attack-speed updates (in-round ATTACK_SPEED purchases, GOLDEN_ZIGGURAT damage
     * buff) were silently dropped — the cooldown reset in `update` always used the
     * construction-time value. Recomputing per-tick is cheap (one float divide) and matches
     * how every other stat (damage, defense, knockback, lifesteal, …) is read live from
     * `stats`. R4-01 dropped the Overdrive ASSAULT 2× attack speed reference; the invariant
     * is unchanged.
     */
    private val attackInterval: Float get() = (1.0 / stats.attackSpeed).toFloat()

    private val layerCount = 5
    private val totalHeight: Float = screenHeight * 0.25f
    private val baseWidth: Float = screenWidth * 0.35f
    private val layerHeight: Float = totalHeight / layerCount

    private val layerPaints = layerColors.map { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it } }.toTypedArray()
    private val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt() }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF; style = Paint.Style.FILL }
    private val rangeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1.5f }

    init {
        width = baseWidth; height = totalHeight
        x = screenWidth / 2f; y = screenHeight * 0.45f + totalHeight
    }

    val originX: Float get() = x
    val originY: Float get() = y - totalHeight
    val centerY: Float get() = y - totalHeight / 2f

    /**
     * Replaces the live stats reference. Called by `GameEngine.applyStats` whenever an
     * in-round upgrade or Ultimate Weapon mutates the engine's resolved stats (RO-08). The
     * engine separately reconciles `maxHp` / `currentHp` / orb count to keep those side
     * effects centralised; this entry point exists purely to redirect every subsequent stat
     * read on the entity (attack speed, range, health regen, multishot targets, knockback,
     * lifesteal, …) at the new instance.
     */
    fun updateStats(newStats: ResolvedStats) { stats = newStats }

    override fun update(deltaTime: Float) {
        currentHp = min(currentHp + stats.healthRegen * deltaTime, maxHp)
        attackCooldown -= deltaTime
        if (attackCooldown <= 0f) {
            val targets = findNearestEnemies(stats.multishotTargets)
            if (targets.isNotEmpty()) {
                attackCooldown = attackInterval
                for (target in targets) onFireProjectile(originX, originY, target.x, target.y)
            } else attackCooldown = 0f
        }
    }

    override fun render(canvas: Canvas) {
        // Attack range circle
        canvas.drawCircle(originX, originY, attackRange, rangePaint)
        canvas.drawCircle(originX, originY, attackRange, rangeStrokePaint)
        // Ziggurat layers (aura now handled by EffectEngine)
        for (i in 0 until layerCount) {
            val wf = 1f - (i.toFloat() / layerCount) * 0.6f
            val lw = baseWidth * wf; val ly = y - (i + 1) * layerHeight
            canvas.drawRect(x - lw / 2f, ly, x + lw / 2f, ly + layerHeight, layerPaints[i])
        }
        canvas.drawCircle(originX, originY, 6f, originPaint)
    }

    companion object {
        val DEFAULT_COLORS = listOf(0xFF8B7355.toInt(), 0xFF9C8565.toInt(), 0xFFC2B280.toInt(), 0xFFCDBFA0.toInt(), 0xFFD4A843.toInt())
    }
}
