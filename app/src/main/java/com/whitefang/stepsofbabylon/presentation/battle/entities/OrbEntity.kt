package com.whitefang.stepsofbabylon.presentation.battle.entities

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.VisibleForTesting
import com.whitefang.stepsofbabylon.presentation.battle.engine.Entity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Defensive orbiting projectile that pulses radially through the enemy melee zone.
 *
 * Closes #54. Pre-fix orbs orbited at a fixed radius `stats.range * 0.4f` ≈ 120 px from
 * the ziggurat origin while enemies stopped at `EnemyEntity.meleeRange` = 40 px from the
 * same origin. The minimum orb-to-enemy distance was therefore 80 px > [HIT_RANGE] = 25
 * px, so orbs literally couldn't reach stopped enemies — the upgrade was effectively dead
 * content from Plan 10b through to v14. Surfaced during v14 closed-track soak (#54).
 *
 * Post-fix the orbit radius oscillates between [ORBIT_RADIUS_MIN] (25 px, just inside
 * meleeRange so HIT_RANGE reaches a stopped enemy at the same angle) and
 * [ORBIT_RADIUS_MAX] (70 px, cleanly outside HIT_RANGE so the kill zone "breathes" rather
 * than constantly damages). One full cycle takes [ORBIT_PERIOD_SEC] = 2.5 s. The 6 orbs
 * the player can buy at maxLevel share the same radial phase by default so the swarm
 * pulses inward together — a visible defensive rhythm rather than a chaotic spiral.
 *
 * Per-enemy [HIT_COOLDOWN] = 0.5 s still gates double-hits within a single inward sweep,
 * matching pre-fix behaviour. Damage value is unchanged from the call site
 * (`GameEngine.spawnOrbs` still passes `stats.damage * 0.5 * conditions.orbDamageMultiplier`).
 */
class OrbEntity(
    private val zigX: Float,
    private val zigY: Float,
    private var angle: Float,
    private val angularSpeed: Float = 2f,
    private val damage: Double,
    private val getEnemies: () -> List<EnemyEntity>,
    private val onHitEnemy: (EnemyEntity, Double) -> Unit,
    /**
     * Initial value of the radial-oscillation phase angle (radians). Default `0` puts the
     * orb at mid-radius (~ 47.5 px) on the first frame; `-PI/2` puts it at the inner
     * extreme (R = MIN); `+PI/2` at the outer extreme (R = MAX). All 6 orbs in a swarm
     * share the same default so they pulse together.
     */
    initialRadialPhase: Float = 0f,
) : Entity(width = 10f, height = 10f) {

    private val hitCooldowns = mutableMapOf<EnemyEntity, Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00BCD4.toInt() }

    /**
     * Tracks the radial-oscillation phase angle in radians. Advances by
     * [RADIAL_ANGULAR_SPEED] × `deltaTime` on every [update] call, producing one full
     * cycle per [ORBIT_PERIOD_SEC]. Exposed `internal` so tests can verify oscillation
     * without driving the full game loop, but writes are gated through [update] (no public
     * setter) so production code can't accidentally jump the phase.
     */
    @VisibleForTesting
    internal var radialPhase: Float = initialRadialPhase
        private set

    /**
     * Current orbit radius in pixels, derived from [radialPhase] via the sine wave
     * `MID + AMPLITUDE × sin(phase)`. Exposed `internal` for tests; production code reads
     * the same value indirectly via the `x = zigX + cos(angle) × R` / `y = zigY + sin(angle) × R`
     * computation in [update].
     */
    @VisibleForTesting
    internal val currentOrbitRadius: Float
        get() = MID_ORBIT_RADIUS + AMPLITUDE_ORBIT_RADIUS * sin(radialPhase)

    companion object {
        private const val HIT_COOLDOWN = 0.5f
        private const val HIT_RANGE = 25f

        /**
         * Inner-sweep orbit radius in pixels. At 25 px the orb sits 15 px from a stopped
         * enemy at meleeRange = 40 px (same angle), well within HIT_RANGE = 25 px → hit.
         */
        @VisibleForTesting internal const val ORBIT_RADIUS_MIN = 25f

        /**
         * Outer-sweep orbit radius in pixels. At 70 px the orb sits 30 px from a stopped
         * enemy at meleeRange = 40 px (same angle), outside HIT_RANGE = 25 px → no hit.
         */
        @VisibleForTesting internal const val ORBIT_RADIUS_MAX = 70f

        /**
         * Full radial-oscillation cycle in seconds. 2.5 s gives a visibly slow sweep that
         * reads as a deliberate "breathing" pulse rather than a fast wobble.
         */
        @VisibleForTesting internal const val ORBIT_PERIOD_SEC = 2.5f

        private const val MID_ORBIT_RADIUS: Float = (ORBIT_RADIUS_MIN + ORBIT_RADIUS_MAX) / 2f
        private const val AMPLITUDE_ORBIT_RADIUS: Float = (ORBIT_RADIUS_MAX - ORBIT_RADIUS_MIN) / 2f

        /** Radial-oscillation angular speed in rad/sec. Computed once at class load. */
        private val RADIAL_ANGULAR_SPEED: Float = (2.0 * PI / ORBIT_PERIOD_SEC).toFloat()
    }

    override fun update(deltaTime: Float) {
        angle += angularSpeed * deltaTime
        radialPhase += RADIAL_ANGULAR_SPEED * deltaTime

        val r = currentOrbitRadius
        x = zigX + cos(angle) * r
        y = zigY + sin(angle) * r

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
