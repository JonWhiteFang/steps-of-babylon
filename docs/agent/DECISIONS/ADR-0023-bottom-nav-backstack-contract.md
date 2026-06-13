# ADR-0023: Bottom-nav tab tap goes to the tab root (no save/restore of push-children)

**Status:** Accepted (2026-06-13, Bundle B PR-B2, #161)

## Context

The app's bottom navigation (`BottomNavBar`) originally used the canonical Google
multi-back-stack idiom for every tab tap:

```kotlin
navController.navigate(route) {
    popUpTo(Screen.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

That idiom **saves and restores each tab's entire nested sub-stack**. It is correct when
each tab owns an independent navigation graph (Instagram-style: drill into tab A's detail,
switch to B, return to A → A's detail is preserved).

This app does **not** match that shape. The `NavHost` is a single **flat** graph (no nested
`navigation{}` sub-graphs): the 5 tabs (Home/Workshop/Battle/Labs/Stats) and the push-only
children (Cards, Weapons — reached via plain `navigate()` from Workshop) are all top-level
`composable()` destinations. So a pushed child like Cards becomes part of "Workshop's saved
branch," and `restoreState = true` **resurrected the child** when the user returned to the
Workshop tab — landing them on Cards instead of the Workshop root (#161, the
"restore-wrong-screen" bug).

On-device repro (confirmed before fixing, per `systematic-debugging`):
`Home → Workshop(tab) → Cards(push) → Stats(tab) → Workshop(tab)` landed back on **Cards**.
Note the original #161 report described "Cards → tap Home → Cards"; that exact path did **not**
reproduce — the defect surfaces on returning to the **owning** tab (Workshop), not on the Home
tap. The device repro corrected the reported symptom before any code changed.

## Decision

A bottom-nav tab tap means **"go to that tab's root, always."** Drop `saveState`/`restoreState`:

```kotlin
fun NavOptionsBuilder.bottomNavOptions() {
    popUpTo(Screen.Home.route)
    launchSingleTop = true
}
```

The NavOptions are extracted into the shared `bottomNavOptions()` builder
(`presentation/navigation/BottomNavBar.kt`) so the regression test drives the **exact** options
the bar uses, not a hand-copied approximation.

**Contract for future changes:** as long as the graph is flat (push-children are top-level
destinations rather than members of a per-tab nested graph), bottom-nav navigation must **not**
use `saveState`/`restoreState`. If per-tab state preservation is ever genuinely wanted, the
prerequisite is restructuring the graph so each tab owns a nested `navigation{}` sub-graph — at
which point the save/restore idiom becomes correct and can be reconsidered.

## Alternatives considered

- **A — `popUpTo(graph.findStartDestination().id)` instead of `popUpTo(Home.route)`.** Rejected:
  proven a **no-op** here (the graph is flat and Home *is* the start destination, so the two forms
  are identical). It does not change which id the saved branch is keyed under, so it does not fix
  the bug. (Confirmed at the navigation-compose 2.9.8 bytecode level during the design review.)
- **B — Nest Cards/Weapons under a Workshop `navigation{}` sub-graph, keep save/restore.** This is
  the "correct" long-term shape if per-tab sub-stack preservation is desired, but it is a larger
  structural change to the `NavHost` for a bug-fix PR, and the product intent here is "tab tap →
  tab root," which save/restore actively fights. Deferred; recorded as the prerequisite above.
- **C — Explicitly pop push-children on tab switch while keeping save/restore.** More moving parts
  than simply not saving them; same observable result as the chosen fix but harder to reason about.

## Consequences

- **Positive:** tab taps are now predictable (always land on the tab root); the bug is fixed for
  every owning-tab case, not just the originally-reported path; the shared `bottomNavOptions()`
  builder makes the behaviour testable and gives one place to change it.
- **Negative / tradeoffs:** cross-tab **scroll-position preservation is not retained** — switching
  away from a tab and back resets its scroll. Acceptable: the tab screens are short, and that
  preservation was unreliable anyway (it was entangled with the bug). System Back and Home-tile
  pushes are unaffected (they don't route through `BottomNavBar`), verified on-device.
- **Follow-ups:** none required. Guarded by `BottomNavRestoreTest` (JVM, `TestNavHostController`,
  red-before-green verified). If a future tab needs persistent internal navigation, revisit via
  alternative B.

## Links
- Commit(s): `88e1720` (fix + regression guard). Branch `fix/bundle-b-nav-restore` (PR-B2).
- Spec: `docs/superpowers/specs/2026-06-13-look-and-feel-bundle-b-design.md` §5.
- Related ADRs: ADR-0022 (look-and-feel direction this bundle belongs to). Sibling PR-B1 (back
  affordances) shipped in #166.
