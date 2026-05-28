# ADR-0015: STEP_MULTIPLIER Asymptotic Curve

**Status:** Accepted
**Date:** 2026-05-28
**Supersedes:** Original "linear +1%/level capped at 100%" decision implicit in Plan 01 (`UpgradeType.STEP_MULTIPLIER` config).
**Superseded by:** None

## Context

The `UpgradeType.STEP_MULTIPLIER` upgrade was designed pre-launch with a linear curve: each level grants +1% walking-step bonus, hard-capped at +100% (i.e. 2× walking steps). The cost scaling is 1.35× per level with `maxLevel = 100`.

GitHub issue #49 (2026-05-25 triage) flagged two problems:

1. **Dead content at high levels.** L99 and L100 both display "+100% steps" — the cap creates a flat readout that gives the player no incentive to upgrade further. Adjacent-level decisions become meaningless.

2. **Cost wall vs progression mismatch.** Linear "+1%/level" means each level is equally valuable, but cost scaling 1.35× per level means each level is exponentially more expensive. The marginal value-per-step drops to zero. Players who optimise reach a cost-wall around L40-50 where the next level costs more than they could earn in a year of walking.

## Decision

**Replace the linear curve with an asymptotic curve: `bonus(level) = 1 - (1 - 0.05)^level`.**

The function is implemented in `domain/battle/engine/SimulationMath.stepMultiplierBonus(level)` (V1X-09 Phase 1 extracted SimulationMath earlier; this ADR adds one more function to that helper).

Properties:

| Level | Old (linear) | New (asymptotic) | Δ |
|---|---|---|---|
| L0 | 0% | 0% | 0 |
| L1 | 1% | 5.0% | +4 pp |
| L10 | 10% | 40.1% | +30 pp |
| L20 | 20% | 64.2% | +44 pp |
| L50 | 50% | 92.3% | +42 pp |
| L100 | 100% | 99.4% | -0.6 pp |
| L200 | 100% (capped) | 99.99% | -0.01 pp |

Net effect: **early-mid game gets significantly buffed, late game gets a tiny nerf at the L100 mark and infinite levels become academic** (the asymptote is +100%, never reached).

## Rationale

1. **Solves dead-content problem.** Adjacent levels at the high end visibly differ in the readout (L99 = +99.41%, L100 = +99.44%, displayed at 2-decimal precision). Every level remains a meaningful purchase decision.

2. **Exponential-cost / asymptotic-curve symmetry.** Both functions share the same shape (one approaches a horizontal asymptote, the other approaches an infinite asymptote). The marginal cost-per-bonus stays roughly constant across the level range, instead of dropping to zero.

3. **Buffs early-game progression.** L10 was previously +10% bonus; now it's +40%. This makes STEP_MULTIPLIER an attractive early-mid-game purchase decision, rather than a niche "save up for L100" upgrade.

4. **Late-game preserved.** L100 still feels like a milestone (now +99.41% instead of +100%). The 0.6 pp difference is imperceptible in actual gameplay (4-step difference per 1000 walking steps). The asymptotic tail past L100 is computationally fine but practically unreachable due to cost scaling.

5. **Centralises math in domain layer.** The formula is a pure function in `SimulationMath` — the same place V1X-09 Phase 1 extracted other simulation math. Domain layer owns it; presentation layer reads it.

## Consequences

### Positive

- 11 new tests (4 ResolveStats / 4 SimulationMath / 3 DescribeUpgradeEffect) cover the formula at L0/L1/L10/L20/L50/L100/L200 plus regression guards.
- `DescribeUpgradeEffect` formats with 2-decimal precision so adjacent-level differences stay visible.
- Late-game balance for STEP_MULTIPLIER no longer dead at L100; players can theoretically push higher (constrained by cost).
- Test count 789 → 800 (+11).

### Negative

- **Player-facing balance change.** Existing high-level players see their step income shift: a Lv 25 player goes from +25% bonus to +72% bonus (huge buff). A Lv 100 player goes from +100% to +99.41% (tiny nerf). Should be noted in the changelog.
- **Cross-validator interaction unchanged.** RO-09 deferred finding #3 (STEP_MULTIPLIER × cross-validator unit mismatch) is **NOT** addressed by this ADR. The cross-validator at Level 2+ offense compares `creditedSteps` against `hcSteps`, and creditedSteps now includes a larger STEP_MULTIPLIER bonus. A high-bonus player who hits Level 2 offense via a separate trigger (e.g. occasional sensor / HC discrepancy) will see a larger deduction than before. Mitigated by: (a) Level 2 only triggers after 3+ recorded offenses, (b) the discrepancy threshold (20%) catches the multiplier-driven gap, (c) the deduction shape is the same — just slightly larger numbers. **Tracked as V1X-18b for a follow-up PR that subtracts the known multiplier-bonus before comparison.**

### Neutral

- `UpgradeConfig.maxLevel = 100` retained for UI consistency. The asymptotic curve doesn't strictly require a cap, but keeping the cap prevents pathological "level 10000" states that could surface from save corruption / cheat tools.
- `STEP_MULTIPLIER_PER_LEVEL = 0.01` constant in `DailyStepManager` is no longer read by production code (the asymptotic curve replaces it) but retained for KDoc reference value. Could be deleted in a future cleanup pass.
- STEP_EFFICIENCY (Lab) keeps its linear +2%/level — separate upgrade, separate balance. The two stack additively under the +100% combined cap (unchanged from RO-11).

## Implementation

Files changed:

- `domain/battle/engine/SimulationMath.kt` — new `stepMultiplierBonus(level)` function and `STEP_MULTIPLIER_DECAY_FACTOR` / `STEP_MULTIPLIER_BONUS_CAP` constants.
- `data/sensor/DailyStepManager.applyStepMultiplier` — replaces linear formula with `SimulationMath.stepMultiplierBonus(wsLevel)`. Lab portion unchanged.
- `domain/usecase/DescribeUpgradeEffect.kt` — STEP_MULTIPLIER branch reads `stepMultiplierBonus`. Format bumped to `%.2f%%` so adjacent-level readouts visibly differ.
- `domain/model/UpgradeType.kt` — STEP_MULTIPLIER description updated to reflect asymptotic shape.

Tests added/changed:

- `SimulationMathTest`: 8 new tests for `stepMultiplierBonus` (L0/L1/L10/L20/L50/L100/L200 + L99-vs-L100 dead-content regression guard).
- `DescribeUpgradeEffectTest`: 4 new tests (L0/L50/L100/L99-vs-L100 regression guard); the old "L50 reads +50%" test deleted.
- `DailyStepManagerTest`: 4 STEP_MULTIPLIER tests rewritten for new curve values (L50 → 192 credited, L200 → 199, combined L10+L5 → 150, L100+L10 → 200 cap).

## Future Reconsideration

If gameplay testing reveals the buffed early-mid game is overpowered, the per-level decay factor `p` can be tuned (currently 0.05). Smaller p → slower curve → less early buff. The constant is in one place (`SimulationMath.STEP_MULTIPLIER_DECAY_FACTOR`) so retuning is a single-line change.

V1X-18b (deferred): cross-validator subtracts the multiplier-bonus before comparing creditedSteps to hcSteps. This addresses RO-09 #3 directly and removes the slight false-positive risk for high-multiplier players.

## References

- GitHub issue #49 — original triage finding
- `domain/battle/engine/SimulationMath.kt` — `stepMultiplierBonus(level)` implementation
- `data/sensor/DailyStepManager.kt` — `applyStepMultiplier` consumer
- `domain/usecase/DescribeUpgradeEffect.kt` — UI readout
- ADR-0012 — Simulation extraction Phase 1 (established `SimulationMath` location)
- RO-09 deferred finding #3 — cross-validator interaction (V1X-18b follow-up)
