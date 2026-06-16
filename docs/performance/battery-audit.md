# Battery Audit — Steps of Babylon (#26 / Gate G)

**Date:** 2026-06-16 · **Issue:** [#26](../../../issues/26) (Performance & battery, Gate G) · **Spec:** `docs/superpowers/specs/2026-06-16-perf-battery-gate-g-design.md`

This is the **battery profiling process + wake-source inventory** deliverable for the in-repo slice of
Gate G. It is code-grounded and document-only: **no cadence/behaviour change is applied this round.** The
candidate tunings below are stated as hypotheses for a *future* PR, each gated on a device measurement
that cannot be performed in-repo.

> **Scope boundary (read first).** The device-measured half of Gate G — overnight idle-drain and OEM
> background-process behaviour (Samsung / Xiaomi / OnePlus / Pixel) — **cannot be closed from the
> repository.** It needs physical devices and developer judgment. It is recorded as a `[deferred]`
> Gate-G line in `docs/plans/plan-FORWARD.md`, not silently dropped.

---

## 1. Wake / wakeup-source inventory (code-grounded)

Every periodic or event-driven wakefulness source in the app, with its current constant and `file:line`.

| Source | Mechanism | Current cadence / trigger | Location |
|---|---|---|---|
| **Step counting** | Foreground `Service` + `Sensor.TYPE_STEP_COUNTER` listener | Runs while counting is active; sensor at `SENSOR_DELAY_NORMAL` (hardware-batched — see §2) | `StepCounterService.kt:61` (`startForeground`), `:104`/`:117` (sensor register, `SENSOR_DELAY_NORMAL`) |
| **Foreground notification refresh** | In-process throttle on notification update | **30 s** min interval between updates | `StepNotificationManager.kt:25` (`THROTTLE_MS = 30_000L`), gate at `:88` |
| **Home-screen widget refresh** | In-process throttle on widget update | **60 s** min interval | `WidgetUpdateHelper.kt:13` (`THROTTLE_MS = 60_000L`), gate at `:20` |
| **Health Connect cross-validation sync** | `WorkManager` `PeriodicWorkRequest` (`StepSyncWorker`) | **15 min** period (HC read + activity-minute reconciliation runs each fire) | `StepSyncScheduler.kt:14` (`PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)`); worker body `StepSyncWorker.kt:37` (`doWork`) |
| **Smart reminders** | Notification posted from `checkAndNotify()` (no own alarm/worker — invoked by an existing wake) | Event-driven; gated by `NotificationPreferences` | `SmartReminderManager.kt:46` (`checkAndNotify`) |
| **Boot re-arm** | `BootReceiver` re-schedules the periodic worker after reboot | Once per boot | `BootReceiver.kt` |

### No-op findings (issue-scope items confirmed absent)

- **Wake-lock audit:** there are **zero** explicit `PowerManager.WakeLock` acquisitions anywhere in
  `app/src/main` (verified by grep — `PowerManager`/`newWakeLock`/`WakeLock` return nothing). The
  foreground `Service` is the wakefulness mechanism; the app holds no partial/full wake locks of its own.
  → **No wake-lock work to do.** Recorded as a closed no-op rather than a deferred task.
- **Sensor-polling audit:** `TYPE_STEP_COUNTER` is **hardware-batched** by Android (the sensor hub
  accumulates step deltas and delivers them in batches; `SENSOR_DELAY_NORMAL` is a hint, not an app-side
  poll loop). There is **no app-side polling cadence to tune** for steps. → Closed no-op.

---

## 2. `TYPE_STEP_COUNTER` rationale (standing)

The step sensor is read via an Android `SensorEventListener`, not an app timer. The OS sensor hub batches
counter increments in hardware and wakes the app to deliver them; the app does not spin a polling loop.
Consequently the per-step delivery cadence is an OS/hardware concern and is **not** an app-tunable battery
lever. The only app-side step-path battery costs are (a) the foreground notification refresh (§1, 30 s)
and (b) the periodic HC sync worker (§1, 15 min) — both of which ARE app-tunable and are covered below.

---

## 3. Candidate tunings — HYPOTHESES, NOT APPLIED

Each is a behaviour change that needs on-device verification before it can ship. This section is the
spec for a future cadence-tuning PR; none of these constants is changed in the #26 in-repo PR.

### 3.1 Foreground notification refresh: 30 s → 60 s
- **Change:** `StepNotificationManager.kt:25` `THROTTLE_MS` 30_000L → 60_000L.
- **Hypothesis:** halves the foreground-notification update rate → fewer per-minute wakeups while walking.
- **Risk:** the live step count in the ongoing notification updates half as often (a UX freshness
  regression — the user may glance at a stale count for up to 60 s). Low correctness risk (the count is
  display-only; the credited balance is unaffected).
- **Device measurement required before shipping:** Battery Historian / `dumpsys batterystats` delta over a
  fixed walking session at 30 s vs 60 s; subjective freshness check on-device.

### 3.2 Health Connect sync: 15 min → 30 min
- **Change:** `StepSyncScheduler.kt:14` `PeriodicWorkRequestBuilder<StepSyncWorker>(15, …)` → `(30, …)`.
- **Hypothesis:** ≈50 % fewer WorkManager wakeups + HC reads → meaningful background-drain reduction.
- **Risk (load-bearing):** the HC cross-validation + activity-minute reconciliation runs each fire
  (`StepSyncWorker.doWork`). Doubling the period doubles the worst-case window before an indoor-workout's
  Activity-Minute Parity steps or a cross-validation discrepancy is reconciled. **Must verify
  cross-validation accuracy is not degraded** (the anti-cheat graduated-offense logic and Activity Minute
  Parity both depend on timely HC reads). This is the riskier of the two and the reason cadence tuning was
  scoped OUT of the in-repo round.
- **Device measurement required before shipping:** a multi-hour background run comparing credited-vs-HC
  step reconciliation latency + offense-level correctness at 15 min vs 30 min on a device with real HC
  data; battery delta via batterystats.

---

## 4. Measurement procedure (the "process exists" deliverable)

### 4.1 In-repo measurable (this PR delivers the harness)
- **Startup time:** `:macrobenchmark:StartupBenchmark` (cold-start `StartupTimingMetric`, None vs
  BaselineProfiles). Run locally on a connected device:
  `./run-gradle.sh :macrobenchmark:connectedBenchmarkReleaseAndroidTest`. Numbers + caveats →
  `docs/performance/startup-baseline.md`.
- **Jank / frame timing:** `:macrobenchmark:JourneyBenchmark` (`FrameTimingMetric`). Same run command.
- **Baseline Profile:** `:app:generateBaselineProfile` on a connected (root-capable) device writes
  `app/src/release/generated/baselineProfiles/baseline-prof.txt` (committed).

### 4.2 Device-only (deferred manual pass — NOT in-repo)
- **Battery drain:** `adb shell dumpsys batterystats --reset` → fixed activity → `adb shell dumpsys
  batterystats` (or Battery Historian) to attribute drain to the app's foreground service / worker.
- **Overnight idle drain:** leave the app installed + counting overnight on a charged device; read the
  morning batterystats delta. Repeat at the §3 candidate cadences to validate the tunings.
- **OEM background-process matrix:** install on **Samsung / Xiaomi / OnePlus / Pixel** and verify the
  foreground service + periodic worker survive each OEM's aggressive background management (the OEMs differ
  most in Doze/standby/kill behaviour). This is the part that genuinely needs physical devices.

> The §4.2 procedures + the §3 tunings together close Gate G's battery line — as a **developer-judgment,
> physical-device pass**, recorded `[deferred]` in `plan-FORWARD.md`.

---

## 5. Summary

| Item | Status |
|---|---|
| Wake-source inventory | ✅ documented (§1) |
| Wake-lock audit | ✅ closed no-op (zero wake locks) |
| Sensor-polling audit | ✅ closed no-op (hardware-batched) |
| Notification cadence tuning (30→60 s) | ⏸ hypothesis, deferred (needs device measurement) |
| HC sync cadence tuning (15→30 min) | ⏸ hypothesis, deferred (needs cross-validation device check) |
| Battery measurement process | ✅ documented (§4) |
| Overnight idle-drain + OEM matrix | ⏸ deferred manual pass (physical devices) |
