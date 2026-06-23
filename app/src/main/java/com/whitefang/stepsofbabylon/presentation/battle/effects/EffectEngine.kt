package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import android.graphics.Paint

interface Effect {
    val isFinished: Boolean

    fun update(dt: Float)

    fun render(canvas: Canvas)
}

class EffectEngine(
    val reducedMotion: Boolean = false,
) {
    val pool = ParticlePool(200)
    private val effects = mutableListOf<Effect>()
    private val pendingEffects = mutableListOf<Effect>()
    val screenShake = ScreenShake()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // #191 CONC-1: effects/pendingEffects are touched from coroutine threads (addEffect) and the
    // loop thread (update/render). Guard every structural touch with a private monitor. Mirrors the
    // GameEngine #118 entitiesLock pattern. pool/screenShake stay loop-confined (NOT guarded).
    private val effectsLock = Any()

    fun addEffect(effect: Effect) {
        synchronized(effectsLock) { pendingEffects.add(effect) }
    }

    fun update(dt: Float) {
        // Drain pending + snapshot under the lock. The per-effect update() runs OUTSIDE the lock
        // (no monitor held across effect logic). removeAll is deferred to AFTER the per-effect
        // update — preserving today's update→removeAll ordering exactly (no 1-frame lifetime shift).
        val snapshot: List<Effect> =
            synchronized(effectsLock) {
                effects.addAll(pendingEffects)
                pendingEffects.clear()
                effects.toList()
            }
        pool.updateAll(dt)
        snapshot.forEach { it.update(dt) }
        synchronized(effectsLock) { effects.removeAll { it.isFinished } }
        if (!reducedMotion) screenShake.update(dt)
    }

    fun render(canvas: Canvas) {
        pool.renderAll(canvas, particlePaint)
        val snapshot = synchronized(effectsLock) { effects.toList() }
        snapshot.forEach { it.render(canvas) }
    }

    fun clear() {
        synchronized(effectsLock) {
            effects.clear()
            pendingEffects.clear()
        }
        pool.clear()
        screenShake.reset()
    }
}
