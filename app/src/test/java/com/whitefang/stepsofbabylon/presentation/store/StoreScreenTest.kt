package com.whitefang.stepsofbabylon.presentation.store

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeBillingManager
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #253 — Compose UI tests for [StoreScreen]. Renders the real composable backed by a
 * fake-wired [StoreViewModel] (no Hilt graph) on the Robolectric/JVM unit-test lane.
 * Verifies gem balance display, section headers, Buy buttons, and ad-removed state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class StoreScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var billingManager: FakeBillingManager
    private lateinit var cosmeticRepo: FakeCosmeticRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(
        gems: Long = 0,
        adRemoved: Boolean = false,
    ): StoreViewModel {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = gems, adRemoved = adRemoved))
        billingManager = FakeBillingManager()
        cosmeticRepo = FakeCosmeticRepository()
        return StoreViewModel(playerRepo, billingManager, cosmeticRepo)
    }

    @Test
    fun `renders gem balance and section headers`() {
        val viewModel = createVm(gems = 500)

        composeRule.setContent { StoreScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("500").assertExists()
        composeRule.onNodeWithText("Gem Packs").assertExists()
        composeRule.onNodeWithText("Premium").assertExists()
        // "Cosmetics" is below the initial viewport in the LazyColumn — scroll to compose it.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Cosmetics"))
        composeRule.onNodeWithText("Cosmetics").assertExists()
    }

    @Test
    fun `Buy buttons present for gem packs`() {
        val viewModel = createVm(gems = 100)

        composeRule.setContent { StoreScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        val buyNodes = composeRule.onAllNodesWithText("Buy")
        buyNodes[0].assertExists()
    }

    @Test
    fun `ad-removed state shows Purchased`() {
        val viewModel = createVm(gems = 0, adRemoved = true)

        composeRule.setContent { StoreScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Purchased").assertExists()
    }
}
