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
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs a full local data wipe: cancels background work, closes and deletes
 * the encrypted database, clears all SharedPreferences, removes the Keystore
 * alias, and recreates the Activity so Hilt rebuilds the object graph cleanly.
 */
@Singleton
class DataDeletionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: AppDatabase,
    ) {
        fun deleteAllData(activity: Activity) {
            // 1. Cancel all WorkManager work (StepSyncWorker, etc.) and AWAIT the cancellation (#248).
            //    cancelAllWork() is asynchronous; without the await, an in-flight StepSyncWorker coroutine could
            //    still be mid-write when database.close() runs below → "attempt to re-open an already-closed
            //    object" on a background thread. Bound the wait (this runs on the main thread, off the Settings
            //    click handler) so a stuck cancel can't ANR; on timeout we proceed anyway — a missed write is
            //    wiped regardless. This deterministically closes the WorkManager half of the close-race.
            try {
                WorkManager
                    .getInstance(context)
                    .cancelAllWork()
                    .result
                    .get(CANCEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                Log.w(TAG, "Timed out awaiting work cancellation; proceeding with wipe", e)
            } catch (e: Exception) {
                Log.w(TAG, "Error awaiting work cancellation; proceeding with wipe", e)
            }

            // 2. Stop foreground step-counting service (best-effort; async — the service nulls its own collector
            //    on teardown, and Room's lazy reopen-after-close backstops a late sensor write).
            context.stopService(Intent(context, StepCounterService::class.java))

            // 3. Close the database connection — now strictly after the WorkManager cancel barrier.
            database.close()

            // 4. Delete the database file + WAL/SHM companions
            context.deleteDatabase(DB_FILENAME)

            // 5. Clear all SharedPreferences
            PREFS_NAMES.forEach { name ->
                context
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
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

            // #248: bounded wait for work cancellation — must stay well under Android's 5s ANR window since
            // deleteAllData runs on the main thread (called synchronously from the Settings click handler).
            private const val CANCEL_TIMEOUT_SECONDS = 2L

            // #247: the authoritative list of every SharedPreferences file the "Delete All Data" wipe
            // must clear. DataDeletionPrefsCoverageTest scans the source tree for getSharedPreferences()
            // call sites and fails the build if a prefs file is missing here, so this list can't drift.
            internal val PREFS_NAMES =
                listOf(
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
                    "onboarding_prefs", // #247: a wipe must re-show the first-run tutorial/permission primer
                    "haptics_prefs", // #247
                )
        }
    }
