package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

        fun isMuted(): Boolean = prefs.getBoolean("muted", false)

        fun setMuted(muted: Boolean) {
            prefs.edit().putBoolean("muted", muted).apply()
        }

        fun getVolume(): Float = prefs.getFloat("volume", 0.5f)

        fun setVolume(volume: Float) {
            prefs.edit().putFloat("volume", volume).apply()
        }
    }
