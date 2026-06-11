# Trace 08 — Round end cascade

*Phase 3 Deep Trace. Ground truth:
`presentation/battle/BattleViewModel.kt` (`endRound`, `quitRound`,
`playAgain`, `startPollingEngine`),
`presentation/battle/engine/GameEngine.kt` (`roundOver`),
`domain/usecase/UpdateBestWave.kt`,
`domain/usecase/AwardWaveMilestone.kt`,
`domain/usecase/CheckTierUnlock.kt`,
`service/MilestoneNotificationManager.kt`,
`data/local/DailyMissionDao.kt`,
`data/repository/PlayerRepositoryImpl.kt` (`incrementBattleStats`,
`updateHighestUnlockedTier`, `updateBestWave`). This is the
transition from "round active" to "post-round overlay showing".*

## 1. Entry Point

Three ways to reach `BattleViewModel.endRound()`:

1. **Ziggurat HP reaches 0.** In `GameEngine.update(dt)`, when
   `zig.currentHp <= 0.0`, the engine sets `roundOver = true` and
   plays `ROUND_END` sound. The VM's 200-ms polling loop observes
   this next tick and calls `endRound()`.
2. **Player taps "Quit Round"** (from pause overlay or
   `IconButton(onClick = { viewModel.quitRound() })` at top-right).
   `quitRound()` sets `engine.roundOver = true` then calls
   `endRound()` directly.
3. **(Edge)** If the polling loop somehow terminates without seeing
   `roundOver=true` (e.g. exception), `endRound` is never called and
   the round silently hangs. The auto-pause lifecycle observer
   (trace 05 §2.1 `DisposableEffect`) can pause but not end.

`endRound` is guarded by `if (roundEnded) return` so multiple triggers
are idempotent.

## 2. Execution Path

### 2.1 Inside `BattleViewModel.endRound()`

```kotlin
if (roundEnded) return
roundEnded = true
val eng = engine ?: return
val wave = eng.waveSpawner?.currentWave ?: 1

viewModelScope.launch {
    // [a] Best-wave persistence + PS award
    val result = updateBestWave(tier, wave)                            // UpdateBestWave use case
    val psAwarded = if (result.isNewRecord) awardWaveMilestone(wave)   // AwardWaveMilestone
                    else 0
    if (result.isNewRecord) {
        milestoneNotificationManager.notifyNewBestWave(
            wave,
            Biome.forTier(tier).name.replace("_", " "),
        )
    }

    // [b] Tier unlock check
    val profile = playerRepository.observeProfile().first()
    val newTier = checkTierUnlock(profile.bestWavePerTier, profile.highestUnlockedTier)
    if (newTier != null) playerRepository.updateHighestUnlockedTier(newTier)

    // [c] UI state transition — round → post-round
    _uiState.update {
        it.copy(
            isPaused = false,
            showUpgradeMenu = false,
            showOverdriveMenu = false,
            roundEndState = RoundEndState(
                wave = wave,
                enemiesKilled = eng.totalEnemiesKilled,
                cashEarned = eng.totalCashEarned,
                elapsedSeconds = eng.elapsedTimeSeconds,
                isNewRecord = result.isNewRecord,
                previousBest = result.previousBest,
                tierUnlocked = newTier,
                powerStonesAwarded = psAwarded,
                stepsEarned = it.stepsEarnedThisRound,
                adRemoved = it.adRemoved,
            ),
        )
    }

    // [d] Battle stats increment (best-effort)
    try {
        playerRepository.incrementBattleStats(
            rounds = 1,
            kills = eng.totalEnemiesKilled.toLong(),
            cash = eng.totalCashEarned,
        )
    } catch (_: Exception) {}

    // [e] Daily mission updates (best-effort)
    try {
        val today = LocalDate.now().toString()
        val missions = dailyMissionDao.getByDateOnce(today)
        for (m in missions) {
            if (m.claimed || m.completed) continue
            when (m.missionType) {
                DailyMissionType.REACH_WAVE_30.name -> {
                    val newProgress = maxOf(m.progress, wave)
                    dailyMissionDao.updateProgress(m.id, newProgress, newProgress >= m.target)
                }
                DailyMissionType.KILL_500_ENEMIES.name -> {
                    val newProgress = m.progress + eng.totalEnemiesKilled
                    dailyMissionDao.updateProgress(m.id, newProgress, newProgress >= m.target)
                }
            }
        }
    } catch (_: Exception) {}
}
```

### 2.2 The underlying use cases

- **`UpdateBestWave.invoke(tier, waveReached)`** reads
  `playerRepository.observeProfile().first()`, compares
  `waveReached > bestWavePerTier[tier] ?: 0`, and calls
  `playerRepository.updateBestWave(tier, wave)` if higher. Returns a
  `Result(isNewRecord, previousBest)`.
  - `PlayerRepositoryImpl.updateBestWave` reads the whole entity with
    `dao.get().first()`, copies its `bestWavePerTier + (tier to wave)`
    map, then writes with `dao.updateBestWavePerTier(updated)` (serialised
    to JSON via `Converters`).
- **`AwardWaveMilestone.invoke(newBestWave)`** credits Power Stones:
  `5 if wave % 25 == 0, 2 if wave % 10 == 0, else 1`. Calls
  `playerRepository.addPowerStones(ps.toLong())` which goes through
  `PlayerProfileDao.adjustPowerStones` + `incrementPowerStonesEarned`.
- **`CheckTierUnlock.invoke(bestWavePerTier, highestUnlockedTier)`**
  iterates `(highestUnlockedTier + 1)..10`, checks
  `tier.unlockTierRequirement`'s wave gate against
  `bestWavePerTier`. Returns the highest newly-unlocked tier, or null.

### 2.3 Post-round UI

`BattleScreen` observes `state.roundEndState`:

```kotlin
state.roundEndState?.let {
    PostRoundOverlay(
        state = it,
        onPlayAgain = { viewModel.playAgain() },
        onExitBattle = onExitBattle,
        onWatchGemAd = { viewModel.watchGemAd() },
        onWatchPsAd  = { viewModel.watchPsAd() },
    )
}
```

`PostRoundOverlay` shows:
- Wave reached, enemies killed, cash, time, Steps earned this round.
- "New personal best!" banner if `isNewRecord`.
- "🔓 Tier X Unlocked!" banner if `tierUnlocked != null` (with
  cash multiplier teaser from `TierConfig.forTier(newTier).cashMultiplier`).
- Two reward-ad buttons: "Watch ad for +1 Gem" (`watchGemAd`) and
  "Watch ad to double PS" (`watchPsAd`), only enabled on appropriate
  conditions.

Play Again → `viewModel.playAgain()` resets in-round state, re-applies
card effects, re-configures the surface view, re-wires the step callback.
Exit → `navController.popBackStack()` from `BattleScreen`'s
`onExitBattle`.

## 3. Resource Management

| Concern | How |
|---|---|
| Threading | All inside `viewModelScope.launch` (Main). Room writes hop to Room's own dispatcher via suspend. |
| Atomicity | None. Each of the 4+ writes is independent. |
| Idempotency | `endRound()` guarded by `roundEnded` flag — extra triggers are no-ops. |
| Polling | The polling loop's `while (true) { delay(200); if (eng.roundOver && !roundEnded) { endRound(); break } }` ensures endRound runs exactly once per round. |
| Notification | `MilestoneNotificationManager.notifyNewBestWave` fires only on new record. Respects `NotificationPreferences.isMilestoneAlertsEnabled()`. |
| Mission DAO | `getByDateOnce` is a suspend read. Updates happen via `dailyMissionDao.updateProgress(id, newProgress, completed)` — atomic single-row update. |

## 4. Error Path

- **`updateBestWave` race** — it reads the profile then writes back the
  modified map. If another coroutine writes the map between, we clobber.
  No mutex. Unlikely during battle but possible if the player
  returns-from-notification during the end-round coroutine.
- **`awardWaveMilestone` / `updateHighestUnlockedTier` fail** —
  propagate to the `viewModelScope.launch`'s `CoroutineExceptionHandler`.
  Because there is no custom handler, it goes to the default, which in
  release builds effectively swallows; in debug logs with a stack trace.
  No user-facing recovery.
- **`notifyNewBestWave` fails** (e.g. permission revoked mid-round) —
  not try/catched. Same fate as above. Failure would lose the *entire*
  endRound coroutine after that point — skipping `incrementBattleStats`
  and mission updates.
- **`incrementBattleStats` fails** — explicit try/catch; swallowed with
  no log.
- **Mission update loop fails** — explicit try/catch on the whole loop;
  partial progress may have been written.
- **VM cleared mid-coroutine** — cancellation propagates; writes in
  flight abort at next suspension point. Best-wave and tier-unlock
  writes could be lost.
- **Surface destroyed mid-coroutine** — irrelevant; the VM and scope
  outlive the surface.

## 5. Performance Characteristics

- 1 profile read (`observeProfile().first()`).
- 0-1 best-wave map write.
- 0-1 PS adjust + PS-earned increment.
- 0-1 notification post.
- 0-1 highest-tier update.
- 1 state-flow emission.
- 1 `incrementBattleStats` (3-col atomic update).
- 0-3 mission progress updates.

Total Room I/O: 2-7 writes in the worst case, all small. Latency <10
ms on a mid-tier device. The end-round overlay appears roughly one
polling-interval (200 ms) after the fatal hit.

## 6. Observable Effects

- Room `player_profile`:
  - `bestWavePerTier` map updated (on new record).
  - `powerStones`, `totalPowerStonesEarned` incremented (on new
    record).
  - `highestUnlockedTier` possibly bumped (on new tier gate
    passing).
  - `totalRoundsPlayed += 1`, `totalEnemiesKilled += kills`,
    `totalCashEarned += cash` (best-effort).
- Room `daily_mission_entity`: wave/kill mission progress updated.
- Notification tray: possibly one new-best-wave notification (ID 4001).
- UI: `PostRoundOverlay` slides in over the battle canvas; pause /
  upgrade / overdrive buttons disappear (controls only show while
  `roundEndState == null`).
- `MainActivity.BottomNavBar` still hidden (we're still on the Battle
  route).
- Any `HomeViewModel` observer elsewhere would see the new tier /
  wave / wallet on next `WhileSubscribed(5000)` activation.

## 7. Why This Design

- **Polling model** for the kill-to-endRound trigger means the engine
  doesn't need a coroutine. The VM polls a `@Volatile Boolean` every
  200 ms. Good enough since round end is not animation-critical.
- **`roundEnded` guard** because the polling loop breaks after
  `endRound()` runs once, but `quitRound()` also calls `endRound()`
  directly — both paths converge and the guard makes the call
  idempotent.
- **Use cases as composable units** — `UpdateBestWave`,
  `AwardWaveMilestone`, `CheckTierUnlock` are plain Kotlin classes
  composed inline by the VM. This is the pattern throughout the
  codebase.
- **PS only on new record** — the `AwardWaveMilestone` pricing
  (1/2/5 PS) was tuned so that non-record rounds give 0 PS;
  only breakthroughs pay out.
- **Tier unlock checked *after* best wave persists** because
  `CheckTierUnlock` reads from the updated `bestWavePerTier` map.
  The `playerRepository.observeProfile().first()` ensures the fresh
  state.
- **Separate best-effort try/catches for stats and missions** —
  losing these doesn't invalidate the core reward flow.
- **Previous-best + isNewRecord in the overlay** — the player wants
  to know their previous best number, not just "you improved".

## 8. Feels Incomplete

- **No try/catch around the first three critical writes**
  (`updateBestWave`, `awardWaveMilestone`, `updateHighestUnlockedTier`,
  and the notification post). A single exception mid-stream will skip
  everything after it, including the _uiState update. The player
  would see the battle frozen with no overlay.
- **Tier unlock computation doesn't factor in the freshly-written
  best wave**. `updateBestWave` stages the new wave in Room; then
  `observeProfile().first()` reads it back. This works because Flow
  emits synchronously after a DAO update — but it costs an extra DAO
  round-trip. Passing the updated map directly into `CheckTierUnlock`
  would avoid this.
- **No check that mission updates only happen once per battle**.
  If `endRound()` were somehow called twice (the `roundEnded` guard
  prevents this today), missions would double-count. Defensive; not
  necessary but missing.
- **No transaction around the end-round writes**. Best-wave update
  and PS award can diverge if the process dies between them — the
  record stands but the reward is lost.
- **Reward ad integrations (`watchGemAd`, `watchPsAd`) are
  controlled by flags `gemAdWatched` / `psAdWatched` on the
  `RoundEndState`** — these flags are VM-local, not persisted. If
  the VM is re-created (play again), they reset. But if the user
  taps the ad button then backgrounds the app and returns to a
  fresh VM (e.g. low memory), they can watch the ad again.

## 9. Feels Vulnerable

- **`playerRepository.updateBestWave` is read-modify-write.** On a
  concurrent writer (hypothetical — none today), it drops edits.
  Using an atomic SQL `UPDATE` with JSON column update would be
  safer, but Room doesn't expose that for Map<Int, Int> without
  raw SQL.
- **Milestone notification is posted synchronously in the
  endRound coroutine.** A slow or blocked notification channel
  would delay the PostRoundOverlay reaching the user by exactly
  as long as `notify()` takes. Usually <10 ms.
- **Tier unlock is off of the `player_profile` map, not the
  computed one.** If `updateBestWave` failed silently but
  `awardWaveMilestone` succeeded, we'd have credit but no
  stored record — `CheckTierUnlock` would use stale data and
  possibly miss an unlock. Chained error handling is implicit.
- **`dailyMissionDao.getByDateOnce(today)` uses local system
  clock.** A player crossing midnight mid-round could see
  missions change dates and end-round updates miss the "day
  you actually played in" window. Minor but possible.

## 10. Feels Like Bad Design

- **`endRound` is one giant coroutine.** Four distinct
  responsibilities: persistence, reward, notification, UI
  transition. Splitting into `persistRoundResult`,
  `awardRoundRewards`, and `emitRoundEndState` would improve
  testability.
- **`quitRound` sets `engine.roundOver = true` then calls
  `endRound()` synchronously.** The polling loop in
  `startPollingEngine` separately checks `eng.roundOver` — if
  there's a micro-race, endRound could be called twice. The
  `roundEnded` guard saves us, but the design is
  awkward.
- **Mission progress update is in the same method** as round
  end. Any time a future mission type is added, this method
  grows. A "mission reducer" that consumes a `RoundResult` event
  would decouple.
- **`updateBestWave.invoke` does its own read of the profile.**
  Meanwhile the VM already has `playerRepository.observeProfile().first()`
  seconds earlier. Two reads where one would suffice; data
  staleness risk if the two reads cross a write.
- **PostRoundOverlay has two ad buttons.** The contract is
  implicit in the VM: `gemAdWatched` flag blocks one, `psAdWatched`
  the other. No obvious sign in `RoundEndState` of "which ads are
  available". Readable but not discoverable.
- **`BattleViewModel` imports `MilestoneNotificationManager`
  directly.** Cross-layer leak (presentation → service). A
  dedicated notifier interface in the domain layer would preserve
  the Clean Architecture boundary; the other notifications in the
  codebase (widget, supply drop) live in `service/` too and are
  similarly imported from VMs.
