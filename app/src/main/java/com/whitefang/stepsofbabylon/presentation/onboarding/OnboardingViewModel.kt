package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import com.whitefang.stepsofbabylon.data.sensor.StepSensorDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    stepSensorDataSource: StepSensorDataSource,
) : ViewModel() {

    val slides: List<OnboardingSlide> = OnboardingContent.slides

    /**
     * #193: whether this device has a hardware step-counter sensor. When false, onboarding tells the
     * player the core mechanic won't work via the on-device sensor and steers them to Health Connect,
     * instead of letting them grant a meaningless permission and earn nothing. Read once at
     * construction (a cheap, stable registry lookup — hardware presence doesn't change at runtime).
     */
    val stepSensorAvailable: Boolean = stepSensorDataSource.isSensorAvailable()

    /** Persists that the player has finished (or skipped through) onboarding. Idempotent. */
    fun completeOnboarding() {
        onboardingPreferences.setCompleted()
    }
}
