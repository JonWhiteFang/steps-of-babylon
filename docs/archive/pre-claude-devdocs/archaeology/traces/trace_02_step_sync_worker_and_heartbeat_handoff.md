# Trace 02 — StepSyncWorker: 15-minute catch-up + HC handoff

*Phase 3 Deep Trace. Ground truth:
`service/StepSyncWorker.kt`, `service/StepSyncScheduler.kt`,
`data/sensor/StepIngestionPreferences.kt`,
`data/healthconnect/`. Companion to trace 01 (foreground path) and
trace 03 (escrow).*

## 1. Entry Point

- **Schedule creation**: `StepsOfBabylonApp.onCreate` calls
  `StepSyncScheduler.schedule(this)` every app launch.
  That's a `PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES).build()`
  enqueued under `WORK_NAME = "step_sync"` with
  `ExistingPeriodicWorkPolicy.KEEP`. Re-enqueueing is idempotent —
  the existing periodic work is kept.
- **Runtime entry**: WorkManager invokes `StepSyncWorker.doWork()` on a
  background coroutine. Because `StepSyncWorker` is `@HiltWorker` with
  `@AssistedInject`, the `HiltWorkerFactory` installed via
  `StepsOfBabylonApp.workManagerConfiguration` resolves constructor
  dependencies (10 services/DAOs/repositories + `appContext` + `params`).
- **Cold start**: the worker can fire without `MainActivity` ever having
  launched — WorkManager brings the app process up just to run this.

## 2. Execution Path

The worker is short and linear; nearly every line is a handoff across an
internal module boundary.

```
WorkManager → StepSyncWorker.doWork()
  → sensorCatchUp(today)                      [cooperative with foreground service]
      ├─ stepIngestionPrefs.isServiceAlive(now)   [heartbeat < 120_000 ms]
      │   if alive → return (skip catch-up)
      ├─ sensorManager.getDefaultSensor(TYPE_STEP_COUNTER) ?: return
      ├─ readCurrentCounter(sensor)            [one-shot, CountDownLatch 5s]
      ├─ stepIngestionPrefs.getCounterAtDayStart(today)
      │   if null → setCounterAtDayStart(today, current) and return (establish baseline)
      ├─ rawToday = current - dayStart
      ├─ record = stepRepository.getDailyRecord(today)
      ├─ alreadyCredited = record?.sensorSteps ?: 0L
      ├─ gap = rawToday - alreadyCredited
      └─ if (gap > 0) dailyStepManager.recordSteps(gap, now)   [trace 01 recreditation]

  → try {
      stepGapFiller.fillGaps(today)            [HC → DailyStepManager.recordSteps]
      stepCrossValidator.validate(today)        [trace 03]
      sessions = exerciseSessionReader.getSessionsForDate(today)
      validSessions = activityMinuteValidator.validate(sessions)   [filters]
      if (validSessions.isNotEmpty()) {
          sensorStepsPerMinute = dailyStepManager.getSensorStepsPerMinute()
          result = activityMinuteConverter.convert(validSessions, sensorStepsPerMinute)
          dailyStepManager.recordActivityMinutes(result.activityMinutes, result.stepEquivalents)
      }
    } catch (e) { Log.w(...) }

  → try { smartReminderManager.checkAndNotify() } catch (e) { Log.w(...) }

  → return Result.success()                    [never returns retry/failure today]
```

Key delegations:

- **`StepGapFiller`** reads HC aggregate for today, computes
  `gap = hcTotal - sensorTotal`, and — if positive — routes the gap
  through **the same** `DailyStepManager.recordSteps(gap, now)` so
  rate limiting, velocity analysis, and the 50k ceiling apply. Gaps
  claimed from HC are therefore *not* a backdoor around anti-cheat.
- **`ExerciseSessionReader`** runs `client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, ...))`
  over `[start_of_day, start_of_tomorrow)` in the system time zone.
  Returns `List<ExerciseSessionInfo>` — a plain Kotlin data class with
  type, start/end instants, and pre-computed duration in minutes.
- **`ActivityMinuteValidator`** filters the list: drops sessions
  `< 2 min`, caps total distinct activity types at 5, truncates any
  session `> 240 min` to 240 min. Rejected duration goes into
  `AntiCheatPreferences.incrementActivityMinutesRejected`.
- **`ActivityMinuteConverter`** applies per-activity rules
  (`stepEqPerMin`, `dailyCap`) and — critically — uses the per-minute
  sensor map to *drop any minute in which the sensor recorded ≥50
  steps*. This is the "don't double-count a cycling class that also
  happened to rack up sensor steps on a wrist-mounted device" guard.
  Only minutes below that threshold contribute step equivalents.
- **`DailyStepManager.recordActivityMinutes`** writes the activity
  minute map via `stepRepository.updateActivityMinutes`, credits the
  delta through `playerRepository.addSteps(credited)`, and runs the
  *same* `runFollowOnPipeline` as walking (trace 04).

## 3. Resource Management

| Concern | How |
|---|---|
| WorkManager constraints | None set; the worker always runs every ~15 min regardless of network / battery. |
| Thread model | WorkManager runs `CoroutineWorker.doWork()` on its own coroutine dispatcher. The worker itself is non-blocking; the one-shot sensor read is blocking. |
| Sensor read | `readCurrentCounter` registers a listener and waits on `CountDownLatch.await(5, TimeUnit.SECONDS)`. This blocks the worker coroutine's underlying thread for up to 5 s — small, but it is the only blocking call in the path. |
| Backoff | No retry logic. `Result.success()` is the only exit even on HC failure. The 15-min cadence handles regularisation. |
| HC client lifetime | `HealthConnectClient.getOrCreate(context)` returns a singleton — reused. `wrapper.getClient()` gates on `SDK_AVAILABLE`. `hasPermissions()` gates on the permission controller. Either missing returns empty / null from HC adapters, which then no-op downstream. |
| Process | Worker may run inside a brand-new process (no MainActivity). Hilt modules still install; `StepsOfBabylonApp.onCreate` runs and schedules itself, which is a no-op the second time. |
| Shared singletons | `DailyStepManager`, `StepRateLimiter`, `StepVelocityAnalyzer`, `AntiCheatPreferences` are all `@Singleton` — the worker shares state with the service if they co-exist in-process. |

## 4. Error Path

- **Sensor unavailable** — `sensorCatchUp` returns. HC branch still runs.
- **Counter read timeout (5 s)** — `readCurrentCounter` returns `null`;
  `sensorCatchUp` returns. HC branch still runs.
- **No HC SDK / no permission** — `stepReader.getStepsForDate(date)`
  returns `null`; `stepGapFiller.fillGaps` returns (guard
  `?: return`). Cross-validator returns (guard on null HC). Reader
  returns empty list → converter skipped. Silent.
- **HC SDK throws** — both `HealthConnectStepReader` and
  `ExerciseSessionReader` wrap in `try { ... } catch (_: Exception) { null / emptyList() }`.
  The outer `try` in `doWork` catches anything else and logs. One call
  failing does not stop the next one.
- **Smart reminder failure** — separate `try/catch` so it never poisons
  the main path.
- **`DailyStepManager.recordSteps(gap)` throws** — same as the foreground
  path; the outer `try` in the HC block catches.
- **`Result.failure()` / `Result.retry()`** — **never returned** today.
  This means a transient HC error will just wait for the next 15-min
  window; no exponential backoff and no user feedback.

## 5. Performance Characteristics

- Worker cadence: 15 min. Real interval is WorkManager-jittered; 15 min
  is the *minimum* periodic interval the framework allows.
- Heavy calls: 1× `HealthConnectClient.aggregate` (step count) + 1×
  `HealthConnectClient.readRecords` (exercise sessions) + 1× blocking
  sensor read (up to 5 s). All roughly constant-cost per day, not
  per-minute.
- Cost of `DailyStepManager.recordSteps(gap, now)`: same as trace 01
  but only once per worker run rather than per delta.
- Activity minute conversion is linear in minutes × sessions; bounded by
  `MAX_SESSION_MINUTES=240` × `MAX_ACTIVITY_TYPES=5` = 1200 iterations
  worst-case over the per-minute map lookup.
- Smart reminder iterates all Workshop upgrades (~23) → constant time,
  cached flow.

## 6. Observable Effects

Assuming all branches succeed at least once per day:

- SharedPreferences `step_ingestion`: `day_start_counter` +
  `day_start_date` set on first run of the day.
- Room `daily_step_record[today]`:
  - `sensorSteps` / `creditedSteps` bumped by gap-filler
  - `healthConnectSteps` written by cross-validator
  - `activityMinutes` + `stepEquivalents` written by converter
  - (from trace 03) `escrowSteps`, `escrowSyncCount` bumped on
    discrepancy
- Room `player_profile.id=1`: `currentStepBalance` and
  `totalStepsEarned` adjusted by every `addSteps`/`spendSteps` triggered
  in gap-fill, activity-minute, and cross-validator paths.
- Same follow-on pipeline side effects as trace 01 × every successful
  `recordSteps` / `recordActivityMinutes`: widget update, supply drop
  chance, daily login check, weekly challenge check, walking missions.
- Smart reminder may post a notification ID `3001` via
  `SmartReminderManager` (once per day, gated by `prefs.last_sent`).
- Logs: `android.util.Log.w("StepSyncWorker", ..., e)` on caught
  exceptions; `Log.d("AntiCheat", ...)` on rejected activity minutes.

## 7. Why This Design

- **Redundant-but-coordinated ingest.** Android regularly kills a
  foreground service (power save, process-kill, user dismissal of the
  notification depending on OEM). WorkManager has stronger delivery
  guarantees (deferred constraint, not immediate) so the worker is the
  safety net. Coordinating via a 120-second heartbeat bit instead of a
  lock avoids any cross-thread blocking: if the service is healthy, the
  worker simply skips sensor catch-up.
- **Same `recordSteps` entry for every path.** Anti-cheat cannot be
  bypassed by HC or by gap recovery — they all traverse
  `DailyStepManager.recordSteps` (or `recordActivityMinutes`) and
  therefore rate limiting, velocity analysis, and the daily ceiling.
- **HC gap-fill by subtraction**, not raw import. The worker computes
  `hcTotal − sensorTotal` and credits only the difference. This makes
  the Room `sensorSteps` column (never `healthConnectSteps`) the ground
  truth for what the player has been credited for walking.
- **Double-counting prevention via per-minute map.** Exercise sessions
  and sensor steps can overlap physically (wrist-mounted wheelchair,
  treadmill, cycling-with-shake). The 50-steps-per-minute threshold is
  a blunt but effective heuristic: a busy walking minute rules out its
  activity-minute credit.
- **No retry.** 15 min is enough breathing room to wait for the next
  attempt. Retries in WorkManager also add complexity to the scheduling
  semantics and risk thundering-herd with the service if both paths
  contend.

## 8. Feels Incomplete

- **No `Result.retry()` ever.** If the HC aggregate fails due to a
  transient issue (say the phone is in a weird state for a minute), the
  worker returns success and waits 15 min. For a player who is
  borderline on a daily challenge, a 15-min window of un-credited
  exercise minutes is real.
- **No network / battery constraints**, but also no explicit no-network
  handling. HC is local, so this is likely fine; worth making explicit.
- **`dailyStepManager.getSensorStepsPerMinute()` reads the in-process
  map.** If the worker is running in a *cold* process (no prior sensor
  events today), the map is empty, so every sensor minute will look
  "empty" and activity-minute conversion will credit everything. The
  comment in the worker (in the preflight context) calls this out,
  but there is no code compensation. A previously active service that
  wrote to Room but died before updating `stepsPerMinute` cache on the
  new process restart leaves us in exactly this state.
- **Smart reminder's "almost there" gap cap of 10,000 steps** is
  hard-coded. Whether this threshold is right for players who are far
  into the geometric cost curve is untested.
- **Activity minute validator's "5 activity types per day"** is shared
  across worker invocations only by the single `seenTypes` local set —
  a new worker run re-scans from the beginning. If morning + afternoon
  exercise span 6 distinct types, a single run will cap at 5 and drop
  the 6th, but across two runs the counter resets. Mildly exploitable.

## 9. Feels Vulnerable

- **Idempotency of activity minute writes is fragile.** Each worker run
  calls `convert(sessions, sensorStepsPerMinute)` fresh and writes the
  absolute `stepEquivalents` via `updateActivityMinutes`; but inside
  `DailyStepManager.recordActivityMinutes` the code computes
  `delta = stepEquivalents - dailyActivityMinuteTotal` and only credits
  the positive delta. Correct as long as the in-memory
  `dailyActivityMinuteTotal` reflects what was last written — which it
  does only in-process. If the process was killed and resumed, the
  `ensureInitialized()` path seeds `dailyActivityMinuteTotal` from
  `existing.stepEquivalents`. But the cumulative value per session is
  not deduplicated; a change in HC data (a session being added or
  extended) could in theory lead to a lower `stepEquivalents` that
  silently does nothing, or a higher one that credits the difference.
  Sessions that *disappear* from HC (e.g. user deletes them) never
  un-credit. That is consistent with "anti-cheat is one-way" but is an
  attack surface.
- **The per-minute sensor-overlap check only looks at the *current* day
  in-memory map.** If a user's phone reboots at 11 AM, the service comes
  back up with an empty `stepsPerMinute` map, and the worker runs at
  11:15 with a 9-11 AM exercise session, every pre-reboot minute will
  be treated as sensor-free. This under-deducts, favouring the user —
  not actively exploitable, but it is a soft spot.
- **Latch-based blocking sensor read in a WorkManager coroutine.** A
  5-second block is technically fine for the worker's 10-minute
  timeout, but it is the only place in the codebase that does this.
  Unusual code tends to break unusually.
- **No mutex on `DailyStepManager`.** If the service and worker happen
  to both call `recordSteps` near-simultaneously (e.g. service is
  restarting), interleaving `dailySensorTotal`, `dailyCreditedTotal`,
  and `stepsPerMinute` mutations could double-credit. The 120-second
  heartbeat makes this unlikely but not impossible.

## 10. Feels Like Bad Design

- **Two classes named in parallel but structured differently.**
  `StepCounterService` vs. `StepSyncWorker` — the service has flat
  `@Inject` fields and a `CoroutineScope`; the worker uses constructor
  `@AssistedInject`. They do nearly the same job (call
  `DailyStepManager.recordSteps`) but look nothing alike. A shared
  "step ingester" façade would make this relationship explicit.
- **Smart reminder piggybacks on the worker.** `SmartReminderManager` is
  a notification helper that only runs during
  `StepSyncWorker.doWork`. There's no independent scheduler and no
  clear boundary — you cannot test or trigger it without the worker.
- **Day-start counter baseline is written twice**, once by
  `StepCounterService.initDayStartCounter` and once by
  `StepSyncWorker.sensorCatchUp`. Both essentially do the same thing
  with a `CountDownLatch(5)` around a one-shot listener. Duplication.
- **Silent fallback on HC errors.** The outer `try { ... } catch (e) { Log.w(...) }`
  block loses granularity: a failing cross-validator looks identical to
  a failing activity-minute converter. A structured
  `StepSyncResult` with per-stage booleans would be more observable
  when (e.g.) user reports "my Gems went down after a walk".
- **Hard-coded 15-minute cadence with no way to nudge.** If the user has
  just spent real money on a Gem pack or crossed a big threshold, a
  manual one-time sync would be satisfying but there is no
  `enqueueUniqueWork` escape hatch. Nothing in the UI surface invokes
  `StepSyncScheduler` other than app start.
