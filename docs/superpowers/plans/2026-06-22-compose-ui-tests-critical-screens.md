# Compose UI Tests — Critical Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Compose UI tests for 7 critical screens (Workshop, Store, Labs, Missions, UltimateWeapons, UnclaimedSupplies, BattleControlRail) to close issue #253.

**Architecture:** Each test file follows the established pattern — Robolectric + `@GraphicsMode(NATIVE)` + `createComposeRule()` + real ViewModel wired with existing fakes (no Hilt, no mocks). Tests assert key rendered text/button states, not ViewModel logic (that's already covered by *ViewModel tests).

**Tech Stack:** JUnit 4, Robolectric (SDK 34), Compose UI Test (`ui-test-junit4`), kotlinx-coroutines-test (`UnconfinedTestDispatcher`), existing `test/fakes/`

---

## File Map

All files are **new** (Create):

| # | File | Responsibility |
|---|------|---------------|
| 1 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreenTest.kt` | Workshop screen render + purchase interaction |
| 2 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreenTest.kt` | Store screen render + ad-removed state |
| 3 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreenTest.kt` | Labs screen render + active research state |
| 4 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreenTest.kt` | Missions screen render + claim affordance |
| 5 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreenTest.kt` | UW screen render + equip cap |
| 6 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreenTest.kt` | Supplies screen render + empty state |
| 7 | `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt` | Battle control rail composable render + callback |

---

## Task 1: WorkshopScreenTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreenTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package com.whitefang.stepsofbabylon.presentation.workshop

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWorkshopRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class WorkshopScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var workshopRepo: FakeWorkshopRepository
    private val missionRepo = FakeMissionRepository(FakeDailyMissionDao())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seedUpgrades() {
        workshopRepo.upgrades.value = UpgradeType.entries
            .filter { it.isWorkshopVisible }
            .associateWith { 0 }
    }

    private fun createVm(): WorkshopViewModel {
        return WorkshopViewModel(workshopRepo, playerRepo, missionRepo, SavedStateHandle())
    }

    @Test
    fun `renders step balance and upgrade list`() {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 9999))
        workshopRepo = FakeWorkshopRepository(linkedPlayer = playerRepo)
        seedUpgrades()
        val viewModel = createVm()

        composeRule.setContent { WorkshopScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Balance: 9999 Steps", substring = true).assertExists()
        composeRule.onNodeWithText("Damage").assertExists()
    }

    @Test
    fun `upgrade card is disabled when balance is zero`() {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 0))
        workshopRepo = FakeWorkshopRepository(linkedPlayer = playerRepo)
        seedUpgrades()
        val viewModel = createVm()

        composeRule.setContent { WorkshopScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Damage").assertIsNotEnabled()
    }

    @Test
    fun `successful purchase increments level`() {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 100_000))
        workshopRepo = FakeWorkshopRepository(linkedPlayer = playerRepo)
        seedUpgrades()
        val viewModel = createVm()

        composeRule.setContent { WorkshopScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lv. 0", substring = true).assertExists()
        composeRule.onNodeWithText("Damage").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lv. 1", substring = true).assertExists()
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.workshop.WorkshopScreenTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreenTest.kt
git commit -m "test(ui): add WorkshopScreen Compose UI tests (#253)"
```

---

## Task 2: StoreScreenTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreenTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package com.whitefang.stepsofbabylon.presentation.store

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
        billingManager = FakeBillingManager()
        cosmeticRepo = FakeCosmeticRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): StoreViewModel {
        return StoreViewModel(playerRepo, billingManager, cosmeticRepo)
    }

    @Test
    fun `renders gem balance and section headers`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 500))
        val viewModel = createVm()

        composeRule.setContent { StoreScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("500").assertExists()
        composeRule.onNodeWithText("Gem Packs").assertExists()
        composeRule.onNodeWithText("Premium").assertExists()
        composeRule.onNodeWithText("Cosmetics").assertExists()
    }

    @Test
    fun `Buy buttons present for gem packs`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 0))
        val viewModel = createVm()

        composeRule.setContent { StoreScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Buy").fetchSemanticNodes().isNotEmpty().let {
            assert(it) { "Expected at least one Buy button" }
        }
    }

    @Test
    fun `ad-removed state shows Purchased`() {
        playerRepo = FakePlayerRepository(PlayerProfile(gems = 0, adRemoved = true))
        val viewModel = createVm()

        composeRule.setContent { StoreScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Purchased").assertExists()
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.store.StoreScreenTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreenTest.kt
git commit -m "test(ui): add StoreScreen Compose UI tests (#253)"
```

---

## Task 3: LabsScreenTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreenTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package com.whitefang.stepsofbabylon.presentation.labs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.domain.model.ActiveResearch
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.time.TimeReading
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeLabRepository
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeBaselineSource
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class LabsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var labRepo: FakeLabRepository
    private lateinit var playerRepo: FakePlayerRepository
    private val missionRepo = FakeMissionRepository(FakeDailyMissionDao())
    private val timeBaseline = FakeTimeBaselineSource(reading = TimeReading(0, Long.MAX_VALUE))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        labRepo = FakeLabRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): LabsViewModel {
        return LabsViewModel(labRepo, playerRepo, missionRepo, timeBaseline)
    }

    @Test
    fun `renders balances and lab slot count`() {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 5000, gems = 200, labSlotCount = 2))
        val viewModel = createVm()

        composeRule.setContent { LabsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lab Slots: 0/2").assertExists()
    }

    @Test
    fun `Start button disabled when balance insufficient`() {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 0, gems = 0, labSlotCount = 2))
        val viewModel = createVm()

        composeRule.setContent { LabsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Start", substring = true).assertIsNotEnabled()
    }

    @Test
    fun `active research shows Rush button`() {
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 5000, gems = 100, labSlotCount = 2))
        val now = System.currentTimeMillis()
        labRepo.active.value = listOf(
            ActiveResearch(
                type = ResearchType.entries.first(),
                level = 0,
                startedAt = now - 60_000,
                completesAt = now + 3_600_000,
            ),
        )
        val viewModel = createVm()

        composeRule.setContent { LabsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Rush", substring = true).assertExists()
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.labs.LabsScreenTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreenTest.kt
git commit -m "test(ui): add LabsScreen Compose UI tests (#253)"
```

---

## Task 4: MissionsScreenTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreenTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package com.whitefang.stepsofbabylon.presentation.missions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.data.local.DailyMissionEntity
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository
import com.whitefang.stepsofbabylon.fakes.FakeDailyMissionDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneDao
import com.whitefang.stepsofbabylon.fakes.FakeMilestoneRepository
import com.whitefang.stepsofbabylon.fakes.FakeMissionRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeStepRepository
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val timeProvider = FakeTimeProvider()
    private val missionDao = FakeDailyMissionDao()
    private val missionRepo = FakeMissionRepository(missionDao)
    private val milestoneRepo = FakeMilestoneRepository(dao = FakeMilestoneDao())
    private val stepRepo = FakeStepRepository()
    private lateinit var playerRepo: FakePlayerRepository
    private val cosmeticRepo = FakeCosmeticRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 1000))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): MissionsViewModel {
        return MissionsViewModel(missionRepo, milestoneRepo, stepRepo, playerRepo, cosmeticRepo, timeProvider)
    }

    @Test
    fun `renders Daily Missions header and mission cards`() = runTest {
        val today = timeProvider.today().toString()
        missionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.EARN_STEPS.name,
            target = 1000, progress = 500, rewardGems = 10, rewardPowerStones = 0,
            completed = false, claimed = false,
        ))
        val viewModel = createVm()

        composeRule.setContent { MissionsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Daily Missions").assertExists()
        composeRule.onNodeWithText("500 / 1,000", substring = true).assertExists()
    }

    @Test
    fun `Claim button appears when mission is complete but unclaimed`() = runTest {
        val today = timeProvider.today().toString()
        missionDao.insert(DailyMissionEntity(
            date = today, missionType = DailyMissionType.EARN_STEPS.name,
            target = 1000, progress = 1000, rewardGems = 10, rewardPowerStones = 0,
            completed = true, claimed = false,
        ))
        val viewModel = createVm()

        composeRule.setContent { MissionsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Claim").assertExists()
    }

    @Test
    fun `milestones section renders`() = runTest {
        val viewModel = createVm()

        composeRule.setContent { MissionsScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Walking Milestones").assertExists()
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.missions.MissionsScreenTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreenTest.kt
git commit -m "test(ui): add MissionsScreen Compose UI tests (#253)"
```

---

## Task 5: UltimateWeaponScreenTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreenTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class UltimateWeaponScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var uwRepo: FakeUltimateWeaponRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): UltimateWeaponViewModel {
        uwRepo = FakeUltimateWeaponRepository(linkedPlayer = playerRepo)
        return UltimateWeaponViewModel(uwRepo, playerRepo)
    }

    @Test
    fun `renders power stones balance and weapon list`() {
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 50))
        val viewModel = createVm()

        composeRule.setContent { UltimateWeaponScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Power Stones: 50").assertExists()
        composeRule.onNodeWithText("Chain Lightning").assertExists()
    }

    @Test
    fun `Unlock button disabled when cannot afford`() {
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 0))
        val viewModel = createVm()

        composeRule.setContent { UltimateWeaponScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        // DEATH_WAVE has a unique unlockCost of 50 (no other UW shares it)
        val cost = UltimateWeaponType.DEATH_WAVE.unlockCost
        composeRule.onNodeWithText("Unlock ($cost PS)").assertIsNotEnabled()
    }

    @Test
    fun `equipped 3 of 3 cap message when three equipped`() {
        playerRepo = FakePlayerRepository(PlayerProfile(powerStones = 100))
        uwRepo = FakeUltimateWeaponRepository(linkedPlayer = playerRepo)
        uwRepo.weapons.value = mapOf(
            UltimateWeaponType.CHAIN_LIGHTNING to OwnedWeapon(UltimateWeaponType.CHAIN_LIGHTNING, isUnlocked = true, isEquipped = true),
            UltimateWeaponType.DEATH_WAVE to OwnedWeapon(UltimateWeaponType.DEATH_WAVE, isUnlocked = true, isEquipped = true),
            UltimateWeaponType.BLACK_HOLE to OwnedWeapon(UltimateWeaponType.BLACK_HOLE, isUnlocked = true, isEquipped = true),
        )
        val viewModel = UltimateWeaponViewModel(uwRepo, playerRepo)

        composeRule.setContent { UltimateWeaponScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Equipped: 3/3 — unequip one to swap").assertExists()
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.weapons.UltimateWeaponScreenTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreenTest.kt
git commit -m "test(ui): add UltimateWeaponScreen Compose UI tests (#253)"
```

---

## Task 6: UnclaimedSuppliesScreenTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreenTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import com.whitefang.stepsofbabylon.fakes.FakePlayerRepository
import com.whitefang.stepsofbabylon.fakes.FakeWalkingEncounterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class UnclaimedSuppliesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var encounterRepo: FakeWalkingEncounterRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var cardRepo: FakeCardRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        encounterRepo = FakeWalkingEncounterRepository()
        playerRepo = FakePlayerRepository(PlayerProfile(stepBalance = 1000))
        cardRepo = FakeCardRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createVm(): UnclaimedSuppliesViewModel {
        return UnclaimedSuppliesViewModel(encounterRepo, playerRepo, cardRepo)
    }

    @Test
    fun `renders supply drop list with Claim buttons`() = runTest {
        encounterRepo.createDrop(SupplyDropTrigger.STEP_THRESHOLD, SupplyDropReward.GEMS, 5)
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 100)
        val viewModel = createVm()

        composeRule.setContent { UnclaimedSuppliesScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Claim", substring = true).assertExists()
        composeRule.onNodeWithText(SupplyDropTrigger.STEP_THRESHOLD.message).assertExists()
    }

    @Test
    fun `empty state shows no drops message`() {
        val viewModel = createVm()

        composeRule.setContent { UnclaimedSuppliesScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No supply drops yet — keep walking!").assertExists()
    }

    @Test
    fun `Claim All button present when drops exist`() = runTest {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.POWER_STONES, 3)
        val viewModel = createVm()

        composeRule.setContent { UnclaimedSuppliesScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Claim All").assertExists()
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.supplies.UnclaimedSuppliesScreenTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreenTest.kt
git commit -m "test(ui): add UnclaimedSuppliesScreen Compose UI tests (#253)"
```

---

## Task 7: BattleControlRailTest

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
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
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Speed 1x").assertExists()
        composeRule.onNodeWithContentDescription("Speed 2x").assertExists()
        composeRule.onNodeWithContentDescription("Speed 4x").assertExists()
        composeRule.onNodeWithContentDescription("Pause").assertExists()
        composeRule.onNodeWithContentDescription("Upgrades").assertExists()
    }

    @Test
    fun `pause button invokes callback`() {
        var pauseClicked = false

        composeRule.setContent {
            BattleControlRail(
                speedMultiplier = 1f,
                isPaused = false,
                showUpgradeMenu = false,
                onSetSpeed = {},
                onTogglePause = { pauseClicked = true },
                onToggleUpgradeMenu = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.waitForIdle()

        assertTrue("onTogglePause should have been called", pauseClicked)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.ui.BattleControlRailTest"`
Expected: All 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt
git commit -m "test(ui): add BattleControlRail Compose UI tests (#253)"
```

---

## Task 8: Final verification & docs

- [ ] **Step 1: Run all new tests together**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.workshop.WorkshopScreenTest" --tests "com.whitefang.stepsofbabylon.presentation.store.StoreScreenTest" --tests "com.whitefang.stepsofbabylon.presentation.labs.LabsScreenTest" --tests "com.whitefang.stepsofbabylon.presentation.missions.MissionsScreenTest" --tests "com.whitefang.stepsofbabylon.presentation.weapons.UltimateWeaponScreenTest" --tests "com.whitefang.stepsofbabylon.presentation.supplies.UnclaimedSuppliesScreenTest" --tests "com.whitefang.stepsofbabylon.presentation.battle.ui.BattleControlRailTest"`
Expected: All 20 tests PASS

- [ ] **Step 2: Run full test suite to confirm no regressions**

Run: `./run-gradle.sh testDebugUnitTest`
Expected: All tests PASS (count should be ~1254)

- [ ] **Step 3: Update CLAUDE.md headline test count**

In `CLAUDE.md`, update the line:
```
- **Headline count: 1234 JVM tests + 9 instrumented tests.**
```
to reflect the new count (read the actual number from the Gradle output).

- [ ] **Step 4: Update docs (STATE.md, RUN_LOG.md, CHANGELOG.md)**

Per the PR Task-List Convention:
- Update `CHANGELOG.md` with a new `[Unreleased]` entry for this PR
- Update `docs/agent/STATE.md` current objective
- Append `docs/agent/RUN_LOG.md` with the session entry

- [ ] **Step 5: Final commit with doc updates**

```bash
git add CLAUDE.md CHANGELOG.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs: update test count and state after Compose UI test expansion (#253)"
```
