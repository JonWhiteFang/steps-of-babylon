package com.whitefang.stepsofbabylon.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #26 Gate G — cold-start timing for the most-used launch. Run on a connected device:
 *   ./run-gradle.sh :macrobenchmark:connectedBenchmarkReleaseAndroidTest
 * Compares CompilationMode.None() (no cloud profile) vs Partial(BaselineProfiles()) to quantify
 * the committed profile's win. Numbers are recorded in docs/performance/startup-baseline.md.
 * NOT part of the CI gate — emulator timings are unreliable.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        startup(CompilationMode.Partial(androidx.benchmark.macro.BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = "com.whitefang.stepsofbabylon",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
