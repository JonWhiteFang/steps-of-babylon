package com.whitefang.stepsofbabylon.presentation.onboarding

import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class OnboardingViewModelTest {

    private val prefs = mock<OnboardingPreferences>()
    private val viewModel = OnboardingViewModel(prefs)

    @Test
    fun `exposes the canonical slide list`() {
        // Identity (not value-equality) is the contract: the VM must expose the SAME list
        // instance, never a copy — keeps the no-copy guarantee the screen relies on.
        assertSame(OnboardingContent.slides, viewModel.slides)
    }

    @Test
    fun `completeOnboarding persists the completion flag`() {
        viewModel.completeOnboarding()
        verify(prefs).setCompleted()
    }
}
