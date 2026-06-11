# Plan RO-11 — Labs research wiring + in-round upgrade visibility

| Field | Value |
|---|---|
| **Status** | Proposed |
| **Severity** | 🔴 Critical (closed-test blocker) for Phase A; 🟡 Moderate for Phases B + C |
| **Date opened** | 2026-05-18 |
| **Author** | Discovered while implementing RO-10 (in-round upgrade-effect readout) |
| **Supersedes** | RO-10 (in-round upgrade visibility, paused) — folded in as Phase C |
| **Predecessors** | RO-08 (4-fix upgrade-wiring bundle), RO-09 (3-fix pre-closed-test bundle) |
| **Target window** | Pre-closed-test (before promoting v4 internal → closed track) |

---

## 1. Executive summary

While building the in-round upgrade-effect readout requested by the user (originally scoped as RO-10), I discovered that **all 10 `ResearchType` enums in the Labs system are dead** — declared with effect descriptions, costing Steps + real-time + Gems (rush) to complete, displayed on the Labs screen, but never consumed by the running game.

This is the same shape as RO-08 #1 (`STEP_MULTIPLIER` + `RECOVERY_PACKAGES` dead enums) and RO-09 #1 (`CHRONO_FIELD` rendering-overlay-only) — UI / cost / timer / persistence all wired, **gameplay disconnected**. Just much wider in blast radius: **an entire game system instead of a single upgrade or weapon**.

Closed-test exposure is high. A Labs-curious tester at level 3+ DAMAGE_RESEARCH will compare tower DPS to a control round and immediately report "research does nothing". Same for HEALTH, CASH, CRITICAL, REGEN, STEP_EFFICIENCY, UW_COOLDOWN — any one of them surfaces the gap.

**Recommendation: fix Phase A (7 simple multipliers) before promoting v4 → closed. Wire Phase B's WAVE_SKIP. Defer AUTO_UPGRADE_AI to v1.x with a UI gate. Repurpose ENEMY_INTEL. Land Phase C (the original RO-10 readout) on top of the now-correct stat resolution.**

---

## 2. Discovery + evidence

### Code search

```text
$ grep -r "DAMAGE_RESEARCH|HEALTH_RESEARCH|CASH_RESEARCH|CRITICAL_RESEARCH|REGEN_RESEARCH|UW_COOLDOWN|STEP_EFFICIENCY|WAVE_SKIP|AUTO_UPGRADE_AI|ENEMY_INTEL" app/src/main/

domain/model/ResearchType.kt           # declared (10 entries)
data/repository/LabRepositoryImpl.kt   # CRUD only
domain/repository/LabRepository.kt     # interface
domain/usecase/StartResearch.kt        # cost / max-level gate only
domain/usecase/CompleteResearch.kt     # timer-elapsed gate only
domain/usecase/RushResearch.kt         # Gem-rush cost only
presentation/labs/LabsViewModel.kt     # screen rendering only
```

`LabRepository.observeAllResearch(): Flow<Map<ResearchType, Int>>` exists but is consumed by exactly **zero** combat-path / step-credit / cash-economy / UW-cooldown call sites. `BattleViewModel` does not inject `LabRepository`. Neither does `GameEngine`, `ResolveStats`, `ApplyCardEffects`, `DailyStepManager`, `WaveSpawner`, `EnemyEntity`, or any other gameplay class.

### Severity table (per `ResearchType`)

| Enum | Claimed effect | Current actual effect | Closed-test exposure | Fix complexity | Recommended phase |
|---|---|---|---|---|---|
| `DAMAGE_RESEARCH` | "+5% base damage / level" (max L20 = +100%) | None | **High** — visible in 1 round | Trivial — outer multiplier on `ResolvedStats.damage` | A |
| `HEALTH_RESEARCH` | "+5% max health / level" (max L20 = +100%) | None | **High** — visible in 1 round | Trivial — outer multiplier on `ResolvedStats.maxHealth` | A |
| `CASH_RESEARCH` | "+5% cash earned / level" (max L20 = +100%) | None | **High** — visible across multiple rounds | Trivial — `* (1 + level × 0.05)` on `killCash` | A |
| `CRITICAL_RESEARCH` | "+3% crit damage / level" (max L15 = +45%) | None | **Medium** — needs crit roll to surface | Trivial — outer multiplier on `ResolvedStats.critMultiplier` | A |
| `REGEN_RESEARCH` | "+4% health regen / level" (max L15 = +60%) | None | **Low** — regen is subtle | Trivial — outer multiplier on `ResolvedStats.healthRegen` | A |
| `STEP_EFFICIENCY` | "+2% bonus steps from walking" (max L10 = +20%) | None | **High** — easy to A/B if tester walks two days | Low — extend `DailyStepManager.applyStepMultiplier` to read both | A |
| `UW_COOLDOWN` | "-3% UW cooldown / level" (max L15 = -45%) | None | **Medium** — needs unlocked UW | Low — wrap `cooldownAtLevel(uw.level)` with research multiplier | A |
| `WAVE_SKIP` | "Start rounds at wave X instead of wave 1" (max L10) | None — `WaveSpawner.currentWave = 1` always | **High** — visible the moment a tester starts a round | Moderate — `WaveSpawner` constructor needs `startWave: Int`, `BattleViewModel.init` reads research level | B |
| `AUTO_UPGRADE_AI` | "Auto-spends cash on optimal upgrades" (max L5) | None — no auto-purchase logic exists | **Medium** — only obvious to active testers | High — needs new "optimal" definition + UI toggle + auto-purchase coroutine | B (defer to v1.x + UI gate) |
| `ENEMY_INTEL` | "Show enemy HP bars and incoming wave preview" (max L3) | HP bars unconditionally on; no wave preview UI | **Low** — HP bars work, preview missing | Mixed — HP bars need conditional gate; wave preview needs new UI | B (repurpose / defer wave preview) |

### Why this slipped through earlier audits

- RO-08 audited Workshop / Cards / Overdrive / UWs end-to-end but **explicitly excluded the Labs system** (see `docs/agent/RUN_LOG.md` 2026-05-18 RO-09 audit-findings entry: the audit checklist covered `CardType`, `OverdriveType`, `UltimateWeaponType`, `UpgradeType`, `DailyMissionType` — `ResearchType` was not listed).
- RO-09 was a follow-up self-audit of the gaps RO-08 had surfaced + one Flow-combiner pass; it did not extend the dead-enum sweep to Labs.
- The Labs screen displays the level number correctly because `LabRepositoryImpl.observeAllResearch` works at the persistence layer; nothing on the Labs screen exercises the missing combat-path consumers, so the gap is invisible from the UI.

---

## 3. Decision matrix

| Decision | Rationale |
|---|---|
| **Fix all 7 simple multipliers in Phase A** | Each is one-line outer multiplier wrapped around the existing `ResolvedStats` field or single-site engine read. Closed-test exposure is high and the fix is small. Test cost is ~1 unit test per type. |
| **Wire WAVE_SKIP in Phase B** | The change is well-bounded: `WaveSpawner` constructor gains `startWave: Int = 1`; `BattleViewModel.init` reads the research level and passes it. Enemy scaling at `wave = startWave` is automatic via `EnemyScaler`. Cap of L10 is conservative — the player still has to survive the higher wave, so it's quality-of-life not bypass. |
| **Defer AUTO_UPGRADE_AI to v1.x but gate the UI** | The feature requires a real "optimal upgrade" definition (likely `QuickInvest` reused), an in-round toggle, and a per-tick auto-purchase coroutine. ~2 days of work and design questions (which category? respect player-current-tab? confirmation?). Update enum description to "Coming in v1.x — research progress preserved". Lab-screen badge says "Coming Soon" so testers don't expect it. |
| **Repurpose ENEMY_INTEL** | HP bars are already on; the "wave preview" UI doesn't exist. Two options: (a) make HP bars conditional on L1 (regression for current players who see them) — bad; (b) repurpose: L1 = HP bar **percentages** as numeric text under the bar (currently bar-only); L2 = next-wave enemy mix preview overlay during cooldown phase; L3 = boss telegraph banner enabled. Lower complexity than auto-AI but still real UI work. **Recommendation: defer all three levels to v1.x**, update description to "Reserved for v1.x — research progress preserved", gate Labs UI to mark it "Coming Soon". |
| **Phase C absorbs original RO-10 (in-round visibility)** | The readout becomes meaningful only once the underlying stat resolution is correct (otherwise it would render placeholder values for Labs contributions). Bundling them into one PR keeps the change cohesive. |

### Net scope

| Phase | What lands | Test count delta (estimated) |
|---|---|---|
| **A** — Simple multipliers (7 types) | DAMAGE / HEALTH / CASH / CRITICAL / REGEN / STEP_EFFICIENCY / UW_COOLDOWN wired end-to-end | +12 (7 ResolveStatsTest, 1 GameEngineTest cash, 1 GameEngineTest UW cooldown, 2 DailyStepManagerTest, 1 BattleViewModelTest hydration) |
| **B** — WAVE_SKIP wired; AUTO_UPGRADE_AI + ENEMY_INTEL deferred with UI gate | WaveSpawner accepts `startWave`; BattleViewModel reads research level; LabsViewModel marks the deferred two as "Coming Soon" | +3 (WaveSpawnerTest, BattleViewModelTest start-wave, LabsViewModelTest "Coming Soon" badge) |
| **C** — In-round upgrade visibility (`DescribeUpgradeEffect` + UI) | Use case + UI integration; readout includes Lab + Workshop + in-round contributions | +24 (one DescribeUpgradeEffectTest entry per visible upgrade type) |
| **Total** | 572 → ~611 (+39) | |

---

## 4. Per-research-type fix specs (Phase A)

### 4.1 DAMAGE_RESEARCH

- **Claimed:** +5% base damage per level, max L20 → max +100%.
- **Current:** none.
- **Fix:** in `ResolveStats.invoke` apply outer multiplier:
  ```kotlin
  damage = ZigguratBaseStats.BASE_DAMAGE *
      (1 + ws(UpgradeType.DAMAGE) * 0.02) *
      (1 + ir(UpgradeType.DAMAGE) * 0.02) *
      (1 + lab(ResearchType.DAMAGE_RESEARCH) * 0.05)
  ```
  Add `labLevels: Map<ResearchType, Int> = emptyMap()` parameter. Default empty preserves all existing call sites that don't yet pass it.
- **Test:** `ResolveStatsTest`: `damage scales by +5% per DAMAGE_RESEARCH level (L0/L10/L20)`.

### 4.2 HEALTH_RESEARCH

- **Claimed:** +5% max health per level, max L20 → max +100%.
- **Fix:** outer multiplier on `maxHealth` in `ResolveStats`. Same shape as DAMAGE_RESEARCH.
- **Test:** `ResolveStatsTest`: `maxHealth scales by +5% per HEALTH_RESEARCH level`.

### 4.3 CASH_RESEARCH

- **Claimed:** +5% cash earned per level, max L20 → max +100%.
- **Current:** none. `GameEngine.handleEnemyDeath` reads `wsLevel(UpgradeType.CASH_BONUS)` only.
- **Fix:** new `@Volatile var cashResearchMultiplier: Double = 1.0` on `GameEngine`, set from `BattleViewModel.init` via new public `engine.setCashResearchMultiplier(mult)`. Apply in `handleEnemyDeath`:
  ```kotlin
  val killCash = (baseCash * tierMult * cashBonus * fortuneMultiplier *
      (1.0 + cashBonusPercent / 100.0) * cashResearchMultiplier).toLong()
  ```
  Same multiplier applies to `handleWaveComplete`'s `waveCash`.
- **Test:** `GameEngineTest`: `cashResearchMultiplier scales kill cash and wave-end cash`.

### 4.4 CRITICAL_RESEARCH

- **Claimed:** +3% critical damage multiplier per level, max L15 → max +45%.
- **Current:** none. `ResolveStats.critMultiplier = 2.0 + total(CRITICAL_FACTOR) * 0.1`.
- **Fix:** outer multiplier:
  ```kotlin
  critMultiplier = (2.0 + total(UpgradeType.CRITICAL_FACTOR) * 0.1) *
      (1 + lab(ResearchType.CRITICAL_RESEARCH) * 0.03)
  ```
- **Test:** `ResolveStatsTest`: `critMultiplier scales by +3% per CRITICAL_RESEARCH level`.

### 4.5 REGEN_RESEARCH

- **Claimed:** +4% health regen multiplier per level, max L15 → max +60%.
- **Fix:** outer multiplier on `healthRegen` in `ResolveStats`. Same shape as DAMAGE_RESEARCH.
- **Test:** `ResolveStatsTest`: `healthRegen scales by +4% per REGEN_RESEARCH level`.

### 4.6 STEP_EFFICIENCY

- **Claimed:** +2% bonus steps from walking per level, max L10 → max +20%.
- **Current:** none. RO-08 added STEP_MULTIPLIER (Workshop), but research has its own slot.
- **Fix:** in `DailyStepManager.applyStepMultiplier`, read both `WorkshopRepository` and `LabRepository` and combine:
  ```kotlin
  private suspend fun applyStepMultiplier(baseCredit: Long): Long {
      val wsLevel = workshopRepository.observeAllUpgrades().first()[STEP_MULTIPLIER] ?: 0
      val labLevel = labRepository.observeAllResearch().first()[STEP_EFFICIENCY] ?: 0
      val totalMultiplier = (wsLevel * 0.01 + labLevel * 0.02)
          .coerceAtMost(STEP_MULTIPLIER_CAP)  // existing 1.0 cap stays
      return (baseCredit * (1.0 + totalMultiplier)).toLong()
  }
  ```
  Constructor gains `LabRepository`. Hilt resolves; tests need `FakeLabRepository`.
- **Tests:** `DailyStepManagerTest`: `STEP_EFFICIENCY level 5 produces +10% credited steps (no STEP_MULTIPLIER)`; `STEP_MULTIPLIER L10 + STEP_EFFICIENCY L5 add (+10% + +10% = +20%)`; `combined cap at +100% still holds`.
- **Cross-validator implication:** Same edge case as RO-09 deferred finding #3 — the cross-validator now compares `record.creditedSteps` (which includes BOTH bonuses) raw against `hcSteps`. Closed-test cohort is clean-history so exposure is still ~zero. Document this in the deferred-findings list.

### 4.7 UW_COOLDOWN

- **Claimed:** -3% Ultimate Weapon cooldown per level, max L15 → max -45%.
- **Current:** `UltimateWeaponType.cooldownAtLevel(level: Int): Float = baseCooldownSeconds * (1f - 0.05f * (level - 1))` — accounts for UW level only, not research.
- **Fix:** new `@Volatile var uwCooldownMultiplier: Float = 1f` on `GameEngine`, set from `BattleViewModel.init`. Apply at the two cooldown-set sites:
  ```kotlin
  // GameEngine.activateUW
  uw.cooldownRemaining = uw.type.cooldownAtLevel(uw.level) * uwCooldownMultiplier
  // GameEngine.resetUWCooldowns: no change (resets to 0)
  ```
  And update `UWSlotInfo.cooldownTotal` calculation in `BattleViewModel.startPollingEngine` to use the same multiplier so the UI ring-fill is correct.
- **Test:** `GameEngineTest`: `UW_COOLDOWN L10 reduces cooldown to 70% of base`.

---

## 5. Per-research-type fix specs (Phase B)

### 5.1 WAVE_SKIP — wire

- **Claimed:** Start rounds at wave X (X = research level + 1, capped at L10 → start at wave 11). Or: X = research level (start at wave 1+L). The description is ambiguous; recommend reading max-L10 as "start at wave 11".
- **Decision:** Read as "start at wave 1 + research_level" so L0 → wave 1 (current behaviour), L10 → wave 11. Floor of 1 preserved. Enemy scaling via `EnemyScaler` is automatic — the player just spawns into harder waves.
- **Fix:**
  - `WaveSpawner` constructor gains `private val startWave: Int = 1`.
  - `WaveSpawner.currentWave: Int = startWave` (replaces hardcoded `= 1`).
  - `GameEngine.init` accepts a `startWave: Int` arg or reads it via a new `engine.setStartWave(wave)` setter, plumbed to `WaveSpawner` constructor.
  - `BattleViewModel.init` reads `labRepository.observeAllResearch().first()[WAVE_SKIP] ?: 0`, passes `1 + level` to engine.
- **Reset on round end:** the `engine.init(...)` call at round start is the only point that needs the new value; subsequent waves increment from there.
- **Cap interaction:** L10 is already the enum cap; tier-progression unlock (best-wave milestones) still triggers normally when the player reaches a new milestone wave. No need to special-case "best wave" against the start-wave skip — the player still **has to survive** the higher wave, so the milestone is earned legitimately.
- **Test:** `WaveSpawnerTest` (new file): `currentWave starts at startWave constructor arg`. `BattleViewModelTest`: `init reads WAVE_SKIP and passes startWave to engine`.

### 5.2 AUTO_UPGRADE_AI — defer to v1.x

- **Decision:** defer.
- **Why:** the feature requires (a) a real "optimal upgrade" definition (probably reuse `QuickInvest` use case but adapted for cash instead of Steps), (b) a per-round UI toggle ("Auto-AI: ON / OFF"), (c) a per-tick auto-purchase coroutine, (d) a per-level max-purchase-rate (L1 = 1 purchase / 30 s, L5 = 1 / 5 s? — needs design). ~2 days of work and design ambiguity is out of scope for the closed-test window.
- **What lands now:**
  - Update enum description: `"Reserved for v1.x — research progress preserved"`.
  - Labs screen badge: render a "Coming Soon" overlay on the AUTO_UPGRADE_AI row so testers don't try to research it.
  - Optionally `enabled = false` on the Start Research button for this row only.
- **Test:** `LabsViewModelTest`: `AUTO_UPGRADE_AI row marked coming soon`.

### 5.3 ENEMY_INTEL — defer / repurpose to v1.x

- **Decision:** defer.
- **Why:** HP bars are already on unconditionally (no `hpBar` / `HPBar` gate found in `EnemyEntity`); turning them off for L0 is a regression; the wave-preview UI doesn't exist (no preview composable, no boss-telegraph state). Both subfeatures need real UI work.
- **What lands now:** same treatment as AUTO_UPGRADE_AI — description updated, Labs row gated "Coming Soon".
- **Test:** `LabsViewModelTest`: `ENEMY_INTEL row marked coming soon`.

---

## 6. Phase C — In-round upgrade visibility (RO-10 absorbed)

### 6.1 Original ask

User report: *"It is hard to see the upgrades and how they affect the tower. Can we have this visible, such as damage in round shown by the upgrade, and the same with all of them?"*

### 6.2 Design

New use case `domain/usecase/DescribeUpgradeEffect.kt` (pure Kotlin, no Android imports):

```kotlin
data class UpgradeEffectReadout(val current: String, val next: String)

class DescribeUpgradeEffect(private val resolveStats: ResolveStats = ResolveStats()) {
    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int>,
        labLevels: Map<ResearchType, Int>,
        type: UpgradeType,
    ): UpgradeEffectReadout
}
```

Internally:
- For stat-bearing upgrades: call `resolveStats(workshopLevels, inRoundLevels, labLevels)` twice — once with current `inRoundLevels`, once with `inRoundLevels[type]++`. Map relevant `ResolvedStats` field → readout string.
- For cash utilities (CASH_BONUS, CASH_PER_WAVE, INTEREST, FREE_UPGRADES): compute from `UpgradeConfig.effectPerLevel` directly (these don't pass through `ResolveStats`).
- For RECOVERY_PACKAGES + STEP_MULTIPLIER: hidden from in-round menu but include for Workshop reuse later.

### 6.3 Per-upgrade readout format

| Category | Upgrade | Format example |
|---|---|---|
| Multiplicative | DAMAGE | `12.5 dmg → 13.0 dmg` |
| | ATTACK_SPEED | `1.32/s → 1.34/s` |
| | RANGE | `420 px → 428 px` |
| | HEALTH | `1080 HP → 1112 HP` |
| | HEALTH_REGEN | `2.4/s → 2.5/s` |
| | KNOCKBACK | `12.0 → 12.2 px` |
| Additive caps | CRITICAL_CHANCE | `12% → 12.5%` (cap 80%) |
| | CRITICAL_FACTOR | `×2.5 → ×2.6` |
| | DEFENSE_PERCENT | `15% → 15.3%` (cap 75%) |
| | DEFENSE_ABSOLUTE | `+8 blocked → +9 blocked` |
| | THORN_DAMAGE | `5% → 6% reflect` |
| | LIFESTEAL | `1.0% → 1.2%` (cap 15%) |
| | DAMAGE_PER_METER | `+5%/m → +6%/m` |
| | DEATH_DEFY | `8% → 9%` (cap 50%) |
| Discrete | MULTISHOT | `1 target → 2 targets` (only when crossing 20-level threshold) |
| | BOUNCE_SHOT | `0 bounces → 1 bounce` (only when crossing 15-level threshold) |
| | ORBS | `2 orbs → 3 orbs` |
| Cash utility | CASH_BONUS | `+9% cash → +12% cash` |
| | CASH_PER_WAVE | `+15 cash/wave → +20 cash/wave` |
| | INTEREST | `2.0% → 2.5%` (cap 10%) |
| | FREE_UPGRADES | `5% free → 6% free` (cap 25%) |

### 6.4 UI integration

In `InRoundUpgradeMenu`, add a third line per row below the existing description:

```
DAMAGE                                                   [$50]
Lv 3 · +2% base damage per level
Now: 12.5 dmg → Next: 13.0 dmg                          ← new
```

Color: current value white 70% opacity; arrow + next value Gold (#D4A843) for visual reinforcement of the upgrade.

Add new parameter `describeEffect: (UpgradeType) -> UpgradeEffectReadout` to `InRoundUpgradeMenu` (composables shouldn't directly inject use cases). `BattleScreen` provides it from `viewModel`.

`BattleViewModel` exposes:
- `describeEffect(type) = describeUpgradeEffect(workshopLevels, inRoundLevels, labLevels, type)`
- `labLevels: Map<ResearchType, Int>` field, populated alongside `workshopLevels` in `init` and `playAgain`.

### 6.5 Test plan

`DescribeUpgradeEffectTest` — one entry per visible upgrade type (24 total: 21 in-round + 3 hidden-but-tested-for-reuse). Each test verifies:
- L0 baseline current readout matches expected zero-state string.
- L0 → L1 next readout matches expected post-purchase string.
- Cap behaviour for capped upgrades (CRITICAL_CHANCE at 80%, etc.).
- Lab contribution included for stat-bearing upgrades when `labLevels` non-empty.

---

## 7. Implementation plan (commit-by-commit)

Single PR titled `feat(labs): RO-11 — wire all 10 research types + in-round upgrade visibility`.

| # | Commit message | Files | Tests |
|---|---|---|---|
| 1 | `feat(stats): wire DAMAGE/HEALTH/CRITICAL/REGEN_RESEARCH into ResolveStats (RO-11 #A.1)` | `ResolveStats.kt` (signature: add `labLevels`); `ResolveStatsTest.kt` | +4 |
| 2 | `feat(battle): wire CASH_RESEARCH + UW_COOLDOWN engine multipliers (RO-11 #A.2)` | `GameEngine.kt`; `BattleViewModel.kt` (read lab levels, push multipliers); `GameEngineTest.kt` | +2 |
| 3 | `feat(steps): wire STEP_EFFICIENCY lab research into walking credit (RO-11 #A.3)` | `DailyStepManager.kt` (constructor: `LabRepository`); `DailyStepManagerTest.kt` (FakeLabRepository setup) | +3 |
| 4 | `feat(battle): wire WAVE_SKIP research to start rounds at higher wave (RO-11 #B.1)` | `WaveSpawner.kt` (constructor: `startWave`); `GameEngine.kt`; `BattleViewModel.kt`; new `WaveSpawnerTest.kt` | +2 |
| 5 | `chore(labs): mark AUTO_UPGRADE_AI + ENEMY_INTEL as Coming Soon (RO-11 #B.2)` | `ResearchType.kt` (descriptions); `LabsScreen.kt` (Coming Soon overlay); `LabsViewModelTest.kt` | +1 |
| 6 | `feat(battle): in-round upgrade-effect readout per row (RO-11 #C / RO-10)` | new `DescribeUpgradeEffect.kt`; `InRoundUpgradeMenu.kt`; `BattleViewModel.kt`; `BattleScreen.kt`; new `DescribeUpgradeEffectTest.kt` | +24 |
| 7 | `docs(ro-11): sync state, run log, changelog, AGENTS, source-files` | `STATE.md`, `RUN_LOG.md`, `CHANGELOG.md`, `AGENTS.md`, `source-files.md`, `structure.md` | 0 |

Total estimated test delta: **+36** (572 → ~608). May land 1–3 lower depending on test consolidation.

### Why one PR not multiple

- All 6 fix commits touch the same `ResolveStats` signature path; serializing them into separate PRs would require keeping the old signature alive during the gap, doubling the diff.
- Phase C (the readout) is meaningless without Phases A + B — it would render lies.
- One PR → one Play Console upload (versionCode 4 → 5) → one smoke test cycle. Clean.

---

## 8. Acceptance criteria

Before declaring this PR done:

1. `./run-gradle.sh test` BUILD SUCCESSFUL with ≥ 605 tests passing (target 608).
2. `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL.
3. **Manual smoke checks** (must pass on physical device with v5 internal-track AAB):
   - Research DAMAGE_RESEARCH to L5; start a round; tower visibly hits harder than a control round at L0.
   - Research HEALTH_RESEARCH to L5; start a round; max-HP bar reads ≥ 25% higher.
   - Research CASH_RESEARCH to L5; complete a wave; cash earned per kill is ≥ 25% above control.
   - Research CRITICAL_RESEARCH to L10 + CRITICAL_CHANCE Workshop L100 (guaranteed crit); crit damage per shot is ≥ 30% above control.
   - Research STEP_EFFICIENCY to L5; walk 100 sensor steps; daily-step record shows ≥ 110 steps credited (10 % bonus).
   - Research UW_COOLDOWN to L10; activate any UW; cooldown ring-fill takes ~70% of the unmodified duration.
   - Research WAVE_SKIP to L5; start a round; HUD shows "Wave 6" not "Wave 1".
   - Open the in-round upgrade menu; every visible upgrade row shows a "Now → Next" readout below its description with non-empty current and next strings.
   - AUTO_UPGRADE_AI + ENEMY_INTEL rows on the Labs screen are clearly marked "Coming Soon" (greyed out / overlay / disabled Start button).
4. **Regression checks:**
   - All RO-08 fixes still work (in-round ATTACK_SPEED visibly speeds up tower; STEP_MULTIPLIER bonus stacks correctly with STEP_EFFICIENCY).
   - All RO-09 fixes still work (CHRONO_FIELD slows enemies; GOLDEN × overdrive cash multiplier behaves correctly).
5. Project memory synced: STATE.md / RUN_LOG.md / AGENTS.md / source-files.md / structure.md / CHANGELOG.md all updated.

---

## 9. Risks + open questions

### Risks

| Risk | Mitigation |
|---|---|
| **Schema migration not needed but data race possible.** `LabRepository.observeAllResearch().first()` in `DailyStepManager.applyStepMultiplier` performs a per-credit DB read. Same pattern as existing `WorkshopRepository` read. | Defensive `try { ... } catch { 0 }` already present for STEP_MULTIPLIER; extend the pattern. |
| **Cross-validator unit mismatch worsens.** RO-09 deferred finding #3 already flagged this; STEP_EFFICIENCY widens the gap. | Document in CHANGELOG; closed-test cohort has clean step history; v1.x schema fix tracked. |
| **WAVE_SKIP interacts with best-wave milestones.** Player at WAVE_SKIP L10 starting at wave 11 doesn't earn the wave-1 milestone. | Wave milestones already key on `currentWave` not "did you survive every wave from 1 up". Document as intentional — reaching wave 11 means you're skipping 10 wave-cooldown periods of credit-eligible Cash, so the milestone-PS reward is effectively earlier. Acceptable. |
| **`ResolveStats` signature change cascades.** New `labLevels` parameter touches every test that constructs ResolveStats. Default `emptyMap()` preserves call sites. | Default value preserves binary compat in tests; ResolveStatsTest already exercises empty-map paths for backwards-compat. |
| **`BattleViewModel` constructor reaches 16 params.** Already at 14; adding `LabRepository` makes 15. | Acceptable for v1.0. v1.x candidate for refactor — extract a `BattleSessionRepositories` aggregator. Track in B.4 / B.5 backlog or new ADR. |
| **`DailyStepManager` constructor adds another repo dependency.** | Hilt resolves automatically. Tests need `FakeLabRepository`; already exists in fakes/. |

### Open questions (decide before commit 1)

1. **`WAVE_SKIP` semantics.** Read as "start at wave 1 + level" (L1 = wave 2) or "start at wave level" (L1 = wave 1, no-op)? Recommendation: **`start at wave 1 + level`** — L0 = wave 1 (current), L1 = wave 2, L10 = wave 11. Aligns with description ("start at wave X").
2. **Lab multipliers stack with workshop?** Recommendation: **multiplicatively** — same pattern as workshop × in-round in RO-08. So DAMAGE final = `BASE × (1+ws×0.02) × (1+ir×0.02) × (1+lab×0.05)`.
3. **`STEP_EFFICIENCY` cap interaction with `STEP_MULTIPLIER` cap.** Recommendation: **shared +100% cap** — `(ws*0.01 + lab*0.02).coerceAtMost(1.0)`. Otherwise stacking the two could exceed the GDD-documented +100% bonus ceiling.
4. **AUTO_UPGRADE_AI / ENEMY_INTEL: refund Steps spent on already-completed research?** Recommendation: **no refund** — these are now declared "Coming in v1.x"; player will benefit retroactively when v1.x ships. Closed-test cohort is unlikely to have spent Steps on these anyway given they're high-tier costs.
5. **Readout format Localization?** Recommendation: **English-only for v1.0** — match existing string conventions. Localization is a v2.0 effort.

---

## 10. Deferred items (explicitly **not** in this plan)

| Item | Rationale | Tracking |
|---|---|---|
| `AUTO_UPGRADE_AI` real implementation | ~2 days of design + code. Not blocker for closed test. | v1.x backlog item; new plan-v1x-auto-upgrade-ai.md to be created post-launch |
| `ENEMY_INTEL` real implementation | UI work for HP-bar gating + wave preview + boss telegraph. Not blocker. | v1.x backlog item; new plan-v1x-enemy-intel.md to be created post-launch |
| Cross-validator unit fix for STEP_MULTIPLIER + STEP_EFFICIENCY | Schema migration v9 → v10 needed. Closed-test exposure ~zero. Same as RO-09 deferred #3. | Tracked in CHANGELOG RO-09 deferred section; v1.x patch backlog |
| Workshop screen surfacing the same readout | Workshop is browse-and-purchase outside battle; player has time to read description. Lower priority than in-round visibility. | v1.x candidate |
| Refactor `BattleViewModel` to fewer constructor params | 16 params is hard to read but functional. ADR + refactor for v1.x. | v1.x backlog item |

---

## 11. Schedule

| Activity | Estimate |
|---|---|
| Phase A implementation + tests (commits 1–3) | ~2 hours |
| Phase B implementation + tests (commits 4–5) | ~1.5 hours |
| Phase C implementation + tests (commit 6) | ~3 hours |
| Doc sync + verification (commit 7) | ~30 minutes |
| Manual smoke test on device | ~30 minutes |
| **Total active work** | ~7.5 hours, single session |

Target: complete + push to `main` within 24 hours of plan acceptance. Then bump `versionCode 4 → 5`, bundleRelease, upload to internal track for one final smoke check, then promote to closed.

---

## 12. Summary for STATE.md

```
- **Current objective:** RO-11 — wire all 10 dead Labs research enums (currently zero gameplay
  effect) + in-round upgrade-effect readout (RO-10 absorbed). Plan: docs/plans/plan-RO-11-labs-wiring.md.
  Phases: A (7 simple multipliers, closed-test blocker), B (WAVE_SKIP wired, AUTO_UPGRADE_AI +
  ENEMY_INTEL deferred to v1.x with UI gate), C (DescribeUpgradeEffect use case + in-round menu
  readout). Single PR, 7 commits, target +36 tests (572 → ~608). Then bump versionCode 4 → 5,
  re-upload to internal track, smoke test, promote to closed.
```

---

## References

- `docs/plans/plan-RO-09-pre-closed-test-fixes.md` — predecessor audit findings doc
- `docs/StepsOfBabylon_GDD.md` § Labs — original Labs design intent
- `docs/agent/RUN_LOG.md` 2026-05-18 entries — RO-08 + RO-09 + v4-upload context
- `docs/battle-formulas.md` — multiplicative-stat formula reference
- ADR-0003 (Battle Step Rewards) — pattern for additive bonuses on credited steps
- Original RO-10 task list (paused): "Show in-round upgrade effect on each row" — absorbed as Phase C
