package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.VisibleForTesting
import com.whitefang.stepsofbabylon.domain.battle.entity.OrbState
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity
import kotlin.math.hypot

/**
 * Defensive orbiting projectile that pulses radially through the enemy melee zone (#54).
 *
 * V1X-09 Phase 2 (ADR-0012): the orbit-position + radial-oscillation math now lives in the
 * pure-domain [OrbState]; this class delegates position to it and keeps the
 * enemy-proximity / per-enemy [HIT_COOLDOWN] hit logic (which needs [EnemyEntity]
 * references + the `onHitEnemy` callback) plus the Canvas `render()`. Constructor signature
 * unchanged. See [OrbState] for the radius-oscillation rationale.
 */
class OrbEntity(
    zigX: Float,
    zigY: Float,
    angle: Float,
    angularSpeed: Float = 2f,
    private val damage: Double,
    private val getEnemies: () -> List<EnemyEntity>,
    private val onHitEnemy: (EnemyEntity, Double) -> Unit,
    initialRadialPhase: Float = 0f,
) : Entity(width = 10f, height = 10f) {

    private val state = OrbState(zigX, zigY, angle, angularSpeed, initialRadialPhase)
    private val hitCooldowns = mutableMapOf<EnemyEntity, Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00BCD4.toInt() }

    /** Current orbit radius; delegates to [OrbState]. Exposed `internal` for tests. */
    @VisibleForTesting
    internal val currentOrbitRadius: Float
        get() = state.currentOrbitRadius

    companion object {
        private const val HIT_COOLDOWN = 0.5f
        private const val HIT_RANGE = 25f

        @VisibleForTesting internal const val ORBIT_RADIUS_MIN = OrbState.ORBIT_RADIUS_MIN
        @VisibleForTesting internal const val ORBIT_RADIUS_MAX = OrbState.ORBIT_RADIUS_MAX
        @VisibleForTesting internal const val ORBIT_PERIOD_SEC = OrbState.ORBIT_PERIOD_SEC
    }

    override fun update(deltaTime: Float) {
        state.update(deltaTime)
        x = state.x
        y = state.y

        // Decrement cooldowns, remove dead
        val iter = hitCooldowns.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (!entry.key.isAlive) { iter.remove(); continue }
            entry.setValue(entry.value - deltaTime)
            if (entry.value <= 0f) iter.remove()
        }

        // Check proximity to enemies
        for (enemy in getEnemies()) {
            if (!enemy.isAlive || hitCooldowns.containsKey(enemy)) continue
            if (hypot(x - enemy.x, y - enemy.y) < HIT_RANGE) {
                onHitEnemy(enemy, damage)
                hitCooldowns[enemy] = HIT_COOLDOWN
            }
        }
    }

    override fun render(canvas: Canvas) {
        canvas.drawCircle(x, y, width / 2f, paint)
    }
}
