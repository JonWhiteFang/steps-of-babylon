package com.whitefang.stepsofbabylon

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1X-08 Phase 1A smoke test. Proves the instrumented-test harness boots end-to-end:
 *
 * - `androidTest` source set is compiled and packaged into a debug-androidTest APK
 * - [HiltTestRunner] swaps in `HiltTestApplication` at instrumentation init
 * - `kspAndroidTest` generates Hilt component code for `@HiltAndroidTest` classes
 * - [AndroidJUnit4] runner dispatches the test method
 * - `HiltAndroidRule.inject()` succeeds without crashing
 *
 * Deliberately does NOT touch the database, foreground service, sensors, or any
 * permissions-requiring subsystem. Those are the responsibility of the three real
 * suites (BattleSurfaceLifecycleTest, StoreIapFlowTest, DeepLinkIntentTest) being
 * layered on top in follow-up PRs.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InfrastructureSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun harnessBoots() {
        // If we reached this point, every layer of the instrumented harness is wired up
        // (Gradle config, runner, KSP, Hilt). The assertion is a formality.
        assertTrue("instrumented test harness up and running", true)
    }
}
