package com.whitefang.stepsofbabylon

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.service.StepSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StepsOfBabylonApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var crashBreadcrumbStore: CrashBreadcrumbStore

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        StepSyncScheduler.schedule(this)
        // #190 REL-1: install a chaining global handler so any uncaught exception is recorded
        // locally before the platform handler terminates the process (preserves Play vitals).
        Thread.setDefaultUncaughtExceptionHandler(
            buildCrashHandler(
                previous = Thread.getDefaultUncaughtExceptionHandler(),
                record = { thread, ex, ts -> crashBreadcrumbStore.record(thread, ex, ts) },
            )
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

/**
 * Builds the chaining uncaught-exception handler (#190 REL-1). Extracted as a top-level
 * `internal fun` so the chaining + best-effort-record behaviour is JVM-unit-testable without
 * an Android `Application`. Records the breadcrumb FIRST (the process may die on delegation),
 * never throws, then delegates to [previous] so the platform crash / Play vitals is preserved.
 */
internal fun buildCrashHandler(
    previous: Thread.UncaughtExceptionHandler?,
    record: (threadName: String, throwable: Throwable, timestampMillis: Long) -> Unit,
): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, ex ->
    runCatching { record(thread.name, ex, System.currentTimeMillis()) }
    runCatching { Log.e("StepsOfBabylonApp", "Uncaught exception on ${thread.name}", ex) }
    previous?.uncaughtException(thread, ex)
}
