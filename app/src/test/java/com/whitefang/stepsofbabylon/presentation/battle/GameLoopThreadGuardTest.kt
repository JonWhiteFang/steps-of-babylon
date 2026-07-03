package com.whitefang.stepsofbabylon.presentation.battle

import android.view.SurfaceHolder
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

class GameLoopThreadGuardTest {
    @Test
    fun `an exception in update stops the loop, records a breadcrumb, fires onLoopError once`() {
        val engine = mock<GameEngine>()
        whenever(engine.update(any())).thenThrow(RuntimeException("engine boom"))
        val holder = mock<SurfaceHolder>() // lockCanvas() returns null → canvas block skipped

        val recordCount = AtomicInteger(0)
        val store = mock<CrashBreadcrumbStore>()
        whenever(store.record(any(), any(), any())).thenAnswer {
            recordCount.incrementAndGet()
            Unit
        }

        val errorCount = AtomicInteger(0)
        val thread =
            GameLoopThread(holder, engine, store).apply {
                onLoopError = { errorCount.incrementAndGet() }
                isPaused = false
                isRunning = true
            }

        thread.start()
        thread.join(2_000) // join-then-assert: deterministic, no sleep race

        assertFalse(thread.isAlive, "loop thread must have stopped")
        assertFalse(thread.isRunning, "isRunning must be false after a loop crash")
        assertEquals(1, errorCount.get(), "onLoopError must fire exactly once")
        assertEquals(1, recordCount.get(), "breadcrumb must be recorded exactly once")
    }

    @Test
    fun `a render crash unlocks the canvas before propagating to the outer catch`() {
        // Spec §B1 load-bearing assertion: the inner lockCanvas/unlockCanvasAndPost try/finally
        // must stay strictly nested inside the outer try/catch, so a render() crash unlocks the
        // canvas (no frozen surface / ANR) before the throwable reaches the outer catch.
        val engine = mock<GameEngine>()
        // update() no-ops; render() throws. (update is called inside the same outer try.)
        whenever(engine.render(any())).thenThrow(RuntimeException("render boom"))
        val canvas = mock<android.graphics.Canvas>()
        val holder = mock<SurfaceHolder>()
        whenever(holder.lockCanvas()).thenReturn(canvas) // non-null → render() block is entered

        val store = mock<CrashBreadcrumbStore>()
        val errorCount = AtomicInteger(0)
        val thread =
            GameLoopThread(holder, engine, store).apply {
                onLoopError = { errorCount.incrementAndGet() }
                isPaused = false
                isRunning = true
            }

        thread.start()
        thread.join(2_000)

        assertFalse(thread.isAlive, "loop thread must have stopped after a render crash")
        // The inner finally must have unlocked the canvas before the outer catch fired.
        verify(holder).unlockCanvasAndPost(canvas)
        assertEquals(1, errorCount.get(), "onLoopError must fire exactly once on a render crash")
    }

    @Test
    fun `a DEBUG frame-stats overlay crash unlocks the canvas and is caught by the #190 guard`() {
        // #384 / SCOPE-4: the DEBUG overlay draw is new surface INSIDE the #190 guard + inner canvas
        // try/finally. Pin that a throwing overlay.draw() (not just engine.render()) still stops the loop,
        // unlocks the canvas, and fires onLoopError exactly once — same protection as a render crash.
        val engine = mock<GameEngine>() // update() + render() no-op
        val canvas = mock<android.graphics.Canvas>()
        val holder = mock<SurfaceHolder>()
        whenever(holder.lockCanvas()).thenReturn(canvas) // non-null → the render/overlay block is entered
        val store = mock<CrashBreadcrumbStore>()

        // Inject an overlay whose draw() throws on the first frame (BuildConfig.DEBUG is true in unit tests).
        val throwingOverlay =
            object : FrameStatsOverlay() {
                override fun draw(
                    canvas: android.graphics.Canvas,
                    snapshot: FrameStats.Snapshot?,
                ): Unit = throw IllegalStateException("overlay boom")
            }

        val errorCount = AtomicInteger(0)
        val thread =
            GameLoopThread(holder, engine, store, throwingOverlay).apply {
                onLoopError = { errorCount.incrementAndGet() }
                isPaused = false
                isRunning = true
            }

        thread.start()
        thread.join(2_000)

        assertFalse(thread.isAlive, "loop thread must have stopped after an overlay crash")
        verify(holder).unlockCanvasAndPost(canvas) // inner finally unlocked before the outer catch
        assertEquals(1, errorCount.get(), "onLoopError must fire exactly once on an overlay crash")
    }
}
