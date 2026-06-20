package com.whitefang.stepsofbabylon.presentation.cards

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.usecase.PackTier
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeRewardAdManager
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
 * #253 (Compose-UI-test beachhead) — the first Compose UI tests in the project. Renders the real
 * [CardsScreen] composable backed by a fake-wired [CardsViewModel] (no Hilt graph) on the
 * Robolectric/JVM unit-test lane (the PR gate), and asserts the currency/claim affordances the
 * 2026-06-18 audit flagged as entirely unverified at the UI layer.
 *
 * Wiring notes (see the wave spec/plan):
 * - `createComposeRule()` runs under Robolectric with `@GraphicsMode(NATIVE)`; `ui-test-manifest`
 *   (on `debugImplementation`) supplies the host `ComponentActivity`.
 * - main = `UnconfinedTestDispatcher` so `viewModelScope` + `WhileSubscribed(5000)` collection run
 *   eagerly; `waitForIdle()` then lets the composition observe the seeded fake state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class CardsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var cardRepo: FakeCardRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var adManager: FakeRewardAdManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        cardRepo = FakeCardRepository()
        adManager = FakeRewardAdManager()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CardsViewModel(cardRepo, playerRepo, adManager)

    @Test
    fun `renders gem balance and owned-card count`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 250))
        cardRepo.cards.value = listOf(
            OwnedCard(1, CardType.SHARP_SHOOTER, 1, false),
            OwnedCard(2, CardType.IRON_SKIN, 1, false),
        )
        val viewModel = vm()

        composeRule.setContent { CardsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2 cards").assertExists()
        composeRule.onNodeWithText("250").assertExists()
    }

    @Test
    fun `shows 3 of 3 cap message when three cards are equipped`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 0))
        cardRepo.cards.value = listOf(
            OwnedCard(1, CardType.SHARP_SHOOTER, 1, true),
            OwnedCard(2, CardType.IRON_SKIN, 1, true),
            OwnedCard(3, CardType.GLASS_CANNON, 1, true),
        )
        val viewModel = vm()

        composeRule.setContent { CardsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Equipped: 3/3 — unequip one to swap").assertExists()
    }

    @Test
    fun `pack button is disabled when the player cannot afford it`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 0))
        val viewModel = vm()

        composeRule.setContent { CardsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        // COMMON is the cheapest tier; with 0 gems every pack is unaffordable → disabled.
        composeRule.onNodeWithText(PackTier.COMMON.name).assertIsNotEnabled()
    }

    @Test
    fun `affordable pack button is enabled and opening it grants a card`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 100_000))
        val viewModel = vm()

        composeRule.setContent { CardsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(PackTier.COMMON.name).assertIsEnabled()
        composeRule.onNodeWithText(PackTier.COMMON.name).performClick()
        composeRule.waitForIdle()

        // openPack routed through the VM → the fake repo opened a pack (granting ≥1 card).
        assert(cardRepo.cards.value.isNotEmpty()) { "opening an affordable pack should grant at least one card" }
    }
}
