# Trace 13 — Boot recovery: BootReceiver → StepCounterService

*Phase 3 Deep Trace. Ground truth:
`service/BootReceiver.kt`, `AndroidManifest.xml`,
`service/StepCounterService.kt`, `data/sensor/StepSensorDataSource.kt`,
`data/sensor/StepIngestionPreferences.kt`. The "after reboot" contract
for step counting.*

## 1. Entry Point

- Device finishes booting → Android fires the
  `android.intent.action.BOOT_COMPLETED` broadcast.
- The manifest-declared `<receiver android:name=".service.BootReceiver">`
  matches. Android delivers the intent to `BootReceiver.onReceive`.
- No user interaction; no foreground UI.

## 2. Execution Path

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            context.startForegroundService(
                Intent(context, StepCounterService::class.java)
            )
        }
    }
}
```

Effect chain:

```
BOOT_COMPLETED
  → BootReceiver.onReceive
  → startForegroundService(StepCounterService)
  → StepCounterService.onCreate              [same as trace 01]
      - starts foreground (health type) with notification
      - initDayStartCounter() if today is new
      - collects sensorDataSource.stepDeltas → dailyStepManager.recordSteps(...)
```

After `onReceive` returns, the broadcast is complete. Android gives
the service ~5 seconds to call `startForeground` or it will be killed.
`StepCounterService.onCreate` is synchronous and calls
`startForeground` before the first `scope.launch` — safe.

## 3. Resource Management

| Concern | How |
|---|---|
| Receiver lifetime | `BootReceiver.onReceive` is very short (permission check + one method call). Well under the ~10-second limit Android enforces. |
| Permission check | `ContextCompat.checkSelfPermission` — cheap, PM lookup. |
| Foreground service start | `startForegroundService(Intent)` on API 26+ required; the service's `onCreate` then calls `startForeground(...)` with a notification inside 5 s. |
| Coroutine scope | `BootReceiver` doesn't start any; all work is handed off to `StepCounterService`. |
| No pendingResult | `goAsync()` is not used. The broadcast is handled synchronously. |

## 4. Error Path

- **Intent action mismatch** — `if (intent.action != ACTION_BOOT_COMPLETED) return` — should never fire given the manifest filter, but defensive.
- **ACTIVITY_RECOGNITION not granted** — the `if (hasPermission)` branch is skipped. No service. The user, on next app open, will re-grant and the service will start via `MainActivity`'s permission launcher. No backfill of steps that occurred during the OFF window is performed at that moment; `StepSyncWorker` will catch up on its next 15-min tick once the service becomes alive (or sooner if the service itself calls the worker directly — it doesn't).
- **`startForegroundService` fails (ForegroundServiceStartNotAllowedException on API 34+)** — rare, only if the device is in a restricted state. Not caught by this code. The exception would crash the receiver, but because `BootReceiver` has nothing else to do, the effect is a one-time "service didn't start on boot". Next MainActivity launch fixes it.
- **Service crashes in `onCreate` before `startForeground`** — Android kills the process and logs. `START_STICKY` will eventually restart when conditions clear.

## 5. Performance Characteristics

- `onReceive` total wall time: ~1 ms. Just a permission check and a
  one-shot IPC call to the activity manager.
- Service creation is deferred to the service's own process wake-up;
  the receiver returns immediately.
- Negligible battery cost at boot — one service wake-up, not a
  pre-registered alarm.

## 6. Observable Effects

- **Foreground service starts**: notification with `NOTIFICATION_ID=1001`
  appears in the tray (or a minimal version if the user has
  disabled the persistent notification via settings — see
  `StepCounterService.onCreate` `buildMinimalNotification` branch).
- **`StepIngestionPreferences.service_heartbeat`** begins being
  updated every sensor delta (trace 01).
- **`day_start_counter`** is captured if not already set for today.
- **Sensor subscription** — `TYPE_STEP_COUNTER` re-registered by
  the new `StepSensorDataSource` listener.

Nothing is written to Room by the receiver itself; the service
handles all persistence via `DailyStepManager.recordSteps` as steps
arrive.

## 7. Why This Design

- **Manifest-registered receiver** because the app may not be running
  at boot. `BOOT_COMPLETED` is one of the few broadcasts that still
  supports manifest registration on modern Android (most implicit
  broadcasts require runtime registration now).
- **Permission gate** so the app doesn't noisily crash with
  SecurityException on devices where the user never granted
  activity recognition.
- **`startForegroundService` (not `startService`)** is mandatory on
  API 26+. The service class declares
  `foregroundServiceType=health` in the manifest.
- **No work done in the receiver body** — all step logic lives in
  the service. Receivers have a short budget, so offloading is
  standard.
- **Single-line permission branch** — simple, no branching logic,
  no state machine.
- **BootReceiver is `android:exported="false"`**: only the system
  can deliver its action. `BOOT_COMPLETED` is a protected broadcast,
  so this is primarily defence-in-depth.

## 8. Feels Incomplete

- **Only `ACTION_BOOT_COMPLETED`**. Other "process death resurrection"
  broadcasts that would benefit from the same restart:
  `ACTION_MY_PACKAGE_REPLACED` (app upgrade), `ACTION_LOCKED_BOOT_COMPLETED`
  (direct boot mode). Neither is handled.
- **No fallback if permission is missing**. The receiver silently
  no-ops. Could post a notification ("Steps of Babylon: re-grant
  activity permission to resume step counting") to nudge.
- **No step gap recovery trigger**. Steps taken during the off-state
  (device was powered down) were never sensed anyway, and the
  service's day-start counter captures the cumulative at first read
  post-restart — so steps walked *while off* are not visible to the
  service. Health Connect's separate reading (trace 02) may close
  part of the gap at the next worker tick. This is fine but worth
  understanding: the sensor's `TYPE_STEP_COUNTER` baseline resets
  on reboot (cumulative-since-last-reboot), which is why
  `StepSensorDataSource` uses a `lastCumulative = -1L` seed pattern
  and the worker has its own day-start counter mechanism.

## 9. Feels Vulnerable

- **Race between receiver returning and service starting.** The
  receiver fires `startForegroundService` then returns. The OS
  queues service start. If the OS kills the process before the
  service gets there (rare in boot flow), the service never runs
  and there's no retry from this receiver until the next boot.
- **`startForegroundService` delivery requires the service to
  `startForeground()` within 5 s.** If the DB bootstrap (trace 12)
  is slow on a cold cold-start (e.g. Keystore is slow) AND the
  service's `onCreate` ever accesses the DB, we could exceed the
  5-s budget. Today, `StepCounterService.onCreate` only touches
  `notificationPreferences` (SharedPreferences) and
  `sensorManager` — fast. But a future change that reads from Room
  inside `onCreate` would be a latent ANR-on-boot.
- **`hasPermission` read**: `ContextCompat.checkSelfPermission` is
  safe, but permission state can change between check and service
  start (user revokes permission in a parallel flow). If revoked
  mid-start, the service still starts and the sensor listener
  registration may throw SecurityException. Not observed but
  theoretically possible.
- **Boot receiver runs in the "main" process** by default. No
  `android:process` attribute. Anything it does runs in the same
  memory space as MainActivity — fine here because the only work
  is a permission check and a startService IPC.

## 10. Feels Like Bad Design

- **Boot recovery as a 14-line class is fine**. But it's a narrow
  cut of a broader problem: "reliably keep the service alive".
  The codebase has:
  - `BootReceiver` (this file)
  - `StepSyncWorker` (trace 02) doing periodic catch-up
  - `START_STICKY` on the service
  - Heartbeat in `StepIngestionPreferences`
  Four mechanisms, no single owner. Consider a `StepIngestionSupervisor`
  that documents and centralises the invariants.
- **No logging** in `BootReceiver`. `Log.d` one line per boot
  would help operators confirm the path fired.
- **Does nothing on permission missing.** A logged warning at minimum
  would help debug "why did my steps stop after reboot".
- **Tightly coupled to `StepCounterService`.** If a future design
  moved step counting into a different component (e.g. a
  `JobScheduler` job), `BootReceiver` would need to change.
  Abstracting `StepIngestionBootstrap.start(context)` that both
  this receiver and `MainActivity` call would decouple.
- **No test**. No Robolectric test for BootReceiver delivering the
  intent. Coverage: zero. The class is trivial, but a regression
  (say, a future commit filters out ACTION_BOOT_COMPLETED
  accidentally) would go unnoticed.
