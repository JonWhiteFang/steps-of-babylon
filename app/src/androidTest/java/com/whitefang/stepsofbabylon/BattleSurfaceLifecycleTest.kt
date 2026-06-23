package com.whitefang.stepsofbabylon

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whitefang.stepsofbabylon.presentation.battle.GameSurfaceView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1X-08 first real instrumented suite. Re-runs the four R3-01 backgrounding-state-loss
 * invariants (GitHub issue #2) against the **real** Android framework on a connected
 * emulator, hardening the existing
 * [com.whitefang.stepsofbabylon.presentation.battle.GameSurfaceViewTest] which exercises the
 * same seams under Robolectric's shadow framework.
 *
 * Why duplicate the Robolectric coverage: [GameSurfaceView] extends [android.view.SurfaceView],
 * and the R3-01 fix hinges on `SurfaceHolder.Callback` lifecycle timing. Robolectric shadows
 * SurfaceView with a stub that never produces real surface callbacks, so the Robolectric suite
 * can only drive the testable seams ([GameSurfaceView.initEngineIfNeeded], `pendingSpeed`,
 * `pendingPaused`) directly. This instrumented run constructs the view on the real main thread
 * with a real [Context] and verifies the same seams behave identically against the genuine
 * framework — the gap STATE.md flagged ("R3-01's existing guard is a Robolectric workaround
 * that doesn't exercise true Android lifecycle").
 *
 * No Hilt: [GameSurfaceView] takes only a [Context], so this suite skips `HiltAndroidRule`.
 * The [HiltTestRunner] still installs `HiltTestApplication`, which is harmless here.
 */
@RunWith(AndroidJUnit4::class)
class BattleSurfaceLifecycleTest {
    /** [GameSurfaceView] is a [android.view.View]; it must be constructed on the main thread. */
    private fun newView(): GameSurfaceView {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var view: GameSurfaceView
        instrumentation.runOnMainSync { view = GameSurfaceView(context.applicationContext as Context) }
        return view
    }

    @Test
    fun surfaceRecreationPreservesEngineProgressMidRound() {
        val view = newView()
        view.engine.init(800f, 600f)
        view.engine.update(0.5f)
        val progressBefore = view.engine.elapsedTimeSeconds
        assertTrue("Sanity: a tick must advance elapsedTimeSeconds past zero", progressBefore > 0f)
        assertTrue("Sanity: hasWaveProgress must be true after a tick", view.engine.hasWaveProgress())

        // Simulate the background-and-resume surfaceCreated path. Post-fix this short-circuits
        // via engine.hasWaveProgress(); pre-fix it unconditionally re-init'd and wiped progress.
        view.initEngineIfNeeded()

        assertEquals(
            "elapsedTimeSeconds must survive a background-and-resume cycle (R3-01 / issue #2)",
            progressBefore,
            view.engine.elapsedTimeSeconds,
            0.001f,
        )
    }

    @Test
    fun setSpeedMultiplierPersistsPendingSpeedForNextThread() {
        val view = newView()
        view.setSpeedMultiplier(4f)
        assertEquals(
            "setSpeedMultiplier must persist pendingSpeed so a recreated game thread inherits it",
            4f,
            view.pendingSpeed,
            0.001f,
        )
    }

    @Test
    fun setPausedPersistsPendingPausedForNextThread() {
        val view = newView()
        view.setPaused(true)
        assertTrue(
            "setPaused must persist pendingPaused so a recreated game thread inherits it",
            view.pendingPaused,
        )
    }

    @Test
    fun initEngineIfNeededDoesRunEngineInitWhenNoProgressYet() {
        val view = newView()
        assertFalse("Sanity: a fresh engine has no wave progress", view.engine.hasWaveProgress())

        view.initEngineIfNeeded()
        view.engine.update(0.1f)

        assertTrue(
            "A fresh engine must still init + become tickable (no-regression guard)",
            view.engine.hasWaveProgress(),
        )
    }
}
