package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import com.whitefang.stepsofbabylon.presentation.battle.biome.BackgroundRenderer
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import com.whitefang.stepsofbabylon.presentation.battle.ui.HealthBarRenderer

/**
 * Presentation-layer renderer for the battle SurfaceView (#231 decomposition). Owns ALL Canvas
 * `Paint` allocation and the per-frame draw sequence lifted verbatim from the old
 * `GameEngine.render()`. Pure draw logic — it mutates no shared engine state; the engine takes the
 * under-`entitiesLock` entity snapshot and passes it (plus the per-frame scalar reads) in.
 *
 * The three Paint fields are cached (allocated once) — A31 audit fix preserved exactly.
 */
class BattleRenderer(private val healthBarRenderer: HealthBarRenderer = HealthBarRenderer()) {

    // A31 (audit): cached CHRONO_FIELD overlay paint — was allocated per frame in render(). Colour
    // 0x222196F3 preserved exactly (semi-transparent blue). The literal intentionally keeps the
    // existing value; the alpha nuance the audit flagged is left unchanged (no observable colour
    // change in this PR).
    private val chronoOverlayPaint = android.graphics.Paint().apply {
        color = 0x222196F3; style = android.graphics.Paint.Style.FILL
    }

    private val hpPercentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8E7.toInt(); textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER
    }
    private val bossCountdownPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF44336.toInt(); textSize = 26f; textAlign = android.graphics.Paint.Align.RIGHT; isFakeBoldText = true
    }

    /**
     * Renders one frame. [renderSnapshot] is the engine's under-lock copy of the live entity list;
     * everything else is a per-frame scalar/nullable read the engine supplies. Behaviour is identical
     * to the pre-decomposition `GameEngine.render()` — the draw sequence + the ENEMY_INTEL overlays +
     * the chrono overlay + screen-shake apply/restore are unchanged.
     */
    fun render(
        canvas: Canvas,
        backgroundRenderer: BackgroundRenderer?,
        effectEngine: EffectEngine?,
        reducedMotion: Boolean,
        renderSnapshot: List<Entity>,
        chronoActive: Boolean,
        screenWidth: Float,
        screenHeight: Float,
        enemyIntelLevel: Int,
        ziggurat: ZigguratEntity?,
        bossCountdownLabel: String?,
    ) {
        backgroundRenderer?.render(canvas) ?: canvas.drawColor(0xFF6B3A2A.toInt())

        val fx = effectEngine
        // Screen shake
        if (fx != null && !reducedMotion) fx.screenShake.apply(canvas)

        renderSnapshot.forEach { it.render(canvas) }

        // Render effects (particles, floating text, UW visuals, auras, announcements)
        fx?.render(canvas)

        // Chrono field overlay
        if (chronoActive) {
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, chronoOverlayPaint)
        }

        if (fx != null && !reducedMotion) fx.screenShake.restore(canvas)

        // V1X-15b: ENEMY_INTEL L5+ per-enemy HP-% label above each enemy's HP bar. Drawn here
        // (not in EnemyEntity.render) so the level gate stays out of the entity constructor.
        if (enemyIntelLevel >= 5) {
            for (e in renderSnapshot) {
                if (e is EnemyEntity && e.isAlive) {
                    val pct = ((e.currentHp / e.maxHp).coerceIn(0.0, 1.0) * 100).toInt()
                    canvas.drawText("$pct%", e.x, e.y - e.height / 2f - 14f, hpPercentPaint)
                }
            }
        }

        ziggurat?.let { healthBarRenderer.render(canvas, it.currentHp, it.maxHp, screenWidth) }

        // V1X-15b: ENEMY_INTEL L10 boss-arrival countdown, right-aligned to clear the
        // centre-aligned cooldown banner.
        bossCountdownLabel?.let { canvas.drawText(it, screenWidth - 16f, 90f, bossCountdownPaint) }
    }
}
