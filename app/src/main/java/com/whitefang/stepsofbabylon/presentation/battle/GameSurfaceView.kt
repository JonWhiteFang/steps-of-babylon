package com.whitefang.stepsofbabylon.presentation.battle

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.VisibleForTesting
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

    init {
        holder.addCallback(this)
        soundManager = SoundManager(context)
        val prefs = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)
        soundManager.setMuted(prefs.getBoolean("muted", false))
        soundManager.setVolume(prefs.getFloat("volume", 1f))
        engine.soundManager = soundManager
    }

    fun configure(stats: ResolvedStats, tier: Int, wsLevels: Map<UpgradeType, Int>, startWave: Int = 1) {
        currentStats = stats; currentTier = tier; currentWsLevels = wsLevels
        currentStartWave = startWave
        if (surfaceReady) engine.init(width.toFloat(), height.toFloat(), stats, tier, wsLevels, isReducedMotion, startWave)
    }

    /**
     * Initialise the engine for a new round when called by [surfaceCreated] / [surfaceChanged].
     *
     * Pre-fix (R3-01 / issue #2): unconditional [GameEngine.init] call — wipes wave / cash /
     * kills / elapsedTime when the user backgrounds + resumes mid-round. The post-fix variant
     * (next commit on this branch) gates this on [GameEngine.hasWaveProgress] so an in-flight
     * round survives the surface lifecycle. Extracted as `@VisibleForTesting internal` so the
     * regression test can drive it without spinning up a real game-loop thread.
     */
    @VisibleForTesting
    internal fun initEngineIfNeeded() {
        engine.init(width.toFloat(), height.toFloat(), currentStats, currentTier, currentWsLevels, isReducedMotion, currentStartWave)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        engine.init(width.toFloat(), height.toFloat(), currentStats, currentTier, currentWsLevels, isReducedMotion, currentStartWave)
        val thread = GameLoopThread(holder, engine)
        thread.isRunning = true; thread.start(); gameThread = thread
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.init(width.toFloat(), height.toFloat(), currentStats, currentTier, currentWsLevels, isReducedMotion, currentStartWave)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        val thread = gameThread ?: return
        thread.isRunning = false
        try { thread.join(1000) } catch (_: InterruptedException) {}
        gameThread = null
        soundManager.release()
    }

    fun setSpeedMultiplier(speed: Float) { gameThread?.speedMultiplier = speed }
    fun setPaused(paused: Boolean) { gameThread?.isPaused = paused }
    fun updateSoundMuted(muted: Boolean) { soundManager.setMuted(muted) }
}
