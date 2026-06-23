package com.whitefang.stepsofbabylon.presentation.home

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #224: Home must teach the core walk→spend→battle loop at the one moment it matters — right after
 * onboarding, when the player has earned no Steps yet. [HomeUiState.showFirstWalkPrompt] is the pure
 * predicate that gates the "Go for a walk to earn your first Steps" prompt: shown only when the
 * player has both walked nothing today AND has essentially no Step balance to spend (so a returning
 * player who simply hasn't walked *today* but has a stockpile isn't nagged).
 */
class HomeFirstWalkPromptTest {
    @Test
    fun `prompt shows for a brand-new player with no steps today and no balance`() {
        assertTrue(HomeUiState(todaySteps = 0, stepBalance = 0, isLoading = false).showFirstWalkPrompt)
    }

    @Test
    fun `prompt hidden once the player has walked today`() {
        assertFalse(HomeUiState(todaySteps = 120, stepBalance = 0, isLoading = false).showFirstWalkPrompt)
    }

    @Test
    fun `prompt hidden for a returning player who has a Step balance to spend`() {
        // Hasn't walked today, but has a stockpile from previous days — not a first-walk moment.
        assertFalse(HomeUiState(todaySteps = 0, stepBalance = 5000, isLoading = false).showFirstWalkPrompt)
    }

    @Test
    fun `prompt never shows while loading or in an error state`() {
        assertFalse(HomeUiState(todaySteps = 0, stepBalance = 0, isLoading = true).showFirstWalkPrompt)
        assertFalse(HomeUiState(todaySteps = 0, stepBalance = 0, isLoading = false, error = "boom").showFirstWalkPrompt)
    }
}
