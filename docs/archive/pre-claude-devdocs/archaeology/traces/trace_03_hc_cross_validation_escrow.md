# Trace 03 — Health Connect Cross-Validation + Escrow

*Phase 3 Deep Trace. Ground truth: `data/healthconnect/StepCrossValidator.kt`,
`data/anticheat/AntiCheatPreferences.kt`,
`data/repository/StepRepositoryImpl.kt`, `data/local/DailyStepDao.kt`,
`data/repository/PlayerRepositoryImpl.kt`. Invoked by
`StepSyncWorker.doWork` (trace 02).*

## 1. Entry Point

- `StepSyncWorker.doWork()` calls `stepCrossValidator.validate(today)`
  once per 15-minute tick. There is no other caller.
- Internal, not user-facing — happens silently in the background.
- Re-entrancy: the worker is periodic-unique (`WORK_NAME="step_sync"`,
  `KEEP` policy) so only one instance exists at a time.

## 2. Execution Path

Inputs into the state machine: the previously-persisted row from
`daily_step_record[today]` (if any), the live Health Connect aggregate
for today, and the rolling offense counter in `AntiCheatPreferences`.

```
StepCrossValidator.validate(date)
  ├─ record = stepRepository.getDailyRecord(date) ?: return   [no walking yet today]
  ├─ hcSteps = stepReader.getStepsForDate(date) ?: return      [HC unavailable / no perms]
  ├─ stepRepository.updateHealthConnectSteps(date, hcSteps)    [always write HC steps for UI]
  │       → DailyStepDao.upsert
  ├─ if (sensorSteps <= 0 || hcSteps <= 0) return               [insufficient data]
  ├─ discrepancy = (sensorSteps - hcSteps) / hcSteps.toDouble()
  ├─ offenseCount = antiCheatPrefs.getCvOffenseCount()
  │
  ├─ if (discrepancy > 0.20) {                                  [THE violation branch]
  │     antiCheatPrefs.recordCvOffense(date)                    [count++ + last-date]
  │     ┌── Level select by offense count ──────────────────────────────┐
  │     │ Level 3 (>=6 offenses): excess = credited - (hc × 0.90)        │
  │     │   playerRepository.spendSteps(excess)                          │
  │     │   stepRepository.updateEscrow(date, excess, 3)                 │
  │     │                                                                 │
  │     │ Level 2 (>=3): excess = credited - hc                           │
  │     │   playerRepository.spendSteps(excess)                           │
  │     │   stepRepository.updateEscrow(date, excess, 3)                  │
  │     │                                                                 │
  │     │ Level 1 (>=1): excess = sensor - hc                             │
  │     │   newSync = record.escrowSyncCount + 1                          │
  │     │   if (newSync >= 2 && record.escrowSteps > 0)                   │
  │     │       stepRepository.discardEscrow(date)                        │
  │     │   else if (record.escrowSteps == 0L)                            │
  │     │       playerRepository.spendSteps(excess)                       │
  │     │       stepRepository.updateEscrow(date, excess, newSync)        │
  │     │   else                                                          │
  │     │       stepRepository.updateEscrow(date, excess, newSync)        │
  │     │                                                                 │
  │     │ Level 0: excess = sensor - hc                                   │
  │     │   newSync = record.escrowSyncCount + 1                          │
  │     │   if (newSync >= 3 && escrowSteps > 0) discardEscrow(date)      │
  │     │   else if (escrowSteps == 0L)                                   │
  │     │       playerRepository.spendSteps(excess)                       │
  │     │       stepRepository.updateEscrow(date, excess, newSync)        │
  │     │   else                                                          │
  │     │       stepRepository.updateEscrow(date, excess, newSync)        │
  │     └─────────────────────────────────────────────────────────────────┘
  │  }
  │
  └─ else if (record.escrowSteps > 0) {                                  [release path]
       playerRepository.addSteps(record.escrowSteps)
       stepRepository.releaseEscrow(date)
       antiCheatPrefs.decayCvOffenses()        [decrements if > 7 days since last offense]
    }
```

- `updateEscrow` / `releaseEscrow` / `discardEscrow` all land on the
  same DAO method `DailyStepDao.clearEscrow(date)` for the release/discard
  cases and `DailyStepDao.upsert(entity.copy(escrowSteps, escrowSyncCount))`
  for the write case. Room then re-emits the daily_step_record flow,
  which every observer reacts to.
- `PlayerProfileDao.adjustStepBalance` — used by both
  `playerRepository.spendSteps` and `playerRepository.addSteps` —
  atomically applies `UPDATE ... SET currentStepBalance = MAX(0, currentStepBalance + :delta)`.
  This means escrow deductions that would take the balance negative
  silently floor at 0; they are effectively capped by what the player
  actually has spent versus been credited.

## 3. Resource Management

| Concern | How |
|---|---|
| Threading | All `suspend` functions; run on WorkManager's coroutine dispatcher. Room schedules its own I/O thread. |
| State distribution | `record.escrowSteps` + `record.escrowSyncCount` live in SQLCipher-encrypted Room. `offenseCount` + `cvLastOffenseDate` live in `SharedPreferences("anti_cheat_prefs")`. The two stores are written in separate transactions; no cross-store atomicity. |
| Idempotency | Each `validate(date)` call is a *state transition*, not a snapshot. The method is not idempotent; calling it twice without intervening change will re-apply an escrow. See §9. |
| HC interactions | One `aggregate(StepsRecord.COUNT_TOTAL)` query per run (the one in `stepGapFiller.fillGaps` is a separate aggregate — worker runs both). |
| Currency clamping | `PlayerProfileDao.adjustStepBalance(-excess)` uses `MAX(0, ...)` — never goes negative. This is load-bearing. |
| Decay | `decayCvOffenses` decrements by 1 if `daysSince >= 7` since `cvLastOffenseDate`. Tightly coupled to `LocalDate.now()`. |

## 4. Error Path

- **No daily_step_record yet** (new day, no sensor activity) → return.
- **HC unavailable / no perms** (`stepReader.getStepsForDate` returns null) → return early, no writes.
- **`hcSteps == 0 || sensorSteps == 0`** → return (can't compute ratio).
- **HC returns a value lower than our sensor but within 20%** — nothing
  happens, no side effects, no escrow released (even if escrow > 0)
  until HC catches back up. This is the "grey zone" — the player is
  held at current balance.
- **Discrepancy negative** (HC reports *more* steps than our sensor) →
  falls into the `else` branch. If `escrowSteps > 0` it releases,
  otherwise no-op. This is correct: an under-sensing phone shouldn't
  be penalised.
- **HC throws during `aggregate`** — `HealthConnectStepReader` returns
  `null` via the inner `try/catch`. Same as "HC unavailable".
- **`playerRepository.spendSteps` fails** — propagates; worker's outer
  `try` catches and logs. Escrow metadata may or may not have been
  written, depending on ordering. In the Level 2/3 branch, `spendSteps`
  happens *before* `updateEscrow`, so a failure there means money was
  spent but no escrow record exists — and because `clearEscrow` is the
  "release" call, the escrow will not be released later. Silent loss.
  See §9.
- **`updateEscrow` fails** — less bad in the Level 2/3 branch because
  money was already spent; the row state is simply stale but will be
  re-overwritten on the next worker tick.

## 5. Performance Characteristics

- O(1) work per call (no loops), excluding HC I/O.
- 1× HC aggregate query, 1× Room upsert (HC steps), up to 2× Room
  writes (wallet + escrow) on the violation branch, 1× SharedPreferences
  write (offense counter).
- No batching — one validation per worker tick, cadence 15 min.
- `decayCvOffenses` runs on the release path; reads today's date and
  subtracts — a tiny date calculation.

## 6. Observable Effects

- Room `daily_step_record[date]`:
  - Always: `healthConnectSteps` set to current HC aggregate.
  - Violation branches: `escrowSteps`, `escrowSyncCount` updated.
  - Release / discard: `escrowSteps=0`, `escrowSyncCount=0`.
- Room `player_profile.id=1`:
  - Violation: `currentStepBalance -= excess` (floored at 0).
  - Release: `currentStepBalance += escrowSteps`.
- SharedPreferences `anti_cheat_prefs`:
  - Violation: `cv_offense_count++`, `cv_last_offense_date=today`.
  - Release: `cv_offense_count` may decrement (only if > 7 days since
    last offense).
- Any UI observing wallet or the daily step record will re-emit on the
  next frame. There is **no user-facing message** — the player sees
  their step balance drop with no explanation. `Log.d("AntiCheat", ...)`
  is the only operator-visible trace.

## 7. Why This Design

- **Graduated response, not hard denial.** The first time a user's
  sensor wildly disagrees with HC (noisy wrist device, phone in a bag,
  different devices), we provisionally escrow the excess and give them
  3 chances (sync counts) to have HC catch up. This maps to the
  CONSTRAINTS.md "4 offense levels" rule.
- **Room-backed escrow** survives process kills. Cannot live in memory
  because the worker may run in a fresh process.
- **Separate offense counter in SharedPreferences** is an
  implementation choice to avoid a schema migration. Functionally it
  could be another column on `player_profile`.
- **Decay-based forgiveness.** 7 days clean = 1 offense removed. Keeps
  the Level ladder from being permanent for casual users who had a
  bad day.
- **Destructive deduction, then maybe refund.** Escrow is *actual*
  spending, not a hold. This was chosen because:
  - The player cannot spend escrowed steps (balance is actually
    reduced).
  - Rounding via `MAX(0, ...)` floors negative balances, so a
    dispute cannot push the balance into debt.
  - Any UI reading the wallet sees the "adjusted" balance without
    needing to know about escrow.
- **20% threshold** is deliberately loose — phone sensors regularly
  disagree by 5-15% with HC.
- **Level 2/3 cap at HC (possibly minus 10%) rather than "sensor -
  HC"**. The earlier levels penalise only the excess; later levels
  force the balance below HC too, disincentivising further attempts.

## 8. Feels Incomplete

- **Silent penalty.** The player has no way to know anti-cheat fired.
  There is no notification, no Stats Screen breakdown, no explanation.
  For a new user whose phone genuinely under-reports to HC this is a
  trust problem.
- **Offense count has no visible ceiling.** Once at Level 3 (6+ offenses),
  the user is stuck there until 7 days of clean activity elapse. That
  could punish returning players who haven't played for a week after a
  bad streak — though the decay does fire.
- **`updateEscrow` when `escrowSteps > 0`** in Level 1 updates the metadata
  but *not the balance*. If today's `excess` is larger than yesterday's
  `escrowSteps` (because the discrepancy has grown), the incremental
  difference is never deducted. Only the syncCount moves; eventually it
  reaches the discard threshold and `clearEscrow` runs. Seems like an
  under-deduction hole in the ladder.
- **`updateHealthConnectSteps`** is called even when neither branch
  fires and the data is insufficient. Writes happen before the
  validator has decided whether this is a violation — no real issue,
  but worth explicit ordering.
- **`releaseEscrow`** doesn't touch the offense counter directly; it
  only calls `decayCvOffenses`, which then checks `>= 7 days`. So on
  the happy-path of "sensor caught up to HC within 3 syncs", the user
  still counts this as 1 offense forever unless they have 7 clean
  days.

## 9. Feels Vulnerable

- **No single-transaction guarantee**. `playerRepository.spendSteps(excess)`
  and `stepRepository.updateEscrow(date, excess, newSync)` are two
  independent Room writes on different entities. If the process is
  killed between them (rare but possible in background work), you can
  end up with:
  - Money gone, no escrow metadata → never refunded.
  - Money gone, metadata partially written → next tick may double-deduct.
- **`MAX(0, ...)` flooring** hides quite a lot. An aggressive sequence
  of Level 3 escrows could repeatedly try to deduct `credited - hc * 0.9`
  while the balance is already 0; nothing happens and the user doesn't
  notice. Operator too.
- **Offense count is in plain-text SharedPreferences.** `anti_cheat_prefs`
  is unencrypted. A rooted user could clear it between each 15-min
  window, permanently staying at Level 0.
- **Level 1 path has a subtle bug-shape**: the `if (newSync >= 2 && escrowSteps > 0)` /
  `else if (escrowSteps == 0L)` / `else` chain treats "already escrowed
  but newSync < 2" as "don't touch the balance, just bump metadata".
  That means once escrowed, the user at Level 1 receives no further
  deductions for growing discrepancies until sync count hits 2 and the
  escrow is discarded.
- **"Discard" merely means clearing escrow metadata.** The money was
  already deducted. "Discard" vs. "release" is only the difference
  between refunding and not — but the naming is confusing because in
  both cases, `clearEscrow` is the DAO call. Reading the code, you have
  to track which branch ran to know if the user got their steps back.
- **Date key** is a string (`"yyyy-MM-dd"`). Time-zone changes or DST
  crossings during a day could alter the daily boundary used by
  `LocalDate.now()` in HC reader vs. the one in `DailyStepManager`,
  misaligning sensor totals with HC totals. Probably benign in practice.

## 10. Feels Like Bad Design

- **Two-store state for one conceptual piece**. Offense count in
  SharedPreferences, escrow in Room. No atomic write, no consistency
  invariant documented. A single table `anti_cheat_state` (1 row) would
  be clearer.
- **The four branches are largely duplicated.** Levels 0 and 1 differ
  only by threshold (`3` vs `2`) and the value of `excess` (both use
  `sensor - hc`). The logic could be factored to a single helper
  `applyEscrow(excess, maxSync)` with a branch that sets `excess =
  credited - capped` for Levels 2/3.
- **The behaviour is not testable end-to-end.** There is a
  `StepCrossValidatorTest` at the JVM level but it uses Robolectric-style
  fakes; true escrow lifecycle across multiple `validate()` calls with
  state carried in Room is covered by `EscrowLifecycleTest` — separate
  file, but the split means the state transitions are spread across
  two test files.
- **The `else` branch's release/discard is collapsed into a single
  `clearEscrow` method on the DAO.** `releaseEscrow` and `discardEscrow`
  in `StepRepository` both call the same DAO method. Semantically they
  are different (one should refund, one should not), but the "refund"
  is done outside the DAO via `playerRepository.addSteps`. The
  asymmetric API invites misuse.
- **Inconsistent excess formulas across levels.**
  - Level 0/1: `sensor - hc`
  - Level 2: `credited - hc`
  - Level 3: `credited - hc × 0.9`
  Why Level 0/1 use sensor and Level 2/3 use credited is not stated in
  code or docs. Possibly intentional (Level 2/3 has to claw back
  already-spent steps), but worth a code comment.
- **Hard-coded thresholds** (`0.20`, `0.10`, `3`, `2`, `6`) with no
  central config. Tuning the anti-cheat ladder requires a code change.
