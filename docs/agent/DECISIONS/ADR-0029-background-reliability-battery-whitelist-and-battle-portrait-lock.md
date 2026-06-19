# ADR-0029: Background reliability — battery-optimization whitelist primer (#261) and battle portrait-lock (#233)

**Status:** Accepted — 2026-06-19 (shipped on branch `fix/background-reliability-261-233`, `[Unreleased]`).

## Context

The last two net-new HIGHs from the 2026-06-18 complete-app review, both about keeping the core
step-counting mechanic alive against the platform:

- **#261** — the GDD rates the foreground `StepCounterService` being killed by Doze/App-Standby on
  aggressive OEMs (Xiaomi/Huawei/Samsung/OnePlus) as its highest-likelihood/highest-impact risk, and
  names a battery-optimization whitelist prompt as the primary mitigation. `docs/step-tracking.md`
  documented it as intended ("Request battery optimization whitelist on first launch"), but it was never
  implemented — no `PowerManager` code anywhere, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission.
- **#233** — on any configuration change the Activity→Composable recreated: `remember { GameSurfaceView }`
  discarded the engine and built a fresh one (wave 1 / 0 cash) while the durable `BattleViewModel`
  survived believing a round was in progress. `LaunchedEffect(state.isLoading)` only re-configures on an
  `isLoading` flip (which doesn't happen post-init), so the engine/VM desynced → lost round + possible
  mis-credit/skip of end-of-round persistence. No orientation lock existed; battle is portrait-designed.

## Decision

### #261 — onboarding primer + Settings re-offer, gated on a testable VM boolean

Mirror the #193 no-sensor pattern (concrete injectable status source → VM boolean → onboarding branch;
intent firing in MainActivity).

- **`data/sensor/BatteryOptimizationStatus`** — `@Singleton` `@Inject` wrapper around `PowerManager`;
  `isIgnoring()` returns `isIgnoringBatteryOptimizations(packageName)` (API 23+; minSdk 34, no guard).
  Concrete class, mocked directly in JVM tests (mockito-core 5.x inline maker — the #193 resolution).
- **`OnboardingViewModel.shouldOfferBatteryExemption`** = `!batteryStatus.isIgnoring()`, read once at
  construction (skips the primer when already exempt).
- **`OnboardingScreen` granted-branch primer** — when `shouldOfferBatteryExemption && !batteryPrimerHandled`,
  show an explanation + "Allow background activity" (fires `onRequestBatteryExemption`) + "Maybe later".
  **Both buttons set `batteryPrimerHandled = true`** — the construction-time boolean is stale after the
  grant, so the session-local handled flag (not the boolean) is what closes the primer; setting it only on
  "Maybe later" would re-show the primer after the user just allowed it (caught in adversarial review).
  Never blocks — both paths reach `finish()`.
- **`MainActivity.requestBatteryExemption(context)`** — top-level helper firing
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`package:` Uri), falling back to
  `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` if unresolved. Wired to both the onboarding callback and
  the Settings row.
- **Settings "Background activity" re-offer row** — onboarding is one-shot, so existing players never see
  the primer; the Settings `OutlinedCard` (mirroring "Replay tutorial") is the durable entry point. Always
  available (the system handles already-exempt gracefully).
- **Manifest**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` declared. Play-eligible for the direct-ask because
  of the `foregroundServiceType="health"` step service.

No persistent "asked" preference: the primer is gated on `isIgnoring()` (skip when exempt) + a
session-local handled flag; the durable re-offer is the Settings row. Keeps the new surface minimal.

### #233 — per-screen portrait lock (minimal path)

Battle is a Compose destination inside the single `MainActivity`, so a manifest `screenOrientation` would
lock the whole app. The surgical fix matching the issue ("lock the battle screen") is a per-screen
`requestedOrientation` toggle in `BattleScreen` via `DisposableEffect` + `LocalActivity` (the accessor
`SettingsScreen` already uses): `PORTRAIT` on enter, `UNSPECIFIED` on dispose. The desync becomes
unreachable (no mid-round rotation). A one-time recreate at entry (if the device was physically in
landscape) is harmless because the round starts only after `configure`/`startPollingEngine`, strictly
after `isLoading` flips false — confirmed in review. Touches no VM/engine/surface-survival logic
(R3-01/#2, RO-03, #190).

## Alternatives rejected

- **#261 Home banner** instead of onboarding — larger (new HomeUiState field + ViewModel plumbing +
  Compose-UI tests); the onboarding branch reuses the existing granted/!asked/denied/no-sensor structure
  and its pure-JVM test harness.
- **#261 persistent "asked" SharedPreferences flag** — unnecessary; gate on `isIgnoring()` + session flag,
  durable re-offer in Settings.
- **#233 app-wide manifest `android:screenOrientation="portrait"`** — defensible (nothing rotates today)
  but broader than needed; the per-screen toggle keeps non-battle screens free to rotate in future.
- **#233 clean Simulation-hoist (ADR-0012 durable owner in the VM)** — the correct long-term fix but a
  large battle-engine refactor; deferred. The portrait lock makes the bug unreachable for v1 cheaply.
- **#233 app-wide `configChanges`** to survive multi-window resize — a broader behaviour change; the
  multi-window residual is documented + accepted (low probability for a portrait phone game).

## Consequences

- The GDD's top-rated background-reliability risk now has its documented mitigation (first-launch primer +
  Settings re-offer); already-exempt devices skip it.
- A mid-round rotation can no longer destroy the in-flight battle round.
- New fragile-zone surface (see STATE.md): the onboarding primer's `batteryPrimerHandled`-gates-re-display
  contract (set on BOTH buttons) and the `BatteryOptimizationStatus`/`shouldOfferBatteryExemption` seam;
  the BattleScreen portrait-lock `DisposableEffect`.
- No schema change. 1098 → 1100 JVM tests. The battery-exemption intent firing and the portrait lock are
  framework wiring — build/on-device verified (documented coverage boundaries, consistent with #193 and
  the #171 "on-device is the acceptance gate" stance).
