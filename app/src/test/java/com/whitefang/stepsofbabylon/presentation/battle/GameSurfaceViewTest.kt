package com.whitefang.stepsofbabylon.presentation.battle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric regression suite for [GameSurfaceView] surface-lifecycle handling.
 *
 * Closes the closed-test-blocker bug filed as GitHub issue #2 (Plan R3-01): backgrounding the
 * app mid-round destroys all in-progress round state, and the new game-loop thread spun up on
 * resume defaults to `speed = 1f / paused = false` regardless of what the UI shows.
 *
 * The pre-fix code path:
 *   1. [GameSurfaceView.surfaceCreated] / [GameSurfaceView.surfaceChanged] unconditionally call
 *      [GameEngine.init], which resets `cash`, `totalEnemiesKilled`, `elapsedTimeSeconds`,
 *      [WaveSpawner.currentWave], all UW cooldown / effect state, and the entity list. Each
 *      Android lifecycle event therefore wipes the round.
 *   2. [GameSurfaceView.setSpeedMultiplier] / [GameSurfaceView.setPaused] write only to the
 *      live [GameLoopThread]. After [GameSurfaceView.surfaceDestroyed] sets `gameThread = null`
 *      these setters become silent no-ops, so the next thread (created by `surfaceCreated`)
 *      starts with the [GameLoopThread] defaults regardless of what the user had selected.
 *
 * The fix routes both responsibilities through testable seams ([initEngineIfNeeded] and the
 * [pendingSpeed] / [pendingPaused] fields) so the regression can be guarded without spinning
 * up an actual game-loop thread or surface holder. The four tests below cover:
 *   - the primary state-preservation invariant (test 1 — the headline regression),
 *   - speed-state survival across thread recreation (test 2),
 *   - pause-state survival across thread recreation (test 3),
 *   - the inverse / no-regression path: a fresh battle (engine never ticked) MUST still init
 *     correctly so we don't break the round-start path while plugging the resume gap (test 4).
 *
 * Robolectric is required because [GameSurfaceView] extends [android.view.SurfaceView]; the
 * @Config matches the project's other Robolectric tests ([DailyStepDaoTest], [BillingManagerImplTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GameSurfaceViewTest {
    private fun newView(): GameSurfaceView {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return GameSurfaceView(ctx)
    }

    @Test
    fun `R3-01 surface recreation preserves engine progress mid-round`() {
        // Bootstrap: simulate the first surfaceCreated by directly calling engine.init with
        // explicit dimensions (Robolectric SurfaceView starts at 0x0; the test bypasses the
        // view's getWidth/getHeight to give the engine a sane viewport for tick math).
        val view = newView()
        view.engine.init(800f, 600f)

        // Tick the engine to give it observable progress. After this call,
        // engine.hasWaveProgress() must return true (elapsedTimeSeconds > 0f).
        view.engine.update(0.5f)
        val progressBefore = view.engine.elapsedTimeSeconds
        assertTrue(
            "Sanity: engine.update() should have advanced elapsedTimeSeconds past zero",
            progressBefore > 0f,
        )
        assertTrue(
            "Sanity: hasWaveProgress() must be true after a tick",
            view.engine.hasWaveProgress(),
        )

        // Simulate the second surfaceCreated (background + resume) by going through the
        // helper that surfaceCreated uses to (re)initialise the engine. PRE-FIX: this
        // unconditionally calls engine.init, wiping elapsedTimeSeconds back to 0f and
        // failing the assertion below. POST-FIX: the helper short-circuits via
        // engine.hasWaveProgress() and the in-progress round survives.
        view.initEngineIfNeeded()

        assertEquals(
            "GameEngine.elapsedTimeSeconds must survive a background-and-resume cycle " +
                "(R3-01 / issue #2: pre-fix this is wiped to 0f because surfaceCreated " +
                "unconditionally calls engine.init)",
            progressBefore,
            view.engine.elapsedTimeSeconds,
            0.001f,
        )
    }

    @Test
    fun `R3-01 setSpeedMultiplier persists pendingSpeed for next thread`() {
        // Pre-fix: setSpeedMultiplier(4f) writes only to gameThread, which is null before any
        // surfaceCreated has fired (and after surfaceDestroyed). The setter is therefore a
        // silent no-op; the next thread spun up by surfaceCreated will start at 1f regardless
        // of what the UI thinks the speed is. Post-fix: the setter ALSO writes [pendingSpeed],
        // and surfaceCreated reads it when configuring the new thread.
        val view = newView()
        view.setSpeedMultiplier(4f)
        assertEquals(
            "setSpeedMultiplier(4f) must update pendingSpeed so a freshly-created game thread " +
                "inherits the correct speed (R3-01 / issue #2 sub-bug 2b)",
            4f,
            view.pendingSpeed,
            0.001f,
        )
    }

    @Test
    fun `R3-01 setPaused persists pendingPaused for next thread`() {
        // Symmetric to the speed test: pre-fix setPaused(true) writes only to a (potentially
        // null) gameThread. Post-fix, [pendingPaused] also captures the value so the next
        // surfaceCreated can seed the new thread with isPaused = true.
        val view = newView()
        view.setPaused(true)
        assertTrue(
            "setPaused(true) must update pendingPaused so a freshly-created game thread " +
                "inherits the correct pause state (R3-01 / issue #2 sub-bug 2b)",
            view.pendingPaused,
        )
    }

    @Test
    fun `R3-01 initEngineIfNeeded does run engine init when no progress yet`() {
        // Inverse / no-regression check: a fresh battle (engine never ticked, no progress)
        // MUST still initialise the engine when surfaceCreated fires. The post-fix guard
        // gates engine.init on !hasWaveProgress(), which is exactly the case here, so init
        // is expected to run. After init the engine should be tickable and accumulate
        // progress; this confirms the helper isn't accidentally short-circuiting fresh inits.
        val view = newView()
        assertFalse(
            "Sanity: a fresh GameSurfaceView's engine has no wave progress",
            view.engine.hasWaveProgress(),
        )

        view.initEngineIfNeeded()
        view.engine.update(0.1f)

        assertTrue(
            "After initEngineIfNeeded() on a fresh engine, the engine must be tickable and " +
                "begin accumulating progress (no-regression guard against the fix " +
                "accidentally suppressing fresh-battle initialisation)",
            view.engine.hasWaveProgress(),
        )
    }

    // --- #245: battle SFX must survive a background→resume (surface destroy→recreate) cycle ---

    @Test
    fun `245 a recreated surface re-points the engine at a fresh SoundManager`() {
        // A SurfaceView surface is destroyed and recreated across an app background→foreground cycle.
        // Pre-fix: surfaceDestroyed called soundManager.release() but surfaceCreated never recreated
        // it, so engine.soundManager kept pointing at the released SoundPool — every play(...) became
        // a no-op and the player lost all combat SFX for the rest of the session.
        val view = newView()
        val original = view.engine.soundManager
        assertTrue("Sanity: engine starts wired to a SoundManager", original != null)

        // surfaceDestroyed releases the SoundManager.
        view.releaseSoundManager()
        // surfaceCreated must recreate it and re-point the engine.
        view.ensureSoundManager()

        val recreated = view.engine.soundManager
        assertNotSame(
            "engine.soundManager must be a FRESH instance after a surface destroy→recreate cycle, " +
                "not the released one (#245)",
            original,
            recreated,
        )
    }

    @Test
    fun `245 the engine reference is dropped while the surface is destroyed`() {
        // Between surfaceDestroyed (release) and the next surfaceCreated (recreate), engine.soundManager
        // must be null so a main-thread SFX call (e.g. an upgrade purchase) is a clean no-op via the
        // `soundManager?.play(...)` guard — NOT a play() on a released SoundPool.
        val view = newView()
        view.releaseSoundManager()
        assertEquals(
            "engine.soundManager must be null in the destroyed window so play() is a clean no-op, " +
                "not a use-after-release on a freed SoundPool (#245)",
            null,
            view.engine.soundManager,
        )
    }

    @Test
    fun `245 the recreated SoundManager re-reads the persisted mute pref`() {
        // The recreated manager must be rebuilt from the persisted sound_prefs (not left at defaults),
        // so the player's mute choice survives a background→resume — an observable-state check that
        // the rebuild actually re-seeds, beyond mere instance identity.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx
            .getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("muted", true)
            .commit()

        val view = GameSurfaceView(ctx)
        view.releaseSoundManager()
        view.ensureSoundManager()

        assertTrue(
            "the recreated SoundManager must re-read the persisted muted=true pref (#245)",
            view.engine.soundManager!!.isMuted(),
        )
    }

    @Test
    fun `245 ensureSoundManager without a prior release keeps the existing instance`() {
        // No-regression guard: a surfaceCreated that is NOT preceded by a release (the very first
        // surface creation) must not needlessly churn the SoundManager / reload every SoundPool clip.
        val view = newView()
        val original = view.engine.soundManager

        view.ensureSoundManager()

        assertSame(
            "ensureSoundManager() must be a no-op when the SoundManager was never released " +
                "(don't reload clips on the first surfaceCreated)",
            original,
            view.engine.soundManager,
        )
    }
}
