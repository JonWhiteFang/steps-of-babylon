package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

    val slides: List<OnboardingSlide> = OnboardingContent.slides

    /** Persists that the player has finished (or skipped through) onboarding. Idempotent. */
    fun completeOnboarding() {
        onboardingPreferences.setCompleted()
    }
}
