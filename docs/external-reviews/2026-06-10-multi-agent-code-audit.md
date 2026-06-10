# Steps of Babylon — Reachability-Aware Code Audit

**Date:** 2026-06-10  
**Scope:** Full repository — 255 main Kotlin files (~17,600 LOC), 128 JVM test files, 4 instrumented.  
**Method:** Orchestrated multi-agent audit. 8 specialist finders (Correctness, Concurrency, Data/Room, Sensor/Anti-cheat, Security, Async, Performance, Supply-chain) fanned out over the codebase after a shared reconnaissance pass. Each of the 50 raw findings was then handed to an independent **adversarial verifier** instructed to *disprove* it by reading the actual code and checking for upstream guards, existing tests, and reachability. The single High-severity finding was additionally re-verified at source by hand.  
**Outcome:** 50 raw findings → **45 confirmed, 5 rejected** (false positives). After adversarial severity adjustment: **1 High · 14 Medium · 30 Low**. Nine findings were downgraded from the finders' original severity by the verifiers.

> This file is a historical, point-in-time artifact (per the CLAUDE.md docs convention). It is not maintained after authoring; tracked follow-up lives in GitHub issues cross-referenced below.

---

## Risk assessment: MEDIUM

A mature, heavily-tested (867 JVM tests), security-conscious codebase — SQLCipher-encrypted DB, atomic guarded DAOs for most spends, network cleartext blocked, secrets correctly gitignored, no hardcoded keys. Residual risk concentrates in **two structural seams**: (1) the battle engine's thread-safety model, where `GameEngine.update()` runs unlocked against a shared mutable `entities` list that the main thread also mutates — the one High finding is a reachable in-game **crash**; and (2) a handful of economy spend/claim paths that did not receive the atomic-guard treatment the rest of the code has. None are remotely exploitable (offline single-player, no server backend), so no Critical. The crash (#1) and the GOLDEN upgrade-loss (#2) are the most player-visible.

### Hidden-bug hotspots
1. **`GameEngine.kt` (1055 lines) + the `entities` list** — the central thread-safety fault line. Findings #1, #2, #11, #15 originate here; the unsynchronized `update()`-vs-main-thread-mutation pattern likely has siblings beyond ORBS.
2. **`DailyStepManager.kt`** — a mutable `@Singleton` on the currency-minting boundary driven by two concurrent producers (service + worker). Findings #3, #6, #12, plus day-rollover and timezone findings cluster here.
3. **Economy use-case + `PlayerRepositoryImpl` spend layer** — most spends were hardened with atomic guards, but the audit found the misses (Steps #4, Gems/PS return-value #5, claims #9/#10). The recurring shape is *"guarded DAO exists, caller ignores its result"* — worth a systematic sweep of every spend/grant pair.

### Confidence statement
Estimated false-positive rate among the 45 confirmed: **~10–15%**, concentrated in the Low tier (several Low concurrency findings on benign `var lastUpdateMs` fields). The Medium-and-above tier is high-confidence — each cites exact code opened by a finder and a verifier. **Highest-value next step:** write the three concurrency stress harnesses (#1 `entities` race, #6 `DailyStepManager` ceiling race, #15 re-init race) as JVM tests that fail today, then run the suite under a stress loop to surface remaining `entities`-mutation siblings.


## GitHub issue tracking

The Medium-and-above findings were filed as bug issues on 2026-06-10 (root-cause deduplicated); the 30 Low findings are tracked in a single consolidated issue.

| Issue | Covers (report #) | Severity label |
|---|---|---|
| [#118](../../issues/118) | #1 + #15 — cross-thread `entities` mutation (crash) | severity:major |
| [#119](../../issues/119) | #2 — GOLDEN_ZIGGURAT snapshot loses upgrades | severity:major |
| [#120](../../issues/120) | #6 + #12 — DailyStepManager singleton race | severity:major |
| [#121](../../issues/121) | #3 — daily_step_record lost-update | severity:minor |
| [#122](../../issues/122) | #4 + #5 + #9 + #10 — economy spend/claim atomicity | severity:major |
| [#123](../../issues/123) | #7 — mid-day reboot loses steps | severity:major |
| [#124](../../issues/124) | #8 — no purchase signature verification | severity:minor |
| [#125](../../issues/125) | #11 — getAliveEnemies per-frame allocation | severity:minor |
| [#126](../../issues/126) | #13 — game-loop spiral of death | severity:minor |
| [#127](../../issues/127) | #14 — duplicate daily missions | severity:minor |
| [#128](../../issues/128) | Tracker for all 30 Low findings (#16–#45 excl. those above) | severity:minor |

Cross-referenced epics: #22 (anti-exploit), #26 (performance), #21 (test coverage).

---

## Summary table

| # | Sev | Conf | Surface | Issue |
|---|-----|------|---------|-------|
| 1 | High | High | battle-engine | Cross-thread shared-list mutation (data race / ConcurrentModificationException) |
| 2 | Medium | High | battle-engine | Stale snapshot loses in-round upgrades (state-machine restore bug) |
| 3 | Medium | High | data-room | Concurrency / lost update (data integrity) |
| 4 | Medium | High | data-room | TOCTOU economy / silent currency clamp |
| 5 | Medium | High | data-room | TOCTOU / item granted despite failed deduction |
| 6 | Medium | High | sensor-anticheat | Concurrency / data race on shared mutable state |
| 7 | Medium | High | sensor-anticheat | Reboot baseline staleness — under/over-credit |
| 8 | Medium | High | security | Missing purchase signature verification (client-side IAP trust) |
| 9 | Medium | High | concurrency-async | Check-then-act double-credit (economy exploit via concurrent claims) |
| 10 | Medium | High | concurrency-async | Check-then-act double-credit (daily mission claim) |
| 11 | Medium | High | performance | GC churn + redundant work — getAliveEnemies allocates two lists per call, called many times per frame |
| 12 | Medium | High | supplychain-infra-debt | Data race / concurrency |
| 13 | Medium | Medium | battle-engine | Unbounded fixed-timestep catch-up (spiral of death) amplified by speed multiplier |
| 14 | Medium | Medium | data-room | Duplicate-row insert (no unique constraint + TOCTOU) |
| 15 | Medium | Medium | concurrency-async | Engine init/update cross-thread structural mutation of shared list |
| 16 | Low | High | battle-engine | Off-by-one / duplicate-trigger on round start |
| 17 | Low | High | battle-engine | Lifesteal granted on fully armor-absorbed hits (economy/correctness) |
| 18 | Low | High | battle-engine | Per-frame list allocations in hot UW loop |
| 19 | Low | High | domain-economy | TOCTOU / atomic-guard return value ignored — item/level granted regardless of whether the currency was actually spent |
| 20 | Low | High | domain-economy | Overloaded field — CARD_COPY rewardAmount is a card-type index at generation/claim time but rendered as a quantity in the UI |
| 21 | Low | High | domain-economy | Description/effect mismatch — IRON_SKIN labeled as a percentage but applied as flat defense |
| 22 | Low | High | domain-economy | Dead computation masking intended threshold logic in supply-drop check count |
| 23 | Low | High | data-room | Missing migration runtime test (data-transform unverified) |
| 24 | Low | High | sensor-anticheat | Anti-cheat rate-limit bypass via clock manipulation / batched timestamps |
| 25 | Low | High | security | Replay across reinstall — purchaseToken idempotency is local-only |
| 26 | Low | High | security | Reward-ad grant trust without Server-Side Verification (SSV) |
| 27 | Low | High | concurrency-async | Non-atomic compound update on @Volatile field across threads |
| 28 | Low | High | performance | GC churn — per-frame heap allocation in 60fps loop |
| 29 | Low | High | performance | Flow re-query storm — no distinctUntilChanged on shared single-row profile Flow |
| 30 | Low | High | performance | Redundant recomputation — LocalDate.parse in nested filter on every Flow emission |
| 31 | Low | High | performance | GC churn — per-frame Paint allocation in render path |
| 32 | Low | High | supplychain-infra-debt | Concurrency / visibility (non-volatile cross-thread field) |
| 33 | Low | High | supplychain-infra-debt | Pre-release (alpha) dependency in a shipping app |
| 34 | Low | Medium | battle-engine | Two game-loop threads on one engine after join timeout (data race) |
| 35 | Low | Medium | domain-economy | Crash on empty rarity bucket — unguarded random.nextInt over a filtered list that can be empty |
| 36 | Low | Medium | data-room | TOCTOU loadout-cap bypass |
| 37 | Low | Medium | sensor-anticheat | Day-boundary / timezone inconsistency |
| 38 | Low | Medium | sensor-anticheat | State-machine off-by-one in cross-validation offense levels |
| 39 | Low | Medium | sensor-anticheat | Negative-discrepancy / sensor-undercount never reconciled |
| 40 | Low | Medium | sensor-anticheat | Lost reset of in-memory baseline on day rollover during active ingestion |
| 41 | Low | Medium | security | Exported StepWidgetProvider trusts unencrypted SharedPreferences shown in widget |
| 42 | Low | Medium | concurrency-async | Data race / ConcurrentModificationException |
| 43 | Low | Medium | supplychain-infra-debt | Swallowed error producing wrong user-visible value |
| 44 | Low | Low | data-room | Concurrency / silently-dropped insert losing a card copy |
| 45 | Low | Low | security | SharedPreferences async flush can lose DB passphrase across crash (key-recovery wipe abuse) |

---

## Findings (ranked by severity × confidence)

### 1. 🔴 HIGH / Confidence: High — Cross-thread shared-list mutation (data race / ConcurrentModificationException)

- **Category:** Bugs  
- **Surface:** battle-engine  
- **Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/BattleViewModel.kt`  
- **Lines:** GameEngine.kt:353-367 (applyStats), 363-366 (entities.removeAll / spawnOrbs), 332-334; BattleViewModel.kt:504-525 (purchaseInRoundUpgrade -> updateZigguratStats)  

**Description.** An in-round ORBS upgrade purchase mutates the engine's shared `entities` list from the UI/main thread while the dedicated GameLoopThread is concurrently iterating that same list. `BattleViewModel.purchaseInRoundUpgrade` runs on the coroutine main dispatcher and calls `eng.updateZigguratStats(resolvedStats)` -> `applyStats(newStats)`. When `newStats.orbCount != oldStats.orbCount` (true for any in-round ORBS purchase, and ORBS has an in-round equivalent — `ResolveStats` reads `UpgradeType.ORBS`), `applyStats` executes `entities.removeAll { it is OrbEntity }` then `spawnOrbs()` (which calls `entities.add(...)`). Meanwhile the GameLoopThread is inside `update()` iterating `entities` via `simulation.tickEntities(entities, ...)`, the projectile-trail `for (e in entities)`, `entities.removeAll { !it.isAlive }`, or `entities.forEach { it.render(canvas) }`. `entities` is a plain `mutableListOf` with no synchronization. Structural modification of an ArrayList from one thread while another iterates it throws ConcurrentModificationException (crash) or silently corrupts iteration. This is a genuine two-writer/one-reader race, not theoretical: every ORBS purchase mid-round is a coin-flip against the 60fps loop.

**Evidence.** private val entities = mutableListOf<Entity>() ... private fun applyStats(newStats) { ... if (newStats.orbCount != oldStats.orbCount) { entities.removeAll { it is OrbEntity }; spawnOrbs() } }  // called from BattleViewModel.purchaseInRoundUpgrade -> updateZigguratStats on the main thread; entities is simultaneously iterated by GameLoopThread.run -> engine.update/render

**Verification (<2 min).** Start a round, walk so you can afford the ORBS in-round upgrade, then repeatedly tap the in-round ORBS upgrade while ~20+ enemies/projectiles are on screen. Within a handful of taps the app crashes with ConcurrentModificationException originating in GameEngine.update or render (visible in logcat). Repro is far more reliable at 4x speed.

**Recommended fix.** Never structurally mutate `entities` from outside the game-loop thread. Route orb reconciliation through the loop: have `applyStats` set a pending flag/`@Volatile var orbCountDirty` (or push the new stats into a `@Volatile` field) and perform the actual `entities.removeAll`/`spawnOrbs` at the top of `GameEngine.update()` on the loop thread. Alternatively guard all `entities` access with `synchronized(entities)` on both the loop and the mutation paths. Same treatment for any other `entities` mutation reachable from the VM.

**Regression test.** A Robolectric/JVM test that drives a GameEngine on a background thread running update() in a tight loop while the test thread calls updateZigguratStats with a stats copy whose orbCount differs, asserting no exception is thrown over N iterations. Before the fix this intermittently throws ConcurrentModificationException; after the fix (loop-thread-deferred reconciliation) it passes deterministically.

**Verifier adjustment.** The race is real and reachable, but the per-purchase probability is lower than "coin-flip." The crash window is the GameLoop thread's brief iteration of `entities` (microseconds out of each ~16.6ms tick), so most ORBS purchases will NOT collide; over a playtest session, however, an intermittent ConcurrentModificationException / iteration corruption is realistic. Severity remains High (an in-game crash during normal play). Also worth noting: the underlying flaw is broader than ORBS — `GameEngine.entities` is a plain unsynchronized `mutableListOf` shared between the GameLoop thread (`update()` is NOT inside the `synchronized(surfaceHolder)` block; only `render()` is) and the main thread; ORBS is simply the only in-round-purchasable upgrade whose `applyStats` branch structurally mutates `entities`.

---

### 2. 🟡 MEDIUM / Confidence: High — Stale snapshot loses in-round upgrades (state-machine restore bug)

- **Category:** Bugs  
- **Surface:** battle-engine  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `presentation/battle/engine/GameEngine.kt`  
- **Lines:** GameEngine.kt:571-583 (GOLDEN activation captures preGoldenStats), 606-613 (GOLDEN expiry restores preGoldenStats), 332-334/353-367 (updateZigguratStats/applyStats)  

**Description.** When GOLDEN_ZIGGURAT activates it snapshots `preGoldenStats = stats` and bumps damage. On expiry it restores `applyStats(preGoldenStats)`. But any in-round upgrade purchased WHILE GOLDEN is active goes through `updateZigguratStats -> applyStats(newStats)` which overwrites `stats` and does NOT update `preGoldenStats`. So when GOLDEN expires it rolls the ziggurat back to the stale pre-GOLDEN snapshot, silently discarding every stat-bearing upgrade the player bought during the GOLDEN window (damage, attack speed, health, range, etc.). Because GOLDEN now auto-triggers on cooldown (R4-06) and its effect lasts effectDurationSeconds, this window recurs every cooldown cycle — a player who upgrades during a GOLDEN proc loses those upgrades a few seconds later with no feedback. The HP-rebalance in applyStats also means a maxHealth upgrade made during GOLDEN gets reverted, dropping the ziggurat's effective HP cap mid-fight.

**Evidence.** GOLDEN_ZIGGURAT -> { goldenZigActive = true; preGoldenStats = stats; ...; applyStats(stats.copy(damage = stats.damage * dmgMult)) }  // expiry: preGoldenStats?.let { applyStats(it) }  // updateZigguratStats(newStats) just does applyStats(newStats) and never re-captures preGoldenStats

**Verification (<2 min).** Equip GOLDEN_ZIGGURAT, start a round, wait for it to auto-fire (goldenZigActive). While active, buy an in-round DAMAGE upgrade — projectile damage increases. Wait for GOLDEN to expire (~effectDurationSeconds). Observe damage drops back below the just-purchased level. In a unit test: activateUW(golden), updateZigguratStats(stats.copy(damage=X)), advance updateUWs past expiry, assert engine stats.damage == X (fails; it equals the pre-golden value).

**Recommended fix.** Don't restore stats by snapshot. Either (a) when GOLDEN is active, have `applyStats` apply the GOLDEN damage multiplier on top of incoming stats and keep `preGoldenStats` in sync (re-capture the new base before re-applying the multiplier), or (b) model GOLDEN as a separate multiplier layer that is re-derived from the current base stats rather than a captured snapshot. Same pattern should be audited for any future snapshot-and-restore UW.

**Regression test.** GameEngineTest: equip GOLDEN at L1, activateUW(0), call updateZigguratStats(stats.copy(damage = 999.0)), run updateUWs(deltaTime) long enough to expire the effect, then read the engine's resolved damage and assert it reflects the 999.0 in-round purchase (not the pre-golden snapshot). Fails before the fix.

---

### 3. 🟡 MEDIUM / Confidence: High — Concurrency / lost update (data integrity)

- **Category:** Bugs  
- **Surface:** data-room  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `data/repository/StepRepositoryImpl.kt`, `data/sensor/DailyStepManager.kt`, `data/healthconnect/StepCrossValidator.kt`, `data/local/DailyStepDao.kt`  
- **Lines:** StepRepositoryImpl.kt:21-42 (updateDailySteps/updateHealthConnectSteps/updateActivityMinutes/updateEscrow); StepCrossValidator.kt:71  

**Description.** Every per-field update of the daily_step_record row is a non-atomic read-modify-write: getByDateOnce(date) -> upsert(existing.copy(field=...)). The @Upsert writes ALL columns, so each writer persists the OTHER columns at the value it read. Multiple INDEPENDENT components write the same date row concurrently with no Mutex/transaction: StepCounterService (sensor, Dispatchers.IO) calls recordSteps->updateDailySteps(sensorSteps,creditedSteps); StepSyncWorker (WorkManager coroutine) calls recordSteps + recordActivityMinutes; StepGapFiller calls recordSteps; StepCrossValidator.validate calls updateHealthConnectSteps (line 71) and updateEscrow. If a sensor updateDailySteps and an HC updateHealthConnectSteps interleave (both read the same row snapshot, then write), the second writer clobbers the first writer's field with the stale value it read. Concretely: a fresh sensor credit written to creditedSteps is lost because the HC validator's upsert copies back the OLD creditedSteps it read. The wallet (player_profile.currentStepBalance) was already credited via addSteps, so the wallet and the daily_step_record diverge. Downstream this corrupts: (a) the 50,000/day ceiling accounting reloaded by DailyStepManager.ensureInitialized on next launch (reads creditedSteps+stepEquivalents back into dailyCreditedTotal), (b) sumCreditedSteps used by the weekly challenge, (c) stats/history charts. DailyStepManager is @Singleton with mutable non-synchronized fields, so the same lost-update also hits its in-memory counters when recordSteps and recordActivityMinutes run on different coroutines.

**Evidence.** StepRepositoryImpl.updateHealthConnectSteps: `val existing = dao.getByDateOnce(date) ?: DailyStepRecordEntity(date = date); dao.upsert(existing.copy(healthConnectSteps = healthConnectSteps))` — upsert rewrites sensorSteps/creditedSteps/escrow from the read snapshot. StepCrossValidator.validate line 71 calls this OUTSIDE any transaction; StepCounterService.kt:62 `scope.launch(Dispatchers.IO){ dailyStepManager.recordSteps(...) }`. No Mutex/synchronized/withLock anywhere in data/sensor, data/healthconnect, or service.

**Verification (<2 min).** In a Robolectric Room test, seed a row with creditedSteps=1000. Concurrently (two launched coroutines on a multithreaded dispatcher, or interleaved by reading both then writing both) run updateDailySteps(date, sensor=2000, credited=2000) and updateHealthConnectSteps(date, hc=1800). Read the row back: creditedSteps will sometimes be 1000 (the HC writer's stale copy) instead of 2000 — the sensor credit is lost. Wall-clock-realistic trigger: walk (sensor ticks) while a Health Connect sync worker fires for today.

**Recommended fix.** Replace the per-field read-copy-upsert with column-targeted UPDATE...SET queries that touch only the intended column(s) and ON-CONFLICT-insert the row if missing (the same INSERT...ON CONFLICT(date) DO UPDATE SET <oneColumn> pattern already used by incrementBattleSteps/incrementBossPs). e.g. `UPDATE daily_step_record SET healthConnectSteps=:v WHERE date=:d` with an insert-if-absent guard. That makes each writer touch a disjoint column set so concurrent writers no longer clobber each other. For the cap accounting, also derive remaining ceiling from a fresh DB read inside a transaction rather than the in-memory dailyCreditedTotal.

**Regression test.** DailyStepDao/StepRepository Robolectric test: seed creditedSteps=2000, then call updateHealthConnectSteps(date, 1800); assert getByDateOnce(date).creditedSteps == 2000 (proves HC update no longer rewrites credited). And an interleaving test: read row A, read row B, write field X from A's snapshot, write field Y from B's snapshot, assert BOTH X and Y persisted.

**Verifier adjustment.** The lost-update mechanism is real, but the claim that "the wallet and the daily_step_record diverge" overstates the wallet impact. PlayerRepositoryImpl.addSteps delegates to PlayerProfileDao.adjustStepBalance, which is an ATOMIC SQL increment (`UPDATE player_profile SET currentStepBalance = MAX(0, currentStepBalance + :delta) ...`) on a DIFFERENT table (player_profile), so the player's actual wallet balance is NOT corrupted or lost — every credited step still lands in the wallet. The lost update is confined to the daily_step_record accounting columns (sensorSteps/creditedSteps/healthConnectSteps/escrow/stepEquivalents). Downstream impact is therefore an accounting drift (50k/day ceiling reload in DailyStepManager.ensureInitialized, sumCreditedSteps weekly-challenge undercount, stats/history charts), not real currency loss, and is partly self-correcting because recordSteps writes cumulative totals (the next sensor tick re-writes a fresh higher sensorSteps/creditedSteps). The narrow interleaving window (worker runs only every 15 min) makes per-event probability low. Hence Medium, not High.

---

### 4. 🟡 MEDIUM / Confidence: High — TOCTOU economy / silent currency clamp

- **Category:** Bugs  
- **Surface:** data-room  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `domain/usecase/StartResearch.kt`, `data/repository/PlayerRepositoryImpl.kt`, `data/local/PlayerProfileDao.kt`  
- **Lines:** StartResearch.kt:37-42; PlayerRepositoryImpl.kt:28 (spendSteps); PlayerProfileDao.kt:41-42 (adjustStepBalance)  

**Description.** StartResearch is the only Step-spend that was NOT migrated to the guarded-atomic-deduct pattern that WorkshopDao.purchaseUpgradeAtomic established. It checks `wallet.stepBalance < cost` against a stale wallet snapshot passed from the ViewModel, then calls playerRepository.spendSteps(cost) -> adjustStepBalance(-cost), whose SQL is `currentStepBalance = MAX(0, currentStepBalance + :delta)`. Two failure modes: (1) TOCTOU — between the snapshot and the spend, a concurrent spend (another research start, a workshop purchase, a UW unlock) reduces the balance below cost. The check already passed, so research starts. The deduct silently clamps the balance to 0 instead of failing, so the player pays LESS than the research cost (or nothing) yet receives the research. (2) The clamp masks the under-payment entirely — there is no rows-affected guard, so the use case cannot detect it. This is a Steps economy exploit (Steps are the hard-gated primary currency).

**Evidence.** StartResearch: `if (wallet.stepBalance < cost) return Result.InsufficientSteps; playerRepository.spendSteps(cost); ... labRepository.startResearch(...)`. PlayerProfileDao.adjustStepBalance: `SET currentStepBalance = MAX(0, currentStepBalance + :delta)` — no `WHERE currentStepBalance >= :cost` guard, unlike adjustStepBalanceIfSufficient (line 53) which returns Int rows-affected.

**Verification (<2 min).** Set balance=100, cost=100. Fire two StartResearch concurrently for two different research types (both see snapshot balance=100). Both pass the check; first spends 100 -> balance 0; second spends 100 but MAX(0,...) clamps to 0. Both researches start; player got the second research for free. Observable: two active researches, balance 0, total spent 100 not 200.

**Recommended fix.** Add a PlayerProfileDao guarded deduct already exists for Steps (adjustStepBalanceIfSufficient). Route StartResearch's spend through a transactional atomic that calls adjustStepBalanceIfSufficient(cost) and only writes startResearch when it returns 1; return InsufficientSteps when it returns 0 (mirror WorkshopDao.purchaseUpgradeAtomic). Do NOT rely on the MAX(0,...) clamp as an affordability gate.

**Regression test.** JVM/Robolectric: balance=100, cost=100; first spend succeeds; second StartResearch must return InsufficientSteps and NOT create a second active-research row; assert only one research active and total spent == 100.

**Verifier adjustment.** The core mechanism is confirmed real. StartResearch is the only Step-spend-for-value path NOT migrated to the guarded-atomic-deduct pattern (PurchaseUpgrade -> purchaseUpgradeAtomic -> adjustStepBalanceIfSufficient, which returns rows-affected). StartResearch.kt:37 checks wallet.stepBalance < cost against a ViewModel-supplied snapshot, then StartResearch.kt:39 unconditionally calls playerRepository.spendSteps(cost) -> PlayerProfileDao.adjustStepBalance(-cost), whose SQL is `SET currentStepBalance = MAX(0, currentStepBalance + :delta)` with no `WHERE currentStepBalance >= :cost` guard. The MAX(0,...) clamp silently masks an under-payment, and there is no rows-affected return for the use case to detect it. No regression test covers the concurrent/TOCTOU case (StartResearchTest only covers happy-path + zero-balance). Two corrections to the original claim: (1) The cited 'a UW unlock' concurrent Step-drain is wrong — UnlockUltimateWeapon spends Power Stones via the atomic spendPowerStonesAtomic, not Steps; the realistic concurrent Step-spend is a Workshop purchase (a different, Singleton-shared ViewModel/repository) or another StartResearch. (2) LabsViewModel.startResearch has a `_processing` mutex (line 110/122/136) that serializes all Labs-internal operations, so two research-starts from the same screen cannot interleave — the live race is cross-surface (a slow workshop-purchase coroutine committing between the Labs snapshot read at LabsViewModel.kt:124 and the spend at StartResearch.kt:39). Severity lowered from High to Medium: the trigger requires two near-simultaneous Step-spends across two different screens, the snapshot is read fresh inside the tap coroutine (narrow window), per-incident loss is bounded to a single underpayment (<= the research cost), and it is a non-deterministic race a player cannot reliably farm. It is nonetheless a genuine economy-correctness gap of the exact class the project hardened everywhere else under RO-02, and should be migrated to an atomic guarded deduct.

---

### 5. 🟡 MEDIUM / Confidence: High — TOCTOU / item granted despite failed deduction

- **Category:** Bugs  
- **Surface:** data-room  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `data/repository/PlayerRepositoryImpl.kt`, `domain/usecase/UnlockUltimateWeapon.kt`, `domain/usecase/UnlockLabSlot.kt`, `domain/usecase/OpenCardPack.kt`, `domain/usecase/RushResearch.kt`, `domain/usecase/UpgradeUltimateWeapon.kt`  
- **Lines:** PlayerRepositoryImpl.kt:33-35 (spendGems), 40-42 (spendPowerStones); UnlockUltimateWeapon.kt:25-27; UnlockLabSlot.kt:16-21; OpenCardPack.kt:27-29  

**Description.** spendGems/spendPowerStones in PlayerRepositoryImpl call the correctly-guarded spendGemsAtomic/spendPowerStonesAtomic (WHERE gems>=:amount, returns rows-affected) but DISCARD the Int return value and return Unit. Every caller (UnlockUltimateWeapon, UnlockLabSlot, OpenCardPack, RushResearch, UpgradeUltimateWeapon) does an in-memory affordability check against a stale snapshot, then calls spend...(cost) and grants the item UNCONDITIONALLY. The guarded SQL correctly prevents the balance going negative, but because the grant is decoupled from the deduct's success, a TOCTOU (snapshot says affordable, a concurrent spend depletes the pool, the guarded deduct then no-ops) results in the item being granted for FREE. Realistic trigger: rapid double-tap of Unlock/Open-Pack, or opening a pack while another Gem purchase is mid-flight. The same single currency pool can fund two grants.

**Evidence.** PlayerRepositoryImpl: `override suspend fun spendGems(amount: Long) { dao.spendGemsAtomic(amount) }` — return Int ignored. UnlockUltimateWeapon.kt:24-27: `if (powerStones < type.unlockCost) return false; playerRepository.spendPowerStones(type.unlockCost.toLong()); uwRepository.unlockWeapon(type); return true` — unlock happens regardless of whether the deduct actually moved the balance.

**Verification (<2 min).** Gems=200, slot cost=200. Fire UnlockLabSlot twice concurrently (both see snapshot gems=200). First: spendGemsAtomic deducts 200 (rows=1), slot count++. Second: spendGemsAtomic returns 0 (gems now 0, WHERE fails) but the use case still does updateLabSlotCount(newCount). Result: slots incremented twice for 200 Gems. Same with two EPIC pack opens (500 each) on a 500-Gem balance: both grant 3 cards, only 500 charged.

**Recommended fix.** Make spendGems/spendPowerStones (and the Steps path) return Boolean (rows-affected > 0). Each use case must only grant the item when the guarded deduct returns true; otherwise return InsufficientX. Ideally wrap deduct+grant in a single Room @Transaction (per the purchaseUpgradeAtomic/claimMilestoneAtomic pattern) so the grant and the guarded deduct commit together.

**Regression test.** JVM test with FakePlayerRepository whose spendGems returns false when insufficient: assert UnlockLabSlot returns InsufficientGems and does NOT call updateLabSlotCount when the guarded deduct fails. Concurrency test against real Room: gems=200, two UnlockLabSlot, assert final slotCount incremented exactly once.

**Verifier adjustment.** The core mechanism is real: PlayerRepositoryImpl.spendGems (lines 33-35) and spendPowerStones (lines 40-42) call the guarded spendGemsAtomic/spendPowerStonesAtomic but discard the Int rows-affected return; the PlayerRepository interface signatures return Unit, so a use case structurally cannot learn whether a deduct actually moved the balance. Every cited use case (UnlockUltimateWeapon L26-27, UpgradeUltimateWeapon L31-32, UnlockLabSlot L18-20, OpenCardPack L29+, RushResearch L30-31) does a stale in-memory affordability check then grants unconditionally, with no mutex or shared check+spend+grant transaction. HOWEVER, two corrections to the finding's trigger list: (1) CardsViewModel.openPack/upgradeCard and LabsViewModel.unlockSlot/rushResearch/freeRush are in fact protected against rapid double-tap by a `_processing` MutableStateFlow guard — `if (_processing.value) return` is checked synchronously on Main and `_processing.value = true` is set synchronously inside the viewModelScope.launch (Dispatchers.Main.immediate) before the first DAO suspension, so a second Main-thread tap event sees processing=true and bails. So OpenCardPack/RushResearch/UnlockLabSlot are NOT reachable via the double-tap vector the finding cites. (2) The only genuinely unguarded path is UltimateWeaponViewModel.unlock()/upgrade(), which have NO _processing guard. Concrete reachable sequence: on the UW screen a player with Power Stones for exactly one unlock taps Unlock on UW-A then quickly taps Unlock on UW-B; both coroutines read the stale uiState.value.powerStones (Room InvalidationTracker has not re-emitted yet), C1's atomic deduct succeeds and unlocks UW-A, C2's atomic deduct no-ops (WHERE powerStones>=cost is now false) but unlockWeapon(UW-B) runs anyway -> UW-B unlocked for free. Severity is Medium not High: the V1X-10 atomic SQL DOES prevent the balance going negative; the impact is limited to an occasional duplicate/free grant within a narrow (~tens of ms) race window, on a single-player fully-offline local game with no server or other-player impact, and only via one screen since the other surfaces are guarded.

---

### 6. 🟡 MEDIUM / Confidence: High — Concurrency / data race on shared mutable state

- **Category:** Bugs  
- **Surface:** sensor-anticheat  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `data/sensor/DailyStepManager.kt`, `service/StepCounterService.kt`, `service/StepSyncWorker.kt`  
- **Lines:** DailyStepManager.kt:69-115,117-160,162-193; StepCounterService.kt:59-71; StepSyncWorker.kt:53,92  

**Description.** DailyStepManager is a @Singleton with non-synchronized mutable fields (currentDate, dailySensorTotal, dailySensorCredited, dailyCreditedTotal, dailyActivityMinuteTotal, dropState, initialized, stepsPerMinute). The single instance is shared by StepCounterService (collecting sensor deltas on Dispatchers.Default) and StepSyncWorker (recordSteps + recordActivityMinutes on the WorkManager coroutine). These run on different threads/coroutines with no mutex. recordSteps does a read-modify-write across dailyCreditedTotal/dailySensorTotal/dailySensorCredited and then persists with updateDailySteps using the local copy. If the service-thread credit and a worker-thread recordActivityMinutes (or fillGaps recordSteps) interleave, the ceiling check (remainingCeiling = DAILY_CEILING - dailyCreditedTotal) and the subsequent dailyCreditedTotal += credited are not atomic. Two concurrent credits can both read the same dailyCreditedTotal, both pass the ceiling check, and both add — overshooting the 50,000/day ceiling and persisting a stale lower creditedSteps/sensorSteps (last-writer-wins on updateDailySteps clobbers the other's count). Result: ceiling bypass and/or lost-update under-credit, plus a corrupted in-memory baseline that diverges from Room.

**Evidence.** No @Synchronized, no Mutex, no @Volatile on the counters. recordSteps: `val remainingCeiling = (DAILY_CEILING - dailyCreditedTotal)...; ...dailyCreditedTotal += credited; stepRepository.updateDailySteps(currentDate, dailySensorTotal, dailySensorCredited + credited)`. recordActivityMinutes mutates dailyCreditedTotal/dailyActivityMinuteTotal on the same fields from the worker coroutine.

**Verification (<2 min).** In a JVM test, share one DailyStepManager between two coroutines on a multi-threaded dispatcher; from coroutine A call recordSteps(40000,...) and from coroutine B call recordActivityMinutes(mapOf(...),40000) concurrently (each within ceiling individually but >50k combined). Assert final wallet > 50,000 (ceiling bypass) on at least some runs, or that the persisted creditedSteps does not equal wallet (lost update). The race is observable because there is no lock around the check-then-credit.

**Recommended fix.** Serialize all mutation in DailyStepManager behind a single kotlinx.coroutines.sync.Mutex (withLock around the whole recordSteps / recordActivityMinutes / ensureInitialized body), or move the credit-and-ceiling logic into an atomic Room transaction (like creditBattleStepsAtomic) where the ceiling check and wallet write are one SQL statement against the persisted creditedSteps rather than an in-memory mirror. The in-memory counters should be treated as a cache derived from Room, not the source of truth for the ceiling.

**Regression test.** DailyStepManagerConcurrencyTest: launch N concurrent recordSteps/recordActivityMinutes calls totalling >50k on Dispatchers.Default; assert getStepBalance() <= 50_000 and persisted creditedSteps == in-memory dailyCreditedTotal after all complete.

**Verifier adjustment.** The data race is real and reachable, but the finding overstates one specific mechanism. The `isServiceAlive` heartbeat guard (StepSyncWorker.sensorCatchUp, line 70) DOES make the worker's sensor-catch-up `recordSteps` mutually exclusive with the live service, so the "two concurrent recordSteps both clobber updateDailySteps" path requires the worker's StepGapFiller.fillGaps->recordSteps (worker lines 44-45) which is NOT gated by isServiceAlive — so it can still race the service's sensor recordSteps. The more reliably-reachable race is service `recordSteps` (Dispatchers.Default sensor-collect coroutine) vs. worker `recordActivityMinutes` / `fillGaps` (the HC-sync block at worker lines 44-54 runs unconditionally, no isServiceAlive guard). Those two write DIFFERENT Room columns via different DAO methods (updateDailySteps vs updateActivityMinutes), so the "last-writer-wins on updateDailySteps clobbers the other's count" only fully holds for the rarer recordSteps-vs-recordSteps (service vs fillGaps) collision. The genuinely-impactful, broadly-reachable consequences are: (1) non-atomic ceiling RMW on the shared un-synchronized `dailyCreditedTotal` field — two interleaved credits can both pass `remainingCeiling = DAILY_CEILING - dailyCreditedTotal` and both add, overshooting the 50k ceiling and double-crediting `playerRepository.addSteps`; (2) the plain `mutableMapOf` `stepsPerMinute` is concurrently mutated (lines 153-156) AND read by the worker via `getSensorStepsPerMinute().toMap()` (worker line 51) — unsynchronized HashMap access can throw ConcurrentModificationException / corrupt the map; (3) `initialized` check-then-set in `ensureInitialized` is racy (double Room load); (4) the in-memory baseline diverges from Room and stays corrupted until day-rollover or process restart. No @Synchronized / Mutex / @Volatile / AtomicLong anywhere, no single-thread confinement (both CoroutineWorker default and Dispatchers.Default are multi-threaded pools), and no concurrency regression test in DailyStepManagerTest.

---

### 7. 🟡 MEDIUM / Confidence: High — Reboot baseline staleness — under/over-credit

- **Category:** Bugs  
- **Surface:** sensor-anticheat  
- **Files:** `service/StepSyncWorker.kt`, `service/StepCounterService.kt`, `data/sensor/StepIngestionPreferences.kt`  
- **Lines:** StepSyncWorker.kt:67-94; StepCounterService.kt:78-100; StepIngestionPreferences.kt:33-44  

**Description.** TYPE_STEP_COUNTER resets to 0 on device reboot. The day-start baseline (day_start_counter) is captured ONCE per local day at service/worker startup and never re-captured after a mid-day reboot. After reboot the live counter restarts near 0 while day_start_counter still holds the large pre-reboot value (e.g. 8000). In StepSyncWorker.sensorCatchUp: `rawToday = currentCounter - dayStart` becomes negative (e.g. 120 - 8000 = -7880) -> `if (rawToday <= 0) return` -> ALL steps walked after the reboot for the rest of the local day are silently dropped from the catch-up path. The only recovery is StepGapFiller via Health Connect (which itself has the double-credit bug). Conversely, if the user reboots and the COUNTER happens to climb back past the stale baseline before midnight, the gap math will resume but mis-attribute the post-reboot accumulation. Net effect: a mid-day reboot causes step loss (player-facing) and an inconsistent baseline that defeats the service/worker handoff for the rest of the day.

**Evidence.** setCounterAtDayStart is gated by `if (getCounterAtDayStart(today) != null) return` (service) and `if (dayStart == null) { setCounterAtDayStart...; return }` (worker) — so once set for the day it is never refreshed, even though a reboot invalidates the absolute counter value. No detection of `currentCounter < dayStart` to re-baseline.

**Verification (<2 min).** Set day_start_counter=8000 for today, simulate reboot by stubbing the sensor to return currentCounter=120. Run sensorCatchUp -> rawToday=-7880 -> returns, zero steps credited. Walk more (currentCounter=500) -> still rawToday<0 -> nothing credited all day.

**Recommended fix.** Detect counter reset: when currentCounter < dayStart (or < lastSeenCounter), treat it as a reboot and re-baseline day_start_counter to currentCounter, crediting nothing for the discontinuity (the pre-reboot steps were already credited live). Persist a lastSeenCounter and use delta = currentCounter - lastSeenCounter with a reset guard, rather than an absolute day-start subtraction that a reboot invalidates.

**Regression test.** StepSyncWorkerTest: dayStart=8000, currentCounter=120 (post-reboot); assert sensorCatchUp re-baselines day_start_counter to 120 and credits 0, then currentCounter=300 next run credits exactly 180.

**Verifier adjustment.** The core mechanism is real and confirmed, but the "ALL steps walked after the reboot are silently dropped" framing is overstated. It applies only to the WORKER's sensor catch-up path (used when the foreground service is dead). The LIVE service path does NOT use day_start_counter: StepSensorDataSource.stepDeltas computes deltas between consecutive readings off its own `lastCumulative = -1L` baseline (re-established on first reading), so post-reboot steps walked while the restarted service is alive are credited correctly. Additionally StepGapFiller.fillGaps reads Health Connect's reboot-resilient full-day total and credits `hcTotal - sensorTotal`, a genuine recovery path when HC is installed + permitted. So the actual unrecoverable loss is bounded to: post-reboot steps walked while the service is NOT alive, for the remainder of the local day, on devices without a working Health Connect backstop.

---

### 8. 🟡 MEDIUM / Confidence: High — Missing purchase signature verification (client-side IAP trust)

- **Category:** Security  
- **Surface:** security  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `data/billing/BillingManagerImpl.kt`, `data/billing/internal/RealBillingClientAdapter.kt`, `data/billing/internal/BillingClientAdapter.kt`  
- **Lines:** BillingManagerImpl.kt:175-241 (handleCompletedPurchase/creditWallet) + 278-315 (reconcileType); RealBillingClientAdapter.kt:272-291 (Purchase.toSdk); BillingClientAdapter.kt:134-143 (SdkPurchase has no signature/originalJson field)  
- **Source→Sink:** Source: attacker-controlled Purchase object (rooted device / hooked Play Billing / forged queryPurchasesAsync result) -> RealBillingClientAdapter.purchasesUpdatedListener or queryPurchases -> Purchase.toSdk (drops signature) -> BillingManagerImpl.handleCompletedPurchase/reconcileType -> grantOnceAtomic -> creditWallet. Sink: PlayerProfileDao.adjustGems / updateAdRemoved / updateSeasonPass (permanent currency/entitlement grant).  

**Description.** No Google Play purchase signature verification exists anywhere in the billing pipeline. Purchases are trusted purely on the basis of the SDK returning a Purchase whose state == PURCHASED. The SDK-neutral SdkPurchase projection (RealBillingClientAdapter.Purchase.toSdk) deliberately drops Purchase.getOriginalJson() and Purchase.getSignature(), so even if the team wanted to verify the RSA signature against the app's Play public key, the data has already been discarded before it reaches BillingManagerImpl. creditWallet() then grants up to 700 Gems (GEM_PACK_LARGE), permanent ad-removal, or a 30-day Season Pass with zero integrity check. On a rooted device or with a tampered/repackaged build using a fake Play Billing implementation (e.g. Luckypatcher/Freedom-style In-App-Purchase emulators), any Purchase object with purchaseState=PURCHASED and a productId matching one of the 5 SKUs is accepted. Because BillingProduct.fromSkuIdOrNull maps purely on the lowercase enum name, an attacker who can inject a Purchase into the PurchasesUpdatedListener or queryPurchasesAsync result (the reconcile path) gets the full Gem/ad-removal/season-pass grant. The class KDoc states server-side verification is 'forbidden by CONSTRAINTS.md for v1.0', but client-side signature verification (Security.verifyPurchase against the Base64 public key, no server needed) is a standard mitigation that is also absent.

**Evidence.** RealBillingClientAdapter.kt Purchase.toSdk(): builds SdkPurchase(productId, orderId, purchaseToken, purchaseTime, purchaseState, isAcknowledged, isAutoRenewing, rawRef=this) — no originalJson, no signature. BillingManagerImpl.creditWallet(): `when (product) { GEM_PACK_* -> playerProfileDao.adjustGems(amount) ...}` runs on any PURCHASED purchase with no verification step before grantOnceAtomic.

**Assumptions.** Assumes the release build can be repackaged/hooked or run on a rooted device (the malicious-local-user threat model explicitly in scope). On a fully stock, un-rooted device the Play SDK does its own end-to-end check, so this is a defense-in-depth gap rather than a stock-device break — hence High not Critical.

**Verification (<2 min).** Input: a Purchase with purchaseState=PURCHASED, productId="gem_pack_large", arbitrary purchaseToken="forged_"+random, fed through a fake BillingClientAdapter (or, on device, a hooked Play Billing). Trigger: call purchase(GEM_PACK_LARGE) (the listener delivers the forged purchase) OR drop the forged purchase into queryPurchases and call reconcilePendingPurchases(). Observable: PlayerProfile.gems increases by 700 and a granted=true BillingReceiptEntity is persisted, despite no real transaction. <2 min via BillingManagerImplTest harness: stub queryPurchases to return a fabricated SdkPurchase and assert gems==700.

**Recommended fix.** Add Purchase.getOriginalJson()+getSignature() to the SdkPurchase projection and verify the RSA-SHA1 signature against the app's Base64-encoded Play license public key (e.g. a local port of the Play Billing sample's Security.verifyPurchase) BEFORE calling grantOnceAtomic/creditWallet, in BOTH handleCompletedPurchase and reconcileType. The public key can be obfuscated/split or fetched at runtime; this requires no server. Reject (and do not persist a granted receipt for) any purchase whose signature fails. This is distinct from server-side verification and not forbidden by CONSTRAINTS.md.

**Regression test.** BillingManagerImplTest: `purchase with invalid signature does not credit wallet` — feed a StartPurchaseResult.Completed whose SdkPurchase carries a tampered originalJson/signature pair; assert PurchaseResult.Error, profile.gems == 0, and receiptDao.getAll().isEmpty(). This test cannot even be written today because SdkPurchase has no signature field — adding the field + verification is the fix.

**Verifier adjustment.** The mechanism is real and accurately described, but the severity should be Medium, not High. All code-level claims verified exactly: RealBillingClientAdapter.Purchase.toSdk() (lines 272-291) drops getSignature()/getOriginalJson() — the neutral SdkPurchase (BillingClientAdapter.kt:134-143) has no such field; BillingManagerImpl.creditWallet() (226-241) grants 700 gems / ad-removal / 30-day season pass purely on purchaseState==PURCHASED via grantOnceAtomic, with zero signature/RSA/Security.verifyPurchase logic anywhere in the billing package (grep-confirmed). fromSkuIdOrNull maps purely on lowercased enum name. The finding's nuance — that client-side RSA signature verification needs no backend and is therefore NOT covered by the 'no-server-side verification' constraint in ADR-0005/CONSTRAINTS.md — is correct and well-stated. Severity adjustment rationale: (a) this is explicitly a defense-in-depth gap requiring a rooted/repackaged/hooked device — on stock un-rooted devices Play's end-to-end signing protects the transaction, which the finding itself acknowledges; (b) client-side signature verification is itself trivially bypassable by patching the embedded Base64 public key in a repackaged APK, so the proposed mitigation only raises the bar marginally against the same local-attacker; (c) all grants are local-only — no server economy, no multiplayer, no other-player impact (a self-experience idle game where the only loss is unrealized cosmetic/convenience IAP revenue from a fraudster who was never going to pay). Local IAP fraud is a well-known accepted residual risk for client-only games. Real and reachable, worth a tracked post-v1.0 hardening item, but Medium given local-only impact and bypassable mitigation.

---

### 9. 🟡 MEDIUM / Confidence: High — Check-then-act double-credit (economy exploit via concurrent claims)

- **Category:** Bugs  
- **Surface:** concurrency-async  
- **Files:** `domain/usecase/ClaimSupplyDrop.kt`, `presentation/supplies/UnclaimedSuppliesViewModel.kt`, `data/local/WalkingEncounterDao.kt`  
- **Lines:** ClaimSupplyDrop.kt:20-35 (if drop.claimed -> credit -> claimDrop); UnclaimedSuppliesViewModel.kt:31-39 (claimDrop/claimAll each viewModelScope.launch); WalkingEncounterDao.kt:20-21 (unconditional UPDATE ... WHERE id)  
- **Source→Sink:** Tainted source: rapid UI taps -> two coroutines reading stale drop.claimed==false -> sink: unguarded wallet credit + non-atomic markClaimed -> double credit.  

**Description.** ClaimSupplyDrop checks `drop.claimed` (a stale in-memory snapshot from the UI list), then credits the wallet (addSteps/addGems/addPowerStones/addCardOrIncrementCopy), then calls encounterRepository.claimDrop(id) which maps to an UNCONDITIONAL `UPDATE walking_encounter SET claimed=1 WHERE id=:id` (no `AND claimed=0` guard, no rows-affected gate). Unlike milestones and billing (which use *Atomic guarded writes), there is no atomic claim guard here. Each tap launches a separate viewModelScope coroutine on Dispatchers.Main.immediate; the credit calls suspend at the Room IO hop, letting a second coroutine (a rapid double-tap, or claimAll() overlapping with an individual claimDrop()) re-enter with the same drop.claimed==false snapshot and credit a SECOND time before either marks it claimed. Result: free duplicate Gems/Power Stones/Steps — a real premium-currency exploit reachable by any tester double-tapping a supply drop or hitting 'Claim All' twice.

**Evidence.** // ClaimSupplyDrop
if (drop.claimed) return Result.AlreadyClaimed
when (drop.reward) { ... playerRepository.addGems(drop.rewardAmount.toLong()) ... }  // credit
encounterRepository.claimDrop(drop.id)                                              // mark, no guard
// WalkingEncounterDao
@Query("UPDATE walking_encounter SET claimed = 1, claimedAt = :claimedAt WHERE id = :id")

**Assumptions.** Assumes the UI allows two taps before the Flow re-emits (true — the Room Flow refresh is async and the screen offers both per-drop and Claim-All buttons).

**Verification (<2 min).** On the Unclaimed Supplies screen with one GEMS drop, double-tap it (or tap an individual drop and Claim All in quick succession). Observe gems incremented twice for one drop. In a test: launch two concurrent claimSupplyDrop(sameDrop) coroutines against a real in-memory Room DB and assert gems credited once.

**Recommended fix.** Add an atomic guarded claim: a DAO @Query 'UPDATE walking_encounter SET claimed=1, claimedAt=:t WHERE id=:id AND claimed=0' returning the affected row count, and only credit the wallet when it returns 1 — ideally inside a single Room @Transaction (mirror MilestoneDao.claimMilestoneAtomic / BillingReceiptDao.grantOnceAtomic). Re-fetch the drop's claimed state inside the transaction rather than trusting the UI snapshot.

**Regression test.** ClaimSupplyDropConcurrencyTest: seed one unclaimed GEMS drop in a real Room DB, launch 5 concurrent invoke(drop) calls, assert gems increased by exactly rewardAmount and exactly one Success result (fails today — credits multiple times).

---

### 10. 🟡 MEDIUM / Confidence: High — Check-then-act double-credit (daily mission claim)

- **Category:** Bugs  
- **Surface:** concurrency-async  
- **Files:** `presentation/missions/MissionsViewModel.kt`, `data/local/DailyMissionDao.kt`  
- **Lines:** MissionsViewModel.kt:96-104 (claimMission: find !claimed -> addGems/addPowerStones -> markClaimed); DailyMissionDao.kt:27-28 (markClaimed unconditional UPDATE)  
- **Source→Sink:** Tainted source: two rapid taps -> two coroutines both see claimed=0 -> sink: unguarded addGems/addPowerStones + non-atomic markClaimed -> double credit.  

**Description.** MissionsViewModel.claimMission(id) launches a coroutine that reads missions via getByDateOnce, finds the row with `it.completed && !it.claimed`, credits Gems/Power Stones, then calls dailyMissionDao.markClaimed(id) — an unconditional 'UPDATE daily_mission SET claimed=1 WHERE id=:id' with no `AND claimed=0` guard and no atomic credit. A double-tap launches two coroutines; the credit suspends at the Room IO hop, the second coroutine re-runs getByDateOnce (still sees claimed=0 because the first hasn't reached markClaimed yet) and credits again. Same exploit shape as the supply-drop finding: duplicate Gems/Power Stones for one mission.

**Evidence.** val m = missions.find { it.id == id && it.completed && !it.claimed } ?: return@launch
if (m.rewardGems > 0) playerRepository.addGems(m.rewardGems.toLong())   // credit, suspends
if (m.rewardPowerStones > 0) playerRepository.addPowerStones(...)
dailyMissionDao.markClaimed(id)  // unconditional UPDATE — no claimed=0 guard

**Assumptions.** Assumes two taps register before the Flow re-emits the claimed row; realistic on Dispatchers.Main.immediate where launches interleave at the addGems suspension point.

**Verification (<2 min).** Complete a daily mission, then double-tap Claim. Observe reward credited twice. Test: two concurrent claimMission(id) against a completed-unclaimed row in a real Room DB; assert gems credited once.

**Recommended fix.** Add 'UPDATE daily_mission SET claimed=1 WHERE id=:id AND claimed=0' returning rows-affected, and only credit when it returns 1, inside a single @Transaction with the wallet writes (mirror the existing *Atomic DAO pattern). Alternatively add an in-VM isProcessing guard like the other VMs use, but the DB guard is the durable fix.

**Regression test.** ClaimMissionConcurrencyTest: seed a completed/unclaimed mission, launch N concurrent claimMission(id), assert wallet credited exactly once and claimed==true (fails today).

---

### 11. 🟡 MEDIUM / Confidence: High — GC churn + redundant work — getAliveEnemies allocates two lists per call, called many times per frame

- **Category:** Performance  
- **Surface:** performance  
- **Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/entities/OrbEntity.kt`  
- **Lines:** GameEngine.kt:728-729 (definition), 557/562/624/635/647/722 (callers); OrbEntity.kt:63 (per-orb per-frame getEnemies())  

**Description.** getAliveEnemies() does `entities.filterIsInstance<EnemyEntity>().filter { it.isAlive }` — two list allocations + a full O(entities) scan per call. It is invoked once per frame from the UW auto-trigger gate (line 647) whenever any UW is equipped, plus once per frame for EACH active BLACK_HOLE / POISON_SWAMP ongoing-effect UW (lines 624, 635), and DEATH_WAVE/CHAIN_LIGHTNING on activation (557/562). Worse: each OrbEntity.update() calls the `getEnemies` callback (OrbEntity.kt:63) which is bound to ::getAliveEnemies (GameEngine.kt:722), so with N orbs the same full enemy filter is recomputed N times per frame, each allocating two lists. A late-game player with 4 orbs + 2 ongoing UWs + the auto-trigger gate recomputes the identical alive-enemy list ~7 times every tick (×240/s at 4x speed), each time scanning all ~80 entities and allocating two lists.

**Evidence.** private fun getAliveEnemies(): List<EnemyEntity> =
    entities.filterIsInstance<EnemyEntity>().filter { it.isAlive }
// OrbEntity.update(): for (enemy in getEnemies()) { ... }  (called per orb per frame)

**Assumptions.** Assumes ORBS upgrade and ongoing UWs are reachable in normal play (they are — Workshop ORBS upgrade and BLACK_HOLE/POISON_SWAMP UWs are shipped).

**Verification (<2 min).** Equip ORBS upgrade to 4 orbs and an ongoing UW (BLACK_HOLE), run a wave with 30+ enemies at 4x. Profiler shows getAliveEnemies as a top allocation site with call count ≈ (orbCount + ongoingUWs + 1) per frame. Set a conditional breakpoint counting calls per update() to confirm multiple invocations per tick.

**Recommended fix.** Compute the alive-enemy list ONCE per update() (into a reusable scratch list filled in the single partition pass from finding #1) and pass that snapshot to orbs, UW ongoing effects, and the auto-trigger gate. Change OrbEntity to receive the per-frame enemy snapshot as a parameter to update() (or have the engine drive orb hit-checks against the shared snapshot) instead of each orb pulling its own freshly-filtered list.

**Regression test.** SimulationTest/GameEngineTest: with 4 orbs and 3 enemies positioned so exactly one orb is in HIT_RANGE of one enemy, assert that one orb-hit fires per HIT_COOLDOWN window and total damage matches the pre-refactor value — proves sharing the snapshot does not change hit semantics.

**Verifier adjustment.** The mechanism is confirmed verbatim. Two corrections to the finding's details (both make it slightly worse or are immaterial): (1) ORBS caps at 6, not 4 (UpgradeType.kt:68 `ORBS to UpgradeConfig(800, 1.22, 6, ...)`, ResolveStats.kt:104 `min(total(ORBS), 6)`) — so the worst-case orb multiplier is 6, not 4. (2) Lines 557/562 (DEATH_WAVE / CHAIN_LIGHTNING) fire only on UW activation (gated by `cooldownRemaining > 0f` / `effectTimeRemaining > 0f` in activateUW), not every frame — the finding correctly already labels these as on-activation. The per-frame callers are confirmed: auto-trigger gate (647, runs whenever uwStates.isNotEmpty()), BLACK_HOLE ongoing (624), POISON_SWAMP ongoing (635), and OrbEntity.update→getEnemies (OrbEntity.kt:63) bound to ::getAliveEnemies (722). At most 2 of {BLACK_HOLE, POISON_SWAMP} can be active at once since UW loadout is capped at 3.

---

### 12. 🟡 MEDIUM / Confidence: High — Data race / concurrency

- **Category:** Bugs  
- **Surface:** supplychain-infra-debt  
- **Severity:** Medium *(adjusted down from High by adversarial verification)*  
- **Files:** `data/sensor/DailyStepManager.kt`, `service/StepCounterService.kt`, `service/StepSyncWorker.kt`  
- **Lines:** DailyStepManager.kt:33-34 (@Singleton), 117-160 (recordSteps), 162-193 (recordActivityMinutes), 144-157 (unsynchronized mutable-field mutation); StepCounterService.kt:59-71 (collector on Dispatchers.Default); StepSyncWorker.kt:37-65 (doWork)  

**Description.** DailyStepManager is an app-wide @Singleton (single process — no android:process in the manifest) and is mutated from two genuinely concurrent threads with no Mutex/synchronization. Writer A: StepCounterService's sensor collector coroutine running on Dispatchers.Default. Writer B: StepSyncWorker, a CoroutineWorker dispatched on WorkManager's own executor thread (the app installs a HiltWorkerFactory but no custom executor, so it is a different thread from Dispatchers.Default). recordSteps()/recordActivityMinutes() read-modify-write the plain (non-@Volatile, non-atomic) fields dailyCreditedTotal, dailySensorTotal, dailySensorCredited, dailyActivityMinuteTotal, and the stepsPerMinute map, then call playerRepository.addSteps(credited). The worker's sensorCatchUp() is gated by isServiceAlive() so the SENSOR path is mostly mutually exclusive, BUT the worker's Health-Connect path (stepGapFiller.fillGaps -> recordSteps, and recordActivityMinutes via activityMinuteConverter) runs UNCONDITIONALLY every 15 minutes even while the service is alive (StepSyncWorker.kt:44-54 has no isServiceAlive guard). So a sensor delta arriving on the service collector can interleave with the worker crediting HC steps. Classic lost-update: both read dailyCreditedTotal=X, both compute remainingCeiling against X, both write back X+credit independently. The 50,000-step daily ceiling (remainingCeiling at line 140/182) can be exceeded, and dailyCreditedTotal can be left inconsistent with what was persisted, because the cap check and the increment are not atomic across the two threads. This directly undermines the anti-cheat daily-ceiling invariant and can mis-credit the permanent Steps currency.

**Evidence.** DailyStepManager.kt:140-149 — `val remainingCeiling = (DAILY_CEILING - dailyCreditedTotal).coerceAtLeast(0); val credited = multiplied.coerceAtMost(remainingCeiling); ... dailyCreditedTotal += credited; stepRepository.updateDailySteps(...); playerRepository.addSteps(credited)` with no lock around the read-check-write. Grep confirms zero Mutex/withLock/synchronized/withContext/@Volatile in the file (only one match for widgetUpdateHelper.update).

**Assumptions.** Assumes WorkManager dispatches the worker on a thread distinct from Dispatchers.Default (true by default — WorkManager uses its own Executor unless overridden, which this app does not). Assumes the service and worker can be simultaneously active, which the unconditional HC path at StepSyncWorker.kt:44-54 confirms.

**Verification (<2 min).** Write a JVM test that constructs one DailyStepManager with fake repos and launches two coroutines on two single-thread dispatchers, each calling recordSteps(30_000, t) concurrently when dailyCreditedTotal is already 40_000. Expected (correct): total credited capped at 50_000. Observed (racy): with repeated runs / a yield injected between the read of dailyCreditedTotal and the write, total credited exceeds 50_000 and/or playerRepository total != dailyCreditedTotal. Even without a forced yield, the ceiling check + increment is non-atomic and fails under interleave.

**Recommended fix.** Serialize all state mutation in DailyStepManager behind a single kotlinx.coroutines.sync.Mutex (withLock around the entire read-check-write-persist body of recordSteps/recordActivityMinutes/ensureInitialized), or confine the singleton to a dedicated single-thread dispatcher and wrap each public suspend fun in withContext(thatDispatcher). Either makes the cap-check-and-credit atomic across the service and worker threads. The DB-side atomic creditBattleStepsAtomic pattern already used for battle steps should be mirrored here for walking ingestion.

**Regression test.** DailyStepManagerConcurrencyTest: two coroutines on separate single-thread dispatchers each call recordSteps near the 50k ceiling; assert final credited == 50_000 and playerRepository balance == dailyCreditedTotal. Fails before the Mutex (over-credit / divergence), passes after.

**Verifier adjustment.** The data race is real, but the finding's stated threading MECHANISM is imprecise. The worker does NOT run on a "WorkManager executor thread distinct from Dispatchers.Default" — CoroutineWorker.doWork() defaults to Dispatchers.Default (StepSyncWorker does not override coroutineContext), the same dispatcher the service collector uses. This does not rescue the code, however: Dispatchers.Default is a multi-threaded pool (>=2 threads), so the two independently-launched coroutines (service collector + worker doWork) still run concurrently on different pool threads with no happens-before relationship. The conclusion (two genuinely concurrent unsynchronized writers) holds regardless. Severity reduced from High to Medium: each individual write is still clamped by coerceAtMost(remainingCeiling), so the daily-ceiling overshoot is bounded to roughly one extra concurrent batch (not unbounded), and the precise interleaving requires the 15-min worker to fire during active sensor ingestion while the service is alive — a narrow window. The most likely concrete symptom is corruption/ConcurrentModificationException on the plain HashMap `stepsPerMinute` (lines 79, 153-157), with a secondary lost-update on dailyCreditedTotal/dailySensorCredited and a possible small double-credit to the permanent Steps ledger via playerRepository.addSteps.

---

### 13. 🟡 MEDIUM / Confidence: Medium — Unbounded fixed-timestep catch-up (spiral of death) amplified by speed multiplier

- **Category:** Performance  
- **Surface:** battle-engine  
- **Files:** `presentation/battle/GameLoopThread.kt`  
- **Lines:** GameLoopThread.kt:33-44 (accumulator += elapsed*speedMultiplier; while (accumulator >= TICK_NS) engine.update())  

**Description.** The fixed-timestep accumulator has no maximum-frame-time clamp. After any long frame (GC pause, the main thread starving the loop, or a heavy `engine.update` itself), `elapsed` can be hundreds of milliseconds; multiplied by `speedMultiplier` (up to 4x) the accumulator demands dozens-to-hundreds of `engine.update(1/60)` calls in a single iteration before a frame is rendered. Each catch-up tick spawns/moves entities and runs collision, so a slow tick begets more catch-up ticks — the classic spiral of death where the loop never recovers and the screen visibly freezes. At 4x speed the threshold for entering the spiral is 4x lower. There is also no clamp on the case where one `engine.update` exceeds TICK_NS, which guarantees accumulator growth.

**Evidence.** accumulator += (elapsed * speedMultiplier).toLong(); while (accumulator >= TICK_NS) { engine.update(TICK_NS / 1_000_000_000f); accumulator -= TICK_NS }   // no clamp on accumulator or elapsed

**Verification (<2 min).** At 4x speed with a high wave (many enemies), induce a GC or a debugger pause for ~500ms; on resume the loop runs ~120 update ticks back-to-back and the frame visibly hitches/freezes. Reproducible by injecting a Thread.sleep into update() once and observing the post-sleep catch-up burst.

**Recommended fix.** Clamp the per-iteration accumulator (e.g. `accumulator = min(accumulator + delta, MAX_ACCUM)` where MAX_ACCUM caps to ~5 ticks) so the loop drops time instead of trying to simulate every missed tick. This trades a brief slow-motion blip for guaranteed recovery and matches standard game-loop practice.

**Regression test.** Unit test of an extracted accumulator-step helper: feed a single 2-second elapsed at speedMultiplier 4 and assert the number of update() calls is capped (e.g. <= 5) rather than ~480. Fails before the clamp.

**Verifier adjustment.** The mechanism is real and the quoted code matches exactly. Calibration: a transient long frame (one-off GC pause) produces a bounded burst of catch-up ticks the loop normally recovers from; a *permanent* unrecoverable spiral / visible freeze requires steady-state per-tick compute to exceed the speed-scaled budget (~16.6ms at 1x, ~4.16ms/tick at 4x), which is reachable on low-end devices with many entities at 4x but is the worst case, not the typical outcome. Severity stays Medium; the 'screen visibly freezes and never recovers' framing describes the worst case rather than the common one.

---

### 14. 🟡 MEDIUM / Confidence: Medium — Duplicate-row insert (no unique constraint + TOCTOU)

- **Category:** Bugs  
- **Surface:** data-room  
- **Files:** `domain/usecase/GenerateDailyMissions.kt`, `data/local/DailyMissionDao.kt`, `data/local/DailyMissionEntity.kt`  
- **Lines:** GenerateDailyMissions.kt:11-25; DailyMissionDao.kt:18-19 (insert); DailyMissionEntity.kt:7-9  

**Description.** GenerateDailyMissions guards on `if (dailyMissionDao.getByDateOnce(date).isNotEmpty()) return` then inserts 3 rows via a plain @Insert. DailyMissionEntity has an autoGenerate Int PK and NO unique index on `date` (or on date+missionType). If invoke(today) runs twice concurrently before the first insert batch commits, both pass the emptiness check and both insert — producing 6 (or more) daily-mission rows for the same day. The reward for those missions can then be completed/claimed multiple times (countClaimable counts all rows; markClaimed is per-id), inflating Gem/PowerStone payouts. MissionsViewModel.init launches generateMissions(today) and a separate updateWalkingMissionProgress; DailyStepManager's follow-on pipeline also touches missions — a config-change re-init or a second viewer can re-enter the generator.

**Evidence.** GenerateDailyMissions: `if (dailyMissionDao.getByDateOnce(todayDate).isNotEmpty()) return ... dailyMissionDao.insert(DailyMissionEntity(date=todayDate,...))`. DailyMissionDao.insert is `@Insert suspend fun insert(entity)` with no OnConflictStrategy. DailyMissionEntity: `@PrimaryKey(autoGenerate=true) val id: Int = 0, val date: String, ...` — no `indices=[Index(value=["date","missionType"], unique=true)]`.

**Verification (<2 min).** Call GenerateDailyMissions("2026-06-10") from two coroutines launched together against a real Room DB seeded empty. Then dailyMissionDao.getByDateOnce("2026-06-10") returns 6 rows instead of 3. Each can be claimed independently.

**Recommended fix.** Add a unique index (date + missionType, or just date if exactly one set per day) on daily_mission and use @Insert(onConflict=IGNORE) — plus wrap the check-then-insert in a single @Transaction default method so the emptiness check and the inserts are serialized. Requires a schema bump + migration adding the unique index (and dedup of any pre-existing duplicates, as the v10->v11 card migration did with GROUP BY).

**Regression test.** Robolectric Room test: run two concurrent GenerateDailyMissions(date); assert getByDateOnce(date).size == MissionCategory.entries.size (3), not 6.

---

### 15. 🟡 MEDIUM / Confidence: Medium — Engine init/update cross-thread structural mutation of shared list

- **Category:** Bugs  
- **Surface:** concurrency-async  
- **Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/BattleViewModel.kt`, `presentation/battle/GameLoopThread.kt`  
- **Lines:** GameEngine.kt:276-306 (init clears+rebuilds `entities`), :277 sets roundOver=false BEFORE rebuilding, :371-419 update() iterates/mutates `entities`; BattleViewModel.playAgain() -> surfaceView.configure -> engine.init on Main  

**Description.** GameEngine.init() runs on the main thread (via BattleViewModel.playAgain() -> GameSurfaceView.configure() -> engine.init()) while the GameLoopThread is still alive and looping (it is only stopped in surfaceDestroyed, not at round end). init() sets `roundOver = false` at line 277 but then continues to structurally rebuild the shared `entities` ArrayList (entities.add(zig) at :304, spawnOrbs() at :306+) AFTER clearing the roundOver guard. The game thread reads `roundOver` once per frame in update(); once it observes the freshly-cleared `false`, it proceeds into update() which structurally mutates the SAME ArrayList (entities.addAll(pendingAdd) at :390, entities.removeAll at :419) and iterates it (tickEntities, projectile-trail loop). Two threads structurally modifying one ArrayList → ConcurrentModificationException or a lost/partial entity (e.g. ziggurat dropped, immediate NPE on ziggurat==null). Window is the few microseconds between `roundOver=false` (:277) and `entities.add(zig)`/spawnOrbs completion.

**Evidence.** // GameEngine.init():
entities.clear(); pendingAdd.clear()        // :276
simulation.reset(); roundOver = false       // :277  <-- guard cleared too early
...
entities.add(zig)                            // :304  (game thread may already be in update())
spawnOrbs()                                  // :306
// GameLoopThread.run(): engine.update() runs unsynchronized on the loop thread each frame.

**Assumptions.** Assumes the GameLoopThread remains running after round end (confirmed — it is only torn down in surfaceDestroyed). The roundOver early-return narrows but does not close the window because init clears roundOver before finishing the rebuild.

**Verification (<2 min).** Mid-round, finish a round (HP->0) so PostRoundOverlay shows but the GameLoopThread keeps looping; tap Play Again repeatedly. Under load (4x speed, many entities) this can throw ConcurrentModificationException from entities iteration or surface a transient ziggurat-null NPE. Most reliably reproduced in a stress harness that calls engine.init() on one thread in a loop while another thread calls engine.update() with roundOver toggling.

**Recommended fix.** Set `roundOver = false` (and surface the engine as 'live') as the LAST statement of init(), after the entity list is fully rebuilt — so the game thread never sees a live engine with a half-built entity list. Better: pause/stop the GameLoopThread before re-init (configure could set isRunning=false, join, init, restart), or confine init() to the game-loop thread via a pending-init flag the loop drains at a safe point (mirrors the existing pendingSpeed/pendingPaused handoff pattern).

**Regression test.** GameEngineReinitRaceTest (Robolectric/JVM): run engine.update(0.016f) on a background thread in a tight loop while the test thread calls engine.init(...) repeatedly; assert no ConcurrentModificationException/NPE over N iterations (fails today, passes once roundOver is cleared last).

**Verifier adjustment.** The finding is real but its framing is slightly too narrow. The race is broader than just the "few microseconds between roundOver=false (line 277) and entities.add(zig) (line 304)". TWO unsynchronized cross-thread structural-mutation windows exist on every playAgain(): (1) GameLoopThread.run() calls engine.render() on EVERY loop iteration (GameLoopThread.kt:50-52), and render() iterates `entities` via `entities.forEach` (GameEngine.kt:433) — render() takes `synchronized(surfaceHolder)` but init() does NOT, so init()'s entities.clear()/add() (276/304/306) races render()'s iteration regardless of roundOver/isPaused; this window spans the full duration of init(). (2) the update() race the finding describes: after roundOver=false (277), the loop can pass `if (roundOver) return` (372) and reach Simulation.tickEntities (forEach, Simulation.kt:152), entities.addAll/removeAll (390/419) concurrently. The GameLoopThread is confirmed to keep running after round end (only stopped in surfaceDestroyed) and is NOT paused at post-round (runEndRoundPersistence sets isPaused=false, BattleViewModel.kt:382-384). `entities` is a plain ArrayList (mutableListOf, not thread-safe).

---

### 16. ⚪ LOW / Confidence: High — Off-by-one / duplicate-trigger on round start

- **Category:** Bugs  
- **Surface:** battle-engine  
- **Files:** `presentation/battle/engine/GameEngine.kt`  
- **Lines:** GameEngine.kt:294 (lastWave = 0), 308-329 (init triggers announcement for safeStartWave), 382-387 (update re-triggers because lastWave still 0)  

**Description.** The first-wave announcement fires twice on every round start (and every playAgain/surface replay). `init()` sets `lastWave = 0` then calls `triggerWaveAnnouncement(safeStartWave)` but never updates `lastWave` to `safeStartWave`. On the very first `update()` tick, `currentWave (= safeStartWave) != lastWave (= 0)` evaluates true, so it sets `lastWave = currentWave` and calls `triggerWaveAnnouncement(currentWave)` a second time for the same wave. Result: the WaveAnnouncement effect is added twice and `SoundEffect.WAVE_START` plays twice, and the cooldown-text effect is constructed twice. Observable as a doubled wave-start sound / stacked announcement overlay at the start of every round.

**Evidence.** init: lastWave = 0; ...; triggerWaveAnnouncement(safeStartWave)   // update(): val currentWave = waveSpawner?.currentWave ?: 1; if (currentWave != lastWave) { lastWave = currentWave; triggerWaveAnnouncement(currentWave) }

**Verification (<2 min).** Start any round with sound on; the wave-start sting plays twice in quick succession and two WaveAnnouncement overlays are spawned. Or unit-test: stub effectEngine, call init(), call update(0.016f) once, count WaveAnnouncement effects added == 2 (should be 1).

**Recommended fix.** In `init()` set `lastWave = safeStartWave` (instead of 0) after the initial `triggerWaveAnnouncement(safeStartWave)`, so the first `update()` does not re-detect a wave change for the starting wave.

**Regression test.** GameEngineTest with a counting fake EffectEngine: after init() + a single update(0.016f), assert exactly one WaveAnnouncement was emitted (and WAVE_START sound played once). Fails before the fix (count == 2).

---

### 17. ⚪ LOW / Confidence: High — Lifesteal granted on fully armor-absorbed hits (economy/correctness)

- **Category:** Bugs  
- **Surface:** battle-engine  
- **Files:** `presentation/battle/engine/GameEngine.kt`, `presentation/battle/entities/EnemyEntity.kt`  
- **Lines:** GameEngine.kt:876-895 (onProjectileHitEnemy: lifesteal uses result.amount), 731-743 (onOrbHitEnemy: lifesteal uses damage); EnemyEntity.kt:65-69 (takeDamage returns early when armorHits>0)  

**Description.** `EnemyEntity.takeDamage` consumes an armor hit and returns WITHOUT applying any HP damage when `armorHits > 0`. But the lifesteal heal in both `onProjectileHitEnemy` and `onOrbHitEnemy` is computed from the intended damage (`result.amount` / `damage`) regardless of whether the hit actually dealt damage. So against armored enemies (present at Tier conditions with `armorHits > 0`) the ziggurat heals from lifesteal on hits that did zero damage. Knockback is similarly applied to enemies whose hit was fully absorbed. Minor, but it is free healing/CC the design intends to be gated on damage dealt.

**Evidence.** enemy.takeDamage(result.amount) ... if (stats.lifestealPercent > 0) applyLifesteal(result.amount * stats.lifestealPercent)   // takeDamage: if (armorHits > 0) { armorHits--; return } — no damage dealt, yet lifesteal/knockback still fire

**Verification (<2 min).** Set a tier with armorHits>0 and equip LIFESTEAL. Fire at a freshly-spawned armored enemy (first hit absorbed). Ziggurat HP ticks up via the lifesteal floating text even though the enemy's HP/armor bar shows the hit was absorbed.

**Recommended fix.** Have `takeDamage` return the actual damage dealt (0 when absorbed), and gate lifesteal/knockback on `dealt > 0`. Or check `armorHits` before applying lifesteal. Keeps lifesteal proportional to real damage.

**Regression test.** GameEngineTest: spawn an enemy with armorHits=1, equip LIFESTEAL, record ziggurat HP, fire one projectile (absorbed by armor), assert ziggurat HP unchanged. Fails before the fix.

---

### 18. ⚪ LOW / Confidence: High — Per-frame list allocations in hot UW loop

- **Category:** Performance  
- **Surface:** battle-engine  
- **Files:** `presentation/battle/engine/GameEngine.kt`  
- **Lines:** GameEngine.kt:618-647 (updateUWs ongoing effects + auto-trigger), 728-729 (getAliveEnemies), 981-989 (findNearestEnemies)  

**Description.** `getAliveEnemies()` (and `findNearestEnemies`) allocate fresh lists via `filterIsInstance<EnemyEntity>().filter{}` on every call. In `updateUWs` this runs up to three times per frame (BLACK_HOLE pull, POISON_SWAMP DoT, and the auto-trigger `getAliveEnemies().isNotEmpty()` guard) plus once per ziggurat shot via `findNearestEnemies`. At 60fps with up to 40 enemies and multiple equipped UWs, this is steady allocation churn feeding the young-gen GC, which on a battle SurfaceView directly correlates with frame hitches (and, per finding above, can tip the unclamped loop into catch-up). Caching one alive-enemy snapshot per frame and reusing it across the UW loop eliminates most of it.

**Evidence.** private fun getAliveEnemies(): List<EnemyEntity> = entities.filterIsInstance<EnemyEntity>().filter { it.isAlive }   // called 3x in updateUWs each frame; if (uwStates.isNotEmpty() && getAliveEnemies().isNotEmpty()) { ... }

**Verification (<2 min).** Profile a high-wave round (Android Studio allocation tracker or `adb shell dumpsys gfxinfo`); observe per-frame allocations scaling with enemy count and UW count, and GC pauses correlating with frame drops.

**Recommended fix.** Compute the alive-enemy list once at the top of update()/updateUWs() and pass it to the ongoing-effect branches and the auto-trigger guard; avoid recomputing per UW. Consider reusing a scratch list to avoid allocation entirely.

**Regression test.** Not a behavioural test; add a microbenchmark or assert getAliveEnemies is invoked once per frame after refactor (e.g. a counting wrapper in a test). Primarily a profiling-verified perf fix.

**Verifier adjustment.** The allocation pattern is real but the "runs up to three times per frame" framing is an upper bound, not steady-state. Per frame in updateUWs, getAliveEnemies() is guaranteed to run only ONCE — the auto-trigger guard at GameEngine.kt:647 (`if (uwStates.isNotEmpty() && getAliveEnemies().isNotEmpty())`), and only when at least one UW is equipped. The BLACK_HOLE (line 624) and POISON_SWAMP (line 635) calls run only while those specific UW ongoing-effects are active (gated by the effectWasActive timer block), not unconditionally. findNearestEnemies (line 981) is NOT called every frame — ZigguratEntity.update only invokes it when the attack cooldown is ready (ZigguratEntity.kt:72, gated by attackInterval), so it fires at attack-speed rate. Each getAliveEnemies() call does allocate two intermediate lists (filterIsInstance produces one ArrayList, then .filter another), and findNearestEnemies materializes via sortedBy + toList. The frame-amplification point is correct and worth noting: GameLoopThread runs a fixed-timestep catch-up loop (TICK_NS = ~60 UPS, lines 40-43) with no max-iteration clamp, so at speedMultiplier 2x/4x engine.update() — and thus these allocations — run 2-4x per render frame.

---

### 19. ⚪ LOW / Confidence: High — TOCTOU / atomic-guard return value ignored — item/level granted regardless of whether the currency was actually spent

- **Category:** Bugs  
- **Surface:** domain-economy  
- **Severity:** Low *(adjusted down from High by adversarial verification)*  
- **Files:** `domain/repository/PlayerRepository.kt`, `data/repository/PlayerRepositoryImpl.kt`, `domain/usecase/OpenCardPack.kt`, `domain/usecase/StartResearch.kt`, `domain/usecase/RushResearch.kt`, `domain/usecase/UnlockUltimateWeapon.kt`, `domain/usecase/UpgradeUltimateWeapon.kt`, `domain/usecase/UnlockLabSlot.kt`  
- **Lines:** PlayerRepository.kt:14-16; PlayerRepositoryImpl.kt:33-42; OpenCardPack.kt:27-29; StartResearch.kt:36-42; UnlockUltimateWeapon.kt:24-28; UpgradeUltimateWeapon.kt:28-33  

**Description.** Only PurchaseUpgrade uses the SQL-guarded atomic spend (WorkshopDao.purchaseUpgradeAtomic, which checks rowsAffected). Every other economy use case follows the pattern: (1) compare cost against a stale wallet/gems/powerStones SNAPSHOT passed as a parameter, then (2) call PlayerRepository.spend{Gems,PowerStones,Steps}() which returns Unit, then (3) unconditionally grant the reward (open pack, start research, complete rush, unlock UW, upgrade UW path level, unlock lab slot). The repository's spendGems/spendPowerStones DO run an atomic SQL guard (spendGemsAtomic / spendPowerStonesAtomic with WHERE gems >= :amount returning rowsAffected), but PlayerRepositoryImpl discards that Int return, and spendSteps uses adjustStepBalance(-amount) which clamps to MAX(0, balance-amount) with no guard at all. Because the interface return type is Unit, NO caller can observe an insufficient-funds failure at spend time. Net effect: if the snapshot is stale (gems/PS/steps already spent on another screen, or two coroutines racing), the pre-check passes, the atomic deduct silently no-ops (or step balance silently floors at 0), and the player still receives the item/level/research for free. The UW screen is the most reachable: UltimateWeaponViewModel.unlock/upgrade launch a new coroutine per tap with NO _processing guard, so a double-tap on Upgrade fires two coroutines reading the same powerStones snapshot and the same currentPathLevel — both call spendPowerStonesAtomic and both call upgradePathLevel(type, path, currentPathLevel+1), desyncing paid Power Stones from the level actually written.

**Evidence.** PlayerRepositoryImpl.spendGems: `dao.spendGemsAtomic(amount)` (return Int discarded). OpenCardPack.invoke: `if (!isFree && gems < packTier.gemCost) return InsufficientGems; if (!isFree) playerRepository.spendGems(packTier.gemCost); ... repeat(3){ ... addCard/incrementCopyCount }` — cards granted with no check that the spend succeeded. UpgradeUltimateWeapon.invoke: `if (powerStones < cost) return false; if (cost>0) playerRepository.spendPowerStones(cost.toLong()); uwRepository.upgradePathLevel(type, path, currentPathLevel+1); return true` — level write is unconditional on spend success. UltimateWeaponViewModel.upgrade: `viewModelScope.launch { upgradeUW(type, path, pathInfo.level, uiState.value.powerStones) }` (no _processing guard).

**Assumptions.** Reachability for the single-screen cases requires a stale snapshot (currency spent elsewhere between the StateFlow emission and the tap, or a background credit/spend). The UW double-tap case is reachable today because UltimateWeaponViewModel has no re-entrancy guard. FakePlayerRepository.spendGems mirrors production by clamping at maxOf(0, ...) and returning Unit, so the exploit reproduces in a pure JVM test.

**Verification (<2 min).** Unit test on OpenCardPack with a fake CardRepository: set FakePlayerRepository profile gems=10, call openCardPack(PackTier.COMMON /*cost 50*/, gems = 1000 /*stale snapshot*/). Observe: result is Opened, 3 cards added, and profile.gems is floored at 0 — the player received a 50-gem pack for free. Or on the device: open Weapons screen with just enough Power Stones for one upgrade and rapidly double-tap an Upgrade button — observe the path level advance once while Power Stones are deducted twice (or vice-versa).

**Recommended fix.** Change PlayerRepository.spend{Gems,PowerStones,Steps} to return Boolean (rowsAffected == 1), have PlayerRepositoryImpl propagate the atomic SQL result (and add an adjustStepBalanceIfSufficient-backed spendSteps), and make every consuming use case return its failure variant (InsufficientGems / InsufficientSteps / false) when the spend returns false — granting the reward ONLY when the deduct succeeded. Mirror the PurchaseUpgrade atomic pattern. Also add a _processing/in-flight guard to UltimateWeaponViewModel.unlock/upgrade like CardsViewModel/LabsViewModel already have.

**Regression test.** OpenCardPackTest `stale snapshot does not grant a free pack`: gems=10 on disk, call invoke(COMMON, gems=1000); assert result is InsufficientGems AND cardRepository.addCard was never called. UpgradeUltimateWeaponTest `insufficient power stones does not advance path level`: powerStones=0 on disk but pass snapshot powerStones=999; assert returns false AND uwRepository.upgradePathLevel was not called.

**Verifier adjustment.** The core mechanism is real and confirmed: PlayerRepository.spendGems/spendPowerStones return Unit and PlayerRepositoryImpl (lines 33-42) discards the Int rowsAffected from spendGemsAtomic/spendPowerStonesAtomic; spendSteps uses the unguarded adjustStepBalance(-amount) which floors at MAX(0,...) (DAO line 41) even though a guarded adjustStepBalanceIfSufficient exists (line 53) but is unused. All economy use cases except PurchaseUpgrade compare a stale snapshot parameter, call a Unit-returning spend, then unconditionally grant the reward. So no caller can detect an insufficient-funds failure at spend time. HOWEVER, the finding materially overstates reachability/breadth. The double-tap concurrency race is NOT reachable for OpenCardPack, StartResearch, RushResearch, or UnlockLabSlot: CardsViewModel and LabsViewModel both guard every spend entry point with a synchronous `if (_processing.value) return` before launching, and viewModelScope runs on Dispatchers.Main.immediate so the guard serializes re-entrant taps. Only UltimateWeaponViewModel.unlock/upgrade (lines 97-107) lacks this guard, making the UW screen the sole concretely-reachable concurrency path. Even there, the dominant outcome is the OPPOSITE of the finding's headline: because upgradePathLevel writes an absolute newLevel (currentPathLevel+1, RepositoryImpl lines 40-56), two coroutines both reading level L both write L+1 (idempotent level) while both spendPowerStonesAtomic deducts succeed when funds exist — i.e. the player is OVER-charged two stones for one level, not granted a level for free. The 'free grant' direction requires a stale snapshot where the atomic no-ops but the grant still writes, which is a narrower window (currency already drained elsewhere). This is a genuine robustness defect — a Unit return type that throws away the SQL guard's signal, plus a missing re-entrancy guard on one screen that every sibling screen has — but it is a Low-severity edge case, not a High-severity free-economy exploit.

---

### 20. ⚪ LOW / Confidence: High — Overloaded field — CARD_COPY rewardAmount is a card-type index at generation/claim time but rendered as a quantity in the UI

- **Category:** Bugs  
- **Surface:** domain-economy  
- **Files:** `domain/usecase/GenerateSupplyDrop.kt`, `domain/usecase/ClaimSupplyDrop.kt`, `presentation/supplies/UnclaimedSuppliesScreen.kt`  
- **Lines:** GenerateSupplyDrop.kt:67 (random.nextInt(0,9)); ClaimSupplyDrop.kt:27-31 (rewardAmount % entries.size as index, awards exactly 1 copy); UnclaimedSuppliesScreen.kt:105 ("+${drop.rewardAmount} $label")  

**Description.** For SupplyDropReward.CARD_COPY, GenerateSupplyDrop.rollRandomReward stores `random.nextInt(0, 9)` (0..8) in `rewardAmount`, and ClaimSupplyDrop interprets that field as a card-type index (`CardType.entries[rewardAmount % entries.size]`) while always awarding exactly ONE copy via addCardOrIncrementCopy. But UnclaimedSuppliesScreen renders the same field as a count: `"+${drop.rewardAmount} Card Copy"`. So a CARD_COPY drop displays as "+0 Card Copy" through "+8 Card Copy" in the inbox while the player actually always receives 1 copy of a (pseudo-random) card. "+0 Card Copy" in particular reads as a broken/empty reward.

**Evidence.** GenerateSupplyDrop.kt:67 `else -> makeDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.CARD_COPY, random.nextInt(0, 9), timestampMs)`. ClaimSupplyDrop.kt:29 `val cardType = CardType.entries[drop.rewardAmount % CardType.entries.size]` then `cardRepository.addCardOrIncrementCopy(cardType)` (1 copy). UnclaimedSuppliesScreen.kt:105 `return "+${drop.rewardAmount} $label"`.

**Assumptions.** addCardOrIncrementCopy increments copyCount by exactly 1 (verified in CardRepositoryImpl). CARD_COPY drops are reachable from the RANDOM 1%-per-500-steps path.

**Verification (<2 min).** Generate a RANDOM CARD_COPY drop (force the `else` branch / nextInt(4)==3 then nextInt(0,9)==0), open the Unclaimed Supplies inbox: the row shows "+0 Card Copy". Claim it: exactly 1 copy of CardType.entries[0] (IRON_SKIN) is added.

**Recommended fix.** Stop overloading rewardAmount. Either add a dedicated cardTypeIndex/cardType column to SupplyDrop and make rewardAmount the real copy count (always 1 for CARD_COPY), or special-case the UI label so CARD_COPY renders the resolved card name and a fixed "x1" instead of the raw index. The display string must not multiply by the index.

**Regression test.** UnclaimedSuppliesScreen label test (or a pure formatter unit test): a SupplyDrop(reward=CARD_COPY, rewardAmount=0) must NOT render "+0 Card Copy"; it should render the card name with quantity 1. ClaimSupplyDropTest already proves 1 copy is awarded regardless of rewardAmount — assert the displayed amount and the claimed amount agree.

---

### 21. ⚪ LOW / Confidence: High — Description/effect mismatch — IRON_SKIN labeled as a percentage but applied as flat defense

- **Category:** Bugs  
- **Surface:** domain-economy  
- **Files:** `domain/usecase/ApplyCardEffects.kt`, `domain/model/CardType.kt`, `domain/usecase/CalculateDefense.kt`  
- **Lines:** ApplyCardEffects.kt:27; CardType.kt:15 (IRON_SKIN "+10% Defense Absolute", valueLv1=10.0); CalculateDefense.kt:8-9  

**Description.** IRON_SKIN's description is "+10% Defense Absolute" (and effectDescriptionAtLevel renders "+v% Defense Absolute"), but ApplyCardEffects applies it as a FLAT additive: `defenseAbsolute = s.defenseAbsolute + v` where v is 10..42. CalculateDefense uses defenseAbsolute as flat damage blocked: `incomingDamage*(1-defensePercent) - defenseAbsolute`. So IRON_SKIN at L1 adds 10 flat blocked damage, not 10% of anything. The '%' in the label is a unit error — the player sees a percentage but gets a flat value. This either over- or under-delivers relative to the displayed promise depending on enemy damage scale, and is confusing at high tiers where 42 flat blocked is negligible against thousands of incoming damage.

**Evidence.** CardType.kt: `IRON_SKIN(CardRarity.COMMON, "+10% Defense Absolute", "+42% Defense Absolute", 10.0, 42.0)`. ApplyCardEffects.kt: `CardType.IRON_SKIN -> s = s.copy(defenseAbsolute = s.defenseAbsolute + v)`.

**Assumptions.** Treating defenseAbsolute as flat is consistent with CalculateDefense and the upgrade DEFENSE_ABSOLUTE ('+flat damage blocked per hit'), so the math is internally consistent; only the card label is wrong. Marked Medium confidence because '%' could be a deliberate shorthand the team accepts.

**Verification (<2 min).** Equip IRON_SKIN L1, inspect ResolvedStats after ApplyCardEffects: defenseAbsolute increased by exactly 10.0 (flat). Compare to the "+10%" label shown on the card.

**Recommended fix.** Fix the labels to match the math ("+10 Defense Absolute" — drop the '%'), updating both effectLv1/effectLv7 strings and effectDescriptionAtLevel's IRON_SKIN branch. Do NOT change the gameplay math unless the design intends a percentage scaling, which would require a different formula.

**Regression test.** CardTypeTest `IRON_SKIN description has no percent sign` and ApplyCardEffectsTest asserting defenseAbsolute delta equals valueAtLevel exactly (flat). The label test fails before the fix.

**Verifier adjustment.** The mechanism is exactly as described, but it is a player-facing label/unit mismatch only, not a gameplay-logic bug. IRON_SKIN is labeled "+10% Defense Absolute" (CardType.kt:15) and effectDescriptionAtLevel renders "+v% Defense Absolute" (CardType.kt:62), yet ApplyCardEffects.kt:27 adds the raw value (10..42) directly to the flat defenseAbsolute field — `s.copy(defenseAbsolute = s.defenseAbsolute + v)` — with NO `/100` division, unlike every genuine percent card (SHARP_SHOOTER/VAMPIRIC_TOUCH use v/100.0; WALKING_FORTRESS/GLASS_CANNON use 1±v/100.0). CalculateDefense.kt:9 subtracts defenseAbsolute flatly: `incomingDamage*(1-defensePercent) - defenseAbsolute`. defenseAbsolute is definitionally the flat "+flat damage blocked per hit" field (UpgradeType.kt:65, DescribeUpgradeEffect.kt:118 "+N blocked"); the true percentage path is defensePercent. So the engine math is internally consistent and matches the design intent of a flat block — only the `%` glyph in the displayed string is wrong. This is a UX/copy defect, not an incorrect calculation.

---

### 22. ⚪ LOW / Confidence: High — Dead computation masking intended threshold logic in supply-drop check count

- **Category:** Bugs  
- **Surface:** domain-economy  
- **Files:** `domain/usecase/GenerateSupplyDrop.kt`  
- **Lines:** GenerateSupplyDrop.kt:38-39  

**Description.** In the STEP_THRESHOLD branch, `checks` is computed as `((stepsAfterBoundary + delta).coerceAtMost(delta) / 100).coerceAtLeast(1)`. Because `stepsAfterBoundary` is always >= 0, `(stepsAfterBoundary + delta).coerceAtMost(delta)` always equals `delta`, so the whole expression reduces to `(delta / 100).coerceAtLeast(1)` and `stepsAfterBoundary` (computed on line 38) is dead. The intent (judging by the variable name) appears to be to count only the 100-step sub-intervals that fall *after* the crossed 2,000-step boundary, but the coerceAtMost collapses that to the full delta. Result: a player who crosses a 2,000-step boundary gets `delta/100` reward rolls at 5% each rather than the (likely intended) smaller post-boundary count — i.e. the threshold reward fires slightly more often than designed, and the boundary-relative math is silently inert.

**Evidence.** `val stepsAfterBoundary = dailyCreditedSteps - (currBoundary * STEP_THRESHOLD_INTERVAL); val checks = ((stepsAfterBoundary + delta).coerceAtMost(delta) / 100).coerceAtLeast(1)` — coerceAtMost(delta) always selects delta since stepsAfterBoundary >= 0.

**Assumptions.** STEP_THRESHOLD_INTERVAL=2000, THRESHOLD_CHANCE_PER_100=0.05. The drop rate impact is small (economy is gem/step trickle) so severity is Low, but the dead boundary math indicates the implemented cadence diverges from the named design intent.

**Verification (<2 min).** Unit-test GenerateSupplyDrop with a deterministic Random; with lastCheckSteps=1900, dailyCreditedSteps=2300 (delta=400, crosses the 2000 boundary), `checks` evaluates to 4 regardless of stepsAfterBoundary(=300). Removing the `stepsAfterBoundary` term produces the identical result, proving it is dead.

**Recommended fix.** Decide the intended semantics and make the code reflect it: if only post-boundary sub-intervals should roll, use `(stepsAfterBoundary / 100).coerceAtLeast(1)`; if the full delta should roll, delete the dead `stepsAfterBoundary` line and `.coerceAtMost(delta)`. Add a comment with the chosen reward cadence so it can be balance-tested.

**Regression test.** GenerateSupplyDropTest pinning the number of 5% rolls for a known (lastCheckSteps, dailyCreditedSteps) pair across a boundary crossing — fails if the check-count formula changes from the intended cadence.

---

### 23. ⚪ LOW / Confidence: High — Missing migration runtime test (data-transform unverified)

- **Category:** Technical Debt  
- **Surface:** data-room  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/local/Migrations.kt`, `data/local/RoomSchemaTest.kt`  
- **Lines:** Migrations.kt:81-129 (MIGRATION_9_10), 136-174 (MIGRATION_10_11); RoomSchemaTest.kt (no MigrationTestHelper)  

**Description.** There is no Room MigrationTestHelper-based test, despite room-testing being on the classpath (gradle/libs.versions.toml:54). Room's compile-time schema validation only checks that the FINAL entity set matches the version-N export hash; it does NOT execute the migrations against real prior-version data, nor verify the migrations even run without SQL error. The two recreate-table dances are non-trivial and irreversible if wrong: MIGRATION_9_10 splits `level` across damage/secondary/cooldown via integer division and DROPs/renames ultimate_weapon_state; MIGRATION_10_11 aggregates card_inventory by cardType with GROUP BY MAX(level)/MAX(isEquipped)/COUNT(*) then DROPs/renames and adds a unique index. A subtle data-transform bug (e.g. losing the highest-leveled duplicate card's equip state, or an isUnlocked seeding error) would ship undetected because the only test (RoomSchemaTest) builds a fresh in-memory DB at the current version and never runs a migration. The DDL itself currently matches the exports (I verified column order/types/NOT NULL/defaults against schemas/9-11.json), but the transform semantics are untested.

**Evidence.** RoomSchemaTest.setup uses `Room.inMemoryDatabaseBuilder(...).build()` (fresh current-version schema, no .addMigrations, no MigrationTestHelper). No file references MigrationTestHelper anywhere in the repo (grep returned none). Migrations.kt:153-159 INSERT...SELECT ... GROUP BY cardType is executed only on real upgrade installs, which CI never exercises.

**Verification (<2 min).** Add an androidTest MigrationTestHelper test: create v9 DB, insert an ultimate_weapon_state row with level=5, run MIGRATION_9_10, assert damageLevel=2/secondaryLevel=2/cooldownLevel=1/isUnlocked=1; create v10 DB with two card_inventory rows of the same cardType (levels 3 and 5, one equipped), run MIGRATION_10_11, assert a single row with level=5/isEquipped=1/copyCount=2. These currently have zero coverage.

**Recommended fix.** Add a MigrationTestHelper instrumented test exercising MIGRATION_7_8, 8_9, 9_10, 10_11 with representative prior-version data and assert both the post-migration schema (helper does this automatically) AND the transformed row values. Run it in the existing connected/instrumented CI lane. This is the standard guard for recreate-table migrations.

**Regression test.** MigrationTestHelper test as described above — it fails today purely by being absent (no coverage); after authoring, deliberately break MIGRATION_9_10's split formula and confirm the test catches it.

**Verifier adjustment.** The finding is factually accurate as a test-gap / technical-debt item; I am only down-tuning severity. It is NOT a confirmed runtime defect (the finding itself concedes the DDL matches the v9/v10/v11 exports and only the transform semantics are untested). The migrations have already shipped through v16 to a closed track, so the transforms are field-proven on at least the developer's own install — there is no demonstrated incorrect output. The residual risk is forward-looking: a future edit or a subtle aggregation-semantic choice could regress undetected. That is a Low-severity coverage gap, not a Medium-severity active risk.

---

### 24. ⚪ LOW / Confidence: High — Anti-cheat rate-limit bypass via clock manipulation / batched timestamps

- **Category:** Security  
- **Surface:** sensor-anticheat  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/sensor/StepRateLimiter.kt`, `data/sensor/DailyStepManager.kt`, `service/StepCounterService.kt`  
- **Lines:** StepRateLimiter.kt:33-54; DailyStepManager.kt:122,152; StepCounterService.kt:61  
- **Source→Sink:** User-controllable system clock (Settings/auto-time-off) -> StepCounterService now=System.currentTimeMillis() -> StepRateLimiter.credit window keying -> wallet credit (rate cap bypassed)  

**Description.** The rate limiter and per-minute tracking key off System.currentTimeMillis() (wall clock), supplied by the caller (StepCounterService passes `now = System.currentTimeMillis()`). Wall clock is user-controllable (Settings > Date & time, or via a rooted/auto-time-off device). An abuser who advances the system clock forward between sensor batches gives every event a fresh, far-apart timestamp -> the rate-limiter window (60s, evicts entries older than now-60s) is always empty -> the full rawDelta is credited every call, defeating the 200-300 steps/min cap. Separately, the BURST_CAP (250) is granted whenever `timestampMs - firstEntryMs < 5min`; firstEntryMs is reset every time the window empties, so an attacker producing one event, waiting >60s (or jumping the clock), and repeating keeps firstEntryMs fresh and stays in the 250-cap burst tier indefinitely. The 50k/day ceiling still bounds total abuse, but the per-minute rate limit (a core anti-cheat control) is bypassable.

**Evidence.** StepRateLimiter.credit uses `timestampMs` (wall clock) for cutoff/eviction; DailyStepManager passes the caller's timestampMs straight through; StepCounterService uses System.currentTimeMillis(). Velocity analyzer also keys off the same caller-supplied timestamp, so spaced/jumped timestamps also defeat the constant-rate and instant-jump heuristics.

**Verification (<2 min).** Call rateLimiter.credit(10000, t) then credit(10000, t + 61_000) then t + 122_000 ... each returns up to the cap because the window evicts the prior entry; on a device, toggle automatic time off and jump the clock forward between collects to feed spaced timestamps and observe the per-minute cap never engaging.

**Recommended fix.** Use a monotonic clock (SystemClock.elapsedRealtime()) for rate-limit windowing instead of wall clock, so forward clock jumps cannot manufacture fresh windows. Detect non-monotonic / large wall-clock jumps and treat the batch conservatively (reject or escrow). Keep the 50k ceiling as the backstop but do not let the per-minute control be defeated by user-settable time.

**Regression test.** StepRateLimiterTest: feed credit(10000, t), credit(10000, t+61000), credit(10000, t+122000) and assert the SUM credited over the simulated >3-minute span is bounded by the intended cap when windowing uses a monotonic source (currently it credits ~10000 each call).

**Verifier adjustment.** The mechanism is real and reachable, but the impact is narrower than "rate cap bypassed -> arbitrary step inflation." Clock manipulation cannot fabricate steps: rawDelta originates from the hardware TYPE_STEP_COUNTER sensor (StepSensorDataSource.kt:39-47, delta = cumulative - lastCumulative), which counts real physical steps and is unaffected by the wall clock. What an attacker (or even a legitimate heavy-batch user) actually gains by advancing System.currentTimeMillis() between sensor batches is: (1) defeat of the 200/250-per-minute throttle in StepRateLimiter.credit (the 60s window is always empty so the full rawDelta is credited and the firstEntryMs reset-on-empty keeps them in the 250 BURST_CAP tier permanently), and (2) defeat of the StepVelocityAnalyzer constant-rate (CV) and instant-jump heuristics, both of which key off the same caller-supplied timestamp. So the affected control is the secondary anti-cheat throttle/heuristic layer, not a primary step-integrity gate. The 50,000/day ceiling (DailyStepManager.kt:140-141) remains a clock-independent absolute backstop on daily gain (dailyCreditedTotal is an in-process accumulator). Root cause is correct: a security-relevant time window is keyed off the user-manipulable wall clock (System.currentTimeMillis) instead of a monotonic source (SystemClock.elapsedRealtime). Severity adjusted Medium -> Low because the bypass only weakens a throttle layered behind the hardware sensor and the hard daily ceiling, steps are non-purchasable, and all monetization is cosmetic/convenience.

---

### 25. ⚪ LOW / Confidence: High — Replay across reinstall — purchaseToken idempotency is local-only

- **Category:** Security  
- **Surface:** security  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/billing/BillingManagerImpl.kt`, `data/local/BillingReceiptDao.kt`, `data/local/DatabaseKeyManager.kt`  
- **Lines:** BillingReceiptDao.kt:94-105 (grantOnceAtomic dedup keyed on local row); BillingManagerImpl.kt:278-315 (reconcileType re-credits any PURCHASED not in local table); DatabaseKeyManager.kt:42-58 (decrypt-fail wipes DB incl. billing_receipt)  
- **Source→Sink:** Source: DB/receipt-table wipe (Keystore decrypt fail in DatabaseKeyManager OR clear app data) -> billing_receipt rows lost -> StoreViewModel.init -> reconcilePendingPurchases -> queryPurchases returns still-owned purchase -> grantOnceAtomic sees no prior row -> creditWallet. Sink: PlayerProfileDao.adjustGems (duplicate Gem grant).  

**Description.** Double-credit protection (grantOnceAtomic) relies entirely on the local billing_receipt table, which lives inside the SQLCipher DB. There are two ways to reset that table and re-trigger a grant for a real purchase: (1) android:allowBackup=false prevents cloud backup, but a clear-app-data, OR a DatabaseKeyManager recovery-wipe (any Keystore decrypt failure deletes steps_of_babylon.db, taking billing_receipt with it), erases all granted=true rows; on next launch reconcilePendingPurchases() re-queries Play, sees the still-owned non-consumed purchase, and re-runs creditWallet. For the non-consumable AD_REMOVAL and the SEASON_PASS subscription this is harmless (idempotent flag set). But for GEM packs the consumable is only consumed AFTER the local grant; if consume succeeded on the first install, Play no longer reports the purchase so no re-credit happens — the real risk is the window where consume failed/was pending (granted=true, consumed=false) and then the DB is wiped: the receipt is gone, Play still reports the consumable as owned, and reconcile credits the Gems a second time. An attacker can deliberately induce the Keystore-wipe (clear the Keystore via a factory-key-rotation or by triggering the decrypt-fail path) to farm a re-credit on a stuck consumable. Root cause: dedup state is co-located with wipeable game state and there is no server/Play-Developer-API to anchor 'already granted'.

**Evidence.** DatabaseKeyManager.getPassphrase catch block: `prefs.edit().clear().apply(); wipeDatabaseFile(context)` — deletes steps_of_babylon.db which contains the billing_receipt table. BillingManagerImpl.reconcileType: for any PURCHASED purchase it unconditionally calls grantOnceAtomic which, with the receipt row gone, sees existing==null and credits.

**Assumptions.** Requires either user-triggerable clear-data or the Keystore-decrypt-fail wipe path plus a consumable stuck in granted-but-not-consumed. Narrower than finding 1, hence Medium.

**Verification (<2 min).** Input: buy GEM_PACK_LARGE, force consume to fail (offline) so receipt is granted=true,consumed=false. Trigger: invoke DatabaseKeyManager.wipeDatabaseFile (or clear app data), relaunch, StoreViewModel.init calls reconcilePendingPurchases(). Observable: Gems credited again (+700) for one paid purchase. Reproduce in test by deleting the receipt row then calling reconcilePendingPurchases with the same SdkPurchase still PURCHASED.

**Recommended fix.** Persist the billing-idempotency ledger OUTSIDE the wipeable DB (a small separate SharedPreferences or a non-encrypted Room file that is never deleted by the recovery-wipe), or consume consumables BEFORE crediting where Play's own ownership state becomes the dedup anchor (note: this reorders the documented invariant and needs care). At minimum, exclude billing_receipt from the recovery wipe by storing it in a second database file the wipe path leaves intact.

**Regression test.** BillingManagerImplTest: `reconcile after receipt-table wipe does not re-credit a previously granted consumable` — seed wallet 700 + delete the receipt row (simulating wipe), stub queryPurchases to still return the PURCHASED gem pack, call reconcilePendingPurchases(), assert gems stays 700. Fails today (re-credits to 1400).

**Verifier adjustment.** The mechanism is accurate as described, but the severity is overstated. It should be Low, not Medium. The re-credit is real and reachable only in a narrow conjunction (stuck consumable + DB wipe), and the impact is duplicating a single-player, non-real-money currency the user already paid for.

---

### 26. ⚪ LOW / Confidence: High — Reward-ad grant trust without Server-Side Verification (SSV)

- **Category:** Security  
- **Surface:** security  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/ads/RewardAdManagerImpl.kt`, `data/ads/internal/RealRewardedAdAdapter.kt`  
- **Lines:** RewardAdManagerImpl.kt:88-121 (showRewardAd maps SdkAdShowResult.Rewarded -> AdResult.Rewarded); RealRewardedAdAdapter.kt:104-132 (rewarded flag set in client-side OnUserEarnedRewardListener)  
- **Source→Sink:** Source: attacker-controlled OnUserEarnedRewardListener / hooked RewardedAdAdapter -> rewarded AtomicBoolean -> SdkAdShowResult.Rewarded -> RewardAdManagerImpl AdResult.Rewarded -> ViewModel reward grant. Sink: PlayerProfileDao gem/power-stone/card-pack credit.  

**Description.** Reward attribution is entirely client-side: the rewarded AtomicBoolean is flipped inside the on-device OnUserEarnedRewardListener and that flag alone decides whether AdResult.Rewarded (which the ViewModel turns into Gems / a double-Power-Stone / a free card pack) is returned. AdMob's Server-Side Verification callback is not used. On a rooted device or instrumented build, a user can hook OnUserEarnedRewardListener / FullScreenContentCallback to fire onUserEarnedReward immediately (or stub the whole RewardedAdAdapter) and harvest the reward without watching — or even without a real ad load. The code is correct relative to its stated design (reward only on Rewarded, never on Dismissed — RealRewardedAdAdapter.kt:106-112 is the right footgun-avoidance), but the trust boundary is the device, so the economy reward (Gems are premium currency) is forgeable. The KDoc (ADR-0006) chose no SSV; for a Gem-granting placement (POST_ROUND_GEM, POST_ROUND_DOUBLE_PS) this is an economy-integrity gap.

**Evidence.** RealRewardedAdAdapter.kt:122-130 `rewardedAd.show(activity, OnUserEarnedRewardListener { _ -> rewarded.set(true) })` then onAdDismissedFullScreenContent completes with `if (rewarded.get()) Rewarded else Dismissed`. RewardAdManagerImpl.kt:113-115 `is SdkAdShowResult.Rewarded -> AdResult.Rewarded`. No verification, no SSV, no nonce.

**Assumptions.** Requires a rooted/instrumented device to hook the SDK (malicious-local-user threat model). Stock devices get AdMob's own anti-fraud. Medium because the reward magnitude is bounded and per-placement frequency-capped at the ViewModel.

**Verification (<2 min).** Input: hooked/mocked RewardedAdAdapter.showAd that returns SdkAdShowResult.Rewarded without showing an ad. Trigger: showRewardAd(AdPlacement.POST_ROUND_GEM). Observable: AdResult.Rewarded -> ViewModel grants Gems with no ad watched. In test: stub adapter.showAd doReturn SdkAdShowResult.Rewarded and assert RewardAdManagerImpl returns AdResult.Rewarded — proving the only gate is the client flag.

**Recommended fix.** For the Gem/Power-Stone reward placements, enable AdMob Server-Side Verification (set a custom data / user id on the RewardedAd and grant only after the SSV callback hits your endpoint) — this is the documented anti-fraud mechanism and does not require a full game backend, only a lightweight verification receiver. If SSV is out of scope for v1.0, accept the risk explicitly in the ADR and keep the reward magnitude low. At minimum, treat reward-ad Gems as lower-value than purchased Gems in balancing so a forge is not lucrative.

**Regression test.** RewardAdManagerImplTest: document the trust assumption with a test that asserts `Rewarded comes straight from the adapter with no further check` so a future reviewer sees the gap; the real fix (SSV) is integration-tested on device. The forge itself (adapter returns Rewarded with no ad) is already trivially demonstrable in the existing test harness.

**Verifier adjustment.** The mechanism is exactly as described and the source-to-sink path is real and reachable, but two qualifiers lower the severity from Medium to Low: (1) the reward magnitude is tightly bounded by per-event frequency caps and legitimate per-round values, and (2) the only available remedy is architecturally precluded by the project's own constraints, so this is a consciously-documented design tradeoff rather than an actionable defect against the code's intended design.

---

### 27. ⚪ LOW / Confidence: High — Non-atomic compound update on @Volatile field across threads

- **Category:** Bugs  
- **Surface:** concurrency-async  
- **Files:** `domain/battle/engine/Simulation.kt`, `presentation/battle/engine/GameEngine.kt`, `presentation/battle/BattleViewModel.kt`  
- **Lines:** Simulation.kt:90-115 (creditCash `cash += amount`, applyInterest `cash += ...`, spend `cash -= amount` on @Volatile cash); GameEngine.spendCash -> simulation.spend (called from BattleViewModel.purchaseInRoundUpgrade on Main); creditCash/applyInterest called from GameLoopThread  

**Description.** Simulation.cash is @Volatile (visibility) but creditCash/applyInterest (`cash += ...`) run on the GameLoopThread (kills, wave completion) while spend (`if (cash < amount) return false; cash -= amount`) runs on the main thread from BattleViewModel.purchaseInRoundUpgrade. `+=`/`-=` on a @Volatile is a non-atomic read-modify-write; concurrent updates from the two threads lose writes. Concretely: a player taps an in-round upgrade (deduct on Main) at the same frame a kill credits cash (add on game thread) — one update is dropped, so the player either gets free cash (deduction lost → can afford more than they paid for) or loses earned cash. spend()'s check-then-act (cash<amount then cash-=amount) can also let a purchase through against a balance another thread just lowered. Bounded blast radius (cash is in-round only, resets each round), hence Low severity, but it is a genuine race occurring every round since kills stream continuously while the player taps.

**Evidence.** @Volatile var cash: Long = 0L; private set
fun creditCash(amount: Long) { ...; cash += amount; totalCashEarned += amount }   // game thread
fun spend(amount: Long): Boolean { if (cash < amount) return false; cash -= amount; return true } // main thread

**Assumptions.** Assumes purchaseInRoundUpgrade runs on Dispatchers.Main while update() runs on the GameLoopThread (confirmed). 64-bit long writes are atomic on ART, so this is a lost-update (not a torn-value) race.

**Verification (<2 min).** In a JVM test, drive Simulation from two threads: one calling creditCash(100) in a loop, the other calling spend(50) in a loop; after N iterations assert cash == expected(creditedTotal - spentTotal). It will drift due to lost updates. On device: spam in-round purchases while many kills land and watch cash occasionally not decrease by the purchase cost.

**Recommended fix.** Back cash/totalCashEarned with AtomicLong (getAndAdd/compareAndSet for spend), or serialize all simulation cash mutation onto the game-loop thread (post purchase deductions as a pending action the loop drains, mirroring pendingSpeed/pendingPaused), so reads/writes are single-threaded.

**Regression test.** SimulationCashConcurrencyTest: 10k concurrent creditCash(1) on thread A + 10k spend(1) on thread B starting from cash=10000; assert final cash == 10000 (fails today due to lost updates).

---

### 28. ⚪ LOW / Confidence: High — GC churn — per-frame heap allocation in 60fps loop

- **Category:** Performance  
- **Surface:** performance  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `presentation/battle/engine/CollisionSystem.kt`, `presentation/battle/engine/GameEngine.kt`  
- **Lines:** CollisionSystem.kt:24-29; GameEngine.kt:410-418 (called from update())  

**Description.** CollisionSystem.checkCollisions allocates THREE new ArrayLists on every single game-loop tick, even when nothing collides. Each of `entities.filterIsInstance<ProjectileEntity>().filter{}`, `.filterIsInstance<EnemyEntity>().filter{}`, and `.filterIsInstance<EnemyProjectileEntity>().filter{}` builds an intermediate list from filterIsInstance and then a second list from filter (filterIsInstance returns a new List, filter returns another). That is up to 6 short-lived List allocations per frame plus the iterator objects. The loop runs at 60 ticks/s, and at the player-selectable 4x speed the accumulator drives `engine.update()` up to ~240 times per second (GameLoopThread.kt:38-44). With a full wave (enemiesPerWave caps at 40, WaveSpawner.kt:137) plus dozens of in-flight projectiles, this is the dominant steady-state allocator on the loop thread and shows up as periodic GC pauses / frame hitches on low-end Android 14 devices.

**Evidence.** val projectiles = entities.filterIsInstance<ProjectileEntity>().filter { it.isAlive }
val enemies = entities.filterIsInstance<EnemyEntity>().filter { it.isAlive }
val enemyProjectiles = entities.filterIsInstance<EnemyProjectileEntity>().filter { it.isAlive }

**Assumptions.** Assumes the loop runs unthrottled toward 60fps and that 4x speed multiplies update() invocations (confirmed at GameLoopThread.kt:39 `accumulator += (elapsed * speedMultiplier)`).

**Verification (<2 min).** Run a Tier 5+ round to wave 20+ at 4x speed with several projectiles on screen; open Android Studio Profiler (Memory, live allocation). Observe a steady sawtooth of small List/Object[] allocations attributed to CollisionSystem.checkCollisions and recurring minor GCs. Compare allocation rate to a reduced-entity round to confirm it scales with entity count.

**Recommended fix.** Keep three reusable mutable scratch lists (e.g. private val projScratch = ArrayList<ProjectileEntity>()) on the engine or Simulation; clear() and refill them with a single manual `for (e in entities)` pass that partitions by type and alive-ness, then pass them to the sweeps. One pass, zero per-frame list allocation, identical behaviour. Alternatively maintain separate typed entity lists (enemies/projectiles/enemyProjectiles) on the engine instead of one heterogeneous `entities` list, which also kills the filterIsInstance scans in getAliveEnemies/findNearestEnemies.

**Regression test.** An allocation-counting JVM test is awkward, but a behavior-preserving refactor can be guarded by a SimulationTest that asserts detectProjectileEnemyHits/detectZigguratHits produce identical onHit firing order when fed the same entity snapshot via the new scratch-list path vs the old filterIsInstance path (golden sequence of (projIndex,enemyIndex) pairs).

**Verifier adjustment.** The allocation mechanism is exactly as described, but the "dominant steady-state allocator" framing is overstated. CollisionSystem.checkCollisions (CollisionSystem.kt:24-26) allocates 6 short-lived lists per call (filterIsInstance returns a new ArrayList, then filter returns another, x3 entity types) plus iterators, invoked once per engine.update() (GameEngine.kt:410), driven at ~60/s scaling to ~240/s at 4x (GameLoopThread.kt:39). Entity count is bounded at 40 enemies (WaveSpawner.kt:137) plus projectiles. However the same update() path contains comparable per-frame allocators the finding ignores: getAliveEnemies() (GameEngine.kt:728-729) is the identical filterIsInstance().filter{} double-allocation called 2-3x per frame inside updateUWs (lines 624/635/647), plus the projectile-trail loop, FloatingText spawns, entities.removeAll{} iterator, and render() Paint/FloatingText objects. So CollisionSystem is one of several equal-weight allocators, not uniquely dominant. On Android 14 (min SDK 34) the default concurrent generational GC reclaims young-gen allocations of this size cheaply, so the claimed periodic GC pauses / frame hitches on low-end devices are plausible but unproven and overstated. This is a legitimate GC-hygiene micro-optimization, not a correctness issue.

---

### 29. ⚪ LOW / Confidence: High — Flow re-query storm — no distinctUntilChanged on shared single-row profile Flow

- **Category:** Performance  
- **Surface:** performance  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/local/PlayerProfileDao.kt`, `data/repository/PlayerRepositoryImpl.kt`  
- **Lines:** PlayerProfileDao.kt:11-12 (get(): Flow); PlayerRepositoryImpl.kt:18-25 (observeProfile/observeWallet/observeTier all derived from dao.get())  

**Description.** PlayerProfileDao.get() is a Room `@Query` Flow over the single profile row. Room invalidates and re-emits this Flow on EVERY write to the player_profile table — including the very high-frequency counter writes adjustStepBalance, incrementBattleStats, adjustGems, adjustPowerStones, incrementGemsEarned, etc. observeProfile/observeWallet/observeTier all map this same Flow with no distinctUntilChanged (grep confirms distinctUntilChanged appears nowhere in the codebase). Every screen ViewModel combine() chain that includes observeProfile/observeWallet (Home, Workshop, Cards, Economy, Stats, UltimateWeapon, Missions) therefore re-runs its full mapping block on every profile write, even when the field that screen displays did not change. The mapping blocks are non-trivial: WorkshopViewModel re-runs resolveStats() and rebuilds the whole upgrade list; HomeViewModel recomputes achievableMilestones over Milestone.entries; StatsViewModel rebuilds all bars. During end-of-round persistence BattleViewModel issues a burst of profile writes (incrementBattleStats, updateHighestUnlockedTier, addGems via ad) each of which fan-outs to every subscribed ViewModel mapping.

**Evidence.** @Query("SELECT * FROM player_profile WHERE id = 1")
fun get(): Flow<PlayerProfileEntity?>
// override fun observeWallet() = dao.get().filterNotNull().map { it.toDomain().toWallet() }  // no distinctUntilChanged

**Assumptions.** Assumes Room's standard table-level invalidation (true for @Query Flows — they re-run on any write to referenced tables). Severity is Medium not High because the heaviest writers (battle step rewards) mostly fire while only the Battle screen is foregrounded, and WhileSubscribed(5000) tears down off-screen collectors after 5s.

**Verification (<2 min).** Add a log/counter in WorkshopViewModel's combine mapper, open Workshop, then trigger several profile counter writes (e.g. award steps). Observe the mapper re-running on each write even though only an unrelated counter changed. Confirms re-emission without value change.

**Recommended fix.** Add `.distinctUntilChanged()` to observeProfile (after map) and have observeWallet/observeTier map to their narrow projection THEN distinctUntilChanged so a step-balance write doesn't wake a tier-only observer. Even cheaper: give observeTier its own `SELECT currentTier` query and observeWallet a projection query so Room only re-emits when the projected columns change.

**Regression test.** Repository test using a Turbine/collect harness over a fake/Room in-memory DB: write a counter that does not affect the wallet projection (e.g. lastActiveAt) and assert observeWallet (with distinctUntilChanged) emits exactly once (initial) rather than twice.

**Verifier adjustment.** The mechanism is real but the "storm" framing and Medium severity are overstated; this is a Low-severity efficiency nit. Two corrections: (1) The waste is redundant re-execution of the mapping block (resolveStats / Milestone.entries iteration), NOT a UI recomposition storm. Each VM uses stateIn(...) which produces a StateFlow, and StateFlow has built-in value-equality conflation: identical resulting UiState values are NOT re-emitted to Compose, so there is no extra recompose when the displayed fields are unchanged. The cost is purely CPU spent recomputing a value that then gets discarded by StateFlow equality. (2) The work per emission is tiny (~23 upgrade types, ~dozen Milestone.entries) — microsecond-to-low-millisecond, off the render thread (viewModelScope default dispatcher), so no frame-drop hazard.

---

### 30. ⚪ LOW / Confidence: High — Redundant recomputation — LocalDate.parse in nested filter on every Flow emission

- **Category:** Performance  
- **Surface:** performance  
- **Files:** `presentation/stats/StatsViewModel.kt`  
- **Lines:** StatsViewModel.kt:102-118 (QUARTER branch of buildBars), reachable from the combine mapper at 35-72  

**Description.** In the QUARTER period, buildBars runs an inner `history.filter { LocalDate.parse(rec.date, fmt) ... }` for each of 12 weekly buckets. history is up to 90 rows (today.minusDays(89)). That is up to 12 × 90 = 1080 LocalDate.parse() calls (each parse allocates and validates) every time buildBars runs. buildBars runs inside the StatsViewModel combine mapper, which re-fires on every emission of observeProfile (no distinctUntilChanged — see finding #3), the history Flow, AND observeAllUpgrades. So an unrelated profile counter write while the Stats screen is open re-parses ~1080 date strings on the main-thread-bound StateFlow mapper. The WEEK/MONTH branches already precompute `byDate = history.associateBy { it.date }` and do O(days) map lookups — the QUARTER branch ignores that map and reparses instead.

**Evidence.** val weekRecords = history.filter { rec ->
    val d = LocalDate.parse(rec.date, fmt)
    !d.isBefore(weekStart) && !d.isAfter(weekEnd)
}

**Assumptions.** Assumes up to 90 history rows are realistically present for an active player (the query window is fixed at 90 days). On a fresh install history is short so the cost is small — hence Low severity.

**Verification (<2 min).** Open Stats, switch to QUARTER, then cause any profile write (e.g. earn steps). Profiler/Trace shows ~1080 LocalDate.parse calls per re-emission attributed to buildBars. Or add a counter inside the filter lambda and log it.

**Recommended fix.** Parse each history row's date once up front (`val parsed = history.map { it to LocalDate.parse(it.date, fmt) }`) outside the 12-week loop, or bucket rows into weeks in a single O(history) pass using the already-computed byDate map / a week-index function. Also gate the mapper with distinctUntilChanged (finding #3) so unrelated profile writes don't re-trigger it at all.

**Regression test.** StatsViewModel/JVM test: build a 90-day history with known per-week sums, select QUARTER, and assert the 12 DailyBarData bucket totals equal the expected weekly sums — passes before and after the single-pass refactor (behavior-preserving), and a counting wrapper around the date parser asserts parse count drops from O(weeks×days) to O(days).

---

### 31. ⚪ LOW / Confidence: High — GC churn — per-frame Paint allocation in render path

- **Category:** Performance  
- **Surface:** performance  
- **Files:** `presentation/battle/engine/GameEngine.kt`  
- **Lines:** GameEngine.kt:439-442 (chrono overlay Paint in render())  

**Description.** While a CHRONO_FIELD UW is active, GameEngine.render() allocates a brand-new android.graphics.Paint object every single frame to draw the full-screen blue overlay: `val p = android.graphics.Paint().apply { color = 0x222196F3; style = FILL }`. render() runs once per drawn frame (~60fps). Paint allocation is comparatively expensive (it wraps a native object) and is pure waste — the Paint is identical every frame. Every other Paint in the render path is a cached field (hpPercentPaint, bossCountdownPaint, the entity paints). CHRONO_FIELD can be active for many seconds (secondary-path duration), so this allocates ~60 Paint objects/second for the whole effect.

**Evidence.** if (chronoActive) {
    val p = android.graphics.Paint().apply { color = 0x222196F3; style = android.graphics.Paint.Style.FILL }
    canvas.drawRect(0f, 0f, screenWidth, screenHeight, p)
}

**Assumptions.** Assumes render() is called per drawn frame (confirmed: GameLoopThread.kt:48-52 calls engine.render every loop iteration).

**Verification (<2 min).** Activate a CHRONO_FIELD UW in battle and watch the Memory profiler: a steady stream of android.graphics.Paint allocations attributed to GameEngine.render for the duration of the effect. Stops the instant the effect expires.

**Recommended fix.** Hoist the overlay Paint to a private cached field next to hpPercentPaint/bossCountdownPaint (`private val chronoOverlayPaint = Paint().apply { color = 0x222196F3; style = Paint.Style.FILL }`) and reuse it in render(). Note also the literal 0x222196F3 lacks the explicit .toInt()/alpha that the other ARGB literals use — worth double-checking the intended alpha while fixing.

**Regression test.** Robolectric GameSurfaceView/GameEngine render test with chronoActive=true: spy/verify canvas.drawRect is called with a Paint whose color/style match the expected values across two consecutive render() calls and that the same Paint instance is reused (identity check on the captured argument).

---

### 32. ⚪ LOW / Confidence: High — Concurrency / visibility (non-volatile cross-thread field)

- **Category:** Bugs  
- **Surface:** supplychain-infra-debt  
- **Files:** `service/WidgetUpdateHelper.kt`, `service/StepNotificationManager.kt`  
- **Lines:** WidgetUpdateHelper.kt:16 (private var lastUpdateMs = 0L), 18-24 (update); StepNotificationManager.kt:30 (private var lastUpdateMs = 0L), 85-91 (updateNotification)  

**Description.** Both WidgetUpdateHelper and StepNotificationManager are @Singleton and hold a plain (non-@Volatile) mutable Long throttle field lastUpdateMs that gates a time-based throttle (60s / 30s). The same singletons are reachable from the service collector thread (Dispatchers.Default) and the WorkManager worker thread (DailyStepManager.runFollowOnPipeline -> widgetUpdateHelper.update is invoked from both recordSteps and recordActivityMinutes, which as shown above run on two threads). Without @Volatile/AtomicLong, the read-check-write (now - lastUpdateMs < THROTTLE_MS; lastUpdateMs = now) is both a stale-read hazard and a non-atomic update. Worst case is benign (an extra widget/notification refresh or a slightly-early one), so severity is Low — but it is a genuine memory-visibility defect on shared mutable state.

**Evidence.** WidgetUpdateHelper.kt:16-24 — `private var lastUpdateMs = 0L ... if (now - lastUpdateMs < THROTTLE_MS) return; lastUpdateMs = now`. StepNotificationManager.kt:30,87-89 mirrors it. Neither field is @Volatile; both classes are @Singleton.

**Assumptions.** Assumes both singletons are reached from >1 thread, confirmed by the DailyStepManager call sites.

**Verification (<2 min).** Code inspection: the field is plain `var`, mutated from at least two threads (service collector + WorkManager worker via DailyStepManager.runFollowOnPipeline). No tool run needed.

**Recommended fix.** Make lastUpdateMs @Volatile (sufficient for the read-then-write throttle since over-firing is harmless) or use java.util.concurrent.atomic.AtomicLong with compareAndSet for a strictly-monotonic throttle. Same change in both classes.

**Regression test.** Low-value to unit-test (timing/visibility). Document the @Volatile requirement; a reviewer-level guard is adequate.

---

### 33. ⚪ LOW / Confidence: High — Pre-release (alpha) dependency in a shipping app

- **Category:** Supply Chain  
- **Surface:** supplychain-infra-debt  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`  
- **Lines:** libs.versions.toml:16 (healthConnect = "1.2.0-alpha02"), 70 (health-connect-client); app/build.gradle.kts:203 (implementation(libs.health.connect.client))  

**Description.** androidx.health.connect:connect-client is pinned to 1.2.0-alpha02 in an app that is in closed-track soak heading to a v1.0.0 production release. Alpha AndroidX artifacts carry no API-stability guarantee and can change or remove APIs between alpha drops; Health Connect is load-bearing for this app (cross-validation, Activity Minute Parity, gap-filling — all anti-cheat-relevant and all routed through StepSyncWorker). Shipping an alpha as a production dependency means a future dependency bump can break the build or behavior with no deprecation cycle, and Google's own guidance discourages alpha libraries in production. A stable connect-client line exists; the project should pin to the newest stable (or at minimum a beta) before tagging v1.0.0.

**Evidence.** libs.versions.toml:16 `healthConnect = "1.2.0-alpha02"`. The CLAUDE.md tech-stack block also lists it as alpha. It is consumed unconditionally at app/build.gradle.kts:203.

**Assumptions.** Assumes a stable connect-client release exists that covers the APIs in use (the project predates the build, and Health Connect has shipped stable lines). If a 1.2-only API is genuinely required, this drops to documented-accepted-risk rather than a fix.

**Verification (<2 min).** Read libs.versions.toml line 16 — the `-alpha02` qualifier is on a dependency that flows into the release AAB (it is implementation, not testImplementation). Cross-check against the Maven Google repo for a stable connect-client release.

**Recommended fix.** Move to the latest stable androidx.health.connect:connect-client (1.1.0 stable line, or the newest stable available at build time) before the v1.0.0 tag, and add a CHANGELOG note. If a 1.2.x feature is required, pin a beta and document the risk in an ADR rather than an alpha.

**Regression test.** Not a unit-test target. Add a Gradle assertion/lint that fails the release build if any version-catalog entry consumed by releaseRuntimeClasspath contains '-alpha' (a small `tasks.register` check over the resolved configuration), so an alpha can never silently enter a release.

**Verifier adjustment.** The factual claims are all accurate and verified, but the framing overstates impact. This is a build/release-hygiene (supply-chain) concern, not a runtime-reachable defect: the app builds and runs correctly with the pinned alpha today, and there is no concrete input or sequence that triggers a fault in the shipped binary. The risk is forward-looking (a future Dependabot/manual bump to an incompatible alpha drop could break the build or behavior with no deprecation cycle). Severity adjusted Medium -> Low accordingly.

---

### 34. ⚪ LOW / Confidence: Medium — Two game-loop threads on one engine after join timeout (data race)

- **Category:** Bugs  
- **Surface:** battle-engine  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `presentation/battle/GameSurfaceView.kt`, `presentation/battle/GameLoopThread.kt`  
- **Lines:** GameSurfaceView.kt:113-120 (surfaceDestroyed join(1000) then gameThread=null), 90-105 (surfaceCreated starts a new thread on the same engine)  

**Description.** `surfaceDestroyed` requests the loop to stop and `join(1000)` with a 1-second timeout, then unconditionally nulls `gameThread`. If the join times out (a single frame stuck >1s under GC pressure, a slow `engine.update` catch-up burst at 4x, or main-thread jank), the old GameLoopThread is still alive and will execute at least one more `engine.update`/`engine.render` after the timeout returns. A subsequent `surfaceCreated` constructs and starts a SECOND GameLoopThread bound to the SAME `engine` instance. Two threads then mutate/iterate the engine's `entities`, `pendingAdd`, `uwStates`, and timers concurrently — unsynchronized — causing data races / ConcurrentModificationException. The timeout-and-forget pattern provides no guarantee the old thread has actually terminated before a new one starts.

**Evidence.** surfaceDestroyed: thread.isRunning = false; try { thread.join(1000) } catch (_) {}; gameThread = null; soundManager.release()   // surfaceCreated: val thread = GameLoopThread(holder, engine).apply { ...; isRunning = true }; thread.start(); gameThread = thread

**Verification (<2 min).** Hard to trigger deterministically; induce by making engine.update sleep >1s on a debug build, then rapidly background/foreground the activity so surfaceDestroyed's join times out and surfaceCreated spins up a second thread. Observe two 'GameLoop' threads in a thread dump both calling engine.update, followed by ConcurrentModificationException in logcat.

**Recommended fix.** Block until the old thread truly dies before starting a new one: loop the join (join with no timeout, or re-join in surfaceCreated if gameThread is non-null and alive), or interrupt+join, and guard against starting a new loop while the old one is still alive. At minimum, don't null `gameThread` until `!thread.isAlive`. Long-term, route all engine state mutation through the single loop thread or synchronize it.

**Regression test.** Instrumented/Robolectric test that simulates a join timeout (loop body that blocks) and asserts surfaceCreated does not start a second thread while the first is still alive (e.g. assert only one live GameLoopThread). Difficult in pure JVM; a focused unit test on a refactored 'ensureSingleLoop' helper is the practical regression guard.

**Verifier adjustment.** The code matches the finding exactly. surfaceDestroyed (GameSurfaceView.kt:113-120) sets thread.isRunning=false, join(1000), then unconditionally gameThread=null with no confirmation the thread terminated. surfaceCreated (lines 90-105) unconditionally constructs+starts a NEW GameLoopThread on the SAME engine instance. GameEngine.update() mutates non-thread-safe mutableListOf collections (entities, pendingAdd, uwStates) and many plain (non-@Volatile) fields (recoveryTimer, rapidFireTimer, fortuneMultiplier, chronoActive, chronoSlowFactor, lifestealAccumulator, stats, lastWave) with zero synchronization. Only engine.render() is wrapped in synchronized(surfaceHolder); update() is unprotected entirely — so even two render+update interleavings race. No guard prevents a second thread starting while the first is still alive. No regression test covers this (GameSurfaceViewTest only covers R3-01 state-preservation and pendingSpeed/pendingPaused, not concurrency). Severity adjusted to Low rather than Medium: the SurfaceHolder.Callback methods all run serially on the main thread, so surfaceDestroyed (incl. its join) fully returns before any later surfaceCreated runs; the overlap window is bounded to at most one extra loop iteration of the stale thread (isRunning is @Volatile and re-checked each iteration). The trigger requires (a) a single frame genuinely stuck >1s — which is precisely the GC-pause / 4x catch-up burst the finding cites — AND (b) the stale thread still executing update()/render() at the instant the new thread begins ticking, AND (c) a real surface destroy+recreate (note ON_PAUSE pauses the loop via viewModel.pause(), which does not slow a frame). Realistic impact is a rare ConcurrentModificationException crash or transient render corruption on a round-scoped engine, not durable data loss. The mechanism and absence-of-guard are confirmed; only the likelihood/impact were overstated as Medium.

---

### 35. ⚪ LOW / Confidence: Medium — Crash on empty rarity bucket — unguarded random.nextInt over a filtered list that can be empty

- **Category:** Bugs  
- **Surface:** domain-economy  
- **Files:** `domain/usecase/OpenCardPack.kt`  
- **Lines:** OpenCardPack.kt:36-37  

**Description.** OpenCardPack picks a rarity, filters CardType.entries to that rarity, then indexes with `candidates[random.nextInt(candidates.size)]`. kotlin.random.Random.nextInt(0) throws IllegalArgumentException ("bound must be positive"). Today every rarity (COMMON/RARE/EPIC) has exactly 3 CardType entries so candidates is never empty, but this is a latent landmine: adding a new CardRarity, or removing all cards of a rarity (e.g. during a content rebalance), turns a pack open into an uncaught exception that propagates out of the use case after gems were already spent (and, per finding #1, the spend is not rolled back).

**Evidence.** `val candidates = CardType.entries.filter { it.rarity == rarity }; val type = candidates[random.nextInt(candidates.size)]`

**Assumptions.** Only reachable if a future content change empties a rarity bucket; not reachable on the current 9-card roster. Hence Medium confidence.

**Verification (<2 min).** Add a CardRarity with no CardType members (or temporarily filter out a rarity), open a pack whose tier can roll that rarity — IllegalArgumentException from random.nextInt(0).

**Recommended fix.** Guard the empty case: `val candidates = CardType.entries.filter { it.rarity == rarity }.ifEmpty { CardType.entries.filter { it.rarity == CardRarity.COMMON } }` (or fall back to any non-empty bucket), or assert at startup that every CardRarity has >=1 CardType.

**Regression test.** OpenCardPackTest `every CardRarity has at least one CardType` (set-coverage guard) — fails the moment a rarity bucket goes empty, before it can crash a live pack open.

**Verifier adjustment.** The code and the Kotlin framework fact are exactly as described, and there is no upstream guard or regression test. However, the finding is NOT reachable against the current codebase: it requires a future source-code change to trigger. It is a genuine but latent defensive-coding gap, not an exploitable bug today.

---

### 36. ⚪ LOW / Confidence: Medium — TOCTOU loadout-cap bypass

- **Category:** Bugs  
- **Surface:** data-room  
- **Files:** `domain/usecase/ManageCardLoadout.kt`, `data/repository/CardRepositoryImpl.kt`, `data/repository/UltimateWeaponRepositoryImpl.kt`  
- **Lines:** ManageCardLoadout.kt:14-18; CardRepositoryImpl.kt:54-57 (equipCard); UltimateWeaponRepositoryImpl.kt:58-61 (equipWeapon)  

**Description.** ManageCardLoadout.equip enforces the 3-card cap by comparing a passed-in `equippedCount` snapshot to CardLoadout.MAX_SIZE, then calls cardRepository.equipCard(id) which does getById->update(copy(isEquipped=true)). The cap is enforced in memory against a stale count, not in the DB. Two concurrent equips with equippedCount=2 both pass and produce 4 equipped cards, violating the documented loadout invariant (max 3 UWs, 3 Cards in CLAUDE.md/CONSTRAINTS). Same shape for UW equip (no count check at the repository at all). Low severity because it affects per-round bonus stacking, not a permanent currency, but it is a real invariant breach surfaced by double-tap.

**Evidence.** ManageCardLoadout.equip: `if (equippedCount >= CardLoadout.MAX_SIZE) return Result.LoadoutFull; cardRepository.equipCard(cardId)`. CardDao.countEquipped() (Flow<Int>) exists but the equip path does not re-check it transactionally.

**Verification (<2 min).** Equip card A and B (count goes to 2 via two snapshots). Fire equip(C, equippedCount=2) and equip(D, equippedCount=2) concurrently. Both pass the cap check; countEquipped() then returns 4.

**Recommended fix.** Enforce the cap in a DAO @Transaction default method that re-reads `SELECT COUNT(*) WHERE isEquipped=1` inside the transaction and only flips isEquipped when count < MAX. Apply the same to UW equip.

**Regression test.** Robolectric: equip 3 cards, then two concurrent equips of a 4th/5th; assert countEquipped() == 3 and the use case returns LoadoutFull for the over-cap call.

---

### 37. ⚪ LOW / Confidence: Medium — Day-boundary / timezone inconsistency

- **Category:** Bugs  
- **Surface:** sensor-anticheat  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/sensor/DailyStepManager.kt`, `data/healthconnect/ActivityMinuteConverter.kt`  
- **Lines:** DailyStepManager.kt:152-157,86,238-247; ActivityMinuteConverter.kt:53-59  

**Description.** The per-minute overlap-dedup map (stepsPerMinute) is keyed by UTC epoch-minute: `val epochMin = timestampMs / 60_000`. ActivityMinuteConverter looks the same minutes up by `session.startTime.epochSecond / 60` (also UTC epoch-minute) — those agree. BUT the daily bucket (currentDate) is computed in LOCAL time via LocalDate.now(), and Health Connect day ranges are computed in ZoneId.systemDefault(). So the in-memory stepsPerMinute map mixes minutes that may belong to two different LOCAL days around midnight, while ensureInitialized() clears the map only when the LOCAL date rolls. More importantly, the double-counting-prevention check in ActivityMinuteConverter relies on stepsPerMinute being populated for the session's minutes — but stepsPerMinute is purged to 1440 entries (24h) and cleared on local-date rollover, while HC sessions can be read for a date whose minutes were already evicted (worker runs every 15 min but the map only holds what the CURRENT process credited since last clear). After any process restart the map is empty (it is NOT rehydrated from Room), so the <50 steps/min double-count guard passes for EVERY minute, crediting activity-minute step-equivalents for time the user actually walked (already credited as sensor steps). This is a path to double-credit walking as both sensor + activity-minute after a restart.

**Evidence.** DailyStepManager.getSensorStepsPerMinute() returns the in-memory map only; ensureInitialized() rehydrates dailySensorTotal/creditedTotal from Room but NOT stepsPerMinute (it is `clear()`ed on rollover and never repopulated). ActivityMinuteConverter: `val sensorSteps = sensorStepsPerMinute[epochMin] ?: 0; if (sensorSteps < MAX_SENSOR_STEPS_PER_MIN) eligibleMinutes++`.

**Verification (<2 min).** Walk 8000 steps with the app foreground (sensor credits them, stepsPerMinute populated). Force-stop the app. Re-launch so a fresh DailyStepManager is created (stepsPerMinute empty). Trigger the worker with a Health Connect WALKING-derived exercise session over the same minutes — converter sees sensorStepsPerMinute empty -> every minute 'eligible' -> credits step-equivalents on top of the already-credited sensor steps. Observe wallet > real steps.

**Recommended fix.** Persist the per-minute sensor histogram to Room (or derive eligibility from the persisted sensorSteps/timestamps) so the double-count guard survives process restarts, and key both the daily bucket and the per-minute map in the same zone (use a configurable TimeProvider/zone consistently — the codebase already has a TimeProvider abstraction). Do not rely on a volatile in-memory map as the sole guard against sensor/activity-minute double-credit.

**Regression test.** ActivityMinuteConverter / DailyStepManager test: credit sensor steps, simulate restart (new DailyStepManager, same Room), then record an overlapping WALKING-derived activity session and assert no additional credit for minutes the sensor already covered.

**Verifier adjustment.** The core defect is real but mislabeled and its impact is overstated. The genuine bug is NOT a timezone inconsistency (the finding itself admits the map's population key `timestampMs / 60_000` and the converter's lookup key `session.startTime.epochSecond / 60` are BOTH UTC epoch-minute, so lookups are consistent). The genuine bug is that `DailyStepManager.stepsPerMinute` (the per-minute sensor-overlap dedup map used by `ActivityMinuteConverter` to skip minutes with >=50 sensor steps) is purely in-memory and is NOT rehydrated from Room on process restart — `ensureInitialized()` only restores `dailySensorTotal`/`dailySensorCredited`/`dailyActivityMinuteTotal`/`dailyCreditedTotal`. After a process restart (common: WorkManager workers run in fresh processes, OS kills backgrounded apps), the map is empty, so the converter treats every session minute as eligible (sensorSteps 0 < 50) and computes the maximum stepEquivalents Y. The over-credit is, however, bounded by the existing idempotency guard in `recordActivityMinutes`: `val delta = stepEquivalents - dailyActivityMinuteTotal; if (delta <= 0) return`, where `dailyActivityMinuteTotal = X` is rehydrated from Room. So only the difference (Y - X) is re-credited — i.e. only minutes that were previously EXCLUDED due to >=50 sensor-step overlap, NOT the whole session as the finding implies ('crediting ... for EVERY minute'). Crucially, the qualifying exercise types (cycling, rowing, swimming, wheelchair, yoga, stretching) generally do not register >=50 sensor steps/min — that is the premise of activity-minute parity for non-ambulatory exercise — so the sensor-overlap exclusion rarely fires and Y == X (zero over-credit) in the overwhelming majority of real sessions. The over-credit is further capped by per-activity daily caps (5k-12k step-equivalents) and affects only Steps, a soft non-purchasable progression currency, so it is an economy/balance leak, not a security or monetary issue.

---

### 38. ⚪ LOW / Confidence: Medium — State-machine off-by-one in cross-validation offense levels

- **Category:** Bugs  
- **Surface:** sensor-anticheat  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/healthconnect/StepCrossValidator.kt`, `data/anticheat/AntiCheatPreferences.kt`  
- **Lines:** StepCrossValidator.kt:76-143; AntiCheatPreferences.kt:58-65,69-82  

**Description.** recordCvOffense() is called BEFORE the level branch reads getCvOffenseCount(), so the level decision uses the PRE-increment count. The doc comment claims 'Level 0 (0 offenses)... Level 2 (3-5 offenses)... Level 3 (6+)'. But the FIRST offense increments the count to 1 while the branch still sees offenseCount=0 -> Level 0 path. Each detection thus applies the penalty for one-offense-too-few. A repeat offender at their 6th detection is treated as Level 2 (offenseCount read as 5) instead of Level 3, deferring the 10% penalty by a full offense. Additionally, decayCvOffenses() only decrements by 1 and ONLY runs on a clean reconciliation that also has record.escrowSteps > 0; an attacker who is escrowed once, never reconciles cleanly (keeps the discrepancy until discard), accrues offenses that never decay because the decay path requires a no-discrepancy sync with outstanding escrow. So offense count can be a monotonic ratchet that, combined with the off-by-one, is both too lenient on the escalation timing and effectively impossible to recover from for an honest user who had one bad HC day.

**Evidence.** validate(): `antiCheatPrefs.recordCvOffense(date)` then `when { offenseCount >= 6 -> ... offenseCount >= 3 -> ... }` where offenseCount was read at line 77 BEFORE recordCvOffense. decayCvOffenses is only reached in the `else if (record.escrowSteps > 0)` branch (no current discrepancy AND outstanding escrow).

**Verification (<2 min).** Stub getCvOffenseCount to return 5, trigger a >20% discrepancy: validator applies Level 2 (cap at HC, no 10% penalty) even though after recordCvOffense the persisted count is 6 (Level 3). The 6th offense is penalized as the 5th.

**Recommended fix.** Read the offense count AFTER recording the offense (or compute level = newCount), so thresholds match the documented semantics. Decouple decay from the escrow-present condition so an honest user's offense count decays on any clean reconciliation, and document/clamp the ratchet. Add explicit boundary tests at counts 0,1,2,3,5,6.

**Regression test.** StepCrossValidatorTest: with persisted offense count 5 and a fresh >20% discrepancy, assert Level 3 (HC*0.9) penalty is applied (currently it applies Level 2). With count 2 and a clean reconciliation that has escrow, assert decay still occurs even when escrowSteps just got released.

**Verifier adjustment.** The finding bundles two claims; only the SECOND is a real defect. (1) The "off-by-one in offense level decision" is NOT a bug. `validate()` reads `offenseCount` pre-increment and the `when` branch maps it (>=6 Level3, >=3 Level2, >=1 Level1, else Level0). This is exactly the documented spec table in docs/step-tracking.md (Level0=count 0, Level1=1-2, Level2=3-5, Level3=6+) where the count means *accumulated prior offenses*, and it is pinned by StepCrossValidatorTest (getCvOffenseCount=7->Level3, =4->Level2, =2->Level1, =0->Level0). The finding interprets "offenses" as "including the current detection," but the implementation, its own doc comment, the canonical step-tracking.md table, and the test suite are mutually consistent on the other reading. There is no deviation from spec — it is a defensible, documented, tested design choice, not a defect. (2) The decay-ratchet observation IS a real logic defect. decayCvOffenses() is called from exactly one site: StepCrossValidator.kt:151, inside `else if (record.escrowSteps > 0)` (clean sync AND outstanding escrow). But that same branch first calls releaseEscrow() which runs DailyStepDao.clearEscrow (UPDATE ... SET escrowSteps = 0). So the clean sync that would start the 7-day decay clock also zeroes the escrow, and every subsequent clean sync sees escrowSteps==0 and skips decayCvOffenses() entirely. Combined with decayCvOffenses() only decrementing when daysSince>=7 (DECAY_DAYS), the `escrowSteps>0` gate and the `daysSince>=7` gate are nearly mutually exclusive, so the documented "decays by 1 after 7 days without offense" can essentially never fire for a recovered user. The cv_offense_count becomes a near-monotonic ratchet for an honest user who had one bad Health Connect day. This is uncovered by tests (no AntiCheatPreferencesTest; the only decay reference asserts the call happens, not the 7-day or recovery semantics).

---

### 39. ⚪ LOW / Confidence: Medium — Negative-discrepancy / sensor-undercount never reconciled

- **Category:** Bugs  
- **Surface:** sensor-anticheat  
- **Files:** `data/healthconnect/StepCrossValidator.kt`  
- **Lines:** StepCrossValidator.kt:67-76,108-110  

**Description.** In the Level-1 and Level-0 escrow branches, `excess = sensorSteps - hcSteps` is used as the escrow amount, but this branch is only entered when `discrepancy > DISCREPANCY_THRESHOLD` (sensor >> HC). That is consistent. However the escrow amount uses sensorSteps - hcSteps while the deduction conceptually should target creditedSteps (what the player actually got), not raw sensorSteps. creditedSteps can differ from sensorSteps (rate-limit/velocity penalties already reduced the credit, and the STEP_MULTIPLIER bonus can make creditedSteps EXCEED sensorSteps). When STEP_MULTIPLIER is high, creditedSteps > sensorSteps, so escrowing only (sensorSteps - hcSteps) under-deducts the inflated bonus portion: the player keeps multiplier-bonus Steps minted on top of a discrepant (likely spoofed) sensor count. Levels 2/3 correctly use creditedSteps, but Levels 0/1 use the raw sensor delta, creating an inconsistency that favors the cheater at low offense counts (which is exactly when a first-time spoofer is operating).

**Evidence.** Level 0/1: `val excess = sensorSteps - hcSteps`. Levels 2/3: `val excess = record.creditedSteps - hcSteps` (or - capped). creditedSteps includes the applyStepMultiplier bonus (DailyStepManager:285) and can exceed sensorSteps.

**Verification (<2 min).** Set sensorSteps=1500, creditedSteps=2900 (STEP_MULTIPLIER ~+93%), hcSteps=1000, offenseCount=0 -> escrow = 1500-1000 = 500 deducted, leaving 2400 credited against an HC truth of 1000. The bonus-inflated 1400 excess over HC is barely touched.

**Recommended fix.** Compute escrow against creditedSteps consistently across all levels (excess = creditedSteps - hcSteps), since creditedSteps is what was actually added to the wallet. Using raw sensorSteps mis-sizes the clawback whenever the multiplier or anti-cheat penalties moved creditedSteps away from sensorSteps.

**Regression test.** StepCrossValidatorTest: record(sensor=1500, credited=2900, hc=1000), offenseCount=0; assert spendSteps escrow equals creditedSteps - hcSteps (1900), not sensorSteps - hcSteps (500).

**Verifier adjustment.** The code reads exactly as cited. StepCrossValidator.kt level-3 (line 90) and level-2 (line 100) compute `excess` from `record.creditedSteps`, whereas level-1 (line 109) and level-0 (line 127) compute `excess = sensorSteps - hcSteps`. The asymmetry is real. The premise that `creditedSteps` can exceed `sensorSteps` is also confirmed: DailyStepManager.recordSteps accumulates the POST-multiplier value into `dailySensorCredited` (creditedSteps) at lines 138/141/147, while `dailySensorTotal` (sensorSteps) accumulates raw `rawDelta` at line 144; applyStepMultiplier (lines 264-286) multiplies by up to +100%, and the player's wallet is credited the multiplied `credited` amount (line 149). creditedSteps is walking-sensor-only (activity-minutes go to a separate stepEquivalents field per StepRepositoryImpl.toDomain), so the creditedSteps-vs-sensorSteps comparison is apples-to-apples and the multiplier is the sole source of creditedSteps > sensorSteps. No existing test exercises creditedSteps != sensorSteps — every StepCrossValidatorTest case pins them equal (record() defaults credited==sensor; levels 2/3 use credited=1500==sensor=1500), so the under-deduction path is unguarded. Two caveats lower this below a clean "bug": (1) levels 0/1 are an ESCROW model (deduct then restore on HC reconciliation), conceptually distinct from the levels 2/3 immediate-CAP model — escrowing exactly the disputed raw sensor-vs-HC gap is a defensible design rather than an obvious error; (2) reachability requires a cheater to simultaneously produce a >20% sensor-over-HC discrepancy, evade the velocity analyzer (which would otherwise reduce credited below sensor and cancel the effect), AND have invested heavily in STEP_MULTIPLIER. The leaked amount is bounded by the multiplier bonus applied to the discrepant portion.

---

### 40. ⚪ LOW / Confidence: Medium — Lost reset of in-memory baseline on day rollover during active ingestion

- **Category:** Bugs  
- **Surface:** sensor-anticheat  
- **Files:** `data/sensor/DailyStepManager.kt`  
- **Lines:** DailyStepManager.kt:90-115,144-149  

**Description.** ensureInitialized() detects a local-date rollover and resets the in-memory daily counters, but the day-start SENSOR baseline used by the worker (StepIngestionPreferences day_start_counter) is independent and is only set once per day at startup. When midnight rolls while the foreground service is running, DailyStepManager resets its daily counters and rehydrates from Room for the NEW date (existing == null -> all zero), but the StepSensorDataSource.lastCumulative keeps accumulating across midnight, so the delta computed at 00:00 spans both days and is credited entirely to the NEW day. Meanwhile the worker's day_start_counter for the new day will be re-established lazily, potentially after the service already credited a cross-midnight delta, leaving the worker's rawToday baseline inconsistent with what the service already credited. The window is small but produces a misattributed credit at the day boundary (steps walked just before midnight credited to the next day, and the daily-ceiling/missions accounting for the boundary minute is split inconsistently between the UTC-keyed per-minute map and the local-date bucket).

**Evidence.** ensureInitialized resets dailyCreditedTotal etc. on local date change but StepSensorDataSource.lastCumulative (the absolute counter cursor) is process-lifetime, not day-scoped; the first delta after midnight is `cumulative - lastCumulative` accumulated since the last sensor event before midnight.

**Verification (<2 min).** Run the service across a simulated local midnight: feed a sensor event at 23:59 then 00:01 with the counter advancing; observe the entire 23:59->00:01 delta credited to the new local day's record, and the worker's day_start_counter for the new day captured only on its next run, so worker rawToday for the new day double-counts or misses the boundary delta depending on ordering.

**Recommended fix.** Re-establish day_start_counter atomically with the DailyStepManager date rollover (have ensureInitialized, on rollover, capture the current absolute counter as the new day's baseline) so service and worker agree on the boundary. Bucket the per-minute map by the same local-date logic used for the daily ceiling.

**Regression test.** DailyStepManagerTest: drive recordSteps across a date change (inject TimeProvider/clock) and assert the new day's record starts from a baseline consistent with the worker's day_start_counter and that no boundary delta is counted twice.

**Verifier adjustment.** The "lost reset of in-memory baseline" framing is incorrect: ensureInitialized() (DailyStepManager.kt:90-115) DOES correctly reset every in-memory daily counter (dailySensorTotal, dailySensorCredited, dailyCreditedTotal, dailyActivityMinuteTotal, dropState, stepsPerMinute) on a local-date change and rehydrates from Room for the new date. Nothing is "lost." The only genuine residual effect is much narrower: StepSensorDataSource.lastCumulative (StepSensorDataSource.kt:35-49) is process-lifetime by design because it computes incremental per-EVENT deltas (delta = cumulative - lastCumulative since the PREVIOUS sensor event), not since midnight. The step counter fires continuously while walking (SENSOR_DELAY_NORMAL, ~per-step/few-second batching), so the single delta straddling midnight covers only the 1-2 second window between the last pre-midnight event and the first post-midnight event — i.e. a handful of steps (typically 1-5) walked across the exact midnight second get credited to the NEW calendar day's history bucket / daily-mission progress. Those steps ARE credited (no loss) and consume the new day's fresh 50k ceiling (no ceiling breach, no double-credit). The permanent Step wallet (playerRepository.addSteps) is day-agnostic, so the player's total is unaffected. The "worker day_start_counter inconsistency" sub-claim is NOT reachable under the finding's own premise: StepSyncWorker.sensorCatchUp() returns immediately when stepIngestionPrefs.isServiceAlive() is true (StepSyncWorker.kt:70), so while the foreground service is running the worker's catch-up path is a no-op — the two paths are mutually exclusive. On a true rollover the worker also re-establishes its baseline correctly (existing regression test `day rollover resets baseline` in StepIngestionTest.kt:120 credits 0 and re-anchors at the current counter). Net: a real but cosmetic boundary-minute misattribution of a few steps to the adjacent day's bucket, not a credit/ceiling/wallet defect.

---

### 41. ⚪ LOW / Confidence: Medium — Exported StepWidgetProvider trusts unencrypted SharedPreferences shown in widget

- **Category:** Security  
- **Surface:** security  
- **Files:** `service/StepWidgetProvider.kt`, `app/src/main/AndroidManifest.xml`  
- **Lines:** StepWidgetProvider.kt:21-45 (saveData/updateAllWidgets using MODE_PRIVATE widget_data prefs); AndroidManifest.xml:95-104 (receiver android:exported=true)  
- **Source→Sink:** Source: DailyStepManager -> StepWidgetProvider.saveData (cleartext widget_data prefs) -> updateAllWidgets RemoteViews. Sink: home-screen widget display (information exposure of currency on rooted device; not an authoritative grant).  

**Description.** StepWidgetProvider is exported=true (required for the home-screen widget) and its onUpdate calls updateAllWidgets, which reads daily_steps/balance from a plaintext MODE_PRIVATE SharedPreferences file (widget_data) that lives OUTSIDE the SQLCipher-encrypted DB. The whole app's security posture is 'all game state in an encrypted DB' (CONSTRAINTS.md), but the step balance and daily steps are mirrored in cleartext to drive the widget. On a rooted device this cleartext file is readable/writable, and because the widget is exported, an APPWIDGET_UPDATE broadcast (which any app can technically send, though the system normally brokers it) re-renders from that file. The PendingIntent used is correctly FLAG_IMMUTABLE (good), so there is no intent-tampering vector. The real exposure is the cleartext mirror of game currency that defeats the encrypted-DB invariant — a rooted user can edit widget_data to display arbitrary balances, and more importantly any other process on a rooted device can read the user's step/balance data without the SQLCipher key.

**Evidence.** StepWidgetProvider.kt: `context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)` with PREFS="widget_data", storing KEY_STEPS/KEY_BALANCE as cleartext longs; manifest receiver exported=true. The DB is SQLCipher-encrypted but this mirror is not.

**Assumptions.** Requires root to read/edit the file; the widget value is display-only and not authoritative, so it cannot directly grant currency — hence Low.

**Verification (<2 min).** Input: rooted device, read /data/data/com.whitefang.stepsofbabylon/shared_prefs/widget_data.xml. Trigger: none beyond having the widget data saved. Observable: daily_steps and balance in plaintext. Edit the file, send APPWIDGET_UPDATE, observe forged balance rendered.

**Recommended fix.** This is a display-only mirror so the impact is low, but to honor the encrypted-DB invariant, either (a) store the widget mirror in EncryptedSharedPreferences, or (b) accept it explicitly and ensure the widget value is never treated as authoritative (it isn't — the DB is the source of truth, so forging the widget file does not grant currency). Document that the widget mirror is a non-authoritative cleartext cache.

**Regression test.** Not a code-defect with a failing unit test; covered by a documentation/ADR note. If hardened: WidgetDataTest asserting the prefs are written via the encrypted store.

**Verifier adjustment.** The mechanism is accurate but the framing ("defeats the encrypted-DB invariant", "any other process can read") is overstated. (1) The widget mirror is display-only non-authoritative data: Room is the single source of truth, so editing widget_data cannot grant currency or change game state — it only alters a text label. (2) The file uses MODE_PRIVATE, the correct standard Android mode; it lives in the app's private UID-scoped sandbox and is NOT readable by other apps. The cited read/write exposure requires ROOT, which is outside the Android threat model and which equally compromises the SQLCipher path (a root-level attacker controlling the process can drive the app to decrypt the DB with its Keystore key). So this adds no meaningful attack surface a rooted attacker doesn't already have. (3) The stated invariant (CONSTRAINTS.md:30) is scoped specifically to "SQLCipher encryption for the Room database" and line 33's "cleartext blocked" is the NETWORK security config — neither claims all local state is encrypted. Multiple other unencrypted MODE_PRIVATE prefs exist by design (biome_prefs, notification_prefs, anti_cheat_prefs, etc. in DataDeletionManager.PREFS_NAMES). (4) exported=true is mandatory for any AppWidgetProvider; an APPWIDGET_UPDATE broadcast from another app only re-renders the already-stored values and grants no cross-process read. PendingIntent is correctly FLAG_IMMUTABLE (no tamper vector, as the finding concedes). Net: a minor, root-only information-disclosure note about non-sensitive display data, not a real defeat of the security posture.

---

### 42. ⚪ LOW / Confidence: Medium — Data race / ConcurrentModificationException

- **Category:** Bugs  
- **Surface:** concurrency-async  
- **Severity:** Low *(adjusted down from High by adversarial verification)*  
- **Files:** `data/sensor/DailyStepManager.kt`, `service/StepSyncWorker.kt`, `service/StepCounterService.kt`  
- **Lines:** DailyStepManager.kt:79 (stepsPerMinute map), :86 getSensorStepsPerMinute().toMap(), :151-157 write; StepSyncWorker.kt:51 getSensorStepsPerMinute() (ungated by isServiceAlive)  

**Description.** DailyStepManager is a @Singleton shared by the foreground StepCounterService and the StepSyncWorker, both running on different threads (service collects on Dispatchers.Default; worker runs on the WorkManager executor). The service writes `stepsPerMinute` (a plain HashMap) inside recordSteps() on every sensor batch. The worker calls getSensorStepsPerMinute() which does `stepsPerMinute.toMap()` — iterating the map. Critically the worker's Health Connect block (StepSyncWorker.doWork lines 44-54, where getSensorStepsPerMinute is called) is NOT gated by `stepIngestionPrefs.isServiceAlive(now)` — only sensorCatchUp() is. So while the service is alive and actively walking-credit writing the map, the worker iterates it → ConcurrentModificationException, crashing the periodic sync worker. The same unsynchronized singleton also exposes a lost-update race on the non-volatile Long counters (dailyCreditedTotal, dailyActivityMinuteTotal, dailySensorCredited): service recordSteps() and worker recordActivityMinutes() can interleave read-modify-write on dailyCreditedTotal, corrupting the daily-ceiling accounting (e.g. two credits both read the same remainingCeiling and overshoot the 50,000 cap, or one += is lost).

**Evidence.** private val stepsPerMinute = mutableMapOf<Long, Long>()  // plain HashMap, no sync
fun getSensorStepsPerMinute(): Map<Long, Long> = stepsPerMinute.toMap()  // iterates while service writes
// StepSyncWorker.doWork — HC block runs unconditionally:
val sensorStepsPerMinute = dailyStepManager.getSensorStepsPerMinute()  // line 51, no isServiceAlive guard

**Assumptions.** Assumes WorkManager and the foreground service can be alive simultaneously (true: worker's HC sync runs regardless of service liveness) and that recordSteps/getSensorStepsPerMinute touch the same singleton instance (confirmed via Hilt @Singleton).

**Verification (<2 min).** Start the foreground service (grant ACTIVITY_RECOGNITION) and trigger sensor deltas in a tight loop while concurrently invoking StepSyncWorker (e.g. run TestListenableWorkerBuilder<StepSyncWorker>().build().doWork() on a background dispatcher while another coroutine calls recordSteps in a loop). Observe ConcurrentModificationException from stepsPerMinute.toMap(). For the counter race, run recordSteps and recordActivityMinutes from two threads with dailyCreditedTotal near 50,000 and assert the final credited never exceeds DAILY_CEILING.

**Recommended fix.** Make DailyStepManager thread-safe: either confine all mutation to a single-threaded dispatcher/actor (a dedicated Mutex around recordSteps/recordActivityMinutes/getSensorStepsPerMinute), or replace stepsPerMinute with a ConcurrentHashMap AND wrap the cap-check/counter-increment in the Mutex. Also gate the worker's getSensorStepsPerMinute()/recordActivityMinutes() usage behind isServiceAlive (or have the converter snapshot the map under the same lock). Mark the counters @Volatile is insufficient — they need mutual exclusion because they are read-modify-write.

**Regression test.** DailyStepManagerConcurrencyTest: launch 100 concurrent recordSteps(1, t) calls plus parallel getSensorStepsPerMinute() reads on Dispatchers.Default; assert no exception thrown and getDailyCredited() == 100 (currently fails with CME or a count < 100).

**Verifier adjustment.** The data race exists but the impact is overstated. CRITICAL CORRECTION: the ConcurrentModificationException does NOT crash the worker. The getSensorStepsPerMinute() call (StepSyncWorker.kt:51) sits inside a broad try { ... } catch (e: Exception) block spanning lines 44-57; a CME is a RuntimeException and would be caught and logged at line 56, after which the worker returns Result.success() and survives. The HC sync for that one 15-minute cycle is skipped, not the worker. So 'crashing the periodic sync worker' is wrong. The lost-update race on the non-volatile Long counters (dailyCreditedTotal etc.) is real but its worst realistic outcome is a minor accounting drift — a small overshoot of the 50k anti-cheat daily ceiling or a lost/duplicated credit batch — not a crash or meaningful user-facing data loss.

---

### 43. ⚪ LOW / Confidence: Medium — Swallowed error producing wrong user-visible value

- **Category:** Bugs  
- **Surface:** supplychain-infra-debt  
- **Files:** `service/StepCounterService.kt`  
- **Lines:** StepCounterService.kt:65-69  

**Description.** In the service collector, the player balance is fetched with `val balance = try { playerRepository.observeProfile().first().stepBalance } catch (_: Exception) { 0L }` and then passed straight into notificationManager.updateNotification(dailySteps, balance). A transient repository failure (e.g. SQLCipher open contention, a momentary Room exception) silently substitutes balance = 0, so the persistent notification will display 'Balance: 0' to the user even though their real balance is non-zero. Because this is the always-on foreground notification, a single transient read failure shows alarming wrong data (looks like the player lost all their Steps). The catch should either skip the notification update on failure (keep the last good value) or distinguish 'unknown' from zero, not coerce to 0.

**Evidence.** StepCounterService.kt:65 `val balance = try { playerRepository.observeProfile().first().stepBalance } catch (_: Exception) { 0L }` followed by updateNotification(... balance = balance).

**Assumptions.** Assumes observeProfile().first() can throw transiently under DB contention; the explicit catch in the code confirms the author anticipated failures here.

**Verification (<2 min).** In a Robolectric/service test, make playerRepository.observeProfile().first() throw once; observe that the next notification text renders 'Balance: 0' instead of retaining the prior value. Inspection alone substantiates the path.

**Recommended fix.** On read failure, return early (skip this notification refresh) so the previous correct balance stays on screen, or cache the last good balance and reuse it. Do not coerce a read error to 0.

**Regression test.** StepCounterServiceTest: stub the profile flow to throw; assert updateNotification is NOT called with balance=0 (either not called, or called with the cached prior value). Fails against current catch-to-0 code.

---

### 44. ⚪ LOW / Confidence: Low — Concurrency / silently-dropped insert losing a card copy

- **Category:** Bugs  
- **Surface:** data-room  
- **Severity:** Low *(adjusted down from Medium by adversarial verification)*  
- **Files:** `data/repository/CardRepositoryImpl.kt`, `data/local/CardDao.kt`, `domain/usecase/OpenCardPack.kt`  
- **Lines:** CardRepositoryImpl.kt:25-44; CardDao.kt:20-21 (insert IGNORE), 38-39 (incrementCopyCount); OpenCardPack.kt:39-45  

**Description.** addCard/addCardOrIncrementCopy/incrementCopyCount do a non-atomic read (getByType/hasCard) then either insert or incrementCopyCount. card_inventory has a UNIQUE index on cardType and CardDao.insert uses OnConflictStrategy.IGNORE returning the new rowId (or -1 on conflict). If two flows both observe hasCard==false for the same CardType and both call addCard (e.g. OpenCardPack from the Cards screen racing with ClaimSupplyDrop.addCardOrIncrementCopy fired by a walking-encounter notification), the second insert is silently IGNORED by the unique-index conflict — so one earned card copy is lost (it should have become copyCount=2). OpenCardPack and CardRepositoryImpl never check the returned rowId, so the loss is invisible. Within a single coroutine OpenCardPack's 3 sequential awaits are safe (insert commits before the next hasCard), so this is purely a cross-coroutine race — hence Low confidence on real-world frequency, but the loss is permanent currency-equivalent value (Gems were already spent on the pack).

**Evidence.** CardDao: `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity): Long`. CardRepositoryImpl.addCard: `dao.insert(CardInventoryEntity(cardType = type.name))` (rowId discarded). OpenCardPack: `if (cardRepository.hasCard(type)) cardRepository.incrementCopyCount(type) else cardRepository.addCard(type)`.

**Verification (<2 min).** Two coroutines concurrently call cardRepository.addCard(CardType.X) against a real Room DB. Assert getByType(X).copyCount: with the race, the table holds a single row at copyCount=1 (one insert IGNORED) instead of the intended two copies; no error surfaces.

**Recommended fix.** Add a single atomic upsert-or-increment in CardDao: `INSERT INTO card_inventory (cardType, level, isEquipped, copyCount) VALUES (:t,1,0,1) ON CONFLICT(cardType) DO UPDATE SET copyCount = copyCount + 1` and have addCardOrIncrementCopy/incrementCopyCount/OpenCardPack route through it, eliminating the read-then-write window entirely. Keep returning whether the row was newly created via changes()/rowid if the caller needs isNew.

**Regression test.** Robolectric: two concurrent addCardOrIncrementCopy(X); assert final copyCount == 2 (no copy lost).

**Verifier adjustment.** The race mechanism is real, but the finding's primary concurrency vector is factually wrong: the walking-encounter supply-drop notification does NOT claim cards in the background. SupplyDropNotificationManager.notify (SupplyDropNotificationManager.kt:39-57) only builds a PendingIntent with extra navigate_to=supplies that navigates the user to the Supplies screen. The actual claim only ever runs as a foreground user action via UnclaimedSuppliesViewModel.claimDrop/claimAll. So both racing paths (OpenCardPack from CardsScreen, ClaimSupplyDrop from SuppliesScreen) are user-initiated, each on its own ViewModel viewModelScope. claimAll() is sequential (forEach with sequential awaits in one coroutine), so it does not self-race, and openPack is guarded by a _processing re-entry flag within its own ViewModel. A genuine trigger requires the user to fire an openPack coroutine and, within its sub-millisecond Room DB window, fire a claimDrop for a supply drop awarding the same CardType from the other screen — a vanishingly narrow timing window. Severity is Low (worst realistic case is the loss of a single card copy in an extremely rare window), not Medium.

---

### 45. ⚪ LOW / Confidence: Low — SharedPreferences async flush can lose DB passphrase across crash (key-recovery wipe abuse)

- **Category:** Security  
- **Surface:** security  
- **Files:** `data/local/DatabaseKeyManager.kt`  
- **Lines:** DatabaseKeyManager.kt:52-58 (generate passphrase, encrypt, prefs.edit()...apply()) and 33-59 overall  
- **Source→Sink:** Source: process kill between DB creation and async prefs flush -> missing passphrase blob -> DatabaseKeyManager.getPassphrase decrypt path -> wipeDatabaseFile. Sink: deletion of steps_of_babylon.db (all game state + billing receipts).  

**Description.** On first run the random 32-byte passphrase is generated, used to open/create the SQLCipher DB, and the encrypted blob is written to SharedPreferences with apply() (asynchronous). If the process is killed after the DB file is created on disk but before apply() flushes the blob to disk, the next launch finds no encrypted_passphrase, generates a NEW random passphrase, fails to open the existing encrypted DB, hits the catch block, and WIPES the DB. This is a self-healing data-loss path, but it means a determined attacker (or flaky low-memory device) can repeatedly induce the recovery-wipe, which is the same wipe that finding 2 abuses to re-credit consumables and that destroys all permanent currency. apply() is the wrong durability choice for a one-time key-establishment write that the entire encrypted DB depends on.

**Evidence.** DatabaseKeyManager.kt: `prefs.edit().putString(PREF_ENCRYPTED, ...).putString(PREF_IV, ...).apply()` immediately after `return passphrase`. apply() schedules the disk write off the calling thread; a crash before flush loses the blob while the DB created with that passphrase persists.

**Assumptions.** Narrow crash window at first-run only; the recovery wipe makes it self-healing for crypto purposes but it is the trigger for the more serious finding 2 and destroys progress. Low severity, Medium confidence on real-world hit rate.

**Verification (<2 min).** Input: fresh install, no prefs. Trigger: call getPassphrase (creates DB+passphrase), kill the process before SharedPreferences commits, relaunch. Observable: decrypt path finds null blob -> generateRandomPassphrase -> open fails -> wipeDatabaseFile deletes the DB. Reproducible by mocking SharedPreferences.edit().apply() to no-op then re-reading.

**Recommended fix.** Use commit() (synchronous) for the one-time passphrase-blob write so the key is durably persisted before the DB is used, or write the blob to EncryptedSharedPreferences/file with fsync before opening the DB. The latency cost of a single commit() at first run is negligible.

**Regression test.** DatabaseKeyManagerTest: `passphrase blob is persisted synchronously before returning` — stub a SharedPreferences whose apply() is a no-op and assert the stored blob is non-null immediately after getPassphrase returns (fails with apply(), passes with commit()).

**Verifier adjustment.** The code does use apply() (async) for the one-time key-establishment write (DatabaseKeyManager.kt:54-57), which is a genuine durability anti-pattern: commit() (or a sync write) would be the correct choice for a write the entire encrypted DB depends on, and the catch block at lines 42-50 does wipe steps_of_babylon.db on any decrypt/missing-key path. To that extent the finding's underlying smell is real and the data-loss sink (wipeDatabaseFile deleting all game state + billing receipts) is real. HOWEVER, the specific exploitation sequence is misordered and the reachability is overstated: (1) getPassphrase() issues the prefs apply() at step 1, then provideDatabase() (DatabaseModule.kt:20-29) constructs a SupportOpenHelperFactory and calls Room.databaseBuilder(...).build() — Room is LAZY by default (no RoomDatabase.Callback, no allowMainThreadQueries, no prepackaged DB), so the SQLCipher DB FILE is not created on disk at build() time. The file is only created on the first DAO operation, which happens later on a background coroutine. So the prefs write is enqueued strictly BEFORE the DB file is created — the opposite of the finding's stated 'DB file created, then apply not yet flushed' window. (2) The apply() payload is two short Base64 strings that flush within milliseconds, and Android QueuedWork flushes pending apply() writes on standard lifecycle transitions (activity pause/stop, service lifecycle) that normally precede an OOM kill or process death, so a real-world hit requires a process kill in a sub-millisecond window AND after a later DB query already wrote the file. (3) The 'attacker can repeatedly induce the wipe' framing does not hold: an attacker with the ability to kill the process at a precise instant can trivially just clear app data, which already wipes everything with no timing precision required — there is no privilege escalation or remote vector. allowBackup is false in the manifest, confirming the recovery-wipe is the intended self-healing path. Net: a real, minor durability weakness worth a one-line commit()/synchronous-write fix, but NOT a realistically reachable or abusable security exploit.

---

## Rejected findings (false positives killed by adversarial verification)

These were raised by finders but disproven on close reading — recorded for calibration and to prevent re-litigation.

### ❌ (battle-engine) SharedFlow buffer overflow can silently drop earned-Step events (DROP_OLDEST) — *claimed Low*
- **Files:** `domain/battle/engine/Simulation.kt`, `presentation/battle/BattleViewModel.kt` — Simulation.kt:65-74 (MutableSharedFlow extraBufferCapacity=64, DROP_OLDEST, tryEmit); BattleViewModel.kt:217 (single collector), 419-454 (handleSimulationEvent)

**Why rejected.** The code is accurately quoted, but the scenario where DROP_OLDEST silently drops earned-Step events is not reachable. Wave size is hard-capped at 40 enemies (WaveSpawner.enemiesPerWave), one StepReward emits per death, so events in-flight are bounded well below the 64-slot buffer and are spread over time, not simultaneous. The single collector body is non-suspending (just launch{} + return), and the Step credit runs on applicationScope/Dispatchers.Default — a separate thread pool, not main — so it never blocks the collector. Dropping an event would require the main thread to freeze while >64 enemies die in a burst, which the 40-enemy cap prevents. DROP_OLDEST + replay=0 are intentional, documented choices (replay=0 avoids cross-round double-credit). Theoretical design observation, not a reachable defect.

---

### ❌ (sensor-anticheat) Double-credit / currency duplication — *claimed High*
- **Files:** `data/healthconnect/StepGapFiller.kt`, `service/StepSyncWorker.kt`, `data/sensor/DailyStepManager.kt` — StepGapFiller.kt:18-28; StepSyncWorker.kt:44-54,67-94

**Why rejected.** The finding misreads the self-correcting design and overstates impact. `StepGapFiller.fillGaps` credits only the POSITIVE difference `hcTotal - record.sensorSteps`, not `hcTotal`. Critically, `DailyStepManager.recordSteps` does `dailySensorTotal += rawDelta` (DailyStepManager.kt:144) and immediately persists it via `updateDailySteps` (line 147), so the gap-filler's own credit ALSO advances the very `sensorSteps` counter it subtracts from next time. This makes gap-filling idempotent/self-correcting across worker runs: the system credits `max(cumulative_sensor, cumulative_HC)` per day, which is the intended gap-fill semantic. The finding's load-bearing claim — that fillGaps mints steps that 'compound on every 15-minute worker run' — is provably false. I simulated a constant HC lead of D=50 across 6 runs: the total over-credit settles at exactly D=50 (total credited tracks `hcTotal`), NOT N×D. The reason: when fillGaps credits D, it raises persisted `sensorSteps` by D; the next run's `sensorCatchUp` then under-credits the new real sensor walk by exactly D (gap1 = rawToday - inflatedSensorSteps), and fillGaps re-adds D to reach hcTotal — the two cancel. Furthermore, on the same-hardware phones the finding itself cites as the 'normal case', HC aggregation LAGS the real-time sensor counter, so `gap2 = hcTotal - sensorSteps <= 0` and fillGaps credits nothing (second simulation: 0 over-credit). The only sustained extra credit comes from a genuinely separate HC source (e.g., a paired watch contributing steps the phone sensor never sees) — and crediting that is the documented PURPOSE of the HC gap-filler, representing real physical activity, bounded at the true HC total. Note also `StepCrossValidator.validate` (runs immediately after fillGaps in doWork) does NOT guard the HC>sensor direction (only penalizes sensor>HC discrepancy >20%), so it neither catches nor causes this; it is simply orthogonal.

---

### ❌ (security) Overly broad keep rules / -dontwarn suppress R8 hardening of SDK surfaces — *claimed Low*
- **Files:** `app/proguard-rules.pro` — proguard-rules.pro:26-40 (-keep class com.android.billingclient.** {*;} / com.google.android.gms.ads.** / com.google.android.ump.** with -dontwarn)

**Why rejected.** The cited ProGuard rules (proguard-rules.pro:26-40) do exist and are intentionally over-broad ({ *; } + -dontwarn) for the Play Billing, AdMob, and UMP SDK packages, and it is true that no purchase-signature-verification class exists in the app. However, this is not a defect tied to these rules. The keep rules match only THIRD-PARTY SDK packages (com.android.billingclient / com.google.android.gms.ads / com.google.android.ump) whose class and method names are already public/documented — obfuscating them would add negligible security value, and keeping them un-obfuscated removes negligible protection. Critically, the rules do NOT keep the app's own grant/credit code (com.whitefang.stepsofbabylon.data.billing.**, e.g. BillingManagerImpl.creditWallet / BillingReceiptDao.grantOnceAtomic), which R8 still obfuscates — so the stated mechanism ("broad keeps ease locating the grant call sites for hooking") is inaccurate. -dontwarn on reflection/AIDL-heavy SDK packages is standard recommended practice. The "no signature verification" sub-point is a real, separate concern but is not introduced or worsened by these ProGuard rules. No reachable security impact attributable to lines 26-40.

---

### ❌ (performance) GC churn — per-bounce projectile spawn carries growing hitEnemies set; bounce target scan is O(enemies) per hit — *claimed Low*
- **Files:** `presentation/battle/engine/GameEngine.kt` — GameEngine.kt:898-912 (bounce-shot next-target scan in onProjectileHitEnemy)

**Why rejected.** The cited code is accurate, but the impact characterization that drives the finding is wrong. The bounce path does NOT produce "(projectiles × bouncesRemaining) full O(entities) enemy scans within a single frame" / "O(P×B×E) work concentrated in one tick." A bounce child is queued into `pendingAdd` (GameEngine.kt:904/909), not into the live `entities` list nor into the `projectiles` snapshot the collision sweep is iterating. `pendingAdd` is flushed into `entities` only at the START of the next frame's `update()` (GameEngine.kt:390), which runs before that frame's `CollisionSystem.checkCollisions` (line 410). Within any single frame, each alive projectile fires `onHit` at most once (the sweep `break`s on first overlap and sets `isAlive=false`), so it performs exactly ONE O(enemies) next-target scan. Bounce multiplication is therefore spread across consecutive frames (one bounce per projectile per frame), never concentrated in one tick. The per-frame cost is O(P×E) — the same order as the baseline collision sweep already running — with no `×B` factor. The only genuine residual is a small per-bounce-hit allocation (a Sequence/iterator chain + one new ProjectileEntity) on the game-loop thread, which is negligible micro-churn comparable to work already done elsewhere in the sweep.

---

### ❌ (supplychain-infra-debt) Untested high-risk modules — *claimed Medium*
- **Files:** `service/StepSyncWorker.kt`, `service/StepCounterService.kt`, `service/BootReceiver.kt`, `service/SmartReminderManager.kt` — Whole files; test inventory: only app/src/test/java/com/whitefang/stepsofbabylon/service/StepWidgetProviderTest.kt exists for the entire service/ package (1 of 10 files).

**Why rejected.** The finding's central claim — that StepSyncWorker.sensorCatchUp()'s reconciliation arithmetic (rawToday = currentCounter - dayStart; gap = rawToday - alreadyCredited; recordSteps(gap)) "has zero tests" — is FALSE. app/src/test/java/com/whitefang/stepsofbabylon/data/sensor/StepIngestionTest.kt contains 11 tests (lines 66-182) that faithfully mirror that exact arithmetic via a `workerCatchUp` helper + a faithful `FakeStepIngestionPreferences` (whose semantics match the real class, which is itself tested in StepIngestionPreferencesTest.kt). Those tests directly cover the scenarios the finding says are untested: the dayStart==null baseline-establish branch (`worker establishes baseline on first run and credits nothing`), incremental off-by-one crediting (`multiple worker runs credit incrementally without duplication`), the sign-error/negative-credit guard (`counter reboot mid-day produces no negative credit`), and double-credit prevention (`no double credit when service and worker both active`, `service credits then dies then worker recovers exactly the gap`, `day rollover resets baseline`). Additionally the credit sink DailyStepManager.recordSteps re-guards rawDelta<=0, applies rate-limit + velocity + a 50k daily ceiling, and is tested in DailyStepManagerTest.kt. The only genuine residual gaps are: (a) the tests mirror the logic rather than driving the real StepSyncWorker class, leaving a logic-duplication drift risk; (b) SmartReminderManager's gap-selection loop, BootReceiver, and StepCounterService.initDayStartCounter have no tests — but none of these perform step-crediting arithmetic (text selection / service-start / baseline-seed only), so they cannot mint or destroy Steps. This is a Low-severity "improve test fidelity / mirror-drift" item, not the Medium "highest-risk untested arithmetic that can silently mint/destroy permanent Steps" the finding asserts.

---
