# Design — First-Launch Onboarding (Gate C slice of V1X-22 / #24)

**Date:** 2026-06-11
**Issue:** #24 (V1X-22) · **Gate:** Closed-Test Readiness Gate **C** (`docs/plans/plan-FORWARD.md`)
**Status:** approved + adversarially reviewed (5-lens workflow, 2026-06-11) — ready for implementation plan

## 1. Goal & scope

A brand-new player must understand the **walk → spend → battle** loop before being dropped into Home,
and must grant the activity-recognition permission *with* context rather than via a cold system
dialog. The loop-comprehension piece is what closes Readiness Gate C:

> *"A brand-new player understands the walk → spend → battle loop (onboarding / tutorial / first-walk
> moment)."*

The **permission primer** is additional #24 (V1X-22) scope bundled into the same first-launch flow (it
is not strictly required by the Gate-C text, but the first launch is the right place for it). **Note:**
Gate C and Gate D ("clean fresh-install run") share the same developer sign-off, and Gate C is only
honestly tickable once the Skip/deny path still leaves the player on a *functional* Home with a clear
re-grant route — see §4. A tutorial that teaches the loop but strands the player without step counting
does not really satisfy "understands the loop."

**In scope**
- One-time full-screen tutorial **carousel** (4 slides) shown on first launch, before Home.
- A **permission primer** as the final slide (rationale → triggers the real system permission dialog),
  with a defined Skip contract and a deny/permanently-denied recovery path.
- **Settings** opt-out / **replay tutorial**.
- Gating the existing **cold permission request** (`MainActivity`) behind onboarding-completion so the
  carousel and the system dialog don't collide.

**Explicitly out of scope** (deferred to a later PR):
- D2 / D7 re-engagement push notifications — *pair with telemetry #23 / V1X-21 (Phase 3); unmeasurable
  without it.*
- First-time wave-5 celebration animation — *engagement-spectacle polish whose retention impact is best
  validated once telemetry #23 lands; not build-coupled to telemetry, just sequenced after it.*
- Projected-reward estimates ("a 15-min walk ≈ enough Steps for 2 upgrades") — *pairs with telemetry to
  evaluate impact.*

**Product decision (resolved):** the original V1X-22 "100 free Steps welcome bonus" is **rejected** —
it violates the hard invariant *Steps are never generated in-game* (`CONSTRAINTS.md` / `START_HERE.md`
/ `product.md`). Onboarding is **explain-only**: no currency is granted. The first-session
call-to-action is "Go for a walk to earn your first Steps." No ADR *exception* to the invariant is
created (the invariant stands unbroken); see §9 for the optional documentation note.

## 2. Components & layering

New `presentation/onboarding/` package + one `data/` preferences class:

```
data/onboarding/
└── OnboardingPreferences.kt        device-local completion flag (SharedPreferences)

presentation/onboarding/
├── OnboardingSlide.kt              pure slide content (icon, title, body) + the canonical slide list
├── OnboardingViewModel.kt          reads/writes completion flag; exposes slide list
└── OnboardingScreen.kt             HorizontalPager carousel + permission primer (Compose)
```

- **`OnboardingPreferences`** (`data/onboarding/`, because it touches Android `SharedPreferences`) —
  `@Singleton`, **constructor-injected** (`@Inject constructor(@ApplicationContext context)`), mirroring
  the *structure* of `MusicPreferences` / `AntiCheatPreferences` (both `@Singleton`, no Hilt module
  needed — Hilt auto-provides via the constructor). Stores one boolean `hasCompletedOnboarding` (file
  `onboarding_prefs`). Exposes a **synchronous** `hasCompletedOnboarding(): Boolean`, `setCompleted()`,
  and `reset()` (for Settings "replay tutorial").
- **`OnboardingScreen`** — Compose `HorizontalPager` (from `androidx.compose.foundation.pager`,
  available transitively via the existing material3/foundation dependency — no new artifact required;
  see §8 for an optional explicit-catalog note). 4 slides, page-dot indicator, **Next / Back** controls,
  and a **Skip** affordance on slides 1–3 only.
  - **Skip contract (load-bearing):** Skip on slides 1–3 **jumps to the permission-primer final slide**
    (it skips the *lessons*, not the permission ask). The final slide has **no** Skip — every completion
    path therefore flows through the primer. This closes the "Skip → flag set true without ever asking →
    next launch fires the exact cold prompt we're avoiding" hole.
  - **Accessibility / lifecycle:** honor the app-wide reduced-motion convention (Next/Back use
    `scrollToPage` instantly under reduced motion vs `animateScrollToPage` otherwise, matching
    `MainActivity`'s existing `reducedMotion` handling); use `rememberPagerState` so the current page
    survives config change / process death via its Saver; slide icons are decorative
    (`contentDescription = null`), with title/body as the semantic content and Next/Back/Skip as the
    accessible navigation path.
- **`OnboardingViewModel`** — Hilt `@HiltViewModel`, injects `OnboardingPreferences`. Exposes the slide
  list and a `completeOnboarding()` that flips the flag. Unit-tested via a Mockito mock of the concrete
  `OnboardingPreferences` (no interface/refactor introduced — see §6).
- **Routing** (in `MainActivity`) — a new `Screen.Onboarding` route.
  - `startDestination` is computed from the **synchronous** `OnboardingPreferences.hasCompletedOnboarding()`
    getter **read at composition** (mirroring how `MainActivity` already reads `MusicPreferences`
    synchronously in `onCreate`). It is `Screen.Onboarding.route` **iff** `!hasCompletedOnboarding`, else
    `Screen.Home.route`. It MUST NOT be driven by an async `StateFlow` default — `NavHost` captures
    `startDestination` only on first composition, so a default-`false` first emission would wrongly
    re-show the tutorial to a returning user. Extract the decision into a pure helper
    `startDestination(hasCompletedOnboarding: Boolean): String` for JVM testing.
  - **Completion handler:** `completeOnboarding()` then
    `navController.navigate(Screen.Home.route) { popUpTo(navController.graph.startDestinationId) { inclusive = true }; launchSingleTop = true }` so hardware Back from Home does **not** re-enter Onboarding.
  - **Launcher access:** the permission launcher vals stay in `MainActivity` (`setContent` scope)
    unchanged; the `composable(Screen.Onboarding.route)` lambda passes a trigger callback into
    `OnboardingScreen` (e.g. `onEnableStepCounting = { permissionLauncher.launch(...) }`), mirroring the
    existing route lambdas that capture `navController`. `OnboardingScreen` never references the launcher
    directly, so the success/HC-chain callback at `MainActivity.kt:103-115` is not duplicated.
  - **Route registration:** `Screen.Onboarding` is a first-run/replay-only route reached by literal
    `Screen.Onboarding.route` navigation. Do **NOT** add it to `items` (bottom nav) or to
    `allScreens`/`argumentFreeRoutes` (the deep-link allowlist) — otherwise it becomes a public
    `navigate_to` deep-link target. Per the `Screen.kt` exclusion comment + the `by lazy` init-order
    guard (commit 1872af9), if it must be in `allScreens` for any reason, it has to be explicitly
    excluded from `argumentFreeRoutes`.

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
Biomes). The final slide being the permission primer is a **load-bearing ordering invariant** (routing
+ completion + the Skip contract all depend on it).

## 4. Permission-flow change (the careful bit)

`MainActivity`'s first-launch `LaunchedEffect(Unit)` (lines 117-144) is a sibling of `NavHost`, keyed
on `Unit`, so it fires once on first composition regardless of start destination. It currently runs
several branches: start the foreground service if already-granted (125-128), chain Health Connect
(129-131), fire the **cold multi-permission request** if ungranted (134-140), and push the initial
deep-link (142-143). Step counting is non-functional without `ACTIVITY_RECOGNITION`, so the cold ask
with no rationale is a denial risk. Change — **surgical, gate only the request branch:**

- Gate **only** the cold multi-permission request branch (lines 134-140) on the **synchronous**
  `hasCompletedOnboarding()` read (the same value used for `startDestination`). Wrap that branch in
  `if (hasCompletedOnboarding)`; do **not** read the flag via `StateFlow` (its default first emission
  would let the cold dialog fire before the true value arrives).
- Leave the service-start (125-128), HC-chain (129-131), and deep-link push (142-143) branches
  **ungated** — they correctly no-op on a fresh install (nothing granted yet) and must still run
  post-grant via the existing `permissionLauncher` success callback (103-115). So "the HC +
  foreground-service logic is unchanged" is precisely true: only the *cold request* branch moves behind
  the gate.
- Gate the **deep-link collector** (the `pendingNavigation` collector + `onNewIntent`) the same way:
  while `!hasCompletedOnboarding`, buffer/ignore `pendingNavigation` so a `navigate_to` intent can't
  push a screen over the Onboarding start destination; drop or replay it after completion. (The deep-link
  *push* at 142-143 may stay — it just sets `pendingNavigation`; the *collector* is what must wait.)
- The onboarding **final slide owns the first** activity-recognition request, asked *with* context, via
  the `onEnableStepCounting` callback (§2).
- After onboarding completes, the gated cold-request branch resumes its normal role on subsequent
  launches (re-prompting if still ungranted).
- **Denial / permanently-denied recovery (new — closes the dead-game hole):** a user who denies in the
  primer hits the (now gated, post-onboarding) cold re-prompt on next launch. Android throttles repeat
  prompts, so guard the re-prompt with `shouldShowRequestPermissionRationale` and, when it returns false
  (permanently denied) or the user keeps declining, fall back to a "step counting is off — enable in
  Settings" affordance that deep-links to app settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) instead
  of the current silent no-op. *(This recovery gap pre-exists in `MainActivity` for every deny user
  today; the onboarding gate makes the Skip/deny path a guaranteed route to it, so it is fixed here
  rather than inherited.)*
- The permission **launchers** themselves (`rememberLauncherForActivityResult` for the multi-permission
  contract and the HC contract) stay exactly as they are. This is the one spot touching load-bearing
  code, so the change is deliberately minimal and branch-scoped.

## 5. Settings opt-out / replay

`NotificationSettingsScreen` (the Settings screen, route `Screen.Settings`) **currently takes no
navigation callbacks**, so this adds one. Plumbing:

- `NotificationSettingsScreen` gains an `onReplayTutorial: () -> Unit` parameter and a "Replay tutorial"
  row that invokes it (mirroring how `HomeScreen` takes `onSettingsClick` etc.).
- `MainActivity`'s `composable(Screen.Settings.route)` block passes
  `onReplayTutorial = { navController.navigate(Screen.Onboarding.route) }`.
- **Do NOT flip the flag at tap time.** `reset()`/`setCompleted()` is deferred until the replayed
  carousel is actually *completed*, so an abandoned replay can't strand a returning player into
  re-onboarding. (Equivalently: the replay simply re-navigates to the carousel; the flag only ever
  changes on genuine completion.)
- **Replay when permission already granted:** the final-slide primer renders its button as
  already-satisfied ("Step counting enabled ✓ — Done") when `ACTIVITY_RECOGNITION` is held, rather than
  re-asking.
- **Replay back-stack:** completing (or hardware-Back from) a *replayed* carousel pops back to
  **Settings**, not Home — the completion `popUpTo` idiom in §2 applies to the first-launch path; the
  replay path is a normal forward navigation onto the existing back stack and returns to Settings
  naturally.

This satisfies V1X-22's "Settings opt-out for tutorial" requirement (implemented as a replay, since the
tutorial is one-time and skippable rather than a recurring thing to hide).

## 6. Testing

- **JVM (unit):**
  - **Pure `startDestination(hasCompletedOnboarding)` helper** — `false → Onboarding.route`,
    `true → Home.route`. This JVM-tests the routing decision that was previously "device-verified only".
  - `OnboardingViewModel` — tested with a **Mockito mock of the concrete `OnboardingPreferences`** (per
    the existing `HomeViewModelTest` `mock<…Preferences>()` pattern; no interface extraction needed):
    `completeOnboarding()` calls `setCompleted()`; the flag-read delegates to the prefs.
  - `OnboardingSlide` list — a **single** smoke check: slides non-empty **and the final slide is the
    permission primer** (the load-bearing ordering invariant). Do *not* assert "exactly 4 slides in
    order" as a literal-equality change-detector (brittle tautology).
  - Pin that a `navigate_to` intent still sets `pendingNavigation` regardless of onboarding state (so
    the deep-link gating in §4 buffers rather than drops by accident).
- **`OnboardingPreferences`** — a real-SharedPreferences round-trip test following the existing
  **`MusicPreferencesTest` precedent** (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`,
  construct with `RuntimeEnvironment.getApplication()`). The precedent exists — do **not** skip to a
  device-verified shim. *(Correction: an earlier draft assumed `MusicPreferences` was untested; it has a
  dedicated Robolectric test.)*
- **Routing init-order guard:** extend the existing **JVM** `DeepLinkRoutingTest` (and/or instrumented
  `DeepLinkIntentTest`) to assert `Screen.Onboarding` resolves via `Screen.fromRoute`, that it is NOT in
  `argumentFreeRoutes`, and that the `by lazy` init order stays intact (guards the init-order risk).
- **Device-verified only** (carousel Composable + the live system permission dialog): rotation
  mid-carousel preserves page; Skip routes to the primer; deny → Settings-fallback affordance appears.
- **`DomainPurityTest`** unaffected: all new code is in `presentation/` and `data/`; zero `domain/`
  changes.
- **Target delta:** ~4–6 new JVM tests (helper + ViewModel + slide smoke + prefs round-trip + deep-link
  pin). Update the headline count when known.

## 7. What this deliberately does NOT do

- **No schema migration.** The completion flag is a device-local UI preference in SharedPreferences, not
  game state — so it must not sync if cloud-save (#36) ever lands, and a reinstall correctly re-shows the
  tutorial (the current build is `allowBackup=false`, so reinstall-re-show is the correct behavior).
  **This corrects `STATE.md`'s "(Gate C, schema)" tag — #24 needs no schema bump.**
  - *Cloud-save follow-up (not handled now, flagged so it isn't lost):* when #36 (V1X-12) lands, the
    Onboarding gate must become "show iff `!hasCompletedOnboarding` **AND** `totalStepsEarned == 0`"
    (`PlayerRepository` is already injected in `MainActivity`) so a cross-device progress restore doesn't
    re-onboard a player who already has progress.
- **No currency grant** (explain-only — invariant preserved).
- **No retention notifications / projected-reward math** (telemetry-coupled → Phase 3).
- **No change to the Help screen** — it stays the always-available reference; onboarding is the one-time
  first-run teach. The two are complementary.

## 8. Affected files (anticipated)

- **New:** `data/onboarding/OnboardingPreferences.kt`, `presentation/onboarding/OnboardingSlide.kt`,
  `OnboardingViewModel.kt`, `OnboardingScreen.kt`; matching tests under `app/src/test/...` (helper,
  ViewModel, slide smoke) + `OnboardingPreferences` Robolectric test. **No Hilt module** —
  `OnboardingPreferences` is auto-provided by its `@Inject constructor(@ApplicationContext context)`,
  exactly like `MusicPreferences` / `AntiCheatPreferences`.
- **Modified:**
  - `presentation/navigation/Screen.kt` — add the `Onboarding` route; do **not** add it to `items` or
    `allScreens`/`argumentFreeRoutes` (mind the `by lazy` init-order guard, commit 1872af9).
    Opportunistically fix the stale "All 12 screens" comment (≈ `Screen.kt:37`) to the new count.
  - `presentation/MainActivity.kt` — conditional `startDestination` via the pure helper + synchronous
    prefs read; gate **only** the cold-request branch (134-140) + the deep-link collector on
    `hasCompletedOnboarding`; add `composable(Screen.Onboarding.route)` passing `onEnableStepCounting`;
    pass `onReplayTutorial = { navController.navigate(Screen.Onboarding.route) }` to the
    `composable(Screen.Settings.route)` callsite; add the `shouldShowRequestPermissionRationale` →
    app-settings fallback. Opportunistically fix the stale "(currently all 12)" comment (≈ `:151`).
  - `presentation/settings/NotificationSettingsScreen.kt` — add `onReplayTutorial: () -> Unit` param +
    the "Replay tutorial" row.
- **Optional dependency hygiene:** `androidx.compose.foundation` (pager API) is currently transitive via
  material3. If desired, add an explicit BOM-managed `foundation` entry to `libs.versions.toml` +
  `app/build.gradle.kts` so the pager API isn't relying on transitive resolution. Not required.

## 9. Doc updates on completion (per PR Task-List Convention)

- `docs/agent/STATE.md` — move #24 out of "next actions" into "Recently shipped"; **drop the
  "schema" qualifier in both places** (the "next actions" line ≈ 120 and the objective paragraph wrapped
  across ≈ 23-24); tick Gate C in the narrative.
- `docs/plans/plan-FORWARD.md` — tick Gate C item.
- **GitHub #24 stays OPEN** after this PR: Gate C is ticked, but #24's broader retention/pacing scope
  (wave-5 celebration, D2/D7 push, projected-reward estimates, dead-zone pacing) remains deferred per §1.
  Either keep #24 open with a scope note, or cut a successor issue for the deferred half and reference it
  — nothing silently dropped.
- `docs/steering/source-files.md` — add the new onboarding files.
- `docs/steering/structure.md` — add the `presentation/onboarding/` + `data/onboarding/` packages.
- `CHANGELOG.md` — add the PR section; bump headline test count.
- `CLAUDE.md` — update headline test count; add `onboarding/` to the architecture tree if warranted.
- `docs/agent/RUN_LOG.md` — append the session entry.
- **ADR:** no ADR *exception* to the invariant is required (see §1). A one-paragraph ADR or RUN_LOG note
  recording the **rejection of the welcome-Steps bonus** in favour of explain-only is optional but
  recommended, so the invariant boundary stays documented.
