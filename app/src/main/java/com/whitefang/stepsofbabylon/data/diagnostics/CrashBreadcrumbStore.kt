package com.whitefang.stepsofbabylon.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local crash breadcrumb (#190 REL-1). Mirrors `OnboardingPreferences`:
 * @Singleton, constructor-injected, no Hilt module, SharedPreferences-backed.
 * Deliberately NOT Room — must not sync (cloud save #36) and a reinstall discards it.
 *
 * Single slot, newest-wins. Writes synchronously (`commit()`) because the process may be
 * dying when the global handler calls it. Every method is best-effort and never throws —
 * a diagnostic that crashes the crash handler is worse than useless.
 *
 * The backing file `crash_breadcrumb_prefs` is wiped by `DataDeletionManager` (#192-adjacent).
 */
@Singleton
class CrashBreadcrumbStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE)

    fun record(threadName: String, throwable: Throwable, timestampMillis: Long) {
        runCatching {
            prefs.edit()
                .putLong(KEY_TS, timestampMillis)
                .putString(KEY_THREAD, threadName)
                .putString(KEY_CLASS, throwable.javaClass.name)
                .putString(KEY_MESSAGE, throwable.message)
                .putString(KEY_STACK, throwable.stackTraceToString().take(MAX_STACK_CHARS))
                .commit() // synchronous — the process may be exiting
        }
    }

    fun peek(): CrashBreadcrumb? {
        if (!prefs.contains(KEY_TS)) return null
        return CrashBreadcrumb(
            timestampMillis = prefs.getLong(KEY_TS, 0L),
            threadName = prefs.getString(KEY_THREAD, "") ?: "",
            exceptionClass = prefs.getString(KEY_CLASS, "") ?: "",
            message = prefs.getString(KEY_MESSAGE, null),
            stackPreview = prefs.getString(KEY_STACK, "") ?: "",
        )
    }

    fun clear() {
        runCatching { prefs.edit().clear().commit() }
    }

    companion object {
        const val MAX_STACK_CHARS = 4096
        private const val KEY_TS = "crash_ts"
        private const val KEY_THREAD = "crash_thread"
        private const val KEY_CLASS = "crash_class"
        private const val KEY_MESSAGE = "crash_message"
        private const val KEY_STACK = "crash_stack"
    }
}
