# Trace 07 — Enemy death → cash award + battle-step reward callback hop

*Phase 3 Deep Trace. Ground truth:
`presentation/battle/engine/GameEngine.kt` (`handleEnemyDeath`,
`onProjectileHitEnemy`),
`presentation/battle/engine/CollisionSystem.kt`,
`presentation/battle/engine/EnemyScaler.kt`,
`presentation/battle/entities/EnemyEntity.kt`,
`presentation/battle/BattleViewModel.kt` (`wireStepRewardCallback`),
`domain/usecase/AwardBattleSteps.kt`,
`data/local/DailyStepDao.kt`,
`data/local/DailyStepRecordEntity.kt` (`battleStepsEarned`).
This is where the game thread delivers a `@Volatile` callback across
to the VM coroutine and ultimately into Room. Documented by ADR-0003.*

## 1. Entry Point

- The game loop's `engine.update(dt)` calls `CollisionSystem.checkCollisions`
  (see trace 06).
- `CollisionSystem` detects a projectile-enemy overlap and invokes
  the engine's `onProjectileHitEnemy(proj, enemy)` callback.
- That callback computes damage via `CalculateDamage`, calls
  `enemy.takeDamage(damage)`, which — when `currentHp <= 0.0` — invokes
  the `onDeath: (EnemyEntity) -> Unit` callback wired in
  `EnemyEntity`'s constructor. In the `WaveSpawner.spawnEnemy` path
  that callback is `::handleEnemyDeath` on `GameEngine`.

So the true per-kill entry point is `GameEngine.handleEnemyDeath(enemy)`,
called on the game thread.

## 2. Execution Path

### 2.1 Inside the engine (game thread)

```
handleEnemyDeath(enemy)
  ├─ totalEnemiesKilled++                                 [@Volatile Int]
  ├─ baseCash      = EnemyScaler.cashReward(enemy.enemyType)
  ├─ tierMult      = TierConfig.forTier(tier).cashMultiplier
  ├─ cashBonus     = 1.0 + wsLevel(CASH_BONUS) * 0.03
  ├─ killCash      = (baseCash * tierMult * cashBonus * fortuneMultiplier
  │                    * (1.0 + cardCashBonus/100.0)).toLong()
  ├─ cash += killCash                                     [@Volatile]
  ├─ totalCashEarned += killCash                          [@Volatile]
  ├─ waveSpawner?.onEnemyKilled()                         [decrements enemiesAlive]
  ├─ soundManager?.play(ENEMY_DEATH)
  │
  ├─ DeathEffect.spawn(pool, enemy.x, enemy.y, enemy.enemyType)   [particles, skipped in reduced motion]
  ├─ FloatingText(enemy.x, enemy.y, "+$killCash")                  [yellow text rising]
  ├─ if (enemy.enemyType == BOSS && !reducedMotion) screenShake.trigger(8f, 0.3f)
  │
  ├─ stepReward = EnemyScaler.stepReward(enemy.enemyType)
  │      [BASIC=1, FAST=1, TANK=3, RANGED=2, BOSS=10, SCATTER=1]
  ├─ if (stepReward > 0L) {
  │     totalStepsEarned += stepReward                     [@Volatile — engine-local counter]
  │     onStepReward?.invoke(stepReward)                   [ENGINE → VIEWMODEL BOUNDARY]
  │     fx?.addEffect(FloatingText(..., "+$stepReward Step", color = STEP_COLOR))  [green]
  │ }
  │
  └─ if (enemy.enemyType == SCATTER) {
        childCount = (2..3).random()
        repeat(childCount) { pendingAdd.add(EnemyEntity(BASIC, …)) }
      }
```

### 2.2 `engine.onStepReward?.invoke(stepReward)` — the hop

`GameEngine.onStepReward: ((Long) -> Unit)?` is `@Volatile`. It is
installed by `BattleViewModel.wireStepRewardCallback(engine)` during
`startPollingEngine` (trace 05 §2.2). The callback body is:

```kotlin
engine.onStepReward = { amount ->
    viewModelScope.launch {                   // MAIN THREAD via coroutine dispatch
        val credited = awardBattleSteps(amount)
        if (credited > 0L) {
            _uiState.update { s ->
                s.copy(
                    stepsEarnedThisRound = s.stepsEarnedThisRound + credited,
                    stepBalance          = s.stepBalance + credited,
                )
            }
        }
    }
}
```

The lambda is invoked synchronously on the game thread — but the
lambda body immediately does `viewModelScope.launch` which enqueues
on `Dispatchers.Main`. The game thread is unblocked the moment the
launch returns (microseconds).

### 2.3 Inside `AwardBattleSteps.invoke(amount, today)` — use case

```kotlin
if (amount <= 0L) return 0L
val alreadyEarned = dailyStepDao.getBattleStepsEarned(today)          [Room read]
val remaining = (DAILY_BATTLE_STEP_CAP - alreadyEarned).coerceAtLeast(0L)
if (remaining <= 0L) return 0L
val credited = min(amount, remaining)
playerRepository.addSteps(credited)                                    [atomic DAO adjust]
dailyStepDao.incrementBattleSteps(today, credited)                    [UPSERT with increment]
return credited
```

- `DAILY_BATTLE_STEP_CAP = 2_000L` per day. Separate from the 50k
  walking ceiling.
- `DailyStepDao.incrementBattleSteps` uses a single SQL statement:
  ```sql
  INSERT INTO daily_step_record (date, battleStepsEarned) VALUES (:date, :delta)
  ON CONFLICT(date) DO UPDATE SET battleStepsEarned = battleStepsEarned + :delta
  ```
  This creates today's row if it doesn't exist yet (e.g. battle before
  any walking step), with all other columns defaulted.
- `playerRepository.addSteps(credited)` goes through
  `PlayerProfileDao.adjustStepBalance` — `UPDATE ... SET currentStepBalance = MAX(0, currentStepBalance + :delta), totalStepsEarned = totalStepsEarned + :delta`.

## 3. Resource Management

| Concern | How |
|---|---|
| Threading | Callback invoke on game thread; body marshals to `viewModelScope` (Main). Room writes happen on Room's internal dispatcher. |
| Ordering | Game thread is single-threaded, so kills fire sequentially. But `viewModelScope.launch { ... }` dispatches each to the Main queue; two kills in consecutive ticks enqueue two coroutines that run *serially* on the main dispatcher, but their suspend calls (`dailyStepDao` reads) interleave on the Room dispatcher. |
| Atomicity | `AwardBattleSteps` performs a read-modify-write across three statements (`getBattleStepsEarned`, `addSteps`, `incrementBattleSteps`). There is no Room transaction wrapping them. |
| Flow emissions | `adjustStepBalance` triggers a re-emission of `PlayerProfileDao.get()`, fanning out to every UI observing wallet. |
| UI state | `_uiState.update` is thread-safe on `MutableStateFlow`; local mutation uses `copy`. |
| Callback | `onStepReward = null` in `BattleViewModel.onCleared` cleanly detaches; `nil` guard in `.invoke` ensures no NPE. |

## 4. Error Path

- **`engine.onStepReward == null`** — stepReward is computed but not
  delivered. `totalStepsEarned` in the engine still bumps (unused for
  rewards, just a display counter). The floating text still shows.
  This happens during the microsecond after VM.onCleared but before
  engine disposal, or during very early composition.
- **`DailyStepDao.getBattleStepsEarned` throws** — the coroutine
  fails; `viewModelScope`'s default error handler logs and cancels
  that coroutine. Next kill starts a fresh coroutine; the cap logic
  self-heals on subsequent successful reads.
- **Daily cap exhausted** — `awardBattleSteps(amount)` returns 0;
  `_uiState` is not mutated. The green "+N Step" floating text still
  appeared *before* the Room call, so the player sees a flash that
  never materialised. See §8.
- **`playerRepository.addSteps` succeeds but `incrementBattleSteps`
  fails** — money is in the wallet but the per-day counter did not
  move. On next kill, the cap check will re-read the old
  `battleStepsEarned`, so the user can double-credit toward their
  daily cap. This is a silent pay-off bug of the non-atomic design.
- **`incrementBattleSteps` succeeds but `addSteps` fails** —
  inversion: cap moves but balance doesn't. User loses steps.
- **Multiple concurrent kills on the main coroutine** — each
  `launch { awardBattleSteps(amount) }` runs suspended between `await`
  boundaries; the read-then-write sequence can interleave. Two
  kills landing at the same tick could both read
  `alreadyEarned=X`, both compute `remaining=CAP-X`, both credit up
  to `remaining`, and overshoot the cap by up to `amount`.
- **Entity despawn before callback finishes** — harmless; the enemy
  object is GC'd after the engine sweep.

## 5. Performance Characteristics

Per-kill cost on game thread: pure arithmetic + a
`soundManager.play(...)` + particle spawns + one `(Long) -> Unit`
callback invocation. Sub-microsecond.

Per-kill cost on main / Room dispatcher: two DAO operations per kill.
A heavy round could see 5-10 kills per second, meaning 10-20 Room
writes per second. `battleStepsEarned` increments are append-shaped
(single-row upsert), handled comfortably by SQLCipher.

Tallies of each tick's `_uiState.update`: trivial (single flow
emission per kill; state flow collapses identical values).

Under 4× speed multiplier, update runs 4× and kills cluster; Room
will briefly be hot but still well within budget.

## 6. Observable Effects

Per kill (cash):
- HUD `cash` field updates via the 200-ms poll on next VM tick.
- Yellow "+N" FloatingText rises at enemy death position.
- Death particle burst (enemy-type specific count/colour).
- `soundManager.play(ENEMY_DEATH)` SFX.
- Screen shake on BOSS kills.

Per kill (Steps — only once per 2,000 cumulative per day):
- Room: `daily_step_record[today].battleStepsEarned += credited`.
- Room: `player_profile.currentStepBalance += credited`,
  `totalStepsEarned += credited`.
- `_uiState.stepsEarnedThisRound` increments; the HUD shows a live
  green `"👟 +N Steps"` block in the top-left.
- `_uiState.stepBalance` bumps; if any other VM is observing
  `playerRepository.observeProfile()`, they recompute next
  `WhileSubscribed(5000)` cycle.
- Green "+1 Step" / "+3 Step" / "+10 Step" FloatingText rises (in
  addition to the yellow cash text).
- When the cap is hit, the floating text still appears but no wallet
  change occurs. Player sees the animation without reward. See §8.

## 7. Why This Design

- **Callback indirection (`@Volatile onStepReward`) keeps the engine
  pure-Kotlin w.r.t. Room.** The engine has zero injected
  repositories. All persistence crosses via this one callback.
- **Game thread does not block on Room.** `viewModelScope.launch {...}`
  is the fire-and-forget pattern — game loop stays responsive even if
  Room is slow.
- **ADR-0003 separate counter** (`battleStepsEarned`) ensures the
  walking-based anti-cheat ladder (trace 03, which compares
  `sensorSteps` to HC steps) does not see battle credits. They live
  on the same row but in a different column.
- **Flat per-enemy reward** deliberately skips multipliers that apply
  to cash (Fortune, Cash Bonus, Golden Ziggurat). Battle Steps are a
  trickle, not an economy — they intentionally stay small no matter
  what the player builds. This is documented in the `EnemyScaler.stepReward`
  Kdoc and in ADR-0003.
- **Floating green text** distinguishes Steps from cash visually —
  players see both amounts rise from the same kill.
- **Cap check against Room, not in-memory** — if the VM is recreated
  (e.g. during play-again or config change), `awardBattleSteps` still
  sees the correct daily total. In-memory caching could drift.

## 8. Feels Incomplete

- **No Room transaction** around the three statements inside
  `AwardBattleSteps`. A partial failure leaves the wallet and the
  cap counter out of sync. Adding `@Transaction` on a suspend
  function would require refactoring — not trivial but important for
  correctness.
- **Visible "+N Step" text even when capped.** `handleEnemyDeath` on
  the game thread spawns the FloatingText *before* the VM's coroutine
  has asked Room whether there is cap room left. Disappointing from
  a UX angle: player sees reward then no balance change. Fixing this
  cleanly would require the callback to return a `Deferred<Long>` or
  similar, which would block the game thread — exactly what the
  current design avoids.
- **Engine's `totalStepsEarned`** counter is maintained (as a
  `@Volatile Long`) but never read by any other code. Dead field
  that could confuse future readers; at least it matches the
  `totalEnemiesKilled` pattern used by the post-round overlay.
- **`_uiState.stepBalance` is incremented by `credited`** from the
  callback path, but the real source of truth is `player_profile`.
  A concurrent wallet write from, say, a Workshop purchase in
  another screen (impossible during battle but in theory) would
  drift this local estimate from Room. Not a real issue because the
  Battle screen is modal.
- **Races under 4× speed**. Multiple kills per tick create multiple
  `launch { awardBattleSteps(...) }` coroutines that interleave at
  their suspend points. Cap can be exceeded by at most one
  `amount` per race — up to 10 steps if a BOSS and a minion die in
  the same tick. Empirically small.

## 9. Feels Vulnerable

- **Non-atomic read-modify-write.** The race above means:
  > "Cap effective max = 2,000 + (concurrent requests − 1) × max_reward"
  i.e. up to ~2,009 with optimistic scheduling, realistically 2,001.
  Not abusable, but the invariant isn't what the constant suggests.
- **Callback rebinding during play-again.** `wireStepRewardCallback`
  is called again during `playAgain`. If the previous callback's
  coroutine hasn't yet completed when the new one is installed, the
  in-flight `launch` still uses the *previous* VM's scope — actually
  fine, since it is the same VM instance during play-again. But the
  pattern is fragile if lifecycle handling changes.
- **Battle Step cap is per calendar day** — determined by
  `LocalDate.now()` via the default arg. During DST / timezone
  change mid-round, the cap day could shift and "reset" the counter
  halfway through. Minor.
- **`dailyStepDao.incrementBattleSteps`** uses `ON CONFLICT(date)` —
  which assumes `date` is the primary key. It is (`DailyStepRecordEntity.date`
  is `@PrimaryKey`). But if the table schema ever changed, the
  upsert semantics would silently break. No test guards this in
  schema.

## 10. Feels Like Bad Design

- **Three layers to understand one kill**: game thread → callback →
  VM coroutine → use case → DAO. Each layer adds a small piece but
  navigating the flow is hard. A sequence diagram per kill would
  save half an hour for every new engineer.
- **Two separate "step totals"**: `engine.totalStepsEarned`
  (engine-private, unused) and `_uiState.stepsEarnedThisRound`
  (shown to UI, driven by credited amount). They can differ if the
  cap is exhausted. Calling both "steps earned" is confusing.
- **`EnemyScaler.stepReward` is a static `when`** with six entries.
  Same with `cashReward`. A `data class EnemyRewardTable(stepReward: Long, cashReward: Long)`
  per enemy type would be more maintainable.
- **`wireStepRewardCallback` is `@VisibleForTesting internal`** —
  i.e. it *is* tested at unit-level. Good. But the test harness
  fires the callback synchronously without the game-thread /
  coroutine-dispatch scaffolding, so race conditions under 4× speed
  cannot be reproduced in JVM tests.
- **`FloatingText.STEP_COLOR`** is a reference on the FloatingText
  companion — a constant that implicitly couples the "Step reward"
  feature to the effect class. A `BattleMessage.stepReward(amount)`
  factory would be more cohesive.
