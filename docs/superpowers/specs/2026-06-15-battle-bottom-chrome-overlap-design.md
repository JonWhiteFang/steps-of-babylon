# Design — Battle bottom-chrome overlap fix (#171)

**Date:** 2026-06-15
**Issue:** [#171](https://github.com/JonWhiteFang/steps-of-babylon/issues/171) — "Battle screen: bottom control bar and pop-up panels overlap (clipped/cluttered, blocks interaction)" (`area:ui`, `bug`, `severity:minor`, `ux`)
**Type:** Presentation-only. No domain / data / engine / economy / concurrency change.

---

## 1. Problem & root cause

On the battle screen, three bottom-anchored elements are each absolutely positioned in `BattleScreen.kt`
with hand-guessed `bottom` offsets, so their vertical bands collide:

| Element | Current anchor + offset | Approx. vertical band (from bottom) |
|---|---|---|
| UW cooldown bar (`UltimateWeaponBar`) | `BottomCenter`, `padding(bottom = 72.dp)`, 48dp tall | 72–120dp |
| Speed/pause/upgrade row | `BottomCenter`, `windowInsetsPadding(navigationBars)` + `padding(bottom = 24.dp)` | ~48–120dp |
| In-round upgrade menu (`InRoundUpgradeMenu`) | `BottomCenter`, `padding(bottom = 72.dp)`, 280dp tall | 72–352dp |

Consequences (all visible in the issue screenshots):

- The UW cooldown bar and the speed row occupy nearly the same band. The speed row is composed **after**
  the UW bar in the `Box` child order, so it paints over the UW boxes → the clipped, half-hidden cooldown
  numbers ("73 / 45 / 45") seen behind the `2x` / `4x` / pause pill.
- Only the speed row accounts for the system-navigation inset; the other two ignore insets entirely.
- When the upgrade menu is open its bottom edge (72dp) collides with both the UW bar and the speed row.

Root cause: **each bottom-anchored panel independently guesses its own offset; nothing coordinates a single
bottom layout.** This is a z-order / overlapping-layout bug, not a missing element (matches the issue's own
"Likely a z-order / overlapping-layout issue" note).

## 2. Goal

Satisfy all four acceptance criteria from #171:

- No bottom-anchored panel is clipped by the screen edge or another panel.
- Bottom panels never overlap each other while open.
- Every open window is fully readable and individually tappable.
- Verified on the battle screen at the affected resolution (1080×2400 portrait, per the screenshots).

## 3. Chosen direction — left vertical control rail

Move the **speed / pause / upgrade controls** off the contested bottom strip and onto a **left vertical
rail**, vertically centered on the left edge. This frees the bottom-center for the UW cooldown bar alone and
removes the contention at its source rather than re-tuning three offsets that will drift again.

Layout after the change:

- **Left rail** — `CenterStart`, a vertical `Column` of the existing five controls (1x / 2x / 4x / pause /
  upgrade), same pill background and button styling as today's row.
- **UW cooldown bar** — stays `BottomCenter`, now owns the bottom strip alone.
- **Upgrade menu** — stays a `BottomCenter` bottom sheet, but **left-pads to clear the rail** so the rail
  stays tappable while shopping (you can change speed / pause mid-shop without closing the menu).

Decided against (considered during brainstorming):

- **Simple vertical stack at bottom-center** (UW bar above speed row in one `Column`): fixes the overlap but
  keeps the bottom strip crowded and doesn't let you reach controls while the tall menu is open.
- **Floating-card menu above a still-visible bottom row**: more restyle of the menu, more vertical real
  estate consumed; the left rail achieves "controls usable while shopping" with less change to the menu.

## 4. Layout spec

### 4.1 New composable: `BattleControlRail`

New file `presentation/battle/ui/BattleControlRail.kt`. Extracts the control cluster out of `BattleScreen`.

- Container: `Column` with
  `Arrangement.spacedBy(8.dp)`, `Alignment.CenterHorizontally`,
  `background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))`,
  `padding(horizontal = 8.dp, vertical = 12.dp)` (vertical/horizontal swapped vs. the old row to suit a
  column; tune on-device).
- Buttons, **in order 1x / 2x / 4x / pause / upgrade**, are the *exact* buttons from today's row — same
  `Button` vs `FilledTonalButton` selected-state logic, same `containerColor` values, same
  `contentDescription` semantics, same `haptics.tap()` on pause/upgrade. Only the parent container flips
  `Row → Column`.
- The rail takes its callbacks/state as parameters (`speedMultiplier`, `isPaused`, `showUpgradeMenu`,
  `onSetSpeed`, `onTogglePause`, `onToggleUpgradeMenu`) so it is a pure presentational unit testable in
  isolation and holds no VM reference.
- Safety net: `verticalScroll(rememberScrollState())` on the inner column so five stacked buttons never
  clip on a short/landscape viewport (replaces the old row's `horizontalScroll`, same defensive intent as
  the existing `BattleScreen.kt:166` comment).

### 4.2 Rail placement & insets (in `BattleScreen`)

- `Modifier.align(Alignment.CenterStart)` — vertically centered on the left edge, clearing the top-left HUD
  and the bottom UW bar.
- Left-edge inset: `windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start))`
  so the rail insets from the left for both system bars and display cutout, and ignores the other three
  sides it doesn't touch. Additive to the rail's own padding; the pill never sits under a cutout or hugs the
  exact edge.
- **No** `systemGestureExclusion` — the rail's targets are taps, not swipes, so they don't fight the
  back-gesture; the Start inset already pushes the pill in from the edge. (Stated explicitly so the omission
  reads as deliberate, not an oversight.)
- RTL: `CenterStart` + `Start`-side insets are layout-direction-aware, so the rail mirrors to the right edge
  on an RTL locale automatically. No hardcoded left.
- Gated by `roundActive` exactly as the speed row is today — the rail disappears at round end.

### 4.3 UW cooldown bar

- Stays `BottomCenter`. Offset changes from `padding(bottom = 72.dp)` to
  `windowInsetsPadding(WindowInsets.navigationBars)` + `padding(bottom = 24.dp)` so it owns the bottom strip
  alone and sits above the system gesture handle. No change to `UltimateWeaponBar.kt` internals.

### 4.4 Upgrade menu wrapper

- The menu stays `InRoundUpgradeMenu` unchanged internally — a `BottomCenter` flush bottom sheet, 280dp,
  existing styling.
- Its wrapping `Box` in `BattleScreen` gets **start padding = rail footprint** so the open menu never covers
  the rail. The `padding(bottom = 72.dp)` on the wrapper is removed; the sheet sits flush to the bottom with
  the navigation-bar inset applied so its contents clear the gesture handle.
- The open menu **does** still cover the bottom-center UW cooldown bar. This is intentional and consistent
  with a focus-on-menu bottom sheet: the UW bar is a passive status indicator with no tap targets, so
  covering it costs no interaction. (Explicitly in scope as accepted behavior, not a defect.)

### 4.5 Single source of truth for the rail width

- Define `BattleControlRailDefaults.WIDTH` (a `Dp` `val`) once. The rail is given a **fixed**
  `Modifier.width(WIDTH)` so its on-screen footprint is deterministic, and the upgrade-menu wrapper's start
  padding is computed from the same `WIDTH`, so the "menu clears the rail" coupling can't drift into two
  hardcoded numbers. `WIDTH` is sized to comfortably hold the widest control (the `4x` / pause / upgrade
  buttons) with the pill padding; the five buttons size within it.
- **Clearing the rail correctly with a left inset.** The rail sits at `inset` from the screen edge (its Start
  inset, §4.2), so its right edge is at `inset + WIDTH`. The menu sheet must clear that. To avoid re-deriving
  the device-dependent `inset` by hand, the **menu wrapper applies the *same* Start inset as the rail**
  (`windowInsetsPadding(... .only(Start))`) and then adds a regular `padding(start = WIDTH + GAP)`. Both
  baselines share the inset, so the menu content begins at `inset + WIDTH + GAP` — always `GAP` past the
  rail's right edge, on any device. `GAP` is a small shared constant (e.g. `BattleControlRailDefaults.GAP`);
  verify the exact value on-device.

### 4.6 Unchanged

Top-left HUD (Wave / Spawning / cash / steps banner), top-right quit button, `PauseOverlay`,
`PostRoundOverlay`, `BiomeTransitionOverlay`, `SnackbarHost`, all strings, the ViewModel, and every button's
behavior/action.

## 5. Edge cases

- **Full-screen overlays** (`PauseOverlay`, `PostRoundOverlay`, `BiomeTransitionOverlay`) are
  `Box(fillMaxSize)` composed *after* the rail in child order, so they correctly cover the rail when active —
  unchanged.
- **Short / landscape viewport** — five stacked buttons (~260dp) could exceed height; `verticalScroll`
  handles it. Battle is portrait-designed but the manifest does **not** lock orientation, so the scroll guard
  is the honest safety net rather than assuming portrait.
- **Rail growth** — center anchoring means a taller rail expands symmetrically from center, not into the
  top-left HUD.

## 6. Test strategy

Battle-screen layout has **no Compose-UI / instrumented coverage today** — `BattleViewModelTest` is pure-VM
and never touches layout. The repo has a documented incompatibility (STATE "fragile zones"; the
`BottomNavRestoreTest` saga): **Compose-UI test rules don't run under Robolectric here** — `ActivityScenario`
can't resolve a host activity (PR-4736). A full layout-assertion test is therefore not feasible in the JVM
suite without fighting that infra. Proportionate approach:

1. **One pure-JVM unit test** (new, e.g. `BattleControlRailTest`) pinning the extractable contracts:
   - `BattleControlRailDefaults.WIDTH` is a positive, sane `Dp`.
   - If the control descriptors are factored into a pure helper (button order = 1x / 2x / 4x / pause /
     upgrade), assert that list/order. (If extraction adds no real value, keep just the WIDTH assertion and
     say so — don't manufacture a test for a Composable that can't be JVM-rendered.)
2. **No Compose-rule layout test** — explicitly, due to the documented Robolectric/Compose incompatibility.
   Stated here so a reviewer doesn't read the absence as an oversight.
3. **On-device verification is the real gate** (matches how #159/#160/#161-B1 inset/HUD fixes were signed
   off). On the emulator at 1080×2400 portrait, open a round and verify:
   - (a) nothing clips at the screen edge;
   - (b) rail + UW bar + open menu never overlap;
   - (c) speed / pause / upgrade all tap correctly **with the menu open**;
   - (d) the rail clears the top-left HUD and the left gesture edge.
   Capture a screenshot for the PR, as with the prior look-&-feel bundles.

Build/verify: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`, then the emulator walkthrough.

Headline test count: **996 → 997+ JVM** (the new rail test).

## 7. Scope boundary (YAGNI)

In scope: the rail extraction + placement, UW-bar offset, menu start-pad, the single WIDTH constant, one
JVM test, on-device verification.

Out of scope: orientation lock, `systemGestureExclusion`, menu restyle into a floating card, any change to
UW-bar behavior or any button action, new strings, the landscape battle experience beyond the scroll
safety net.

## 8. Files touched

- `presentation/battle/BattleScreen.kt` — replace bottom `Row` with `BattleControlRail` at `CenterStart`;
  adjust UW-bar offset; add start-pad to the upgrade-menu `Box`.
- `presentation/battle/ui/BattleControlRail.kt` — **new**; the vertical rail + `BattleControlRailDefaults`.
- `app/src/test/.../presentation/battle/BattleControlRailTest.kt` — **new**; the JVM contract test.
- Current-state docs per the PR Task-List Convention (CLAUDE.md, source-files, structure, STATE, RUN_LOG,
  CHANGELOG) touched only where the change invalidates them.

## 9. Adversarial Review Gate

Per CLAUDE.md → Agent protocol, this spec passes the Adversarial Review Gate (code-grounded multi-dimension
fan-out → adversarial refute → confirmed-only synthesis) **before** the implementation plan is written. The
review record is appended to this file under §10.
