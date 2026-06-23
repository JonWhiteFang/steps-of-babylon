package com.whitefang.stepsofbabylon.data

import android.content.Context
import com.whitefang.stepsofbabylon.domain.model.Milestone
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneNotificationPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("milestone_notification_prefs", Context.MODE_PRIVATE)

        fun hasNotified(milestone: Milestone): Boolean = prefs.getBoolean(milestone.name, false)

        fun markNotified(milestone: Milestone) {
            prefs.edit().putBoolean(milestone.name, true).apply()
        }
    }
