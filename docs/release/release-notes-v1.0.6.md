# Release Notes — v1.0.6 (versionCode 22)

**Track:** Play Console **internal**
**Tag:** `v1.0.6` · **Supersedes:** `v1.0.5` (versionCode 21, 2026-06-14)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim (280 chars):

```
The game now feels alive in your hands:

• Tap feedback on purchases, upgrades, and claims
• Reward celebrations when you collect missions, milestones, and supply drops
• A richer end-of-round summary

Toggle haptics anytime in Settings. Thanks for playing — keep walking!
```

---

## What shipped (developer detail)

Look & Feel **Bundle C (#162)** — the *feedback / feel* wave of the 2026-06-12 UX review (PR #172,
squash `5f0d5bf`), plus the post-v1.0.5 doc-drift sweep that sat in `[Unreleased]`.
**Presentation-only** — zero engine / economy / domain / concurrency / persistence / schema change
(0 lines touched in `domain/**`, `service/**`, `di/**`, `battle/{engine,entities,effects}`,
`BattleViewModel`, `RoundEndState`/`BattleUiState`, `Screen.kt`, DAOs/entities/migrations, build
files). Full detail is in `CHANGELOG.md` under `[1.0.6]`.

### Haptics infrastructure (greenfield — there were zero haptics in the app)

- New `data/HapticsPreferences.kt` (SharedPreferences `"haptics_prefs"`, default ON, mirrors
  `SoundPreferences`) + `presentation/ui/Haptics.kt` (`tap()` VIRTUAL_KEY / `success()` CONFIRM over
  `View.performHapticFeedback`, gated on the pref read at *call* time — toggling Settings takes effect
  on the next tap, no `VIBRATE` permission) + `rememberHaptics()`.
- New Settings **"Haptic Feedback"** toggle (default ON, independent of Reduced-Motion). Wired to
  purchase / equip / claim / BATTLE-start (incl. Post-Round Play-Again) / pause taps.

### Shared, bigger purchase pulse

- Extracted the inline Workshop `UpgradeCard` 1.05× pulse into reusable
  `presentation/ui/PurchasePulse.kt` (`rememberPulse()` + `Modifier.pulseScale()`), enlarged to
  **1.12×** (`graphicsLayer` scale — no layout reflow), and applied it (with a `tap()` haptic) across
  all spend buttons (Workshop, in-round, Store, UW, Cards, Labs). Equip / BATTLE-start / pause / resume
  get the haptic only. The 3 real-money Store buttons fire the pulse+haptic inside the `!isPurchasing`
  guard.

### Post-Round + claim celebrations

- The "Round Over" overlay gains an entrance animation (hosted in `BattleScreen` via
  `AnimatedVisibility` keyed on the round-end **nullability transition** — watch-ad copies don't
  re-trigger it) + a **staggered reward-line sting** over the lines actually present this round, each
  firing a `success()` haptic. Compose-HUD only; renderer/engine untouched.
- Missions / Milestones / Supplies claims fire a one-shot reward chip
  (`presentation/ui/ClaimCelebration.kt`) + `success()` haptic, from a new conflated `Channel`-backed
  `celebration` event on each claim VM, gated on `Result.Success` (the 3 `UnknownCosmetic` milestones
  never celebrate; `claimAll` celebrates once, only if ≥1 drop succeeded).

### Deferred (audio-debt track)

- Reward **SFX** is blocked on the placeholder sine-tone audio debt; the animation/haptic hooks are
  designed so a later SFX call slots in beside the haptic with no rework.

> **990 JVM tests** (up from 981 at v1.0.5 — +1 `HapticsPreferencesTest` Robolectric, +4 supplies VM,
> +4 missions VM, incl. the ticker-safe `@VisibleForTesting cancelForTest()` harness + pure top-level
> `supplyLabel`/`missionRewardLabel` builders) + 9 instrumented. View/Compose helpers untested per the
> house norm.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 21 → 22; versionName 1.0.5 → 1.0.6.
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (990 tests, 0 failures) + CI PR gate + instrumented lane before
  tagging. On-device feel sign-off (haptics, pulse, celebrations, reduced-motion) done by the developer
  before release.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). The remaining code-actionable gate items are **#29** (upgrade
decision-support, Gate F) and **#26** (performance/battery, Gate G); the manual play-feel gates
(A audio, E balance) are the developer's call. The look-&-feel bundles **#163** (UW/Card rarity
visuals) and **#164** (custom font + onboarding theming + real ziggurat asset) are the next feature
work. The ≥14-day tester soak and production access are Phase 2 — they begin *after* the developer
decides to promote.
