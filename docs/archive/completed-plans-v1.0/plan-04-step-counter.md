# Plan 04 — Step Counter Service

**Status:** Complete
**Dependencies:** Plan 03 (Repository Layer)
**Layer:** `service/` + `data/` — Android service layer

---

## Objective

Implement reliable background step counting using Android's hardware step sensor, a foreground service with persistent notification, WorkManager periodic sync, and boot receiver. Includes anti-cheat rate limiting and daily ceiling. After this plan, the app counts steps in the background even when killed or rebooted.

Reference: `docs/step-tracking.md` for the full sensor stack and anti-cheat specification.

---

## Task Breakdown

### Task 1: Step Sensor Data Source

Create `data/sensor/StepSensorDataSource.kt`:
- Registers `TYPE_STEP_COUNTER` sensor listener via `SensorManager`
- Tracks cumulative count and computes deltas between readings
- Exposes `stepDelta: Flow<Long>` for real-time step events
- Uses `SENSOR_DELAY_NORMAL` for battery efficiency
- Handles sensor unavailability gracefully (emit nothing)

---

### Task 2: Rate Limiter

Create `data/sensor/StepRateLimiter.kt`:
- Tracks steps per rolling 1-minute window
- Caps at 200 steps/min (allows 250 burst for <5 min windows)
- Returns credited steps after rate limiting
- Logs discarded steps count for analytics

---

### Task 3: Daily Step Manager

Create `data/sensor/DailyStepManager.kt`:
- Injects `StepRepository` and `PlayerRepository`
- Enforces 50,000 steps/day hard ceiling
- Tracks current day's total credited steps
- On each step delta: rate-limit → check ceiling → update `DailyStepRecord` → update `PlayerProfile.currentStepBalance`
- Handles day rollover (midnight reset of daily counter)

---

### Task 4: Foreground Service

Create `service/StepCounterService.kt`:
- `LifecycleService` (or `Service`) with foreground notification
- Starts `StepSensorDataSource` and collects step deltas
- Pipes deltas through `StepRateLimiter` → `DailyStepManager`
- Persistent notification showing: daily step count, spendable balance
- Notification tap opens `MainActivity`
- Foreground service type: `health`
- Starts as `START_STICKY` to survive process death

---

### Task 5: Notification Channel & Builder

Create `service/StepNotificationManager.kt`:
- Creates notification channel (`step_counter`) on API 26+
- Builds and updates the persistent notification
- Shows: "Today: X steps | Balance: Y Steps"
- Low priority to minimize intrusiveness
- Update notification on each step batch (throttled to every ~30 seconds)

---

### Task 6: Boot Receiver

Create `service/BootReceiver.kt`:
- `BroadcastReceiver` for `BOOT_COMPLETED`
- Starts `StepCounterService` on device boot
- Register in `AndroidManifest.xml`

---

### Task 7: WorkManager Step Sync

Create `service/StepSyncWorker.kt`:
- `CoroutineWorker` scheduled every 15 minutes via `PeriodicWorkRequest`
- Catches up on missed steps if the foreground service was killed
- Reads current `TYPE_STEP_COUNTER` value, computes delta since last sync
- Applies rate limiting and ceiling checks
- Updates Room via `DailyStepManager`

Create `service/StepSyncScheduler.kt`:
- Enqueues the periodic work request with `ExistingPeriodicWorkPolicy.KEEP`
- Called from `Application.onCreate()` or service start

---

### Task 8: Manifest & Permissions

Update `AndroidManifest.xml`:
- Add permissions: `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`
- Register `StepCounterService` with `foregroundServiceType="health"`
- Register `BootReceiver` with `BOOT_COMPLETED` intent filter
- Request battery optimization whitelist via `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (prompt on first launch)

---

### Task 9: Service Lifecycle Integration

Update `StepsOfBabylonApp.kt` (Application class):
- Start `StepCounterService` on app launch
- Schedule `StepSyncWorker` via `StepSyncScheduler`

Create `presentation/PermissionHelper.kt`:
- Runtime permission request for `ACTIVITY_RECOGNITION` (required API 29+)
- Runtime permission request for `POST_NOTIFICATIONS` (required API 33+)
- Battery optimization whitelist prompt

---

## File Summary

```
data/sensor/
├── StepSensorDataSource.kt    (new)
├── StepRateLimiter.kt         (new)
└── DailyStepManager.kt        (new)

service/
├── StepCounterService.kt      (new)
├── StepNotificationManager.kt (new)
├── BootReceiver.kt            (new)
├── StepSyncWorker.kt          (new)
└── StepSyncScheduler.kt       (new)

presentation/
└── PermissionHelper.kt        (new)

StepsOfBabylonApp.kt           (update)
AndroidManifest.xml             (update)
```

## Completion Criteria

- Steps counted reliably in background via foreground service
- Persistent notification shows daily count and balance
- Service restarts after reboot via boot receiver
- WorkManager catches up on missed steps every 15 minutes
- Rate limiter caps at 200 steps/min (250 burst)
- Daily ceiling enforced at 50,000 steps
- All required permissions declared and requested at runtime
- Step deltas flow through to Room and update PlayerProfile balance
