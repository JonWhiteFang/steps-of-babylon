package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #260: the claim formatter must cover every case the retired missionRewardLabel literal test did
 * (single currency, multi-currency join, Generic fallback) plus cosmetic names and card grants.
 *
 * `createComposeRule().setContent` may be called only ONCE per test, so each test formats all the
 * rewards it needs inside a single composition (mirrors CardsScreenTest's one-setContent-per-test).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ClaimRewardFormatTest {
    @get:Rule val rule = createComposeRule()

    /** Formats each reward inside ONE composition; returns the rendered strings in input order. */
    private fun formatAll(vararg rewards: ClaimReward?): List<String> {
        val out = arrayOfNulls<String>(rewards.size)
        rule.setContent {
            rewards.forEachIndexed { i, r -> out[i] = formatClaimReward(r) }
        }
        rule.waitForIdle()
        return out.map { it!! }
    }

    private fun format(reward: ClaimReward?): String = formatAll(reward).single()

    @Test fun `single currency`() {
        assertEquals("+5 Gems claimed!", format(ClaimReward.Bundle(gems = 5)))
    }
    @Test fun `single gem is singular`() {
        assertEquals("+1 Gem claimed!", format(ClaimReward.Bundle(gems = 1)))
    }
    @Test fun `multi currency joins`() {
        assertEquals("+5 Gems +2 Power Stones claimed!", format(ClaimReward.Bundle(gems = 5, powerStones = 2)))
    }
    @Test fun `cosmetic name is carried verbatim`() {
        assertEquals(
            "+200 Gems +50 Power Stones Lapis Lazuli Ziggurat Skin claimed!",
            format(ClaimReward.Bundle(gems = 200, powerStones = 50, cosmeticNames = listOf("Lapis Lazuli Ziggurat Skin"))),
        )
    }
    @Test fun `generic and null`() {
        val (generic, nullText) = formatAll(ClaimReward.Generic, null)
        assertEquals("Reward claimed!", generic)
        assertEquals("", nullText)
    }
}
