# First-Launch Onboarding (Gate C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A brand-new player sees a one-time 4-slide tutorial teaching the walk → spend → battle loop, is asked for step-counting permission *with* context (never stranded by Skip or denial), and can replay the tutorial from Settings.

**Architecture:** A self-contained `presentation/onboarding/` Compose carousel (`HorizontalPager`) backed by an `OnboardingViewModel` and a device-local `OnboardingPreferences` (SharedPreferences — no Room schema change). `MainActivity` chooses the NavHost start destination from a synchronous prefs read via a pure, JVM-testable helper, gates *only* the existing cold-permission request branch + the deep-link collector behind onboarding-completion, and passes a permission-trigger callback into the onboarding route. The final slide owns the first permission ask and shows a Settings-recovery affordance on denial.

**Tech Stack:** Kotlin · Jetpack Compose (`HorizontalPager` from `androidx.compose.foundation.pager`, transitive via material3) · Hilt (constructor injection, no module) · navigation-compose 2.9.8 · JUnit Jupiter + mockito-kotlin (JVM tests) · Robolectric (SharedPreferences / `Screen` tests).

**Source spec:** `docs/superpowers/specs/2026-06-11-onboarding-gate-c-design.md` (approved + adversarially reviewed).

---

## File Structure

**New files:**
- `app/src/main/java/com/whitefang/stepsofbabylon/data/onboarding/OnboardingPreferences.kt` — device-local completion flag. One responsibility: read/write/reset `hasCompletedOnboarding`.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingSlide.kt` — pure Kotlin slide model + the canonical slide list (`OnboardingContent`). No Android imports.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingViewModel.kt` — exposes the slide list; `completeOnboarding()` persists the flag.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt` — the carousel + permission primer Composable.
- `app/src/test/java/com/whitefang/stepsofbabylon/data/onboarding/OnboardingPreferencesTest.kt` — Robolectric round-trip.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingContentTest.kt` — pure JVM slide-list smoke.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingViewModelTest.kt` — JVM, mockito mock of prefs.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/OnboardingRoutingTest.kt` — Robolectric; route resolution + `startDestination` helper + NOT-in-allowlist.

**Modified files:**
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt` — add `Onboarding` data object + `startDestination()` helper; keep it out of `items`/`allScreens`/`argumentFreeRoutes`; fix stale "All 12 screens" comment.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt` — conditional start destination; gate the cold-request branch; gate the deep-link collector on live nav state; permanent-denial recovery (`shouldShowRequestPermissionRationale` → Snackbar → `ACTION_APPLICATION_DETAILS_SETTINGS`, spec §4) in the launcher callback + Scaffold snackbarHost; onboarding route composable + launcher state; pass `onReplayTutorial` to Settings.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/NotificationSettingsScreen.kt` — `onReplayTutorial` param + "Replay tutorial" row.

**Conventions to copy:**
- SharedPreferences class shape: `MusicPreferences.kt` (`@Singleton class … @Inject constructor(@ApplicationContext context: Context)`, no Hilt module).
- Robolectric prefs test: `MusicPreferencesTest.kt`.
- JVM test mocking a concrete final prefs class: `DailyStepManagerTest.kt` (`mock<AntiCheatPreferences>()`, JUnit Jupiter) — proves mockito-kotlin mocks final classes here. `HomeViewModelTest.kt` shows the Jupiter ViewModel-test shape (but uses `Dispatchers.setMain`, which the onboarding VM test does NOT need — see Task 4).
- Robolectric `Screen` test: `DeepLinkRoutingTest.kt`.

**Build commands** (non-TTY — always use `./run-gradle.sh`, never `./gradlew`):
- JVM tests: `./run-gradle.sh testDebugUnitTest`
- A single JVM test class: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingViewModelTest"`
- Compile/assemble (verifies Compose code): `./run-gradle.sh assembleDebug`
- Save verbose Gradle output to a temp log and tail it, per CLAUDE.md.

---

## Task 1: OnboardingPreferences (device-local completion flag)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/data/onboarding/OnboardingPreferences.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/onboarding/OnboardingPreferencesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `OnboardingPreferencesTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.data.onboarding

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingPreferencesTest {

    @Test
    fun `defaults to not completed`() {
        val prefs = OnboardingPreferences(RuntimeEnvironment.getApplication())
        assertFalse(prefs.hasCompletedOnboarding())
    }

    @Test
    fun `setCompleted then reset round-trip`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = OnboardingPreferences(context)

        prefs.setCompleted()
        assertTrue(prefs.hasCompletedOnboarding())

        prefs.reset()
        assertFalse(prefs.hasCompletedOnboarding())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferencesTest"`
Expected: FAIL — `OnboardingPreferences` is unresolved (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `OnboardingPreferences.kt`:

```kotlin
package com.whitefang.stepsofbabylon.data.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local first-launch onboarding state. Deliberately NOT in Room: this is a
 * UI/device preference, not game state, so it must not sync if cloud save (#36) ever
 * lands, and a reinstall correctly re-shows the tutorial. Mirrors the structure of
 * MusicPreferences / AntiCheatPreferences — @Singleton, constructor-injected, no Hilt module.
 */
@Singleton
class OnboardingPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)
    fun setCompleted() { prefs.edit().putBoolean(KEY_COMPLETED, true).apply() }
    fun reset() { prefs.edit().putBoolean(KEY_COMPLETED, false).apply() }

    private companion object {
        const val KEY_COMPLETED = "has_completed_onboarding"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferencesTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/onboarding/OnboardingPreferences.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/onboarding/OnboardingPreferencesTest.kt
git commit -m "feat(onboarding): device-local OnboardingPreferences completion flag"
```

---

## Task 2: OnboardingSlide model + canonical content

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingSlide.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingContentTest.kt`

> Slides use emoji strings (not `ImageVector`) so the model stays pure Kotlin — its test runs as plain JVM (no Robolectric). The **final slide being the permission primer** is the load-bearing ordering invariant (routing + completion + Skip all depend on it).

- [ ] **Step 1: Write the failing test**

Create `OnboardingContentTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.onboarding

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingContentTest {

    @Test
    fun `slides are non-empty and only the final slide is the permission primer`() {
        val slides = OnboardingContent.slides
        assertTrue(slides.isNotEmpty(), "onboarding must have at least one slide")

        // The final slide MUST be the permission primer — routing, completion, and the
        // Skip contract all assume the last page is where the permission ask lives.
        assertTrue(slides.last().isPermissionPrimer, "last slide must be the permission primer")

        // No earlier slide may be a primer (exactly one, at the end).
        slides.dropLast(1).forEach { slide ->
            assertFalse(slide.isPermissionPrimer, "only the final slide may be the permission primer")
        }
    }

    @Test
    fun `every slide has a title and body`() {
        OnboardingContent.slides.forEach { slide ->
            assertTrue(slide.title.isNotBlank(), "slide title must not be blank")
            assertTrue(slide.body.isNotBlank(), "slide body must not be blank")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingContentTest"`
Expected: FAIL — `OnboardingSlide` / `OnboardingContent` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `OnboardingSlide.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.onboarding

/**
 * One tutorial slide. Pure Kotlin (emoji icon, not an ImageVector) so the content list
 * is JVM-testable without Android. [isPermissionPrimer] marks the final slide that owns
 * the activity-recognition permission ask.
 */
data class OnboardingSlide(
    val icon: String,
    val title: String,
    val body: String,
    val isPermissionPrimer: Boolean = false,
)

/** Canonical first-launch tutorial content. Final slide is the permission primer. */
object OnboardingContent {
    val slides: List<OnboardingSlide> = listOf(
        OnboardingSlide(
            icon = "🏛️",
            title = "Walk to power your ziggurat",
            body = "Every real step you take earns Steps — the permanent currency that " +
                "fuels everything. Steps are earned only by walking.",
        ),
        OnboardingSlide(
            icon = "🔨",
            title = "Spend Steps in the Workshop",
            body = "Permanent upgrades make your tower stronger across three categories: " +
                "Attack, Defense, and Utility.",
        ),
        OnboardingSlide(
            icon = "⚔️",
            title = "Send it into battle",
            body = "Your ziggurat auto-battles waves of enemies. Survive, climb tiers, and " +
                "unlock new biomes.",
        ),
        OnboardingSlide(
            icon = "👣",
            title = "Enable step counting",
            body = "To turn your real-world steps into Steps, we need activity-recognition " +
                "permission. Notifications are optional. Then go for a walk to earn your first Steps!",
            isPermissionPrimer = true,
        ),
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingContentTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingSlide.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingContentTest.kt
git commit -m "feat(onboarding): OnboardingSlide model + canonical 4-slide content"
```

---

## Task 3: Screen.Onboarding route + startDestination helper

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/OnboardingRoutingTest.kt`

> `Onboarding` MUST stay out of `items` (bottom nav), `allScreens`, and `argumentFreeRoutes` — otherwise it becomes a public `navigate_to` deep-link target, and adding it to `allScreens` would break `DeepLinkRoutingTest`'s exact-13-routes assertion. It is reached only by literal `Screen.Onboarding.route` navigation. The `startDestination` helper is pure (returns route strings) and JVM-relevant, but references `Screen`, which imports `ImageVector` — so its test runs under Robolectric like `DeepLinkRoutingTest`.

- [ ] **Step 1: Write the failing test**

Create `OnboardingRoutingTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingRoutingTest {

    @Test
    fun `startDestination is Onboarding when not completed`() {
        assertEquals(Screen.Onboarding.route, Screen.startDestination(hasCompletedOnboarding = false))
    }

    @Test
    fun `startDestination is Home when completed`() {
        assertEquals(Screen.Home.route, Screen.startDestination(hasCompletedOnboarding = true))
    }

    @Test
    fun `fromRoute resolves onboarding`() {
        assertSame(Screen.Onboarding, Screen.fromRoute("onboarding"))
    }

    @Test
    fun `onboarding is NOT a public deep-link target`() {
        // Must stay out of the navigate_to allowlist so an external intent can't push it.
        assertFalse("onboarding" in Screen.argumentFreeRoutes)
    }

    @Test
    fun `onboarding is not in bottom-nav items`() {
        assertFalse(Screen.items.any { it.route == "onboarding" })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.navigation.OnboardingRoutingTest"`
Expected: FAIL — `Screen.Onboarding` and `Screen.startDestination` unresolved.

> Note: `fromRoute("onboarding")` returning the object requires `Onboarding` to be reachable via lookup. We add a dedicated single-object check inside `fromRoute` rather than putting it in `allScreens` (which feeds `argumentFreeRoutes`). See implementation.

- [ ] **Step 3: Write minimal implementation**

In `Screen.kt`, add the `Onboarding` data object after `Help` (line 32). It needs an `icon` arg (constructor requires one) even though it is never shown in nav — reuse the already-imported help icon:

```kotlin
    data object Help : Screen("help", "Help", Icons.AutoMirrored.Filled.HelpOutline)

    // First-run / replay-only route. NOT in `items`, `allScreens`, or `argumentFreeRoutes`
    // (so it can't be reached as a public navigate_to deep-link target). The icon is
    // unused — Onboarding never appears in the bottom nav. Reached only via literal
    // Screen.Onboarding.route navigation (start destination on first launch; Settings replay).
    data object Onboarding : Screen("onboarding", "Onboarding", Icons.AutoMirrored.Filled.HelpOutline)
```

Then **replace the entire companion object (`Screen.kt` lines 34-62)** with the version below — it
fixes the stale "All 12 screens" comment to "All 13 non-onboarding screens", adds `startDestination`,
and extends `fromRoute` to also match `Onboarding` without adding it to the allowlist:

```kotlin
    companion object {
        val items by lazy { listOf(Home, Workshop, Battle, Labs, Stats) }

        // All 13 non-onboarding screens, needed for O(1) deep-link lookup. Uses `by lazy`
        // for the same reason as `items` above — sealed-class init order can NPE if this
        // list is evaluated before all data objects are constructed (see commit 1872af9).
        // Onboarding is deliberately excluded: it must never be a public navigate_to target.
        private val allScreens by lazy {
            listOf(Home, Workshop, Battle, Labs, Stats, Weapons, Cards, Supplies, Economy, Missions, Settings, Store, Help)
        }

        /**
         * Deep-link routes that can be navigated to directly from an Intent extra
         * (`navigate_to=<route>`). Onboarding is intentionally absent — it is a
         * first-run/replay-only route, never a public deep-link target.
         */
        val argumentFreeRoutes: Set<String> by lazy { allScreens.map { it.route }.toSet() }

        /**
         * Resolves a route name to its [Screen], or null if no screen matches.
         * Includes [Onboarding] (so internal navigation/tests can resolve it) even though
         * Onboarding is excluded from [argumentFreeRoutes]; deep-link callers gate on
         * [argumentFreeRoutes], so resolving Onboarding here does not make it a deep-link target.
         */
        fun fromRoute(name: String?): Screen? =
            name?.let { n -> (allScreens + Onboarding).firstOrNull { it.route == n } }

        /**
         * The NavHost start destination, chosen from the synchronous onboarding-completion
         * flag. Pure (route strings only) so it is unit-testable. NavHost captures
         * startDestination only on first composition — callers MUST pass a synchronous read,
         * not an async StateFlow default.
         */
        fun startDestination(hasCompletedOnboarding: Boolean): String =
            if (hasCompletedOnboarding) Home.route else Onboarding.route
    }
```

- [ ] **Step 4: Run the new test AND the existing deep-link test to verify both pass**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.navigation.OnboardingRoutingTest" --tests "com.whitefang.stepsofbabylon.presentation.DeepLinkRoutingTest"`
Expected: PASS. `DeepLinkRoutingTest`'s "contains all 13 current screens" assertion still holds because `Onboarding` is NOT in `allScreens`/`argumentFreeRoutes`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/navigation/Screen.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/navigation/OnboardingRoutingTest.kt
git commit -m "feat(onboarding): Screen.Onboarding route + pure startDestination helper

Kept out of allScreens/argumentFreeRoutes/items so it is not a public
deep-link target; fromRoute resolves it for internal nav only."
```

---

## Task 4: OnboardingViewModel

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingViewModelTest.kt`

> Tested with a Mockito mock of the concrete `OnboardingPreferences` (the project's mockito-kotlin already mocks concrete final classes — see `DailyStepManagerTest`'s `mock<AntiCheatPreferences>()`). No interface extraction.
>
> **Runner note:** `OnboardingViewModel` extends `androidx.lifecycle.ViewModel`, whose no-arg
> constructor does not touch Android — so this test runs as plain JVM under JUnit Jupiter with **no
> Robolectric**. It also does no `viewModelScope` work, so deliberately omit the
> `Dispatchers.setMain`/`runTest` `@BeforeEach` from the `HomeViewModelTest` template — don't copy it in.

- [ ] **Step 1: Write the failing test**

Create `OnboardingViewModelTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.onboarding

import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class OnboardingViewModelTest {

    private val prefs = mock<OnboardingPreferences>()
    private val viewModel = OnboardingViewModel(prefs)

    @Test
    fun `exposes the canonical slide list`() {
        // Identity (not value-equality) is the contract: the VM must expose the SAME list
        // instance, never a copy — keeps the no-copy guarantee the screen relies on.
        assertSame(OnboardingContent.slides, viewModel.slides)
    }

    @Test
    fun `completeOnboarding persists the completion flag`() {
        viewModel.completeOnboarding()
        verify(prefs).setCompleted()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingViewModelTest"`
Expected: FAIL — `OnboardingViewModel` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `OnboardingViewModel.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

    val slides: List<OnboardingSlide> = OnboardingContent.slides

    /** Persists that the player has finished (or skipped through) onboarding. Idempotent. */
    fun completeOnboarding() {
        onboardingPreferences.setCompleted()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingViewModel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): OnboardingViewModel (slides + completeOnboarding)"
```

---

## Task 5: OnboardingScreen carousel + permission primer

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt`

> No unit test (Compose UI is device-verified here, consistent with the rest of the app's screens). Verified by compilation in Task 6's build. The screen is driven entirely by parameters from `MainActivity` plus the injected `OnboardingViewModel` for the slide list + completion persistence.
>
> **Skip contract:** Skip is shown only on non-final slides and jumps to the final (permission) slide — it skips the lessons, never the permission ask. The final slide has no Skip. (Backward swipe/Next remain available on the primer so lessons are re-readable; the completion flag is only ever set by the final-slide finish buttons, so the ask is never bypassed.)
> **Final-slide states (ORDER MATTERS — spec §5):** the `when{}` checks `stepCountingGranted` FIRST (so an already-granted *replay* shows the satisfied "✓ / Start playing" state and does NOT re-ask), then `!permissionAsked` (the "Enable step counting" ask), then the asked-but-denied state (in-carousel "Open Settings" + "Continue without step counting"). A finish button is always present so a denying player still lands on Home.
> **Post-onboarding denial** (permanently-denied on a *later* cold launch, off the carousel) is handled separately in `MainActivity`'s launcher callback (Task 6) via a Snackbar→Settings fallback — the in-carousel "Open Settings" only covers the on-carousel case.
> **Accessibility/lifecycle:** `rememberPagerState` preserves the page across config change/process death; reduced motion uses `scrollToPage` (instant) vs `animateScrollToPage`; the decorative emoji uses `clearAndSetSemantics {}` (NOT `contentDescription = ""`, which would still announce) and the page dots carry no semantics.

- [ ] **Step 1: Write the implementation**

Create `OnboardingScreen.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * One-time first-launch tutorial. Teaches walk -> spend -> battle, then asks for
 * step-counting permission with context on the final slide. Driven by callbacks from
 * MainActivity; the ViewModel supplies the slide list and persists completion.
 *
 * @param stepCountingGranted whether ACTIVITY_RECOGNITION is currently held.
 * @param permissionAsked whether the permission dialog has been shown this session.
 * @param reducedMotion honor the system reduce-animations setting.
 * @param onEnableStepCounting fire the system permission request (owned by MainActivity).
 * @param onOpenAppSettings open the app's system settings page (denial recovery).
 * @param onFinished persist+navigate away (MainActivity decides Home vs. back-to-Settings).
 */
@Composable
fun OnboardingScreen(
    stepCountingGranted: Boolean,
    permissionAsked: Boolean,
    reducedMotion: Boolean,
    onEnableStepCounting: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val slides = viewModel.slides
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val lastIndex = slides.lastIndex

    fun goTo(page: Int) {
        scope.launch {
            if (reducedMotion) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
        }
    }

    fun finish() {
        viewModel.completeOnboarding()
        onFinished()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {

            // Top bar: Skip (non-final slides only) jumps to the permission primer.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (pagerState.currentPage < lastIndex) {
                    TextButton(onClick = { goTo(lastIndex) }) { Text("Skip") }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val slide = slides[page]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Emoji icon is decorative — the title/body carry the meaning for TalkBack.
                    // clearAndSetSemantics{} (NOT semantics{contentDescription=""}) actually
                    // removes the auto-generated text node from the a11y tree.
                    Text(
                        slide.icon,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(slide.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        slide.body,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Page dots.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(slides.size) { i ->
                    val active = i == pagerState.currentPage
                    // Decorative dot — a single Box with a background, no inner Surface, no
                    // semantics (it carries no text, so nothing to hide from TalkBack).
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else Color.Gray.copy(alpha = 0.4f)
                            ),
                    )
                }
            }

            // Bottom controls.
            if (pagerState.currentPage < lastIndex) {
                Button(
                    onClick = { goTo(pagerState.currentPage + 1) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Next") }
            } else {
                // Final (permission primer) slide.
                // ORDER MATTERS: stepCountingGranted is checked FIRST so a replay where the
                // permission is already held shows the satisfied state and does NOT re-ask
                // (spec §5). Only if not granted do we branch on whether we've asked yet.
                when {
                    stepCountingGranted -> {
                        Text(
                            "Step counting enabled ✓",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start playing")
                        }
                    }
                    !permissionAsked -> {
                        Button(onClick = onEnableStepCounting, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable step counting")
                        }
                    }
                    else -> {
                        // Asked but denied — give an explicit recovery path, never strand the player.
                        Text(
                            "Step counting is off. You can enable it any time in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Settings")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Continue without step counting")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./run-gradle.sh assembleDebug > /tmp/onb-build.log 2>&1; tail -n 20 /tmp/onb-build.log`
Expected: `BUILD SUCCESSFUL`. (Fixes any unresolved `HorizontalPager`/`rememberPagerState` import — they resolve transitively via material3; if assemble reports them unresolved, add `implementation(libs.compose.foundation)` per the optional step in Task 8's notes — but verify first, it should resolve.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/onboarding/OnboardingScreen.kt
git commit -m "feat(onboarding): carousel + permission primer screen with skip/deny recovery"
```

---

## Task 6: Wire onboarding into MainActivity (start destination, gating, route, launcher state)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt`

> This is the load-bearing edit. Changes, precisely:
> 1. Inject `OnboardingPreferences`; read it synchronously to compute `startDestination` via the pure helper.
> 2. Hoist `mutableState` for `onboardingComplete`, `permissionAsked`, `stepCountingGranted`, plus a `SnackbarHostState` and `showStepPermissionSettingsHint` for the permanently-denied recovery affordance (spec §4).
> 3. Update the `permissionLauncher` callback to record `permissionAsked`/`stepCountingGranted`, keep the granted-path behavior, AND detect permanent denial (denied result + `!shouldShowRequestPermissionRationale`) on the post-onboarding path to trigger the Settings-recovery hint.
> 4. Gate **only** the cold multi-permission request branch on `onboardingComplete` — leave service-start, HC-chain, and the deep-link push ungated. (Permanent-denial detection lives in the callback, which fires even on the silent no-op `launch()` returns for a permanently-denied permission — so a single detection point covers both the in-carousel ask and the cold re-prompt.)
> 5. Wire the Scaffold `snackbarHost` + a `LaunchedEffect` that shows "Step counting is off — Enable in Settings" (deep-linking to `ACTION_APPLICATION_DETAILS_SETTINGS`) when the hint fires. This is the spec §4 fallback that replaces the current silent no-op.
> 6. Gate the deep-link collector on the **live nav state** (current route == Onboarding) so it does not navigate over the carousel on first launch OR Settings replay.
> 7. Add the `composable(Screen.Onboarding.route)` route, passing the callbacks; completion goes to Home (first launch) or back to Settings (replay) via `previousBackStackEntry`.

- [ ] **Step 1: Add the import + injected preference**

Add to the import block (near the other `data.*` imports, e.g. after the `HealthConnectClientWrapper` import at line 35):

```kotlin
import com.whitefang.stepsofbabylon.data.onboarding.OnboardingPreferences
import com.whitefang.stepsofbabylon.presentation.onboarding.OnboardingScreen
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

> `getValue` is ALREADY imported (line 22) — do NOT re-add it (a duplicate import is a Kotlin
> compile error). `remember` is already imported (line 23). `ContextCompat` is already imported
> (line 26). Add only the lines above that are genuinely missing.

Add to the `@Inject` block (after `musicPreferences`, line 72):

```kotlin
    @Inject lateinit var onboardingPreferences: OnboardingPreferences
```

- [ ] **Step 2: Hoist onboarding/permission state inside setContent**

Inside `setContent { StepsOfBabylonTheme { … } }`, right after `val navController = rememberNavController()` (line 97), add:

```kotlin
                var onboardingComplete by remember {
                    mutableStateOf(onboardingPreferences.hasCompletedOnboarding())
                }
                var permissionAsked by remember { mutableStateOf(false) }
                var stepCountingGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACTIVITY_RECOGNITION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                // Permanently-denied recovery (spec §4): when set, the Scaffold snackbar offers a
                // deep-link to app settings instead of the current silent no-op. Reset after shown.
                val snackbarHostState = remember { SnackbarHostState() }
                var showStepPermissionSettingsHint by remember { mutableStateOf(false) }
```

> `context` is defined at line 96 (`val context = LocalContext.current`) — these lines come after it. Move them below the `val context` line if ordering complains.

- [ ] **Step 3: Update the permissionLauncher callback to record result state**

Replace the existing `permissionLauncher` block (lines 103-115) with:

```kotlin
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val activityGranted = results[Manifest.permission.ACTIVITY_RECOGNITION] == true
                    permissionAsked = true
                    stepCountingGranted = activityGranted
                    if (activityGranted) {
                        context.startForegroundService(
                            Intent(context, StepCounterService::class.java)
                        )
                        if (healthConnectWrapper.isAvailable()) {
                            hcPermissionLauncher.launch(healthConnectWrapper.getRequiredPermissions())
                        }
                    } else if (onboardingComplete &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION
                        )
                    ) {
                        // Permanently denied ("Don't ask again") AND past onboarding: a bare
                        // launch() is now a silent no-op, so surface the Settings-recovery hint
                        // instead of stranding the player (spec §4). During onboarding itself the
                        // carousel's own "Open Settings" affordance handles this, so we don't
                        // double up while !onboardingComplete.
                        showStepPermissionSettingsHint = true
                    }
                }
```

- [ ] **Step 4: Gate ONLY the cold-request branch in the first-launch LaunchedEffect**

In the `LaunchedEffect(Unit)` (lines 117-144), change only the cold-request `if` (lines 134-140). Replace:

```kotlin
                    if (!activityGranted || !notifGranted) {
                        val needed = buildList {
                            if (!activityGranted) add(Manifest.permission.ACTIVITY_RECOGNITION)
                            if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(needed.toTypedArray())
                    }
```

with:

```kotlin
                    // Gate ONLY the cold request: on a fresh install (onboarding not yet
                    // complete) the onboarding final slide owns the first ask, so we must
                    // not fire a context-free system dialog over the carousel. On later
                    // launches (onboarding complete) this resumes its normal re-prompt role —
                    // and because permanent-denial recovery lives in the launcher callback
                    // (Step 3), the previously-silent no-op now surfaces the Settings hint.
                    if (onboardingComplete && (!activityGranted || !notifGranted)) {
                        val needed = buildList {
                            if (!activityGranted) add(Manifest.permission.ACTIVITY_RECOGNITION)
                            if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(needed.toTypedArray())
                    }
```

> Leave the service-start (125-128), HC-chain (129-131), and deep-link push (142-143) exactly as they are — they no-op on a fresh install and must still run post-grant.

- [ ] **Step 5: Gate the deep-link collector on the live nav state (not the flag)**

Replace the body of the `pendingNavigation.collect { route -> … }` block (lines 146-160) with the
version below. It keys on the **current route**, not `onboardingComplete`, so a deep-link is dropped
whenever the carousel is on top — covering BOTH first launch AND a Settings replay (where
`onboardingComplete` is already `true` but the carousel is showing):

```kotlin
                LaunchedEffect(Unit) {
                    pendingNavigation.collect { route ->
                        if (route != null) {
                            // Don't navigate over the onboarding carousel (first launch OR replay).
                            // A brand-new install has no scheduled notifications to deep-link from,
                            // and during a replay the user is mid-tutorial — drop rather than
                            // buffer; the route is reissued by the notification tap if it recurs.
                            val onOnboarding = navController.currentBackStackEntry
                                ?.destination?.route == Screen.Onboarding.route
                            if (!onOnboarding) {
                                Screen.fromRoute(route)
                                    ?.takeIf { it.route in Screen.argumentFreeRoutes }
                                    ?.let { navController.navigate(it.route) }
                            }
                            pendingNavigation.value = null
                        }
                    }
                }
```

- [ ] **Step 5b: Wire the Scaffold snackbar host + the permanent-denial recovery hint**

The existing `Scaffold(...)` (line 162) has only a `bottomBar`. Add a `snackbarHost` and a
`LaunchedEffect` that shows the recovery snackbar when `showStepPermissionSettingsHint` fires. Add the
`snackbarHost` parameter to the `Scaffold` call:

```kotlin
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
```

…and just inside the `Scaffold { innerPadding -> … }` content lambda (before the `NavHost`), add:

```kotlin
                    LaunchedEffect(showStepPermissionSettingsHint) {
                        if (showStepPermissionSettingsHint) {
                            val result = snackbarHostState.showSnackbar(
                                message = "Step counting is off — enable it in Settings",
                                actionLabel = "Settings",
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            }
                            showStepPermissionSettingsHint = false
                        }
                    }
```

> This is the spec §4 fallback: it replaces the previously-silent post-onboarding no-op. The same
> `ACTION_APPLICATION_DETAILS_SETTINGS` deep-link is reused by the in-carousel `onOpenAppSettings`
> (Step 7), so the recovery action is identical on both paths.

- [ ] **Step 6: Make the start destination conditional**

In the `NavHost(...)` call, change line 187 from:

```kotlin
                        startDestination = Screen.Home.route,
```

to:

```kotlin
                        startDestination = Screen.startDestination(onboardingComplete),
```

> `onboardingComplete`'s initial value is the synchronous prefs read from Step 2, so NavHost captures the correct route on first composition (no async StateFlow default race).

- [ ] **Step 7: Add the onboarding route composable**

Inside the `NavHost { … }` body, add a new `composable` (e.g. right after the `Screen.Home` block, before `Screen.Workshop`):

```kotlin
                        composable(Screen.Onboarding.route) {
                            val reducedMotion = remember {
                                com.whitefang.stepsofbabylon.presentation.battle.effects
                                    .ReducedMotionCheck.isReducedMotionEnabled(context)
                            }
                            OnboardingScreen(
                                stepCountingGranted = stepCountingGranted,
                                permissionAsked = permissionAsked,
                                reducedMotion = reducedMotion,
                                onEnableStepCounting = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACTIVITY_RECOGNITION,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    )
                                },
                                onOpenAppSettings = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        )
                                    )
                                },
                                onFinished = {
                                    onboardingComplete = true
                                    if (navController.previousBackStackEntry == null) {
                                        // First launch: Onboarding was the start destination.
                                        // Go to Home and clear onboarding from the back stack.
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        // Replay from Settings: return to Settings.
                                        navController.popBackStack()
                                    }
                                },
                            )
                        }
```

> `Settings` is already imported (`android.provider.Settings`, line 7). `ReducedMotionCheck` is referenced fully-qualified to avoid touching the import block twice; you may add a top import instead.

- [ ] **Step 8: Verify it compiles**

Run: `./run-gradle.sh assembleDebug > /tmp/onb-build2.log 2>&1; tail -n 20 /tmp/onb-build2.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Run the full JVM suite to confirm no regressions**

Run: `./run-gradle.sh testDebugUnitTest > /tmp/onb-test.log 2>&1; tail -n 25 /tmp/onb-test.log`
Expected: `BUILD SUCCESSFUL`, all tests pass (including the pre-existing `DeepLinkRoutingTest`).

- [ ] **Step 9b: Pin the `navigate_to` → `pendingNavigation` contract (spec §6)**

The deep-link *push* (`intent.getStringExtra("navigate_to")` → `pendingNavigation.value`) must keep
working regardless of onboarding state — the gating only affects the *collector*, not the push. The
extraction half is already covered by `DeepLinkRoutingTest`. Add two explicit guard tests to it (it's
the JVM/Robolectric home for this contract) documenting that the route string survives extraction so
the (gated) collector can decide:

In `app/src/test/java/com/whitefang/stepsofbabylon/presentation/DeepLinkRoutingTest.kt`, add:

```kotlin
    @Test
    fun `navigate_to onboarding route is NOT a valid deep-link target`() {
        // The push extracts any string, but the collector gates on argumentFreeRoutes —
        // onboarding must never be reachable as a public deep-link even if injected.
        val intent = Intent().putExtra("navigate_to", "onboarding")
        assertEquals("onboarding", intent.getStringExtra("navigate_to"))
        assertFalse("onboarding" in Screen.argumentFreeRoutes)
    }

    @Test
    fun `navigate_to store remains a valid deep-link target`() {
        // Regression: a real notification deep-link still resolves + passes the allowlist.
        val intent = Intent().putExtra("navigate_to", "store")
        val route = intent.getStringExtra("navigate_to")
        assertSame(Screen.Store, Screen.fromRoute(route))
        assertTrue(route in Screen.argumentFreeRoutes)
    }
```

> `assertFalse`/`assertTrue`/`assertSame`/`assertEquals` and the `Screen` import are already present in
> `DeepLinkRoutingTest`. Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.DeepLinkRoutingTest"` → PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/DeepLinkRoutingTest.kt
git commit -m "feat(onboarding): wire first-launch flow into MainActivity

Conditional start destination via synchronous prefs read; gate only the
cold-permission request branch behind completion + deep-link collector on
live nav state; permanent-denial recovery via Snackbar->Settings in the
launcher callback (spec §4); onboarding route owns the first ask;
completion -> Home (first launch) or back to Settings (replay)."
```

---

## Task 7: Settings "Replay tutorial" row

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/NotificationSettingsScreen.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt`

> `NotificationSettingsScreen` currently takes no nav callback. Add an `onReplayTutorial: () -> Unit` param (default `{}` so previews/tests don't break) and a row that invokes it. Per the spec, the completion flag is NOT reset at tap — re-navigating to the carousel is enough; the flag only ever changes on genuine completion (and `completeOnboarding()` is idempotent), so an abandoned replay can't strand a returning player.

- [ ] **Step 1: Add the param + replay row**

In `NotificationSettingsScreen.kt`, change the signature (line 21):

```kotlin
@Composable
fun NotificationSettingsScreen(
    onReplayTutorial: () -> Unit = {},
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
```

Add a "Help" section with the replay row. Insert before the `Spacer(Modifier.height(24.dp))` that precedes the "Data" section (line 49):

```kotlin
        Spacer(Modifier.height(16.dp))
        Text("Help", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedCard(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Replay tutorial", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "See the first-launch walkthrough again",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
```

> `OutlinedCard`, `Row`, `Column`, `Text`, `Spacer`, `Modifier`, `MaterialTheme`, `FontWeight`, `Alignment`, `dp` are all already imported in this file (it uses `material3.*` and `foundation.layout.*`).

- [ ] **Step 2: Wire the callback in MainActivity**

In `MainActivity.kt`, find the Settings route (lines 237-239):

```kotlin
                        composable(Screen.Settings.route) {
                            NotificationSettingsScreen()
                        }
```

Replace with:

```kotlin
                        composable(Screen.Settings.route) {
                            NotificationSettingsScreen(
                                onReplayTutorial = { navController.navigate(Screen.Onboarding.route) },
                            )
                        }
```

- [ ] **Step 3: Verify it compiles**

Run: `./run-gradle.sh assembleDebug > /tmp/onb-build3.log 2>&1; tail -n 20 /tmp/onb-build3.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/NotificationSettingsScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt
git commit -m "feat(onboarding): Settings 'Replay tutorial' row -> re-enter carousel"
```

---

## Task 8: Final verification + docs sync + checkpoint

**Files:**
- Modify (docs): see the doc list below.

> Per the project's PR Task-List Convention, sync current-state docs BEFORE the STATE/RUN_LOG update. This task closes Gate C in the narrative and corrects the "schema" tag.

- [ ] **Step 1: Full clean verification**

Run the whole JVM suite + assemble one more time:

```bash
./run-gradle.sh testDebugUnitTest > /tmp/onb-final-test.log 2>&1; tail -n 25 /tmp/onb-final-test.log
./run-gradle.sh assembleDebug > /tmp/onb-final-build.log 2>&1; tail -n 10 /tmp/onb-final-build.log
```

Expected: both `BUILD SUCCESSFUL`. Record the new JVM test count (previous headline: **960**; this plan adds **11**: 2 prefs + 2 content + 1 viewmodel + 5 routing + 1 deep-link pin (Task 6 Step 9b) → ~971, but **recount from the log's reported test total and use the actual number** — it is authoritative over this estimate).

> **If `assembleDebug` ever reported `HorizontalPager` unresolved** in Task 5/6 (it should not — it's transitive via material3), add the explicit dependency now: in `gradle/libs.versions.toml` add `compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }` under `# Compose`, and in `app/build.gradle.kts` add `implementation(libs.compose.foundation)` near line 210. Re-run assemble. Skip this step if the build already succeeded.

- [ ] **Step 2: Sync current-state docs**

Edit these (touch only what the change actually invalidates):

1. `docs/steering/source-files.md` — add entries for the 4 new source files (`OnboardingPreferences`, `OnboardingSlide`/`OnboardingContent`, `OnboardingViewModel`, `OnboardingScreen`).
2. `docs/steering/structure.md` — add the `presentation/onboarding/` and `data/onboarding/` packages to the structural reference.
3. `CLAUDE.md` — update the headline test count (Testing section) to the new number from Step 1; add `onboarding/` to the architecture tree under `presentation/` and `data/`.
4. `CHANGELOG.md` — add a section under `[Unreleased]` describing the onboarding feature; update the test count.
5. `docs/plans/plan-FORWARD.md` — tick the Gate C checkbox (line 45): change `- [ ]` to `- [x]` and note "satisfied-by onboarding PR".

- [ ] **Step 3: Correct the STATE.md schema tag + close-out**

In `docs/agent/STATE.md`:
- Move #24 from "Top priorities / next actions" into "Recently shipped (newest first)".
- **Remove the "(Gate C, schema)" qualifier** in BOTH places it appears: the "next actions" line (~120) and the objective paragraph (~23-24). #24 needed no schema bump.
- Add a fragile-zone bullet: onboarding completion flag is device-local SharedPreferences (`OnboardingPreferences`), intentionally not Room; the cold-permission request branch in `MainActivity` is gated on completion — don't ungate it or the carousel collides with the system dialog.

Also add the cloud-save follow-up where the #36 implementer will actually read it — in
`docs/plans/plan-V1X-roadmap.md`, under the **V1X-12** (cloud save / #36) section, add a one-line
restore note: *"Cloud restore must not re-onboard a progressed player — gate Onboarding on
`!hasCompletedOnboarding && totalStepsEarned == 0` (PlayerRepository is injected in MainActivity),
since OnboardingPreferences is device-local and does not sync. Per onboarding spec §7."*

- [ ] **Step 4: Decide #24 issue disposition**

Per spec §9: Gate C is closed but #24's broader retention scope (wave-5 celebration, D2/D7 push, projected-reward estimates) remains deferred. Keep #24 OPEN with a scope-update comment noting the Gate-C slice shipped and the retention half is deferred (pairs with telemetry #23). Draft the comment text in the PR description; do not auto-close.

- [ ] **Step 5: Run the checkpoint skill for the memory writes**

This handles STATE.md + RUN_LOG.md append + optional ADR (the welcome-Steps-bonus rejection is worth a one-line RUN_LOG/ADR note, per spec §9):

Invoke the `/checkpoint` skill (or follow its steps): append a `RUN_LOG.md` entry describing the onboarding PR; record the explain-only / no-Steps-grant decision and the SharedPreferences-not-Room decision.

- [ ] **Step 6: Commit the docs**

```bash
git add docs/ CLAUDE.md CHANGELOG.md
git commit -m "docs: sync state for onboarding (Gate C); correct #24 schema tag

Gate C ticked in plan-FORWARD; #24 moved to Recently shipped; '(Gate C,
schema)' qualifier removed (flag is device-local SharedPreferences, no
schema bump); headline test count updated; onboarding packages added to
source-files/structure; CHANGELOG entry."
```

- [ ] **Step 7: Push the branch + open the PR**

```bash
git push -u origin feat/onboarding-gate-c
gh pr create --title "feat(onboarding): first-launch tutorial + permission primer (Gate C / #24)" \
  --body "Closes Gate C of the Closed-Test Readiness Gate. Gate-C slice of V1X-22 (#24 stays open for deferred retention scope). See docs/superpowers/specs/2026-06-11-onboarding-gate-c-design.md."
```

---

## Self-Review checklist (completed during authoring)

- **Spec coverage:** carousel (Task 5) · permission primer + Skip contract + deny recovery — in-carousel "Open Settings" (Task 5) AND the post-onboarding permanent-denial Snackbar→Settings fallback in the launcher callback (Task 6 Steps 3/5b), satisfying spec §4 · SharedPreferences flag no-schema (Task 1) · synchronous start-destination helper (Tasks 3/6) · gate only cold-request branch + live-nav-state deep-link gate (Task 6 Steps 4/5) · launcher-as-callback (Task 6) · Settings replay + no-flag-flip-at-tap (Task 7) · already-granted replay shows satisfied state, no re-ask — `when{}` checks `stepCountingGranted` first (Task 5) · route kept out of allowlist (Task 3) · no Hilt module (Task 1) · reduced-motion + rememberPagerState + `clearAndSetSemantics` decorative emoji (Task 5) · tests via Mockito mock + Robolectric prefs + pure content + `navigate_to`→`pendingNavigation` pin (Tasks 1-4, 6 Step 9b) · cloud-save (#36) restore-gate note in V1X-12 + doc sync incl. schema-tag correction + #24 stays open (Task 8).
- **Coverage honesty:** every spec §-item maps to a task EXCEPT the load-bearing MainActivity *runtime branches* (gate polarity, start-destination selection, deep-link drop-while-on-carousel, Snackbar fallback) which are **device/instrumented-verified only** — they live in `@Composable`/Activity code with no pure-JVM seam. The `startDestination()` decision is the one piece extracted to a JVM-tested pure helper (Task 3); the rest is verified by the assemble build + manual device check. This is a known under-test, called out rather than papered over.
- **Type consistency:** `OnboardingPreferences.hasCompletedOnboarding()/setCompleted()/reset()` used identically across Tasks 1/3/4/6. `OnboardingViewModel.slides`/`completeOnboarding()` consistent Tasks 4/5. `Screen.startDestination(Boolean)`/`Screen.Onboarding.route` consistent Tasks 3/6. `OnboardingScreen(stepCountingGranted, permissionAsked, reducedMotion, onEnableStepCounting, onOpenAppSettings, onFinished, viewModel)` signature identical Tasks 5/6. `NotificationSettingsScreen(onReplayTutorial, viewModel)` consistent Task 7.
- **No placeholders:** every code step shows complete code; every run step has a command + expected result.
