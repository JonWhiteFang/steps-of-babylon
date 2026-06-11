# Archaeology Phase 4 — "5 Things" Improvement List

*Companion to `small_summary.md` (Phase 1), `intro2codebase.md` +
`intro2deployment.md` (Phase 2), and the 13 per-boundary traces in
`traces/` (Phase 3). This document synthesises the recurring soft
spots surfaced in the traces into five improvement bets, each with a
code-authoritative citation, a historical rationale for why it has
not already been done, and a PR-sized first step.*

Selection criteria: each item (a) was flagged in at least two Phase 3
traces, (b) has a cheap entry point that does not fight the existing
architecture, and (c) improves a different quality attribute
(testability, correctness, reliability, maintainability, trust /
observability) so the five items together widen coverage instead of
piling on the same kind of fix.

Global rule reminder (see repo-root prompt §1, §2, §4): these
proposals keep the existing architecture, do not delete anything that
works, and — where they touch time/randomness/IDs — use the patterns
the codebase has already committed to (`Random = Random` constructor
parameter for seedable RNG; `today: String = LocalDate.now().toString()`
default parameter for date). The one place where they introduce a new
abstraction (item 1, `TimeProvider`) is a direct response to §5 of
`intro2codebase.md`, which already names the gap.

---

## 1. Introduce a narrow `TimeProvider` abstraction and migrate the handful of use cases that already pin time

**Quality attribute:** testability, reproducibility, correctness.

### Where this bites today

`intro2codebase.md` §5 *Time* names this gap explicitly:

> "There is no injected `Clock` today."

It then catalogues the consequences. The traces corroborate in detail:

- **Trace 01 §9 (Feels Vulnerable)** — "Day rollover is implicit on
  every `recordSteps`" … late-day sensor deltas buffered in the
  channel can be credited to the next day because
  `DailyStepManager.todayDate()` reads `LocalDate.now()` at
  dispatch time, not at event time. See
  `app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt`
  (member `recordActivityMinutes(..., timestampMs: Long = System.currentTimeMillis())`).
- **Trace 03 §9** — `StepCrossValidator` keys escrow by
  `"yyyy-MM-dd"`; DST / timezone changes shift the daily boundary
  used by `LocalDate.now()`. File
  `app/src/main/java/com/whitefang/stepsofbabylon/data/healthconnect/StepCrossValidator.kt`.
- **Trace 07 §9** — `AwardBattleSteps` uses `LocalDate.now()` as the
  default `today` argument. "During DST / timezone change mid-round,
  the cap day could shift and 'reset' the counter halfway through."
  See `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleSteps.kt:27`.
- **Trace 08 §9** — `endRound` reads `LocalDate.now().toString()` for
  daily mission lookup. Crossing midnight mid-round misses the
  played-day's mission. See
  `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt:168`.
- **Trace 09 §8** — `LabsViewModel` and `MissionsViewModel` run
  `while (true) { delay(1000) }` tickers that are "currently tested at
  the use-case level, not VM level, because `StandardTestDispatcher`
  with `advanceTimeBy` hangs on them."

Scale of the current coupling: `grep 'System.currentTimeMillis|LocalDate.now()|Instant.now' app/src/main`
returns **53 matches across 33 files** (7 of them in
`LabsViewModel`, 4 in `MissionsViewModel`, 3 in `HomeViewModel`).
A wholesale migration would be a big-bang PR touching a third of the
app; the small-PR step below avoids that.

### Plausible reason it isn't already done

The codebase deliberately committed to a "parameter-default" pattern
instead of DI for time-sensitive values (see ADR-0003 and Phase 2 §5
"Time"). That pattern works for use cases the engineer remembers to
parameterise, but fails for VM tickers (because Compose screens can't
thread a clock through `hiltViewModel()`) and fails for cross-module
consistency (two callers may pass different `today` strings for the
same round). The decision to avoid `Clock` was not wrong at the time
— it kept the early-project domain layer Android-free and dependency-
free — but the cost is now reaching across traces.

### Low-risk first step (PR-sized)

Introduce one new file and migrate exactly three call sites — no more
— so the abstraction earns its keep without rewriting the world:

1. `domain/time/TimeProvider.kt` (pure Kotlin — stays in `domain/`,
   zero Android imports):

   ```kotlin
   interface TimeProvider {
       fun nowEpochMs(): Long
       fun today(): String // ISO LocalDate string
   }
   ```

2. `data/time/SystemTimeProvider.kt` — default implementation using
   `System.currentTimeMillis()` and `LocalDate.now().toString()`;
   `@Binds @Singleton` in a new `di/TimeModule.kt`.

3. Migrate three call sites that already demonstrate the pain:
   - `AwardBattleSteps` — change the `today: String = LocalDate.now().toString()`
     default to take a `TimeProvider` constructor parameter; keep the
     `today: String? = null` override for current tests.
   - `BattleViewModel.endRound` — use `timeProvider.today()` instead
     of `LocalDate.now().toString()` at line 168.
   - `MissionsViewModel` ticker — take `timeProvider.nowEpochMs()`
     so a fake provider can drive midnight rollover.

Cost: ~80 lines of new code, ~6 lines changed at call sites. All
existing tests keep passing because the parameter defaults remain.
Adds one Hilt binding.

### Risk assessment

- **Main risk:** scope creep — someone "finishing" the migration in
  the same PR and touching 33 files. Mitigate by explicitly listing
  the three target files in the PR description and rejecting any
  other migrations.
- **Build risk:** low — adding an interface and one implementation is
  additive. No existing call sites change behaviour.
- **Test risk:** low — existing tests use default parameters, which
  stay. New tests can inject a `FakeTimeProvider`.

### Rollback plan

Revert the PR. The three migrated call sites can keep calling
`LocalDate.now()` as before; the `TimeProvider` files are additive
and removing them does not affect other code.

### Verification steps

1. `./run-gradle.sh testDebugUnitTest` — all 412 tests remain green.
2. Add `FakeTimeProvider` to `test/fakes/` and one new test in
   `AwardBattleStepsTest` that verifies behaviour at a synthetic
   midnight boundary. (Currently untestable without this change.)
3. Verify no Android imports leaked into `domain/time/` via
   `grep -r 'import android' app/src/main/java/com/whitefang/stepsofbabylon/domain/`.

---

## 2. Wrap the four currency-mutating multi-writes in Room `@Transaction` suspend functions

**Quality attribute:** correctness.

### Where this bites today

The entire codebase has **zero uses of `@Transaction` or
`withTransaction`** — verified by grep over `data/local/` and the
whole module. Every multi-step write relies on (a) atomic
`UPDATE … SET col = MAX(0, col + :delta)` for balances on a single
row, or (b) sequential suspend calls across different DAOs with no
rollback. This is fine for single-row deltas but leaks in four
specific places where two rows or two tables must move together:

- **`AwardBattleSteps.invoke`** —
  `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleSteps.kt:31-37`:
  reads `getBattleStepsEarned`, then calls `addSteps(credited)` on
  one DAO, then `incrementBattleSteps(today, credited)` on another.
  Trace 07 §8: *"No Room transaction around the three statements …
  A partial failure leaves the wallet and the cap counter out of sync."*
- **`PurchaseUpgrade.invoke`** —
  `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/PurchaseUpgrade.kt:20-22`:
  `playerRepository.spendSteps(cost)` then
  `workshopRepository.setUpgradeLevel(type, currentLevel + 1)`.
  Trace 09 §9: *"If … the process is killed before
  `setUpgradeLevel` runs, the player's balance is down but the level
  didn't advance."*
- **`StepCrossValidator.validate`** —
  `data/healthconnect/StepCrossValidator.kt:60-65` (and the parallel
  branches at 69-73, 77-90): `playerRepository.spendSteps(excess)`
  then `stepRepository.updateEscrow(date, excess, newSync)`. Trace
  03 §9: *"Money gone, no escrow metadata → never refunded."*
- **`BattleViewModel.endRound`** —
  `presentation/battle/BattleViewModel.kt:144-184`: no transaction
  around `updateBestWave` → `awardWaveMilestone` → `updateHighestUnlockedTier`.
  Trace 08 §8: *"No transaction around the end-round writes. Best-wave
  update and PS award can diverge if the process dies between them."*

The shared symptom is the same: **Steps are spent but the
corresponding state change is lost.** Because balances are clamped
with `MAX(0, …)`, the user never sees a negative number; they just
see missing currency with no explanation.

### Plausible reason it isn't already done

- Room `@Transaction` requires a DAO method (or
  `RoomDatabase.withTransaction {}`) and therefore needs a
  DAO-level or repository-level method that owns both writes. The
  current architecture keeps the two writes in separate
  repositories (`PlayerRepository` vs
  `WorkshopRepository` / `StepRepository`). A transaction must
  straddle both, which means either:
  - a new DAO method on a composite DAO, or
  - `AppDatabase.withTransaction { … }` from inside the use case,
    which pulls an Android dependency (`RoomDatabase`) into the
    pure-Kotlin `domain/` layer.
- Neither option is free. A shallow fix looks like "just add
  `@Transaction`"; the real fix has cross-layer implications and has
  therefore been deferred repeatedly. Phase 2 §5 notes "balances never
  go negative" as the primary invariant; divergence between balance
  and level was accepted as a low-probability tail.

### Low-risk first step (PR-sized)

Pick the single highest-impact write (`PurchaseUpgrade`, the most
user-visible of the four) and add one `@Transaction` method on
`WorkshopDao` that takes both the current upgrade level and the cost
to deduct:

```kotlin
// data/local/WorkshopDao.kt
@Transaction
suspend fun purchaseUpgradeAtomic(
    type: String,
    newLevel: Int,
    cost: Long,
    playerDao: PlayerProfileDao,
): Boolean {
    val spent = playerDao.adjustStepBalanceIfSufficient(cost) // new guarded SQL
    if (!spent) return false
    setUpgradeLevel(type, newLevel)
    return true
}
```

And add a matching SQL-level guard on `PlayerProfileDao`:

```kotlin
// Returns 1 if the row was updated (sufficient balance), 0 otherwise.
@Query("UPDATE player_profile SET currentStepBalance = currentStepBalance - :cost WHERE id = 1 AND currentStepBalance >= :cost")
suspend fun adjustStepBalanceIfSufficient(cost: Long): Int
```

Wire `PurchaseUpgrade` to call this one atomic path instead of the
two separate calls. Leave the other three sites untouched in this
PR — they become follow-up tickets.

Cost: ~30 lines. No layer-crossing introduced; the transaction lives
in `data/local/` (where it belongs) and the domain use case still
depends only on the repository interface.

### Risk assessment

- **Main risk:** the SQL guard `WHERE … >= :cost` is subtly different
  from the read-then-deduct pattern elsewhere. Two Workshop
  purchases for the same upgrade hitting the same millisecond are
  no longer possible (each sees the same row; the second's UPDATE
  affects 0 rows). This is actually an improvement — it closes the
  "debounce race" from trace 09 §9 — but it is a behaviour change.
- **Test risk:** low — add two unit tests (success + insufficient)
  on top of the existing `PurchaseUpgradeTest`. Use the in-memory
  Room setup already used by `RoomSchemaTest`.

### Rollback plan

Revert the PR. The old read-then-write path is preserved in the
default-argument form of `PurchaseUpgrade` until the new path
replaces it — make the new method additive behind a feature flag
constant if desired. Functionally, reverting restores the prior
behaviour verbatim.

### Verification steps

1. `./run-gradle.sh testDebugUnitTest` — all existing Workshop tests
   remain green.
2. New test: two concurrent `purchase()` calls on the same
   `PlayerProfileDao` must result in either one success + one
   failure, never double-level-up. Use a mock wallet with
   `stepBalance = 1 * cost` exactly.
3. Room schema export — no new schema version needed (this PR adds
   a `@Query` on an existing table).
4. Manual smoke: build a debug APK, purchase a Workshop upgrade,
   confirm balance decreases exactly once and level increases exactly
   once.

---

## 3. Make `BattleViewModel.endRound` idempotent and resilient against mid-round navigation

**Quality attribute:** reliability, correctness, UX.

### Where this bites today

This is the trace 10 *soft bug* called out in §9:

> "Deep-link during mid-battle: `navigate_to=supplies` will replace
> the Battle route with Supplies. The `BattleViewModel.onCleared`
> fires, which sets `engine.onStepReward = null`; the game loop
> thread is stopped when `BattleScreen` leaves composition; any
> in-progress round is quietly ended without the end-round cascade
> (trace 08). **Soft bug**: the round's wave/kills/cash are lost;
> best-wave is not updated."

Compounding it, trace 08 §8 flags three separate fragility issues in
the cascade itself:

- No `try/catch` around `updateBestWave` / `awardWaveMilestone` /
  `updateHighestUnlockedTier`. A single exception skips `_uiState`
  update, leaving the player on a frozen battle screen with no
  overlay.
- `roundEnded` guard is function-local; `quitRound()` and the polling
  loop both call `endRound()` and rely on the guard to dedupe.
- Reward ad flags (`gemAdWatched`, `psAdWatched`) are VM-local, not
  persisted. Kill the VM and the user can watch the ad again.

Exact entry points:

- `presentation/battle/BattleViewModel.kt` — `endRound()` at line 144,
  `quitRound()` at line 188, `onCleared()` (sets
  `engine.onStepReward = null`).
- `presentation/MainActivity.kt` — `pendingNavigation: MutableStateFlow<String?>`
  (trace 10 §6) consumes deep-links unconditionally, without
  checking whether a battle is in progress.

### Plausible reason it isn't already done

The battle system was built top-down from the happy path: user
presses **Battle**, plays, dies, sees the post-round overlay. Deep-
links were added later (Plan 19 Walking Encounters) without revisiting
the round-lifecycle contract. The assumption "users don't tap
notifications mid-battle" is reasonable but not defensive. Adding
this properly touches three files (`BattleViewModel`, `MainActivity`,
`BattleScreen`) and needed nobody to raise it as a ticket because the
symptom — a silently-lost round — is invisible to anyone not
instrumenting it.

### Low-risk first step (PR-sized)

Scope: treat navigation away from Battle as a quit, not an abandon.

1. Add a single guard in `BattleViewModel.onCleared()`:

   ```kotlin
   override fun onCleared() {
       super.onCleared()
       val eng = engine
       if (eng != null && !roundEnded && eng.waveSpawner?.currentWave != null && eng.waveSpawner!!.currentWave > 0) {
           eng.roundOver = true
           // endRound launches in viewModelScope — scope is about to cancel.
           // Use a scope that survives VM death:
           ProcessLifecycleOwner.get().lifecycleScope.launch { runEndRoundPersistence(eng) }
       }
       eng?.onStepReward = null
   }
   ```

   Where `runEndRoundPersistence` is a new pure-persistence function
   extracted from `endRound()` that writes Best Wave / milestone
   Power Stones / tier unlock **without** touching `_uiState`
   (because the UI is gone).

2. Wrap the three critical writes inside the extracted
   `runEndRoundPersistence` in try/catch with a single `Log.w` line
   per failure. Covers trace 08 §8 item 1.

3. Make `quitRound()` and the polling loop share the same extracted
   function.

Cost: ~60 lines. No new dependencies; `ProcessLifecycleOwner` is
already transitively available via `androidx.lifecycle`.

### Risk assessment

- **Main risk:** `ProcessLifecycleOwner.get().lifecycleScope` outlives
  the VM but not the process. A background kill between
  `viewModelScope.launch` and the first Room write still loses the
  round. Mitigate by wrapping the extracted function's writes in the
  `@Transaction` introduced by item 2 (they compose cleanly).
- **Surface area:** the change is confined to a single ViewModel
  file plus a small function extraction. Does not touch the game
  engine or Compose.
- **UX risk:** a user who *intentionally* aborts via deep-link will
  now see their wave counted as a valid round and their best-wave
  possibly updated. This is the intended behaviour per the existing
  `quitRound()` — which already marks the round as over and persists
  — but it is a tiny behavioural change from silent abort to "we
  saved your run".

### Rollback plan

Revert the PR. `onCleared()` goes back to just nulling the callback.
`endRound()` returns to one giant coroutine. No persisted state
changes.

### Verification steps

1. Add a ViewModel-level test using a fake `PlayerRepository` and
   fake DAOs (already exist in `test/fakes/`): simulate
   `onCleared()` mid-round, assert that `updateBestWave` was called
   exactly once.
2. Add a second test: throw from `updateBestWave`; assert
   `awardWaveMilestone` is not called but the VM does not propagate
   the exception.
3. Manual smoke: start a battle, reach wave 5, tap the system back
   button. Open the Stats screen; confirm the best-wave row
   increased and the round appears in battle stats.
4. `./run-gradle.sh testDebugUnitTest` — all existing
   `BattleViewModelTest` cases remain green.

---

## 4. Extract `FollowOnPipeline` out of `DailyStepManager`

**Quality attribute:** maintainability, testability, separation of concerns.

### Where this bites today

`DailyStepManager` is the "God class" of the step-ingestion path.
File:
`app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/DailyStepManager.kt`.
It has **11 constructor parameters** (verified lines 28–46) spanning:

- Two repositories (`StepRepository`, `PlayerRepository`)
- Four DAOs (`DailyLoginDao`, `WeeklyChallengeDao`, `DailyStepDao`,
  `DailyMissionDao`)
- Three anti-cheat components (`StepRateLimiter`,
  `StepVelocityAnalyzer`, `AntiCheatPreferences`)
- Walking-encounter repository + notifier
- Widget update helper

The traces name this repeatedly:

- **Trace 01 §10** — *"`DailyStepManager` has 11 constructor parameters
  and touches 8 Room DAOs / repositories. It is the 'God service' of
  this pipeline. … Worth splitting out the follow-on pipeline into a
  separate type (see trace 04)."*
- **Trace 04 §10** — *"`DailyStepManager` is the God class of this
  repository. … Extract `FollowOnPipeline` as its own `@Singleton`
  consuming `DailyStepManager` or taking `dailyCreditedTotal` +
  `currentDate` as parameters."*
- **Trace 04 §10** — *"`lazy { TrackDailyLogin(...) }` and
  `lazy { TrackWeeklyChallenge(...) }` inside the class — these are
  use cases built inline instead of constructor-injected. The rest of
  the codebase passes use cases as constructor parameters on
  ViewModels. This inconsistency violates the convention documented
  in the Phase 2 §4 'Use cases are not Hilt-annotated'."*

The `runFollowOnPipeline` method itself is well-factored into five
stages (lines 160–191) — widget, supply drop, daily login, weekly
challenge, walking missions — but they all live inside a class whose
primary job is anti-cheat-gated crediting. Any new step-triggered
reward (whether it's a new walking mission type or a new challenge
tier) has to touch `DailyStepManager`.

### Plausible reason it isn't already done

Pure growth. `DailyStepManager` started in Plan 04 (Step Counter
Service) as a small class that credited steps and updated the daily
record. Plan 19 added walking encounters; Plan 20 added economy;
Plan 21 added missions. Each added one stage to `runFollowOnPipeline`
and one or two constructor parameters. The class has never been
restructured because each incremental change was small.
`DailyStepManagerTest` has grown to match, and the refactor requires
updating that test to pass the new pipeline to the manager.

### Low-risk first step (PR-sized)

Pure mechanical extraction, no behaviour change.

1. Create `data/sensor/FollowOnPipeline.kt`:

   ```kotlin
   @Singleton
   class FollowOnPipeline @Inject constructor(
       private val walkingEncounterRepository: WalkingEncounterRepository,
       private val supplyDropNotificationManager: SupplyDropNotificationManager,
       private val dailyLoginDao: DailyLoginDao,
       private val weeklyChallengeDao: WeeklyChallengeDao,
       private val dailyMissionDao: DailyMissionDao,
       private val widgetUpdateHelper: WidgetUpdateHelper,
       private val playerRepository: PlayerRepository,
   ) {
       private var dropState: DropGeneratorState = DropGeneratorState()

       suspend fun run(
           context: Context,
           date: String,
           dailyCreditedTotal: Long,
           activityMinuteTotal: Long,
       ) { /* body moved from DailyStepManager.runFollowOnPipeline */ }
   }
   ```

2. In `DailyStepManager`, replace the `runFollowOnPipeline` body with
   `followOnPipeline.run(...)`. Drop six of the eleven constructor
   parameters (they move to the new class).

3. Move the inline `lazy { TrackDailyLogin(...) }` and
   `lazy { TrackWeeklyChallenge(...) }` into `FollowOnPipeline`, so
   the use-case-as-constructor-parameter convention is respected (use
   cases are plain Kotlin — they stay non-`@Inject`, just held as
   fields constructed in `init`).

4. Update `DailyStepManagerTest` — most assertions move verbatim to
   a new `FollowOnPipelineTest`. Verify the remaining
   `DailyStepManager` tests cover only anti-cheat + crediting.

Cost: ~200 lines moved, ~15 lines changed. Zero behaviour change if
the extraction is mechanical. All existing tests must still pass.

### Risk assessment

- **Main risk:** `dropState` is an in-memory field on `DailyStepManager`
  today. Moving it to `FollowOnPipeline` changes who holds it, but
  lifetime is identical (`@Singleton` → app lifetime), so behaviour is
  preserved. Flag in the PR for a reviewer.
- **Coupling:** `FollowOnPipeline.run` takes `dailyCreditedTotal` as
  a parameter, breaking the implicit "was the class-field updated
  before the pipeline ran" invariant flagged in trace 04 §8. That is
  a net improvement.
- **Test risk:** high effort (splitting `DailyStepManagerTest` —
  ~500 lines — across two files) but low regression risk. The
  existing fakes cover both classes.

### Rollback plan

Revert the PR. `DailyStepManager` is restored verbatim. The extracted
file is deleted.

### Verification steps

1. `./run-gradle.sh testDebugUnitTest` — all 412 tests remain green.
   In particular, `DailyStepManagerTest` and the new
   `FollowOnPipelineTest` together preserve full coverage of today's
   assertions.
2. Re-run the existing `balance/StepEconomyTest` and
   `data/integration/EscrowLifecycleTest` — no behaviour change
   expected, so these must remain green without modification.
3. `grep runFollowOnPipeline app/src` — confirm only two call sites
   remain, both delegating to `followOnPipeline.run`.

---

## 5. Give legitimate users visibility when anti-cheat and premium-currency awards fire

**Quality attribute:** trust (UX), debuggability (operability), observability.

### Where this bites today

Three traces flag the same problem from three angles:

- **Trace 01 §8** — `StepVelocityAnalyzer`'s `0.5×` / `0.0×` penalty
  multiplier applies silently. *"There's no user feedback when `0.5×`
  kicks in."* File:
  `app/src/main/java/com/whitefang/stepsofbabylon/data/sensor/StepVelocityAnalyzer.kt`.
- **Trace 03 §8** — `StepCrossValidator`'s four escrow levels apply
  silently. *"The player has no way to know anti-cheat fired. There
  is no notification, no Stats Screen breakdown, no explanation. For
  a new user whose phone genuinely under-reports to HC this is a
  trust problem."* Offense counter lives in plain SharedPreferences
  (`data/anticheat/AntiCheatPreferences.kt`, file
  `anti_cheat_prefs`).
- **Trace 04 §9** — *"Gem / PS addition is not audited. Daily login
  awards 1 PS + N Gems on a streak boundary with no row in any
  'awards log' table. The player cannot retrospectively see why
  their Gems went up."*

Today the Stats screen
(`app/src/main/java/com/whitefang/stepsofbabylon/presentation/stats/StatsScreen.kt`)
shows walking history, battle totals, all-time aggregates — but
nothing about anti-cheat rejections or reward sources. The counter
fields that *would* back such a view already exist in
`AntiCheatPreferences` (daily rate/velocity counters), they just are
not read by any UI.

For a single-player game where the anti-cheat ladder can demote a
legitimate user who owns two step-tracking devices, this is a real
retention risk and a support nightmare ("my Steps vanished").

### Plausible reason it isn't already done

Two historical tradeoffs:

1. *"Don't tip off cheaters."* A stated anti-cheat design principle
   in early plans. This is a reasonable instinct for multiplayer
   games with high-value economies; it is mis-applied to a solo
   offline game where the only loser of silent anti-cheat is the
   player it misfires on.
2. The data is already persisted (`antiCheatPrefs`,
   `DailyStepRecordEntity.escrowSteps`, `DailyLoginEntity`); what is
   missing is UI. Plan 22 (Stats Screen) shipped before the anti-
   cheat full ladder landed (Plan 25). No one revisited Stats to
   add these sections after Plan 25.

### Low-risk first step (PR-sized)

Ship the smallest slice that moves the trust needle: a single new
section on the Stats screen that reads the already-persisted counters.
No new writes, no new tables.

1. `AntiCheatPreferences` — add three read-only accessors (the
   underlying fields already exist):

   ```kotlin
   fun getStepsDiscardedToday(): Long        // rate-limited + velocity-penalised
   fun getCvOffenseCount(): Int              // already exists — expose to VM
   fun getLastEscrowNote(): String?          // "Level 1 on 2026-05-04"
   ```

2. Extend `StatsUiState` with a small data class
   `AntiCheatSummary(discardedToday: Long, offenseLevel: Int)`.

3. `StatsScreen` gains one new `Card { }` titled "Step validation",
   shown only when `discardedToday > 0 || offenseLevel > 0`. Copy:
   "Today, N steps were not counted because they looked unusual. If
   this keeps happening and you believe it's wrong, open Settings →
   Health Connect Permissions."

Cost: ~80 lines. No database migration; no new SharedPreferences
file; no new notification channel.

### Risk assessment

- **Main risk:** making anti-cheat visible to sophisticated users can
  inform them of exactly how the ladder works. Mitigate by showing
  only the coarse facts — number of steps not counted today, and a
  level indicator — not thresholds, not the 7-day decay rule.
- **Surface area:** one new UI section, one VM addition, three
  getters on an existing Prefs wrapper. Easy to review.
- **UX risk:** a user who sees this message every day has something
  wrong with their phone — which is useful signal. The card nudging
  them to Health Connect is actionable.

### Rollback plan

Revert the PR. The counters continue being tracked invisibly as
before. No data is lost.

### Verification steps

1. Add a `StatsViewModelTest` case: set the fake
   `AntiCheatPreferences` to `stepsDiscardedToday = 500, cvOffenseCount = 2`,
   assert the UI state exposes the summary.
2. Manual smoke: build debug APK, call
   `AntiCheatPreferences.incrementRateLimited(100)` via a debug hook,
   open Stats, confirm the new card renders.
3. `./run-gradle.sh testDebugUnitTest` — all tests remain green.
4. Screenshot verification on a physical device at different times
   of day to confirm "show only when > 0" hiding works.

---

## Summary table

| # | Improvement | Quality attribute | Smallest first step | Trace citations |
|---|---|---|---|---|
| 1 | `TimeProvider` abstraction | Testability, reproducibility | 1 new file + 3 call sites migrated | 01, 03, 07, 08, 09; `intro2codebase.md` §5 |
| 2 | `@Transaction` for multi-writes | Correctness | Add atomic `purchaseUpgradeAtomic` DAO method, wire `PurchaseUpgrade` | 03, 07, 08, 09 |
| 3 | Resilient `BattleViewModel.endRound` | Reliability, UX | Guard `onCleared`, extract persistence, try/catch each write | 08, 10 |
| 4 | Extract `FollowOnPipeline` | Maintainability, testability | Mechanical extraction, zero behaviour change | 01, 04 |
| 5 | Surface anti-cheat + reward activity | Trust, operability | New Stats-screen card reading existing counters | 01, 03, 04 |

## What is deliberately not on this list

These came up in the traces but were judged less impactful, already
tracked elsewhere, or already near-term on the plan board:

- **App icon + store assets.** Called out in `STATE.md`. Already
  covered by Plan 31.
- **Real Billing / Ads SDKs.** Stub implementations; Plan 31 replaces.
- **`PlaceholderScreen` dead code** at `MainActivity.kt:237`. One-line
  delete; trivial and not archaeology-worthy.
- **Documentation drift** (schema v7 vs v8 in docs). Called out in
  `intro2codebase.md` §9; a one-line steering fix.
- **Deep-link central registry.** Valid (trace 10 §10) but lower
  leverage than item 3 above, which solves the actual user-visible
  symptom (lost round on mid-battle deep-link).
- **Game-loop max accumulator clamp.** Trace 06 §8. Real but narrow;
  not yet observed in practice because the `isPaused` lifecycle
  observer covers the common case.
- **Device-restore DB crash.** Trace 12 §9. Real — if a user
  restores to a new device, the passphrase wipe leaves the encrypted
  DB undecryptable and the app crashes-in-a-loop until the user clears
  app data. Important and under-appreciated, but narrower in scope
  than the five above.

---

*Written 2026-05-05. All citations verified against code at HEAD
`a9d0386`; grep over `app/src/main` confirms zero uses of
`@Transaction` or `withTransaction` as of this writing, and 53
direct time calls across 33 files.*
