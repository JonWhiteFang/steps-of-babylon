package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import com.whitefang.stepsofbabylon.data.sensor.BatteryOptimizationStatus
import com.whitefang.stepsofbabylon.data.sensor.StepSensorDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val onboardingPreferences: OnboardingPreferences,
        stepSensorDataSource: StepSensorDataSource,
        batteryOptimizationStatus: BatteryOptimizationStatus,
    ) : ViewModel() {
        val slides: List<OnboardingSlide> = OnboardingContent.slides

        /**
         * #193: whether this device has a hardware step-counter sensor. When false, onboarding tells the
         * player the core mechanic won't work via the on-device sensor and steers them to Health Connect,
         * instead of letting them grant a meaningless permission and earn nothing. Read once at
         * construction (a cheap, stable registry lookup — hardware presence doesn't change at runtime).
         */
        val stepSensorAvailable: Boolean = stepSensorDataSource.isSensorAvailable()

        /**
         * #261: whether onboarding should offer the battery-optimization exemption prompt — true only when
         * the app is NOT already exempt. The foreground step service is killed by Doze on aggressive OEMs
         * (the GDD's highest-rated risk); the prompt is the documented mitigation. Read once at construction
         * (like [stepSensorAvailable]); the onboarding screen also tracks a session-local "handled" flag so
         * the prompt closes after the user responds (the stale boolean alone would re-show it). The durable
         * re-offer lives in Settings.
         */
        val shouldOfferBatteryExemption: Boolean = !batteryOptimizationStatus.isIgnoring()

        /** Persists that the player has finished (or skipped through) onboarding. Idempotent. */
        fun completeOnboarding() {
            onboardingPreferences.setCompleted()
        }
    }
