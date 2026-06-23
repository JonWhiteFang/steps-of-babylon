package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.whitefang.stepsofbabylon.R
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages background music playback with two tracks (walking/battle),
 * audio focus handling, and volume/mute controls.
 *
 * #242 (ADR-0033): each track's [MediaPlayer] is built **at most once**, **off the main thread** (the
 * 1.3 MB OGG decode is synchronous in `MediaPlayer.create`), and then cached. Track switches `pause()`
 * the outgoing player and `start()` the incoming one — no release/recreate on navigation, so
 * Battle↔menu transitions no longer hitch the main thread. **Concurrency invariant:** the
 * [decodeExecutor] runs ONLY the blocking decode; ALL shared state and ALL `MediaPlayer` control calls
 * run on the main thread (the decode result is posted back via [mainHandler]). [desiredTrack] is the
 * last-requested track (last-write-wins); [activeTrack] is what is actually started; a per-track
 * "pending" flag dedups in-flight decodes so a request that arrives mid-decode never starts a second
 * decode.
 *
 * @param playerFactory creates a [MediaPlayer] for a raw-resource id, returning `null` on failure
 *   (#246; degrade to silent). Injectable so the null-failure path is testable without a real codec.
 * @param injectedExecutor if non-null, used for the decode (tests pass a synchronous executor for
 *   determinism); if null, an owned single-thread executor is created and shut down in [release].
 */
class MusicManager(
    private val context: Context,
    private val playerFactory: (Context, Int) -> MediaPlayer? = { ctx, resId -> MediaPlayer.create(ctx, resId) },
    injectedExecutor: Executor? = null,
) : AudioManager.OnAudioFocusChangeListener {
    private val ownedExecutorService: ExecutorService? =
        if (injectedExecutor == null) Executors.newSingleThreadExecutor() else null
    private val decodeExecutor: Executor = injectedExecutor ?: ownedExecutorService!!
    private val mainHandler = Handler(Looper.getMainLooper())

    private var walkingPlayer: MediaPlayer? = null
    private var battlePlayer: MediaPlayer? = null
    private var activeTrack: Track = Track.NONE
    private var desiredTrack: Track = Track.NONE
    private var walkingPending = false
    private var battlePending = false
    private var released = false
    private var volume: Float = 0.5f
    private var muted: Boolean = false
    private var focusLost: Boolean = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            ).setOnAudioFocusChangeListener(this)
            .build()

    enum class Track { NONE, WALKING, BATTLE }

    fun playWalking() = switchTo(Track.WALKING, R.raw.bgm_walking)

    fun playBattle() = switchTo(Track.BATTLE, R.raw.bgm_battle)

    /**
     * Request [track]. Main-thread only. Already playing it → no-op (covers the frequent menu→menu
     * navigations that all call [playWalking]). Already built → switch in immediately. Otherwise
     * dispatch the decode once (dedup'd by the pending flag) and resolve in [onDecoded].
     */
    private fun switchTo(
        track: Track,
        resId: Int,
    ) {
        desiredTrack = track
        if (activeTrack == track) return // already playing this track — don't restart
        val existing = playerFor(track)
        if (existing != null) {
            activate(track, existing)
            return
        }
        if (isPending(track)) return // a decode for this track is already in flight (build-once)
        setPending(track, true)
        val ctx = context
        // Off-main-thread decode ONLY; the result is posted back to main where all state lives.
        decodeExecutor.execute {
            val player = playerFactory(ctx, resId)
            mainHandler.post { onDecoded(track, player) }
        }
    }

    /** Main-thread callback once a decode finishes (or fails). */
    private fun onDecoded(
        track: Track,
        player: MediaPlayer?,
    ) {
        setPending(track, false)
        if (released) {
            // App is being torn down — don't retain or start; just free the just-built player.
            player?.release()
            return
        }
        if (player == null) {
            // #246: decode failed → degrade to silent. The player stays unbuilt and the pending flag
            // is cleared, so a later navigation re-attempts (activeTrack is untouched — if another
            // track is currently playing it keeps playing rather than being cut to silence).
            return
        }
        player.isLooping = true
        player.setVolume(volume, volume)
        storePlayer(track, player)
        if (desiredTrack == track) {
            activate(track, player)
        }
        // else: superseded by a newer request — keep it cached + paused, do NOT start.
    }

    /** Switch playback to [player] for [track]: pause the outgoing track, rewind, start if unmuted. */
    private fun activate(
        track: Track,
        player: MediaPlayer,
    ) {
        activePlayer()?.pause()
        activeTrack = track
        player.seekTo(0) // restart each track at the top on entry (matches the pre-#242 recreate feel)
        startIfNotMuted()
    }

    fun pause() {
        activePlayer()?.pause()
    }

    fun resume() {
        if (!muted && !focusLost) activePlayer()?.start()
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        activePlayer()?.setVolume(volume, volume)
    }

    fun setMuted(m: Boolean) {
        muted = m
        if (muted) {
            activePlayer()?.pause()
        } else if (!focusLost) {
            activePlayer()?.start()
        }
    }

    fun isMuted(): Boolean = muted

    fun getVolume(): Float = volume

    fun release() {
        released = true
        walkingPlayer?.release()
        walkingPlayer = null
        battlePlayer?.release()
        battlePlayer = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        activeTrack = Track.NONE
        desiredTrack = Track.NONE
        // Only shut down the executor we own; an injected (test) executor is left alone.
        ownedExecutorService?.shutdown()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                focusLost = true
                activePlayer()?.pause()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                if (!muted) activePlayer()?.start()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                activePlayer()?.setVolume(volume * 0.3f, volume * 0.3f)
            }
        }
    }

    @VisibleForTesting
    internal fun activePlayerOrNullForTest(): MediaPlayer? = activePlayer()

    private fun startIfNotMuted() {
        if (!muted) {
            audioManager.requestAudioFocus(focusRequest)
            activePlayer()?.start()
        }
    }

    private fun activePlayer(): MediaPlayer? = playerFor(activeTrack)

    private fun playerFor(track: Track): MediaPlayer? =
        when (track) {
            Track.WALKING -> walkingPlayer
            Track.BATTLE -> battlePlayer
            Track.NONE -> null
        }

    private fun storePlayer(
        track: Track,
        player: MediaPlayer,
    ) {
        when (track) {
            Track.WALKING -> {
                walkingPlayer = player
            }

            Track.BATTLE -> {
                battlePlayer = player
            }

            Track.NONE -> {}
        }
    }

    private fun isPending(track: Track): Boolean =
        when (track) {
            Track.WALKING -> walkingPending
            Track.BATTLE -> battlePending
            Track.NONE -> false
        }

    private fun setPending(
        track: Track,
        pending: Boolean,
    ) {
        when (track) {
            Track.WALKING -> {
                walkingPending = pending
            }

            Track.BATTLE -> {
                battlePending = pending
            }

            Track.NONE -> {}
        }
    }
}
