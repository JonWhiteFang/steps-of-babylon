package com.whitefang.stepsofbabylon.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #26 Gate G — generates the Baseline Profile. Run on a connected device with:
 *   ./run-gradle.sh :baselineprofile:generateBaselineProfile   (or the connected-device variant task)
 * The output is merged into app/src/main/baseline-prof.txt (committed). See docs/performance/.
 *
 * SCOPE TODO (refined on-device — see plan Task 9): today this captures only cold launch + Home
 * settle, which is the resilient minimum that works without UiAutomator label lookups. Deeper
 * Home → Workshop → Battle navigation (the full most-used path) is added on a real device by
 * inserting `device.findObject(By.text(...))` / click steps once the on-device labels are confirmed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.whitefang.stepsofbabylon") {
        pressHome()
        startActivityAndWait()
        // Let Home settle (currency dashboard + steps hero compose). Deeper nav is a deliberate
        // on-device TODO (see the class KDoc) — startup + Home are captured regardless.
        device.waitForIdle()
    }
}
