package com.whitefang.stepsofbabylon.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)

        fun isMuted(): Boolean = prefs.getBoolean("muted", false)

        fun setMuted(muted: Boolean) = prefs.edit().putBoolean("muted", muted).apply()

        fun getVolume(): Float = prefs.getFloat("volume", 1f)

        fun setVolume(volume: Float) = prefs.edit().putFloat("volume", volume.coerceIn(0f, 1f)).apply()
    }
