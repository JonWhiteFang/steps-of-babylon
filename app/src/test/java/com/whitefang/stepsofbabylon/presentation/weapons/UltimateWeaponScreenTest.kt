package com.whitefang.stepsofbabylon.presentation.weapons

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeUltimateWeaponRepository
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
 * #253 — Compose UI tests for [UltimateWeaponScreen]. Renders the real composable backed by a
 * fake-wired [UltimateWeaponViewModel] (no Hilt graph) on the Robolectric/JVM unit-test lane.
 *
 * Wiring: `createComposeRule()` under Robolectric with `@GraphicsMode(NATIVE)`;
 * `ui-test-manifest` (on `debugImplementation`) supplies the host `ComponentActivity`.
 * main = `UnconfinedTestDispatcher` so `viewModelScope` + `WhileSubscribed(5000)` collection
 * run eagerly; `waitForIdle()` then lets the composition observe the seeded fake state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class UltimateWeaponScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var uwRepo: FakeUltimateWeaponRepository
    private lateinit var playerRepo: FakePlayerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        uwRepo = FakeUltimateWeaponRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = UltimateWeaponViewModel(uwRepo, playerRepo)

    @Test
    fun `renders power stones balance and weapon list`() {
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 50))
        val viewModel = vm()

        composeRule.setContent { UltimateWeaponScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Power Stones: 50").assertExists()
        composeRule.onNodeWithText("Chain Lightning").assertExists()
    }

    @Test
    fun `unlock button disabled when cannot afford`() {
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 0))
        val viewModel = vm()

        composeRule.setContent { UltimateWeaponScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        // DEATH_WAVE has unlockCost=50 (unique among UWs), so "Unlock (50 PS)" matches one node.
        composeRule.onNodeWithText("Unlock (50 PS)").assertIsNotEnabled()
    }

    @Test
    fun `equipped 3 of 3 cap message when three equipped`() {
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 100))
        uwRepo.weapons.value = mapOf(
            UltimateWeaponType.DEATH_WAVE to OwnedWeapon(
                type = UltimateWeaponType.DEATH_WAVE, isUnlocked = true, isEquipped = true,
            ),
            UltimateWeaponType.CHAIN_LIGHTNING to OwnedWeapon(
                type = UltimateWeaponType.CHAIN_LIGHTNING, isUnlocked = true, isEquipped = true,
            ),
            UltimateWeaponType.BLACK_HOLE to OwnedWeapon(
                type = UltimateWeaponType.BLACK_HOLE, isUnlocked = true, isEquipped = true,
            ),
        )
        val viewModel = vm()

        composeRule.setContent { UltimateWeaponScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Equipped: 3/3 — unequip one to swap").assertExists()
    }
}
