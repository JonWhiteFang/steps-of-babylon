# ADR-0017: ENEMY_INTEL Research Design

**Status:** Accepted (combat foundation shipped PR #84; UI overlays shipped PR #86; on-device verified 2026-05-29 — overlays legible/uncluttered, +2 %/lvl coefficient confirmed; all open balance items resolved)
**Date:** 2026-05-29
**Supersedes:** The `isComingSoon = true` placeholder for `ResearchType.ENEMY_INTEL` introduced in RO-11 #B.2.
**Superseded by:** None

## Context

`ResearchType.ENEMY_INTEL` was one of two Lab research enums flagged `isComingSoon = true` in RO-11 #B.2 (the other being `AUTO_UPGRADE_AI`). Both were dead like the other ten enums pre-RO-11, but rather than ship a real implementation without a design, RO-11 gated them in the Labs UI with a "Coming Soon" badge and a disabled Start-Research button.

The user decision on 2026-05-26 (recorded in `plan-V1X-roadmap.md`) approved designing v1.x content for ENEMY_INTEL while keeping AUTO_UPGRADE_AI deferred. Theme: "tactical awareness research — the better you understand your enemies, the harder you hit and the more you see."

## Decision

**ENEMY_INTEL is a 10-level Lab research project that grants two stacked benefits:**

1. **Combat (every level): +2 % damage per level.** Outer multiplier on `ResolvedStats.damage`, mirroring the DAMAGE_RESEARCH pattern from RO-11 #A.1 but at a smaller per-level coefficient (DAMAGE_RESEARCH = 5 %/lvl; ENEMY_INTEL = 2 %/lvl). Stacks multiplicatively with DAMAGE_RESEARCH and is independent of the crit-multiplier path.

2. **Information (level milestones): UI overlays unlock at L1 / L5 / L10.**
   - **L1+:** during the 9-second wave-cooldown phase, the wave-announcement banner shows the next wave's enemy composition.
   - **L5+:** each spawned enemy renders an HP-percentage label above its existing HP bar (pure visual; no combat-math change).
   - **L10:** the HUD shows a boss-arrival countdown when the next wave will contain a boss.

**Cost + time curve (matches the CASH_RESEARCH pattern):** baseCostSteps 8,000; costScaling 1.5×; baseTimeHours 4; timeScaling 1.10×; maxLevel 10. L10 cumulative cost ≈ 460,000 Steps, ≈ 64 h real time — comparable to CASH_RESEARCH end-state.

### Shipping split

The design is split into two PRs because the two benefits have different verifiability:

- **Combat foundation (this PR — fully JVM-verifiable):** the enum flip + the `ResolveStats` +2 %/lvl damage multiplier + the Labs-visibility flip (ENEMY_INTEL no longer "Coming Soon"; only AUTO_UPGRADE_AI remains). Covered by pure-JVM unit tests.
- **UI overlays (follow-up PR — needs on-device verification):** the L1 next-wave composition string, the L5 per-enemy HP-% label, and the L10 boss-arrival countdown, plus the `WaveSpawner.getWaveComposition(wave)` / `wavesUntilNextBoss()` helpers and the `enemyIntelLevel` plumbing through `BattleViewModel` → `GameEngine` → `WaveAnnouncement` / `EnemyEntity` / HUD. These are SurfaceView rendering changes; like every visual change in this project they require human on-device eyes (e.g. HP-% labels must not clutter the screen at 30+ enemies on high-tier Tank waves).

The combat benefit is live and meaningful on its own the moment this PR lands: a player researching ENEMY_INTEL gets the +2 %/level damage immediately, surfaced through the existing DAMAGE readout's `labLevels` term. The information overlays are additive polish layered on next.

## Rationale

1. **2 %/level damage coefficient (vs DAMAGE_RESEARCH's 5 %).** ENEMY_INTEL also grants information value, so the raw-damage component is deliberately smaller to keep it from dominating DAMAGE_RESEARCH as a pure-damage pick. First-pass guess — flagged as an open balance item below.
2. **Multiplicative stacking.** Consistent with how every other lab-research damage modifier composes in `ResolveStats` (workshop × in-round × lab tiers). No special-casing.
3. **Cost curve matched to CASH_RESEARCH.** A mid-tier research that is neither the cheapest nor the most expensive; the 1.5× scaling matches the other "premium" research (MULTISHOT/BOUNCE) so the catalogue stays internally consistent.
4. **No standalone `DescribeUpgradeEffect` readout.** `DescribeUpgradeEffect` is keyed on `UpgradeType` (the in-round Cash upgrade menu), not `ResearchType`. Research types — including DAMAGE_RESEARCH — have never had a standalone readout there; their effect surfaces through the matching stat upgrade's readout via the `labLevels` argument. ENEMY_INTEL follows that existing convention. (This is a deliberate deviation from the plan's "add a DescribeUpgradeEffect ENEMY_INTEL branch" item, which was written before that keying was re-confirmed.)

## Consequences

### Positive

- ENEMY_INTEL is no longer dead content — it appears in Labs with a real description, real balance values, and a real combat effect the first time a tester researches it.
- Only `AUTO_UPGRADE_AI` remains `isComingSoon`; the `ResearchTypeTest` set-equality contract is tightened to `{AUTO_UPGRADE_AI}`, catching any future regression in either direction.
- Combat-foundation test count 800 → 806 (+6: 5 `ResolveStatsTest` + 1 `ResearchTypeTest` balance-values guard; the contract test was renamed, not added).

### Resolved balance items (on-device verified 2026-05-29)

1. **The 2 %/level coefficient is confirmed.** On-device verification kept ENEMY_INTEL at +2 %/lvl (user decision 2026-05-29). The information value compensates for the smaller raw-damage coefficient vs DAMAGE_RESEARCH; no change to `ResolveStats`. May still be revisited if closed-test feedback indicates otherwise.
2. **Boss-arrival countdown (L10)** verified clean at end-game — not too cluttered.
3. **HP-% label rendering (L5)** verified legible at 30+ enemies — does not interfere with the existing HP bar.

### Neutral

- Research progress on the still-deferred `AUTO_UPGRADE_AI` remains preserved per the RO-11 #B.2 contract.
- The UI-overlay helpers (`getWaveComposition`, `wavesUntilNextBoss`) will be pure functions on `WaveSpawner`, easily JVM-testable when that PR lands.

## Implementation (combat foundation, PR #84)

Files changed:

- `domain/model/ResearchType.kt` — ENEMY_INTEL constructor flipped from the placeholder (3000/6.0h/maxLevel 3/`isComingSoon = true`) to the locked balance (8000/1.5× cost, 4.0h/1.10× time, maxLevel 10, +2 %/lvl, new tactical-awareness description). Class + field KDoc updated to reflect one remaining deferred enum.
- `domain/usecase/ResolveStats.kt` — `damage` gains a `× (1 + lab(ENEMY_INTEL) × 0.02)` factor stacking with the DAMAGE_RESEARCH factor; KDoc updated.
- `presentation/labs/LabsScreen.kt` + `presentation/labs/LabsViewModel.kt` — comments updated (only AUTO_UPGRADE_AI deferred now). No logic change: both read `isComingSoon`, so ENEMY_INTEL auto-surfaces once the flag flips.

Tests:

- `domain/model/ResearchTypeTest.kt` — set-equality contract `{AUTO_UPGRADE_AI, ENEMY_INTEL}` → `{AUTO_UPGRADE_AI}`; new `ENEMY_INTEL has full balance values populated` guard against a partial revert.
- `domain/usecase/ResolveStatsTest.kt` — 5 new tests: L0 no-op, L1 → 1.02×, L10 → 1.20×, stacks with DAMAGE_RESEARCH (1.10 × 1.25 = 1.375×), preserves CRITICAL_RESEARCH alongside.

## Implementation (UI overlays, follow-up PR)

The three information overlays from the Decision shipped in a second PR (the combat half was meaningful on its own). All three are gated on a new `@Volatile var GameEngine.enemyIntelLevel`, set by `BattleViewModel` from the round-start `labLevels` snapshot via an extracted `applyResearchParams(engine)` helper (deduped across `startPollingEngine` + `playAgain`) and **not** reset in `GameEngine.init` — the VM owns the value, mirroring the existing `cashResearchMultiplier` pattern.

- **L1 next-wave composition.** `WaveSpawner.getWaveComposition(wave)` is a new pure helper returning deterministic *expected* per-type counts (the `pickType` probability bands × `enemiesPerWave`, with one BOSS split off index 0 on boss waves). `GameEngine.nextWaveCompositionLabel()` (null below L1) formats it as `Next: 12 BASIC, 4 RANGED, 1 BOSS` and feeds it into a new optional `WaveCooldownText(nextWaveComposition)` param, drawn below the cooldown timer.
- **L5 per-enemy HP %.** Drawn in `GameEngine.render()` looping the live `EnemyEntity` list when `enemyIntelLevel >= 5` — deliberately **not** in `EnemyEntity.render` (see Rationale 5 below), so the level gate never touches the entity constructor or the SCATTER child-spawn path.
- **L10 boss countdown.** `WaveSpawner.wavesUntilNextBoss()` (pure) + `GameEngine.bossCountdownLabel()` (null below L10) render `Boss in N waves` / `Boss next wave`, right-aligned in `render()` so it clears the centre-aligned cooldown banner.

+6 JVM tests (3 `WaveSpawnerTest` — deterministic early-wave composition, boss-wave includes one BOSS, `wavesUntilNextBoss` forward count; 2 `GameEngineTest` — `nextWaveCompositionLabel` null-below-L1 / populated-at-L1, `bossCountdownLabel` null-below-L10 / populated-at-L10; 1 `BattleViewModelTest` — `applyResearchParams` pushes the ENEMY_INTEL level onto the engine). `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL, 806 → 812. The overlays still need human **on-device verification** (HP-% labels at 30+ enemies; boss-countdown clutter at end-game) — these are visual-legibility checks no JVM test can make.

### Rationale 5 — L5 HP-% labels rendered in `GameEngine`, not `EnemyEntity`

The plan listed the L5 label in `EnemyEntity.render`. Drawing it in `GameEngine.render` instead (looping the live entity list) keeps the `enemyIntelLevel` gate out of the per-enemy constructor — `EnemyEntity` is built in two places (`WaveSpawner.spawnEnemy` + the SCATTER child-spawn in `GameEngine.handleEnemyDeath`), so threading the level through both constructors would be churn for no benefit. The engine already owns the level and already loops entities in `render`; the label is a single `drawText` per living enemy at the same gate. Functionally identical to the plan; lower blast radius.

## References

- GitHub issue #44 — ENEMY_INTEL design debt
- `docs/plans/plan-V1X-roadmap.md` — V1X-15b sub-plan (locked design + user decision 2026-05-26)
- RO-11 #A.1 / #B.2 — the lab-research multiplier pattern + the `isComingSoon` gate this ADR builds on
- ADR-0015 — sibling V1X balance ADR (STEP_MULTIPLIER curve)
