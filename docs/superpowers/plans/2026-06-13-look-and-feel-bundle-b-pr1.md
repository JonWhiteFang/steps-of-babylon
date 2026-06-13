# Look & Feel Bundle B — PR-B1 (Back Affordances) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the 8 secondary (push-navigated) screens a visible back affordance and a consistent, correctly-styled title via a single shared `SobTopAppBar` rendered in MainActivity's outer Scaffold — eliminating the "no way up except system back" gap and the "inconsistent title sizes" finding from the 2026-06-12 UX review.

**Architecture:** ONE `SobTopAppBar` (Material3 `CenterAlignedTopAppBar`, using the default `TopAppBarDefaults.windowInsets` so the bar self-pads the status bar) lives in MainActivity's existing outer `Scaffold`'s `topBar` slot. A pure, unit-testable `Screen.secondaryTitle(route)` helper returns the bar title for exactly the 8 push-children and `null` for everything else (tabs, Battle, Onboarding, unknown) — so the bar renders only where it should, with no per-screen param threading and no change to the `by lazy` route lists. The back arrow calls `navController.navigateUp()` (mirrors system/predictive back). The 5 screens that currently render their own inline title header delete it (the bar now carries it); Weapons/Cards gain a title they never had; Missions keeps its two *section* headers.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 — `CenterAlignedTopAppBar`, `ExperimentalMaterial3Api`), `Icons.AutoMirrored.Filled.ArrowBack` (already used at `BattleScreen.kt:141`), Robolectric + JUnit4 for the one JVM test (mirrors `OnboardingRoutingTest`, required because `Screen.kt` imports `ImageVector`). Build via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-13-look-and-feel-bundle-b-design.md` (§4) · **Issue:** #161 · **Branch:** `feat/look-and-feel-bundle-b`

---

## Conventions used in this plan

- **String literals, not resources.** The screens use inline title literals today (i18n is documented phase-2 debt; `lint { error += "HardcodedText" }` is XML-only and does not flag Compose `Text()`). Matching surrounding code, the bar title and `"Back"` contentDescription are literals. Do NOT expand i18n scope here.
- **Build only on Kotlin-changing tasks.** Run `./run-gradle.sh assembleDebug` (compile) and the noted tests; redirect verbose output per the project norm if needed.
- **Commit per task.** Each task ends green and builds on its own.
- **No `Screen.kt` route/list changes.** `secondaryTitle` is a new *function* on the companion — it does NOT touch `items`/`allScreens`/`argumentFreeRoutes` (the init-order fragile zone) or the pinned 13-route deep-link set.

---

## Task 1: `Screen.secondaryTitle(route)` helper + JVM test (TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt` (companion object, after `startDestination`, ~line 74)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/ScreenSecondaryTitleTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/ScreenSecondaryTitleTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenSecondaryTitleTest {

    @Test
    fun `secondaryTitle returns the explicit title for each of the 8 push-children`() {
        assertEquals("Ultimate Weapons", Screen.secondaryTitle(Screen.Weapons.route))
        assertEquals("Cards", Screen.secondaryTitle(Screen.Cards.route))
        assertEquals("Unclaimed Supplies", Screen.secondaryTitle(Screen.Supplies.route))
        assertEquals("Premium Currencies", Screen.secondaryTitle(Screen.Economy.route))
        assertEquals("Missions", Screen.secondaryTitle(Screen.Missions.route))
        assertEquals("Settings", Screen.secondaryTitle(Screen.Settings.route))
        assertEquals("Store", Screen.secondaryTitle(Screen.Store.route))
        assertEquals("Help", Screen.secondaryTitle(Screen.Help.route))
    }

    @Test
    fun `secondaryTitle is null for every bottom-nav tab`() {
        // Tabs reach themselves via the bottom nav; they get no back-arrow bar.
        Screen.items.forEach { tab ->
            assertNull("tab ${tab.route} must not get a bar", Screen.secondaryTitle(tab.route))
        }
    }

    @Test
    fun `secondaryTitle is null for Battle and Onboarding`() {
        // Battle is a tab with its own exit affordance; Onboarding is a self-contained carousel.
        assertNull(Screen.secondaryTitle(Screen.Battle.route))
        assertNull(Screen.secondaryTitle(Screen.Onboarding.route))
    }

    @Test
    fun `secondaryTitle is null for an unknown route and for null`() {
        assertNull(Screen.secondaryTitle("not-a-real-route"))
        assertNull(Screen.secondaryTitle(null))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.navigation.ScreenSecondaryTitleTest"`
Expected: FAIL — compile error, `secondaryTitle` is unresolved.

- [ ] **Step 3: Add the `secondaryTitle` helper**

In `Screen.kt`, inside the `companion object`, immediately after the `startDestination(...)` function (the function block ending at the line above the closing `}` of the companion, ~line 74), add:

```kotlin
        /**
         * The top-app-bar title for the 8 secondary (push-navigated) screens that get a back
         * affordance (Bundle B / #161, ADR-0022 follow-up). Returns null for bottom-nav tabs,
         * Battle (a tab with its own exit affordance), Onboarding (a self-contained carousel),
         * and unknown routes — those render NO SobTopAppBar.
         *
         * Pure (route strings only) so it is unit-testable and so adding the bar never touches the
         * `by lazy` route lists or the pinned deep-link set. Titles are deliberately explicit, NOT
         * derived from `label` (e.g. Supplies → "Unclaimed Supplies", Economy → "Premium Currencies"
         * read better as headers than their narrow tab labels).
         */
        fun secondaryTitle(route: String?): String? = when (route) {
            Weapons.route -> "Ultimate Weapons"
            Cards.route -> "Cards"
            Supplies.route -> "Unclaimed Supplies"
            Economy.route -> "Premium Currencies"
            Missions.route -> "Missions"
            Settings.route -> "Settings"
            Store.route -> "Store"
            Help.route -> "Help"
            else -> null
        }
```

> Note: this is a *function*, evaluated at call time — long after all `data object`s are constructed — so it is free of the sealed-class init-order NPE that forced `items`/`allScreens` to use `by lazy` (commit 1872af9).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.navigation.ScreenSecondaryTitleTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/ScreenSecondaryTitleTest.kt
git commit -m "feat(nav): add pure Screen.secondaryTitle() — bar title for the 8 push-children (#161)"
```

---

## Task 2: `SobTopAppBar.kt` — shared top bar component

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/SobTopAppBar.kt`

No JVM test — it is a thin visual composable with no logic (consistent with the Bundle-A norm of visual verification for thin helpers; its only logic, "which screens show it", lives in `secondaryTitle`, tested in Task 1).

- [ ] **Step 1: Create the component**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/SobTopAppBar.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * The single shared back-affordance app bar for the 8 secondary (push-navigated) screens
 * (Bundle B / #161). Rendered once in MainActivity's outer Scaffold `topBar`, gated by
 * [com.whitefang.stepsofbabylon.presentation.navigation.Screen.secondaryTitle].
 *
 * Inset handling: the bar deliberately uses the DEFAULT `TopAppBarDefaults.windowInsets`
 * (status-bar Top + Horizontal) — i.e. `windowInsets` is NOT overridden. In a Material3
 * `Scaffold`, the `topBar` slot owns its own top inset: the bar self-pads the status bar, and the
 * Scaffold then sets `innerPadding.top = topBarHeight` (height INCLUDING that inset), which the
 * NavHost consumes via `Modifier.padding(innerPadding)`. So there is one coherent inset path — the
 * bar pushes its arrow/title below the status bar and the content below the bar. Do NOT zero the
 * insets: that would draw the arrow/title under the status bar (clipped) and strip the status-bar
 * offset from content. Adopting themed-bar art later is a one-file change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./run-gradle.sh assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/SobTopAppBar.kt
git commit -m "feat(ui): add shared SobTopAppBar (centered title + back arrow) (#161)"
```

---

## Task 3: Wire the bar into MainActivity's outer Scaffold

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt` (import; the outer `Scaffold` at lines 210–232)

After this task the 8 secondary screens will briefly show BOTH the bar title and their old inline title (Task 4 removes the inline ones). The build is green throughout.

- [ ] **Step 1: Add the import**

In `MainActivity.kt`, with the other `presentation.ui` / `presentation` imports (alphabetical block around lines 44–61), add:

```kotlin
import com.whitefang.stepsofbabylon.presentation.ui.SobTopAppBar
```

- [ ] **Step 2: Add the `topBar` to the outer Scaffold**

The outer `Scaffold(...)` currently opens at line 210 with `snackbarHost = { ... }` (line 211) and `bottomBar = { ... }` (lines 212–231). Add a `topBar` parameter immediately after the `snackbarHost = { ... },` line (i.e. between `snackbarHost` and `bottomBar`):

```kotlin
                    topBar = {
                        val topBarEntry by navController.currentBackStackEntryAsState()
                        val topBarRoute = topBarEntry?.destination?.route
                        Screen.secondaryTitle(topBarRoute)?.let { title ->
                            SobTopAppBar(
                                title = title,
                                onNavigateBack = { navController.navigateUp() },
                            )
                        }
                    },
```

> `currentBackStackEntryAsState` and `getValue` (`by`) are already imported (used by `bottomBar`). The `topBar` slot renders nothing when `secondaryTitle` is `null`, so it contributes 0 height on tabs/Battle/Onboarding and the existing `Modifier.padding(innerPadding)` on the NavHost (line 263) keeps content correctly offset on the 8 screens. Distinct local names (`topBarEntry`/`topBarRoute`) avoid shadowing the `bottomBar`'s `backStackEntry`/`currentRoute`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./run-gradle.sh assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt
git commit -m "feat(nav): render SobTopAppBar in outer Scaffold, gated to push-children (#161)"
```

---

## Task 4: Delete the now-duplicated inline title headers

**Files (all Modify):**
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt:31-32`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/help/HelpScreen.kt:27`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt:61`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/economy/CurrencyDashboardScreen.kt:54-61`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt:45-52`

Weapons, Cards, Missions are NOT touched (no title header today; Missions keeps its two section headers).

- [ ] **Step 1: SettingsScreen — delete the title + its spacer**

Delete exactly these two consecutive lines (SettingsScreen.kt:31-32) — the `Column(...)` opener above and the `ToggleRow("Live Step Updates", …)` line below them stay unchanged:
```kotlin
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
```
So the `Column { … }` body now opens directly onto the first `ToggleRow(...)`. (Do NOT include the `ToggleRow` line in the match string — it is unchanged context, and its full real line is long.)

- [ ] **Step 2: HelpScreen — delete the title**

Replace:
```kotlin
        Text("Help", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        HelpSection("💰 Currencies") {
```
with:
```kotlin
        HelpSection("💰 Currencies") {
```
(The decorative `💰`/section-heading emoji are intentionally left as-is — owned by #164.)

- [ ] **Step 3: StoreScreen — delete the "Store" title line only**

Replace:
```kotlin
        item {
            Text("Store", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            CurrencyValue(CurrencyType.GEMS, state.gems)
            Spacer(Modifier.height(8.dp))
        }
```
with:
```kotlin
        item {
            CurrencyValue(CurrencyType.GEMS, state.gems)
            Spacer(Modifier.height(8.dp))
        }
```

- [ ] **Step 4: CurrencyDashboardScreen (Economy) — delete title, right-align the Store button**

Replace:
```kotlin
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Premium Currencies", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            androidx.compose.material3.TextButton(onClick = onStoreClick) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Store")
            }
        }
```
with:
```kotlin
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = onStoreClick) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Store")
            }
        }
```

- [ ] **Step 5: UnclaimedSuppliesScreen — delete title, right-align Claim All**

Replace:
```kotlin
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Unclaimed Supplies", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (state.drops.isNotEmpty()) {
                Button(onClick = viewModel::claimAll, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                    Text("Claim All")
                }
            }
        }
```
with:
```kotlin
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (state.drops.isNotEmpty()) {
                Button(onClick = viewModel::claimAll, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                    Text("Claim All")
                }
            }
        }
```

- [ ] **Step 6: Build + lint to verify no unused imports / no compile error**

Run: `./run-gradle.sh assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL; lint green. (Note: the project's lint only promotes `HardcodedText` to an error — it does NOT flag unused imports, and Kotlin doesn't fail the build on them either. But all five edits genuinely leave every affected import still in use — `FontWeight`/`Text`/`Spacer`/`MaterialTheme`/`Alignment`/`Icons.Default.ShoppingCart`/`CurrencyValue` each remain referenced by other code in their file — so `assembleDebug` is the real guard and stays green.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/help/HelpScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/economy/CurrencyDashboardScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt
git commit -m "feat(ui): move 5 screens' titles into SobTopAppBar; right-align their actions (#161)"
```

---

## Task 5: Full validation + on-device verification

**Files:** none (verification only).

- [ ] **Step 1: Full JVM suite + lint + assemble**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL; all tests green; **JVM count 975 → 979** (+4 `@Test` methods from the one new class `ScreenSecondaryTitleTest` — the headline counts test *methods*, not classes; cf. CHANGELOG's `+2 from CurrencyDisplayTest`). `DeepLinkRoutingTest` + `OnboardingRoutingTest` pass unchanged (route set untouched — regression guard).

- [ ] **Step 2: On-device (emulator API 36) visual check**

Install (`./run-gradle.sh installDebug` or via the existing run flow) and verify:
- Each of the 8 screens (Weapons, Cards, Supplies, Economy, Missions, Settings, Store, Help) shows the centered-title bar with a working back arrow.
- Back arrow returns to the correct parent: Weapons/Cards → Workshop; Store → Economy *and* (from Home tile) Home; Economy/Missions/Settings/Supplies/Help → Home.
- No title renders twice; the bar's back arrow + title sit fully **below** the status bar (not clipped/overlapping the clock/battery — the key check that the default `TopAppBarDefaults.windowInsets` is doing its job); no wasted gap above the bar.
- **Inner-Scaffold screens (Cards, Missions, Store):** confirm there is no double top-gap between the bar and the first content row (they each host their own `Scaffold`; the outer bar should reserve its height once, with content flush below it).
- The 5 bottom-nav tabs (Home/Workshop/Battle/Labs/Stats), the Battle screen, and the Onboarding carousel show **NO** bar.
- Economy's "Store" button and Supplies' "Claim All" button are right-aligned and still work.

> If any check fails, fix and re-run Steps 1–2 before proceeding. (No commit in this task unless a fix is made.)

---

## Task 6: Docs sync + checkpoint (PR Task-List Convention)

**Files:** `docs/steering/source-files.md`, `CLAUDE.md`, `CHANGELOG.md`, then `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md`.

Per CLAUDE.md, current-state docs are synced BEFORE the STATE/RUN_LOG update.

- [ ] **Step 1: `docs/steering/source-files.md`**

Add an entry for `presentation/ui/SobTopAppBar.kt` (shared back-affordance app bar; rendered once in MainActivity, gated by `Screen.secondaryTitle`). Note the new `Screen.secondaryTitle(route)` helper and that 5 screens' inline title headers moved into the bar.

- [ ] **Step 2: `CLAUDE.md` test-count line**

Update the headline count `975 JVM` → `979 JVM` (instrumented unchanged at 9). Touch nothing else in CLAUDE.md (no stable architecture/convention changed).

- [ ] **Step 3: `CHANGELOG.md`**

Add a `[Unreleased]` entry: Bundle B PR-B1 — shared `SobTopAppBar` back affordances on the 8 secondary screens; titles centralized into the bar; `Screen.secondaryTitle` helper + `ScreenSecondaryTitleTest` (+4 methods); 975 → 979 JVM tests.

- [ ] **Step 4: `/checkpoint`**

Run the `/checkpoint` skill to update `docs/agent/STATE.md` (Bundle B PR-B1 done; PR-B2 — nav-restore bug — is next and must wait until PR-B1 is merged) and append `docs/agent/RUN_LOG.md`. **No ADR for PR-B1** (implements the existing ADR-0022 token/UX direction; no new architectural decision — the ADR comes with PR-B2's back-stack contract).

- [ ] **Step 5: Commit the docs**

```bash
git add docs/ CLAUDE.md CHANGELOG.md
git commit -m "docs: checkpoint — Bundle B PR-B1 (back affordances) (#161)"
```

- [ ] **Step 6: Open the PR**

Push the branch and open a PR for PR-B1 only (B2 is a separate later PR on a separate branch). Title: `feat(ui): look-and-feel Bundle B PR-B1 — back affordances (#161)`. In the body, note that #161 stays OPEN until PR-B2 (nav-restore bug) also lands.

---

## Self-Review (completed by plan author)

- **Spec coverage (§4):** SobTopAppBar component (Task 2) ✓; outer-Scaffold placement gated by `secondaryTitle` (Tasks 1+3) ✓; `navigateUp()` back action (Task 3) ✓; explicit per-screen title map + delete duplicated headers, Missions keeps section headers (Tasks 1+4) ✓; default `TopAppBarDefaults.windowInsets` so the topBar self-pads the status bar — one coherent inset path (Task 2) ✓; no `Screen.kt` route-list change / DeepLinkRoutingTest unaffected (Task 1 note + Task 5) ✓; `ScreenSecondaryTitleTest`, 975→979 (Tasks 1+5) ✓; docs per convention (Task 6) ✓. PR-B2 (bug fix) is intentionally a separate plan.
- **Placeholders:** none — every code step shows exact before/after.
- **Type/name consistency:** `secondaryTitle(route: String?): String?` defined in Task 1, called identically in Task 3 and tested in Task 1. `SobTopAppBar(title, onNavigateBack)` defined in Task 2, called with those exact params in Task 3. Title strings match between Task 1's map, Task 1's test assertions, and the spec's §4.3 table.
