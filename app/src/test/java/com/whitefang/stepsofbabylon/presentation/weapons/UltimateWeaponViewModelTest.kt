package com.whitefang.stepsofbabylon.presentation.weapons

import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeUltimateWeaponRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UltimateWeaponViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var uwRepo: FakeUltimateWeaponRepository
    private lateinit var playerRepo: FakePlayerRepository

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        uwRepo = FakeUltimateWeaponRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 500))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = UltimateWeaponViewModel(uwRepo, playerRepo)

    @Test
    fun `displays all 6 UW types`() =
        runTest(dispatcher) {
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(6, vm.uiState.value.weapons.size)
        }

    @Test
    fun `unlocked weapon shows as unlocked with 3 paths`() =
        runTest(dispatcher) {
            uwRepo.weapons.value =
                mapOf(
                    UltimateWeaponType.DEATH_WAVE to
                        OwnedWeapon(
                            UltimateWeaponType.DEATH_WAVE,
                            damageLevel = 2,
                            secondaryLevel = 1,
                            cooldownLevel = 0,
                            isUnlocked = true,
                            isEquipped = false,
                        ),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            val dw =
                vm.uiState.value.weapons
                    .find { it.type == UltimateWeaponType.DEATH_WAVE }!!
            assertTrue(dw.isUnlocked)
            assertEquals(3, dw.paths.size)
            assertEquals(2, dw.paths[UWPath.DAMAGE]!!.level)
            assertEquals(1, dw.paths[UWPath.SECONDARY]!!.level)
            assertEquals(0, dw.paths[UWPath.COOLDOWN]!!.level)
        }

    @Test
    fun `locked weapon shows not unlocked with empty paths`() =
        runTest(dispatcher) {
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            val first =
                vm.uiState.value.weapons
                    .first()
            assertFalse(first.isUnlocked)
            assertTrue(first.paths.isEmpty())
        }

    @Test
    fun `power stones balance shown`() =
        runTest(dispatcher) {
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(500, vm.uiState.value.powerStones)
        }

    @Test
    fun `per-path canAfford reflects wallet`() =
        runTest(dispatcher) {
            // DEATH_WAVE costForPath(5) = 50*2*5 = 500. Wallet = 500 → can afford.
            uwRepo.weapons.value =
                mapOf(
                    UltimateWeaponType.DEATH_WAVE to
                        OwnedWeapon(
                            UltimateWeaponType.DEATH_WAVE,
                            damageLevel = 5,
                            isUnlocked = true,
                        ),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            val dw =
                vm.uiState.value.weapons
                    .find { it.type == UltimateWeaponType.DEATH_WAVE }!!
            assertTrue(dw.paths[UWPath.DAMAGE]!!.canAfford)
        }

    @Test
    fun `maxed path shows isMaxed`() =
        runTest(dispatcher) {
            uwRepo.weapons.value =
                mapOf(
                    UltimateWeaponType.DEATH_WAVE to
                        OwnedWeapon(
                            UltimateWeaponType.DEATH_WAVE,
                            damageLevel = 10,
                            isUnlocked = true,
                        ),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            val dw =
                vm.uiState.value.weapons
                    .find { it.type == UltimateWeaponType.DEATH_WAVE }!!
            assertTrue(dw.paths[UWPath.DAMAGE]!!.isMaxed)
        }

    // #154: a maxed path must report canAfford == false even with a huge balance — the screen
    // uses canAfford to drive the upgrade button's `enabled`, so this is what disables it at cap.
    @Test
    fun `R154 maxed path is not affordable even with a huge balance`() =
        runTest(dispatcher) {
            playerRepo.profile.value = PlayerProfile(powerStones = Long.MAX_VALUE)
            uwRepo.weapons.value =
                mapOf(
                    UltimateWeaponType.DEATH_WAVE to
                        OwnedWeapon(
                            UltimateWeaponType.DEATH_WAVE,
                            damageLevel = UltimateWeaponType.MAX_PATH_LEVEL,
                            secondaryLevel = UltimateWeaponType.MAX_PATH_LEVEL,
                            cooldownLevel = UltimateWeaponType.MAX_PATH_LEVEL,
                            isUnlocked = true,
                        ),
                )
            val vm = createVm()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            val dw =
                vm.uiState.value.weapons
                    .find { it.type == UltimateWeaponType.DEATH_WAVE }!!
            for (path in UWPath.ALL) {
                assertTrue(dw.paths[path]!!.isMaxed, "$path should be maxed")
                assertFalse(
                    dw.paths[path]!!.canAfford,
                    "$path at cap must NOT be affordable even with MAX_VALUE balance",
                )
            }
        }
}
