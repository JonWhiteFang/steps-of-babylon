# Look & Feel Bundle C (#162) — Feedback / Feel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Steps of Babylon feel tactile — add haptics (purchase/equip/claim/battle-start/pause), a bigger shared purchase pulse, a Post-Round entrance + staggered reward sting, and one-shot claim celebrations — all behind a new Settings "Haptics" toggle, presentation-only.

**Architecture:** A new shared feel-layer in `presentation/ui/` (`Haptics`, `PurchasePulse`, `ClaimCelebration`) + one `data/HapticsPreferences` SharedPreferences flag. Haptics fire via `View.performHapticFeedback`; claim celebrations fire from a new conflated one-shot event on the two claim ViewModels, gated on `Result.Success`. Zero engine/economy/domain-logic change.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, SharedPreferences, kotlinx-coroutines (Channel/Flow), JUnit5 + Robolectric + kotlinx-coroutines-test.

**Source spec:** `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-c-design.md` (passed the Adversarial Review Gate — 25 findings applied, commit `67cdbe1`). Read §3 (decisions D1–D10), §6 (wiring map), §8 (test harness) before starting.

**Baseline:** 981 JVM + 9 instrumented tests green; schema v12; branch off `main` (`ce70351`).

---

## File Structure (decomposition)

**New (production):**
- `data/HapticsPreferences.kt` — on/off flag (SharedPreferences `"haptics_prefs"`, default true). Mirrors `SoundPreferences`.
- `presentation/ui/Haptics.kt` — `Haptics` class (`tap()`/`success()`) + `rememberHaptics()` composable. Thin View-layer helper over `performHapticFeedback`.
- `presentation/ui/PurchasePulse.kt` — `PulseState` + `rememberPulse()` + `Modifier.pulseScale()`. Extracts & enlarges the `UpgradeCard` pulse (1.05→1.12×).
- `presentation/ui/ClaimCelebration.kt` — `data class ClaimCelebrationEvent(val label: String)` + `ClaimCelebration(event, onConsumed)` one-shot reward chip.

**New (test):**
- `test/.../data/HapticsPreferencesTest.kt`

**Modified (production):** `UpgradeCard.kt`, `StoreScreen.kt`, `UltimateWeaponScreen.kt`, `CardsScreen.kt`, `LabsScreen.kt`, `InRoundUpgradeMenu.kt`, `HomeScreen.kt`, `BattleScreen.kt`, `PostRoundOverlay.kt`, `PauseOverlay.kt`, `MissionsViewModel.kt`, `MissionsScreen.kt`, `UnclaimedSuppliesViewModel.kt`, `UnclaimedSuppliesScreen.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`. (NOT `WorkshopScreen.kt` — its spend lives in `UpgradeCard`.)

**Modified (test):** `MissionsViewModelTest.kt`, `UnclaimedSuppliesViewModelTest.kt`.

**Untouched (fragile zones):** `presentation/battle/{engine,entities,effects}`, `BattleViewModel.kt`, `RoundEndState`/`BattleUiState`, all `data/` except the new prefs file, `service/`, `domain/`, `Screen.kt` routes.

**Task ordering rationale:** shared helpers first (Tasks 1–3), then wire them (Tasks 4–5, 9–10), VM event logic with its tests interleaved (Tasks 6–8). Each task is independently committable.

---

## Task 1: `HapticsPreferences` (the on/off flag)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/data/HapticsPreferences.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/HapticsPreferencesTest.kt`

- [ ] **Step 1: Write the failing test** (mirrors `MusicPreferencesTest`'s Robolectric mechanics)

`app/src/test/java/com/whitefang/stepsofbabylon/data/HapticsPreferencesTest.kt`:
```kotlin
package com.whitefang.stepsofbabylon.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HapticsPreferencesTest {

    @Test
    fun `defaults to enabled and round-trips`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = HapticsPreferences(context)

        assertTrue(prefs.isEnabled())   // default ON

        prefs.setEnabled(false)
        assertFalse(prefs.isEnabled())

        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.HapticsPreferencesTest"`
Expected: FAIL — `HapticsPreferences` unresolved reference (does not compile yet).

- [ ] **Step 3: Write minimal implementation** (verbatim mirror of `SoundPreferences.kt`)

`app/src/main/java/com/whitefang/stepsofbabylon/data/HapticsPreferences.kt`:
```kotlin
package com.whitefang.stepsofbabylon.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local on/off flag for haptic feedback (Bundle C, #162). Default ON. Mirrors
 * [SoundPreferences] exactly: a @Singleton SharedPreferences wrapper, no Hilt module needed
 * (constructor injection auto-provides it). The Settings ViewModel writes it; the
 * `rememberHaptics()` composable reads it (both resolve the same process-cached
 * "haptics_prefs" instance, so a toggle takes effect on the next tap with no restart).
 */
@Singleton
class HapticsPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("haptics_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean("enabled", enabled).apply()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.HapticsPreferencesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/HapticsPreferences.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/HapticsPreferencesTest.kt
git commit -m "feat(haptics): HapticsPreferences on/off flag (#162, Bundle C Task 1)"
```

---

## Task 2: `Haptics` helper + `rememberHaptics()`

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Haptics.kt`

No JVM test (thin View-layer helper; house norm — the tested logic is `HapticsPreferences`, Task 1).

- [ ] **Step 1: Write the implementation**

`app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Haptics.kt`:
```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.whitefang.stepsofbabylon.data.HapticsPreferences

/**
 * Tactile feedback helper (Bundle C, #162). Wraps [View.performHapticFeedback] and gates every
 * pulse on [HapticsPreferences.isEnabled] read at *call* time, so toggling "Haptics" in Settings
 * takes effect on the next tap without recomposition.
 *
 * Uses the no-flag overload: it honours the host view's haptic-enabled state (the Compose default
 * is enabled) and VIRTUAL_KEY additionally honours the system touch-haptic setting — intended.
 * No VIBRATE permission required.
 */
class Haptics(private val view: View, private val prefs: HapticsPreferences) {
    /** Light tick — purchase / equip / battle-start / pause taps. */
    fun tap() {
        if (prefs.isEnabled()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Heavier confirm — claim celebrations + the Post-Round reward sting. */
    fun success() {
        if (prefs.isEnabled()) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) { Haptics(view, HapticsPreferences(context)) }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./run-gradle.sh :app:compileDebugKotlin > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/Haptics.kt
git commit -m "feat(haptics): Haptics helper + rememberHaptics() (#162, Bundle C Task 2)"
```

---

## Task 3: `PurchasePulse` (extract + enlarge the shared spend pulse)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/PurchasePulse.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeCard.kt`

No JVM test (visual; the existing 1.05× pulse was never JVM-tested — verified on device in Task 11).

- [ ] **Step 1: Write the shared pulse**

`app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/PurchasePulse.kt`:
```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck

/**
 * A one-shot "a spend just happened" scale pulse (Bundle C, #162). Extracted from the inline
 * UpgradeCard pulse and enlarged 1.05× → 1.12× (D9). graphicsLayer scale → no layout reflow.
 * Under reduced-motion the spec uses snap() (instant jump, effectively no pulse).
 *
 * Usage:
 *   val pulse = rememberPulse()
 *   Button(onClick = { pulse.trigger(); haptics.tap(); onClick() }, ...) { ... }
 *   ...Modifier.pulseScale(pulse)
 */
class PulseState internal constructor(
    private val scaleState: State<Float>,
    private val setActive: (Boolean) -> Unit,
) {
    internal val scale: Float get() = scaleState.value
    fun trigger() = setActive(true)
}

private const val PULSE_TARGET = 1.12f

@Composable
fun rememberPulse(): PulseState {
    val context = LocalContext.current
    val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(context) }
    var active by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (active) PULSE_TARGET else 1f,
        animationSpec = if (reducedMotion) snap() else tween(100),
        label = "purchasePulse",
    )
    LaunchedEffect(active) {
        if (active) { kotlinx.coroutines.delay(100); active = false }
    }
    return remember { PulseState(scale) { active = it } }
}

fun Modifier.pulseScale(pulse: PulseState): Modifier =
    this.graphicsLayer(scaleX = pulse.scale, scaleY = pulse.scale)
```

> Note: `PulseState` captures the `State<Float>` and the setter; `remember { PulseState(...) }` is safe
> because `scale` reads `.value` lazily (recomposition tracks it), and the `active` setter closure stays
> valid across recompositions.

- [ ] **Step 2: Refactor `UpgradeCard` to consume the shared pulse**

In `app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeCard.kt`, delete the inline pulse (current lines 47–58: the `context`/`reducedMotion`/`pulseActive`/`scale`/`LaunchedEffect` block) and the now-unused imports (`animateFloatAsState`, `snap`, `tween`, `LaunchedEffect`, `mutableStateOf`, `getValue`/`setValue` if unused elsewhere, `graphicsLayer`, `LocalContext`, `ReducedMotionCheck`, `kotlinx.coroutines.delay`). Replace with the shared pulse + haptics. The new top of the composable body:

```kotlin
@Composable
fun UpgradeCard(info: UpgradeDisplayInfo, onClick: () -> Unit) {
    val cardAlpha = if (info.isMaxed) 0.85f else 1f
    val valueAlpha = when {
        info.isMaxed -> 1f
        info.canAfford -> 1f
        else -> 0.55f
    }

    val pulse = rememberPulse()
    val haptics = rememberHaptics()

    Card(
        onClick = {
            if (info.canAfford && !info.isMaxed) {
                pulse.trigger()
                haptics.tap()
                onClick()
            }
        },
        enabled = info.canAfford && !info.isMaxed,
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha).pulseScale(pulse),
        colors = /* unchanged */ ...,
    ) {
        /* unchanged body */
    }
}
```
Add imports: `import com.whitefang.stepsofbabylon.presentation.ui.rememberPulse`, `import com.whitefang.stepsofbabylon.presentation.ui.pulseScale`, `import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics`. Keep `import androidx.compose.ui.draw.alpha` (still used). The `colors = if (info.isMaxed) {...}` block and the whole `Row{...}` body are unchanged.

- [ ] **Step 3: Verify it compiles**

Run: `./run-gradle.sh :app:compileDebugKotlin > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL (no unused-import lint error — confirm in Task 11's lint pass).

- [ ] **Step 4: Run the existing suite** (UpgradeCard behavior must be unchanged bar the scale)

Run: `./run-gradle.sh :app:testDebugUnitTest > build.log 2>&1; tail -n 15 build.log`
Expected: 981 tests, 0 failures (no test references the inline pulse).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/PurchasePulse.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/workshop/UpgradeCard.kt
git commit -m "feat(haptics): shared PurchasePulse (1.12x) + UpgradeCard adopts it & adds tap haptic (#162, Bundle C Task 3)"
```

---

## Task 4: Wire pulse + haptic into the remaining spend / equip / nav sites

**Files (all Modify):** `StoreScreen.kt`, `UltimateWeaponScreen.kt`, `CardsScreen.kt`, `LabsScreen.kt`, `InRoundUpgradeMenu.kt`, `HomeScreen.kt`, `BattleScreen.kt`, `PauseOverlay.kt`. Per §6 wiring map.

No JVM test (visual/tactile; verified on device in Task 11).

**Pattern for a guarded spend `Button`** (Workshop/in-round/UW/Cards/Labs/Store-cosmetic — already `enabled`-gated):
```kotlin
val pulse = rememberPulse()
val haptics = rememberHaptics()
Button(
    onClick = { pulse.trigger(); haptics.tap(); <existing call> },
    enabled = <existing guard>,
    modifier = <existing>.pulseScale(pulse),
) { ... }
```

**Pattern for the 3 real-money Store buttons** (`:77/:106/:137` — NO `enabled` guard; fire inside the launch path guarded by `!isPurchasing`, per §6 guard note):
```kotlin
val pulse = rememberPulse()
val haptics = rememberHaptics()
Button(onClick = {
    if (!state.isPurchasing) { pulse.trigger(); haptics.tap() }
    viewModel.purchaseGemPack(product)   // VM also has its own re-entrancy guard
}, modifier = Modifier.pulseScale(pulse)) { Text("Buy") }
```
(Confirm `StoreUiState` exposes `isPurchasing`; the cosmetic button at `:180` already reads `enabled = !state.isPurchasing` — reuse that field. If the field name differs, use whatever the cosmetic button reads.)

**Equip buttons (haptic only, NO pulse):** UW toggle (`UltimateWeaponScreen.kt:136`), Card equip/unequip (`CardsScreen.kt:174,176`), cosmetic equip/unequip (`StoreScreen.kt:174,175`):
```kotlin
val haptics = rememberHaptics()
Button(onClick = { haptics.tap(); <existing equip call> }, ...) { ... }
```

**BATTLE-start (Home, `HomeScreen.kt:155`):**
```kotlin
val haptics = rememberHaptics()
Button(onClick = { haptics.tap(); onBattleClick() }, ...) { Text("BATTLE", ...) }
```

**Pause (`BattleScreen.kt:197`) + Resume (`PauseOverlay.kt:46`):**
```kotlin
// BattleScreen.kt:197
FilledTonalButton(onClick = { haptics.tap(); viewModel.togglePause() }, ...) { ... }
// PauseOverlay.kt:46
Button(onClick = { haptics.tap(); onResume() }, ...) { Text(stringResource(R.string.action_resume)) }
```
(In `BattleScreen`, declare `val haptics = rememberHaptics()` once near the top of the HUD composable and reuse for pause; `PauseOverlay` declares its own.)

- [ ] **Step 1: Edit `StoreScreen.kt`** — pulse+haptic on `:77/:106/:137` (real-money launch-path pattern), `:180` (cosmetic, guarded pattern); haptic-only on `:174/:175` (equip/unequip). Add the three `presentation.ui` imports.
- [ ] **Step 2: Edit `UltimateWeaponScreen.kt`** — pulse+haptic on unlock `:113` + per-path upgrade `:179` (guarded pattern); haptic-only on the equip toggle `:136`.
- [ ] **Step 3: Edit `CardsScreen.kt`** — pulse+haptic on pack-open `:89` + card upgrade `:179` (guarded); haptic-only on equip `:176` + unequip `:174`.
- [ ] **Step 4: Edit `LabsScreen.kt`** — pulse+haptic on slot-unlock `:66`, rush `:156`, start `:173` (all already `enabled`-guarded).
- [ ] **Step 5: Edit `InRoundUpgradeMenu.kt`** — pulse+haptic on the buy `Button` `:138` (`enabled = affordable` at `:139`). Note this composable takes `onPurchase` from `BattleScreen`; declare `rememberPulse()`/`rememberHaptics()` inside `InRoundUpgradeMenu`.
- [ ] **Step 6: Edit `HomeScreen.kt:155`** — haptic on `onBattleClick`.
- [ ] **Step 7: Edit `BattleScreen.kt:197`** (pause) — haptic; **and `PauseOverlay.kt:46`** (resume) — haptic.

- [ ] **Step 8: Verify it compiles + existing suite green**

Run: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest > build.log 2>&1; tail -n 15 build.log`
Expected: BUILD SUCCESSFUL; 981 tests, 0 failures.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/cards/CardsScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/InRoundUpgradeMenu.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/home/HomeScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/PauseOverlay.kt
git commit -m "feat(haptics): wire pulse+haptic into spend/equip/battle-start/pause sites (#162, Bundle C Task 4)"
```

---

## Task 5: Settings "Haptics" toggle

**Files (Modify):** `SettingsViewModel.kt`, `SettingsScreen.kt`. (No `SettingsViewModelTest` exists; the one-line setter mirrors 6 existing setters — covered by `HapticsPreferences` test.)

- [ ] **Step 1: Add `hapticsPrefs` + state field + setter to `SettingsViewModel.kt`**

Add the import `import com.whitefang.stepsofbabylon.data.HapticsPreferences`. Add `val hapticsEnabled: Boolean = true` to `SettingsState`. Add the constructor param and wiring:
```kotlin
class SettingsViewModel @Inject constructor(
    private val prefs: NotificationPreferences,
    private val soundPrefs: SoundPreferences,
    private val musicPrefs: MusicPreferences,
    private val hapticsPrefs: HapticsPreferences,   // NEW
    private val dataDeletionManager: DataDeletionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(
        // ...existing...
        musicVolume = musicPrefs.getVolume(),
        hapticsEnabled = hapticsPrefs.isEnabled(),   // NEW
    ))
    // ...
    fun setHapticsEnabled(enabled: Boolean) { hapticsPrefs.setEnabled(enabled); _state.update { it.copy(hapticsEnabled = enabled) } }
}
```

- [ ] **Step 2: Add the `ToggleRow` to `SettingsScreen.kt`** (in the "Sound" group, after the music rows at `:39`/`:49`):
```kotlin
ToggleRow("Haptic Feedback", "Vibrate on taps, claims, and rewards", state.hapticsEnabled, viewModel::setHapticsEnabled)
```

- [ ] **Step 3: Verify it compiles + existing suite green**

Run: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest > build.log 2>&1; tail -n 15 build.log`
Expected: BUILD SUCCESSFUL; 981 tests, 0 failures (Hilt provides `HapticsPreferences` via constructor injection — no module change).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsViewModel.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/settings/SettingsScreen.kt
git commit -m "feat(haptics): Settings 'Haptic Feedback' toggle (#162, Bundle C Task 5)"
```

---

## Task 6: `ClaimCelebration` + `ClaimCelebrationEvent`

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ClaimCelebration.kt`

No JVM test (thin Compose helper; the tested logic is the VM emission, Tasks 7–8).

- [ ] **Step 1: Write the event type + composable**

`app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ClaimCelebration.kt`:
```kotlin
package com.whitefang.stepsofbabylon.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck
import kotlinx.coroutines.delay

/** One-shot claim/reward payload (Bundle C, #162). A flat, pre-formatted label built in the VM. */
data class ClaimCelebrationEvent(val label: String)

/**
 * Brief one-shot reward chip shown when a claim succeeds. Scales+fades in, fires a success haptic
 * once on appearance, auto-dismisses after ~1.4s, then calls [onConsumed] to clear the VM event.
 * Under reduced-motion it appears/disappears instantly (no scale/fade) but the haptic still fires.
 */
@Composable
fun ClaimCelebration(event: ClaimCelebrationEvent?, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(context) }
    val haptics = rememberHaptics()

    LaunchedEffect(event) {
        if (event != null) {
            haptics.success()
            delay(1400)
            onConsumed()
        }
    }

    Box(Modifier.fillMaxSize().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = event != null,
            enter = if (reducedMotion) fadeIn(snapSpec()) else scaleIn() + fadeIn(),
            exit = if (reducedMotion) fadeOut(snapSpec()) else fadeOut(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    event?.label ?: "",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun snapSpec() = androidx.compose.animation.core.snap<Float>()
```

> The chip reads `event.label` (D10). Hosting screens overlay it in their root layout (Task 9). The
> `AnimatedVisibility` shows while `event != null`; `onConsumed` clears the VM event after the dwell.

- [ ] **Step 2: Verify it compiles**

Run: `./run-gradle.sh :app:compileDebugKotlin > build.log 2>&1; tail -n 20 build.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/ClaimCelebration.kt
git commit -m "feat(haptics): ClaimCelebration one-shot reward chip + event type (#162, Bundle C Task 6)"
```

---

## Task 7: Supplies celebration event (VM, with TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesViewModelTest.kt`

This VM has **no ticker** → it's directly constructible (it already is in the existing test). Do this one before Missions to bank the simpler harness.

- [ ] **Step 1: Write the failing tests** (live-count via `backgroundScope`, per §8)

Append to `UnclaimedSuppliesViewModelTest.kt` (add imports `import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent`, `import kotlinx.coroutines.flow.toList`):
```kotlin
    @Test
    fun `claimDrop emits one celebration on success`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.claimDrop(vm.uiState.value.drops.first())
        advanceUntilIdle()
        assertEquals(1, events.size)
    }

    @Test
    fun `claimAll emits exactly one aggregate celebration for N drops`() = runTest(dispatcher) {
        encounterRepo.createDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5)
        encounterRepo.createDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, 100)
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.claimAll()
        advanceUntilIdle()
        assertEquals(1, events.size)   // one aggregate, NOT N
    }

    @Test
    fun `claimAll on an empty batch emits no celebration`() = runTest(dispatcher) {
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.claimAll()   // no drops → no Success → no event
        advanceUntilIdle()
        assertTrue(events.isEmpty())
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.supplies.UnclaimedSuppliesViewModelTest"`
Expected: FAIL — `vm.celebration` unresolved.

- [ ] **Step 3: Implement the event on the VM**

`UnclaimedSuppliesViewModel.kt` (add imports `import com.whitefang.stepsofbabylon.domain.usecase.ClaimSupplyDrop.Result`, `import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent`, `import kotlinx.coroutines.channels.Channel`, `import kotlinx.coroutines.flow.receiveAsFlow`):
```kotlin
    private val _celebration = Channel<ClaimCelebrationEvent>(Channel.CONFLATED)
    val celebration = _celebration.receiveAsFlow()

    fun claimDrop(drop: SupplyDrop) {
        viewModelScope.launch {
            if (claimSupplyDrop(drop) is Result.Success) {
                _celebration.trySend(ClaimCelebrationEvent(label = supplyLabel(drop)))
            }
        }
    }

    fun claimAll() {
        viewModelScope.launch {
            val anySuccess = uiState.value.drops.fold(false) { acc, d ->
                (claimSupplyDrop(d) is Result.Success) || acc
            }
            if (anySuccess) _celebration.trySend(ClaimCelebrationEvent(label = "All supplies claimed!"))
        }
    }

    private fun supplyLabel(drop: SupplyDrop): String = when (drop.reward) {
        SupplyDropReward.STEPS -> "+${drop.rewardAmount} Steps claimed!"
        SupplyDropReward.GEMS -> "+${drop.rewardAmount} Gems claimed!"
        SupplyDropReward.POWER_STONES -> "+${drop.rewardAmount} Power Stones claimed!"
        SupplyDropReward.CARD_COPY -> "Card claimed!"
    }
```
Add `import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward`. Note: in `claimAll`, use `fold` (not `any { }`) so **every** drop is actually claimed — `any` would short-circuit after the first Success and skip the rest.

- [ ] **Step 4: Run to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.supplies.UnclaimedSuppliesViewModelTest"`
Expected: PASS (6 tests: 3 existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesViewModel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesViewModelTest.kt
git commit -m "feat(haptics): supplies claim celebration event, gated on >=1 Success (#162, Bundle C Task 7)"
```

---

## Task 8: Missions + milestones celebration event (VM, with the ticker-safe harness)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsViewModel.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsViewModelTest.kt`

**⚠ Ticker hazard (§8):** `MissionsViewModel.init` runs `while(true){ delay(1000) … }`. The existing test never constructs the VM. We construct it with an injected `FakeTimeProvider`, collect on `backgroundScope`, and **never call `advanceUntilIdle()`** (the loop is never idle) — drain only the claim with `runCurrent()`, then cancel the VM scope in teardown. `backgroundScope` coroutines are exempt from `runTest`'s uncompleted-coroutines check.

- [ ] **Step 1: Write the failing tests**

Append to `MissionsViewModelTest.kt` (add imports `import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider`, `import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent`, `import kotlinx.coroutines.flow.toList`, `import kotlinx.coroutines.launch`, `import kotlinx.coroutines.test.runCurrent`, `import org.mockito.kotlin.mock`, `import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao`, `import com.whitefang.stepsofbabylon.fakes.FakeCosmeticRepository`):

```kotlin
    private fun createVm() = MissionsViewModel(
        dailyMissionDao = missionDao,
        milestoneDao = milestoneDao,
        dailyStepDao = mock(),
        playerRepository = playerRepo,
        playerProfileDao = mock<PlayerProfileDao>(),
        cosmeticRepository = FakeCosmeticRepository(),
        timeProvider = FakeTimeProvider(fixedDate = java.time.LocalDate.parse(today)),
    )

    @Test
    fun `claiming a completed mission emits one celebration`() = runTest {
        missionDao.insert(DailyMissionEntity(date = today, missionType = DailyMissionType.WALK_5000.name, target = 5000, progress = 5000, completed = true, rewardGems = 5))
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        val id = missionDao.getByDateOnce(today).first().id
        vm.claimMission(id)
        runCurrent()
        assertEquals(1, events.size)
        vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `claiming an achieved milestone emits one celebration`() = runTest {
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        vm.claimMilestone(Milestone.FIRST_STEPS)   // player has 5000 steps >= FIRST_STEPS req
        runCurrent()
        assertEquals(1, events.size)
        vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `claiming an unachievable milestone emits no celebration`() = runTest {
        // SUPER_WALKER requires far more than 5000 steps → InsufficientSteps, snackbar only.
        val vm = createVm()
        val events = mutableListOf<ClaimCelebrationEvent>()
        backgroundScope.launch { vm.celebration.toList(events) }
        vm.claimMilestone(Milestone.entries.maxBy { it.requiredSteps })
        runCurrent()
        assertTrue(events.isEmpty())
        vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
```
(`runTest` here uses the class's `UnconfinedTestDispatcher` set as Main in `@BeforeEach`. `viewModelScope` runs on Main; cancelling its `Job` in-body stops the ticker so the test completes. The `import androidx.lifecycle.viewModelScope`-equivalent: `viewModelScope` is accessible because it's a public extension — if not visible, add a `@VisibleForTesting fun cancelScope()` to the VM that calls `viewModelScope.cancel()` and call that instead.)

- [ ] **Step 2: Run to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.missions.MissionsViewModelTest"`
Expected: FAIL — `vm.celebration` unresolved.

- [ ] **Step 3: Implement the event on the VM**

`MissionsViewModel.kt` (add imports `import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent`, `import com.whitefang.stepsofbabylon.domain.usecase.ClaimMissionResult`, `import kotlinx.coroutines.channels.Channel`, `import kotlinx.coroutines.flow.receiveAsFlow`):
```kotlin
    private val _celebration = Channel<ClaimCelebrationEvent>(Channel.CONFLATED)
    val celebration = _celebration.receiveAsFlow()

    fun claimMission(id: Int) {
        viewModelScope.launch {
            if (claimMissionUseCase(id, today) == ClaimMissionResult.Success) {
                val m = uiState.value.missions.find { it.id == id }
                _celebration.trySend(ClaimCelebrationEvent(label = missionRewardLabel(m)))
            }
        }
    }

    private fun missionRewardLabel(m: MissionDisplayInfo?): String {
        if (m == null) return "Reward claimed!"
        val parts = buildList {
            if (m.rewardGems > 0) add("+${m.rewardGems} Gems")
            if (m.rewardPowerStones > 0) add("+${m.rewardPowerStones} Power Stones")
        }
        return if (parts.isEmpty()) "Reward claimed!" else parts.joinToString(" ") + " claimed!"
    }
```
And in `claimMilestone`, change the Success arm from `Unit` to emit (build the label from the milestone's `rewardsSummary()`):
```kotlin
            when (val result = claimMilestoneUseCase.invoke(milestone)) {
                ClaimMilestoneResult.Success ->
                    _celebration.trySend(ClaimCelebrationEvent(label = "${milestone.rewardsSummary()} claimed!"))
                ClaimMilestoneResult.InsufficientSteps -> userMessage.value = "You haven't walked enough steps yet."
                ClaimMilestoneResult.AlreadyClaimed -> userMessage.value = "Milestone already claimed."
                is ClaimMilestoneResult.UnknownCosmetic -> userMessage.value = "Reward temporarily unavailable (cosmetic “${result.cosmeticId}” is being finalised). Try again after the next update."
            }
```
> The failure arms keep `userMessage` — the 3 `UnknownCosmetic` milestones must never celebrate.
> `uiState.value.missions` is the post-claim snapshot; for the celebration label we read `rewardGems`/
> `rewardPowerStones`, which are unchanged by claiming (only `claimed` flips), so the lookup is valid
> whether or not the Room flow has re-emitted yet.

- [ ] **Step 4: Run to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.missions.MissionsViewModelTest"`
Expected: PASS (existing + 3 new; no `UncompletedCoroutinesError`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsViewModel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsViewModelTest.kt
git commit -m "feat(haptics): missions+milestones claim celebration event (Success-gated) (#162, Bundle C Task 8)"
```

---

## Task 9: Host `ClaimCelebration` in the two claim screens

**Files (Modify):** `MissionsScreen.kt`, `UnclaimedSuppliesScreen.kt`.

No JVM test (visual; the emission is tested in Tasks 7–8).

- [ ] **Step 1: `MissionsScreen.kt`** — collect the event + overlay the chip. Add a state holder and a `LaunchedEffect` collector (mirror the `userMessage` pattern at `:35-40`), wrap the `Scaffold` content in a `Box` and add `ClaimCelebration` on top:
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebration
import com.whitefang.stepsofbabylon.presentation.ui.ClaimCelebrationEvent
// ...
var celebration by remember { mutableStateOf<ClaimCelebrationEvent?>(null) }
LaunchedEffect(Unit) { viewModel.celebration.collect { celebration = it } }
// wrap existing Scaffold in: Box(Modifier.fillMaxSize()) { <Scaffold...>; ClaimCelebration(celebration) { celebration = null } }
```

- [ ] **Step 2: `UnclaimedSuppliesScreen.kt`** — same pattern (collect `viewModel.celebration`, overlay `ClaimCelebration` in the screen's root `Box`/`Column`).

- [ ] **Step 3: Verify it compiles + suite green**

Run: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest > build.log 2>&1; tail -n 15 build.log`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreen.kt
git commit -m "feat(haptics): host ClaimCelebration in Missions + Supplies screens (#162, Bundle C Task 9)"
```

---

## Task 10: Post-Round entrance + staggered reward sting + Play-Again haptic

**Files (Modify):** `PostRoundOverlay.kt`, `BattleScreen.kt`.

No JVM test (Compose HUD; verified on device). **HUD/Compose only — no `BattleViewModel`/`RoundEndState`/engine change.**

- [ ] **Step 1: Wrap the host in `AnimatedVisibility` (entrance, keyed on the transition) — `BattleScreen.kt:226`**

Replace:
```kotlin
state.roundEndState?.let { PostRoundOverlay(state = it, onPlayAgain = { viewModel.playAgain() }, onExitBattle = onExitBattle, onWatchGemAd = { viewModel.watchGemAd() }, onWatchPsAd = { viewModel.watchPsAd() }) }
```
with (key the `MutableTransitionState` on `roundEndState != null` so the in-place `gemAdWatched`/`psAdWatched` copies do NOT re-run the entrance):
```kotlin
val showRoundEnd = remember { MutableTransitionState(false) }
showRoundEnd.targetState = state.roundEndState != null
state.roundEndState?.let { roundEnd ->
    AnimatedVisibility(visibleState = showRoundEnd, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
        PostRoundOverlay(state = roundEnd, onPlayAgain = { viewModel.playAgain() }, onExitBattle = onExitBattle, onWatchGemAd = { viewModel.watchGemAd() }, onWatchPsAd = { viewModel.watchPsAd() })
    }
}
```
Add imports: `androidx.compose.animation.AnimatedVisibility`, `androidx.compose.animation.fadeIn`, `androidx.compose.animation.fadeOut`, `androidx.compose.animation.scaleIn`, `androidx.compose.animation.core.MutableTransitionState`, `androidx.compose.runtime.remember`.
> The `let` content leaves composition when `roundEndState` becomes null (Play Again resets it) — this is the intended **entrance-only** behavior (the exit fade plays only while the data lingers; the design is entrance-only, §3 D3).

- [ ] **Step 2: Add the Play-Again haptic + staggered sting inside `PostRoundOverlay.kt`**

At the top of `PostRoundOverlay`'s body add `val haptics = rememberHaptics()` (import `com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics`). On the Play Again button (`:123`):
```kotlin
Button(onClick = { haptics.tap(); onPlayAgain() }, modifier = Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.postround_play_again))
}
```

Staggered sting over the **present** subset (D3). Compute the present highlight lines, reveal them on a one-shot timer:
```kotlin
val reducedMotion = remember { ReducedMotionCheck.isReducedMotionEnabled(LocalContext.current) }
// Build the ordered list of present highlight lines (record, tier, power-stones, steps).
val highlights: List<@Composable () -> Unit> = buildList {
    if (state.isNewBestWave) add { /* record block (current :61-67) */ }
    state.tierUnlocked?.let { tier -> add { /* tier block (current :69-73) */ } }
    if (state.powerStonesAwarded > 0) add { /* power-stones block (current :75-78) */ }
    if (state.stepsEarned > 0) add { /* steps block (current :80-87) */ }
}
var visibleCount by remember { mutableIntStateOf(if (reducedMotion) highlights.size else 0) }
LaunchedEffect(Unit) {
    if (!reducedMotion) {
        for (i in highlights.indices) { delay(180); visibleCount = i + 1; haptics.success() }
    } else {
        haptics.success()   // one confirm, no stagger
    }
}
// Render: highlights.take(visibleCount).forEach { Spacer(Modifier.height(8.dp)); it() }
```
Imports: `androidx.compose.runtime.mutableIntStateOf`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.getValue`/`setValue`, `kotlinx.coroutines.delay`, `com.whitefang.stepsofbabylon.presentation.battle.effects.ReducedMotionCheck`, `androidx.compose.ui.platform.LocalContext` (already imported). Move the 4 conditional `Text` blocks into the `highlights` lambdas (preserve their exact existing content/colors). The stat block + buttons (`:91+`) render after, unchanged.
> Stagger over `highlights` (present subset), NOT a fixed index 0–3 — a typical round renders 1–2 lines.

- [ ] **Step 3: Verify it compiles + suite green**

Run: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest > build.log 2>&1; tail -n 15 build.log`
Expected: BUILD SUCCESSFUL; all tests pass; `SimulationTest`/`GameEngine*Test` unaffected (no engine change).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/PostRoundOverlay.kt
git commit -m "feat(haptics): Post-Round entrance + staggered reward sting + Play-Again haptic (#162, Bundle C Task 10)"
```

---

## Task 11: Full build, lint, and on-device validation

**Files:** none (validation only).

- [ ] **Step 1: Full unit + lint + assemble**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > build.log 2>&1; tail -n 30 build.log`
Expected: BUILD SUCCESSFUL; 0 test failures; **no `UnusedImports`/`HardcodedText` lint errors** (check the `UpgradeCard` import cleanup from Task 3 landed). Record the new headline JVM test count (981 + new = the count to pin in docs).

- [ ] **Step 2: On-device (emulator API 36)** — install the debug APK and verify (per spec §8):
  - Haptic ticks on: a Workshop purchase **and** one other surface (Store/UW/Cards/Labs), an equip, **Home BATTLE button AND Post-Round "Play Again"**, pause, and a claim.
  - Settings → toggle "Haptic Feedback" OFF → the same taps are silent immediately (no app restart); toggle ON → they return.
  - Pulse: visibly bigger (1.12×) on Workshop **and** now present on Store/UW/Cards/Labs/in-round. Check the tightest grids (Store gem-pack, Cards, in-round rows) for unpleasant neighbor overlap during the pulse — reduce scale on grid surfaces if it reads badly.
  - Post-Round: overlay animates in; **present** reward lines stagger with a tick (run a round with only 1–2 lines — no empty stagger gaps); tapping Watch-Gem-Ad / Watch-PS-Ad does **not** re-trigger the entrance.
  - Claims: a mission, a milestone, and a single supply claim each show the celebration chip; `claimAll` celebrates once; re-tapping an already-empty supplies batch does not celebrate.
  - **Reduced-motion device setting ON** (Developer options → animator duration scale = off): animations collapse to instant, but haptics still fire.

- [ ] **Step 3: Record the validation result** in the RUN_LOG entry (Task 12) — what was checked, on what device, pass/fail per bullet.

---

## Task 12: Docs sync + checkpoint (PR Task-List Convention)

**Files (Modify, in this order — current-state docs BEFORE STATE/RUN_LOG):**

- [ ] **Step 1: `docs/steering/source-files.md`** — add entries for `data/HapticsPreferences.kt`, `presentation/ui/Haptics.kt`, `presentation/ui/PurchasePulse.kt`, `presentation/ui/ClaimCelebration.kt`; update `PostRoundOverlay.kt` (now animated), `MissionsViewModel.kt`/`UnclaimedSuppliesViewModel.kt` (new celebration event), `UpgradeCard.kt` (uses shared pulse), `SettingsViewModel.kt`/`SettingsScreen.kt` (haptics toggle).
- [ ] **Step 2: `docs/steering/structure.md`** — note the `presentation/ui/` feel-helper trio (`Haptics`/`PurchasePulse`/`ClaimCelebration`) joins the shared-component layer.
- [ ] **Step 3: `CLAUDE.md`** — update the headline test count (981 → the Task 11 number). No architecture/convention change.
- [ ] **Step 4: `CHANGELOG.md`** — add a Bundle C entry under `[Unreleased]` (haptics + Post-Round/claim celebration + shared 1.12× purchase pulse; presentation-only; new test count).
- [ ] **Step 5: `docs/agent/STATE.md`** — add to "Recently shipped"; add the fragile-zone note: *"Haptics + feel centralized in `presentation/ui/` (`Haptics`/`PurchasePulse`/`ClaimCelebration`); claim celebrations fire from a conflated VM event gated on `Result.Success` (never on failure/`UnknownCosmetic`; `claimAll` only on ≥1 Success); Post-Round entrance keyed on the round-end transition, not `RoundEndState` identity (watch-ad copies must not re-trigger it). Haptics via `View.performHapticFeedback`, gated by `HapticsPreferences` (Settings toggle, default ON)."* Update the look-&-feel-bundles priority line (Bundle C done; #163/#164 remain).
- [ ] **Step 6: Append `docs/agent/RUN_LOG.md`** — session entry: what shipped, the adversarial-review-then-fix history, the ticker-safe test harness, on-device result, test-count delta.
- [ ] **Step 7: ADR decision** — default **no ADR** (implements ADR-0022 feel direction). If the conflated-claim-event is judged the first event-bus worth recording, add a short ADR; otherwise cross-reference spec §3 D2/D6 in the RUN_LOG.
- [ ] **Step 8: Run `/checkpoint`** to finalize the memory writes, then open the PR (close #162 on merge; note the deferred SFX sub-item belongs to the audio-debt track).

```bash
git add -A && git commit -m "docs: Bundle C (#162) — sync current-state docs + STATE/RUN_LOG (Task 12)"
```

---

## Self-review notes (coverage vs. spec)

- **§1 in-scope items** → Tasks 1–10 (haptics infra T1–2; pulse T3–4; battle-start incl. Play-Again T4/T10; pause T4; Post-Round T10; claim celebration T6–9; Settings toggle T5). ✓
- **§3 decisions** → D1 (T5), D2 (T2), D3 present-subset stagger (T10), D4 1.12× everywhere (T3–4), D5/D6 (T7–8), D7 ≥1-Success `claimAll` (T7), D9 (T3), D10 `ClaimCelebrationEvent` (T6). ✓
- **§6 wiring map** → T4 (real-money Store launch-path guard; equip haptic-only; Labs sites) + T10 (Play-Again). WorkshopScreen correctly NOT touched. ✓
- **§8 test harness** → Supplies live-count (T7); Missions ticker-safe `backgroundScope`+`runCurrent`+scope-cancel (T8); `HapticsPreferences` Robolectric (T1); no Compose-helper tests (house norm). ✓
- **Fragile zones** → no engine/economy/domain/`Screen.kt`/`BattleViewModel` change; Post-Round is HUD-only. ✓
