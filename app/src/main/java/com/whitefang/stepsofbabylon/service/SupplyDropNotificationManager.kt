package com.whitefang.stepsofbabylon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.notification.NotificationPolicy
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import com.whitefang.stepsofbabylon.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplyDropNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val notificationPreferences: NotificationPreferences,
        private val timeProvider: TimeProvider,
    ) {
        companion object {
            const val CHANNEL_ID = "supply_drops"
            private const val BASE_NOTIFICATION_ID = 2000
            private const val PREFS = "supply_drop_notifications"
            private const val KEY_DATE = "sent_date"
            private const val KEY_COUNT = "sent_count"
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // #216: field-cached so the one-time SharedPreferences disk load happens at construction
        // (@Singleton, off the #120 credit-mutex path) — NOT inside notify(), which runs under that
        // mutex via DailyStepManager.runFollowOnPipeline. Per-call get/put is then in-memory.
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        init {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_supply_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_supply_channel_desc) }
            notificationManager.createNotificationChannel(channel)
        }

        fun notify(drop: SupplyDrop) {
            if (!notificationPreferences.isSupplyDropsEnabled()) return

            // #216: quiet-hours + per-day cap. Read the wall clock ONCE (via the injected TimeProvider
            // seam) and split into local date (counter bucket) + local time (quiet-hours) so they can't
            // straddle a tick. The drop itself is already generated + persisted by the caller — only
            // this push is gated.
            val zoned = timeProvider.now().atZone(ZoneId.systemDefault())
            val today = zoned.toLocalDate().toString()
            val storedDate = prefs.getString(KEY_DATE, "")
            val sentToday = if (storedDate == today) prefs.getInt(KEY_COUNT, 0) else 0
            if (!NotificationPolicy.canSendSupplyDropNotification(zoned.toLocalTime(), sentToday)) return

            val intent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra("navigate_to", "supplies")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pending =
                PendingIntent.getActivity(
                    context,
                    drop.id,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle(context.getString(R.string.notif_supply_title))
                    .setContentText(drop.trigger.message)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(BASE_NOTIFICATION_ID + drop.id, notification)

            // Advance the per-day counter only after a real send (implicit reset on a new date).
            prefs
                .edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_COUNT, sentToday + 1)
                .apply()
        }
    }
