package com.whitefang.stepsofbabylon.presentation.battle

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.VisibleForTesting
import com.whitefang.stepsofbabylon.data.AndroidStrings
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.presentation.audio.SoundManager
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine

class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    val engine = GameEngine()
    private var gameThread: GameLoopThread? = null
    private var currentStats: ResolvedStats = ResolvedStats()
    private var currentTier: Int = 1
    private var currentWsLevels: Map<UpgradeType, Int> = emptyMap()
    /**
     * Wave number the next [GameEngine.init] call should open on (RO-11 #B.1, WAVE_SKIP).
     * Updated by [BattleViewModel] via [configure]; default `1` preserves pre-RO-11 behaviour
     * if [configure] is never called (e.g. surfaceCreated fires before the VM init coroutine
     * has read [LabRepository]). The fresh-engine `init` re-reads this on every surface
     * lifecycle event so a `playAgain` mid-session sees the latest WAVE_SKIP level.
     */
    private var currentStartWave: Int = 1
    private var surfaceReady = false
    private val isReducedMotion = ReducedMotionCheck.isReducedMotionEnabled(context)
    private val soundManager: SoundManager

    /**
     * Pending speed multiplier captured by [setSpeedMultiplier] before / outside an active
     * [GameLoopThread]. Read by [surfaceCreated] when constructing a new thread so that a
     * background-and-resume cycle preserves the speed the player had selected (R3-01 / issue
     * #2). Without this, the new thread defaults to `1f` and the on-screen "4x" highlight
     * would contradict the actual loop speed.
     */
    @Volatile internal var pendingSpeed: Float = 1f

    /**
     * Pending pause flag, mirror of [pendingSpeed] for the [GameLoopThread.isPaused] field.
     * `BattleScreen` calls [setPaused] from a `LaunchedEffect(state.isPaused)` which only fires
     * on value change — so a fresh thread after surface recreation never receives a re-sync,
     * leading to UI/loop desync (R3-01 / issue #2).
     */
    @Volatile internal var pendingPaused: Boolean = false

    // #190 REL-1/REL-2: built directly from Context (like AndroidStrings) — same SharedPreferences
    // file as the Hilt singleton, keyed by name, so both write the same breadcrumb. The loop thread
    // writes the breadcrumb directly (no dependency on onLoopError being set yet).
    private val crashBreadcrumbStore = CrashBreadcrumbStore(context)

    /**
     * #190 REL-2: forwarded to the current [GameLoopThread] AND re-seeded onto each new thread in
     * [surfaceCreated] (threads are recreated every surface lifecycle). Unlike pendingSpeed/Paused
     * — which are re-set on every toggle and so self-heal — this is set ONCE by the VM, so the
     * re-seed is load-bearing: without it the battle-error callback is lost after a background→resume.
     */
    @Volatile
    var onLoopError: ((Throwable) -> Unit)? = null
        set(value) {
            field = value
            gameThread?.onLoopError = value
        }

    init {
        holder.addCallback(this)
        soundManager = SoundManager(context)
        val prefs = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)
        soundManager.setMuted(prefs.getBoolean("muted", false))
        soundManager.setVolume(prefs.getFloat("volume", 1f))
        engine.soundManager = soundManager
        engine.strings = AndroidStrings(context)
    }

    fun configure(stats: ResolvedStats, tier: Int, wsLevels: Map<UpgradeType, Int>, startWave: Int = 1) {
        currentStats = stats; currentTier = tier; currentWsLevels = wsLevels
        currentStartWave = startWave
        if (surfaceReady) engine.init(width.toFloat(), height.toFloat(), stats, tier, wsLevels, isReducedMotion, startWave)
    }

    /**
     * Initialise the engine for a new round when called by [surfaceCreated] / [surfaceChanged].
     *
     * Gated on [GameEngine.hasWaveProgress] so a background-and-resume cycle does not wipe an
     * in-flight round (R3-01 / issue #2). Pre-fix every Android lifecycle event (surfaceCreated
     * + surfaceChanged) unconditionally called [GameEngine.init], resetting `cash`,
     * `totalEnemiesKilled`, `elapsedTimeSeconds`, [WaveSpawner.currentWave], and every UW
     * cooldown / effect flag. The guard reuses the [GameEngine.hasWaveProgress] helper added
     * by RO-03 (B.3 PR 2) for [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel.onCleared] —
     * exactly the right signal at the wrong call site, now wired in.
     *
     * Fresh-battle path (engine never ticked) and replay-after-end (round-end clears progress
     * via the engine's `roundOver` reset) both still hit the [GameEngine.init] branch and
     * receive a correctly-initialised engine. Extracted as `@VisibleForTesting internal` so
     * the regression test in [GameSurfaceViewTest] can drive it without spinning up a real
     * game-loop thread.
     */
    @VisibleForTesting
    internal fun initEngineIfNeeded() {
        if (!engine.hasWaveProgress()) {
            engine.init(width.toFloat(), height.toFloat(), currentStats, currentTier, currentWsLevels, isReducedMotion, currentStartWave)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        // Guarded init: a background-and-resume cycle must not wipe an in-flight round.
        // initEngineIfNeeded short-circuits via engine.hasWaveProgress() in that case.
        initEngineIfNeeded()
        // Seed the new thread from [pendingSpeed] / [pendingPaused] so a recreated game loop
        // inherits the player's UI selections — setSpeedMultiplier / setPaused calls made
        // while gameThread was null (between surfaceDestroyed and this surfaceCreated) would
        // otherwise be lost (R3-01 / issue #2 sub-bug 2b).
        val thread = GameLoopThread(holder, engine, crashBreadcrumbStore).apply {
            speedMultiplier = pendingSpeed
            isPaused = pendingPaused
            onLoopError = this@GameSurfaceView.onLoopError
            isRunning = true
        }
        thread.start(); gameThread = thread
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Same guarded-init contract as surfaceCreated: a re-layout (e.g. orientation change)
        // must not destroy in-flight round state.
        initEngineIfNeeded()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        val thread = gameThread ?: return
        thread.isRunning = false
        try { thread.join(1000) } catch (_: InterruptedException) {}
        gameThread = null
        soundManager.release()
    }

    fun setSpeedMultiplier(speed: Float) {
        // Capture into [pendingSpeed] so the next thread spun up by surfaceCreated picks up
        // the latest value even if the current gameThread is null (R3-01 / issue #2).
        pendingSpeed = speed
        gameThread?.speedMultiplier = speed
    }
    fun setPaused(paused: Boolean) {
        // Same shape as setSpeedMultiplier — [pendingPaused] survives surface lifecycle gaps
        // so the new thread starts in the player's intended pause state.
        pendingPaused = paused
        gameThread?.isPaused = paused
    }
    fun updateSoundMuted(muted: Boolean) { soundManager.setMuted(muted) }
}
