package com.whitefang.stepsofbabylon.presentation.battle

import android.view.SurfaceHolder
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine

class GameLoopThread(
    private val surfaceHolder: SurfaceHolder,
    private val engine: GameEngine,
) : Thread("GameLoop") {

    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var speedMultiplier: Float = 1f

    @Volatile
    var isPaused: Boolean = false

    var fps: Int = 0
        private set

    companion object {
        private const val TICK_NS = 16_666_667L // ~60 UPS (1e9 / 60)
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
                    try { surfaceHolder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }
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
                try { sleep(sleepMs) } catch (_: InterruptedException) {}
            }
        }
    }
}
