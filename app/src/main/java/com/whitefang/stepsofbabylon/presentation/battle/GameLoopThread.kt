package com.whitefang.stepsofbabylon.presentation.battle

import android.util.Log
import android.view.SurfaceHolder
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine

class GameLoopThread(
    private val surfaceHolder: SurfaceHolder,
    private val engine: GameEngine,
    private val crashBreadcrumbStore: CrashBreadcrumbStore,
) : Thread("GameLoop") {
    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var speedMultiplier: Float = 1f

    @Volatile
    var isPaused: Boolean = false

    /**
     * #190 REL-2: invoked (on the loop thread) when the guarded loop catches a throwable from
     * engine.update()/render(). The surface view forwards this to the ViewModel, which surfaces
     * a battle-error UI state. Null until wired.
     */
    @Volatile
    var onLoopError: ((Throwable) -> Unit)? = null

    var fps: Int = 0
        private set

    companion object {
        private const val TICK_NS = 16_666_667L // ~60 UPS (1e9 / 60)
        private const val TAG = "GameLoopThread"
    }

    override fun run() {
        var previousTime = System.nanoTime()
        var accumulator = 0L
        var frameCount = 0
        var fpsTimer = System.nanoTime()

        while (isRunning) {
            val currentTime = System.nanoTime()
            val elapsed = currentTime - previousTime
            previousTime = currentTime

            // #190 REL-2: guard the per-tick update + render. An uncaught exception here used to
            // kill the dedicated loop thread → silent process death. Now: record a breadcrumb,
            // stop the loop, and surface a battle-error state via onLoopError.
            try {
                if (!isPaused) {
                    accumulator += (elapsed * speedMultiplier).toLong()
                    // #126: bound the catch-up backlog so a long frame (GC pause, starved loop,
                    // or a heavy update itself) — amplified up to 4× by speedMultiplier — can't
                    // demand an unbounded burst of update() calls before the next render. Without
                    // this clamp each slow tick begets more catch-up ticks (spiral of death) and
                    // the screen visibly freezes; the clamp drops the excess backlog instead.
                    accumulator = SimulationMath.clampAccumulator(accumulator, TICK_NS)
                    while (accumulator >= TICK_NS) {
                        engine.update(TICK_NS / 1_000_000_000f)
                        accumulator -= TICK_NS
                    }
                }

                var canvas = null as android.graphics.Canvas?
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        synchronized(surfaceHolder) {
                            engine.render(canvas)
                        }
                    }
                } finally {
                    canvas?.let {
                        try {
                            surfaceHolder.unlockCanvasAndPost(it)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (t: Throwable) {
                runCatching { crashBreadcrumbStore.record(name, t, System.currentTimeMillis()) }
                runCatching { Log.e(TAG, "Game loop crashed; stopping loop", t) }
                isRunning = false
                runCatching { onLoopError?.invoke(t) }
                break
            }

            // FPS counter
            frameCount++
            if (currentTime - fpsTimer >= 1_000_000_000L) {
                fps = frameCount
                frameCount = 0
                fpsTimer = currentTime
            }

            // Yield to avoid burning CPU if we're ahead
            val frameTime = System.nanoTime() - currentTime
            val sleepMs = (TICK_NS - frameTime) / 1_000_000
            if (sleepMs > 0) {
                try {
                    sleep(sleepMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }
}
