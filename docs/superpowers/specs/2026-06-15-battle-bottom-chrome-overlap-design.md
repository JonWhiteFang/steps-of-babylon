# Design — Battle bottom-chrome overlap fix (#171)

**Date:** 2026-06-15
**Issue:** [#171](https://github.com/JonWhiteFang/steps-of-babylon/issues/171) — "Battle screen: bottom control bar and pop-up panels overlap (clipped/cluttered, blocks interaction)" (`area:ui`, `bug`, `severity:minor`, `ux`)
**Type:** Presentation-only. No domain / data / engine / economy / concurrency change.

> **Post-implementation addendum (2026-06-15, after on-device review).** §4.4/§4.5 below specify the upgrade
> menu clearing the rail **horizontally** (left-pad by `menuStartPadding()` = WIDTH + GAP, pinned by
> `BattleControlRailTest`). After verifying on-device, the developer asked for the menu to span the **full
> screen width**. As shipped, the menu therefore clears the rail **vertically** instead: it is `fillMaxWidth()`
> and a fixed `IN_ROUND_MENU_HEIGHT` (240dp) keeps its top edge below the rail's bottom. This retired the
> `GAP`/`menuStartPadding()` horizontal coupling and its JVM test (the vertical clearance is a Compose layout
> fact, on-device-verified, not JVM-pinnable). The rest of the design (left rail at `CenterStart`, UW bar owns
> bottom-center, verbatim buttons, `railStartInset` for the rail's own left inset, landscape de-scoped) is
> unchanged. See `RUN_LOG.md` (2026-06-15) for the pivot detail.

---

## 1. Problem & root cause

On the battle screen, three bottom-anchored elements are each absolutely positioned in `BattleScreen.kt`
with hand-guessed `bottom` offsets, so their vertical bands collide:

| Element | Current anchor + offset | Approx. vertical band (from bottom) |
|---|---|---|
| UW cooldown bar (`UltimateWeaponBar`) | `BottomCenter`, `padding(bottom = 72.dp)`, 48dp tall | 72–120dp |
| Speed/pause/upgrade row | `BottomCenter`, `windowInsetsPadding(navigationBars)` + `padding(bottom = 24.dp)` | ~48–120dp |
| In-round upgrade menu (`InRoundUpgradeMenu`) | `BottomCenter`, `padding(bottom = 72.dp)`, 280dp tall | 72–352dp |

> Measurement origin: the UW-bar and upgrade-menu bands are **edge-relative** (no inset applied); the speed
> row's band is **nav-inset-relative** (it adds `windowInsetsPadding(navigationBars)`). They collide on real
> devices regardless of nav style — the exact dp figures are approximate, but the qualitative overlap (the
> actual root cause) holds either way.

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

- **Buttons are the *exact* buttons from today's row** — preserve each button's existing `onClick` body
  **verbatim**. Only the parent container flips `Row → Column`. In order **1x / 2x / 4x / pause / upgrade**,
  with the same `Button` vs `FilledTonalButton` selected-state logic, the same `containerColor` values, and
  the same `contentDescription` semantics. Note on haptics: **only the pause button calls `haptics.tap()`
  today** (`BattleScreen.kt:204`); the upgrade button (`:211`) and the 1x/2x/4x speed buttons (`:195`/`:197`)
  have no haptic call. Do **not** add `haptics.tap()` to any button that lacks it — that would be a behavior
  change §4.6/§7 forbid.
- Container: `Column` with `Arrangement.spacedBy(8.dp)`, `Alignment.CenterHorizontally`, and the inner
  modifier chain in **this exact order** (mirrors the existing Row's deliberate ordering at
  `BattleScreen.kt:181–187`):

  ```
  Modifier
      .width(BattleControlRailDefaults.WIDTH)   // fixed footprint applies to the viewport (§4.5)
      .verticalScroll(rememberScrollState())     // scroll BEFORE background
      .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
      .padding(horizontal = 8.dp, vertical = 12.dp)  // cosmetic pill padding; tune on-device
  ```

  The `background` **must** sit *after* `verticalScroll` so the pill wraps the visible viewport (and only the
  buttons), not the full scrollable content extent — otherwise on the short/landscape viewport the §5 scroll
  guards against, the rounded pill would be sized to the overflowing content and clip/mis-render (this is the
  same intent as the `BattleScreen.kt:175-176` comment). `align(CenterStart)` and the Start `windowInsetsPadding`
  from §4.2 sit *outside* this inner chain (applied at the call site), as with the existing Row.
- Safety net rationale: `verticalScroll` replaces the old row's `horizontalScroll` — same defensive intent as
  the existing `BattleScreen.kt:166` comment, now for vertical overflow.
- The rail takes its callbacks/state as parameters (`speedMultiplier`, `isPaused`, `showUpgradeMenu`,
  `onSetSpeed`, `onTogglePause`, `onToggleUpgradeMenu`) so it is a pure presentational unit testable in
  isolation and holds no VM reference.

### 4.2 Rail placement & insets (in `BattleScreen`)

> **Inset baseline (applies to §4.2/§4.3/§4.5).** `BattleScreen` renders inside the Scaffold's
> `NavHost(Modifier.padding(innerPadding))` (`MainActivity.kt:276`); on the Battle route the bottomBar is
> hidden, so `innerPadding` already carries the system-bar inset (the top-HUD comment at
> `BattleScreen.kt:127-129` says as much). `Modifier.padding(innerPadding)` positions but does **not consume**
> insets, and Material3 `Scaffold` does not `consumeWindowInsets`, so any in-screen `windowInsetsPadding(...)`
> is **additive/redundant** — the real bottom gap is ~2×navBar + 24dp, and the rail's absolute Start offset
> is ~2×(Start inset) + WIDTH. This is **benign** (over-padded, never clipped). We keep the in-screen
> `windowInsetsPadding` for parity with the existing speed Row (`BattleScreen.kt:183`); the on-device check
> (§6) must accept the slightly doubled gap. Crucially, the §4.5 rail↔menu clearing is **relative** — both
> elements share the identical Start inset, so the doubling shifts both equally and the "menu clears rail by
> GAP" invariant is unaffected; only the absolute on-screen position is doubled.

- `Modifier.align(Alignment.CenterStart)` — vertically centered on the left edge, clearing the top-left HUD
  and the bottom UW bar (see §5 for the short/landscape caveat).
- Left-edge inset — define **once** as a shared value so the rail and the menu wrapper (§4.5) apply the
  *byte-identical* expression (mirrors how WIDTH/GAP are single-sourced):

  ```
  // single source of truth for the Start-edge inset
  val railStartInset = WindowInsets.systemBars
      .union(WindowInsets.displayCutout)
      .only(WindowInsetsSides.Start)
  ```

  The rail applies `Modifier.windowInsetsPadding(railStartInset)` at its call site. It insets from the left
  for both system bars **and** display cutout, ignoring the three sides it doesn't touch.
- **No** `systemGestureExclusion` — the rail's targets are taps, not swipes, so they don't fight the
  back-gesture. In **portrait** the Start inset is ~0; what actually holds the pill off the exact edge is the
  rail's own `padding(horizontal = 8.dp)` plus the pill background. The `railStartInset` is the additive guard
  for the **landscape / side-cutout** case (nav bar or display cutout on the side), which the unlocked
  orientation (§5) makes reachable. (Omission stated explicitly so it reads as deliberate, not an oversight.)
- RTL: `CenterStart` + `WindowInsetsSides.Start` are layout-direction-aware, so the rail mirrors to the right
  edge on an RTL locale automatically. No hardcoded left.
- Gated by `roundActive` exactly as the speed row is today — the rail disappears at round end. The rail is
  **not** hidden when the upgrade menu opens (it is gated only on `roundActive`, like the menu) — both render
  together, which is what makes "change speed / pause while shopping" possible (§3).

### 4.3 UW cooldown bar

- Stays `BottomCenter`. Offset changes from `padding(bottom = 72.dp)` to
  `windowInsetsPadding(WindowInsets.navigationBars)` + `padding(bottom = 24.dp)` so it owns the bottom strip
  alone and sits above the system gesture handle. No change to `UltimateWeaponBar.kt` internals.

### 4.4 Upgrade menu wrapper

- The menu stays `InRoundUpgradeMenu` unchanged internally — a `BottomCenter` flush bottom sheet, 280dp,
  existing styling.
- Its wrapping `Box` in `BattleScreen` gets the **rail-clearing padding defined in §4.5** — the shared
  `railStartInset` plus `padding(start = WIDTH + GAP)` — so the menu's left edge begins one `GAP` past the
  rail's right edge (**not** flush against it). The `padding(bottom = 72.dp)` on the wrapper is removed; the
  sheet sits flush to the bottom with the navigation-bar inset applied so its contents clear the gesture
  handle.
- **Why the start-pad shrinks rather than shifts the sheet.** This clearance relies on
  `InRoundUpgradeMenu`'s root being `Modifier.fillMaxWidth()` (`InRoundUpgradeMenu.kt:71`): a wrapper
  `padding(start = …)` shrinks the constraints, and a `fillMaxWidth` child absorbs that by shrinking, so the
  rail stays uncovered. This is an invariant the menu composable must keep. The §7-excluded floating-card
  restyle would convert a fixed-width card from "shrink" into "left-shift" (and risk right-edge overflow); if
  that restyle is ever taken up, revisit this start-pad approach.
- The open menu **does** still cover the bottom-center UW cooldown bar. This is intentional and consistent
  with a focus-on-menu bottom sheet: the UW bar is a passive status indicator with **no tap targets**
  (`UltimateWeaponBar.kt:36-37` — "No clickable, no `onActivate` callback"), so covering it costs no
  interaction. (Explicitly in scope as accepted behavior, not a defect.)

### 4.5 Single source of truth for the rail width & menu clearance

- `BattleControlRailDefaults` holds the shared constants and the derived clearance, used by both the rail
  and the menu wrapper so the coupling can't drift into two hardcoded numbers:

  ```
  object BattleControlRailDefaults {
      val WIDTH: Dp = 64.dp        // fixed rail footprint; sized to hold the widest control + pill padding (tune on-device)
      val GAP: Dp = 8.dp           // separation between the rail's right edge and the menu's left edge (tune on-device)
      fun menuStartPadding(): Dp = WIDTH + GAP   // the value the menu wrapper MUST consume — see below
  }
  ```

- The rail is given a **fixed** `Modifier.width(WIDTH)` so its on-screen footprint is deterministic (a fixed
  `width()` imposes min == max == WIDTH; a long label wraps/truncates *inside* a button but the rail footprint
  stays exactly WIDTH — this determinism is why we use `width()` rather than an intrinsic/content width).
  `WIDTH` is sized to comfortably hold the widest control (`4x` / pause / upgrade) with the pill padding.
- **Clearing the rail correctly with a left inset.** The rail sits at `inset` from the screen edge (its
  `railStartInset`, §4.2), so its right edge is at `inset + WIDTH`. The menu sheet must clear that. To avoid
  re-deriving the device-dependent `inset` by hand, the **menu wrapper applies the *same* `railStartInset`**
  (`windowInsetsPadding(railStartInset)`, byte-identical to the rail's) and then adds
  `padding(start = BattleControlRailDefaults.menuStartPadding())`. Both baselines share the inset, so the menu
  content begins at `inset + WIDTH + GAP` — always `GAP` past the rail's right edge, on any device (the
  doubled-inset note in §4.2 doesn't matter here because both elements share it). The call site **must**
  consume `menuStartPadding()`, **not** re-type `WIDTH + GAP` inline — that is what gives the §6 coupling test
  teeth (otherwise the real layout can still diverge from the asserted value).

### 4.6 Unchanged

Top-left HUD (Wave / Spawning / cash / steps banner), top-right quit button, `PauseOverlay`,
`PostRoundOverlay`, `BiomeTransitionOverlay`, `SnackbarHost`, all strings, the ViewModel, and every button's
behavior/action.

## 5. Edge cases

- **Full-screen overlays** (`PauseOverlay`, `PostRoundOverlay`, `BiomeTransitionOverlay`) are
  `Box(fillMaxSize)` composed *after* the rail in child order, so they correctly cover the rail when active —
  unchanged.
- **Target (portrait) is sound.** At 1080×2400 portrait the content height is ~800–914dp; a ~260dp rail
  centered on the left edge spans roughly the middle third, cleanly clearing the top-left HUD (which ends
  ~130dp down) and the bottom UW bar. This is the verified case (§6).
- **HUD↔rail invariant.** The HUD (`TopStart`) and the rail (`CenterStart`) share the **left edge**, so they
  must not vertically overlap. In portrait they don't (previous bullet).
- **Short / landscape viewport — accepted limitation.** The manifest does **not** lock orientation. In
  landscape the content height collapses to ~350–400dp; a ~260dp centered rail would span into the HUD's
  band, producing a HUD↔rail overlap that the old bottom-center row never had. `verticalScroll` prevents the
  rail from *clipping* off-screen but does **not** prevent this overlap (both are `Start`-aligned).
  Battle is portrait-designed and landscape is explicitly out of scope (§7); we accept this as a known
  landscape limitation rather than expand the fix to lock orientation app-wide (a larger, undiscussed blast
  radius) or re-anchor the rail. **Corrects the earlier draft's false claim** that center anchoring keeps a
  taller rail "out of the top-left HUD" — true in portrait, false in landscape. If landscape becomes a
  supported target later, re-anchor (e.g. `BottomStart` so the rail grows away from the HUD) or reserve a
  fixed HUD band.

## 6. Test strategy

Battle-screen layout has **no Compose-UI / instrumented coverage today** — `BattleViewModelTest` is pure-VM
and never touches layout. The repo has a documented incompatibility (STATE "fragile zones"; the
`BottomNavRestoreTest` saga): **Compose-UI test rules don't run under Robolectric here** — `ActivityScenario`
can't resolve a host activity (PR-4736). A full layout-assertion test is therefore not feasible in the JVM
suite without fighting that infra. Proportionate approach:

1. **One pure-JVM unit test** (new, `BattleControlRailTest`) pinning the **one genuinely drift-prone
   invariant** the design relies on — the menu-clears-rail coupling (§4.5):
   - `BattleControlRailDefaults.menuStartPadding() == WIDTH + GAP` and `menuStartPadding() > WIDTH`. This
     pins the relationship the design says must not drift into two hardcoded numbers. It has teeth **only
     because** the menu wrapper consumes `menuStartPadding()` at the call site (§4.5) rather than re-typing
     `WIDTH + GAP` inline.
   - Pure `Dp` arithmetic — no Compose render, no Robolectric, so it sidesteps the PR-4736 incompatibility.
   - Deliberately **not** asserting `WIDTH > 0` (a compile-time tautology) or the button order (a parallel
     restatement of source that wouldn't catch the overlap bug class).
2. **No Compose-rule layout test** — explicitly, due to the documented Robolectric/Compose incompatibility:
   `ActivityScenario` can't resolve a host activity (PR-4736), and the `compose-ui-test-*` deps were
   intentionally removed in PR-B2 (RUN_LOG ~L399), so writing one is infeasible without dedicated infra
   work. Stated here so a reviewer doesn't read the absence as an oversight.
3. **On-device verification is the real gate** (matches how #159/#160/#161-B1 inset/HUD fixes were signed
   off). On the emulator at 1080×2400 portrait, open a round and verify:
   - (a) nothing clips at the screen edge;
   - (b) rail + UW bar + open menu never overlap;
   - (c) speed / pause / upgrade all tap correctly **with the menu open**;
   - (d) the rail clears the top-left HUD and the left gesture edge.
   Capture a screenshot for the PR, as with the prior look-&-feel bundles.
4. **One cutout/landscape spot-check** — the portrait acceptance test does **not** exercise the
   `railStartInset` (Start inset ≈0 in portrait). Before sign-off, spot-check the left-cutout path once
   (e.g. `adb shell cmd window set-display-cutout`, or rotate to landscape with a simulated left cutout) to
   confirm the rail and the menu both shift together and the menu still clears the rail. Document the result;
   this is a quick manual check, not an automated gate.

Build/verify: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`, then the emulator walkthrough.

Headline test count: **996 → 997+ JVM** (the new rail test).

## 7. Scope boundary (YAGNI)

In scope: the rail extraction + placement, UW-bar offset, menu start-pad, the single WIDTH constant, one
JVM test, on-device verification.

Out of scope: orientation lock, `systemGestureExclusion`, menu restyle into a floating card, any change to
UW-bar behavior or any button action, new strings, the landscape battle experience beyond the scroll
safety net.

## 8. Files touched

- `presentation/battle/BattleScreen.kt` — replace bottom `Row` with `BattleControlRail` at `CenterStart`
  (applying `railStartInset`); adjust UW-bar offset; wrap the upgrade-menu `Box` with `railStartInset` +
  `padding(start = BattleControlRailDefaults.menuStartPadding())`.
- `presentation/battle/ui/BattleControlRail.kt` — **new**; the vertical rail + `BattleControlRailDefaults`
  (`WIDTH`, `GAP`, `menuStartPadding()`).
- `app/src/test/.../presentation/battle/BattleControlRailTest.kt` — **new**; the JVM coupling test
  (`menuStartPadding() == WIDTH + GAP`).
- Current-state docs per the PR Task-List Convention (CLAUDE.md, source-files, structure, STATE, RUN_LOG,
  CHANGELOG) touched only where the change invalidates them.

## 9. Adversarial Review Gate

Per CLAUDE.md → Agent protocol, this spec passed the Adversarial Review Gate (code-grounded multi-dimension
fan-out → adversarial refute → confirmed-only synthesis) **before** the implementation plan was written. The
review record is §10.

## 10. Adversarial Review record (2026-06-15)

**Workflow:** 6-dimension code-grounded fan-out (code-grounding, Compose-API correctness, layout-safety,
scope-vs-issue, consistency/ambiguity, test-feasibility) → per-finding adversarial refutation → confirmed/
partial-only synthesis. **27 agents.**

**Tally:** 21 findings raised → **14 surviving** (0 critical, 0 confirmed-major), **7 refuted**. The lone
`major` (double-inset) was downgraded to `partial` on verification — the result is over-padded, never
clipped, and the §4.5 *relative* clearing math is unaffected.

**Surviving findings applied to this spec:**

1. *(minor, code-grounding)* Spec wrongly implied `haptics.tap()` is on both pause **and** upgrade; only
   pause has it today. → §4.1 corrected + "preserve each `onClick` verbatim; don't add haptics" directive.
2. *(nit, code-grounding)* §1 band table mixed edge-relative and inset-relative origins. → added the
   measurement-origin note under the table.
3. *(partial→minor, Compose-API)* `BattleScreen` is already inside the Scaffold's `innerPadding`, so in-screen
   `windowInsetsPadding` double-counts the inset. Benign (over-padded), and §4.5's relative math survives. →
   added the "Inset baseline" note to §4.2.
4. *(partial→minor, Compose-API)* Portrait Start inset ≈0; the gesture-exclusion rationale over-credited the
   inset. → §4.2 softened (rail padding holds the edge in portrait; `railStartInset` guards landscape/cutout);
   §6 adds a cutout spot-check.
5. *(minor, Compose-API)* Pill `background` vs `verticalScroll` order was unspecified. → §4.1 pins the exact
   modifier chain (scroll **before** background) with rationale.
6. *(minor, layout-safety)* §5's "taller rail expands… not into the HUD" is **false** in landscape. → §5
   rewritten: portrait sound, landscape HUD-overlap an accepted/de-scoped limitation; stated the HUD↔rail
   invariant.
7. *(nit, layout-safety)* Menu start-pad works only because `InRoundUpgradeMenu` is `fillMaxWidth()`. → §4.4
   records the invariant + the floating-card caveat.
8. *(nit, layout-safety)* Verified UW-bar↔menu coverage is interaction-safe (UW bar has no tap targets) and
   rail↔UW-bar bands are disjoint. → no change needed; §4.4 already states it (citation added).
9. *(minor, consistency)* Menu inset must be the **same expression** as the rail's (incl. the `displayCutout`
   union), else a left-cutout device regresses. → introduced single-sourced `railStartInset` (§4.2), consumed
   by both rail and menu (§4.5).
10. *(minor, consistency)* §4.4 "start padding = rail footprint" (reads as WIDTH) contradicted §4.5
    (`WIDTH + GAP`). → §4.4 now defers to §4.5 and includes GAP.
11. *(partial→minor, test-feasibility)* The proposed `WIDTH > 0` test is a tautology; the drift-prone coupling
    is the contract worth pinning. → §4.5 adds `menuStartPadding()` (consumed at the call site) and §6 asserts
    `menuStartPadding() == WIDTH + GAP`.
12–14. *(nits, test-feasibility — confirmations)* Robolectric/Compose incompatibility claim, "BattleViewModelTest
    is pure-VM", and "996→997+ JVM" are all accurate; on-device-as-real-gate matches prior practice. → minor
    wording added to §6 (deps removed in PR-B2); no substantive change.

**Refuted (not applied) — with the refutation reasoning:**

- *"Taps eaten under the opaque sheet if WIDTH under-shoots"* — **refuted**: `Modifier.width(WIDTH)` is a
  fixed (min==max) constraint; the rail footprint is exactly WIDTH by construction, buttons can't render
  wider. The proposed `widthIn(min=WIDTH)` fix would *reintroduce* variable width. (Real residual: text
  truncation at large font scale — not eaten taps — left to on-device check.)
- *"SnackbarHost collides with the relocated UW bar"* + *"…and isn't verified on-device"* — **refuted (×2)**:
  the snackbar fires **only** from the `PostRoundOverlay` watch-ad callbacks, i.e. only when
  `roundEndState != null` → `roundActive == false`, when the UW bar / rail / menu are all unmounted. They can
  never co-occupy the screen, so there is no reachable overlap.
- *"'tune on-device' hides undecided values the tests depend on"* — **refuted**: the WIDTH test is a property
  assertion, the §4.5 coupling is value-independent, and WIDTH has a sizing rule; the only "soft" values
  (pill padding, GAP) are exactly the cosmetic spacing meant to be tuned.
- *"'swapped' padding wording"* + *"§4.5 byte-identical inset (standalone)"* + *"996→997 count"* —
  **refuted**: no spec error; concrete values already stated; concerns subsumed by findings already applied
  (single-sourced `railStartInset`) or simply accurate.
