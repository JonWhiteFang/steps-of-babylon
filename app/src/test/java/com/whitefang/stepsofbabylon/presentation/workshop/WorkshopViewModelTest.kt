package com.whitefang.stepsofbabylon.presentation.workshop

import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var workshopRepo: FakeWorkshopRepository
    private lateinit var playerRepo: FakePlayerRepository
    private val dailyMissionDao = mock<com.whitefang.stepsofbabylon.data.local.DailyMissionDao>()

    @BeforeEach
    fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 10_000))
        workshopRepo = FakeWorkshopRepository(linkedPlayer = playerRepo)
        workshopRepo.upgrades.value = UpgradeType.entries.associateWith { 0 }
        whenever(dailyMissionDao.getByDateOnce(org.mockito.kotlin.any())).thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun createVm() = WorkshopViewModel(workshopRepo, playerRepo, dailyMissionDao)

    @Test
    fun `initial state shows ATTACK upgrades`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(UpgradeCategory.ATTACK, state.selectedCategory)
        assertTrue(state.upgrades.all { it.type.category == UpgradeCategory.ATTACK })
        assertTrue(state.upgrades.isNotEmpty())
    }

    @Test
    fun `R402b MULTISHOT and BOUNCE_SHOT are filtered out of Workshop UI`() = runTest(dispatcher) {
        // Post-R4-02b: MULTISHOT and BOUNCE_SHOT are `isWorkshopVisible = false`. They remain
        // in the in-round upgrade menu (Cash) and in Labs (Steps research) but must not appear
        // on the permanent-Steps Workshop screen. WorkshopViewModel filters by the flag in
        // addition to the legacy `hiddenUpgrades` set.
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val attackTypes = vm.uiState.value.upgrades.map { it.type }.toSet()
        assertTrue(
            UpgradeType.MULTISHOT !in attackTypes,
            "MULTISHOT must not appear in Workshop ATTACK list (R4-02b: in-round Cash + Labs research only)",
        )
        assertTrue(
            UpgradeType.BOUNCE_SHOT !in attackTypes,
            "BOUNCE_SHOT must not appear in Workshop ATTACK list (R4-02b: in-round Cash + Labs research only)",
        )
        // Sanity: the other 7 ATTACK upgrades still appear (DAMAGE / ATTACK_SPEED /
        // CRITICAL_CHANCE / CRITICAL_FACTOR / RANGE / DAMAGE_PER_METER / RAPID_FIRE).
        assertEquals(
            7,
            attackTypes.size,
            "Workshop ATTACK list should contain 7 upgrades post-R4-03 (was 6 in R4-02b, 8 in R4-02)",
        )
    }

    @Test
    fun `category switching filters correctly`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectCategory(UpgradeCategory.DEFENSE)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(UpgradeCategory.DEFENSE, state.selectedCategory)
        assertTrue(state.upgrades.all { it.type.category == UpgradeCategory.DEFENSE })
    }

    @Test
    fun `purchase deducts steps and increments level`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val upgrade = vm.uiState.value.upgrades.first()
        val costBefore = upgrade.cost
        vm.purchase(upgrade.type)
        advanceUntilIdle()
        val state = vm.uiState.value
        val after = state.upgrades.find { it.type == upgrade.type }!!
        assertEquals(1, after.level)
        assertEquals(10_000 - costBefore, state.stepBalance)
    }

    @Test
    fun `purchase when unaffordable is no-op`() = runTest(dispatcher) {
        playerRepo.profile.value = PlayerProfile(stepBalance = 0)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val upgrade = vm.uiState.value.upgrades.first()
        vm.purchase(upgrade.type)
        advanceUntilIdle()
        val after = vm.uiState.value.upgrades.find { it.type == upgrade.type }!!
        assertEquals(0, after.level)
    }

    @Test
    fun `step balance shown`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(10_000, vm.uiState.value.stepBalance)
    }

    @Test
    fun `maxed upgrade shows isMaxed`() = runTest(dispatcher) {
        val typeWithMax = UpgradeType.entries.first { it.config.maxLevel != null }
        workshopRepo.upgrades.value = workshopRepo.upgrades.value + (typeWithMax to typeWithMax.config.maxLevel!!)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.selectCategory(typeWithMax.category)
        advanceUntilIdle()
        val item = vm.uiState.value.upgrades.find { it.type == typeWithMax }!!
        assertTrue(item.isMaxed)
    }

    // #154: at cap, the buy control must be disabled (canAfford == false) regardless of how much
    // currency the player has — the UI uses canAfford to drive `enabled`, so an unaffordable-at-cap
    // flag is what makes the button both un-clickable AND visually disabled. Visible-on-Workshop
    // capped types (ORBS is the issue's headline example) must all satisfy this.
    @Test
    fun `R154 maxed upgrade is not affordable even with a huge balance`() = runTest(dispatcher) {
        playerRepo.profile.value = PlayerProfile(stepBalance = Long.MAX_VALUE)
        // ORBS (cap 6) is the issue's headline example; assert it specifically plus every
        // Workshop-visible capped type for the "future capped item inherits it" guarantee.
        val cappedVisible = UpgradeType.entries.filter {
            it.config.maxLevel != null && it.isWorkshopVisible &&
                it != UpgradeType.STEP_MULTIPLIER && it != UpgradeType.RECOVERY_PACKAGES
        }
        // max out every capped visible upgrade
        workshopRepo.upgrades.value = workshopRepo.upgrades.value +
            cappedVisible.associateWith { it.config.maxLevel!! }
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(cappedVisible.any { it == UpgradeType.ORBS }, "ORBS must be a Workshop-visible capped type")
        for (type in cappedVisible) {
            vm.selectCategory(type.category)
            advanceUntilIdle()
            val item = vm.uiState.value.upgrades.find { it.type == type }!!
            assertTrue(item.isMaxed, "${type.name} should be maxed")
            assertFalse(item.canAfford, "${type.name} at cap must NOT be affordable (drives disabled buy control) even with MAX_VALUE balance")
        }
    }

    // #154: purchasing a maxed upgrade must be a true no-op (no spend, level unchanged) — the
    // state contract above stops the click, this guards the spend path behind it.
    @Test
    fun `R154 purchasing a maxed upgrade does not spend or change level`() = runTest(dispatcher) {
        playerRepo.profile.value = PlayerProfile(stepBalance = Long.MAX_VALUE)
        val orbs = UpgradeType.ORBS
        workshopRepo.upgrades.value = workshopRepo.upgrades.value + (orbs to orbs.config.maxLevel!!)
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val balanceBefore = playerRepo.profile.value.stepBalance
        vm.purchase(orbs)
        advanceUntilIdle()
        assertEquals(orbs.config.maxLevel, workshopRepo.upgrades.value[orbs], "maxed level must be unchanged")
        assertEquals(balanceBefore, playerRepo.profile.value.stepBalance, "no Steps spent at cap")
    }
}
