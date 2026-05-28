package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.whitefang.stepsofbabylon.R

enum class SoundEffect { SHOOT, HIT, ENEMY_DEATH, UW_ACTIVATE, UPGRADE_PURCHASE, WAVE_START, ROUND_END }

class SoundManager(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()

    private val soundIds = mutableMapOf<SoundEffect, Int>()
    private var volume = 1f
    private var muted = false
    private var lastShootTime = 0L

    init {
        soundIds[SoundEffect.SHOOT] = soundPool.load(context, R.raw.sfx_shoot, 1)
        soundIds[SoundEffect.HIT] = soundPool.load(context, R.raw.sfx_hit, 1)
        soundIds[SoundEffect.ENEMY_DEATH] = soundPool.load(context, R.raw.sfx_enemy_death, 1)
        soundIds[SoundEffect.UW_ACTIVATE] = soundPool.load(context, R.raw.sfx_uw_activate, 1)
        soundIds[SoundEffect.UPGRADE_PURCHASE] = soundPool.load(context, R.raw.sfx_upgrade, 1)
        soundIds[SoundEffect.WAVE_START] = soundPool.load(context, R.raw.sfx_wave_start, 1)
        soundIds[SoundEffect.ROUND_END] = soundPool.load(context, R.raw.sfx_round_end, 1)
    }

    fun play(effect: SoundEffect, expectedIntervalMs: Long = 100L) {
        if (muted) return
        // Frequency-aware throttle for SHOOT: scales to ~1/3 of expected interval,
        // floored at 30ms (SoundPool channel safety) and capped at 100ms (baseline).
        if (effect == SoundEffect.SHOOT) {
            val now = System.currentTimeMillis()
            val throttle = (expectedIntervalMs / 3L).coerceIn(30L, 100L)
            if (now - lastShootTime < throttle) return
            lastShootTime = now
        }
        soundIds[effect]?.let { soundPool.play(it, volume, volume, 1, 0, 1f) }
    }

    fun setVolume(v: Float) { volume = v.coerceIn(0f, 1f) }
    fun setMuted(m: Boolean) { muted = m }
    fun isMuted(): Boolean = muted
    fun release() { soundPool.release() }
}
