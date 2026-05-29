package com.whitefang.stepsofbabylon

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom [AndroidJUnitRunner] that swaps the production [StepsOfBabylonApp] for Hilt's
 * [HiltTestApplication] during instrumented tests, so `@HiltAndroidTest`-annotated tests
 * can resolve dependencies through Hilt's component graph instead of trying to boot the
 * real app (Room+SQLCipher, Health Connect, foreground service, AdMob/UMP, etc.).
 *
 * Wired via `testInstrumentationRunner = "com.whitefang.stepsofbabylon.HiltTestRunner"`
 * in `app/build.gradle.kts`. Companion to V1X-08 Phase 1A.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
