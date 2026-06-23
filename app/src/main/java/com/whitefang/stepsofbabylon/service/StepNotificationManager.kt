package com.whitefang.stepsofbabylon.service

import android.app.Notification
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
class StepNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val notificationPreferences: NotificationPreferences,
    ) {
        companion object {
            const val CHANNEL_ID = "step_counter"
            const val NOTIFICATION_ID = 1001
            private const val THROTTLE_MS = 30_000L
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private var lastUpdateMs = 0L

        init {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_step_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.notif_step_channel_desc) }
            notificationManager.createNotificationChannel(channel)
        }

        fun buildNotification(
            dailySteps: Long,
            balance: Long,
        ): Notification {
            val tapIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val workshopIntent =
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java).putExtra("navigate_to", "workshop"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val battleIntent =
                PendingIntent.getActivity(
                    context,
                    2,
                    Intent(context, MainActivity::class.java).putExtra("navigate_to", "battle"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(
                    context.getString(
                        R.string.notif_step_content,
                        context.resources.getQuantityString(
                            R.plurals.notif_today_steps,
                            dailySteps.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                            dailySteps,
                        ),
                        context.getString(R.string.notif_balance, balance),
                    ),
                ).setContentIntent(tapIntent)
                .addAction(0, context.getString(R.string.notif_step_action_workshop), workshopIntent)
                .addAction(0, context.getString(R.string.notif_step_action_battle), battleIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }

        fun buildMinimalNotification(): Notification {
            val tapIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notif_step_minimal))
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }

        fun updateNotification(
            dailySteps: Long,
            balance: Long,
        ) {
            if (!notificationPreferences.isPersistentEnabled()) return
            val now = System.currentTimeMillis()
            if (now - lastUpdateMs < THROTTLE_MS) return
            lastUpdateMs = now
            notificationManager.notify(NOTIFICATION_ID, buildNotification(dailySteps, balance))
        }
    }
