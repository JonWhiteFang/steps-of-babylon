package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import com.whitefang.stepsofbabylon.domain.battle.entity.Damageable
import com.whitefang.stepsofbabylon.domain.battle.entity.ZigguratState
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity

class ZigguratEntity(
    private val screenWidth: Float,
    private val screenHeight: Float,
    initialStats: ResolvedStats,
    private val findNearestEnemies: (Int) -> List<EnemyEntity>,
    layerColors: List<Int> = DEFAULT_COLORS,
    private val onFireProjectile: (startX: Float, startY: Float, targetX: Float, targetY: Float) -> Unit,
) : Entity() {
    /**
     * V1X-09 Phase 2 (ADR-0012): the live combat stats, HP, RAPID_FIRE multiplier, attack
     * cooldown, and the regen / attack-readiness logic now live in the pure-domain
     * [ZigguratState]. Every public property below delegates to it so `GameEngine` /
     * `BattleViewModel` (which read/write `currentHp` / `maxHp` / `rapidFireMultiplier` and
     * call `updateStats`) are untouched. This class keeps the layer geometry, the
     * nearest-enemy targeting + fire callback, and the Canvas `render()`.
     */
    private val state = ZigguratState(initialStats)

    /**
     * The ziggurat's damage/HP surface, exposed as the [Damageable] port (NOT the concrete
     * [ZigguratState]) so a combat resolver can apply damage while [ZigguratState]'s loop-thread-only
     * mutators (regenHp/tickAttackReady/onFired/holdReady) stay encapsulated (#306, ADR-0012 Phase 5).
     */
    val zigguratState: Damageable get() = state

    /** Live combat stats (read-only externally; mutated via [updateStats]). Delegates to [state]. */
    val stats: ResolvedStats get() = state.stats

    var currentHp: Double
        get() = state.currentHp
        set(value) {
            state.currentHp = value
        }

    var maxHp: Double
        get() = state.maxHp
        set(value) {
            state.maxHp = value
        }

    /** Reads the live `stats.range`; delegates to [state]. */
    val attackRange: Float get() = state.attackRange

    /** Transient RAPID_FIRE attack-speed multiplier (R4-03); delegates to [state]. */
    var rapidFireMultiplier: Float
        get() = state.rapidFireMultiplier
        set(value) {
            state.rapidFireMultiplier = value
        }

    private val layerCount = 5
    private val totalHeight: Float = screenHeight * 0.25f
    private val baseWidth: Float = screenWidth * 0.35f
    private val layerHeight: Float = totalHeight / layerCount

    private val layerPaints = layerColors.map { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it } }.toTypedArray()
    private val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt() }
    private val rangePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.FILL
        }
    private val rangeStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.STROKE
            strokeWidth =
                1.5f
        }

    init {
        width = baseWidth
        height = totalHeight
        x = screenWidth / 2f
        y = screenHeight * 0.45f + totalHeight
    }

    val originX: Float get() = x
    val originY: Float get() = y - totalHeight
    val centerY: Float get() = y - totalHeight / 2f

    /** Redirects every subsequent stat read at [newStats]. Called by `GameEngine.applyStats` (RO-08). */
    fun updateStats(newStats: ResolvedStats) = state.updateStats(newStats)

    override fun update(deltaTime: Float) {
        state.regenHp(deltaTime)
        if (state.tickAttackReady(deltaTime)) {
            val targets = findNearestEnemies(stats.multishotTargets)
            if (targets.isNotEmpty()) {
                state.onFired()
                for (target in targets) onFireProjectile(originX, originY, target.x, target.y)
            } else {
                state.holdReady()
            }
        }
    }

    override fun render(canvas: Canvas) {
        // Attack range circle
        canvas.drawCircle(originX, originY, attackRange, rangePaint)
        canvas.drawCircle(originX, originY, attackRange, rangeStrokePaint)
        // Ziggurat layers (aura now handled by EffectEngine)
        for (i in 0 until layerCount) {
            val wf = 1f - (i.toFloat() / layerCount) * 0.6f
            val lw = baseWidth * wf
            val ly = y - (i + 1) * layerHeight
            canvas.drawRect(x - lw / 2f, ly, x + lw / 2f, ly + layerHeight, layerPaints[i])
        }
        canvas.drawCircle(originX, originY, 6f, originPaint)
    }

    companion object {
        val DEFAULT_COLORS =
            listOf(0xFF8B7355.toInt(), 0xFF9C8565.toInt(), 0xFFC2B280.toInt(), 0xFFCDBFA0.toInt(), 0xFFD4A843.toInt())
    }
}
