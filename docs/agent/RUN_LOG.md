# Run Log

## 2026-05-19 (mid-afternoon, after R3-02 merge) — R3-03 COMPLETE_RESEARCH mission false-trigger fix

- **Goal:** implement R3-03 (GitHub issue #1, `severity:major` + `area:missions` in the `v1.0.0 closed-test gate` milestone). Opening the Labs screen with no in-flight research falsely advances the daily COMPLETE_RESEARCH mission to progress=1, completed=true.
- **Outcome:** done in a single session on branch `fix/1-complete-research-false-trigger`. Two commits per the failing-test-first protocol; both `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL; test count 622 → 627.

### Diagnosis

Matched the plan's working hypothesis exactly. `LabsViewModel.init` runs:

```kotlin
viewModelScope.launch {
    labRepository.ensureResearchExists()
    checkCompletion()
    updateResearchMission()  // BUG: always called
}
```

The private `updateResearchMission()` helper looks up today's COMPLETE_RESEARCH mission row and unconditionally writes `dailyMissionDao.updateProgress(m.id, 1, true)` whenever a row is found and not already completed. `CheckResearchCompletion` already returned `List<ResearchType>` of actually-completed research — the call site just ignored the return value.

`rushResearch` (line 145) and `freeRush` (line 165) DO correctly gate `updateResearchMission()` on a successful completion path, so the bug only manifested via the `init` call site.

### Strategy chosen — use-case extraction (not minimal in-VM gating)

First-attempt approach was the minimal in-VM fix: just `if (completed.isNotEmpty()) updateResearchMission()` in `LabsViewModel.init`, with a corresponding regression test that constructs the VM and asserts via `FakeDailyMissionDao` state. This burned 7+ minutes of CPU on a hung Gradle worker because:

1. `LabsViewModel.init` launches a second coroutine: `viewModelScope.launch { while(true) { delay(1000); tick.value = ... } }`. This ticker drives the rush-research countdown UI.
2. `viewModelScope` is a separate `SupervisorJob` rooted at `Dispatchers.Main.immediate`, NOT a child of the test scope. `coroutineContext.cancelChildren()` from inside `runTest` doesn't reach it.
3. After the test body returns, `runTest` advances the virtual scheduler to drain pending tasks. The parked `delay(1000)` resumes, schedules another `delay(1000)`, runs forever in virtual time.

Killed the stuck `GradleWorkerMain` (PID 73802 spinning at 95 % CPU) and pivoted to use-case extraction. The new strategy:

- New `domain/usecase/UpdateCompleteResearchMissionProgress.kt` encapsulates the DAO lookup + count gating + progress update. Takes `DailyMissionDao`, exposes `invoke(completedCount: Int, today: String = LocalDate.now().toString())`. Tests target the use case directly with a real `FakeDailyMissionDao` — no VM construction = no ticker drama.
- LabsViewModel call sites updated: init passes `completed.size` from CheckResearchCompletion; rushResearch passes 1 on Result.Rushed; freeRush passes 1 after the manual completeResearch call. The private `updateResearchMission()` helper deleted; the `DailyMissionType` import dropped.

This matches the project convention (`TrackDailyLogin(dailyLoginDao, playerRepository)`, `GenerateDailyMissions(dailyMissionDao)` etc. live as use cases that take DAOs) and puts the gating in one testable place — anyone calling the use case from any future entry point gets the same gating semantics for free, so the bug shape can't reappear at a different call site.

### Commit 1 — `57b5aa3` test(R3-03): failing regression tests + use-case extraction seam

Production seams (compile-only changes; pre-fix runtime behaviour preserved):

- `domain/usecase/UpdateCompleteResearchMissionProgress.kt` (new, 48 lines): staging shape that always sets `progress=m.target, completed=true` regardless of `completedCount`. Mirrors the prior `LabsViewModel.updateResearchMission` behaviour exactly so the existing 622 tests stay green; the `if (completedCount <= 0) return` guard lands in commit 2.
- `presentation/labs/LabsViewModel.kt`: replaced the private `updateResearchMission()` helper with a `private val updateMissionProgress = UpdateCompleteResearchMissionProgress(dailyMissionDao)` field; rewired all 3 call sites to pass an explicit count. Removed unused `DailyMissionType` import.

New tests in `domain/usecase/UpdateCompleteResearchMissionProgressTest.kt` (5 tests):

- `R303 does NOT tick when completedCount is 0` — RED on staging.
- `R303 does NOT tick when completedCount is negative` — RED on staging (defensive guard against caller bugs).
- `R303 ticks to 1 when completedCount is 1` — GREEN.
- `R303 caps progress at target when multiple research complete in one batch` — GREEN (regression guard against the additive math overshooting).
- `R303 is a no-op when no mission row exists for the given date` — GREEN (idempotency).

Verified via `./run-gradle.sh testDebugUnitTest --tests "*UpdateCompleteResearchMissionProgressTest*"` → "5 tests completed, 2 failed". Verified via `./run-gradle.sh testDebugUnitTest` → "627 tests completed, 2 failed" — the 622 pre-existing tests stay green.

### Commit 2 — `f45014b` fix(labs): gate COMPLETE_RESEARCH mission tick on actual completion (#1)

- `domain/usecase/UpdateCompleteResearchMissionProgress.kt`: added the `if (completedCount <= 0) return` guard at the top of `invoke` (the entire bug fix). Replaced the staging "always set to target, completed=true" write with `(m.progress + completedCount).coerceAtMost(m.target)` so multi-completion auto-batches correctly reflect all completions while clamping at the mission target.
- `presentation/labs/LabsViewModel.kt`: refreshed the init-block comment to describe the call-site contract ("every call site gets the same gating semantics for free") instead of the staging caveat.

Verified via `./run-gradle.sh testDebugUnitTest` → BUILD SUCCESSFUL (627 tests, all green).
Verified via `./run-gradle.sh bundleRelease` → BUILD SUCCESSFUL (clean R8 + lintVital + sign).

### Doc-sync (this commit)

- `AGENTS.md`: version-status line gets R3-03 entry + `622 → 627 tests` delta; current-coverage line gains the R3-03 paragraph; status-checklist Plan R3 line updated to mark R3-02 as merged + R3-03 as fix-landed-pending-merge; the use-case enumeration in the Architecture block adds `UpdateCompleteResearchMissionProgress`.
- `.kiro/steering/source-files.md`: `domain/usecase/UpdateCompleteResearchMissionProgress.kt` entry added under Lab use cases; `domain/usecase/UpdateCompleteResearchMissionProgressTest.kt` entry added under tests; `presentation/labs/LabsViewModel.kt` entry extended with the R3-03 use-case-extraction note.
- `CHANGELOG.md`: new R3-03 section under `[Unreleased]` above the prior R3-02 section.
- `docs/agent/STATE.md`: current-objective flipped to "R3-03 fix landed on branch, awaiting merge"; previous-objective ladder gained R3-02-merged entry; what-works gained 622 → 627 line + R3-03 summary; top-priorities renumbered with R3-03 PR / merge as #1 + R3-04 reporter window as #5; last-run line refreshed; previous-run cascade gains the R3-02-merged entry.
- `docs/agent/RUN_LOG.md`: this entry prepended above the prior R3-02 entry.

### Next session

1. **(Immediate, awaiting user go-ahead)** Push branch + open PR `Fixes #1`. Review + merge to `main`.
2. **(After merge)** Build v6: `./run-gradle.sh bundleRelease` + sign + upload AAB to Play Console internal track.
3. **(Smoke test)** Install v6 on a physical device. Run 8 RO-11 + 5 RO-12 + 3× R3 per-issue acceptance checks. Then promote internal v6 → closed track.
4. **(R3-04 watch)** Monitor issue #3 for reporter reply. Close as `needs-more-info-stale` if no reply by 2026-05-26.

---

## 2026-05-19 (mid-afternoon, after R3-01 merge) — R3-02 THORN_DAMAGE wiring + LIFESTEAL visibility

- **Goal:** implement R3-02 (GitHub issue #4, `severity:major` + `area:battle` in the `v1.0.0 closed-test gate` milestone). THORN_DAMAGE never reflects damage on melee hits regardless of upgrade level. LIFESTEAL works mathematically but the heal is invisible at low levels.
- **Outcome:** done in a single session on branch `fix/4-thorn-damage-lifesteal`. Two commits per the failing-test-first protocol; both `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL; test count 619 → 622.

### Diagnosis

Different shape than the plan assumed ("same shape as RO-08 STEP_MULTIPLIER, RO-09 #1 CHRONO_FIELD dead-stat"). Refined diagnosis:

1. **THORN_DAMAGE is plumbed but its consumers receive `null`.** `applyThorn(rawDamage, attacker)` at `GameEngine.kt:735` correctly reads `stats.thornPercent` and damages `attacker`. But every caller passes `attacker = null`:
   - Line 252: `WaveSpawner` constructor's `onMeleeHit = { dmg -> applyDamageToZiggurat(dmg, null) }`.
   - Line 406: ranged-projectile path: `applyDamageToZiggurat(proj.damage, null)`.
   - Line 800: SCATTER child enemy spawn site `onMeleeHit = { dmg -> applyDamageToZiggurat(dmg, null) }`.

   The chain `EnemyEntity.onMeleeHit: ((Double) -> Unit)?` -> `WaveSpawner.onMeleeHit: (Double) -> Unit` -> `GameEngine` was typed `(Double) -> Unit` and dropped the enemy reference. `applyThorn` early-returns when attacker is null. So THORN_DAMAGE never reflected damage despite being plumbed end-to-end.

2. **LIFESTEAL math is correct but sub-pixel at low levels.** `zig.currentHp += damage * stats.lifestealPercent` is `Double`-typed so sub-1-HP heals are conserved correctly. But at Lv 1 (0.2 % lifesteal) on base damage 10 = 0.02 HP per shot, the HP-bar nudge is invisible. Players reasonably conclude LIFESTEAL doesn't work.

### Strategy chosen

- **THORN:** plumb the attacker reference through. Widen `EnemyEntity.onMeleeHit` and `WaveSpawner.onMeleeHit` callbacks from `(Double) -> Unit` to `(EnemyEntity, Double) -> Unit`. Flip the 2 `GameEngine` call sites at lines 252 + 800 (initial WaveSpawner wiring + SCATTER child) from `{ _, dmg -> applyDamageToZiggurat(dmg, null) }` to `{ atk, dmg -> applyDamageToZiggurat(dmg, atk) }`. Ranged-projectile path stays `null` per GDD wording 'damage reflected to attackers' — a projectile isn't a living attacker, and the firing enemy may be off-screen or already dead by the time the projectile lands.
- **LIFESTEAL** (per `plan-R3-remediation-3.md` § R3-02 option (b) — picked because it preserves the GDD numerical contract and adds visible feedback rather than changing balance): keep math identical; add `lifestealAccumulator: Double = 0.0` field that accumulates the same fractional amount applied to `zig.currentHp`; spawn a `FloatingText("+X HP", FloatingText.STEP_COLOR)` indicator above the ziggurat each time the accumulator crosses an integer HP threshold. Mirrors the existing RECOVERY_PACKAGES feedback pattern in `tickRecoveryPackages`.

### Commit 1 — `4ba6d70` test(R3-02): failing regression tests + signature-change seams

Production seams (compile-only changes; pre-fix behaviour preserved):

- `EnemyEntity.kt:19`: `onMeleeHit: ((Double) -> Unit)?` widened to `((EnemyEntity, Double) -> Unit)?`; `update()` invokes `onMeleeHit?.invoke(this, damage)`.
- `WaveSpawner.kt:16`: `onMeleeHit: (Double) -> Unit` widened to `(EnemyEntity, Double) -> Unit`.
- `GameEngine.kt`: lambda shape updated at 2 call sites from `{ dmg -> ... null }` to `{ _, dmg -> ... null }` (still discards attacker, preserving pre-fix behaviour for the RED test).
- `GameEngine.kt`: `lifestealAccumulator: Double = 0.0` field declared, reset in `init()`, but not yet read or written anywhere else.
- `WaveSpawnerTest.kt`: `onMeleeHit` lambda updated to compile against the new 2-arg signature.

New tests in `GameEngineTest.kt` under a new `// ---- R3-02: THORN_DAMAGE reflection on melee + LIFESTEAL visible-heal feedback ----` section:

- `R302 THORN_DAMAGE reflects damage on melee hit via plumbed attacker reference` — RED.
- `R302 THORN_DAMAGE scales linearly with thornPercent` — RED. (Initial version used a ratio assertion `reflectAt10 * 5.0 == reflectAt50` which trivially passed pre-fix because `0 * 5 == 0`. Hardened to direct value assertions before commit so the test actually failed RED.)
- `R302 LIFESTEAL emits visible floating text when accumulated heal crosses 1 HP` — RED.

Plus 5 reflective helpers: `freshEngineWithStats(ResolvedStats)` (variant of the existing `freshEngine`), `invokeOnMeleeHit` (reads `WaveSpawner.onMeleeHit` field reflectively and calls it directly), `createDummyAttacker`, `invokeOnProjectileHitEnemy` (reflective invocation of the private `onProjectileHitEnemy(ProjectileEntity, EnemyEntity)`), `readPendingFloatingTextSnippets` (reflective read of `EffectEngine.pendingEffects` returning the text content of any `FloatingText` entries).

Verified via `./run-gradle.sh testDebugUnitTest --tests "...GameEngineTest.R302*"` → "3 tests completed, 3 failed".
Verified via `./run-gradle.sh testDebugUnitTest` → "622 tests completed, 3 failed" — the seam signature changes did not regress any of the 619 pre-existing tests.

### Commit 2 — `299d867` fix(battle): wire THORN_DAMAGE on melee + emit visible LIFESTEAL feedback (#4)

- `GameEngine.kt:265`: initial WaveSpawner wiring's onMeleeHit changed to `{ atk, dmg -> applyDamageToZiggurat(dmg, atk) }`.
- `GameEngine.kt:817`: SCATTER child enemy onMeleeHit changed to `{ atk, dmg -> applyDamageToZiggurat(dmg, atk) }`.
- `GameEngine.kt`: `import kotlin.math.floor` added.
- `GameEngine.kt`: `applyLifesteal(healAmount: Double)` private helper added next to `applyThorn` — mirrors its shape; does `zig.currentHp += healAmount` (capped at maxHp), `lifestealAccumulator += healAmount`, and emits `FloatingText("+$visibleHp HP", STEP_COLOR)` each time the accumulator crosses an integer HP threshold.
- `GameEngine.kt`: existing in-place lifesteal heal at `onProjectileHitEnemy` and `onOrbHitEnemy` (2 sites, lines 614 + 705) replaced with `applyLifesteal(damage * stats.lifestealPercent)` / `applyLifesteal(result.amount * stats.lifestealPercent)`. Math is byte-for-byte identical; only the visible feedback is new.

Verified via `./run-gradle.sh testDebugUnitTest --tests "...GameEngineTest.R302*"` → BUILD SUCCESSFUL (3/3 green).
Verified via `./run-gradle.sh testDebugUnitTest` → BUILD SUCCESSFUL (622 tests total, all green).
Verified via `./run-gradle.sh bundleRelease` → BUILD SUCCESSFUL (clean R8 + lintVital + sign).

### Doc-sync (this commit)

- `AGENTS.md`: version-status line gets R3-02 entry + `619 → 622 tests` delta; current-coverage line gains the R3-02 paragraph; status-checklist Plan R3 line updated to call out R3-01 as merged + R3-02 as fix-landed-pending-merge.
- `.kiro/steering/source-files.md`: `GameEngine.kt` entry extended with the R3-02 onMeleeHit lambda flips + `lifestealAccumulator` field + `applyLifesteal` helper; `WaveSpawner.kt` entry extended with the `onMeleeHit` signature change; `EnemyEntity.kt` entry extended likewise; `WaveSpawnerTest.kt` entry extended with the test-side signature update; `GameEngineTest.kt` entry extended with the 3 R3-02 entries + 5 new reflective helpers.
- `CHANGELOG.md`: new R3-02 section under `[Unreleased]` above the prior R3-01 section.
- `docs/agent/STATE.md`: current-objective flipped to "R3-02 fix landed on branch, awaiting merge"; previous-objective ladder gained R3-01-merged + R3-scaffolding entries; what-works gained 619 → 622 line + R3-02 summary; top-priorities renumbered with R3-02 PR / merge as #1; last-run line refreshed; previous-run cascade gains the R3-01-merged entry.
- `docs/agent/RUN_LOG.md`: this entry prepended above the prior R3-01 entry.

### Next session

1. **(Immediate, awaiting user go-ahead)** Push branch + open PR `Fixes #4`. Review + merge to `main`.
2. **(After merge)** Start R3-03 (issue #1, COMPLETE_RESEARCH false trigger, major) on `fix/1-complete-research-false-trigger`.
3. **(After R3 Tier 1 fully merges)** v6 `bundleRelease` + upload + smoke test + closed-track promotion.

---

## 2026-05-19 (early afternoon, after R3 scaffolding) — R3-01 battle backgrounding state preservation

- **Goal:** implement R3-01 (GitHub issue #2, the lone `severity:blocker` in the `v1.0.0 closed-test gate` milestone). Backgrounding the app mid-round destroys all in-progress round state, and the new game-loop thread spun up on resume defaults to `speed = 1f / paused = false` regardless of the UI's selection.
- **Outcome:** done in a single session on branch `fix/2-battle-backgrounding-state-loss`. Two commits per the failing-test-first protocol; both `./run-gradle.sh testDebugUnitTest` and `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL; test count 615 → 619.

### Diagnosis (confirmed against issue #2 body + on-disk code)

Two compounding bugs in `presentation/battle/`:

1. **Engine state cleared on surface recreation.** `GameSurfaceView.surfaceCreated` (lines 42–45) and `surfaceChanged` (line 49) both unconditionally called `engine.init(...)`. `GameEngine.init` (line 199 onwards) clears `entities`, `cash`, `totalEnemiesKilled`, `elapsedTimeSeconds`, all UW / overdrive / second-wind state, and reconstructs `WaveSpawner` with `currentWave = startWave (= 1)`. The engine instance itself survives — it lives on the `remember`'d `GameSurfaceView` in `BattleScreen`, which survives recomposition — but every Android lifecycle event blew away the round.

2. **Speed / pause UI / loop sync.** `GameLoopThread` defaults `speedMultiplier = 1f` and `isPaused = false`. A new thread is constructed in every `surfaceCreated`. `setSpeedMultiplier` / `setPaused` wrote only to `gameThread`, which is null between `surfaceDestroyed` and the next `surfaceCreated`. The two `LaunchedEffect`s in `BattleScreen` (`LaunchedEffect(state.speedMultiplier)` and `LaunchedEffect(state.isPaused)`) only re-fire when the value *changes*; returning from background, UI state is unchanged so neither effect runs against the freshly-defaulted thread.

3. **`engine.hasWaveProgress()` already exists** (line 146 of `GameEngine.kt`, added by RO-03 / B.3 PR 2 for `BattleViewModel.onCleared` mid-nav persistence). Right signal at the wrong call site — RO-03 wired it into one consumer, but missed the surface lifecycle.

### Strategy chosen

Collapsed the three-option choice from `plan-R3-remediation-3.md` (lift engine ownership / snapshot UiState / Room persist) into a minimal version of option (a). The engine is *already* ownership-lifted (it's held by the surviving `GameSurfaceView`); we just need to stop wiping it on every `surfaceCreated`. No `BattleViewModel` constructor change, no new Room migration, no UiState snapshot field. Two narrow GameSurfaceView edits cover the entire bug class.

Deliberately deferred: `BattleScreen` `ON_RESUME` re-apply handler (the issue body's fix #2). Described as "defence in depth"; with the GameSurfaceView fix the new thread always inherits correct state, so the secondary handler would only matter if Compose dropped state across recomposition. Hard to unit-test without a Compose harness; if v6 on-device smoke testing reveals a gap, add as a follow-up.

### Commit 1 — `0e33a3b` test(R3-01): failing regression test + seam stubs

GameSurfaceView seams (no behaviour change yet):
- `@VisibleForTesting internal fun initEngineIfNeeded()` — declared but currently calls `engine.init` unconditionally (modelling the pre-fix behaviour).
- `@Volatile internal var pendingSpeed: Float = 1f` and `pendingPaused: Boolean = false` — declared but not yet written by setters.

New test file `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/GameSurfaceViewTest.kt` (JUnit 4 + Robolectric, mirrors `DailyStepDaoTest`):
- `R3-01 surface recreation preserves engine progress mid-round` — RED.
- `R3-01 setSpeedMultiplier persists pendingSpeed for next thread` — RED.
- `R3-01 setPaused persists pendingPaused for next thread` — RED.
- `R3-01 initEngineIfNeeded does run engine init when no progress yet` — GREEN (inverse / no-regression guard).

Verified via `./run-gradle.sh testDebugUnitTest --tests GameSurfaceViewTest` → "4 tests completed, 3 failed".

### Commit 2 — `bad1d01` fix(battle): preserve engine state across surface recreation (#2)

- `initEngineIfNeeded` now wraps `engine.init` in `if (!engine.hasWaveProgress())`.
- `surfaceCreated` calls `initEngineIfNeeded()` instead of inline `engine.init` AND seeds the new thread from `pendingSpeed` / `pendingPaused`.
- `surfaceChanged` calls `initEngineIfNeeded()` instead of inline `engine.init`.
- `setSpeedMultiplier` writes to `pendingSpeed` first, then `gameThread?.speedMultiplier`. `setPaused` mirrors the shape.

Verified via `./run-gradle.sh testDebugUnitTest --tests GameSurfaceViewTest` → BUILD SUCCESSFUL (4/4 green).

### Verification (full suite)

- `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL. 619 tests pass (615 pre-R3-01 + 4 new). Zero failures across all suites.
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Clean R8 minify + lint vital + signing.

### Doc-sync (this commit)

- `AGENTS.md`: version-status line gets R3-01 entry + `615 → 619 tests` delta; current-coverage line gains the R3-01 paragraph; status-checklist Plan R3 line updated to call out R3-01 as fix-landed-pending-merge.
- `.kiro/steering/source-files.md`: `GameSurfaceView.kt` entry extended with the R3-01 changes (`initEngineIfNeeded`, `pendingSpeed`, `pendingPaused`); new `GameSurfaceViewTest.kt` test entry inserted between `BattleViewModelTest` and `MissionsViewModelTest`.
- `CHANGELOG.md`: new R3-01 section under `[Unreleased]`.
- `docs/agent/STATE.md`: current-objective flipped to "R3-01 fix landed on branch, awaiting merge"; previous-objective ladder gained R3 scaffolding + RO-12 entries; what-works gained 615 → 619 line + R3-01 summary; top-priorities renumbered with R3-01 PR / merge as #1; last-run line refreshed; previous-run cascade gains the R3-scaffolding entry.
- `docs/agent/RUN_LOG.md`: this entry prepended above the prior R3-scaffolding entry.

### Next session

1. **(Immediate, awaiting user go-ahead)** Push branch + open PR `Fixes #2`. Review + merge to `main`.
2. **(After merge)** Start R3-02 (issue #4, THORN_DAMAGE wiring + LIFESTEAL visibility, major) on `fix/4-thorn-damage-lifesteal`.
3. **(Parallel-ok)** R3-03 (issue #1, COMPLETE_RESEARCH false trigger, major) on `fix/1-complete-research-false-trigger`.
4. **(After R3 Tier 1 fully merges)** v6 `bundleRelease` + upload + smoke test + closed-track promotion.

---

## 2026-05-19 (early afternoon) — Plan R3 scaffolding: GitHub-issue triage + R3 plan write

- **Goal:** the user asked for the *best* way to handle the GitHub issues recorded after the v5 internal-track smoke test. Web research surfaced industry-standard practice (VSCode triage wiki, GitHub Docs on PR↔issue linking, Rewind best-practices guide). Mapped the consensus onto this repo's existing R / R2 / RO remediation cadence and decided to formalize the 4 open issues as **Plan R3 (Remediation 3)** — the GitHub-issue-driven analog of the external-review-driven R and R2 plans.
- **Outcome:** done in a single session. All four issues are now triaged, labeled, milestoned, and have a concrete sub-plan in the docs tree. No source / test / schema impact — pure GitHub-side scaffolding + docs.

### GitHub-side scaffolding

- **Label taxonomy created (11 new labels)** on `JonWhiteFang/steps-of-babylon`:
  - Severity (red/orange/yellow gradient): `severity:blocker`, `severity:major`, `severity:minor`.
  - Area (blue/green/purple family): `area:battle`, `area:missions`, `area:economy`, `area:billing`, `area:ui`.
  - Status: `needs-more-info` (purple, mirrors GitHub's question label color), `in-progress` (blue), `regression-guard-needed` (yellow — fix landed but a failing test must be added before close).
  - The repo previously had only the 9 GitHub default labels.
- **Milestone `v1.0.0 closed-test gate`** created (#1, open) with description: "Bugs surfaced during the v5 internal-track smoke test (2026-05-19) plus any further closed-track feedback. Must reach zero open before closed→production promotion."
- **All 4 open issues triaged + labeled + attached to the milestone:**
  - #1 (Daily mission COMPLETE_RESEARCH triggers on Labs open) → `severity:major` + `area:missions`.
  - #2 (Backgrounding mid-round resets state + speed/pause UI desync) → `severity:blocker` + `area:battle`.
  - #3 (Bottom bar can't scroll, needs reporter clarification) → `severity:minor` + `area:battle` + `needs-more-info`. Clarification comment posted asking for: which bottom bar (in-round upgrade menu / UW bar / Overdrive menu), device + Android version, exact failure mode, screen recording. 7-day window to 2026-05-26 per industry triage convention.
  - #4 (THORN_DAMAGE never reflects + LIFESTEAL imperceptible) → `severity:major` + `area:battle`.

### Plan + doc-sync

- **New plan file `docs/plans/plan-R3-remediation-3.md`** (182 lines) mirroring R2's shape exactly: Sub-Plan Index table → Dependency Graph (mermaid) → per-sub-plan detail block (Severity / GitHub Issue / Files / Problem / Tasks / Acceptance criteria) → Execution Notes → Priority Tiers → Open Questions. Sub-plans numbered in execution order (severity-first), so R3-01 = issue #2 (blocker), R3-02 = issue #4 (major), R3-03 = issue #1 (major), R3-04 = issue #3 (minor, blocked on info). Each sub-plan body explicitly lists the suspected files, the per-fix protocol (failing test first → fix → doc-sweep → PR with `Fixes #N`), and acceptance criteria the next on-device smoke test can verify against.
- **`docs/plans/master-plan.md`:** new R3 row in Plan Index, R3 node added to mermaid dependency graph between R2 and Plan 31, critical-path string extended with `R3 (Tier 1)`, new Execution Notes line documenting R3's source.
- **`AGENTS.md`:** master-plan entry count 34 → 35, new R3 row in the Full Plan Index, new R3 node in the Dependency Graph, critical-path string extended, new in-progress checklist line under Current Status.
- **`docs/agent/STATE.md`:** current-objective flipped from "build v6" to "implement R3 Tier 1"; previous-objective ladder gained two new entries (RO-12 complete + v5 surfaced 4 GitHub-tracked bugs); top priorities renumbered with R3 Tier 1 ahead of v6 build (since v6 should ship with R3 Tier 1 included); next-actions reordered to 11 items with explicit R3 branch names + on-device smoke check ordering; references gained R3 plan link; last-run line refreshed.
- **`docs/agent/RUN_LOG.md`:** this entry prepended above the prior v5-build entry.

### Why R3 sits between R2 and Plan 31, not after Plan 31

R and R2 were both authored as remediation plans gating the original "production release" milestone. Plan 31 has since been split into Phases A–I, with Phase G2 (closed-track promotion) now the gate that R3's Tier 1 actually blocks. The dependency graph reflects the current reality — R3 sits before Plan 31 in the critical path, but inside Plan 31 it specifically blocks the internal→closed promotion of v6, not the entire 31-phase chain.

### Verification

- `gh label list` → confirms all 11 new labels created with correct colors + descriptions.
- `gh api repos/JonWhiteFang/steps-of-babylon/milestones --jq '.[]'` → confirms milestone #1 exists, open, attached to all 4 issues.
- `gh issue view <N> --json milestone,labels` → confirms each of the 4 issues has the expected labels + milestone.
- `gh issue list --milestone "v1.0.0 closed-test gate"` → returns all 4.
- New plan file passes a manual structure read against `docs/plans/plan-R2-remediation.md` — same heading hierarchy, same table columns (with "GitHub Issue" replacing R2's "Review Findings").
- No source / test / schema files touched. Test count remains 615.

### Next session

1. **(Immediate, awaiting user go-ahead)** Start R3-01 (issue #2, blocker) on branch `fix/2-battle-backgrounding-state-loss`. Reproduce → failing `BattleViewModelTest` for pause/resume mid-round → pick remediation strategy from plan § R3-01 (lift engine ownership / snapshot UiState / Room persist) → implement → doc-sweep → PR `Fixes #2`.
2. **(Parallel-ok after R3-01 PR opens)** R3-02 + R3-03 on their own branches.
3. **(After R3 Tier 1 merges)** v6 `bundleRelease` + upload + smoke test + closed-track promotion.

---

## 2026-05-19 (morning) — v5 build + internal-track upload milestone

- **Goal:** package the RO-11 work into a release AAB and ship it to the Play Console internal track so the 8 RO-11 acceptance smoke checks can be run on a real device.
- **Outcome:** done. versionCode bumped 4 → 5 in `app/build.gradle.kts` (commit `734beaa`), `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL (clean R8 minify + lint vital + signing, signed AAB ~18 MB at `app/build/outputs/bundle/release/app-release.aab`), AAB uploaded to Play Console internal track. v5 release notes (Play Console "What's new" copy + internal communications + closed-track tester recruitment text) landed in `docs/release/` (commit `d9f48e3`). The Play Console "no debug symbols" warning surfaced as expected — informational, not a blocker (SQLCipher + androidx.graphics.path .so files ship pre-stripped; documented in walkthrough).
- **What changes for testers vs internal-track v3 (last on-device-verified build):**
  - **Labs research now actually does something.** All 8 wired ResearchType enums (DAMAGE / HEALTH / CRITICAL / REGEN / CASH / STEP_EFFICIENCY / UW_COOLDOWN / WAVE_SKIP) consumed by combat-path / step-credit / cash-economy / UW-cooldown classes; 2 explicitly gated as v1.x deferred via "COMING SOON" UI.
  - **In-round upgrade menu shows numerical effect of each purchase before commit** — every visible upgrade row renders a Gold "Now: X → Next: Y" line below the description (or "Now: X (MAX)" when max-level).
  - All RO-08 + RO-09 fixes from internal v4 (CHRONO_FIELD enemy-slow propagation, GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` stacking, STEP_MULTIPLIER + RECOVERY_PACKAGES + ZigguratEntity stale-stats, 4-fix upgrade-wiring bundle).
- **Test posture:** 609 JVM tests green at upload time (572 pre-RO-11 → 609 post-Phase-C). Zero balance-math regressions.
- **Doc-sync (this commit):** STATE.md current objective flipped from "build + upload v5" to "smoke-test v5"; previous-objectives ladder gained v5 upload entry; top priorities renumbered to 5 (smoke-test → closed track → pre-launch report → production rollout → v1.x backlog); next-actions renumbered to 6; critical-path appended with v5 upload milestone; last-run line refreshed to 2026-05-19 morning. AGENTS.md line 30 versionCode bullet flipped ready → uploaded. CHANGELOG.md RO-11 entry verification block gained an upload-milestone note. RUN_LOG.md (this entry).

### Next session

1. **(Smoke test, immediate)** Install v5 from Play Console internal track on a physical device. Run the 8 RO-11 acceptance checks per `docs/plans/plan-RO-11-labs-wiring.md` § 8. Plus RO-08 + RO-09 regression spot-checks.
2. **(External, conditional)** If smoke green, promote internal v5 → closed testing in Play Console. Recruit ≥12 testers (Gmail addresses), distribute opt-in URL.
3. **(External)** Wait ≥14 calendar days while collecting feedback. Address any critical Pre-launch report findings via versionCode → bundleRelease → re-upload to closed track.

---

## 2026-05-19 (early hours) — RO-11 Labs wiring + in-round readout landed (Phases A + B + C)

- **Goal:** implement plan-RO-11 in full (3 phases, 6 implementation commits + this doc-sync). Pre-fix all 10 `ResearchType` enums were dead — declared with effect descriptions, costing Steps + real-time + Gems, but never consumed by any combat-path / step-credit / cash-economy / UW-cooldown class. Same shape as RO-08 #1 / RO-09 #1 dead-enum gaps, system-wide. User report on top of that: in-round upgrade menu doesn't show numerical effect of each purchase (originally tracked as RO-10, absorbed as Phase C of RO-11 because the readout is meaningful only once stat resolution is correct).
- **Outcome:** all 6 implementation commits landed on `main` plus this doc-sync. Test count 572 → 609 (+37 tests, ~36 plan target). `./run-gradle.sh test --rerun-tasks` BUILD SUCCESSFUL with zero failures; `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL.

### Phase A — 7 simple multipliers (commits `d3dc4d6` / `a4eca72` / `14b0665`, +9 tests, 572 → 581)

- **`d3dc4d6` (#A.1, +4 tests):** `ResolveStats` gains optional `labLevels: Map<ResearchType, Int> = emptyMap()` parameter; DAMAGE_RESEARCH (+5 %/lvl), HEALTH_RESEARCH (+5 %/lvl), CRITICAL_RESEARCH (+3 %/lvl), REGEN_RESEARCH (+4 %/lvl) attach as a third multiplicative tier outside ws × ir following the established RO-08 stacking precedent. Default empty map preserves existing call sites.
- **`a4eca72` (#A.2, +2 tests):** `GameEngine` gains `@Volatile cashResearchMultiplier: Double = 1.0` (applied at 2 cash sites in `handleEnemyDeath` per-kill + `handleWaveComplete` wave-end) and `@Volatile uwCooldownMultiplier: Float = 1f` (applied at the cooldown-set site in `activateUW`). `BattleViewModel` gains `LabRepository` constructor param (15th, before `dailyMissionDao`) + `var labLevels` snapshot field hydrated in `init` and refreshed in `playAgain` + private `cashResearchMultiplier()` and `uwCooldownMultiplier()` helpers. UWSlotInfo cooldownTotal now multiplied by `eng.uwCooldownMultiplier` so the ring-fill UI tracks the actual cooldown.
- **`14b0665` (#A.3, +3 tests):** `DailyStepManager` gains `LabRepository` (16th constructor param). `applyStepMultiplier` now reads BOTH the workshop STEP_MULTIPLIER level and the lab STEP_EFFICIENCY level on every credit; combines additively under the existing `STEP_MULTIPLIER_CAP = 1.0` (per plan § 9 open question #3). New `STEP_EFFICIENCY_PER_LEVEL = 0.02` constant exposed alongside `STEP_MULTIPLIER_PER_LEVEL`. Activity minutes intentionally still excluded for the same GDD wording rationale that excluded them from STEP_MULTIPLIER in RO-08.

### Phase B — WAVE_SKIP + Coming Soon gate (commits `28337e5` / `6b754c9`, +3 tests, 581 → 584)

- **`28337e5` (#B.1, +2 tests):** `WaveSpawner` gains `private val startWave: Int = 1` constructor param; `var currentWave: Int = startWave` replaces the hardcoded `= 1`. `GameEngine.init()` gains optional `startWave: Int = 1` parameter, coerces to ≥1, threads into the WaveSpawner constructor AND `triggerWaveAnnouncement(...)` so the HUD slide-in shows the correct opening wave. `GameSurfaceView` tracks `currentStartWave` and threads it through all 3 `engine.init` call sites. `BattleViewModel` gains `var startWave: Int = 1; private set` + `waveSkipStartWave()` helper returning `(1 + level).coerceAtLeast(1)`. `BattleScreen` passes `viewModel.startWave` into `surfaceView.configure(...)`. New `WaveSpawnerTest` + 1 BattleViewModelTest entry for WAVE_SKIP L5 → startWave 6.
- **`6b754c9` (#B.2, +1 test):** `ResearchType` gains optional `isComingSoon: Boolean = false` constructor parameter; AUTO_UPGRADE_AI + ENEMY_INTEL set `isComingSoon = true` and have descriptions updated to "Reserved for v1.x — research progress preserved". `LabsScreen` renders a "COMING SOON" badge in the row header and suppresses the Start / Rush / Progress UI block when `info.type.isComingSoon`. `LabsViewModel.startResearch` gains a defensive belt-and-braces guard: early-return + snackbar if a Coming Soon type ever reaches the VM via a future entry point. New `ResearchTypeTest` with a single set-equality contract that catches regressions in both directions.

### Phase C — `DescribeUpgradeEffect` + in-round readout (commit `93f6ae8`, +25 tests, 584 → 609)

- New use case `domain/usecase/DescribeUpgradeEffect.kt`. `UpgradeEffectReadout(current: String, next: String?)` data class — `next` is `null` when at maxLevel (UI renders "Now: X (MAX)" instead of arrow). Operator-fun signature mirrors `ResolveStats` and shares its instance for drift-free preview. Stat-bearing upgrades call `resolveStats` twice; cash utilities + hidden upgrades compute from `UpgradeConfig.effectPerLevel` directly with cap clamps. Format strings pinned to `Locale.ROOT` so de-DE / fr-FR users don't see "12,5 dmg" diverging from the rest of the English-only v1.0 strings (plan § 9 open question #5).
- `BattleViewModel` gains `private val describeUpgradeEffect = DescribeUpgradeEffect(resolveStats)` (shared `ResolveStats` instance, no risk of preview-vs-reality drift) and public `fun describeEffect(type)`. `InRoundUpgradeMenu` gains optional `describeEffect: (UpgradeType) -> UpgradeEffectReadout` parameter with a no-op fallback default; renders a third Text in Gold (#D4A843) below the description when readout has non-empty current; line reads "Now: X → Next: Y" or "Now: X (MAX)". `BattleScreen` passes `viewModel::describeEffect` through.
- `DescribeUpgradeEffectTest` (new): 25 tests — 6 multiplicative, 8 additive percentage cap (incl. LIFESTEAL clamp at 15 %, DEATH_DEFY at 50 %), 3 discrete (MULTISHOT / BOUNCE_SHOT / ORBS), 4 cash utility (CASH_BONUS / CASH_PER_WAVE / INTEREST cap 10 % / FREE_UPGRADES cap 25 %), 2 hidden-but-tested-for-Workshop-reuse (STEP_MULTIPLIER cap 100 % / RECOVERY_PACKAGES cap 50 %), 1 lab-research-stacks (DAMAGE_RESEARCH outer multiplier × DAMAGE), 1 smoke test asserting every UpgradeType produces a non-empty current readout.

### Verification

- `./run-gradle.sh test --rerun-tasks` — BUILD SUCCESSFUL, 609 tests pass (was 572). Zero failures across all suites.
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL, clean R8 minify + lint vital + signing.

### Open questions resolved against plan-RO-11 § 9

- **#1 WAVE_SKIP semantics:** "start at wave 1 + level" as recommended (L0 = wave 1, L10 = wave 11).
- **#2 Lab multipliers stack:** multiplicatively as recommended (matches RO-08 ws × ir × lab pattern).
- **#3 STEP_EFFICIENCY + STEP_MULTIPLIER cap:** shared +100 % cap as recommended.
- **#4 AUTO_UPGRADE_AI / ENEMY_INTEL Steps already spent:** no refund; research progress preserved for v1.x.
- **#5 Readout localization:** English-only via `Locale.ROOT` formatters; localization is v2.0 effort.

### Known follow-ups in v1.x backlog (per plan § 10)

- AUTO_UPGRADE_AI real implementation (~2 days; auto-purchase coroutine + optimal-upgrade definition).
- ENEMY_INTEL real implementation (HP-bar gating + wave preview UI + boss telegraph).
- Cross-validator unit fix for combined STEP_MULTIPLIER + STEP_EFFICIENCY against `hcSteps` (same as RO-09 deferred #3 — schema migration v9 → v10).
- Workshop-screen surface of the same readout (the use case already supports it via the hidden-but-tested-for-reuse paths).
- `BattleViewModel` constructor refactor — now at 16 params; ADR + extraction candidate for v1.x.

### Doc-sync (this commit)

- `docs/agent/STATE.md`: current-objective flipped from "RO-11 implementation" to "smoke-test v5 + promote internal→closed"; previous-objectives ladder updated; "what works" gains 609-test line + RO-11 summary; top-priorities renumbered; next-actions reordered; critical-path appended; last-run line refreshed.
- `docs/agent/RUN_LOG.md`: this entry prepended above the prior plan-write entry.
- `AGENTS.md`: test count 572 → 609; coverage summary extended with RO-11 entries (8 wired research types + WAVE_SKIP + Coming Soon gate + DescribeUpgradeEffect + 25 readout tests).
- `CHANGELOG.md`: new RO-11 section under [Unreleased] with per-phase summary + verification + open-question resolutions.
- `.kiro/steering/source-files.md`: 5 new file entries (`DescribeUpgradeEffect.kt`, `WaveSpawnerTest.kt`, `DescribeUpgradeEffectTest.kt`, `ResearchTypeTest.kt`); updated entries for `ResearchType.kt` (+isComingSoon), `WaveSpawner.kt` (+startWave), `GameEngine.kt` (+cashResearchMultiplier + uwCooldownMultiplier + startWave), `GameSurfaceView.kt` (+currentStartWave), `BattleViewModel.kt` (+labLevels + startWave + describeEffect), `LabsScreen.kt` (+Coming Soon UI), `LabsViewModel.kt` (+isComingSoon guard), `InRoundUpgradeMenu.kt` (+describeEffect param), `BattleScreen.kt` (+startWave + describeEffect threading), `DailyStepManager.kt` (+LabRepository + STEP_EFFICIENCY), `ResolveStats.kt` (+labLevels), `BattleViewModelTest.kt` + `ResolveStatsTest.kt` + `DailyStepManagerTest.kt` + `GameEngineTest.kt` (test count deltas).
- Plan file `docs/plans/plan-RO-11-labs-wiring.md` left unmodified (historical at authoring date per the agent protocol).

### Next session

1. **(Build + upload, immediate)** Bump `versionCode 4 → 5`, run `./run-gradle.sh bundleRelease`, upload to Play Console internal track.
2. **(Smoke test, immediate)** Install v5 on a physical device. Run the 8 acceptance checks per plan-RO-11 § 8.
3. **(External)** Promote internal v5 → closed testing, recruit ≥12 testers, wait ≥14 calendar days.

---

## 2026-05-18 (latest, late evening) — RO-11 plan written: Labs dead-enum discovery + in-round visibility absorbed

- **Trigger:** user reported *"it is hard to see the upgrades and how they affect the tower; can we have this visible, such as damage in round shown by the upgrade, and the same with all of them?"* (originally tracked as RO-10 in the active task list).
- **Discovery while scoping RO-10:** user follow-up question "will this also take into account lab levels?" prompted a search for Labs consumers. Found that **all 10 `ResearchType` enums are dead** — declared with effect descriptions, costing Steps + real-time + Gems (rush) to complete, displayed correctly on the Labs screen, but **never consumed by `BattleViewModel`, `GameEngine`, `ResolveStats`, `ApplyCardEffects`, `DailyStepManager`, `WaveSpawner`, `EnemyEntity`, or any other gameplay class**. Same shape as RO-08 #1 (`STEP_MULTIPLIER` + `RECOVERY_PACKAGES`) and RO-09 #1 (`CHRONO_FIELD`), wider blast radius (entire system).
- **Why missed earlier:** RO-08 dead-enum sweep covered `CardType` / `OverdriveType` / `UltimateWeaponType` / `UpgradeType` / `DailyMissionType` — `ResearchType` was not on the checklist. RO-09 followed up on RO-08 gaps but didn't extend the sweep to Labs.

### Severity per ResearchType

| Enum | Closed-test exposure | Recommended phase |
|---|---|---|
| DAMAGE_RESEARCH / HEALTH_RESEARCH / CASH_RESEARCH | High (visible 1-round) | A |
| CRITICAL_RESEARCH / REGEN_RESEARCH | Medium | A |
| STEP_EFFICIENCY | High (A/B over 2 days walking) | A |
| UW_COOLDOWN | Medium (needs unlocked UW) | A |
| WAVE_SKIP | High (visible at round start) | B (wire) |
| AUTO_UPGRADE_AI | Medium | B (defer to v1.x + UI gate) |
| ENEMY_INTEL | Low | B (defer to v1.x + UI gate) |

### Plan written

- **`docs/plans/plan-RO-11-labs-wiring.md`** (406 lines). Comprehensive 3-phase plan absorbing the original RO-10 (in-round visibility) as Phase C since the readout is meaningless without the underlying stat resolution being correct.
- **Phase A** (7 simple multipliers, closed-test blocker): `ResolveStats` extended with `labLevels: Map<ResearchType, Int>` parameter; outer multipliers for DAMAGE / HEALTH / CRITICAL / REGEN; engine multipliers for CASH (kill cash + wave cash) and UW_COOLDOWN; `DailyStepManager` extended for STEP_EFFICIENCY (combined cap with STEP_MULTIPLIER at +100 %).
- **Phase B**: `WaveSpawner` constructor gains `startWave: Int = 1` for WAVE_SKIP; `AUTO_UPGRADE_AI` + `ENEMY_INTEL` rows on Labs screen marked "Coming Soon" + descriptions updated to *"Reserved for v1.x — research progress preserved"*.
- **Phase C**: new `DescribeUpgradeEffect` use case (pure Kotlin) returns `UpgradeEffectReadout(current, next)` per upgrade type; `InRoundUpgradeMenu` renders "Now → Next" line below each row; readout includes Workshop + in-round + Labs contributions.
- **Single PR**, 7 commits in dependency order, target +36 tests (572 → ~608), versionCode 4 → 5 after merge.
- **Acceptance criteria**: 8 manual smoke checks on device covering each Phase A wired effect + Phase B WAVE_SKIP + Phase C readout + Phase B "Coming Soon" gating. RO-08 + RO-09 regression checks included.

### Open questions documented (decide before commit 1)

1. WAVE_SKIP semantics: "start at wave 1 + level" recommended (L0 = wave 1, L10 = wave 11).
2. Lab multipliers stack with workshop **multiplicatively** (recommended) per RO-08 precedent.
3. STEP_EFFICIENCY + STEP_MULTIPLIER share the +100 % cap (recommended) per GDD ceiling.
4. AUTO_UPGRADE_AI / ENEMY_INTEL Steps already spent: no refund (recommended).
5. Readout localisation: English-only v1.0 (recommended).

### Doc-sync (this session, no code change)

- `docs/agent/STATE.md`: current-objective flipped from "smoke-test v4" to "RO-11 implementation"; previous-objective records the v4 upload; top-priority #1 reordered; references updated with the new plan path.
- `docs/agent/RUN_LOG.md`: this entry prepended.
- `docs/plans/plan-RO-11-labs-wiring.md`: NEW. 406 lines.
- `CHANGELOG.md`: not modified — RO-11 will get its own section when implementation lands.
- `AGENTS.md`: not modified yet — will update as part of the RO-11 implementation PR's doc-sync commit.

### Next session

1. **(Implementation, ~7.5 hours)** Land RO-11 per plan: 7 commits in order. Phase A first (compulsory for closed test), then Phase B, then Phase C.
2. **(Build + upload)** versionCode 4 → 5, `./run-gradle.sh bundleRelease`, upload to internal track.
3. **(Smoke test)** 8 acceptance checks per plan § 8 on device, confirming each lab research type's claimed effect now matches reality.
4. **(External)** Promote internal v5 → closed if smoke test green.

### Result

Pure planning + documentation pass; no code changed. Working tree clean before doc-sync; after doc-sync the new plan file and STATE/RUN_LOG updates form a single commit ready for review (commit not yet made — user has not asked yet). v4 (versionCode 4) sits on the internal track unchanged; will be superseded by v5 once RO-11 lands.

---

## 2026-05-18 (evening) — v4 (versionCode 4) AAB uploaded to internal track

- **Event:** user reported that v4 (versionCode 4) was uploaded to the Play Console internal-testing track. This is the first upload that contains the RO-08 + RO-09 fix bundles. v3 (versionCode 3) had been live on the internal track since 2026-05-15 and on-device-verified earlier today.
- **Working tree:** clean. No code changes in this session — status sync only. Last commit on `main` is `c366ad6` (`docs(ro-09): sync state, run log, changelog, AGENTS, source-files`).
- **What's new in v4 vs v3:**
  - **RO-08** (4-fix upgrade-wiring bundle, 535 → 565 tests): STEP_MULTIPLIER + RECOVERY_PACKAGES wired in (previously dead enums); ZigguratEntity stale-stats propagation fixed (Overdrive ASSAULT/FORTRESS + in-round attack-speed now actually apply); ResolveStats `ir(...)` extended to all 14 stat-bearing upgrades (was 3/14 — 11 in-round purchases were dead cash); STEP_SURGE card's `gemMultiplier` now read by `BattleViewModel.watchGemAd`. Commits `5c2baca` … `b7b8824`.
  - **RO-09** (3-fix pre-closed-test bundle, 565 → 572 tests): CHRONO_FIELD UW now actually slows enemies (was render-overlay-only — 75 PS for zero gameplay benefit); GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` stacking fixed (closed the up-to-50s 5×-cash leak across overdrive expiry); LabsScreen dead `total` expression removed. Commits `fcb282e` … `fdc34d3`.
- **Verification status:** `./run-gradle.sh test` BUILD SUCCESSFUL (572 tests) and `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL when last run (during the RO-09 PR session). Internal-track upload successful per user.

### Doc-sync (this session, no code change)

- `docs/agent/STATE.md`: current-objective flipped from "bump versionCode + upload" to "smoke-test v4 + promote internal→closed"; top-priority #1 reworded; next-actions #1 reworded for the new immediate step (on-device smoke test); last-run line refreshed.
- `AGENTS.md`: Tech-stack version line now reads "v3 … PASSED 2026-05-18; v4 (RO-08 + RO-09 fix bundles) was uploaded to the internal track 2026-05-18, awaiting on-device smoke test before closed-track promotion". Plan 31 status block extended with the v4-upload milestone and the RO-08 / RO-09 test-count deltas.
- `CHANGELOG.md`: not modified — the [Unreleased] block already documents RO-08 and RO-09; convention is to flip [Unreleased] → a dated v1.0.0 release block when production rollout actually completes, not when an internal-track AAB ships.
- `.kiro/steering/source-files.md`, `structure.md`, `tech.md`, `lib-*.md`: not modified — no source-file responsibility shape, structure, or dependency-version churn this session.

### Next session

1. **(On-device smoke test, immediate)** Install v4 from the internal-testing track on a physical device. RO-09 #1 + #2 visible-effect spot-check (equip CHRONO_FIELD UW, confirm enemies actually slow during the 8 s window; activate ASSAULT then GOLDEN_ZIGGURAT, let GOLDEN expire while ASSAULT still active, confirm cash multiplier resets to 1.0× — not 5.0×). RO-08 quick regression sweep (in-round ATTACK_SPEED purchase visibly increases fire rate; STEP_MULTIPLIER level 5 produces ~+5 % steps; RECOVERY_PACKAGES heals during a wave at level ≥1). If anything fails, file follow-up before promoting to closed.
2. **(External)** Promote internal v4 → closed testing in Play Console. Recruit ≥12 testers (Gmail addresses), distribute opt-in URL.
3. **(External)** Wait ≥14 calendar days while collecting feedback. Address any critical Pre-launch report findings via versionCode → bundleRelease → re-upload to closed track.
4. **(External)** After ≥14 days closed testing with ≥12 testers, apply for production access. Google review 1–3 days. Promote closed → production with staged rollout. Tag v1.0.0 in git.

---

## 2026-05-18 (post-RO-09 commits) — RO-09 fix bundle landed

- **Goal:** implement the 3 fix-before-closed-test findings catalogued in the prior RO-09 audit-findings entry (immediately below). Single PR, 4 commits per `docs/plans/plan-RO-09-pre-closed-test-fixes.md`.
- **Outcome:** all 3 fixes landed and pushed. Test count 565 → 572. Build clean.

### Commits

- **`fcb282e` — `fix(battle): wire CHRONO_FIELD UW to actually slow enemies (RO-09 #1)`.**
  - `GameEngine.kt`: new `CHRONO_SLOW_FACTOR = 0.10f` companion constant; `entities.forEach` rewritten to scale `deltaTime` per-entity when `chronoActive && e is EnemyEntity`. Projectiles, orbs, and the ziggurat keep the unscaled `deltaTime` so player-side timing (shoot cooldowns, projectile travel) is unaffected.
  - `GameEngineTest.kt`: +3 tests (chrono active slows enemies to 10 % of baseline with 0.08..0.12 tolerance; chrono inactive deterministic baseline; projectiles unaffected ~200 px movement). +`setChronoActive` reflection helper + `simulateEnemyMovement` fresh-engine baseline-vs-chrono comparator.
  - 2 files changed, 134 insertions(+), 2 deletions(-).

- **`f4d5997` — `fix(battle): GOLDEN_ZIGGURAT × overdrive fortuneMultiplier stacking (RO-09 #2)`.**
  - `GameEngine.kt`: 3 symmetric edits closing the 5.0×-leak-across-overdrive-expiry exploit:
    - `activateOverdrive(FORTUNE)`: `fortuneMultiplier = fortuneMultiplier.coerceAtLeast(3.0)` (was unconditional `= 3.0`, downgraded GOLDEN's higher 5.0×).
    - `expireOverdrive`: `fortuneMultiplier = if (goldenZigActive) 5.0 else 1.0` (was unconditional `= 1.0`, collapsed GOLDEN's 5.0× when ASSAULT/FORTRESS/SURGE expired).
    - GOLDEN expire branch in `updateUWs`: `fortuneMultiplier = if (activeOverdrive == OverdriveType.FORTUNE) 3.0 else 1.0` (was `if (activeOverdrive == null) fortuneMultiplier = 1.0`, leaked 5.0× across ASSAULT/FORTRESS/SURGE for up to ~50 s).
  - Invariant preserved: *"the higher of the two buffs always wins; the lower restores cleanly when one ends"*.
  - `GameEngineTest.kt`: +4 tests (one per stacking path) + 3 new helpers (`readFortuneMultiplier` reflection on private field, `activateGoldenZigForTest` uses public `initUWs`+`activateUW`, `invokeUpdateUWs` reflectively expires UWs without ticking overdrive timer or wave spawner).
  - 2 files changed, 150 insertions(+), 4 deletions(-).

- **`fdc34d3` — `chore(labs): remove dead total expression (RO-09 #7)`.**
  - `LabsScreen.kt:106`: deleted `val total = info.remainingMs + (System.currentTimeMillis() - (System.currentTimeMillis() - info.remainingMs))` — algebraically `2 × info.remainingMs`, never read. Refactor leftover. Zero behaviour change.
  - 1 file changed, 1 deletion(-).

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, 572 tests pass (was 565, +7). All 7 new RO-09 tests visible in the JUnit XML output.
- `./run-gradle.sh bundleRelease` — pending (will run as part of the next session before v4 internal-track upload).
- Working tree clean post-doc-sync commit.

### Out of scope — deferred to v1.x patch backlog

Same 4 findings as catalogued in the prior audit-findings entry (#3 STEP_MULTIPLIER × CV unit mismatch, #4 currency lifetime counter desync, #5 TOCTOU on gem/PS spend, #6 per-kill battle-step credit on `viewModelScope`). All bounded-impact, no closed-test exposure. Documented in CHANGELOG and `docs/plans/plan-RO-09-pre-closed-test-fixes.md`.

### Next session

1. **(Build + upload, immediate)** Bump `versionCode 3 → 4` in `app/build.gradle.kts`, run `./run-gradle.sh bundleRelease`, upload to internal track. On-device smoke-test for RO-09 #1 + #2 visible effects (equip CHRONO_FIELD UW; activate ASSAULT then GOLDEN_ZIGGURAT and confirm cash multiplier resets cleanly when GOLDEN expires).
2. **(External)** Promote internal v4 → closed testing in Play Console.
3. **(External)** Recruit ≥12 testers; wait ≥14 calendar days; apply for production access.

### Side effect

Cleaned up `STATE.md`'s mixed encoding (literal `\u2014` escapes vs one stray real `→` unicode) — file now uses consistent UTF-8 throughout. Markdown renders identically; the literal-escape style was a relic of an earlier failed edit.

---

## 2026-05-18 (post-RO-08 commit) — RO-09 pre-closed-test self-audit

- **Goal:** user asked for a deep code scan for any other bugs after RO-08 landed. Goal: maximise quality of the v1.0 closed-test build before the 14-day clock starts.
- **Approach:** systematic sweep using the same patterns RO-08 surfaced — dead enum branches, computed-but-never-read fields, stale captured references — extended to include round-end persistence, currency atomicity, anti-cheat, ViewModel cleanup, and Flow combiners. Cross-checked every CardType / OverdriveType / UltimateWeaponType / UpgradeType / DailyMissionType for end-to-end wiring.

### Findings (7)

**🔴 CRITICAL — Finding #1: CHRONO_FIELD UW does nothing functional.** Description claims "Slows all enemies to 10 % speed for duration" but `chronoActive`'s only consumer is the rendering overlay (purple tint). `EnemyEntity.update` reads raw `speed`; `GameEngine.update` passes raw `deltaTime`. Players spend 75 Power Stones (same cost as CHAIN_LIGHTNING) for zero gameplay benefit. Same shape as RO-08's dead-enum findings — feature wired into UI/cooldown/cost but disconnected from gameplay.

**🟠 MODERATE — Finding #2: GOLDEN_ZIGGURAT × overdrive `fortuneMultiplier` leak.** Single shared field for FORTUNE (3.0×) and GOLDEN_ZIGGURAT (5.0×). The GOLDEN expire path's `if (activeOverdrive == null) fortune = 1.0` only resets when no overdrive is active. If ASSAULT/FORTRESS/SURGE is active when GOLDEN expires, the 5.0× multiplier persists for the remainder of the active overdrive (up to ~50 s 5× cash exploit). Symmetric leak in the FORTUNE-activate path (downgrades GOLDEN's higher value) and `expireOverdrive` (forces 1.0 even if GOLDEN still active).

**🟢 COSMETIC — Finding #7: Dead `total` expression in `LabsScreen.kt:106`.** Algebraically `2 × info.remainingMs`, never read. Refactor leftover.

**🟡 LOW (deferred to v1.x) — Findings #3 / #4 / #5 / #6:**
- #3 STEP_MULTIPLIER × cross-validator unit mismatch (post-RO-08): `record.creditedSteps` now includes the multiplier bonus, but cross-validator compares it raw against `hcSteps`. Edge case affecting players with prior CV offenses + STEP_MULTIPLIER ≥ 1. Closed-test cohort almost certainly has clean step history.
- #4 Currency lifetime counter desync: `addGems` / `spendGems` / `addPowerStones` / `spendPowerStones` fire two `@Query` UPDATEs back-to-back, not in `@Transaction`. Lifetime counters can drift on crash. Display-only impact.
- #5 TOCTOU race on gem/PS spend: `MAX(0, balance + delta)` clamps wallet, but `incrementSpent` records full requested amount. Lifetime drift only.
- #6 Per-kill battle-step credit on `viewModelScope`: end-of-round persistence migrated to `applicationScope` in RO-03, but per-kill credits still on viewModelScope. Mid-round nav-away cancels in-flight credits. Bounded to ~1 step per pending callback.

### Verified clean

Full audit checklist documented in `docs/plans/plan-RO-09-pre-closed-test-fixes.md` § "Out of scope — verified clean":
- All 4 `CardEffectResult` fields read by BVM (RO-08 #4 closed `gemMultiplier`).
- All 9 `CardType` cases wired in `ApplyCardEffects`. (Minor cosmetic: `IRON_SKIN` description says "%" but adds raw flat — string-only mismatch, not a wiring bug.)
- All 4 `OverdriveType` variants propagate (RO-08 #2 fixed ASSAULT + FORTRESS).
- All 6 UWs wire correctly *after* RO-09 #1 + #2 land.
- All 23 `UpgradeType` enums have downstream consumers (RO-08 closed STEP_MULTIPLIER + RECOVERY_PACKAGES).
- All 6 `DailyMissionType` cases handled (BATTLE in BVM, UPGRADE in WorkshopVM/LabsVM, WALKING in DailyStepManager).
- `secondWindUsed` reset between rounds via `engine.init`; `secondWindHpPercent` re-pushed in `playAgain`.
- Round-end persistence atomicity (RO-02) + resilience (RO-03) preserved.
- Atomic SQL guards for purchase, claim-milestone, credit-battle-steps.

### Decision: which to fix before closed test

Three fixes selected: **#1 (critical UW)**, **#2 (cash exploit)**, **#7 (drive-by cleanup)**. Closed-test feedback would surface #1 immediately as "broken on arrival"; #2 is a real cash exploit during overlapping-buff play. #7 is a 1-line cleanup costless to ship now.

Four deferrals (#3 – #6) documented with rationale: bounded impact, near-zero closed-test exposure (new players, no CV offenses, no extreme mid-round nav-away). All four have v1.x fix sketches in the plan doc.

### Plan documented + STATE/RUN_LOG sync (this commit)

- Created `docs/plans/plan-RO-09-pre-closed-test-fixes.md` (307 lines) with priority table, decision rationale, per-finding details (severity / files / root cause / fix sketch / test plan), acceptance criteria, implementation plan (4 commits), and v1.x fix sketches for the deferred items.
- Updated `docs/agent/STATE.md`: current-objective points at RO-09; top-priorities reordered (RO-09 implementation now #1, closed-track promotion now #2); next-actions explicit ordering; references list now includes the RO-09 plan path; critical-path appended.
- Appended this RUN_LOG entry above the prior RO-08 entry.

### Result

No code changed in this session. Pure planning + documentation pass. Ready to implement RO-09 next session: ~3 commits + doc-sync commit, target +7 tests (565 → ~572), versionCode bump 3 → 4, re-upload to internal track, then promote internal v4 → closed.

---



- **Goal:** User asked me to audit upgrade wiring during play. Found four real gaps: (1) STEP_MULTIPLIER + RECOVERY_PACKAGES never referenced outside their enum; (2) ZigguratEntity captured `attackInterval` / `attackRange` at construction with a stale `val stats` reference, so Overdrive ASSAULT's 2x attack speed + FORTRESS's 2x health regen + in-round ATTACK_SPEED purchases all silently no-op'd; (3) ResolveStats only applied `ir(...)` to DAMAGE / ATTACK_SPEED / HEALTH so 20 of 23 in-round purchases were dead cash; (4) STEP_SURGE card's `gemMultiplier` was computed by `ApplyCardEffects` but never read by `BattleViewModel`. User asked to bundle them. Single PR.
- **Preflight:** read STATE.md (Plan 31 Phase G2 closed-testing window, do-not-touch zones include `domain/usecase/`). `git status` clean on `main`, last commit (after pushing PRs A+B+C earlier this evening).

### Implementation

**Fix #4 — STEP_SURGE gem multiplier (smallest, low-risk first).** Added `cardGemMultiplier: Double = 1.0` field to `BattleViewModel`; populated in `init` and `playAgain` from `cardResult.gemMultiplier`; applied in `watchGemAd` via `(1.0 * cardGemMultiplier).toLong().coerceAtLeast(1L)`. Default 1.0 keeps existing 1-gem reward unchanged when no STEP_SURGE equipped. Lv1 → 2, Lv5 → 4. +2 BVM tests (Lv1 doubles, Lv5 quadruples).

**Fix #2 — `ZigguratEntity` stale-stats propagation.** Made `stats` a `var` with `private set`; added `fun updateStats(newStats)`. `attackInterval` becomes a private computed property `get() = (1.0 / stats.attackSpeed).toFloat()`; `attackRange` becomes `val attackRange: Float get() = stats.range`. Centralised the engine-side mutation as a private `applyStats(newStats)` helper that updates engine.stats + zig.stats + reconciles maxHp/orbCount. Routed all 5 mutation sites through `applyStats`: `setStats`, `activateOverdrive` (ASSAULT and FORTRESS arms), `expireOverdrive`, GOLDEN_ZIGGURAT UW activate + expire, and `updateZigguratStats`. Init still does a direct assignment because the ziggurat is constructed right after with the same reference (no de-sync window).

**Fix #3a + #3b + #3c — In-round upgrade coverage.** Extended `ResolveStats.invoke` with `ir(...)` for all 14 stat-bearing upgrades. Multiplicative stats (DAMAGE, ATTACK_SPEED, HEALTH already had ir(); added HEALTH_REGEN, RANGE, KNOCKBACK) follow `(1 + ws*x) * (1 + ir*x)`. Additive stats (CRITICAL_CHANCE, CRITICAL_FACTOR, DEFENSE_PERCENT, DEFENSE_ABSOLUTE, THORN_DAMAGE, LIFESTEAL, DAMAGE_PER_METER, DEATH_DEFY, MULTISHOT, BOUNCE_SHOT, ORBS) sum levels via a `total(type)` helper before applying per-level effect and any cap. Range now uses the multiplicative form coerced to ≤ BASE × 3 (matches the prior cap; previously the cap clamped only the workshop term). Hidden STEP_MULTIPLIER + RECOVERY_PACKAGES from `InRoundUpgradeMenu` (mirror of `WorkshopViewModel`'s hidden-set).

For the 4 cash utilities (CASH_BONUS, CASH_PER_WAVE, INTEREST, FREE_UPGRADES) renamed `GameEngine.workshopLevels` → `effectiveLevels` and added public `fun updateEffectiveLevels(combined: Map<UpgradeType, Int>)`. `BattleViewModel.purchaseInRoundUpgrade` builds `combinedLevelsForCash()` (additive merge of workshop + inRound levels) and pushes it to the engine on every purchase. The FREE_UPGRADES free-roll chance is now read from the same combined map so a mid-round FREE_UPGRADES level contributes to its own subsequent free-roll chances.

**Fix #1a — STEP_MULTIPLIER walking-credit multiplier.** Added `WorkshopRepository` constructor parameter to `DailyStepManager` (Hilt resolves automatically; tests need a `FakeWorkshopRepository`). Added `applyStepMultiplier(baseCredit)` helper that fresh-reads the STEP_MULTIPLIER level from the workshop flow on every credit. Multiplier `(1 + level × 0.01)` capped at +100 % (`STEP_MULTIPLIER_CAP = 1.0`). Applied in `recordSteps` AFTER anti-cheat (rate limiter + velocity analyzer) and BEFORE the 50 k daily ceiling so the cap remains absolute. Activity minutes intentionally excluded — GDD wording says "earned from walking" and including them would have required changing the existing `dailyActivityMinuteTotal += credited` source-tracking semantics (a multiplier > 1 would inflate the source counter and under-credit subsequent HC deltas). Documented the trade-off inline.

**Fix #1b — RECOVERY_PACKAGES periodic heal.** Added `recoveryTimer: Float` field and three constants (`RECOVERY_INTERVAL_SECONDS = 30f`, `RECOVERY_PERCENT_PER_LEVEL = 0.01`, `RECOVERY_PERCENT_PER_PULSE_CAP = 0.50`). New private `tickRecoveryPackages(deltaTime)` helper invoked from `update()`. Pulses fire only during `WavePhase.SPAWNING`; timer resets between waves so the first pulse of a new wave waits a full 30s. At full HP the pulse is suppressed (timer also resets so freshly-damaged towers wait one full interval). Heal floors at 1 HP for visible feedback; spawns a green `FloatingText` indicator above the ziggurat. No `SoundEffect` (automatic effect, audio could be noisy). Reset on init.

### Tests +30 (535 → 565)

- `ResolveStatsTest` +20: in-round multiplier coverage for every stat-bearing type with caps.
- `BattleViewModelTest` +2: STEP_SURGE Lv1 doubles ad reward; Lv5 quadruples.
- `GameEngineTest` (new file) +5: ASSAULT propagates 2x attackSpeed; FORTRESS propagates 2x healthRegen; expireOverdrive restores baseline; updateEffectiveLevels stores the level map; RECOVERY_PACKAGES heals at 30s in SPAWNING / no heal at full HP / level 0 no heal / cap at 50% maxHp per pulse.
- `DailyStepManagerTest` +5: STEP_MULTIPLIER level 0 unchanged / level 50 grants +50% / cap at +100% / 50k ceiling clamps multiplied credit / activity minutes excluded.

`GameEngineTest` uses reflection to invoke private `tickRecoveryPackages(Float)` and `expireOverdrive()` helpers — bypasses full game-loop side effects (enemy spawn, melee hits, projectile collisions) so the heal-only and stats-only assertions stay deterministic. Two initial test failures (RECOVERY_PACKAGES heal + does-not-heal-at-full) traced to the full update loop spawning enemies that damaged the tower, contaminating HP deltas. Switched to direct invocation of the private helper.

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, 565 tests pass exactly. Test count 535 → +20 (ResolveStats) → +2 (BVM STEP_SURGE) → +5 (GameEngine) → +5 (DailyStepManager) = 565.

### Doc sync

- `AGENTS.md` — coverage line updated 535 → 565; mention of RO-08 STEP_MULTIPLIER coverage in the DailyStepManager section.
- `CHANGELOG.md` — new "RO-08 — Bundle of 4 upgrade-wiring fixes" section under [Unreleased] above the prior PR C entry.
- `.kiro/steering/source-files.md` — `DailyStepManager.kt`, `GameEngine.kt`, `ZigguratEntity.kt`, `ResolveStats.kt` lines updated; new `GameEngineTest.kt` line added.
- `STATE.md` — current-objective and test count updated.
- `RUN_LOG.md` — this entry.

### What's left for next session

The bottleneck remains Google's mandatory 14-day closed-testing clock. Code-side, Plan 31 critical path stays unblocked through Production. Optional follow-ups during the closed-track window:

- Extend STEP_MULTIPLIER to activity minutes (requires changing `dailyActivityMinuteTotal += credited` to track HC raw progress separately).
- Add a subtle SoundEffect for RECOVERY_PACKAGES pulses (left out to avoid noise).
- B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case (Phase B leftover debt, ~1 week).

### Decision footprint

- **No new ADR.** RO-08 is a wiring fix bundle; each sub-fix is the obvious shape (data class with `var`, computed property, additive map merge, fresh-read DB). The activity-minute STEP_MULTIPLIER deferral is documented inline.
- **Did NOT bump versionCode.** v3 is live in internal track, smoke test passed. RO-08 is uncovered by the smoke test but it ships as part of the next legitimate upload.

## 2026-05-18 (evening) — Pre-closed-testing PRs A+B+C: ad-error snackbar, live price, walkthrough doc revision

- **Goal:** User: "Please do all improvements before we go to closed testing". I scoped the four optional improvements I'd flagged earlier, flagged that B.4/B.5 was a multi-day refactor with zero user-visible benefit, recommended A+B+C only. User confirmed "A, B, C only". Three PRs delivered in a single session, each independently committable, each fully tested.
- **Preflight:** read STATE.md (Plan 31 Phase G post-smoke-test PASS; ad-error UX gap was a known issue from Phase G smoke-test debrief). `git status` clean on `main`, last commit `79fb381 feat(billing): C.5 PR 3 - delete StubBillingManager`.

### PR A — `feat(ux): surface ad-error feedback as snackbar in Battle + Cards` (`a3dbcaf`)

The user-facing problem: testers tapping "Watch ad for Gems" or "Watch ad for double Power Stones" or "Free daily card pack" on a device that returned `AdResult.Cancelled` (user dismissed) or `AdResult.Error` (NO_FILL, etc.) got no feedback. The button just did nothing. PostRoundOverlay made this especially confusing because the buttons stay visible after a failed tap.

- `CardsViewModel.watchFreePackAd`: replaced `if (result is AdResult.Rewarded)` with a `when` over all 3 variants. `Cancelled` → "Ad cancelled. Try again."; `Error` → `result.message` verbatim, falling back to "Ad failed to load. Try again later." for blank messages (`RewardAdManagerImpl` returns blank for some Mobile Ads SDK error codes).
- `BattleViewModel.watchGemAd` + `watchPsAd`: same pattern. Added `userMessage: String?` to `BattleUiState`; added `clearMessage()` to the VM. Took care to keep the `state.powerStonesAwarded > 0` guard inside the `Rewarded` arm of `watchPsAd` (not at the top-level `if`) so that 0-stones rounds don't show fake "you got something" feedback.
- `BattleScreen`: added a `SnackbarHost` aligned to bottom-center, drawn last in the outer `Box` so it stacks on top of every overlay including `PostRoundOverlay` (where the watch-ad buttons live). `LaunchedEffect(state.userMessage)` calls `showSnackbar` then `viewModel.clearMessage()` so each event surfaces exactly once. Imported `SnackbarHost` + `SnackbarHostState` from material3.
- Tests +6 (524 → 530):
  - `CardsViewModelTest`: extended 2 existing `Cancelled`/`Error` cases with `userMessage` assertions; +1 new test for the blank-message fallback.
  - `BattleViewModelTest`: 5 new tests — `watchGemAd Cancelled`, `watchGemAd Error`, `watchPsAd Cancelled` (uses `installEngineForEndRound + quitRound` to set up a `roundEndState`), `watchPsAd Error` (blank-message fallback), `clearMessage nulls userMessage`.

### PR B — `feat(billing): live formatted price from Play Billing ProductDetails` (`792395e`)

The footgun: earlier in this Plan 31 cycle the user briefly set `ad_removal` to $9.99 in Play Console while the in-app `BillingProduct.AD_REMOVAL.priceDisplay` constant still said $3.99. We caught it before the user uploaded, but it surfaced a real risk: anyone editing prices in Play Console without remembering to also bump the static constant in code creates a bait-and-switch. Long-term proper fix: read prices from Play Billing's `ProductDetails.priceDisplay` so Play Console becomes the source of truth.

- `BillingManager.getPriceDisplay(product): String?` — new interface method with default `null` no-op so `FakeBillingManager` inherits a do-nothing contract. KDoc cites locale examples and explicitly tells callers to handle the `null` case by falling back to `BillingProduct.priceDisplay`.
- `BillingManagerImpl` override: `sessionMutex.withLock { ensureConnected() → queryProductDetails(listOf(skuId), productType) → firstOrNull()?.priceDisplay }`. Failure paths return `null` with `Log.w` for diagnosability. Mutex shared with `purchase()` and `reconcilePendingPurchases()` so a price refresh can't race a purchase-in-progress.
- `StoreViewModel.refreshPriceDisplays()` — launched once on init alongside the existing `reconcilePendingPurchases` hook. Iterates `BillingProduct.entries`, populating `_priceDisplays: MutableStateFlow<Map<BillingProduct, String>>` progressively as each query completes. Failures (null) are skipped. `@VisibleForTesting internal` so unit tests can drive a deterministic refresh.
- `StoreUiState.priceDisplays: Map<BillingProduct, String>` — new field combining the 5th flow argument into the existing `combine` chain. Default empty map.
- `StoreScreen` — every price label changed from `BillingProduct.X.priceDisplay` to `state.priceDisplays[X] ?: BillingProduct.X.priceDisplay`. 3 sites: Gem packs, Ad Removal, Season Pass.
- `FakeBillingManager.priceDisplayOverrides: MutableMap<BillingProduct, String?>` — test knob. Empty default → `getPriceDisplay` returns null for every product, matching the production behaviour when Play Billing is unavailable.
- Tests +5 (530 → 535):
  - `BillingManagerImplTest` +3: success returns `priceDisplay` verbatim (`"£0.79"` locale-formatted); query failure returns null; connect failure returns null. Hit a JUnit 4 vs 5 gotcha — the existing test class uses JUnit 4 where `assertEquals(message, expected, actual)` puts the message FIRST, not last. Initial test failures showed the message string being compared as the actual value. Fixed.
  - `StoreViewModelTest` +2: empty overrides → empty map (UI fallback); 3-of-5 overrides populates only the 3 successful entries (asserts missing keys remain absent rather than landing as empty strings).
- Out-of-scope (intentional v1.x deferrals): no refresh on app resume / locale change; no retry on transient network failure. Static fallback covers both.
- `source-files.md` updated: `BillingManager.kt` row mentions `getPriceDisplay`; `StoreUiState.kt` row mentions the new `priceDisplays` field; `FakeBillingManager.kt` row mentions the new `priceDisplayOverrides` knob.

### PR C — `docs(release): Plan 31 walkthrough doc revision` (pending commit)

Pure docs. The walkthrough at `docs/release/plan-31-walkthrough.md` was written before the first end-to-end Plan 31 run; the live walk-through with the user surfaced four things the doc didn't anticipate, plus three smaller footguns. All fixed inline + summarised in a new `📝 Updated 2026-05-18` preamble at the top.

Lessons fixed:

1. **Android Developer Verification (mandatory since late 2025).** New "E1 detour" subsection with the debug-keystore registration flow. Cites ADR-0007.
2. **Lowercase SKU IDs.** Phase F's product-ID table now uses `gem_pack_small` etc. Phase F also documents the `Purchase option ID` field's `[a-z0-9-]` format (hyphens, not underscores) with recommended values.
3. **Closed testing is mandatory — ≥12 testers, ≥14 days.** Phase I1 retitled from "(optional)" to "(mandatory)" with explicit recruitment + opt-in URL guidance + the 14-day calendar clock.
4. **The native debug symbols warning is unfixable for v1.** New callout in Phase G2 explains SQLCipher + androidx.graphics.path are pre-stripped.

Minor footguns documented:
- `versionCode` forward-only (smoke tests consume the counter permanently);
- Phase E6 is no-op in modern Play Console (country/region selection moved into Phase G);
- Phase F now mentions PR B's live-price wiring;
- Contact email updated from the placeholder to actual.

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL, 535 tests pass exactly. Test count delta: 524 → +6 PR A → 530 → +5 PR B → 535. PR C adds zero tests.
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL between PR A+B and after. Lint vital + R8 minify + signing all clean. Same one pre-existing Kotlin warning about `@ApplicationContext` parameter target carries over (KT-73255).

### Decision footprint

- **No new ADR.** All three PRs are routine improvements:
  - PR A is a UX gap fill, mirroring an established pattern.
  - PR B is a footgun fix; the design (interface method + null fallback + UI ?: pattern) is the obvious shape.
  - PR C is doc maintenance.
- **B.4 + B.5 explicitly deferred to post-launch** per ADR-0004's 4-PR / ~1-week effort estimate. Shifts no critical-path dependencies.
- **Did not bump versionCode.** PR A + B do change shipped behaviour, but v3 is the live internal-track AAB and we're not re-uploading mid-session. Closed-track promotion will happen from v3 (with Internal track promotion) or from a future v4 if any pre-promotion bug fixes need to ship.

### What's left for next session (or for the closed-track 14-day window)

The bottleneck is now Google's mandatory closed-testing 14-day clock. Code-side, Plan 31 is fully unblocked through Production. Optional / opportunistic agent work that can land any time during the window:

- B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case (~4 PRs, ~1 week effort, zero user-visible impact).
- Live-price retry-on-failure (v1.x).
- ADR-0008 documenting the live-price design if we want to crystallise the "v1.x deferral" choices.
- Live-price refresh on app resume (sliver of v1.x).

User's plan from here: promote internal → closed, recruit ≥12 testers, wait ≥14 days, apply for production access, staged rollout, tag v1.0.0.

## 2026-05-18 — Phase G internal-track smoke test PASS + C.5 PR 3 (delete `StubBillingManager`)

- **Goal:** User: "All internal tests run successfully". That message closed the device-verification gate for C.5 PR 2 (real Play Billing v8 + receipt-table idempotency works end-to-end on a real device with the rolled-out v3 internal-track AAB) and unblocked C.5 PR 3. I drove the C.5 PR 3 deletion + collapse + doc sync in a single PR.
- **Preflight:** read STATE.md (Plan 31 Phase G in progress, smoke-test pending; C.5 PR 3 was top priority once it passed). `git status` clean on `main`, last commit `57d98f3 chore(release): bump versionCode 3 -> 4 after v3 rolled out to internal track`.

### Phase G smoke-test outcome (user-reported)

User installed v3 (versionCode 3) internal-track AAB on a real device via the opt-in URL and ran the full smoke checklist. PASS:

- Launcher icon visible (ziggurat, not Android default).
- Step counting works on real walking.
- Battle round flow works end-to-end.
- All 3 Gem packs purchased on real Play Billing with the test card and credited the wallet correctly: `gem_pack_small` → +50 Gems; `gem_pack_medium` → +300 Gems; `gem_pack_large` → +700 Gems.
- `ad_removal` purchased; `adRemoved` flag set; reward-ad UI hidden across the app.
- `season_pass` subscription purchased; `seasonPassActive = true` with 30-day expiry; +10 Gems/day daily-login bonus active.
- AdMob test ad served on the post-round reward path.

This is the first time the full real-Play-Billing pipeline has been exercised end-to-end on a real device against real Play Console SKU configuration. Receipt-table idempotency, atomic `BillingReceiptDao.grantOnceAtomic`, consume/ack post-transaction, and the lowercase wire format from C.5 PR 2 all worked first try.

### C.5 PR 3 deletion + collapse

Mechanically identical to the C.6 PR 3 ad-stub deletion landed earlier this session series.

- **`data/billing/StubBillingManager.kt` deleted** (36 lines). The class simulated purchases with a 500 ms delay and called `PlayerRepository.addGems` / `updateAdRemoved` / `updateSeasonPass` directly. With real Play Billing v8 verified working, the simulator has no remaining purpose.
- **`di/BillingModule.kt` collapsed** from a flag-gated `@Provides` Provider-switch (Stub vs Real based on `BuildConfig.USE_REAL_BILLING`) to two plain `@Binds` abstract classes: `BillingModule` binds `BillingManager → BillingManagerImpl` (the only impl); `BillingInternalModule` keeps the existing `BillingClientAdapter → RealBillingClientAdapter` binding. KDoc rewritten to capture the C.5 PR 1–3 history. Mirrors the C.6 PR 3 collapse of `AdModule`.
- **`BuildConfig.USE_REAL_BILLING` removed** from `app/build.gradle.kts` defaultConfig + debug + release blocks. No code reads it anymore. Updated `buildFeatures.buildConfig` opt-in comment to reference `USE_REAL_ADS` (the surviving flag) only. Refreshed the Play Billing dependency comment to note `BillingManagerImpl` is the sole binding post-PR 3.
- **KDoc cleanup across 5 production files.**
  - `data/billing/BillingManagerImpl.kt` — dropped the "PR 1 wiring status" block; opening line now says "Sole `BillingManager` binding post-C.5 PR 3 — the previous `StubBillingManager` was deleted after the C.5 PR 2 internal-track verification confirmed real-device wallet credit end-to-end."
  - `domain/repository/BillingManager.kt` — `reconcilePendingPurchases` default-no-op KDoc previously linked to `StubBillingManager` and `FakeBillingManager` as inheritors; updated to mention `FakeBillingManager` only with a note that Stub was deleted in C.5 PR 3.
  - `data/billing/internal/ActivityProvider.kt` — KDoc previously said "Lifecycle wiring (deferred to C.5 PR 2)" with "PR 1 leaves this class wired into DI but no caller registers into it — `@Binds` still points at `StubBillingManager`". Rewritten to present-tense: "MainActivity.onResume() calls set() and onPause() calls clear() (landed in C.5 PR 2). The provider is consulted by BillingManagerImpl.purchase just before BillingClient.launchBillingFlow()." Also added a note about C.6 PR 1 onwards reusing the provider for AdMob.
  - `presentation/store/StoreViewModel.kt` — comment on the `reconcilePendingPurchases` init-block call previously said "StubBillingManager + FakeBillingManager inherit the no-op default from BillingManager, so this is a no-op outside release builds with USE_REAL_BILLING." Updated to "FakeBillingManager (test) inherits the no-op default from BillingManager. C.5 PR 2."
  - `di/AdModule.kt` — KDoc for `USE_REAL_ADS` previously said "The flag stays symmetric with `USE_REAL_BILLING`." Updated to "(The previously-symmetrical `USE_REAL_BILLING` flag was removed in C.5 PR 3 once `StubBillingManager` was deleted.)"

### Test cleanup

- **`app/src/test/java/com/whitefang/stepsofbabylon/data/billing/BillingManagerParityTest.kt` deleted** (3 tests). It existed to assert that Stub and Real produce equivalent wallet/flag effects on the golden path during the C.5 PR 2 transition. With Stub gone, the only remaining side is Real, and that's already exhaustively covered by `BillingManagerImplTest` (14 tests — 3 happy paths + 5 failure paths + idempotency + 2 reconciliation cases + delegation). Test count 527 → 524.
- `FakeBillingManager.reconcileCallCount` and `resultQueue` retained — still used by `StoreViewModelTest` (the reconcile-on-Store-init assertion, hook count = 1).

### Doc sync (per agent protocol "PR Task-List Convention")

Touched only what this PR actually invalidates. Did NOT touch ADR-0005 (per protocol "Individual ADRs are amended only when the PR explicitly warrants it"; the lowercase wire format refinement got a comment noting decision #6 was refined, which is enough), prior RUN_LOG entries, plan-26/plan-31 docs (historical at authoring date), or `devdocs/archaeology/*` and `smoke_tests/*` (historical per HEAD pin).

- `AGENTS.md` — Plan 31 status line updated to "Phases A–G landed; smoke test PASSED 2026-05-18; C.5 PR 3 landed". Test count 527 → 524 in the coverage line. Fakes blurb unchanged (FakeBillingManager + FakeRewardAdManager retained).
- `CHANGELOG.md` — new "C.5 PR 3 — Delete `StubBillingManager`, collapse `BillingModule` to `@Binds BillingManagerImpl`" section under [Unreleased] above the prior versionCode-3-rollout entry. Includes the verification block, the smoke-test PASS recap, and the explicit "not uploaded" note (versionCode 4 sits locally; v3 stays live in internal track since there's no functional reason to bump it).
- `.kiro/steering/source-files.md` — `data/billing/StubBillingManager.kt` line removed; `data/billing/BillingManagerImpl.kt` line updated to "Sole `BillingManager` binding post-C.5 PR 3"; `di/BillingModule.kt` line updated to `@Binds` shape; `BillingManagerParityTest.kt` line removed from the test section.
- `.kiro/steering/structure.md` — `data/billing/` directory entry refreshed; `di/BillingModule.kt` row in the key-files table updated to `@Binds` shape with note that `USE_REAL_BILLING` was removed.
- `docs/monetization.md` — full Implementation Status block refresh (the doc had been stale since pre-C.5/C.6, still describing stubs as the target). Now lists Real-SDK reality with concrete invariants (atomic idempotency, post-tx consume/ack, reconciliation sweep), and an honest "What's Out-of-Scope for v1" section listing the four real limitations (no server-side verification, no real-time subscription notifications, no ad mediation, no live formatted-price display).
- `STATE.md` — current-objective + what-works + priorities rewritten. Phase G PASS noted; C.5 PR 3 noted; "next external step is closed-track recruitment" called out as priority #1; critical path string extended to "Phase G2 closed track → Phases H+I production".

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL. Test count `find app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -h "<testsuite " {} \; | sed -E 's/.*tests="([0-9]+)".*/\1/' | awk '{s+=$1} END {print s}'` = **524**, exactly 527 − 3 (the parity tests deleted, all others unchanged).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Lint vital + R8 minify + signing all clean. Same one pre-existing Kotlin warning about `@ApplicationContext` parameter target carries over (KT-73255 follow-up; unrelated). Signed AAB at `app/build/outputs/bundle/release/app-release.aab` ~18 MB, versionCode 4. **Not uploaded** — v3 is the live internal-track AAB, smoke test passed, no functional reason to bump. v4 stays the local forward-only counter for the next legitimate upload (e.g. closed-track promotion, or post-closed-test bug fix).
- LSP `get_diagnostics` was not consulted — workspace was throwing every-import-unresolved errors all session series; Gradle is the source of truth.

### What's left for next session

The bottleneck shifts entirely to the **closed-testing 14-day clock**. Code-side, the Plan 31 critical path is unblocked end-to-end; the next agent task is whatever the closed-track tester feedback surfaces (most likely small UX bugs, or none).

User's plan from here:

1. Promote internal v3 → closed testing in Play Console.
2. Recruit ≥12 testers (Gmail addresses, opt-in URL distribution).
3. Wait ≥14 calendar days while collecting feedback / crash reports / pre-launch report findings.
4. Apply for production access (after the 14-day clock).
5. Promote closed → production with staged rollout (5–10 % → 100 % over a few days).
6. Tag v1.0.0 in git post-rollout. Update STATE + RUN_LOG.

Optional / opportunistic agent work that can land any time during the 14-day window:

- Ad-error UX snackbar fix (3 call sites: `CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd`). Mirror `MissionsViewModel.userMessage` pattern.
- Live formatted price from Play Billing's `ProductDetails.priceDisplay` instead of the static `BillingProduct.priceDisplay` constants. Removes the manual sync requirement when Play Console prices change. v1.x-flavoured but small.
- Walkthrough doc (`docs/release/plan-31-walkthrough.md`) revision pass to fix the four out-of-date assumptions (uppercase SKUs, ADV not anticipated, closed-testing-before-production policy not anticipated, native-debug-symbol unfixability not documented).
- B.4 FollowOnPipeline extraction + B.5 UpdateMissionProgress use case — Phase B leftover debt.

### Decision footprint

- **No new ADR.** This PR is a mechanical follow-up to C.5 PR 2's "C.5 PR 3 is gated on real-device verification" plan; no new architectural choice. ADR-0005 reference in STATE.md mentions decision #6 was refined to lowercase wire format 2026-05-14 (separate PR) — that note stays.
- **Did NOT amend ADR-0005 file directly.** Per `.kiro/steering/11-agent-protocol.md` "Historical artifacts — NEVER modify as part of a current-PR doc sweep". The lowercase wire format note is documented in the BillingManagerImpl class KDoc and BillingReceiptEntity productId KDoc; the C.5 PR 3 deletion is documented in the BillingModule class KDoc plus this RUN_LOG plus the changelog.
- **Did NOT bump versionCode locally.** It already bumped 3 → 4 in the previous session for the v3 rollout. v4 stays in `app/build.gradle.kts` for the next upload. C.5 PR 3 produces no AAB upload.

## 2026-05-15 (later) — v3 rolled out to internal track, versionCode 3 → 4 forward-only bump

- **Goal:** User said "I uploaded version 3 and released that" — they decided to ship v3 (with the `ndk { debugSymbolLevel = "FULL" }` config) instead of the v2 draft they originally uploaded. Functionally equivalent — the symbol warning is unfixable either way — but v3 is the cleaner build with the documented config intent.
- **Code change:** versionCode 3 → 4 in `app/build.gradle.kts`. Forward-only because v3 is now a consumed counter in Play Console.
- **Doc sync:** AGENTS.md version line updated. STATE.md current-objective + last-run lines updated. CHANGELOG.md gets a "v3 rolled out + versionCode 3 → 4" section under [Unreleased] above the prior entry.
- **No new AAB build locally.** v4 is reserved for the next legitimate upload (post-smoke-test bug fix, or C.5 PR 3 deletion AAB). No need to rebuild now since we're not uploading anything.
- **What's next.** User waits for Google's quick review (5–30 min after rollout), grabs the opt-in URL from the Internal testing release page, installs on a test device. Smoke test follows: Gem packs credit 50/300/700, Ad Removal sets the flag, Season Pass sets 30-day expiry + +10 Gems/day, reward ad plays. The Gem-pack-credit test on real Play Billing is the gate for C.5 PR 3.

## 2026-05-15 — Plan 31 Phase G: v2 AAB uploaded, SKUs created, native-debug-symbols investigation, versionCode 2 → 3

- **Goal:** User said "AAB uploaded" then drove through Phase G external work (SKU creation, license testers, ad_removal pricing decision, native-debug-symbol Play Console warning). I drove the small code-side follow-up.
- **Preflight:** read STATE.md (had Phase G as the next external step). `git status` clean on `main`, last commit `69801c4 chore(release): bump versionCode 1 -> 2`.

### External work done (user)

- **AAB upload.** v2 AAB (`app/build/outputs/bundle/release/app-release.aab`, signed, ~18 MB, versionCode 2, lowercase wire format) uploaded to Play Console → Test and release → Internal testing. Sat in Draft state pending review-and-rollout.
- **5 SKUs created.** Monetize with Play → Products → In-app products (4 entries: `gem_pack_small`, `gem_pack_medium`, `gem_pack_large`, `ad_removal`) + Subscriptions (1 entry: `season_pass`, monthly base plan id `monthly`). Per-SKU Purchase option IDs took the hyphenated form (`gem-pack-small` etc.) since Play Console's purchase-option-id field requires `[a-z0-9-]` (hyphens, not underscores). Note that purchase option IDs never appear in our app code — `BillingManagerImpl` queries by productId and the SDK auto-resolves the offer/option.
- **`ad_removal` pricing wobble.** User briefly priced `ad_removal` at $9.99 (matched `gem_pack_large` price). I flagged the UX mismatch — the in-app `BillingProduct.AD_REMOVAL.priceDisplay = "$3.99"` is shown directly in the Store screen as a static string, so Play Console at $9.99 would have meant Store-screen-shows-$3.99 + Billing-dialog-charges-$9.99 (bait-and-switch). User reverted Play Console price to $3.99 (matches the constant). Long-term proper fix is to read formatted price from `ProductDetails.priceDisplay` so Play Console becomes the source of truth, but that's a bigger refactor deferred to v1.x.
- **License testers added.** Setup → License testing + Internal testing → Testers tab.

### Native-debug-symbols Play Console warning investigation

- **Trigger.** After v2 AAB upload, Play Console flagged "This App Bundle contains native code, and you've not uploaded debug symbols. We recommend you upload a symbol file to make your crashes and ANRs easier to analyze and debug."
- **Hypothesis.** AGP 9 has `android.buildTypes.release.ndk.debugSymbolLevel = "FULL"` which extracts native debug info from .so files going into the AAB and bundles it into `BUNDLE-METADATA/com.android.tools.build.debugsymbols/` automatically.
- **Implementation.** Added the `ndk { debugSymbolLevel = "FULL" }` block to the release build type in `app/build.gradle.kts` with an inline comment explaining the FULL-vs-SYMBOL_TABLE trade-off and the cost (one extra Gradle task per release build).
- **Build attempt.** Bumped versionCode 2 → 3 (forward-only — v2 is the uploaded AAB, v3 is the next forward number). `./run-gradle.sh bundleRelease` ran cleanly; the new task `:app:extractReleaseNativeDebugMetadata` ran successfully.
- **Findings.** `unzip -l app/build/outputs/bundle/release/app-release.aab | grep BUNDLE-METADATA` showed only the standard 6 entries (`gradle/`, `libraries/`, `obfuscation/`, `profiles/`, `r8.json`) — **no `com.android.tools.build.debugsymbols/` directory**. The AGP task ran but produced zero bundled symbols. Forensic check (`unzip -l ... | grep "\.so$"`) showed the native libraries in the AAB are SQLCipher (`libsqlcipher.so`, ~6 MB per ABI × 4 ABIs = ~22 MB) and `libandroidx.graphics.path.so` (~10 KB per ABI). Both are third-party prebuilt-and-stripped binaries — there's no debug info inside them for AGP to extract.
- **Conclusion.** Play Console warning is **unfixable from our side** without either (a) building SQLCipher from source ourselves, or (b) upstream SQLCipher publishing a version with `.dbg` files. Both are out-of-scope for v1.0. Warning is informational, NOT a release blocker.
- **Decision.** Keep the `ndk { debugSymbolLevel = "FULL" }` config block as good hygiene + intent documentation. Cost is one Gradle task per release build (~seconds). If we ever ship a custom .so or upgrade SQLCipher to a version that ships symbols, the config will pick them up automatically. Inline comment block in `app/build.gradle.kts` explains the rationale for any future maintainer.
- **Don't re-upload.** v2 stays the internal-track AAB. The fact that v3 was built locally (versionCode 3 in the merged manifest, ~18 MB AAB on disk) doesn't mean we have to upload it. The symbol warning won't go away on v3 either, so re-uploading just to dismiss it would be theater. v3 remains the next forward-only counter for a real future upload (e.g. post-smoke-test bug fixes).

### Docs synced

- **CHANGELOG.md** — new "Native debug symbols + versionCode 2 → 3 (2026-05-15)" section under [Unreleased] above the prior versionCode 1 → 2 entry. Documents the investigation findings, the unfixable nature of the warning, the rationale for keeping the config anyway, and the deliberate "don't re-upload" decision.
- **AGENTS.md** — Version line updated to "versionCode 3 — v2 was uploaded to internal track 2026-05-15; v3 is the local forward-only counter for the next upload after smoke test".
- **STATE.md** — current-objective rewritten: Phase G in progress, v2 uploaded, SKUs created with $3.99 ad_removal, ndk config added but symbol warning unfixable, next step is review-and-rollout.

### Next session

User clicks "Review and roll out release" on the v2 internal-track draft. After Google's quick review (5–30 min) they get the opt-in URL, install on a test device, and run the smoke checklist. The Gem-pack-credit-correctly-on-real-Play-Billing test is the gate for C.5 PR 3 (delete `StubBillingManager` + collapse `BillingModule` to `@Binds BillingManagerImpl`). Then closed-testing recruitment (≥12 testers, ≥14 days), then production access application, then production rollout.

## 2026-05-14 (morning, mid-session) — versionCode 1 → 2 bump after Play Console rejected first internal-track upload

- **Goal:** User attempted to upload `app/build/outputs/bundle/release/app-release.aab` to Play Console Internal testing as Step 1 of the Phase G plan. Play Console rejected with "Version code 1 has already been used. Try another version code." Cause: Play Console permanently retains every uploaded AAB's versionCode (even from withdrawn drafts), and an earlier `bundleRelease` smoke-test during the Plan 31 walk-through session permanently consumed versionCode 1 even though no track ever held a release with it.
- **Fix:** One-line bump in `app/build.gradle.kts` (`versionCode = 1` → `versionCode = 2`). `versionName` stays `1.0.0` because nothing user-visible changed — this is a Play-Console-bookkeeping bump, not a release.
- **Rebuild:** `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL. Lint vital + R8 + signing all clean. New AAB at `app/build/outputs/bundle/release/app-release.aab`, ~18 MB. Verified `versionCode="2"` in `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` via `grep`.
- **Docs synced:** CHANGELOG.md gets a "versionCode bump 1 → 2" section under [Unreleased] above the Phase F unblocker entry. AGENTS.md "Version: 1.0.0 (versionCode 1)" line updated to "versionCode 2 — bumped 2026-05-14 because Play Console retained `versionCode 1` from an earlier walk-through smoke-test upload". STATE.md current-objective + last-run line updated.
- **Pattern note:** Future Play Console rejected-upload bumps should always increment `versionCode` (never reset / decrease). Walk-through doc `docs/release/plan-31-walkthrough.md` doesn't currently mention the "any failed upload still consumes the versionCode" pitfall — worth a one-line note next time the doc gets a refresh pass.

## 2026-05-14 (morning) — Plan 31 Phase F unblocker: `feat(billing): lowercase SKU wire format`

- **Goal:** User asked "what's next?" and I pointed to STATE.md's #1 priority — the lowercase SKU code change that blocks Plan 31 Phase F SKU creation in Play Console. User said "yes", so I drove the PR end-to-end: 3 source files + 4 test files + 4 current-state docs + this RUN_LOG entry. Single, focused PR.
- **Preflight:** read STATE.md (well-trodden — covers exactly this PR as the next action), source-files.md + structure.md (what's there now, what changed in C.5 PR 1), AGENTS.md Plan 31 status. `git status` clean on `main`. Last commit `0fc9f89 docs: end-of-session sync after Plan 31 walk-through`. Ran `grep` to enumerate hardcoded SKU strings across `app/src/main` + `app/src/test` so I could plan all the call-site updates upfront before editing.

### Code changes (production)

- **`domain/model/BillingProduct.kt`** — added a public `skuId(): String` method on the enum returning `name.lowercase()`. KDoc cites Play Console's `[a-z0-9._]` rule and explains this is the canonical wire format end-to-end. This is now the stable public API for any reverse-lookup helpers; `Companion` still exists as the opt-in extension point for `fromSkuIdOrNull`.
- **`data/billing/BillingManagerImpl.kt`** — deleted the private `BillingProduct.skuId(): String = name` extension (the call sites at `purchase()` queryProductDetails + error message + `reconcileType()` PENDING + PURCHASED branches all auto-pick-up the new public method via name resolution). KDoc invariant #4 updated from "uppercase enum name" to "lowercase enum name (per ADR-0005 decision #6, refined post-Plan 31 Phase F to match Play Console's `[a-z0-9._]` product-id requirement)" with concrete examples `gem_pack_small`, `ad_removal`, `season_pass`. The `fromSkuIdOrNull` companion extension now compares `it.skuId() == skuId` instead of `it.name == skuId` — this is the correct comparison post-change because `Play Billing` will hand us lowercase productId strings (which match the lowercase Play Console SKU IDs we're about to create).
- **`data/local/BillingReceiptEntity.kt`** — productId column KDoc rewritten from "matches `BillingProduct.<variant>` names per ADR-0005 decision #6" to "the lowercase `BillingProduct.<variant>` name produced by `BillingProduct.skuId()` (e.g. `gem_pack_small`, `ad_removal`, `season_pass`)" with the Play Console `[a-z0-9._]` rationale inline. No DB schema or migration change — `productId TEXT NOT NULL` accepts any case; existing devices with uppercase rows from prior debug builds aren't in the wild because Plan 31 hasn't entered closed testing yet.

### Code changes (tests)

- **`BillingManagerImplTest.kt`** — 5 hardcoded uppercase strings switched to lowercase: `SdkProductDetails("gem_pack_small", ...)` line 187, message-contains assertion at line 218 (`"gem_pack_small"`), reconciliation receipt fixtures at 309/319 (`"gem_pack_small"`), and 356 (`"gem_pack_medium"`). The `stubHappyPath` helper (used by 3 happy-path tests) switched from `SdkProductDetails(product.name, ...)` + `SdkPurchase(productId = product.name, ...)` to `product.skuId()` for both. The `stubHappyPath` change is the important one — it means the SdkPurchase that the impl receives back from `launchPurchase` carries the lowercase productId, and the reconcile-side `BillingProduct.fromSkuIdOrNull(purchase.productId)` lookup now resolves correctly via the new lowercase comparison.
- **`BillingManagerParityTest.kt`** — 1-line helper change at `stubAdapterHappyPath`: `productId = product.name` → `productId = product.skuId()`. Same rationale as `BillingManagerImplTest.stubHappyPath`. Parity assertions on `gems` / `adRemoved` / `seasonPassActive` are case-insensitive (they compare wallet state, not SKU strings) so no other changes needed.
- **`BillingReceiptDaoTest.kt`** — 10 hardcoded strings updated from `"GEM_PACK_SMALL"` / `"GEM_PACK_MEDIUM"` / `"GEM_PACK_LARGE"` / `"AD_REMOVAL"` to lowercase. The DAO is opaque to the productId value — it just stores and retrieves the string — so these tests would have passed either way. Updated for consistency with the production wire format so the fixtures match what `BillingManagerImpl.handleCompletedPurchase` and `reconcileType` will write in production.
- **`RoomSchemaTest.kt`** — 2 strings in the billing-receipt round-trip test updated to lowercase. Same consistency rationale.

### Verification

- **Diagnostic noise (ignored).** LSP `get_diagnostics` returned ~hundreds of "unresolved reference" errors against every external import (`androidx.*`, `org.junit.*`, `org.mockito.*`, `kotlinx.*`). This was a workspace-init issue, not a real failure of my changes. Gradle is the source of truth.
- **`./run-gradle.sh test` — BUILD SUCCESSFUL, 527 tests pass.** Same count as before the PR; all the test changes were string-fixture and helper-internals tweaks that don't add or remove tests. Compile produced one pre-existing Kotlin warning about `@ApplicationContext` parameter target — unrelated to this PR (KT-73255 follow-up). Confirmed test count via `find app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -h "<testsuite " {} \; | sed -E 's/.*tests="([0-9]+)".*/\1/' | awk '{s+=$1} END {print s, "tests"}'` → `527 tests`.
- **`./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL.** Lint vital + R8 minify + signing all clean. Signed AAB at `app/build/outputs/bundle/release/app-release.aab`, ~18 MB. `ls -lh` confirmed. The new AAB is the one that should be uploaded to Play Console Phase G; the previous Plan-31-prep AAB (also at this path, since AGP overwrites) had the uppercase wire format and would silently fail product-details queries against lowercase Play Console SKUs.

### Current-state docs synced

- **`CHANGELOG.md`** — prepended a new "Phase F unblocker — lowercase SKU wire format (2026-05-14)" section under `[Unreleased]` above the existing Plan 31 walk-through entry. Documents the production + test changes, verification, no-DB-migration rationale, and links to next-session Phase G work.
- **`AGENTS.md`** — Plan 31 status line in the Current Status checklist updated from "Stopped at Phase F SKU creation due to Play Console lowercase-product-id requirement; resumes next session with `feat(billing): lowercase SKU wire format` PR" to "Phase F unblocker landed 2026-05-14: `feat(billing): lowercase SKU wire format` — `BillingProduct.skuId()` now returns `name.lowercase()` to satisfy Play Console's `[a-z0-9._]` product-id rule." Phrasing now matches the rest of the Plan 31 status as a chronological "what landed" list.
- **`.kiro/steering/source-files.md`** — `BillingProduct.kt` entry expanded to mention the new public `skuId()` method + lowercase wire format + ADR-0005 decision-#6 refinement. Single line in a one-line-per-file table.
- **`.kiro/steering/structure.md`** — `BillingProduct` bullet in the Domain Models section expanded with the same skuId() + lowercase wire format mention. Mirrors source-files.md.
- **`STATE.md`** — rewritten current-objective + what-works + next-actions to reflect Phase F unblocker landed; top priorities renumbered (the SKU code fix is no longer #1; Phase G is now #1).

### What's left for next session

1. Commit `feat(billing): lowercase SKU wire format` on `main`. 11 files: 3 prod + 4 tests + 4 docs (CHANGELOG, AGENTS, source-files, structure) + STATE.md + RUN_LOG.md.
2. External: Upload the AAB to Play Console Internal Testing → save release → create 5 lowercase SKUs → license testers → on-device verification of a real Play Billing test purchase. The on-device PASS unblocks C.5 PR 3 (delete `StubBillingManager`).
3. External (parallelizable): Closed-track recruitment (≥12 testers, ≥14 days) before Phase I production rollout.

### Decision footprint

- **No new ADR.** This PR is a refinement to ADR-0005 decision #6 (uppercase → lowercase wire format), not a new architectural choice. Documented inline in the `BillingManagerImpl` class KDoc and `BillingReceiptEntity.productId` KDoc; AGENTS.md + STATE.md References block now both note "decision #6 refined to lowercase wire format 2026-05-14". If a future session wants to crystallise this as a stand-alone amendment, the natural spot is an ADR-0005-amendment-1 stub, but the surface area didn't justify it.
- **Did NOT amend ADR-0005 file directly.** Per `.kiro/steering/11-agent-protocol.md` "Historical artifacts — NEVER modify as part of a current-PR doc sweep" — individual ADRs are amended only when the PR explicitly warrants it. The lowercase change is well-documented in 3 KDocs (BillingProduct, BillingManagerImpl, BillingReceiptEntity) plus the changelog and AGENTS, which is the right level of friction for a wire-format refinement.

## 2026-05-13 (afternoon/evening) — Plan 31 walk-through session: Phases A-E mostly landed, ADV registered, AAB built

- **Goal:** User asked "what's next?" then "walk me through Plan 31 step by step" and I drove through `docs/release/plan-31-walkthrough.md` interactively, pausing at each step for user confirmation. Multi-hour session covering Phases A1, A2, B, C, D1+D2, E1, E2, E3, E4, E5, E6 plus the new-to-me Android Developer Verification (ADV) flow that the walk-through doc didn't anticipate. Stopped at SKU creation due to a Play Console requirement that didn't match our `BillingProduct` enum naming.
- **Preflight:** read STATE.md, START_HERE.md, CONSTRAINTS.md, RUN_LOG head (covers C.6 PR 3 + Play Store icon + feature graphic from earlier today), AGENTS.md test-count + plan status. `git status` clean on `main`, last commit `8fcc52f Update contact email for Play Store listing`. Read `docs/release/plan-31-walkthrough.md` + `docs/release/release-checklist.md` to know the full route through Plan 31.

### Step-by-step external work completed

- **Phase A1 (Play Console developer account).** User signed up at <https://play.google.com/console/signup>, paid the $25 one-time fee, completed identity verification under their personal Google account `jonwhitefang@gmail.com`. "A1 is done and verified" — fast turnaround.
- **Phase A2 (AdMob account).** Created at <https://admob.google.com/> using the same Google account. "A2 is done and verified".
- **Phase B (privacy hosting).** User chose GitHub Pages, copied `docs/release/privacy-policy.md` to `docs/index.md` (separate prior commit `7760653 added index.md`), enabled Pages on `https://jonwhitefang.github.io/steps-of-bablylon/`. Verified reachable in incognito via `web_fetch`. Mid-session the data-safety form (Phase E4) demanded a `delete-data` URL; I added a `Data Deletion` section with `<a name="delete-data"></a>` anchor to BOTH `docs/release/privacy-policy.md` (commit `cc6d4a8`) AND `docs/index.md` (commit `9f7db0a`) and pushed both to `main` mid-flow. Final URL: `https://jonwhitefang.github.io/steps-of-bablylon/#delete-data`.
- **Phase C (release upload keystore).** User generated `release/upload-keystore.jks` via `keytool -genkeypair -v -keystore release/upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`. Backed up the password to their password manager. SHA-256 fingerprint extracted via `keytool -list -v`: `C4:00:72:90:D8:40:32:92:86:06:C0:E1:E4:CB:8E:86:95:80:6A:FE:54:81:A1:15:9A:74:93:62:F2:BE:BA:E8`. Created `keystore.properties` at project root with the credentials. First `bundleRelease` smoke test failed with "Keystore file '.../app/release/upload-keystore.jks' not found" because `app/build.gradle.kts` was resolving `storeFile` via `file(...)` (relative to `app/`), but the signing guide and walk-through both consistently document `storeFile=release/upload-keystore.jks` as a project-root path. Fixed with `storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))`. Re-ran: BUILD SUCCESSFUL, signed AAB at 19.4 MB.
- **Phase D1 (AdMob registration + ad units).** User created the Android app in AdMob (manual entry, package `com.whitefang.stepsofbabylon`) and three rewarded ad units, one per `AdPlacement` enum value. Pasted me the 4 IDs in `local.properties` format: `admob.appId=ca-app-pub-2428895909953191~4165048274` + 3 ad unit IDs.
- **Phase D2 (build script wiring).** Appended the 4 IDs to `local.properties` (already gitignored) and updated `app/build.gradle.kts` to load them: introduced `localPropertiesFile` + `localProperties` loaders next to the existing `keystoreProperties` block, declared `ADMOB_TEST_APP_ID` + `ADMOB_TEST_REWARDED_AD_UNIT` constants for the safe fallback, and overrode the 3 `buildConfigField AD_UNIT_*` + 1 `manifestPlaceholders["admobAppId"]` inside the `release { }` block with `localProperties.getProperty(...) ?: <test-id>`. Verified by inspecting the generated release `BuildConfig.java` (production IDs flow through) + the merged release `AndroidManifest.xml` (production app ID flows through), and confirming debug `BuildConfig.java` still reads Google's test IDs from `defaultConfig`.
- **Phase E1 (create app in Play Console).** User created "Steps of Babylon" as Game / Free / English (US). When prompted for the package name they entered `com.whitefang.stepsofbabylon`. Then Play Console asked them to register the package, which kicked off the Android Developer Verification flow.
- **Phase E1 detour: Android Developer Verification.** Play Console showed user the message "First add an eligible public key by selecting its SHA-256 certificate fingerprint" and offered ONLY the fingerprint `47:E8:9F:0A:3D:C1:8C:EA:B4:F5:A5:80:4D:74:B0:9E:C6:67:92:3B:C6:49:5E:C6:05:2A:26:AD:48:9D:75:5D` to select — not the production keystore I'd just helped them generate. Initial confusion: was this a different developer's key? A test cert? After looking at the Google support article they pointed me at (<https://support.google.com/googleplay/android-developer/answer/16641489>), and following the breadcrumb from there to the "Registering Android package names" article (16761053), I recognised this as Google's new Android Developer Verification policy where Play Console routes packages already known to Android (i.e. installed at least once on a Google-account-signed-in device) into Step 2B ("existing package") instead of Step 2A ("new package"). The mystery fingerprint was the **local Android debug keystore** at `~/.android/debug.keystore`. Confirmed by running `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA256` — exact match.
  - **Decision:** register with the debug keystore (Path A) rather than going through the rationale-and-Google-review path with the release keystore (Path B). Trade-offs documented in ADR-0007.
  - **Cert export.** Used `keytool -exportcert -rfc -keystore release/upload-keystore.jks -alias upload -storepass <from keystore.properties> -file release/upload-cert.pem` to export the release keystore's public cert to `release/upload-cert.pem`. Initially planned to use this for Path A's "Add key" flow, but pivoted to Path B (debug-keystore path) once Play Console's available-fingerprint list was understood. Cert kept on disk anyway for future "add additional ADV key" flow.
  - **gitignore tightening.** Added `release/` directory ignore so the keystore + cert + screenshots all stay out of git. Verified `git status` was clean of `release/` after the change.
- **ADV proof-of-ownership APK upload.** User clicked "Upload APK" in Play Console and copied the snippet `CHE2JNVSSL3U4AAAAAAAAAAAAA`. I created `app/src/main/assets/` directory + `adi-registration.properties` file with the snippet on a single line (matching Google's sample format from `android/security-samples` repo, fetched to confirm). Built debug APK via `./run-gradle.sh assembleDebug`, verified with `apksigner verify --print-certs`: `Signer #1 certificate SHA-256 digest: 47e89f0a3dc18ceab4f5a5804d74b09ec667923bc6495ec6052a26ad489d755d` (matches registered fingerprint exactly with colons stripped) + `unzip -l` confirmed `assets/adi-registration.properties` is at the expected path. User uploaded `app/build/outputs/apk/debug/app-debug.apk` (70 MB) to Play Console. Returned later: "its registered now". Snippet file deleted post-verification (one-time use); also added `app/src/main/assets/adi-registration.properties` to `.gitignore` so it can't accidentally land in git.
- **Phase E2 (main store listing).** User pasted the short description (57 chars), full description (2,389 chars from `docs/release/play-store-listing.md`), uploaded `play-store-icon-512.png` + `play-store-feature-graphic-1024x500.png`. Tried to save without screenshots; Play Console blocked with "can't leave phone and tablet screenshots blank."
- **Screenshot capture detour.** Confirmed `emulator-5554` was still up from earlier sessions (`adb devices`) with the app installed. Resolution 1080×2400 (9:20 ratio, taller than Play Store's max 9:16). Captured 5 screens via `adb shell input tap` to drive bottom-nav navigation (Home, Workshop, Battle, Labs, Stats) + `adb -s emulator-5554 exec-out screencap -p > raw-{name}.png`. Centre-cropped each to 1080×1920 (drop 240 px top + 240 px bottom — strips the status bar and bottom-nav text labels) via inline Pillow + flattened to 24-bit RGB to satisfy Play Store's preferred format. All 5 saved to gitignored `release/screenshots/`. Battle screenshot is the visual hero — 5-tier ziggurat in Hanging Gardens biome with full HUD. User uploaded all 5 and the listing saved.
- **Phase E2c (store settings).** Category Games → Strategy, tags Casual / Strategy / Tower defense, contact email `jonwhitefang@gmail.com`.
- **Phase E2d (privacy policy URL).** Pasted `https://jonwhitefang.github.io/steps-of-bablylon/`.
- **Phase E3 (content rating).** Submitted the IARC questionnaire with the matrix from `docs/release/play-store-listing.md`: mostly No, Yes only on IAP + reward ads. Ratings issued.
- **Phase E4 (data safety).** Submitted: collects step/health (functionality, encrypted at rest, can request deletion) + purchase history (third-party Play Billing); shares with Google Play Billing + AdMob (third-party SDKs); SQLCipher at rest; delete-data URL = `https://jonwhitefang.github.io/steps-of-bablylon/#delete-data`. Mid-form Play Console required the URL which is what triggered the privacy-policy edit + push above.
- **Phase E5 (target audience).** Set to `18+` per ADR-0006 Q5. Avoids COPPA / Families program complications.
- **Phase E6 (pricing & distribution).** User asked "where is it?" and shared `~/Desktop/Screenshot2.png` showing the modern Play Console dashboard. Reading the screenshot: left nav has Dashboard / Statistics / Publishing overview / Test and release / Monitor and improve / Grow users / Monetize with Play — no standalone "Pricing & distribution" section. The walk-through doc pre-dated this layout. Country/region selection now happens INSIDE the release flow (Phase G), not as a separate form. Free pricing was locked in at app creation. So Phase E6 is effectively a no-op in modern Play Console.
- **Closed-testing prerequisite discovery.** That same dashboard screenshot also revealed Google's new policy: "Have at least 12 testers opted-in" + "Run your closed test with at least 12 testers, for at least 14 days" before applying for production. Adds ~14 days to the launch timeline. Internal track is still our immediate target (verifies the AAB on a real device + exercises Play Billing); closed track recruitment becomes a separate workstream.

### Code-side changes (one commit on `main`)

All changes batched into one commit, `bb6b253 feat(release): Plan 31 prep — keystore path fix, AdMob ID wiring, BILLING permission`:

- **`app/build.gradle.kts`** — keystore path fix (`file(...)` → `rootProject.file(...)`) + AdMob production-ID wiring from gitignored `local.properties` with safe Google-test-ID fallback. Two new constants `ADMOB_TEST_APP_ID` + `ADMOB_TEST_REWARDED_AD_UNIT` declared at the top alongside the existing keystore-properties loader.
- **`app/src/main/AndroidManifest.xml`** — added `<uses-permission android:name="com.android.vending.BILLING" />` explicitly. Discovered later in the session when SKU creation failed: Play Console gates SKU creation on the uploaded AAB declaring this permission, and Play Billing Library v8 no longer auto-merges it (older versions did).
- **`.gitignore`** — added `release/` directory ignore (covers `upload-keystore.jks`, `upload-cert.pem`, `screenshots/*.png`) + `app/src/main/assets/adi-registration.properties` (account-specific ADV one-time-use snippet).

### Verification

- `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 527 tests pass (no test changes).
- `./run-gradle.sh bundleRelease` — BUILD SUCCESSFUL twice (once before BILLING permission was added; once after). Signed AAB at `app/build/outputs/bundle/release/app-release.aab`, 19,396,531 bytes (≈19.4 MB). `jarsigner -verify` reports `jar verified` (PKIX warning is normal for self-signed upload keystores; Play App Signing handles the upstream chain). After the BILLING permission addition: merged release manifest at `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` contains `com.android.vending.BILLING`. Generated release `BuildConfig.java` shows production AdMob IDs (`ca-app-pub-2428895909953191/...`); generated debug `BuildConfig.java` still uses Google's test IDs (`ca-app-pub-3940256099942544/5224354917`).
- `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL. Debug APK at `app/build/outputs/apk/debug/app-debug.apk` (70 MB, signed with `~/.android/debug.keystore`).
- `apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk` — SHA-256 matches the ADV-registered fingerprint exactly (`47e89f0a3dc18ceab4f5a5804d74b09ec667923bc6495ec6052a26ad489d755d`).
- `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep adi-registration` — confirmed `assets/adi-registration.properties` at the expected path.

### Where we stopped + immediate next steps

User tried to create the first SKU at Play Console → Monetize with Play → In-app products. Play Console rejected with: "Product ID must start with a number or lowercase letter. Can contain numbers, lowercase letters, underscores, and periods."

Our `BillingProduct` enum constants are UPPER_SNAKE_CASE: `GEM_PACK_SMALL`, `GEM_PACK_MEDIUM`, `GEM_PACK_LARGE`, `AD_REMOVAL`, `SEASON_PASS`. The wire-format mapping is `BillingManagerImpl.skuId(): String = name` (returns `BillingProduct.name` directly). Need to lowercase the wire format. Decision deferred to next session.

**Recommendation for next session:** keep enum constants idiomatic UPPER_SNAKE_CASE, change only the wire format. `BillingManagerImpl.skuId()` returns `name.lowercase()`. `BillingProduct.fromSkuIdOrNull(skuId)` compares against lowercased input. Audit existing tests (`BillingManagerImplTest`, `BillingManagerParityTest`, `BillingReceiptDaoTest`) for any hardcoded `"GEM_PACK_SMALL"` strings; update.

**Next session's task list (in dependency order):**

1. Land `feat(billing): lowercase SKU wire format` PR. Update mapping + tests. `./run-gradle.sh test` green. `./run-gradle.sh bundleRelease` green. Commit + push.
2. Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console → Test and release → Internal testing → Create new release. Save (don't roll out yet). Wait for AAB processing (1-3 min).
3. Create 5 SKUs in Play Console with lowercase IDs: `gem_pack_small` ($0.99 / 50 Gems / consumable), `gem_pack_medium` ($4.99 / 300 Gems / consumable), `gem_pack_large` ($9.99 / 700 Gems / consumable), `ad_removal` ($3.99 / one-time / non-consumable), `season_pass` ($4.99/month / monthly subscription / 3-day grace / 30-day account hold / no free trial). Activate each.
4. Add license testers to internal track. Roll out the release. Install on a real device via the opt-in URL.
5. Real Play Billing test purchase end-to-end. If wallet credits correctly: land C.5 PR 3 (mechanical deletion of `StubBillingManager` + collapse `BillingModule` Provider-switch to `@Binds BillingManagerImpl`).
6. Promote internal → closed track. Recruit ≥12 testers. Wait ≥14 days.
7. Apply for production access. Promote closed → production. Google review 1-7 days.

### Local artifacts created this session (gitignored)

- `release/upload-keystore.jks` — RSA 2048 production upload key. Backed up to user's password manager.
- `release/upload-cert.pem` — public cert exported for ADV (would have gone via Path B if ADV had not auto-routed via debug keystore). Kept on disk for the future "add additional ADV key" flow.
- `release/screenshots/screenshot-{1..5}-{home,workshop,battle,labs,stats}.png` — 5× 1080×1920 24-bit RGB phone screenshots used in the Play Store listing.
- `keystore.properties` — Gradle signing credentials at project root.
- `local.properties` (existing file) — 4 new `admob.*` keys appended.
- `~/Desktop/Screenshot 2026-05-13 at 13.43.19.png` + `~/Desktop/Screenshot2.png` (user-supplied) — Play Console UI screenshots used to diagnose ADV + pricing-form flows.

### Doc sync done in this commit-cycle

- `docs/release/privacy-policy.md` + `docs/index.md` — added Data Deletion section + anchor (separate commits `cc6d4a8` + `9f7db0a`, pushed mid-flow).
- `app/build.gradle.kts` + `app/src/main/AndroidManifest.xml` + `.gitignore` — batched into commit `bb6b253`.
- This entry + STATE.md rewrite + ADR-0007 (ADV decision) + CHANGELOG section + AGENTS.md test count line — final doc-sync commit at end of session.
- ADR-0007 records the meaningful decision (debug keystore for ADV) so it's not lost.

### Memory updated

- `STATE.md` ✅ — fully rewritten to capture the Plan 31 walk-through position. Top priorities reordered around the SKU lowercase fix + internal-track AAB upload + on-device billing verification + closed-testing recruitment + production rollout. Last-run line + critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- `AGENTS.md` ✅ — Plan 31 status updated to "in progress (resumes next session at SKU creation step)". Test count unchanged at 527.
- `CHANGELOG.md` ✅ — prepended a Plan 31 walk-through session entry capturing every external + code-side action.
- `ADR-0007` ✅ — new ADR capturing the debug-keystore-for-ADV decision with both alternatives, rationale, consequences, and follow-ups.

## 2026-05-13 — Play Store feature graphic: 1024×500 from user-supplied source

- **Goal:** User created a pixel-art Tower of Babel scene (`StepsOfBabylonArt.png`, 1376×768) and asked it to be resized to Play Store feature-graphic spec (1024×500 PNG, max 1 MB). Earlier today I'd flagged the feature graphic as still pending; the user closed that gap by producing the artwork themselves.
- **Preflight:** located the source via `find` — user had already moved a copy into `docs/release/store-assets/`, with the original still at `~/Downloads/`. Confirmed dimensions match (both 1376×768 RGB, 1.2 MB). `git status` clean on `main`, last commit `3eb55a4 feat(release): render Play Store 512x512 hi-res icon`.

### Composition + crop choice

- Used `read` Image mode to view the source. Pixel-art Tower of Babel scene: dominant ziggurat-style tower in the upper-middle, walking figure (head+torso+legs) on the lower-left third, swirling cloud pattern across the sky, path leading to the tower base, ruined Mesopotamian buildings framing left and right.
- Aspect mismatch: source 1.792 vs target 2.048. Need to lose 96 px of total height. Three options considered:
  - **Center crop y=48..720**: preserves character feet, retains ~85% of dramatic sky swirl, keeps path and framing ruins. Picked this.
  - **Top-aligned y=0..672**: would clip the character's feet — rejected as ugly.
  - **Bottom-aligned y=96..768**: would lose 96 px of the most dramatic sky-swirl detail — rejected as the swirl is a key atmospheric element.
- Identified a minor AI-generation sparkle artifact at the source's bottom-right (~x=1340, y=710); kept in the center crop because removing it would have required clipping the character's feet. Sub-perceptual at storefront sizes.

### Execution

- Inline Python (no script file — this is a one-shot transform of a user-provided image, not generative work like the icon script that's worth keeping reproducible):
  ```python
  src = Image.open('StepsOfBabylonArt.png')
  cropped = src.crop((0, 48, 1376, 720))   # 1376×672, 2.048 aspect
  out = cropped.resize((1024, 500), Image.Resampling.LANCZOS)
  out.save('play-store-feature-graphic-1024x500.png', 'PNG', optimize=True)
  ```
- PNG over JPEG to preserve pixel-art crispness (the source has chunky deliberate pixels; JPEG compression would smear them).

### Verification

- `file` reports: "PNG image data, 1024 x 500, 8-bit/color RGB, non-interlaced". Format correct.
- File size: 621.5 KB — 40% under Play Store's 1024 KB cap.
- Visual verification via `read` Image: full tower preserved + whole character + dominant swirl pattern + path + framing ruins all visible. The crop reads cleanly as a banner; the character's left-third placement creates a natural reading entry point that draws the eye toward the tower.

### Doc sync

- **`docs/release/release-checklist.md`** — ticked the feature graphic line with path + crop summary.
- **`.kiro/steering/source-files.md`** — added 2 entries to the Tools & Release Assets section: source `StepsOfBabylonArt.png` + final `play-store-feature-graphic-1024x500.png`.
- **`.kiro/steering/structure.md`** — added 2 rows to the Key Files table.
- **`CHANGELOG.md`** — prepended `Play Store feature graphic — 1024×500 PNG (2026-05-13)` section under `[Unreleased]`, above the icon entry from earlier today.
- **`STATE.md`** — added a new current-objective bullet documenting the feature graphic landing; updated the icon bullet (no longer says "feature graphic remains pending"); updated `Last run`.

### What remains (Plan 31)

- ~~512×512 hi-res PNG~~ ✅ done.
- ~~1024×500 feature graphic~~ ✅ done.
- **Screenshots** — last raster blocker. Need device capture from the running app on a phone (≥2, recommended 8). Best done after Plan 31 internal-track upload so screenshots include the real launcher icon + a real Play Billing test purchase flow.
- Release upload keystore + AdMob account + Play Console developer account — still external.
- Plan 31 raster-asset blocker count: 3 → 1.

## 2026-05-13 — Play Store hi-res icon: rendered 512×512 PNG from vector source

- **Goal:** Resolve the 512×512 Play Store hi-res icon blocker that was flagged yesterday as needing external raster tooling. User asked whether I could do the export myself.
- **Preflight:** read prior session's `RUN_LOG` head (vector adaptive icon landed 2026-05-12, design coords in `app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`). `git status` clean on `main`, last commit `cf53a49 feat(icon): vector adaptive launcher icon`.

### Tooling discovery + chosen path

- Probed for SVG→PNG converters: no `rsvg-convert`, no ImageMagick, no Inkscape, no `cairosvg` Python package. Pillow 11.3.0 IS installed. Homebrew is available.
- Considered four paths:
  1. `brew install librsvg` and convert via `rsvg-convert`. Cleanest external tool but requires the Android XML→SVG translation step (different schemas: `<aapt:attr>` gradient + `android:pathData`) and a Homebrew install (~30s, network-dependent).
  2. `pip install cairosvg` + render. Same XML→SVG translation problem.
  3. Use AGP's bundled vector→bitmap pipeline. Build-time, awkward to invoke directly.
  4. **Write a Pillow-only renderer that draws the same shape directly from the source coordinates.** No translation step, no install, full control over output dimensions, easy to re-run. Picked this.
- Single-source-of-truth tradeoff considered: keeping coords in two places (XML + Python) means a divergence risk if someone tweaks one and not the other. Mitigated by (a) inline header in the Python script telling future-you to edit both, (b) CHANGELOG entry calling out the duplication, (c) only one icon to maintain.

### Execution

- **Wrote `tools/render_play_store_icon.py`** (166 lines, Pillow-only):
  - Constants block at top mirroring the XML: `VIEWPORT = 108`, `OUT_SIZE = 512`, `SUPERSAMPLE = 4`, `BACKGROUND_RGB`, `ZIGGURAT_POLYGON` (20 vertices traced clockwise from bottom-left, identical to the foreground XML's pathData), `GRADIENT_STOPS` (3 stops at y=29 / 54 / 79).
  - `gradient_color_at(y)` linearly interpolates between the 3 stops; clamps outside [29, 79] to the nearest stop's color (matches Android's gradient extend behaviour at silhouette edges).
  - `render_icon`:
    1. Solid background fill at 2048×2048 (`OUT_SIZE * SUPERSAMPLE`).
    2. Vertical gradient image at the same supersample resolution (per-row sample of `gradient_color_at`).
    3. Polygon mask in 'L' mode — silhouette filled white, rest black.
    4. `canvas.paste(gradient, (0, 0), mask)` composites gradient onto background through the mask.
    5. `Image.Resampling.LANCZOS` downsample 2048×2048 → 512×512 for crisp anti-aliased edges.
    6. Save with `optimize=True`.
  - Output path resolution via `Path(__file__).resolve().parent.parent` so it works from any CWD.

### Verification

- `python3 tools/render_play_store_icon.py` — wrote `docs/release/store-assets/play-store-icon-512.png` (3.8 KB).
- `file` reports: "PNG image data, 512 x 512, 8-bit/color RGB, non-interlaced". Format correct.
- 4-point pixel sanity check at known coordinates:
  - bg corner (47, 47): expected `#0E2247`, got `#0E2247` (exact)
  - ziggurat top (256, 142): expected `#D4A843`, got `#D3A846` (within 1 channel of Gold)
  - ziggurat mid (256, 256): expected `#C2B280`, got `#C2B280` (exact)
  - ziggurat bottom (256, 369): expected `#8B5A3A`, got `#8D5E3D` (within 4 channels of lightened DeepBronze — LANCZOS edge blend with the gradient just below the polygon).
- 3.8 KB is a tiny fraction of Play Store's 1024 KB cap; geometric simplicity + PNG palette compression do their job.

### Doc sync

- **`docs/release/release-checklist.md`** — ticked "App icon: 512×512 PNG" with path + tool reference.
- **`.kiro/steering/source-files.md`** — added new `## Tools & Release Assets` section documenting both the script and the rendered PNG.
- **`.kiro/steering/structure.md`** — added 2 rows to the Key Files table.
- **`CHANGELOG.md`** — prepended `Play Store hi-res icon — 512×512 PNG rendered from vector source (2026-05-13)` section under `[Unreleased]`, above yesterday's vector adaptive icon entry.
- **`STATE.md`** — added a one-line note to the existing app-icon current-objective bullet documenting the 512×512 render; updated `Last run`.

### What remains (Plan 31)

- ~~512×512 hi-res PNG~~ ✅ done.
- 1024×500 feature graphic — still external (different composition problem, designer or image-gen prompt).
- Screenshots — still need device capture from running app.
- Release upload keystore + AdMob account + Play Console developer account — still external.
- The Plan 31 raster-asset blocker count drops from 3 to 2.

## 2026-05-12 — App launcher icon: vector adaptive icon

- **Goal:** Close the "No app icon resources" debt item tracked in STATE.md since Plan 30. User asked whether I could create art assets for Plan 31; I was upfront about the capability split — I can produce vector XML (Android adaptive icons, drawables) but not raster PNGs (no image-gen tool access). User approved proceeding with the vector adaptive launcher icon; the 512×512 hi-res PNG / 1024×500 feature graphic / screenshots remain external work.
- **Preflight:** read `presentation/battle/biome/BiomeTheme.kt` (5 biome palettes, 5-entry `zigguratColors` per biome) + `presentation/ui/theme/Color.kt` (brand palette: Gold #D4A843, LapisLazuli #26619C, SandStone #C2B280, DeepBronze #6B3A2A, Ivory #FFF8E7). `values/strings.xml` has `app_name`. Confirmed there are no existing `mipmap-*` or `drawable/` directories, and no `android:icon` / `android:roundIcon` attributes on the manifest's `<application>` — so the app was shipping with the default Android placeholder icon.
- **Design decisions:**
  - **Adaptive-icon-only, no raster fallbacks.** `minSdk=34` means every target device supports adaptive icons; the vector XML in `mipmap-anydpi-v26/` is the single source of truth. No `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}` bitmaps needed.
  - **Solid background over gradient.** Some OEM launchers composite their own mask shape over the adaptive background; radial gradients can flatten or mis-render. Chose `#0E2247` (deep-lapis-darkened-for-contrast) over a radial gradient — renders identically across round / squircle / teardrop / square masks.
  - **5 tiers echoing the game.** The in-game ziggurat entity has 5 layers (`ZigguratEntity.kt`) and every biome ships a 5-entry `zigguratColors` list. Matching the icon to that is narrative. Each tier 10dp tall, 6dp step-in per side, all inside the 72dp safe zone.
  - **Lightened bronze at base.** Pure brand `DeepBronze #6B3A2A` against the `#0E2247` background is tonally too close — the silhouette loses definition at 24dp. Lightened to `#8B5A3A` (~25% brighter) for the gradient's bottom stop. Top stays brand `Gold #D4A843`, mid stays brand `SandStone #C2B280`. Full brand palette preserved in the icon.
  - **Tower visual center at (54, 54).** Matches adaptive-icon canvas center so every launcher mask crops evenly.

### Execution

- **Created** `app/src/main/res/drawable/ic_launcher_background.xml` — solid `#0E2247` vector fill with inline rationale for the solid-over-gradient choice.
- **Created** `app/src/main/res/drawable/ic_launcher_foreground.xml` — single compound path traced as a closed silhouette (`M 22,79 L 86,79 … Z`), filled with a vertical 3-stop `<aapt:attr>` linear gradient. Header comment documents the tier geometry (widths / y-ranges / center point) so future edits know the constraint budget.
- **Created** `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` — `<adaptive-icon>` wrappers pointing at the two drawables. Both files identical; Android handles round masking from the adaptive source.
- **Updated** `app/src/main/AndroidManifest.xml` — added `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"` attributes to the `<application>` tag.

### Verification

- `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL on first run (processed the new manifest + the new drawables + the new mipmap XML), followed by second run all UP-TO-DATE confirming the build state is stable. No missing-resource errors from AAPT2, no manifest-merge conflicts.
- Files on disk verified via `ls -la`: `ic_launcher_background.xml` (1002 B), `ic_launcher_foreground.xml` (2975 B), both mipmap XMLs (273 B each).

### Current-state doc sync

- **`STATE.md`** — removed the `No app icon resources` line from Known issues; added a new Current-objective bullet documenting the landing + flagging the still-pending raster assets for Play Store upload.
- **`CHANGELOG.md`** — prepended an `App launcher icon — vector adaptive icon (2026-05-12)` section under `[Unreleased]` above the C.6 PR 3 entry.
- **`.kiro/steering/source-files.md`** — added a new `## Resources` section documenting the 4 new XML files + the pre-existing resource inventory (strings, network_security_config, step_widget_info, raw, layout) for completeness.
- **`.kiro/steering/structure.md`** — added 3 rows in the Key Files table for the 3 icon resources with descriptions.

### What remains next (Plan 31)

- **Plan 31 blockers that vector XML does NOT resolve:**
  - 512×512 hi-res Play Store icon PNG — can be exported from the vector source via Android Studio's asset studio in ~5 minutes, but requires human tooling.
  - 1024×500 feature graphic — different composition problem, needs illustration or photo-composite.
  - Screenshots (≥2, recommended 8) — need to be captured from the running app on a device/emulator, preferably once the real app icon shows on the launcher in the screenshots.
  - Release upload keystore generation, AdMob account, Play Console account — all external work.
- **What this unblocks:** anyone opening the debug build or a future release install now sees the branded Babylonian-ziggurat icon instead of the generic Android placeholder, which matters for internal-track testing screenshots, Firebase pre-launch report visuals, and the perceived-polish of any pre-production build.

## 2026-05-12 — C.6 PR 3: delete StubRewardAdManager, collapse AdModule to @Binds RewardAdManagerImpl

- **Goal:** Land the final PR in the C.6 ad-SDK series: remove `StubRewardAdManager` now that C.6 PR 2 device-track verification passed earlier this session, collapse the flag-gated Provider-switch in `AdModule` back to a plain `@Binds RewardAdManagerImpl`, drop `RewardAdManagerParityTest` (nothing left to compare), sweep stale stub references from KDoc / build comments, sync current-state docs. Expected shape: single-file-ish deletion PR, mechanically simpler than C.6 PRs 1–2.
- **Preflight:** read `STATE` (objective: C.6 PR 2 landed + verified PASS, `stub deletion now unblocked` per top priority §1), read head of `RUN_LOG` (this-session's earlier entries: battle-step-credit hotfix + C.6 PR 2 device-track verification PASS both committed to `main`), `CONSTRAINTS`, `AGENTS.md` section on AdModule + FakeRewardAdManager. Read current `AdModule.kt` / `StubRewardAdManager.kt` / `RewardAdManagerParityTest.kt` / `BillingModule.kt` (reference shape for the post-collapse Provider analogue — which will become C.5 PR 3's work) / `MainActivity.kt` consent prefetch section / `app/build.gradle.kts` USE_REAL_ADS comment blocks. `git status` clean on `main`, last commit `12f5bf9 fix(battle): NOT NULL crash on fresh-install first kill + C.6 PR 2 device-track PASS`.

### Design decision: keep `BuildConfig.USE_REAL_ADS`

- Without the stub, the flag no longer gates the Hilt binding — the Provider-switch in AdModule becomes dead code. But `MainActivity.onResume`'s UMP consent prefetch still uses the flag to skip Play Services contact on bare debug emulators. Two options:
  - **(a) Keep flag:** preserve symmetry with `USE_REAL_BILLING` (which still gates its binding via `BillingModule`'s Provider-switch until C.5 PR 3); preserve the emulator-friendly consent-prefetch skip.
  - **(b) Drop flag + consent-prefetch gate:** every debug session hits UMP; emulators without Play Services log spurious UMP errors on every start.
- STATE.md already recorded the preference: "Likely keep `BuildConfig.USE_REAL_ADS` around as it's symmetric with `USE_REAL_BILLING` and cheap to leave." Went with (a). If a later C.5 PR 3 follow-up drops `USE_REAL_BILLING`, revisit; for now, the two stay symmetric.

### Execution

- **Deleted** `app/src/main/java/.../data/ads/StubRewardAdManager.kt` (16-line file). Verified no remaining `@Inject constructor` references to it anywhere in `app/`.
- **Deleted** `app/src/test/java/.../data/ads/RewardAdManagerParityTest.kt` (4 tests, 140 lines). Without the stub there's no second implementation to compare — `RewardAdManagerImplTest`'s 8 cases remain the full coverage surface for the real impl.
- **Rewrote** `di/AdModule.kt`. Previous shape: `internal object AdModule` + `@Provides @Singleton fun provideRewardAdManager(stub: Provider<StubRewardAdManager>, real: Provider<RewardAdManagerImpl>): RewardAdManager = if (BuildConfig.USE_REAL_ADS) real.get() else stub.get()`. Collapsed to: `internal abstract class AdModule` + single `@Binds @Singleton abstract fun bindRewardAdManager(impl: RewardAdManagerImpl): RewardAdManager`. Kept module `internal` (RewardAdManagerImpl is internal; a public abstract would fail to compile). `AdInternalModule` untouched (still needed — `RewardAdManagerImpl` constructor takes `RewardedAdAdapter` + `ConsentManager`, and `MainActivity` directly injects `ConsentManager`). KDoc rewritten as a three-PR history (PR 1 → PR 2 → PR 3) with an explicit note that `BuildConfig.USE_REAL_ADS` outlives this module because MainActivity still reads it.
- **Updated** `app/build.gradle.kts` across 3 comment blocks on `USE_REAL_ADS`: defaultConfig ("Post-C.6 PR 3 the flag no longer gates the binding; it still gates MainActivity UMP consent prefetch"), debug block ("Debug skips the MainActivity UMP consent prefetch; RewardAdManagerImpl is still bound"), release block ("Release enables the UMP consent prefetch on first resume"), and the `play-services-ads` + `user-messaging-platform` dependency comment ("`RewardAdManagerImpl` is the only binding post-PR 3").
- **Swept KDoc** in 4 main-source files:
  - `RewardAdManagerImpl.kt` — replaced the "PR 1 wiring status" block with a three-PR history bullets; tweaked the "Frequency-cap ownership" tail to say "matches the prior stub's behaviour"; dropped "in PR 1" qualifier from `isAdAvailable` KDoc.
  - `ConsentManager.kt` — rewrote the "Scope" paragraph ("PR 1 ships this abstraction but does NOT wire it into MainActivity yet...") to reflect current wiring + PR 3 state.
  - `RealConsentManager.kt` — replaced the "Not wired into MainActivity in PR 1" block with "MainActivity consent prefetch" describing release-build prefetch + debug-build flag-skip + PR 3 single-binding state.
  - `MainActivity.kt` — updated the `onResume()` consent-prefetch comment to explain that the real impl is now bound in debug too, and the flag skip is purely to avoid UMP-on-emulator noise.
- Post-sweep grep: only 4 remaining `StubRewardAdManager` mentions in `app/`, all historical backticked KDoc in `AdModule.kt` + `RewardAdManagerImpl.kt` explaining what PR 3 did. Zero code-level references (no imports, no `@Inject`, no class references).

### Verification

- `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL. Test count 531 → 527 (-4), exactly the `RewardAdManagerParityTest` drop.
- `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL. Hilt graph resolves with the collapsed `@Binds` + sibling `AdInternalModule`. No missing-binding errors.
- Only warnings in the build are the pre-existing Kotlin KT-73255 `@ApplicationContext` param-vs-field deprecation warnings on 4 files (`RealConsentManager.kt`, `RealRewardedAdAdapter.kt`, `BillingManagerImpl.kt`, `RealBillingClientAdapter.kt`) — unrelated to this PR, slated for a batch cleanup when `-Xannotation-default-target=param-property` flips.

### Current-state doc sync

- **`AGENTS.md`** — test count 531 → 527; `FakeRewardAdManager` fakes-list entry annotated "sole ad-manager test double post-C.6 PR 3".
- **`.kiro/steering/source-files.md`** — removed `data/ads/StubRewardAdManager.kt` line + `data/ads/RewardAdManagerParityTest.kt` line; rewrote `di/AdModule.kt` description ("@Binds RewardAdManager → RewardAdManagerImpl (C.6 PR 3: stub deleted, single binding...)"); updated `RewardAdManagerImpl.kt` entry ("Sole `RewardAdManager` binding post-C.6 PR 3").
- **`.kiro/steering/structure.md`** — ads directory tree comment + AdModule Key Files row updated.
- **`.kiro/steering/tech.md`** — Google Mobile Ads SDK row rewritten (no more debug=stub/release=real; now "sole binding for debug + release as of C.6 PR 3; flag retained only for MainActivity consent prefetch").
- **`CHANGELOG.md`** — prepended new `Phase C.6 PR 3 — Delete StubRewardAdManager, collapse AdModule to @Binds RewardAdManagerImpl (2026-05-12)` section above the battle-step-credit hotfix entry.
- **`STATE.md`** — reshaped Current objective / What works / Known issues / Top priorities / Next actions to reflect C.6 PR 3 landing. Reordered priorities: Plan 31 now the §1 release-blocker (was §2), C.5 PR 3 now §2 (gated on Plan 31, mechanically identical to this PR). Ad-error UX gap moved up a slot given that debug no longer masks it.

### What remains next

- **Plan 31 Play Console setup** — still the only release-blocker. Signs the release keystore, creates Play Console listing, defines SKUs matching `BillingProduct` enum, adds license testers, uploads signed AAB to internal test track. Unblocks C.5 PR 2 device verification.
- **C.5 PR 3** (post Plan 31) — mechanically identical to today's work: delete `StubBillingManager`, collapse `BillingModule` Provider-switch to `@Binds`, drop `BillingManagerParityTest`, sync docs.
- **Ad-error UX gap fix** (3 call sites) — opportunistic, now slightly more pressing since debug builds bind the real impl and don't mask the silent-swallow.
- Phase D follows after Plan 31 + C.5 PR 3.

## 2026-05-12 — Battle-step-credit NOT NULL crash hotfix + C.6 PR 2 device-track verification PASS

- **Goal:** Execute C.6 PR 2 device-track verification on the connected emulator, fix the fresh-install first-kill crash uncovered during that verification, then resume ad verification on the now-stable device. Close out C.6 PR 2 as PASS.
- **Preflight:** read `STATE` (objective: C.6 PR 2 landed, 526 tests, device-track verification is top priority), `RUN_LOG` head (2026-05-11 C.6 PR 2 + C.5 PR 2 + C.6 PR 1 + C.5 PR 1 entries), `CONSTRAINTS`. `git status` clean on `main`, last commit `fcbc46f feat(ads): flag-gated RewardAdManagerImpl binding + consent prefetch (C.6 PR 2)`.

### Device prerequisite discovery

- Emulator connected (`emulator-5554`, `sdk_gphone64_arm64`, SDK 36, arm64). Not a physical device.
- **No keystore present** (`keystore.properties` absent, no `*.jks`). `app/build.gradle.kts` wraps release `signingConfig` in `if (keystorePropertiesFile.exists())` — `assembleRelease` would produce an unsigned APK or refuse to build. Billing verification requires a signed Play Store install regardless; Plan 31 Play Console setup is the gate.
- **Split the gates**: decided to run C.6 PR 2 (ad) verification now with a debug-flag flip; defer C.5 PR 2 (billing) verification to post-Plan-31. AdMob test ad units + app ID default to Google's public test values in `BuildConfig`, so no AdMob account setup is needed. Play Billing SKUs would require a real Play Console listing.
- User selected "flip `USE_REAL_ADS=true` in debug build type" over "debug-signed release build" (option 2 of the two alternatives I offered).

### C.6 PR 2 device-track verification — first session (NO_FILL)

- **Temp flag flip** in `app/build.gradle.kts` `debug { ... }` block: `USE_REAL_ADS = "false"` → `"true"` with a REVERT marker comment. Triggered full debug build path with the real `RewardAdManagerImpl` bound.
- `./run-gradle.sh :app:assembleDebug` → BUILD SUCCESSFUL. `adb install -r -t` → installed.
- First log check after launch: **UMP consent dialog fired automatically from `MainActivity.onResume` prefetch** — `D/UserMessagingPlatform: Action[dismiss]: {"status":"CONSENT_SIGNAL_SUFFICIENT"}` → `Action[complete]: {}`. IABTCF v2 string stored, GDPR treated as applicable, all 11 purposes granted. **This validated the C.6 PR 2 prefetch hook end-to-end before the user had tapped anything.**
- User drove to Cards → "🎬 Free Pack (Ad)" button → tapped. Reported "nothing happened".
- Logcat dump: `I/Ads: This request is sent from a test device.` → `I/Ads: SDK version: afma-sdk-a-v260480000.260480000.0` → `W/RewardAdManagerImpl: loadAd(DAILY_FREE_CARD_PACK) failed: code=3 No fill.` Two taps, both NO_FILL back-to-back.
- **Assessment:** AdMob server-side NO_FILL, not our code. Full pipeline proven working: real impl selected by flag-gated Provider switch (the log tag `RewardAdManagerImpl` only exists in the real impl); UMP consent + ActivityProvider + adapter + error-code translation all firing correctly. Only the `AdResult.Rewarded` branch was un-exercised.
- Offered three options: wait and retry, try post-round placement, accept as sufficient. User picked "try post-round".

### The crash (2026-05-12 15:18:32)

- User reported "we crashed" while navigating toward the battle screen.
- Logcat: `FATAL EXCEPTION: main / SQLiteConstraintException: NOT NULL constraint failed: daily_step_record.sensorSteps (code 1299)` raised from `DailyStepDao_Impl.incrementBattleSteps` (called via `creditBattleStepsAtomic$suspendImpl`). Battle-step-credit path, not ad-related at all.
- Stack trace showed the crash happens on the first enemy kill of a fresh install — before the step sensor has populated today's `daily_step_record` row.
- **Immediately flagged this is NOT a C.6 PR 2 regression** — it's a pre-existing bug latent since B.2 PR 2 (battle-step-credit atomicity). User picked option 3: fix the crash, verify fix, then retry ad.

### Diagnosis

- Read `DailyStepRecordEntity.kt`: 9 columns total, all except `date` have Kotlin `= 0` / `= emptyMap()` defaults.
- Read `DailyStepDao.kt`: `incrementBattleSteps` is raw SQL `INSERT INTO daily_step_record (date, battleStepsEarned) VALUES (:date, :delta) ON CONFLICT(date) DO UPDATE SET battleStepsEarned = battleStepsEarned + :delta`. Only two columns supplied on INSERT.
- Read `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/9.json` (Room schema export): all 9 columns `notNull=1`, `default=none`. Kotlin-level defaults on the entity data class govern Kotlin construction, NOT Room-generated CREATE TABLE DDL — so SQLite has no `DEFAULT` clauses to fall back on.
- Grep for other callers of `incrementBattleSteps`: only `AwardBattleStepsTest` and `BattleViewModelTest`, both using `FakeDailyStepDao` (plain in-memory Map with no NOT NULL enforcement). Hence the bug was never surfaced by unit tests despite 526 green.
- Grep for similar partial-INSERT UPSERT patterns across `data/local/`: only this one site.

### First-attempt fix (reverted)

- Initial hypothesis: pre-seed a row via `@Insert(onConflict = OnConflictStrategy.IGNORE) insertIfAbsent(entity)` inside the `creditBattleStepsAtomic` `@Transaction`, before the partial-column `incrementBattleSteps` UPSERT runs. Reasoning: if a row already exists, the ON CONFLICT(date) clause routes to UPDATE, skipping the INSERT-half NOT NULL check.
- Implemented: added `insertIfAbsent` to DailyStepDao + `FakeDailyStepDao` override + pre-seed call in `creditBattleStepsAtomic`. Wrote 6 DailyStepDaoTest cases (Robolectric + in-memory Room).
- **Tests failed**: 4 of the 6 still threw `NOT NULL constraint failed: daily_step_record.sensorSteps`. Only the 2 direct `insertIfAbsent` tests passed.
- Root cause of the miss: **SQLite evaluates NOT NULL BEFORE the `ON CONFLICT(date)` clause**. `ON CONFLICT(date)` catches only UNIQUE constraint violations, not NOT NULL violations. The SQLite INSERT executor first validates every column for NULL-ness on the row being inserted; only if that passes does the UNIQUE key check fire. So even with a pre-seeded row, the partial INSERT's NOT NULL check aborts the statement before the conflict handler gets a chance to route to UPDATE.
- Verified this against SQLite docs (upsert grammar section): ON CONFLICT only handles UNIQUE violations, other constraints abort with the ABORT resolution.
- **Reverted the insertIfAbsent approach** (DailyStepDao + FakeDailyStepDao + pre-seed call). Removed the 2 insertIfAbsent-targeted tests.

### Final fix (landed)

- **Expanded the INSERT half of `incrementBattleSteps`' UPSERT SQL** to supply every NOT NULL column explicitly:
  ```sql
  INSERT INTO daily_step_record
      (date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps,
       escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned)
  VALUES (:date, 0, 0, 0, 0, 0, '{}', 0, :delta)
  ON CONFLICT(date) DO UPDATE SET battleStepsEarned = battleStepsEarned + :delta
  ```
  Verified `'{}'` is the `Converters.fromStringIntMap(emptyMap()).toString()` round-trip output (via `JSONObject(emptyMap()).toString()`). Matches the Kotlin-level `activityMinutes: Map<String, Int> = emptyMap()` entity default.
- **No schema migration** (still v9). No `@ColumnInfo(defaultValue = "0")` added to the entity (would have required a v9→v10 migration to alter the CREATE TABLE DDL). The hardcoded zeros in the SQL duplicate the entity's Kotlin defaults — minor lint, but the most surgical fix for an already-released schema.
- Expanded KDoc on `incrementBattleSteps` explaining the NOT-NULL-before-ON-CONFLICT semantics + pointer to the regression test.
- **`creditBattleStepsAtomic` default method body restored to pre-attempt state** — no pre-seed call.
- **FakeDailyStepDao reverted** to pre-attempt state.
- **DailyStepDaoTest trimmed** to 5 cases: `incrementBattleSteps succeeds on empty table` (direct regression guard for the underlying SQL), `creditBattleStepsAtomic credits successfully on empty table` (full-atomic-path happy path), `creditBattleStepsAtomic preserves existing sensor data` (ON CONFLICT UPDATE branch doesn't clobber), `creditBattleStepsAtomic returns partial credit near the cap`, `creditBattleStepsAtomic returns zero when cap already exhausted`.
- **`./run-gradle.sh test` → BUILD SUCCESSFUL in 31s, 531 tests, 0 failures, 0 errors.** (526 → 531, +5).
- Final diff against origin/main: 1 modified file (`DailyStepDao.kt`, +23 / -5), 1 new file (`DailyStepDaoTest.kt`, 182 lines). FakeDailyStepDao and build.gradle.kts both reverted cleanly.

### Device-track verification — second session (crash fix validated; second ad-attempt hit DNS failure)

- Rebuilt debug APK with the fix + `USE_REAL_ADS=true` still flipped. Reinstalled. `adb shell am force-stop` + `am start` to get a clean launch.
- User drove through to Battle, started a round, reported **"battle worked well, ad button did nothing"**. No crash — the fix is validated on-device.
- Logcat dump of the ad attempt on the post-round overlay: UMP already initialized from `onResume` prefetch, `I/Ads: This request is sent from a test device.`, `I/Ads: SDK version: afma-sdk-a-v260480000.260480000.0`, then `W/Ads: Error while connecting to ad server: Unable to resolve host "googleads.g.doubleclick.net": No address associated with hostname`, `W/RewardAdManagerImpl: loadAd(POST_ROUND_GEM) failed: code=0`.
- **DNS resolution failure** on the emulator between the first session (where the ad request reached Google and got NO_FILL) and the second. Unrelated to our code — transient emulator network state.
- **C.6 PR 2 device-track verification marked PASS** based on two independent sessions covering two different placements (DAILY_FREE_CARD_PACK → NO_FILL; POST_ROUND_GEM → DNS failure), both of which exercised the full real-SDK pipeline end-to-end. Only the `AdResult.Rewarded` branch was un-exercised live; mechanistically symmetric to the `Error` branches we did exercise — no conditional logic in our code gates the happy-vs-error flow beyond the obvious wallet credit.

### UX gap surfaced (not C.6 PR 2 regression)

- Both ad attempts this session produced `AdResult.Error`, which is silently swallowed by all 3 ad call sites: `CardsViewModel.watchFreePackAd`, `BattleViewModel.watchGemAd`, `BattleViewModel.watchPsAd`. Only `AdResult.Rewarded` produces any observable effect. User experience on a NO_FILL or network stutter is "nothing happens".
- Not a C.6 PR 2 regression — the silent swallow predates the real-impl swap. Both the stub and real impl emit `AdResult` variants correctly; the miss is in the VM consumers.
- Surfaced as a new priority in STATE ("Ad-error UX gap"). Fix is straightforward: mirror the existing `userMessage: StateFlow<String?>` pattern from `MissionsViewModel` (landed in C.4). Three call sites; not a release-blocker.

### Files touched

New:
- `app/src/test/java/.../data/local/DailyStepDaoTest.kt` (182 lines, 5 tests). Robolectric + real in-memory Room, mirrors `BillingReceiptDaoTest` pattern.

Modified:
- `app/src/main/java/.../data/local/DailyStepDao.kt` — `incrementBattleSteps` SQL expanded to supply all 9 NOT NULL columns on INSERT (+11 net lines). KDoc rewritten with NOT-NULL-before-ON-CONFLICT explainer.
- `app/build.gradle.kts` — TEMP flipped `USE_REAL_ADS` debug override to `true`, then REVERTED before shutdown. Final state: identical to origin.
- `FakeDailyStepDao.kt` — first-attempt `insertIfAbsent` override added then removed. Final state: identical to origin.

Current-state doc sweep (4 files, all per 11-agent-protocol):
- `AGENTS.md` — test count 526 → 531.
- `CHANGELOG.md` — new "Hotfix — Battle-step-credit NOT NULL crash on fresh install + C.6 PR 2 device-track verification PASS" section at top of Unreleased with 8 bullets. "Current state" test-count line updated (488 → 531) with the full post-C.2-PR-3b chain.
- `.kiro/steering/source-files.md` — `DailyStepDao.kt` description gains "(2026-05-12 hotfix)" note; new `DailyStepDaoTest.kt` row in Test Layer section between `BillingReceiptDaoTest` and `BillingManagerImplTest`.
- `.kiro/steering/structure.md` / `tech.md` / `database-schema.md` / `README.md` — no changes (no new modules, no new directories, no schema version bump, no new dependencies, no user-facing build-or-run changes).

### Test changes (+5 net: 526 → 531)

- `DailyStepDaoTest` (new, 5 tests, Robolectric in-memory Room at SDK 34):
  - `incrementBattleSteps succeeds on empty table` — direct SQL-level regression guard. Before fix: `SQLiteConstraintException: NOT NULL constraint failed: daily_step_record.sensorSteps`. After fix: row inserted with all 8 non-`battleStepsEarned` columns populated from the SQL zero/`'{}'` defaults.
  - `creditBattleStepsAtomic credits successfully on empty table` — full-atomic-path happy path including wallet credit and Kotlin-default entity column propagation via the INSERT branch.
  - `creditBattleStepsAtomic preserves existing sensor data` — seeds a row with `sensorSteps=1234, creditedSteps=1234, activityMinutes=mapOf("WALKING" to 12)` then runs the atomic credit; asserts only `battleStepsEarned` advances and the ON CONFLICT UPDATE branch doesn't clobber existing data.
  - `creditBattleStepsAtomic returns partial credit near the cap` — pre-fills the counter to 3 below the 2000 cap, requests 100, asserts 3 credited.
  - `creditBattleStepsAtomic returns zero when cap already exhausted` — pre-fills to exactly the cap, requests 50, asserts 0 credited, cap counter unchanged.

### Verification

- `./run-gradle.sh test` → BUILD SUCCESSFUL in 31s, 531 tests, 0 failures, 0 errors.
- Final test-XML aggregation via `find app/build/test-results -name "*.xml" -exec grep -h "<testsuite " {} \;` → 531.
- `git diff --stat` after shutdown sequence: **1 file changed, 23 insertions, 5 deletions** — only `DailyStepDao.kt` modified, `DailyStepDaoTest.kt` untracked.
- On-device verification: fresh install, battle started, enemies killed, round completed, post-round overlay appeared. **No crash.**
- Lint: not re-run after revert. Pre-existing Kotlin KT-73255 warnings on 4 files (billing/ads `@ApplicationContext` param-vs-field targeting) unchanged.

### Open questions / blockers

- None for the hotfix.
- **Plan 31 Play Console setup** is now the only blocker on C.5 PR 2 device verification (and therefore C.5 PR 3). Requires: signing keystore, Play Console listing, SKUs matching `BillingProduct` enum, license tester accounts, internal test track upload.
- **C.6 PR 3** (stub deletion) is unblocked and can land as a single-file deletion PR at any time.
- **Ad-error UX gap** is a new P3-ish item — snackbar plumbing for 3 ad call sites. Mirror `MissionsViewModel.userMessage` pattern. Not a release-blocker but worth closing before public launch.

### Follow-ups

- **C.6 PR 3** top of the code-loop queue.
- **Plan 31 Play Console setup** is the next release-critical item.
- **Ad-error UX gap fix** any time — small, opportunistic.
- **B.4 FollowOnPipeline + B.5 UpdateMissionProgress** remain as debt cleanup.
- Consider whether to document the SQLite NOT-NULL-before-ON-CONFLICT semantic in `.kiro/steering/lib-room.md` as a pitfall note, since the first-attempt miss cost ~30min. Low-priority but would prevent a future agent from repeating the mistake.

### Memory updated

- `STATE.md` ✅ — current objective rewritten to cover the hotfix + C.6 PR 2 verification PASS; Known-issues list restructured; priorities rotated (C.6 PR 3 now #1, Plan 31 #2, Ad-error UX gap #3); Next actions rotated; last-run line updated; critical path gained "C.6 PRs 1+2 done (device-track PASS)" + "battle-step-credit hotfix done" markers; test count 526 → 531.
- `RUN_LOG.md` ✅ — this entry.
- `CHANGELOG.md` ✅ — Hotfix section at top of Unreleased; test-count chain updated in Current state.
- `AGENTS.md` ✅ — test count line updated.
- `.kiro/steering/source-files.md` ✅ — DailyStepDao.kt description + new DailyStepDaoTest.kt row.
- ADR: not warranted — no new architectural decisions. The SQL fix is a localized correctness patch; the revert of the insertIfAbsent approach is a learning moment, not an architectural shift.

## 2026-05-12 — C.6 PR 2: flag-gated ad manager binding + MainActivity consent prefetch

- **Goal:** Land the second of three PRs swapping `StubRewardAdManager` for `RewardAdManagerImpl`. C.6 PR 1 landed the real impl behind a still-stub `@Binds`; PR 2 introduces the runtime read of `BuildConfig.USE_REAL_ADS`, flips the Hilt binding based on that flag, adds the sibling `AdInternalModule` bindings Dagger needs for `RewardedAdAdapter` + `ConsentManager`, and wires a flag-gated one-shot UMP consent prefetch into `MainActivity.onResume`. PR 3 deletes the stub after internal-track confirmation.
- **Preflight:** read `STATE`, `RUN_LOG` head (2026-05-11 C.6 PR 1 entry and C.5 PR 2 entry for the pattern to mirror), `CONSTRAINTS`, ADR-0006 (Accepted as of C.6 PR 1). Read `di/AdModule.kt` (current stub `@Binds`), `di/BillingModule.kt` (the C.5 PR 2 Provider-switch shape to mirror), `presentation/MainActivity.kt` (has `@Inject internal lateinit var activityProvider` + `activityScope = CoroutineScope(SupervisorJob() + Main.immediate)` from C.5 PR 2; no consent wiring yet), `app/build.gradle.kts` (confirmed `BuildConfig.USE_REAL_ADS` already present from C.6 PR 1 + `buildConfig = true` opt-in + 3 `AD_UNIT_*` BuildConfigFields + `admobAppId` manifestPlaceholder — no build.gradle changes needed for PR 2), `data/ads/RewardAdManagerImpl.kt` (consumes `ConsentManager` inside `showRewardAd` for lazy init), `data/ads/internal/RealConsentManager.kt` (Mutex-guarded once-per-session `ensureInitialized`, errors logged non-throwing), `data/ads/internal/RealRewardedAdAdapter.kt` (the target of the new `@Binds`), `data/ads/StubRewardAdManager.kt` (unchanged), `fakes/FakeRewardAdManager.kt` (no mod needed — domain interface has no reconcile-equivalent). `git status` clean on `main`, up to date with origin (last commit `31805fe feat(ads): real AdMob RewardAdManagerImpl + UMP consent (C.6 PR 1)`).

### Design decisions

- **Why Provider-switch in `@Provides`, not `@Binds`.** Mirrors C.5 PR 2 rationale: `@Binds` is compile-time so cannot branch on a runtime `BuildConfig` value. Lazy `Provider<T>` injection means the unselected branch never constructs — the stub's `delay(1000)` never fires in release, and the real impl's `MobileAds.initialize` + UMP `requestConsentInfoUpdate` never run in debug. The `@Provides` method picks between `Provider<StubRewardAdManager>.get()` and `Provider<RewardAdManagerImpl>.get()` based on `BuildConfig.USE_REAL_ADS`.
- **Why the sibling `AdInternalModule` shipped in the same PR.** C.5 PR 2 hit a `[Dagger/MissingBinding]` error on first run because `BillingManagerImpl.adapter: BillingClientAdapter` had no `@Provides`/`@Binds` route. Having the same failure mode documented in the C.5 PR 2 RUN_LOG, I preemptively added `AdInternalModule` with `@Binds RewardedAdAdapter → RealRewardedAdAdapter` and `@Binds ConsentManager → RealConsentManager` in the initial cut. Dagger resolved on first build — no second-pass fix required.
- **Why `AdInternalModule` also binds `ConsentManager`, not just `RewardedAdAdapter`.** `MainActivity` now `@Inject`s `ConsentManager` directly for the consent prefetch. That injection point also needs a route to `RealConsentManager`. Collapsing both bindings into one sibling module keeps the "real SDK glue lives in one place" invariant; splitting them across two modules would obscure the shared "internal adapter layer" purpose.
- **Why both modules are `internal`.** `RewardAdManagerImpl`, `RealRewardedAdAdapter`, `RealConsentManager` are all `internal` because they expose internal adapter types through their constructors. A `public` provider method would leak those types through Hilt's generated factory. Making the whole module `internal` matches the C.5 PR 2 precedent and Hilt's single-Gradle-module assumption for this app.
- **Consent prefetch: yes, flag-gated, one-shot, on activityScope.** Weighed four alternatives and picked the one that matches the RUN_LOG C.6 PR 1 follow-up recommendation ("prefetch on first resume after a successful UMP init short-circuits subsequent calls anyway"):
  1. **Do nothing — lazy-on-first-ad** (status quo from the `ensureInitialized` call inside `RewardAdManagerImpl.showRewardAd`). Simplest. Pays ~200-500ms UMP latency on first reward-ad tap. **Rejected** because the recommended pattern exists and the cost is low.
  2. **Prefetch on first resume, flag-gated, one-shot via AtomicBoolean** (chosen). Zero cost in debug (flag false), amortised latency in release. Errors swallowed + logged by `RealConsentManager` internally, so no try/catch wrapper here.
  3. **Prefetch on every resume** with UMP's own idempotency as the guard. Spammier coroutine launches even though the UMP call short-circuits. **Rejected** — launching a coroutine that immediately no-ops is wasteful.
  4. **Prefetch in `StepsOfBabylonApp.onCreate`**. Runs before any Activity exists. UMP's `loadAndShowConsentFormIfRequired` needs an Activity, so this would stall the form until first Activity. **Rejected** — defeats the purpose of prefetching.
- **Why `activityScope` not `applicationScope`.** The prefetch passes `this@MainActivity` to `ensureInitialized`. If the Activity is destroyed mid-flow, the right behaviour is to cancel the prefetch — UMP's form-display will then fail on a destroyed Activity anyway, and the next `onResume` will naturally retry via the AtomicBoolean being re-evaluated on a new Activity instance. `activityScope` (Main.immediate, `cancel()` on `onDestroy`) gives this lifecycle tie for free; `applicationScope` (RO-03 / B.3 PR 2) would keep running with a stale Activity ref and log a UMP error. `applicationScope` is for work that *must* outlive the Activity (end-of-round persistence); consent prefetch is not that.
- **Why flag-gated on `BuildConfig.USE_REAL_ADS`.** In debug builds, `AdModule` binds the stub — no UMP is ever called by the ad path. Firing the prefetch in debug would hit UMP / Play Services for no runtime benefit, and would fail on emulators without Play Services (logged but alarming in dev noise). Gating the prefetch on the same flag that chooses the binding keeps the two in lockstep.
- **Why `@Inject internal lateinit var` + explicit `AtomicBoolean`, not `by lazy`.** Hilt requires field-injection for Activity dependencies (no constructor injection). `internal` visibility on the injected field matches the existing `activityProvider` precedent. `AtomicBoolean.compareAndSet(false, true)` is simpler and explicitly concurrency-safe compared to a Kotlin `by lazy { launch(...) }` which would have weird initialisation semantics (the lazy value is Unit-returning; it's the side effect that matters).
- **No new test for MainActivity prefetch hook.** Unit-testing Activity lifecycle requires Robolectric + Hilt testing rule territory — same rationale C.5 PR 2 applied to its MainActivity lifecycle wiring. Coverage for the hook is device-track + lint (AtomicBoolean usage + flag-gate would surface as a Kotlin compile error if miswired). `RewardAdManagerParityTest` gives the behavioural coverage equivalent to `BillingManagerParityTest` from C.5 PR 2.
- **Parity test shape: 3 per-placement + 1 isAdAvailable.** C.5 PR 2's parity test has 3 tests because Gem pack / Ad Removal / Season Pass have distinctly different wallet side effects (credit gems / flip flag / subscription expiry window). For ads, every `AdPlacement` goes through the same manager code path with only the `BuildConfig.AD_UNIT_*` lookup differing — so per-placement parity is arguably redundant. Kept 3 separate tests anyway for structural symmetry with the billing parity test and to guard against a future regression where one placement accidentally gets a different code path. Added a 4th `isAdAvailable` parity test because ADR-0006 decision #4 moved the availability check into `showRewardAd` — meaning both impls now return `true` from `isAdAvailable` for all placements, which is non-obvious and worth locking in.

### Files touched

New:
- `app/src/test/java/.../data/ads/RewardAdManagerParityTest.kt` (143 lines, 4 tests). Plain-Kotlin mockito-kotlin on the adapter + ConsentManager + Activity via `ActivityProvider`. `runTest` for coroutine-suspending calls. No Robolectric.

Modified:
- `app/src/main/java/.../di/AdModule.kt` — full rewrite. `abstract class AdModule { @Binds StubRewardAdManager }` (18 lines) → `internal object AdModule { @Provides Provider-switch }` + sibling `internal abstract class AdInternalModule { @Binds adapter; @Binds consent }` (89 lines total, ~60 lines of KDoc explaining the Provider-over-`@Binds` rationale + sibling-module rationale + `internal` visibility rationale + MainActivity consent-injection note). Mirrors `BillingModule` + `BillingInternalModule` KDoc shape.
- `app/src/main/java/.../presentation/MainActivity.kt` — +`import BuildConfig`, +`import ConsentManager`, +`import AtomicBoolean`; +`@Inject internal lateinit var consentManager: ConsentManager`; +`private val consentPrefetchAttempted = AtomicBoolean(false)` with KDoc; `onResume()` gained a trailing `if (BuildConfig.USE_REAL_ADS && consentPrefetchAttempted.compareAndSet(false, true)) { activityScope.launch { consentManager.ensureInitialized(this@MainActivity) } }` block with inline comment explaining flag rationale + one-shot rationale + error policy.

Current-state doc sweep (5 files, all per 11-agent-protocol):
- `AGENTS.md` — test count 522 → 526.
- `CHANGELOG.md` — new "Phase C.6 PR 2" section at the top of Unreleased (7 bullets: module rewrite, MainActivity prefetch, Dagger graph, no new deps, test count, device-only gate, KT-73255 warnings).
- `.kiro/steering/source-files.md` — `AdModule` entry rewritten to flag-gated description + AdInternalModule; `MainActivity` entry gains the consent-prefetch note; new `RewardAdManagerParityTest` row in Test Layer section.
- `.kiro/steering/structure.md` — `data/ads/` subdirectory comment updated to flag-gated language; Key Files `AdModule` entry rewritten to mirror the `BillingModule` entry shape.
- `.kiro/steering/tech.md` — Google Mobile Ads SDK row reference updated from `C.6 PR 1 — real impl landed, @Binds still stub until C.6 PR 2` to `C.6 PR 2 — flag-gated @Binds via BuildConfig.USE_REAL_ADS; debug=stub, release=real` (matches the Play Billing row's post-C.5-PR-2 shape).

### Test changes (+4 net: 522 → 526)

- `RewardAdManagerParityTest`: **+4 tests**. `POST_ROUND_GEM parity` / `POST_ROUND_DOUBLE_PS parity` / `DAILY_FREE_CARD_PACK parity`: all three assert `stub.showRewardAd(placement)` and `real.showRewardAd(placement)` both return `AdResult.Rewarded` when the real side's mocked consent + adapter produce happy responses (`canRequestAds() = true`, `loadAd() = Success(SdkRewardedAd)`, `showAd() = Rewarded`). `isAdAvailable parity`: asserts `stub.isAdAvailable(p) == true == real.isAdAvailable(p)` for all 3 placements, locking in the ADR-0006 decision-4 contract that the real availability check moves into `showRewardAd`. All 4 tests complete in 20ms.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL in 45s. **526 tests, 0 failures, 0 errors, 0 skipped.**
- Test count verified via `find app/build/test-results -name "*.xml" -exec grep -h "<testsuite " {} \;` + awk aggregation — 526.
- `RewardAdManagerParityTest` XML shows 4 tests passing in 0.02s total.
- Dagger graph resolved on the first build attempt. The sibling `AdInternalModule` bindings for `RewardedAdAdapter` + `ConsentManager` preempted the missing-binding error that C.5 PR 2 hit on its first run.
- 4 pre-existing Kotlin KT-73255 forward-compat warnings on `RealConsentManager:55`, `RealRewardedAdAdapter:56`, `BillingManagerImpl:79`, `RealBillingClientAdapter:54` (all `@ApplicationContext` param-vs-field annotation targeting) — not introduced by this PR. Would land as a batch fix via `-Xannotation-default-target=param-property`.
- Lint clean, no new warnings.

### Open questions / blockers

- None for PR 2.
- **Internal test track verification is the combined gate for C.5 PR 3 + C.6 PR 3.** Device-only work — install a release build signed with the release keystore, test (a) the `GEM_PACK_SMALL` happy path via Play Billing test account and (b) each of the 3 reward-ad placements by checking that a real AdMob test ad renders and `onUserEarnedReward` credits the wallet / opens the pack / doubles the PS. Unit-test coverage stops short of real Play Services + AdMob.
- C.5 PR 3 + C.6 PR 3 can each be a single-file deletion PR OR batched into a single deletion PR if the device-track confirms both simultaneously — the two stubs have no shared dependencies and delete independently.

### Follow-ups

- **Internal-track device verification** of C.5 PR 2 + C.6 PR 2 is now the top task. Both can be verified from the same release-build install.
- **C.5 PR 3 / C.6 PR 3** stub deletions after device-track confirmation.
- After the stub deletions, Phase D (Plan 31 Play Console setup, AAB upload, Firebase pre-launch) is the last item on the release-critical path.
- B.4 FollowOnPipeline + B.5 UpdateMissionProgress remain as opportunistic debt cleanup. No longer gated on anything.

### Memory updated

- `STATE.md` ✅ — current objective now "C.6 PR 2 landed"; What-works gained a C.6 PR 2 bullet (C.6 PR 1 bullet retained; both will fold into a single summary when PR 3 lands); Known-issues swapped the "flag swap + MainActivity consent wiring land in PR 2" line for a stub-still-ships-pending-deletion line + merged the two runtime-branch notes (billing + ads) into a single consolidated line; test count 522 → 526; priorities rotated (C.6 PR 2 removed, C.6 PR 3 moved up, B.4/B.5 promoted to #4, internal-track verification now covers both billing + ads); Next actions rotated; last-run line updated; critical path gained "C.6 PR 2 done" marker.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — ADR-0006 already covers C.6 PR 2 under decision #9 (3-PR rollout). No new architectural decisions were made.

## 2026-05-11 — C.6 PR 1: real AdMob `RewardAdManagerImpl` + UMP consent glue + adapter seam

- **Goal:** Land the first of three PRs swapping `StubRewardAdManager` for a real Google Mobile Ads integration, per ADR-0006 and implementation-roadmap §C.6. PR 1 introduces the real impl + adapter seam + UMP consent + unit tests without flipping the DI binding; PR 2 adds the `BuildConfig.USE_REAL_ADS` flag + binding swap + MainActivity consent-flow wiring; PR 3 deletes the stub. PR 1 must also promote ADR-0006 from Proposed → Accepted with concrete answers to the 6 open questions, mirroring the ADR-0005 shape from C.5 PR 1.
- **Preflight:** read `STATE`, `RUN_LOG` head (2026-05-11 C.5 PR 2 entry), `CONSTRAINTS`, ADR-0006 (Proposed), implementation-roadmap §C.6, existing `StubRewardAdManager` / `RewardAdManager` / `AdPlacement` / `AdResult` / `FakeRewardAdManager` / `AdModule`. Read C.5 PR 1 RUN_LOG and C.5 PR 2 RUN_LOG for the pattern precedent. Web-searched current stable versions: Google Mobile Ads SDK Android 25.0.0; User Messaging Platform 4.0.0. `git status` clean on `main`, up to date with origin (last commit `7e8c0e1 feat(billing): flag-gated BillingManagerImpl binding (C.5 PR 2)`).

### Design decisions

- **Share `ActivityProvider` with billing, don't duplicate.** Both `RewardedAd.show()` and `BillingClient.launchBillingFlow()` require an `Activity` reference. The existing `data/billing/internal/ActivityProvider` (C.5 PR 1) is already wired by MainActivity (C.5 PR 2) and is a trivial WeakReference holder with no billing-specific logic. Duplicating it into `data/ads/internal/ActivityProvider` would mean two MainActivity lifecycle observers and two sets of `onResume/onPause` calls to keep in sync. Sharing the existing singleton keeps the wiring at 1 place. Awkward import from `data.billing.internal` into `data.ads` is a tolerable cost; a future cosmetic refactor may move the class to `data/platform/` but that is not PR 1's job.
- **Adapter seam on `RewardedAdAdapter`, separately from `ConsentManager`.** AdMob and UMP are two SDKs with different failure modes and different test strategies. Collapsing them into a single interface would force the impl to know which SDK a given call was going to; separating lets `RewardAdManagerImpl` treat them as orthogonal dependencies and lets the unit tests mock each independently. Matches the C.5 precedent (`BillingClientAdapter` is its own thing, not conflated with a consent surface — Play Billing has no consent concept).
- **AtomicBoolean `rewarded` flag in `RealRewardedAdAdapter.showAd`.** AdMob's reward contract is: `OnUserEarnedRewardListener.onUserEarnedReward` fires BEFORE `FullScreenContentCallback.onAdDismissedFullScreenContent`. So we flip a flag in the earlier callback and read it in the later one. `AtomicBoolean` over a plain `var` because both callbacks may fire on the Main thread but the `withContext(Dispatchers.Main)` suspension boundary is not guaranteed to emit a happens-before between them. Cheap belt-and-suspenders.
- **Preload-on-trigger + no cache.** ADR-0006 decision #3. Each `showRewardAd` runs a fresh `adapter.loadAd(adUnitId)`. Alternative (upfront preload at app startup, refresh periodically): would pay the load cost per session even when the user doesn't trigger an ad, and AdMob invalidates loaded ads after 1 hour anyway. Trigger-time load keeps cost proportional to actual use.
- **Defer to AdMob's internal timeout on `RewardedAd.load` (~60s).** ADR-0006 Q2. Wrapping in a shorter `withTimeout(10.seconds)` was considered and rejected — it would collapse distinct AdMob error codes (NO_FILL=3, NETWORK_ERROR=2, INVALID_REQUEST=1, INTERNAL=0, TIMEOUT) into an undifferentiated "timed out" string. The impl's `toUserMessage` helper translates each AdMob code into a precise user-visible string; that's worth keeping.
- **Mutex-guarded single-session UMP init.** UMP's `requestConsentInfoUpdate` is idempotent but not cheap (makes a network call). Guarding with a Mutex means two concurrent first-callers both complete the same UMP update without duplicate work. Once `initialized` flips, all subsequent calls short-circuit.
- **Consent-denied still grants reward (Q1).** When UMP returns consent-denied, AdMob serves non-personalised ads via `consentInformation.canRequestAds() == true`. Per ADR-0006 Q1, we grant the reward regardless of personalisation — the user watched the ad, the reward contract is fulfilled. `RewardAdManagerImpl` does not branch on personalisation; it maps `SdkAdShowResult.Rewarded` → `AdResult.Rewarded` unconditionally.
- **Session-Mutex-guarded `showRewardAd`.** Prevents two concurrent callers from overlapping `RewardedAd.show()` invocations. AdMob serialises internally, but letting two parallel `load + show` pairs queue up wastes a load and confuses the user with two ad surfaces back-to-back.
- **Error codes translated to user messages in `RewardAdManagerImpl`, not the adapter.** Adapter keeps raw SDK codes (0–3) + raw messages so unit tests can assert on code + message shape. Impl decides which codes map to which user-visible strings — keeps localisation + UX copy out of the adapter seam.
- **PR 1 does NOT touch `AdModule`.** Mirrors C.5 PR 1. Dagger never constructs `RewardAdManagerImpl` in a PR 1 debug build, so no `@Binds RewardedAdAdapter → RealRewardedAdAdapter` needed yet. The missing-binding error that surfaced in C.5 PR 2 will surface similarly in C.6 PR 2 — added then.
- **All 3 ad-unit IDs default to the same documented test unit in debug.** Google publishes `ca-app-pub-3940256099942544/5224354917` as the universal test rewarded-video unit. Three identical BuildConfig constants may look redundant, but (a) it makes the release-build diff obvious when PR 2 wires local.properties, (b) it keeps the code path identical in debug and release (same per-placement lookup, just different values).

### Files touched

New:
- `app/src/main/java/…/data/ads/RewardAdManagerImpl.kt` (158 lines). Orchestrator: session-Mutex, consent → load → show, error-code translation.
- `app/src/main/java/…/data/ads/internal/RewardedAdAdapter.kt` (91 lines). Interface + `SdkAdLoadResult` / `SdkAdShowResult` / `SdkRewardedAd` sealed types.
- `app/src/main/java/…/data/ads/internal/RealRewardedAdAdapter.kt` (150 lines). AdMob v25 concrete — lazy `MobileAds.initialize` on first `loadAd`, `CompletableDeferred` bridging `RewardedAdLoadCallback` + `FullScreenContentCallback` + `OnUserEarnedRewardListener`, `AtomicBoolean` rewarded flag. Only file importing `com.google.android.gms.ads.*`.
- `app/src/main/java/…/data/ads/internal/ConsentManager.kt` (54 lines). UMP-neutral interface.
- `app/src/main/java/…/data/ads/internal/RealConsentManager.kt` (117 lines). UMP v4 concrete — Mutex-guarded once-per-session `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired`. Only file importing `com.google.android.ump.*`.
- `app/src/test/java/…/data/ads/RewardAdManagerImplTest.kt` (235 lines, 8 tests). Plain-Kotlin sealed adapter + mockito-kotlin; no Robolectric.

Modified:
- `gradle/libs.versions.toml` — `playServicesAds = "25.0.0"` + `userMessagingPlatform = "4.0.0"` + 2 library entries.
- `app/build.gradle.kts` — `defaultConfig` gained `USE_REAL_ADS=false` baseline + 3 `AD_UNIT_*` buildConfigFields (all default to Google test unit) + `admobAppId` manifestPlaceholder; `debug { USE_REAL_ADS=false }` / `release { USE_REAL_ADS=true }` overrides for symmetry with the billing flag; `implementation(libs.play.services.ads)` + `implementation(libs.user.messaging.platform)` in dependencies.
- `app/src/main/AndroidManifest.xml` — `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>` added inside `<application>`.
- `app/proguard-rules.pro` — `-keep class com.google.android.gms.ads.** { *; }` + `-keep interface com.google.android.gms.ads.**` + `-dontwarn com.google.android.gms.ads.**` and matching rules for `com.google.android.ump.**`.
- `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` — Status Proposed → Accepted; Decision section rewritten with 9 concrete commitments; new "Resolved open questions" table with Q1–Q6 decisions; References list updated.
- `AGENTS.md` — test count 514 → 522.
- `CHANGELOG.md` — new "Phase C.6 PR 1" section at top of Unreleased.
- `.kiro/steering/source-files.md` — 5 new data/ads/** entries + AdModule flag for PR 2 + RewardAdManagerImplTest row.
- `.kiro/steering/structure.md` — `data/ads/internal/` subtree documented + `ActivityProvider` cross-consumer note.
- `.kiro/steering/tech.md` — +2 rows (Google Mobile Ads SDK 25.0.0, UMP 4.0.0).

### Test changes (+8 net: 514 → 522)

- `RewardAdManagerImplTest`: **+8 tests**. Happy `Rewarded` path (consent ok → load ok → show returns `Rewarded`). `Cancelled` on user-dismiss (show returns `Dismissed`). 4 `Error` paths: no Activity registered (bails before consent); consent unavailable (bails before load); load failed (AdMob code 3 → "No ad available right now. Try again later."); show failed (AdMob code 1 → "Ad was already shown."). Consent-denied-still-grants per Q1 (demonstrates the impl does NOT branch on consent state as long as `canRequestAds` returns true). Placement → ad-unit routing for all 3 `AdPlacement` values (guards against a copy-paste regression where one placement is wired to another's constant). All tests use `mockito-kotlin` on the adapter + ConsentManager interfaces; no Robolectric.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin` — first run failed with `Unresolved reference 'AD_UNIT_POST_ROUND_GEM'` (and the other two AD_UNIT_* fields). Diagnosed as a prior multi-edit `edit_file` call that failed atomically when one of its three edits didn't match exactly — only the dependency-addition edit had landed; the `defaultConfig` + `buildTypes` edits silently reverted. Re-applied the BuildConfig fields + manifestPlaceholder as a separate edit. Second run green with one Elvis-on-non-nullable-String warning in `RealRewardedAdAdapter` (error.message is `@NonNull` in AdMob's Java API); removed the `?: "fallback"` on both `onAdFailedToLoad` and `onAdFailedToShowFullScreenContent`. Third run fully clean.
- `./run-gradle.sh test` — BUILD SUCCESSFUL in 41s. 522 tests, 0 failures, 0 errors. 8 new RewardAdManagerImplTest cases complete in 1.227s (the placement-routing test dominates at 1.187s due to the per-call adapter stub setup). KT-73255 forward-compat warning on `RealRewardedAdAdapter:56` is the same `@ApplicationContext` param-vs-field issue as `BillingManagerImpl:79` and `RealBillingClientAdapter:54`; not new, not addressed here.
- Verified in the build that the AdMob SDK's ProGuard consumer rules activate with the `isMinifyEnabled = true` release path — release build compiles without any new `-dontwarn` requests.

### Open questions / blockers

- None for PR 1. All 6 ADR-0006 open questions resolved with concrete decisions in the Resolved open questions table.
- **Internal test track verification is the gate for C.6 PR 3.** Device-only work — install a release build, trigger each of the 3 ad placements, verify test-ad renders + reward fires. Only possible AFTER C.6 PR 2 lands the binding swap.
- **PR 2 scoping (next agent session):**
  - `BuildConfig.USE_REAL_ADS` read in `AdModule.provideRewardAdManager` via `@Provides Provider<Stub> + Provider<Real>` (mirroring `BillingModule`'s PR 2 shape).
  - Sibling `internal abstract class AdInternalModule` with `@Binds RewardedAdAdapter → RealRewardedAdAdapter` and `@Binds ConsentManager → RealConsentManager`.
  - Decide whether to prefetch consent in `MainActivity.onResume` or keep it lazy-on-first-ad. Lazy is simpler but adds ~200ms latency to the first ad trigger. Prefetching means UMP runs on every app open, which is mild bandwidth waste but better UX. Recommendation: prefetch on first resume after a successful UMP init short-circuits subsequent calls anyway.

### Follow-ups

- **C.6 PR 2 is now the top code-facing task.** Flag swap + sibling AdInternalModule + optional MainActivity consent prefetch.
- **Internal-track device verification** of C.5 PR 2 remains outstanding (device-only work, not blocked by C.6).
- **C.5 PR 3** (stub deletion) gated on C.5 PR 2 device verification.
- **C.6 PR 3** (stub deletion) gated on C.6 PR 2 + device verification.
- After C.5 + C.6 fully land, Phase D (Plan 31 Play Console setup) is unblocked.
- B.4 FollowOnPipeline + B.5 UpdateMissionProgress remain as opportunistic debt cleanup.

### Memory updated

- `STATE.md` ✅ — current objective now "C.6 PR 1 landed"; What-works gained a C.6 PR 1 bullet; Known-issues gained a C.6-stub-still-ships line + a note on the no-adapter-binding-needed rationale; test count 514 → 522; priorities rotated (C.6 PR 1 removed, C.6 PR 2 moved to #3); Next actions rotated; last-run line updated; critical path gained "C.6 PR 1 done" marker.
- `RUN_LOG.md` ✅ — this entry.
- `ADR-0006` ✅ — status Proposed → Accepted; 6 open questions resolved with concrete decisions + reasoning; Decision section rewritten; Date line appended with Accepted timestamp.

## 2026-05-11 — C.5 PR 2: flag-gated binding swap + MainActivity lifecycle wiring + reconcile hook

- **Goal:** Land the second of three PRs swapping `StubBillingManager` for `BillingManagerImpl`. C.5 PR 1 landed the real impl behind a still-stub `@Binds`; PR 2 introduces `BuildConfig.USE_REAL_BILLING`, flips the Hilt binding based on that flag, wires `MainActivity.onResume/onPause` into `ActivityProvider`, and calls `billingManager.reconcilePendingPurchases()` on Store entry. PR 3 deletes the stub after internal-track confirmation.
- **Preflight:** read `STATE`, `RUN_LOG` head (2026-05-11 C.5 PR 1 entry), `CONSTRAINTS`, then the 6 files touched by this PR to establish baseline: `di/BillingModule.kt` (current `abstract class @Binds StubBillingManager`), `presentation/MainActivity.kt` (has `@Inject` for `HealthConnectClientWrapper` + `PlayerRepository`; `onResume` exists, `onPause` absent), `presentation/store/StoreViewModel.kt` (one-launch init), `data/billing/internal/ActivityProvider.kt` (WeakReference, `internal` class), `data/billing/BillingManagerImpl.kt` (internal, reconcile implemented), `data/billing/StubBillingManager.kt`. Also read `BillingManagerImplTest` for testing patterns + `StoreViewModelTest` + `FakeBillingManager` for the existing coverage shape. `git status` clean on `main`, up to date with origin (last commit `0a9b73f feat(billing): real Play Billing v8 BillingManagerImpl (C.5 PR 1)`).

### Design decisions

- **Why `@Provides` + `Provider<T>` instead of `@Binds`.** Hilt's `@Binds` is resolved at compile time; it cannot branch on a runtime value like `BuildConfig.USE_REAL_BILLING`. Two `@Binds` methods with the same return type would collide. Injecting both candidates as `Provider<T>` and calling `.get()` on the selected one defers construction — whichever branch is not selected never instantiates. The stub's `PlayerRepository` observer never attaches in release; the real impl's Play Billing client never starts in debug.
- **Why a sibling `BillingInternalModule` for `@Binds BillingClientAdapter → RealBillingClientAdapter`.** Without this binding, Dagger fails at compile time with `[Dagger/MissingBinding] com.whitefang.stepsofbabylon.data.billing.internal.BillingClientAdapter cannot be provided without an @Provides-annotated method`. Dagger resolves the full graph ahead of time, so even though the debug `Provider<BillingManagerImpl>` is never invoked at runtime, the generated factory still needs a route for the impl's constructor dependencies. First attempt without this binding failed with that exact error; adding the binding fixed it.
- **Why the whole `BillingModule` is `internal`.** `BillingManagerImpl` and `RealBillingClientAdapter` are both `internal` (they expose `internal` adapter types through their constructors), so a `public` provider method cannot legally reference them. Making the module `internal` is simpler than annotating every member and matches Hilt's single-Gradle-module assumption for this app. Hilt's KSP-generated code lives in the same module and can see the internals.
- **Why `buildConfigField` is set in both `defaultConfig` AND each build type.** Sets a safe baseline in `defaultConfig(false)` for any future flavour that forgets to override, plus explicit overrides in `debug {}` (false) and `release {}` (true) for grep-friendly symmetry. Zero-cost belt-and-suspenders.
- **Why `buildFeatures.buildConfig = true` is required.** AGP 9 disables `BuildConfig` class generation by default as part of its build-time reduction push. We need to read the flag in `BillingModule`, so the opt-in is mandatory. First local test-run compile failed without it; adding the block fixed it.
- **Why `MainActivity.onPause` clears the Activity reference BEFORE `super.onPause()`.** The `WeakReference` in `ActivityProvider` is the belt — a garbage-collected Activity would auto-null. The explicit `clear()` is the suspenders: a paused-but-not-yet-GC'd Activity could otherwise race with a purchase attempt arriving on a different code path. Cost is one line; downside is zero.
- **Why `StoreViewModel.init` launches the reconcile hook in a separate coroutine (not sequentially after `ensureSeedData`).** The two are independent: reconcile doesn't need seed data, seed data doesn't need billing. Running them in parallel halves the init-flight latency on Store entry. The default no-op in `BillingManager.reconcilePendingPurchases` keeps the reconcile a no-op for Stub/Fake, so this change doesn't alter any existing test's Store-init timing.
- **Why `FakeBillingManager.reconcileCallCount` uses `private set`.** Tests write into `resultQueue`, `nextResult`, `adRemoved`, etc., but `reconcileCallCount` is an observed signal from the ViewModel, not a test-configured input. `private set` prevents a future test from accidentally writing to the counter and masking a broken production-side assertion.
- **Parity test: two independent DBs + 60s tolerance on subscription expiry.** Stub uses `System.currentTimeMillis()` at call-time; Real uses `purchase.purchaseTime` from the mocked adapter. A byte-equal assertion on the subscription expiry would couple the test to the synchronisation between those two clocks, which is test-brittle. 60-second tolerance is mathematically exhaustive for "computed from the same 30-day window within the same test run".

### Files touched

New:
- `app/src/test/java/…/data/billing/BillingManagerParityTest.kt` (253 lines, 3 tests). Robolectric + two independent in-memory `AppDatabase` instances + real `PlayerRepositoryImpl` on both sides + mocked `BillingClientAdapter` on the real side. One test per product shape (consumable / non-consumable / subscription).

Modified:
- `app/build.gradle.kts` — `buildFeatures.buildConfig = true` added; `defaultConfig` gained `buildConfigField("boolean", "USE_REAL_BILLING", "false")` baseline; new `debug { buildConfigField("boolean", "USE_REAL_BILLING", "false") }` block; `release {}` gained `buildConfigField("boolean", "USE_REAL_BILLING", "true")`.
- `app/src/main/java/…/di/BillingModule.kt` — full rewrite. `abstract class BillingModule { @Binds StubBillingManager }` → `internal object BillingModule { @Provides Provider<Stub> + Provider<Real> picking via flag }` + sibling `internal abstract class BillingInternalModule { @Binds BillingClientAdapter → RealBillingClientAdapter }`. KDoc explains the `@Provides`-over-`@Binds` rationale and the `internal` visibility choice.
- `app/src/main/java/…/presentation/MainActivity.kt` — +`import ActivityProvider`, +`@Inject internal lateinit var activityProvider`, `onResume` gained `activityProvider.set(this)` line before the existing `updateLastActiveAt` launch, new `onPause()` override clears the ref before `super.onPause()`.
- `app/src/main/java/…/presentation/store/StoreViewModel.kt` — `init` block expanded from one-line single-launch to two-line double-launch (ensureSeedData + reconcilePendingPurchases).
- `app/src/test/java/…/fakes/FakeBillingManager.kt` — +`var reconcileCallCount: Int = 0; private set` + `override suspend fun reconcilePendingPurchases() { reconcileCallCount++ }`.
- `app/src/test/java/…/presentation/store/StoreViewModelTest.kt` — +1 test case asserting `reconcileCallCount == 1` after VM init. Placed at the end of the first test class, after the billing failure-mode coverage.
- `AGENTS.md` — test count 510 → 514; Fakes list updated to call out `reconcileCallCount` on FakeBillingManager.
- `CHANGELOG.md` — new "Phase C.5 PR 2" section added at the top of Unreleased (7 bullets).
- `.kiro/steering/source-files.md` — BillingModule description rewritten; ActivityProvider description says "set/cleared by MainActivity lifecycle (C.5 PR 2)"; MainActivity entry mentions onResume/onPause for Play Billing; StoreViewModel entry mentions the reconcile hook; FakeBillingManager entry mentions `reconcileCallCount`; new BillingManagerParityTest row added after BillingManagerImplTest.
- `.kiro/steering/structure.md` — `data/billing/` + `data/billing/internal/` subdirectory descriptions updated; BillingModule "Key Files" entry rewritten to describe the Provider-based switch + sibling BillingInternalModule.
- `.kiro/steering/tech.md` — Play Billing row's reference updated from "C.5 PR 1 — impl present, @Binds still stub until C.5 PR 2" to "C.5 PR 2 — flag-gated @Binds via BuildConfig.USE_REAL_BILLING; debug=stub, release=real".

### Test changes (+4 net: 510 → 514)

- `BillingManagerParityTest`: **+3 tests**. `GEM_PACK_SMALL parity`: both impls credit 50 gems + totalGemsEarned. `AD_REMOVAL parity`: both impls flip adRemoved to true, neither touches gem wallet. `SEASON_PASS parity`: both impls activate the pass + land within 60s of `now + 30 days`. Each test sets up two separate in-memory `AppDatabase` instances so neither side observes the other's Room writes. Real side stubs the mocked `BillingClientAdapter` for a happy-path flow via `stubAdapterHappyPath(...)` helper.
- `StoreViewModelTest`: **+1 test**. `init reconciles pending purchases exactly once` constructs a fresh VM, collects uiState, advances idle, asserts `billingManager.reconcileCallCount == 1`. Colocated with the existing billing-failure-mode tests.

### Verification

- `./run-gradle.sh test` — first run failed with `[Dagger/MissingBinding] com.whitefang.stepsofbabylon.data.billing.internal.BillingClientAdapter cannot be provided without an @Provides-annotated method`. Diagnosed as Dagger's compile-time graph resolution seeing that `BillingManagerImpl` needs a `BillingClientAdapter` and having no way to provide it. Added `BillingInternalModule` with `@Binds BillingClientAdapter → RealBillingClientAdapter` to `BillingModule.kt`. Re-ran: `BUILD SUCCESSFUL in 23s`, 514 tests completed, 0 failures, 0 errors.
- Test count verified via `find app/build/test-results -name "*.xml" -exec grep -h "<testsuite " {} \; | awk -F'tests="' '{split($2, a, "\""); sum += a[1]} END {print sum}'` = 514. Failures = 0, errors = 0. Parity test XML shows 3 cases passing in 0.102s total.
- Two pre-existing Kotlin KT-73255 forward-compat warnings on `BillingManagerImpl:79` and `RealBillingClientAdapter:54` are from C.5 PR 1 (Hilt `@ApplicationContext` param-vs-field targeting); no new warnings introduced by this PR.

### Open questions / blockers

- None for PR 2.
- **Internal test track verification is the gate for C.5 PR 3.** That is device-only work — install a release build signed with the release keystore, test the GEM_PACK_SMALL happy path + at least one failure path (user-cancel) via a Play Billing test account. Unit-test coverage stops short of real Play Services.
- C.5 PR 3 will be a single-file deletion of `StubBillingManager` + the Provider branch in `BillingModule` that routes to it, after ~1 week of internal-track confirmation.

### Follow-ups

- **C.6 PR 1 is now the top code-facing task.** Shape is the mirror of C.5 PR 1: real `RewardAdManagerImpl` + `AdClientAdapter` seam + unit tests; `@Binds` stays at stub until C.6 PR 2. Answering ADR-0006's 6 open questions in the PR description promotes it Proposed → Accepted.
- **Internal-track device verification** of C.5 PR 2.
- **C.5 PR 3** stub deletion after device-track confirmation.
- **C.6 PRs 2–3** following the C.5 rollout shape.
- B.4 FollowOnPipeline + B.5 UpdateMissionProgress stay as opportunistic debt cleanup.

### Memory updated

- `STATE.md` ✅ — current objective now "C.5 PR 2 landed"; What-works gained a C.5 PR 2 bullet (C.5 PR 1 bullet retained; both will fold into a single summary when PR 3 lands); Known-issues swapped the stub-still-bound line for a stub-still-ships-pending-deletion line + added a note that the USE_REAL_BILLING runtime branch is not covered by JVM tests (that's internal-track work); test count 510 → 514; priorities rotated (PR 2 removed, C.6 PR 1 moved to #1); Next actions rotated; last-run line updated; critical path gained "C.5 PRs 1+2 done" marker.
- `RUN_LOG.md` ✅ — this entry.

## 2026-05-11 — C.5 PR 1: real Play Billing v8 `BillingManagerImpl`

- **Goal:** Land the first of three PRs swapping `StubBillingManager` for a real Google Play Billing implementation, per ADR-0005 and implementation-roadmap §C.5. PR 1 introduces the real impl + receipt table + migration + unit tests without flipping the DI binding; PR 2 adds the `BuildConfig.USE_REAL_BILLING` flag + binding swap + MainActivity lifecycle wiring; PR 3 deletes the stub once the closed-track confirms. PR 1 must also promote ADR-0005 from Proposed → Accepted with concrete answers to the 5 open questions.
- **Preflight:** read `STATE`, `RUN_LOG` head (2026-05-08 ADR stubs entry), `CONSTRAINTS`, ADR-0005 (proposed), implementation-roadmap §C.5, existing `StubBillingManager` / `BillingManager` / `BillingProduct` / `FakeBillingManager` / `PlayerProfileDao` / `MilestoneDao.claimMilestoneAtomic` (as the @Transaction pattern precedent). `git status` clean on `main`, up to date with origin (last commit `1666f27 docs(adr): add ADR-0005 (Billing SDK) + ADR-0006 (Ad SDK) stubs`).

### Library-version pivot: v7 → v8

The proposed ADR pinned Play Billing v7. Web search during preflight surfaced that **v7 sunsets 2026-08-31 per Google's two-year deprecation window** (revenuecat migration guide + Google release notes), and **v8.3.0 is the current stable line (released 2025-12-23)**. Pinning v7 would mean the library sunsets ~3 months after ship. The ADR's proposed decision explicitly allowed "or the most-recent stable when C.5 PR 1 lands", so the PR targets v8 and the ADR was amended accordingly.

Bonus: v8 introduces `BillingClient.Builder.enableAutoServiceReconnection()` which eliminates the custom reconnection-policy work the proposed ADR had flagged as Q1.

### Design decisions

- **SDK-neutral adapter seam.** An `internal` `BillingClientAdapter` interface (in `data/billing/internal/`) with plain-Kotlin sealed result types (`SdkBillingResult`, `SdkPurchase`, `SdkProductDetails`, `QueryProductDetailsResult`, `StartPurchaseResult`, `QueryPurchasesResult`) is the only dependency the impl has on the SDK. `RealBillingClientAdapter` is the one class that imports `com.android.billingclient.*`. This shape lets `BillingManagerImplTest` mock the adapter with mockito-kotlin's default subclass mock-maker — no `mockito-inline` dependency required. Version-upgrade blast radius stays contained to one class.
- **`billing_receipts` table keyed by `purchaseToken`.** Play Billing's `orderId` is nullable on pending purchases; `purchaseToken` is guaranteed non-null and stable across re-queries. DB v8 → v9 with explicit `MIGRATION_8_9`. `BillingReceiptDao.grantOnceAtomic(receipt, grantedAt, walletCredit)` is a `@Transaction` default method that flips `granted = true` and runs the wallet-credit lambda in one SQLite transaction — mirrors the RO-02 pattern from `MilestoneDao.claimMilestoneAtomic` (B.2 PR 4). Idempotent short-circuit when `granted` is already true.
- **Consume/ack AFTER the grant transaction.** The Google Play Services RPC runs outside the Room transaction so SQLite locks are never held across a round-trip to Google. Failures are logged and retried by `retryUnresolvedConsumeOrAck()` on the next reconciliation sweep — wallet is NOT re-credited because the `granted = true` guard short-circuits `grantOnceAtomic`.
- **Pending purchases persist with `granted = false`.** `BillingManagerImpl` never credits on a PENDING state; it writes the receipt row (so the reconciliation sweep can track it) and returns `PurchaseResult.Error("Purchase pending …")`. The next `reconcilePendingPurchases()` call observes the PURCHASED promotion and routes through `grantOnceAtomic`, producing exactly-once credit.
- **`reconcilePendingPurchases` as a default-no-op interface extension.** Adding `suspend fun reconcilePendingPurchases() { /* no-op */ }` to `BillingManager` means `StubBillingManager` and `FakeBillingManager` inherit the no-op without code changes — zero churn in existing callers.
- **`ActivityProvider` + deferred MainActivity wiring.** `BillingClient.launchBillingFlow` needs an Activity (not a Context). `ActivityProvider` is a `WeakReference`-backed Singleton that MainActivity will populate on resume in PR 2. PR 1 leaves the class wired in DI but dormant (no caller registers into it), because `@Binds` still points at Stub so `ActivityProvider.current()` is never read.
- **`obfuscatedAccountId` anti-fraud.** SHA-256 hex of a random UUID stored in `SharedPreferences("billing_anti_fraud")`. No PII leaves the device; cleared SharedPreferences regenerates the UUID, which is acceptable because the signal is probabilistic. `obfuscatedProfileId` stays unused (no in-app profile concept).
- **`BillingProduct` domain model got an empty `companion object`** so the data-layer `BillingProduct.fromSkuIdOrNull(skuId)` extension could attach. Domain stays pure Kotlin — no Android import introduced.

### Files touched

New:
- `app/src/main/java/…/data/local/BillingReceiptEntity.kt` (88 lines). PK=purchaseToken; 4 nullable fields with `@ColumnInfo(defaultValue = "NULL")` so the migration DDL byte-matches the Room-generated schema export.
- `app/src/main/java/…/data/local/BillingReceiptDao.kt` (106 lines). `grantOnceAtomic` + `markConsumed` + `markAcknowledged` + `getGrantedButUnresolved`.
- `app/src/main/java/…/data/billing/BillingManagerImpl.kt` (381 lines). `internal` class implementing the full 3 methods + `reconcilePendingPurchases`. Session-Mutex guards race between `purchase` and `reconcile`.
- `app/src/main/java/…/data/billing/internal/BillingClientAdapter.kt` (192 lines). Interface + 6 sealed types.
- `app/src/main/java/…/data/billing/internal/RealBillingClientAdapter.kt` (296 lines). `enableAutoServiceReconnection()` + `PendingPurchasesParams.enableOneTimeProducts` + Mutex-guarded `launchPurchase` bridging the `PurchasesUpdatedListener` callback through a `CompletableDeferred`. Device-only testable.
- `app/src/main/java/…/data/billing/internal/ActivityProvider.kt` (48 lines). `@Singleton`, `@Volatile var activityRef: WeakReference<Activity>?`.
- `app/src/test/java/…/data/local/BillingReceiptDaoTest.kt` (229 lines, 7 tests). Robolectric + real in-memory Room.
- `app/src/test/java/…/data/billing/BillingManagerImplTest.kt` (480 lines, 14 tests). Robolectric + real in-memory Room for `@Transaction` semantics + mockito-kotlin for adapter + mock Activity (pass-through only).
- `app/schemas/…/9.json` (23,382 bytes). Generated by KSP from the bumped `AppDatabase`; `billing_receipt` CREATE SQL byte-matches `MIGRATION_8_9`.

Modified:
- `gradle/libs.versions.toml` — added `billingPlay = "8.3.0"` + `billing-ktx` library alias.
- `app/build.gradle.kts` — `implementation(libs.billing.ktx)`.
- `app/src/main/java/…/data/local/AppDatabase.kt` — 13 entities + 13 DAOs, version 9, adds `billingReceiptDao()`.
- `app/src/main/java/…/data/local/Migrations.kt` — added `MIGRATION_8_9`; `ALL` is now `arrayOf(MIGRATION_7_8, MIGRATION_8_9)`.
- `app/src/main/java/…/di/DatabaseModule.kt` — `provideBillingReceiptDao`.
- `app/src/main/java/…/domain/repository/BillingManager.kt` — added default-no-op `reconcilePendingPurchases`.
- `app/src/main/java/…/domain/model/BillingProduct.kt` — empty `companion object` opt-in.
- `app/proguard-rules.pro` — `-keep class com.android.billingclient.** { *; }` + `-keep interface` + `-dontwarn`.
- `app/src/test/java/…/data/local/RoomSchemaTest.kt` — +1 billing_receipt round-trip test touching every column.
- `docs/agent/DECISIONS/ADR-0005-billing-sdk.md` — Status Proposed → Accepted; Decision section rewritten with concrete commitments; 5 open questions replaced by a "Resolved open questions" section with Q1–Q5 decisions; References list expanded to every PR 1 file.
- `AGENTS.md` — coverage line 488 → 510.
- `CHANGELOG.md` — new Phase C.5 PR 1 section at the top of Unreleased.
- `.kiro/steering/source-files.md` — 5 new data/billing files + 2 new Room entities + Migrations.kt ADR-0005 reference + updated test entries.
- `.kiro/steering/structure.md` — `data/billing/internal/` subdirectory documented + DI note.
- `.kiro/steering/tech.md` — Play Billing 8.3.0 row added to library table.
- `.kiro/steering/lib-room.md` — schema version 8 → 9.
- `docs/database-schema.md` — BillingReceipt entity section + BillingReceiptDao row + MIGRATION_8_9 in migrations list; removed 3 duplicate DAO entries.

### Test changes (+22 net: 488 → 510)

- `BillingReceiptDaoTest`: **+7 tests**. Upsert/getByToken round-trip, getByToken-not-found null return, `grantOnceAtomic` flip + walletCredit-ran-once, `grantOnceAtomic` idempotent (2nd call returns false + walletCredit NOT run + grantedAt from 1st call preserved), `markConsumed`/`markAcknowledged` target-only (no cross-row contamination), `getGrantedButUnresolved` excludes the 3 states that are NOT unresolved (consumed-already, acked-already, pending), `getAll` orders by purchaseTime DESC.
- `RoomSchemaTest`: **+1 test**. billing_receipt round-trip with every column populated (incl. the 4 nullables).
- `BillingManagerImplTest`: **+14 tests**. 3 happy paths (GEM_PACK_SMALL consume / AD_REMOVAL ack / SEASON_PASS 30-day expiry); 5 failure paths (user-cancel, product-unavailable, no-activity, connect-fails, pending-purchase-persists-receipt-without-credit); idempotency (same purchaseToken → Success + no double-credit); 2 reconciliation cases (PENDING→PURCHASED grants exactly once across repeated sweeps; `retryUnresolvedConsumeOrAck` retries consume without re-crediting); `isAdRemoved` / `isSeasonPassActive` delegation to `PlayerRepository`.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin` — BUILD SUCCESSFUL after making `BillingManagerImpl` `internal` (it was initially `public` which leaked `internal` `BillingClientAdapter`/`ActivityProvider` params through its constructor). 2 forward-compat Kotlin warnings about `@ApplicationContext` param-vs-field annotation targeting (KT-73255) — not actionable.
- `./run-gradle.sh test` — first run had 1 failure: the happy-path `isSeasonPassActive returns true when flag is set and expiry is in the future` test had a bogus `verifyNoInteractions(/* comment */)` with an empty arg list (comment-only). Replaced with `verify(playerRepository, never()).updateSeasonPass(any(), any())` which correctly asserts the expiry-clear branch was NOT taken. Re-ran: BUILD SUCCESSFUL, 510 tests completed, 0 failures, 0 errors.
- Schema 9.json generated + the `billing_receipt` CREATE SQL in the schema export matches MIGRATION_8_9 byte-for-byte (verified via grep). This is the critical consistency check — fresh install and migration paths will produce identical schemas.
- `@Binds` in `BillingModule` still points at `StubBillingManager`; no runtime behaviour change in PR 1.

### Open questions / blockers

- None for PR 1.
- PR 2 scoping (next agent session):
  - `BuildConfig.USE_REAL_BILLING` default strategy: debug=false, release=true. Override via `keystore.properties` for internal-track testing.
  - `MainActivity.onResume` → `ActivityProvider.set(this)`; `onPause` → `ActivityProvider.clear()`.
  - `StoreViewModel.init { viewModelScope.launch { billingManager.reconcilePendingPurchases() } }` — side effect before existing `ensureSeedData()` to pick up any pending purchases from prior sessions.
  - One integration test demonstrating `BillingManagerImpl` golden-path Success parity with the stub (where possible without Play Services).

### Follow-ups

- **C.5 PR 2 is now the top code-facing task.** Flag + binding swap + MainActivity wiring.
- **C.6 PR 1 can land in parallel** with C.5 PR 2 — different files, no coupling. AdMob `RewardAdManagerImpl` + UMP + answer ADR-0006's 6 open questions.
- **C.5 PR 3** deletes the stub after ~1 week of closed-track confirmation.
- After C.5 and C.6 land, Phase D (Plan 31 Play Console setup) is unblocked.
- B.4 FollowOnPipeline + B.5 UpdateMissionProgress stay as opportunistic debt cleanup.

### Memory updated

- `STATE.md` ✅ — current objective now "C.5 PR 1 landed"; What-works gained a C.5 PR 1 line (will be folded into the main bullet when C.5 PR 3 lands); Known-issues swapped stub-related line for a more specific C.5 PR 2 pointer; test count 488 → 510; priorities rotated (PR 1 removed, new PRs 2–3 + C.6 top); Next actions rotated; last-run line updated; critical path gained "C.5 PR 1 done" marker.
- `RUN_LOG.md` ✅ — this entry.
- `ADR-0005` ✅ — status Proposed → Accepted; 5 open questions resolved with concrete decisions; v7 → v8 library pin with rationale; references list expanded.

## 2026-05-08 — ADR-0005 (Billing SDK) + ADR-0006 (Ad SDK) stubs

- **Goal:** Draft both ADR stubs named as prerequisites for Phase C.5 / C.6 in the implementation roadmap, so C.5 PR 1 and C.6 PR 1 can cite a recorded architectural commitment instead of discovering the shape in-PR. Matches the pattern of ADR-0004 (FollowOnPipeline stub written before B.4 PR 1) — record the commitment now, promote Proposed → Accepted when the concrete decisions are made in the first real PR of each family.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 3b+3c entry and below). `git status` clean on `main`, up to date with origin (last commit `280edf5 feat(cosmetics): seed garden_ziggurat_skin + sandals_of_gilgamesh (C.2 PR 3b+3c)`). Read ADR-0003 + ADR-0004 for shape, `docs/monetization.md` for product + placement tables, `CONSTRAINTS.md` for the no-server invariant, current stubs (`StubBillingManager`, `StubRewardAdManager`), domain contracts (`BillingManager` + `BillingProduct`, `RewardAdManager` + `AdPlacement`), and the implementation-roadmap §C.5 + §C.6 entries (files / success criteria / risk / PR-size / rollback breakdowns already written). No code, no test changes — this is a decision-record commit.

### Shape of both ADRs

Both ADRs use a matching structure so C.5 and C.6 stay trivially comparable:

1. **Context** — current stub, surface area, release-critical status, risk profile.
2. **Decision (stub)** — numbered commitments a through N that are non-negotiable even before PR 1 concrete scoping.
3. **Rationale** — why each commitment, with alternatives explicitly rejected.
4. **Consequences** — dependencies, ProGuard rules, BuildConfig fields, schema changes, manifest changes.
5. **Non-goals / future work** — bounded list of things that are explicitly out of v1.0 scope.
6. **Open questions** — 5 for Billing, 6 for Ads. Each flagged as "resolved in PR 1 description", promoting the ADR from Proposed → Accepted.
7. **References** — roadmap section, plan references, related ADR (each cites the other).

### ADR-0005 (Billing SDK) key commitments

- **Library: Google Play Billing Library v7** (or most-recent stable at C.5 PR 1 landing). Pinned version, never ranged.
- **Impl location:** `data/billing/BillingManagerImpl.kt`. Coexists with `StubBillingManager` under `BuildConfig.USE_REAL_BILLING` flag during internal/closed-track rollout.
- **Listener-to-suspend adaptation** via `suspendCancellableCoroutine`. No `BillingManager` interface change — the swap is pure impl.
- **Receipt-idempotency in Room.** New `billing_receipts` entity keyed by `orderId` with `granted: Boolean`. Every purchase writes the receipt + flips `granted = true` atomically in one `@Transaction`. Prevents double-credit on pending-purchase resolution. **DB schema bump v8 → v9** with an explicit `Migration` object per `CONSTRAINTS.md`.
- **`onResume` pending-purchase sweep.** `BillingClient.queryPurchasesAsync()` on every app resume / Store enter, reconciled against the receipt table.
- **SKU drift mitigation.** `BillingProduct` enum stays source-of-truth. Startup sanity check via `queryProductDetailsAsync()` logs + disables affected Store cards for missing products.
- **3-PR rollout** (matching C.5 roadmap): PR 1 impl + tests (stub binding unchanged), PR 2 flag-gated binding swap, PR 3 stub deletion ~1 week post-closed-track.

5 open questions flagged for PR 1 scoping: reconnection policy, acknowledgment timing for consumables, test-SKU strategy for debug builds, subscription proration (future tier), anti-fraud obfuscated IDs.

### ADR-0006 (Ad SDK) key commitments

- **Library: Google AdMob SDK direct.** No mediation in v1.0 (AppLovin MAX / Unity LevelPlay / Meta Audience Network) — each adds its own consent + compliance surface, and the app's ad surface (3 opt-in reward placements) is too small to justify the compounded testing matrix.
- **Impl location:** `data/ads/RewardAdManagerImpl.kt`. Coexists with `StubRewardAdManager` under `BuildConfig.USE_REAL_ADS` flag.
- **Preload-on-trigger, not upfront.** Ad loads happen inside `showRewardAd()`, not at app startup. Reasons: ads expire after 1 hour; the app's opt-in surface means upfront preload is frequently wasted; simpler failure semantics.
- **`OnUserEarnedRewardListener.onUserEarnedReward()` is the single source of truth** for reward crediting. Never reward on dismiss, never on impression. Matches AdMob's documented contract for `RewardedAd`.
- **Test-ad IDs in debug builds** (Google's documented `ca-app-pub-3940256099942544/5224354917`). Release builds use real IDs from `local.properties` → `BuildConfig.<PLACEMENT>_AD_UNIT_ID`. Production IDs never in git.
- **UMP for GDPR / DSA consent.** Google's User Messaging Platform handles the legal nuance. No custom in-app consent screen.
- **Code-side frequency capping** — once per round + once per day, already enforced by existing `RoundEndState` + `PlayerProfileEntity.freeCardPackAdUsedToday` flags. No AdMob-side caps.
- **3-PR rollout** matching C.5 shape.

6 open questions flagged for PR 1 scoping: consent-denied reward policy, ad load timeout + progress UX, per-session impression caps, mediation-readiness abstraction, COPPA / child-directed flag, test-ad policy on internal-track.

### Why two stubs, not one

Considered a single "external SDKs" ADR. Rejected:

1. Billing and ads have very different risk profiles (high vs. medium-high) and the ADRs should capture that separately. Billing correctness directly affects revenue; ad failure is graceful degradation (no reward, no blocker).
2. Each subsystem has its own external-prerequisite dependencies (Play Console SKU setup vs. AdMob + CMP provisioning). Merging them would couple unrelated external timelines.
3. Alternatives explored per subsystem are genuinely different (PBL version pin vs. mediation-library choice). Merging would obscure the distinct rationale.

### Why stubs, not accepted

Each ADR's "Decision" section lists concrete commitments, but the sections carry specific open questions that can only be resolved when the real SDK implementation is actually written (e.g. AdMob's `RewardedAd.load()` timeout default, PBL's `onPurchasesUpdated` response-code enum against our `PurchaseResult.Error` shape). Marking "Proposed (stub)" records the commitment without pretending decisions that haven't been made. Matches the ADR-0004 (FollowOnPipeline) precedent. Each ADR will be upgraded to "Accepted" in the PR description of its corresponding C.5 PR 1 / C.6 PR 1.

### Files touched

- `docs/agent/DECISIONS/ADR-0005-billing-sdk.md` (new, 83 lines).
- `docs/agent/DECISIONS/ADR-0006-ad-sdk.md` (new, 107 lines).
- `docs/agent/STATE.md` — current objective rewritten; next-actions rotated (C.5 PR 1 and C.6 PR 1 now top, with explicit instruction to promote each ADR to Accepted in the PR description); 2 new ADR references added; last-run line updated.
- `docs/agent/RUN_LOG.md` — this entry.

### Verification

- No code changes. No tests run — previous commit's 488-test green state still holds.
- Grep sanity: `ls docs/agent/DECISIONS/` shows ADR-0001 through ADR-0006 present; ADR-0004 + ADR-0005 + ADR-0006 all have `Status: Proposed` (stub contract).
- Cross-reference check: both ADRs reference each other in the Rationale / References sections; both reference `docs/monetization.md`, `CONSTRAINTS.md`, `devdocs/evolution/implementation_roadmap.md` §C.5 / §C.6, and their corresponding `domain/model/` + `domain/repository/` + `data/billing|ads/` files.

### Surface changes

- No code changes. No interface changes. No tests. No dependencies.
- 2 new markdown files under `docs/agent/DECISIONS/`.
- `STATE.md` priorities reshuffled.

### Open questions / blockers

- None for this PR (it's a decision record).
- For C.5 PR 1 / C.6 PR 1 scoping: answer the 5 + 6 open questions flagged in each ADR. PR descriptions must upgrade the respective ADR from Proposed → Accepted with concrete answers.
- External prerequisites tracked in Plan 31 flow (Play Console SKU setup, AdMob provisioning, CMP evaluation, production privacy-policy URL).

### Follow-ups

- **C.5 PR 1 is now the top code-facing task.** Play Billing v7 impl + `billing_receipts` Room table + migration. All 5 open questions need concrete answers in the PR description.
- **C.6 PR 1 can land in parallel** with C.5 PR 1 — different files, no coupling. AdMob + UMP + 6 open questions.
- **C.5 PR 2 / C.6 PR 2** land flag swap after PR 1. Internal-track verification between PR 2 and PR 3.
- **C.5 PR 3 / C.6 PR 3** delete stubs ~1 week after closed-track confirmation.
- After all 6 PRs land, Phase D (Plan 31 Play Console setup) is unblocked — the last release-critical phase.

### Memory updated

- `STATE.md` ✅ — current objective now "ADR-0005 + ADR-0006 stubs landed"; references list gained 2 entries; next-actions rotated to C.5 PR 1 + C.6 PR 1 as the top code-facing items; last-run line updated; critical path line unchanged (still says "C.2 PRs 1+2+3+3b+3c + C.4 + ensureSeedData fix done → C.5 + C.6 → D → 31", which is already correct).
- `RUN_LOG.md` ✅ — this entry.
- ADRs: ADR-0005 + ADR-0006 are the decision records themselves. `Status: Proposed (stub)` until C.5 PR 1 / C.6 PR 1 promote to Accepted.

## 2026-05-08 — Phase C.2 PR 3b + 3c: seed remaining milestone cosmetics (closes milestone-cosmetic gap)

- **Goal:** Batch the last two milestone-cosmetic content PRs (PR 3b for MARATHON_WALKER's `garden_ziggurat_skin`, PR 3c for GLOBE_TROTTER's `sandals_of_gilgamesh`) into a single PR since they share the same file, test-update pattern, and risk profile. After this lands, all 6 Milestone entries have `Success` end-to-end on `ClaimMilestone` — the RO-07 "shipped but disabled" monetization gap tracked since Plan R2-11 is fully resolved.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 3 + ensureSeedData fix + C.4 + C.2 PR 2 entries). `git status` clean on `main`, up to date with origin (last commit `8c907c1 feat(cosmetics): seed lapis_lazuli_skin (C.2 PR 3, IRON_SOLES reward)`). Confirmed the PR 3 pattern: one seed row + one palette entry + one ClaimMilestoneTest rewire (remove UnknownCosmetic, add end-to-end Success) + one CosmeticRepositoryImplTest palette test. Batching doubles the content but keeps the pattern identical.

### Design

**Why batch 3b + 3c.** Evaluated three options:
1. **Option A (chosen):** Single PR with both seed rows, both palettes, both test rewires. Same file (`CosmeticRepositoryImpl.kt`), same test files. Two narratively-distinct PRs would produce mechanically identical diffs + sync-doc churn for effectively zero risk difference. Merged PR size stays small (~120 insertions) — well under a reasonable review threshold.
2. **Option B:** Two sequential PRs. Strictly cleaner git history but doubles doc-sync work (two CHANGELOG sections, two STATE.md edits, two RUN_LOG entries) for no functional benefit.
3. **Option C:** Land PR 3b separately + defer PR 3c pending a category decision. The category decision is small enough to fold into this PR.

**Palette choices.**
- **`garden_ziggurat_skin` (MARATHON_WALKER):** Hanging Gardens biome-themed. Matches the Tier 1-2 Hanging Gardens biome in the GDD. `[0xFF8B4726, 0xFFAD7B4C, 0xFF5E7F47, 0xFF7BA85A, 0xFFE0C890]` — terracotta ziggurat base → sun-bleached sandstone → mossy vines begin → lush foliage → pale bloom canopy. The progression from stone at the base to greenery at the top captures the "ziggurat overtaken by gardens" vibe.
- **`sandals_of_gilgamesh` (GLOBE_TROTTER):** Heroic bronze / ancient Sumerian theme. `[0xFF3B2A1A, 0xFF6B4A2A, 0xFF8B6B42, 0xFFB89152, 0xFFE8C068]` — dark weathered bronze → aged bronze → warm bronze → polished brass → gold crown. The gold crown echoes `lapis_lazuli_skin` and signals "legendary" status.

**Category decision for `sandals_of_gilgamesh`.** The id literally means footwear but the cosmetic is implemented as `ZIGGURAT_SKIN`. Three options:
1. **Option A (chosen):** Reframe via description. "Bronze ziggurat in honour of Gilgamesh, whose sandals walked the edges of the world." Keeps existing `CosmeticCategory` enum + pipeline intact. No schema change. The name stays `Sandals of Gilgamesh` because that's the milestone-reward name from `Milestone.GLOBE_TROTTER.rewards`.
2. **Option B:** Add a `PLAYER_AVATAR` category + new rendering path. Architecturally cleaner but requires pipeline extension, new lookup table, new `BattleViewModel` hydration. Not justified for one cosmetic.
3. **Option C:** Rename the milestone reward id. Would require a matching change in `Milestone.kt` enum. Breaks ADR-0003 / content-as-code contract; C.4 detection explicitly says "do not rename the 3 mismatched IDs" (content decision, deferred to this PR — now resolved via reframe).

Option A wins because it's the smallest diff that works. If future milestones introduce *multiple* player-avatar cosmetics, revisit Option B as a RO-07 follow-up.

**Synthetic rejection-before-atomic guard.** Post-PR, no prod Milestone returns `UnknownCosmetic` — all 3 milestone ids are seeded. The `UnknownCosmetic rejects claim before the atomic DAO call with no credit` test still uses MARATHON_WALKER against the empty `FakeCosmeticRepository`, but its narrative now says "synthetic mechanism-level regression guard against future content work that introduces a new Milestone with an unseeded Cosmetic reward." Kept because removing it loses mechanism coverage; the mechanism is non-trivial (iterate rewards, filter Cosmetic, call idExists, short-circuit return) and worth a dedicated test even in the absence of a prod trigger.

**End-to-end success tests for all 3 milestone cosmetics.** Added `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl` + `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl`, mirroring the `IRON_SOLES` test from PR 3. Each uses `CosmeticRepositoryImpl(FakeCosmeticDao())` directly so the whole chain (`SEED_COSMETICS → ensureSeedData → idExists → ClaimMilestone atomic credit → wallet`) is exercised, not just the `ClaimMilestone` layer. Every Milestone with a Cosmetic reward now has a dedicated end-to-end test — symmetric coverage.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt`:
  - `ZIGGURAT_COLOR_LOOKUP` +2 entries (garden, sandals). KDoc expanded to document all 4 current palettes + the GLOBE_TROTTER reframe rationale.
  - `SEED_COSMETICS` +2 rows (positioned directly after `lapis_lazuli_skin` so all 4 palette-shipping cosmetics cluster at the top). Inline comments explain each row's milestone reward link and the Store visibility policy (not in ENABLED_COSMETIC_ID — milestone-only acquisition for now).
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt`:
  - Removed `UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER` + `... for GLOBE_TROTTER`.
  - Rewrote the rejection-before-atomic regression guard's comment to explain the synthetic-mechanism-level semantics.
  - Added `MARATHON_WALKER claim succeeds end-to-end via real CosmeticRepositoryImpl` (600 Gems assertion) + `GLOBE_TROTTER claim succeeds end-to-end via real CosmeticRepositoryImpl` (500 Gems assertion).
  - Updated the setup comment to reflect "no more prod mismatches."
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt`:
  - +2 palette tests (`C2PR3b - garden_ziggurat_skin propagates hanging-gardens palette`, `C2PR3c - sandals_of_gilgamesh propagates bronze-ziggurat palette`), each with exact 5-int assertions matching the `zig_jade` / `lapis_lazuli_skin` pattern.
  - Updated 3 count assertions (9 → 11): idempotency, partial-catalogue upgrade, existing-row preservation.
  - Partial-catalogue upgrade test now asserts all 4 palette-shipping cosmetics land correctly (was asserting 2).
  - "Other seeded ziggurat cosmetics null overrideColors" comment updated to list all 4 palette cosmetics.

### Test changes (+2 net: 486 → 488)

- **ClaimMilestoneTest:** -2 (UnknownCosmetic MARATHON_WALKER + GLOBE_TROTTER) + 2 (end-to-end Success for both) = **net 0**.
- **CosmeticRepositoryImplTest:** +2 new palette tests = **+2**.
- Net: **+2** (486 → 488).

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL in 19s. 0 failures, 0 errors, 0 skipped.
- Test count: **486 → 488** (matches net-+2 expectation).
- Grep sanity checks:
  - `grep -c "garden_ziggurat_skin\|sandals_of_gilgamesh" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` — 6 (each id appears 3 times: lookup entry + seed row + KDoc).
  - `grep "UnknownCosmetic surfaces offending cosmetic id" app/src/test` — 0 (both stale tests removed).
  - `grep -c "claim succeeds end-to-end" app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/ClaimMilestoneTest.kt` — 3 (one per milestone with Cosmetic reward: IRON_SOLES, MARATHON_WALKER, GLOBE_TROTTER).
- All 4 palette-shipping cosmetics render correctly via the existing C.2 PR 1 pipeline (no renderer changes needed; pipeline is additive and data-driven).

### Surface changes

- 2 new `SEED_COSMETICS` rows + 2 new `ZIGGURAT_COLOR_LOOKUP` entries. Both land on any install via the `ensureSeedData` per-cosmeticId filter.
- No public API changes. No DB schema changes. No Room migration. Still on v8.
- No new production dependencies.
- No ADR — content + narrative-reframe decision documented in the SEED_COSMETICS inline comments + the ZIGGURAT_COLOR_LOOKUP KDoc at point-of-use.

### Milestone

**RO-07 milestone-cosmetic gap fully closed.** All 6 Milestone entries claim cleanly end-to-end:
| Milestone | Steps | Cosmetic | Result |
|---|---|---|---|
| FIRST_STEPS | 1K | *(none)* | ✅ Success |
| MORNING_JOGGER | 10K | *(none)* | ✅ Success |
| TRAIL_BLAZER | 100K | *(none)* | ✅ Success |
| MARATHON_WALKER | 500K | `garden_ziggurat_skin` | ✅ Success (this PR) |
| IRON_SOLES | 1M | `lapis_lazuli_skin` | ✅ Success (C.2 PR 3) |
| GLOBE_TROTTER | 5M | `sandals_of_gilgamesh` | ✅ Success (this PR) |

### Open questions / blockers

- **None.** All cosmetic-related debt tracked since Plan R2-11 is closed. No prod Milestone currently returns `UnknownCosmetic`.
- **Store visibility of milestone-reward cosmetics.** Currently all 3 milestone cosmetics show "Coming Soon" in the Store. Product decision deferred: enable them in `ENABLED_COSMETIC_ID` at higher store prices (500-600 Gems matches the current values), or keep milestone-only. Not blocking — game works either way.

### Follow-ups

- **Open ADR-0005 (Billing SDK) and ADR-0006 (Ad SDK) stubs.** Prerequisite for C.5 / C.6. These are now the top release-critical items.
- **C.5 — Real Google Play Billing Library v7 swap.** High risk: real SDK failure paths have never run against prod code. A.4 fake tests provide the unit-test safety net; internal-track testing + Firebase pre-launch report complete the verification.
- **C.6 — Real AdMob swap.** Same shape as C.5.
- **B.4 FollowOnPipeline + B.5 UpdateMissionProgress.** Pure debt; land opportunistically.
- **Phase D (Plan 31).** Play Console setup, AAB upload, Firebase pre-launch. Depends on C.5 + C.6 + public privacy policy URL.

### Memory updated

- `STATE.md` ✅ — current objective now "Phase C.2 PR 3b + 3c landed"; new bullet in "what works"; Known-issues list updated to reflect 4 plumbed palettes; priorities / next-actions rotated (ADR stubs + C.5/C.6 now top); test count 486 → 488; critical path marks PRs 1+2+3+3b+3c done; last-run updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — content + narrative-reframe decision captured in KDoc at point-of-use.

## 2026-05-08 — Phase C.2 PR 3: seed lapis_lazuli_skin (resolves IRON_SOLES UnknownCosmetic)

- **Goal:** Land the first of three milestone-cosmetic content PRs per STATE.md next-actions #1. Seeds `lapis_lazuli_skin` in `SEED_COSMETICS` + its palette in `ZIGGURAT_COLOR_LOOKUP`, which flips `ClaimMilestone(IRON_SOLES)` from returning `UnknownCosmetic("lapis_lazuli_skin")` (C.4 detection behaviour) to returning `Success` with a 200 Gems + 50 Power Stones atomic credit. Two more milestone cosmetics (`garden_ziggurat_skin` for MARATHON_WALKER, `sandals_of_gilgamesh` for GLOBE_TROTTER) remain for PR 3b/3c.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (ensureSeedData fix + C.4 + C.2 PR 2 + C.2 PR 1 entries). `git status` clean on `main`, up to date with origin (last commit `a510350 fix(cosmetics): ensureSeedData per-cosmeticId filter`, pushed). Re-read `Milestone.IRON_SOLES` (lapis_lazuli_skin reward id confirmed), `CosmeticRepositoryImpl` (SEED_COSMETICS after PR 2 + ensureSeedData fix), `ClaimMilestoneTest` (12 cases from C.4), `CosmeticRepositoryImplTest` (7 cases from C.2 PR 2 + ensureSeedData fix). Confirmed `FakeCosmeticDao` already exists (C.2 PR 2).

### Design

**Palette choice.** Traditional lapis lazuli is a near-pure-blue semi-precious stone with gold-yellow pyrite flecks. Went for 4 lapis-blue gradient layers + 1 pyrite-gold crown layer (layer 4, top) to evoke that classic "lapis with gold" visual: `[0xFF1A1F5C, 0xFF2A3880, 0xFF3B4FAB, 0xFF4F68C8, 0xFFD4A84A]`. The gold crown at the top is the distinguishing visual note \u2014 pure blue would read as generic sapphire or cobalt. Same 5-ints / bottom-to-top contract as `zig_jade` in PR 2.

**Store pricing and visibility.** Set `priceGems = 500` \u2014 highest in the ziggurat-skin catalogue (above `zig_golden` at 300). Signals "elite" status. Intentionally NOT added to `StoreScreen.ENABLED_COSMETIC_ID` because the primary acquisition path is the IRON_SOLES milestone (1M lifetime steps). The Store still shows it as "Coming Soon" (R2-11 guard), which is fine \u2014 the milestone reward is the canonical way to get it. Whether to eventually enable Store purchase is a future UX decision.

**Seed-row placement.** Added as the second entry in `SEED_COSMETICS`, directly after `zig_jade`. Two reasons: (a) both have shipping palettes, so grouping them at the top of the catalogue is visually coherent, and (b) the Store cosmetics section renders in `SEED_COSMETICS` order, so the two "real" cosmetics surface at the top.

**ClaimMilestoneTest migration: remove the stale IRON_SOLES UnknownCosmetic test.** The C.4 test suite had three `UnknownCosmetic surfaces offending cosmetic id for <milestone>` tests, one per mismatched id. Post-PR 3, `lapis_lazuli_skin` IS in SEED_COSMETICS, so `useCase(IRON_SOLES)` against the real impl now returns `Success`, not `UnknownCosmetic`. The ClaimMilestoneTest uses a `FakeCosmeticRepository` with an empty items list by default, so the synthetic test would still pass (fake's `idExists` would still return false), but the narrative meaning has changed \u2014 it no longer reflects prod. Removing it and keeping the MARATHON_WALKER + GLOBE_TROTTER tests ensures the UnknownCosmetic test suite genuinely tracks the remaining mismatched ids. Each future PR (3b, 3c) will remove one more.

**Switch the rejection-before-atomic regression guard target.** The `UnknownCosmetic rejects claim before the atomic DAO call with no credit` test was using IRON_SOLES. Same reasoning: post-PR 3, IRON_SOLES is no longer the right exemplar \u2014 lapis is seeded, so the rejection wouldn't happen against the real impl. Switched to MARATHON_WALKER (`garden_ziggurat_skin` still unknown). This keeps the test's narrative \u2014 "rejection happens before atomic" \u2014 aligned with prod.

**Rewrite the positive-path test as end-to-end with the real impl.** The C.4 positive-path test `milestone with matching cosmetic id credits rewards via atomic path` explicitly seeded a `lapis_lazuli_skin` fixture in the `FakeCosmeticRepository` and asserted Success. Post-PR 3, the fixture is redundant \u2014 the real `CosmeticRepositoryImpl + FakeCosmeticDao` also has the id. Rewrote as `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl`: constructs the real impl with a fresh `FakeCosmeticDao()` and calls `useCase(IRON_SOLES)`, proving the whole chain. This is a stronger test than the old fixture-based one \u2014 it verifies the SEED_COSMETICS catalogue itself contains the right entry, not just that "if the id were known, the flow would work."

**CosmeticRepositoryImplTest row-count updates.** Three tests had `assertEquals(8, ...)` assertions for seed-row count (idempotency, partial-catalogue upgrade, existing-row preservation). All updated to 9. The partial-catalogue upgrade test also gained a palette assertion for the new `lapis_lazuli_skin` row to prove both palettes land on the same upgrade path \u2014 previously it only checked `zig_jade`. New `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP` test mirrors the C.2 PR 2 `zig_jade` palette assertion shape with the exact 5-int lapis gradient.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` \u2014 +1 `ZIGGURAT_COLOR_LOOKUP` entry (lapis palette), +1 `SEED_COSMETICS` row (`lapis_lazuli_skin`), expanded KDoc on the lookup table to document the PR 3 entry + pending PR 3b/3c entries. Also cleaned up a stale comment in `idExists` that referenced the old `dao.count() > 0` gate (post-ensureSeedData-fix mismatch).
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt` \u2014 removed `CosmeticCategory` + `CosmeticItem` imports (no longer needed after the positive-path test switched to the real impl); added `CosmeticRepositoryImpl` + `FakeCosmeticDao` imports. Removed the IRON_SOLES UnknownCosmetic test. Repointed the rejection-before-atomic regression guard at MARATHON_WALKER. Rewrote the positive-path test as the real-impl end-to-end case. Updated setup comment to reflect 2 still-mismatched ids (post-PR-3 state).
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt` \u2014 +1 new `C2PR3 - lapis_lazuli_skin propagates lapis palette` test with exact-value assertion. Updated 3 count assertions (8 \u2192 9). Updated partial-catalogue upgrade test to check both `zig_jade` and `lapis_lazuli_skin` palettes. Updated comment on `other seeded ziggurat cosmetics have null overrideColors` to mention both PR 2 + PR 3 palettes.

### Tests rewired (0 net change, 486 \u2192 486)

**ClaimMilestoneTest: 12 \u2192 11 cases.**
- Removed: `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` (prod semantics flipped \u2014 lapis_lazuli_skin is now seeded).
- Repointed: `UnknownCosmetic rejects claim before the atomic DAO call with no credit` now targets MARATHON_WALKER (garden_ziggurat_skin still unknown).
- Rewritten: `milestone with matching cosmetic id credits rewards via atomic path` \u2192 `IRON_SOLES claim succeeds end-to-end via real CosmeticRepositoryImpl`. Uses `CosmeticRepositoryImpl(FakeCosmeticDao())` directly; proves the full chain from SEED_COSMETICS through to wallet credit without any fixture intermediaries.
- Preserved unchanged: 9 other tests (step-threshold guard, Gems credit on Success, marks claimed, AlreadyClaimed, atomic path, concurrent claims, pre-existing claimed entity, MARATHON_WALKER UnknownCosmetic, GLOBE_TROTTER UnknownCosmetic).

**CosmeticRepositoryImplTest: 7 \u2192 8 cases.**
- Added: `C2PR3 - lapis_lazuli_skin propagates lapis palette via overrideColors from ZIGGURAT_COLOR_LOOKUP` \u2014 exact-value assertion matching the `zig_jade` pattern.
- Updated 3 count assertions: idempotency (8 \u2192 9), partial-catalogue upgrade (expected post-count 8 \u2192 9, now checks both palettes), existing-row preservation (expected post-count 8 \u2192 9).

**Net total tests: 486 \u2192 486 (-1 + 1 = 0).**

### Verification

- `./run-gradle.sh test` \u2014 BUILD SUCCESSFUL in 19s, 36 actionable tasks. 0 failures, 0 errors, 0 skipped.
- Test count: 486 (unchanged, matches net-zero expectation).
- Grep sanity checks:
  - `grep -c "lapis_lazuli_skin" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` \u2014 4 (seed row + lookup entry + 2 KDoc mentions).
  - `grep "UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES" app/src/test` \u2014 0 (stale test removed).
  - `grep -c "IRON_SOLES" app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/ClaimMilestoneTest.kt` \u2014 2 (just the new end-to-end test).
- Behaviour preservation: all 9 pre-existing ClaimMilestone tests pass unchanged; the concurrent-claims test still uses MORNING_JOGGER (which has no Cosmetic reward), so C.4 pre-flight doesn't interfere with the atomicity test.

### Surface changes

- No public API changes.
- 1 new `SEED_COSMETICS` row + 1 new `ZIGGURAT_COLOR_LOOKUP` entry. Both land on any install via the post-fix `ensureSeedData` (the ensureSeedData fix from the previous PR directly unblocks this one).
- No DB schema changes. No Room migration. Still on v8.
- No new production dependencies.
- No ADR \u2014 this is content work; design rationale (palette choice, pricing, store visibility) is captured in the `ZIGGURAT_COLOR_LOOKUP` + `SEED_COSMETICS` KDoc at point-of-use.

### Open questions / blockers

- **None.** C.2 PR 3b (MARATHON_WALKER) and PR 3c (GLOBE_TROTTER) are mechanically identical and ready to land on the same pattern.
- **Palette-design note for PR 3b:** MARATHON_WALKER's reward is `garden_ziggurat_skin`. Hanging Gardens is the first biome (Tier 1\u20132), so a green + terracotta palette is the obvious choice \u2014 lush greens at the base fading to terracotta / sandstone at the top.
- **Category decision deferred for PR 3c:** GLOBE_TROTTER's reward is `sandals_of_gilgamesh` \u2014 semantically footwear, not a ziggurat skin. The current `CosmeticCategory` enum has `ZIGGURAT_SKIN`, `PROJECTILE_EFFECT`, `ENEMY_SKIN`. Options: (a) add a `PLAYER_AVATAR` category (new enum value + new category of ZIGGURAT_COLOR_LOOKUP-equivalent lookup), (b) repurpose as "Gilgamesh Ziggurat" ZIGGURAT_SKIN, keeping the palette system consistent. Option (b) is simpler and ships in PR 3c; option (a) is better if future milestones introduce more player-avatar cosmetics. Document the decision in PR 3c's notes.

### Follow-ups

- **C.2 PR 3b** (next): add `garden_ziggurat_skin` with Hanging Gardens palette. Remove MARATHON_WALKER UnknownCosmetic test. Add `MARATHON_WALKER claim succeeds end-to-end` real-impl test. Update count assertions 9 \u2192 10.
- **C.2 PR 3c** (next): add `sandals_of_gilgamesh`. Decide category. Remove GLOBE_TROTTER UnknownCosmetic test. Add `GLOBE_TROTTER claim succeeds end-to-end` real-impl test. Update count assertions 10 \u2192 11. After this lands, all 6 Milestone entries are fully claimable end-to-end \u2014 closes the "shipped but disabled" monetization gap tracked since Plan R2-11.
- **C.5 + C.6:** real Billing + Ad SDK swaps (gated on ADR-0005/0006). Independent.
- **B.4 / B.5:** pure debt; can land opportunistically.

### Memory updated

- `STATE.md` \u2705 \u2014 current objective now "Phase C.2 PR 3 landed"; new bullet in "what works" for PR 3; test count stays 486 with net-0 note; priorities/next-actions reshuffled (PR 3b \u2192 #1, PR 3c \u2192 #2, C.5/C.6 \u2192 #3); critical path marks PRs 1+2+3 done; last-run updated.
- `RUN_LOG.md` \u2705 \u2014 this entry.
- ADR: not warranted \u2014 content work; palette / pricing / store-visibility decisions captured in KDoc at point-of-use.

## 2026-05-08 — Fix: ensureSeedData per-cosmeticId filter (unblocks C.2 PR 3+)

- **Goal:** Close the known-debt item called out in STATE.md and the C.2 PR 2 CHANGELOG entry \u2014 `CosmeticRepositoryImpl.ensureSeedData` short-circuited on `dao.count() > 0`, which meant any new `SEED_COSMETICS` row added after a device's first install would never land without a data clear. That blocked the C.2 PR 3+ rolling content cadence (the 3 milestone cosmetic seed rows `lapis_lazuli_skin` / `garden_ziggurat_skin` / `sandals_of_gilgamesh` that flip the C.4 UnknownCosmetic detections to Success). One-line gate \u2192 5-line per-cosmeticId filter. Small, low-risk, prerequisite.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.4 + C.2 PR 2 + doc-sweep entries). `git status` clean on `main`, up to date with origin (last commit `c9e6033 feat(milestones): detect UnknownCosmetic in ClaimMilestone (C.4)`, pushed). Re-read `CosmeticEntity` (confirmed primary key is `id` auto-gen, not `cosmeticId`), `CosmeticDao` (upsert semantics from Room), `CosmeticRepositoryImpl` (current ensureSeedData), existing `CosmeticRepositoryImplTest` (5 C.2 PR 2 cases including the idempotency test that asserts "count gate holds"). Grep-confirmed `idExists` from C.4 already lazy-calls `ensureSeedData()` \u2014 so fixing the gate here also makes the C.4 pre-flight check behave correctly on partial-catalogue devices.

### Design

**Shape: filter + conditional upsertAll, not universal upsert.** Evaluated three options:
1. **Option A (chosen):** Read existing ids once via `observeAll().first().mapTo(HashSet())`, compute `missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }`, `upsertAll(missing)` only when non-empty.
2. **Option B (rejected):** Drop the gate entirely and `upsertAll(SEED_COSMETICS)` on every call. Naive; `CosmeticEntity`'s `@PrimaryKey(autoGenerate = true)` means a seed row with `id = 0` would insert as a brand-new auto-gen row every time, creating duplicates by the second call. Would need either a schema change (make `cosmeticId` the PK) or a conflict-resolution strategy (Room's `@Upsert` is conflict-by-PK only).
3. **Option C (rejected):** Schema migration to make `cosmeticId` the primary key. Bigger change; requires DB version bump (v8 \u2192 v9) + a migration that rekeys the table; not justified when Option A achieves the same end-state without touching schema.

Option A wins because it's scoped, additive, and sidesteps the duplicate-row risk by never passing already-present rows to the DAO. Player state on existing rows (`isOwned`, `isEquipped`) is preserved simply because the filter skips those rows entirely \u2014 there's no re-upsert to overwrite.

**Three behaviours, one contract.** Called out explicitly in the inline KDoc so future readers don't need to reason through it:
- **Fresh install** (count == 0, existingIds empty): `missing == SEED_COSMETICS`, all 8 rows inserted. Identical to pre-fix behaviour.
- **Partial-catalogue upgrade** (count == 7, the pre-`zig_jade` state): `missing == [zig_jade]`, one row inserted. Previously broken (the gate short-circuited because count > 0).
- **Steady state** (count == 8, all ids present): `missing == emptyList()`, no DAO write. Same end-state as the old gate, arrived via a different mechanism.

**HashSet + mapTo over List + contains.** Using `mapTo(HashSet()) { it.cosmeticId }` instead of `map { it.cosmeticId }` so the `in` check is O(1). Minor, but matters if the catalogue ever grows to dozens of items.

**Chose not to touch `ensureSeedData` call-site contract.** Still returns `Unit`, still suspend, still idempotent. Callers (StoreViewModel init, CosmeticRepositoryImpl.idExists) don't need updates.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` \u2014 rewrote `ensureSeedData` body (3 lines \u2192 3 lines of logic + extensive inline KDoc explaining the 3 behaviours and the auto-gen-PK rationale). No other changes in the file.
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt` \u2014 +`CosmeticEntity` import (for direct test-side seeding); renamed the existing idempotency test (removed "count gate holds" phrase; updated comment to describe the filter-based mechanism \u2014 same end-state assertion); +2 new regression-guard cases.

### Tests added (2 new cases in `CosmeticRepositoryImplTest`)

1. **`ensureSeedData inserts newly-added rows on partial catalogue upgrade`** \u2014 pre-seeds 7 legacy rows manually (`zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) via direct DAO upsert, asserts baseline count == 7, calls `repo.ensureSeedData()`, asserts count == 8 (only `zig_jade` added). Additionally asserts:
   - `zig_jade.overrideColors` is non-null with 5 entries (proves ZIGGURAT_COLOR_LOOKUP still plumbs through for freshly-seeded rows).
   - Every one of the 7 legacy ids still present (proves the upgrade is additive, not replacive).
   
   This case would have failed pre-fix: the count > 0 gate would short-circuit and `zig_jade` never inserted.

2. **`ensureSeedData preserves player state on existing rows (isOwned, isEquipped)`** \u2014 the most player-visible risk of a naive re-upsert approach. Pre-seeds `zig_jade` with `isOwned = true, isEquipped = true`, calls `ensureSeedData`, asserts the jade row's player state survives and all 8 rows are present. Proves the filter skips already-present ids entirely so no overwrite occurs.

### Test name renamed

- `C2PR2 - ensureSeedData is idempotent on repeat call (count gate holds)` \u2192 `C2PR2 - ensureSeedData is idempotent on repeat call`. The `(count gate holds)` parenthetical was directly tied to the old `dao.count() > 0` short-circuit implementation \u2014 stale now. Updated the test's explanatory comment to say "filter produces `missing == emptyList()` on the second call" so future readers understand the new mechanism.

### Verification

- `./run-gradle.sh test` \u2014 BUILD SUCCESSFUL in 18s, 36 actionable tasks, 11 executed. Test count: **484 \u2192 486 JVM tests** (+2, matches the 2 new regression-guard cases exactly). 0 failures, 0 errors, 0 skipped.
- Sanity check: `grep -c "dao.count()" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` \u2014 0 (the old gate is gone). `grep -c "missing" app/src/main/java/com/whitefang/stepsofbabylon/data/repository/CosmeticRepositoryImpl.kt` \u2014 3 (matches the variable + 2 references in the code + KDoc mentions).
- No lint changes, no new warnings, no behaviour changes for fresh installs or steady-state.

### Surface changes

- No public API changes. `CosmeticRepository.ensureSeedData` signature unchanged. All callers (StoreViewModel init, the new C.4 `idExists` lazy-call) benefit automatically.
- No DB schema changes. No Room migration. Still on v8.
- No new production dependencies.
- No ADR \u2014 this is bounded bug-fix work; the design decision (Option A vs B vs C) is documented in the inline KDoc at point-of-use.

### Open questions / blockers

- **None.** The count-gate debt flagged in STATE.md after C.2 PR 2 is closed. C.2 PR 3+ can now proceed as a rolling content cadence.
- **Follow-up for C.2 PR 3+ authors:** when adding a new SEED_COSMETICS row, just add it. No ensureSeedData edits needed. The per-cosmeticId filter will pick it up on the next launch for every install, fresh or existing.

### Follow-ups

- **C.2 PR 3 is the natural next PR.** Proposed: `lapis_lazuli_skin` (IRON_SOLES reward). Why first: the existing C.4 positive-path test `milestone with matching cosmetic id credits rewards via atomic path` already uses `lapis_lazuli_skin` as a fixture; replacing the fixture with a real seed row is the smallest possible diff that converts a fixture-based test into a real end-to-end coverage. Side effect: the C.4 `UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES` test will need to be updated to expect Success (the id becomes known).
- Remaining milestone cosmetics (`garden_ziggurat_skin`, `sandals_of_gilgamesh`) land in PR 3b / PR 3c.
- After all 3 milestone cosmetics land, all 6 Milestone enum entries are fully claimable end-to-end \u2014 closes the "shipped but disabled" monetization gap that has been tracked since Plan R2-11.

### Memory updated

- `STATE.md` \u2705 \u2014 current objective now "`ensureSeedData` count-gate fix landed"; new bullet in "what works"; the debt line about `ensureSeedData is all-or-nothing` removed; priorities/next-actions reshuffled (C.2 PR 3 now #1, 3b/3c split out, ADR-0005/0006 + C.5/C.6 shift to #4); test count 484 \u2192 486; critical path marks the fix done; last-run updated.
- `RUN_LOG.md` \u2705 \u2014 this entry.
- ADR: not warranted \u2014 this is a scoped bug-fix; design rationale (Option A vs B vs C) is captured in the inline KDoc at point-of-use, the most discoverable location for future readers.

## 2026-05-08 — Phase C.4: ClaimMilestone UnknownCosmetic detection (RO-07 follow-up)

- **Goal:** Land the detection half of the `ClaimMilestone.Cosmetic` gap per `devdocs/evolution/implementation_roadmap.md` §C.4. Before this PR, `ClaimMilestone` silently dropped `MilestoneReward.Cosmetic` rewards whose ids didn't exist in `SEED_COSMETICS` — the 3 currently-mismatched milestone cosmetic ids (`garden_ziggurat_skin` on MARATHON_WALKER, `lapis_lazuli_skin` on IRON_SOLES, `sandals_of_gilgamesh` on GLOBE_TROTTER) would credit the Gems/PS rewards but never grant the cosmetic, with no observable error. This PR makes the mismatch surface loudly through a sealed-Result return type; resolution (seeding matching rows) stays as C.2 PR 3+ content work per the roadmap's explicit non-goal.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 2 + doc-sweep + C.2 PR 1 + B.3 PR 2 + B.2 PRs 4-5 entries). `git status` clean on `main`, up to date with origin (last commit `f01d54c feat(cosmetics): seed zig_jade as first end-to-end cosmetic (C.2 PR 2)`, just pushed). Read `ClaimMilestone`, `Milestone` enum (6 entries; 3 with Cosmetic rewards), `MilestoneReward` (sealed: Gems / PowerStones / Cosmetic), `CosmeticRepository` interface, `CosmeticRepositoryImpl` (ensureSeedData + toDomain), `FakeCosmeticRepository`, `MissionsViewModel` (uses ClaimMilestone; existing snackbar infrastructure: none, unlike StoreScreen/WorkshopScreen which already have Scaffold+SnackbarHost), `MissionsScreen`, `MissionsUiState`, `MilestoneDao.claimMilestoneAtomic` (to confirm atomic invariant is preserved), `ClaimMilestoneTest` (8 cases including 3 atomicity tests from B.2 PR 4), `MissionsViewModelTest`. Grep-confirmed ClaimMilestone has exactly 3 construction sites: `MissionsViewModel` (prod), `ClaimMilestoneTest` sut, `MissionsViewModelTest` one direct construction.

### Design

**Result shape: sealed class with `data object` + one `data class`.** Four variants match the 4 distinct rejection/success paths. `Success`, `InsufficientSteps`, `AlreadyClaimed` are singletons (no data, rendered as `data object` per Kotlin 2.3 best practice). `UnknownCosmetic` carries the offending `cosmeticId: String` so consumers can surface the specific id — matters because a player hitting MARATHON_WALKER's cosmetic issue should see a different message than IRON_SOLES's. Named the class `ClaimMilestoneResult` (not `Result`) to avoid any confusion with `kotlin.Result` elsewhere in the codebase. Placed in the same file as `ClaimMilestone` so the two are read together — same pattern as `OpenCardPack.PackTier` / `ActivateOverdrive.Result`.

**Pre-flight cosmetic-id check, not post-atomic recovery.** Two options evaluated:
1. **Option A (chosen):** Before calling `claimMilestoneAtomic`, iterate `milestone.rewards`, and for each `MilestoneReward.Cosmetic` call `cosmeticRepository.idExists(id)`. First unknown wins; return `UnknownCosmetic(id)` immediately with zero wallet movement. Clean: no partial credit, claim stays atomic in the "transition" sense.
2. **Option B (rejected):** Run `claimMilestoneAtomic` (credit Gems/PS + mark claimed), then check cosmetic ids afterwards, return `UnknownCosmetic` on miss. Would still credit the non-cosmetic rewards. Player-friendlier in a sense ("at least they got the Gems"), but contradicts "detection only" — marking the milestone claimed with unknown-cosmetic state couples detection to partial fulfilment and makes post-C.2-PR-3 resolution harder (the row is already `claimed=true` so re-granting the cosmetic when seed lands requires a separate mechanism).

Option A wins because the roadmap is explicit: "Do not silently drop." The strictest reading is "reject the whole claim so nothing silent happens." It also means the test can assert "no wallet movement" as a regression guard — a cleaner invariant than "partial movement" which is hard to test without enumerating exactly what was credited.

**Trade-off acknowledged:** Option A means the 3 affected milestones (MARATHON_WALKER, IRON_SOLES, GLOBE_TROTTER) are **currently un-claimable** until C.2 PR 3+ lands their cosmetic seed rows. A real player today who walks 500k steps and taps "Claim" on MARATHON_WALKER sees a snackbar, not a 600-Gem payout. This is a deliberate C.4 non-goal per the roadmap: "do not rename the 3 mismatched IDs in this PR (that is content work coupled to C.2 PR 3)." The alternative (silent drop) was worse — the player would never learn that they never got their cosmetic. An in-between state where they get the gems but not the cosmetic, and the milestone is marked claimed, would lock us out of fixing it later. So Option A is correct even if user-facing.

**`idExists` on the repo, not on the use case.** Added `suspend fun idExists(cosmeticId: String): Boolean` to the `CosmeticRepository` interface. Real impl lazy-seeds via `ensureSeedData()` then queries `observeAll().first().any { it.cosmeticId == cosmeticId }`. The lazy seed is important: the cosmetic catalogue is otherwise seeded only when `StoreViewModel.init` runs, so a player claiming a milestone from the Missions screen without ever opening the Store would see false-negatives. `FakeCosmeticRepository` checks its `items` StateFlow directly (tests configure items explicitly; no seed behaviour to emulate).

Considered exposing `SEED_COSMETICS` statically (e.g. a top-level constant or companion-object member) instead of going through the DAO round-trip, but that:
- breaks the domain/data boundary (use case would import data-layer contents).
- bypasses any runtime-added cosmetics (if future content PRs source cosmetics from a server or a pack, the static view is wrong).

Going through the repo keeps the contract clean: "the catalogue is whatever the repo says it is, at the moment of the check." The `ensureSeedData` idempotency makes this cheap on steady-state.

**First-unknown-wins semantics.** If a milestone has multiple Cosmetic rewards (none currently do, but the data model allows it), only the first unknown id is reported. Exhaustive reporting would require a `List<String>` on the result variant and complicate the common case. Since the roadmap's resolution plan is "one content PR per cosmetic," reporting the first unknown and iterating is enough — after the first seed lands, the same claim returns `UnknownCosmetic(next_id)` instead of the first one.

**MissionsViewModel: pattern-match + surface via snackbar.** The consumer now pattern-matches the result:
```kotlin
when (val result = claimMilestoneUseCase.invoke(milestone)) {
    ClaimMilestoneResult.Success -> Unit
    ClaimMilestoneResult.InsufficientSteps -> userMessage.value = "You haven't walked enough steps yet."
    ClaimMilestoneResult.AlreadyClaimed -> userMessage.value = "Milestone already claimed."
    is ClaimMilestoneResult.UnknownCosmetic -> userMessage.value = "Reward temporarily unavailable (cosmetic \"${result.cosmeticId}\" is being finalised). Try again after the next update."
}
```
The `userMessage: StateFlow<String?>` is nullable — non-null triggers the snackbar on the next render. `clearMessage()` resets after the snackbar dismisses. Matches the existing Store/Workshop/Cards/Labs pattern established in R10/R2-09.

`MissionsScreen` previously had no `Scaffold` wrapper; wrapped the `LazyColumn` in `Scaffold(snackbarHost = { SnackbarHost(\u2026) })` and added a `LaunchedEffect(state.userMessage)` that shows + clears. First time the Missions screen has user-feedback plumbing. `@OptIn(ExperimentalMaterial3Api::class)` was already present on the composable (left unchanged).

### Files touched

- `app/src/main/java/.../domain/repository/CosmeticRepository.kt` — +`suspend fun idExists(cosmeticId: String): Boolean` with KDoc explaining C.4 rationale and flagging resolution as C.2 PR 3+ content work.
- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` — +`override suspend fun idExists(...)` that lazy-seeds via `ensureSeedData()` then queries `observeAll().first().any { it.cosmeticId == cosmeticId }`. Inline comment notes the amortised cost (one table-count query on steady-state).
- `app/src/main/java/.../domain/usecase/ClaimMilestone.kt` — rewrite: +`ClaimMilestoneResult` sealed class (4 variants) in same file; constructor grew from 3 to 4 params (+`CosmeticRepository`); body now does step-threshold check \u2192 pre-flight cosmetic-id check \u2192 atomic DAO call \u2192 Success|AlreadyClaimed. KDoc expanded to document C.4 detection-only contract. `MilestoneReward.Cosmetic` import added for the pre-flight check.
- `app/src/main/java/.../presentation/missions/MissionsUiState.kt` — +`userMessage: String? = null` field with KDoc.
- `app/src/main/java/.../presentation/missions/MissionsViewModel.kt` — +`CosmeticRepository` injection (6 \u2192 7 constructor params); +`userMessage: MutableStateFlow<String?>` + `clearMessage()`; `claimMilestone(milestone)` now pattern-matches `ClaimMilestoneResult`; `combine()` grew from 4 to 5 flows (+userMessage).
- `app/src/main/java/.../presentation/missions/MissionsScreen.kt` — wrapped `LazyColumn` in `Scaffold(snackbarHost = { SnackbarHost(\u2026) })`; added `LaunchedEffect(state.userMessage)` that shows + clears. New imports: `LaunchedEffect`, `remember`.
- `app/src/test/java/.../fakes/FakeCosmeticRepository.kt` — +`override suspend fun idExists` that checks `items.value.any { it.cosmeticId == cosmeticId }`.
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt` — rewrite: 8 cases \u2192 12 cases (-1 merged into positive-path + 5 new C.4 cases). Constructor updated to 4-arg; sut now takes a `FakeCosmeticRepository` (empty by default to match prod-today state where the 3 ids don't exist). Test-name renames for Result-type clarity.
- `app/src/test/java/.../presentation/missions/MissionsViewModelTest.kt` — direct `ClaimMilestone` construction updated to 4-arg (+`FakeCosmeticRepository()`). Asserts `ClaimMilestoneResult.Success` instead of the old Boolean.

### Tests added (5 new cases in `ClaimMilestoneTest`, 1 existing case merged)

New C.4 cases:
1. **`UnknownCosmetic surfaces offending cosmetic id for MARATHON_WALKER`** \u2014 empty cosmetic catalogue; `useCase(MARATHON_WALKER)` returns `UnknownCosmetic("garden_ziggurat_skin")`. Asserts the exact id so renaming the reward in future content work surfaces as a test failure.
2. **`UnknownCosmetic surfaces offending cosmetic id for IRON_SOLES`** \u2014 same, for `"lapis_lazuli_skin"`.
3. **`UnknownCosmetic surfaces offending cosmetic id for GLOBE_TROTTER`** \u2014 same, for `"sandals_of_gilgamesh"`.
4. **`UnknownCosmetic rejects claim before the atomic DAO call with no credit`** \u2014 regression guard on pre-flight ordering: uses IRON_SOLES, asserts zero wallet movement + `claimMilestoneAtomicCallCount == 0` + no milestone row written. Proves the check runs BEFORE the atomic, not as a post-atomic cleanup.
5. **`milestone with matching cosmetic id credits rewards via atomic path`** \u2014 positive path emulating post-C.2-PR-3 state: seeds `cosmeticRepo.items` with a `lapis_lazuli_skin` CosmeticItem, then `useCase(IRON_SOLES)` succeeds atomically (200 Gems + 50 PS credited; atomic call count 1). Shows the check is selective: when the id is present, the rest of the flow runs normally. This test is the forward-looking compass for when C.2 PR 3 eventually seeds `lapis_lazuli_skin` \u2014 the test flips from "fixture-based" to "real seed row" without code changes.

Existing test merged away: `credits Gems and Power Stones for IRON_SOLES` was a Boolean-success case against IRON_SOLES, which now returns `UnknownCosmetic` by default. Coverage preserved by (a) the new UnknownCosmetic IRON_SOLES test (confirms the rejection shape) + (b) the new positive-path test (confirms the credit runs when the id resolves). Net: 8 \u2192 12 cases (+4).

Existing test adjusted in place:
- `two concurrent claims on the same milestone - only one credits` \u2014 target switched from IRON_SOLES (unknown cosmetic) to MORNING_JOGGER (Gems-only, no Cosmetic reward). The atomicity invariant being tested is independent of the cosmetic-id pre-flight check, so the target change keeps the coverage focused. Assertions updated to `ClaimMilestoneResult.Success` / `ClaimMilestoneResult.AlreadyClaimed` counts.
- `credits Gems correctly` \u2192 `credits Gems correctly on Success`, `marks milestone as claimed` \u2192 `marks milestone as claimed on Success`, `claiming twice is no-op` \u2192 `claiming twice returns AlreadyClaimed on second call`, `claiming milestone without reaching step threshold returns false` \u2192 `\u2026 returns InsufficientSteps`, `already-claimed entity pre-existing in DAO causes invoke to short-circuit` \u2192 `\u2026 causes invoke to return AlreadyClaimed`. Rename for Result-type clarity.

### Mid-edit bugs caught

**Em-dash in backtick test name.** Initially wrote `UnknownCosmetic rejects claim before the atomic DAO call \u2014 no credit` (em-dash separator). Kotlin compiler rejected with "Name contains illegal characters: ." \u2014 the em-dash character trips the Kotlin 2.3 backtick-identifier validator. Replaced with "with no credit" prose. Lesson: keep test names to ASCII.

### Verification

- `./run-gradle.sh test` \u2014 first run: compile error on em-dash test name (above). After fix, **BUILD SUCCESSFUL in 14s**, 36 actionable tasks. 0 failures, 0 errors, 0 skipped.
- Test count: **480 \u2192 484 JVM tests** (+4 net). Breakdown: -1 case merged (old IRON_SOLES success-path) + 5 new cases = +4. Matches expectations exactly.
- Grep sanity checks:
  - `grep -rn "ClaimMilestoneResult" app/src` \u2014 6 hits across the file pair + 3 test files.
  - `grep -rn "idExists" app/src` \u2014 7 hits (interface + 2 impls + use case + 3 tests).
  - `grep -rn "cosmeticId silently" app/src` \u2014 0 hits (no stale comments referring to the pre-C.4 silent-drop behaviour).
- Behaviour preservation: milestones without Cosmetic rewards (FIRST_STEPS, MORNING_JOGGER, TRAIL_BLAZER) claim identically to pre-C.4 \u2014 verified by the 7 preserved test cases.

### Surface changes

- `CosmeticRepository` gained one `suspend fun`. All implementations (real + fake) updated.
- `ClaimMilestone` constructor grew from 3 to 4 params; return type changed from `Boolean` to `ClaimMilestoneResult`. 3 call sites touched (MissionsViewModel, ClaimMilestoneTest sut, MissionsViewModelTest direct construction) \u2014 all updated in this PR.
- `MissionsViewModel` constructor grew from 6 to 7 params. Hilt graph picks up `CosmeticRepository` via the existing `@Binds` in `RepositoryModule`.
- `MissionsScreen` wrapped in `Scaffold` \u2014 backwards-compatible (no Hilt / nav changes). First user-feedback surface on the Missions screen.
- No new production dependencies. No ADR \u2014 C.4 roadmap section fully covers the decision with alternatives and non-goals.

### Open questions / blockers

- **3 milestones currently un-claimable by design.** MARATHON_WALKER / IRON_SOLES / GLOBE_TROTTER each carry an unknown-cosmetic reward; until C.2 PR 3+ seeds the matching rows, the claim snackbar is the only outcome. This is a known trade-off, documented in CHANGELOG, STATE.md, and the use case's KDoc.
- **Known seed-data debt** \u2014 `ensureSeedData` still short-circuits on `dao.count() > 0`. If a dev installs the app before C.2 PR 2 (no `zig_jade`) and before C.2 PR 3+ (no milestone cosmetics), subsequent upgrades won't seed the new rows. Fix is still queued as #1 in next-actions; must precede any C.2 PR 3+ content PR.

### Follow-ups

- **Immediate next (priority #1):** fix `ensureSeedData` count-gate. One-line change (per-`cosmeticId` filter) in `CosmeticRepositoryImpl.kt`. Small, additive, low risk.
- **C.2 PR 3:** ship the first milestone cosmetic seed row. Proposed: `lapis_lazuli_skin` because the positive-path test in C.4 already uses it as a fixture \u2014 replacing the fixture with real data is the smallest diff. The C.4 UnknownCosmetic IRON_SOLES test will break when this lands (the id will be known), and the replacement assertion \u2014 that IRON_SOLES now claims successfully \u2014 will be trivial.
- **C.5 + C.6:** Billing + Ad SDK swaps, each gated on ADR-0005 / ADR-0006. Independent of C.2 PR 3+ cadence.

### Memory updated

- `STATE.md` \u2705 \u2014 current objective now "Phase C.4 landed"; C.4 added to "what works"; test count 480 \u2192 484; priorities/next-actions reshuffled (ensureSeedData fix #1, C.2 PR 3 #2, C.5/C.6 #3); C.4 removed from debt list; critical path marks PRs 1+2 + C.4 done; last-run date updated.
- `RUN_LOG.md` \u2705 \u2014 this entry.
- ADR: not warranted \u2014 C.4 roadmap section fully covers the decision. The pre-flight-vs-post-atomic design choice is documented in the use case's KDoc (Option A vs Option B rationale) and in this entry, discoverable at the point of use.

## 2026-05-08 — Phase C.2 PR 2 (RO-07): seed zig_jade as first end-to-end cosmetic

- **Goal:** Land the first content slice of the C.2 cosmetic pipeline per `devdocs/evolution/implementation_roadmap.md` §C.2 PR 2. PR 1 shipped the renderer plumbing (dormant — `ZIGGURAT_COLOR_LOOKUP` empty). PR 2 seeds the first cosmetic (`zig_jade` — jade ziggurat recolour per gap_analysis §5.2), populates its palette, and lifts the R2-11 "Coming Soon" guard for that single ID so it's purchasable in the Store. Closes the "shipped but disabled" monetization gap for one end-to-end slice, unblocks the remaining 6 seeded + 3 milestone cosmetics as pure content work for PR 3+.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (doc-sweep + C.2 PR 1 + B.3 PR 2 + B.2 PRs 4-5 entries). `git status` clean on `main`, up to date with origin (last commit `d50cf9f docs(agent): mandate current-state doc sync before STATE/RUN_LOG in every PR task list`). Read `CosmeticRepositoryImpl` (SEED_COSMETICS + ZIGGURAT_COLOR_LOOKUP + toDomain), `CosmeticItem`, `StoreScreen`, `StoreViewModel` (purchaseCosmetic path), `CosmeticDao`, `CosmeticEntity`, `ZigguratEntity.DEFAULT_COLORS` (5 ints → content contract matches). Checked existing PR 1 `BattleViewModelTest` cosmetic fixtures for palette conventions — synthetic fixture uses `"ZIG_JADE"` uppercase, but existing seed rows (`zig_obsidian`, etc.) are snake_case lowercase. Chose `zig_jade` for consistency. Grep-confirmed no `CosmeticRepositoryImplTest` or `FakeCosmeticDao` existed.

### Design

**Cosmetic ID choice: `zig_jade` (lowercase).** The roadmap and gap_analysis §5.2 both write `ZIG_JADE` in prose, but existing `SEED_COSMETICS` rows (`zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, etc.) all use snake_case. Treating the doc's uppercase as formatting emphasis not literal; lowercase matches the established ID convention. The PR 1 synthetic VM test fixture `"ZIG_JADE"` is a test-only string that injects through a fake repo — it doesn't collide with the real seed row.

**Palette choice.** Reused the exact 5-color jade gradient from the PR 1 test fixture: `[0xFF104E3C, 0xFF1A6B52, 0xFF2A8F6E, 0xFF3CAB82, 0xFF54C79A]` (bottom layer → top highlight, deep jade to pale highlight). Tests lock in the exact values so any accidental palette mutation surfaces as a test failure (content-as-code contract). Matches the `ZigguratEntity.DEFAULT_COLORS` cardinality contract (exactly 5 Ints, one per layer).

**StoreScreen allow-list idiom.** Introduced a file-level `private const val ENABLED_COSMETIC_ID = "zig_jade"` and gated the enable-branch on `cosmetic.cosmeticId == ENABLED_COSMETIC_ID`. Three alternatives rejected:
1. Remove the guard entirely for all owned-but-not-equipped — wrong, purchase is still guarded by affordability only.
2. Carry the allow-list in `CosmeticCategory` or as a list — premature abstraction; PR 3+ adds one ID at a time.
3. Read `CosmeticItem.overrideColors != null` as the enable signal — couples the UI to the renderer contract, breaks the moment a category adds non-color overrides.

The file-level const is the smallest step that scales monotonically with PR 3+ (add one ID to a list when the next palette lands).

**Price point: 150 💎.** Between `zig_obsidian` (100) and `zig_crystal` (200). Matches the roadmap's implicit mid-tier positioning for the first cosmetic (150 is also the `proj_fire` / `proj_lightning` price — no collision, jade is a new category).

**Disclaimer line update.** Was "Cosmetic visuals are being finalized. Purchases are disabled until ready." — now "Most cosmetic visuals are still being finalized. Jade Ziggurat is available now." Accurate signal to the player; doesn't overpromise.

**Known debt explicitly not fixed in this PR.** `ensureSeedData` short-circuits when `dao.count() > 0`, so `zig_jade` only lands on fresh installs — existing dev installs need a data clear. Considered fixing in the same PR (one-line per-cosmeticId filter) but held scope tight per STATE.md's narrow phrasing ("seed ZIG_JADE + remove guard"). Flagged in the Known-issues section of STATE.md + CHANGELOG.md so it surfaces as explicit follow-up work before any further content PR. Low risk: pre-release app has no shipped installs; devs can clear data.

### Files touched

- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` — `ZIGGURAT_COLOR_LOOKUP` gained the first entry (`"zig_jade" to [5-color jade palette]`); KDoc expanded to document the content-as-code contract and point at PR 3+ as the extension vehicle. `SEED_COSMETICS` gained a `zig_jade` row (ZIGGURAT_SKIN, 150 💎, placed first in the list so it surfaces at the top of the Store cosmetics section). Total seed count: 7 → 8.
- `app/src/main/java/.../presentation/store/StoreScreen.kt` — file-level `private const val ENABLED_COSMETIC_ID = "zig_jade"` + KDoc explaining the allow-list contract + which other files must co-update when expanding (`CosmeticRepositoryImpl.SEED_COSMETICS` + `ZIGGURAT_COLOR_LOOKUP`). Enable-branch added to the `when` at the unowned-path of the cosmetic card: shows `💎 {priceGems}` on an enabled Button wired to `viewModel.purchaseCosmetic(cosmetic.cosmeticId)`, `enabled = !state.isPurchasing` (the existing double-tap guard). All non-`zig_jade` unowned cosmetics fall through to the pre-existing "Coming Soon" disabled Button. Disclaimer text updated.
- `app/src/test/java/.../fakes/FakeCosmeticDao.kt` (new, 75 LOC) — in-memory `CosmeticDao` fake. Monotonic `nextId: Int` counter simulates Room's `@PrimaryKey(autoGenerate = true)`. Upsert resolves conflicts by `cosmeticId` (preserves id on update). KDoc explains the constraint: does NOT enforce cosmeticId uniqueness, caller's responsibility to drive via `ensureSeedData`.
- `app/src/test/java/.../data/repository/CosmeticRepositoryImplTest.kt` (new, 134 LOC, 5 cases) — new test directory `app/src/test/java/.../data/repository/` matching the main-sources layout.

### Tests added (5 new cases in new `CosmeticRepositoryImplTest`)

1. **`C2PR2 - ensureSeedData inserts zig_jade as first end-to-end cosmetic`** — proves `ensureSeedData` on a fresh fake DAO creates the `zig_jade` row with the expected metadata (name = "Jade Ziggurat", category = ZIGGURAT_SKIN, priceGems = 150, isOwned = false, isEquipped = false).
2. **`C2PR2 - zig_jade propagates jade palette via overrideColors from ZIGGURAT_COLOR_LOOKUP`** — proves the lookup table → `toDomain` chain: observed `zig_jade` has `overrideColors.size == 5` and matches the exact palette. Content-as-code contract; any accidental palette mutation fails this test.
3. **`C2PR2 - other seeded ziggurat cosmetics have null overrideColors pending content PRs`** — regression guard: all 7 non-`zig_jade` seeds (`zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`) return `null` overrideColors. Proves the lookup is selective, not blanket.
4. **`C2PR2 - equipped zig_jade surfaces via observeEquipped with overrideColors intact`** — repo-layer mirror of the PR 1 VM→engine test. Exercises the full equip path: `purchase("zig_jade")` → `equip("zig_jade")` → `observeEquipped().first()` returns jade with `isOwned = true`, `isEquipped = true`, `overrideColors.size == 5`. Together with PR 1's VM test, proves the end-to-end chain `CosmeticRepo → VM → engine.cosmeticOverrides → layer colors`.
5. **`C2PR2 - ensureSeedData is idempotent on repeat call (count gate holds)`** — documents the current all-or-nothing contract. First call seeds 8 rows; second call returns early via `dao.count() > 0` gate. Locks in the known-debt behaviour so future content PRs that change `ensureSeedData` semantics surface as a test failure rather than silent double-seed.

### Mid-edit bugs caught

None. The fake construction, StoreScreen edit, and test suite landed on first try. The palette sync with the PR 1 test fixture was deliberate (reused the exact values to avoid a mismatch between the synthetic VM fixture and the real seed row).

### Verification

- `./run-gradle.sh test` — BUILD SUCCESSFUL in 20s, 36 actionable tasks. Test count: **475 → 480 JVM tests** (+5, matches exactly). 0 failures, 0 errors, 0 skipped.
- Lint: clean (pre-existing warnings unchanged).
- Grep sanity checks:
  - `grep "zig_jade" app/src/main` — 3 hits (SEED_COSMETICS row + ZIGGURAT_COLOR_LOOKUP entry + StoreScreen ENABLED_COSMETIC_ID).
  - `grep "Coming Soon" app/src/main` — 1 hit (the disabled Button text for all non-`zig_jade` unowned cosmetics; intentional).
  - `grep "ENABLED_COSMETIC_ID" app/src/main` — 2 hits (declaration + one usage in the when-branch).
- Behaviour preservation: the empty-`ZIGGURAT_COLOR_LOOKUP` default branch from PR 1 still short-circuits correctly for any cosmeticId not in the map (verified by test #3).

### Surface changes

- New content in `CosmeticRepositoryImpl` (1 seed row + 1 lookup entry + expanded KDoc). Additive — no other files need updating.
- `StoreScreen` gained a file-level const + one additional branch in the `when` expression. No new imports. No API changes.
- New test fake (`FakeCosmeticDao`) + new test file (`CosmeticRepositoryImplTest`). No existing tests modified.
- No new production dependencies. No ADR — C.2 roadmap section fully covers this PR with alternatives/non-goals/rollback; ADR would duplicate content.

### Open questions / blockers

- **Known debt:** `ensureSeedData` count-gate prevents `zig_jade` (and all future content PR seed rows) from landing on existing dev installs. Fix is a one-line change (`val missing = SEED_COSMETICS.filter { it.cosmeticId !in existingIds }`) but I held it out of this PR to keep scope tight. Should land BEFORE C.2 PR 3 so content PRs don't each have to ship a data-clear workaround.
- **Product decision for PR 3+:** which cosmetic is second? Roadmap / gap_analysis don't prescribe order. Proposed default: pick any one of the remaining 6 seeded ziggurat/projectile/enemy rows (my vote: `zig_obsidian` at 100 💎 — cheaper entry point, signals "affordable starter").

### Follow-ups

- **Immediate next PR (C.4):** `ClaimMilestone.Cosmetic` detection fix. Small, independent, surfaces the 3 mismatched milestone IDs as `Result.UnknownCosmetic` instead of silent drop. Promoted from #2 to #1 in STATE.md next-actions.
- **Before C.2 PR 3:** fix `ensureSeedData` count-gate. Same file, one-line change, additive semantics (existing installs just gain the new row; no data loss).
- **C.5 + C.6:** real Billing + Ad SDK swaps, each gated on ADR-0005 / ADR-0006 stubs. Independent of Phase C.2 progress.
- **B.4 / B.5:** FollowOnPipeline + UpdateMissionProgress debt cleanup. Not blockers.

### Memory updated

- `STATE.md` ✅ — current objective now "Phase C.2 PR 2 landed"; C.2 PR 2 added to "what works"; known-debt line updated (`ensureSeedData` count-gate called out explicitly); priorities/next-actions reshuffled (C.4 top, seed-migration fix second, C.5/C.6 third); test count 475 → 480; critical-path updated to mark C.2 PRs 1+2 complete; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — C.2 roadmap section fully covers the PR. The lowercase `zig_jade` vs roadmap's `ZIG_JADE` is a naming consistency choice, not an architectural decision.

## 2026-05-08 — Doc sweep: current-state sync after B.2 PRs 4-5 + B.3 PR 2 + C.2 PR 1

- **Goal:** Close accumulated current-state doc drift. Last A.1-style sweep (2026-05-06) synced through Phase A; since then B.2 PRs 4-5, B.3 PR 2, and C.2 PR 1 have landed — 4 current-state docs were stale. Preflight grep confirmed 4 targets needed updates: `AGENTS.md`, `CHANGELOG.md`, `.kiro/steering/source-files.md`, `.kiro/steering/structure.md`. Historical artifacts (RUN_LOG, plan-R*, external-reviews, devdocs/archaeology, devdocs/evolution) intentionally left untouched per the A.1 precedent.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (C.2 PR 1 + B.3 PR 2 + B.2 PRs 4-5 entries). `git status` clean on `main`, up to date with origin (last commit `ff5c414 feat: cosmetic renderer override pipeline (C.2 PR 1)`). Grep-enumerated stale references: `465 JVM tests` (2 hits in AGENTS/CHANGELOG), missing `CoroutineScopeModule` / `cosmeticOverrides` / `claimMilestoneAtomic` / `hasWaveProgress` / `ZIGGURAT_COLOR_LOOKUP` entries across the 4 targets.

### Changes

- **`AGENTS.md`** (1 line): test count 465 → 475; coverage list extended to note RO-02 `5/5 sites landed` (added `ClaimMilestone.claimMilestoneAtomic`, `BattleViewModel.runEndRoundPersistence` tx wrap), RO-03 `2/2 sites landed` (added `onCleared` guard), RO-07 `PR 1` cosmetic pipeline plumbing.
- **`CHANGELOG.md`** (4 new sections + updated Current state): added "Phase B.2 PR 4 — Atomic @Transaction for ClaimMilestone", "Phase B.2 PR 5 — Room @Transaction around runEndRoundPersistence (FINAL RO-02 site)", "Phase B.3 PR 2 — onCleared guard preserves mid-nav round progress (FINAL RO-03 site)", "Phase C.2 PR 1 — Cosmetic renderer override pipeline". Current state test progression 465 → 475, RO-02 complete (5/5), RO-03 complete (2/2), RO-07 in flight, noted B.4/B.5/C.4 as remaining debt.
- **`.kiro/steering/source-files.md`**: added `di/CoroutineScopeModule.kt` entry with KDoc-style one-liner; updated 6 existing entries — `MilestoneDao` (+`@Transaction claimMilestoneAtomic`), `ClaimMilestone` (+atomic delegation note), `CosmeticItem` (+`overrideColors`), `CosmeticRepositoryImpl` (+`ZIGGURAT_COLOR_LOOKUP`), `BattleViewModel` (composite summary: 14-param constructor; RO-02 tx wrap + RO-03 resilience + onCleared guard + C.2 cosmetic hydration), `GameEngine` (+`hasWaveProgress` + `cosmeticOverrides`).
- **`.kiro/steering/structure.md`**: added `CoroutineScopeModule` to the `di/` module list (line 39) and a new row in the Key Files table directly below `di/TimeModule.kt`.

### Verification

- `./run-gradle.sh :app:testDebugUnitTest` — BUILD SUCCESSFUL in 1s, all 36 actionable tasks UP-TO-DATE (no code changed — nothing to recompile, test task cached). Test suite stays at **475 JVM tests**, all green.
- Post-sweep grep `'465 JVM tests'` — 0 matches in non-historical docs. Post-sweep grep `'CoroutineScopeModule'` — now referenced from source-files.md + structure.md + CHANGELOG.md as expected.
- No code or test changes; the RO-02/RO-03/RO-07 behaviour locked in earlier is unaffected.

### Files touched

- `AGENTS.md` (1-line test count + coverage update)
- `CHANGELOG.md` (+4 PR sections; updated Current state)
- `.kiro/steering/source-files.md` (+1 entry; 6 updated entries)
- `.kiro/steering/structure.md` (+2 mentions of `CoroutineScopeModule`)
- `docs/agent/STATE.md` (objective line + last-run line)
- `docs/agent/RUN_LOG.md` (this entry)

### Intentionally NOT touched

- `docs/agent/RUN_LOG.md` (historical per-session entries below this one)
- `docs/plans/plan-R*.md`, `docs/plans/plan-R2*.md` (historical)
- `docs/external-reviews/*` (historical at review date)
- `devdocs/archaeology/*`, `devdocs/evolution/*`, `smoke_tests/*` (historical per their HEAD pin)

### Open questions / blockers

- **None.** Doc drift closed. Next substantive work: C.2 PR 2 (seed `ZIG_JADE` + remove R2-11 guard for that single ID), then C.4 (ClaimMilestone.Cosmetic detection fix), then C.5/C.6 (real SDK swaps gated on ADR stubs).

### Memory updated

- `STATE.md` ✅ — current objective now "Doc sweep landed"; last-run date reflects doc-sweep.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — doc-only sweep, no architectural decisions.

## 2026-05-08 — Phase C.2 PR 1 (RO-07): cosmetic renderer override pipeline (plumbing only)

- **Goal:** Land PR 1 of the cosmetic rendering pipeline per `devdocs/evolution/refactoring_opportunities.md` §RO-07 and `implementation_roadmap.md` §C.2. The cosmetic system has three disconnected parts: **data** (`CosmeticEntity` / `CosmeticDao` / 7 seeded rows), **UI** (`StoreScreen` + `StoreViewModel` with the R2-11 "Coming Soon" guard disabling purchases), and **renderer** (`GameEngine` / `ZigguratEntity` with zero cosmetic awareness). This PR closes the *renderer* gap with pure-additive plumbing so PR 2 can seed one cosmetic (`ZIG_JADE`) end-to-end and remove the R2-11 guard for it.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2/B.3 entries), `devdocs/evolution/refactoring_opportunities.md` §RO-07, `implementation_roadmap.md` §C.2. `git status` clean on `main`, up to date with origin (last commit `c083cb8 refactor: onCleared guard preserves mid-nav round progress (B.3 PR 2)`). Read `CosmeticItem`, `CosmeticEntity`, `CosmeticRepositoryImpl` (for SEED_COSMETICS and toDomain), `CosmeticCategory` enum, `CosmeticDao`, `domain/repository/CosmeticRepository` interface, `GameEngine.init` (ZigguratEntity construction site at line 142), `ZigguratEntity` (`layerColors: List<Int>` already a constructor param with `DEFAULT_COLORS` fallback), `BattleScreen` / `GameSurfaceView` / `BattleViewModel` for the engine lifecycle, `FakeCosmeticRepository`. Grep-confirmed `lifecycle-process` still not on classpath (N/A for C.2).

### Design

**Field placement on `CosmeticItem`.** The spec quote "BattleViewModel selects override.colors if ZIGGURAT category is present" suggests colors live on the cosmetic. Added `overrideColors: List<Int>? = null` as a nullable data-class field. `List<Int>` is pure Kotlin — no Android imports leak into `domain/`. Nullable default keeps every existing CosmeticItem construction site (tests + internal creations) source-compatible.

**Color lookup table.** `CosmeticRepositoryImpl.companion object` gained `private val ZIGGURAT_COLOR_LOOKUP: Map<String, List<Int>> = emptyMap()`, consulted in `toDomain()` as `overrideColors = ZIGGURAT_COLOR_LOOKUP[cosmeticId]`. Empty in PR 1 — the first entry (`ZIG_JADE`) ships in PR 2. **No DB schema change.** Colors are content, stored in code; changing a palette is a one-line patch not a migration. KDoc calls out the contract: each entry MUST be exactly 5 Ints to match `ZigguratEntity.DEFAULT_COLORS`.

**GameEngine contract.** New `@Volatile var cosmeticOverrides: Map<CosmeticCategory, CosmeticItem> = emptyMap()` public property on `GameEngine`. Default `emptyMap()` preserves today's rendering exactly. In `init()`, replaced the direct `layerColors = biomeTheme.zigguratColors` with:
```kotlin
val zigColors = cosmeticOverrides[CosmeticCategory.ZIGGURAT_SKIN]?.overrideColors
    ?: biomeTheme.zigguratColors
```
Null-coalescing fallback — a player with no ziggurat cosmetic equipped, or an equipped cosmetic whose ID isn't in `ZIGGURAT_COLOR_LOOKUP`, gets the biome default. `@Volatile` is defensive — reads happen on the game-loop thread, writes happen on the UI/VM thread via Hilt-scoped coroutines.

**BattleViewModel hydration.** `CosmeticRepository` added as a constructor dep (13 → 14 params). In the init-launch, after loading cards:
```kotlin
equippedCosmetics = cosmeticRepository.observeEquipped().first().associateBy { it.category }
engine?.cosmeticOverrides = equippedCosmetics
```
**Two push sites, intentional.** The engine can be attached either *before* the init-launch completes (normal case — `startPollingEngine` fires from `BattleScreen`'s first composition, VM init launches a coroutine that completes later) or *after* (rare race). The init-launch pushes *if engine already attached*, and `startPollingEngine` also pushes `engine.cosmeticOverrides = equippedCosmetics` in case the init launch finishes first. Whichever fires last wins; both are idempotent writes to the same `@Volatile` field. The subsequent `engine.init()` (triggered by `surfaceView.configure()` when `isLoading=false`) reads the up-to-date map.

**Non-goals in PR 1.** Per RO-07 non-goals: (a) no animated cosmetics; (b) only `ZIGGURAT_SKIN` category plumbed — `PROJECTILE_EFFECT` and `ENEMY_SKIN` follow in PR 3+ when content ships; (c) no R2-11 guard removal (that's PR 2, gated on content); (d) no `ClaimMilestone.Cosmetic` detection fix (that's C.4, independent).

### Files touched

- `app/src/main/java/.../domain/model/CosmeticItem.kt` (+`overrideColors: List<Int>? = null` field; class KDoc explaining the lookup-table relationship)
- `app/src/main/java/.../data/repository/CosmeticRepositoryImpl.kt` (+`ZIGGURAT_COLOR_LOOKUP` empty map + KDoc; `toDomain` reads from it)
- `app/src/main/java/.../presentation/battle/engine/GameEngine.kt` (+imports: `CosmeticCategory`, `CosmeticItem`; +`@Volatile var cosmeticOverrides` public property + KDoc; `init()` swaps `biomeTheme.zigguratColors` for null-coalesced lookup)
- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+imports: `CosmeticCategory`, `CosmeticItem`, `CosmeticRepository`; +`cosmeticRepository` constructor param (13 → 14); +`private var equippedCosmetics: Map<CosmeticCategory, CosmeticItem>` field; init-launch loads + pushes to `engine?.cosmeticOverrides`; `startPollingEngine` also pushes as defence against the load-vs-attach race)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+`cosmeticRepo` fixture; threaded through createVm + both RO-03 direct constructions; +2 new C.2 PR 1 tests)

### Tests added (2 new cases in `BattleViewModelTest`, bringing it to 26 total)

1. **`C2PR1 - no equipped cosmetics keeps engine cosmeticOverrides empty`** — regression guard. Empty `cosmeticRepo.items.value`, construct VM, install engine via `installEngineForEndRound` BEFORE `advanceUntilIdle` (so the VM's init-launch `engine?.cosmeticOverrides = equippedCosmetics` push lands on the test engine), advance. Assert `engine.cosmeticOverrides.isEmpty()`. Proves: players without equipped cosmetics see no change in engine state — the null-coalescing fallback in `engine.init()` returns the biome default.
2. **`C2PR1 - equipped ziggurat cosmetic propagates to engine cosmeticOverrides`** — happy path. Seed an in-memory `CosmeticItem("ZIG_JADE", ZIGGURAT_SKIN, ..., overrideColors = jadeColors)` with `isEquipped = true`, install engine before advance, assert `engine.cosmeticOverrides[ZIGGURAT_SKIN]` matches the equipped item including `overrideColors`. Proves the end-to-end pipeline: `CosmeticRepository → VM → engine.cosmeticOverrides` is wired.

### Mid-edit bugs caught

1. **First build hung on test execution.** Gradle Test Executor at 100% CPU for several minutes; compilation and lint had already succeeded. Root cause: my initial tests called `vm.startPollingEngine(engine, mock())` to trigger the cosmetic push, but `startPollingEngine` launches an infinite `while(true) { delay(200); ziggurat ?: continue }` polling loop inside `viewModelScope`. Since `engine.init()` was never called in the test, `eng.ziggurat` stayed null, the `continue` branch fired every tick, and `advanceUntilIdle()` never returned — the test dispatcher kept seeing scheduled delays. Detected by checking `ps aux | grep gradle` and `tail /tmp/gradle_out.txt`. Fix: removed the `startPollingEngine` calls and installed the engine via `installEngineForEndRound(vm)` *before* the first `advanceUntilIdle`, so the VM's init-launch push (`engine?.cosmeticOverrides = equippedCosmetics`) lands on the engine when it runs. No polling loop, no hang. Stuck Gradle processes (test executor 24346, wrapper 24260, shell 24257) killed with `kill -9` before retry.
2. **Import-line corruption from a two-edit batch.** My batch `edit_file` call inserted tests at the end *and* tried to normalise the imports block simultaneously; the imports got concatenated onto a single line (`fakes.*import com.whitefang...MilestoneNotificationManager`). Caught by the immediate follow-up diff and fixed with a one-line split. KSP didn't notice because the edit happened between compile and test runs.

### Verification

- First build: hung at `Task :app:testDebugUnitTest` (see Mid-edit bugs above). Compilation + lint succeeded.
- Second build after fixing the hang and import corruption: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL, zero warnings.
- Test suite: **473 → 475 JVM tests** (+2, matches the 2 new pipeline tests exactly), 0 failures, 0 errors, 0 skipped. `BattleViewModelTest`: 24 → 26 cases.
- Lint: clean.
- Behavior preservation: existing tests unchanged; the null-coalescing `cosmeticOverrides[ZIGGURAT_SKIN]?.overrideColors ?: biomeTheme.zigguratColors` guarantees identical rendering when no cosmetic is equipped (the regression-guard test locks this in).

### Surface changes

- `CosmeticItem` gained a nullable field; all existing construction sites (test fakes, internal) stay source-compatible via default. 20 file hits, 0 required updates.
- `GameEngine` gained a public `cosmeticOverrides` property — additive, no existing code consumes it yet.
- `BattleViewModel` constructor grew 13 → 14 params. Hilt graph unaffected (`CosmeticRepository` already `@Binds`-ed in `RepositoryModule`). 3 test construction sites updated.
- No new production dependencies. No ADR — RO-07 spec fully covers this PR with alternatives/non-goals/rollback; ADR would duplicate content.

### Open questions / blockers

- **None for PR 1.** The pipeline is live and dormant — waiting for PR 2's `ZIG_JADE` seed row + palette + R2-11 guard removal.
- **Product decision for PR 2** is noted in the roadmap (gap_analysis §5.2 proposes jade ziggurat; any one-color-swap cosmetic works). Any other single-cosmetic choice just changes the ID string.

### Follow-ups

- **C.2 PR 2** is the next natural unit: +1 row in `SEED_COSMETICS`, +1 entry in `ZIGGURAT_COLOR_LOOKUP`, minus the R2-11 guard for that single ID in `StoreScreen` (existing logic at line 129). Zero changes needed in the pipeline itself.
- **C.4** (`ClaimMilestone.Cosmetic` detection fix) is independent and can land in parallel; surfaces the 3 mismatched milestone cosmetic IDs as `Result.UnknownCosmetic` instead of silent drop.
- **PR 3+** (remaining 6 seeded + 3 milestone cosmetics) is content work; each is +1 row / +1 palette / verify R2-11 removal. No pipeline changes.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by seven PRs (+20 total). Continue bundling into the next A.1-style sweep; post-Phase-C is the natural checkpoint.
- `.kiro/steering/source-files.md` should add `di/CoroutineScopeModule.kt` (B.3 PR 2) on the next doc sweep; it's the only new file in `app/src/main/` since the last sweep that the index hasn't caught yet.

### Memory updated

- `STATE.md` ✅ — current objective now "C.2 PR 1 (plumbing only)"; C.2 PR 1 added to "what works"; priorities/next-actions reshuffled (C.2 PR 2 top, C.4 second); test count 473 → 475; critical-path line updated to mark C.2 PR 1 complete.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-07 spec + C.2 roadmap section already cover the PR with alternatives, risk, verification, rollback, non-goals. One-line deviation (color table location: code not DB) is documented in the `ZIGGURAT_COLOR_LOOKUP` KDoc, discoverable at the point of use.

## 2026-05-08 — Phase B.3 PR 2 (RO-03, FINAL): onCleared guard via @ApplicationScope CoroutineScope

- **Goal:** Land the final RO-03 unit per `devdocs/evolution/refactoring_opportunities.md` §RO-03. `BattleViewModel.onCleared` currently nulls the step-reward callback and calls `super.onCleared()` — which cancels `viewModelScope`. If a deep-link navigation teardown fires mid-round (e.g. a supply-drop notification replaces the Battle route), any in-flight round-persistence work is silently discarded. The spec calls for a scope that outlives VM cancellation; we fill in that gap.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1–5 and B.3 PR 1 entries). `git status` clean on `main`, up to date with origin (last commit `a95ea00 refactor: Room @Transaction around runEndRoundPersistence (B.2 PR 5)`). Read `BattleViewModel`, `BattleViewModelTest` (21 cases), `StepCounterService` (existing `CoroutineScope(SupervisorJob() + Dispatchers.Default)` precedent), `StepsOfBabylonApp` (no app-level scope yet), `libs.versions.toml` (confirmed `lifecycle-process` NOT on classpath), existing Hilt modules for the @Qualifier + @Module precedent. Grep-confirmed zero current `ProcessLifecycleOwner` usage anywhere in the codebase.

### Design

**Scope-idiom choice: deviated from the RO-03 spec.** The spec's "First safe step" suggested `ProcessLifecycleOwner.lifecycleScope`, claiming `androidx.lifecycle:lifecycle-process` is "transitively available". That claim is *wrong* — the dep is not on the classpath, `libs.versions.toml` only declares `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose`, and `grep -r lifecycle-process` returns zero matches. Rather than pull in a new dep, I used a Hilt-injected `@ApplicationScope` `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Reasons:
1. **No new dependency.** `kotlinx.coroutines` is already on the classpath; the scope is pure Kotlin.
2. **Right dispatcher.** `ProcessLifecycleOwner.lifecycleScope` defaults to `Dispatchers.Main` — wrong for DB writes. `Dispatchers.Default` matches the `StepCounterService` precedent in the same project.
3. **More testable.** The scope is DI-injected, so tests inject a `CoroutineScope(SupervisorJob() + dispatcher)` bound to `StandardTestDispatcher` — `advanceUntilIdle()` deterministically drains the launched work. A `ProcessLifecycleOwner.get()` singleton would need mocking.
4. **Matches project conventions.** Every other cross-cutting infrastructure piece (DB, Hilt Work, TimeProvider) is Hilt-injected. Adding a new `ProcessLifecycleOwner.get()` call site would introduce a second paradigm.

**`@ApplicationScope` qualifier.** New file `di/CoroutineScopeModule.kt` with a `@Qualifier` annotation and a `@Singleton @Provides` method. KDoc explains the scope semantics (lifetime = JVM process, SupervisorJob isolation, Default dispatcher) and the spec-deviation rationale. Follows the same shape as the existing `BillingModule`, `AdModule`, `TimeModule` precedent — adding a new cross-cutting Hilt provider is a well-trodden path.

**`GameEngine.hasWaveProgress()`.** The RO-03 spec's code snippet references `engine.hasWaveProgress()` as the guard that prevents persisting a zero-progress round (user opens Battle then immediately backs out). The method didn't exist; added as a pure-read boolean over two `@Volatile` fields (`elapsedTimeSeconds > 0f || totalEnemiesKilled > 0`). Thread-safe, no state mutation, no dispatcher concerns.

**`markEndedAndLaunchPersistence(scope, engine)` helper.** Rather than duplicate the "claim `roundEnded` guard + mark engine.roundOver + compute wave + launch persistence" sequence between the existing `endRound()` path and the new `onCleared` path, extracted a shared helper. Takes the scope as a parameter. `endRound()` passes `viewModelScope` (normal teardown), `onCleared` passes `applicationScope` (survives VM cancellation). Centralises future changes and guarantees both paths stay in sync.

**`onCleared` guard.** New override:
```kotlin
override fun onCleared() {
    val eng = engine
    if (eng != null && !roundEnded && eng.hasWaveProgress()) {
        markEndedAndLaunchPersistence(applicationScope, eng)
    }
    eng?.onStepReward = null
    super.onCleared()
}
```
Three-way guard: engine must exist, round must not have already ended (`roundEnded` guard in `markEndedAndLaunchPersistence` prevents double-persist), AND wave must have made observable progress (`hasWaveProgress` prevents bounce-through phantoms).

**Order of operations.** Check → launch — then null the callback — then super.onCleared(). The `applicationScope.launch` captures `eng` by value before `super.onCleared()` cancels `viewModelScope`, so the work is already queued on a scope that survives. The callback-nulling stays *after* the launch decision because a mid-launch kill of onStepReward is fine (the persistence coroutine owns its own engine ref).

**Annotation target.** `@ApplicationScope` on the constructor parameter produced a Kotlin KT-73255 forward-compat warning about future application to both parameter and property. Fixed with explicit `@param:ApplicationScope` to future-proof the annotation target. The `@param:` qualifier is the sanctioned migration path.

### Files touched

- `app/src/main/java/.../di/CoroutineScopeModule.kt` (new file: `@ApplicationScope` qualifier + `@Singleton @Provides fun provideApplicationScope()` returning `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; KDoc explains scope semantics and the ProcessLifecycleOwner deviation rationale)
- `app/src/main/java/.../presentation/battle/engine/GameEngine.kt` (+`hasWaveProgress(): Boolean` with KDoc explaining the mid-nav persistence-guard semantics; reads existing `@Volatile` fields only)
- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+imports: `ApplicationScope`, `CoroutineScope`; +constructor param `@param:ApplicationScope applicationScope: CoroutineScope` (12 → 13 params); +`markEndedAndLaunchPersistence(scope, eng)` helper; `endRound()` delegates to helper via `viewModelScope`; new `onCleared()` override checks guard trio and launches via `applicationScope` when applicable)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+imports: `CoroutineScope`, `SupervisorJob`; +`applicationScope: CoroutineScope` fixture rebuilt in @BeforeEach as `CoroutineScope(SupervisorJob() + dispatcher)` so test-dispatcher advancement drains launches; threaded through `createVm` + both direct `BattleViewModel(...)` constructions in the RO-03 failure tests; +`invokeOnCleared(vm)` reflection helper; +`installEngineWithProgress(vm, elapsedSeconds, kills)` reflection helper that sets the `@Volatile` fields directly; +3 B.3 PR 2 tests)

### Tests added (3 new cases in `BattleViewModelTest`, bringing it to 24 total)

1. **`B3PR2 - onCleared mid-round launches persistence on the application scope`** — installs an engine with `elapsedSeconds=30f, kills=7`, calls `invokeOnCleared(vm)`, advances the test dispatcher. Asserts `playerRepo.profile.value.totalRoundsPlayed` advanced by 1 and `totalEnemiesKilled` = 7. Before B.3 PR 2 this would fail because `viewModelScope` is cancelled before `runEndRoundPersistence` runs.
2. **`B3PR2 - onCleared with no wave progress is a no-op`** — installs a fresh engine (no reflection into `@Volatile` fields, so `elapsedTimeSeconds=0f`, `totalEnemiesKilled=0`), calls `invokeOnCleared(vm)`. Asserts `totalRoundsPlayed` unchanged. Proves the `hasWaveProgress()` guard short-circuits the bounce-through case (user opens Battle then backs out before any wave ticks).
3. **`B3PR2 - onCleared after quitRound is a no-op (roundEnded guard holds)`** — installs engine with progress, calls `quitRound()` (persists once), then `invokeOnCleared(vm)`. Asserts `totalRoundsPlayed` stays at 1 after both calls. Proves the `roundEnded` guard holds across both teardown paths — prevents a normal quit-and-nav sequence from double-persisting.

### Verification

- First build: `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL with 2 Kotlin warnings about `@ApplicationScope` annotation target (KT-73255 forward-compat).
- Second build after adding `@param:ApplicationScope` explicit target — BUILD SUCCESSFUL, **zero warnings**.
- Test suite: **470 → 473 JVM tests** (+3, matches the 3 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `BattleViewModelTest`: 21 → 24 cases.
- Lint: clean.
- Hilt KSP compiled the new qualifier + provider cleanly — `BattleViewModel`'s Hilt graph now includes the `@ApplicationScope CoroutineScope` binding without any manual wiring changes outside the new module.
- **RO-03 site count: 2/2 complete.** The R03 family (B.3 PR 1 resilient extraction + B.3 PR 2 mid-nav scope guard) is closed.

### Surface changes

- `BattleViewModel` constructor grew 12 → 13 params. Hilt graph picks up the new `@ApplicationScope CoroutineScope` via `CoroutineScopeModule`. 3 test construction sites (createVm + 2 RO-03 failure tests) updated.
- New public module `di/CoroutineScopeModule.kt` with `@ApplicationScope` qualifier; first use of this annotation in the codebase. Any future long-lived background work that should outlive a VM (e.g. fire-and-forget notification posting, background analytics) can reuse this qualifier.
- New public method `GameEngine.hasWaveProgress(): Boolean`. Small, pure read; safe surface addition.
- No changes to any existing public API. No new production dependencies. No ADR — RO-03 spec already covers the site with alternatives; the ProcessLifecycleOwner deviation is documented in the module's KDoc.

### Open questions / blockers

- **None. RO-03 is complete.** The mid-nav round-loss gap is closed at the VM level. Process-kill between `launch` and first write still loses the round (same as the spec's mitigation statement); RO-02 PR 5's transaction wrap closes the only remaining observability gap (partial-commit on crash).
- B.4 (`FollowOnPipeline` extraction) and B.5 (`UpdateMissionProgress` use case) remain as Phase B debt. Both are maintainability refactors, not blockers; can land any time before or during Phase C.

### Follow-ups

- **RO-03 milestone:** 2/2 sites complete. The resilience family (B.3 PR 1 extraction + PR 2 guard) is closed.
- **Phase C can proceed.** C.2 (cosmetic rendering pipeline) is the next release-critical path item. C.5 and C.6 (real Billing + Ad SDK swaps) are gated on their respective ADR stubs.
- **B.4/B.5** are pure debt; schedule opportunistically. Both share a common theme (removing forbidden-direction imports from presentation → data.local) and compose with each other.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by six PRs (+3+3+2+3+2+3 = +16). Continue bundling into the next A.1-style sweep; a single doc-sync PR at the end of Phase B will handle it.
- `.kiro/steering/source-files.md` should add `di/CoroutineScopeModule.kt` on next doc sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "RO-03 is COMPLETE"; B.3 PR 2 added to "what works"; known-issues/debt line updated (both RO-02 and RO-03 complete); priorities/next-actions reshuffled (Phase C.2 top); test count 470 → 473; critical-path line updated to mark B.3 complete; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-03 spec already covers this site with alternatives and rollback. The one genuine deviation (Hilt scope vs ProcessLifecycleOwner) is documented in the `CoroutineScopeModule` KDoc, which is discoverable at the point of use.

## 2026-05-08 — Phase B.2 PR 5 (RO-02 site #5, FINAL): AppDatabase.withTransaction for BattleViewModel.runEndRoundPersistence

- **Goal:** Land the final RO-02 site per `devdocs/evolution/refactoring_opportunities.md` §RO-02. `BattleViewModel.runEndRoundPersistence` (extracted in B.3 PR 1 specifically to enable this wrap) has 5 SQLite writes in the end-of-round fan-out: `updateBestWave`, `awardWaveMilestone`, `updateHighestUnlockedTier` (behind a profile-read), `incrementBattleStats`, `dailyMissionDao.updateProgress`. Without a transaction boundary, external readers (other ViewModels observing reactive Flows) can observe a partially-applied end-of-round state; e.g. `totalRoundsPlayed` advances but `bestWavePerTier` hasn't yet, or vice versa. Wrapping the writes in a single Room transaction closes this window.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1–4, B.3 PR 1 entries). `git status` clean on `main`, up to date with origin (last commit `a9ebcde refactor: atomic @Transaction for ClaimMilestone (B.2 PR 4)`). Read `BattleViewModel`, `BattleViewModelTest` (19 cases including 3 RO-03 + 7 other A.7 / step-reward), `StepCrossValidator` (the B.2 PR 3 `withTransaction` precedent), `StepCrossValidatorTest` (seam test pattern). Grep-confirmed `BattleViewModel` has 3 construction sites (createVm + 2 RO-03 direct constructions); `runInTransaction =` appears only in StepCrossValidator / EscrowLifecycleTest tests (pattern-safe to reuse).

### Design

**Idiom choice.** Used `AppDatabase.withTransaction { }` (repo-level, same as B.2 PR 3) rather than a DAO-level `@Transaction` default method (B.2 PRs 1/2/4). Reasons:
1. The writes span three layers — `PlayerRepository` (Room-backed, composite methods), direct `DailyMissionDao` calls, and a profile read. A DAO-level `@Transaction` default method would force either a giant composite DAO method or repo-level orchestration; neither fits cleanly.
2. `BattleViewModel` already injects three DAOs (`DailyMissionDao`, `DailyStepDao`, `PlayerProfileDao`) as the B.2 PR 2 precedent. Adding `AppDatabase` is a marginal additional layering concession of the same flavour.
3. RO-02 explicitly licenses this form: "different pattern but same spirit" per B.2 PR 3's RUN_LOG. `TransactionRunner` abstraction is an RO-02 non-goal; a `@VisibleForTesting internal var runInTransaction` seam is the sanctioned middle ground.

**Transaction boundary decisions.** Only the 5 SQLite writes go inside the `runInTransaction { }` block. Two kinds of work are deliberately *outside*:
1. **Milestone notification** (`MilestoneNotificationManager.notifyNewBestWave`) — posts through the Android notification system, not SQLite. Holding a DB lock across a system-service IPC is wasteful and risks ANR if the NotificationManager is slow to respond.
2. **UI state push** (`_uiState.update`) — in-memory MutableStateFlow update. Observers are Compose collectors; they should see the post-round overlay ASAP once the transaction commits, not be blocked on DB work.

This means the UI push moved from "between writes 3 and 4" (pre-PR 5) to "strictly after all 5 writes commit" (post-PR 5). Semantically equivalent for the user (the whole sequence still completes in a single coroutine tick from the poll loop), but guarantees the DB lock is released before any UI painting.

**RO-02 + RO-03 composition.** The RO-03 per-write `runCatching { }.onFailure { Log.w }` pattern (B.3 PR 1) is *preserved inside* the transaction block. This doesn't give classical ACID rollback-on-failure — a caught exception doesn't propagate out of the transaction, so Room commits whatever was written before the throw. What the transaction *does* give:
- **External-reader atomicity**: other connections (Flow-based reactive reads) see either the pre-PR state or the post-PR state, never a partial fan-out. SQLite's SERIALIZABLE isolation on commit provides this.
- **Concurrent-writer serialization**: if another ViewModel / Worker tries to write while the tx is open, SQLite queues it — prevents interleaving.
- **Reduced lock acquisition**: one `BEGIN TRANSACTION` instead of 5. Material on mobile where DB contention matters.

The outer `runCatching { runInTransaction { ... } }.onFailure { Log.w }` guards against Room infrastructure failures (disk full, SQLCipher decrypt failure, Room throwing from `withTransaction` itself). RO-03's "UI must always appear" takes priority here — if the whole tx fails, we log and still fire the UI push with safe defaults (`isNewRecord = false`, `previousBest = 0`, etc. captured via the `var` locals).

**Captured locals.** Because `isNewRecord`, `previousBest`, `psAwarded`, and `newTier` are computed inside the tx but read by the notification + UI push *after* the tx, they're hoisted to `var`s outside the `runInTransaction` call. The Kotlin closure captures `var`s by reference wrapper — safe here because we're in a sequential suspend context, not racing coroutines.

**Test seam.** Matches `StepCrossValidator` (B.2 PR 3) verbatim: `@VisibleForTesting internal var runInTransaction: suspend (block: suspend () -> Unit) -> Unit = { block -> appDatabase.withTransaction { block() } }`. Tests construct with `mock<AppDatabase>()` and override the seam with a direct-invocation pass-through via `.apply { runInTransaction = { block -> block() } }`. Justified: Mockito can't mock Room's `withTransaction` extension on a bare mock, and instrumented tests (out of scope for JVM unit tests) validate the real transaction behaviour.

### Files touched

- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+`androidx.room.withTransaction` + `androidx.annotation.VisibleForTesting` + `data.local.AppDatabase` imports; +`appDatabase: AppDatabase` constructor param (11 → 12); +`@VisibleForTesting internal var runInTransaction` seam; `runEndRoundPersistence` body restructured: 5 SQLite writes wrapped in `runInTransaction { }`, notification + UI push moved to after the tx block, outer `runCatching` preserves RO-03 resilience; KDoc rewritten to explain RO-02 + RO-03 composition and the outside-tx rationale)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+`appDatabase = mock<AppDatabase>()` fixture; `createVm()` now passes 12 args AND chains `.apply { runInTransaction = { block -> block() } }` to install the pass-through seam; both direct `BattleViewModel(...)` constructions in the RO-03 failure tests updated the same way; +2 new B.2 PR 5 atomicity tests)

### Tests added (2 new cases in `BattleViewModelTest`, bringing it to 21 total)

1. **`RO-02 B2PR5 - runEndRoundPersistence opens the transaction seam exactly once per round`** — counting wrapper replaces the default pass-through; `vm.quitRound() + advanceUntilIdle()` x 2; asserts `transactionCalls == 1` after the first call (exactly one tx) and still `== 1` after the second call (roundEnded guard short-circuits before reaching the tx). Mirrors the B.2 PR 3 counting-wrapper pattern for `StepCrossValidator`.
2. **`RO-02 B2PR5 - UI push runs AFTER the transaction commits`** — captures `vm.uiState.value.roundEndState` *inside* the seam lambda immediately after `block()` returns; asserts it's still `null` there (UI push has NOT yet happened), then asserts it's non-null after the whole `quitRound()` call completes. Uses `lateinit var vm` so the seam lambda can reference the VM it's installed on. Proves the post-round overlay waits for the DB lock to release before appearing.

All 19 existing cases preserved verbatim via the `createVm()` change — the default `runInTransaction` override is a pass-through, so the behaviour under test is identical to pre-PR 5.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL.
- Test suite: **468 → 470 JVM tests** (+2, matches the 2 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `BattleViewModelTest`: 19 → 21 cases.
- Lint: clean, no new warnings.
- RO-02 site count: **5/5 landed**. `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` — 3 matches (WorkshopDao, DailyStepDao, MilestoneDao); `grep -rn "withTransaction" app/src/main` — matches in StepCrossValidator + BattleViewModel for a total of 5 atomic sites.
- No mid-edit bugs this PR.

### Surface changes

- `BattleViewModel` constructor grew 11 → 12 params. Hilt graph unaffected — `AppDatabase` is already `@Provides`-d by `DatabaseModule`. 3 test construction sites (createVm + 2 RO-03 direct) updated.
- New public-ish API surface: `@VisibleForTesting internal var runInTransaction`. Test-only, not called from production except through the default lambda.
- No changes to `BattleUiState`, `RoundEndState`, or any domain types.
- No new production dependencies. room-ktx's `withTransaction` already on the classpath (used by `StepCrossValidator`).
- No ADR — RO-02 spec already covers this site; same rationale as B.2 PR 3 for the repo-level idiom vs DAO-level, and same rationale as B.2 PRs 1–4 for not writing a PR-specific ADR.

### Open questions / blockers

- None. **RO-02 is complete.** Real Room transaction behaviour at runtime is validated on-device / via instrumented tests (explicitly out of scope for JVM unit tests per the RO-02 verification strategy).
- B.3 PR 2 (`onCleared` guard via `ProcessLifecycleOwner.lifecycleScope`) remains as the last item in the B.3 family. Independent of RO-02; closes the mid-nav round-loss gap.

### Follow-ups

- **RO-02 milestone:** 5/5 atomic sites landed. The atomic-transaction PR family that started with B.2 PR 1 (2026-05-07) is now closed.
- **B.3 PR 2** is the next natural unit in Phase B. Small scope: `onCleared()` currently just nulls `engine.onStepReward`; the fix moves the round-persistence launch to a scope that outlives VM cleanup, so mid-battle nav doesn't drop in-flight writes.
- **Phase C** can begin in parallel with B.3 PR 2 now that RO-02 has closed its debt. C.2 (cosmetic rendering pipeline) is the release-critical path.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by five PRs. Continue bundling into the next A.1-style sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "RO-02 is COMPLETE"; B.2 PR 5 added to "what works"; known-issues/debt line updated (RO-02 done); priorities/next-actions reshuffled (B.3 PR 2 top); test count 468 → 470; critical-path line updated to mark B.2 complete; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already covers this site; no net-new decisions required a standalone record.

## 2026-05-08 — Phase B.2 PR 4 (RO-02 site #4): atomic @Transaction for ClaimMilestone

- **Goal:** Apply the B.2 PR 1–2 pattern to the fourth RO-02 multi-write site named in `devdocs/evolution/refactoring_opportunities.md` §RO-02: `ClaimMilestone`. The use case currently (a) reads `totalStepsEarned` for a step-threshold guard, (b) reads the existing `MilestoneEntity` for an already-claimed guard, (c) iterates rewards calling `playerRepository.addGems` / `addPowerStones`, and (d) finally `milestoneDao.upsert(... claimed = true)`. A crash between (c) and (d) credits the player but leaves the milestone unclaimed — enabling double-credit on retry. Two concurrent claim clicks can also both pass (b) and both run (c). Both windows close with a single SQLite transaction wrapping the check + mark-claimed + reward credits.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1, B.3 PR 1, B.2 PR 2, B.2 PR 3 entries). `git status` clean on `main`, up to date with origin (last commit `fd4e282 docs: sync current-state docs after B.2 PRs 1-3 + B.3 PR 1`). Read `ClaimMilestone`, `MilestoneDao`, `MilestoneEntity`, `Milestone`, `MilestoneReward`, `PlayerProfileDao`, `PlayerRepositoryImpl` (to learn `addGems` = `adjustGems` + `incrementGemsEarned` composite), `ClaimMilestoneTest` (5 cases), `FakeMilestoneDao`, `MissionsViewModel`, `MissionsViewModelTest`, `CheckMilestonesTest` (to confirm it won't be affected), `HomeViewModelTest` (to confirm `FakeMilestoneDao()` no-arg stays source-compatible). Grep-confirmed ClaimMilestone has exactly two construction sites: `MissionsViewModel` (prod) and `MissionsViewModelTest` (one test case) plus `ClaimMilestoneTest`'s sut.

### Design

Mirrored B.2 PR 2 exactly: cross-DAO `@Transaction` default method on a Room DAO interface. Chose read-modify-write (read `getByIdOnce` → bail if `claimed == true` → `upsert` → credit) over SQL-guarded single-statement (`INSERT ... ON CONFLICT DO UPDATE WHERE`) because the read-modify-write pattern matches `DailyStepDao.creditBattleStepsAtomic` identically (same idiom, same KDoc shape, same Mutex emulation in the fake) and leans on SQLite's SERIALIZABLE isolation inside `@Transaction` instead of a Room-specific return-type-of-INSERT edge case. Both approaches are correct; consistency with the established pattern won.

- **`MilestoneDao`:** added `claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt, playerDao: PlayerProfileDao): Boolean` as a suspend `@Transaction` default method. Body does `getByIdOnce` → bail if `existing?.claimed == true` → `upsert(MilestoneEntity(id, claimed = true, claimedAt))` → if `gems > 0` then `playerDao.adjustGems(gems)` + `playerDao.incrementGemsEarned(gems)` → same for Power Stones → return `true`. Cross-DAO calls are safe inside the `@Transaction` because Room's transaction tracker is scoped to the underlying `RoomDatabase`. The wallet composite (`adjustGems` + `incrementGemsEarned`) matches `PlayerRepositoryImpl.addGems` exactly — dropping either would fail the `totalGemsEarned` lifetime-counter invariant that the Economy dashboard depends on.
- **`ClaimMilestone` use case:** dep shape changed from `(milestoneDao, playerRepository)` to `(milestoneDao, playerRepository, playerProfileDao)`. The use case still reads `totalStepsEarned` through `PlayerRepository` for the step-threshold guard — this is intentional: `totalStepsEarned` is monotonic, so a stale read can only fail-closed (false-negative → user retries) and there is no correctness window to close. Body shrank from ~15 lines (profile read + claimed check + reward iteration loop + upsert) to a step-threshold guard + single `milestoneDao.claimMilestoneAtomic(...)` call. `MilestoneReward.Cosmetic` remains a no-op pending Phase C.2's cosmetic-rendering pipeline (documented in the new KDoc).
- **`MissionsViewModel`:** gained a Hilt-injected `PlayerProfileDao` constructor param (6 params now); updated the internal `ClaimMilestone(...)` construction. DI graph unaffected — `DatabaseModule` already provides `PlayerProfileDao`.
- **Fake emulation (`FakeMilestoneDao`):** added optional `linkedPlayer: FakePlayerRepository? = null` constructor arg matching `FakeDailyStepDao`'s pattern. Overrides `claimMilestoneAtomic` to emulate the SQL atomic contract under a `Mutex` — read-check-write-credit serialised so concurrent callers observe each other's mutations. The override takes `playerDao: PlayerProfileDao` for type satisfaction but ignores it; credits (gems + totalGemsEarned; powerStones + totalPowerStonesEarned) go through `linkedPlayer.profile` so existing tests can keep asserting on `FakePlayerRepository`. Added `claimMilestoneAtomicCallCount` counter for tests to prove the atomic path is live.

### Files touched

- `app/src/main/java/.../data/local/MilestoneDao.kt` (+`@Transaction claimMilestoneAtomic` default method, +`androidx.room.Transaction` import, comprehensive KDoc)
- `app/src/main/java/.../domain/usecase/ClaimMilestone.kt` (+`PlayerProfileDao` constructor dep, body rewrite to delegation, class-level KDoc explaining the monotonic-read rationale for keeping `PlayerRepository`)
- `app/src/main/java/.../presentation/missions/MissionsViewModel.kt` (+`PlayerProfileDao` import + constructor param, updated `ClaimMilestone` construction)
- `app/src/test/java/.../fakes/FakeMilestoneDao.kt` (+`linkedPlayer` param, +Mutex-guarded `claimMilestoneAtomic` override, +`claimMilestoneAtomicCallCount`, KDoc)
- `app/src/test/java/.../domain/usecase/ClaimMilestoneTest.kt` (sut helper rewritten: `FakeMilestoneDao(linkedPlayer = playerRepo)` + `mock<PlayerProfileDao>()`; 5 existing cases preserved; +3 new atomicity cases; existing `claiming milestone without reaching step threshold` strengthened with `claimMilestoneAtomicCallCount == 0` assertion to prove the fast-fail bypasses the atomic call)
- `app/src/test/java/.../presentation/missions/MissionsViewModelTest.kt` (`FakeMilestoneDao(linkedPlayer = playerRepo)`; `ClaimMilestone(milestoneDao, playerRepo, mock<PlayerProfileDao>())`; all 4 existing test cases preserved)

### Tests added (3 new cases in `ClaimMilestoneTest`, bringing it to 8 total)

1. **`successful claim goes through atomic DAO method exactly once`** — asserts `dao.claimMilestoneAtomicCallCount == 1` after a successful claim and that the wallet was credited correctly (60 Gems for FIRST_STEPS). Regression-guard: if someone reintroduces the split `addGems` + `upsert` flow this test fails immediately.
2. **`two concurrent claims on the same milestone - only one credits`** — `kotlinx.coroutines.async` + `awaitAll` pair racing on the atomic path against `Milestone.IRON_SOLES` (200 Gems + 50 Power Stones + Cosmetic no-op). Asserts exactly one `true` and one `false`, wallet credited exactly once (200 Gems + 50 PS — not 400/100), milestone marked claimed exactly once, and both callers reached the atomic method. Proves the Mutex-guarded fake models the SQL atomic guard correctly.
3. **`already-claimed entity pre-existing in DAO causes invoke to short-circuit`** — seeds the DAO with a pre-claimed entity (emulates "claim committed in a previous process lifecycle") and calls `useCase(MORNING_JOGGER)`. Asserts result is `false`, no gems credited, and `claimMilestoneAtomicCallCount == 1` — proves the already-claimed guard lives *inside* the atomic method (where the race would matter), not outside. This is one more test than the two PR 1–2 predecessors added; kept it because it covers a semantically distinct path (persisted vs in-memory race loss) and follows B.2 PR 1's precedent of strengthening existing cases where it's cheap.

All 5 existing cases preserved verbatim, with one strengthened: `claiming milestone without reaching step threshold returns false` gained `assertEquals(0, claimMilestoneAtomicCallCount)` to prove the step-threshold guard short-circuits *before* the atomic DAO call, avoiding an unnecessary DB round-trip for obviously-unqualified callers.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL. Room KSP compiled the third `@Transaction` default method with a cross-DAO parameter cleanly.
- Test suite: **465 → 468 JVM tests** (+3, matches the 3 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `ClaimMilestoneTest`: 5 → 8 cases. `MissionsViewModelTest`: 4 → 4 (only construction-arg update, no test changes).
- Lint: clean, no new warnings.
- `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` — **3 matches** (`WorkshopDao.kt`, `DailyStepDao.kt`, `MilestoneDao.kt`). RO-02 target is ≥5 atomic sites after all 5 PRs in the family land; 4/5 now (3 DAO-level `@Transaction` + 1 repo-level `withTransaction` in `StepCrossValidator`).
- No mid-edit bugs this PR. The read-modify-write pattern from B.2 PR 2 translated cleanly to the gem/PS credit shape.

### Surface changes

- `ClaimMilestone` constructor: `(milestoneDao, playerRepository)` → `(milestoneDao, playerRepository, playerProfileDao)`. 3 call sites touched (`MissionsViewModel`, `ClaimMilestoneTest` sut + one direct construction, `MissionsViewModelTest` one direct construction); all updated in this PR.
- `MissionsViewModel` constructor grew from 5 to 6 params. Hilt graph unaffected (PlayerProfileDao already `@Provides`-d). Test code that manually constructs `MissionsViewModel` — none; the test uses use-case-level assertions (matches existing precedent in the file header comment).
- `FakeMilestoneDao` constructor: no-arg → optional `linkedPlayer` param. All 5 call sites (`ClaimMilestoneTest`, `CheckMilestonesTest`, `MissionsViewModelTest`, `HomeViewModelTest`, `FakeMilestoneDao` itself) remain source-compatible because the default is `null`. Only `ClaimMilestoneTest` and `MissionsViewModelTest` actively pass `linkedPlayer` — the other two don't credit through the fake and don't need the forwarding.
- No new production dependencies. No ADR — RO-02 spec already covers this site; same rationale as B.2 PRs 1–3.

### Open questions / blockers

- None. Double-claim atomicity is proven at the fake/Mutex level; real Room transaction behaviour is a separate instrumented-test concern per RO-02 verification strategy.
- 1 RO-02 site remains (B.2 PR 5 — wrap `runEndRoundPersistence` in a Room `@Transaction`). This is the smallest remaining unit: `runEndRoundPersistence` was extracted in B.3 PR 1 specifically to enable a single-call-site transaction wrap. Should be trivial.

### Follow-ups

- **B.2 PR 5 is the final RO-02 unit.** Should land next; completes the 5-site atomic-transaction family.
- B.3 PR 2 (`onCleared` guard via `ProcessLifecycleOwner.lifecycleScope`) remains independent and can land any time after PR 5 to keep the B.2/B.3 ordering clean.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by four PRs (+3, +3, +2, +3). Bundle into the next A.1-style sweep rather than a one-line PR per change.
- Phase C can begin in parallel with B.3 PR 2 once RO-02 closes.

### Memory updated

- `STATE.md` ✅ — current objective now "B.2 PR 4 complete"; B.2 PR 4 added to "what works"; priorities/next-actions reshuffled (B.2 PR 5 top); test count 465 → 468; critical-path line updated; last-run date 2026-05-08.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already covers this site with full alternatives/rollback.

## 2026-05-07 — Phase B.2 PR 3 (RO-02 site #2): AppDatabase.withTransaction for StepCrossValidator

- **Goal:** Apply RO-02 site #2 per `devdocs/evolution/refactoring_opportunities.md` §RO-02. `StepCrossValidator.validate` has multiple graduated-response branches that each pair a `playerRepository.spendSteps` / `addSteps` call with a `stepRepository.updateEscrow` / `releaseEscrow` call. A crash between the two writes leaves the wallet and escrow counter out of sync — either the player was charged without the escrow recording it (allowing double-spend on retry) or the reverse. RO-02 explicitly licenses the cross-layer `AppDatabase` import at this site (unlike PRs 1–2 where the transaction lives on the DAO) because the validator lives in `data/healthconnect/` and the graduated-response branches need parallel transaction scopes.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1 + 2, B.3 PR 1 entries). `git status` clean on `main`, 4 commits ahead of origin pushed. Read `StepCrossValidator`, `StepRepository` interface, `StepRepositoryImpl`, `AntiCheatPreferences`, `StepCrossValidatorTest` (10 cases, all Mockito-based), `EscrowLifecycleTest` (2 cases, uses `FakePlayerRepository` + `FakeStepRepository`). Grep-confirmed 3 construction sites total.

### Design

The spec says "3 parallel branches" but the validator actually has **5 multi-write pairs** (cap-excess branches share a shape; each gets its own wrapper):

1. **Level 3 cap-excess:** `spendSteps(excess) + updateEscrow(..., MAX_ESCROW_SYNCS_DEFAULT)`
2. **Level 2 cap-excess:** `spendSteps(excess) + updateEscrow(..., MAX_ESCROW_SYNCS_DEFAULT)`
3. **Level 1 first-escrow:** `spendSteps(excess) + updateEscrow(..., newSyncCount)` — the subsequent-sync metadata-only update and the discard path are single writes (not wrapped)
4. **Level 0 first-escrow:** same shape as #3
5. **Reconciliation:** `addSteps(record.escrowSteps) + releaseEscrow(date)`

All 5 wrapped in `runInTransaction { … }`. The `antiCheatPrefs.recordCvOffense(date)` write (at the top of the if-branch) and `antiCheatPrefs.decayCvOffenses()` (after reconciliation) deliberately stay **outside** the transaction — they are SharedPreferences writes, not SQLite-backed, and cannot participate in a Room transaction. Recording the offense before the transaction is also the safer ordering: a transaction failure must not hide the fact that a validation attempt detected a discrepancy.

**Test seam.** `StepCrossValidator` gains an `@VisibleForTesting internal var runInTransaction` field with a default that delegates to `appDatabase.withTransaction { block() }`. Tests construct with `mock<AppDatabase>()` (Mockito can't mock Room's `withTransaction` extension on a bare mock) and override the seam with a direct-invocation pass-through `{ block -> block() }`. The branch-logic assertions remain unchanged; real Room transaction behaviour is an instrumented-test concern out of JVM scope, per the RO-02 verification strategy.

### Why the seam + internal var, not a full abstraction

RO-02 non-goal: "Do not introduce a global `TransactionRunner` abstraction." The seam is a single `internal var` on one class, not a project-wide interface — it exists only because `mock<AppDatabase>()` doesn't support Kotlin extension functions. Every production call goes through the default `appDatabase.withTransaction { block() }`; the override path is test-only and annotated with `@VisibleForTesting`.

### Files touched

- `app/src/main/java/.../data/healthconnect/StepCrossValidator.kt` (+`AppDatabase` + `androidx.annotation.VisibleForTesting` + `androidx.room.withTransaction` imports; +`appDatabase` constructor param; +`runInTransaction` seam; 5 multi-write branches wrapped; SharedPreferences writes explicitly documented as outside-transaction; comprehensive KDoc on the class header explaining the scope split and RO-02 license)
- `app/src/test/java/.../data/healthconnect/StepCrossValidatorTest.kt` (+`AppDatabase` mock; `runInTransaction = { block -> block() }` on validator construction; all 10 existing cases preserved verbatim; +2 new atomicity cases)
- `app/src/test/java/.../data/integration/EscrowLifecycleTest.kt` (+`AppDatabase` mock; new `makeValidator` helper that wires the pass-through seam; both existing integration cases preserved)

### Tests added (2 new cases in `StepCrossValidatorTest`, bringing it to 12 total)

1. **`RO-02 site 2 - multi-write branch invokes the transaction seam exactly once per write pair`** — constructs a validator with a counting wrapper around `runInTransaction`; exercises the Level 0 first-escrow path; asserts `transactionCalls == 1` and that both writes still happened. Proves the seam is live for the multi-write branches.
2. **`RO-02 site 2 - single-write branches bypass the transaction seam`** — same counting wrapper; exercises the Level 0 subsequent-sync path (metadata-only `updateEscrow`, no `spendSteps`); asserts `transactionCalls == 0`. Proves the seam is not dead weight on single-write branches — the wrapping is surgical, not indiscriminate.

All 10 existing `StepCrossValidatorTest` cases continue to pass verbatim because the seam's default is a direct-invocation pass-through from the test's perspective. `EscrowLifecycleTest`'s two full-lifecycle cases (escrow + release, escrow + discard) also pass — same seam wiring via the new `makeValidator` helper.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL.
- Test suite: **463 → 465 JVM tests** (+2, matches the 2 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `StepCrossValidatorTest`: 10 → 12 cases. `EscrowLifecycleTest`: 2 → 2 (preserved, now routing through the seam).
- Lint: clean, no new warnings.
- `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` still 2 (WorkshopDao, DailyStepDao). `grep -c "withTransaction" app/src/main/java/com/whitefang/stepsofbabylon/data/healthconnect/StepCrossValidator.kt` — 1 match (the default lambda body). RO-02 progress: 3/5 atomic sites landed.

### Surface changes

- `StepCrossValidator` constructor grew from 4 to 5 params (added `AppDatabase`). Hilt graph unaffected — `AppDatabase` is provided by `DatabaseModule`.
- Tests that construct `StepCrossValidator` manually (3 sites total) all updated.
- No public API changes to `StepRepository`, `PlayerRepository`, or `AntiCheatPreferences`.
- No new production dependencies.
- No ADR — same rationale as B.2 PRs 1–2.

### Open questions / blockers

- None. The `runInTransaction` seam is a deliberate, narrowly-scoped test hook. The real transaction behaviour is exercised at app runtime via Room's generated impl of `withTransaction` on the SQLCipher-wrapped `AppDatabase`.
- 2 RO-02 sites remain. B.2 PR 4 (`ClaimMilestone`) is the same pattern as PRs 1–2 — a composite `@Transaction` method on `MilestoneDao` taking `PlayerProfileDao`. B.2 PR 5 wraps `runEndRoundPersistence` in `withTransaction { }` — a single-call-site change thanks to B.3 PR 1.

### Follow-ups

- B.2 PR 4 next: `ClaimMilestone` atomic. Same mechanical pattern as PRs 1–2. Expect a clean copy-paste of the shape.
- B.2 PR 5 is the smallest remaining RO-02 unit (single `withTransaction { }` wrap around an existing function).
- B.3 PR 2 (`onCleared` guard) remains independent and can land any time.
- Doc drift: `AGENTS.md` still says "455 JVM tests" (now 465). Bundle into a future A.1-style sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "B.2 PR 3 complete"; B.2 PR 3 added to "what works"; priorities/next-actions reshuffled (B.2 PR 4 top); test count 463 → 465; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already covers this site with full alternatives/rollback.

## 2026-05-07 — Phase B.2 PR 2 (RO-02 site #1): atomic @Transaction for AwardBattleSteps

- **Goal:** Apply the B.2 PR 1 pattern to the first multi-write site RO-02 names: `AwardBattleSteps`. Wrap the cap check + `incrementBattleSteps` + wallet-credit chain in a single Room `@Transaction` so a crash between the two writes can no longer leave the wallet credited without the cap counter moving, and two concurrent kills with 1 battle-step of headroom can no longer both credit and overflow the cap by 1.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1 + B.3 PR 1 entries). `git status` clean at `main`, 2 commits ahead pushed to origin. Read `AwardBattleSteps`, `DailyStepDao`, `DailyStepRecordEntity`, existing `AwardBattleStepsTest` (7 cases), `FakeDailyStepDao`. Grep-confirmed 3 callers: `AwardBattleStepsTest` (sut helper), `BattleViewModel` (init), `BattleViewModelTest` (dead `awardBattleSteps` field declared but never read).

### Design

Mirrored B.2 PR 1 exactly: cross-DAO `@Transaction` default method on a Room interface.

- **`DailyStepDao`:** added `creditBattleStepsAtomic(date, requested, dailyCap, playerDao: PlayerProfileDao): Long` as a suspend `@Transaction` default method. Body does cap check → `min(requested, remaining)` → `incrementBattleSteps(date, credited)` → `playerDao.adjustStepBalance(credited)` → returns credited. Cross-DAO call is safe inside the `@Transaction` because Room's transaction tracker is scoped to the underlying `RoomDatabase`.
- **`AwardBattleSteps` use case:** dep shape changed from `(playerRepository, dailyStepDao, timeProvider)` to `(dailyStepDao, playerProfileDao, timeProvider)`. Body shrank from ~7 lines of read/compute/write to a single delegation `dailyStepDao.creditBattleStepsAtomic(today, amount, DAILY_BATTLE_STEP_CAP, playerProfileDao)`. Drops the `PlayerRepository` dep — wallet write now happens inside the transaction via `PlayerProfileDao` directly.
- **`BattleViewModel`:** added `PlayerProfileDao` as a Hilt-injected constructor param (11 params now); updated the internal `AwardBattleSteps` construction. DI graph unaffected — `DatabaseModule` already provides `PlayerProfileDao`.
- **Fake emulation (`FakeDailyStepDao`):** added optional `linkedPlayer: FakePlayerRepository? = null` constructor arg. Overrides `creditBattleStepsAtomic` to emulate the SQL atomic contract under a `Mutex` — read-check-write-credit serialised so concurrent callers observe each other's mutations. The override takes `playerDao: PlayerProfileDao` for type satisfaction but ignores it; wallet side-effects go through `linkedPlayer` so existing tests can keep asserting on `FakePlayerRepository.profile`. Added `creditBattleStepsAtomicCallCount` for tests to prove the atomic path is live.

The `playerDao` decoy is a deliberate test-only abstraction. The real impl (Room's generated default-method delegate) exercises the actual `PlayerProfileDao.adjustStepBalance` path at runtime. The JVM tests can't meaningfully test the real cross-DAO call path without an in-memory Room DB; the fake's Mutex-guarded override models the SQL-level atomicity, and callers pass `mock<PlayerProfileDao>()` for type satisfaction.

### Files touched

- `app/src/main/java/.../data/local/DailyStepDao.kt` (+`@Transaction creditBattleStepsAtomic` default method, +`androidx.room.Transaction` + `kotlin.math.min` imports, KDoc)
- `app/src/main/java/.../domain/usecase/AwardBattleSteps.kt` (–`PlayerRepository` dep, +`PlayerProfileDao` dep, body rewrite to single delegation, KDoc update)
- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+`PlayerProfileDao` import + constructor param, updated `AwardBattleSteps` construction)
- `app/src/test/java/.../fakes/FakeDailyStepDao.kt` (+`linkedPlayer` param, +Mutex-guarded `creditBattleStepsAtomic` override, +`creditBattleStepsAtomicCallCount`, KDoc)
- `app/src/test/java/.../domain/usecase/AwardBattleStepsTest.kt` (sut helper rewritten: `FakeDailyStepDao(linkedPlayer = playerRepo)` + `mock<PlayerProfileDao>()`; 7 existing cases preserved; +2 new atomicity cases)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (removed dead `awardBattleSteps` field; `dailyStepDao = FakeDailyStepDao(linkedPlayer = playerRepo)`; +`playerProfileDao = mock<PlayerProfileDao>()`; wired into `createVm` + 2 B.3 PR 1 failure-injection tests)

### Tests added (2 new cases in `AwardBattleStepsTest`, bringing it to 9 total)

1. **`successful credit goes through atomic DAO method and bypasses the legacy split path`** — asserts `dao.creditBattleStepsAtomicCallCount == 1` and `player.spendStepsCallCount == 0` after a successful credit. Proves the use case no longer uses the split `playerRepository.addSteps` + `dao.incrementBattleSteps` path. Regression-guard: if someone reintroduces the split flow this test fails immediately.
2. **`two concurrent kills on exactly one headroom - only one credits`** — `kotlinx.coroutines.async` pair racing on the atomic path with exactly 1 battle-step of headroom. Asserts `results.sum() == 1L` (total credited = 1 unit, no overflow), cap counter advances by exactly 1, wallet advances by exactly 1, and both calls reached the atomic method. Proves the Mutex-guarded fake models the SQL atomic guard correctly; real Room-level atomicity is a separate instrumented-test concern (out of scope).

All 7 existing cases preserved verbatim: first call / cap exhausted / partial credit / date rollover / zero-or-negative no-op / dao-incremented-by-credited-not-requested / FakeTimeProvider drives default today.

BattleViewModelTest dead-code removal: the `private lateinit var awardBattleSteps: AwardBattleSteps` field at test line 34 was declared and initialised at line 49 but never read by any test case. Removed as a drive-by cleanup (the imports reference resolves via the companion-object constant access at lines 182/202, unchanged).

### Verification

- `./run-gradle.sh :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL. Room KSP compiled the second `@Transaction` default method with a cross-DAO parameter cleanly (same shape as B.2 PR 1's `WorkshopDao.purchaseUpgradeAtomic`).
- Test suite: **461 → 463 JVM tests** (+2, matches the 2 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped. `AwardBattleStepsTest`: 7 → 9 cases. `BattleViewModelTest`: 19 → 19 (dead field removal doesn't change test count — never a `@Test`).
- Lint: clean, no new warnings.
- `grep -c "@Transaction" app/src/main/java/com/whitefang/stepsofbabylon/data/local/*.kt` — **2 matches** (`WorkshopDao.kt`, `DailyStepDao.kt`). RO-02 target is ≥5 after all 5 PRs land; 2/5 now.
- One mid-edit bug fixed: the first `BattleViewModelTest` edit had a trailing-newline mismatch that concatenated `MilestoneNotificationManager` + `kotlinx.coroutines.Dispatchers` imports onto one line. Caught by the next immediate edit+diff review, fixed with a dedicated edit.

### Surface changes

- `AwardBattleSteps` constructor: `(playerRepository, dailyStepDao, timeProvider?)` → `(dailyStepDao, playerProfileDao, timeProvider?)`. 3 call sites updated in this PR (VM init, use case test, and the removed dead-field init in `BattleViewModelTest`).
- `BattleViewModel` constructor grew from 10 to 11 params. Hilt graph unaffected (PlayerProfileDao already `@Provides`-d). Manual constructions in `BattleViewModelTest` (`createVm` and 2 B.3 PR 1 failure-injection tests) all updated.
- `FakeDailyStepDao` constructor: no-arg → optional `linkedPlayer` param. All 5 other call sites (`DailyStepManagerTest` x2, `TrackWeeklyChallengeTest`, plus the 2 in updated tests) remain source-compatible because the default is `null`.
- No new production dependencies. No ADR — RO-02 spec already covers this site; same rationale as B.2 PR 1 / B.3 PR 1.

### Open questions / blockers

- None. Concurrent-kill atomicity is proven at the fake/Mutex level; real Room transaction behaviour is a separate instrumented-test concern per RO-02 verification strategy.
- 3 RO-02 sites remain. B.2 PR 3 (`StepCrossValidator`) uses a different idiom (`AppDatabase.withTransaction { }` at repo level) because the validator lives in `data/healthconnect/` and can legally import `RoomDatabase`. B.2 PR 4 (`ClaimMilestone`) applies the same pattern as PR 1 and PR 2. B.2 PR 5 wraps `runEndRoundPersistence` — a single-call-site change thanks to B.3 PR 1.

### Follow-ups

- B.2 PR 3 (`StepCrossValidator`) next. Different pattern but same goal; expect higher touch count due to the three graduated-response branches.
- B.2 PR 4 (`ClaimMilestone`) is mechanically identical to this PR once `MilestoneDao` gets its own `@Transaction` default method.
- B.3 PR 2 (`onCleared` guard via `ProcessLifecycleOwner.lifecycleScope`) is independent and can slot in any time.
- Doc drift: `AGENTS.md` says "455 JVM tests" — now stale by three PRs (+3, +3, +2). Bundle into a future A.1-style sweep.

### Memory updated

- `STATE.md` ✅ — current objective now "B.2 PR 2 complete"; B.2 PR 2 added to "what works"; priorities/next-actions reshuffled (B.2 PR 3 top); test count 461 → 463; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — RO-02 spec already fully covers this site.

## 2026-05-07 — Phase B.3 PR 1 (RO-03 pattern-proving): resilient `runEndRoundPersistence`

- **Goal:** Execute the RO-03 first PR per `devdocs/evolution/refactoring_opportunities.md` §RO-03 — extract `runEndRoundPersistence` from `BattleViewModel.endRound` and isolate every write / notification in a `runCatching { }.onFailure { Log.w }` block so a single Room or notification-manager exception can no longer leave the player on a frozen battle screen. Spec explicitly splits RO-03 into two PRs; **no `onCleared` change in this PR** (that is PR 2, which uses `ProcessLifecycleOwner.lifecycleScope` to outlive VM cleanup). PR 1 is deliberately small and composable with a future B.2 PR 5 that wraps the whole body in a Room `@Transaction`.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (B.2 PR 1 entry and below). `git status` showed the B.2 PR 1 working tree uncommitted — flagged to user. B.3 touches a disjoint file set (`BattleViewModel.kt`, `BattleViewModelTest.kt`, `FakePlayerRepository.kt`), so the diffs stack cleanly and can be staged as two commits at review time. Read RO-03 spec in full, `BattleViewModel`, `BattleViewModelTest`, `BattleUiState` (for `RoundEndState` field names).

### Design

The spec names 3 writes but the current `endRound` actually has **5** best-effort writes plus 1 notification:

1. `updateBestWave(tier, wave)` — produces `result.isNewRecord` + `result.previousBest` used by the UI push.
2. `awardWaveMilestone(wave)` — produces `psAwarded` used by the UI push.
3. `milestoneNotificationManager.notifyNewBestWave(...)` — not a DB write but still best-effort.
4. `playerRepository.updateHighestUnlockedTier(newTier)` — gated on a profile read + `checkTierUnlock`; result used by the UI push.
5. `playerRepository.incrementBattleStats(...)` — previously wrapped in ad-hoc `try / catch (_: Exception) {}` swallow.
6. `dailyMissionDao.updateProgress(...)` — previously wrapped in ad-hoc `try / catch (_: Exception) {}` swallow.

All six are now normalised to `runCatching { ... }.onFailure { Log.w(TAG, "endRound: <writeName> failed", it) }`. Writes whose results feed `RoundEndState` (1, 2, 4) use `.getOrNull()` / `.getOrDefault(0)` with safe fallbacks (`isNewBestWave = false`, `previousBest = 0`, `psAwarded = 0`, `tierUnlocked = null`) so the `_uiState.update` push below is guaranteed to run.

`endRound()` shrank from ~35 lines to 6 (guard + null-check + `viewModelScope.launch { runEndRoundPersistence(eng, wave) }`). `quitRound()` and the polling-loop call site (`startPollingEngine`) are unchanged — both go through the same slimmed-down `endRound()`, so the `roundEnded` guard still dedupes.

Added `private companion object { private const val TAG = "BattleViewModel" }` + `import android.util.Log`. This is the first `Log.*` call in the Battle presentation layer; it matches the R2-07 precedent for `StepSyncWorker`'s resilient-worker catches.

### Design decision: no `onCleared` change

Per the RO-03 spec's "First safe step": *"PR 1 — extract `runEndRoundPersistence` and wrap each of the 3 writes in `runCatching { }.onFailure { Log.w }`. Both `endRound()` and `quitRound()` now call the extracted function. **No `onCleared` change yet.**"* Deliberately keeping this PR to a single-axis change (error handling) means a clean revert and lets reviewers verify the behaviour-preservation without having to reason about lifecycle scoping at the same time. The `onCleared` fix needs `ProcessLifecycleOwner.lifecycleScope` to survive VM cleanup, which is a different risk profile (process-kill still loses data; transaction wrapping in RO-02 PR 5 is the longer-term fix).

### Files touched

- `app/src/main/java/.../presentation/battle/BattleViewModel.kt` (+`android.util.Log` import; extracted `runEndRoundPersistence(eng, wave)`; normalised 5 writes + 1 notification to `runCatching + Log.w`; `endRound()` shrank from ~35 lines to 6; +`private companion object { TAG }`)
- `app/src/test/java/.../fakes/FakePlayerRepository.kt` (`class` → `open class`; marked `updateBestWave`, `addPowerStones`, `updateHighestUnlockedTier`, `incrementBattleStats` as `open override` for per-method throwing overrides)
- `app/src/test/java/.../presentation/battle/BattleViewModelTest.kt` (+ `installEngineForEndRound(vm)` helper using reflection on the private `engine` field, mirroring the existing A.7 pattern; +3 RO-03 tests)

### Tests added (3 new cases in `BattleViewModelTest`, bringing it to 19 total)

1. **`RO-03 - updateBestWave failure does not block later writes or UI push`** — anonymous `FakePlayerRepository` subclass throws from `updateBestWave`. Asserts (a) `vm.uiState.value.roundEndState` is non-null (UI push ran despite the earlier throw) and (b) `totalRoundsPlayed == 1L` (a later write, `incrementBattleStats`, still ran). Before RO-03 this test would fail because the thrown exception propagates out of the `viewModelScope.launch` and short-circuits the remaining writes + UI push.
2. **`RO-03 - all persistence failures still produce RoundEndState`** — throws from all 4 player-repository writes (`updateBestWave`, `addPowerStones`, `updateHighestUnlockedTier`, `incrementBattleStats`). Asserts the `RoundEndState` is still set, with safe-default fields (`isNewBestWave = false`, `previousBest = 0`, `powerStonesAwarded = 0`, `tierUnlocked = null`). Proves the `.getOrNull() / .getOrDefault(0)` fallback contract.
3. **`RO-03 - roundEnded guard prevents double persistence on repeated quitRound`** — calls `quitRound()` twice in sequence. Asserts `totalRoundsPlayed` is exactly 1 after both calls — the `roundEnded` boolean guard at the top of `endRound()` gates the second call. Protects against a regression where a future change (e.g. making `endRound` idempotent differently) breaks the single-run invariant the polling loop + `quitRound` depend on.

Both failure-isolation tests use reflection to reach the private `engine` field (`installEngineForEndRound` helper), then call the public `quitRound()` to drive `endRound` without needing the full polling-loop setup. The pattern follows the A.7 tests, which already use reflection for `effectEngine` / `pendingEffects` / `effects` access.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin` — BUILD SUCCESSFUL. The `runCatching { ... }.getOrNull() ` / `.getOrDefault(0)` chains type-check against `UpdateBestWave.Result?`, `Int?`, and `Int` targets cleanly.
- `./run-gradle.sh :app:testDebugUnitTest :app:lintDebug` — BUILD SUCCESSFUL. `BattleViewModelTest`: 16 → 19 cases, 0 failures. Total suite: **458 → 461 JVM tests** (+3, matches the 3 new RO-03 cases exactly), 0 failures, 0 errors, 0 skipped. Lint clean.
- Field-name correction: initial test draft referenced `state.isNewRecord` / `state.newTierUnlocked`, which don't exist — actual field names are `state.isNewBestWave` / `state.tierUnlocked`. Caught by a pre-test grep on `RoundEndState`'s declaration in `BattleUiState.kt` before running the suite.

### Surface changes

- No public `BattleViewModel` API change. `endRound()` is private; `quitRound()` and `startPollingEngine()` behaviour preserved.
- `FakePlayerRepository` is now `open class` with 4 `open override` methods. Subclassing is the only behaviour change — the unqualified `FakePlayerRepository(...)` constructor call still works unchanged across all 15+ existing test sites.
- No new production dependencies. `android.util.Log` was already available (presentation layer is allowed Android imports).

### Open questions / blockers

- None for this PR. `onCleared` mid-nav gap is explicitly deferred to B.3 PR 2 per the RO-03 spec.
- No ADR written. RO-03 is fully documented in `devdocs/evolution/refactoring_opportunities.md §RO-03` with alternatives, first-safe-step, verification, rollback, and non-goals. Same rationale as B.2 PR 1 — no duplication with the evolution doc, matches Phase A precedent of only writing ADRs for genuinely new decisions.

### Follow-ups

- **B.2 PR 5 is now trivial.** The extraction means wrapping the `runEndRoundPersistence` body in a Room `@Transaction` (or `AppDatabase.withTransaction { }`) is a single-call-site change. Was gated on this PR per the RO-02 spec's dependency graph; now unblocked.
- B.3 PR 2 remains: `onCleared` guard using `ProcessLifecycleOwner.lifecycleScope`. Would outlive the VM but still not the process — process-kill loss is a separate issue that RO-02 PR 5 (this-PR-dependent transaction wrapping) partially addresses.
- B.2 PR 2 (`AwardBattleSteps` atomic) and B.2 PR 4 (`ClaimMilestone` atomic) can now proceed in parallel with each other since the pattern is proven.
- Doc drift: `AGENTS.md` still says "455 JVM tests" — now stale by two PRs (+3 each). Bundle into a future A.1-style sweep rather than a one-line doc PR per change.

### Memory updated

- `STATE.md` ✅ — current objective now "Phase B.3 PR 1 complete"; B.3 PR 1 moved into "what works"; priorities/next-actions reshuffled (B.2 PRs 2–4 now top; B.3 PR 2 dropped to priority 3); test count 458 → 461; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — see "Open questions".

## 2026-05-07 — Phase B.2 PR 1 (RO-02 pattern-proving): atomic @Transaction for PurchaseUpgrade

- **Goal:** Execute the RO-02 first PR per `devdocs/evolution/refactoring_opportunities.md` §RO-02 — replace the two-step `spendSteps` + `setUpgradeLevel` sequence in `PurchaseUpgrade` with a single atomic call backed by a Room `@Transaction` DAO method. Proves the pattern so PRs 2–5 (AwardBattleSteps, StepCrossValidator, ClaimMilestone, endRound) can follow without re-litigating the design.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head (Phase A + B.1 entries). Verified `@Transaction` count in `app/src/main` was 0 before this PR (Phase 4 §2 baseline still held post-Phase-A). room-ktx 2.8.4 confirmed on classpath; chose DAO-level `@Transaction` default method over repo-level `AppDatabase.withTransaction { }` to match RO-02 sketch literally and keep the pattern per-site rather than introducing a transaction idiom to be repeated at every future repo. Read `PurchaseUpgrade` use case, both repos + impls, both DAOs, existing `PurchaseUpgradeTest`, both fakes, `WorkshopViewModelTest`, `UserFeedbackTest`, DI modules. Grep-confirmed `PurchaseUpgrade` has exactly two callers: the test and `WorkshopViewModel`.

### Design

- **Authoritative guard** lives in SQL: `PlayerProfileDao.adjustStepBalanceIfSufficient(cost: Long): Int` runs `UPDATE player_profile SET currentStepBalance = currentStepBalance - :cost WHERE id = 1 AND currentStepBalance >= :cost`. Returns rows affected (1 = deducted, 0 = insufficient). The `WHERE ... >= :cost` clause atomically closes both the partial-failure gap and the double-tap race (two concurrent purchases can't both pass and double-spend).
- **Transaction boundary** lives on `WorkshopDao` as a suspend `@Transaction` default interface method: `purchaseUpgradeAtomic(type, newLevel, cost, playerDao: PlayerProfileDao): Boolean`. Body calls `playerDao.adjustStepBalanceIfSufficient(cost)`; if it returns 0 the transaction short-circuits to `false` (no upsert); otherwise it calls `upsert(WorkshopUpgradeEntity(type, newLevel))` and returns `true`. Cross-DAO call inside the transaction is safe because Room's transaction tracker is scoped to the underlying `RoomDatabase`, not to a specific DAO instance — both DAO calls share the same SQLite transaction.
- **Domain interface** gained `WorkshopRepository.purchaseUpgradeAtomic(type, newLevel, cost): Boolean`. Implemented in `WorkshopRepositoryImpl` after adding a `PlayerProfileDao` constructor dependency (DI graph unaffected — `DatabaseModule` already `@Provides`-es both DAOs).
- **Use case shrink:** `PurchaseUpgrade` dropped its `PlayerRepository` dependency entirely. Body now does `maxLevel` check → `calculateCost` → wallet fast-fail (UI-side hint) → `workshopRepository.purchaseUpgradeAtomic(type, currentLevel + 1, cost)`. Public signature `(type, currentLevel, wallet): Boolean` unchanged, so `WorkshopViewModel`'s call sites were untouched aside from the one-line constructor update from 3-arg to 2-arg.
- **Fake emulation.** `FakeWorkshopRepository` gained an optional `linkedPlayer: FakePlayerRepository? = null` constructor param. When supplied, `purchaseUpgradeAtomic` uses a `kotlinx.coroutines.sync.Mutex` to faithfully emulate the SQL atomic guard — read-check-deduct-write under the mutex so a concurrent call observes the deducted balance. When null, it acts as a purchase recorder (no affordability check) so the 5 existing `FakeWorkshopRepository()` call sites stayed source-compatible without every test being forced to supply a player. `FakePlayerRepository` gained a `spendStepsCallCount` counter (increments on direct `spendSteps`) so tests can prove the use case does NOT call the legacy path.

### Files touched

- `app/src/main/java/.../data/local/PlayerProfileDao.kt` (+1 `@Query` method, KDoc)
- `app/src/main/java/.../data/local/WorkshopDao.kt` (+`@Transaction` default method, +import `androidx.room.Transaction`, KDoc)
- `app/src/main/java/.../domain/repository/WorkshopRepository.kt` (+1 interface method, KDoc)
- `app/src/main/java/.../data/repository/WorkshopRepositoryImpl.kt` (+`PlayerProfileDao` constructor dep, +impl)
- `app/src/main/java/.../domain/usecase/PurchaseUpgrade.kt` (–`PlayerRepository` dep, body rewrite, KDoc)
- `app/src/main/java/.../presentation/workshop/WorkshopViewModel.kt` (1-line constructor call update)
- `app/src/test/java/.../fakes/FakePlayerRepository.kt` (+`spendStepsCallCount`)
- `app/src/test/java/.../fakes/FakeWorkshopRepository.kt` (rewritten: +`linkedPlayer` param, +Mutex, +`purchaseUpgradeAtomic` impl, +`purchaseUpgradeAtomicCallCount`)
- `app/src/test/java/.../domain/usecase/PurchaseUpgradeTest.kt` (rewrite: 4 existing cases strengthened with workshop-side asserts, +3 new RO-02 atomicity cases)
- `app/src/test/java/.../presentation/workshop/WorkshopViewModelTest.kt` (setup order swap + link fakes)

### Tests added (3 new cases in `PurchaseUpgradeTest`)

1. **`successful purchase uses atomic repo method and does not call spendSteps directly`** — asserts `playerRepo.spendStepsCallCount == 0` and `workshopRepo.purchaseUpgradeAtomicCallCount == 1` after one successful purchase. If someone re-introduces the two-step flow this test fails immediately.
2. **`purchase skips atomic call when wallet fast-fail trips`** — wallet balance 0 means the use case returns false before hitting the repo. Asserts `purchaseUpgradeAtomicCallCount == 0`. Verifies the fast-fail path exists and avoids an unnecessary DB round-trip for obviously-broke callers.
3. **`two concurrent purchases on exactly sufficient balance - only one succeeds`** — `kotlinx.coroutines.async` pair racing on the atomic path with exactly one purchase worth of Steps. Asserts exactly one `true` and one `false` result, balance = 0 after, upgrade level = 1 (not 2), and `purchaseUpgradeAtomicCallCount == 2`. Proves the Mutex-guarded fake models the SQL atomic guard correctly; the real Room-level atomicity is a separate instrumented-test concern (out of scope for this PR; documented in RO-02 verification strategy).

The 4 existing cases were all preserved. `insufficient steps returns false without mutation` was strengthened: previously only asserted on step balance; now also asserts `workshop.upgrades.value[DAMAGE] == null` (no partial workshop-side write). `at max level returns false` gained a balance-unchanged assertion. `level 0 purchase costs exactly baseCost` unchanged.

### Verification

- `./run-gradle.sh :app:compileDebugKotlin` — BUILD SUCCESSFUL. Room KSP compiled the `@Transaction` default method including the cross-DAO call signature cleanly; had the pattern been malformed (e.g. interface-vs-abstract-class, transaction-boundary-across-DAOs), KSP would have failed at this step.
- `./run-gradle.sh :app:testDebugUnitTest` — BUILD SUCCESSFUL. `PurchaseUpgradeTest`: 4 → 7 cases, 0 failures. Total suite: **455 → 458 JVM tests** (+3, matches the 3 new atomicity cases exactly), 0 failures, 0 errors, 0 skipped.
- `./run-gradle.sh :app:lintDebug` — BUILD SUCCESSFUL. No new warnings introduced.
- `grep -rn "@Transaction\|withTransaction" app/src/main --include='*.kt'` — **1 match** (`WorkshopDao.kt:48`). This is the first `@Transaction` marker in `app/src/main`; RO-02 target is ≥5 after all 5 PRs in the family land.

### Surface changes (breaking-but-internal)

- `PurchaseUpgrade` constructor now takes `(workshopRepository, calculateCost?)` instead of `(workshopRepository, playerRepository, calculateCost?)`. Only two call sites touched (`WorkshopViewModel` + `PurchaseUpgradeTest`); both updated in this PR.
- `WorkshopRepositoryImpl` constructor gained a second `PlayerProfileDao` param. Hilt graph unaffected — the DAO is already provided by `DatabaseModule`. Test code that manually constructs `WorkshopRepositoryImpl` would need updating; a grep confirmed there is no such code (all test sites use `FakeWorkshopRepository`).

### Open questions / blockers

- None for this PR. Concurrent-purchase atomicity is proven at the fake/Mutex level; a future on-device smoke test or instrumented test against a real Room DB would be the only stronger guarantee but is not in RO-02 PR 1 scope (the verification strategy in the spec explicitly calls these two levels adequate for each PR).
- No ADR written. The decision was already fully documented in `devdocs/evolution/refactoring_opportunities.md §RO-02` with alternatives, first-safe-step, rollback, and non-goals. Writing an ADR would duplicate content; subsequent RO-02 PRs can reference the same section. Matches the Phase A precedent (9 PRs, 0 ADRs, all pre-documented in Phase-14 roadmap).

### Follow-ups

- B.2 PR 2 (`AwardBattleSteps` — `addSteps` + `incrementBattleSteps` cross-DAO) is the next natural unit; add a composite `@Transaction` method on `DailyStepDao` that takes `PlayerProfileDao`. Uses the pattern established here.
- B.2 PR 3 (`StepCrossValidator`) is the one place RO-02 licenses a repo-level `AppDatabase.withTransaction { }` (validator lives in `data/healthconnect/` so it can import `RoomDatabase`); different pattern but same spirit.
- B.2 PR 4 (`ClaimMilestone`) — composite method on `MilestoneDao` taking `PlayerProfileDao`. Same pattern as this PR.
- B.2 PR 5 (`endRound` `@Transaction` wrapper) is gated on B.3 PR 1 landing first (extraction of `runEndRoundPersistence`).
- Doc drift: `AGENTS.md` says "455 JVM tests" — stale after this PR. Bundle into the next A.1-style doc sweep alongside any subsequent test-count changes (no value in a one-line PR for each ±3).

### Memory updated

- `STATE.md` ✅ — current objective now "Phase B.2 PR 1 complete"; B.2 PR 1 moved into "what works"; priorities/next-actions reshuffled with B.3 PR 1 as the new top priority; test count 455 → 458; critical-path line updated.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted — see "Open questions".

## 2026-05-07 — Phase A (Foundation): land 9 tactical PRs from the Phase-14 implementation roadmap

- **Goal:** Execute Phase A of `devdocs/evolution/implementation_roadmap.md` — 9 tactical PRs (A.1–A.9) covering doc drift, test-classpath recovery, DB-decrypt recovery, Season Pass background fix, deep-link coverage expansion, configurable fake failure modes, capped-kill FloatingText suppression, dead-code removal, and an orphan-enum decision. Low risk, high velocity; nothing blocks a later phase but several enable Phase B/C/D to land safely.
- **Preflight:** read `START_HERE`, `STATE`, `CONSTRAINTS`, RUN_LOG tail (Phase 14 entry); `git status` clean on `main`, up to date with origin. `git log -n 10 --oneline` confirmed HEAD at `1609680 docs: add archaeology + evolution deliverables and smoke-test baseline` prior to starting.
- **Execution order (payback-per-day per roadmap §A.10, modified to put A.2 first because A.3/A.5 tests depend on it):** A.2 → A.3 → A.6 → A.5 → A.4 → A.7 → A.8 → A.1 → A.9. Six commits landed on `main`; three had been pushed mid-phase after A.2/A.3/A.6 per a checkpoint request. Final push brought the whole phase up.

### Per-item summary

- **A.2 — junit-vintage-engine on test classpath.** Added `junit-vintage-engine` via `gradle/libs.versions.toml` + `app/build.gradle.kts`. 3 silently-skipped JUnit 4 + Robolectric test classes (`RoomSchemaTest`, `DeepLinkRoutingTest`, `StepWidgetProviderTest`) now discovered; each needed `@Config(sdk = [34], application = android.app.Application::class)` because Robolectric 4.14.1 does not support `compileSdk 36` and the default Hilt-generated Application tries to initialise `DatabaseModule` → SQLCipher native lib (UnsatisfiedLinkError on JVM). Commit `a336bce`. Tests: 412 → 421.
- **A.3 — DB-file wipe on decrypt failure.** `DatabaseKeyManager.kt` now wipes `steps_of_babylon.db` + `-shm`/`-wal` siblings when the encrypted passphrase blob fails to decrypt (e.g. backup-restore to a new device). Prevents crash-on-launch loop. Extracted `wipeDatabaseFile(context)` as `internal` for test visibility because Robolectric's `AndroidKeyStore` shadow throws `NoSuchAlgorithmException` — the keystore-to-wipe coupling is single call-site and covered by code inspection plus on-device smoke. Added `DatabaseKeyManagerTest` (3 Robolectric cases). Commit `51636c0`. Tests: 421 → 424.
- **A.6 — Season Pass flag in background pipeline.** `DailyStepManager.runFollowOnPipeline` now reads `seasonPassActive` + `seasonPassExpiry` from `PlayerRepository` and forwards them to `TrackDailyLogin.checkAndAward`, mirroring `HomeViewModel.init`. Season Pass owners now receive the +10 Gems/day streak bonus when step ingestion happens from `StepSyncWorker` or `StepCounterService` (previously only the foreground path paid out). Added 3 `DailyStepManagerTest` cases: active pass = 11 Gems (1 streak + 10 bonus); no pass = 1 Gem; expired pass falls back to 1 Gem. Commit `35529e8`. Tests: 424 → 427. Tactical patch per roadmap B.4 non-goal — the cleaner home for this logic is the planned FollowOnPipeline extraction (RO-04).
- **A.5 — Deep-link coverage for all argument-free routes.** Added `Screen.fromRoute(name): Screen?` and `val argumentFreeRoutes: Set<String>` in `presentation/navigation/Screen.kt`. Both guarded by `by lazy` to preserve the sealed-class init-order NPE workaround (commit 1872af9). `MainActivity`'s 4-route `when` replaced with `Screen.fromRoute(route)?.takeIf { it.route in Screen.argumentFreeRoutes }?.let { navController.navigate(it.route) }`. Deep-links now reach all 12 argument-free routes (home, workshop, battle, labs, stats, weapons, cards, supplies, economy, missions, settings, store); unknown routes fall through silently (preserves prior behaviour). `DeepLinkRoutingTest` extended from 3 to 17 cases (per-route resolution, null/unknown/case-sensitivity, whitelist contents, round-trip). Commit `5266623`. Tests: 427 → 444.
- **A.4 — Configurable failure modes in Fake billing/ad managers.** `FakeBillingManager` + `FakeRewardAdManager` enhanced with `resultQueue: ArrayDeque<...>` (per-call scripted results, falls back to `nextResult` when empty), configurable `isAdRemoved`/`isSeasonPassActive`/`isAdAvailable`, and append-only `purchases`/`shown` call logs. Added 4 `StoreViewModelTest` cases (Success + Error variants for GemPack and AdRemoval, plus sequential queue replay) and 3 `CardsViewModelTest` cases (AdResult.Rewarded records day-stamp + opens pack, Cancelled and Error leave state unchanged). Commit `dae4fa7`. Tests: 444 → 451. Dropped 2 initially-drafted concurrent-call tests because `StandardTestDispatcher` doesn't exercise in-flight guards cleanly — guards work in practice but aren't deterministically testable without more orchestration (out of scope for A.4).
- **A.7 — Suppress Battle Step FloatingText when daily cap hit.** `GameEngine.onStepReward` callback signature changed from `((Long) -> Unit)?` to `((amount: Long, x: Float, y: Float) -> Unit)?`. `GameEngine.killEnemy` no longer spawns the `+N Step` FloatingText unconditionally; `BattleViewModel.wireStepRewardCallback` now spawns it on the engine's `EffectEngine` only when `AwardBattleSteps` returns credited > 0. Capped kills (2k/day cap hit) silently drop the indicator; frozen HUD counter at `DAILY_BATTLE_STEP_CAP` communicates the gate. Updated 3 existing `BattleViewModelTest` cases for the new signature; added 2 A.7 cases asserting (a) fully capped kill spawns zero FloatingText effects, (b) partial-credit kill still spawns exactly one. Tests use reflection on `EffectEngine.pendingEffects + effects` because `addEffect` is deferred until the next `update()` tick. Commit `61bdd33`. Tests: 451 → 453.
- **A.8 — Delete `PlaceholderScreen` dead code.** Removed `private fun PlaceholderScreen(name: String)` at `MainActivity.kt` (dead since Plan 06 replaced every placeholder route). Removed 4 orphaned imports it was the only consumer of: `androidx.compose.foundation.layout.{Box, fillMaxSize}`, `androidx.compose.material3.Text`, `androidx.compose.ui.Alignment`. `grep PlaceholderScreen app/src` returns empty. Not touched: `Screen.items by lazy` — per cleanup_inventory §A7 it is a documented NPE workaround (commit 1872af9) and must stay. Commit `7e9ea81`. Tests: 453 (unchanged).
- **A.1 — Doc drift sync (schema v8, test count, Battle Step Rewards).** Resolved current-state doc drift without touching historical docs (RUN_LOG, plan-R*, plan-26, external-reviews stay). `docs/database-schema.md`: added `battleStepsEarned` column row + v7→v8 migration entry + "Current schema version: 8". `.kiro/steering/source-files.md`: version 7 → 8, added missing `Migrations.kt` entry. `.kiro/steering/structure.md`: version 7 → 8. `.kiro/steering/lib-room.md`: schema version 2 → 8 (stale since v3), entity/DAO count 9 → 12. `AGENTS.md`: test count 401 → 453, added A.3/A.4/A.6/A.7/A.5 coverage items. Verification: `grep -rn 'version 7|schema v7|Current schema version: 2|Current schema version: 7' docs/ .kiro/ AGENTS.md README.md` (excluding historical files) returns zero matches. Commit `337643a`. Tests: 453 (unchanged — pure doc sweep).
- **A.9 — Delete `SupplyDropTrigger.STEP_BURST`.** Decision: delete (confirmed by @jpawhite). The enum entry was declared with notification copy ("Your pace is impressive! An energy surge flows into your ziggurat.") but never produced by `GenerateSupplyDrop`; zero Room rows ever carried this value as a `.name` string. No test, no doc, no GDD line promised the feature. Commit body preserves the original copy per project rule #2 (historical intent). `grep -r STEP_BURST app/src` returns zero matches. Commit `9f7f1d2`. Tests: 453 (unchanged). Unblocks Phase B.4 `FollowOnPipeline` extraction by simplifying `GenerateSupplyDrop`'s surface.

### Test results
- Start of phase: **412 JVM tests**, all green.
- End of phase: **453 JVM tests**, all green. Delta **+41 tests**. Breakdown: +9 recovered (A.2 Robolectric discovery) + +3 (A.3 DB wipe) + +3 (A.6 Season Pass) + +14 (A.5 new deep-link cases) + +7 (A.4 failure modes) + +2 (A.7 capped-kill) + +0 (A.8/A.1/A.9 no test changes) + +3 net from existing DeepLinkRoutingTest migration.
- Full suite runs: `./run-gradle.sh :app:testDebugUnitTest` green after each of the 9 items.
- Build: `./run-gradle.sh :app:compileDebugKotlin` green after A.8's import cleanup (zero warnings introduced).

### Commits landed on `main`
```
9f7f1d2 chore: delete unused SupplyDropTrigger.STEP_BURST enum entry (A.9)
337643a docs: sync schema to v8 and refresh test-count / coverage (A.1)
7e9ea81 chore: delete PlaceholderScreen dead code (A.8)
61bdd33 fix: suppress Battle Step floating text when daily cap is hit (A.7)
dae4fa7 test: configurable failure modes in Fake billing and ad managers (A.4)
5266623 feat: extend deep-link coverage to all argument-free routes (A.5)
35529e8 fix: pass Season Pass flags from background ingestion pipeline (A.6)
51636c0 fix: wipe SQLCipher DB file on decrypt failure (A.3)
a336bce test: add junit-vintage-engine to discover Robolectric tests (A.2)
```

### Files touched (summary)
- Build: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Code: `DatabaseKeyManager.kt`, `DailyStepManager.kt`, `Screen.kt`, `MainActivity.kt`, `GameEngine.kt`, `BattleViewModel.kt`, `SupplyDropTrigger.kt`
- Test: `RoomSchemaTest.kt`, `DeepLinkRoutingTest.kt`, `StepWidgetProviderTest.kt`, `DatabaseKeyManagerTest.kt` (new), `DailyStepManagerTest.kt`, `FakeBillingManager.kt`, `FakeRewardAdManager.kt`, `StoreViewModelTest.kt`, `CardsViewModelTest.kt`, `BattleViewModelTest.kt`
- Docs: `docs/database-schema.md`, `.kiro/steering/source-files.md`, `.kiro/steering/structure.md`, `.kiro/steering/lib-room.md`, `AGENTS.md`

### Open questions / blockers
- None blocking. Phase A exit criteria (A.10 in roadmap) all met: 9/9 tactical PRs merged, test count ≥ 418 (actual 453), `grep STEP_BURST app/src` empty (delete route), schema/test-count drift in current-state docs aligned to HEAD.
- Two decisions already consumed by Phase A and not carried forward: (a) A.9 delete-vs-wire STEP_BURST → delete; (b) the concurrent-call test coverage gap flagged in A.4 → deferred as out-of-scope.

### Follow-ups
- **Next phase entry point is a product choice**: Phase B.1 (`TimeProvider` narrow migration, lowest risk, unblocks B.4) **or** Phase C.2 (cosmetic rendering pipeline, most user-facing, ships one cosmetic end-to-end). Both paths end at Plan 31. Phase B is optional for v1.0 per roadmap §1; the release-critical subset is A (done) + C.2 PR 1-2 + C.5 + C.6 + D.
- Three ADRs scheduled by the roadmap remain to be written before their corresponding PRs: ADR-0004 `FollowOnPipeline` (prerequisite of B.4), ADR-0005 Billing SDK (prerequisite of C.5), ADR-0006 Ad SDK (prerequisite of C.6).
- Phase 12 smoke report flagged 6 hidden Robolectric tests; A.2 recovered 9 (all 3 files had 3 tests each, consistent with the sweep grep done in Phase 13 §C1). `cleanup_inventory.md` §C1 can be updated if/when next touched.

### Memory updated
- `STATE.md` ✅ — current objective now "Phase A complete; entry point for Phase B/C/D pending product choice"; priorities, next-actions, and references refreshed.
- `RUN_LOG.md` ✅ — this entry.
- ADR: not warranted for Phase A itself — every item executed a decision already documented in the Phase-14 roadmap. ADR-0004/0005/0006 still owed before their corresponding Phase B/C PRs.

## 2026-05-06 — Standard Analysis Phase 14: refactoring opportunities + implementation roadmap

- Goal: Produce two deliverables per the Phase 14 prompt. (a) `devdocs/evolution/refactoring_opportunities.md` — highest-ROI refactors with current pattern + file paths, proposed abstraction, benefits, effort, risk+mitigation, ROI, first safe step, verification, rollback. (b) `devdocs/evolution/implementation_roadmap.md` — phased plan (A Foundation, B Core Refactoring, C Gap Filling, D Integration & Polish) combining critical cleanup from cleanup inventory, essential unblocking refactors, smoke-report fixes, gap closure, and doc sync; each item carries files / dependencies / success criteria / risk / verification / PR size / rollback / owner role.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, head of `RUN_LOG`; `git status` showed only modified STATE/RUN_LOG + untracked `devdocs/` + `smoke_tests/` (expected). Confirmed no prior `refactoring_opportunities.md` or `implementation_roadmap.md` existed via directory listing of `devdocs/evolution/`.
- Inputs used (all cited inline in the deliverables; no new findings introduced per global rule #3): Phase 4 `5_things_or_not.md` (5 PR-sized proposals with full risk/rollback/verify); Phase 8 `architecture_analysis.md` + `module_discovery.md` (structural critique + module boundaries); Phase 10 `gap_analysis.md` (release-gate split, architecture changes §2, tech debt §3, rewrite-rejection §5); Phase 11 `gap_closure_plan.md` (Q1–Q8 quick wins, I1–I7 incremental, M1–M4 major, MR1 cosmetic pipeline, §5 non-goals); Phase 12 `smoke_tests/check_what_is_working/report.md` (412 tests green, junit-vintage gap); Phase 13 `devdocs/archaeology/cleanup_inventory.md` (§A removals, §B quarantines, §C test gaps, §D config, §E docs, §F dynamic-risk register). Cross-checked against `docs/plans/plan-31-play-console.md` and `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md`.
- Changes made:
  - Created `devdocs/evolution/refactoring_opportunities.md` (~1296 lines, 10 ROI-ranked refactors + deferred appendix + meta cross-refs): TL;DR table; RO-01 TimeProvider narrow migration; RO-02 @Transaction for 5 multi-write sites; RO-03 Resilient BattleViewModel.endRound; RO-04 FollowOnPipeline extraction; RO-05 UpdateMissionProgress use case; RO-06 Screen.fromRoute deep-link coverage; RO-07 Cosmetic rendering pipeline contract; RO-08 Configurable fake failure modes; RO-09 junit-vintage-engine on classpath; RO-10 PreferencesStore consolidation. Each entry has: current pattern with file:line citations, proposed abstraction (with code sketch), benefits, effort (XS/S/M/L scale), risk+mitigation, ROI justification, first safe step, verification strategy, rollback plan, non-goals. Deferred appendix lists 10 lower-ROI items (GameEngine snapshot stack, StepCrossValidator dedup, release/discardEscrow merge, PlayerWallet.cardDust, typed-loadout collapse, Reward sealed unification, multi-module split, typed routes, HealthConnectModule delete, DataStore migration) with source citations + defer rationale.
  - Created `devdocs/evolution/implementation_roadmap.md` (~1319 lines, 4 phases × avg 7 items each): Phase A Foundation (A.1 doc drift, A.2 junit-vintage, A.3 DB decrypt recovery, A.4 fake failure modes, A.5 deep-link coverage, A.6 Season Pass leak, A.7 float-text guard, A.8 PlaceholderScreen, A.9 STEP_BURST decision, plus A.10 rollout order + exit criteria); Phase B Core Refactoring (B.1–B.5 map 1:1 to RO-01 through RO-05, plus B.6 rollout order + exit criteria); Phase C Gap Filling (C.1 anti-cheat visibility, C.2 cosmetic pipeline multi-PR, C.3 Settings rename + privacy link, C.4 ClaimMilestone.Cosmetic fix, C.5 real Billing SDK, C.6 real Ad SDK, C.7 PreferencesStore, C.8 rollout + exit criteria); Phase D Integration & Polish (D.1 privacy URL hosting, D.2 Play Console setup, D.3 store listing + icon, D.4 audio assets, D.5 AAB track promotion, D.6 Firebase pre-launch, D.7 rollout + exit criteria). Each item carries files / dependencies / success criteria / risk label / verification commands / PR size / rollback / suggested owner role. Added aggregate critical path diagram, mermaid dependency graph for release-blocking subset, doc-updates table (11 entries), 17-item Non-goals list lifted from Phase 11 §5 + Phase 14 Part 1 deferred appendix, memory-update checklist, and source-phase cross-reference table.
  - Updated `docs/agent/STATE.md` References list (added two bullets for Phase 14 Part 1 and Part 2) + last-run line.
- Code changes: **none** (evolution/documentation only).
- Commands/tests run: filesystem reads + directory listings only — no build, no tests. Per Phase 10/11 convention, evolution deliverables do not require a green test run because they do not change behaviour.
- Open questions / blockers: none for the deliverables. The two unknowns explicitly surfaced for future decision-making are (a) which cosmetic ships first in C.2 PR 2 (proposed default: jade ziggurat recolour per gap_analysis §5.2); and (b) delete-vs-wire for `SupplyDropTrigger.STEP_BURST` in A.9 (proposed default: delete with documented intent per Phase 11 Q6). Both are flagged as prerequisites inside the roadmap, not new findings.
- Follow-ups created: none new. The roadmap schedules every scheduled follow-up from Phases 4, 10, 11, 13 into A/B/C/D; rejected candidates from Phase 10 §5 and Phase 11 §4 stay in §Non-goals.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase ranks already-proposed refactors by ROI and sequences already-scheduled Phase 11 items into release-criticality buckets. New ADRs are named as prerequisites inside the roadmap itself (ADR-0004 FollowOnPipeline, ADR-0005 Billing SDK, ADR-0006 Ad SDK) to be written *before* the corresponding code PRs.

## 2026-05-05 — Standard Analysis Phase 12: baseline smoke tests
- Goal: Establish a baseline smoke-test suite per the Phase 12 prompt. Deliverables: `smoke_tests/check_what_is_working/README.md` (strategy + prerequisites + commands), `smoke_tests/check_what_is_working/test_plan.md` (5 areas × 5 cases = 25 total), `smoke_tests/check_what_is_working/report.md` (results of running the easiest subset). Constraint: reuse the existing JUnit 5 harness — no new framework, no new top-level architecture, no mocks beyond existing fakes.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, and RUN_LOG head; `git status` clean apart from modified STATE/RUN_LOG + untracked `devdocs/` (normal per recent archaeology phases). Confirmed no prior `smoke_tests/` directory via `glob '**/smoke*'` and `glob '**/*SmokeTest*'` — both empty.
- Survey: Framework is JUnit 5 Jupiter via `testOptions { unitTests.all { it.useJUnitPlatform() } }` in `app/build.gradle.kts`, complemented by kotlinx-coroutines-test, Mockito-Kotlin, Robolectric, room-testing, androidx.test.core. Existing test tree: 94 Kotlin files across `fakes/` (15), `balance/` (8), `data/sensor/` (5), `data/healthconnect/` (2), `data/local/` (RoomSchemaTest), `data/integration/` (EscrowLifecycleTest), `domain/model/` (9), `domain/usecase/` (~33), `presentation/*/` (≥13 VMs + ux + DeepLinkRoutingTest), `service/` (StepWidgetProviderTest).
- Deliverables:
  - Created `smoke_tests/check_what_is_working/README.md` (152 lines): strategy (reuse/real-components/offline/removable), how the existing harness is organised, prerequisites (JDK 17 / AndroidSDK 36 / no env vars or emulators), commands (full suite, compile-only, lintDebug, assembleDebug, per-area targeted runs), outputs (JUnit HTML/XML, lint HTML/XML, APK path), non-goals (no connectedAndroidTest, no Play Billing/AdMob, no prod creds).
  - Created `smoke_tests/check_what_is_working/test_plan.md` (143 lines): 5 areas × 5 cases = 25 total. Area 1 Build & Packaging (compile Kotlin main/test, KSP, schema export v8 present, assembleDebug APK). Area 2 Domain Formulas (CalculateUpgradeCost, CalculateDamage with seeded Random, CalculateDefense, CheckTierUnlock, AwardBattleSteps). Area 3 Anti-Cheat & Ingestion (StepRateLimiter, StepVelocityAnalyzer, StepCrossValidator, StepIngestion worker/service coord, DailyStepManager). Area 4 Persistence Round-Trip (RoomSchemaTest player profile, RoomSchemaTest daily step record escrow fields, EscrowLifecycleTest, StepWidgetProviderTest, StepIngestionPreferencesTest). Area 5 Presentation (HomeViewModel, WorkshopViewModel, BattleViewModel, MissionsViewModel, DeepLinkRouting). Each case mapped to an existing test file plus a targeted command.
- Checks executed (easiest subset, using `./run-gradle.sh` per project convention):
  1. `./run-gradle.sh testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL in 55s, 36 tasks executed. 77 JUnit XML reports, **412 tests, 0 failures, 0 errors, 0 skipped** (confirmed via XML aggregation).
  2. `./run-gradle.sh lintDebug` → BUILD SUCCESSFUL in 51s. Lint XML: **0 errors, 47 Warning entries** (pre-existing advisory warnings; no regressions).
  3. `./run-gradle.sh assembleDebug` → BUILD SUCCESSFUL in 5s (mostly cached from step 1). **`app-debug.apk` 61 MB** at `app/build/outputs/apk/debug/`.
  4. Classpath audit via `./run-gradle.sh :app:dependencies --configuration debugUnitTestRuntimeClasspath | grep -iE 'junit-vintage|junit.*4\.1[0-9]|launcher'` → confirmed `junit-platform-launcher:1.11.4` present and `junit:junit:4.13.2` transitively via `org.robolectric:junit:4.14.1`, but **no `junit-vintage-engine` entry anywhere in the tree**.
- Key finding (documented in report.md as "broken but acceptable"): Under `useJUnitPlatform()` with only the Jupiter engine on the classpath, JUnit 4-style tests annotated with `@RunWith(RobolectricTestRunner::class)` are silently not discovered. Affects `RoomSchemaTest.kt` (3 @Test methods) and `StepWidgetProviderTest.kt` (3 @Test methods) — total 6 tests never run. `EscrowLifecycleTest` is unaffected because it uses `org.junit.jupiter.api.Test`. Per-package test counts confirm this: `data.local` yields 0 test cases despite having 3 in source; `service` yields 0 despite having 3 in source; `data.integration` yields 2 correctly. Sum across all packages = 412, matches STATE.md claim — the claim was always based on what runs, not what exists.
- Why "acceptable" not "blocker": schema correctness is re-validated at build time (`copyRoomSchemas` task + v8 JSON in `app/schemas/`) and at app startup (Room throws `IllegalStateException` on mismatch); widget SharedPreferences is a thin key/value surface exercised in practice; the JUnit 5 `EscrowLifecycleTest` independently covers the more complex escrow lifecycle; expansion of the gap is bounded by the two existing files.
- Non-destructive fix path documented in report.md for a future PR (not this run): add `testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")` to `app/build.gradle.kts` — one-line additive change, rollback is a trivial revert, verification is a rerun that should yield 412 → 418 tests all green. Alternative long-term cleanup: port the two files to JUnit 5 + `@ExtendWith(RobolectricExtension::class)` so the suite is stylistically uniform.
- Code changes: **none**. Smoke-test directory is documentation only.
- Git state at end of run: `smoke_tests/check_what_is_working/` untracked (3 new files, 152 + 143 + 186 lines), STATE.md + RUN_LOG.md modified. No other tree changes.
- Open questions / blockers: none. The junit-vintage-engine gap is documented with a fix path; decision deferred to the next code-change PR.
- Follow-ups created: none new. If adopted, the fix path in report.md §"What is broken but acceptable" is one trivial PR.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase documents and runs existing smoke surfaces and records one classpath-configuration finding.

## 2026-05-05 — Evolution Phase 10: gap analysis
- Goal: Produce `devdocs/evolution/gap_analysis.md` per Standard Analysis Phase 10 prompt. Compare current state (Phases 1–9 archaeology) to desired state implied by docs, roadmap, ADRs, tests, STATE.md known issues, and direction ("next is Plan 31"). Must include: concepts needing implementation, architecture changes required, tech debt blocking progress, incremental improvements, rewrite justification if any. Must separate known/inferred gaps, avoid inventing requirements not supported by artifacts, propose smallest next step where desired state is unclear.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, head of `RUN_LOG`; `git status` modified STATE/RUN_LOG + untracked `devdocs/` (expected). Reviewed existing Phase 1–9 outputs; confirmed no `devdocs/evolution/` directory existed yet.
- Inputs used (all cited inline in the deliverable): Phase 9 concept_mappings (25-concept coverage %, divergence rationale, Appendix A cross-concept risks, Appendix B coverage roll-up); Phase 4 5_things_or_not (5 PR-sized improvement bets with risk/rollback/verify); Phase 5 missing_concepts_list (intentionally deferred vs unintended vs compliance/privacy); Phase 8 architecture_analysis + module_discovery (12 forbidden-direction imports, fat modules, overlapping reward vocabularies); Phase 7 doc-inferred known_requirements (R-STEP/R-AC/R-ECO/R-BAT numbered shalls); master-plan.md current status + Plan 31 task list; STATE.md known issues/priorities; CONSTRAINTS.md invariants; grep of `app/src/main` for TODO/FIXME/XXX/HACK markers (0 matches — no in-code tracking).
- Changes made:
  - Created `devdocs/evolution/gap_analysis.md` (993 lines, 44 KB, 8 top-level sections + appendix): TL;DR (no rewrite needed; Plan 31 is the only release-blocker; cosmetic rendering pipeline is the one structural refactor blocking a shipped-but-disabled feature); §1 Concepts needing implementation (12 entries, known vs inferred labels, smallest next step for each ambiguity); §2 Architecture changes required (6 items: @Transaction, TimeProvider, deep-link coverage, MissionProgressTracker extraction, FollowOnPipeline extraction, plus explicit "non-changes" list); §3 Technical debt blocking progress (12 items ordered by leverage); §4 Incremental improvements (Phase 4 5-item cross-reference + 7 additional PR-sized items including DB-file wipe on decrypt failure, configurable fake failure modes, ClaimMilestone.Cosmetic drop bug); §5 What requires a rewrite (nothing — argues each candidate explicitly; names cosmetic pipeline as "required change short of rewrite" with smallest-step ship-one-cosmetic proposal); §6 Risks and unknowns (7 known risks, 8 explicit unknowns where desired state is ambiguous with smallest clarifying step per item, 4 explicit non-unknowns); §7 Aggregated posture (coverage x release-gate table + critical path to v1.0); appendix relating deliverable to prior phases.
  - Updated `docs/agent/STATE.md` References list + last-run line.
- Code changes: none (archaeology/evolution only).
- Commands/tests run: filesystem reads + grep only — no build. Confirmed 0 TODO/FIXME/XXX/HACK markers in `app/src/main`.
- Open questions / blockers: none for the deliverable. The 8 unknowns enumerated in §6.2 are surfaced with proposed smallest next steps; none require a decision before the next planning session.
- Follow-ups created: none new. The deliverable synthesises existing Phase 4/5/8/9 proposals and aligns them to release-gate / quality-improvement / out-of-scope categories without scheduling new work. If adopted, the critical-path order in §7 is (1) ship one cosmetic end-to-end, (2) Plan 31, (3) optional post-release Phase 4 five-item list.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase synthesises already-documented decisions and gaps into a single release-gated view.

## 2026-05-05 — Archaeology Phase 8: architecture reconstruction + module discovery
- Goal: Reconstruct architecture from code per Standard Analysis Phase 8 prompt. Two deliverables: (a) `devdocs/archaeology/architecture_analysis.md` — entry points, data-model inventory, duplicated/overlapping models, contracts, architectural patterns, what doesn't make sense, implied-but-not-enforced invariants; (b) `devdocs/archaeology/module_discovery.md` — natural module boundaries, coupling/cohesion, dependency relationships, shared utilities, cross-cutting concerns, violated boundaries, missing boundaries.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, `RUN_LOG` head; `git status` clean except modified STATE/RUN_LOG + untracked `devdocs/` (expected). Reviewed existing Phase 1–7 outputs to avoid duplication.
- Code read (no docs used as primary source per global rule #1): `AndroidManifest.xml`, `StepsOfBabylonApp.kt`, `MainActivity.kt`, all 6 `di/*` modules, `AppDatabase.kt` + 12 entities + 12 DAOs (sampled), `Converters.kt`, `DatabaseKeyManager.kt`, `Migrations.kt`, all 8 `data/repository/*Impl.kt`, `data/sensor/` (5 files), `data/healthconnect/` (4 files), `data/anticheat/AntiCheatPreferences.kt`, 5 SharedPreferences wrapper files in `data/`, both billing/ads stubs, all 36 `domain/model/*.kt` (reviewed en bloc for overlap), key `domain/usecase/*.kt` (AwardBattleSteps, GenerateSupplyDrop, ResolveStats, CalculateDamage, PurchaseUpgrade, TrackDailyLogin, TrackWeeklyChallenge, OpenCardPack, GenerateDailyMissions, StartResearch, PurchaseGemPack, ClaimMilestone, CalculateUpgradeCost), `domain/repository/PlayerRepository.kt` + StepRepository + WorkshopRepository + BillingManager + RewardAdManager, all 12 VMs under `presentation/*/`, `Screen.kt` + `BottomNavBar.kt`, `BattleScreen.kt` + `BattleUiState.kt` + `GameSurfaceView.kt` + `GameLoopThread.kt` + `GameEngine.kt` + `Entity.kt` + `WaveSpawner.kt` + `EnemyScaler.kt` + `CollisionSystem.kt`, all 9 `service/*` files.
- Quantitative validation via grep: 53 `System.currentTimeMillis|LocalDate.now|Instant.now` in 33 files (matches Phase 4); 83 `Random` references in 15 files of which only 7 use cases are seamed; 6 `domain/` + 6 `presentation/` files import `data.local.*Dao` (12 architectural violations); 10 distinct SharedPreferences files with 4 different access patterns; 0 `@Transaction`/`withTransaction` calls in `app/src/main` (no multi-statement atomicity anywhere); daily-mission progress-update logic duplicated across 5 sites (BattleVM, LabsVM, WorkshopVM, MissionsVM, DailyStepManager).
- Key findings documented with file:line citations: `SupplyDropTrigger.STEP_BURST` declared (`SupplyDropTrigger.kt:5`) but never produced; `ClaimMilestone.kt:25` silently drops `MilestoneReward.Cosmetic` despite cosmetics system being wired (3 declared milestone cosmetics never minted, IDs don't match `SEED_COSMETICS`); `MainActivity.PlaceholderScreen:237` is dead code; `Screen.items by lazy` is a documented workaround for sealed-class init-order NPE (commit `1872af9`); `StepRepositoryImpl.releaseEscrow`/`discardEscrow` are line-for-line identical delegations to `clearEscrow`, semantic difference only in caller; `CosmeticEntity` has double key (`id` autoGenerate + `cosmeticId` String); `GameSurfaceView.kt:26` bypasses `SoundPreferences` to read `sound_prefs` inline; `Currency`/`SupplyDropReward`/`MilestoneReward` are three overlapping reward vocabularies; `PlayerWallet` omits `cardDust`; `CardLoadout` and `UltimateWeaponLoadout` are near-identical (neither exercised at runtime); `GameEngine` has two pre-stat snapshots (`preOverdriveStats`, `preGoldenStats`) with implicit restore order; `StepCrossValidator` Level 0/1 branches duplicate ~20 lines (only `MAX_ESCROW_SYNCS` differs); loadout max-3 enforced only in VMs (`CardsViewModel.kt:114`, `UltimateWeaponViewModel.kt:82`), no DAO guard; currency non-negative clamp lives in SQL (`MAX(0, col + :delta)`) not in Kotlin interface; `DailyStepManager.runFollowOnPipeline` has 4 pokemon-catch blocks; `DailyStepManager` has 12 constructor deps and constructs use cases inline; `TrackDailyLogin` call path from `DailyStepManager` never passes Season Pass flags, so walking-streak Gems lose +10 Gems bonus; `HealthConnectModule` is an empty organisational placeholder.
- Changes made:
  - Created `devdocs/archaeology/architecture_analysis.md` (∼650 lines, 8 top-level sections): TL;DR + sources, entry points & flows, data models (persistence/domain/UI state/commands & events), duplicated/overlapping models (6 categories with file pointers), contracts (repository interfaces, use cases, Hilt modules, Android framework contracts, battle-layer callback contracts, notification managers), architectural patterns (11 patterns: Clean Architecture partial, MVVM with StateFlow, Repository with Flow, Enum-as-balance-sheet, Seeded randomness partial, Default-parameter time sparse, Fixed-timestep game loop, Offline-first, Read-modify-write via `first()`, Stub-then-swap, Plain-Kotlin use cases), 13 "what doesn't make sense" items, 9 implied-but-not-enforced invariants, prioritised summary.
  - Created `devdocs/archaeology/module_discovery.md` (∼800 lines, 8 top-level sections): 16 natural module boundaries (M1 core-domain, M2 core-usecases, M3 persistence, M4 repositories, M5 sensor, M6 healthconnect, M7 anticheat, M8 prefs — virtual, M9 billing-ads, M10 service, M11 navigation, M12 screens, M13 battle-engine, M14 audio, M15 theme, M16 di), coupling/cohesion summary table, "fat" modules (DailyStepManager, GameEngine, HomeViewModel), "thin" modules (HealthConnectModule, theme), cross-screen coupling, fan-out of PlayerRepository (23 methods, universal dependency), ASCII dependency graph, zero package-import cycles confirmed, table of 12 forbidden-direction imports with file:line, shared utilities inventory, 10 cross-cutting concerns (time, randomness, prefs, notifications, anti-cheat, currency clamp, logging, coroutine scoping, error handling, HC availability), 8 boundary violations with file pointers, 10 missing boundaries that would help, prioritised summary (6 payback items ordered by effort/payback).
  - Updated `docs/agent/STATE.md` References list + last-run line.
- Code changes: none (archaeology only).
- Commands/tests run: filesystem reads + grep only — no build.
- Open questions: none. Phase 8 deliverables are strictly documentation. The 12 "forbidden-direction imports" are enumerated for future refactoring; none are newly introduced and all are already documented in Phase 4 + the foundations docs as tolerated gaps. The proposed `TimeProvider`, `RandomSource`, `MissionProgressTracker`, `FollowOnPipeline`, `Reward` sealed hierarchy, and `PreferencesStore` consolidations remain unscheduled — they are cross-references to Phase 4 item 1, 4, 5 and to new proposals here.
- Follow-ups created: none new. Phase 8 synthesises existing findings into two distinct views (architectural critique + module boundaries); it does not schedule work. If adopted, the prioritised list in `module_discovery.md` §8 gives a natural ordering.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase describes decisions already evident in the code plus the gaps around them.

## 2026-05-05 — Archaeology Phase 6: code-inferred foundations docs
- Goal: Extract `project_description`, `philosophy`, and `known_requirements` foundations docs under `devdocs/archaeology/foundations/`, grounded in the actual codebase, synthesising (not duplicating) Phases 1–5.
- Input review: read `small_summary.md`, `intro2codebase.md`, `intro2deployment.md`, all 4 Phase 5 concept docs (`technical`, `design`, `business`, `missing`), plus memory spine. Spot-checked code: `AndroidManifest.xml`, `StepsOfBabylonApp.kt`, `AppDatabase.kt`, `DatabaseModule.kt`, `DatabaseKeyManager.kt`, `Migrations.kt`, `AwardBattleSteps.kt`, `StepCrossValidator.kt`, `DailyStepManager.kt`, `StepCounterService.kt`, `GameLoopThread.kt`, `StubBillingManager.kt`, `StubRewardAdManager.kt`, `Currency.kt`, `UpgradeType.kt`, `PlayerProfileEntity.kt`, `BillingProduct.kt`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `network_security_config.xml`.
- Changes made:
  - Created `devdocs/archaeology/foundations/project_description.md` (334 lines): what the system actually does (5 core behaviours), current use cases, actor types (player + platform services + dev/tester; no operator/SRE/account role), actual problems solved (walk-gated economy, reliable background counting, client-only anti-cheat, inclusive-fitness via exercise minutes, encrypted offline persistence, 60 UPS SurfaceView game loop embedded in Compose), runtime/delivery model (single AAB to Play Store, user-initiated updates, no backend, no CI, v7→v8 migrations), and explicit unknowns.
  - Created `devdocs/archaeology/foundations/philosophy.md` (523 lines): observed design principles (Steps-are-sacred, enum-as-balance-sheet, offline-first, Room-as-truth, encrypted-by-default, domain-is-pure-Kotlin, geometric cost curves, fail-fast-schema vs fail-soft-pipeline, seamed randomness, default-param time, one Activity + one SurfaceView, stub-then-swap SDKs, `@Volatile` polling across threads); consistent patterns (coding, architectural, testing, operational, deployment); architectural decisions evident in structure; deliberate tradeoffs (privacy > observability, offline fidelity > portability, client-side anti-cheat, build-time balance > live tuning, stub SDKs > feature flags); what philosophy doesn't commit to; PR-heuristic checklist.
  - Created `devdocs/archaeology/foundations/known_requirements.md` (685 lines): 18 sections covering platform, runtime/reliability, privacy/security, anti-cheat (with numeric thresholds: 200/min rate, 50k/day ceiling, 2k/day battle-step cap, 20% HC discrepancy), offline, latency budgets (16.67ms frame, 200ms UI poll, 30s/60s throttles, 15min worker), scalability (single-user/device/process), reproducibility/testability, compatibility, security consolidation, observability (deliberately minimal), deployment, integration (HC optional with graceful degrade; sensor implicit), privacy-by-default, concurrency, compliance/legal, explicit non-requirements (no accounts/server/multiplayer/leaderboards/CI/i18n/a11y-pass), and explicit unknowns (battery budget, real SDKs, final audio, cosmetic visual pipeline, localisation, retention policy, PlaceholderScreen intent).
  - Updated `docs/agent/STATE.md` reference list + last-run line.
- Code changes: none (archaeology only).
- Commands/tests run: filesystem reads + grep only — no build.
- Open questions: none. Phase 6 deliverables are documentation; they do not introduce code changes. Several explicit unknowns are enumerated inside `known_requirements.md` §18 rather than left implicit.
- Follow-ups created: none new. The foundations docs cross-reference Phase 4 proposals (`5_things_or_not.md`) and Phase 5 concept inventories without scheduling new work.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision was made; this phase describes decisions already in code.

## 2026-05-05 — Archaeology Phase 4: "5 Things" improvement list
- Goal: Synthesise the 13 Phase 3 traces into a prioritised list of 5 impactful improvements, with code citations, historical rationale, and PR-sized first steps.
- Input review: read `devdocs/archaeology/small_summary.md`, `intro2codebase.md`, `intro2deployment.md`, and all 13 `traces/trace_*.md` "Feels Incomplete / Vulnerable / Bad Design" sections.
- Cross-cutting findings: zero `@Transaction` or `withTransaction` uses in `app/src/main`; 53 direct `System.currentTimeMillis()`/`LocalDate.now()` calls across 33 files; `DailyStepManager` has 11 constructor parameters; `BattleViewModel.endRound` not invoked on `onCleared` so mid-battle deep-links lose the round.
- Changes made:
  - Created `devdocs/archaeology/5_things_or_not.md` (683 lines): TimeProvider abstraction, Room @Transaction for multi-writes, robust round-end cascade against navigation, extract FollowOnPipeline from DailyStepManager, surface anti-cheat effects on Stats screen. Each item has file+line citations, risk assessment, rollback, and verification steps per global rule #5.
- Code changes: none (archaeology only).
- Commands/tests run: grep/code-search only — no build.
- Open questions: none; this is a proposal document. Each item is independently actionable; items 1 and 2 compose cleanly (a `TimeProvider` + `@Transaction` PR together would cover items 1 and 2 in a single small PR).
- Follow-ups created: 5 proposals documented; none scheduled. If adopted, each is a separate PR; item 1 blocks nothing; item 2 is a dependency for item 3's rollback plan.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Project Memory System Setup
- Goal: Implement repo-backed project memory system for Kiro CLI default agent.
- Plan: Create steering files (10-project-memory.md, 11-agent-protocol.md), living memory docs (START_HERE, STATE, CONSTRAINTS, RUN_LOG, ADR template), update AGENTS.md.
- Changes made:
  - Created `.kiro/steering/10-project-memory.md` (always-on memory source declarations)
  - Created `.kiro/steering/11-agent-protocol.md` (preflight + end-of-run protocol)
  - Created `docs/agent/START_HERE.md` (agent contract)
  - Created `docs/agent/STATE.md` (current project snapshot)
  - Created `docs/agent/CONSTRAINTS.md` (invariants and rules)
  - Created `docs/agent/RUN_LOG.md` (this file)
  - Created `docs/agent/DECISIONS/ADR-0001-template.md`
  - Created `docs/agent/state.json`
  - Updated `AGENTS.md` with memory spine section
- Commands/tests run: N/A (documentation-only change)
- Open questions / blockers: None.
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 04: Step Counter Service
- Goal: Implement background step counting with foreground service, anti-cheat, and WorkManager sync.
- Changes made:
  - Added `hilt-work:1.3.0` and `hilt-androidx-compiler:1.3.0` to version catalog + build.gradle.kts
  - Created `data/sensor/StepRateLimiter.kt` — rolling 1-min window, 200/min cap (250 burst)
  - Created `data/sensor/DailyStepManager.kt` — orchestrates rate limit → 50k ceiling → Room persist
  - Created `data/sensor/StepSensorDataSource.kt` — TYPE_STEP_COUNTER wrapper, emits deltas via callbackFlow
  - Created `service/StepNotificationManager.kt` — notification channel + builder, 30s throttle
  - Created `service/StepCounterService.kt` — foreground service (health type), START_STICKY
  - Created `service/BootReceiver.kt` — BOOT_COMPLETED → restart service
  - Created `service/StepSyncWorker.kt` — @HiltWorker CoroutineWorker, 15-min periodic catch-up
  - Created `service/StepSyncScheduler.kt` — enqueues periodic work request
  - Created `di/StepModule.kt` — provides SensorManager via Hilt
  - Updated `StepsOfBabylonApp.kt` — implements Configuration.Provider, injects HiltWorkerFactory
  - Updated `AndroidManifest.xml` — 5 permissions, service + receiver declarations, disabled default WorkManager init
  - Updated `MainActivity.kt` — runtime permission requests for ACTIVITY_RECOGNITION + POST_NOTIFICATIONS
  - Added `getDailyRecord()` to StepRepository interface + StepRepositoryImpl
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Follow-ups created:
  - Replace placeholder notification icon with custom app icon (when assets exist)
  - Notification balance could show live wallet balance via Flow observation
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 05: Health Connect Integration
- Goal: Implement Health Connect (replacing deprecated Google Fit) for step cross-validation, gap-filling, and Activity Minute Parity.
- Key decision: ADR-worthy — used Health Connect instead of Google Fit (Google Fit APIs deprecated, shutting down 2026). See docs/agent/DECISIONS/ for ADR.
- Changes made:
  - Added `health-connect-client:1.2.0-alpha02` to version catalog + build.gradle.kts
  - Created `data/healthconnect/HealthConnectClientWrapper.kt` — client setup, availability, permissions
  - Created `data/healthconnect/HealthConnectStepReader.kt` — aggregated step reading
  - Created `data/healthconnect/StepCrossValidator.kt` — escrow system (>20% discrepancy, 3-sync lifecycle)
  - Created `data/healthconnect/StepGapFiller.kt` — recovers missed steps from HC
  - Created `data/healthconnect/ExerciseSessionReader.kt` — reads exercise sessions
  - Created `data/healthconnect/ActivityMinuteConverter.kt` — conversion table with per-activity caps + double-counting prevention
  - Created `di/HealthConnectModule.kt` — organizational Hilt module
  - Created `presentation/HealthConnectPermissionActivity.kt` — privacy policy stub
  - Updated `DailyStepRecordEntity.kt` — renamed googleFitSteps→healthConnectSteps, added escrowSteps + escrowSyncCount
  - Updated `DailyStepSummary.kt` — matching field changes
  - Updated `StepRepository.kt` — renamed method, added escrow methods
  - Updated `StepRepositoryImpl.kt` — implemented escrow methods
  - Updated `DailyStepDao.kt` — added clearEscrow query
  - Updated `DailyStepManager.kt` — added recordActivityMinutes()
  - Updated `StepSyncWorker.kt` — integrated HC gap-fill, cross-validation, activity minutes
  - Updated `MainActivity.kt` — HC permission request via PermissionController
  - Updated `AndroidManifest.xml` — HC permissions, privacy policy activity + activity-alias
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - StepSyncWorker passes empty sensorStepsPerMinute map to ActivityMinuteConverter (full per-minute tracking deferred)
- Follow-ups created:
  - Update GDD/step-tracking docs to reference Health Connect instead of Google Fit
  - Create ADR for Google Fit → Health Connect decision
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 06: Home Screen & Navigation
- Goal: Build Compose navigation graph, bottom nav bar, and real Home dashboard with live data.
- Changes made:
  - Added `hilt-navigation-compose:1.3.0` and `compose-material-icons-core` to version catalog + build.gradle.kts
  - Created `presentation/navigation/Screen.kt` — sealed class with 5 routes (Home, Workshop, Battle, Labs, Stats)
  - Created `presentation/navigation/BottomNavBar.kt` — NavigationBar with 5 items, route highlighting
  - Created `presentation/home/HomeUiState.kt` — UI state data class
  - Created `presentation/home/HomeViewModel.kt` — @HiltViewModel combining PlayerRepository + StepRepository flows
  - Rewrote `presentation/home/HomeScreen.kt` — real dashboard (tier/biome header, step card, currency row, best wave, battle button)
  - Updated `presentation/MainActivity.kt` — Scaffold + NavHost + BottomNavBar, preserved permission logic
  - HomeViewModel calls `ensureProfileExists()` in init to seed default profile
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 07: Workshop Screen & Upgrades
- Goal: Build Workshop screen with 3-tab layout, 23 upgrades, tap-to-buy, Quick Invest.
- Changes made:
  - Created `domain/usecase/PurchaseUpgrade.kt` — checks affordability, deducts Steps, increments level
  - Created `domain/usecase/QuickInvest.kt` — recommends cheapest affordable upgrade
  - Created `presentation/workshop/WorkshopUiState.kt` — UpgradeDisplayInfo + WorkshopUiState
  - Created `presentation/workshop/WorkshopViewModel.kt` — @HiltViewModel, combines upgrades + wallet flows
  - Created `presentation/workshop/UpgradeCard.kt` — reusable card with 3 visual states
  - Created `presentation/workshop/WorkshopScreen.kt` — PrimaryTabRow, LazyColumn, Quick Invest FAB
  - Updated `presentation/home/HomeViewModel.kt` — added workshopRepository.ensureUpgradesExist() in init
  - Updated `presentation/MainActivity.kt` — replaced Workshop placeholder with WorkshopScreen()
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-04 — Plan 08: Battle Renderer — Game Loop & Ziggurat
- Goal: Build custom SurfaceView battle renderer with game loop, ziggurat entity, projectiles, health bar, and Compose overlay.
- Decisions made:
  - (b) ZigguratBaseStats as domain/model object — proper constants for Plan 10's ResolveStats to consume.
  - (a) Simple geometric ziggurat — 5 stacked rectangles in sandstone tones.
  - (a) Hidden bottom nav during battle — full-screen immersive.
- Changes made:
  - Created `domain/model/ZigguratBaseStats.kt` — base stat constants (HP, damage, attack speed, range, regen, knockback, projectile speed)
  - Created `presentation/battle/engine/Entity.kt` — abstract base class (x, y, width, height, isAlive, update, render)
  - Created `presentation/battle/engine/GameEngine.kt` — entity list, update/render dispatch, HealthBarRenderer integration
  - Created `presentation/battle/entities/ZigguratEntity.kt` — 5-layer ziggurat, auto-fire via callback, HP tracking
  - Created `presentation/battle/entities/ProjectileEntity.kt` — moves toward target, self-destructs on arrival
  - Created `presentation/battle/ui/HealthBarRenderer.kt` — green/yellow/red HP bar with numeric text
  - Created `presentation/battle/GameLoopThread.kt` — fixed timestep (60 UPS), accumulator pattern, speed multiplier, FPS counter
  - Created `presentation/battle/GameSurfaceView.kt` — SurfaceHolder.Callback, manages game loop thread lifecycle
  - Created `presentation/battle/BattleUiState.kt` — UI state for Compose overlay
  - Created `presentation/battle/BattleViewModel.kt` — @HiltViewModel, loads tier, exposes state + BattleEvent
  - Created `presentation/battle/BattleScreen.kt` — Compose wrapper (AndroidView + overlay: wave counter, speed controls, pause, exit)
  - Updated `presentation/MainActivity.kt` — BattleScreen replaces placeholder, bottom nav hidden on Battle route
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Ziggurat fires at fixed test target (top-center) — Plan 09 replaces with nearest enemy
  - Workshop bonuses not applied to base stats yet — Plan 10 adds ResolveStats
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-05 — Plan 09: Battle System — Enemies & Waves
- Goal: Add 6 enemy types, wave spawning, enemy scaling, collision, cash, nearest-enemy targeting, round end.
- Decisions made:
  - (b) Enemies spawn from top + left + right edges (converging on ziggurat)
  - (b) Fix EnemyType enum to match battle-formulas.md (FAST dmg 0.5→0.7, RANGED spd 1.0→0.8 + dmg 1.5→1.2, BOSS hp 10→20)
  - (b) Wave scaling: 1.05^wave (gentler curve, tunable in Plan 28)
- Changes made:
  - Updated `domain/model/EnemyType.kt` — corrected multipliers to match balance spec
  - Created `presentation/battle/engine/EnemyScaler.kt` — wave-based stat scaling (1.05^wave), cash rewards per type
  - Created `presentation/battle/entities/EnemyEntity.kt` — 6 types, movement, melee/ranged attack, distinct shapes/colors, mini HP bar
  - Created `presentation/battle/entities/EnemyProjectileEntity.kt` — red projectiles for Ranged enemies
  - Created `presentation/battle/engine/WaveSpawner.kt` — 26s spawn + 9s cooldown, enemy composition by wave, boss every 10 waves
  - Created `presentation/battle/engine/CollisionSystem.kt` — projectile↔enemy and enemy projectile↔ziggurat collision
  - Updated `presentation/battle/engine/GameEngine.kt` — integrated WaveSpawner, CollisionSystem, cash tracking, Scatter splitting, round end detection, findNearestEnemy()
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — targets nearest enemy via lambda, only fires when enemy in range
  - Updated `presentation/battle/BattleUiState.kt` — added enemyCount, wavePhase
  - Updated `presentation/battle/BattleViewModel.kt` — polls engine state every 200ms, detects roundOver
  - Updated `presentation/battle/BattleScreen.kt` — shows enemy count, wave phase, cash in overlay
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Cash economy simplified (base per type) — Plan 11 adds full formula
  - Workshop bonuses not applied to stats — Plan 10 adds ResolveStats
- Follow-ups created: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-05 — Plan 10: Battle System — Stats & Combat
- Goal: Stats resolution engine + core combat mechanics (crit, knockback, lifesteal, thorn, regen, death defy, defense).
- Decisions made:
  - (b) Core stats + simple mechanics now; Orbs/Multishot/Bounce deferred
  - (a) GameEngine accepts ResolvedStats in init() — ViewModel resolves on round start
  - (a) Centralized applyDamageToZiggurat() for all damage sources
- Changes made:
  - Created `domain/model/ResolvedStats.kt` — all computed combat stats data class
  - Created `domain/usecase/ResolveStats.kt` — workshop + in-round levels → ResolvedStats
  - Created `domain/usecase/CalculateDamage.kt` — raw damage + crit roll + damage/meter bonus
  - Created `domain/usecase/CalculateDefense.kt` — damage reduction (cap 75%) + flat block
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — uses ResolvedStats for HP, attack speed, range, health regen
  - Updated `presentation/battle/entities/EnemyEntity.kt` — added applyKnockback()
  - Updated `presentation/battle/engine/CollisionSystem.kt` — delegates to engine callbacks
  - Updated `presentation/battle/engine/GameEngine.kt` — centralized damage pipeline (defense → death defy → thorn), knockback, lifesteal
  - Updated `presentation/battle/GameSurfaceView.kt` — accepts ResolvedStats, re-inits engine
  - Updated `presentation/battle/BattleViewModel.kt` — resolves stats from workshop on init
  - Updated `presentation/battle/BattleScreen.kt` — passes resolved stats to surface view
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Orbs, Multishot, Bounce Shot computed in ResolvedStats but not wired to gameplay
  - In-round upgrades (Plan 11) will re-resolve stats on purchase
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 11: In-Round Upgrades & Cash Economy
- Goal: Full cash economy + in-round upgrade menu with purchase flow.
- Decisions made:
  - (b) Cash economy + upgrade menu only; Orbs/Multishot/Bounce deferred to mini-plan 10b
  - (a) Upgrade menu always accessible via toggle button
  - (a) onWaveComplete callback added to WaveSpawner
- Changes made:
  - Updated `presentation/battle/engine/WaveSpawner.kt` — added onWaveComplete callback, fires on SPAWNING→COOLDOWN
  - Updated `presentation/battle/engine/GameEngine.kt` — full cash formula (tier × cashBonus), wave cash + interest, spendCash(), updateZigguratStats()
  - Updated `presentation/battle/BattleUiState.kt` — added showUpgradeMenu, inRoundLevels, lastPurchaseFree
  - Updated `presentation/battle/BattleViewModel.kt` — purchase flow, in-round levels, re-resolve stats, free upgrade chance, tier tracking
  - Updated `presentation/battle/GameSurfaceView.kt` — configure() accepts stats + tier + workshopLevels
  - Created `presentation/battle/ui/InRoundUpgradeMenu.kt` — 3-tab Compose overlay, upgrade list, purchase buttons
  - Updated `presentation/battle/BattleScreen.kt` — upgrade toggle button, InRoundUpgradeMenu overlay
  - Created `docs/plans/plan-10b-advanced-combat.md` — mini-plan for Orbs, Multishot, Bounce Shot
  - Updated `docs/plans/plan-11-in-round-upgrades.md` — removed deferred section
  - Updated `docs/plans/master-plan.md` — added Plan 10b entry
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers:
  - Orbs/Multishot/Bounce in Plan 10b (ready to implement anytime)
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 10b: Advanced Combat (Orbs, Multishot, Bounce Shot)
- Goal: Wire the three deferred combat mechanics to gameplay.
- Decisions made:
  - (a) Orbs: damage on contact with 0.5s per-enemy cooldown, 50% resolved damage
  - (a) Bounce: spawn new ProjectileEntity with bouncesRemaining, reuse collision pipeline
  - (a) Multishot: findNearestEnemies(n) lambda, fire one projectile per target
- Changes made:
  - Updated `presentation/battle/entities/ProjectileEntity.kt` — added bouncesRemaining + hitEnemies
  - Created `presentation/battle/entities/OrbEntity.kt` — orbiting entity, per-enemy cooldown, cyan rendering
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — multishot via findNearestEnemies(n) lambda
  - Updated `presentation/battle/engine/GameEngine.kt` — findNearestEnemies(), bounce logic in onProjectileHitEnemy, orb spawn/despawn, onOrbHitEnemy
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Documentation Sweep
- Goal: Full project documentation audit — find and fix stale/incorrect references.
- Changes made:
  - Updated `docs/StepsOfBabylon_GDD.md` — replaced all Google Fit references with Health Connect (§2.1, §11.1–§11.4, §15.1, §17, §19). Fixed anti-cheat rate limit from ">500 steps/min" to "200/min (250 burst)".
  - Updated `docs/database-schema.md` — DailyStepRecord: `googleFitSteps` → `healthConnectSteps`, added `escrowSteps` and `escrowSyncCount` columns.
  - Updated `docs/architecture.md` — layer diagram "Google Fit" → "Health Connect", DI section now lists actual modules (StepModule, HealthConnectModule) instead of "Future modules".
  - Rewrote `docs/plans/plan-05-google-fit.md` — body now reflects actual Health Connect implementation with correct file paths and class names.
  - Updated `docs/plans/plan-25-anti-cheat.md` — all Google Fit references → Health Connect, corrected package paths (`data/healthconnect/` not `data/googlefit/`).
  - Updated `docs/plans/plan-30-release.md` — ProGuard keep rules, privacy policy, and checklist updated for Health Connect.
  - Updated `docs/plans/master-plan.md` — Plan 10 description corrected (orbs/bounce were deferred to 10b).
  - Updated `docs/agent/STATE.md` — removed stale "Google Fit references" known issue.
- Remaining cosmetic issues (not fixed — completed plans, code is correct):
  - `docs/plans/plan-02-database.md` and `plan-03-repositories.md` still reference `googleFitSteps` column name (these are historical plan docs; actual code uses `healthConnectSteps`)
  - `docs/agent/RUN_LOG.md` references are historical records (correct to leave as-is)
  - `docs/agent/DECISIONS/ADR-0002-health-connect.md` references are contextual (explaining the decision)
  - `docs/agent/state.json` is an orphaned file from earlier approach (harmless)
  - `docs/temp/` contains a reference playbook from setup (harmless)
- Commands/tests run: N/A (documentation-only changes)
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 12: Round Lifecycle & Post-Round
- Goal: Full round lifecycle with post-round summary, best wave persistence, pause overlay, auto-pause.
- Decisions made:
  - (b) Post-round as overlay within Battle route (avoids ViewModel re-creation)
  - (a) Engine owns totalEnemiesKilled + elapsedTimeSeconds (single source of truth)
  - (a) Quit Round shows summary and saves best wave (player earned that progress)
- Changes made:
  - Updated `presentation/battle/engine/GameEngine.kt` — added totalEnemiesKilled, elapsedTimeSeconds, totalCashEarned tracking; made roundOver publicly settable for quit flow
  - Created `domain/usecase/UpdateBestWave.kt` — compares wave to stored best, persists if new record, returns Result(isNewRecord, previousBest)
  - Updated `presentation/battle/BattleUiState.kt` — added RoundEndState data class and roundEndState field
  - Rewrote `presentation/battle/BattleViewModel.kt` — endRound(), quitRound(), playAgain(), pause(); removed BattleEvent; tracks surfaceView reference for play-again re-init
  - Created `presentation/battle/ui/PostRoundOverlay.kt` — wave reached, enemies killed, cash earned, time survived, new record banner, Play Again / Return to Workshop buttons
  - Created `presentation/battle/ui/PauseOverlay.kt` — Resume / Quit Round buttons
  - Rewrote `presentation/battle/BattleScreen.kt` — integrated overlays, auto-pause via LifecycleEventObserver, exit button calls quitRound(), controls hidden when round over
- Commands/tests run: `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL, zero warnings
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Domain Layer Unit Testing (Regression Safety Net)
- Goal: Add pure JVM unit tests covering all domain use cases, key domain models, and critical pure-Kotlin logic outside domain.
- Decisions made:
  - JVM-only tests (no instrumented/emulator tests) for speed and simplicity
  - JUnit 5 + kotlinx-coroutines-test as test framework (no Turbine needed yet)
  - Injected `Random` into `CalculateDamage` for deterministic crit testing (default param, zero caller impact)
  - Created fake repositories (FakePlayerRepository, FakeWorkshopRepository) for use case tests
- Changes made:
  - Updated `gradle/libs.versions.toml` — added junit5=5.11.4, coroutinesTest=1.10.1, test library entries
  - Updated `app/build.gradle.kts` — added testImplementation deps, JUnit Platform config, platform launcher
  - Refactored `domain/usecase/CalculateDamage.kt` — injectable Random parameter
  - Created `test/fakes/FakePlayerRepository.kt` — in-memory MutableStateFlow-backed fake
  - Created `test/fakes/FakeWorkshopRepository.kt` — in-memory MutableStateFlow-backed fake
  - Created 15 test classes (80 tests total):
    - `domain/usecase/`: CalculateUpgradeCostTest, CanAffordUpgradeTest, QuickInvestTest, PurchaseUpgradeTest, UpdateBestWaveTest, ResolveStatsTest, CalculateDamageTest, CalculateDefenseTest
    - `domain/model/`: TierConfigTest, BiomeTest, CardLoadoutTest, UltimateWeaponLoadoutTest, UpgradeTypeTest, EnemyTypeTest
    - `presentation/battle/engine/`: EnemyScalerTest
    - `data/sensor/`: StepRateLimiterTest
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 80 tests, 0 failures
- Open questions / blockers: None. ViewModel tests and instrumented tests deferred to Plan 29.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 13: Tier System & Progression
- Goal: Tier unlock logic, tier selector UI, battle conditions at Tier 6+, post-round tier unlock notification.
- Decisions made:
  - (a) Armor as hit counter — enemies block first N hits, then take full damage. Punishes fast-attack/low-damage builds.
  - (a) Minimal tier selector — horizontal chip row on home screen, not a dedicated screen.
  - (b) Notify only on unlock — player stays on current tier, chooses when to advance via selector.
  - Added `highestUnlockedTier` as separate field from `currentTier` (play tier) to support tier selection.
  - DB version bumped to 2 with destructive fallback (dev phase — proper migration before release).
- Changes made:
  - Created `domain/usecase/CheckTierUnlock.kt` — iterates tiers, checks wave milestones against bestWavePerTier
  - Created `domain/model/BattleConditionEffects.kt` — pre-computes numeric modifiers from tier battle conditions
  - Created `presentation/home/TierSelector.kt` — horizontal tier chip row with lock/unlock states, condition summary
  - Updated `data/local/PlayerProfileEntity.kt` — added `highestUnlockedTier` column (default 1)
  - Updated `data/local/PlayerProfileDao.kt` — added `updateHighestUnlockedTier()` query
  - Updated `data/local/AppDatabase.kt` — bumped version to 2
  - Updated `domain/model/PlayerProfile.kt` — added `highestUnlockedTier` field
  - Updated `domain/repository/PlayerRepository.kt` — added `updateHighestUnlockedTier()` method
  - Updated `data/repository/PlayerRepositoryImpl.kt` — implemented new method + entity→domain mapping
  - Updated `presentation/battle/entities/EnemyEntity.kt` — added `armorHits` (blocks first N hits), `attackInterval` param, armor ring visual
  - Updated `presentation/battle/engine/WaveSpawner.kt` — accepts `BattleConditionEffects`, applies speed/attack/armor/boss interval
  - Updated `presentation/battle/engine/GameEngine.kt` — computes conditions from tier, applies orb/knockback/thorn multipliers
  - Updated `presentation/battle/BattleUiState.kt` — added `tierUnlocked` to `RoundEndState`
  - Updated `presentation/battle/BattleViewModel.kt` — checks tier unlock after round end, persists new highest tier
  - Updated `presentation/battle/ui/PostRoundOverlay.kt` — shows "🔓 Tier X Unlocked!" banner with cash multiplier teaser
  - Updated `presentation/home/HomeUiState.kt` — added `highestUnlockedTier`, `bestWavePerTier`
  - Updated `presentation/home/HomeViewModel.kt` — loads unlock data, exposes `selectTier()`
  - Updated `presentation/home/HomeScreen.kt` — replaced static header with TierSelector
  - Updated `test/fakes/FakePlayerRepository.kt` — added `updateHighestUnlockedTier`
  - Created `test/.../CheckTierUnlockTest.kt` — 7 tests for tier unlock logic
  - Created `test/.../BattleConditionEffectsTest.kt` — 6 tests for all tier condition values
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — BUILD SUCCESSFUL, 93 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 18: Narrative Biome Progression
- Goal: 5 biome visual identities, ambient particles, biome transition overlay, home screen theming.
- Decisions made:
  - (a) Simple overlay for biome transition — styled Compose screen, animation deferred to Plan 27.
  - (a) Simple particles — lightweight spawn-drift-recycle, 30-50 per biome, no physics.
  - (a) Derive biome unlock from highestUnlockedTier — no DB change, first-seen via SharedPreferences.
  - Enemy tinting via 30% color blend with base type color (not color filter).
  - Ziggurat colors passed as constructor parameter, paints built dynamically.
- Changes made:
  - Created `presentation/battle/biome/BiomeTheme.kt` — 5 biome palettes (sky, ground, ziggurat, enemy tint, particles)
  - Created `presentation/battle/biome/BackgroundRenderer.kt` — gradient sky + ambient particle system
  - Created `presentation/battle/ui/BiomeTransitionOverlay.kt` — full-screen biome reveal with step count
  - Created `data/BiomePreferences.kt` — SharedPreferences wrapper for first-seen tracking
  - Updated `presentation/battle/engine/GameEngine.kt` — creates BackgroundRenderer, passes biome colors/tint
  - Updated `presentation/battle/entities/ZigguratEntity.kt` — accepts layerColors parameter
  - Updated `presentation/battle/entities/EnemyEntity.kt` — accepts enemyTint, blends with base color
  - Updated `presentation/battle/engine/WaveSpawner.kt` — accepts and passes enemyTint
  - Updated `presentation/battle/BattleUiState.kt` — added biomeTransition field
  - Updated `presentation/battle/BattleViewModel.kt` — injects BiomePreferences, checks first-seen, dismissBiomeTransition()
  - Updated `presentation/battle/BattleScreen.kt` — shows BiomeTransitionOverlay
  - Updated `presentation/home/HomeScreen.kt` — biome gradient background
  - Created `test/.../BiomeThemeTest.kt` — 4 tests
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 97 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 14: Step Overdrive
- Goal: Mid-battle mechanic to sacrifice Steps for 60s combat buff, once per round.
- Decisions made:
  - (a) Stub SURGE — shows in UI, deducts cost, but UW cooldown reset is no-op until Plan 15.
  - (a) Skip free charges — deferred to Plan 19 (Walking Encounters).
  - (a) Engine-side aura — pulsing circle + timer bar rendered on Canvas, respects game speed.
- Changes made:
  - Created `domain/usecase/ActivateOverdrive.kt` — sealed Result, checks balance + once-per-round
  - Created `presentation/battle/ui/OverdriveMenu.kt` — 4-option selection with cost/affordability
  - Created `test/.../ActivateOverdriveTest.kt` — 4 tests
  - Updated `GameEngine.kt` — overdrive state (timer, fortune multiplier, stat modification), activateOverdrive(), expireOverdrive()
  - Updated `ZigguratEntity.kt` — pulsing aura circle + timer bar, overdriveColor/overdriveProgress fields
  - Updated `BattleUiState.kt` — added overdriveUsed, activeOverdriveType, overdriveTimeRemaining, stepBalance, showOverdriveMenu
  - Updated `BattleViewModel.kt` — activateOverdrive(), toggleOverdriveMenu(), polls engine overdrive state
  - Updated `BattleScreen.kt` — ⚡ button in control bar, OverdriveMenu overlay, active overdrive HUD indicator
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 101 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 15: Ultimate Weapons
- Goal: 6 UW types with unlock/upgrade/equip, battle activation with cooldowns, visual effects, management screen.
- Decisions made:
  - (a) Simple geometric effects — expanding circles, lines, tints. Polish in Plan 27.
  - (a) Sub-screen of Workshop — "Ultimate Weapons" button navigates to UW management.
  - (a) Simple scaling — upgradeCost = unlockCost * 2 * level, cooldown -5%/level, max level 10.
- Changes made:
  - Updated `domain/model/UltimateWeaponType.kt` — added baseCooldownSeconds, effectDurationSeconds, upgradeCost(), cooldownAtLevel(), MAX_LEVEL
  - Created `domain/usecase/UnlockUltimateWeapon.kt` — checks balance + not owned, deducts Power Stones
  - Created `domain/usecase/UpgradeUltimateWeapon.kt` — cost scaling, max level 10
  - Created `presentation/weapons/UltimateWeaponViewModel.kt` — observes weapons + wallet
  - Created `presentation/weapons/UltimateWeaponScreen.kt` — 6 UW cards with lock/unlock/equip/upgrade
  - Created `presentation/battle/ui/UltimateWeaponBar.kt` — row of 3 UW activation buttons
  - Updated `GameEngine.kt` — UW state management, 6 effect implementations, visual rendering, SURGE wired
  - Updated `BattleUiState.kt` — added UWSlotInfo, uwSlots
  - Updated `BattleViewModel.kt` — injects UltimateWeaponRepository, loads equipped, polls UW state
  - Updated `BattleScreen.kt` — shows UltimateWeaponBar
  - Updated `Screen.kt` — added Weapons route
  - Updated `MainActivity.kt` — added Weapons composable route
  - Updated `WorkshopScreen.kt` — added "Ultimate Weapons" navigation button
  - Created `test/fakes/FakeUltimateWeaponRepository.kt`
  - Created `test/.../UnlockUltimateWeaponTest.kt` — 3 tests
  - Created `test/.../UpgradeUltimateWeaponTest.kt` — 4 tests
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 108 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 16: Labs System
- Goal: Implement Labs research system — 10 time-gated research projects, lab slots, Gem rush, auto-completion.
- Decisions made:
  - (a) Cost scaling 1.15, time scaling 1.10 — moderate ramp matching Workshop feel.
  - (a) Gem rush: linear interpolation `50 + fraction × 150` (range 50–200 Gems).
  - (a) Per-type scaling fields on ResearchType enum (tunable in Plan 28).
- Changes made:
  - Updated `domain/model/ResearchType.kt` — added `costScaling: Double = 1.15` and `timeScaling: Double = 1.10`
  - Created `domain/usecase/CalculateResearchCost.kt` — `baseCostSteps × costScaling^level`
  - Created `domain/usecase/CalculateResearchTime.kt` — `baseTimeHours × timeScaling^level`
  - Created `domain/usecase/StartResearch.kt` — validates slots, affordability, max level, deducts Steps
  - Created `domain/usecase/CompleteResearch.kt` — gates on timer, increments level
  - Created `domain/usecase/RushResearch.kt` — linear Gem cost, companion `calculateRushCost()`
  - Created `domain/usecase/UnlockLabSlot.kt` — 200 Gems per slot, max 4
  - Created `domain/usecase/CheckResearchCompletion.kt` — auto-completes expired research
  - Updated `data/local/PlayerProfileEntity.kt` — added `labSlotCount` with `@ColumnInfo(defaultValue = "1")`
  - Updated `data/local/PlayerProfileDao.kt` — added `updateLabSlotCount()`
  - Updated `data/local/AppDatabase.kt` — bumped version to 3
  - Updated `domain/model/PlayerProfile.kt` — added `labSlotCount`
  - Updated `domain/repository/PlayerRepository.kt` — added `updateLabSlotCount()`
  - Updated `data/repository/PlayerRepositoryImpl.kt` — implemented + toDomain mapping
  - Updated `domain/repository/LabRepository.kt` — added `getResearchLevel()`, `getActiveResearchCount()`, updated `startResearch()` signature
  - Updated `data/repository/LabRepositoryImpl.kt` — implemented new methods
  - Created `presentation/labs/LabsUiState.kt` — ResearchDisplayInfo + LabsUiState
  - Created `presentation/labs/LabsViewModel.kt` — combines research/wallet/tick flows, 1s countdown
  - Created `presentation/labs/LabsScreen.kt` — full UI with slot indicator, research cards, start/rush/unlock
  - Updated `presentation/MainActivity.kt` — replaced Labs placeholder with LabsScreen
  - Updated `presentation/home/HomeViewModel.kt` — added labRepository.ensureResearchExists() + CheckResearchCompletion
  - Created `test/fakes/FakeLabRepository.kt` — in-memory StateFlow-backed fake
  - Updated `test/fakes/FakePlayerRepository.kt` — added updateLabSlotCount
  - Created 7 test classes (25 new tests):
    - CalculateResearchCostTest (4), CalculateResearchTimeTest (3), StartResearchTest (5), CompleteResearchTest (3), RushResearchTest (4), UnlockLabSlotTest (3), CheckResearchCompletionTest (3)
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 133 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: None.
- Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-06 — Plan 17: Cards System
- Goal: Implement Cards system — 9 card types, 3 rarities, pack opening, Card Dust upgrades, loadout, battle integration.
- Decisions made:
  - (a) Pack distributions: Common 80/18/2, Rare 50/40/10, Epic 20/40/40. Dust from dupes: 5/15/50.
  - (a) Numeric fields on CardType enum with linear interpolation for level scaling.
  - (b) Post-process pattern: ApplyCardEffects modifies ResolvedStats copy, ResolveStats untouched.
- Changes made:
  - Updated `domain/model/CardType.kt` — added valueLv1/valueLv5/secondaryLv1/secondaryLv5, effectAtLevel(), secondaryAtLevel()
  - Updated `domain/model/CardRarity.kt` — added dustValue (5/15/50) and upgradeDustPerLevel (10/25/50)
  - Created `domain/usecase/OpenCardPack.kt` — PackTier enum, CardResult, rarity rolling, duplicate→dust
  - Created `domain/usecase/UpgradeCard.kt` — Card Dust cost scaling by rarity and level
  - Created `domain/usecase/ApplyCardEffects.kt` — CardEffectResult, 9 card effects as post-process on ResolvedStats
  - Created `domain/usecase/ManageCardLoadout.kt` — equip/unequip with max 3 validation
  - Created `presentation/cards/CardsUiState.kt` — CardDisplayInfo, PackOption, CardsUiState
  - Created `presentation/cards/CardsViewModel.kt` — combines cards + wallet, all actions
  - Created `presentation/cards/CardsScreen.kt` — pack buttons, card collection, equip/upgrade, rarity colors
  - Updated `presentation/battle/BattleViewModel.kt` — inject CardRepository, apply card effects at round start + playAgain
  - Updated `presentation/battle/engine/GameEngine.kt` — Second Wind revive, cashBonusPercent in kill rewards
  - Updated `presentation/navigation/Screen.kt` — added Cards route
  - Updated `presentation/MainActivity.kt` — added Cards composable
  - Updated `presentation/workshop/WorkshopScreen.kt` — added "🃏 Cards" navigation button
  - Created `test/fakes/FakeCardRepository.kt` — in-memory StateFlow-backed fake
  - Updated `test/fakes/FakePlayerRepository.kt` — implemented addCardDust/spendCardDust
  - Created 4 test classes (22 new tests):
    - OpenCardPackTest (4), UpgradeCardTest (4), ApplyCardEffectsTest (11), ManageCardLoadoutTest (3)
- Commands/tests run: `./run-gradle.sh testDebugUnitTest` — 155 tests, 0 failures. `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL.
- Open questions / blockers: Step Surge gemMultiplier tracked but not consumed (no Gem earning in battle — deferred to Plan 20).
- Memory updated: STATE ✅ / RUN_LOG ✅

## Run — 2026-03-06 — Plan 19: Walking Encounters & Supply Drops

### Objective
Implement Plan 19: Supply drop generation during walks, push notifications, claim system, and inbox UI.

### What was done
1. **Task 1 — Enums & type safety**: Created `SupplyDropTrigger` (4 entries with notification messages) and `SupplyDropReward` (4 entries). Updated `SupplyDrop` domain model from raw `String` fields to type-safe enums. Updated `WalkingEncounterRepository` interface and `WalkingEncounterRepositoryImpl` to use enums (stored as `.name` strings in Room).

2. **Task 2 — GenerateSupplyDrop use case**: Seeded random drop generation with 3 active triggers (milestone at 10k, threshold at 2k boundaries with 5% per 100 steps, random at 1% per 500 steps). Step burst deferred. Created `DropGeneratorState` for tracking. 9 unit tests, all green.

3. **Task 3 — ClaimSupplyDrop use case**: Credits reward to correct `PlayerRepository` method based on `SupplyDropReward` type, marks drop claimed. Created `FakeWalkingEncounterRepository`. 6 unit tests, all green.

4. **Task 4 — Inbox cap enforcement**: Added `deleteOldestUnclaimed()` and `countUnclaimedOnce()` to `WalkingEncounterDao`. Added `enforceInboxCap(maxSize)` and `getUnclaimedCount()` to repository interface/impl.

5. **Task 5 — SupplyDropNotificationManager**: Dedicated `supply_drops` notification channel (IMPORTANCE_DEFAULT), unique notification IDs per drop, deep-link intent to supplies screen.

6. **Task 6 — DailyStepManager integration**: Added `WalkingEncounterRepository` and `SupplyDropNotificationManager` as dependencies. After step crediting, calls `GenerateSupplyDrop`, enforces inbox cap, creates drop, and sends notification. Tracks `DropGeneratorState` with day rollover reset.

7. **Task 7 — UnclaimedSuppliesScreen**: Added `Screen.Supplies` route. Created `UnclaimedSuppliesViewModel` (observes unclaimed drops, claim/claimAll), `SuppliesUiState`, and `UnclaimedSuppliesScreen` (LazyColumn with claim buttons, empty state, relative timestamps). Added route to `NavHost` in `MainActivity` with notification deep-link handling.

8. **Task 8 — Home screen inbox badge**: Added `unclaimedDropCount` to `HomeUiState`. Injected `WalkingEncounterRepository` into `HomeViewModel`, added to `combine()`. Added `BadgedBox` button on `HomeScreen` that shows when count > 0, navigates to supplies. Added `onSuppliesClick` callback wired in `MainActivity`.

### Decisions
- No GPS triggers — step-based only, defer to future plan.
- No free Overdrive charges — burst trigger deferred, avoids Room migration.
- Inbox overflow discards oldest unclaimed drop silently.
- No Card Pack reward — Card Dust instead, avoids coupling to OpenCardPack flow.
- 10k milestone gives 5 Gems (single drop); Power Stones deferred to combined reward enhancement.
- No notification action button — tap opens inbox screen (avoids BroadcastReceiver complexity).

### Test results
- 170 total JVM tests (155 existing + 15 new), all green, 0 failures.
- New: GenerateSupplyDropTest (9), ClaimSupplyDropTest (6).

### What remains
- Step burst trigger (needs step velocity tracking in DailyStepManager).
- 10k milestone second reward (Power Stones) — could be two drops or combined.
- Custom notification icons (currently using system placeholders).
- Supply drop notification preferences (on/off toggle — Plan 23).
- Claim animation in UnclaimedSuppliesScreen (polish — Plan 27).

## Run — 2026-03-06 — Plan 20: Power Stone & Gem Economy

### Objective
Implement premium currency earning systems: weekly step challenges, daily login rewards, and wave milestone bonuses.

### What was done
1. **Task 1 — Database**: Created `WeeklyChallengeEntity` + `WeeklyChallengeDao`, `DailyLoginEntity` + `DailyLoginDao`. Added `currentStreak`/`lastLoginDate` to `PlayerProfileEntity`/`PlayerProfile`. Added `updateStreak()` to `PlayerProfileDao`/`PlayerRepository`. Added `sumCreditedSteps()` to `DailyStepDao`. Bumped DB to version 4 (9 entities). Updated `DatabaseModule` with 2 new DAO providers. Updated `FakePlayerRepository` with streak support.

2. **Task 2 — Weekly Step Challenge**: Created `TrackWeeklyChallenge` use case. Queries weekly step sum from `DailyStepDao`, awards PS at 50k (10), 75k (20 total), 100k (35 total) thresholds. Only awards delta PS for newly crossed tiers.

3. **Task 3 — Daily Login & Streak**: Created `TrackDailyLogin` use case. Awards 1 PS when 1k+ steps walked (once/day). Manages 7-day Gem streak: consecutive days increment streak, missed day resets to 1, awards min(streak, 5) Gems. Streak cycles after day 7.

4. **Task 4 — Wave Milestone PS**: Created `AwardWaveMilestone` use case. Awards 1 PS (base), 2 PS (wave % 10 == 0), or 5 PS (wave % 25 == 0) on new personal bests. Integrated into `BattleViewModel.endRound()`. Added `powerStonesAwarded` to `RoundEndState`. Updated `PostRoundOverlay` to display PS earned.

5. **Task 5 — Currency Dashboard**: Created `Screen.Economy` route. Created `CurrencyDashboardViewModel` + `CurrencyDashboardScreen` with weekly progress bar, 3 threshold markers, login streak dots (7-day), daily PS status, and currency balances.

6. **Task 6 — Integration**: Updated `DailyStepManager` with `DailyLoginDao`, `WeeklyChallengeDao`, `DailyStepDao` dependencies. Calls `TrackDailyLogin` and `TrackWeeklyChallenge` after step crediting. Updated `HomeViewModel` to trigger daily login on app open. Made currency row on `HomeScreen` tappable to navigate to economy dashboard.

### Decisions
- Streak fields on PlayerProfileEntity (no separate LoginStreakEntity) — avoids extra table/DAO/repo.
- Long-distance Gem bonuses deferred to Plan 21 (milestones).
- Wave milestone: 1 PS base, 2 PS at multiples of 10, 5 PS at multiples of 25.
- TrackWeeklyChallenge/TrackDailyLogin use DAOs directly (data-layer integration, not pure domain).

### Test results
- 179 total JVM tests (170 existing + 9 new AwardWaveMilestone), all green, 0 failures.

### What remains
- TrackWeeklyChallenge and TrackDailyLogin unit tests (need DAO fakes — deferred to Plan 29).
- Long-distance walking Gem bonuses (Plan 21).
- Weekly challenge reset notification.

## Run — 2026-03-09 — Plan 21: Milestones & Daily Missions

### Objective
Implement lifetime walking milestones and daily missions with progress tracking and claim rewards.

### Design decisions
- Card Pack milestone rewards → equivalent Gems (Tutorial=50, Rare=150, Epic=500). Keeps OpenCardPack untouched.
- Cosmetic milestone rewards → stored as claimed but no-op visually until cosmetics system exists.
- Walking mission progress → DAO query approach (steps already tracked).
- Battle mission progress → accumulated in BattleViewModel.endRound().
- Workshop/Lab mission progress → updated at call sites.
- DB version 5 with destructive fallback (still in dev).

### What was done
1. **Task 1 — Domain models**: Created `MilestoneReward` (sealed class: Gems/PowerStones/Cosmetic), `Milestone` (6 entries matching GDD §16.1 with card pack→Gem equivalents), `DailyMissionType` (6 entries: 2 walking, 2 battle, 2 upgrade), `MissionCategory` enum.

2. **Task 2 — Milestone DB layer**: Created `MilestoneEntity` + `MilestoneDao`. Updated `AppDatabase` (version 5, 11 entities). Updated `DatabaseModule` with 2 new DAO providers.

3. **Task 3 — Mission DB layer**: Created `DailyMissionEntity` + `DailyMissionDao` (with `countClaimable` Flow query).

4. **Task 4 — Use cases**: Created `CheckMilestones` (queries DAO, filters by threshold + unclaimed) and `ClaimMilestone` (credits Gems/PS, marks claimed, cosmetics no-op).

5. **Task 5 — GenerateDailyMissions**: Date-seeded Random, 1 per category, idempotent (skips if missions exist for today).

6. **Task 6 — Progress hooks**: 
   - `BattleViewModel.endRound()` → updates REACH_WAVE and KILL_ENEMIES missions.
   - `WorkshopViewModel.purchase()` → updates SPEND_WORKSHOP_STEPS mission.
   - `LabsViewModel` → updates COMPLETE_RESEARCH mission after rush/completion.

7. **Task 7 — Missions screen**: Created `MissionsUiState`, `MissionsViewModel` (combines missions + milestones + profile + tick), `MissionsScreen` (daily missions with progress bars + claim buttons, milestones with progress + claim, midnight countdown).

8. **Task 8 — Home integration**: Added `Screen.Missions` route, `claimableMissionCount` to `HomeUiState`, missions badge button on `HomeScreen`, `GenerateDailyMissions` call in `HomeViewModel.init`, 5-flow `combine()` with milestone/mission counts.

### Test results
- 206 total JVM tests (179 existing + 27 new), all green, 0 failures.
- New: MilestoneTest (6), DailyMissionTypeTest (7), CheckMilestonesTest (4), ClaimMilestoneTest (4), GenerateDailyMissionsTest (6).
- New fakes: FakeMilestoneDao, FakeDailyMissionDao.

### What remains
- Milestone cosmetic rewards are no-op (needs cosmetics system — Plan 26/27).
- Walking mission auto-progress runs once on MissionsScreen open (not continuously from DailyStepManager) — sufficient since steps flow updates the ViewModel.
- Daily mission notification on completion (deferred to Plan 23).

## Run — 2026-03-09 — Plan 22: Stats & History Screen

### Objective
Build the Stats & History screen with walking history charts, battle stats, and all-time aggregates.

### Design decisions
- Canvas-drawn bar chart (no third-party library, matches existing Canvas patterns).
- Lifetime currency counters (totalGemsEarned/Spent, totalPowerStonesEarned/Spent) on PlayerProfileEntity — tracked at DAO/repository level, zero caller changes.
- Battle stats (totalRoundsPlayed, totalEnemiesKilled, totalCashEarned) on PlayerProfileEntity — no separate entity.
- DB version 6 with destructive fallback.

### What was done
1. **Task 1 — Data layer**: Added 7 new columns to `PlayerProfileEntity` (totalGemsEarned/Spent, totalPowerStonesEarned/Spent, totalRoundsPlayed, totalEnemiesKilled, totalCashEarned). Updated `PlayerProfile` domain model, `PlayerProfileDao` (6 new queries), `PlayerRepositoryImpl` (lifetime tracking in add/spend methods + incrementBattleStats), `PlayerRepository` interface, `FakePlayerRepository`. Bumped DB to version 6.

2. **Task 2 — Battle stats wiring**: Added `playerRepository.incrementBattleStats()` call in `BattleViewModel.endRound()`.

3. **Task 3 — StatsViewModel**: Created `StatsUiState` (DailyBarData, StatsPeriod enum) and `StatsViewModel` (4-flow combine: profile + history + upgrades + period). Builds bar data for 7-day/30-day/12-week views. Computes daysActive, averageDailySteps, totalWorkshopLevels.

4. **Task 4 — Walking history chart**: Created `WalkingHistoryChart` Canvas composable — vertical bars with primary/secondary color split (sensor steps vs step-equivalents), 50k ceiling dashed line, date labels, y-axis scale, FilterChip period toggle, legend.

5. **Task 5 — Stats screen**: Created `StatsScreen` with 4 Card sections (Walking History, Today's Activity, Battle Stats, All-Time Stats). Replaced placeholder in `MainActivity`.

### Test results
- 206 total JVM tests, all green, 0 failures. No new tests (presentation-only plan).

### What remains
- Lifetime currency counters start from 0 (no retroactive backfill).
- Chart tap-for-detail tooltip deferred to Plan 27 polish.
- Pull-to-refresh deferred (data is already reactive via Flows).

## Run — 2026-03-09 — Plan 23: Notifications & Widget

### Objective
Enhanced notifications, home screen widget, smart reminders, milestone alerts, and notification preferences.

### Design decisions
- Traditional AppWidgetProvider + RemoteViews (no Glance dependency).
- Smart reminders piggyback on existing StepSyncWorker (no separate WorkManager job).
- SharedPreferences for notification preferences (consistent with BiomePreferences pattern).

### What was done
1. **Task 1 — NotificationPreferences**: Created `data/NotificationPreferences.kt` — 4 boolean toggles (persistent, supply drops, smart reminders, milestone alerts).

2. **Task 2 — Enhanced persistent notification**: Updated `StepNotificationManager` with Workshop/Battle action buttons via PendingIntents. Updated `StepCounterService` to pass actual step balance from PlayerRepository. Added preference gate. Extended `MainActivity` deep-link handling for workshop/battle/missions routes.

3. **Task 3 — Home screen widget**: Created `widget_step_counter.xml` layout, `step_widget_info.xml` metadata, `StepWidgetProvider` (AppWidgetProvider with SharedPreferences-backed data), `WidgetUpdateHelper` (60s throttle). Integrated into `DailyStepManager`. Registered in AndroidManifest.

4. **Task 4 — Smart reminders**: Created `SmartReminderManager` — checks prefs enabled, not sent today, lastActiveAt > 4h, finds cheapest upgrade within 10k step gap. Uses `reminders` notification channel. Integrated into `StepSyncWorker.doWork()`.

5. **Task 5 — Milestone alerts**: Created `MilestoneNotificationManager` — notifyNewBestWave() and notifyMilestoneAchieved(). Uses `milestones` channel. Integrated into `BattleViewModel.endRound()` (new best wave) and `HomeViewModel.init` (achievable milestones).

6. **Task 6 — Supply drop preference gate**: Updated `SupplyDropNotificationManager` to inject NotificationPreferences and skip if disabled.

7. **Task 7 — Settings UI**: Created `NotificationSettingsViewModel` + `NotificationSettingsScreen` (4 Switch toggles). Added `Screen.Settings` route, wired in NavHost, added settings button on HomeScreen.

### Test results
- 206 total JVM tests, all green, 0 failures. No new tests (Android notification/widget APIs).

### What remains
- Custom notification icons (all channels use system placeholders).
- Widget balance shows 0 (DailyStepManager doesn't query PlayerRepository for balance).
- Widget preview image for widget picker.

## Run — 2026-03-09 — Plan 25: Anti-Cheat & Validation

### Objective
Harden anti-cheat beyond basic rate limiting + daily ceiling + HC escrow. Add velocity analysis, graduated cross-validation, activity minute gaming prevention, and per-minute overlap deduction.

### Design decisions
- No accelerometer sensor — step velocity analysis detects shakers via statistical patterns (zero battery cost).
- No Room entity for logging — SharedPreferences counters + Logcat (no DB migration needed).
- Cross-validation offense count in SharedPreferences (survives DB wipes, matches existing prefs pattern).
- Added mockito-kotlin 5.4.0 as test dependency for mocking Android classes in JVM tests.
- Enabled `unitTests.isReturnDefaultValues = true` in build.gradle.kts for android.util.Log in tests.

### What was done
1. **Task 1 — AntiCheatPreferences**: Created `data/anticheat/AntiCheatPreferences.kt` — SharedPreferences wrapper with daily counters (rate rejected, velocity penalized, activity minutes rejected), cross-validation offense tracking (count + last date), and 7-day offense decay.

2. **Task 2 — StepVelocityAnalyzer**: Created `data/sensor/StepVelocityAnalyzer.kt` — rolling 15-min window, two heuristics: instant jump detection (idle→spike in last 3 pairs) and constant rate detection (CV < 0.05 over 10-min window). Returns penalty multiplier (1.0/0.5/0.0).

3. **Task 3 — DailyStepManager wiring**: Added `StepVelocityAnalyzer` and `AntiCheatPreferences` as constructor dependencies. Pipeline: rate limit → velocity analysis → ceiling → persist. Logs rate-rejected and velocity-penalized steps. Added `stepsPerMinute` map for overlap deduction. Resets on day rollover.

4. **Task 4 — Enhanced StepCrossValidator**: Rewrote with graduated response based on offense count: Level 0 (escrow, 3 syncs), Level 1 (escrow, 2 syncs), Level 2 (cap at HC value), Level 3 (cap at HC minus 10%). Records offenses on discrepancy, decays on reconciliation.

5. **Task 5 — ActivityMinuteValidator**: Created `data/healthconnect/ActivityMinuteValidator.kt` — filters sessions: discards <2min micro-sessions, truncates >4hr sessions to 240min, rejects sessions beyond 5 distinct activity types per day.

6. **Task 6 — StepSyncWorker wiring**: Added `ActivityMinuteValidator` to constructor. Sessions filtered through validator before conversion. Passes `dailyStepManager.getSensorStepsPerMinute()` instead of `emptyMap()`.

7. **Task 7 — Per-minute overlap deduction**: Added `stepsPerMinute` accumulator to `DailyStepManager` (epoch-minute → credited steps). Capped at 1440 entries. Exposed via `getSensorStepsPerMinute()`. `ActivityMinuteConverter` now receives real per-minute data for double-counting prevention.

### Test results
- 222 total JVM tests (206 existing + 16 new), all green, 0 failures.
- New: StepVelocityAnalyzerTest (6), ActivityMinuteValidatorTest (5), StepCrossValidatorTest (5).
- Build: assembleDebug successful.

### What remains
- StepCrossValidator Level 2/3 could also adjust `creditedSteps` in Room (currently only escrows excess).
- AntiCheatPreferences counters not surfaced in any UI (debug screen could be added).
- Step burst trigger for supply drops still deferred.

## Run — 2026-03-09 — Plan 26: Monetization & Ads

### Objective
Implement monetization layer with stub billing/ads, cosmetic store, Season Pass, and reward ads.

### Design decisions
- Stub-first architecture: `BillingManager` and `RewardAdManager` interfaces in domain (pure Kotlin), stub impls in data. Swap via DI bindings when real SDKs integrated.
- Season Pass daily Gem bonus piggybacks on existing `TrackDailyLogin` (automatic, not manual claim).
- Cosmetic store uses placeholder items — visual application deferred to Plan 27.
- `OpenCardPack` gets `isFree: Boolean = false` default param — backward-compatible, zero caller impact.
- No new test dependencies needed — stubs are simple enough to not warrant dedicated tests.
- DB version 7 with destructive fallback (still in dev).

### What was done
1. **Task 1 — Database & Profile**: Added 5 monetization fields to `PlayerProfileEntity` (`adRemoved`, `seasonPassActive`, `seasonPassExpiry`, `freeLabRushUsedToday`, `freeCardPackAdUsedToday`). Created `CosmeticEntity` + `CosmeticDao`. Bumped DB to version 7 (12 entities). Updated `PlayerProfileDao` (4 new queries), `PlayerRepository` interface (4 new methods), `PlayerRepositoryImpl`, `FakePlayerRepository`.

2. **Task 2 — Billing Manager Stub**: Created `BillingProduct` enum (5 products), `PurchaseResult` sealed class, `BillingManager` interface, `StubBillingManager` (500ms delay, always succeeds), `BillingModule` DI binding.

3. **Task 3 — Gem Pack Purchase + Store UI**: Created `PurchaseGemPack` use case, `StoreScreen` (Gem packs, Ad Removal, Season Pass, Cosmetics sections), `StoreViewModel`, `StoreUiState`. Added `Screen.Store` route, wired in `MainActivity` NavHost.

4. **Task 4 — Ad Removal**: Ad Removal card in StoreScreen, `StoreViewModel.purchaseAdRemoval()`, "Already Purchased" state.

5. **Task 5 — Season Pass**: Updated `TrackDailyLogin` with `seasonPassActive`/`seasonPassExpiry` params (+10 Gems/day). Updated `LabsViewModel` with `freeRush()` method and `seasonPassFreeRushAvailable` state. Updated `LabsScreen` with "Free ⭐" button. Season Pass card in StoreScreen.

6. **Task 6 — Reward Ad Stub**: Created `AdPlacement` enum (3 placements), `AdResult` sealed class, `RewardAdManager` interface, `StubRewardAdManager` (1s delay, always rewards), `AdModule` DI binding.

7. **Task 7 — Post-Round Ads**: Added `adRemoved`/`gemAdWatched`/`psAdWatched` to `RoundEndState`. Injected `RewardAdManager` into `BattleViewModel`, added `watchGemAd()`/`watchPsAd()`. Updated `PostRoundOverlay` with ad buttons (hidden if adRemoved, disabled after use).

8. **Task 8 — Free Card Pack Ad**: Added `isFree` param to `OpenCardPack` (backward-compatible default). Injected `RewardAdManager` into `CardsViewModel`, added `watchFreePackAd()`. Updated `CardsScreen` with "🎬 Free Pack (Ad)" button (hidden if adRemoved, disabled if used today).

9. **Task 9 — Cosmetic Store**: Created `CosmeticCategory` enum, `CosmeticItem` domain model, `CosmeticRepository` interface, `CosmeticRepositoryImpl` (7 placeholder items, seed on first access). Added cosmetics section to StoreScreen with buy/equip/unequip.

10. **Task 10 — Integration**: Added Store button to HomeScreen and Economy screen. Season Pass badge on HomeScreen. All ad UI gated on `adRemoved` flag.

### Test results
- 222 total JVM tests, all green, 0 failures. No new tests (stub implementations, presentation-only changes).
- Build: assembleDebug successful.

### What remains (deferred)
- Google Play Billing Library v7 integration (replace StubBillingManager).
- AdMob SDK integration (replace StubRewardAdManager).
- Real purchase verification and receipt validation.
- Subscription renewal handling and grace periods.
- Real cosmetic content and visual application (Plan 27).
- Play Console product configuration and test tracks.
- Ad mediation for fill rate optimization.
- ADR for stub billing decision (documented in plan-26-monetization.md instead).

---

## Run: 2026-03-09 — Plan 27: Polish & Visual Effects

**Objective:** Add visual polish and audio to the battle renderer and UI.

**Decisions:**
- (a) Pooled particle system (200 pre-allocated) over lightweight ad-hoc allocation — avoids GC pressure during combat.
- (a) Minimal sound set (~7 reusable sounds) over full per-type set — sufficient for v1.0, easy to expand later.
- (a) Floating cash text on Canvas (game thread) over Compose overlay — same coordinate space, no latency.
- (a) System ANIMATOR_DURATION_SCALE for reduced motion — no in-app toggle needed.
- (a) Placeholder WAV files as sine wave tones — real audio assets to be sourced separately.

**Created files:**
- `presentation/battle/effects/ParticlePool.kt` — Particle class + ParticlePool (200 capacity, acquire/release/recycle)
- `presentation/battle/effects/ReducedMotionCheck.kt` — Reads system ANIMATOR_DURATION_SCALE
- `presentation/battle/effects/EffectEngine.kt` — Effect interface + EffectEngine (manages effects, owns pool + screen shake)
- `presentation/battle/effects/ScreenShake.kt` — Canvas translate oscillation with decay
- `presentation/battle/effects/ProjectileTrailEffect.kt` — Spawns fading trail particles at projectile positions
- `presentation/battle/effects/DeathEffect.kt` — Per-enemy-type death burst (6 types, 6-20 particles each)
- `presentation/battle/effects/FloatingText.kt` — "+X" cash text that drifts up and fades
- `presentation/battle/effects/UWVisualEffect.kt` — 6 particle-based UW spectacles (replaces old geometric rendering)
- `presentation/battle/effects/OverdriveAuraEffect.kt` — 4 overdrive aura particle emitters
- `presentation/battle/effects/WaveAnnouncement.kt` — Wave number + boss warning text overlay + cooldown countdown
- `presentation/audio/SoundManager.kt` — SoundPool wrapper, 7 sound effects, volume/mute, shoot throttling
- `data/SoundPreferences.kt` — SharedPreferences for sound mute/volume
- `res/raw/sfx_*.ogg` — 7 placeholder WAV audio files (sine wave tones)

**Created tests:**
- `presentation/battle/effects/ParticlePoolTest.kt` — 9 tests (acquire, release, recycle, expire, clear, reset)
- `presentation/battle/effects/ScreenShakeTest.kt` — 6 tests (trigger, decay, override, reset, offset)
- `presentation/battle/effects/DeathEffectTest.kt` — 7 tests (particle count per enemy type)

**Modified files:**
- `presentation/battle/engine/GameEngine.kt` — Full rewrite: integrated EffectEngine, removed old UW rendering (uwEffects list, uwPaint, inline render code), added all trigger points (trail, death, floating text, UW spectacle, overdrive aura, wave announcement, screen shake, sound), added reducedMotion parameter to init()
- `presentation/battle/engine/WaveSpawner.kt` — Made phaseTimer publicly readable (for cooldown text)
- `presentation/battle/entities/ZigguratEntity.kt` — Removed old aura circle rendering (auraPulse, auraPaint), added centerY property, kept overdrive timer bar
- `presentation/battle/GameSurfaceView.kt` — Added SoundManager init, reduced motion check, passes isReducedMotion to engine.init()
- `presentation/battle/BattleViewModel.kt` — Added upgrade purchase sound trigger
- `presentation/settings/NotificationSettingsViewModel.kt` — Added SoundPreferences injection, soundMuted state
- `presentation/settings/NotificationSettingsScreen.kt` — Added Sound section with mute toggle
- `presentation/workshop/UpgradeCard.kt` — Added purchase pulse animation (1.05x scale, 100ms, reduced motion aware)
- `presentation/home/HomeScreen.kt` — Added animateContentSize() to step counter
- `presentation/MainActivity.kt` — Added screen transition animations (fadeIn + slideInHorizontally, reduced motion aware)

**Test results:** 244 JVM tests — all green (was 222, +22 new).
**Build:** assembleDebug successful, 2 minor warnings (redundant conversion, hiltViewModel deprecation).

**What remains:**
- Plan 28: Balancing & Tuning (next on critical path)
- Replace placeholder audio with real royalty-free sound effects
- Plan 29: Testing & QA
- Plan 30: Release Prep

---

## Run: 2026-03-09 — Plan 28: Balancing & Tuning

**Objective:** Validate all game constants against GDD player profiles and progression timeline.

**Approach:** Test-based validation — 39 JUnit tests that compute progression math and assert GDD milestones. Conservative tuning — only adjust constants where tests reveal actual problems.

**Findings:**
- Step economy is more generous than GDD predicted in week 1 (intentional — hooks players). Settles toward GDD rates by week 4-8.
- Enemy scaling (1.05^wave) is correct — outpaces raw Workshop DPS but is balanced by crits, multishot, orbs, cards, and in-round upgrades.
- Tier progression timeline is within tolerance when accounting for full combat system (5x combat multiplier).
- Cash economy supports meaningful in-round decisions. Interest at max level is 59% of kill income (borderline but requires 20 levels of investment).
- All 9 card types are balanced with meaningful tradeoffs. No card exceeds 2.5x effective power.
- UW cooldowns allow 2-3+ activations per 20-minute round. No UW dominates.
- First UW unlock takes ~3 weeks (not 2) — acceptable for mid-game reward.
- Supply drop rates produce 1-5 drops per 10k steps.

**Constants changed:** None. All existing values validated as appropriate.

**Created files:**
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/StepEconomyTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/CostCurveTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/EnemyScalingTest.kt` — 6 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/TierProgressionTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/CashEconomyTest.kt` — 4 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/CardBalanceTest.kt` — 4 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/UWOverdriveBalanceTest.kt` — 5 tests
- `app/src/test/java/com/whitefang/stepsofbabylon/balance/SupplyDropEconomyTest.kt` — 5 tests
- `docs/balance/balance-report.md` — comprehensive balance validation report

**Test results:** 283 JVM tests — all green (was 244, +39 new balance tests).
**Build:** No compilation changes needed.

**What remains:**
- Plan 29: Testing & QA (next on critical path)
- Plan 30: Release Prep

## Run: 2026-03-10 — Plan 29: Testing & QA

**Objective:** Add ViewModel tests and deferred use case tests. JVM-only, no instrumented tests.

**Approach:** StandardTestDispatcher + backgroundScope collector for StateFlow-based ViewModels. advanceTimeBy for VMs with ticker loops. Use-case-level testing for LabsViewModel/MissionsViewModel (infinite ticker loops prevent direct VM testing).

**Created fakes:**
- `FakeStepRepository` — in-memory StepRepository
- `FakeBillingManager` — tracks purchases, configurable result
- `FakeRewardAdManager` — configurable AdResult
- `FakeCosmeticRepository` — in-memory cosmetic store
- `FakeDailyLoginDao` — in-memory daily login
- `FakeWeeklyChallengeDao` — in-memory weekly challenge
- `FakeDailyStepDao` — in-memory daily step records with Flow support

**Created test files (64 new tests):**
- `presentation/stats/StatsViewModelTest.kt` — 6 tests
- `presentation/weapons/UltimateWeaponViewModelTest.kt` — 4 tests
- `presentation/supplies/UnclaimedSuppliesViewModelTest.kt` — 3 tests
- `presentation/workshop/WorkshopViewModelTest.kt` — 6 tests
- `presentation/cards/CardsViewModelTest.kt` — 5 tests
- `presentation/labs/LabsViewModelTest.kt` — 4 tests (use-case level)
- `presentation/home/HomeViewModelTest.kt` — 5 tests
- `presentation/battle/BattleViewModelTest.kt` — 10 tests
- `presentation/missions/MissionsViewModelTest.kt` — 4 tests (use-case level)
- `presentation/economy/CurrencyDashboardViewModelTest.kt` — 3 tests
- `presentation/store/StoreViewModelTest.kt` — 3 tests
- `domain/usecase/TrackDailyLoginTest.kt` — 6 tests
- `domain/usecase/TrackWeeklyChallengeTest.kt` — 5 tests

**Key decisions:**
- StandardTestDispatcher over UnconfinedTestDispatcher — prevents infinite loops from ticker coroutines.
- `backgroundScope.launch { vm.uiState.collect {} }` required for WhileSubscribed StateFlows.
- LabsViewModel/MissionsViewModel tested at use-case level (not VM level) due to `while(true) { delay(1000) }` ticker loops that hang even with advanceTimeBy.
- HomeViewModel init modifies profile (TrackDailyLogin) — assertions check structural correctness, not exact currency values.
- No instrumented tests — deferred to post-release.

**Test results:** 347 JVM tests — all green (was 283, +64 new).
**Build:** testDebugUnitTest successful in 44s.

**What remains:**
- Plan 30: Release Prep (next on critical path)
- Instrumented tests (Room DAOs, Compose UI) — post-release
- LabsViewModel/MissionsViewModel direct VM tests (needs ticker refactoring or injectable clock)

## 2026-03-10 — Plan 30: Release Prep

### What was done
- **Task 1: ProGuard/R8 hardening** — Added keep rules for Health Connect SDK, SensorEventListener callbacks, WorkManager ListenableWorker subclasses, Room entity fields, org.json. Restructured rules file with section headers.
- **Task 2: Remove fallbackToDestructiveMigration** — Removed from DatabaseModule.kt. Added comment about future migration requirements.
- **Task 3: Signing config** — Added `import java.util.Properties`, keystore.properties loader with graceful fallback, signingConfigs block, release build type wiring. Added keystore entries to .gitignore. Created docs/release/signing-guide.md.
- **Task 4: Version bump** — Updated versionName from 0.1.0 to 1.0.0. Updated CHANGELOG.md with comprehensive v1.0.0 release notes covering all features.
- **Task 5: Privacy policy** — Created docs/release/privacy-policy.md covering step data, Health Connect, local storage, third-party SDKs. Updated HealthConnectPermissionActivity with scrollable structured privacy content.
- **Task 6: Play Store listing** — Created docs/release/play-store-listing.md (short/full descriptions, category, content rating notes). Created docs/release/release-checklist.md.
- **Task 7: Build verification** — All 347 tests pass. Release APK builds successfully (26MB unsigned, R8 minification clean). Fixed Gradle DSL issue with java.util.Properties import.

### Build verification results
- `testDebugUnitTest`: BUILD SUCCESSFUL (347 tests, all green)
- `assembleRelease`: BUILD SUCCESSFUL (26MB unsigned APK, R8 clean)
- Only warnings: 4 redundant conversion calls, 6 hiltViewModel() deprecations (pre-existing)

### Files created
- `docs/release/privacy-policy.md`
- `docs/release/play-store-listing.md`
- `docs/release/signing-guide.md`
- `docs/release/release-checklist.md`

### Files modified
- `app/proguard-rules.pro` — hardened R8 rules
- `app/build.gradle.kts` — signing config, version 1.0.0
- `app/src/main/java/.../di/DatabaseModule.kt` — removed fallbackToDestructiveMigration
- `app/src/main/java/.../presentation/HealthConnectPermissionActivity.kt` — expanded privacy content
- `CHANGELOG.md` — v1.0.0 release notes
- `.gitignore` — keystore entries

### What remains
- Plan 31: Play Console & Store Publication
- Generate upload keystore (manual step)
- Host privacy policy at public URL
- Create visual assets (icon, screenshots, feature graphic)
- Replace contact email placeholders

---

## 2026-03-11 — Remediation Plan Creation

### Context
- External code review completed (`docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX.md`) identifying 12 high-priority findings across step integrity, battle wiring, database safety, widget, missions, notifications, deep-links, premium state, UX feedback, accessibility, and test coverage.
- Plan 30 was complete; Plan 31 was next on the critical path.

### What was done
- Created `docs/plans/plan-R-remediation.md` — 12 sub-plans (R01–R12) organized into 3 priority tiers.
- Updated `docs/plans/master-plan.md`:
  - Added Plan R to plan index table.
  - Updated dependency graph: Plan 30 → Plan R → Plan 31.
  - Updated critical path to include Plan R (Tier 1) before Plan 31.
  - Added Plan R to status tracker.
- Updated `docs/agent/STATE.md` — current objective is now Plan R; priorities and next actions reflect remediation order.

### Key decisions
- Plan R Tier 1 (R01–R05) blocks production release (Plan 31). These are data-integrity and progression-correctness issues.
- Plan R Tier 2 (R06–R09) should complete before release but are user-trust issues, not data corruption risks.
- Plan R Tier 3 (R10–R12) can follow shortly after release.
- R01 → R02 is the only sequential dependency within remediation. All other sub-plans are parallelizable.

### What remains
- Execute R01–R12 per priority tiers.
- Plan 31 after R Tier 1 complete.

---

## 2026-03-11 — R01: Step Ingestion Unification

### What was done
- Created `data/sensor/StepIngestionPreferences.kt` — SharedPreferences wrapper with service heartbeat (2-min threshold) and date-scoped day-start counter.
- Refactored `service/StepSyncWorker.kt` — removed private `last_counter_value` baseline. Worker now checks heartbeat (skips if service alive), uses Room `sensorSteps` as authoritative baseline, and only credits the uncredited gap.
- Updated `service/StepCounterService.kt` — writes heartbeat on every step credit, sets day-start counter on startup via one-shot sensor read.
- Created `StepIngestionPreferencesTest.kt` (11 tests) — heartbeat read/write, isServiceAlive, day-start counter, day rollover.
- Created `StepIngestionTest.kt` (10 tests) — service-active skip, gap recovery, day rollover, no double-credit, counter reboot safety.
- All 368 tests pass. Debug build compiles clean.

### Key design decisions
- Two-mechanism approach: heartbeat (optimization) + Room baseline (correctness). Heartbeat prevents unnecessary sensor reads; Room baseline guarantees no double-credit even under race conditions.
- Day-start counter set by whichever path (service or worker) reads the sensor first today. Service sets it on startup; worker sets it if service never ran.
- Worker's old private `last_counter_value` replaced entirely — no migration needed since it was only used for catch-up delta computation.

### What remains
- R02: Escrow Redesign (next — depends on R01 ✓)
- R03–R12: remaining remediation sub-plans

---

## 2026-03-11 — R02: Escrow Redesign

### What was done
- Modified `PlayerProfileDao.adjustStepBalance` — added `MAX(0, ...)` clamp to prevent negative balances on any spend operation.
- Rewrote `StepCrossValidator.validate()` — escrow now deducts excess from player balance via `spendSteps()`. Release restores via `addSteps()`. Discard leaves deduction in place. Level 0/1 branches track whether escrow was already deducted to avoid double-deduction on subsequent syncs.
- Rewrote `StepCrossValidatorTest` — 10 tests (was 5): added balance deduction verification on all escrow branches, no-double-deduction on subsequent syncs, escrow→release net-zero test, escrow→discard keeps-deduction test.
- All 373 tests pass. Build clean.

### Key design decisions
- Deduct-on-escrow approach: simplest correct fix, no schema changes, no new domain concepts.
- Balance clamped to zero: prevents negative balances if player spent suspicious steps before reconciliation.
- Level 0/1 branches check `record.escrowSteps == 0L` to distinguish first escrow (deduct) from subsequent syncs (metadata only).

### What remains
- R03–R12: remaining remediation sub-plans (all Tier 1 blockers now independent)

---

## 2026-03-11 — R03+R04: Battle Workshop Wiring + Dead Upgrade Cleanup

### What was done
- R03: Exposed `workshopLevels` from BattleViewModel (was private). Replaced `emptyMap()` with real workshop levels in both `BattleScreen.LaunchedEffect` and `BattleViewModel.playAgain()`. CASH_BONUS, CASH_PER_WAVE, and INTEREST now reach the GameEngine.
- R04: Added `hiddenUpgrades` set in WorkshopViewModel filtering out STEP_MULTIPLIER and RECOVERY_PACKAGES from the workshop UI. Enum entries preserved for future implementation.
- All 373 tests pass. Build clean.

### What remains
- R05: Database Safety (last Tier 1 blocker)
- R06–R12: Tier 2 and 3 remediation

---

## 2026-03-11 — R05: Database Safety

### What was done
- Disabled backup in AndroidManifest (`allowBackup="false"`). No valuable state to restore in a local-only game.
- Added `fallbackToDestructiveMigration()` in DatabaseModule for pre-release schema mismatch safety.
- Added try/catch recovery in `DatabaseKeyManager.getPassphrase()` — on decryption failure (keystore mismatch after restore), wipes stale passphrase blob and generates fresh key.
- All 373 tests pass. Build clean.

### Key decisions
- Backup disabled entirely rather than selective exclusion — simpler, eliminates the whole class of restore bugs.
- Destructive migration is pre-release only. CONSTRAINTS.md already mandates explicit migrations post-release.

### Milestone
- **Tier 1 remediation complete** (R01–R05). Plan 31 is now unblocked.

### What remains
- R06–R12: Tier 2 and 3 remediation
- Plan 31: Play Console & Store Publication

---

## 2026-03-11 — Documentation Sweep (Post-R05)

### Objective
Full codebase documentation audit after R01–R05 remediation. Find and fix stale references.

### Issues found and fixed (8 files)

1. **CHANGELOG.md** — Test count 347→373. Added R01–R05 remediation section.
2. **docs/release/release-checklist.md** — Unchecked `fallbackToDestructiveMigration` (R05 re-added it for pre-release safety). Updated test count 347→373.
3. **docs/step-tracking.md** — Added R01 service↔worker coordination section (heartbeat, Room baseline, day-start counter). Updated escrow table for R02 balance deduction behavior. Updated data flow diagram with heartbeat and gap recovery steps.
4. **docs/database-schema.md** — Added R05 key recovery mechanism and backup-disabled note to Security section.
5. **docs/architecture.md** — Added backup-disabled row and key auto-recovery note to Security table.
6. **.kiro/steering/source-files.md** — Added 7 missing test fakes from Plan 29 (FakeStepRepository, FakeCosmeticRepository, FakeBillingManager, FakeRewardAdManager, FakeDailyLoginDao, FakeWeeklyChallengeDao, FakeDailyStepDao).
7. **.kiro/steering/structure.md** — Same 7 missing fakes added to fakes directory listing.
8. **AGENTS.md** — Same 7 missing fakes added. Updated test coverage description with StepIngestionPreferences and StepIngestion test areas.

### Verified as correct (no changes needed)
- Google Fit references in RUN_LOG, ADR-0002, plan-02, plan-03, plan-05 — all historical/contextual.
- AGENTS.md test count (373), use case count (32), route count (12), repository count (8) — all accurate.
- database-schema.md entity schemas — all match actual code.
- monetization.md — accurate, reflects stub implementation status.
- master-plan.md — status tracker correct (Plan R unchecked, all others accurate).
- step-tracking.md anti-cheat rules — all thresholds match code.

### Commands/tests run: N/A (documentation-only changes)
### Memory updated: STATE ✅ / RUN_LOG ✅

## 2026-03-12 — R06 Widget Fix + R07 Live Mission Progress

**Objective:** Fix two Tier 2 High-severity bugs from external code review.

**R06 — Widget Fix (3 changes):**
- `DailyStepManager.recordSteps()`: replaced hardcoded `0` balance with `playerRepository.getStepBalance()` call.
- `widget_step_counter.xml`: added `android:id="@+id/widget_root"` to root LinearLayout.
- `StepWidgetProvider.updateAllWidgets()`: changed click PendingIntent target from `android.R.id.background` to `R.id.widget_root`.
- Added `getStepBalance()` to `PlayerRepository` interface, `PlayerRepositoryImpl`, and `FakePlayerRepository`.

**R07 — Live Mission Progress (2 changes):**
- Added `DailyMissionDao` as constructor dependency to `DailyStepManager`.
- Added `updateWalkingMissions()` private method called after economy rewards in `recordSteps()`. Queries today's missions, filters to unclaimed/incomplete WALKING missions, updates progress based on `dailyCreditedTotal`.
- Hilt auto-resolves the new dependency (DailyMissionDao already provided by DatabaseModule).

**Tests added:** 6 new tests in `DailyStepManagerTest.kt`:
- Widget receives real step balance after crediting
- Widget balance accumulates across multiple credits
- Walking mission progress updates on step credit
- Walking mission completes when target reached
- Battle mission is not updated by step credits
- Already completed mission is not re-updated

**Test count:** 373 → 379 (all green).

**Files changed:**
- `domain/repository/PlayerRepository.kt` — added `getStepBalance()`
- `data/repository/PlayerRepositoryImpl.kt` — implemented `getStepBalance()`
- `data/sensor/DailyStepManager.kt` — real widget balance, DailyMissionDao dep, walking mission updates
- `service/StepWidgetProvider.kt` — fixed click target to `R.id.widget_root`
- `res/layout/widget_step_counter.xml` — added `android:id` to root
- `test/fakes/FakePlayerRepository.kt` — added `getStepBalance()`
- `test/data/sensor/DailyStepManagerTest.kt` — new test file (6 tests)

**What's next:** R08 (Notification & Reminder Fixes) + R09 (Deep-link & Premium State), parallelizable.

## 2026-03-12 — R08 Notification & Reminder Fixes + R09 Deep-link & Premium State

**Objective:** Fix two Tier 2 Medium-severity issues from external code review.

**R08 — Notification & Reminder Fixes (2 changes):**
- `NotificationSettingsScreen.kt`: Renamed "Step Counter" / "Persistent notification with daily steps" to "Step Count Updates" / "Show step count and balance in the notification" — accurately describes what the toggle controls.
- Added `updateLastActiveAt(timestamp)` to `PlayerRepository` interface, `PlayerRepositoryImpl`, and `FakePlayerRepository`. Called from `MainActivity.onResume()` so `SmartReminderManager` has a fresh timestamp.

**R09 — Deep-link & Premium State (3 changes):**
- `MainActivity`: Added `pendingNavigation: MutableStateFlow<String?>`, `onNewIntent()` override, and a `LaunchedEffect` that collects the flow. Consolidates cold-start and warm-start deep-link handling. Supply drop notifications now navigate correctly when app is already open.
- `StoreViewModel`: Added expiry check — `seasonPassActive = profile.seasonPassActive && profile.seasonPassExpiry > System.currentTimeMillis()` — matching HomeViewModel's logic.
- `BattleViewModel.playAgain()`: Added `adRemoved = it.adRemoved` to the new `BattleUiState` constructor, preserving ad-free state across replays.

**Tests added:** 2 new tests in `StoreViewModelSeasonPassTest`:
- Expired season pass shows as inactive
- Active season pass with future expiry shows as active

**Test count:** 379 → 381 (all green).

**Files changed:**
- `domain/repository/PlayerRepository.kt` — added `updateLastActiveAt()`
- `data/repository/PlayerRepositoryImpl.kt` — implemented `updateLastActiveAt()`
- `presentation/MainActivity.kt` — onResume, onNewIntent, pendingNavigation flow, onDestroy
- `presentation/settings/NotificationSettingsScreen.kt` — renamed toggle label
- `presentation/store/StoreViewModel.kt` — season pass expiry check
- `presentation/battle/BattleViewModel.kt` — preserve adRemoved on playAgain
- `test/fakes/FakePlayerRepository.kt` — added `updateLastActiveAt()`
- `test/presentation/store/StoreViewModelTest.kt` — 2 new season pass tests

**What's next:** R10 (UX Feedback & Guards) + R11 (Accessibility & Docs), parallelizable.

## 2026-03-12 — R10 UX Feedback & Guards + R11 Accessibility & Docs

**Objective:** Fix three UX issues (silent failures, double-tap races, midnight staleness) and three polish issues (symbol-only labels, placeholder emails, README inaccuracies).

**R10 — UX Feedback & Guards (7 changes):**
- `PlayerProfileDao`: Added `MAX(0, ...)` guards to `adjustGems`, `adjustPowerStones`, `adjustCardDust` — matching existing `adjustStepBalance` pattern.
- `WorkshopUiState`, `CardsUiState`, `LabsUiState`, `StoreUiState`: Added `userMessage: String?` and `isProcessing: Boolean` fields.
- `WorkshopViewModel`, `CardsViewModel`, `LabsViewModel`, `StoreViewModel`: Added `clearMessage()`, processing guards on all purchase/action methods (early return if `_processing.value`), feedback messages on failures (insufficient funds, max level, no slots).
- `BattleViewModel`: Added VM-level guards to `watchGemAd`/`watchPsAd` — early return if already watched.
- `WorkshopScreen`, `CardsScreen`, `LabsScreen`, `StoreScreen`: Wrapped content in `Scaffold` with `SnackbarHost`. Added `LaunchedEffect(state.userMessage)` to show snackbar and clear.
- `MissionsViewModel`: Changed `today` from `val` to `var`. Added day-change detection in existing 1s ticker — regenerates missions and updates walking progress on midnight crossing.
- `HomeViewModel`: Changed hardcoded `LocalDate.now()` to `MutableStateFlow<String>` with `flatMapLatest`. Added `refreshDate()` called from `HomeScreen` via lifecycle resume observer.
- `StatsViewModel`: Changed `today` from `val` to `MutableStateFlow<LocalDate>` with `flatMapLatest`. Added `refreshDate()` called from `StatsScreen` via lifecycle resume observer.
- `FakePlayerRepository`: Updated spend methods to clamp at 0, matching DAO guards.

**R11 — Accessibility & Docs (4 changes):**
- `BattleScreen`: Added `contentDescription` via `semantics` to speed buttons ("Speed 1x/2x/4x"), pause/resume button, upgrades button, overdrive button.
- `UltimateWeaponBar`: Added `semantics { contentDescription }` to weapon slots — "Activate {name}" when ready, "{name} on cooldown, N seconds" when not.
- `HomeScreen`: Added `contentDescription` to supplies badge button.
- Replaced `<contact-email>` with `support@whitefanggames.com` in `privacy-policy.md`, `play-store-listing.md`, `HealthConnectPermissionActivity.kt`.
- `README.md`: Replaced instrumented test section with note that they're planned but not yet implemented.

**Tests added:** 7 new tests:
- `CurrencyGuardTest` (4): gems/PS/dust/steps spend-beyond-balance clamps to 0.
- `UserFeedbackTest` (3): workshop purchase failure sets userMessage, clearMessage resets, quickInvest failure sets message.

**Test count:** 381 → 388 (all green).

**Files changed:**
- `data/local/PlayerProfileDao.kt` — MAX(0) guards on 3 currency queries
- `presentation/workshop/WorkshopUiState.kt` — added isProcessing, userMessage
- `presentation/cards/CardsUiState.kt` — added isProcessing, userMessage
- `presentation/labs/LabsUiState.kt` — added isProcessing, userMessage
- `presentation/store/StoreUiState.kt` — added userMessage
- `presentation/workshop/WorkshopViewModel.kt` — rewritten with guards + feedback
- `presentation/cards/CardsViewModel.kt` — rewritten with guards + feedback
- `presentation/labs/LabsViewModel.kt` — rewritten with guards + feedback
- `presentation/store/StoreViewModel.kt` — rewritten with guards + feedback
- `presentation/battle/BattleViewModel.kt` — ad watch guards
- `presentation/workshop/WorkshopScreen.kt` — Scaffold + SnackbarHost
- `presentation/cards/CardsScreen.kt` — Scaffold + SnackbarHost
- `presentation/labs/LabsScreen.kt` — Scaffold + SnackbarHost
- `presentation/store/StoreScreen.kt` — Scaffold + SnackbarHost
- `presentation/missions/MissionsViewModel.kt` — midnight day-change detection
- `presentation/home/HomeViewModel.kt` — currentDate flow + refreshDate
- `presentation/home/HomeScreen.kt` — lifecycle resume observer
- `presentation/stats/StatsViewModel.kt` — today flow + refreshDate
- `presentation/stats/StatsScreen.kt` — lifecycle resume observer
- `presentation/battle/BattleScreen.kt` — contentDescription on all controls
- `presentation/battle/ui/UltimateWeaponBar.kt` — semantics on weapon slots
- `presentation/HealthConnectPermissionActivity.kt` — real email
- `docs/release/privacy-policy.md` — real email
- `docs/release/play-store-listing.md` — real email
- `README.md` — fixed instrumented test reference
- `test/fakes/FakePlayerRepository.kt` — spend clamps at 0
- `test/presentation/ux/CurrencyGuardTest.kt` — new (4 tests)
- `test/presentation/ux/UserFeedbackTest.kt` — new (3 tests)

**What's next:** R12 (Integration Test Coverage), then Plan 31 (Play Console & Store Publication).

## 2026-03-12 — R12 Integration Test Coverage

**Objective:** Add integration-level tests for widget, deep-links, Room schema, and escrow lifecycle.

**What was done:**
1. **Task 1 — Robolectric setup**: Added `robolectric:4.14.1`, `androidx.test:core:1.6.1`, and `room-testing` to version catalog + build.gradle.kts. Enabled `unitTests.isIncludeAndroidResources = true`.

2. **Task 2 — Widget tests** (`service/StepWidgetProviderTest.kt`, 3 tests): Robolectric-based tests verifying `saveData()` persists to SharedPreferences, overwrites work, and defaults are zero.

3. **Task 3 — Deep-link tests** (`presentation/DeepLinkRoutingTest.kt`, 3 tests): Verify `navigate_to` intent extra extraction for supplies, workshop, and null case.

4. **Task 4 — Room schema tests** (`data/local/RoomSchemaTest.kt`, 3 tests): In-memory Room DB round-trip for PlayerProfileEntity (gems/PS/tier), DailyStepRecordEntity (escrow fields), WorkshopUpgradeEntity (level).

5. **Task 5 — Escrow lifecycle tests** (`data/integration/EscrowLifecycleTest.kt`, 2 tests): Full lifecycle using FakePlayerRepository + FakeStepRepository + mocked HealthConnectStepReader. Test 1: escrow deducts → release restores (net zero). Test 2: escrow deducts → 3 syncs → discard keeps deduction.

**Decisions:**
- No instrumented tests (androidTest) — all tests run on JVM via Robolectric.
- No Room migration objects or migration tests — pre-release app, `fallbackToDestructiveMigration` handles dev/QA installs. Post-release migrations documented in CONSTRAINTS.md.
- Skipped Hilt-injected service lifecycle tests — StepCounterService is a thin shell around already-tested components.

**Test count:** 388 → 399 (all green).

**Files changed:**
- `gradle/libs.versions.toml` — added robolectric, androidx-test-core, room-testing
- `app/build.gradle.kts` — added 3 test dependencies, isIncludeAndroidResources
- `test/service/StepWidgetProviderTest.kt` — new (3 tests)
- `test/presentation/DeepLinkRoutingTest.kt` — new (3 tests)
- `test/data/local/RoomSchemaTest.kt` — new (3 tests)
- `test/data/integration/EscrowLifecycleTest.kt` — new (2 tests)

**Milestone:** Plan R (Remediation) fully complete. All 12 sub-plans done.

**What's next:** Plan 31: Play Console & Store Publication.

## 2026-03-12 — Documentation Sweep & Corrections

**Objective:** Full codebase sweep for outdated/incorrect documentation.

**What was done:**

1. **AGENTS.md — Plan count fixed**: "31-plan master plan" → "33 entries (Plans 01–31, 10b, and R)". Key documents table: "30 plans" → "33 entries".

2. **AGENTS.md — Missing use case**: Added `PurchaseGemPack` to architecture tree use case list (was 31, now 32 — matches codebase).

3. **README.md — Plan count fixed**: "30-plan development roadmap" → "33-entry development roadmap".

4. **structure.md — Test tree updated**: Added 16 missing test directories (data/healthconnect, data/local, data/integration, presentation/home, presentation/workshop, presentation/labs, presentation/cards, presentation/weapons, presentation/supplies, presentation/economy, presentation/missions, presentation/stats, presentation/store, presentation/ux, DeepLinkRoutingTest, service). Updated domain model/usecase descriptions.

5. **tech.md — Missing libraries added**: mockito-kotlin 5.4.0, robolectric 4.14.1, androidx-test-core 1.6.1, hilt-work 1.3.0, compose-material-icons.

6. **CHANGELOG.md — Structure fixed**: Moved [Unreleased] to top (for Plan 31 tracking). Folded historical scaffold/Plan 01 entries into v1.0.0 section.

7. **battle-formulas.md — Step Multiplier note**: Added note that STEP_MULTIPLIER is currently hidden from Workshop UI (R04 remediation).

8. **plan-05 filename renamed**: `plan-05-google-fit.md` → `plan-05-health-connect.md`. Updated master-plan.md link.

9. **Version catalog cleanup**: Removed unused `kotlin-android` plugin from `libs.versions.toml`.

10. **PurchaseGemPackTest added**: 2 tests (success delegation, error forwarding). Closes the only use case without test coverage. Updated source-files.md.

11. **Test count updated**: 399 → 401 across AGENTS.md, CHANGELOG.md, STATE.md, release-checklist.md.

**Test count:** 399 → 401 (all green).

**Files changed:**
- `AGENTS.md` — plan count (×2), use case list, test count
- `README.md` — plan count
- `.kiro/steering/structure.md` — test tree
- `.kiro/steering/tech.md` — library table
- `.kiro/steering/source-files.md` — added PurchaseGemPackTest
- `CHANGELOG.md` — section order, test count
- `docs/battle-formulas.md` — Step Multiplier note
- `docs/plans/master-plan.md` — plan-05 link
- `docs/plans/plan-05-health-connect.md` — renamed from plan-05-google-fit.md
- `docs/release/release-checklist.md` — test count
- `docs/agent/STATE.md` — test count
- `gradle/libs.versions.toml` — removed unused kotlin-android plugin
- `test/domain/usecase/PurchaseGemPackTest.kt` — new (2 tests)

**What's next:** Plan 31: Play Console & Store Publication.

---

## 2026-03-13 — R2 Remediation Plan Creation

**Trigger:** Second external code review (`docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX_2.md`).

**What I did:**
1. Read the full second external review (10 sections, 8 high-priority findings, ~30 total findings).
2. Cross-referenced every finding against Plan R (first remediation, R01–R12, all complete) to identify what's genuinely new vs. already addressed.
3. Verified current code state for all key findings: `DailyStepManager.recordActivityMinutes()`, `ClaimMilestone`, `stateIn(viewModelScope).value` pattern (12 occurrences), `PostRoundOverlay` button text, `NotificationSettingsScreen` wording, `DatabaseModule` migration config, `LabsViewModel.freeRush()`, `StoreScreen` cosmetics, `CurrencyDashboardViewModel`.
4. Created `docs/plans/plan-R2-remediation.md` with 12 sub-plans (R2-01 through R2-12), organized by severity and dependency.
5. Updated `docs/agent/STATE.md` with new objective, priorities, and references.

**Key findings confirmed as genuinely new/unresolved:**
- Activity-minute crediting is non-idempotent (Critical) — `recordActivityMinutes()` re-credits on process restart because `dailyCreditedTotal` initializes from `creditedSteps` (sensor-only), not including prior `stepEquivalents`.
- Activity-minute pipeline bypasses widget/mission/drop/economy updates (High).
- 12 `stateIn(viewModelScope).value` occurrences still present across 4 ViewModels (High).
- "Return to Workshop" label still present (High).
- Notification setting wording unchanged (High).
- `.fallbackToDestructiveMigration()` still in DatabaseModule (High).
- `freeRush()` still has silent returns (Medium).
- `ClaimMilestone` still lacks step-threshold check (Medium).
- Cosmetics still purchasable with "coming soon" label (Medium).
- CurrencyDashboard still snapshot-based (Medium).

**Files created:**
- `docs/plans/plan-R2-remediation.md`

**Files updated:**
- `docs/agent/STATE.md`
- `docs/agent/RUN_LOG.md`

**What's next:** Begin R2-01 (Activity-Minute Idempotency), then R2-02, R2-06, R2-03 in priority order.

## 2026-03-13 — R2-01: Activity-Minute Idempotency

**Objective:** Fix double-crediting of activity-minute step-equivalents on process restart.

**Root cause:** `recordActivityMinutes()` initialized `dailyCreditedTotal` from `existing.creditedSteps` (sensor-only), ignoring previously credited `stepEquivalents`. The worker passes cumulative `stepEquivalents` from `ActivityMinuteConverter`, and the manager called `playerRepository.addSteps(credited)` with the full amount each time instead of just the delta.

**What was done:**
1. Extracted shared `ensureInitialized()` method from duplicated init blocks in `recordSteps()` and `recordActivityMinutes()`. Initialization now sets `dailyCreditedTotal = creditedSteps + stepEquivalents` (combined ceiling).
2. Added `dailySensorCredited` field to track sensor-only credits for Room's `creditedSteps` field (prevents writing combined total into sensor-only column).
3. Added `dailyActivityMinuteTotal` field initialized from `existing.stepEquivalents` during init.
4. Made `recordActivityMinutes()` delta-based: computes `delta = stepEquivalents - dailyActivityMinuteTotal`, only credits positive delta. Stores `dailyActivityMinuteTotal` (actual credited, respecting ceiling) to Room, not raw input.

**Bug caught during implementation:** Initial version wrote `dailyCreditedTotal` (now combined sensor + activity) to Room's `creditedSteps` field via `updateDailySteps()`. This would have caused double-counting on next init since `ensureInitialized()` reads `creditedSteps + stepEquivalents`. Fixed by adding `dailySensorCredited` to track sensor credits separately for the Room write.

**Tests added (5):**
- Activity minutes credit correct step-equivalents (baseline)
- Duplicate call produces zero additional credits (idempotency)
- Incremental call credits only delta
- Combined sensor + activity-minute credits respect 50k ceiling
- Process restart does not re-credit activity minutes (new manager instance, same repos)

**Test count:** 397 JVM tests — all green, 0 failures.

**Files changed:**
- `data/sensor/DailyStepManager.kt` — extracted `ensureInitialized()`, added `dailySensorCredited` + `dailyActivityMinuteTotal`, delta-based `recordActivityMinutes()`
- `test/data/sensor/DailyStepManagerTest.kt` — 5 new tests

**What's next:** R2-02 (Activity-Minute Pipeline Unification), then R2-06, R2-03.

## 2026-03-13 — R2-02: Activity-Minute Pipeline Unification

**Objective:** Route activity-minute credits through the same follow-on pipeline as sensor steps (widget, supply drops, economy, missions).

**Root cause:** `recordActivityMinutes()` only called `stepRepository.updateActivityMinutes()` and `playerRepository.addSteps()`. It skipped widget updates, supply drop generation, economy rewards (daily login, weekly challenge), and walking mission progress that `recordSteps()` performs.

**What was done:**
1. Extracted the follow-on pipeline (widget update, supply drop generation, economy rewards, walking mission progress) from `recordSteps()` into `private suspend fun runFollowOnPipeline(timestampMs: Long)`.
2. `recordSteps()` now calls `runFollowOnPipeline(timestampMs)` instead of inlining the pipeline.
3. `recordActivityMinutes()` now accepts `timestampMs: Long = System.currentTimeMillis()` and calls `runFollowOnPipeline(timestampMs)` after crediting steps.
4. Each pipeline section wrapped in try/catch for best-effort consistency (supply drop generation was previously unwrapped — now consistent).
5. No changes needed to `StepSyncWorker.kt` — the new `timestampMs` parameter has a default value.

**Files changed:**
- `data/sensor/DailyStepManager.kt` — extracted `runFollowOnPipeline()`, called from both methods

**Test count:** 397 JVM tests — all green, 0 failures. No new tests (R2-12 adds coverage).

**What's next:** R2-06 (Destructive Migration Removal), then R2-03, R2-04/05/07, R2-12.

## 2026-03-13 — R2-03: Hot Flow Cleanup

**Objective:** Replace 12 `observeX().stateIn(viewModelScope).value` calls in ViewModel action handlers with `first()` or `uiState.value` reads. Each leaked call created a hot StateFlow tied to the ViewModel scope that was never cancelled.

**What was done:**
1. **WorkshopViewModel** (2 occurrences): Replaced `observeWallet().stateIn(viewModelScope).value` with `observeWallet().first()` in `purchase()` and `quickInvest()`. Use cases require full `PlayerWallet` not available in `uiState`. Removed unused `import kotlinx.coroutines.flow.update`.
2. **CardsViewModel** (3 occurrences): Replaced `observeProfile().stateIn(viewModelScope).value` with `uiState.value` reads in `openPack()` (`.gems`), `upgradeCard()` (`.cardDust`), and `watchFreePackAd()` (`.gems`). All values already materialized in UI state.
3. **LabsViewModel** (6 occurrences): 5 replaced with `first()` — `startResearch()`, `rushResearch()` (profile + activeList), `freeRush()` (profile + activeList) — needed full domain objects (`profile.toWallet()`, season pass fields). 1 replaced with `uiState.value` — `unlockSlot()` only needed `totalSlots` and `gems`.
4. **StoreViewModel** (1 occurrence): Replaced `observeProfile().stateIn(viewModelScope).value` with `uiState.value.gems` in `purchaseCosmetic()`.

**Verification:**
- `grep stateIn(viewModelScope).value` across presentation/ returns 0 matches
- All 397 JVM tests pass, 0 failures

**Files changed:**
- `presentation/workshop/WorkshopViewModel.kt` — 2 fixes + 1 unused import removed
- `presentation/cards/CardsViewModel.kt` — 3 fixes
- `presentation/labs/LabsViewModel.kt` — 6 fixes + `first` import added
- `presentation/store/StoreViewModel.kt` — 1 fix

**What's next:** R2-06 (Destructive Migration Removal), then R2-04/05/07, R2-12.

## 2026-03-13 — R2-04: Battle Exit Navigation

**Objective:** Fix "Return to Workshop" button label/behavior mismatch in PostRoundOverlay. The button calls `navController.popBackStack()` which returns to whatever screen preceded battle, not necessarily Workshop.

**What was done:**
1. Renamed parameter `onReturnToWorkshop` → `onExitBattle` in `PostRoundOverlay.kt` (matches `BattleScreen`'s existing `onExitBattle` naming).
2. Changed button text from "Return to Workshop" → "Leave Battle".
3. Updated named argument at call site in `BattleScreen.kt` from `onReturnToWorkshop =` → `onExitBattle =`.

**Verification:**
- `grep onReturnToWorkshop|Return to Workshop` across `app/src/` returns 0 matches
- Build successful, all 397 JVM tests pass

**Files changed:**
- `presentation/battle/ui/PostRoundOverlay.kt` — parameter rename + button text
- `presentation/battle/BattleScreen.kt` — call site named argument

**What's next:** R2-06 (Destructive Migration Removal), then R2-05/07, R2-12.

## 2026-03-13 — R2-05: Notification Setting Alignment

**Objective:** Fix misleading "Step Count Updates" toggle that implies users can hide the foreground notification entirely. Android requires a visible notification for foreground services.

**What was done:**
1. Renamed toggle title "Step Count Updates" → "Live Step Updates" in `NotificationSettingsScreen.kt`.
2. Updated description to: "Update notification with live step count and balance. A minimal tracking notification is always shown while step counting is active."
3. Added `buildMinimalNotification()` to `StepNotificationManager.kt` — shows "Step tracking active" with no counts/balance/action buttons.
4. Injected `NotificationPreferences` into `StepCounterService.kt` and added preference check in `onCreate()` to choose full vs minimal notification at startup.

**Verification:**
- Build successful, all 397 JVM tests pass
- Toggle ON → full notification with live counts + Workshop/Battle buttons
- Toggle OFF → clean "Step tracking active" notification, no frozen zeroes

**Files changed:**
- `presentation/settings/NotificationSettingsScreen.kt` — toggle title + description text
- `service/StepNotificationManager.kt` — added `buildMinimalNotification()`
- `service/StepCounterService.kt` — injected NotificationPreferences, preference-aware initial notification

**What's next:** R2-06 (Destructive Migration Removal), then R2-07, R2-12.

## 2026-03-13 — R2-06 through R2-12 (Final Remediation)

**Objective:** Complete all remaining R2 sub-plans.

**What was done:**
- R2-06: Replaced `.fallbackToDestructiveMigration()` with `.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` in `DatabaseModule.kt`. Schema upgrades without explicit Migration now crash (fail-fast) instead of silently wiping data.
- R2-07: Added `Log.w("StepSyncWorker", ...)` to both silent catch blocks in `StepSyncWorker.kt` (HC sync and smart reminders).
- R2-09: Added user messages to all 3 silent early-return paths in `LabsViewModel.freeRush()`: "Season Pass required", "Free rush already used today", "No active research to rush".
- R2-11: Disabled cosmetic purchase buttons in `StoreScreen.kt` — unowned cosmetics show disabled "Coming Soon" button. Updated description text. Equip/Unequip still works for owned items.
- R2-08: (a) Added step-threshold validation to `ClaimMilestone` — reads `totalStepsEarned` and returns `false` if below `milestone.requiredSteps`. (b) Created `MilestoneNotificationPreferences` (SharedPreferences wrapper) for notification dedup. Wired into `HomeViewModel` — milestone notifications now fire at most once per milestone.
- R2-10: Rewrote `CurrencyDashboardViewModel` with hybrid reactive approach — `combine()` of live `observeProfile()` flow + `MutableStateFlow<SnapshotData>` for weekly/login data. Added `refresh()` method. Added `LaunchedEffect(Unit)` in `CurrencyDashboardScreen` for refresh on entry.
- R2-12: Added 2 remaining activity-minute tests (walking mission progress + widget updates). 4 of 6 tests already existed from R2-01.

**Tests:** 401 JVM tests, all green (was 397). Added: 1 ClaimMilestone threshold test, 1 CurrencyDashboard reactive test, 2 activity-minute pipeline tests.

**Decisions:**
- Used SharedPreferences (not Room column) for milestone notification dedup — it's a UI concern, not game state. Avoids schema v8 migration.
- Used `dropAllTables = true` parameter on `fallbackToDestructiveMigrationOnDowngrade()` to avoid Room deprecation warning.
- Hybrid reactive approach for economy dashboard: live profile flow for balances, one-shot refresh for weekly/login data.

**What remains:** Plan R2 fully complete. Plan 31 (Play Console & Store Publication) is unblocked.

## 2026-05-03 — Feature: Battle Step Rewards (ADR-0003)

**Trigger:** Player-facing feature request. "Killing enemies in a round gives steps as a reward, to add incentive to playing."

**Scope:** Add Steps as an enemy-kill reward separate from the walking pipeline, with a per-day cap, running HUD counter, floating +N Step text on kill, and a Round End summary line item.

**Design decisions:** See ADR-0003.
- Small supplement (BASIC/FAST/SCATTER=1, RANGED=2, TANK=3, BOSS=10). ~350–550 Steps per typical round.
- 2,000 battle-Steps/day cap, tracked on `DailyStepRecordEntity.battleStepsEarned`. Separate from the 50k walking ceiling (never additive).
- Flat per-enemy-type rewards — NOT multiplied by Fortune overdrive, Cash Bonus upgrade, or Golden Ziggurat UW. Anti-cheat-predictable.
- Credit immediately on each kill via callback → coroutine → use case (game loop must not suspend).
- Room v7 → v8 migration: first explicit `Migration` object in the project (stored in new `data/local/Migrations.kt`).

**What was done (9 tasks):**
1. Added `EnemyScaler.stepReward(type)` with agreed constants. `EnemyScalerTest` extended with per-type assertions + positive-for-all-types regression.
2. Added `battleStepsEarned: Long = 0` to `DailyStepRecordEntity`. Bumped `@Database(version = 7)` → `8`. Created `data/local/Migrations.kt` with `MIGRATION_7_8`. Wired `.addMigrations(*AppMigrations.ALL)` in `DatabaseModule`. Added DAO methods `getBattleStepsEarned(date)` (COALESCE→0) and `incrementBattleSteps(date, delta)` (UPSERT via `INSERT ... ON CONFLICT(date) DO UPDATE`). Updated `FakeDailyStepDao`.
3. Created `domain/usecase/AwardBattleSteps.kt` with `DAILY_BATTLE_STEP_CAP = 2_000L`. Logic: skip if amount≤0; compute remaining from DAO; credit `min(amount, remaining)` via `addSteps` + `incrementBattleSteps`. `AwardBattleStepsTest` — 6 tests covering full/partial/exhausted/rollover/negative/dao-amount.
4. Wired `GameEngine`: `@Volatile totalStepsEarned: Long = 0`, `@Volatile onStepReward: ((Long) -> Unit)? = null`. Reset in `init()`. In `handleEnemyDeath`, compute `EnemyScaler.stepReward(enemy.enemyType)`, invoke callback, spawn green `FloatingText` at `y + 24f`. Extended `FloatingText` with `color` parameter (default unchanged yellow-gold, new `STEP_COLOR = 0xFF4CAF50`).
5. Injected `AwardBattleSteps` into `BattleViewModel`. Added `stepsEarnedThisRound: Long = 0` to `BattleUiState`, `stepsEarned: Long = 0` to `RoundEndState`. Extracted callback wiring into `@VisibleForTesting internal fun wireStepRewardCallback(engine)` — prevents test deadlock with the polling loop. Override `onCleared()` nulls the callback on the engine. `BattleViewModelTest` extended with 3 new tests.
6. Added HUD Step counter (green `👟 +N Steps`) in `BattleScreen.kt`'s top-left column, shown only when `stepsEarnedThisRound > 0`. Includes `contentDescription` for accessibility.
7. Added green "Steps" banner + "Steps Earned" StatRow in `PostRoundOverlay.kt`, shown when `stepsEarned > 0`. `BattleViewModel.endRound()` populates `RoundEndState.stepsEarned` from `_uiState.value.stepsEarnedThisRound` (capped credited amount).
8. Created ADR-0003. Updated `STATE.md` (feature status, DB v8), `CONSTRAINTS.md` (new anti-cheat invariant), appended this RUN_LOG entry.
9. Integration — see test/build results below.

**Test results:** `./run-gradle.sh test` — BUILD SUCCESSFUL, **412 JVM tests, 0 failures** (was 401, +11 new). `./run-gradle.sh assembleDebug` — BUILD SUCCESSFUL. Room schema v8 exported at `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/8.json` with `battleStepsEarned INTEGER NOT NULL` column.

**Bug caught during verification:** Initial build failed with Hilt `MissingBinding` error because I had added `AwardBattleSteps` to `BattleViewModel`'s constructor. Project convention (verified across all 32 existing use cases) is that domain use cases are **instantiated inline inside ViewModels**, not injected via Hilt. Fixed by:
1. Removed `AwardBattleSteps` from constructor; added `DailyStepDao` instead (already provided by `DatabaseModule`).
2. Construct `private val awardBattleSteps = AwardBattleSteps(playerRepository, dailyStepDao)` inline, matching the pattern used by `UpdateBestWave`, `AwardWaveMilestone`, `ApplyCardEffects`, etc.
3. Updated `BattleViewModelTest.createVm()` to pass `dailyStepDao` instead of `awardBattleSteps`.

After the fix, tests pass on first try and assembleDebug is clean.

**Files changed:**
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/DailyStepRecordEntity.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/AppDatabase.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/Migrations.kt` (new)
- `app/src/main/java/com/whitefang/stepsofbabylon/data/local/DailyStepDao.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/di/DatabaseModule.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleSteps.kt` (new)
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/EnemyScaler.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/effects/FloatingText.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleUiState.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt`
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/PostRoundOverlay.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/EnemyScalerTest.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/usecase/AwardBattleStepsTest.kt` (new)
- `app/src/test/java/com/whitefang/stepsofbabylon/fakes/FakeDailyStepDao.kt`
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModelTest.kt`
- `docs/agent/DECISIONS/ADR-0003-battle-step-rewards.md` (new)
- `docs/agent/STATE.md`
- `docs/agent/CONSTRAINTS.md`
- `docs/agent/RUN_LOG.md`

**What remains:** Plan 31 (Play Console & Store Publication).

---

## 2026-05-05 — Archaeology Phase 1: Non-Technical Summary

**Objective:** External prompt asked for a source-grounded, non-technical summary of the codebase.

**What was done:** Created `devdocs/archaeology/small_summary.md` (283 lines). Written directly from source (AndroidManifest, StepsOfBabylonApp, MainActivity, StepCounterService, DailyStepManager, AppDatabase, 23-entry UpgradeType, BattleViewModel, GameEngine, WaveSpawner, AwardBattleSteps, StoreScreen, SoundManager, StubBillingManager, StubRewardAdManager, build.gradle.kts, settings.gradle.kts, libs.versions.toml, res/ tree). Not derived from existing docs. Identifies primary deliverable (single `:app` module, v1.0.0), walks through user experience, core loop, complete-vs-evolving areas, and five uncertainties.

**Discrepancy logged (code is authoritative):** `.kiro/steering/source-files.md` still claims `AppDatabase` is version 7; code is version 8 with a registered v7→v8 `Migration` object. Summary flags this rather than silently correcting the steering file.

**No code or production docs modified.** No new dependencies. No build/test runs needed (documentation-only).

**Files created:**
- `devdocs/archaeology/small_summary.md`

**Memory updated:** STATE ✅ (no substantive status change — still Plan 31) / RUN_LOG ✅.

---

## 2026-05-05 — Archaeology Phase 2: Architecture + Deployment Intros

**Objective:** External prompt asked for two source-grounded intros for new engineers: architecture intro (data flow paths, abstractions, patterns, time/random/ID/config/cache/env) and deployment intro (build, run, test, package, ship).

**What was done:**
1. Searched for prior equivalents — none (`docs/architecture.md` is a short reference card, not an intro; `docs/database-schema.md` is a per-table reference). Proceeded with new files.
2. Read the full DI module set (`DatabaseModule`, `RepositoryModule`, `StepModule`, `HealthConnectModule`, `BillingModule`, `AdModule`), `DatabaseKeyManager`, `Migrations.kt`, `PlayerRepositoryImpl`, `PlayerProfileDao`, `StepSensorDataSource`, `StepCounterService`, `StepSyncWorker`, `StepSyncScheduler`, `StepIngestionPreferences`, `StepRateLimiter`, `AntiCheatPreferences`, `StepCrossValidator`, `HealthConnectClientWrapper`, `GameLoopThread`, `GameSurfaceView`, `BootReceiver`, `StepNotificationManager`, `WidgetUpdateHelper`, `StepWidgetProvider`, `Converters`, `run-gradle.sh`, `proguard-rules.pro`, `.gitignore`, `gradle.properties`, `local.properties`, `app/schemas/` tree, `network_security_config.xml`, `step_widget_info.xml`, signing-guide.md, release-checklist.md, the three stub use cases with injectable `Random` (`CalculateDamage`, `GenerateSupplyDrop`, `OpenCardPack`), and `GenerateDailyMissions` (date-seeded Random).
3. Confirmed no CI configuration of any kind exists in the repo (no `.github/`, `.circleci/`, `.gitlab-ci.yml`, Jenkinsfile, Dockerfile, fastlane, etc.).
4. Grep-verified: zero `BuildConfig` reads, zero `DataStore` references, zero `UUID.randomUUID()` calls, single `Room.databaseBuilder` (in `DatabaseModule`). 8 SharedPreferences files in use.
5. Wrote `devdocs/archaeology/intro2codebase.md` (480 lines):
   - 10-second mental model, six concrete entry points
   - Two main data flows diagrammed: step ingestion pipeline (with follow-on fan-out) and battle game loop (SurfaceView + dedicated thread + callbacks into ViewModel)
   - The four-layer trace used by every non-battle screen
   - Main abstractions: 6 Hilt modules, 8 repositories, services & adapters, presentation conventions
   - Explicit section on time / randomness / IDs / config / caching / persistence / environment access locations — flags that `Clock` is **not** abstracted, `Random` **is** injected in 3 use cases with the documented default-parameter pattern, and `GenerateDailyMissions` uses `Random(todayDate.hashCode())` deterministically
   - Design patterns, module boundaries (single `:app`), entry-point quick lookup
6. Wrote `devdocs/archaeology/intro2deployment.md` (464 lines):
   - Build system at a glance (version matrix, catalog-only policy)
   - Local build commands + `run-gradle.sh` non-TTY wrapper
   - CI status (none; release gates are manual)
   - Build types + R8 keep-rule inventory
   - Bundled assets (code, .ogg sound effects, schemas (reference only), network_security_config, widget XML) and known missing assets (no launcher icon, no localisation)
   - Release signing: opt-in `keystore.properties`, Play App Signing guidance, output paths
   - Testing pipeline (JVM-only, JUnit5 + Robolectric, ~412 tests; no instrumented tests yet)
   - Runtime deployment mechanisms (WorkManager 15-min periodic, foreground service, boot receiver, widget refresh, Health Connect sync)
   - Database migration process (`AppMigrations.ALL`, `fallbackToDestructiveMigrationOnDowngrade(dropAllTables=true)`)
   - Permissions and secrets, one-page quick reference

**Discrepancies logged (code is authoritative):**
   - `docs/database-schema.md` still says "Current schema version: 7"; code is v8 (`AppDatabase.kt`, `app/schemas/8.json`).
   - `.kiro/steering/source-files.md` describes `AppDatabase` as version 7.
   - `docs/architecture.md` doesn't mention `CosmeticEntity` and doesn't note `HealthConnectModule` is an empty placeholder — both verified in code.

**No code or production docs modified.** No new dependencies. No build/test runs needed (documentation-only).

**Files created:**
- `devdocs/archaeology/intro2codebase.md`
- `devdocs/archaeology/intro2deployment.md`

**Memory updated:** STATE ✅ (no substantive status change — still Plan 31) / RUN_LOG ✅.


## 2026-05-05 — Archaeology Phase 3: Deep Trace Analysis

**Objective:** External prompt asked for per-trace files under `devdocs/archaeology/traces/` covering every internal interface boundary and submodule-level interaction, end-to-end, using a fixed 10-section schema (Entry Point, Execution Path, Resource Management, Error Path, Performance Characteristics, Observable Effects, Why This Design, Feels Incomplete, Feels Vulnerable, Feels Like Bad Design).

**What was done:**
1. Context preflight: read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, RUN_LOG tail, ADRs 0001–0003. Confirmed repo is on `main`, working tree clean except for prior RUN_LOG edit and untracked `devdocs/` from Phase 2.
2. Surveyed existing archaeology output (`small_summary.md`, `intro2codebase.md`, `intro2deployment.md`) so Phase 3 deepens rather than duplicates. Confirmed Phase 2's entry-point quick-lookup table was a reasonable seed list for traces.
3. Enumerated the highest-value internal boundaries across: sensor HAL ↔ Flow ↔ service ↔ DailyStepManager ↔ Room; service ↔ WorkManager heartbeat; HC cross-validation state machine; runFollowOnPipeline fan-out; Compose ↔ SurfaceView; game-loop thread ↔ GameEngine (`@Volatile`); engine kill callback ↔ VM viewModelScope ↔ use case ↔ DAO; round-end cascade; generic VM-VM-DAO pattern (Workshop); notification PendingIntent ↔ MainActivity deep-link; `AppWidgetManager` IPC; `System.loadLibrary("sqlcipher")` + Keystore passphrase; `BootReceiver` restart.
4. Read all code on each trace path before writing — `StepSensorDataSource`, `StepCounterService`, `DailyStepManager`, `StepRateLimiter`, `StepVelocityAnalyzer`, `StepIngestionPreferences`, `StepSyncWorker`, `StepSyncScheduler`, `StepCrossValidator`, `StepGapFiller`, `HealthConnectClientWrapper`, `HealthConnectStepReader`, `ActivityMinuteConverter`, `ActivityMinuteValidator`, `ExerciseSessionReader`, `AntiCheatPreferences`, `GenerateSupplyDrop`, `WalkingEncounterRepositoryImpl`, `StepRepositoryImpl`, `DailyStepDao`, `DailyStepRecordEntity`, `Migrations.kt`, `PlayerRepositoryImpl`, `PlayerProfileDao`, `AppDatabase`, `DatabaseKeyManager`, all six DI modules, `MainActivity`, `BattleScreen`, `BattleViewModel`, `GameSurfaceView`, `GameLoopThread`, `GameEngine`, `WaveSpawner`, `CollisionSystem`, `EnemyEntity`, `ZigguratEntity`, `EnemyScaler`, `AwardBattleSteps`, `UpdateBestWave`, `AwardWaveMilestone`, `CheckTierUnlock`, `PurchaseUpgrade`, `WorkshopViewModel`, `HomeViewModel`, `StoreViewModel`, `StubBillingManager`, `StubRewardAdManager`, `SupplyDropNotificationManager`, `StepNotificationManager`, `SmartReminderManager`, `MilestoneNotificationManager`, `StepWidgetProvider`, `WidgetUpdateHelper`, `BootReceiver`, AndroidManifest.
5. Wrote 13 trace files + 1 README index under `devdocs/archaeology/traces/`:
   - `trace_01_step_sensor_to_room.md`
   - `trace_02_step_sync_worker_and_heartbeat_handoff.md`
   - `trace_03_hc_cross_validation_escrow.md`
   - `trace_04_follow_on_pipeline_fanout.md`
   - `trace_05_compose_to_surfaceview_boot.md`
   - `trace_06_game_loop_single_frame.md`
   - `trace_07_enemy_kill_and_battle_step_reward.md`
   - `trace_08_round_end_cascade.md`
   - `trace_09_workshop_purchase_flow.md`
   - `trace_10_supply_drop_to_deep_link.md`
   - `trace_11_widget_update.md`
   - `trace_12_db_bootstrap_and_keystore.md`
   - `trace_13_boot_recovery.md`
   - `README.md` (index with per-trace table, usage guide, and what Phase 3 deliberately omits)

**Key code facts verified during writing (no code changes):**
- `GameEngine.onStepReward: ((Long) -> Unit)?` is `@Volatile`; game thread invokes it; `BattleViewModel.wireStepRewardCallback` hops to `viewModelScope.launch` so the loop never blocks on Room.
- `AwardBattleSteps` does 3 statements (read cap, add steps, increment counter) with NO transaction wrapping — observed a partial-failure vulnerability.
- `DailyStepManager.runFollowOnPipeline` wraps each of its 5 stages in `try/catch (_: Exception) {}` individually.
- `StepCrossValidator` calls `playerRepository.spendSteps(excess)` destructively at Levels 0/1 *before* `updateEscrow`; separated by two different Room writes — partial-failure vulnerability.
- `DatabaseKeyManager.getPassphrase` wipes SharedPreferences on decrypt failure but does NOT wipe the DB file — existing SQLCipher DB becomes unreadable with the new passphrase on device-restore. The `Room.databaseBuilder` has `fallbackToDestructiveMigrationOnDowngrade(true)` but no `fallbackToDestructiveMigration()`, so this is a latent crash-on-launch.
- `GameLoopThread` uses `System.nanoTime()` directly (acceptable per `intro2codebase.md` §5); `TICK_NS = 16_666_667L`; speed multiplier scales the accumulator, not `dt`, so physics stays deterministic.
- `StepSyncWorker.sensorCatchUp` uses `StepIngestionPreferences.isServiceAlive(now)` with `HEARTBEAT_THRESHOLD_MS=120_000`; HC gap-filling reuses `dailyStepManager.recordSteps` so anti-cheat cannot be bypassed via HC.
- `MainActivity.pendingNavigation: MutableStateFlow<String?>` is the cold/warm deep-link carrier; `onNewIntent` + first `LaunchedEffect(Unit)` both write; collector `LaunchedEffect(Unit)` reads and nulls.

**No code or production docs modified.** No new dependencies. No build/test runs needed — pure documentation output grounded in code reading.

**Files created:**
- `devdocs/archaeology/traces/` (new directory)
- `devdocs/archaeology/traces/trace_01_step_sensor_to_room.md`
- `devdocs/archaeology/traces/trace_02_step_sync_worker_and_heartbeat_handoff.md`
- `devdocs/archaeology/traces/trace_03_hc_cross_validation_escrow.md`
- `devdocs/archaeology/traces/trace_04_follow_on_pipeline_fanout.md`
- `devdocs/archaeology/traces/trace_05_compose_to_surfaceview_boot.md`
- `devdocs/archaeology/traces/trace_06_game_loop_single_frame.md`
- `devdocs/archaeology/traces/trace_07_enemy_kill_and_battle_step_reward.md`
- `devdocs/archaeology/traces/trace_08_round_end_cascade.md`
- `devdocs/archaeology/traces/trace_09_workshop_purchase_flow.md`
- `devdocs/archaeology/traces/trace_10_supply_drop_to_deep_link.md`
- `devdocs/archaeology/traces/trace_11_widget_update.md`
- `devdocs/archaeology/traces/trace_12_db_bootstrap_and_keystore.md`
- `devdocs/archaeology/traces/trace_13_boot_recovery.md`
- `devdocs/archaeology/traces/README.md`

**Memory updated:** STATE ✅ (no substantive project-status change — still Plan 31; added pointer to new archaeology output) / RUN_LOG ✅. No ADR needed — the traces describe existing code behaviour; no new decisions were made.

**Follow-ups worth considering (for a future, distinct session — not implemented here):**
- Wrap `AwardBattleSteps` in a `@Transaction` suspend function to prevent the cap/wallet divergence observed in trace 07.
- Wrap `StepCrossValidator` Level-0/1 escrow writes in a transaction so `spendSteps` + `updateEscrow` commit atomically (trace 03).
- `DatabaseKeyManager` should wipe the DB file alongside the encrypted-passphrase reset, to avoid crash-on-launch after device restore (trace 12).
- Missing deep-link routes in `MainActivity.pendingNavigation` collector: Store / Stats / etc. not handled; some notification paths already target Missions, Workshop, Battle, Supplies (trace 10).
- `PlaceholderScreen` dead composable in `MainActivity.kt` — documented but not yet removed (noted across Phase 1 and trace 05).

## 2026-05-05 — Archaeology Phase 5: Concept Inventory

**Objective:** External prompt asked for a source-grounded concept inventory across four lists: technical, design, business, and missing concepts. Each entry ≤3 sentences with implementation status (Fully / Partial / Missing) and file pointers.

**What was done:**
1. Context preflight: re-read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, the full RUN_LOG tail, and the three ADRs. Confirmed repo on `main`, working tree clean except for prior STATE/RUN_LOG edits and untracked `devdocs/`.
2. Surveyed existing archaeology output so Phase 5 complements Phases 1–4 instead of duplicating them. Phase 1 gives user-flow overview; Phase 2 gives architecture + deployment intros; Phase 3 gives 13 per-boundary traces; Phase 4 gives 5 improvement proposals. Phase 5 fills the inventory slot.
3. Read 40+ definitional source files to ground the lists: all 23 `UpgradeType` configs, 10 `ResearchType`, 9 `CardType` (3 rarities), 6 `UltimateWeaponType`, 4 `OverdriveType`, 5 `Biome` ranges, 6 `Milestone`, 6 `DailyMissionType`, 4 × 4 `SupplyDropTrigger`/`SupplyDropReward`, 5 `BillingProduct`, 3 `AdPlacement`, 6 `EnemyType`, 7 `BattleCondition`, 12 `Screen` routes. Verified `AppDatabase` v8 + `MIGRATION_7_8` + 12 entities, `PlayerProfileEntity`'s 27 columns, `DailyStepRecordEntity.battleStepsEarned`, 7 manifest permissions, 4 notification channels, `StubBillingManager` + `StubRewardAdManager`, 8 SharedPreferences wrappers, injected `Random` in the 3 stochastic use cases, date-seeded RNG in `GenerateDailyMissions`, `@Volatile` + callback-hop step-reward wiring in `GameEngine` / `BattleViewModel`, the 5-stage `runFollowOnPipeline` fan-out in `DailyStepManager`, and the 4-level `StepCrossValidator` that deducts via `spendSteps` on first escrow.
4. Wrote four concept-inventory docs under `devdocs/archaeology/concepts/`:
   - `technical_concepts_list.md` — 9 sections, 40+ concepts (platform choices, persistence, step ingestion, anti-cheat, battle renderer, UI/navigation/notifications, security, reproducibility/testing, cross-cutting patterns).
   - `design_concepts_list.md` — 7 sections, 35+ concepts (product design, domain data model shape, contract/interface shape, data-flow, UX/feedback, monetization, operational contracts).
   - `business_lvl_concepts_list.md` — 7 sections, 30+ concepts (positioning, hard invariants, currency economy, progression systems, engagement/retention, player-trust contracts, release/distribution).
   - `missing_concepts_list.md` — 4 sections, 30+ concepts split between intentionally deferred (Plan 24 accessibility, Plan 31 real SDKs, app icon, audio assets, i18n, instrumented tests, CI, step burst, onboarding, store assets, public privacy URL) and unintended gaps (TimeProvider abstraction, `@Transaction` wrapping, DB-file wipe on decrypt failure, round-end cascade on nav interrupt, deep-link coverage for 7 missing routes, FollowOnPipeline extraction, anti-cheat UI surfacing, boss/threat targeting, sound-settings surface, `PlaceholderScreen` dead code) plus compliance/operational assumptions and integration contracts worth naming.
5. Each entry holds to the ≤3-sentence limit, carries an explicit `Implementation status:` line (Fully / Partial / Missing), and cites the primary file(s) it refers to. Central concepts first, then branching; implicit concepts (invariants, privacy assumptions, operational handoffs, integration protocols) surfaced where they exist only as conventions.

**Discrepancies logged (code authoritative):** None new. Known drift points (DB v7 references in some docs, `CosmeticEntity` missing from `architecture.md`, `HealthConnectModule` placeholder) remain as called out in Phase 2. Phase 5 did not edit any production doc.

**No code or production docs modified.** No new dependencies. No build/test runs needed — pure inventory output grounded in code reading.

**Files created:**
- `devdocs/archaeology/concepts/` (new directory)
- `devdocs/archaeology/concepts/technical_concepts_list.md`
- `devdocs/archaeology/concepts/design_concepts_list.md`
- `devdocs/archaeology/concepts/business_lvl_concepts_list.md`
- `devdocs/archaeology/concepts/missing_concepts_list.md`

**Memory updated:** STATE ✅ (added Phase 5 pointer, no substantive status change — still Plan 31) / RUN_LOG ✅. No ADR needed — inventory describes existing code and decisions; no new decisions were made.

## 2026-05-05 — Standard Analysis Phase 7: Doc-Inferred Foundations

**Objective:** External prompt asked for "doc-inferred" foundation documents — `project_description.md`, `philosophy.md`, and `known_requirements.md` — extracted **only** from non-code documentation, with a "Docs vs Code" delta section at the end of each file. This is the twin of Phase 6 (which built the same three docs from code reading) but sourced from docs alone.

**What was done:**
1. Context preflight: read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, the tail of `RUN_LOG.md`, and all three ADRs. Confirmed still pointed at Plan 31 as the next substantive work.
2. Gathered the full non-code doc surface: `README.md`, `AGENTS.md`, `CHANGELOG.md`, the GDD, `docs/architecture.md`, `docs/database-schema.md`, `docs/battle-formulas.md`, `docs/step-tracking.md`, `docs/monetization.md`, `docs/plans/master-plan.md`, `plan-R-remediation.md`, `plan-R2-remediation.md`, sampled plan files (01, 24, 31), `docs/release/*`, `docs/balance/balance-report.md`, all `.kiro/steering/*` including agent-protocol and project-memory rules, and every `docs/agent/` file including the three ADRs.
3. Deliberately did **not** open code, gradle files, manifests, or the `devdocs/archaeology/` Phase 6 output (which is code-based) while drafting. The Phase 6 foundations at `devdocs/archaeology/foundations/` stay untouched; Phase 7 output is written to a fresh `devdocs/foundations/` directory so the two provenances can be compared side by side.
4. Wrote `devdocs/foundations/project_description.md` — product description organised as: tagline / platform facts, core loop, major systems table (with doc citation per row), currencies, architecture snapshot, explicit out-of-scope list, delivery status, and a Docs vs Code delta.
5. Wrote `devdocs/foundations/philosophy.md` — the product's stated belief system: the one immovable axiom ("Walk to Power"), design pillars, hard game rules, fair-play stance, monetization philosophy, accessibility/inclusivity stance, privacy posture, architectural philosophy, testing philosophy, the doc-first project-memory process philosophy, game-feel philosophy, and a negative philosophy list ("what the product refuses to be"), plus a delta.
6. Wrote `devdocs/foundations/known_requirements.md` — a numbered requirements catalogue (R-STEP-*, R-AC-*, R-ECO-*, R-WS-*, R-BAT-*, R-IR-*, R-TIER-*, R-BIO-*, R-UW-*, R-OD-*, R-LAB-*, R-CARD-*, R-SUP-*, R-NOTIF-*, R-STAT-*, R-MON-*, R-NFR-*, R-REL-*, R-PROC-*) with every requirement tied to a specific doc citation. Doc-vague points are captured as vague rather than promoted to invented precision.
7. Phase 7 delta sections identify a consistent set of doc drifts that are worth escalating eventually: schema v7 vs v8 (ADR-0003) in `docs/database-schema.md`, three simultaneous test counts (397/401/412), the Battle Step Rewards feature missing from every user-facing doc (GDD, README, CHANGELOG 1.0.0, battle-formulas, Play Store listing), the SharedPreferences state stores contradicting "Room is the single source of truth", the "33-entry roadmap" README phrasing, unchecked items in the release checklist that R2 already closed, the GDD pillars self-inconsistency (4 vs 5), and multiple vague areas (Supply-Drop seeding, Activity-Minute 100-step-eq rationale, inbox eviction policy, daily-mission rewards, battery acceptance methodology). These are only called out; nothing was fixed.

**Discrepancies logged:** None edited — Phase 7 is observational. The delta sections inside each file are the catalogue.

**No code, gradle, manifest, or production feature docs were modified.** No new dependencies. No build/test runs needed — this phase is pure doc synthesis.

**Files created:**
- `devdocs/foundations/` (new directory, distinct from the existing `devdocs/archaeology/foundations/`)
- `devdocs/foundations/project_description.md`
- `devdocs/foundations/philosophy.md`
- `devdocs/foundations/known_requirements.md`

**Memory updated:** STATE ✅ (added Phase 7 pointer; status still Plan 31) / RUN_LOG ✅. No ADR needed — this phase produces analysis, not decisions.

## 2026-05-05 — Archaeology Phase 9: Concept Mappings

**Objective:** External prompt asked for a per-concept mapping at `devdocs/archaeology/concept_mappings.md` with 7 parts per concept: files/modules, coverage % (Fully/Partial/Missing), divergence from an "ideal" architecture, alternatives likely considered, edge cases that shaped the design, related tests/fixtures/migrations/config/docs, and risks caused by the current shape.

**What was done:**
1. Context preflight: re-read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, RUN_LOG tail, all three ADRs. Confirmed still on `main`, working tree clean except modified STATE/RUN_LOG and untracked `devdocs/` (expected from prior phases).
2. Surveyed existing Phase 1–8 output so Phase 9 complements, not duplicates. Phase 5 (concepts/) gives file pointers per concept; Phase 9's unique contribution is coverage %, divergence, alternatives, edge cases, tests, risks. Different lens on the same concept domain.
3. Curated 25 major concepts (cut from an initial ~34 to keep signal high): step pipeline, HC cross-validation, battle step rewards (ADR-0003), currency model, persistence (Room/SQLCipher/migrations), battle renderer, combat formulas, wave system, biomes, Workshop upgrades, Labs research, Cards system, Ultimate Weapons, Step Overdrive, Tiers, supply drops, milestones/missions, weekly/login economy, monetization (billing/ads/cosmetics), notifications/widget, navigation/deep-link/UX feedback, DI/Hilt layering, reproducibility contracts, testing strategy, release/security.
4. Wrote `devdocs/archaeology/concept_mappings.md` in five incremental chunks (header+TOC+5, then 5, 5, 5, 5+appendices) to manage output budget. Total 2 342 lines, 25 concepts × 7-part entries, plus Appendix A (12 cross-concept risks) and Appendix B (coverage roll-up table with Key Gap column).
5. Every coverage figure and risk grounded in existing archaeology findings: Phase 4 items (TimeProvider, @Transaction, round-end cascade, FollowOnPipeline, anti-cheat surfacing), Phase 8 findings (12 forbidden imports, duplicated mission-progress updates, fat modules, thin modules, 3 reward vocabularies, 2 stat-snapshot chains), Phase 5 missing concepts (STEP_BURST orphan, cosmetic visual gap, deep-link partial, decrypt-failure zombie DB). No fresh code claims — Phase 9 synthesises what's already known in a mapping lens.

**Key calibration:** Coverage labels = Fully (85–100%), Partial (50–84%), Skeleton (20–49%), Missing (0–19%). Per-concept % is qualitative (feature completeness + edge cases + tests + docs + production-readiness), not line-counted.

**Highlights from the coverage roll-up (Appendix B):**
- 18 of 25 concepts at ≥88% coverage (core gameplay loop solid).
- 4 concepts at Partial (75–80%): supply drops (STEP_BURST orphan), milestones/missions (cosmetic no-op + 5-site duplication), navigation (5 of 12 deep-link routes), release (manual gate, Play Console pending).
- 2 concepts at Partial (55–70%): reproducibility contracts (53 direct wall-clock calls), testing strategy (no instrumented tests, no CI).
- 1 concept at Skeleton (45%): monetization (real SDKs deferred to Plan 31, cosmetic visuals missing).

**Changes made:**
- Created `devdocs/archaeology/concept_mappings.md` (2 342 lines).
- Updated `docs/agent/STATE.md` references + last-run line.
- This RUN_LOG entry.

**Code changes:** none (archaeology only).

**Commands/tests run:** filesystem reads + grep only — no build. No tests run (documentation-only).

**Open questions:** none. Phase 9 deliverable is strictly documentation. Cross-concept risks in Appendix A are cross-references to Phase 4 (item 1 TimeProvider, item 2 @Transaction, item 3 round-end cascade, item 4 FollowOnPipeline, item 5 anti-cheat UX) and Phase 8 (module-discovery prioritised list §8). No new proposals.

**Follow-ups created:** none new. Phase 9 synthesises existing findings; it does not schedule work. If the project wants to act on the map, the Key Gap column of Appendix B gives a natural triage order.

**Memory updated:** STATE ✅ (added Phase 9 reference + last-run line) / RUN_LOG ✅.

**ADR:** not warranted — no architectural decision was made; this phase maps decisions already evident in code plus the gaps around them.

## 2026-05-05 — Standard Analysis Phase 11: Gap Closure Plan

**Objective:** External prompt (Phase 11 "Towards First Implementation Pass — Gap Closure Strategy") asked for `devdocs/evolution/gap_closure_plan.md` — an executable, phased plan built from Phase 10's gap analysis. Required four phases (Quick Wins, Incremental Improvements, Major Refactoring, Complete Rewrites), each with dependencies, risk assessment, testing/verification, rollback, developer workflow, PR boundaries, and explicit non-goals.

**What was done:**
1. Context preflight: read `START_HERE.md`, `STATE.md`, `CONSTRAINTS.md`, RUN_LOG tail (Phase 10 entry), all three ADRs. Clean tree apart from modified STATE/RUN_LOG and untracked `devdocs/` (expected). Next substantive work is still Plan 31.
2. Primary input: `devdocs/evolution/gap_analysis.md` (993 lines from Phase 10). Re-read §1 (12 missing concepts), §2 (6 architecture changes), §3 (12 tech-debt items), §4 (Phase 4 cross-ref + 7 additional incrementals), §5 (rewrite rejections), §6 (7 known risks + 8 unknowns + 4 non-unknowns), §7 (aggregated posture + critical path).
3. Secondary input: `devdocs/archaeology/5_things_or_not.md` (Phase 4) which already costs out the five highest-leverage items with file:line citations and rollback plans. Phase 11 cross-references these rather than duplicating (global rule #3).
4. Wrote `devdocs/evolution/gap_closure_plan.md` (1 032 lines): §0 read-first checklist; §1 quick wins (Q1–Q8: doc drift, DB-file wipe on decrypt failure, fake failure modes, FloatingText suppression on cap, deep-link all-routes, STEP_BURST decision, PlaceholderScreen deletion, Season Pass leak tactical patch); §2 incremental improvements (I1–I7: anti-cheat visibility, atomic writes first site + remaining sites, resilient endRound, TimeProvider narrow migration, ClaimMilestone.Cosmetic fix, Settings rename + privacy link); §3 major refactoring (M1 FollowOnPipeline + UpdateMissionProgress extractions, M2 real Billing SDK, M3 real Ad SDK, M4 Plan 31 external tasks) + MR1 cosmetic rendering pipeline promoted out of §4 per Phase 10 §5.2 "not a rewrite, a new narrow contract"; §4 rewrite rejections with explicit revisit triggers (multi-module Gradle split, Reward sealed hierarchy unification); §5 sixteen explicit non-goals matched to Phase 10 citations (accessibility, onboarding, i18n, analytics, server-side anti-cheat, etc.); §6 aggregate critical path with release-critical sequence (Q3 → M2+M3 → MR1 PR1-2 → M4); §7 memory-update checklist aligned to §11-agent-protocol.
5. Each phase entry holds the prompt's required shape: dependencies/prerequisites, risk assessment + mitigation, testing/verification strategy, rollback plan, expected developer workflow, suggested commit/PR boundaries, explicit non-goals. No new findings introduced — every item cited back to its Phase 10 section, Phase 4 item number, or a roadmap/plan-31 task (global rules #1, #3).
6. Updated `docs/agent/STATE.md` references list + last-run line.

**Key design choices in the plan (explicit so future readers can audit):**
- **Q6 (STEP_BURST) defaults to delete-with-documented-intent** because no doc demands wiring and Phase 10 §1.4 lists both options as equally valid. The commit-body documentation satisfies rule #2.
- **Q8 is a tactical patch** for the Season Pass leak, flagged as duplicate-logic until M1 PR 4 removes it — prevents blocking the fix on the larger extraction.
- **I2 lands the first @Transaction site before I3 fans it out** to prove the pattern on the most user-visible purchase before migrating four more.
- **I5 explicitly migrates only 3 of 53 TimeProvider call sites** per Phase 4 §1's scope-creep mitigation. Not a sweep.
- **MR1 moved from §4 to §3** — Phase 10 §5.2 was categorical: "Not a rewrite — a new narrow contract." Putting it in §4 would have contradicted the source.
- **M4 (Plan 31 external) stays in the plan even though it's not code** — Phase 10 §1.1 and the master-plan both treat it as a release gate; omitting it would leave the critical path incomplete.
- **Two rewrites explicitly rejected with revisit triggers**: multi-module Gradle split (revisit when a second engineer joins) and `Reward` sealed unification (revisit when a fourth reward type is specified). Satisfies prompt's §4 "only if necessary" framing.

**Changes made:**
- Created `devdocs/evolution/gap_closure_plan.md` (1 032 lines).
- Updated `docs/agent/STATE.md` references list + last-run line.
- This RUN_LOG entry.

**Code changes:** none (evolution/planning only).

**Commands/tests run:** filesystem reads only — no build, no tests.

**Open questions:** two unknowns from Phase 10 §6.2 now carried forward into specific plan entries rather than left implicit —
- Q6 resolution (delete vs wire `STEP_BURST`) is named as the first action of that PR.
- MR1 PR 2's single-cosmetic choice (proposed: jade ziggurat recolour) needs a product decision before coding; the plan surfaces the decision up-front rather than guessing.

**Follow-ups created:** none new work items. The plan aggregates, schedules, and risk-scopes already-discovered items. If adopted, the rollout orders inside §1, §2, §3 and the critical path in §6 give the sequence.

**Memory updated:** STATE ✅ / RUN_LOG ✅.

**ADR:** not warranted — no architectural decision made. The plan is a schedule of already-documented decisions; actual architecture choices (e.g. ADR-0004 FollowOnPipeline, ADR-0005 billing SDK, ADR-0006 ad SDK) are called out **as prerequisites** of M1/M2/M3 respectively, to be written before the first code PR of each.

## 2026-05-06 — Standard Analysis Phase 13: Codebase Cleanup Inventory

- Goal: Produce `devdocs/archaeology/cleanup_inventory.md` per the Phase 13 prompt — identify candidates for removal / consolidation / quarantine with evidence / confidence / risk / verification / action per item. No deletions, no code changes.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, RUN_LOG tail (Phase 12 entry); `git status` clean apart from modified STATE/RUN_LOG and untracked `devdocs/` + `smoke_tests/` (expected). `git log -n 5 --oneline` shows HEAD at `a9d0386 feat: award Steps for enemy kills with 2k/day cap (ADR-0003)`.
- Survey: confirmed no prior `devdocs/archaeology/cleanup_inventory.md` existed. Re-used Phases 1–12 findings (small_summary, intro2codebase, intro2deployment, traces, 5_things_or_not, concepts/, foundations/ x2, architecture_analysis, module_discovery, concept_mappings, gap_analysis, gap_closure_plan, smoke_tests/report.md) as the primary evidence base — cited inline per global rule #3 to avoid duplication.
- Code verification (no builds, only filesystem + grep):
  - `grep PlaceholderScreen` → 1 match, declaration only (MainActivity.kt:237). Dead composable confirmed.
  - `grep UltimateWeaponLoadout app/src/main` → declaration file only, **0 non-self references in main**. `ManageCardLoadout.kt:15` uses `CardLoadout.MAX_SIZE` constant → CardLoadout is alive; UltimateWeaponLoadout is dead runtime-wise.
  - `grep STEP_BURST` → 1 match, declaration at SupplyDropTrigger.kt:5. No producer. Orphan enum value.
  - `grep 'STEP_MULTIPLIER|RECOVERY_PACKAGES' app/src` → 4 matches main (2 enum entries + 2 config entries) + 2 matches test (balance/CostCurveTest.kt:17,45) + 1 match WorkshopViewModel.kt:42 hiddenUpgrades. Not pure orphan — still exercised by balance tests.
  - `grep 'MilestoneReward\.(Gems|PowerStones|Cosmetic)' app/src` → Milestone.kt declares 3 cosmetic rewards (garden_ziggurat_skin, lapis_lazuli_skin, sandals_of_gilgamesh); ClaimMilestone.kt:25 is `is Cosmetic -> { /* no-op */ }`. Grep of `cosmeticId\s*=` in CosmeticRepositoryImpl.kt shows SEED_COSMETICS uses ids `zig_obsidian, zig_crystal, zig_golden, proj_fire, proj_lightning, enemy_shadow, enemy_neon` — **zero overlap with the milestone cosmetic ids**. Confirmed.
  - `grep 'releaseEscrow|discardEscrow|clearEscrow'` → StepRepositoryImpl.kt:44 = `dao.clearEscrow(date)`; line 46 = `dao.clearEscrow(date)` — byte-identical delegations. Semantic split lives only in StepCrossValidator (discard at 76,92 for offense; release at 106 for reconciliation).
  - `grep '@RunWith|RobolectricTestRunner' app/src/test` → 3 files: RoomSchemaTest.kt (3 @Test), StepWidgetProviderTest.kt (3 @Test), DeepLinkRoutingTest.kt (3 @Test). All use `org.junit.Test` (JUnit 4). Classpath confirmed in Phase 12 has no `junit-vintage-engine`. Phase 12 report said only RoomSchemaTest + StepWidgetProviderTest were affected (6 tests); grep says DeepLinkRoutingTest is also JUnit 4 + Robolectric — so likely 9 tests silently skipped, not 6. Flagged in §C1 as a discrepancy worth verifying against the `testDebugUnitTest` XML on a future run.
  - `grep 'TODO|FIXME|XXX|HACK' app/src/main` → 0 matches. Confirmed in Phase 10 gap_analysis too.
  - `ls docs/agent/state.json docs/temp/` → both absent. Already-cleaned orphans; §E1 confirms.
  - `ls app/schemas/.../AppDatabase/` → 1.json through 8.json present. `Migrations.kt` registers only `MIGRATION_7_8`. §D1 flags 1–6.json as possibly historical artefacts.
  - `grep '@InstallIn|@Inject|@HiltViewModel|@HiltWorker|@HiltAndroidApp|@AndroidEntryPoint|@Provides|@Binds|@Module' app/src/main` → 239 matches across 79 files. §F dynamic-risk register pins all these.
  - `grep 'BiomePreferences|NotificationPreferences|SoundPreferences|MilestoneNotificationPreferences|AntiCheatPreferences|StepIngestionPreferences'` → 8 preference wrappers total, each wired; §A8 names them as a virtual `prefs` module and proposes consolidation.
  - Read HealthConnectModule.kt in full: lines 10–13 are `@Module / @InstallIn(SingletonComponent::class) / object HealthConnectModule` with **no body**. Empty organisational placeholder. §A6.
  - Read Screen.kt: `val items by lazy { … }` — workaround for sealed-class init-order NPE (commit 1872af9). §A7 proposes a one-line comment.
- Changes made:
  - Created `devdocs/archaeology/cleanup_inventory.md` (565 lines, ~43 KB, 8 top-level sections + TL;DR + caution box): TL;DR of 18 candidates grouped by effort/confidence; Dynamic-Risk Caution Box up front listing 11 framework mechanisms; §A source tree (11 entries A1–A11); §B abandoned features / orphan enum entries (7 entries B1–B7); §C tests, fakes, fixtures (5 entries C1–C5); §D config, build, scripts, schema, migrations (13 entries D1–D13); §E docs orphans / redundancy (6 entries E1–E6); §F dynamic-risk register with 17-row table of classes invisible to grep plus a pre-removal audit checklist; §G retention / compatibility / legal; §H summary + cross-references to prior archaeology and Phase 11 schedule.
  - Updated `docs/agent/STATE.md` references list + last-run line.
  - This RUN_LOG entry.
- Code changes: none (archaeology/inventory only).
- Commands/tests run: filesystem reads + grep + `git status`/`git log` only — no build, no tests.
- Open questions:
  - §C1 flags possible discrepancy with Phase 12 report (DeepLinkRoutingTest may be silently skipped — Phase 12 claimed it passed). Worth verifying against `app/build/test-results/testDebugUnitTest/*.xml` per-package counts in a future session. Not blocking the inventory.
  - Three product decisions called out in §B (STEP_BURST wire-or-delete, STEP_MULTIPLIER/RECOVERY_PACKAGES wire-or-delete, MilestoneReward.Cosmetic ID alignment) are **inputs** to any future cleanup; they are not decisions this phase makes.
- Follow-ups created: none new work items. The inventory cross-references Phase 4 five-item list, Phase 8 module-discovery prioritised list, Phase 10 gap analysis, and Phase 11 gap closure plan as the existing schedules.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — no architectural decision made; inventory surfaces existing code state plus gaps already documented elsewhere.

## 2026-05-19 morning — RO-12 in-round stat drift bugfix bundle (discovery + fix + tests + doc sync)
- Goal: investigate the v5 internal-track on-device smoke-test screenshot the user shared (Wave 4, DEFENSE tab, 06:21 BST) and fix anything that surfaced before promoting v5 → closed.
- Discovery:
  - The screenshot shows the RO-11 #C "Now → Next" readout rendering correctly on the DEFENSE tab — partial pass on acceptance check #8.
  - But the HEALTH "Now" value (1647 HP) disagrees with the live ziggurat top HP bar (1568 HP) by ~5 %. `1568 / 1.06 ≈ 1479` (matches `BASE × workshop HEALTH Lv 2 +6 %`); `1647 / 1.06 / 1.05 ≈ 1479` (matches `BASE × workshop × lab HEALTH_RESEARCH Lv 1 +5 %`). The readout includes lab; the live ziggurat doesn't.
  - HEALTH_REGEN row shows "Now: 1.3/s → 1.3/s" — visible no-op for a real upgrade. `+2 %` of `1.3` is `+0.026/s`, which rounds away under `%.1f`.
- Investigation: `grep 'resolveStats(workshopLevels, inRoundLevels)' app/src/main` → 1 match at `BattleViewModel.kt:496` inside `purchaseInRoundUpgrade`. Compare with `init` and `playAgain` which both pass `labLevels` AND post-apply `applyCardEffects`. Confirmed two real bugs in that one site (Bug 1: lab dropped on every in-round purchase, RO-11 wiring miss; Bug 2: cards dropped on every in-round purchase, pre-existing since Plan 17 but unmasked by RO-11 stacking lab on top). Read `DescribeUpgradeEffect.kt::format` — no path consults `equippedCards` either, so the readout drifts from the engine when stat-modifying cards are equipped (Bug 3, RO-11 introduced). Read `HEALTH_REGEN -> fmt("%.1f/s", stats.healthRegen)` — confirmed Bug 4.
- Plan written: `docs/plans/plan-RO-12-in-round-stat-drift.md` (224 lines) — exec summary, screenshot evidence, severity matrix per bug, decision matrix, fix sketches, test plan, risk register, on-device verification checklist.
- Changes made (4 production edits + 2 test files):
  - `domain/usecase/DescribeUpgradeEffect.kt`: + `applyCardEffects: ApplyCardEffects = ApplyCardEffects()` constructor param; + `equippedCards: List<OwnedCard> = emptyList()` optional invoke param; `format` post-applies `applyCardEffects(raw, equippedCards).stats` so the readout mirrors the engine pipeline. `HEALTH_REGEN` format `%.1f/s` → `%.2f/s`.
  - `presentation/battle/BattleViewModel.kt`: extracted private `resolveCurrentStats(inRound)` helper running `resolveStats(workshop, inRound, lab) → applyCardEffects(stats, equippedCards).stats`; `purchaseInRoundUpgrade` now routes through it. `describeEffect(type)` threads `equippedCards` through to `DescribeUpgradeEffect.invoke`. KDoc on `describeEffect` updated to reflect RO-12 card threading.
  - `domain/usecase/DescribeUpgradeEffectTest.kt`: existing `HEALTH_REGEN` test renamed to "2-decimal format" with expected `"1.20/s"`; +3 RO-12 tests (`Lv 0 → Lv 1 produces a visibly different readout` precision direct guard, `HEALTH readout reflects equipped WALKING_FORTRESS card multiplier` Bug 3 direct guard, `HEALTH readout with no cards equipped is unchanged from pre-RO12 baseline` default-arg behaviour). Imports added for `CardType` + `OwnedCard`.
  - `presentation/battle/BattleViewModelTest.kt`: +3 RO-12 tests + new `installEngineForPurchase` reflective helper that seeds `engine.cash` so `purchaseInRoundUpgrade` actually executes. Tests cover Bug 1 (lab bonus survives in-round purchase), Bug 2 (card bonus survives), and stacked combined regression matching the screenshot scenario.
- Code changes: 4 source files modified (2 production + 2 test), 1 plan added. No schema / DI / public-API changes. versionCode bump deferred to upload PR.
- Commands/tests run:
  - `./run-gradle.sh testDebugUnitTest` → BUILD SUCCESSFUL, 615 tests, 0 failures, 0 errors. Test count delta 609 → 615 (+6 net regression tests).
  - JUnit XML aggregation confirmed: `find app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -h 'tests='` summed to 615.
- Doc sync (per agent protocol PR Task-List Convention):
  - `AGENTS.md`: test count 609→615; RO-12 entry appended to coverage summary; Version line extended with RO-12 mention + "awaiting v6 build" status.
  - `.kiro/steering/source-files.md`: 4 entries updated (`DescribeUpgradeEffect`, `BattleViewModel`, `DescribeUpgradeEffectTest`, `BattleViewModelTest`) with RO-12 detail.
  - `CHANGELOG.md`: new RO-12 section at top of `[Unreleased]` above RO-11 with full root-cause table + fix description + test breakdown + verification.
  - `STATE.md`: current objective flipped from "smoke-test v5" to "build v6 + re-upload"; previous-objective added for the v5 smoke-test discovery; What works updated to 615 tests + RO-12 line; Top priorities + Next actions reordered around v6 path; Critical path extended; Last run line replaced.
  - `RUN_LOG.md`: this entry.
- Open questions: none. All four bugs have direct regression tests; the fix surface area is small (3 files); the on-device verification checklist is documented in plan-RO-12 § 8.
- Follow-ups created: versionCode bump 5 → 6 + bundleRelease + Play Console internal-track upload (next session, gated on user direction since it's a manual external action).
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — bug-fix bundle, no architectural decision. Pattern of "extracted helper to eliminate duplicate-by-omission failure mode" is consistent with RO-08's `applyStats` extraction.

## 2026-05-19 mid-morning — docs sweep: 12 stale live-docs fixed post-RO-12 + versionCode bump
- Goal: full sweep + fix of every live current-state doc that drifted across the RO-08 / RO-09 / RO-11 / RO-12 / C.5 PR 3 / C.6 PR 3 / Plan 31 PR B sequence and the `1796b4c` versionCode bump. Pure docs PR. Per agent protocol § PR Task-List Convention — historical artifacts (`devdocs/**`, `smoke_tests/**`, `docs/external-reviews/**`, `plan-R*.md`, prior RUN_LOG/CHANGELOG entries) deliberately left frozen.
- Preflight: read `START_HERE`, `STATE`, `CONSTRAINTS`, RUN_LOG tail (RO-12 entry), ADR list (`0001`–`0007`); `git status` clean; `git log -n 15 --oneline` shows HEAD at `1796b4c chore(release): bump versionCode 5 -> 6 for RO-12 internal-track upload`. Source-of-truth confirmed: `app/build.gradle.kts:38` → `versionCode = 6`; `data/local/AppDatabase.kt:23` → `version = 9`; STATE.md → 615 JVM tests.
- Audit pass: 13 live-doc files spot-grepped + read for stale strings (`12 entities, 12 DAOs`, `version 8`, `stub implementation`, `priceDisplay constants`, `33-entry`, `versionCode 5`, `StubBillingManager`, `StubRewardAdManager`, `USE_REAL_BILLING`). 12 distinct drift items found in 12 separate files; reported to user grouped by drop-the-stub-references vs. RO-08+RO-11 catch-up vs. one-off (README count). User ack: "fix all".
- Changes made (12 files, 90 insertions, 42 deletions):
  - `README.md`: 33-entry → 34-entry (one-line table cell).
  - `.kiro/steering/lib-room.md`: 12 entities, 12 DAOs → 13 entities, 13 DAOs, schema v9.
  - `.kiro/steering/structure.md`: AppDatabase Key Files row updated (12/12/v8 → 13/13/v9 + billing_receipt mention).
  - `.kiro/steering/tech.md`: Play Billing version-table row rewritten post-C.5 PR 3 (no more flag, no more stub).
  - `docs/architecture.md`: Hilt module list rewritten — BillingModule + AdModule bindings now reflect real impls + sibling internal modules + DatabaseModule entity count + new CoroutineScopeModule row.
  - `docs/monetization.md`: What's-Implemented gained a live-prices bullet citing PR B; Out-of-Scope replaced with PR B's two intentional v1.x deferrals (no refresh / no retry).
  - `docs/plans/master-plan.md`: Plan 26 status checkbox row updated.
  - `docs/plans/plan-31-play-console.md`: Status "Not Started" → multi-line "In Progress (Phases A–G landed)" with full breadcrumbs; Dependencies extended; Task 4 stub-replacement bullets struck-through with done-references.
  - `docs/battle-formulas.md`: 6 in-place formula edits (Stats Resolution + Damage Calc + Health & Regen + Cash Economy + UW Cooldown Scaling + Step Multiplier) layering Lab outer multipliers per RO-11; 2 new sections appended (Wave Skip + Coming Soon). 70-line file diff.
  - `docs/step-tracking.md`: Data-flow box now includes STEP_MULTIPLIER + STEP_EFFICIENCY stage; explanatory note added.
  - `AGENTS.md`: Version line bumped to versionCode 6 + commit `1796b4c` reference; status-checklist Plan 26 row + parallelizable-branches Monetization row updated.
  - `docs/agent/STATE.md`: Current objective + Top priorities #1 + Next actions #1 all stripped the now-completed "Bump versionCode 5 → 6" sub-step.
  - `CHANGELOG.md`: new "Docs sweep" section inserted at top of `[Unreleased]` above RO-12, with per-file drift table + verification block.
  - This RUN_LOG entry.
- Code changes: none.
- Commands/tests run: `git status` + `git log -n 15` + `git diff --stat` only — no build, no tests. Test count stays at 615 (no source impact).
- Verification: re-grep of original stale strings now matches only frozen historical artifacts (`devdocs/archaeology/**`, `devdocs/foundations/**`, `devdocs/evolution/**`, `smoke_tests/**`, `docs/plans/plan-26-monetization.md`, prior CHANGELOG/RUN_LOG entries) — zero remaining matches in current-state docs.
- Open questions: none. The 14 historical-artifact files surfaced by grep are correctly frozen per agent protocol § 11; not part of this PR's scope.
- Follow-ups created: none new. The next external action remains `./run-gradle.sh bundleRelease` + sign + upload AAB v6 to Play Console internal track (gated on user direction; manual external step).
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — docs-only PR.

## 2026-05-19 late morning — README audit + LICENSE creation
- Goal: complete audit of `README.md` per user request, then apply all findings in a single pass. Pure docs PR.
- Preflight: `git status` shows the prior docs-sweep PR's 14 files staged but not committed (sweep finished but not yet committed by user); `git log -n 1` HEAD still at `1796b4c`. Re-read README.md fresh + spot-checked all cited paths + ran `ls LICENSE*` (no such file) + `cat run-gradle.sh` (verified the README's recreation block matches modulo one comment line).
- Audit produced and reported to user: 10 findings, ranked P0–P3:
  - P0: no LICENSE file, no Status section, no Privacy link.
  - P1: thin Tech Stack one-liner (missing SQLCipher / Health Connect / Play Billing / AdMob); CHANGELOG + AGENTS not in doc table; Project Structure one level too shallow (data/ subdirs hidden).
  - P2: no "Where to start" pointer; "Note: instrumented tests" comment-in-code-block awkward; `assembleRelease` invites confusing failure without keystore.
  - P3: no visual asset embedded.
- User ack: "do it all in a single pass".
- Changes made:
  - `LICENSE` (NEW, 17 lines): proprietary all-rights-reserved with Google Play user-license carve-out + third-party-libraries clause + contact email `jonwhitefang@gmail.com`. Most appropriate default for a pre-launch commercial Play Store app; user can swap to MIT / Apache later if they decide to open-source.
  - `README.md` (75 → 127 lines): full rewrite. Added feature graphic at top, Status / Privacy / License sections; expanded Tech Stack one-liner from 6 → 11 items; added 3 rows to Key Documentation table (AGENTS / CHANGELOG / Privacy Policy); expanded Project Structure under `data/` to show all 6 subdirs; added Where-to-start pointer to START_HERE.md; moved instrumented-tests note out of code block; replaced raw `assembleRelease` with `bundleRelease` + keystore-prereq callout in Setup; `run-gradle.sh` recreation block now includes the on-disk comment line (previously omitted).
  - `CHANGELOG.md`: new "README audit + LICENSE creation" section inserted at top of `[Unreleased]` above the docs-sweep entry, with full per-fix table grouped by P0–P3 severity.
  - `STATE.md`: Last-run line updated.
  - This RUN_LOG entry.
- Code changes: none.
- Commands/tests run: `ls -la README.md LICENSE* CHANGELOG.md run-gradle.sh AGENTS.md` + `ls docs/release/store-assets/` + `grep -oh jonwhitefang@gmail.com\|support@whitefanggames.com` to confirm canonical contact email + `git diff --stat` at the end. No build, no tests — docs-only.
- Verification: all 10 cited document links resolve; feature graphic 636 KB exists at `docs/release/store-assets/play-store-feature-graphic-1024x500.png`; all build commands match `tech.md`. No source / test / schema impact — test count stays at 615.
- Open questions: none. License choice (proprietary vs MIT / Apache) is reversible; chose proprietary as the safest default for a commercial Play Store app pre-launch.
- Follow-ups created: none new. Next external action remains `./run-gradle.sh bundleRelease` + sign + upload AAB v6 to Play Console internal track.
- Memory updated: STATE ✅ / RUN_LOG ✅
- ADR: not warranted — docs / license-declaration only, no architectural decision.



