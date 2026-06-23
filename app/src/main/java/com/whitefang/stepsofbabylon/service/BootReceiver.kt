package com.whitefang.stepsofbabylon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val hasPermission =
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            // #244: startForegroundService can itself throw ForegroundServiceStartNotAllowedException
            // on Android 12+ if a background BOOT_COMPLETED receiver isn't permitted to start an FGS.
            // Don't crash the receiver — WorkManager's StepSyncWorker handles step catch-up either way.
            try {
                context.startForegroundService(
                    Intent(context, StepCounterService::class.java),
                )
            } catch (_: RuntimeException) {
            }
        }
    }
}
