package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Issue #191 (CONC-1): EffectEngine's `effects` / `pendingEffects` lists are mutated cross-thread
 * — addEffect is called from coroutine threads (StepReward/BossKilled) while the loop thread
 * drains in update() and iterates in render(). Neither list was synchronized. This stress test
 * races addEffect against update()+render(); before the effectsLock fix it throws a CME within
 * milliseconds, after the fix it completes cleanly.
 */
class EffectEngineConcurrencyTest {

    /** A trivial Effect that never finishes (so the list keeps growing → maximises iteration overlap). */
    private class StubEffect : Effect {
        override val isFinished: Boolean = false
        override fun update(dt: Float) {}
        override fun render(canvas: Canvas) {}
    }

    @Test
    fun `addEffect racing update and render does not throw`() {
        val fx = EffectEngine(reducedMotion = false)
        val canvas = mock<Canvas>()
        val caught = AtomicReference<Throwable?>(null)
        val keepLooping = AtomicBoolean(true)

        val loopThread = Thread {
            try {
                while (keepLooping.get()) {
                    fx.update(1f / 60f)
                    fx.render(canvas)
                }
            } catch (t: Throwable) {
                caught.compareAndSet(null, t)
            }
        }
        loopThread.start()

        try {
            for (i in 0 until 200_000) {
                if (caught.get() != null) break
                fx.addEffect(StubEffect())
            }
        } catch (t: Throwable) {
            caught.compareAndSet(null, t)
        } finally {
            keepLooping.set(false)
            loopThread.join(5_000)
        }

        assertNull(
            caught.get(),
            "addEffect from another thread must not race update()/render() iteration. Got: ${caught.get()}",
        )
    }
}
