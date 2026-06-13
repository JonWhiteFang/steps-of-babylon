# Look & Feel — Bundle B: Navigation (back affordances + bottom-nav restore-wrong-screen bug)

**Date:** 2026-06-13
**Issue:** #161
**Source review:** `docs/external-reviews/2026-06-12-look-and-feel-ux-review.md` (§4 HIGH Navigation/IA, §10 Back/gesture nav, Remaining Rec 6)
**Predecessors:** #159 (design tokens / ActionBar removal), #160 / PR #165 (Bundle A — de-emoji, loading/empty, a11y, Settings rename)
**Status:** Design approved; ready for implementation plan.
**Adversarial review:** A 5-lens fan-out (insets/layout, back/deep-link semantics, bug root-cause, test strategy, scope/fragile-zones) with a per-finding verify stage — 26 findings, 23 confirmed/partial, 3 refuted. Verify stage decompiled `navigation-compose 2.9.8` to confirm the B2 mechanism and corrected several over-claimed severities. This design folds in every confirmed finding.

---

## 1. Goal & Scope

Bundle B is the **navigation** wave of the 2026-06-12 review. It has two halves with different risk
profiles, so it ships as **TWO SEQUENTIAL PRs — PR-B1 merges to `main` before PR-B2 starts.**

- **PR-B1 — Back/up affordances** (presentation-only, low risk): a shared top bar giving the 8
  secondary (push-navigated) screens a visible back control + a consistent, correctly-styled title.
- **PR-B2 — Bottom-nav restore-wrong-screen bug** (NavHost back-stack logic defect): tapping a bottom-nav
  tab after visiting a push-child (e.g. Cards) restores the wrong screen. Gets `systematic-debugging`
  treatment with an on-device repro **before** any code change, plus an ADR and a regression guard.

**Explicitly out of scope (other bundles — do NOT do here):**
- Haptics + reward/claim animation + purchase pulse → #162 (Bundle C)
- UW + Card rarity visual system → #163 (Bundle D)
- Custom font + onboarding per-slide theming + ziggurat asset → #164 (Bundle E)
- Any change to the battle renderer/engine/effects, economy, or concurrency.

---

## 2. Decisions (locked during brainstorming + adversarial review)

| # | Decision | Choice | Driver |
|---|---|---|---|
| D1 | Back-affordance UI | **Shared centered top bar** (`CenterAlignedTopAppBar`) with a back arrow + centered title. | User pick; also fixes the review's "inconsistent title sizes" Medium in one place. |
| D2 | Bar placement | **Single bar in MainActivity's OUTER Scaffold `topBar` slot**, gated to the 8 push-child routes via a route→title map — NOT a per-screen bar threaded through each composable. | Review (insets-layout, scope-fragile): one inset path, no per-screen structural divergence, no `onNavigateBack` param, smaller diff. |
| D3 | Back action | **`navController.navigateUp()`** (not bare `popBackStack()`). | Review (back-deeplink): mirrors system/predictive back exactly; one mechanism, three affordances stay consistent. |
| D4 | Title source | **Explicit per-screen title map** (NOT derived from `Screen.label`); delete the now-duplicated inline headers. | Review (scope-fragile): `Screen.label` would give Supplies/Economy worse titles than today, and an un-deleted header double-renders. |
| D5 | PR split | **Two sequential PRs**; B1 merged before B2 begins. | User pick; isolates the risky logic change, independently revertable. |
| D6 | B2 fix approach | Decided by `systematic-debugging` + on-device repro. The `popUpTo(graph.startDestination)` idea is a **no-op** here and is explicitly NOT the fix (§5.2). | Review (bug-rootcause): verified at the bytecode level. |
| D7 | B2 test lane | **JVM Robolectric + `TestNavHostController`**, enabled by extracting a Hilt-free `AppNavHost`. Runs in the fast PR gate. | User pick + review (test-strategy): the bug lives in `navigation-common` (JVM), not the device framework. |

---

## 3. Audit Corrections vs. the Review (refuted / down-rated claims)

Recorded so a future reviewer doesn't re-raise them:

| Claim raised | Disposition |
|---|---|
| Back arrow `popBackStack()` becomes a dead button / cold-launch exit trap on deep-linked Store/Supplies/Missions | **Refuted.** NavHost commits `startDestination = Home` synchronously *before* the deep-link collector fires (`MainActivity.kt:191-208`), so the target is always pushed on top of Home — a parent always exists. D3 (`navigateUp()`) makes it moot regardless. |
| "Same Store screen, two back destinations" is a defect | **Refuted.** Returning to the actual previous screen is correct push-nav behavior; no notification currently deep-links to `store` (verified: deep-link producers target only `supplies/workshop/missions/battle`). |
| `WindowInsets(0)` fragile-coupling is a live bug | **Refuted as a *current* defect** (the bar doesn't exist yet) — but folded into §4.1 as a forward design constraint with a parity check. |
| B2 fix = `popUpTo(graph.findStartDestination().id)` | **Confirmed no-op.** Flat graph + Home IS the start destination ⇒ identical to `popUpTo(Home.route)`. Dropped as "the fix" (§5.2). |
| B2 root-cause "saves into Workshop's slot" | **Corrected.** The sub-stack is keyed under **Home's** id (Home is the graph start), restored on the first **Home**-tab tap — not Workshop's. Symptom statement fixed (§5.1). |
| Instrumented test is *required* (real framework) | **Down-rated.** `saveState`/`restoreState` live in `navigation-common` (JVM-testable); D7 uses the JVM lane. |

---

## 4. PR-B1 — Shared top bar + back affordances

### 4.1 New component — `presentation/ui/SobTopAppBar.kt`

```kotlin
@Composable
fun SobTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
)
```
- Wraps Material3 `CenterAlignedTopAppBar`.
- `navigationIcon` = `IconButton(onClick = onNavigateBack)` with `Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")` (the arrow is already used in `BattleScreen.kt:141`).
- `title` = centered `Text(title)` using the token typography (one place → fixes "inconsistent title sizes").
- **No `actions` slot** (YAGNI — D2). Economy's "go to Store" CTA stays in the screen body; only its title moves to the bar.
- **`windowInsets = WindowInsets(0)`** — the outer Scaffold already consumes the top status-bar inset via `Modifier.padding(innerPadding)` on the NavHost (`MainActivity.kt:263`), so the bar must not double-pad. Because the bar lives in the **same** outer Scaffold as the content (D2), there is exactly **one** inset path for all 8 screens — the insets-layout review's "two divergent paths" risk is structurally eliminated, not merely worked around.
- **What it does / why / depends on:** maps (title, back-action) → a styled, inset-correct app bar. One component, applied once in MainActivity. Depends only on Material3 + the token typography. Themed-bar art (later) becomes a one-file change.

### 4.2 Placement — outer Scaffold `topBar`, gated to push-children

In `MainActivity` the outer `Scaffold` (currently `topBar`-less, `:210`) gains a `topBar`:

```kotlin
topBar = {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    Screen.secondaryTitle(route)?.let { title ->
        SobTopAppBar(title = title, onNavigateBack = { navController.navigateUp() })
    }
}
```
- `Screen.secondaryTitle(route): String?` is a **pure** companion helper returning the bar title for the 8 push-children and `null` for everything else (tabs, Battle, Onboarding) → the bar renders **only** on the 8 screens. This mirrors the existing bottom-nav hide predicate (`MainActivity.kt:226-228`) but is centralized and unit-testable.
- The 8 screens need **no signature change** — no `onNavigateBack` param threaded anywhere. (Smaller diff than the per-screen approach; confirmed by the scope-fragile lens.)
- `secondaryTitle` lives in `Screen`'s companion **next to** the existing `by lazy` lists but does **not** mutate them — no new route, no change to `items`/`allScreens`/`argumentFreeRoutes`, so the init-order fragile zone and the pinned 13-route set are untouched (verified).

### 4.3 The 8 screens + explicit title map (D4)

| Screen | Bar title | Inline header today | Action in B1 |
|---|---|---|---|
| Weapons (`UltimateWeaponScreen`) | **"Ultimate Weapons"** | none (top text is the "Power Stones: N" stat) | add bar title; stat line stays |
| Cards (`CardsScreen`) | **"Cards"** | none (top text is "N cards") | add bar title; count row stays |
| Supplies (`UnclaimedSuppliesScreen`) | **"Unclaimed Supplies"** | `:46` "Unclaimed Supplies" | **delete** inline header (moves to bar) |
| Economy (`CurrencyDashboardScreen`) | **"Premium Currencies"** | `:55` "Premium Currencies" | **delete** inline header; keep Store CTA in body |
| Missions (`MissionsScreen`) | **"Missions"** | `:51` "Daily Missions" + `:64` "Walking Milestones" (two **section** headers) | add bar title; **keep both section headers** |
| Settings (`SettingsScreen`) | **"Settings"** | `:31` "Settings" | **delete** inline header |
| Store (`StoreScreen`) | **"Store"** | `:61` "Store" | **delete** inline header |
| Help (`HelpScreen`) | **"Help"** | `:27` "Help" | **delete** inline header |

The 5 bottom-nav tabs (Home/Workshop/Battle/Labs/Stats) are excluded. **Battle** is push-navigated but is a genuine tab with its own `onExitBattle`/`popBackStack` affordance (`MainActivity.kt:336`) and the bottom nav is already hidden on it — `secondaryTitle` returns `null` for it. **Onboarding** is the deliberate 9th-but-excluded push destination (Settings "Replay tutorial", `:357`): it must read as a self-contained first impression and owns its own finish/back flow — `secondaryTitle` returns `null` for it too.

### 4.4 Inner-Scaffold reconciliation

Cards, Missions, Store (and tabs Workshop, Labs) each own an **inner** `Scaffold(snackbarHost = …)`. With the bar in the **outer** Scaffold (D2):
- The inner Scaffolds keep their snackbar hosts unchanged — no snackbar re-anchoring.
- Their content already consumes the inner `innerPadding`; the outer bar reserves its own height above the NavHost content, so the inner content sits below the bar correctly (single outer inset path).
- **No inner Scaffold is restructured.** (Avoids the "double-padding / divergent path" risk the insets lens flagged for any mixed approach.)

### 4.5 PR-B1 risk & tests
- **Risk: Low.** Pure presentation. No `Screen.kt` route changes → `DeepLinkRoutingTest`/`OnboardingRoutingTest` unaffected (re-run green as a guard). No economy/concurrency/renderer touch. Honors every STATE.md fragile zone.
- **New JVM test — `ScreenSecondaryTitleTest`:** pins `Screen.secondaryTitle(route)` → returns the exact title for each of the 8 push-children and `null` for every tab + Battle + Onboarding + unknown route. This is the load-bearing logic (which screens get a bar); the bar composable itself is a thin visual helper (no JVM test, per the Bundle-A norm). Headline JVM count **975 → 976**.
- **On-device (emulator API 36):** each of the 8 screens shows the bar + correct centered title; back arrow returns to the correct parent (Weapons/Cards → Workshop; Store → Economy *or* Home depending on entry; others → Home); no title double-renders; no status-bar overlap or gap; tabs/Battle/Onboarding show **no** bar.

---

## 5. PR-B2 — Bottom-nav restore-wrong-screen bug

### 5.1 Confirmed root cause (verified against navigation-compose 2.9.8)

`BottomNavBar.kt:23-27` uses, for **every** tab:
```kotlin
navController.navigate(screen.route) {
    popUpTo(Screen.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```
Push-children (Cards, Weapons, …) are reached via **optionless** `navigate()` from their parent (e.g. Workshop → Cards, `MainActivity.kt:326`), so a real back stack is `Home → Workshop → Cards`.

When a tab is tapped, `popUpTo(Home){saveState=true}` saves the popped sub-stack `[Workshop, Cards]` into `backStackStates`, **keyed under Home's destination id** (Home is the graph start; the non-inclusive save path keys by walking the `popUpTo` target up its parent chain). On the **first Home-tab tap**, `restoreState=true` with target=Home finds Home's id in `backStackMap` and re-instantiates the whole saved deque → the user lands on **Cards** (with Workshop beneath) instead of a clean Home.

**Reachable repro:** `Home → Workshop(tab) → Cards(push) → Home(tab)` ⇒ lands on Cards. (The review's original "from Cards, tap Home" is the same defect via a different path.) It's a wrong-screen UX glitch — no crash, no data loss — trivially recovered by pressing Back; corrected severity **medium**.

### 5.2 What the fix is NOT
`popUpTo(graph.findStartDestination().id)` instead of `popUpTo(Home.route)` is a **no-op** here: the NavHost is a single flat graph and Home **is** the start destination, so both forms key the save under Home's id identically. Swapping it fixes nothing. The fix must change **save/restore scoping**, not the `popUpTo` target spelling.

### 5.3 Fix direction (decided during debugging, not pinned here)
Per D6, `systematic-debugging` reproduces on device first, then selects among (to be validated, not assumed):
- **(a)** Nest Cards/Weapons under a Workshop **subgraph** so their saved state keys under Workshop, not Home (most idiomatic; keeps per-tab state).
- **(b)** Scope `saveState`/`restoreState` so push-children are dropped from the saved stack.
- **(c)** Explicitly pop push-children on tab-switch.

**Invariants the fix MUST preserve** (all review-confirmed):
1. Keep per-tab state for genuine tabs (Stats/Labs/Workshop `LazyColumn` scroll position survives tab round-trips — they use implicit `rememberLazyListState`).
2. Leave Battle's push-from-Home + `popBackStack` exit and the "hide bottom nav on Battle/Onboarding" logic untouched.
3. Cover the **deep-link / child-push entry path** too (`MainActivity.kt:203`, `:271-277`): a deep-link to a child followed by a tab tap is the same bug class and must not desync `backStackMap`.
4. Don't remove `launchSingleTop` (orthogonal to the bug; prevents duplicate tab entries).

### 5.4 Test strategy (D7)

**Precursor refactor — extract `AppNavHost`:** the nav graph is currently inline in `MainActivity.onCreate.setContent` on a Hilt `@AndroidEntryPoint` with 6 `@Inject` deps + service starts + permission launchers, so no faithful NavHost test can run without booting the whole Activity. Extract a **Hilt-free** composable:
```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    /* screen-content lambdas / callbacks passed in from MainActivity */
)
```
MainActivity keeps owning DI, services, permissions, music, deep-link collection, and the Scaffold (incl. the §4.2 `topBar`); it just calls `AppNavHost(...)`. This makes the real graph + real `BottomNavBar` hostable in a JVM test and is reusable for future nav tests.

**Add (pinned) test dependency** — neither `navigation-testing` nor `compose-ui-test` exists today. Add `androidx.navigation:navigation-testing` (+ `compose-ui-test-junit4` / `-manifest` if a `ComposeTestRule` is needed, via the Compose BOM) as `testImplementation`, **version-pinned in `gradle/libs.versions.toml`** (never hardcode versions). Budget for "first Compose/NavHost test in this project" setup.

**The guard — `BottomNavRestoreTest` (JVM Robolectric):** must **faithfully** reproduce the bug, or it passes before *and* after the fix:
- Drive navigation through the **same NavOptions** `BottomNavBar` uses (render the real `BottomNavBar` + click items, or invoke the identical `popUpTo/saveState/launchSingleTop/restoreState` block) — **not** ad-hoc `navigate()` calls.
- Push Cards via the same **optionless** `navigate()` Workshop uses.
- **Red-before-green:** assert it FAILS on pre-fix `HEAD`, passes after.
- Assert **both** current route == `home` **and** the back stack no longer contains `cards` (not merely route == home).
- Add a no-regression assertion that a tab's scroll/state survives a `Workshop → Stats → Workshop` round-trip (invariant 1).

Headline JVM count rises with B2's test(s); **instrumented stays at 9** (the guard is JVM, not emulator). Update `CLAUDE.md` Testing line + `STATE.md` to the new JVM total.

### 5.5 PR-B2 risk
**Low–medium.** Touches shared bottom-nav back-stack behavior + a MainActivity refactor (AppNavHost extraction). Isolated to its own PR, independently revertable, behind a red-before-green guard. Warrants a short **ADR** recording the bottom-nav back-stack contract (push-children must not be saved/restored into a tab's slot) so it joins the documented fragile zones.

---

## 6. File-Touch Summary

### PR-B1
**New:** `presentation/ui/SobTopAppBar.kt`, `test/.../ScreenSecondaryTitleTest.kt`.
**Modified:**
- `presentation/navigation/Screen.kt` — add pure `secondaryTitle(route)` helper to the companion (no list/route changes).
- `presentation/MainActivity.kt` — add outer-Scaffold `topBar` rendering `SobTopAppBar` gated on `secondaryTitle`.
- `UnclaimedSuppliesScreen.kt`, `CurrencyDashboardScreen.kt`, `SettingsScreen.kt`, `StoreScreen.kt`, `HelpScreen.kt` — **delete** the now-duplicated inline title header (the bar now carries it).
**Untouched (no header to delete, no param threaded — the bar is added entirely in MainActivity):**
`UltimateWeaponScreen.kt`, `CardsScreen.kt` (no title header today), `MissionsScreen.kt` (keeps its two **section** headers), plus all `data/`, `service/`, `domain/`, `presentation/battle/{engine,entities,effects}`, `BottomNavBar.kt`, route definitions.

### PR-B2
**New:** `presentation/navigation/AppNavHost.kt` (extracted), `test/.../BottomNavRestoreTest.kt`, `docs/agent/DECISIONS/ADR-00XX-bottom-nav-backstack-contract.md`.
**Modified:** `BottomNavBar.kt` (the fix), `MainActivity.kt` (call `AppNavHost`), `gradle/libs.versions.toml` + `app/build.gradle.kts` (pinned test deps).
**Untouched:** the renderer/engine/effects, economy, concurrency; `Screen.kt` route lists.

---

## 7. Docs (PR Task-List Convention — both PRs)

Sync current-state docs BEFORE the STATE/RUN_LOG update:
- **PR-B1:** `docs/steering/source-files.md` (add `SobTopAppBar.kt`, note header moves); `CLAUDE.md` test-count (975 → 976); `CHANGELOG.md` `[Unreleased]`. No ADR (implements ADR-0022 token direction).
- **PR-B2:** `docs/steering/source-files.md` (add `AppNavHost.kt`, `BottomNavRestoreTest`); `docs/steering/structure.md` (AppNavHost extraction); `CLAUDE.md` test-count + fragile-zone note (bottom-nav back-stack contract); `CHANGELOG.md`; `docs/plans/master-plan.md` status if tracked; **new ADR** for the back-stack contract.
- Both: `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md` via `/checkpoint`.
- On merge: comment on #161; close it only after **both** PRs land.
