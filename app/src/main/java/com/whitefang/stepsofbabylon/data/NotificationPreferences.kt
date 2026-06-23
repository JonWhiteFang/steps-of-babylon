package com.whitefang.stepsofbabylon.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

        fun isPersistentEnabled(): Boolean = prefs.getBoolean("persistent_steps", true)

        fun setPersistentEnabled(enabled: Boolean) = prefs.edit().putBoolean("persistent_steps", enabled).apply()

        fun isSupplyDropsEnabled(): Boolean = prefs.getBoolean("supply_drops", true)

        fun setSupplyDropsEnabled(enabled: Boolean) = prefs.edit().putBoolean("supply_drops", enabled).apply()

        fun isSmartRemindersEnabled(): Boolean = prefs.getBoolean("smart_reminders", true)

        fun setSmartRemindersEnabled(enabled: Boolean) = prefs.edit().putBoolean("smart_reminders", enabled).apply()

        fun isMilestoneAlertsEnabled(): Boolean = prefs.getBoolean("milestone_alerts", true)

        fun setMilestoneAlertsEnabled(enabled: Boolean) = prefs.edit().putBoolean("milestone_alerts", enabled).apply()
    }
