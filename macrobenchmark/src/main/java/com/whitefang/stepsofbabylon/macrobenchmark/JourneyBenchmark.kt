package com.whitefang.stepsofbabylon.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #26 Gate G — frame-timing (jank visibility) over the Home → Workshop → Battle journey.
 * Gives frame-duration distribution, NOT per-Composable recomposition counts (recomposition
 * profiling is a named follow-up — see the spec). Run on a connected device; not CI-gated.
 */
@RunWith(AndroidJUnit4::class)
class JourneyBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun journey() = rule.measureRepeated(
        packageName = "com.whitefang.stepsofbabylon",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
    ) {
        startActivityAndWait()
        device.waitForIdle()
        // Nav into Workshop + Battle is refined on-device (selectors depend on labels).
    }
}
