package com.whitefang.stepsofbabylon.presentation.onboarding

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.whitefang.stepsofbabylon.domain.model.Biome
import org.junit.jupiter.api.Assertions.assertEquals

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

    @Test
    fun `slides map to the biome journey in order, skipping Underworld`() {
        // Bundle E (#164): the 4 slides walk Gardens -> Sands -> Frozen -> Celestial (the destination
        // biome). UNDERWORLD_OF_KUR is intentionally skipped (spec E5). Pin the exact ordered map.
        val expected = listOf(
            Biome.HANGING_GARDENS,
            Biome.BURNING_SANDS,
            Biome.FROZEN_ZIGGURATS,
            Biome.CELESTIAL_GATE,
        )
        assertEquals(expected, OnboardingContent.slides.map { it.biome })
        assertEquals(
            false,
            OnboardingContent.slides.any { it.biome == Biome.UNDERWORLD_OF_KUR },
            "Underworld of Kur must not theme an onboarding slide (spec E5)",
        )
    }

    @Test
    fun `exactly the first slide carries the ziggurat art`() {
        val slides = OnboardingContent.slides
        assertEquals(OnboardingArt.ZIGGURAT, slides.first().art, "slide 1 must carry the ziggurat emblem")
        assertEquals(
            1,
            slides.count { it.art != null },
            "exactly one slide carries art",
        )
    }
}
