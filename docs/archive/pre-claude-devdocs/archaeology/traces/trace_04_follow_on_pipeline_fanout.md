# Trace 04 — Follow-on Pipeline Fan-out (runFollowOnPipeline)

*Phase 3 Deep Trace. Ground truth:
`data/sensor/DailyStepManager.kt`, `domain/usecase/GenerateSupplyDrop.kt`,
`domain/usecase/TrackDailyLogin.kt`, `domain/usecase/TrackWeeklyChallenge.kt`,
`service/WidgetUpdateHelper.kt`, `service/SupplyDropNotificationManager.kt`,
`data/local/DailyMissionDao.kt`. Invoked by both `recordSteps` (trace 01)
and `recordActivityMinutes` (trace 02).*

## 1. Entry Point

Single private method: `DailyStepManager.runFollowOnPipeline(timestampMs: Long)`.

Two callers, both in the same file:

- `recordSteps(rawDelta, timestampMs)` — after every accepted walking
  delta.
- `recordActivityMinutes(activityMinutes, stepEquivalents, timestampMs)`
  — after every accepted exercise-minute credit.

This is the *only* internal boundary through which credited steps become
rewards. Battle Steps (trace 07) explicitly do **not** run through this
pipeline — they bypass all these side effects.

## 2. Execution Path

```
runFollowOnPipeline(timestampMs)
  ├─ [1] Widget update (60s throttled)
  │     try {
  │         val balance = playerRepository.getStepBalance()          [suspend; one-shot read]
  │         widgetUpdateHelper.update(dailyCreditedTotal, balance)
  │             → StepWidgetProvider.saveData(SharedPreferences("widget_data"))
  │             → AppWidgetManager.updateAppWidget(ids, RemoteViews)   (trace 11)
  │     } catch (_) {}                                               [swallow]
  │
  ├─ [2] Supply drop generation
  │     try {
  │         prevSteps = dropState.lastCheckSteps                     [in-memory field]
  │         unclaimedCount = walkingEncounterRepository.getUnclaimedCount()   [Room read]
  │         drop = generateSupplyDrop(dailyCreditedTotal, prevSteps, now, unclaimedCount)
  │                 → pure-Kotlin use case (seeded Random by default = Random)
  │                 → nullable SupplyDrop
  │         if (drop != null) {
  │             walkingEncounterRepository.enforceInboxCap(MAX_INBOX=10)   [loop delete oldest]
  │             id = walkingEncounterRepository.createDrop(trigger, reward, amount)
  │                  → DAO insert, returns row id
  │             supplyDropNotificationManager.notify(drop.copy(id = id.toInt()))
  │                  → PendingIntent with navigate_to=supplies (trace 10)
  │         }
  │         dropState = dropState.copy(lastCheckSteps = dailyCreditedTotal, milestoneTriggered = ...)
  │     } catch (_) {}                                               [swallow]
  │
  ├─ [3] Daily login streak
  │     try {
  │         trackDailyLogin.checkAndAward(currentDate, dailyCreditedTotal)
  │             → DailyLoginDao.getByDate(date) — is this a new login?
  │             → continueStreak / resetStreak logic
  │             → playerRepository.addGems(...) + addPowerStones(1) on streak milestones
  │             → playerRepository.updateStreak(streak, date)
  │     } catch (_) {}                                               [swallow]
  │     ↑ wired via lazy { TrackDailyLogin(dailyLoginDao, playerRepository) }
  │
  ├─ [3] Weekly challenge
  │     try {
  │         trackWeeklyChallenge.checkAndAward()
  │             → DailyStepDao.sumCreditedSteps(weekStart, weekEnd)
  │             → Compare against 50k / 75k / 100k PS tiers
  │             → playerRepository.addPowerStones(...) on tier cross
  │             → WeeklyChallengeDao.markClaimed(tierId)
  │     } catch (_) {}                                               [swallow]
  │     ↑ wired via lazy { TrackWeeklyChallenge(weeklyChallengeDao, dailyStepDao, playerRepository) }
  │
  └─ [4] Walking mission progress
        try { updateWalkingMissions() } catch (_) {}                  [swallow]
          → dailyMissionDao.getByDateOnce(currentDate) — List<DailyMissionEntity>
          → for each mission: if type.category == WALKING and not claimed/completed:
                progress = dailyCreditedTotal.toInt().coerceAtMost(target)
                dailyMissionDao.updateProgress(id, progress, progress >= target)
```

Key observations:

- **Five conceptually separate stages, each wrapped in its own
  `try/catch (_: Exception) {}`.** One failing stage never blocks a
  later one.
- **All reads happen on the caller's coroutine.** No internal dispatching.
- **Reward crediting is synchronous** — daily login and weekly challenge
  call `playerRepository.addGems` / `addPowerStones` inline, then the
  resulting Flow emission propagates to every UI observer.
- **`generateSupplyDrop` is pure Kotlin** — takes the delta and
  `timestampMs`, returns `SupplyDrop?`. Uses `Random` with a default
  `Random` instance (non-deterministic; tests inject `Random(seed)`).
  Logic inside: Priority 1 = 10k daily milestone boundary crossing;
  Priority 2 = 2,000-step boundary crossing with per-100-step 5%
  chance; Priority 3 = per-500-step 1% chance.
- **Inbox cap** is `MAX_INBOX=10`. `enforceInboxCap` loops
  `deleteOldestUnclaimed()` while count >= 10. Creates room before
  insert, not after — so the newest drop always makes it.

## 3. Resource Management

| Concern | How |
|---|---|
| Threading | Runs on `Dispatchers.Default` (the service scope's default). All inner suspend calls yield within that context. |
| Atomicity | None across stages. Each stage is independent; rewards in stage 3 have landed before stage 4 runs. |
| State | `dropState` (in-memory; `@Volatile` not used but single-coroutine write). `stepsPerMinute` map touched only by caller `recordSteps`, not this pipeline. |
| Singletons | `WidgetUpdateHelper`, `SupplyDropNotificationManager`, `WalkingEncounterRepository`, `PlayerRepository`, `DailyLoginDao`, `WeeklyChallengeDao`, `DailyMissionDao`, `DailyStepDao` — all `@Singleton` via Hilt. |
| Random | `GenerateSupplyDrop` default `Random` = `kotlin.random.Random` — process-global unseeded RNG. |
| Room writes | Each reward crediting is an atomic `UPDATE ... MAX(0, ...)` on PlayerProfileDao; the row insert for supply drops uses `@Upsert`. |
| Notification | `SupplyDropNotificationManager.notify` checks `NotificationPreferences.isSupplyDropsEnabled()` and no-ops if disabled. |

## 4. Error Path

Per-stage `try { ... } catch (_: Exception) {}` — intentional:

- **Widget stage** catch swallows: if `playerRepository.getStepBalance`
  races or the launcher has no ID for the widget, the main step credit
  is already done. No retry.
- **Supply drop stage** catch swallows too. If `createDrop` succeeds but
  `notify` throws (e.g. notification channel rejected), the drop *is*
  in the inbox — the user will see it on next Home Screen check, just
  without a notification.
- **Daily login / weekly challenge** swallow — rewards might quietly
  not be awarded if the DAO read fails. The next `runFollowOnPipeline`
  invocation re-checks both, so usually self-healing.
- **Walking missions** swallow — progress may not update this tick; next
  step credit will retry.

No exception ever bubbles out of `runFollowOnPipeline`, which means
`recordSteps` / `recordActivityMinutes` always reach their normal exit
regardless of follow-on failures.

## 5. Performance Characteristics

Typical: every accepted step delta triggers this entire 5-stage pipeline.
Naive worst case is ~1 credit per second (for a running user).

- **Stage 1 (widget)**: cheap, throttled to `THROTTLE_MS=60_000` inside
  `WidgetUpdateHelper`. Only 1 in 60 deltas actually reaches the widget
  provider.
- **Stage 2 (supply drop)**: 1× Room read (`countUnclaimedOnce`), 0-1
  insert, 0-1 notification, map update of `dropState`. Average per-delta
  cost dominated by the read; Room Flow is not re-subscribed here.
- **Stage 3a (daily login)**: 1-2 Room reads (login row) + 0-1 wallet
  update. The DAO read is cheap.
- **Stage 3b (weekly challenge)**: `sumCreditedSteps(weekStart, weekEnd)`
  — SQL aggregate over up to 7 rows. Cheap.
- **Stage 4 (missions)**: `getByDateOnce(currentDate)` — up to 3 rows.
  Per-mission update on walking types only.

Total per-delta Room I/O: ~3-5 SELECTs + ~0-3 UPDATEs. Still dominated
by the outer `stepRepository.updateDailySteps` / `playerRepository.addSteps`
in `recordSteps` itself. No batching; each DAO call is its own
transaction.

The pipeline is **not deferrable** — if an attacker pushes 1000 deltas
per second (hypothetically through the sensor), this runs 1000 times.
The 200/min rate limit in `DailyStepManager` caps it before we get
here.

## 6. Observable Effects

Observable side effects per invocation (each optional):

- **Home launcher**: widget cells may refresh with new daily step total
  and balance (max 1/min).
- **Notification tray**: may spawn a Supply Drop notification with
  `navigate_to=supplies` extra.
- **Room `walking_encounter_entity` table**: may gain a new row;
  oldest unclaimed rows may be deleted if at cap.
- **Room `daily_login_entity` table**: may set today's login record.
- **Room `player_profile` wallet**: Gems and/or Power Stones may be
  incremented via atomic DAO.
- **Room `weekly_challenge_entity` table**: weekly tier may be marked
  claimed; PS added to wallet.
- **Room `daily_mission_entity` table**: per-mission progress and
  `completed` flag may be updated.
- **Flow re-emissions**: every observer of wallet / daily login /
  weekly challenge / daily missions / walking encounters is nudged
  to recompute. This is how Home Screen's "Unclaimed Supplies"
  badge animates in.

## 7. Why This Design

- **One fan-out point** — `runFollowOnPipeline` is the single
  place where "the user just earned some steps" becomes "award
  downstream side effects". Activity minutes and walking deltas
  share it so exercise-only users get identical rewards to walkers.
- **Per-stage isolation** — the 5 `try/catch` blocks mean a flaky
  stage (e.g. notification permission briefly revoked) never
  blocks others. This is cheap pragmatism.
- **Stateless where possible** — `dropState` is the only in-memory
  field. Everything else reads from Room each time, letting the
  pipeline work correctly after process restart.
- **Widget via SharedPreferences** instead of directly passing the
  values to `RemoteViews` each call — so when the system re-asks
  the widget provider (e.g. every 30 min), it has the last known
  numbers without waking the app up.
- **Sequential, not parallel** — stages run in order because (a) the
  order matters for observable effects (supply drop before widget
  would mean the widget doesn't include the drop state; not an
  issue today but could be), and (b) parallelism would burn
  coroutine cost.

## 8. Feels Incomplete

- **No observable counter** for how many drops, logins, or challenge
  awards this pipeline has fired. Debug builds would benefit from
  some emission.
- **`dropState.milestoneTriggered`** is tracked but not read anywhere
  else in the codebase. Dead flag.
- **Inbox cap via loop delete** — `while (countUnclaimedOnce >= 10) { deleteOldestUnclaimed() }`
  — is fine for single-threaded use, but if two pipelines ran
  concurrently (hypothetical — single coroutine today) they could
  each read count=10 and both delete, leaving the inbox < 10 and
  then both inserting. No mutex.
- **Walking missions only count credited steps, never exercise
  minutes directly.** Since `dailyCreditedTotal = dailySensorCredited + dailyActivityMinuteTotal`
  by construction in `DailyStepManager`, this is actually OK — but
  it depends on the invariant that `dailyCreditedTotal` was
  updated *before* the pipeline runs. In `recordSteps` it is
  (`dailyCreditedTotal += credited` before `runFollowOnPipeline`),
  and in `recordActivityMinutes` it is. Fragile if someone adds a
  third caller.
- **Daily mission `.toInt().coerceAtMost(target)`** — step totals
  are `Long` but mission progress is `Int`. A 50k/day ceiling
  keeps this within Int range forever, but the conversion is
  silent.

## 9. Feels Vulnerable

- **Reward double-credit on process-restart edge cases.** Daily
  login and weekly challenge use their own dedup mechanisms
  (date-keyed rows). Supply drops use the `lastCheckSteps` field
  in `dropState` — an in-memory copy; after process death, it is
  seeded from the DAILY record's `creditedSteps + stepEquivalents`.
  If a drop was created and the notification fired, but the
  process died before `dropState.lastCheckSteps` advanced, the
  next credit may cross the same 10k milestone *again* and spawn
  a second drop. Theoretical but not blocked.
- **`updateWalkingMissions` writes progress=`dailyCreditedTotal` each
  time.** If a third party were to pre-seed the mission row with a
  progress higher than today's total (impossible via normal flow,
  but via SQL edit), this would decrement the mission. The DAO
  update simply overwrites.
- **No backpressure.** High-frequency deltas (even inside the 200/min
  cap) each trigger a fan-out. If the worker and the service are
  both alive briefly during a transition, overlap is possible.
- **Gem / PS addition is not audited.** Daily login awards 1 PS +
  N Gems on a streak boundary with no row in any "awards log"
  table. The player cannot retrospectively see why their Gems
  went up.

## 10. Feels Like Bad Design

- **`DailyStepManager` is the God class of this repository.** It
  injects 11 collaborators (repositories, DAOs, notifiers,
  analyzers, counters, preferences) purely because the pipeline
  needs them. Extract `FollowOnPipeline` as its own `@Singleton`
  consuming `DailyStepManager` or taking `dailyCreditedTotal` +
  `currentDate` as parameters.
- **`lazy { TrackDailyLogin(...) }` and `lazy { TrackWeeklyChallenge(...) }`**
  inside the class — these are use cases built inline instead of
  constructor-injected. The rest of the codebase passes use cases
  as constructor parameters on ViewModels. This inconsistency
  violates the convention documented in the Phase 2 §4 "Use cases
  are not Hilt-annotated".
- **Five try/catch `(_: Exception) {}` blocks** — swallowing every
  exception is a code smell. A logging callback or a specific
  exception type per stage would be safer for debugging.
- **Notification firing inside a Room write path.** The pipeline
  decides to call `SupplyDropNotificationManager.notify` in the
  same coroutine as the DAO insert. A slow notification (e.g.
  first-time channel creation) stalls the step credit pipeline.
  Should be a `launch { ... }` on a separate scope.
- **No explicit contract about when this runs.** The method is
  `private` and invoked by two callers. If Battle Step rewards
  (trace 07) ever decided to trigger the pipeline, suddenly
  running through 5 side effects per kill would be a shock.
  Documenting or enforcing "only walking triggers" would help —
  the 2026-03-06 ADR-0003 explicitly excluded battle steps from
  the pipeline.
