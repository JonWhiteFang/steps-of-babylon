package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import com.whitefang.stepsofbabylon.data.sensor.BatteryOptimizationStatus
import com.whitefang.stepsofbabylon.data.sensor.StepSensorDataSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #253 (Compose-UI-test beachhead) — renders the real [OnboardingScreen] on the Robolectric/JVM
 * lane with a mock-backed [OnboardingViewModel] (its `StepSensorDataSource` / `BatteryOptimizationStatus`
 * deps are concrete final classes with no fakes, so they are mocked exactly as `OnboardingViewModelTest`
 * does). Asserts the carousel, the final-slide CTA branching, and an a11y semantics node — the
 * onboarding-flow gap the 2026-06-18 audit flagged.
 *
 * Assertion targets verified against `OnboardingScreen.kt` (see wave plan F-D/F-E/F-F):
 * - Skip jumps to the last slide (`goTo(lastIndex)`); it does NOT call `onFinished`.
 * - The only real `contentDescription` is the page-dots row "Page X of N" (`:194`); CTAs are plain Text.
 * - "Start playing" requires `stepCountingGranted` + sensor available + `isIgnoring()==true`
 *   (so `shouldOfferBatteryExemption` is false and the battery primer is suppressed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = mock<OnboardingPreferences>()
    private val sensor = mock<StepSensorDataSource>()
    private val battery = mock<BatteryOptimizationStatus>()

    private fun vm() = OnboardingViewModel(prefs, sensor, battery)

    @Test
    fun `renders the first slide and the page-position content description`() {
        whenever(sensor.isSensorAvailable()).thenReturn(true)
        val slides = OnboardingContent.slides

        composeRule.setContent {
            OnboardingScreen(
                stepCountingGranted = false,
                permissionAsked = false,
                reducedMotion = true,
                onEnableStepCounting = {},
                onOpenAppSettings = {},
                onRequestBatteryExemption = {},
                onFinished = {},
                viewModel = vm(),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ctx.getString(slides.first().titleRes)).assertIsDisplayed()
        // a11y: the page-dots row carries the only explicit semantics label (HorizontalPager is silent).
        composeRule.onNodeWithContentDescription("Page 1 of ${slides.size}").assertExists()
    }

    @Test
    fun `Skip jumps to the last slide and does not finish`() {
        whenever(sensor.isSensorAvailable()).thenReturn(true)
        var finished = false
        val slides = OnboardingContent.slides

        composeRule.setContent {
            OnboardingScreen(
                stepCountingGranted = false,
                permissionAsked = false,
                reducedMotion = true,
                onEnableStepCounting = {},
                onOpenAppSettings = {},
                onRequestBatteryExemption = {},
                onFinished = { finished = true },
                viewModel = vm(),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ctx.getString(R.string.onboarding_skip)).performClick()
        composeRule.waitForIdle()

        // Skip navigates to the last slide → the dots row now reports the final page; onFinished untouched.
        composeRule.onNodeWithContentDescription("Page ${slides.size} of ${slides.size}").assertExists()
        assertTrue("Skip must not invoke onFinished", !finished)
    }

    @Test
    fun `final slide shows Enable step counting when ungranted and sensor present`() {
        whenever(sensor.isSensorAvailable()).thenReturn(true)

        composeRule.setContent {
            OnboardingScreen(
                stepCountingGranted = false,
                permissionAsked = false,
                reducedMotion = true,
                onEnableStepCounting = {},
                onOpenAppSettings = {},
                onRequestBatteryExemption = {},
                onFinished = {},
                viewModel = vm(),
            )
        }
        composeRule.waitForIdle()
        // jump to the final (permission) slide
        composeRule.onNodeWithText(ctx.getString(R.string.onboarding_skip)).performClick()
        composeRule.waitForIdle()

        // A Button merges its child Text, so the label matches both the button node and the text node;
        // assert the first match is displayed.
        composeRule
            .onAllNodesWithText(
                ctx.getString(R.string.onboarding_enable_step_counting),
            ).onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun `final slide shows Start playing when granted, sensor present, and already battery-exempt`() {
        whenever(sensor.isSensorAvailable()).thenReturn(true)
        whenever(battery.isIgnoring()).thenReturn(true) // suppresses the #261 battery primer

        composeRule.setContent {
            OnboardingScreen(
                stepCountingGranted = true,
                permissionAsked = true,
                reducedMotion = true,
                onEnableStepCounting = {},
                onOpenAppSettings = {},
                onRequestBatteryExemption = {},
                onFinished = {},
                viewModel = vm(),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(ctx.getString(R.string.onboarding_skip)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(ctx.getString(R.string.onboarding_start_playing)).onFirst().assertIsDisplayed()
    }
}
