package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Issue #118 (audit finding #1 + #15): cross-thread structural mutation of the engine's
 * shared, unsynchronized `entities` list.
 *
 * The dedicated GameLoopThread iterates and structurally mutates `entities` every tick inside
 * [GameEngine.update] (and [GameEngine.render]); meanwhile the UI/main thread can call
 * [GameEngine.updateZigguratStats] (in-round ORBS purchase) or [GameEngine.init] (playAgain),
 * both of which structurally mutate the SAME list (`entities.removeAll { it is OrbEntity }` +
 * `spawnOrbs()` for orbCount changes; `entities.clear()/add()` for re-init). A Java ArrayList
 * structurally modified by one thread while another iterates it throws
 * ConcurrentModificationException (or silently corrupts iteration).
 *
 * These are stress tests: a background "loop" thread runs [GameEngine.update] in a tight loop
 * while the test thread structurally mutates `entities` via the public main-thread channels.
 * Any throwable on either thread is captured; the test asserts none surfaced over a high
 * iteration count. Before the fix the CME reproduces within milliseconds; after confining
 * structural mutation behind a lock, the loop completes cleanly.
 */
class GameEngineConcurrencyTest {

    private fun engineWithOrbs(orbCount: Int): GameEngine {
        val eng = GameEngine()
        eng.init(
            width = 1080f,
            height = 1920f,
            resolvedStats = ResolvedStats(orbCount = orbCount),
            playerTier = 1,
        )
        return eng
    }

    @Test
    fun `concurrent in-round ORBS purchase during update loop does not throw`() {
        val eng = engineWithOrbs(orbCount = 2)
        val caught = AtomicReference<Throwable?>(null)

        // Loop thread: drive update() in a tight loop, mirroring GameLoopThread.run().
        val keepLooping = AtomicBoolean(true)
        val loopThread = Thread {
            try {
                while (keepLooping.get()) {
                    eng.update(1f / 60f)
                }
            } catch (t: Throwable) {
                caught.compareAndSet(null, t)
            }
        }
        loopThread.start()

        // Main/UI thread: simulate repeated in-round ORBS upgrade purchases, each of which
        // changes orbCount and therefore triggers the entities.removeAll + spawnOrbs branch
        // in applyStats — the exact structural mutation that races the loop thread.
        try {
            for (i in 0 until 200_000) {
                if (caught.get() != null) break
                val orbCount = when (i % 3) { 0 -> 0; 1 -> 3; else -> 6 }
                eng.updateZigguratStats(ResolvedStats(orbCount = orbCount))
            }
        } catch (t: Throwable) {
            caught.compareAndSet(null, t)
        } finally {
            keepLooping.set(false)
            loopThread.join(5_000)
        }

        assertNull(
            caught.get(),
            "Structural mutation of GameEngine.entities from the main thread (ORBS purchase) " +
                "must not race the loop thread's update() iteration. Got: ${caught.get()}",
        )
    }

    @Test
    fun `concurrent engine re-init during update loop does not throw`() {
        val eng = engineWithOrbs(orbCount = 3)
        val caught = AtomicReference<Throwable?>(null)

        val keepLooping = AtomicBoolean(true)
        val loopThread = Thread {
            try {
                while (keepLooping.get()) {
                    eng.update(1f / 60f)
                }
            } catch (t: Throwable) {
                caught.compareAndSet(null, t)
            }
        }
        loopThread.start()

        // Main thread: repeatedly re-init the engine (the playAgain path), which clears and
        // rebuilds entities while the loop thread is still iterating it.
        try {
            for (i in 0 until 50_000) {
                if (caught.get() != null) break
                eng.init(
                    width = 1080f,
                    height = 1920f,
                    resolvedStats = ResolvedStats(orbCount = 3),
                    playerTier = 1,
                )
            }
        } catch (t: Throwable) {
            caught.compareAndSet(null, t)
        } finally {
            keepLooping.set(false)
            loopThread.join(5_000)
        }

        assertNull(
            caught.get(),
            "Re-init (playAgain) on the main thread must not race the loop thread's update() " +
                "iteration. Got: ${caught.get()}",
        )
    }
}
