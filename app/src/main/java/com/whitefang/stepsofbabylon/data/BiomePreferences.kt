package com.whitefang.stepsofbabylon.data

import android.content.Context
import com.whitefang.stepsofbabylon.domain.model.Biome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiomePreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("biome_prefs", Context.MODE_PRIVATE)

        fun hasSeenBiome(biome: Biome): Boolean = prefs.getBoolean(biome.name, false)

        fun markBiomeSeen(biome: Biome) {
            prefs.edit().putBoolean(biome.name, true).apply()
        }
    }
