package com.whitefang.stepsofbabylon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.domain.model.Biome
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
            biome: Biome,
        ) {
            if (!notificationPreferences.isMilestoneAlertsEnabled()) return
            // i18n(#34): resolve the biome display name at the notification Context boundary. The VM
            // (no Context) can't call stringResource, and the service layer must stay free of the
            // presentation-layer `Biome.nameRes()` extension — so map Biome→R.string here. Values are
            // byte-identical to the old `Biome.name.toDisplayName()` result the VM used to pass.
            val biomeName = context.getString(biomeNameRes(biome))
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

        /**
         * i18n(#34): Biome→display-name string-resource map, kept in the service layer so it can
         * resolve via the manager's Context without importing the presentation-layer `Biome.nameRes()`
         * extension. Mirrors those resource keys exactly (same `biome_name_*` values), including the
         * pre-existing `UNDERWORLD_OF_KUR` → "Underworld Of Kur" quirk. Exhaustive `when` (no `else`).
         */
        @StringRes
        private fun biomeNameRes(biome: Biome): Int =
            when (biome) {
                Biome.HANGING_GARDENS -> R.string.biome_name_hanging_gardens
                Biome.BURNING_SANDS -> R.string.biome_name_burning_sands
                Biome.FROZEN_ZIGGURATS -> R.string.biome_name_frozen_ziggurats
                Biome.UNDERWORLD_OF_KUR -> R.string.biome_name_underworld_of_kur
                Biome.CELESTIAL_GATE -> R.string.biome_name_celestial_gate
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
