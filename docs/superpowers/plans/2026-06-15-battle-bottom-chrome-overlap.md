# Battle Bottom-Chrome Overlap Fix (#171) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the battle screen's bottom-anchored panels (UW cooldown bar, speed/pause/upgrade controls, upgrade menu) from overlapping by moving the speed/pause/upgrade controls onto a left vertical rail, freeing the bottom-center for the UW bar and left-padding the upgrade menu to clear the rail.

**Architecture:** Presentation-only Jetpack Compose change. Extract the control cluster into a new pure presentational composable `BattleControlRail` (+ a `BattleControlRailDefaults` object holding the single-sourced `WIDTH`/`GAP`/`menuStartPadding()`), then rewire three regions of `BattleScreen.kt`: the UW-bar offset, the controls (Row → rail at `CenterStart`), and the upgrade-menu wrapper (left-pad to clear the rail via a shared `railStartInset`). No domain / data / engine / economy / concurrency change.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, compose-bom 2026.02.00), JUnit Jupiter (pure-JVM unit test), Gradle via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-06-15-battle-bottom-chrome-overlap-design.md` (review-passed; review record in spec §10).

---

## Pre-flight (execution setup)

This plan modifies production code on a repo whose default branch is `main`. Before Task 1, create a feature branch (or worktree) — do **not** implement on `main`:

```bash
git checkout -b fix/171-battle-bottom-chrome
```

The design spec is already committed to `main` (commits `afa61d0`, `8ef3cba`); only the implementation lives on this branch.

---

## File Structure

- **Create** `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRail.kt`
  — the `BattleControlRailDefaults` object + the `BattleControlRail` composable. One file, one responsibility: the left vertical control rail and its layout constants.
- **Create** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt`
  — pure-JVM test pinning the menu-clears-rail coupling (`menuStartPadding() == WIDTH + GAP`).
- **Modify** `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt`
  — imports; add `railStartInset`; rewire UW-bar offset, controls, and upgrade-menu wrapper.
- **Modify (docs)** `CLAUDE.md`, `docs/steering/source-files.md`, `CHANGELOG.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md` per the PR Task-List Convention.

---

### Task 1: `BattleControlRailDefaults` + the coupling test (TDD)

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt`
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRail.kt`

Rationale: the one genuinely drift-prone invariant in this design is that the upgrade menu's start padding is derived from the same constants as the rail's width, so "menu clears the rail" can't decay into two hardcoded numbers (spec §4.5). This is the only thing a pure-JVM test can meaningfully pin (Compose layout can't render under this repo's Robolectric setup — spec §6). `Dp` is a pure value class, so the arithmetic runs on the JVM with no emulator.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #171: pins the single drift-prone invariant of the left control rail — the upgrade-menu start
 * padding is DERIVED from the same WIDTH + GAP the rail uses, so the "menu clears the rail by GAP"
 * coupling can't decay into two independently-edited numbers. Pure Dp arithmetic: no Compose render,
 * no Robolectric (battle-screen layout has no Compose-rule coverage — see spec §6 / PR-4736).
 */
class BattleControlRailTest {

    @Test
    fun `menu start padding equals rail width plus gap`() {
        assertEquals(
            BattleControlRailDefaults.WIDTH + BattleControlRailDefaults.GAP,
            BattleControlRailDefaults.menuStartPadding(),
        )
    }

    @Test
    fun `menu start padding clears the rail by a positive gap`() {
        assertTrue(
            BattleControlRailDefaults.menuStartPadding() > BattleControlRailDefaults.WIDTH,
            "menu must begin strictly past the rail's right edge",
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (compilation error — symbol doesn't exist yet)**

```bash
./run-gradle.sh testDebugUnitTest --tests "*BattleControlRailTest*" > build.log 2>&1; tail -n 20 build.log
```

Expected: FAIL — unresolved reference `BattleControlRailDefaults` (the production object doesn't exist yet).

- [ ] **Step 3: Write the minimal implementation (the Defaults object only)**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRail.kt` with **only** the constants object for now (the composable is added in Task 2):

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout constants for the battle [BattleControlRail] (#171). Single source of truth for the rail's
 * fixed footprint and the upgrade-menu start padding, so the "menu clears the rail by GAP" coupling
 * (spec §4.5) can't drift into two hardcoded numbers. WIDTH/GAP are cosmetic — tune on-device — but
 * the [menuStartPadding] DERIVATION must stay the single value the menu wrapper consumes.
 */
object BattleControlRailDefaults {
    /** Fixed rail footprint. Sized to hold the widest control (4x / pause / upgrade) + pill padding. */
    val WIDTH: Dp = 80.dp

    /** Separation between the rail's right edge and the upgrade menu's left edge. */
    val GAP: Dp = 8.dp

    /** Upgrade-menu wrapper start padding. The menu wrapper MUST consume this (not re-type WIDTH + GAP). */
    fun menuStartPadding(): Dp = WIDTH + GAP
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./run-gradle.sh testDebugUnitTest --tests "*BattleControlRailTest*" > build.log 2>&1; tail -n 20 build.log
```

Expected: PASS — 2 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRail.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt
git commit -m "feat(#171): BattleControlRailDefaults + menu-clears-rail coupling test"
```

---

### Task 2: The `BattleControlRail` composable

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRail.kt`

No JVM test here by design: the composable can't be rendered under this repo's Robolectric setup (`ActivityScenario` can't resolve a host activity, PR-4736; the `compose-ui-test-*` deps were intentionally removed in PR-B2). Verification is "it compiles" here + on-device in Task 4 (spec §6). The button bodies are copied **verbatim** from the existing controls Row (`BattleScreen.kt:191-215`); only the parent container flips `Row → Column`. Only the pause button calls `haptics.tap()` today — do not add haptics to any other button (spec §4.1, §4.6).

- [ ] **Step 1: Add the composable to `BattleControlRail.kt`**

Add these imports at the top of the file (below the existing `Dp`/`dp` imports):

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
```

Then append the composable below the `BattleControlRailDefaults` object:

```kotlin
/**
 * #171: vertical control rail for the battle screen — speed (1x/2x/4x), pause, and the upgrade-menu
 * toggle, stacked against the left edge. Replaces the old bottom-center Row that overlapped the UW
 * cooldown bar and the upgrade menu. Pure presentational unit: takes state + callbacks, holds no VM
 * reference. Button bodies are copied verbatim from the old Row; only the parent container is a Column.
 *
 * Modifier-order invariant: width → verticalScroll → background → padding. `background` MUST sit AFTER
 * `verticalScroll` so the pill wraps the visible viewport (and only the buttons), not the full
 * scrollable content extent — otherwise on a short/landscape viewport the rounded pill mis-renders
 * (same intent as the old Row comment at BattleScreen.kt:175-176). The caller supplies `align` + the
 * Start window-inset via [modifier].
 */
@Composable
fun BattleControlRail(
    speedMultiplier: Float,
    isPaused: Boolean,
    showUpgradeMenu: Boolean,
    onSetSpeed: (Float) -> Unit,
    onTogglePause: () -> Unit,
    onToggleUpgradeMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .width(BattleControlRailDefaults.WIDTH)
            .verticalScroll(rememberScrollState())
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(1f, 2f, 4f).forEach { speed ->
            val desc = stringResource(R.string.battle_cd_speed, speed.toInt())
            val label = stringResource(R.string.battle_speed_label, speed.toInt())
            if (speedMultiplier == speed) {
                Button(onClick = {}, modifier = Modifier.semantics { contentDescription = desc }) { Text(label) }
            } else {
                FilledTonalButton(
                    onClick = { onSetSpeed(speed) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = desc },
                ) { Text(label, color = Color.White) }
            }
        }
        val pauseDesc = stringResource(if (isPaused) R.string.action_resume else R.string.battle_cd_pause)
        FilledTonalButton(
            onClick = { haptics.tap(); onTogglePause() },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isPaused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.semantics { contentDescription = pauseDesc },
        ) { Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = Color.White) }

        val upgradesDesc = stringResource(R.string.battle_cd_upgrades)
        FilledTonalButton(
            onClick = { onToggleUpgradeMenu() },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (showUpgradeMenu) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.semantics { contentDescription = upgradesDesc },
        ) { Icon(Icons.Filled.Upgrade, contentDescription = null, tint = Color.White) }
    }
}
```

Note: `dp` is already imported (from Task 1's `androidx.compose.ui.unit.dp`); the `8.dp`/`12.dp`/`16.dp` literals reuse it.

- [ ] **Step 2: Verify it compiles (and the JVM test still passes)**

```bash
./run-gradle.sh testDebugUnitTest --tests "*BattleControlRailTest*" assembleDebug > build.log 2>&1; tail -n 20 build.log
```

Expected: BUILD SUCCESSFUL — the new composable compiles; the coupling test still passes. (The composable has no JVM test; it is verified on-device in Task 4.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRail.kt
git commit -m "feat(#171): BattleControlRail composable (vertical speed/pause/upgrade rail)"
```

---

### Task 3: Rewire `BattleScreen.kt` (imports, railStartInset, UW-bar offset, rail, menu)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt`

This is a single coherent edit (imports + four body regions). After editing, the build verifies it; there is no per-region JVM test (Compose layout — on-device gate in Task 4).

- [ ] **Step 1: Update imports**

**Remove** these imports (all were used only by the controls Row, which is being deleted):

```
androidx.compose.foundation.horizontalScroll
androidx.compose.foundation.layout.Arrangement
androidx.compose.foundation.layout.Row
androidx.compose.foundation.rememberScrollState
androidx.compose.material3.Button
androidx.compose.material3.ButtonDefaults
androidx.compose.material3.FilledTonalButton
androidx.compose.material.icons.filled.Pause
androidx.compose.material.icons.filled.PlayArrow
androidx.compose.material.icons.filled.Upgrade
com.whitefang.stepsofbabylon.presentation.ui.rememberHaptics
```

(Keep `Icon`, `Icons`, and `androidx.compose.material.icons.automirrored.filled.ArrowBack` — still used by the top-right quit button at `BattleScreen.kt:153`. Keep `androidx.compose.foundation.layout.width` — used by the HUD progress bar at `:135`.)

**Add** these imports:

```kotlin
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import com.whitefang.stepsofbabylon.presentation.battle.ui.BattleControlRail
import com.whitefang.stepsofbabylon.presentation.battle.ui.BattleControlRailDefaults
```

(`androidx.compose.foundation.layout.WindowInsets`, `.navigationBars`, and `.windowInsetsPadding` are already imported.)

- [ ] **Step 2: Add the shared `railStartInset` val**

Find this line near the top of the `BattleScreen` composable body (`BattleScreen.kt:75`):

```kotlin
    val roundActive = state.roundEndState == null
```

Add directly below it:

```kotlin
    // #171: single source of truth for the left-edge inset, shared by the control rail (CenterStart)
    // and the upgrade-menu wrapper so the menu clears the rail by exactly GAP on any device — incl. a
    // side display cutout in landscape. systemBars ∪ displayCutout, Start side only (RTL-aware).
    val railStartInset = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Start)
```

- [ ] **Step 3: Rewire the UW cooldown bar offset**

Replace this block (`BattleScreen.kt:157-162`):

```kotlin
        // UW bar (passive cooldown indicator post-R4-06; auto-trigger handled in engine)
        if (roundActive && state.uwSlots.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                UltimateWeaponBar(slots = state.uwSlots)
            }
        }
```

with:

```kotlin
        // UW bar (passive cooldown indicator post-R4-06; auto-trigger handled in engine).
        // #171: now owns the bottom-center strip alone (speed/pause/upgrade moved to the left rail).
        // Nav-bar inset + 24dp lifts it above the system gesture handle — was a bare 72.dp chosen to
        // dodge the old bottom control row.
        if (roundActive && state.uwSlots.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
            ) {
                UltimateWeaponBar(slots = state.uwSlots)
            }
        }
```

- [ ] **Step 4: Replace the bottom controls Row with the rail**

Replace the **entire** bottom-controls block (`BattleScreen.kt:164-217` — the comment block starting `// Bottom controls` through the closing brace of the `if (roundActive) { Row(...) { ... } }`, including the `val haptics = rememberHaptics()` line) with:

```kotlin
        // #171: speed / pause / upgrade live on a left vertical rail (was a bottom-center Row that
        // overlapped the UW bar + upgrade menu). CenterStart clears the top-left HUD and the bottom UW
        // bar in portrait. `railStartInset` is shared with the upgrade-menu wrapper below so the menu
        // clears the rail by exactly GAP on any device. See
        // docs/superpowers/specs/2026-06-15-battle-bottom-chrome-overlap-design.md.
        if (roundActive) {
            BattleControlRail(
                speedMultiplier = state.speedMultiplier,
                isPaused = state.isPaused,
                showUpgradeMenu = state.showUpgradeMenu,
                onSetSpeed = viewModel::setSpeed,
                onTogglePause = viewModel::togglePause,
                onToggleUpgradeMenu = viewModel::toggleUpgradeMenu,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(railStartInset),
            )
        }
```

(The pause haptic is preserved inside the rail's pause `onClick`; `onTogglePause = viewModel::togglePause` supplies the action, the rail's own `rememberHaptics()` supplies the tap — same behavior as before.)

- [ ] **Step 5: Rewire the upgrade-menu wrapper**

Replace this block (`BattleScreen.kt:219-227`):

```kotlin
        // Upgrade menu
        if (state.showUpgradeMenu && roundActive) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) {
                InRoundUpgradeMenu(cash = state.cash, inRoundLevels = state.inRoundLevels,
                    onPurchase = viewModel::purchaseInRoundUpgrade, onDismiss = viewModel::toggleUpgradeMenu,
                    lastPurchaseFree = state.lastPurchaseFree,
                    describeEffect = viewModel::describeEffect)
            }
        }
```

with:

```kotlin
        // Upgrade menu (#171): left-pads to clear the rail (shared `railStartInset` + WIDTH + GAP) so the
        // rail stays tappable while shopping; bottom nav-bar inset keeps the sheet's controls clear of the
        // gesture handle (flush otherwise — replaces the old flat 72.dp lift). The start-pad SHRINKS the
        // sheet rather than shifting it because InRoundUpgradeMenu's root is fillMaxWidth().
        if (state.showUpgradeMenu && roundActive) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(railStartInset)
                    .padding(start = BattleControlRailDefaults.menuStartPadding())
            ) {
                InRoundUpgradeMenu(cash = state.cash, inRoundLevels = state.inRoundLevels,
                    onPurchase = viewModel::purchaseInRoundUpgrade, onDismiss = viewModel::toggleUpgradeMenu,
                    lastPurchaseFree = state.lastPurchaseFree,
                    describeEffect = viewModel::describeEffect)
            }
        }
```

- [ ] **Step 6: Build, lint, and run the full unit-test suite**

```bash
./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > build.log 2>&1; echo "exit=$?"; tail -n 30 build.log
```

Expected: BUILD SUCCESSFUL; `exit=0`. No unused-import lint warnings for `BattleScreen.kt` (if any straggler unused import is flagged, remove it — the remove-list in Step 1 should cover them all). Full JVM suite green (998 tests — was 996, +2 from `BattleControlRailTest`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt
git commit -m "feat(#171): move battle speed/pause/upgrade to a left rail; UW bar + menu no longer overlap"
```

---

### Task 4: On-device verification (the real acceptance gate)

**Files:** none (manual verification; spec §6.3 / §6.4).

Battle-screen layout has no automated layout coverage (documented Robolectric/Compose incompatibility), so on-device verification is the acceptance gate — matching how #159/#160/#161-B1 inset/HUD fixes were signed off.

- [ ] **Step 1: Install the debug build on an emulator at the reported resolution**

Start an emulator (API 34+, portrait, ideally a 1080×2400 profile to match the issue screenshots), then:

```bash
./run-gradle.sh installDebug > build.log 2>&1; tail -n 10 build.log
```

- [ ] **Step 2: Verify the four acceptance criteria in a live round**

Open the app → start a battle. Confirm:
- (a) **No clipping** — nothing is cut off at the screen edge or by another panel.
- (b) **No overlap** — the left rail, the bottom-center UW cooldown bar, and the open upgrade menu never overlap each other.
- (c) **Tappable with the menu open** — open the upgrade menu, then tap `1x`/`2x`/`4x` and pause on the rail; all respond (the rail stays usable while shopping).
- (d) **Clearances** — the rail clears the top-left HUD (Wave / Spawning / cash) and sits in from the left gesture edge; the pause button's haptic still fires.

- [ ] **Step 3: Cutout spot-check (Start-inset path)**

Portrait has a ~0 Start inset, so this exercises the `railStartInset` path the portrait test can't. Simulate a left cutout and re-check that the rail and menu shift **together** and the menu still clears the rail:

```bash
adb shell cmd window set-display-cutout left
# verify in-app, then reset:
adb shell cmd window set-display-cutout default
```

(Or rotate to landscape with a simulated left cutout. Landscape HUD↔rail overlap is an accepted, de-scoped limitation per spec §5 — only confirm the rail/menu clearance relationship holds, not the landscape HUD.)

- [ ] **Step 4: Capture a screenshot for the PR**

```bash
adb exec-out screencap -p > /tmp/issue171-fixed.png
```

Attach to the PR (as with prior look-&-feel bundles). If any panel clips or buttons feel cramped, tune `BattleControlRailDefaults.WIDTH` (and re-run Task 1's test — the coupling holds for any value) before sign-off.

---

### Task 5: Sync current-state docs (PR Task-List Convention — runs BEFORE STATE/RUN_LOG)

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/steering/source-files.md`
- Modify: `CHANGELOG.md`
- Check (likely no change): `docs/steering/structure.md`

Per CLAUDE.md → PR Task-List Convention, touch only what this PR actually invalidates. This PR adds one file + rewires battle bottom chrome; it changes the headline test count and the battle `ui/` file set. No schema, no deps, no master-plan status, no new module/directory.

- [ ] **Step 1: Update the headline test count in `CLAUDE.md`**

In the Testing section, change the headline-count line from `996 JVM tests + 9 instrumented tests` to `998 JVM tests + 9 instrumented tests` (+2 from `BattleControlRailTest`).

- [ ] **Step 2: Add `BattleControlRail` to the battle `ui/` listing in `CLAUDE.md`**

In the architecture tree, the `presentation/battle/ … ui/` line lists `InRoundUpgradeMenu, PostRoundOverlay, PauseOverlay, HealthBarRenderer, UltimateWeaponBar, …`. Add `BattleControlRail` to that list (it's the #171 left control rail).

- [ ] **Step 3: Add a `source-files.md` entry**

In `docs/steering/source-files.md`, under the `presentation/battle/ui/` group, add an entry for `BattleControlRail.kt` — "left vertical control rail (speed/pause/upgrade) + `BattleControlRailDefaults` (WIDTH/GAP/menuStartPadding); #171."

- [ ] **Step 4: Add a `CHANGELOG.md` entry**

Under `[Unreleased]`, add a `Fixed` bullet: "Battle screen (#171): bottom controls (speed/pause/upgrade) moved to a left vertical rail so they no longer overlap/clip the Ultimate-Weapon cooldown bar or the in-round upgrade menu; menu now left-pads to stay clear of the rail. Presentation-only; +2 JVM tests (996→998)."

- [ ] **Step 5: Confirm `structure.md` needs no change**

`docs/steering/structure.md` documents modules/directories. This PR adds no new directory (`presentation/battle/ui/` already exists). Confirm no edit is needed (grep for the `ui/` description; if it enumerates files, add `BattleControlRail`, otherwise leave it).

- [ ] **Step 6: Commit the doc sync**

```bash
git add CLAUDE.md docs/steering/source-files.md CHANGELOG.md docs/steering/structure.md
git commit -m "docs(#171): sync current-state docs (test count 996→998, battle ui/ file set)"
```

---

### Task 6: Update STATE.md + append RUN_LOG.md

**Files:**
- Modify: `docs/agent/STATE.md`
- Modify: `docs/agent/RUN_LOG.md`

This is the end-of-session memory write. You may run the `/checkpoint` skill to automate it, OR do it manually as below.

- [ ] **Step 1: Update `docs/agent/STATE.md`**

- Bump the headline test count `996 → 998 JVM`.
- Add a "Recently shipped" entry (newest first) for #171: the battle bottom-chrome overlap fix — left vertical control rail; UW bar owns the bottom; menu left-pads to clear the rail; shared `railStartInset` single-sources the left inset; spec + plan both passed the Adversarial Review Gate; on-device verified at 1080×2400.
- Add a fragile-zone bullet: "**Battle bottom chrome is a single coordinated layout (#171)** — speed/pause/upgrade live on the left rail (`BattleControlRail`, `CenterStart`); the UW bar owns bottom-center; the upgrade menu left-pads via the shared `railStartInset` + `BattleControlRailDefaults.menuStartPadding()`. Don't reintroduce independent bottom-anchored offsets — that's exactly the overlap #171 fixed. The menu start-pad consuming `menuStartPadding()` is what gives `BattleControlRailTest` teeth; keep the call site routed through the helper."

- [ ] **Step 2: Append a `docs/agent/RUN_LOG.md` entry**

Append a dated session entry summarizing: issue #171; brainstorm (visual companion, left-rail chosen); spec + Adversarial Review Gate (21 findings → 14 surviving / 7 refuted, 0 critical/major); plan + its review; TDD implementation (coupling test + composable + BattleScreen rewire); 996→998 JVM; on-device verified; what remains (PR/merge).

- [ ] **Step 3: Commit**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#171): checkpoint — battle bottom-chrome overlap fix (STATE + RUN_LOG)"
```

---

### Task 7: Open the PR

**Files:** none.

- [ ] **Step 1: Push and open the PR**

```bash
git push -u origin fix/171-battle-bottom-chrome
gh pr create --fill --base main
```

- [ ] **Step 2: PR body checklist**

Confirm the PR description covers: the #171 acceptance criteria (a)-(d), the presentation-only scope, the 996→998 test count, the on-device screenshot (`/tmp/issue171-fixed.png`), and links to the spec + plan. Let CI (PR gate + instrumented lane) run; address any failures before requesting merge.

---

## Self-review notes

- **Spec coverage:** §3 left rail → Tasks 2-3; §4.1 rail composable + modifier order + verbatim buttons + haptics-only-on-pause → Task 2; §4.2 `railStartInset` + CenterStart + no gesture-exclusion → Task 3 Steps 2,4; §4.3 UW-bar offset → Task 3 Step 3; §4.4 menu wrapper + fillMaxWidth dependency → Task 3 Step 5; §4.5 WIDTH/GAP/menuStartPadding single source + consumed at call site → Task 1 + Task 3 Step 5; §5 landscape accepted limitation → Task 4 Step 3 (verify clearance only); §6 one JVM coupling test + no Compose-rule test + on-device gate + cutout spot-check → Task 1 + Task 4; §7 YAGNI (no orientation lock, no gesture-exclusion, no menu restyle) → honored; §8 files → Tasks 1-3 + 5.
- **Type/name consistency:** `BattleControlRailDefaults.WIDTH`/`GAP`/`menuStartPadding()`, `BattleControlRail(speedMultiplier, isPaused, showUpgradeMenu, onSetSpeed, onTogglePause, onToggleUpgradeMenu, modifier)`, and `railStartInset` are used identically across Tasks 1-3.
- **No placeholders:** every code step shows complete code; every command shows expected output.
