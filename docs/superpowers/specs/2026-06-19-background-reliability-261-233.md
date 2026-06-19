# Spec + Plan — Background reliability: #261 battery-whitelist primer · #233 battle portrait-lock

**Date:** 2026-06-19 · **Branch:** `fix/background-reliability-261-233` · **One combined PR.**
**Review depth:** lighter inline single-agent adversarial review (ultracode off, per the standing choice).
**Discipline:** TDD where there's a testable seam; reuse existing patterns; no schema change.

Two confirmed HIGH audit defects, both about keeping the core mechanic alive against the platform.
The remaining 2 net-new HIGHs from the 2026-06-18 review.

---

## Fix 1 — #261: GDD-promised battery-optimization whitelist prompt is absent

### Problem (code-grounded)
The whole product gates on reliable background step counting, but on aggressive-OEM devices Doze/App-Standby
kills the foreground `StepCounterService` and steps silently stop. The GDD names a battery-exemption
whitelist prompt as the primary mitigation and `docs/step-tracking.md:44-48` documents it as intended
("Request battery optimization whitelist on first launch") — but **no `PowerManager` /
`isIgnoringBatteryOptimizations` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` code exists anywhere** (grep:
zero hits), and the manifest doesn't declare the permission.

### Design — onboarding primer (first-launch) + Settings re-offer, gated on a testable VM boolean
Mirror the #193 no-sensor pattern exactly (concrete injectable status source → VM boolean → onboarding
branch; intent firing in MainActivity).

- **New `data/sensor/BatteryOptimizationStatus.kt`** — `@Singleton class BatteryOptimizationStatus
  @Inject constructor(@ApplicationContext context)` with `fun isIgnoring(): Boolean =
  (context.getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)`.
  A concrete class (mockable directly via mockito-core 5.x inline maker — the #193 resolution; no interface).
- **`OnboardingViewModel`** injects it; exposes `val shouldOfferBatteryExemption: Boolean = !batteryStatus.isIgnoring()`
  (read once at construction, like `stepSensorAvailable`). When already exempt, the primer never shows.
- **`OnboardingScreen` granted branch** (`OnboardingScreen.kt:241-261`, the `stepCountingGranted ->` arm):
  when `shouldOfferBatteryExemption && !batteryPrimerHandled` (a screen-local hoisted `var`, like
  `permissionAsked` is hoisted in MainActivity), show: the "Step counting enabled ✓" line + a short
  explanation ("Some phones pause background apps to save battery, which stops step counting. Allow it
  to run in the background.") + **"Allow background activity"** button + a **"Maybe later"** TextButton.
  **Both buttons set `batteryPrimerHandled = true`** (review finding 3 — critical): "Maybe later" just
  dismisses; "Allow background activity" ALSO fires `onRequestBatteryExemption` (the system dialog over
  the activity, no navigation). Setting the flag on BOTH is required because `shouldOfferBatteryExemption`
  is read once at VM construction and is stale after the grant — gating only on the flag (not the stale
  boolean) is what closes the primer, so without setting it on "Allow" the primer re-shows after the user
  just granted. Once handled (or on a replay where it's already exempt), the arm shows the normal "Start
  playing" → `finish()`. **Never blocks** the flow — both paths reach `finish()`. (The durable Settings
  re-offer covers a later change of mind.)
- **`MainActivity`**: new `onRequestBatteryExemption` callback on the `OnboardingScreen(...)` call site,
  firing `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))`
  via `context.startActivity(...)` (mirrors the existing `onOpenAppSettings` at `MainActivity.kt:327-334`).
  Same callback wired to the Settings re-offer row.
- **Settings re-offer** (onboarding is one-shot → existing testers never see it): a new `OutlinedCard`
  row in `SettingsScreen` ("Battery optimization — allow background step counting") mirroring the
  "Replay tutorial" row, → an `onOptimizeBattery` callback → the same MainActivity intent. Always
  available (the system handles the already-exempt case gracefully; we don't gate the Settings row).
- **Manifest**: add `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`.
  Play-policy: the persistent `foregroundServiceType="health"` step service is the eligible use case
  that justifies the direct-ask (already declared; listing already frames the app as
  walking-powered). Note in `docs/step-tracking.md` that this is now implemented.

### Tests (TDD, RED first)
- `OnboardingViewModelTest` (pure JVM, Mockito — mirrors the `stepSensorAvailable` cases):
  `mock<BatteryOptimizationStatus>()`; `whenever(it.isIgnoring()).thenReturn(false)` →
  `assertTrue(vm.shouldOfferBatteryExemption)`; `true` → `assertFalse(...)`.
- The `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent firing lives in MainActivity (framework
  `Intent`/`startActivity`) — not unit-testable; build + on-device verified, a documented boundary
  consistent with #193's `onEnableStepCounting`/`onOpenAppSettings`.

### Scope note
No persistent "asked" preference — the onboarding primer's appearance is gated on `isIgnoring()` (skips
when already exempt) + a session-local handled flag; the Settings row is the durable re-offer. Keeps the
new surface minimal and avoids a new SharedPreferences file.

---

## Fix 2 — #233: in-flight battle round destroyed on configuration change

### Problem (code-grounded)
On a config change the Activity→Composable recreates: `remember { GameSurfaceView(context) }`
(`BattleScreen.kt:67`) discards the old engine and builds a fresh one, while the durable
`BattleViewModel` survives holding stale `engine`/`surfaceView` refs and a "round in progress" belief.
`LaunchedEffect(state.isLoading)` only re-configures if `isLoading` flips (it doesn't post-init) → the
new engine sits at wave 1 / 0 cash while the VM thinks a round is live → lost round + possible
mis-credit/skip of end-of-round persistence. The app has no orientation lock; battle is portrait-designed
(#171 note) with no landscape resources anywhere.

### Design — per-screen portrait lock (minimal path)
Battle is a Compose destination inside the single `MainActivity` (`MainActivity.kt:365-367`), so a
manifest `android:screenOrientation` would lock the whole app. The surgical fix matching the issue title
("lock the battle screen") is a per-screen `requestedOrientation` toggle, reusing the `LocalActivity`
accessor already used by `SettingsScreen.kt:30`:

In `BattleScreen`, add:
```kotlin
val activity = LocalActivity.current
DisposableEffect(Unit) {
    val prev = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    onDispose { activity?.requestedOrientation = prev }
}
```
Imports: `androidx.activity.compose.LocalActivity`, `android.content.pm.ActivityInfo`. Lock on enter,
restore on leave. Because the lock is set at battle entry **before** a round is in progress (the round
starts after `configure`), a one-time recreate at entry (if the device was in landscape) is harmless;
once locked, no rotation can occur mid-round → the desync is unreachable. Touches none of the
VM/engine/surface-survival logic (R3-01/#2, RO-03, #190) — it only sets `requestedOrientation`.

### Tests
The portrait lock is pure Android-framework wiring (`requestedOrientation` on the real Activity);
`BattleSurfaceLifecycleTest` constructs `GameSurfaceView` in isolation (no Activity scenario) so it can't
assert it, and the repo has no Activity/Compose-rule harness. Verified by build + on-device (rotate
mid-round → round persists), a documented coverage boundary consistent with how battle UI/orientation is
already treated (#171: "on-device is the acceptance gate"). **No new instrumented harness for one toggle.**
Update the fragile-zone note + `docs/steering/structure.md`/source-files as needed.

### Residual (documented, accepted)
The per-screen orientation lock closes rotation (the named, dominant case). Multi-window resize /
screen-size config changes are not covered (would need app-wide `android:configChanges`, a broader
behaviour change) — low probability for a portrait phone game; documented as accepted residual, not
fixed here. The clean long-term fix (hoist `Simulation` to the VM as durable owner, ADR-0012) remains a
deferred larger effort.

---

## Task order (single PR)
1. ☐ #261: `BatteryOptimizationStatus` + `OnboardingViewModel.shouldOfferBatteryExemption` + onboarding
   granted-branch primer + MainActivity `onRequestBatteryExemption` + Settings re-offer row + manifest
   permission. RED→GREEN VM test.
2. ☐ #233: `BattleScreen` portrait-lock `DisposableEffect`.
3. ☐ Full build: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug`.
4. ☐ Sync docs (CLAUDE.md test count; CHANGELOG; source-files; step-tracking.md; STATE/RUN_LOG) + ADR.
5. ☐ Branch, commit, PR, watch CI, merge, checkpoint.

## Open verification items (resolve during impl)
- [ ] Confirm `LocalActivity` import path (`androidx.activity.compose.LocalActivity`) compiles in
      BattleScreen (SettingsScreen uses it — should be fine).
- [ ] Confirm the onboarding granted-branch edit doesn't disturb the #193 no-sensor branch ordering or
      the existing `OnboardingScreen` tests.
- [ ] Confirm `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` doesn't trip a lint/Play pre-check at build (it's a
      normal permission; the FGS-health use case justifies it).
