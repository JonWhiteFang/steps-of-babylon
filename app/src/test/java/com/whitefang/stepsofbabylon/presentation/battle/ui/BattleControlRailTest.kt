package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class BattleControlRailTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders speed buttons and highlights current speed`() {
        composeRule.setContent {
            BattleControlRail(
                speedMultiplier = 2f,
                isPaused = false,
                showUpgradeMenu = false,
                onSetSpeed = {},
                onTogglePause = {},
                onToggleUpgradeMenu = {},
            )
        }

        composeRule.onNodeWithContentDescription("Speed 1x").assertExists()
        composeRule.onNodeWithContentDescription("Speed 2x").assertExists()
        composeRule.onNodeWithContentDescription("Speed 4x").assertExists()
        composeRule.onNodeWithContentDescription("Pause").assertExists()
        composeRule.onNodeWithContentDescription("Upgrades").assertExists()
    }

    @Test
    fun `pause button invokes callback`() {
        var pauseInvoked = false
        composeRule.setContent {
            BattleControlRail(
                speedMultiplier = 1f,
                isPaused = false,
                showUpgradeMenu = false,
                onSetSpeed = {},
                onTogglePause = { pauseInvoked = true },
                onToggleUpgradeMenu = {},
            )
        }

        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertTrue(pauseInvoked)
    }
}
