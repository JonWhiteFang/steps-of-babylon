package com.whitefang.stepsofbabylon.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import com.whitefang.stepsofbabylon.service.StepCounterService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs a full local data wipe: cancels background work, closes and deletes
 * the encrypted database, clears all SharedPreferences, removes the Keystore
 * alias, and recreates the Activity so Hilt rebuilds the object graph cleanly.
 */
@Singleton
class DataDeletionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {

    fun deleteAllData(activity: Activity) {
        // 1. Cancel all WorkManager work (StepSyncWorker, etc.)
        WorkManager.getInstance(context).cancelAllWork()

        // 2. Stop foreground step-counting service
        context.stopService(Intent(context, StepCounterService::class.java))

        // 3. Close the database connection
        database.close()

        // 4. Delete the database file + WAL/SHM companions
        context.deleteDatabase(DB_FILENAME)

        // 5. Clear all SharedPreferences
        PREFS_NAMES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }

        // 6. Delete the Android Keystore alias
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(KEYSTORE_ALIAS)) {
                ks.deleteEntry(KEYSTORE_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete Keystore alias", e)
        }

        // 7. Recreate the Activity — Hilt rebuilds the graph, Room reseeds
        activity.recreate()
    }

    companion object {
        private const val TAG = "DataDeletionManager"
        private const val DB_FILENAME = "steps_of_babylon.db"
        private const val KEYSTORE_ALIAS = "steps_of_babylon_db_key"

        internal val PREFS_NAMES = listOf(
            "biome_prefs",
            "milestone_notification_prefs",
            "notification_prefs",
            "sound_prefs",
            "music_prefs",
            "anti_cheat_prefs",
            "step_ingestion",
            "db_key_prefs",
            "widget_data",
            "smart_reminders",
            "billing_anti_fraud",
            "crash_breadcrumb_prefs",
        )
    }
}
