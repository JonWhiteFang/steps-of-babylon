# Process-death state survival (#234) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the four genuinely-lost, reconstructible UI-state surfaces survive Android process death — Workshop selected tab + Stats selected period (`SavedStateHandle`), Cards pack-reveal payload (presentation-layer `@Parcelize` DTO in `SavedStateHandle`), and `permissionAsked` (`rememberSaveable`) — without violating the domain-purity invariant.

**Architecture:** The three affected ViewModels are `@HiltViewModel`, so Hilt auto-provides a `SavedStateHandle` to their constructors (no factory/module change). The two `_selected*` flows are `combine` sources, so `SavedStateHandle.getStateFlow(key, default)` is a drop-in. Domain `CardResult`/`CardType` stay pure; a presentation `@Parcelize` DTO (`PackRevealState`) is the SavedStateHandle serialization layer, mapped to/from `List<CardResult>` at the boundary so the screen + uiState type are unchanged. `permissionAsked` becomes `rememberSaveable`.

**Tech Stack:** Kotlin, androidx.lifecycle `SavedStateHandle` 2.11.0 (pure in-memory path — JVM-testable, verified by AAR decompile in the spec review), Compose `rememberSaveable`, `kotlin-parcelize` plugin, JUnit Jupiter (plain JVM lane), Hilt. Build via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-22-process-death-state-survival-234.md` (adversarially reviewed: 25 findings → 15 surviving, applied).

---

## Guiding rules

- **Build wrapper is `./run-gradle.sh`** (NOT ./gradlew). Output is large — redirect to a temp file and tail/grep.
- **TDD per surface:** failing test → minimal change → green. The "process-death simulation" test = construct a fresh VM from a pre-seeded `SavedStateHandle` and assert restore.
- **Domain purity is sacrosanct:** `DomainPurityTest` must stay green. The `@Parcelize` DTO lives in `presentation/cards/`; domain `CardResult`/`CardType` are NOT touched.
- **No behavior change for the selections:** `getStateFlow` is a `StateFlow<T>` with the same default; the `combine` downstream is unchanged.
- **Commit after each task.** Already on branch `fix/234-process-death-state-survival` (created at spec stage).
- All VM tests run on the plain JVM lane (`unitTests.isReturnDefaultValues = true`), no Robolectric.

---

## File structure

| File | Change | Responsibility |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | add `kotlin-parcelize` plugin alias |
| `app/build.gradle.kts` | Modify | apply `kotlin-parcelize` plugin |
| `presentation/workshop/WorkshopViewModel.kt` | Modify | `_selectedCategory` → `SavedStateHandle.getStateFlow` |
| `presentation/stats/StatsViewModel.kt` | Modify | `_selectedPeriod` → `SavedStateHandle.getStateFlow` |
| `presentation/cards/PackRevealState.kt` | Create | `@Parcelize` DTO + DTO↔`List<CardResult>` mappers |
| `presentation/cards/CardsViewModel.kt` | Modify | `_lastPackResult` → `SavedStateHandle` via DTO |
| `presentation/MainActivity.kt` | Modify | `permissionAsked` `remember` → `rememberSaveable` |
| `presentation/onboarding/OnboardingScreen.kt` | Modify | one-line KDoc tweak ("this session" → "this process instance") |
| `app/.../workshop/WorkshopViewModelTest.kt` | Modify | `createVm(handle=)` seam + restore/save tests |
| `app/.../stats/StatsViewModelTest.kt` | Modify | `createVm(handle=)` seam + restore/save tests |
| `app/.../cards/CardsViewModelTest.kt` | Modify | `createVm(handle=)` seam + reveal round-trip tests |
| `app/.../cards/PackRevealStateTest.kt` | Create | pure DTO↔domain mapping tests |

**Task order:** Task 1 (parcelize plugin + smoke-test the getStateFlow assumption) → Task 2 (Workshop) → Task 3 (Stats) → Task 4 (PackRevealState DTO + tests) → Task 5 (CardsViewModel wiring) → Task 6 (permissionAsked + KDoc) → Task 7 (docs sync) → Task 8 (STATE/RUN_LOG) → Task 9 (final gate + push + PR).

---

### Task 1: Add kotlin-parcelize plugin + smoke-test the getStateFlow assumption

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/architecture/SavedStateHandleSmokeTest.kt` (create, throwaway)

- [ ] **Step 1: Add the plugin alias to the version catalog**

In `gradle/libs.versions.toml`, under `[plugins]`, add (tracking the existing `kotlin` ref — never hardcode):
```toml
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply the plugin in `:app`**

In `app/build.gradle.kts`'s `plugins { }` block, add the alias (alongside the existing `kotlin.compose`):
```kotlin
    alias(libs.plugins.kotlin.parcelize)
```

- [ ] **Step 3: Write the getStateFlow smoke-test (verifies the plain-JVM-lane assumption)**

Create `app/src/test/java/com/whitefang/stepsofbabylon/architecture/SavedStateHandleSmokeTest.kt`:
```kotlin
package com.whitefang.stepsofbabylon.architecture

import androidx.lifecycle.SavedStateHandle
import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * #234: confirms SavedStateHandle.getStateFlow round-trips on the plain JVM lane
 * (unitTests.isReturnDefaultValues=true) — a pure in-memory-map + MutableStateFlow path in
 * lifecycle 2.11.0, no android.os.Bundle. This is the load-bearing assumption behind the
 * seeded-handle "process-death simulation" tests for the migrated ViewModels.
 */
class SavedStateHandleSmokeTest {
    @Test
    fun `getStateFlow restores a seeded enum and reflects writes`() {
        val handle = SavedStateHandle(mapOf("k" to UpgradeCategory.DEFENSE))
        val flow = handle.getStateFlow("k", UpgradeCategory.ATTACK)
        assertEquals(UpgradeCategory.DEFENSE, flow.value, "seeded value must restore")
        handle["k"] = UpgradeCategory.ATTACK
        assertEquals(UpgradeCategory.ATTACK, flow.value, "write must propagate to the flow")
    }
}
```

- [ ] **Step 4: Run the smoke-test + confirm the build still assembles**

Run: `./run-gradle.sh testDebugUnitTest --tests "*SavedStateHandleSmokeTest*" > /tmp/t1.log 2>&1; tail -25 /tmp/t1.log`
Expected: PASS. (If it fails because `SavedStateHandle`/`getStateFlow` hits a stubbed `android.os.Bundle` under `isReturnDefaultValues`, STOP and report — the test strategy needs the #253 Robolectric lane instead. Per the AAR decompile in the spec review this should NOT happen.)
Then: `./run-gradle.sh assembleDebug > /tmp/t1b.log 2>&1; tail -8 /tmp/t1b.log` → BUILD SUCCESSFUL (confirms the parcelize plugin composes with KSP/Hilt/Room).

- [ ] **Step 5: Delete the throwaway smoke-test + commit the plugin**

```bash
cd /Users/jpawhite/Documents/Claude/steps-of-babylon
rm app/src/test/java/com/whitefang/stepsofbabylon/architecture/SavedStateHandleSmokeTest.kt
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add kotlin-parcelize plugin for #234 SavedStateHandle DTOs"
```
(The smoke-test was process insurance for a new-to-repo API; the real coverage is the per-VM restore tests. Deleting it keeps the suite focused.)

---

### Task 2: Workshop selected tab → SavedStateHandle

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModelTest.kt`
- Test (CTOR-SITE FIX): `app/src/test/java/com/whitefang/stepsofbabylon/presentation/ux/UserFeedbackTest.kt` — constructs `WorkshopViewModel(...)` positionally at **lines 42, 55, 70**; the new required ctor param breaks compilation unless updated.

> **Grep guard (run FIRST):** `rg -rn "WorkshopViewModel\(|StatsViewModel\(|CardsViewModel\(" app/src/test app/src/androidTest` — every hit that passes the OLD positional arg count must get a trailing `SavedStateHandle()` (or route through the `createVm` helper). For Workshop this is `WorkshopViewModelTest` + the 3 `UserFeedbackTest` sites. `testDebugUnitTest` compiles the WHOLE test source set, so a missed site is a hard compile failure at the build gate, not a silent skip.

- [ ] **Step 0: Fix the external WorkshopViewModel ctor call sites (UserFeedbackTest)**

In `UserFeedbackTest.kt`, the three `val vm = WorkshopViewModel(workshopRepo, playerRepo, missionRepo)` calls (lines 42/55/70) each gain a trailing `SavedStateHandle()`:
```kotlin
        val vm = WorkshopViewModel(workshopRepo, playerRepo, missionRepo, SavedStateHandle())
```
(Add `import androidx.lifecycle.SavedStateHandle` to that file. These tests don't assert restore — they just need to compile; a fresh empty handle defaults `selectedCategory` to `ATTACK`, preserving their behavior.)

- [ ] **Step 1: Refit the test helper + write the failing restore/save tests**

In `WorkshopViewModelTest.kt`, change the `createVm()` helper to accept a handle (find the existing helper — it constructs `WorkshopViewModel(...)` with fakes), making it:
```kotlin
private fun createVm(handle: SavedStateHandle = SavedStateHandle()) =
    WorkshopViewModel(workshopRepo, playerRepo, missionRepo, handle)
```
(Match the actual fake field names already in the test file. Add `import androidx.lifecycle.SavedStateHandle` and `import com.whitefang.stepsofbabylon.domain.model.UpgradeCategory` if absent.)

Add these tests. **Use `runTest(dispatcher)` — NOT bare `runTest {}`** — where `dispatcher` is the
test file's existing `StandardTestDispatcher()` field (the same one `@BeforeEach` passes to
`Dispatchers.setMain(dispatcher)`). This is mandatory: the VM's `uiState` is
`stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), …)` and `viewModelScope` runs on
`Dispatchers.Main` = the field dispatcher's scheduler. A bare `runTest {}` creates its OWN scheduler, so
its `advanceUntilIdle()` would never drive the VM's coroutines — the seeded value would never restore
(you'd observe the `stateIn` initial default) and `viewModelScope.launch` bodies would never run. Use
the file's existing collect+advance pattern — `backgroundScope.launch { vm.uiState.collect {} };
advanceUntilIdle()` — and read `vm.uiState.value` AFTER `advanceUntilIdle()`.
```kotlin
@Test
fun `R234 selected category restores from a seeded SavedStateHandle`() = runTest(dispatcher) {
    val handle = SavedStateHandle(mapOf("selectedCategory" to UpgradeCategory.DEFENSE))
    val vm = createVm(handle)
    backgroundScope.launch { vm.uiState.collect {} }
    advanceUntilIdle()
    assertEquals(UpgradeCategory.DEFENSE, vm.uiState.value.selectedCategory,
        "selected tab must restore from SavedStateHandle (process-death survival)")
}

@Test
fun `R234 selectCategory writes through to the SavedStateHandle`() = runTest(dispatcher) {
    val handle = SavedStateHandle()
    val vm = createVm(handle)
    backgroundScope.launch { vm.uiState.collect {} }
    advanceUntilIdle()
    vm.selectCategory(UpgradeCategory.UTILITY)
    advanceUntilIdle()
    assertEquals(UpgradeCategory.UTILITY, handle["selectedCategory"],
        "selectCategory must persist to SavedStateHandle")
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*WorkshopViewModelTest*" > /tmp/t2.log 2>&1; tail -30 /tmp/t2.log`
Expected: FAIL — `WorkshopViewModel` ctor has no `SavedStateHandle` param (compile error) / `selectedCategory` doesn't restore.

- [ ] **Step 3: Migrate the ViewModel**

In `WorkshopViewModel.kt`:
1. Add the import: `import androidx.lifecycle.SavedStateHandle`.
2. Add the constructor param (last param):
```kotlin
class WorkshopViewModel @Inject constructor(
    private val workshopRepository: WorkshopRepository,
    private val playerRepository: PlayerRepository,
    private val missionRepository: MissionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
```
3. Replace the field (line ~48):
```kotlin
    private val _selectedCategory = MutableStateFlow(UpgradeCategory.ATTACK)
```
with:
```kotlin
    private val selectedCategory: StateFlow<UpgradeCategory> =
        savedStateHandle.getStateFlow(KEY_SELECTED_CATEGORY, UpgradeCategory.ATTACK)
```
4. In the `combine(...)`, change the source `_selectedCategory,` to `selectedCategory,` (the lambda param `category` is unchanged).
5. Change the setter (line ~109):
```kotlin
    fun selectCategory(category: UpgradeCategory) { savedStateHandle[KEY_SELECTED_CATEGORY] = category }
```
6. Add a companion key constant (or top-level `private const val`):
```kotlin
    private companion object { const val KEY_SELECTED_CATEGORY = "selectedCategory" }
```
(If a `companion object` already exists, add the const there.)

- [ ] **Step 4: Run to verify pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "*WorkshopViewModelTest*" > /tmp/t2.log 2>&1; tail -20 /tmp/t2.log`
Expected: PASS (all WorkshopViewModelTest green — existing tests unaffected since `getStateFlow` defaults to `ATTACK`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModel.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopViewModelTest.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/ux/UserFeedbackTest.kt
git commit -m "fix(workshop): selected tab survives process death via SavedStateHandle (#234)"
```

---

### Task 3: Stats selected period → SavedStateHandle

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModelTest.kt`

- [ ] **Step 1: Refit the test helper + write the failing restore/save tests**

In `StatsViewModelTest.kt`, change the VM-construction helper to accept a handle:
```kotlin
private fun createVm(handle: SavedStateHandle = SavedStateHandle()) =
    StatsViewModel(stepRepo, playerRepo, workshopRepo, handle)
```
(Match the actual fake field names. Add `import androidx.lifecycle.SavedStateHandle` + `import com.whitefang.stepsofbabylon.presentation.stats.StatsPeriod` if absent — confirm the `StatsPeriod` enum's package when implementing.)

Add:
```kotlin
@Test
fun `R234 selected period restores from a seeded SavedStateHandle`() = runTest(dispatcher) {
    val handle = SavedStateHandle(mapOf("selectedPeriod" to StatsPeriod.MONTH))
    val vm = createVm(handle)
    backgroundScope.launch { vm.uiState.collect {} }
    advanceUntilIdle()
    assertEquals(StatsPeriod.MONTH, vm.uiState.value.selectedPeriod,
        "selected period must restore from SavedStateHandle (process-death survival)")
}

@Test
fun `R234 selectPeriod writes through to the SavedStateHandle`() = runTest(dispatcher) {
    val handle = SavedStateHandle()
    val vm = createVm(handle)
    backgroundScope.launch { vm.uiState.collect {} }
    advanceUntilIdle()
    vm.selectPeriod(StatsPeriod.MONTH)
    advanceUntilIdle()
    assertEquals(StatsPeriod.MONTH, handle["selectedPeriod"],
        "selectPeriod must persist to SavedStateHandle")
}
```
(Use a non-default `StatsPeriod` value — the default is `WEEK`; confirm `MONTH` exists, else use any non-`WEEK` entry.)

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*StatsViewModelTest*" > /tmp/t3.log 2>&1; tail -30 /tmp/t3.log`
Expected: FAIL (ctor has no `SavedStateHandle`).

- [ ] **Step 3: Migrate the ViewModel**

In `StatsViewModel.kt`:
1. Add `import androidx.lifecycle.SavedStateHandle`.
2. Add the constructor param (last):
```kotlin
class StatsViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val playerRepository: PlayerRepository,
    private val workshopRepository: WorkshopRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
```
3. Replace field (line ~36):
```kotlin
    private val _selectedPeriod = MutableStateFlow(StatsPeriod.WEEK)
```
with:
```kotlin
    private val selectedPeriod: StateFlow<StatsPeriod> =
        savedStateHandle.getStateFlow(KEY_SELECTED_PERIOD, StatsPeriod.WEEK)
```
4. In the inner `combine(...)`, change `_selectedPeriod,` to `selectedPeriod,`.
5. Setter (line ~85):
```kotlin
    fun selectPeriod(period: StatsPeriod) { savedStateHandle[KEY_SELECTED_PERIOD] = period }
```
6. Add the key constant:
```kotlin
    private companion object { const val KEY_SELECTED_PERIOD = "selectedPeriod" }
```

- [ ] **Step 4: Run to verify pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "*StatsViewModelTest*" > /tmp/t3.log 2>&1; tail -20 /tmp/t3.log`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModel.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/stats/StatsViewModelTest.kt
git commit -m "fix(stats): selected period survives process death via SavedStateHandle (#234)"
```

---

### Task 4: PackRevealState DTO + pure mapping tests

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/PackRevealState.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/PackRevealStateTest.kt`

- [ ] **Step 1: Write the failing mapping tests**

Create `PackRevealStateTest.kt`:
```kotlin
package com.whitefang.stepsofbabylon.presentation.cards

import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackRevealStateTest {
    @Test
    fun `round-trips a multi-card list preserving type, isNew, and copies`() {
        val original = listOf(
            CardResult(CardType.entries.first(), isNew = true, copiesAwarded = 1),
            CardResult(CardType.entries.last(), isNew = false, copiesAwarded = 3),
        )
        val restored = original.toPackRevealState().toCardResults()
        assertEquals(original, restored, "DTO round-trip must preserve type/isNew/copiesAwarded")
        assertTrue(restored[0].isNew, "the NEW! badge flag must survive")
        assertEquals(3, restored[1].copiesAwarded, "copies must survive")
    }

    @Test
    fun `unresolvable card type name decodes to null payload (graceful drop)`() {
        val dto = PackRevealState(listOf(RevealedCard("NOT_A_REAL_CARD", isNew = true, copiesAwarded = 1)))
        assertNull(dto.toCardResultsOrNull(), "an unknown enum name drops the reveal rather than crashing")
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*PackRevealStateTest*" > /tmp/t4.log 2>&1; tail -25 /tmp/t4.log`
Expected: FAIL — `PackRevealState`/`RevealedCard`/mappers unresolved.

- [ ] **Step 3: Create the DTO + mappers**

Create `PackRevealState.kt`:
```kotlin
package com.whitefang.stepsofbabylon.presentation.cards

import android.os.Parcelable
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import kotlinx.parcelize.Parcelize

/**
 * #234: presentation-layer Parcelable mirror of the transient pack-reveal payload so it survives
 * process death via SavedStateHandle. Domain `CardResult`/`CardType` stay pure (zero Android imports,
 * DomainPurityTest); `CardType` is encoded by `.name` and decoded via `valueOf`. The economic outcome
 * (cards + gems) is already durable in Room; this persists the reveal-once VISUAL confirmation only.
 */
@Parcelize
data class PackRevealState(val cards: List<RevealedCard>) : Parcelable

@Parcelize
data class RevealedCard(
    val cardTypeName: String,
    val isNew: Boolean,
    val copiesAwarded: Int,
) : Parcelable

/** Domain → DTO (write side; called when a pack opens). */
fun List<CardResult>.toPackRevealState(): PackRevealState =
    PackRevealState(map { RevealedCard(it.type.name, it.isNew, it.copiesAwarded) })

/** DTO → domain (read side; trusts the names — used by the test round-trip + after a known write). */
fun PackRevealState.toCardResults(): List<CardResult> =
    cards.map { CardResult(CardType.valueOf(it.cardTypeName), it.isNew, it.copiesAwarded) }

/**
 * Defensive DTO → domain for the restore path: if ANY card type name can't resolve (e.g. an enum was
 * renamed between the write and a later app version reading the saved bundle), drop the whole reveal
 * (returns null) rather than crashing. The reveal is a transient nicety; the cards are already in Room.
 */
fun PackRevealState.toCardResultsOrNull(): List<CardResult>? =
    runCatching { toCardResults() }.getOrNull()
```

- [ ] **Step 4: Run to verify pass + purity guard**

Run: `./run-gradle.sh testDebugUnitTest --tests "*PackRevealStateTest*" --tests "*DomainPurityTest*" > /tmp/t4.log 2>&1; tail -25 /tmp/t4.log`
Expected: PASS for both — `PackRevealStateTest` green AND `DomainPurityTest` green (proves the Android coupling stayed in presentation; domain `CardResult`/`CardType` untouched).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/PackRevealState.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/PackRevealStateTest.kt
git commit -m "feat(cards): @Parcelize PackRevealState DTO for process-death survival (#234)"
```

---

### Task 5: CardsViewModel pack-reveal → SavedStateHandle via the DTO

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsViewModelTest.kt`
- Test (CTOR-SITE FIX): `app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreenTest.kt` — its `vm()` helper at **line 63** constructs `CardsViewModel(cardRepo, playerRepo, adManager)` positionally; the new required ctor param breaks compilation unless updated.

> **Grep guard (run FIRST):** `rg -rn "CardsViewModel\(" app/src/test app/src/androidTest` — both `CardsViewModelTest` and `CardsScreenTest` must pass a 4th `SavedStateHandle()` arg. (`testDebugUnitTest` compiles the whole test source set; a missed site is a hard compile failure.)

- [ ] **Step 0: Fix the external CardsViewModel ctor call site (CardsScreenTest)**

In `CardsScreenTest.kt`, update the `vm()` helper (line 63):
```kotlin
    private fun vm() = CardsViewModel(cardRepo, playerRepo, adManager, SavedStateHandle())
```
(Add `import androidx.lifecycle.SavedStateHandle`. A fresh empty handle defaults the reveal to null — preserving the screen test's behavior; it doesn't assert restore.)

- [ ] **Step 1: Refit the test helper + write the failing round-trip test**

In `CardsViewModelTest.kt`, change the helper (currently `private fun createVm() = CardsViewModel(cardRepo, playerRepo, adManager)`, line ~41) to:
```kotlin
private fun createVm(handle: SavedStateHandle = SavedStateHandle()) =
    CardsViewModel(cardRepo, playerRepo, adManager, handle)
```
(Add `import androidx.lifecycle.SavedStateHandle`.)

Add the round-trip test. **Deterministic `isNew`:** `FakeCardRepository.openCardPackAtomic` derives `isNew` from existing-cards novelty, so assert against the *actual* returned reveal rather than a hard-coded badge vector:
```kotlin
@Test
fun `R234 pack reveal survives process death via the same SavedStateHandle`() = runTest(dispatcher) {
    val handle = SavedStateHandle()
    val vm1 = createVm(handle)
    backgroundScope.launch { vm1.uiState.collect {} }
    advanceUntilIdle()
    // open a pack (player has gems via the @BeforeEach seed)
    vm1.openPack(PackTier.COMMON)
    advanceUntilIdle()
    val revealed = vm1.uiState.value.lastPackResult
    assertNotNull(revealed, "pack open must produce a reveal")

    // simulate process death: a FRESH VM constructed from the SAME (populated) handle
    val vm2 = createVm(handle)
    backgroundScope.launch { vm2.uiState.collect {} }
    advanceUntilIdle()
    assertEquals(revealed, vm2.uiState.value.lastPackResult,
        "the pack reveal must reappear after process death (restored from SavedStateHandle)")
}

@Test
fun `R234 dismissPackResult clears the SavedStateHandle reveal`() = runTest(dispatcher) {
    val handle = SavedStateHandle()
    val vm = createVm(handle)
    backgroundScope.launch { vm.uiState.collect {} }
    advanceUntilIdle()
    vm.openPack(PackTier.COMMON)
    advanceUntilIdle()
    vm.dismissPackResult()
    advanceUntilIdle()
    assertNull(vm.uiState.value.lastPackResult, "dismiss must clear the reveal")
    assertNull(handle.get<PackRevealState?>("packReveal"), "dismiss must clear the handle key")
}
```
(Confirm at implementation: the existing tests at the cited lines that assert `assertNull(vm.uiState.value.lastPackResult)` after a failed/cancelled open still hold — they should, since a non-`Opened` result never writes the handle. Reuse the file's existing imports for `PackTier`/`assertNotNull`/`assertNull`.)

- [ ] **Step 2: Run to verify failure**

Run: `./run-gradle.sh testDebugUnitTest --tests "*CardsViewModelTest*" > /tmp/t5.log 2>&1; tail -30 /tmp/t5.log`
Expected: FAIL (ctor has no `SavedStateHandle`; reveal doesn't round-trip).

- [ ] **Step 3: Migrate the ViewModel**

In `CardsViewModel.kt`:
1. Add imports: `import androidx.lifecycle.SavedStateHandle`.
2. Add the constructor param (last):
```kotlin
class CardsViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val playerRepository: PlayerRepository,
    private val rewardAdManager: RewardAdManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
```
3. Replace the field (line ~42):
```kotlin
    private val _lastPackResult = MutableStateFlow<List<CardResult>?>(null)
```
with the raw saved-DTO `StateFlow` (NOT pre-mapped — see note):
```kotlin
    private val packRevealHandleFlow: StateFlow<PackRevealState?> =
        savedStateHandle.getStateFlow(KEY_PACK_REVEAL, null)
```
   (No extra `map`/`stateIn`/`SharingStarted` imports needed — keep it a single hot stage.)
4. In the `combine(...)`, change the source `_lastPackResult,` to `packRevealHandleFlow,` and map the
   DTO → `List<CardResult>?` **INSIDE the combine lambda** (this matches the reviewed spec §Surface 3 —
   "mapped inside the combine" — and preserves today's single-MutableStateFlow topology exactly; the
   pre-mapped `.map{}.stateIn(Eagerly,null)` alternative would add a second hot stage that can publish a
   transient null on the restore path). Rename the lambda param and map it:
```kotlin
        // ...combine(... , packRevealHandleFlow, _processing, _userMessage) { cards, profile, packReveal, processing, message ->
        // and inside the body, where it built CardsUiState(... lastPackResult = packResult ...):
            lastPackResult = packReveal?.toCardResultsOrNull(),
```
   (`CardsUiState.lastPackResult` stays `List<CardResult>?`, so `CardsScreen` is unchanged.)
5. In `openPack` (line ~98), change:
```kotlin
                    _lastPackResult.value = result.cards
```
to:
```kotlin
                    savedStateHandle[KEY_PACK_REVEAL] = result.cards.toPackRevealState()
```
6. In `watchFreePackAd` (line ~147), change:
```kotlin
                        if (packResult is OpenCardPack.Result.Opened) _lastPackResult.value = packResult.cards
```
to:
```kotlin
                        if (packResult is OpenCardPack.Result.Opened) savedStateHandle[KEY_PACK_REVEAL] = packResult.cards.toPackRevealState()
```
7. `dismissPackResult` (line ~136):
```kotlin
    fun dismissPackResult() { savedStateHandle[KEY_PACK_REVEAL] = null }
```
8. Add the key constant:
```kotlin
    private companion object { const val KEY_PACK_REVEAL = "packReveal" }
```

- [ ] **Step 4: Run to verify pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "*CardsViewModelTest*" > /tmp/t5.log 2>&1; tail -25 /tmp/t5.log`
Expected: PASS (incl. the round-trip + dismiss tests + all existing CardsViewModelTest cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsViewModel.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsViewModelTest.kt app/src/test/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreenTest.kt
git commit -m "fix(cards): pack reveal survives process death via SavedStateHandle DTO (#234)"
```

---

### Task 6: permissionAsked → rememberSaveable + KDoc tweak

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt`

No JVM/Robolectric restore test (per spec §5.3 — no `StateRestorationTester` pattern exists; a screen-level test can't host MainActivity's setContent; verified on-device). This is a one-line, natively-saveable Boolean swap.

- [ ] **Step 1: Swap `remember` → `rememberSaveable`**

In `MainActivity.kt` (line ~118):
```kotlin
                    var permissionAsked by remember { mutableStateOf(false) }
```
→
```kotlin
                    var permissionAsked by rememberSaveable { mutableStateOf(false) }
```
Add the import if absent: `import androidx.compose.runtime.saveable.rememberSaveable`.

- [ ] **Step 2: Tweak the stale KDoc**

In `OnboardingScreen.kt` (the `permissionAsked` param KDoc, ~line 63), change "shown this session" to "shown this process instance (survives process-death restore via rememberSaveable; #234)". Keep the rest of the doc.

- [ ] **Step 3: Build + assemble (no unit test for this surface)**

Run: `./run-gradle.sh testDebugUnitTest assembleDebug > /tmp/t6.log 2>&1; tail -12 /tmp/t6.log`
Expected: BUILD SUCCESSFUL (compiles; no test regressions). On-device restore verification is a developer step (documented).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt
git commit -m "fix(onboarding): permissionAsked survives process death via rememberSaveable (#234)"
```

---

### Task 7: Sync current-state docs (PR Task-List Convention — BEFORE STATE/RUN_LOG)

**Files:**
- Modify: `CLAUDE.md` (headline test count + a SavedStateHandle convention note if warranted)
- Modify: `CHANGELOG.md` (`[Unreleased]` entry + test count)
- Modify: `docs/steering/source-files.md` (new `PackRevealState.kt`; the 3 VMs' SavedStateHandle responsibility note; the `CardsScreenTest` entry now constructs the VM with a `SavedStateHandle`)
- Modify: `docs/steering/tech.md` (it enumerates the plugin list at ~line 49 — add `kotlin.parcelize` to it; condition confirmed true at review)

- [ ] **Step 1: Run the full gate to get the final test count**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t7.log 2>&1; tail -15 /tmp/t7.log`
Expected: BUILD SUCCESSFUL. Record the testcase count: `rg -c "<testcase" app/build/test-results/testDebugUnitTest/*.xml | awk -F: '{s+=$2} END {print s}'`.

- [ ] **Step 2: Update CLAUDE.md**

Update the headline test-count line (`**Headline count: 1205 JVM tests...**` → the new count). Add no new architecture prose unless a convention genuinely changed — a one-line note under Conventions is warranted: "Transient UI selections / reveal-once state that should survive process death go through `SavedStateHandle` (ViewModels) / `rememberSaveable` (Compose); Parcelable DTOs for such state live in `presentation/`, never `domain/` (#234)."

- [ ] **Step 3: Update CHANGELOG.md**

Add an `[Unreleased]` entry at the top: "fix: process-death state survival (#234) — Workshop tab / Stats period / Cards pack-reveal via SavedStateHandle, permissionAsked via rememberSaveable; presentation-layer @Parcelize PackRevealState DTO keeps domain pure; +N JVM tests." Update the current-state test-count block if present.

- [ ] **Step 4: Update source-files.md**

Add an entry for `presentation/cards/PackRevealState.kt` (the @Parcelize DTO + mappers). Append to the three VM entries that they now take a `SavedStateHandle` and persist their selection/reveal across process death (#234). Update the `CardsScreenTest` entry (its `vm()` helper now passes a `SavedStateHandle`). Also add `kotlin.parcelize` to the enumerated plugin list in `docs/steering/tech.md` (~line 49).

- [ ] **Step 5: Commit docs**

```bash
git add CLAUDE.md CHANGELOG.md docs/steering/source-files.md docs/steering/tech.md
git commit -m "docs: sync for process-death state survival (#234)"
```

---

### Task 8: Update STATE.md + RUN_LOG.md

**Files:**
- Modify: `docs/agent/STATE.md`
- Modify: `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Update STATE.md**

Rotate `## Current objective`: new #234 objective on top (what shipped, test-count delta, the 4 surfaces, the battle/onboarding exclusions), demote the prior (#230/#231) entry to a `Previous objective` bullet. Update the headline test count. Keep to ~one page.

- [ ] **Step 2: Append RUN_LOG.md**

New dated entry (newest-first): goal, the 4 surfaces + approach, the @Parcelize-DTO purity solution, spec+plan through the Adversarial Review Gate (spec 25→15 surviving), verification (build/test results), doc-sync list, what remains (on-device permissionAsked check; battle overlay deferred).

- [ ] **Step 3: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(state): checkpoint process-death state survival (#234)"
```

---

### Task 9: Final gate + push + PR

- [ ] **Step 1: Final full gate**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t9.log 2>&1; tail -20 /tmp/t9.log`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Confirm DomainPurityTest is green (the architectural guard)**

Run: `rg -c "<testcase" app/build/test-results/testDebugUnitTest/TEST-*DomainPurityTest.xml 2>/dev/null; grep -l "DomainPurityTest" app/build/test-results/testDebugUnitTest/*.xml | xargs grep -L "failure" >/dev/null && echo "DomainPurityTest GREEN"`
Expected: green (no failures) — proves `@Parcelize` stayed out of domain.

- [ ] **Step 3: Push + open PR**

```bash
git push -u origin fix/234-process-death-state-survival
gh pr create --title "fix: process-death state survival (#234)" --body "<summary: 4 surfaces, @Parcelize DTO keeps domain pure, battle/onboarding excluded with rationale, Closes #234>"
```

---

## Self-review checklist (run before execution)

- **Spec coverage:** §4 surface 1→Task 2; surface 2→Task 3; surface 3→Tasks 4+5; surface 4→Task 6; build change→Task 1; §5 tests→Tasks 2-6 (smoke→1); §7 acceptance→Task 9; docs→Tasks 7-8. ✓
- **Domain purity:** DTO in presentation (Task 4); `DomainPurityTest` asserted green in Task 4 Step 4 + Task 9 Step 2. ✓
- **Type consistency:** keys are `private const val` per VM (`selectedCategory`/`selectedPeriod`/`packReveal`); `toPackRevealState`/`toCardResults`/`toCardResultsOrNull` defined in Task 4, used in Task 5; `CardsUiState.lastPackResult` stays `List<CardResult>?` (screen unchanged). ✓
- **Open implementation detail flagged inline:** exact fake field names in each VM test, `StatsPeriod` enum package + a non-`WEEK` value, and the existing `assertNull(lastPackResult)` cases in CardsViewModelTest — all flagged "confirm at implementation" since they're file-local specifics.
