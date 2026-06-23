package com.whitefang.stepsofbabylon.presentation.onboarding

import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import com.whitefang.stepsofbabylon.data.sensor.BatteryOptimizationStatus
import com.whitefang.stepsofbabylon.data.sensor.StepSensorDataSource
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OnboardingViewModelTest {
    private val prefs = mock<OnboardingPreferences>()

    // mockito-core 5.x's inline maker mocks final Kotlin classes directly (same as the
    // mock<Activity>() usage in BillingManagerImplTest) — no interface extraction needed.
    private val sensor = mock<StepSensorDataSource>()
    private val battery = mock<BatteryOptimizationStatus>()

    private fun vm() = OnboardingViewModel(prefs, sensor, battery)

    @Test
    fun `exposes the canonical slide list`() {
        // Identity (not value-equality) is the contract: the VM must expose the SAME list
        // instance, never a copy — keeps the no-copy guarantee the screen relies on.
        assertSame(OnboardingContent.slides, vm().slides)
    }

    @Test
    fun `completeOnboarding persists the completion flag`() {
        val viewModel = vm()
        viewModel.completeOnboarding()
        verify(prefs).setCompleted()
    }

    // #193: the VM must surface hardware step-counter absence so the screen can warn the player
    // instead of showing a meaningless "enable step counting" path on a no-sensor device.
    @Test
    fun `stepSensorAvailable reflects the sensor data source - absent`() {
        whenever(sensor.isSensorAvailable()).thenReturn(false)
        assertFalse(vm().stepSensorAvailable)
    }

    @Test
    fun `stepSensorAvailable reflects the sensor data source - present`() {
        whenever(sensor.isSensorAvailable()).thenReturn(true)
        assertTrue(vm().stepSensorAvailable)
    }

    // #261: offer the battery-exemption prompt only when the app is NOT already exempt, so an
    // already-whitelisted device skips it.
    @Test
    fun `shouldOfferBatteryExemption is true when not already exempt`() {
        whenever(battery.isIgnoring()).thenReturn(false)
        assertTrue(vm().shouldOfferBatteryExemption)
    }

    @Test
    fun `shouldOfferBatteryExemption is false when already exempt`() {
        whenever(battery.isIgnoring()).thenReturn(true)
        assertFalse(vm().shouldOfferBatteryExemption)
    }
}
