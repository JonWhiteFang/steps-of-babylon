# Trace 01 — Step Sensor → Room (the foreground walking path)

*Phase 3 Deep Trace. Ground truth: source in
`app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/` and
`service/StepCounterService.kt`. Treats code as authoritative; doc drift
noted where observed.*

## 1. Entry Point

Two entry points can drive this path; this trace covers the **foreground
service** one (the other — `StepSyncWorker` — is covered in trace 02).

- Android OS calls `onStartCommand` / `onCreate` on
  `com.whitefang.stepsofbabylon.service.StepCounterService` (`@AndroidEntryPoint`,
  `foregroundServiceType=health`, `START_STICKY`) after one of:
    1. `MainActivity.onCreate` / `LaunchedEffect` post-permission-grant
       (`presentation/MainActivity.kt` →
       `context.startForegroundService(Intent(context, StepCounterService::class.java))`).
    2. `BootReceiver.onReceive` on `ACTION_BOOT_COMPLETED` with
       `ACTIVITY_RECOGNITION` already granted.
    3. The system resurrecting the process because of `START_STICKY` after
       a silent kill.
- `StepCounterService.onCreate` then subscribes to
  `StepSensorDataSource.stepDeltas` — the real entry for *each individual
  step delta* is the Android `SensorEventListener.onSensorChanged` callback
  wrapped by `callbackFlow` in
  `data/sensor/StepSensorDataSource.kt`.

## 2. Execution Path

Files touched in order (no external libs except Android `SensorManager`
and Room):

```
Android TYPE_STEP_COUNTER sensor
  → SensorEventListener.onSensorChanged(event)
      inside StepSensorDataSource.stepDeltas = callbackFlow { ... }
  → trySend(delta)                           [Flow<Long> emission]
  ─────────────── Service boundary ───────────────
  → StepCounterService.onCreate { scope.launch { sensorDataSource.stepDeltas.collect { delta -> ... } } }
  → dailyStepManager.recordSteps(delta, now)
  ─────────────── DailyStepManager orchestrator ───────────────
  → ensureInitialized()                        [date rollover, seed today's row]
  → rateLimiter.credit(rawDelta, now)          [StepRateLimiter.WINDOW_MS=60_000]
  → velocityAnalyzer.analyze(rawDelta, now)    [15-min window, CV < 5% / jump]
  → coerceAtMost(DAILY_CEILING=50_000 − dailyCreditedTotal)
  → stepRepository.updateDailySteps(date, sensorSteps, creditedSteps)
  → playerRepository.addSteps(credited)        [DAO: UPDATE ... MAX(0, ...) ]
  → stepsPerMinute[epochMin] += credited       [in-memory, used by trace 02]
  → runFollowOnPipeline(now)                   [covered in trace 04]
  ─────────────── Back in Service coroutine ───────────────
  → stepIngestionPrefs.updateServiceHeartbeat(now)     [SharedPreferences]
  → playerRepository.observeProfile().first()          [read current balance]
  → notificationManager.updateNotification(daily, balance)   [30s throttle]
```

Normalisations applied along the way:

- **Deltas, not cumulative values.** `StepSensorDataSource` holds
  `lastCumulative: Long = -1L` per-subscription; the first reading seeds
  the baseline and returns without emitting. All subsequent readings
  emit `cumulative - lastCumulative` if positive. This makes every other
  component delta-based and immune to reboot wraparound.
- **Rate limit** — `StepRateLimiter.credit()` uses an `ArrayDeque<Entry>`
  over a rolling 1 min window. Cap is 200/min normally, 250/min inside
  the first `BURST_WINDOW_MS=5*60_000` since the first entry — intended to
  allow running-pace bursts. Steps that exceed the cap are returned as
  rejections to the caller (`DailyStepManager` increments
  `AntiCheatPreferences.incrementRateRejected`).
- **Velocity analyser** — 15 min window. Returns a multiplier in
  `{1.0, 0.5, 0.0}`:
  - "Instant jump" flag: looks at last ≤3 pairs of per-minute rates; flags
    if `prevRate < 20/min` and `currRate > 150/min`.
  - "Constant rate" flag: coefficient of variation of per-minute rates in
    the last 10 min is < 5% and mean is ≥ 30/min.
  - Both set → `0.0` (throws out the delta), one set → `0.5`, neither →
    `1.0`. Lost credit also goes into `AntiCheatPreferences`.
- **Daily ceiling** — hard-coded `DailyStepManager.DAILY_CEILING = 50_000`.
  Nothing bypasses this in the walking path; the only way more than 50k
  Steps can land in the wallet in one day is via battle kills (trace 07).
- **Per-minute bookkeeping** — `stepsPerMinute` is bounded at 1440 entries
  (the oldest is evicted). This is the double-counting input for
  `ActivityMinuteConverter` in trace 02.

## 3. Resource Management

| Concern | How |
|---|---|
| Threading | `CoroutineScope(SupervisorJob() + Dispatchers.Default)` in the service. The sensor callback itself lands on whatever thread `SensorManager` chooses (main/sensor HAL thread); `callbackFlow` serialises through the scope of the collector. |
| Flow lifecycle | `callbackFlow.awaitClose { sensorManager.unregisterListener(listener) }` cleans up listeners when the service-scope cancels. |
| Service lifecycle | `START_STICKY` → OS restarts; `onDestroy` calls `scope.cancel()`. Subsequent restart re-registers the listener and re-establishes the cumulative baseline (`lastCumulative = -1L`). |
| Notification | `startForeground(NOTIFICATION_ID=1001, ..., FOREGROUND_SERVICE_TYPE_HEALTH)` pre-sensor subscribe. Required by Android 14+ declared-type rules; fails launch otherwise. |
| Heartbeat | `StepIngestionPreferences.updateServiceHeartbeat(now)` — `SharedPreferences("step_ingestion")` write after every processed delta. `HEARTBEAT_THRESHOLD_MS=120_000`. |
| Day-start counter | On service start only, `initDayStartCounter()` registers a one-shot sensor listener behind a `CountDownLatch(1).await(5, SECONDS)` on `Dispatchers.IO`. If today already has a counter, it's a no-op. |
| Notification throttle | `StepNotificationManager.updateNotification` throttles to `THROTTLE_MS=30_000`. The service calls it on every delta; only one update every 30s actually reaches the system. |
| Room connection | Singleton from `DatabaseModule`; SQLCipher-backed (see trace 12). Single writer per DAO call — Room serialises writes on its own dispatcher. |

## 4. Error Path

- **Sensor unavailable** — `getDefaultSensor(TYPE_STEP_COUNTER)` returns
  `null` → `Log.w` + `close()` on the channel. The flow collector returns
  immediately. The service continues to exist (it's already foregrounded)
  but no steps are ever delivered. The worker (trace 02) will still run
  and may yield 0-gap updates via Health Connect.
- **No delta > 0** — `callbackFlow` simply doesn't emit; the collector is
  never invoked. Counter always moves forward monotonically with real
  steps, so negative deltas only happen if the cumulative is reset by
  reboot (handled by the `lastCumulative = -1` reseed logic in step 2).
- **Rate limit exhausted** — `credit()` returns 0, the path exits
  immediately (`if (rateLimited <= 0) return`); `antiCheatPrefs.incrementRateRejected`
  records the rejection. No Room write.
- **Velocity analyser rejects** — `velocityAdjusted = 0` → same exit.
- **Daily ceiling hit** — `credited = 0` → same exit.
- **Database write fails** — `updateDailySteps` / `adjustStepBalance`
  propagate exceptions. The service coroutine is on `SupervisorJob`, so
  failure of a single coroutine kills that coroutine only; the flow
  collector is its sibling. In practice this would stop step crediting
  for the remainder of the delta cycle. There is no retry.
- **Profile read fails for notification text** — explicit
  `try { playerRepository.observeProfile().first() } catch (_: Exception) { 0L }`
  falls back to balance=0. Notification still updates.
- **Service killed by OS** — `onDestroy` cancels the scope. Steps taken
  while dead will be back-filled by `StepSyncWorker` using the day-start
  counter baseline + current cumulative (trace 02).

## 5. Performance Characteristics

- Hot path length: ~8 function calls from sensor event to Room write.
- Per-delta allocations: one `ArrayDeque.Entry` in the rate limiter (only
  if `credited > 0`), one `Entry` in the velocity analyser, optional
  `Log.d` string. No reflection, no JSON.
- **Critical contention point**: `stepsPerMinute` is a plain
  `MutableMap<Long, Long>` on the `@Singleton` `DailyStepManager`. The
  service writes it from `Dispatchers.Default`; the worker reads it from
  its own coroutine via `getSensorStepsPerMinute().toMap()`. It is not
  synchronised. This is effectively safe today because `recordSteps` and
  `recordActivityMinutes` are the only writers and both come from single
  collectors, but concurrent calls would be a race. **Flag**: untested
  assumption.
- Room writes: two per delta
  (`DailyStepRecordEntity` upsert + `PlayerProfileEntity` atomic adjust).
  Both small rows (<200 bytes). SQLCipher adds AES per-page; negligible
  for these tiny writes.
- Notification throttled to 30 s ⇒ worst case ~2 `NotificationManager.notify`
  calls per minute, not per step.
- Sensor event rate: `SENSOR_DELAY_NORMAL` is ~200 ms in practice, but
  `TYPE_STEP_COUNTER` typically reports one event per detected step — far
  below any throughput limit.

## 6. Observable Effects

- Room `daily_step_record` row for today: `sensorSteps`, `creditedSteps`
  both increase monotonically through the day.
- Room `player_profile` row (id=1): `currentStepBalance` and
  `totalStepsEarned` both increased by `credited` via
  `PlayerProfileDao.adjustStepBalance(delta)` — atomic
  `UPDATE ... SET x = MAX(0, x + :delta)` with the `totalStepsEarned`
  branch only on positive delta.
- SharedPreferences `step_ingestion`:
  `service_heartbeat=<now>` bumped after every delta.
- SharedPreferences `anti_cheat_prefs`: counters bumped as side effects
  of rejections.
- `StepNotificationManager` — foreground notification text changes up to
  once every 30s.
- Everything inside `runFollowOnPipeline` (widget, supply drops, daily
  login, weekly challenge, walking missions) — trace 04.
- A Flow emission from `PlayerProfileDao.get()` triggered by the
  `adjustStepBalance` write — every observing `ViewModel` (Home, Workshop,
  Labs, Store, Cards, etc.) will recompute its `StateFlow`.

Logs: `Log.d("AntiCheat", ...)` on each counter bump.

## 7. Why This Design

- **Two redundant step paths** (service + worker) because Android kills
  health-type foreground services aggressively when low on memory. The
  worker's only job in the sensor path is catch-up — it uses the same
  `DailyStepManager.recordSteps` entry and therefore the same anti-cheat
  checks. `StepIngestionPreferences.isServiceAlive(now)` is the
  coordination bit.
- **Rate limit + velocity analyser + daily ceiling** are three independent
  defences. Each has a different failure mode: rate limit catches
  sustained firehoses, velocity catches narrow attacks (shaker, one
  huge instantaneous jump), ceiling is the crude backstop.
  Redundancy is intentional — see `docs/agent/CONSTRAINTS.md` "Anti-cheat
  rules".
- **Atomic SQL for currency** — every wallet-changing call goes through
  `PlayerProfileDao.adjust*(delta)`, never through `update(balance)` with
  a read-then-write cycle. The `MAX(0, balance + :delta)` clamp is the
  single source of the "balances never go negative" invariant.
- **Delta-based sensor API** — the raw OS sensor is cumulative-since-boot.
  The `callbackFlow` wrapper re-baselines on first read so every consumer
  downstream can assume incremental semantics.
- **Service as the happy path**, worker as the recovery path. The
  notification on the service is not cosmetic — it is the price of
  `FOREGROUND_SERVICE_TYPE_HEALTH` and is what keeps the process alive
  while step events arrive. Without it, Android's Doze mode would stop
  delivery within minutes.

## 8. Feels Incomplete

- The no-delta cumulative-reset case (reboot) triggers a listener re-bind
  on service restart, so no wallet-side steps are lost; but
  `StepSensorDataSource` has no explicit reset on reboot if the collector
  somehow stays subscribed. This is theoretical (service also dies on
  reboot) but not asserted anywhere.
- Velocity analyser's two flags are combined into a 3-value multiplier;
  there is no path to "flag but don't penalise for manual review"
  — all penalties are applied instantly and silently. There's no user
  feedback when `0.5×` kicks in.
- `stepsPerMinute` map eviction is size-based (keeps last 1440 keys) but
  not time-based. A multi-day gap with no activity would keep stale keys
  in memory until 1441 new keys replace them. Harmless, but wasteful.
- The notification update reads `playerRepository.observeProfile().first()`
  on the service side instead of observing the flow. Every delta causes
  an extra DAO read. Throttle hides it, but this feels like a pattern
  mistake.

## 9. Feels Vulnerable

- **`DailyStepManager` mutable state is process-local, not Room-backed.**
  Fields like `dailyCreditedTotal`, `dailyActivityMinuteTotal`, and
  `stepsPerMinute` only exist while the `@Singleton` lives. Process
  death reinitialises them from Room in `ensureInitialized()`, which
  reads `creditedSteps + stepEquivalents` from `DailyStepRecordEntity`.
  If the Room row was written but the process died *between writing the
  row and updating the wallet*, we'd double-credit on restart. Today
  the two calls are sequential in `recordSteps` (row first, wallet
  second), which actually means the opposite failure mode — a crash
  between lines means the row says we have more steps than the wallet
  does, and the discrepancy is silent.
- **Day rollover is implicit on every `recordSteps`**. If the process
  writes state for day N, hibernates, and is resumed on day N+1 with a
  pending sensor delta buffered in the flow channel, the first
  `recordSteps` call on N+1 will `ensureInitialized()` to the new date
  *before* processing the delta. The delta's `timestampMs` came from
  the service's `System.currentTimeMillis()` at collect time, not at
  sensor event time, so all late-day steps get credited to the next
  day. Minor but observable.
- `StepSensorDataSource` uses `callbackFlow` with `trySend` — if the
  collector cannot keep up, `trySend` drops the value. In practice the
  collector is fast enough, but under heavy GC pressure this would
  silently under-credit.
- `StepRateLimiter` and `StepVelocityAnalyzer` are `@Singleton`. They
  hold rolling windows across app restarts *only while the process
  lives*. After a service restart the window is empty, meaning an
  attacker who timed a burst across a crash could double-burst.

## 10. Feels Like Bad Design

- The `stepsPerMinute` map being in `DailyStepManager` (data layer) but
  consumed only by `StepSyncWorker` (service layer, for HC activity
  minute conversion) couples them via an ad-hoc getter
  (`getSensorStepsPerMinute()`). The worker reaches through the
  `@Singleton` back into a map — there is no interface contract for
  this. A cleaner design would be a dedicated `MinuteBucketTracker`
  class.
- `DailyStepManager` has 11 constructor parameters and touches 8 Room
  DAOs / repositories. It is the "God service" of this pipeline. It
  orchestrates anti-cheat, Room writes, widget updates, notifications,
  and use-case invocations. Worth splitting out the follow-on pipeline
  into a separate type (see trace 04).
- Missing abstraction for time. `DailyStepManager.todayDate()` and
  `recordActivityMinutes(..., timestampMs: Long = System.currentTimeMillis())`
  both read system clock directly; tests do not currently test
  date-rollover scenarios because of this. The codebase has consciously
  declined to introduce a `Clock` abstraction (see Phase 2 §5 "Time");
  for this class that decision feels short-sighted.
- The service has `@Inject` fields for `SensorManager` and
  `StepSensorDataSource` — but `initDayStartCounter()` then uses
  `SensorManager` directly to register its own listener. The indirection
  of `StepSensorDataSource` is bypassed for day-start baseline capture.
  Why not share the same source? Because the day-start listener needs
  the *raw cumulative value*, and `StepSensorDataSource` exposes deltas
  only. The name `StepSensorDataSource` implies it is *the* source; it
  isn't.
- Rate/velocity multipliers are hard-coded thresholds. No way to tune
  them without a rebuild. No telemetry visible to the user beyond
  `Log.d`.
