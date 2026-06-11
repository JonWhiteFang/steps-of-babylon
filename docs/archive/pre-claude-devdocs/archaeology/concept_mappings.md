# Concept Mappings

*Archaeology Phase 9 — maps each major concept to its implementation
locations, coverage percentage, divergence rationale, alternatives likely
considered, edge cases that shaped the design, related tests / fixtures /
migrations / config, and risks caused by the current shape.*

*Grounded in the current working tree (schema v8, 412 JVM tests,
`StepsOfBabylonApp` v1.0.0). Written from code, not from docs — where
docs conflict with code, code is treated as authoritative and the
discrepancy is called out in the relevant entry.*

*Companion to:*
- *Phase 1 `small_summary.md` (user-level overview)*
- *Phase 2 `intro2codebase.md` + `intro2deployment.md` (architecture + shipping intros)*
- *Phase 3 `traces/` (13 per-boundary deep traces)*
- *Phase 4 `5_things_or_not.md` (cross-cutting improvement proposals)*
- *Phase 5 `concepts/` (concept inventory without coverage or divergence)*
- *Phase 6 `foundations/` + Phase 7 `devdocs/foundations/` (foundations)*
- *Phase 8 `architecture_analysis.md` + `module_discovery.md` (architectural reconstruction)*

*Phase 9 is the mapping lens: for each concept, it answers "where does it
live, how complete is it, why does it look the way it does, and what does
its current shape put at risk?"*

---

## Coverage calibration

| Label | Approx % | Meaning |
|---|---|---|
| **Fully** | 85–100% | All intended behaviour, edge cases, tests, and docs present. Minor polish items may remain. |
| **Partial** | 50–84% | Core behaviour present; one or more explicit gaps (feature, test, UX, hardening). |
| **Skeleton** | 20–49% | Shape exists, real implementation is stubbed or deliberately deferred. |
| **Missing** | 0–19% | Referenced in code or docs, but no implementation. |

Coverage %s are qualitative (feature completeness + edge-case handling +
tests + docs + production-readiness), not line-counted. They are **not**
a commitment; they are a best-effort snapshot from reading code.

---

## Index

1. [Step ingestion pipeline (walking + activity minutes)](#1-step-ingestion-pipeline)
2. [Health Connect cross-validation (graduated escrow)](#2-health-connect-cross-validation)
3. [Battle Step Rewards (ADR-0003)](#3-battle-step-rewards)
4. [Currency model (Steps / Cash / Gems / Power Stones / Card Dust)](#4-currency-model)
5. [Persistence: Room, SQLCipher, migrations](#5-persistence-room-sqlcipher-migrations)
6. [Battle renderer: SurfaceView + game loop + entity system](#6-battle-renderer)
7. [Combat formulas: stats resolution, damage, defense, cards](#7-combat-formulas)
8. [Wave system & enemy roster](#8-wave-system--enemy-roster)
9. [Biome progression & visual theming](#9-biome-progression)
10. [Workshop upgrades (23 permanent upgrades)](#10-workshop-upgrades)
11. [Labs research (time-gated projects)](#11-labs-research)
12. [Cards system (packs, rarity, loadout)](#12-cards-system)
13. [Ultimate Weapons (6 abilities with cooldowns)](#13-ultimate-weapons)
14. [Step Overdrive (mid-battle buff)](#14-step-overdrive)
15. [Tier system & battle conditions](#15-tier-system)
16. [Walking encounters / supply drops](#16-walking-encounters--supply-drops)
17. [Milestones & daily missions](#17-milestones--daily-missions)
18. [Weekly challenges & daily login streak](#18-weekly-challenges--daily-login)
19. [Monetization: billing, ads, cosmetics, Season Pass](#19-monetization)
20. [Notifications (4 channels) & home widget](#20-notifications--home-widget)
21. [Navigation, deep-links, UX feedback](#21-navigation-deep-links-ux-feedback)
22. [Dependency injection (Hilt) & module layering](#22-dependency-injection--layering)
23. [Reproducibility contracts (time, randomness, IDs)](#23-reproducibility-contracts)
24. [Testing strategy (JVM-only + Robolectric)](#24-testing-strategy)
25. [Release & security hardening](#25-release--security-hardening)

[Appendix A: cross-concept risks](#appendix-a-cross-concept-risks)
[Appendix B: coverage roll-up](#appendix-b-coverage-roll-up)

---

## 1. Step ingestion pipeline

The pipeline that converts sensor events and Health Connect activity
sessions into authoritative Step credits in Room.

**Files / modules:**
- `data/sensor/StepSensorDataSource.kt` — `TYPE_STEP_COUNTER` callbackFlow wrapper
- `data/sensor/StepRateLimiter.kt` — rolling 1-min window (200/min, 250 burst)
- `data/sensor/StepVelocityAnalyzer.kt` — 15-min window, 1.0/0.5/0.0 penalty
- `data/sensor/StepIngestionPreferences.kt` — heartbeat + day-start counter (R01)
- `data/sensor/DailyStepManager.kt` — orchestrator + 5-stage follow-on pipeline
- `service/StepCounterService.kt` — FG service (health type), START_STICKY
- `service/StepSyncWorker.kt` — 15-min `CoroutineWorker` catch-up
- `service/StepSyncScheduler.kt` — periodic WorkManager request
- `service/BootReceiver.kt` — BOOT_COMPLETED → restart service
- `data/healthconnect/ActivityMinuteConverter.kt` — exercise→step-eq conversion
- `data/repository/StepRepositoryImpl.kt` + `data/local/DailyStepDao.kt`

**Coverage: Fully (≈90%).** End-to-end pipeline from sensor HAL → Room is
complete, with rate limiting, velocity analysis, 50 000/day ceiling, per-minute
overlap deduction, service/worker heartbeat handoff (R01), idempotent
activity-minute crediting (R2-01), and unified follow-on fan-out across both
input paths (R2-02). The remaining 10% is the known-gap list below.

**Why it diverged from an "ideal" architecture:** The "ideal" would be a
small stateless pipeline class that `DailyStepManager` delegates to. In
practice, `DailyStepManager` is a 380-line `@Singleton` with 12 constructor
dependencies that does both the pure pipeline (rate → velocity → ceiling →
persist) and a 5-stage follow-on fan-out (widget, supply drops, daily login,
weekly challenge, walking missions). The two responsibilities grew together
because every plan (19, 20, 21, 23, R01, R2-01, R2-02) added a downstream
consumer, and refactoring to a `FollowOnPipeline` was never urgent enough to
beat feature work (Phase 4 item 4 proposes the extraction).

**Alternatives likely considered:**
- *WorkManager-only crediting* — drop the FG service, rely on periodic
  worker. Rejected because a 15-min worker loses resolution for the 200/min
  rate limiter and can't drive a live notification.
- *DataStore instead of Room for daily records* — rejected because Room
  gives reactive `Flow` + DAOs that repositories and VMs already consume.
- *One mega-pipeline function* — rejected in favour of per-stage
  `try/catch` so one stage's crash can't poison siblings (visible in
  `runFollowOnPipeline`).
- *Shared RxJava pipeline* — not in the stack; coroutines + Flow chosen from
  Plan 01.

**Edge cases that shaped the design:**
- Cumulative sensor counter resets on device reboot → `StepSensorDataSource`
  tracks a private `lastReading` and emits deltas, not cumulative values.
- Service killed, then later restarted by OS → heartbeat (`isServiceAlive`)
  lets the worker skip sensor catch-up while service is live; when service
  is dead, worker uses Room `sensorSteps` as authoritative baseline (R01).
- Activity minutes arrive cumulatively from HC → `recordActivityMinutes`
  tracks `dailyActivityMinuteTotal` separately from `dailySensorCredited`
  and only credits the positive delta (R2-01, prevents double-credit on
  process restart).
- Per-minute activity-sensor overlap → `stepsPerMinute` map (capped at
  1 440 entries) lets `ActivityMinuteConverter` subtract sensor steps ≥50/min
  from conversion output.
- Day rollover mid-pipeline → `dailyCreditedTotal`, `dailySensorCredited`,
  `dailyActivityMinuteTotal` all reset in `ensureInitialized` on new date;
  `stepsPerMinute` map is cleared on rollover.
- 50k ceiling combined with activity minutes → ceiling applies to
  `dailyCreditedTotal = creditedSteps + stepEquivalents`; sensor and
  activity-minute sources share the budget.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `StepRateLimiterTest` (6), `StepVelocityAnalyzerTest` (6),
  `StepIngestionPreferencesTest` (11), `StepIngestionTest` (10),
  `DailyStepManagerTest` (~14, including R06/R07/R2-01/R2-12 paths).
- Migrations: none specific to this pipeline; `DailyStepRecordEntity`
  evolved via Plan 05 (escrow fields) and ADR-0003 (`battleStepsEarned`,
  migration `MIGRATION_7_8`).
- Config: `DailyStepManager.DAILY_CEILING = 50_000L`;
  `StepIngestionPreferences.HEARTBEAT_THRESHOLD_MS = 120_000`;
  `StepSyncScheduler` 15-minute `PeriodicWorkRequest`.
- Docs: `docs/step-tracking.md`, trace 01 (sensor→Room), trace 02 (worker
  handoff), trace 04 (follow-on fan-out).

**Risks caused by the current shape:**
1. **Fat orchestrator** — `DailyStepManager`'s 12-dep constructor is a
   refactor magnet; any change here risks touching every downstream
   subsystem (Phase 4 item 4; Phase 8 §3 "fat modules").
2. **Catch-all `try/catch (_: Exception) {}`** — per-stage swallowing
   prevents sibling poisoning but hides bugs; the only signal of a broken
   stage is silently-missing widget/mission/drop updates.
3. **Heartbeat timing coupling** — `HEARTBEAT_THRESHOLD_MS=120_000` must be
   > service notification-update cadence (30s) and < WorkManager minimum
   periodic interval (15min). If either Android or project policy changes
   these, the worker either silently double-credits or stops catching up
   (trace 02 §9).
4. **No transactional guarantee** — `DailyStepManager` writes to Room (via
   repository) then schedules follow-ons in best-effort mode. A crash
   between persist and fan-out produces a credit without a widget/mission
   update until the next credit. Acceptable but worth naming.
5. **Wall-clock calls sprinkled in** — `System.currentTimeMillis()` at
   multiple call sites; follow-on pipeline uses wall-clock for heartbeat
   (tests must pass fixed `timestampMs`).

---

## 2. Health Connect cross-validation

Graduated-response escrow that reconciles sensor-credited Steps against
Health Connect's independent step count and degrades credit trust when
the discrepancy exceeds 20%.

**Files / modules:**
- `data/healthconnect/HealthConnectClientWrapper.kt` — availability, permissions
- `data/healthconnect/HealthConnectStepReader.kt` — aggregate() daily steps
- `data/healthconnect/StepCrossValidator.kt` — 4-level state machine
- `data/healthconnect/StepGapFiller.kt` — fills gaps when service killed
- `data/healthconnect/ExerciseSessionReader.kt` — exercise sessions for AMP
- `data/healthconnect/ActivityMinuteConverter.kt` + `ActivityMinuteValidator.kt`
- `data/anticheat/AntiCheatPreferences.kt` — persistent 7-day-decay offense
- `data/repository/StepRepositoryImpl.kt` — `releaseEscrow` / `discardEscrow` / `clearEscrow`
- `service/StepSyncWorker.kt` — orchestrates each 15-min sync
- `presentation/HealthConnectPermissionActivity.kt` — HC rationale UI

**Coverage: Fully (≈85%).** The 4-level state machine, offense counter with
7-day decay, escrow balance deduction (R02), and rationale activity are all
shipped. The 15% gap is a known atomicity bug (writes are not transactional)
and the absence of any player-visible signal when credit is being capped.

**Why it diverged from an "ideal" architecture:** The ideal is a pure state
machine with an event log. In practice, the 4 levels are an inlined
`when(offense)` chain in `StepCrossValidator.validate()` that performs two
Room writes (`spendSteps` + `updateEscrow`) without a wrapping transaction,
plus a SharedPreferences write to `AntiCheatPreferences`. The state is
spread across Room (escrow fields on `DailyStepRecordEntity`), SharedPrefs
(offense count + last offense date), and transient in-memory state (none —
validator is stateless across calls). Done this way because (a) Room and
SharedPrefs existed already, (b) offense count needs to survive DB wipes,
and (c) a full event-sourced design would be overkill for a single-user
offline app.

**Alternatives likely considered:**
- *Binary accept/reject* on each sync — rejected; produced cliff behaviour
  that would feel punitive.
- *Exponential offense counter* — rejected for 7-day linear decay because
  it's easier to reason about and test.
- *Escrow-only, no wallet deduction* — the original design in Plan 05.
  Rejected in R02 because it let players spend suspicious steps before
  reconciliation, producing retroactive negative balances.
- *Server-side cross-validation* — rejected because `CONSTRAINTS.md`
  forbids a backend for v1.0; any client-side mechanism is cosmetic
  against a rooted device, which is an accepted tradeoff.

**Edge cases that shaped the design:**
- Player legitimately walks while driving (sensor reads high, HC reads
  low) → 20% tolerance accommodates minor drift before escrow triggers.
- HC permission revoked mid-day → `HealthConnectClientWrapper.availability`
  gracefully bypasses cross-validation; no-op, no crash.
- Escrow already held from prior sync → Level 0/1 check
  `record.escrowSteps == 0L` to distinguish "first offense this window"
  from "subsequent sync, don't double-deduct".
- Offense count resets after 7 quiet days → `AntiCheatPreferences.recordOffense()`
  checks `lastOffenseDate` and resets count if stale; prevents lifetime
  accumulation.
- Release vs discard semantics → `releaseEscrow` adds the held Steps back
  to the wallet (HC eventually agreed with sensor); `discardEscrow` leaves
  the deduction in place (HC never agreed).
- Activity-minute conversions pass through validator before counting →
  `ActivityMinuteValidator` drops <2min micro-sessions, truncates >4hr,
  caps at 5 distinct activity types/day; prevents gaming AMP.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `StepCrossValidatorTest` (10 tests, includes balance-deduction
  verification from R02), `ActivityMinuteValidatorTest` (5),
  `EscrowLifecycleTest` (2 integration tests: release net-zero + discard
  keeps-deduction).
- Migrations: Plan 05 added `escrowSteps` + `escrowSyncCount` to
  `DailyStepRecordEntity` (pre-ADR-0003 schema).
- Config: `StepCrossValidator.DISCREPANCY_THRESHOLD = 0.20`;
  `AntiCheatPreferences.OFFENSE_DECAY_DAYS = 7`; Level 0 `MAX_ESCROW_SYNCS = 3`,
  Level 1 `MAX_ESCROW_SYNCS = 2`.
- Docs: `docs/step-tracking.md` (graduated table), trace 03.

**Risks caused by the current shape:**
1. **Non-atomic escrow** (Phase 4 item 2, trace 03 §9) — `spendSteps` +
   `updateEscrow` are two separate Room writes. A crash between them
   leaves an under-escrowed wallet (deducted but not recorded as escrow),
   and `releaseEscrow` later would restore nothing → permanent loss.
2. **Invisible-to-player** (Phase 4 item 5) — a player whose credit is
   being capped at HC value sees slower-than-expected Step accumulation
   with no explanation. This conflicts with the R10 "UX-feedback contract"
   which treats silent failure as a bug.
3. **Client-only anti-cheat** — a rooted device can intercept sensor
   events or fabricate HC data; every graduated response is cosmetic
   against a determined attacker. Documented as accepted tradeoff.
4. **7-day decay window is fixed** — no tuning surface; if play patterns
   change and 7 days proves too short/long, it takes an app release.

---

## 3. Battle Step Rewards (ADR-0003)

Enemy kills award flat per-type Steps with a 2 000/day cap, separate from
the 50 k walking ceiling and from Cash. Green `+N Step` floating text on
the game canvas + HUD counter + round-end summary line.

**Files / modules:**
- `domain/usecase/AwardBattleSteps.kt` — cap read, `addSteps` + `incrementBattleSteps`
- `presentation/battle/engine/EnemyScaler.kt` — `stepReward(type)` constants
- `presentation/battle/engine/GameEngine.kt` — `@Volatile totalStepsEarned`, `onStepReward` callback
- `presentation/battle/BattleViewModel.kt` — `wireStepRewardCallback`, `onCleared` unsubscribe
- `presentation/battle/effects/FloatingText.kt` — green `STEP_COLOR` variant
- `presentation/battle/BattleScreen.kt` — HUD counter overlay
- `presentation/battle/ui/PostRoundOverlay.kt` — round-end line item
- `data/local/DailyStepDao.kt` — `getBattleStepsEarned`, `incrementBattleSteps` (UPSERT)
- `data/local/DailyStepRecordEntity.kt` — `battleStepsEarned` column (v8)
- `data/local/Migrations.kt` — `MIGRATION_7_8` (first explicit Migration)
- `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md`

**Coverage: Fully (≈95%).** All behaviour from ADR-0003 is implemented and
tested: flat constants (BASIC/FAST/SCATTER=1, RANGED=2, TANK=3, BOSS=10),
2 000/day cap, non-additive with walking ceiling, not multiplied by
Fortune/Cash Bonus/Golden Ziggurat, HUD feedback, round-end summary.
Remaining 5% is the same atomicity gap that affects other multi-write use
cases.

**Why it diverged from an "ideal" architecture:** An ideal event-driven
architecture would publish kill events to a bus and have `AwardBattleSteps`
subscribe. In practice, the game loop runs on a dedicated thread and cannot
suspend (it's a fixed-timestep loop that must not block on IO). ADR-0003
solves this via a callback pattern: `GameEngine.onStepReward` is `@Volatile`
and is invoked synchronously from the game thread; the VM's callback hops
to `viewModelScope.launch` so Room IO runs on a coroutine scope, never on
the game loop. Use case is instantiated inline in the VM (not Hilt-injected)
per the project's existing convention — confirmed explicitly in ADR-0003's
"Bug caught during verification" section.

**Alternatives likely considered:**
- *Proportional scaling* (BOSS steps = BOSS HP / 10 or similar) — rejected
  because HP scales with wave number (`EnemyScaler.hpAtWave`) and that would
  make late-game rounds trivialise the 2 000 cap.
- *Multiplied by Fortune overdrive* — explicitly rejected in ADR-0003
  because it would be anti-cheat-predictable and conflict with "Steps are
  sacred".
- *Credited at round end* (batch) — rejected because FloatingText per kill
  is a better feel-good signal.
- *A shared `stepsEarnedToday` column* — rejected; `battleStepsEarned` is
  deliberately separate so the 50 k walking ceiling stays independent.
- *Card Dust or Gems as reward* — rejected; player-facing request was
  explicitly for Steps.

**Edge cases that shaped the design:**
- Day rollover mid-round → use case re-reads `getBattleStepsEarned(today)`
  each call; fresh day starts fresh cap, in-round kills after midnight
  count toward the new day's cap.
- Cap exhausted → `AwardBattleSteps` returns 0 credit silently; FloatingText
  still shows 0 because `stepReward(type)` always returns the base amount.
  (Minor UX polish opportunity: suppress FloatingText when cap hit.)
- Game thread invokes callback while VM is tearing down → `onCleared` nulls
  the callback on the engine so no new coroutine is launched post-dispose.
- Negative or zero input → `AwardBattleSteps.invoke` early-returns if
  `amount <= 0`; covered by `AwardBattleStepsTest.negative`.
- Kill during overdrive / with Golden Ziggurat / with Cash Bonus — flat per-
  type reward, not multiplied (intentional per ADR-0003).

**Related tests, fixtures, migrations, config, docs:**
- Tests: `AwardBattleStepsTest` (6: full/partial/exhausted/rollover/
  negative/dao-amount), `EnemyScalerTest.stepReward` additions, 3 new
  `BattleViewModelTest` cases for callback wiring, FloatingText colour, and
  round-end summary.
- Migrations: `AppMigrations.MIGRATION_7_8` adds `battleStepsEarned INTEGER
  NOT NULL DEFAULT 0` to `daily_step_records`. First explicit Migration.
- Config: `AwardBattleSteps.DAILY_BATTLE_STEP_CAP = 2_000L`;
  `FloatingText.STEP_COLOR = 0xFF4CAF50`.
- Docs: ADR-0003, CONSTRAINTS.md invariant, trace 07 (full E2E).

**Risks caused by the current shape:**
1. **Non-atomic credit** (same as §2) — `addSteps` then
   `incrementBattleSteps` is two writes. A crash between them produces
   wallet-vs-cap divergence (wallet credited, cap not incremented → player
   can continue earning beyond 2 k; or inverse). Phase 4 item 2 fix covers
   this.
2. **Game-thread callback discipline** — `GameEngine.onStepReward` documented
   as "must not block"; a future maintainer who forgets and calls `runBlocking`
   inside the callback will drop frames. Enforced by convention only.
3. **Tight coupling of constants to `EnemyScaler`** — `stepReward(type)`
   hardcodes the per-type values; any future enemy type must update this
   `when` or return 0 implicitly. No `EnemyType` → `Int` map to audit.
4. **Cap visibility** — 2 000/day cap is not surfaced to the player during
   a round; once hit, rewards silently become 0 with FloatingText still
   showing the full value. Could mislead players.

---

## 4. Currency model

Four permanent currencies + one in-round volatile currency + one crafting
material, with atomic SQL-based balance adjustments and non-negative
clamping.

**Files / modules:**
- `domain/model/Currency.kt` — enum (STEPS, CASH, GEMS, POWER_STONES)
- `domain/model/PlayerWallet.kt` — balance container
- `domain/model/PlayerProfile.kt` — full profile with 27 fields
- `data/local/PlayerProfileEntity.kt` — single-row Room entity (id=1)
- `data/local/PlayerProfileDao.kt` — atomic adjust methods with `MAX(0, ...)` clamp
- `data/repository/PlayerRepositoryImpl.kt` — lifetime counter tracking
- `domain/repository/PlayerRepository.kt` — 23-method facade (universal dep)
- `presentation/battle/engine/GameEngine.kt` — `@Volatile cash` (in-round)
- `domain/usecase/AwardBattleSteps.kt`, `OpenCardPack.kt`, `UpgradeCard.kt`, `TrackDailyLogin.kt`, `TrackWeeklyChallenge.kt`, `AwardWaveMilestone.kt`, etc.

**Coverage: Fully (≈95%).** All five currencies have atomic adjust methods,
non-negative clamps (added in R10), lifetime earned/spent counters, and
exhaustive use-case coverage. 5% gap is the `Currency` enum not including
Card Dust (it lives on `PlayerProfile` as `cardDust: Long` but is not a
first-class enum entry — noted in Phase 8 §4).

**Why it diverged from an "ideal" architecture:** An ideal model would have
a single `Wallet` with a `Map<CurrencyKind, Long>`. In practice, the code
has three overlapping reward vocabularies (`Currency` enum,
`SupplyDropReward` sealed family, `MilestoneReward` sealed family) and
`PlayerWallet` omits Card Dust. Happened because `Currency` was defined in
Plan 01 (before Cards and Supply Drops existed), and when those systems
landed they introduced their own reward representations to avoid re-touching
the original `Currency` enum. Pragmatic, but leaves three vocabularies
talking about overlapping concepts.

**Alternatives likely considered:**
- *Single `Reward` sealed hierarchy* — proposed in Phase 8 §4 as a
  simplifying refactor. Not adopted because existing ViewModels already
  type-match on the specific reward sealed class and refactoring would
  ripple through milestone + supply-drop + daily-login + weekly-challenge
  flows.
- *Open-ended `Map<String, Long>` balance store* — rejected because it
  loses compile-time safety for balance fields.
- *Separate entity per currency* — rejected because every currency is
  read together (Home screen combines them all) and a single-row profile
  is simpler.
- *Event-sourced wallet* — rejected as overkill for a single-user app.

**Edge cases that shaped the design:**
- Spending more than balance → DAO queries use
  `MAX(0, currentStepBalance + :delta)` to clamp at 0 (R10). Prevents
  negative balances without requiring caller-side validation.
- Concurrent adjustments → Room executes each DAO call in its own
  transaction; the single `UPDATE ... SET col = MAX(0, col + :delta)`
  statement is atomic and handles read-modify-write races without caller
  coordination.
- Lifetime tracking → `addSteps`, `addGems`, `addPowerStones` increment
  `totalXxxEarned`; `spendXxx` increment `totalXxxSpent`. Tracked in
  `PlayerRepositoryImpl` so the domain layer doesn't know about lifetime
  fields (kept out of `PlayerRepository.spend…` signatures).
- Cash resets each round → `GameEngine.init()` sets `cash = 0L`; cash is
  never persisted to Room (trace 06 §4).
- Card Dust not in enum → `adjustCardDust` is a dedicated DAO method; UI
  surfaces it as `cardDust: Long` on `PlayerProfile` rather than as
  `wallet.get(Currency.CARD_DUST)`.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `CurrencyGuardTest` (4 tests covering spend-beyond-balance clamp
  for gems/PS/dust/steps), use case tests for every crediting/spending
  path.
- Migrations: `MIGRATION_7_8` added `battleStepsEarned` (not a new
  currency, just a cap tracker); prior migrations evolved
  `PlayerProfileEntity` via destructive fallback during dev (v2–v7).
- Config: balance tests in `balance/*Test.kt` validate currency economy
  against GDD timelines (`StepEconomyTest`, `CashEconomyTest`).
- Docs: `docs/StepsOfBabylon_GDD.md` §2 currency table, Phase 5
  `business_lvl_concepts_list.md` §3.

**Risks caused by the current shape:**
1. **Three overlapping reward vocabularies** (Phase 8 §4) — any future
   addition of a new reward type must update `SupplyDropReward`,
   `MilestoneReward`, *and* potentially `Currency`. Easy to miss one.
2. **Card Dust is second-class** — not in `Currency`, not in
   `PlayerWallet`, surfaced ad-hoc. Any UI that iterates `Currency.values()`
   (e.g. a future debug screen) will miss it silently.
3. **`PlayerRepository` is 23 methods wide** — universal dependency across
   12 ViewModels and 20+ use cases (Phase 8 §3). Changing the interface
   signature risks broad recompilation.
4. **Clamp-in-SQL, not in Kotlin** — the `MAX(0, ...)` clamp is an
   implementation detail of the DAO. A future repository impl that does
   not use SQLite (unlikely, but possible) would need to re-implement the
   clamp. Not defensible in the repository interface contract.
5. **No wallet-snapshot audit log** — currency changes are recorded only
   as "total earned/spent" deltas; debugging a "where did my 50 Gems go?"
   complaint is nearly impossible.

---

## 5. Persistence: Room, SQLCipher, migrations

Room at schema version 8 with 12 entities, SQLCipher encryption via
Android Keystore, and a nascent but growing migration framework.

**Files / modules:**
- `data/local/AppDatabase.kt` — `@Database(version=8, entities=[…12])`
- `data/local/Migrations.kt` — `AppMigrations.ALL` with `MIGRATION_7_8`
- `data/local/DatabaseKeyManager.kt` — SQLCipher passphrase via Keystore
- `data/local/Converters.kt` — `Map<Int,Int>` + `Map<String,Int>` JSON
- `di/DatabaseModule.kt` — Room builder (adds migrations,
  `fallbackToDestructiveMigrationOnDowngrade(dropAllTables=true)`)
- 12 `*Entity.kt` + 12 `*Dao.kt` in `data/local/`
- 8 `*RepositoryImpl.kt` in `data/repository/`
- `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/{1..8}.json`

**Coverage: Fully (≈85%).** All 12 entities are live, SQLCipher is enabled
with Keystore-managed passphrase, schema is exported on every build, and
there is one explicit Migration (v7→v8) plus a framework to add more.
The 15% gap is a known decrypt-failure recovery bug (wipes passphrase but
not DB file) plus the absence of any migration-rehearsal test infrastructure.

**Why it diverged from an "ideal" architecture:** Ideal persistence would
have (a) every schema version covered by a Migration from the start, (b)
migration tests on seeded databases, (c) a transaction-wrapped mutation
layer. In practice, v1–v7 were covered by
`fallbackToDestructiveMigration()` during dev (dropped and rebuilt) and
only v7→v8 has an explicit Migration (ADR-0003). `fallbackToDestructive...`
was removed in R2-06 and replaced with `fallbackToDestructiveMigrationOnDowngrade`
so post-release upgrades fail-fast instead of silently wiping data. Zero
`@Transaction`/`withTransaction` blocks exist in `app/src/main` (grep-
verified in Phase 4). This is the single biggest persistence gap: any
multi-write use case (escrow, battle steps, wallet+mission updates) is
non-atomic.

**Alternatives likely considered:**
- *SQLDelight / Exposed* — rejected because Room is first-party, has
  built-in Compose + coroutine integration, and aligns with the rest of
  Jetpack.
- *DataStore for preferences + simple state* — used for many preferences
  (8 wrapper classes), but Room is canonical for game state. Split
  retained because DataStore is overkill for boolean toggles.
- *Multi-module persistence* — rejected; single `:app` module keeps
  Gradle compile graph simple (Phase 8 §7).
- *Unencrypted DB + file-system permissions* — rejected because the
  app has no account system, so device-level encryption is the only
  barrier against inspection by a compromised device.
- *Auto-migrations* (Room-generated) — could be enabled but
  ADR-0003's migration required a defaulted column, which Room supports
  via `@ColumnInfo(defaultValue = "0")`. The team chose an explicit
  Migration object to set a pattern for future non-trivial cases.

**Edge cases that shaped the design:**
- Device restore after uninstall → Keystore entry is lost, stored
  passphrase blob cannot be decrypted, recovery wipes prefs blob and
  generates fresh key. But the encrypted DB file remains and is now
  unreadable — latent crash-on-launch (trace 12 §9, missing-concept
  "wipe DB on decrypt failure").
- Downgrade attempts → `fallbackToDestructiveMigrationOnDowngrade(true)`
  drops all tables and rebuilds from scratch. Acceptable because we have
  no cloud save; users choosing to side-load an older build lose progress.
- Schema export path → `room { schemaDirectory("$projectDir/schemas") }`
  exports on every build; schemas are version-controlled and serve as
  migration reference (not loaded at runtime).
- Backup → `android:allowBackup="false"` (R05) eliminates Google's Auto
  Backup restore, which would otherwise produce encrypted files the new
  install's Keystore can't unlock.
- `Map<Int,Int>` columns → `Converters.kt` serialises to JSON via
  `org.json.JSONObject`; required for `PlayerProfileEntity.bestWavePerTier`
  and `DailyStepRecordEntity.activityMinutes`.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `RoomSchemaTest` (3 Robolectric tests for round-trip of profile,
  steps, workshop entities); no migration-test infrastructure yet.
- Migrations: `AppMigrations.MIGRATION_7_8` (first explicit), schema
  JSON files for v1..v8 in `app/schemas/`.
- Config: `StepsOfBabylonApp.kt` calls `System.loadLibrary("sqlcipher")`;
  `DatabaseKeyManager` uses `SecureRandom(32 bytes)` for passphrase,
  AES-GCM Keystore alias `"db_passphrase_key"`.
- Docs: `docs/database-schema.md` (note: says v7, code is v8 — code
  authoritative), `.kiro/steering/lib-room.md`, trace 12.

**Risks caused by the current shape:**
1. **Zero `@Transaction` wrapping** (Phase 4 item 2) — every multi-write
   use case is non-atomic. Five known sites: `AwardBattleSteps`,
   `StepCrossValidator`, `DailyStepManager.recordSteps`,
   `PurchaseUpgrade` + mission progress, `ClaimMilestone`. Partial-failure
   produces silent state divergence.
2. **Decrypt-failure recovery leaves zombie DB file** (missing concept;
   trace 12 §9) — new passphrase cannot open existing DB; app crashes on
   launch. Fixable by wiping the DB file alongside the passphrase blob.
3. **`fallbackToDestructiveMigrationOnDowngrade(dropAllTables=true)`** —
   intended safety net, but a misconfigured build that accidentally lowers
   `@Database(version = ...)` would wipe production data silently. Version
   lives in a single file; low probability but non-zero.
4. **No migration-rehearsal test** — the only migration (v7→v8) has no
   round-trip test that seeds a v7 DB, migrates, and asserts v8 schema.
   Future migrations are one-shot on first user upgrade; a mistake ships
   broken.
5. **Schema drift vs docs** — `docs/database-schema.md` says v7; code is
   v8. Already flagged in Phase 2, not yet fixed.
6. **`PlayerProfileEntity` has 27 columns** — fat single-row entity is
   easy to work with but hard to evolve; adding a column means a
   migration even if the column is feature-flagged.

---
## 6. Battle renderer

Custom `SurfaceView` outside Compose, with a dedicated 60-UPS fixed-timestep
game loop on a separate thread, entity system, and effect engine.

**Files / modules:**
- `presentation/battle/BattleScreen.kt` — Compose wrapper, `AndroidView(GameSurfaceView)`
- `presentation/battle/GameSurfaceView.kt` — `SurfaceHolder.Callback`, thread lifecycle
- `presentation/battle/GameLoopThread.kt` — fixed-timestep loop with accumulator
- `presentation/battle/engine/GameEngine.kt` — entity list, update/render, `@Volatile` state
- `presentation/battle/engine/Entity.kt` — abstract base
- `presentation/battle/entities/` — 5 entity subclasses (Ziggurat, Projectile, Enemy, EnemyProjectile, Orb)
- `presentation/battle/effects/ParticlePool.kt` — 200-capacity object pool
- `presentation/battle/effects/EffectEngine.kt` — effect ownership + dispatch
- `presentation/battle/effects/ScreenShake.kt`, `FloatingText.kt`, `DeathEffect.kt`, `UWVisualEffect.kt`, `OverdriveAuraEffect.kt`, `WaveAnnouncement.kt`, `ProjectileTrailEffect.kt`, `ReducedMotionCheck.kt`
- `presentation/audio/SoundManager.kt` — `SoundPool` wrapper
- `presentation/battle/BattleViewModel.kt` — polls engine every 200ms
- `presentation/battle/BattleUiState.kt` — engine→UI state projection

**Coverage: Fully (≈90%).** 60-UPS loop, accumulator-scaled speed controls
(1×/2×/4×), entity system with pending-add queue, particle pool, screen
shake, reduced-motion gating, all wired. 10% gap is (a) placeholder audio
(sine-wave `.ogg` files), (b) no boss-priority targeting (documented Plan 5
omission), (c) no cosmetic visual hook in the renderer (even though
`CosmeticEntity` owns data).

**Why it diverged from an "ideal" architecture:** An ideal design would use
Compose for everything. Compose's layout-and-recomposition model is not a
good fit for a 60-UPS simulation with ~100 entities/frame, so the battle
renderer uses a dedicated SurfaceView. Communication between game thread
and UI thread is done via `@Volatile` fields on `GameEngine` that the VM
polls every 200 ms (trace 06 §6). Not ideal — a channel-based async
pipeline would be cleaner — but polling keeps the game thread free of
coroutine scaffolding and the 200 ms cadence is invisible to users. The
VM-to-engine callback pattern (`onStepReward`) is ad-hoc but works because
there's only one such callback today.

**Alternatives likely considered:**
- *OpenGL ES / Vulkan* — rejected as overkill for 2D canvas rendering.
- *libGDX / Unity* — rejected because they pull heavy runtimes; Android's
  `Canvas` handles the frame budget easily at this entity count.
- *Compose-only* — rejected; tested during prototyping, frame budget
  exceeded with >30 entities.
- *Channel-based engine↔VM comms* — could replace polling; not adopted
  because the 200 ms cadence is fine for HUD updates.
- *ECS (Entity-Component-System)* — considered but not adopted; `Entity`
  is an abstract base with concrete subclasses (classic OO hierarchy).
  Works at this scale; would refactor if entity count grew.

**Edge cases that shaped the design:**
- Surface destroyed mid-loop → `GameSurfaceView.surfaceDestroyed` joins
  the thread before returning; prevents rendering to a dead surface.
- `ConcurrentModificationException` from adding entities mid-update →
  `GameEngine.pendingAdd` queue merges on next tick start.
- Speed multiplier × `dt` drift → accumulator-based loop scales the
  accumulator, not `dt`, so physics stay deterministic at all speeds
  (trace 06 §5).
- Reduced motion → `ReducedMotionCheck` reads
  `ANIMATOR_DURATION_SCALE=0` and disables screen shakes / trails /
  non-essential particles. No in-app toggle — respects OS setting.
- Auto-pause on app backgrounding → `BattleScreen` observes
  `ON_PAUSE` lifecycle event and calls `viewModel.pause()`.
- `Random` in damage/crit is injected → `CalculateDamage(random: Random)`
  lets tests use a seeded Random for deterministic crit assertions.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `ParticlePoolTest` (9), `ScreenShakeTest` (6), `DeathEffectTest`
  (7), `EnemyScalerTest` (wave scaling + step rewards). No rendering
  tests (Canvas output is not asserted).
- Config: `GameLoopThread.TICK_NS = 16_666_667L` (60 UPS);
  `ParticlePool` capacity 200; `SoundPool` 7 effects with shoot throttle;
  `GameEngine.POLL_RATE_MS = 200`.
- Docs: `docs/architecture.md` battle-renderer section, trace 05 (boot),
  trace 06 (single frame).

**Risks caused by the current shape:**
1. **Polling the engine every 200 ms** — works but couples the VM to a
   fixed interval. If the engine adds more `@Volatile` fields, the VM
   has to know to poll each one; no subscription mechanism.
2. **`@Volatile` everywhere** — guarantees visibility but not atomicity.
   `GameEngine.cash`, `roundOver`, `totalEnemiesKilled`, etc. can be
   read mid-update by the VM, producing briefly inconsistent UI snapshots.
3. **Surface teardown coupling** — the thread-join in `surfaceDestroyed`
   is synchronous; if a game-thread action is blocked (shouldn't happen),
   the UI thread blocks during navigation.
4. **No instrumented tests** — Canvas rendering and SoundPool behaviour
   are untested. Bugs in rendering show up only in manual QA.
5. **Cosmetic hook missing** — `CosmeticEntity` exists (§19) but
   `GameEngine` doesn't consult it. Cosmetic visuals are data-only.

---

## 7. Combat formulas

Two-layer stat resolution (Workshop × In-Round) composed multiplicatively,
then card-effect post-processing, then per-hit damage and defense
calculations with hard caps.

**Files / modules:**
- `domain/model/ZigguratBaseStats.kt` — base constants (HP, damage, range, etc.)
- `domain/model/ResolvedStats.kt` — computed combat stats
- `domain/usecase/ResolveStats.kt` — Workshop + In-Round → `ResolvedStats`
- `domain/usecase/ApplyCardEffects.kt` — post-process with equipped cards
- `domain/usecase/CalculateDamage.kt` — base dmg + crit roll + bonuses
- `domain/usecase/CalculateDefense.kt` — damage reduction + flat block
- `domain/model/UpgradeType.kt` — 23 upgrades with `UpgradeConfig`
- `domain/model/CardType.kt` — 9 cards with `valueLv1`/`valueLv5`
- `domain/model/BattleConditionEffects.kt` — tier modifiers
- `presentation/battle/engine/GameEngine.kt` — stat re-resolve on purchase

**Coverage: Fully (≈95%).** Workshop, in-round, cards, and tier conditions
all compose; hard caps enforced (defense ≤75%, crit ≤80%); balance tests
validate the whole stack against GDD profiles. 5% gap is just the two
hidden upgrade types (STEP_MULTIPLIER, RECOVERY_PACKAGES) that are enum
entries but not surfaced in the Workshop UI (R04).

**Why it diverged from an "ideal" architecture:** The ideal is a pure
function `ResolvedStats.from(workshop, inRound, cards, tier)`. Reality is
close — `ResolveStats` composes multiplicatively, then `ApplyCardEffects`
post-processes a copy. The only divergence is that `ApplyCardEffects` is a
separate use case rather than a step inside `ResolveStats`, done so
tests and `GameEngine` can opt out of card application (e.g. on
`playAgain` before cards are re-equipped). `CalculateDamage` also injects
`Random` so crit rolls are deterministic in tests.

**Alternatives likely considered:**
- *Additive stacking* (base + Σ multipliers) — rejected because
  multiplicative is standard for tower defence and makes balance
  predictable.
- *Rule-engine / expression-language for upgrades* — rejected because 23
  upgrades each have hand-tuned `effectPerLevel` math; a generic engine
  would lose readability.
- *Cards as first-class stats (Workshop-level)* — rejected to keep Cards
  temporary (equipped, not permanent upgrades).
- *Floating-point vs fixed-point* — chose doubles for simplicity; balance
  tests catch precision issues.

**Edge cases that shaped the design:**
- Cap enforcement → each stat caps in `ResolveStats` (crit chance
  `min(…, 0.80)`, defense `min(…, 0.75)`). Caps are in-function so a card
  buff can't push past a hard limit.
- Scatter enemy splitting → on-death, `GameEngine.handleEnemyDeath`
  spawns 2–3 BASIC enemies at the dead enemy's position (matches GDD).
- Thorn + death-defy + lifesteal ordering → `GameEngine` applies defense
  first (multiplicative then flat), then death-defy (grants 1 HP if
  damage would kill), then thorn (reflects % back to attacker). Centralised
  in `applyDamageToZiggurat` (Plan 10 decision).
- Crit roll seed → `CalculateDamage.random: Random` defaults to `Random` but
  tests pass `Random(0)` for deterministic rolls.
- Overdrive transition → `GameEngine` keeps a `preOverdriveStats` snapshot
  and restores it on expiry. Same for `preGoldenStats` (Golden Ziggurat UW).
  Two snapshots implicitly chain (Phase 8 §4).

**Related tests, fixtures, migrations, config, docs:**
- Tests: `ResolveStatsTest` (multiplicative stacking + caps),
  `CalculateDamageTest` (crit with injected Random),
  `CalculateDefenseTest` (percent + flat + floor at 0),
  `ApplyCardEffectsTest` (11 tests: all 9 effects + level scaling +
  buff/debuff combos), `BattleConditionEffectsTest` (6 tier-level tests),
  `UpgradeTypeTest`, `CardBalanceTest` (balance regression).
- Config: `ZigguratBaseStats` constants; 23 `UpgradeConfig` balance rows
  in `UpgradeType`; 9 `CardType` value rows; `BattleConditionEffects`
  percent modifiers by tier.
- Docs: `docs/battle-formulas.md` (note R04 disclaimer about hidden
  upgrade types).

**Risks caused by the current shape:**
1. **Two snapshot variables** (`preOverdriveStats`, `preGoldenStats`) —
   restore order is implicit; if a third stat-modifying ability lands
   (e.g. "Lucky Charm UW"), a fourth snapshot + explicit order becomes
   necessary. Easy to miss.
2. **Hidden enum entries** (STEP_MULTIPLIER, RECOVERY_PACKAGES) — filtered
   out by `WorkshopViewModel.hiddenUpgrades` (R04) but fully functional in
   `ResolveStats`. A future developer who unhides them must verify
   balance, not just UI.
3. **Float/double rounding** — balance tests cover the happy path; edge
   cases (very large upgrade level × very low scaling) could produce
   surprises. No property-based testing.
4. **No damage-type system** — every damage source is scalar; future
   additions (fire, cold, poison) would require ResolvedStats expansion.

---

## 8. Wave system & enemy roster

Alternating 26s spawn / 9s cooldown wave phases with deterministic enemy
composition and boss waves.

**Files / modules:**
- `presentation/battle/engine/WaveSpawner.kt` — 26s/9s state machine
- `presentation/battle/engine/EnemyScaler.kt` — 1.05^wave scaling + cash rewards + step rewards
- `presentation/battle/engine/CollisionSystem.kt` — projectile↔enemy, projectile↔ziggurat
- `presentation/battle/entities/EnemyEntity.kt` — 6 types + movement + attack
- `presentation/battle/entities/EnemyProjectileEntity.kt` — ranged enemy projectiles
- `domain/model/EnemyType.kt` — 6 entries (BASIC/FAST/TANK/RANGED/BOSS/SCATTER)
- `domain/model/BattleConditionEffects.kt` — tier modifiers (armor, speed, boss cadence)

**Coverage: Fully (≈95%).** Wave state machine, 6 enemy types with
distinct behaviours, boss every 10 waves (every 7 at tier 9+ via
`MORE_BOSSES`), wave-based scaling (1.05^wave), scatter-splitting,
armored enemies at higher tiers, ranged attacks, knockback resistance.
5% gap is no boss-priority targeting (Phase 5 missing concept) and no
per-wave difficulty tuning knobs beyond the 1.05 curve.

**Why it diverged from an "ideal" architecture:** The ideal would be a
wave-config JSON loaded at runtime. In practice, `WaveSpawner.composition`
is a hard-coded function of wave number (basic + fast + tank tiers, plus
a boss overlay). Works because the enemy roster is small and the difficulty
curve is geometric. A data-driven wave table would be necessary if wave
designs became bespoke; for v1.0 the formula suffices and balance tests
validate it.

**Alternatives likely considered:**
- *JSON wave table* — rejected to keep everything in Kotlin; balance
  tests would need more scaffolding.
- *Pluggable `WaveStrategy` interface* — deferred; single strategy today.
- *Random enemy types* — rejected; deterministic composition makes
  difficulty predictable.
- *Per-wave scripting* (move, spawn, delay) — rejected as overkill for
  current scope.

**Edge cases that shaped the design:**
- Converging spawn from 3 edges (top, left, right) → avoids single-
  direction exploit builds; balance decision in Plan 09.
- Boss overlap with cooldown phase → boss spawns at `SPAWNING→COOLDOWN`
  transition; `onWaveComplete` callback fires only when all enemies are
  dead (Plan 11).
- Scatter split → handled in `GameEngine.handleEnemyDeath` (not in the
  entity) so collision/damage system doesn't need to know about
  self-replication.
- Armored enemies (tier 6+) → `EnemyEntity.armorHits` counts incoming
  projectile hits and only takes damage after armor is stripped;
  counter rendered as an outer ring.
- Knockback resistance via `BattleConditionEffects` → applied as
  multiplier in `GameEngine`, not in the enemy (keeps enemy code agnostic
  of tier).

**Related tests, fixtures, migrations, config, docs:**
- Tests: `EnemyScalerTest` (scaling curve + per-type rewards + step
  rewards), `EnemyTypeTest` (multiplier correctness),
  `BattleConditionEffectsTest`, `EnemyScalingTest` (balance regression).
- Config: `WaveSpawner.SPAWN_DURATION_SEC = 26`, `COOLDOWN_DURATION_SEC = 9`;
  `EnemyScaler.HP_SCALING = 1.05`; boss cadence default 10 (7 at
  `MORE_BOSSES`).
- Docs: `docs/battle-formulas.md` wave section, trace 06 (single frame).

**Risks caused by the current shape:**
1. **Hard-coded composition** — any future bespoke wave (e.g. "all
   bosses wave 50") requires code change; no data file.
2. **No boss-priority targeting** (Phase 5 missing concept) —
   `findNearestEnemies` ignores enemy type; a RANGED enemy behind a wall
   of BASICs may deal huge damage before being shot. Common tower-defence
   ergonomic gap.
3. **26/9 cadence is not tunable** — if balance drifts, cadence is a
   release-level change. No runtime-config surface.
4. **Enemy entity mixes render + AI + stats** — a future new behaviour
   (e.g. "invisible enemy") would add fields to the already-busy class.

---

## 9. Biome progression

Five narrative environments mapped from tier ranges, each supplying a
colour palette, background renderer, ambient particles, and a first-time
transition overlay.

**Files / modules:**
- `domain/model/Biome.kt` — 5 entries + `forTier(n)` map
- `presentation/battle/biome/BiomeTheme.kt` — sky/ground/ziggurat/enemy/particle palettes
- `presentation/battle/biome/BackgroundRenderer.kt` — gradient sky + ambient particles
- `presentation/battle/ui/BiomeTransitionOverlay.kt` — first-entry overlay
- `data/BiomePreferences.kt` — SharedPreferences wrapper for first-seen tracking
- `presentation/battle/BattleViewModel.kt` — consults prefs, emits transition state
- `presentation/home/HomeScreen.kt` — biome gradient background
- `presentation/battle/entities/ZigguratEntity.kt` — uses `layerColors`
- `presentation/battle/entities/EnemyEntity.kt` — 30% enemy tint blend
- `presentation/battle/engine/WaveSpawner.kt` — passes `enemyTint`

**Coverage: Fully (≈95%).** All 5 biomes render, particles tint, ziggurat
recolours, transition overlay shows on first entry, home screen reflects
current biome. 5% gap is (a) transition is a static Compose screen rather
than an animated cinematic (Plan 27 decision: animation deferred), (b)
celestial-gate biome (tier 11+) has no distinct enemy model variant.

**Why it diverged from an "ideal" architecture:** Ideal would be a pluggable
`BiomeRenderer` interface with per-biome render logic. In practice,
`BiomeTheme` is just a palette object and `BackgroundRenderer` takes it as
data; enemy tinting is done by colour blend (30% with base enemy colour),
not by per-biome enemy sprites. Works because the visual signature of each
biome is mostly palette + particles, not topology.

**Alternatives likely considered:**
- *Per-biome background bitmaps* — rejected; vector gradients are smaller
  and scale to all densities.
- *Sprite-based ambient particles* — rejected; geometric particles + palette
  give the feel at lower cost.
- *Animated cinematic overlay* — deferred to Plan 27 (polish). Present
  implementation is a simple Compose screen with biome title + step count.
- *Enemy sprite per biome* — rejected; 30% colour blend provides theme
  without asset multiplication.

**Edge cases that shaped the design:**
- Tier 11+ has no unique biome → `Biome.forTier(n)` returns `Celestial Gate`
  for all n≥11; `BiomeTheme` provides a palette but no special effects.
- First-seen tracking across app reinstalls → `BiomePreferences` in
  SharedPrefs (not Room) survives DB wipes (e.g. `fallbackToDestructive...`)
  so players don't re-see transitions they've already seen.
- Battle starts before biome transition dismissed → game loop thread is
  paused until `BattleViewModel.dismissBiomeTransition()` fires.
- Tier change mid-session → transition overlay re-triggers if the new
  biome hasn't been seen.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `BiomeTest` (tier mapping), `BiomeThemeTest` (palette per biome,
  4 tests).
- Config: `BiomeTheme` constants for 5 palettes; `Biome.forTier` range
  table.
- Docs: `docs/StepsOfBabylon_GDD.md` §14, Plan 18.

**Risks caused by the current shape:**
1. **No tier 11+ visual differentiation** — Celestial Gate uses a
   single palette for all n≥11; late-game players see no visual progression
   beyond tier 10. Minor content gap.
2. **SharedPrefs-only first-seen tracking** — if the prefs file is wiped
   (no known path, but possible), all transitions re-trigger once. Low
   severity.
3. **Tight coupling between `BiomeTheme` and entity rendering** — every
   entity type that participates in biome theming (ziggurat layers, enemy
   tint) must be passed the colours explicitly in its constructor. Adding
   a new biome-aware element means touching every caller.

---

## 10. Workshop upgrades

23 permanent upgrades across Attack/Defense/Utility categories, purchased
with Steps, with geometric cost scaling and per-upgrade max levels.

**Files / modules:**
- `domain/model/UpgradeType.kt` — 23 entries with `UpgradeConfig`
- `domain/model/UpgradeCategory.kt` — 3 categories
- `domain/model/UpgradeConfig.kt` — baseCost, scaling, maxLevel
- `domain/usecase/CalculateUpgradeCost.kt` — `baseCost × scaling^level`
- `domain/usecase/CanAffordUpgrade.kt` — wallet check
- `domain/usecase/PurchaseUpgrade.kt` — deduct + increment
- `domain/usecase/QuickInvest.kt` — recommend cheapest affordable
- `domain/repository/WorkshopRepository.kt` — interface
- `data/repository/WorkshopRepositoryImpl.kt` — Room impl
- `data/local/WorkshopUpgradeEntity.kt` + `WorkshopDao.kt`
- `presentation/workshop/WorkshopScreen.kt` — 3-tab layout, Scaffold + SnackbarHost
- `presentation/workshop/WorkshopViewModel.kt` — purchase flow + `userMessage` + `hiddenUpgrades`
- `presentation/workshop/UpgradeCard.kt` — reusable card component

**Coverage: Fully (≈95%).** All 23 upgrades live, 3-category UI, Quick
Invest FAB, purchase pulse animation, user-feedback snackbar, double-tap
guard, balance regression. 5% gap is the two hidden entries (STEP_MULTIPLIER,
RECOVERY_PACKAGES) that are documented but disabled in UI (R04).

**Why it diverged from an "ideal" architecture:** Ideal would be a
content-driven table of upgrades loaded at runtime. In practice, each
upgrade is an `UpgradeType` enum entry carrying its own `UpgradeConfig`
(balance sheet baked into the enum). Works because the list is small
(23) and rarely changes; balance tests validate costs. The enum-as-balance-
sheet pattern (Phase 8 §5, "enum-as-balance-sheet") is applied uniformly
across upgrades, research, cards, UWs, overdrives, enemies, missions.

**Alternatives likely considered:**
- *JSON/YAML content files* — rejected; adds loading+parsing code for a
  small, stable dataset.
- *DB-backed upgrade table* — rejected; would require seeding and
  migrations for every balance tweak.
- *Separate domain classes per upgrade* — rejected; enum gives
  exhaustive `when` coverage in tests and VMs.

**Edge cases that shaped the design:**
- Max level → `CalculateUpgradeCost` returns `Long.MAX_VALUE` for levels
  ≥ `maxLevel`; `PurchaseUpgrade` early-returns with `userMessage`.
- Spending more than balance → DAO's `MAX(0, …)` clamp prevents negative
  balance; use case surfaces "Not enough Steps" via `userMessage`.
- Concurrent purchase → double-tap guard (`isProcessing`) in VM prevents
  duplicate purchases during the coroutine gap (R10).
- Purchase while a round is active → `BattleViewModel` re-resolves stats
  from workshop on `onStatsChanged`; in-round purchase doesn't apply
  until new round (intentional; workshop upgrades are "permanent", not
  "instant").
- Hidden upgrades → `WorkshopViewModel.hiddenUpgrades` set filters out
  STEP_MULTIPLIER, RECOVERY_PACKAGES from the list but enum is preserved
  for future re-enable (R04 decision).

**Related tests, fixtures, migrations, config, docs:**
- Tests: `CalculateUpgradeCostTest` (all 23 types),
  `CanAffordUpgradeTest`, `PurchaseUpgradeTest`, `QuickInvestTest`,
  `UpgradeTypeTest`, `WorkshopViewModelTest` (6),
  `UserFeedbackTest` (workshop-specific user-message flow),
  `CostCurveTest` (balance regression for all 23 types).
- Config: `UpgradeConfig` base costs (100–5 000), scaling (1.11–1.35),
  max levels per type.
- Docs: `docs/battle-formulas.md`, Phase 5 `business_lvl_concepts_list.md`
  §4.

**Risks caused by the current shape:**
1. **Hidden enum entries in production code** — R04 disabled UI but not
   the enum; a developer writing a new VM could accidentally expose them.
   No test asserts the filter is applied.
2. **Workshop purchase doesn't hot-apply to active rounds** — design
   choice, but surprising if a player thinks a mid-round purchase takes
   effect immediately. Not surfaced in UX copy.
3. **Balance sheet coupled to code** — every balance tweak requires a
   build and release; no live-ops lever.
4. **DAO and domain duplicate key knowledge** — upgrade identifiers are
   both enum entries and `upgradeType: String` columns in Room; a rename
   requires a migration.

---
## 11. Labs research

10 time-gated research projects paid in Steps + real-time duration, with
1–4 concurrent slots, Gem rush for instant completion, and auto-complete
on app launch.

**Files / modules:**
- `domain/model/ResearchType.kt` — 10 entries with `baseCostSteps`, `baseTimeHours`, scaling
- `domain/model/ActiveResearch.kt` — in-progress research domain model
- `domain/usecase/CalculateResearchCost.kt` — `baseCost × 1.15^level`
- `domain/usecase/CalculateResearchTime.kt` — `baseTime × 1.10^level`
- `domain/usecase/StartResearch.kt` — slot/affordability/max-level validation
- `domain/usecase/CompleteResearch.kt` — timer-gated completion
- `domain/usecase/RushResearch.kt` — linear 50–200 Gem cost
- `domain/usecase/UnlockLabSlot.kt` — 200 Gems per slot, max 4
- `domain/usecase/CheckResearchCompletion.kt` — auto-complete on launch
- `domain/repository/LabRepository.kt` + `data/repository/LabRepositoryImpl.kt`
- `data/local/LabResearchEntity.kt` + `LabDao.kt`
- `presentation/labs/LabsScreen.kt` + `LabsViewModel.kt` + `LabsUiState.kt`

**Coverage: Fully (≈90%).** All 10 research types, slot unlocking, Gem
rush with linear interpolation, Season Pass free rush (once/day), 1-second
countdown ticker, `userMessage` feedback. 10% gap: no in-app notification
when research completes while app is closed (user only sees the checkmark
next app open).

**Why it diverged from an "ideal" architecture:** Ideal would be a
WorkManager job per active research that fires a completion notification.
In practice, `CheckResearchCompletion` runs on app launch (via
`HomeViewModel.init`) and auto-completes all expired research in a single
pass. This skips the notification path for closed-app completions —
players discover completions on next open. Design choice: keeps the
completion path simple and batched; avoids ten WorkManager requests fanning
out.

**Alternatives likely considered:**
- *One WorkManager job per research* — rejected; adds complexity for a
  single-user feature whose completion timing isn't latency-critical.
- *Instant complete on manual Gem rush only* — rejected; time-gated
  research is the point of the system.
- *Per-research shared completion time* — rejected; independent timers
  give flexibility.

**Edge cases that shaped the design:**
- App never opened past completion time → `CheckResearchCompletion`
  iterates all active research and completes every expired one in order.
- Rush cost at different completion fractions →
  `calculateRushCost(fraction) = 50 + fraction × 150`, linear
  interpolation between 50 (just started) and 200 (almost done).
- Season Pass free rush → `LabsViewModel.freeRush()` guarded by
  `profile.seasonPassActive && profile.seasonPassExpiry > now` +
  `profile.freeLabRushUsedToday`.
- Max slots → `UnlockLabSlot` caps at 4 even if player has 1 000 Gems.
- Concurrent research cap → `StartResearch` returns `Result.NoSlotsAvailable`
  when `activeCount >= slotCount`.
- Walking missions for research → `LabsViewModel` updates
  `COMPLETE_RESEARCH` mission progress after rush or completion.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `CalculateResearchCostTest` (3), `CalculateResearchTimeTest` (3),
  `StartResearchTest` (5), `CompleteResearchTest` (3), `RushResearchTest`
  (4), `UnlockLabSlotTest` (3), `CheckResearchCompletionTest` (3),
  `LabsViewModelTest` (4).
- Migrations: v3 added `labSlotCount` to `PlayerProfileEntity` (default 1);
  v7 added Season Pass / free-rush fields.
- Config: `ResearchType.costScaling = 1.15`, `timeScaling = 1.10`;
  `UnlockLabSlot.SLOT_UNLOCK_COST = 200` Gems; `RushResearch` 50–200 Gems.
- Docs: `docs/plans/plan-16-labs.md`.

**Risks caused by the current shape:**
1. **No completion notification** — player has to open the app to see
   completed research. If they have 4 slots rushing simultaneously and
   the app is closed, all complete silently.
2. **Silent early-returns fixed in R2-09** — `freeRush()` used to
   silently fail on three paths (Season Pass inactive, already used,
   no active research); R2-09 added `userMessage` for each. Worth naming
   because it was a latent UX problem.
3. **Ticker loop in VM** — `LabsViewModel` has a 1s `while(true) { delay }`
   countdown; direct VM tests hang, tests must use use-case-level
   assertions (Plan 29 decision).
4. **Research → mission progress coupling** — `LabsViewModel` calls
   `DailyMissionDao` directly to bump progress. One of four VMs with this
   pattern (Phase 8 §3 "mission-progress duplication at 5 sites").

---

## 12. Cards system

9 card types × 3 rarities, Gem-purchased packs, Card Dust for upgrades,
loadout of 3, post-process stat application.

**Files / modules:**
- `domain/model/CardType.kt` — 9 entries with `valueLv1`/`valueLv5`/`secondaryLv1`/`secondaryLv5`
- `domain/model/CardRarity.kt` — 3 rarities with `dustValue` + `upgradeDustPerLevel`
- `domain/model/CardLoadout.kt` — max 3 equipped
- `domain/model/OwnedCard.kt` — player-owned card instance
- `domain/usecase/OpenCardPack.kt` — rarity rolling, dust from dupes, injected `Random`
- `domain/usecase/UpgradeCard.kt` — dust cost scaling
- `domain/usecase/ApplyCardEffects.kt` — post-process ResolvedStats
- `domain/usecase/ManageCardLoadout.kt` — equip/unequip with max-3 guard
- `domain/repository/CardRepository.kt` + `data/repository/CardRepositoryImpl.kt`
- `data/local/CardInventoryEntity.kt` + `CardDao.kt`
- `presentation/cards/CardsScreen.kt` + `CardsViewModel.kt` + `CardsUiState.kt`

**Coverage: Fully (≈92%).** 9 types with level interpolation, 3 rarities,
3 pack tiers (Common 50G / Rare 150G / Epic 500G), duplicate→dust
conversion, loadout with max-3 guard, free pack via reward ad, Scaffold +
snackbar feedback. 8% gap: max-3 guard is enforced only in ViewModel, not
at DAO level (Phase 8 §5 "implied-but-not-enforced invariants").

**Why it diverged from an "ideal" architecture:** Ideal would enforce the
loadout cap at the data layer (DAO trigger or SQL CHECK). In practice,
`ManageCardLoadout` use case and `CardsViewModel.equip()` both check
`loadout.size < 3`, but the DAO has no guard. A direct DAO call (e.g. from
a future debug tool) could equip 4 cards. Similar pattern to
`UltimateWeaponLoadout`. Accepted because both code paths to the DAO run
through the use case today.

**Alternatives likely considered:**
- *Loadout column as CSV on PlayerProfile* — rejected; card instances need
  individual level tracking, so they're separate rows with an `equipped`
  boolean.
- *Random with cryptographic seed* — overkill; `Random` seedable for tests
  is sufficient.
- *Card Dust as a currency in `Currency` enum* — rejected (see §4).

**Edge cases that shaped the design:**
- Duplicate opening → `OpenCardPack` converts dupes to dust (5/15/50 per
  rarity) instead of discarding; guarantees value from every pack.
- Pack distribution → Common 80/18/2, Rare 50/40/10, Epic 20/40/40 —
  tuned so Epic packs reliably give at least one Rare+.
- Free pack via reward ad → `watchFreePackAd()` → `StubRewardAdManager`
  → `OpenCardPack(isFree = true)`; once per day guarded by
  `profile.freeCardPackAdUsedToday`.
- Upgrade cost scaling → `dustCost = rarity.upgradeDustPerLevel * level`;
  linear by level, tiered by rarity.
- Loadout duplicates → `CardLoadout.add` rejects if already equipped;
  max-3 also enforced here.
- Level scaling → `CardType.effectAtLevel(level)` linearly interpolates
  between `valueLv1` and `valueLv5`.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `OpenCardPackTest` (4), `UpgradeCardTest` (4),
  `ApplyCardEffectsTest` (11), `ManageCardLoadoutTest` (3),
  `CardLoadoutTest` (3), `CardsViewModelTest` (5), `CardBalanceTest`
  (balance regression).
- Config: 3 pack tiers with Gem costs + distributions; `CardRarity.dustValue`
  5/15/50; `upgradeDustPerLevel` 10/25/50.
- Docs: Plan 17 doc, Phase 5 concepts.

**Risks caused by the current shape:**
1. **Max-3 invariant enforced only in VM/use case** — no DAO-level guard.
   Direct DAO access could violate the invariant. Accepted today because
   no direct DAO path exists.
2. **Random seed not centralised** — `OpenCardPack` takes its own
   `Random`; if a future "guaranteed-fairness" requirement lands (e.g.
   "never 20 common rolls in a row"), that logic has nowhere to live.
3. **Dust balance is not a first-class currency** (§4) — future features
   that want to award dust for non-duplicate reasons (e.g. milestone
   rewards) must route through a dedicated path.

---

## 13. Ultimate Weapons

6 activatable battle abilities with cooldowns, unlocked and upgraded with
Power Stones, loadout of 3, visual spectacles on activation.

**Files / modules:**
- `domain/model/UltimateWeaponType.kt` — 6 entries with unlock cost, cooldown, effect duration
- `domain/model/UltimateWeaponLoadout.kt` — max 3 equipped
- `domain/model/OwnedWeapon.kt` — player-owned instance with level
- `domain/usecase/UnlockUltimateWeapon.kt` — Power Stone deduction + unlock
- `domain/usecase/UpgradeUltimateWeapon.kt` — cost scaling, max level 10
- `domain/repository/UltimateWeaponRepository.kt` + `data/repository/UltimateWeaponRepositoryImpl.kt`
- `data/local/UltimateWeaponStateEntity.kt` + `UltimateWeaponDao.kt`
- `presentation/weapons/UltimateWeaponScreen.kt` + `UltimateWeaponViewModel.kt`
- `presentation/battle/ui/UltimateWeaponBar.kt` — in-battle activation bar
- `presentation/battle/engine/GameEngine.kt` — 6 effect implementations
- `presentation/battle/effects/UWVisualEffect.kt` — 6 particle-based spectacles

**Coverage: Fully (≈92%).** 6 UW types (DEATH_WAVE, CHRONO_FIELD,
CHAIN_LIGHTNING, GOLDEN_ZIGGURAT, POISON_SWAMP, BLACK_HOLE) with unlock
costs (50–100 PS), upgrade scaling, cooldown reduction (−5%/level, max 10),
loadout max 3, activation bar, accessibility semantics (Plan R10), visual
effects. 8% gap: Golden Ziggurat keeps a separate `preGoldenStats` snapshot
that implicitly chains with `preOverdriveStats` (Phase 8 §4 risk).

**Why it diverged from an "ideal" architecture:** Ideal would be a
`UltimateWeaponEffect` interface with one implementation per UW and an
effect dispatch table. In practice, `GameEngine` has inline code for each
of the 6 UWs (`activateDeathWave()`, `activateChronoField()`, etc.) with
explicit branches for per-UW state (e.g. Golden Ziggurat's pre-stat
snapshot, BLACK_HOLE's entity spawn). Works because there are only 6 UWs
and each is structurally distinct; a plugin architecture would over-
abstract.

**Alternatives likely considered:**
- *Effect-interface plugin architecture* — rejected; 6 UWs, each bespoke.
- *UW as data-driven scripts* — rejected for the same reason as upgrades.
- *Cooldown-reset as part of each activation* — rejected; SURGE overdrive
  is the dedicated mechanism.
- *Power Stones purchasable with real money* — rejected (hard invariant,
  §4).

**Edge cases that shaped the design:**
- Unlock without having Power Stones → `UnlockUltimateWeapon` returns
  `InsufficientPowerStones`; VM surfaces via message (for Workshop UI).
- Equip already-equipped UW → loadout `add` rejects duplicates.
- Activate during cooldown → `UltimateWeaponBar` shows cooldown progress
  overlay; taps no-op.
- Max level 10 → upgrade cost = `unlockCost × 2 × level`; capped by
  `MAX_LEVEL = 10`.
- Golden Ziggurat effect stacks with Overdrive → `preGoldenStats` +
  `preOverdriveStats` are separate snapshots; restore order is implicit
  (Phase 8 §4 risk).
- BLACK_HOLE spawns an entity → `GameEngine.spawn` via `pendingAdd` queue.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `UnlockUltimateWeaponTest` (3), `UpgradeUltimateWeaponTest` (4),
  `UltimateWeaponLoadoutTest` (3), `UltimateWeaponViewModelTest` (4),
  `UWOverdriveBalanceTest` (balance regression).
- Migrations: no schema change; `UltimateWeaponStateEntity` stable.
- Config: 6 UW types with unlock costs (DEATH_WAVE 50, POISON_SWAMP 60,
  CHRONO_FIELD / CHAIN_LIGHTNING 75, GOLDEN_ZIGGURAT 80, BLACK_HOLE 100);
  cooldown reduction 5%/level; max level 10.
- Docs: Plan 15.

**Risks caused by the current shape:**
1. **Two implicit stat-snapshot chains** (Phase 8 §4) — Overdrive and
   Golden Ziggurat each keep a pre-snapshot. A third similar ability
   would need a fourth; restore ordering is fragile.
2. **Loadout cap VM-only** (same as §12) — no DAO guard.
3. **UW effect code embedded in `GameEngine`** — adding a 7th UW requires
   editing the already-busy `GameEngine`. Plugin architecture would help
   once the roster grows.
4. **Balance tests pass by numbers, not by feel** — cooldown timings at
   max level give ~2–3 activations per round; if balance tests loosen,
   UW power creep could go unnoticed.

---

## 14. Step Overdrive

Mid-battle mechanic that burns Steps for a 60-second combat buff, once
per round, with 4 variants.

**Files / modules:**
- `domain/model/OverdriveType.kt` — 4 entries (ASSAULT, FORTRESS, FORTUNE, SURGE) with costs + effects
- `domain/usecase/ActivateOverdrive.kt` — balance + once-per-round guard
- `presentation/battle/engine/GameEngine.kt` — overdrive state (timer, multipliers, stat snapshot)
- `presentation/battle/entities/ZigguratEntity.kt` — aura visuals, timer bar
- `presentation/battle/effects/OverdriveAuraEffect.kt` — 4 aura particle emitters
- `presentation/battle/ui/OverdriveMenu.kt` — selection UI
- `presentation/battle/BattleViewModel.kt` — `activateOverdrive()`, `toggleOverdriveMenu()`
- `presentation/battle/BattleUiState.kt` — overdrive state fields

**Coverage: Fully (≈90%).** 4 overdrive types, Step-cost validation,
once-per-round guard, 60s duration, visual aura, HUD indicator, accessibility
semantics, SURGE properly resets UW cooldowns (added in Plan 15). 10% gap:
FORTUNE explicitly excluded from Battle Step Rewards multiplication (ADR-
0003, intentional), which means an engaged Overdrive player still gets the
same 2 000/day battle-step cap.

**Why it diverged from an "ideal" architecture:** Ideal would be a
polymorphic `Overdrive` with `apply` / `revert` on a stat context. In
practice, `GameEngine` has inline overdrive state (`activeOverdrive`,
`overdriveTimer`, `fortuneMultiplier`) and handles the 4 variants via
`when(type)` branches. Same rationale as UWs — 4 bespoke behaviours, plugin
architecture would over-abstract.

**Alternatives likely considered:**
- *Overdrive stacking (multiple at once)* — rejected by "once per round"
  design invariant.
- *Per-category cost curves* — rejected; flat costs (500/500/300/750
  Steps) keep player decision-making simple.
- *Shorter duration* — rejected; 60s is tuned so it covers roughly 2
  waves at 1× speed.
- *FORTUNE multiplies Battle Steps too* — rejected in ADR-0003 to keep
  anti-cheat predictable.

**Edge cases that shaped the design:**
- Insufficient Steps → `ActivateOverdrive.Result.InsufficientSteps`;
  menu disables the button.
- Already used this round → `Result.AlreadyActivated`; menu shows used
  state.
- Round end with active overdrive → `GameEngine.expireOverdrive()`
  restores pre-snapshot stats and clears timer.
- Navigation interrupt (deep-link during overdrive) → same risk as the
  round-end cascade; `onCleared` doesn't expire overdrive cleanly
  (Phase 4 item 3).
- SURGE interaction with UW cooldowns → resets all equipped UWs'
  cooldowns; implemented in `GameEngine.activateSurge()` after Plan 15.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `ActivateOverdriveTest` (4: insufficient Steps, once-per-round,
  success, result types).
- Config: 4 overdrive costs (ASSAULT 500, FORTRESS 500, FORTUNE 300,
  SURGE 750); duration 60s.
- Docs: Plan 14.

**Risks caused by the current shape:**
1. **Timer state in `GameEngine` @Volatile** — VM polls it; brief UI
   inconsistency possible at the ms-level (invisible to users).
2. **Pre-snapshot vs Golden Ziggurat ordering** (Phase 8 §4) — if player
   activates Overdrive, then Golden Ziggurat, then Overdrive expires
   before Golden, the restore chain has to pick the right baseline.
   Currently works by construction (timer ordering), but no test asserts
   the interaction.
3. **Step cost not surfaced on round start** — player has to open the
   menu to see costs vs balance.
4. **No cooldown after use** — once-per-round design, but players often
   forget they used it; no indicator other than greyed menu entry.

---

## 15. Tier system

10 difficulty levels with geometric cash multipliers, wave-milestone-
based unlocks, battle conditions starting at Tier 6, and tier-selector
UI on the home screen.

**Files / modules:**
- `domain/model/Tier.kt` — data class
- `domain/model/TierConfig.kt` — full table (10 tiers) with `forTier(n)`
- `domain/model/BattleCondition.kt` — 7 condition types
- `domain/model/BattleConditionEffects.kt` — pre-computed modifiers
- `domain/usecase/CheckTierUnlock.kt` — checks wave milestones vs bestWavePerTier
- `data/local/PlayerProfileEntity.kt` — `currentTier`, `highestUnlockedTier`, `bestWavePerTier` (Map<Int,Int>)
- `presentation/home/TierSelector.kt` — horizontal chip row
- `presentation/home/HomeViewModel.kt` — `selectTier()` exposes unlocked tiers
- `presentation/battle/engine/WaveSpawner.kt` — consumes `BattleConditionEffects` for boss cadence / armor / speed
- `presentation/battle/BattleViewModel.kt` — checks tier unlock after round end
- `presentation/battle/ui/PostRoundOverlay.kt` — "Tier N Unlocked" banner

**Coverage: Fully (≈95%).** All 10 tiers, 7 battle conditions, tier
selector, unlock flow, cash multipliers (1.0×–10.0×), boss cadence at
`MORE_BOSSES`, post-round unlock banner. 5% gap: no "tier preview" UI to
show player what conditions they'll face before committing.

**Why it diverged from an "ideal" architecture:** Ideal would separate
"tier config" (balance data) from "unlock logic" (game rule). In practice,
`TierConfig` carries both: the full table of (tier → unlock requirements +
cash multiplier + conditions) in a single data object, and `CheckTierUnlock`
is a pure function that walks it. Works because tiers 1–10 are a fixed
curated list; no runtime tuning needed.

**Alternatives likely considered:**
- *Infinite tiers with formula* — rejected; wave milestones and cash
  multipliers are hand-tuned per tier.
- *Tier selector as separate screen* — rejected in Plan 13; horizontal
  chip row is more compact.
- *Tier locked to current highest* — rejected; players want to revisit
  easier tiers to farm.

**Edge cases that shaped the design:**
- Tier 1 auto-unlocked → `highestUnlockedTier` defaults to 1 in
  `PlayerProfileEntity`.
- Unlock new tier mid-round → `BattleViewModel.endRound` calls
  `CheckTierUnlock` and surfaces the banner; doesn't auto-switch
  `currentTier` (player picks when to advance).
- Best-wave tracking per tier → `bestWavePerTier: Map<Int, Int>` serialized
  via `Converters.toIntIntMap`.
- Battle conditions at Tier 6+ → `BattleConditionEffects.fromTier(n)`
  aggregates percent modifiers; Tier 10 applies all 7.
- Tier selection persists → `PlayerRepository.setCurrentTier` updates
  `PlayerProfileEntity.currentTier`; next battle loads that tier's config.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `TierConfigTest` (all 10 tiers, battle conditions),
  `CheckTierUnlockTest` (7), `BattleConditionEffectsTest` (6),
  `TierProgressionTest` (balance regression).
- Migrations: v2 added `highestUnlockedTier`; v6 added battle stats; v7
  added monetization fields.
- Config: cash multipliers 1.0×–10.0×; battle condition percents per
  tier; 7 condition types (ENEMY_SPEED, ENEMY_ATTACK_SPEED, ORB_RESISTANCE,
  KNOCKBACK_RESISTANCE, ARMORED_ENEMIES, THORN_RESISTANCE, MORE_BOSSES).
- Docs: `docs/StepsOfBabylon_GDD.md` §14, Plan 13.

**Risks caused by the current shape:**
1. **No tier preview** — player commits to a tier without a summary of
   conditions. `TierSelector` shows the condition count only.
2. **`bestWavePerTier` as Map column** — JSON-serialised in Room; a
   typo in a caller querying it by tier key produces a silent miss.
3. **Infinite tier support is implicit** — `TierConfig.forTier(n)` for
   n > 10 returns a fallback config but no distinct visual progression
   (§9, celestial gate).
4. **Tier unlocking is computed post-round only** — if the player quits
   a round after passing a milestone but before `endRound`, unlock is
   lost (Phase 4 item 3, round-end cascade risk).

---
## 16. Walking encounters / supply drops

Seeded random drops generated during step crediting, delivered as push
notifications, queued in an Unclaimed Supplies inbox with a 10-item cap.

**Files / modules:**
- `domain/model/SupplyDrop.kt` — drop data
- `domain/model/SupplyDropTrigger.kt` — 4 triggers (MILESTONE, THRESHOLD, RANDOM, STEP_BURST)
- `domain/model/SupplyDropReward.kt` — 4 reward types (Steps, Gems, PS, Card Dust)
- `domain/model/DropGeneratorState.kt` — tracks `lastCheckSteps`, `milestoneTriggered`
- `domain/usecase/GenerateSupplyDrop.kt` — seeded RNG + priority ladder
- `domain/usecase/ClaimSupplyDrop.kt` — credit reward, mark claimed
- `domain/repository/WalkingEncounterRepository.kt` + `data/repository/WalkingEncounterRepositoryImpl.kt`
- `data/local/WalkingEncounterEntity.kt` + `WalkingEncounterDao.kt`
- `service/SupplyDropNotificationManager.kt` — `supply_drops` channel with deep-link
- `presentation/supplies/UnclaimedSuppliesScreen.kt` + `UnclaimedSuppliesViewModel.kt`
- `data/sensor/DailyStepManager.kt` — calls `GenerateSupplyDrop` in follow-on pipeline

**Coverage: Partial (≈75%).** 3 of 4 triggers are wired (MILESTONE,
THRESHOLD, RANDOM), inbox cap enforcement via `enforceInboxCap(max=10)`,
notification with tap-to-navigate, claim/claimAll UI, badge on home
screen. 25% gap: `STEP_BURST` enum value exists but no burst-detection
code emits it (declared in `SupplyDropTrigger.kt:5`, never produced —
Phase 8 finding).

**Why it diverged from an "ideal" architecture:** Ideal would be a
trigger-registration system with pluggable detectors. In practice,
`GenerateSupplyDrop` has hard-coded priority-ladder checks (milestone →
threshold → random), each as an `if` branch. Works because there are few
trigger types and they share state (step count); a plugin system would
over-abstract.

**Alternatives likely considered:**
- *GPS-based triggers* — rejected; adds location permission, battery
  cost, and privacy surface. Step-count-based only.
- *Per-day cap on drops* — rejected in favour of inbox cap (prevents
  notification spam differently: oldest unclaimed discarded).
- *Card Pack as reward type* — rejected; Card Dust instead (avoids
  coupling to OpenCardPack flow).
- *Server-seeded RNG for fairness guarantees* — rejected; no backend.
- *Per-trigger notification channels* — rejected; single channel with
  tap-routing keeps the notification settings simple.

**Edge cases that shaped the design:**
- Inbox overflow → `enforceInboxCap` deletes oldest unclaimed drop
  silently; no user notification of loss (design choice; avoids spam).
- Day rollover mid-step-burst → `DailyStepManager` resets
  `DropGeneratorState` on day rollover; `milestoneTriggered` flag lets
  the 10k-steps milestone drop fire only once per day.
- Claim already-claimed drop → `ClaimSupplyDrop` returns false (idempotent
  guard); VM surfaces no error (invisible to user).
- Seeded randomness → uses injected `Random` default `Random` for
  real generation; tests pass `Random(42)` for deterministic assertions.
- Notification permission denied → drops still generated and inboxed;
  only the OS notification fails silently.
- Navigate from notification → `pendingNavigation` flow routes to
  `supplies` route; works from cold and warm start.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `GenerateSupplyDropTest` (9), `ClaimSupplyDropTest` (6),
  `UnclaimedSuppliesViewModelTest` (3), `SupplyDropEconomyTest` (balance
  regression).
- Config: inbox cap 10, milestone at 10 000 steps/day, threshold 2 000
  boundary with 5% per 100 steps, random 1% per 500 steps.
- Docs: Plan 19, trace 10.

**Risks caused by the current shape:**
1. **`STEP_BURST` orphan enum** — maintainers may wire notifications or
   UI to it assuming it fires; it doesn't. Any future step-velocity
   detector needs to explicitly emit this trigger.
2. **Silent inbox overflow** — player loses a drop without knowing.
   Could surface in Stats as "drops lost today".
3. **Fixed trigger probabilities** — any balance tuning requires an
   app release.
4. **Random-seed is process-scoped** — two devices on the same day will
   see different drops (unlike daily missions which are date-seeded).
   Noted in `devdocs/foundations/known_requirements.md` as vague.

---

## 17. Milestones & daily missions

Lifetime walking milestones (6 thresholds) + 3 daily missions (1 per
category) with date-seeded generation and claim UI.

**Files / modules:**
- `domain/model/Milestone.kt` — 6 entries with thresholds + rewards
- `domain/model/MilestoneReward.kt` — sealed (Gems, PowerStones, Cosmetic)
- `domain/model/DailyMissionType.kt` — 6 entries (2 walking, 2 battle, 2 upgrade)
- `domain/model/MissionCategory.kt` — WALKING/BATTLE/UPGRADE
- `domain/usecase/CheckMilestones.kt` — threshold + unclaimed detection
- `domain/usecase/ClaimMilestone.kt` — step-threshold re-check (R2-08) + credit
- `domain/usecase/GenerateDailyMissions.kt` — `Random(todayDate.hashCode())`
- `data/local/MilestoneEntity.kt` + `MilestoneDao.kt`
- `data/local/DailyMissionEntity.kt` + `DailyMissionDao.kt`
- `data/MilestoneNotificationPreferences.kt` — dedup prefs (R2-08)
- `presentation/missions/MissionsScreen.kt` + `MissionsViewModel.kt`
- `service/MilestoneNotificationManager.kt` — `milestones` channel
- `data/sensor/DailyStepManager.kt` — walking mission progress (R07)
- `presentation/battle/BattleViewModel.kt` — battle mission progress
- `presentation/workshop/WorkshopViewModel.kt` + `labs/LabsViewModel.kt` — upgrade/research mission progress

**Coverage: Partial (≈80%).** Milestone detection + claim + step-threshold
re-check (R2-08), notification dedup, date-seeded mission generation, live
progress from all 5 call sites (walking, battle, workshop, labs,
missions screen), midnight day-change detection (R10). 20% gap:
milestone cosmetic rewards store ownership but have no visual effect
(same as §19 cosmetics), mission progress tracking is duplicated across 5
call sites (Phase 8 §3 "duplication"), and battle mission progress is
not robust against navigation interrupts.

**Why it diverged from an "ideal" architecture:** Ideal would be a
central `MissionProgressTracker` that subscribes to domain events. In
practice, each VM knows which mission categories it touches and calls
`DailyMissionDao.updateProgress()` directly (Phase 8 §3: duplication at 5
sites). Same pattern as "fat follow-on pipeline" — grew incrementally and
never got extracted. Phase 4 item doesn't cover this directly but it's
adjacent.

**Alternatives likely considered:**
- *Event-bus from DAO to VMs* — rejected; would add a new abstraction
  across the data↔presentation boundary.
- *Database triggers for progress* — rejected; Room triggers are
  supported but fragile.
- *Server-generated missions* — rejected; no backend.
- *User-chosen daily missions* — rejected; deterministic generation
  ensures cross-device consistency and removes choice paralysis.

**Edge cases that shaped the design:**
- Milestone cosmetic rewards → `ClaimMilestone` stores `cosmeticId` but
  does not trigger any visual change (cosmetic system visual application
  is missing, §19).
- Step threshold race → R2-08 added `profile.totalStepsEarned ≥
  milestone.requiredSteps` re-check inside `ClaimMilestone` so a stale
  snapshot in the VM can't credit a milestone the player hasn't earned.
- Notification dedup → `MilestoneNotificationPreferences` stores seen-
  milestone IDs so each milestone notification fires at most once
  (R2-08).
- Date-seeded mission generation → `Random(todayDate.hashCode())` gives
  same trio cross-device per day; idempotent on same date.
- Midnight crossing in MissionsScreen → `MissionsViewModel`'s 1 s ticker
  detects day change, regenerates missions, updates walking progress
  (R10).
- Mission progress for closed-app completions → walking missions
  updated inside `DailyStepManager.runFollowOnPipeline`; battle missions
  at `endRound`; workshop at `purchase`; lab at `rush`/`completion`.
- Claim failure paths → `ClaimMilestone` returns false if not yet
  achieved or already claimed; VM surfaces `userMessage`.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `MilestoneTest` (6), `DailyMissionTypeTest` (7),
  `CheckMilestonesTest` (4), `ClaimMilestoneTest` (5 including R2-08
  threshold test), `GenerateDailyMissionsTest` (6),
  `MissionsViewModelTest` (4, use-case level).
- Migrations: v5 added MilestoneEntity + DailyMissionEntity (Plan 21).
- Config: 6 milestone thresholds (1k/10k/100k/500k/1M/5M steps); 6
  mission types with rewards; cosmetic IDs on 3 milestones.
- Docs: Plan 21.

**Risks caused by the current shape:**
1. **Mission progress duplication at 5 sites** (Phase 8 §3) — adding a
   new progress-bumping action means touching 5 VMs and remembering to
   call the DAO. Easy to miss.
2. **Cosmetic milestone rewards are no-ops** — player claims and
   `cosmeticId` lands in the DB, but `CosmeticEntity` has no matching
   seed entries for the 3 milestone cosmetic IDs (Phase 8 §3 finding).
3. **Battle mission progress lost on navigation interrupt** —
   `BattleViewModel.endRound` updates missions, but `onCleared` doesn't
   complete the cascade (Phase 4 item 3).
4. **Mission DAO called directly from VMs** — bypasses the repository
   layer; Phase 8 §8 flags 12 similar forbidden-direction imports.

---

## 18. Weekly challenges & daily login

Weekly step challenge (rolling 7-day sum, 3 thresholds, Power Stones) +
daily login streak (Gems + Power Stones) + wave milestone PS (on new
personal best).

**Files / modules:**
- `domain/usecase/TrackDailyLogin.kt` — streak + PS + Gems with Season Pass bonus
- `domain/usecase/TrackWeeklyChallenge.kt` — threshold detection + delta PS
- `domain/usecase/AwardWaveMilestone.kt` — 1/2/5 PS on new best wave
- `data/local/WeeklyChallengeEntity.kt` + `WeeklyChallengeDao.kt`
- `data/local/DailyLoginEntity.kt` + `DailyLoginDao.kt`
- `data/local/DailyStepDao.kt` — `sumCreditedSteps` query for weekly total
- `data/local/PlayerProfileEntity.kt` — `currentStreak`, `lastLoginDate`
- `data/sensor/DailyStepManager.kt` — calls `TrackDailyLogin` + `TrackWeeklyChallenge` in follow-on
- `presentation/economy/CurrencyDashboardViewModel.kt` — hybrid reactive + snapshot
- `presentation/economy/CurrencyDashboardScreen.kt` — weekly progress + streak dots
- `presentation/home/HomeViewModel.kt` — triggers `TrackDailyLogin` in init

**Coverage: Fully (≈88%).** All three systems live, streak increments /
resets / cycles, Season Pass +10 Gems bonus (Plan 26), wave milestones
at 1/2/5 PS boundaries, hybrid live+snapshot ViewModel (R2-10), lifecycle
refresh on screen entry. 12% gap: `TrackDailyLogin` call path from
`DailyStepManager` never passes Season Pass flags, so walking-streak Gems
lose the +10 Gems Season Pass bonus (Phase 8 §3 — the Season Pass bonus
only applies when login is triggered from HomeViewModel init, not from
the sensor pipeline).

**Why it diverged from an "ideal" architecture:** Ideal would be a pure
reactive economy model where all currency events flow through a single
observable. In practice, each of the three systems has its own DAO, use
case, and call site. `CurrencyDashboardViewModel` uses a hybrid approach
(R2-10): live `observeProfile()` for balances + `MutableStateFlow<SnapshotData>`
for weekly/login data (refreshed on screen entry). Hybrid because weekly
total requires a DAO aggregate query that doesn't fit into a Flow.

**Alternatives likely considered:**
- *Snapshot-only dashboard* — rejected because balances must stay live
  (currency changes from other screens must reflect immediately).
- *Live aggregate query* — possible, but weekly total requires a 7-day
  sum and the `sumCreditedSteps` DAO is easier to call imperatively.
- *Streak state in Room as separate entity* — rejected; 2 fields on
  `PlayerProfileEntity` are cheaper (Plan 20 decision).
- *Monday-relative week boundary* — rejected; rolling 7-day from today
  is simpler and equivalent in the aggregate.

**Edge cases that shaped the design:**
- Missed day → streak resets to 1 if `lastLoginDate` is not yesterday.
- Streak cycles after day 7 → Gem reward is `min(streak, 5)`, day 8
  earns 5 Gems (cap).
- Less than 1 000 steps → daily login PS not awarded (`TrackDailyLogin`
  guard: only fires when walking threshold met).
- Season Pass expiry mid-day → bonus applies only if
  `seasonPassExpiry > now` at call time.
- Weekly threshold partial → delta PS only: crossing 75k after 50k
  grants +10 PS (not 20); crossing 100k after 75k grants +15 PS.
- Wave milestone PS → 1 PS (new best), 2 PS (wave % 10 == 0), 5 PS
  (wave % 25 == 0); paid only on new personal best.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `TrackDailyLoginTest` (6), `TrackWeeklyChallengeTest` (5),
  `AwardWaveMilestoneTest` (9), `CurrencyDashboardViewModelTest` (3
  including R2-10 reactive test).
- Migrations: v4 added WeeklyChallengeEntity + DailyLoginEntity + streak
  fields on PlayerProfile (Plan 20).
- Config: weekly thresholds 50k (10 PS) / 75k (+10 = 20 total) / 100k
  (+15 = 35 total); daily login PS 1 + Gems `min(streak,5)`; Season
  Pass +10 Gems.
- Docs: Plan 20, Phase 5 concepts.

**Risks caused by the current shape:**
1. **Season Pass bonus miss from sensor path** (Phase 8 §3) —
   `DailyStepManager.runFollowOnPipeline` calls `TrackDailyLogin(...)`
   without Season Pass params; HomeViewModel's init call passes them.
   Players who open the app after their steps crediting complete get
   the bonus; players who only pass through the sensor path miss it.
2. **Weekly total requires a snapshot** — if the player walks while
   the Economy screen is open, weekly progress doesn't refresh until
   they navigate away and back or pull-to-refresh. R2-10 added a
   refresh() method; `LaunchedEffect(Unit)` calls it on entry.
3. **Streak state in two places** — `lastLoginDate` on PlayerProfile +
   `DailyLoginEntity` per-day records. Writes to both must stay in
   sync; no `@Transaction` guard.
4. **Wave milestone PS tied to `UpdateBestWave`** — if the wave-best
   write fails partway through round-end, milestone PS is lost.

---

## 19. Monetization

Stub Google Play Billing with 5 SKUs (3 Gem packs + Ad Removal + Season
Pass), stub AdMob with 3 reward placements, 7 placeholder cosmetics.

**Files / modules:**
- `domain/model/BillingProduct.kt` — 5 SKUs + `PurchaseResult` sealed
- `domain/model/AdPlacement.kt` — 3 placements + `AdResult` sealed
- `domain/model/CosmeticCategory.kt` — 3 categories (ziggurat, projectile, enemy)
- `domain/model/CosmeticItem.kt` — cosmetic domain model
- `domain/repository/BillingManager.kt` + `data/billing/StubBillingManager.kt`
- `domain/repository/RewardAdManager.kt` + `data/ads/StubRewardAdManager.kt`
- `domain/repository/CosmeticRepository.kt` + `data/repository/CosmeticRepositoryImpl.kt`
- `data/local/CosmeticEntity.kt` + `CosmeticDao.kt`
- `domain/usecase/PurchaseGemPack.kt`
- `di/BillingModule.kt` + `di/AdModule.kt` — swap points for real SDKs
- `presentation/store/StoreScreen.kt` + `StoreViewModel.kt` + `StoreUiState.kt`
- `presentation/battle/ui/PostRoundOverlay.kt` — post-round ad buttons
- `presentation/cards/CardsScreen.kt` — free pack ad button
- `presentation/labs/LabsViewModel.kt` — Season Pass free rush

**Coverage: Skeleton (≈45%).** All plumbing is complete — interfaces,
stub implementations, UI, purchase/ad flows, Season Pass daily bonus
(Plan 26), ad-removal guard gates all 3 placements, cosmetic
buy/equip/unequip. Major gaps: (a) real Google Play Billing Library
integration deferred to Plan 31, (b) real AdMob integration deferred,
(c) cosmetic visual application not implemented (R2-11 disables the
purchase button with "Coming Soon"), (d) no receipt verification, no
subscription renewal handling, no grace periods.

**Why it diverged from an "ideal" architecture:** Ideal would ship with
real SDKs from Plan 26. Project chose stub-first architecture: domain
interfaces (`BillingManager`, `RewardAdManager`) can be swapped for real
SDK implementations by changing only the `@Binds` in
`BillingModule`/`AdModule`. Rationale: Plan 26 wanted the full UI/UX
end-to-end validated without pulling in heavy SDKs or setting up Play
Console. The stub pattern keeps tests fast (no network, no ad fills) and
lets plan work proceed in parallel with Plan 31 (store publication).

**Alternatives likely considered:**
- *Ship with real SDKs from Plan 26* — rejected; too much release-gate
  coupling.
- *Feature-flag billing* — rejected; a stub is effectively a feature
  flag that also validates the swap point.
- *Adapter pattern with a single `Monetization` interface* — rejected;
  Billing and Ads have orthogonal concerns (receipt verification vs.
  ad fill rates).
- *In-memory cosmetic previews* — rejected because R2-11 disables
  purchases until the visual pipeline exists; a preview without buy
  would confuse players.

**Edge cases that shaped the design:**
- Stub billing → `StubBillingManager` simulates a 500 ms delay and
  always returns `PurchaseResult.Success`; credits Gems on purchase,
  flips flags for Ad Removal and Season Pass.
- Stub ads → `StubRewardAdManager` waits 1 s, returns `AdResult.Rewarded`.
- Ad-removal gate → all 3 ad placements check `profile.adRemoved`;
  once purchased, ad buttons disappear.
- Season Pass expiry → `TrackDailyLogin` + `StoreViewModel` + `LabsViewModel`
  all check `seasonPassExpiry > now` to avoid crediting after expiry
  (R09 added this in StoreViewModel).
- Cosmetic purchases disabled → R2-11 replaced buy button with
  disabled "Coming Soon" label; equip/unequip still works for owned
  items (e.g. if seeded).
- Post-round ads guard against replay double-tap → R09 added guards
  in `watchGemAd` / `watchPsAd`.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `StoreViewModelTest` (3 + 2 Season Pass expiry),
  `PurchaseGemPackTest` (2).
- Migrations: v7 added monetization fields on PlayerProfile +
  CosmeticEntity + CosmeticDao (Plan 26).
- Config: 5 billing SKUs; 3 ad placements; 7 cosmetics seeded on first
  launch.
- Docs: Plan 26, Plan 31, STATE.md known issues.

**Risks caused by the current shape:**
1. **Real SDK integration is a significant lift** — Google Play Billing
   Library v7 needs receipt verification, connection lifecycle handling,
   purchase acknowledgement, subscription renewal. Stub hides all of
   this complexity. Plan 31 work.
2. **Cosmetic purchase disabled** — players can browse but not buy;
   could read as a broken feature rather than "coming soon". R2-11
   intentional but worth revisiting.
3. **Season Pass daily bonus leak** (§18) — sensor-path credit doesn't
   pass Season Pass flags to `TrackDailyLogin`.
4. **No localisation for pricing** — `BillingProduct.kt` hardcodes USD
   prices; real SKUs will return localised prices from Play Store.
5. **Stub always-succeeds masks failure cases** — tests against stubs
   don't exercise purchase cancellation or ad failure paths.

---

## 20. Notifications & home widget

Four notification channels (step_counter, supply_drops, milestones,
reminders) + 2×2 home screen widget + 60s-throttled widget updates.

**Files / modules:**
- `service/StepNotificationManager.kt` — ongoing FG + minimal variant (R2-05)
- `service/SupplyDropNotificationManager.kt` — supply drops channel with deep-link
- `service/MilestoneNotificationManager.kt` — wave + step milestone alerts
- `service/SmartReminderManager.kt` — upgrade proximity reminders
- `data/NotificationPreferences.kt` — 4-toggle prefs
- `data/MilestoneNotificationPreferences.kt` — dedup prefs (R2-08)
- `service/StepWidgetProvider.kt` — AppWidgetProvider with SharedPrefs data
- `service/WidgetUpdateHelper.kt` — 60s throttle
- `res/layout/widget_step_counter.xml` + `res/xml/step_widget_info.xml`
- `presentation/settings/NotificationSettingsScreen.kt` — 4 toggles
- `data/sensor/DailyStepManager.kt` — triggers widget update + milestone notify

**Coverage: Fully (≈88%).** 4 channels with distinct PendingIntents,
widget rendering with real balance (R06), live step balance, 60s
throttle, 4-toggle settings, preference-aware FG notification
(R2-05), smart-reminder integration into StepSyncWorker. 12% gap: no
custom notification icons (system placeholders), no widget preview image,
no custom app icon.

**Why it diverged from an "ideal" architecture:** Ideal would use a
single `NotificationService` with a dispatch table. In practice, each
notifier is a dedicated class because they have distinct PendingIntent
targets, channel importances, and preference toggles. Pragmatic:
four notifiers keep each channel's behaviour self-contained.

**Alternatives likely considered:**
- *Glance widgets* — rejected; traditional AppWidgetProvider + RemoteViews
  has no Compose dependency and works in SharedPreferences pattern.
- *Single notification channel with categories* — rejected; Android 8+
  strongly favours per-channel controls.
- *Room-backed widget data* — rejected; Room can't be queried
  synchronously from `AppWidgetProvider.onUpdate`; SharedPrefs is
  simpler.
- *Custom notification icons* — deferred; system icons for v1.0 (noted
  in Plan 23 follow-ups).

**Edge cases that shaped the design:**
- FG minimal notification → R2-05 added `buildMinimalNotification` for
  when "Live Step Updates" is disabled; Android requires a visible
  notification for FG service.
- Widget throttle → 60s minimum between updates; prevents AppWidgetManager
  spam during rapid step ingestion.
- Widget click target → R06 changed click target from
  `android.R.id.background` to explicit `R.id.widget_root` because
  background doesn't receive click events on all launchers.
- Permission denied → `POST_NOTIFICATIONS` may be denied; notification
  posts silently fail.
- Smart reminder → fires at most once/day via `lastActiveAt` check (R09
  added `updateLastActiveAt(now)` in `MainActivity.onResume()`).
- Deep-link coverage → only 5 of 12 routes are handled (§21).

**Related tests, fixtures, migrations, config, docs:**
- Tests: `StepWidgetProviderTest` (3 Robolectric),
  `DeepLinkRoutingTest` (3).
- Config: 4 notification channels with distinct importance; widget refresh
  throttle 60s; smart reminder threshold 4 hours since last active.
- Docs: Plan 23, trace 11.

**Risks caused by the current shape:**
1. **No custom icons** — uses system placeholder; unprofessional in
   production.
2. **Widget SharedPrefs contract is stringly-typed** (Phase 5 "integration
   contracts") — typo in a key produces stale zeros with no error.
3. **Deep-link partial** (§21) — Store/Stats/Weapons/Cards/Economy/Settings
   notifications go to Home instead of their intended screen.
4. **FG notification opt-out requires always-on minimal** — trade-off
   documented, but some users may not realise they can't fully silence
   the service.

---
## 21. Navigation, deep-links, UX feedback

Single-Activity Compose NavHost with 12 routes, bottom navigation for 5
primary routes, `pendingNavigation` MutableStateFlow for deep-links, and a
Scaffold+SnackbarHost feedback pattern on purchase-heavy screens.

**Files / modules:**
- `presentation/MainActivity.kt` — single Activity, NavHost, permissions,
  `pendingNavigation: MutableStateFlow<String?>`, `onNewIntent` override
- `presentation/navigation/Screen.kt` — sealed with 12 routes
- `presentation/navigation/BottomNavBar.kt` — 5 primary items
- `presentation/{workshop,cards,labs,store}/*Screen.kt` — Scaffold + SnackbarHost
- `presentation/{workshop,cards,labs,store}/*UiState.kt` — `userMessage` + `isProcessing`
- `presentation/{workshop,cards,labs,store}/*ViewModel.kt` — `clearMessage()` + double-tap guard
- `presentation/HealthConnectPermissionActivity.kt` — HC rationale
- `AndroidManifest.xml` — Health Connect permission alias, rationale intent filter

**Coverage: Partial (≈80%).** 12 routes live, bottom nav, permissions flow,
cold/warm deep-link handling via `pendingNavigation`, accessibility
semantics on key buttons (R11), lifecycle-aware date refresh (R10),
Scaffold+Snackbar feedback on 4 screens, double-tap `isProcessing` guards.
20% gap: deep-link collector handles only 5 of 12 routes (Home, Workshop,
Battle, Missions, Supplies) — Store, Stats, Weapons, Cards, Economy,
Settings cannot be reached from notifications (Phase 8 §3).

**Why it diverged from an "ideal" architecture:** Ideal would be type-safe
deep-linking via Navigation Compose's typed routes. In practice,
`MainActivity.pendingNavigation` carries a string route matched by `when`;
only 5 routes have branches. Grew this way because each plan added the
routes it needed: Plan 19 added supplies, Plan 21 added missions, Plan 23
added workshop/battle deep-links. No single plan required all 12. Type-
safe routes would catch this at compile time; adopting them now would
require refactoring all NavHost entries.

**Alternatives likely considered:**
- *Compose Navigation typed routes* — didn't exist when Plan 06 shipped;
  retrofitting is a meaningful refactor.
- *Per-notification-type Activity* — rejected; single-Activity architecture
  is a cornerstone of the product shell.
- *Global navigation bus* — rejected; `pendingNavigation` MutableStateFlow
  is effectively a 1-slot bus already.
- *UX feedback via dialogs* — rejected; snackbars are less intrusive.

**Edge cases that shaped the design:**
- Cold start with deep-link extra → `onCreate` reads `navigate_to` from
  intent; first `LaunchedEffect(Unit)` pushes into `pendingNavigation`.
- Warm start via notification → `onNewIntent` reads the extra and pushes
  into the same flow. Collector navigates, then nulls the flow.
- Double-tap race → VM early-returns if `_processing.value`; mutex-free
  because `MutableStateFlow` writes are atomic.
- Snackbar dismissal → `LaunchedEffect(state.userMessage)` shows and
  calls `clearMessage()` on display.
- Permission denied mid-flow → HC permission denial skips HC features
  gracefully (service still runs; cross-validation skipped).
- Date refresh on ON_RESUME → Home and Stats VMs re-emit after date
  advance (R10).
- Lifecycle.ON_PAUSE in Battle → auto-pause.

**Related tests, fixtures, migrations, config, docs:**
- Tests: `DeepLinkRoutingTest` (3: supplies, workshop, null),
  `UserFeedbackTest` (3: workshop userMessage flow),
  `CurrencyGuardTest` (4: currency clamps).
- Config: 12 routes in `Screen.kt`; 5 items in `BottomNavBar`; 4 `Scaffold`
  screens; `MainActivity.pendingNavigation` default null.
- Docs: trace 10 (supply drop → deep-link).

**Risks caused by the current shape:**
1. **Deep-link silent miss for 7 routes** — a future notification with
   `navigate_to="store"` opens MainActivity but doesn't navigate; user
   sees Home and loses the context. No test asserts coverage.
2. **Stringly-typed route extra** — no schema, no compile-time check; a
   typo (`"workshop "` with trailing space) silently fails.
3. **Double-tap guard pattern is copy-pasted** — 4 VMs each have their
   own `_processing` MutableStateFlow. Any missed VM opens a race.
4. **Snackbar wrapping inconsistent** — 4 screens have it, 8 screens
   don't. Home/Battle/Missions/Stats/etc. rely on inline feedback.
5. **`MainActivity.PlaceholderScreen` dead code** (Phase 1/trace 05) —
   unused @Composable still present; low risk but worth removing.

---

## 22. Dependency injection & layering

Hilt with KSP, 6 `@InstallIn(SingletonComponent::class)` modules, use
cases as plain Kotlin (no Hilt annotations), domain layer with zero
Android imports.

**Files / modules:**
- `StepsOfBabylonApp.kt` — `@HiltAndroidApp`, `Configuration.Provider` for HiltWorkerFactory
- `di/DatabaseModule.kt` — Room DB + 12 DAO providers
- `di/RepositoryModule.kt` — 8 interface→impl `@Binds`
- `di/StepModule.kt` — SensorManager provider
- `di/HealthConnectModule.kt` — organisational placeholder (empty)
- `di/BillingModule.kt` — BillingManager stub `@Binds`
- `di/AdModule.kt` — RewardAdManager stub `@Binds`
- `service/StepSyncWorker.kt` — `@HiltWorker`
- All `*ViewModel.kt` — `@HiltViewModel` + `@Inject constructor`
- `app/build.gradle.kts` — KSP plugin, Hilt + Hilt-Work compilers

**Coverage: Fully (≈90%).** All DI wiring works, use cases are plain
Kotlin (pattern reaffirmed by ADR-0003), KSP (never kapt), HiltWorkerFactory
configured, 8 repositories wired. 10% gap: `HealthConnectModule` is an
organisational placeholder that holds no bindings (Phase 8 §3 "thin
modules"), `DailyStepManager` has 12 constructor dependencies (Phase 8 §3
"fat modules"), forbidden-direction imports at 12 call sites (Phase 8 §8).

**Why it diverged from an "ideal" architecture:** Ideal would split the
monolithic `:app` module into `:domain`, `:data`, `:presentation` Gradle
modules with Hilt enforcing layer boundaries at compile time. In practice,
the app is a single Gradle module with layer boundaries enforced only by
convention (no static analysis, no lint rule). Compiles faster but lets
`domain/` accidentally import from `data.local.*Dao` (6 sites) and
`presentation/` from data types (6 sites) — 12 forbidden-direction imports
enumerated in Phase 8 §8.

**Alternatives likely considered:**
- *Multi-module split* — rejected in Plan 01; overhead for a solo
  dev not worth the boundary guarantee.
- *Koin instead of Hilt* — rejected because Hilt is first-party
  (ships with Jetpack).
- *Manual DI* — rejected; 30+ bindings would be error-prone.
- *Use cases as `@ViewModelScoped`* — rejected; they're cheap to
  construct inline and carry no state.
- *kapt instead of KSP* — explicitly forbidden in `CONSTRAINTS.md` ("never
  do" list).

**Edge cases that shaped the design:**
- DAO-to-VM direct access → 6 of 12 ViewModels construct use cases
  inline using DAOs from Hilt providers. Intentional pattern (ADR-0003);
  keeps use cases Hilt-free.
- WorkerFactory → `HiltWorkerFactory` injected via
  `Configuration.Provider`; WorkManager's default init disabled in the
  manifest.
- `@Binds` vs `@Provides` → interfaces use `@Binds` (cheaper), concrete
  types (Room, SensorManager) use `@Provides`.
- Test substitution → fakes constructed directly in test code (no
  `@TestInstallIn` overrides); use cases accept fakes via constructor
  parameters.
- Empty `HealthConnectModule` → kept as an organisational placeholder so
  future HC bindings have a home; Phase 8 flags this as a "thin module"
  risk.

**Related tests, fixtures, migrations, config, docs:**
- Tests: Hilt bindings not tested directly; use cases and ViewModels test
  with `Fake*` classes.
- Config: 6 modules, all at `SingletonComponent` scope.
- Docs: `.kiro/steering/lib-hilt.md`, `CONSTRAINTS.md` "never do" list.

**Risks caused by the current shape:**
1. **No compile-time layer enforcement** — single `:app` module allows
   `domain/` to import `data/` freely. 12 known forbidden-direction
   imports (Phase 8 §8).
2. **Fat `DailyStepManager` constructor** (12 deps; Phase 8 §3) —
   refactor magnet; adding a 13th dep feels expensive.
3. **`PlayerRepository` fan-out** (23 methods; Phase 8 §3) — every new
   domain need widens the interface; touching it cascades to every
   consumer.
4. **Hilt-Work lifecycle edge** — if `HiltWorkerFactory` initialisation
   fails (unlikely), all workers silently fail.
5. **Empty DI modules are a maintenance smell** — `HealthConnectModule`
   is organisational-only; future reviewers may assume it's missing
   bindings.

---

## 23. Reproducibility contracts

Time, randomness, IDs, environment: how (and where) the code accepts
injected substitutes for non-deterministic inputs.

**Files / modules:**
- `domain/usecase/CalculateDamage.kt` — `random: Random = Random` default param
- `domain/usecase/OpenCardPack.kt` — `random: Random = Random` default param
- `domain/usecase/GenerateSupplyDrop.kt` — `random: Random = Random` default param
- `domain/usecase/GenerateDailyMissions.kt` — `Random(todayDate.hashCode())` deterministic
- `domain/usecase/AwardBattleSteps.kt` — `today: String = LocalDate.now().toString()` default param
- `domain/usecase/TrackDailyLogin.kt` — same default-param time pattern
- `domain/usecase/StartResearch.kt` / `CompleteResearch.kt` — `now: Long = System.currentTimeMillis()` default
- `presentation/battle/GameLoopThread.kt` — `System.nanoTime()` direct (accepted per intro2codebase §5)
- `presentation/missions/MissionsViewModel.kt` — 1 s ticker polling wall-clock
- `service/StepSyncWorker.kt` — worker's wall-clock
- `data/sensor/DailyStepManager.kt` — `todayDate()` + `System.currentTimeMillis()` direct

**Coverage: Partial (≈55%).** Three stochastic use cases inject `Random`
(seedable for tests); one uses a date-seeded `Random(todayDate.hashCode())`
(deterministic cross-device). Several use cases accept `today` / `now`
as default params for testability. 45% gap: 53 direct
`System.currentTimeMillis()` / `LocalDate.now()` calls across 33 files
(Phase 4 item 1) without default-param scaffolding; no centralised
`TimeProvider`; ticker loops in LabsViewModel and MissionsViewModel can't
be tested directly (must test at use-case level). No UUID/ID generation
(Room auto-generates IDs).

**Why it diverged from an "ideal" architecture:** Ideal would have a
`TimeProvider` injected via Hilt that wraps `now()`/`today()`/`nanoTime()`.
In practice, each time-dependent function solves its own testability via
default-parameter arguments (e.g. `fun invoke(today: String =
LocalDate.now().toString()): Result`). Works for use cases but
leaves ticker loops and services reading wall-clock directly. Phase 4
item 1 is the explicit proposal to upgrade to `TimeProvider`.

**Alternatives likely considered:**
- *`Clock` object* (java.time.Clock) — could be injected; rejected
  because default-param pattern is lighter and already established.
- *Kotlinx Datetime* — not adopted; Java 8 `LocalDate`/`Instant` are
  sufficient.
- *Global `TimeProvider` with interceptor* — proposed in Phase 4; not
  adopted because of incremental scope creep risk.
- *Cryptographic random for anti-cheat* — rejected; anti-cheat doesn't
  need unguessable RNG.

**Edge cases that shaped the design:**
- Tests pass fixed `today` / `now` → every time-sensitive use case has
  a default param so callers don't change; tests override.
- Ticker loops in VMs cannot be seeded → `LabsViewModel` /
  `MissionsViewModel` have `while(true) { delay(1000) }` tickers.
  Direct VM tests hang; testing is done at use-case level (Plan 29
  decision).
- Deterministic daily missions → `Random(todayDate.hashCode())` ensures
  every device generates the same trio per date.
- No UUID generation → every entity uses auto-generated Room long IDs;
  no cross-device IDs needed because no backend.
- No environment-dependent behaviour → BuildConfig is not consulted
  beyond `BuildConfig.DEBUG` implicitly via R8 config.

**Related tests, fixtures, migrations, config, docs:**
- Tests: Use case tests pass fixed dates, `Random(0)` for deterministic
  rolls, `StandardTestDispatcher` for coroutine timing.
- Config: Default params on use case signatures; no central `TimeProvider`.
- Docs: `devdocs/archaeology/5_things_or_not.md` item 1, Phase 8 §5
  "seamed randomness partial".

**Risks caused by the current shape:**
1. **53 direct wall-clock calls** (Phase 4 item 1) — any ticker/time-
   dependent feature added without default params inherits this gap.
2. **Ticker-loop VMs untestable directly** — Plan 29 decision to test at
   use-case level is correct but leaves the VM's `combine` wiring
   unverified.
3. **Date-string comparison for day rollover** — `todayDate()` returns
   `LocalDate.now().toString()` (ISO-8601); any timezone-sensitive
   comparison is implicit in `LocalDate.now()`'s default zone.
4. **No anti-cheat RNG** — if a future feature needs unpredictable
   supply-drop timing, current `Random` is predictable from seed.
5. **Cross-device determinism only for daily missions** — other
   stochastic features may produce different drops/crit rolls across
   devices on the same day, which could be a concern if leaderboards
   ever ship.

---

## 24. Testing strategy

JVM-only test suite (412 tests) with JUnit 5 + kotlinx-coroutines-test +
Robolectric for Android-framework dependencies, in-memory Fake
repositories, no instrumented tests.

**Files / modules:**
- `app/src/test/java/com/whitefang/stepsofbabylon/` — all tests
- `fakes/Fake*Repository.kt` + `Fake*Dao.kt` + `FakeBillingManager.kt` +
  `FakeRewardAdManager.kt` (15 fakes total)
- `domain/usecase/*Test.kt` — all 32 use cases covered
- `domain/model/*Test.kt` — invariant tests (Tier, Biome, Loadouts,
  UpgradeType, EnemyType, Milestone, DailyMissionType, BattleConditionEffects)
- `presentation/{home,workshop,cards,labs,weapons,supplies,economy,missions,stats,store,battle}/*Test.kt` — VM tests
- `data/sensor/*Test.kt` — 5 sensor-layer tests
- `data/healthconnect/*Test.kt` — 2 HC tests
- `data/local/RoomSchemaTest.kt` — Robolectric schema round-trip (3)
- `data/integration/EscrowLifecycleTest.kt` — integration (2)
- `balance/*Test.kt` — 39 balance regression tests
- `presentation/battle/effects/*Test.kt` — ParticlePool, ScreenShake, DeathEffect
- `presentation/ux/CurrencyGuardTest.kt` + `UserFeedbackTest.kt` — R10 regressions
- `presentation/DeepLinkRoutingTest.kt` — intent-extra extraction
- `service/StepWidgetProviderTest.kt` — Robolectric widget prefs

**Coverage: Partial (≈70%).** 412 JVM tests cover all use cases, domain
model invariants, balance curves, anti-cheat validators, ViewModels for
most screens, escrow lifecycle, schema round-trip, widget prefs,
deep-link extraction. 30% gap: no instrumented (`androidTest`) tests —
no Room migration tests on real SQLite, no Compose UI tests, no
foreground-service/widget/boot-receiver integration tests on device
(explicitly noted in README + Plan 29 as deferred).

**Why it diverged from an "ideal" architecture:** Ideal would be
instrumented tests for Android-framework behaviours (Room on device,
Compose UI, widget updates) plus JVM tests for pure logic. In practice,
the project chose JVM-only to keep the feedback loop fast (44 s vs
several minutes for emulator-based tests). Robolectric covers the "I need
Android types in tests" cases (Room in-memory, widget SharedPrefs). No
CI, so test-run cost is felt directly by the developer.

**Alternatives likely considered:**
- *Instrumented test suite* — deferred to post-release; README + Plan
  29 explicit.
- *Turbine for Flow testing* — not adopted; coroutines-test's
  `runTest { }` + `StandardTestDispatcher` suffices.
- *Kotest* — rejected in favour of JUnit 5 (more familiar, similar
  features).
- *Property-based testing (Kotest PBT)* — considered for balance tests
  but explicit value-based assertions were preferred.
- *Mutation testing (Pitest)* — not adopted.

**Edge cases that shaped the design:**
- Ticker-loop VMs hang direct tests → Plan 29 tested at use-case level
  for LabsViewModel / MissionsViewModel.
- Robolectric for Android types → `@Config` with
  `unitTests.isIncludeAndroidResources = true` (R12); Room in-memory,
  SharedPrefs, AppWidgetManager all available.
- Coroutine timing → `StandardTestDispatcher` used over `Unconfined` to
  prevent infinite tickers from running.
- Injected Random seeds → `Random(0)` everywhere for deterministic
  crit / pack / drop rolls.
- Android `Log` in tests → `unitTests.isReturnDefaultValues = true`
  returns 0 for Log calls without Robolectric.
- Balance tests as regression → `balance/*Test.kt` validates GDD
  timelines; any tuning that breaks timelines fails the build.

**Related tests, fixtures, migrations, config, docs:**
- Tests: 412 in total (grep-counted in STATE.md); breakdown: 32 use
  cases + ~15 domain models + ~15 VMs + 39 balance + ~30 sensor/HC/
  effects + Robolectric / integration tests.
- Fixtures: 15 Fake classes with MutableStateFlow backing;
  `FakePlayerRepository` has most coverage.
- Config: JUnit 5 platform; kotlinx-coroutines-test 1.10.1; Robolectric
  4.14.1; AndroidX Test Core 1.6.1; mockito-kotlin 5.4.0.
- Docs: Plan 29, README test section.

**Risks caused by the current shape:**
1. **No instrumented tests** — Room DAO behaviour on real SQLite is
   untested (e.g. SQLCipher-specific edge cases); Compose UI behaviour
   is untested (e.g. state preservation across config change).
2. **Ticker-loop VMs only tested at use-case level** — `combine` wiring
   and `stateIn` caching behaviour in LabsViewModel / MissionsViewModel
   are unverified.
3. **No migration-rehearsal tests** (§5) — only one migration exists;
   future migrations ship without test coverage.
4. **No CI means tests are optional in practice** — PR discipline only;
   a hasty commit can break tests silently until someone runs them.
5. **Stub SDKs short-circuit tests** — Billing / Ads stubs always
   succeed; real-world failure paths aren't exercised.

---

## 25. Release & security hardening

Release build pipeline: R8 minify + resource shrinking, opt-in keystore
signing, SQLCipher encryption, backup disabled, cleartext blocked,
Google Play Health Connect + health foreground service, Play Store
listing + privacy policy text.

**Files / modules:**
- `app/build.gradle.kts` — version 1.0.0, release build type, signing
  config (opt-in via `keystore.properties`)
- `app/proguard-rules.pro` — keep rules for HC SDK, SensorEventListener,
  ListenableWorker, Room entity fields, org.json
- `AndroidManifest.xml` — permissions (7), allowBackup="false",
  FG service type health, HC permission alias
- `res/xml/network_security_config.xml` — cleartext traffic denied
- `data/local/DatabaseKeyManager.kt` — SQLCipher passphrase via Keystore
- `.gitignore` — keystore files excluded
- `docs/release/signing-guide.md` — keystore generation + Play App Signing
- `docs/release/privacy-policy.md` — full policy text (no URL yet)
- `docs/release/play-store-listing.md` — short/full descriptions
- `docs/release/release-checklist.md` — manual release gate

**Coverage: Partial (≈75%).** All technical hardening shipped (R8,
resource shrinking, ProGuard keep rules, SQLCipher + Keystore, cleartext
block, backup disabled, signed release APK builds at 26 MB, 412 tests
green). 25% gap: (a) Play Console setup + store listing upload are
manual Plan 31 work, (b) no custom app icon, (c) privacy policy hosted
URL pending, (d) no visual store assets (screenshots, feature graphic,
promo video).

**Why it diverged from an "ideal" architecture:** Ideal would be a full
CI/CD pipeline with automated Play Store upload and staged rollout. In
practice, all release gates are manual (`./run-gradle.sh test` +
`assembleRelease`). This is a deliberate v1.0 choice: no account system,
no server, no analytics, no Firebase means no infrastructure to
automate; manual release lets the solo dev ship when confident.

**Alternatives likely considered:**
- *GitHub Actions / CircleCI* — deferred; release cadence is low and
  manual gates work.
- *Fastlane* — deferred; Play Console upload will be manual initially.
- *Staged rollout automation* — deferred; Plan 31 will configure Play
  Console directly.
- *Enforced `keystore.properties`* — rejected; opt-in pattern lets
  debug builds run without a keystore.
- *Firebase App Distribution* — rejected (privacy-by-default).

**Edge cases that shaped the design:**
- Keystore missing → release build fails with helpful error;
  `docs/release/signing-guide.md` covers generation.
- R8 breaks reflection → ProGuard keep rules cover known reflection
  sites (Health Connect, SensorEventListener, ListenableWorker, Room
  entities, org.json).
- Decrypt failure on restore → `DatabaseKeyManager` wipes prefs blob
  and regenerates; **does not wipe DB file** (trace 12 §9 known gap).
- Backup disabled means reinstall → fresh start (no cloud save by
  design).
- Cleartext blocked → no outbound network in v1.0, network security
  config guards future regressions.
- HC permission rationale → Activity with intent filter
  `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` + Android 14
  `VIEW_PERMISSION_USAGE` alias.

**Related tests, fixtures, migrations, config, docs:**
- Tests: no release-specific tests; release APK verified by manual
  `assembleRelease` run.
- Config: `gradle/libs.versions.toml` pins all versions; R8 mapping
  output in `app/build/outputs/mapping/`; schema exports.
- Docs: Plan 30, Plan 31, `docs/release/` (5 files).

**Risks caused by the current shape:**
1. **No custom app icon** — system placeholder; unprofessional in
   production (STATE.md known issue).
2. **Decrypt recovery leaves zombie DB** (§5, trace 12 §9) — latent
   crash-on-launch after device restore.
3. **Manual release gate** — a forgotten step (e.g. bumping
   versionCode) silently ships a duplicate build.
4. **Privacy policy URL pending** — Google Play requires https-
   reachable policy URL; `docs/release/privacy-policy.md` is text
   only.
5. **No CI** (§24) — regression coverage depends on developer
   discipline.
6. **No crash reporting** (by design) — production crashes visible only
   via Play Console's ANR/crash dashboards.
7. **Real SDKs deferred** (§19) — stub billing/ads mean revenue/ad
   SDKs must be swapped in + verified before first-party live
   economy behaviour is known.

---

## Appendix A: cross-concept risks

These risks span multiple concepts and are listed separately because
they cut across the map rather than belonging to a single entry.

1. **Zero `@Transaction` wrapping in `app/src/main`** (Phase 4 item 2).
   Affected concepts: §2 HC cross-validation, §3 Battle Step Rewards, §5
   Persistence, §16 Supply drops (ClaimSupplyDrop), §17 Milestones
   (ClaimMilestone). Partial-failure between sequential DAO writes
   produces silent state divergence. Highest-payback single fix.

2. **Direct wall-clock calls at 53 sites across 33 files** (Phase 4 item
   1). Affected concepts: §1 Step ingestion, §3 Battle Step Rewards, §11
   Labs, §17 Missions, §18 Weekly/login, §23 Reproducibility. Proposal:
   `TimeProvider` + `@Clock` annotation. Blocks deterministic testing for
   ticker-loop VMs and midnight-edge behaviours.

3. **`PlayerRepository` is a 23-method universal dependency** (Phase 8
   §3). Affected concepts: §4 Currencies, §10 Workshop, §11 Labs, §12
   Cards, §13 UWs, §15 Tiers, §18 Weekly/login. Every new domain need
   widens the interface; touching it cascades to every consumer.

4. **Mission progress duplication at 5 sites** (Phase 8 §3). Affected
   concepts: §1 Step ingestion, §10 Workshop, §11 Labs, §17 Missions,
   §6 Battle renderer. Proposal: extract `MissionProgressTracker`. Easy
   to forget to bump progress in a new action handler.

5. **Deep-link coverage partial (5 of 12 routes)** (Phase 8 §3, trace 10
   §8). Affected concepts: §17 Missions notifications, §19 Store
   notifications, §20 Notifications + widget, §21 Navigation. A future
   notification for Store/Stats/Weapons/Cards/Economy/Settings silently
   routes to Home.

6. **Loadout caps enforced only in ViewModel, not DAO** (Phase 8 §5).
   Affected concepts: §12 Cards, §13 UWs. Any direct DAO path can violate
   max-3.

7. **Fat modules**: `DailyStepManager` (12 deps, 5-stage follow-on),
   `GameEngine` (2 snapshot vars, 6 inline UW effects), `HomeViewModel`
   (5-flow combine) (Phase 8 §3). Each is a refactor magnet.

8. **Three overlapping reward vocabularies** (`Currency`,
   `SupplyDropReward`, `MilestoneReward`) (Phase 8 §4). Affected concepts:
   §4 Currency, §16 Supply drops, §17 Milestones. A single `Reward`
   sealed hierarchy would simplify, but ripple is wide.

9. **Cosmetic system is data-only; no renderer hook** (Phase 5 missing
   concept). Affected concepts: §17 Milestones (cosmetic rewards are
   no-ops), §19 Monetization (R2-11 disabled purchase buttons). Visual
   pipeline is the gate.

10. **No CI, no instrumented tests, manual release gate** (§24, §25).
    Release discipline depends on a single developer remembering to
    run `test` + `assembleRelease` before shipping.

11. **Stub billing + stub ads always succeed** (§19, §24). Real failure
    paths (purchase cancelled, ad failed to load, receipt verification
    rejected) are not exercised by any test.

12. **DB decrypt-failure recovery leaves zombie DB file** (§5, §25,
    trace 12 §9). Device restore produces unreadable DB; next launch
    crashes. Fixable by wiping DB file alongside passphrase blob.

---

## Appendix B: coverage roll-up

| # | Concept | Coverage | Key gap |
|---|---|---|---|
| 1 | Step ingestion pipeline | Fully (≈90%) | Fat orchestrator, no `@Transaction` |
| 2 | HC cross-validation | Fully (≈85%) | Non-atomic escrow, invisible to player |
| 3 | Battle Step Rewards | Fully (≈95%) | Non-atomic credit, cap invisibility |
| 4 | Currency model | Fully (≈95%) | 3 overlapping reward vocabularies |
| 5 | Persistence: Room + SQLCipher | Fully (≈85%) | No `@Transaction`, zombie-DB crash |
| 6 | Battle renderer | Fully (≈90%) | Placeholder audio, no cosmetic hook |
| 7 | Combat formulas | Fully (≈95%) | Hidden upgrade enum entries |
| 8 | Wave system & enemies | Fully (≈95%) | No boss-priority targeting |
| 9 | Biome progression | Fully (≈95%) | No tier 11+ visual variation |
| 10 | Workshop upgrades | Fully (≈95%) | Hidden entries in enum |
| 11 | Labs research | Fully (≈90%) | No closed-app completion notification |
| 12 | Cards system | Fully (≈92%) | Max-3 loadout cap VM-only |
| 13 | Ultimate Weapons | Fully (≈92%) | Implicit stat-snapshot chaining |
| 14 | Step Overdrive | Fully (≈90%) | Navigation-interrupt doesn't expire |
| 15 | Tier system | Fully (≈95%) | No tier preview UI |
| 16 | Supply drops | Partial (≈75%) | `STEP_BURST` orphan enum |
| 17 | Milestones & missions | Partial (≈80%) | Cosmetic rewards no-op, 5-site duplication |
| 18 | Weekly & login | Fully (≈88%) | Season Pass bonus leaks on sensor path |
| 19 | Monetization | Skeleton (≈45%) | Real SDKs deferred, cosmetic visuals missing |
| 20 | Notifications & widget | Fully (≈88%) | No custom icons, deep-link partial |
| 21 | Navigation & UX feedback | Partial (≈80%) | 7 deep-link routes unhandled |
| 22 | DI & layering | Fully (≈90%) | No compile-time layer enforcement |
| 23 | Reproducibility contracts | Partial (≈55%) | 53 direct wall-clock calls |
| 24 | Testing strategy | Partial (≈70%) | No instrumented tests, no CI |
| 25 | Release & security | Partial (≈75%) | Manual gate, Play Console pending |

**Aggregate posture:** the **core gameplay loop** (concepts 1–18) is at
~90%+ coverage with the explicit gaps enumerated. The **platform &
delivery layer** (concepts 19–25) is the release blocker — monetization
real-SDK integration, release polish, and Play Console setup all sit in
Plan 31. The cross-concept risks (Appendix A) are the highest-leverage
work items if the project were to harden before release; several are
already proposed in Phase 4 (`5_things_or_not.md`) and Phase 8
(`module_discovery.md`).

---

*End of Phase 9 concept mappings.*
