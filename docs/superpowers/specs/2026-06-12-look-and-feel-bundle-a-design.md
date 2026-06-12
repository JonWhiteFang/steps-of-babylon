# Look & Feel — Bundle A: Correctness & Accessibility Cleanup

**Date:** 2026-06-12
**Issue:** #160
**Source review:** `docs/external-reviews/2026-06-12-look-and-feel-ux-review.md`
**Predecessor PR:** #159 (first safe wave — design tokens, ActionBar removal, WCAG/visual fixes)
**Status:** Design approved; ready for implementation plan.

---

## 1. Goal & Scope

Bundle A is the next **safe, presentation-only** wave of the 2026-06-12 look-and-feel review. It
removes the remaining "looks unfinished" tells that are pure cleanup: emoji-as-UI, a missing
accessibility label, a mis-titled screen, and missing loading/empty states. It introduces a small
**shared component layer** so these fixes are applied once, not re-implemented per screen.

**In scope (this PR):**
1. Finish de-emoji of UI controls / currency icons (Labs, Cards, Store, Missions, Economy, Onboarding status line). Replace with Material vectors via a shared currency component.
2. Fix the one genuinely label-less meaningful indicator (onboarding pagination dots) + give correct `contentDescription` to the new standalone status icons created by the de-emoji work.
3. Re-title the Settings screen ("Notification Settings" → "Settings") and rename its Composable/ViewModel/State classes + files for internal consistency.
4. Add real loading spinners to all screens with an `isLoading` phase (+ add the field to the two that lack it).
5. Add empty-states to Weapons + Workshop-category lists; route Cards & Supplies through the shared empty-state component.

**Explicitly out of scope (tracked as sibling issues — do NOT do here):**
- Haptics + reward/claim animation + purchase pulse → **#162** (Bundle C)
- UW + Card rarity visual system → **#163** (Bundle D)
- Custom font + onboarding per-slide theming + real ziggurat asset (replaces 🏛️) → **#164** (Bundle E)
- Navigation back-affordances + bottom-nav "restore wrong saved screen" bug → **#161** (Bundle B)
- Help-screen decorative section-heading emoji (`HelpScreen.kt`) — decorative narrative, left as-is; could fold into #164.

**Risk:** Low. Pure `presentation/` (+ one new currency component, possible `strings.xml` later).
Zero engine / economy / concurrency files. Respects every STATE.md fragile zone. The Settings
**route string is unchanged** (`"settings"` / `Screen.Settings`), so the `DeepLinkRoutingTest`
pinned 13-route set is unaffected.

---

## 2. Audit Corrections vs. the Review

The implementation audit (3-agent fan-out over current `HEAD`, post-#159) corrected several review claims:

| Review claim | Reality (verified) |
|---|---|
| Supplies history lacks an empty-state | **Already has one** (`UnclaimedSuppliesScreen.kt:53`). Only Weapons + Workshop-category genuinely lack one. |
| `Icons.Filled.Upgrade` has a bug `contentDescription = null` | **Refuted.** Parent button carries `contentDescription` (`BattleScreen.kt:199`). Compliant. |
| Settings delete icon `contentDescription = null` is a bug | Under the chosen convention ("label-less only"), it's **compliant** — adjacent "Delete All Data" text label carries meaning. Left unchanged; recorded as verified-compliant. |
| Labs uses 🌙 for Steps | Actually 🦶 (`LabsScreen.kt:48`). |
| "Settings screen route" needs renaming | Route is **already** `"settings"`/`Screen.Settings`; only the Composable/VM/State classes carry the `Notification*` name. Rename is class/file-local. |

**Net:** the only genuinely label-less meaningful indicator requiring an a11y fix is the **onboarding
pagination dots** (color+size only). All other "contentDescription bugs" from the review are either
refuted or compliant under the project's documented convention.

---

## 3. Decisions (locked during brainstorming)

| # | Decision | Choice |
|---|---|---|
| D1 | Currency glyphs (💎/🦶/⚡) — no clean Material icon | **Approximate Material icons now**, via one shared mapping (Gems→Diamond, Steps→DirectionsWalk, PowerStones→OfflineBolt, Cash→Paid). Themed art deferred to a follow-up (one-file `CurrencyDisplay.icon()` swap). |
| D2 | a11y depth | **Fix only label-less meaningful icons** — respect the codebase convention ("icon decorative, adjacent label carries meaning → `contentDescription = null` is correct"). |
| D3 | Loading states | **Add real spinners everywhere** an `isLoading` phase exists; add `isLoading` to Weapons + Store. Accept the local-Room spinner-flash trade-off. |
| D4 | Settings rename | **Title text + rename file/class/route-name** (route *string* stays `"settings"`). |
| D5 | Implementation structure | **Shared helpers first, then sweep.** One PR. |

---

## 4. Architecture — Shared Components

Three new composables in `presentation/ui/` (alongside the post-#159 token layer). Each has one
clear purpose, a well-defined interface, and is independently understandable.

### 4.1 `ui/CurrencyDisplay.kt` — the de-emoji workhorse

```kotlin
enum class CurrencyType { STEPS, GEMS, POWER_STONES, CASH }

// Single source of truth for currency presentation. Themed-glyph art later = swap icon() only.
fun CurrencyType.icon(): ImageVector       // STEPS→DirectionsWalk, GEMS→Diamond,
                                            // POWER_STONES→OfflineBolt, CASH→Paid
@Composable fun CurrencyType.tint(): Color  // palette-aligned currency token from Color.kt (#159)
fun CurrencyType.label(): String           // "Steps"/"Gems"/"Power Stones"/"Cash" — a11y + plurals

// Icon + thousands-formatted value (the review's "missing separators" fix lives here).
@Composable fun CurrencyValue(type: CurrencyType, amount: Long, style: TextStyle = ...)

// Compact inline form for button labels: Row { Icon ; Text("%,d") }
@Composable fun CurrencyCost(type: CurrencyType, amount: Long)
```

> `amount` is typed `Long` for a single signature; call sites pass `Int` currency fields widened
> via `.toLong()` (the wallet/cost fields are mixed `Int`/`Long` across screens). The implementation
> plan pins exact call-site conversions.

- **What it does:** maps a currency to its Material icon + tint + label, and renders value/cost rows.
- **Why:** every 💎/🦶/⚡ site routes through one mapping → consistency, and the deferred themed-glyph
  art task becomes a one-file change. Thousands-separator formatting (review §4 HIGH) is centralized
  (a `NumberFormat` already exists unused in the codebase).
- **contentDescription convention:** the `Icon` inside `CurrencyValue`/`CurrencyCost` uses
  `contentDescription = null` when the value text is adjacent (the norm); `label()` is available for
  the rare standalone case.

### 4.2 `ui/LoadingBox.kt`

```kotlin
@Composable fun LoadingBox(modifier: Modifier = Modifier)  // Box(fillMaxSize, Center){ CircularProgressIndicator() }
```
- First screen-level spinner pattern in the app (none exists today). Reused by all gated screens.

### 4.3 `ui/EmptyState.kt`

```kotlin
@Composable fun EmptyState(title: String, message: String, modifier: Modifier = Modifier)
```
- Extracts the Cards empty-state pattern (`CardsScreen.kt:84` — centered `Column`, `titleMedium`
  heading + `bodyMedium`/`onSurfaceVariant` supporting text, `TextAlign.Center`) into one composable.
- Cards + Supplies refactored to call it (no behavior change); Weapons + Workshop-category adopt it.

---

## 5. De-emoji Map (exact sites)

Decorative/narrative emoji (Help headings, onboarding slide-data icons) are **left untouched**.

### Currencies → `CurrencyValue` / `CurrencyCost`
| Site | Now | After |
|---|---|---|
| `LabsScreen.kt:48` | `🦶 ${steps}` | `CurrencyValue(STEPS, steps)` |
| `LabsScreen.kt:49` | `💎 ${gems}` | `CurrencyValue(GEMS, gems)` |
| `LabsScreen.kt:56` | `Unlock Slot (… 💎)` | label + `CurrencyCost(GEMS, …)` |
| `LabsScreen.kt:139` | `Rush (… 💎)` | `CurrencyCost(GEMS, …)` |
| `CardsScreen.kt:54` | `💎 ${gems} Gems` header | `CurrencyValue(GEMS, gems)` |
| `CardsScreen.kt:78` | `${cost}💎` pack | `CurrencyCost(GEMS, cost)` |
| `StoreScreen.kt:51` | `💎 %,d` balance | `CurrencyValue(GEMS, …)` |
| `StoreScreen.kt:62` | `%,d 💎 Gems` | `CurrencyValue(GEMS, …)` |
| `StoreScreen.kt:156` | `💎 ${priceGems}` | `CurrencyCost(GEMS, …)` |
| `MissionsScreen.kt:89` | `${gems} 💎` reward | `CurrencyValue(GEMS, …)` |
| `MissionsScreen.kt:92` | `${powerStones} ⚡` reward | `CurrencyValue(POWER_STONES, …)` |

### Status glyphs → Material icons
| Site | Now | After |
|---|---|---|
| `MissionsScreen.kt:76,121` | `✓` claimed | `Icon(Icons.Default.CheckCircle, contentDescription = "Claimed")` |
| `UltimateWeaponScreen.kt:96` | `✓` equipped | `Icon(Icons.Default.Check, contentDescription = "Equipped")` |
| `CurrencyDashboardScreen.kt:116,129,154` | `✓ Claimed/Earned` | `Icon(Check, null)` + existing adjacent text |
| `CurrencyDashboardScreen.kt:172` | `✓` / `✗` week met/missed | `Check`/`Close` + `contentDescription = "met"/"missed"` (resolves the color-only week status) |
| `OnboardingScreen.kt:146` | `Step counting enabled ✓` | `Check` icon + text |

### Stars → `Icons.Default.Star`
| Site | Now | After |
|---|---|---|
| `LabsScreen.kt:136` | `Free ⭐` | `Star` icon + "Free" |
| `StoreScreen.kt:101` | `⭐ Season Pass` | `Star` icon + "Season Pass" |

### Left as-is (decorative; deferred)
- `HelpScreen.kt` — 9 section-heading emoji (decorative narrative).
- `OnboardingSlide.kt` — slide-data icons incl. 🏛️ (owned by #164 onboarding-art work).

---

## 6. Accessibility, Settings Rename, Loading & Empty States

### 6.1 a11y (label-less meaningful icons only — D2)
- **Onboarding pagination dots** (`OnboardingScreen.kt:119`): page position conveyed by color+size
  only. Add `semantics { contentDescription = "Page ${i+1} of ${n}" }` to the active dot; mark
  inactive dots out of the a11y tree (`clearAndSetSemantics {}` or skip) to avoid TalkBack noise.
- **New standalone status icons** (Section 5): the formerly-glyph-only `✓`/`✗` get real descriptions.
- **Verified-compliant, NOT changed** (record so future reviewers don't re-flag): delete icon
  (`NotificationSettingsScreen.kt:76`), Home season-pass star (`HomeScreen.kt:146`),
  `Icons.Filled.Upgrade` (`BattleScreen.kt:200`).

### 6.2 Settings rename (D4)
- Title string → `"Settings"` (`NotificationSettingsScreen.kt:31`).
- Rename `NotificationSettingsScreen` → `SettingsScreen`, `NotificationSettingsViewModel` →
  `SettingsViewModel`, `NotificationSettingsState` → `SettingsState`; rename the two files
  (`NotificationSettingsScreen.kt`, `NotificationSettingsViewModel.kt`).
- Update the only two external references: `MainActivity.kt:55` (import) + `:356` (call site).
- **Route untouched:** `Screen.Settings` / `"settings"` unchanged → `DeepLinkRoutingTest`'s pinned
  13-route set unaffected (verified). The `onReplayTutorial` callback wiring is preserved.

### 6.3 Loading spinners (D3)
- Screens **with** `isLoading` (Home, Workshop, Cards, Labs, Missions, Stats, Economy, Supplies):
  `if (state.isLoading) LoadingBox() else { <content> }`.
- Screens **without** (Weapons, Store): add `isLoading: Boolean = true` to the UiState data class,
  flip to `false` after the first repository emission in the VM `init`/collector, then gate with
  `LoadingBox`.
- **Trade-off (accepted):** local Room loads are sub-frame; the `isLoading=true` default +
  first-emission flip means the spinner only appears if the first emission is genuinely delayed.
  Documented as a conscious choice.

### 6.4 Empty states (via `EmptyState`)
- **Weapons** (`UltimateWeaponScreen.kt:49`): `if (weapons.isEmpty()) EmptyState(...)`.
- **Workshop** per-category (`WorkshopScreen.kt:76`): `if (upgrades.isEmpty()) EmptyState(...)`
  (category-filtered list → reachable empty).
- **Cards**: refactor existing inline empty-state to call `EmptyState` (no behavior change).
- **Supplies**: refactor existing empty-state to `EmptyState` for consistency (no behavior change).

---

## 7. Testing & Validation

**Tests:**
- **`CurrencyDisplayTest`** (JVM): pins the `CurrencyType → icon/tint/label` map + thousands
  formatting. This is the load-bearing piece the future glyph-swap relies on — worth a regression pin.
- **No new Compose UI tests** — consistent with #159 and the project norm (presentation-only; thin
  helpers; visually verified on device).
- **Regression guards confirmed green:** `DeepLinkRoutingTest`, `OnboardingRoutingTest` (route set
  unchanged), full existing suite compiles after the mechanical Settings rename (no test references
  the old `NotificationSettings*` names — verified).

**Validation gates (project standard):**
- `./run-gradle.sh assembleDebug` → BUILD SUCCESSFUL
- `./run-gradle.sh testDebugUnitTest lintDebug` → green; headline test count **973 → ~974**.
- **On-device (emulator API 36):** de-emoji'd screens render Material icons (Labs/Cards/Store/
  Missions/Economy); Settings titled "Settings"; one loading + one empty state visually confirmed.

**Docs (mandatory PR Task-List Convention):**
- Sync current-state docs BEFORE the STATE/RUN_LOG update:
  - `CHANGELOG.md` — `[Unreleased]` entry.
  - `docs/steering/source-files.md` — add `CurrencyDisplay.kt`, `LoadingBox.kt`, `EmptyState.kt`;
    note the Settings file rename.
  - `CLAUDE.md` — headline test-count bump only.
- Then `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md` via `/checkpoint`.
- **No ADR** — implements the existing ADR-0022 design-tokens direction; no new architectural decision.
- On merge: **close #160**; comment that themed-currency-glyph art is the one-file
  `CurrencyDisplay.icon()` swap (cross-ref the art-owning sibling issue).

---

## 8. File-Touch Summary

**New:** `ui/CurrencyDisplay.kt`, `ui/LoadingBox.kt`, `ui/EmptyState.kt`, `test/.../CurrencyDisplayTest.kt`.

**Renamed:** `settings/NotificationSettingsScreen.kt` → `SettingsScreen.kt`;
`settings/NotificationSettingsViewModel.kt` → `SettingsViewModel.kt` (classes/State renamed within).

**Modified:** `LabsScreen.kt`, `CardsScreen.kt`, `StoreScreen.kt`, `MissionsScreen.kt`,
`CurrencyDashboardScreen.kt`, `UltimateWeaponScreen.kt`, `OnboardingScreen.kt`, `WorkshopScreen.kt`,
`HomeScreen.kt` (spinner gate), `StatsScreen.kt` (spinner gate), `UnclaimedSuppliesScreen.kt`,
`MainActivity.kt` (Settings rename refs), `UltimateWeaponViewModel.kt` + `StoreViewModel.kt`
(+`isLoading`), their UiState classes, plus the doc files above.

**Untouched (fragile zones honored):** all of `domain/`, `data/`, `service/`,
`presentation/battle/{engine,entities,effects}`, `Screen.kt` route definitions.
