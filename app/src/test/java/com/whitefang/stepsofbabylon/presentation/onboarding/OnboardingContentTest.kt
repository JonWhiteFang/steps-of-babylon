package com.whitefang.stepsofbabylon.presentation.onboarding

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingContentTest {

    @Test
    fun `slides are non-empty and only the final slide is the permission primer`() {
        val slides = OnboardingContent.slides
        assertTrue(slides.isNotEmpty(), "onboarding must have at least one slide")

        // The final slide MUST be the permission primer — routing, completion, and the
        // Skip contract all assume the last page is where the permission ask lives.
        assertTrue(slides.last().isPermissionPrimer, "last slide must be the permission primer")

        // No earlier slide may be a primer (exactly one, at the end).
        slides.dropLast(1).forEach { slide ->
            assertFalse(slide.isPermissionPrimer, "only the final slide may be the permission primer")
        }
    }

    @Test
    fun `every slide has a title and body`() {
        OnboardingContent.slides.forEach { slide ->
            assertTrue(slide.title.isNotBlank(), "slide title must not be blank")
            assertTrue(slide.body.isNotBlank(), "slide body must not be blank")
        }
    }
}
