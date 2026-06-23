package com.whitefang.stepsofbabylon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val notificationPreferences: NotificationPreferences,
    ) {
        companion object {
            private const val CHANNEL_ID = "milestones"
            private const val WAVE_NOTIFICATION_ID = 4001
            private const val MILESTONE_NOTIFICATION_ID = 4002
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_milestone_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_milestone_channel_desc) }
            notificationManager.createNotificationChannel(channel)
        }

        fun notifyNewBestWave(
            wave: Int,
            biomeName: String,
        ) {
            if (!notificationPreferences.isMilestoneAlertsEnabled()) return
            val intent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle(context.getString(R.string.notif_milestone_best_wave_title))
                    .setContentText(context.getString(R.string.notif_milestone_best_wave_text, wave, biomeName))
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(WAVE_NOTIFICATION_ID, notification)
        }

        fun notifyMilestoneAchieved(milestoneName: String) {
            if (!notificationPreferences.isMilestoneAlertsEnabled()) return
            val intent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).putExtra("navigate_to", "missions"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle(context.getString(R.string.notif_milestone_achieved_title, milestoneName))
                    .setContentText(context.getString(R.string.notif_milestone_achieved_text))
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(MILESTONE_NOTIFICATION_ID, notification)
        }
    }
