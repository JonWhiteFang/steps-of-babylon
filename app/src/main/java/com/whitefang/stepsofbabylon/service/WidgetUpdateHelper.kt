package com.whitefang.stepsofbabylon.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdateHelper
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val THROTTLE_MS = 60_000L
        }

        private var lastUpdateMs = 0L

        fun update(
            dailySteps: Long,
            balance: Long,
        ) {
            val now = System.currentTimeMillis()
            if (now - lastUpdateMs < THROTTLE_MS) return
            lastUpdateMs = now
            StepWidgetProvider.saveData(context, dailySteps, balance)
            StepWidgetProvider.updateAllWidgets(context)
        }
    }
