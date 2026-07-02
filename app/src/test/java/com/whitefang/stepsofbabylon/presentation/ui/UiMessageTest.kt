package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UiMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val staticCases =
        listOf(
            UiMessage.NotEnoughGems,
            UiMessage.NotEnoughSteps,
            UiMessage.AlreadyMaxLevel,
            UiMessage.NoAffordableUpgrades,
            UiMessage.CardAtMaxLevel,
            UiMessage.NotEnoughCopies,
            UiMessage.ResearchComingSoon,
            UiMessage.NoResearchSlot,
            UiMessage.AlreadyResearching,
            UiMessage.SeasonPassRequired,
            UiMessage.FreeRushUsed,
            UiMessage.NoActiveResearch,
            UiMessage.NotEnoughGemsOrMaxSlots,
            UiMessage.NotEnoughStepsMission,
            UiMessage.MilestoneAlreadyClaimed,
            UiMessage.AdCancelled,
            UiMessage.AdFailed,
        )

    @Test
    fun `every static UiMessage resolves to a non-blank string`() {
        staticCases.forEach { msg ->
            assertTrue("${msg::class.simpleName} must resolve non-blank", msg.resolve(context).isNotBlank())
        }
    }

    @Test
    fun `RewardUnavailable formats the cosmetic id argument`() {
        val text = UiMessage.RewardUnavailable("zig_jade").resolve(context)
        assertTrue("must interpolate the cosmetic id", text.contains("zig_jade"))
    }

    @Test
    fun `Raw returns its verbatim text`() {
        assertEquals("verbatim", UiMessage.Raw("verbatim").resolve(context))
    }
}
