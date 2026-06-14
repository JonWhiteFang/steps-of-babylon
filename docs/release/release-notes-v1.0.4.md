# Release Notes — v1.0.4 (versionCode 20)

**Track:** Play Console **internal**
**Tag:** `v1.0.4` · **Supersedes:** `v1.0.3` (versionCode 19, 2026-06-12)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim:

```
A fresh coat of polish for your climb!

• Cleaner screens: we removed a stray bar above the battle, sharpened the controls, and tuned colours for easier reading
• Currency now shows neat thousands separators, so big numbers are simple to scan
• A back arrow on screens like Weapons, Cards and Supplies makes getting around effortless
• Tapping a tab now always takes you to its home screen

Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

This is a **presentation-only** release — four look-&-feel PRs merged to `main` since v1.0.3, all of
which were on-device verified and passed the CI PR gate + instrumented lane. **No gameplay, economy,
concurrency, persistence/schema, or battle-engine code was touched.** Full per-PR detail is in
`CHANGELOG.md` under `[1.0.4]`; this is the release-collateral summary.

### Look-&-feel polish pass — design tokens + visual consistency (#159, squash `2dc9a08`)

- Removed the redundant platform **ActionBar** app-wide (new `Theme.StepsOfBabylon` `NoActionBar`
  theme + `windowBackground = DeepBronze` to kill the cold-start white flash).
- Hid the bottom nav bar **during onboarding**.
- Added **design-system tokens** (`Type.kt` `SobTypography`, `Shape.kt` `SobShapes`, role/currency/
  status colour tokens) and fixed a **WCAG** contrast fail (LapisLazuli-as-text → `LapisLight`).
- De-emoji'd Home/Economy controls → Material icons; palette-aligned currency colours; thousands
  separators; fixed verified visual bugs (Cards double-gem header, Stats legend label + `toArgb`,
  biome-title capitalization, Store "1 Gems" plural). ADR-0022.

### Look-&-feel Bundle A — correctness & accessibility cleanup (#160, squash `491815b`)

- New shared `presentation/ui/` layer: `CurrencyDisplay.kt` (single source of truth for currency
  presentation), `LoadingBox.kt`, `EmptyState.kt`.
- Finished the **de-emoji sweep** across Labs/Cards/Store/Missions/Economy/Weapons/Onboarding/Battle-HUD.
- **Accessibility:** onboarding pagination dots now carry a row-level `contentDescription`
  ("Page N of M"); correct per-site descriptions on new status icons.
- Screen-level **loading spinners** on 10 menu screens; Workshop defensive empty-state.
- **Settings rename** (`NotificationSettings*` → `Settings*`; route string unchanged); deleted dead
  `domain/model/Currency.kt`.

### Look-&-feel Bundle B PR-B1 — navigation back affordances (#161, PR #166, squash `4d6947d`)

- New shared `presentation/ui/SobTopAppBar.kt` (`CenterAlignedTopAppBar`: centered title + back
  arrow), rendered once in MainActivity's outer Scaffold, gated by the pure `Screen.secondaryTitle(route)`
  helper → appears on exactly the 8 push-nav secondary screens, nowhere else. Back = `navigateUp()`.

### Look-&-feel Bundle B PR-B2 — bottom-nav restore-wrong-screen bug fix (#161, PR #167, squash `b4f2a2b`)

- Fixed a navigation bug where tapping a bottom-nav tab could land on a previously-viewed sub-screen
  instead of that tab's root. A tab tap now goes to the tab root (`popUpTo(Home)` + `launchSingleTop`,
  no save/restore), extracted to a shared `NavOptionsBuilder.bottomNavOptions()`. ADR-0023.

> **Test count:** **981 JVM tests** (was 973 at v1.0.3; +8 across the three new test files —
> `CurrencyDisplayTest` +2, `ScreenSecondaryTitleTest` +4, `BottomNavRestoreTest` +2) + 9 instrumented.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 19 → 20; versionName 1.0.3 → 1.0.4.
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally (981 tests, 0 failures) before tagging.

### On-device spot-checks worth a glance on the internal build

- **Battle HUD vertical offset:** the in-round HUD carries a hardcoded `top = 80.dp` pad
  (`BattleScreen.kt:119`) that pre-dated the ActionBar removal (#159) and was not adjusted, so the HUD
  may now sit ~56dp higher than intended. Visual-only — no gameplay/economy/persistence impact. A
  follow-up presentation tweak, not a ship-blocker.
- The visual polish across all menu screens (de-emoji, contrast, loading/empty states) and the
  navigation back-affordances were on-device verified pre-merge; worth a confirming pass on the
  signed internal build.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). The remaining code-actionable gate items are **#29** (upgrade
decision-support, Gate F) and **#26** (performance/battery, Gate G); the manual play-feel gates
(A audio, E balance) are the developer's call. The ≥14-day tester soak and production access are
Phase 2 — they begin *after* the developer decides to promote.
