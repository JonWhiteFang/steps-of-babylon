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
1. Finish de-emoji of UI controls / currency icons / status glyphs (Labs incl. Start button, Cards incl. Free Pack + pack-result, Store incl. ✅ states, Missions, Economy, Onboarding status line, Battle pause toggle). Replace with Material vectors via a shared currency component. **Exhaustive over UI-control/currency/status glyphs in `presentation/`** (post-completeness-audit; see §5).
2. Fix the one genuinely label-less meaningful indicator (onboarding pagination dots) + give correct `contentDescription` to the new status icons per the §5 per-site classification.
3. Re-title the Settings screen ("Notification Settings" → "Settings") and rename its Composable/ViewModel/State classes + files for internal consistency.
4. Add real loading spinners to all screens with an `isLoading` phase (+ add the field to the two that lack it; Battle excluded — §6.3).
5. Add an empty-state to Workshop-category lists; route Cards through the shared empty-state component. (Weapons excluded — fixed catalog; Supplies untouched — different shape. See §6.4.)
6. Delete the dead `domain/model/Currency.kt` enum (see §4.1).

**Explicitly out of scope (tracked as sibling issues — do NOT do here):**
- Haptics + reward/claim animation + purchase pulse → **#162** (Bundle C)
- UW + Card rarity visual system → **#163** (Bundle D)
- Custom font + onboarding per-slide theming + real ziggurat asset (replaces 🏛️) → **#164** (Bundle E)
- Navigation back-affordances + bottom-nav "restore wrong saved screen" bug → **#161** (Bundle B)
- Help-screen decorative section-heading emoji (`HelpScreen.kt`) — decorative narrative, left as-is; could fold into #164.

**Risk:** Low. Confined to `presentation/` plus one pure deletion in `domain/` (dead `Currency.kt`).
**One Battle file is touched** — the Compose HUD `BattleScreen.kt` pause-button glyph (▶/⏸) only; the
fragile renderer/engine/effects are **not** touched. Zero economy / concurrency files. Respects every
STATE.md fragile zone. The Settings **route string is unchanged** (`"settings"` / `Screen.Settings`),
so the `DeepLinkRoutingTest` pinned 13-route set is unaffected.

---

## 2. Audit Corrections vs. the Review

The implementation audit (3-agent fan-out over current `HEAD`, post-#159) corrected several review claims:

| Review claim | Reality (verified) |
|---|---|
| Supplies history lacks an empty-state | **Already has one** (`UnclaimedSuppliesScreen.kt:53`) — left as-is. Of the screens the review named, only **Workshop-category** genuinely warrants a new one; **Weapons** is a fixed 6-entry catalog (never data-empty) so it gets none either (§6.4). |
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
// Member order matches the (dead) domain Currency enum to kill cosmetic divergence — see note below.
enum class CurrencyType { STEPS, CASH, GEMS, POWER_STONES }

// Single source of truth for currency presentation. Themed-glyph art later = swap icon() only.
fun CurrencyType.icon(): ImageVector       // STEPS→DirectionsWalk, GEMS→Diamond,
                                            // POWER_STONES→OfflineBolt, CASH→Paid
@Composable fun CurrencyType.tint(): Color  // explicit token mapping (see below)
fun CurrencyType.label(): String           // plural-noun for standalone a11y contentDescription;
                                            // "Steps"/"Cash"/"Gems"/"Power Stones" — NO quantity inflection

// Icon + thousands-formatted value (the review's "missing separators" fix lives here).
@Composable fun CurrencyValue(type: CurrencyType, amount: Long, style: TextStyle = MaterialTheme.typography.titleMedium)

// Compact inline form for button labels: Row { Icon ; Text("%,d") }
@Composable fun CurrencyCost(type: CurrencyType, amount: Long)
```

**`tint()` token mapping (explicit — tokens already in `Color.kt`):**
`STEPS → StepColor` (= `Gold`), `GEMS → GemColor`, `POWER_STONES → PowerStoneColor`. **`CASH`** has
no token and **no in-scope render site** in Bundle A — map it to `Gold` as a placeholder (it's the
in-round currency, de-emoji'd in a later bundle if ever surfaced in menus). `CASH` is kept in the
enum only to mirror the domain model; remove it later if it never gains a menu render site.

**`style` default = `titleMedium`** — the dominant style for currency *balances* (Labs/Cards/Store
headers). The two Missions **reward** sites render at `bodySmall`, so their §5 rows pass an explicit
`style = MaterialTheme.typography.bodySmall`. This keeps "consistent everywhere" honest: one default,
explicit overrides where the context differs.

> `amount` is typed `Long` for a single signature; call sites pass `Int` currency fields widened
> via `.toLong()` (the wallet/cost fields are mixed `Int`/`Long` across screens). The implementation
> plan pins exact call-site conversions.

- **What it does:** maps a currency to its Material icon + tint + label, and renders value/cost rows.
- **Why:** every 💎/🦶/⚡ site routes through one mapping → consistency, and the deferred themed-glyph
  art task becomes a one-file change. Thousands-separator formatting (review §4 HIGH) is centralized
  here — currently it's duplicated ad-hoc across screens via `NumberFormat`/`%,d`
  (MissionsScreen/StatsScreen/StepWidgetProvider use `NumberFormat.getNumberInstance()`;
  Home/Economy/Store use `"%,d".format(...)`); centralizing removes the drift.
- **Dead `domain/model/Currency.kt`:** an identical-membered `enum class Currency { STEPS, CASH,
  GEMS, POWER_STONES }` exists in domain but is **dead code** (zero references in main/test —
  confirmed via `git grep`). `CurrencyType` intentionally lives in **presentation** because it carries
  Compose-bound `icon()`/`tint()` (`ImageVector`/`Color`) that the purity-enforced domain layer
  (`DomainPurityTest`) cannot hold. **Delete the dead `Currency.kt`** as part of this PR so we don't
  leave a duplicate behind. (This is the one intentional `domain/` touch — a pure deletion of unused
  code, not a logic change; noted in §8.)
- **contentDescription convention:** the `Icon` inside `CurrencyValue`/`CurrencyCost` uses
  `contentDescription = null` when the value text is adjacent (the norm); `label()` is available for
  the rare standalone case.
- **Icon availability (verified):** every Material vector used by this bundle (`DirectionsWalk`,
  `Diamond`, `OfflineBolt`, `Paid`, `CheckCircle`, `Check`, `Close`, `Star`, `PlayArrow`, `Pause`,
  `FiberNew`, `Autorenew`, `Slideshow`) is in **`material-icons-extended`**, which is already a
  declared dependency (`app/build.gradle.kts:212`). No new dependency, no build-breaker.

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
  Signature: `EmptyState(message: String, title: String? = null, modifier: Modifier = Modifier)` —
  renders message-only (no heading) when `title == null`, so it covers both shapes.
- **Cards** adopts it — its inline shape matches the component exactly → genuine no-op.
- **Weapons** (see §6.4 — actually dropped) / **Workshop-category** adopt it for the genuinely-missing case.
- **Supplies** is **left untouched this bundle.** Its existing empty-state is a *different shape*
  (single `bodyLarge` line in a `Box`, no title) — routing it through `EmptyState` would be a visual
  change, not a no-op. Unifying it is deferred (cosmetic; not defect-driven).

---

## 5. De-emoji Map (exact sites)

Decorative/narrative emoji (Help headings, onboarding slide-data icons) are **left untouched**.

### Currencies → `CurrencyValue` / `CurrencyCost`
| Site | Now | After |
|---|---|---|
| `LabsScreen.kt:48` | `🦶 ${steps}` | `CurrencyValue(STEPS, steps.toLong())` |
| `LabsScreen.kt:49` | `💎 ${gems}` | `CurrencyValue(GEMS, gems.toLong())` |
| `LabsScreen.kt:56` | `Unlock Slot (… 💎)` | label + `CurrencyCost(GEMS, …)` |
| `LabsScreen.kt:139` | `Rush (… 💎)` | `CurrencyCost(GEMS, …)` |
| `LabsScreen.kt:151` | `Start (${costToStart} 🦶)` | `CurrencyCost(STEPS, info.costToStart.toLong())` — **was missed; same Steps glyph as :48, on the primary action button** |
| `CardsScreen.kt:54` | `💎 ${gems} Gems` header | `CurrencyValue(GEMS, gems.toLong())` |
| `CardsScreen.kt:78` | `${cost}💎` pack | `CurrencyCost(GEMS, cost)` |
| `StoreScreen.kt:51` | `💎 %,d` balance | `CurrencyValue(GEMS, …)` |
| `StoreScreen.kt:62` | `%,d 💎 Gems` | `CurrencyValue(GEMS, …)` |
| `StoreScreen.kt:156` | `💎 ${priceGems}` | `CurrencyCost(GEMS, …)` |
| `MissionsScreen.kt:89` | `${gems} 💎` reward | `CurrencyValue(GEMS, …, style = bodySmall)` — see buildString note |
| `MissionsScreen.kt:92` | `${powerStones} ⚡` reward | `CurrencyValue(POWER_STONES, …, style = bodySmall)` — see buildString note |

> **Missions reward row is NOT a one-glyph swap.** `MissionsScreen.kt:88–95` concatenates the reward
> into a single `buildString` → one `Text(reward)`. A Composable can't live inside a Kotlin String, so
> this is a small layout rewrite: replace the `buildString`/`Text` with a `Row` containing
> `CurrencyValue(GEMS, rewardGems.toLong(), style = bodySmall)` and
> `CurrencyValue(POWER_STONES, rewardPowerStones.toLong(), style = bodySmall)`, each gated on `> 0`,
> with a separator (small `Spacer`/`Text(" + ")`) only when both show, and rendering nothing when both
> are zero (preserves today's empty-display behavior). The implementation plan pins this.

### Status glyphs → Material icons
| Site | Now | After |
|---|---|---|
| `MissionsScreen.kt:76,121` | `✓` claimed | `Icon(Icons.Default.CheckCircle, contentDescription = "Claimed")` |
| `UltimateWeaponScreen.kt:96` | `✓` equipped | `Icon(Icons.Default.Check, contentDescription = "Equipped")` |
| `CurrencyDashboardScreen.kt:116,129,154` | `✓ Claimed/Earned` | `Icon(Check, null)` + existing adjacent text |
| `CurrencyDashboardScreen.kt:172` | `✓` / `✗` week met/missed | `Check`/`Close` + `contentDescription = "met"/"missed"` (resolves the color-only week status) |
| `OnboardingScreen.kt:146` | `Step counting enabled ✓` | `Check` icon + text |
| `StoreScreen.kt:83` | `✅ Purchased` (Ad Removal) | `Icon(Icons.Default.CheckCircle, contentDescription = "Purchased")` + "Purchased" — **was missed** |
| `StoreScreen.kt:104` | `✅ Active — N days remaining` (Season Pass) | `Icon(Icons.Default.CheckCircle, contentDescription = "Active")` + text — **was missed** |
| `CardsScreen.kt:119` | `🆕`/`♻` pack-result new/dup | `Icon(Icons.Default.FiberNew, "New")` / `Icon(Icons.Default.Autorenew, "Duplicate")` + existing name/"+1 Copy" text — **was missed; "+1 Copy" already differentiates, so this is de-emoji consistency, not an a11y fix** |
| `BattleScreen.kt:193` | `▶`/`⏸` pause toggle | `Icon(Icons.Default.PlayArrow / Icons.Default.Pause, contentDescription = null)` — **was missed; button already carries `pauseDesc` contentDescription (`:192`), so icon stays decorative. HUD Compose only — does NOT touch the renderer/engine.** |

### Control glyphs → Material icons
| Site | Now | After |
|---|---|---|
| `CardsScreen.kt:63` | `🎬 Free Pack (Ad)` | `Icon(Icons.Default.Slideshow, contentDescription = null)` + "Free Pack (Ad)" inside the existing `OutlinedButton` — **was missed; ad/video affordance** |

### Stars → `Icons.Default.Star`
| Site | Now | After |
|---|---|---|
| `LabsScreen.kt:136` | `Free ⭐` | `Star` icon + "Free" |
| `StoreScreen.kt:101` | `⭐ Season Pass` | `Star` icon + "Season Pass" |

### Left as-is (decorative; deferred)
- `HelpScreen.kt` — 9 section-heading emoji (decorative narrative).
- `OnboardingSlide.kt` — slide-data icons incl. 🏛️ (owned by #164 onboarding-art work).
- *(All other presentation emoji sites were swept by the completeness audit; the above is exhaustive
  over UI-control/currency/status glyphs in `presentation/`.)*

---

## 6. Accessibility, Settings Rename, Loading & Empty States

### 6.1 a11y (label-less meaningful icons only — D2)
- **Onboarding pagination dots** (`OnboardingScreen.kt:115–129`): the dots are a *compound*
  indicator whose page position is conveyed by color+size alone — invisible to TalkBack (Compose
  `HorizontalPager` does **not** auto-announce "page X of N", so this is a real gap, not redundant).
  Treat the dot **row** as one semantic unit: put a single
  `contentDescription = "Page ${pagerState.currentPage + 1} of ${slides.size}"` on the row's container
  (or the active dot) and leave the individual dots without their own semantics (they're decorative
  mirrors of pager state). `i`/`n` come from the existing `repeat(slides.size)` loop already at the
  edit site — no new dependency. This yields one clean "Page 2 of 4" announcement, not per-dot noise.
- **New status icons** (Section 5): per-site `contentDescription` is pinned in the §5 tables (the
  authoritative source). The rule: an icon that is the *sole* carrier of the status gets a real
  description (Missions claimed → "Claimed", week → "met"/"missed"); an icon sitting next to text that
  already conveys the state gets `null` (Economy `✓ Claimed/Earned`). §5 classifies every site — the
  implementer adjudicates none.
- **Verified-compliant, NOT changed** (record so future reviewers don't re-flag): delete icon
  (Settings screen, `:76` — a decorative `Icon` inside a clickable `OutlinedCard` whose merged
  semantics already announce "Delete All Data, Permanently erase all progress"; the icon is never
  independently focusable), Home season-pass star (`HomeScreen.kt:146` — decorative `Icon` beside
  "Season Pass Active" text), `Icons.Filled.Upgrade` (`BattleScreen.kt:200` — parent button at `:199`
  carries `contentDescription`).

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
  `LoadingBox`. (Flip inside the `combine` mapper — as Home/Economy already do — so it clears on the
  first *Room* emission. For Store this means the catalog renders with its static fallback prices
  immediately; the later async billing-price query just swaps prices in-place. The spinner does **not**
  gate the billing query.)
- **Battle (excluded):** `BattleUiState.isLoading` also exists but is intentionally **not** a
  content-spinner — it's a one-way engine-init ordering gate (`LaunchedEffect(state.isLoading)` →
  configure → start polling, Issue #19) over a `SurfaceView`. **Do not add `LoadingBox` here**;
  `BattleScreen`/`BattleUiState` are untouched by the loading work (the only Battle edit is the
  §5 ▶/⏸ HUD de-emoji).
- **Trade-off (accepted):** local Room loads are sub-frame; the `isLoading=true` default +
  first-emission flip means the spinner only appears if the first emission is genuinely delayed.
  The flows use `SharingStarted.WhileSubscribed(5000)`, whose replay cache **never expires** (no
  `replayExpirationMillis` override anywhere — verified), so the cached state is retained across
  navigate-away/return; `isLoading=true` is consumed exactly **once** at first creation, **not** on
  every revisit. No re-flash on resume. Documented as a conscious choice (§7 adds a navigate-away-and-
  return on-device check to confirm this empirically).

### 6.4 Empty states (via `EmptyState`)
- **Weapons** — **no empty-state.** `UltimateWeaponScreen` renders a fixed 6-entry catalog
  (`UltimateWeaponType.entries.map` — locked UWs still render), so `weapons` is never *data*-empty;
  it's empty only in the synchronous pre-emission frame, which the §6.3 `LoadingBox` gate already
  covers. An `EmptyState` there would be unreachable dead code. Weapons still gets the §6.3 loading
  gate, just not an empty-state. *(Recorded so a future reviewer doesn't re-add it.)*
- **Workshop** per-category (`WorkshopScreen.kt:76`): `if (upgrades.isEmpty()) EmptyState(...)` —
  kept as **defensive** cover for the pre-seed transient (`observeAllUpgrades()` emitting before
  `ensureUpgradesExist()` lands). Every seeded category otherwise always has ≥4 Workshop-visible
  upgrades, so this is defense-in-depth, **not** a routinely-reachable data-empty state.
- **Cards**: refactor existing inline empty-state to call `EmptyState` — its shape matches the
  component exactly → genuine no-op.
- **Supplies**: **left untouched** (different shape; see §4.3 — unifying it would be a visual change,
  deferred).

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
- `./run-gradle.sh testDebugUnitTest lintDebug` → green; headline test count **973 → 974** (exactly
  one new test class, `CurrencyDisplayTest`).
- **On-device (emulator API 36):** de-emoji'd screens render Material icons (Labs incl. Start button /
  Cards incl. Free Pack + pack-result / Store incl. ✅ states / Missions / Economy / Battle pause
  toggle); Settings titled "Settings"; one loading + one empty state visually confirmed.
- **Navigate-away-and-return check (required):** on at least one `WhileSubscribed`-gated screen (e.g.
  Cards), enter it, leave for >6s, return — confirm the `LoadingBox` does **not** re-flash (validates
  the §6.3 no-re-flash claim, the one thing a static diff can't show).

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

**Deleted:** `domain/model/Currency.kt` — dead, unreferenced enum duplicated by the new presentation
`CurrencyType` (see §4.1). Pure deletion of unused code; no logic change. `DomainPurityTest` is
unaffected (deletion only).

**Modified:** `LabsScreen.kt`, `CardsScreen.kt`, `StoreScreen.kt`, `MissionsScreen.kt`,
`CurrencyDashboardScreen.kt`, `UltimateWeaponScreen.kt`, `OnboardingScreen.kt`, `WorkshopScreen.kt`,
`HomeScreen.kt` (spinner gate), `StatsScreen.kt` (spinner gate),
`presentation/battle/BattleScreen.kt` (§5 ▶/⏸ HUD de-emoji only — no engine/renderer change),
`MainActivity.kt` (Settings rename refs), `UltimateWeaponViewModel.kt` + `StoreViewModel.kt`
(+`isLoading`), their UiState classes, plus the doc files above.
*(Supplies is NOT modified — its empty-state is left as-is, see §4.3/§6.4.)*

**Untouched (fragile zones honored):** all of `data/`, `service/`, `domain/` **except the pure
deletion of the dead `Currency.kt`**, `presentation/battle/{engine,entities,effects}` (the renderer
and game loop — only the Compose HUD `BattleScreen.kt` pause-button glyph changes), `Screen.kt` route
definitions, `BattleUiState.kt` (loading work excludes Battle).
