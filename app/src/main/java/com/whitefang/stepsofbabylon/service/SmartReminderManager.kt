package com.whitefang.stepsofbabylon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.notification.NotificationPolicy
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.WorkshopRepository
import com.whitefang.stepsofbabylon.domain.usecase.CalculateUpgradeCost
import com.whitefang.stepsofbabylon.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartReminderManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val playerRepository: PlayerRepository,
        private val workshopRepository: WorkshopRepository,
        private val notificationPreferences: NotificationPreferences,
    ) {
        companion object {
            private const val CHANNEL_ID = "reminders"
            private const val NOTIFICATION_ID = 3001
            private const val PREFS = "smart_reminders"
            private const val INACTIVITY_MS = 4 * 60 * 60 * 1000L // 4 hours
            private const val MAX_GAP = 10_000L
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private val calculateCost = CalculateUpgradeCost()

        init {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_desc) }
            notificationManager.createNotificationChannel(channel)
        }

        suspend fun checkAndNotify() {
            if (!notificationPreferences.isSmartRemindersEnabled()) return
            // #216: no reminder during quiet hours. Routed through canSendReminder (not
            // isWithinQuietHours) so the unit-tested decision fn is the one on the shipping path.
            // Placed before the last_sent write below, so a suppressed reminder doesn't burn the
            // day's slot. (This path runs from StepSyncWorker, NOT under the #120 credit mutex, so
            // its in-method getSharedPreferences is not lock-sensitive — unlike the supply-drop path.)
            if (!NotificationPolicy.canSendReminder(LocalTime.now())) return

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = LocalDate.now().toString()
            if (prefs.getString("last_sent", "") == today) return

            val profile = playerRepository.observeProfile().first()
            val now = System.currentTimeMillis()
            if (now - profile.lastActiveAt < INACTIVITY_MS) return

            val upgrades = workshopRepository.observeAllUpgrades().first()
            var bestGap = Long.MAX_VALUE
            var bestName = ""
            for ((type, level) in upgrades) {
                val maxLevel = type.config.maxLevel
                if (maxLevel != null && level >= maxLevel) continue
                val cost = calculateCost(type, level)
                val gap = cost - profile.stepBalance
                if (gap in 1..MAX_GAP && gap < bestGap) {
                    bestGap = gap
                    bestName = type.config.description
                }
            }
            if (bestName.isEmpty()) return

            val intent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).putExtra("navigate_to", "workshop"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_directions)
                    .setContentTitle(context.getString(R.string.reminder_title))
                    .setContentText(
                        context.resources.getQuantityString(
                            R.plurals.reminder_steps_away,
                            bestGap.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                            bestGap,
                            bestName,
                        ),
                    ).setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
            prefs.edit().putString("last_sent", today).apply()
        }
    }
