package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.whitefang.stepsofbabylon.R

/**
 * Manages background music playback with two tracks (walking/battle),
 * audio focus handling, and volume/mute controls.
 */
class MusicManager(private val context: Context) : AudioManager.OnAudioFocusChangeListener {

    private var walkingPlayer: MediaPlayer? = null
    private var battlePlayer: MediaPlayer? = null
    private var activeTrack: Track = Track.NONE
    private var volume: Float = 0.5f
    private var muted: Boolean = false
    private var focusLost: Boolean = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setOnAudioFocusChangeListener(this)
        .build()

    enum class Track { NONE, WALKING, BATTLE }

    fun playWalking() {
        if (activeTrack == Track.WALKING) return
        stopActive()
        activeTrack = Track.WALKING
        walkingPlayer = createPlayer(R.raw.bgm_walking)
        startIfNotMuted()
    }

    fun playBattle() {
        if (activeTrack == Track.BATTLE) return
        stopActive()
        activeTrack = Track.BATTLE
        battlePlayer = createPlayer(R.raw.bgm_battle)
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
        if (muted) activePlayer()?.pause()
        else if (!focusLost) activePlayer()?.start()
    }

    fun isMuted(): Boolean = muted
    fun getVolume(): Float = volume

    fun release() {
        walkingPlayer?.release(); walkingPlayer = null
        battlePlayer?.release(); battlePlayer = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        activeTrack = Track.NONE
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
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

    private fun createPlayer(resId: Int): MediaPlayer =
        MediaPlayer.create(context, resId).apply {
            isLooping = true
            setVolume(volume, volume)
        }

    private fun startIfNotMuted() {
        if (!muted) {
            audioManager.requestAudioFocus(focusRequest)
            activePlayer()?.start()
        }
    }

    private fun stopActive() {
        walkingPlayer?.release(); walkingPlayer = null
        battlePlayer?.release(); battlePlayer = null
    }

    private fun activePlayer(): MediaPlayer? = when (activeTrack) {
        Track.WALKING -> walkingPlayer
        Track.BATTLE -> battlePlayer
        Track.NONE -> null
    }
}
