# Plan RO-09 — Pre-Closed-Test Audit Findings

**Status:** Active
**Created:** 2026-05-18
**Scope:** post-RO-08 deep self-audit, before promoting v3 internal → closed track
**Goal:** maximise quality of the v1.0 closed-test build so the 14-day clock starts on the strongest possible foundation

## Context

After landing **RO-08** (4-fix bundle for upgrade wiring) the codebase was scanned for the same patterns RO-08 surfaced — dead enum branches, computed-but-never-read fields, stale captured references. The scan also covered round-end persistence, currency atomicity, anti-cheat, ViewModel cleanup, and Flow combiners.

Seven findings emerged: one **CRITICAL** (broken UW), one **MODERATE** (cash-multiplier leak across overdrive expiry), one **LOW** but introduced by RO-08 itself (STEP_MULTIPLIER × cross-validator unit mismatch), three **LOW** consistency / atomicity items, and one **COSMETIC** dead-code drive-by.

Project state at audit time: Plan 31 Phase G2, internal-track AAB v3 live, on-device smoke-test PASSED 2026-05-18, **565 JVM tests** green. Closed-test 14-day clock has **not** started.

## Priority summary

| # | Finding | Severity | File(s) | Decision |
|---|---|---|---|---|
| 1 | CHRONO_FIELD UW does nothing functional | 🔴 **CRITICAL** | `GameEngine.kt`, `EnemyEntity.kt` | **Fix before CT** |
| 2 | GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` leak | 🟠 **MODERATE** | `GameEngine.kt` | **Fix before CT** |
| 7 | Dead `total` expression in `LabsScreen.kt:106` | 🟢 **COSMETIC** | `LabsScreen.kt` | **Fix before CT** (drive-by) |
| 3 | STEP_MULTIPLIER × cross-validator unit mismatch | 🟡 LOW | `DailyStepManager.kt`, `StepCrossValidator.kt` | **Defer to v1.x** |
| 4 | Currency lifetime counter desync | 🟡 LOW | `PlayerRepositoryImpl.kt`, `PlayerProfileDao.kt` | **Defer to v1.x** |
| 5 | TOCTOU race on gem / PS spend | 🟡 LOW | `PlayerRepositoryImpl.kt`, `PlayerProfileDao.kt` | **Defer to v1.x** |
| 6 | Per-kill battle-step credit on `viewModelScope` | 🟡 LOW | `BattleViewModel.kt` | **Defer to v1.x** |

### Decision rationale

**Fix before CT:** findings that produce visibly wrong behaviour during normal gameplay. Closed-test feedback would surface them as "broken on arrival" reports and could hurt the test cohort's confidence. CHRONO_FIELD is the highest-priority because a 75-Power-Stone purchase produces zero gameplay benefit — every tester who buys it will report it.

**Defer to v1.x:** findings whose practical impact during the 14-day closed test is zero or near-zero. They are real bugs but are bounded to lifetime-stat drift, edge-case races, or minor accounting-only issues. Documenting them here makes them recoverable as a follow-up patch without blocking the closed-test promotion.

## Acceptance criteria — ready for closed test

- [ ] Finding #1 (CHRONO_FIELD slow) implemented and tested.
- [ ] Finding #2 (GOLDEN_ZIGGURAT fortune leak) implemented and tested.
- [ ] Finding #7 (LabsScreen dead expression) removed.
- [ ] `./run-gradle.sh test` BUILD SUCCESSFUL with no regressions; new tests added per finding.
- [ ] `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL.
- [ ] AAB versionCode bumped (v3 → v4) and uploaded to internal track.
- [ ] CHANGELOG.md entry under `[Unreleased]` named "RO-09 — Pre-closed-test fixes".
- [ ] STATE.md current-objective updated.
- [ ] RUN_LOG.md entry appended.

---

## Findings

### 🔴 #1 — CHRONO_FIELD Ultimate Weapon does nothing functional

**Severity:** CRITICAL — visible feature is non-functional
**Files:**
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt:368-372` (only consumer of `chronoActive`, render-only overlay)
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/entities/EnemyEntity.kt:46-52` (uses raw `speed` with no chrono modulation)
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt:328` (`entities.forEach { it.update(deltaTime) }` — passes raw `deltaTime` to all entities)

**Symptom:** the CHRONO_FIELD UW description claims *"Slows all enemies to 10 % speed for duration"* (8 s, 75 Power-Stone unlock cost). The activate path sets `chronoActive = true`; the expire path (line 444 in `updateUWs`) resets it. The **only** consumer of `chronoActive` anywhere in the codebase is the rendering overlay:

```kotlin
// GameEngine.render — line 368
if (chronoActive) {
    val p = Paint().apply { color = 0x222196F3; style = Paint.Style.FILL }
    canvas.drawRect(0f, 0f, screenWidth, screenHeight, p)
}
```

`EnemyEntity.update` reads its own `speed` field directly with no chrono modulation. `GameEngine.update` passes raw `deltaTime` to every entity. Net: a player who unlocks CHRONO_FIELD spends 75 Power Stones and sees a purple-tinted screen for 8 seconds, with zero gameplay benefit.

**Same shape as the RO-08 findings** — feature wired into UI / cooldown / cost path but never connected to gameplay.

**Fix sketch:** scale `deltaTime` for enemies inside the existing `entities.forEach` block when `chronoActive` is true. Apply only to `EnemyEntity` so projectiles, orbs, and the ziggurat are not affected:

```kotlin
// GameEngine.update — replace line 328
entities.forEach { e ->
    val dt = if (chronoActive && e is EnemyEntity) deltaTime * CHRONO_SLOW_FACTOR else deltaTime
    e.update(dt)
}
```

With `CHRONO_SLOW_FACTOR = 0.10f` defined in the companion object alongside the other UW constants. This matches the description's "10 % speed" wording and keeps projectile / orb timing unaffected.

**Test plan:**
- New test `GameEngineTest.kt::RO09 CHRONO_FIELD active slows enemies to 10 percent`. Spawn an `EnemyEntity` at known position. Tick the engine for 1 s with chrono inactive; record displacement. Reset, activate CHRONO_FIELD via reflection (or via `activateUW` if a CHRONO_FIELD UW is equipped at level 1), tick 1 s, assert displacement is approximately 0.10× the baseline.
- Negative test: chrono inactive → enemy moves at full speed.
- Negative test: chrono active does **not** slow projectile entities.

**Risk:** very low. `EnemyEntity.update` is the sole movement path for enemies. The change is additive and gated on a boolean.

---

### 🟠 #2 — GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` leak

**Severity:** MODERATE — cash-economy correctness during overlapping buffs
**Files:**
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt:444-448` (GOLDEN expire path)
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt:236` (FORTUNE activate path — same shape, opposite direction)

**Symptom:** `fortuneMultiplier` is a single shared field used by both Step Overdrive FORTUNE (3.0×) and Ultimate Weapon GOLDEN_ZIGGURAT (5.0× via `coerceAtLeast`). The expire-on-effect-timeout path:

```kotlin
// GameEngine.updateUWs — line 444
UltimateWeaponType.GOLDEN_ZIGGURAT -> {
    goldenZigActive = false
    preGoldenStats?.let { applyStats(it) }; preGoldenStats = null
    if (activeOverdrive == null) fortuneMultiplier = 1.0
    if (activeOverdrive == null) { zig.overdriveColor = 0; zig.overdriveProgress = 0f }
}
```

The check `if (activeOverdrive == null)` was intended to preserve FORTUNE's 3.0× when GOLDEN expires while FORTUNE is still active. But the check fires the same way for ASSAULT, FORTRESS, and SURGE — none of which set `fortuneMultiplier`. So:

| Scenario at GOLDEN expiry | `fortuneMultiplier` post-fix | `fortuneMultiplier` actual | Correct? |
|---|---|---|---|
| No overdrive active | 1.0 | 1.0 | ✓ |
| FORTUNE active | 3.0 | 5.0 → 5.0 (preserved) | ❌ should be 3.0 |
| ASSAULT active | 1.0 | 5.0 → 5.0 (leaked) | ❌ should be 1.0 |
| FORTRESS active | 1.0 | 5.0 → 5.0 (leaked) | ❌ should be 1.0 |
| SURGE active | 1.0 | 5.0 → 5.0 (leaked) | ❌ should be 1.0 |

The most exploitable case: activate ASSAULT (1.0× cash), then GOLDEN_ZIGGURAT (5.0× cash). When GOLDEN expires while ASSAULT is still active, the 5.0× multiplier persists for the remaining ASSAULT duration (up to ~50 s).

The opposite-direction leak in the FORTUNE activate path (line 236) is also wrong but less impactful — it forces `fortuneMultiplier = 3.0` even if GOLDEN's 5.0× is currently active, downgrading the player's buff.

**Fix sketch:**

```kotlin
// GameEngine.updateUWs — replace line 446
UltimateWeaponType.GOLDEN_ZIGGURAT -> {
    goldenZigActive = false
    preGoldenStats?.let { applyStats(it) }; preGoldenStats = null
    fortuneMultiplier = if (activeOverdrive == OverdriveType.FORTUNE) 3.0 else 1.0
    if (activeOverdrive == null) { zig.overdriveColor = 0; zig.overdriveProgress = 0f }
}
```

```kotlin
// GameEngine.activateOverdrive — replace line 236
OverdriveType.FORTUNE -> {
    fortuneMultiplier = fortuneMultiplier.coerceAtLeast(3.0)
    ziggurat?.let { it.overdriveColor = 0xFFFFD700.toInt() }
}
```

Symmetrical to the GOLDEN_ZIGGURAT activate path which already uses `coerceAtLeast(5.0)`.

Also worth checking the existing `expireOverdrive` (line 274) — it currently does `fortuneMultiplier = 1.0` unconditionally, which is correct when GOLDEN_ZIGGURAT is *not* active but wrong when it is. Fix:

```kotlin
// GameEngine.expireOverdrive — replace fortuneMultiplier reset
fortuneMultiplier = if (goldenZigActive) 5.0 else 1.0
```

**Test plan:**
- `GameEngineTest::RO09 GOLDEN_ZIGGURAT expiry preserves FORTUNE multiplier when FORTUNE active`
- `GameEngineTest::RO09 GOLDEN_ZIGGURAT expiry resets fortuneMultiplier when ASSAULT active` (regression guard for the leak)
- `GameEngineTest::RO09 FORTUNE activation does not downgrade GOLDEN_ZIGGURAT multiplier`
- `GameEngineTest::RO09 expireOverdrive preserves GOLDEN_ZIGGURAT multiplier` (mirror)

All four are reflection-style invocations on the existing private helpers, mirroring the patterns in the post-RO-08 `GameEngineTest.kt`.

**Risk:** low. `fortuneMultiplier` reads / writes are confined to `GameEngine` and the kill-cash math at line 685. The changes preserve the stacking rule that the *higher* of the two buffs wins (5.0× when GOLDEN is active or active+FORTUNE) and the lower restores cleanly when one expires.

---

### 🟢 #7 — Dead `total` expression in `LabsScreen.kt`

**Severity:** COSMETIC — dead code, no runtime impact
**Files:**
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreen.kt:106`

**Symptom:**

```kotlin
val total = info.remainingMs + (System.currentTimeMillis() - (System.currentTimeMillis() - info.remainingMs))
```

Algebraically `2 × info.remainingMs`. Variable is never read — the next line recomputes via `info.timeToCompleteHours * 3_600_000`. Likely a refactor leftover.

**Fix sketch:** delete the line.

**Test plan:** none required (dead code removal). Visual regression-test by running the app and confirming the labs progress bar still renders (covered by existing manual smoke tests).

**Risk:** zero.

---

## Deferred findings (v1.x)

These are documented for tracking but **not** blockers for closed test.

### 🟡 #3 — STEP_MULTIPLIER × Cross-Validator unit mismatch

**Files:** `data/sensor/DailyStepManager.kt:128-139`, `data/healthconnect/StepCrossValidator.kt:73-103`

**Symptom:** post-RO-08, `record.creditedSteps` includes the STEP_MULTIPLIER bonus. The cross-validator compares it raw against `hcSteps` (real walking, no multiplier):

```kotlin
// StepCrossValidator — Level 2 cap (offenseCount ≥ 3)
if (record.creditedSteps > hcSteps) {
    val excess = record.creditedSteps - hcSteps
    runInTransaction { playerRepository.spendSteps(excess); ... }
}
```

A player with prior CV offenses + STEP_MULTIPLIER level ≥ 1 gets the multiplier bonus deducted on every subsequent offense day even when their walking is legitimate. Severity is bounded: only triggers after `offenseCount ≥ 1` AND a discrepancy day, AND the over-deduction never goes below the legitimate HC walking total.

**Why deferred:** requires schema thought (track gross-up amount separately, or compare in pre-multiplier units). Closed-test cohort almost certainly won't have CV offenses — they're new players with clean step history. Real-world impact during the 14-day window is approximately zero.

**Fix sketch (v1.x):** add a `multiplierBonusApplied: Long` column to `daily_step_record`. Compute pre-multiplier credit during `recordSteps`. Cross-validator compares `creditedSteps - multiplierBonusApplied` against `hcSteps`. Migration v9 → v10 with default 0 for existing rows.

### 🟡 #4 — Currency lifetime counter desync

**Files:** `data/repository/PlayerRepositoryImpl.kt:29-43`, `data/local/PlayerProfileDao.kt:55-79`

**Symptom:** `addGems` / `spendGems` / `addPowerStones` / `spendPowerStones` each fire two unrelated `@Query` UPDATEs back-to-back, not in `@Transaction`:

```kotlin
override suspend fun addGems(amount: Long) {
    dao.adjustGems(amount)              // UPDATE 1: wallet
    dao.incrementGemsEarned(amount)     // UPDATE 2: lifetime — separate transaction
}
```

A crash between them leaves the wallet adjusted but the lifetime counter not incremented (or vice versa). Compare to `addSteps` which does both in a single `adjustStepBalance` query (atomic by SQL).

**Why deferred:** lifetime counters are display-only (Stats screen). No game logic reads them. A drift of a few gems in lifetime totals is invisible to gameplay.

**Fix sketch (v1.x):** wrap each pair in `@Transaction`, OR merge into a single `UPDATE … SET balance = …, lifetimeEarned = …` query mirroring the existing `adjustStepBalance` pattern.

### 🟡 #5 — TOCTOU race on `spendGems` / `spendPowerStones`

**Files:** same as #4.

**Symptom:** the DAO uses `MAX(0, gems + :delta)` so an over-spend clamps the wallet at 0, but `incrementGemsSpent` records the full requested amount. Concurrent calls (or a tap before Flow propagation) can leave lifetime-spent > actually-deducted.

**Why deferred:** wallet stays correct (the `MAX(0, ...)` floor is enforced). Only lifetime stats drift. Same display-only impact as #4.

**Fix sketch (v1.x):** add `spendGemsIfSufficient(amount): Int` atomic SQL guard mirroring the existing `adjustStepBalanceIfSufficient` pattern. Repository spendGems delegates and increments lifetime only on success. Same for `spendPowerStones`.

### 🟡 #6 — Per-kill battle-step credit on `viewModelScope`

**Files:** `presentation/battle/BattleViewModel.kt:355-365`

**Symptom:** end-of-round persistence migrated to `applicationScope` in RO-03 B.3 PR 2 to survive mid-round nav-away, but per-kill credits still launch on `viewModelScope`. If the user backs out mid-round, in-flight kill credits are cancelled.

```kotlin
engine.onStepReward = { amount, x, y ->
    viewModelScope.launch { val credited = awardBattleSteps(amount); … }
}
```

**Why deferred:** loss bounded to ~1 step per pending callback (each kill credits 1 step). Even an extreme mid-round nav-away during a 50-enemy wave loses at most ~50 steps. Practical impact during closed test is tiny.

**Fix sketch (v1.x):** route through `applicationScope` like `markEndedAndLaunchPersistence` does. Inject `applicationScope` into `wireStepRewardCallback`.

---

## Out of scope — verified clean

The audit also covered these, all confirmed correct after RO-08:

- All 4 fields of `CardEffectResult` (`stats`, `secondWindHpPercent`, `gemMultiplier`, `cashBonusPercent`) consumed by `BattleViewModel`. RO-08 #4 closed `gemMultiplier`.
- All 9 `CardType` cases wired in `ApplyCardEffects`. (Minor: `IRON_SKIN` description "+10% Defense Absolute" adds raw +10 flat block — unit-mismatch in the description string only, not a wiring bug. Non-blocker.)
- All 4 `OverdriveType` variants propagate after RO-08 #2 (ASSAULT 2× attack speed, FORTRESS 2× regen + +50 % defense, FORTUNE 3× cash, SURGE UW reset).
- All 6 UW types now correctly wired *after* finding #1 + #2 land (DEATH_WAVE, CHAIN_LIGHTNING, BLACK_HOLE, POISON_SWAMP, GOLDEN_ZIGGURAT all good; CHRONO_FIELD broken — finding #1).
- All 23 `UpgradeType` enums have a downstream consumer (after RO-08).
- All 6 `MissionCategory` daily-mission types have a progress handler (BATTLE in BVM, UPGRADE in WorkshopVM/LabsVM, WALKING in DailyStepManager).
- `secondWindUsed` reset between rounds via `engine.init`; `secondWindHpPercent` re-pushed in `playAgain`.
- Round-end persistence transaction + per-write resilience preserved (RO-02 B.2 PR 5 + RO-03).
- Atomicity for purchase, claim-milestone, credit-battle-steps (RO-02 sites all in place).

---

## Implementation plan (single PR, codename **RO-09**)

1. **Commit 1** — `fix(battle): wire CHRONO_FIELD UW to actually slow enemies (RO-09 #1)`
   - `GameEngine.update` entity loop: scale `deltaTime` for `EnemyEntity` when `chronoActive`.
   - `GameEngine` companion: new `CHRONO_SLOW_FACTOR = 0.10f` constant.
   - `GameEngineTest`: +3 tests (slow active, no slow when inactive, projectiles unaffected).

2. **Commit 2** — `fix(battle): GOLDEN_ZIGGURAT × overdrive fortuneMultiplier stacking (RO-09 #2)`
   - GOLDEN_ZIGGURAT expire path: `fortuneMultiplier = if (FORTUNE active) 3.0 else 1.0`.
   - FORTUNE activate path: `coerceAtLeast(3.0)` to preserve GOLDEN's higher value.
   - `expireOverdrive`: `fortuneMultiplier = if (goldenZigActive) 5.0 else 1.0`.
   - `GameEngineTest`: +4 tests covering each stacking path.

3. **Commit 3** — `chore(labs): remove dead total expression (RO-09 #7)`
   - Delete the dead line in `LabsScreen.kt`.

4. **Commit 4** — `docs(ro-09): sync state, run log, changelog`
   - CHANGELOG entry "RO-09 — Pre-closed-test fixes".
   - `STATE.md` current-objective.
   - `RUN_LOG.md` entry.
   - `AGENTS.md` test count delta.
   - `.kiro/steering/source-files.md` updated entries for `GameEngine.kt`, `GameEngineTest.kt`.

After commits land, bump `versionCode 3 → 4`, run `./run-gradle.sh bundleRelease`, and re-upload to internal track. Internal-track verification → promote internal v4 → closed track.

## References

- RO-08 (just-shipped 4-fix bundle): commits `5c2baca` … `b7b8824`
- ADR-0003 (Battle Step Rewards): `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md`
- External reviews that surfaced the original RO-08 findings: `docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX*.md`
- Master plan: `docs/plans/master-plan.md`
- Plan 31 (Play Console): `docs/plans/plan-31-play-console.md`
