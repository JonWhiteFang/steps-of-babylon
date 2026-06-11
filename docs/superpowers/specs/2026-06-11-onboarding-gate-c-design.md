# Design — First-Launch Onboarding (Gate C slice of V1X-22 / #24)

**Date:** 2026-06-11
**Issue:** #24 (V1X-22) · **Gate:** Closed-Test Readiness Gate **C** (`docs/plans/plan-FORWARD.md`)
**Status:** approved — ready for implementation plan

## 1. Goal & scope

A brand-new player must understand the **walk → spend → battle** loop before being dropped into Home,
and must grant the activity-recognition permission *with* context rather than via a cold system
dialog. Satisfying this closes Readiness Gate C:

> *"A brand-new player understands the walk → spend → battle loop (onboarding / tutorial / first-walk
> moment)."*

**In scope**
- One-time full-screen tutorial **carousel** (4 slides) shown on first launch, before Home.
- A **permission primer** as the final slide (rationale → triggers the real system permission dialog).
- **Settings** opt-out / **replay tutorial**.
- Gating the existing **cold permission request** (`MainActivity`) behind onboarding-completion so the
  carousel and the system dialog don't collide.

**Explicitly out of scope** (deferred to a later PR; these pair with telemetry #23 / V1X-21, which is
Phase 3 — building them now means flying blind on whether they work):
- D2 / D7 re-engagement push notifications.
- First-time wave-5 celebration animation.
- Projected-reward estimates ("a 15-min walk ≈ enough Steps for 2 upgrades").

**Product decision (resolved):** the original V1X-22 "100 free Steps welcome bonus" is **rejected** —
it violates the hard invariant *Steps are never generated in-game* (`CONSTRAINTS.md` / `START_HERE.md`
/ `product.md`). Onboarding is **explain-only**: no currency is granted. The first-session
call-to-action is "Go for a walk to earn your first Steps." No ADR exception is created; the invariant
stands unbroken.

## 2. Components & layering

New `presentation/onboarding/` package + one `data/` preferences class:

```
data/onboarding/
└── OnboardingPreferences.kt        device-local completion flag (SharedPreferences)

presentation/onboarding/
├── OnboardingSlide.kt              pure slide content (icon, title, body) + the canonical 4-slide list
├── OnboardingViewModel.kt          reads/writes completion flag; exposes StateFlow; slide list
└── OnboardingScreen.kt             HorizontalPager carousel + permission primer (Compose)
```

- **`OnboardingPreferences`** (`data/onboarding/`, because it touches Android `SharedPreferences`) —
  `@Singleton`, Hilt-injected, mirrors `MusicPreferences` / `AntiCheatPreferences`. Stores one
  boolean `hasCompletedOnboarding` (file `onboarding_prefs`). Exposes `hasCompletedOnboarding(): Boolean`,
  `setCompleted()`, and `reset()` (for Settings "replay tutorial").
- **`OnboardingScreen`** — Compose `HorizontalPager` (Compose dependency already present), 4 slides,
  page-dot indicator, **Skip** (top-right) + **Next / Back** controls. The final slide's primary button
  is "Enable step counting" and triggers the permission request, then completes onboarding.
- **`OnboardingViewModel`** — Hilt `@HiltViewModel`, injects `OnboardingPreferences`. Exposes the slide
  list and a `completeOnboarding()` that flips the flag. Pure enough to unit-test with a fake prefs seam.
- **Routing** (in `MainActivity`) — a new `Screen.Onboarding` route. `startDestination` is
  `Screen.Onboarding.route` **iff** `!hasCompletedOnboarding`, else `Screen.Home.route` as today. On
  completion the flag is set and the nav controller navigates to Home, popping Onboarding off the back
  stack so Back doesn't return to it.

## 3. The four slides (content)

1. **Walk to power your ziggurat** — real-world steps become **Steps**, the permanent currency that
   fuels everything. Steps are earned *only* by walking.
2. **Spend Steps in the Workshop** — permanent upgrades make your tower stronger across three
   categories: Attack / Defense / Utility.
3. **Send it into battle** — your ziggurat auto-battles waves of enemies; survive, climb tiers, and
   unlock new biomes.
4. **Enable step counting** — rationale for `ACTIVITY_RECOGNITION` ("we need this permission to count
   your steps"); button fires the system dialog. Notifications framed as optional. Closing
   call-to-action: *"Go for a walk to earn your first Steps."*

Copy stays consistent with the existing Help screen's vocabulary (Steps / Workshop / Battle / Tiers /
Biomes).

## 4. Permission-flow change (the careful bit)

`MainActivity`'s first-launch `LaunchedEffect` currently fires a **cold** permission request
(`ACTIVITY_RECOGNITION` + `POST_NOTIFICATIONS`, then chains Health Connect) on every launch when
ungranted, with no rationale. Step counting is non-functional without `ACTIVITY_RECOGNITION`, so a
cold prompt is a denial risk. Change:

- The cold-request `LaunchedEffect` is **gated on `hasCompletedOnboarding`** — it does not fire while
  onboarding is on screen (no carousel-vs-system-dialog collision).
- The onboarding **final slide owns the first** activity-recognition request, asked *with* context.
- After onboarding completes, the existing `LaunchedEffect` resumes its normal role on subsequent
  launches: re-prompting if still ungranted, starting the foreground service when granted, and chaining
  Health Connect. **The Health-Connect + foreground-service + notification logic is unchanged** —
  onboarding only changes *when* the first activity-recognition ask happens.
- The permission **launchers** (`rememberLauncherForActivityResult` for the multi-permission contract
  and the HC contract) stay exactly as they are; onboarding reuses the same contracts/pattern. This is
  the one spot touching load-bearing code, so the change is deliberately minimal.

## 5. Settings opt-out / replay

`NotificationSettingsScreen` (the Settings screen, route `Screen.Settings`) gains a **"Replay tutorial"**
row → calls `OnboardingPreferences.reset()` and navigates to `Screen.Onboarding`. This satisfies
V1X-22's "Settings opt-out for tutorial" requirement (implemented as a replay, since the tutorial is
one-time and skippable rather than a recurring thing to hide).

## 6. Testing

- **JVM (unit):**
  - `OnboardingViewModel` with a fake/in-memory preferences seam — flag starts `false`; `completeOnboarding()`
    flips it `true`; `reset()` returns it to `false`; the exposed slide list is exactly the 4 expected
    slides in order.
  - `OnboardingSlide` canonical list — pure assertion on count/order/identity of slides.
- **`OnboardingPreferences`** real-SharedPreferences behaviour — match however `MusicPreferences` is
  (or isn't) tested in the current suite; if the suite has no precedent for SharedPreferences-backed
  classes, cover the contract via the ViewModel's fake seam and treat the real prefs class as a thin
  device-verified shim.
- **Carousel Composable + MainActivity routing/permission-gating** are device-level; add a lightweight
  instrumented check only if it fits existing `androidTest` patterns, otherwise note as device-verified
  (consistent with how the permission flow is treated today — it is not JVM-tested).
- **`DomainPurityTest`** unaffected: all new code is in `presentation/` and `data/`; zero `domain/`
  changes.

## 7. What this deliberately does NOT do

- **No schema migration.** The completion flag is a device-local UI preference in SharedPreferences, not
  game state — so it must not sync if cloud-save (#36) ever lands, and a reinstall correctly re-shows the
  tutorial. **This corrects `STATE.md`'s "(Gate C, schema)" tag — #24 needs no schema bump.**
- **No currency grant** (explain-only — invariant preserved).
- **No retention notifications / projected-reward math** (telemetry-coupled → Phase 3).
- **No change to the Help screen** — it stays the always-available reference; onboarding is the one-time
  first-run teach. The two are complementary.

## 8. Affected files (anticipated)

- **New:** `data/onboarding/OnboardingPreferences.kt`, `presentation/onboarding/OnboardingSlide.kt`,
  `OnboardingViewModel.kt`, `OnboardingScreen.kt`; matching test(s) under `app/src/test/...`.
- **Modified:** `presentation/navigation/Screen.kt` (new `Onboarding` route — mind the
  `Screen.items by lazy` / `argumentFreeRoutes by lazy` init-order guard, commit 1872af9);
  `presentation/MainActivity.kt` (conditional start destination + gate the cold-permission
  `LaunchedEffect`); `presentation/settings/NotificationSettingsScreen.kt` (replay row);
  possibly a Hilt module if `OnboardingPreferences` isn't auto-provided by constructor injection.

## 9. Doc updates on completion (per PR Task-List Convention)

- `docs/agent/STATE.md` — move #24 out of "next actions" into "Recently shipped"; **drop the
  "schema" qualifier**; tick Gate C in the narrative.
- `docs/plans/plan-FORWARD.md` — tick Gate C item.
- `docs/steering/source-files.md` — add the new onboarding files.
- `docs/steering/structure.md` — add the `presentation/onboarding/` + `data/onboarding/` packages.
- `CHANGELOG.md` — add the PR section; bump headline test count.
- `CLAUDE.md` — update headline test count; add `onboarding/` to the architecture tree if warranted.
- `docs/agent/RUN_LOG.md` — append the session entry.
- No ADR strictly required (no architecture-shifting decision), but the **rejection of the welcome-Steps
  bonus** in favour of explain-only is worth a one-paragraph ADR or RUN_LOG note so the invariant
  boundary stays documented.
