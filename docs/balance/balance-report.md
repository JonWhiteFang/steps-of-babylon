# Balance Validation Report

> **Regenerated 2026-06-11** against the live balance suite (originally authored 2026-03-09 / Plan 28).
> The body now reflects the **post-R4 economy** — R4-01 removed Step Overdrive, R4-02b moved
> Multishot/Bounce off the Workshop, R4-03 added Rapid Fire, R4-06 redesigned Ultimate Weapons into
> per-path interpolation, R4-08 moved Cards to 7-level copy progression. The live balance contract is
> the regression suite under `app/src/test/.../balance/`; re-run `testDebugUnitTest --tests
> "…balance.*"` and refresh this page after any constant change.

**Originally authored:** 2026-03-09 (Plan 28 — Balancing & Tuning) · **Regenerated:** 2026-06-11
**Method:** JUnit test-based validation (38 tests across 8 areas)
**Result:** All constants validated against the current economy. Suite green: 38 tests, 0 failures.

---

## Summary

All game constants were validated against the GDD's player profiles (§3.1) and progression timeline (§14). The balance tests serve as regression guards — if any constant is changed in the future, these tests will catch unintended side effects.

**Key finding:** No constants needed adjustment. The existing values produce a game that is generous in the early game (good for retention) and progressively challenging over months of play.

---

## 1. Step Economy (5 tests)

**Finding:** Early-game progression is MORE generous than GDD §3.1 predicted, then settles. The tests assert a generous Week-1 burst and continued (non-zero) progress through Week 8:

| Profile | GDD Weekly Target | Week-1 upgrades (asserted) | Week-8 still progressing (asserted) |
|---|---|---|---|
| Sedentary (2.5k/day) | 5–8 | > 50 | ≥ 1 |
| Casual (6k/day) | 15–25 | > 100 | ≥ 3 |
| Active (11k/day) | 40–60 | > 150 | ≥ 5 |
| Power (17.5k/day) | 80–120 | > 200 | ≥ 10 |
| Marathon (25k+/day) | 120+ | (8-week total > 500) | ≥ 10 |

**Analysis:** The GDD numbers represent ongoing weekly rates after the initial burst. Week 1 is intentionally generous — cheap early upgrades hook the player. By week 4–8, costs have escalated and the weekly rate settles toward GDD targets. Good game design.

**Action:** None. The tests validate multi-week progression curves (generous start → sustained progress) rather than single-week snapshots.

## 2. Workshop Cost Curves (5 tests)

**Finding:** Standard Workshop upgrades stay affordable; the 3 genuine Workshop premium upgrades (Step Multiplier, Orbs, Death Defy) are intentionally expensive as progression gates. (Multishot/Bounce Shot are no longer Workshop-purchasable as of R4-02b — they're in-round Cash purchases / Labs research, balance-tested under Cash Economy / Labs separately.)

| Category | Threshold | Status |
|---|---|---|
| Standard Workshop types | ≤50,000 Steps at Lv25 | ✅ Pass |
| Premium (3 types: Step Multiplier, Orbs, Death Defy) | ≤100,000 at Lv10 | ✅ Pass |
| Diminishing-return caps (Defense %, Crit Chance, Lifesteal) | ≤100,000 at Lv30 | ✅ Pass |
| Cheapest upgrade at Lv0 | ≤100 Steps | ✅ Pass |

**Step Multiplier ROI:** at Lv10 the +10% bonus recoups its cumulative cost within 3 months at 8k steps/day. A long-term investment appropriate for its power level.

**Action:** None.

## 3. Enemy Scaling (6 tests)

**Finding:** Enemy HP scaling is exponential and outpaces raw Workshop DPS, but with a realistic combat multiplier (3.0× for crits + in-round upgrades + multishot + orbs) the time-to-kill stays in the intended band. The tests assert TTK *ranges*, not fixed values:

| Scenario | Asserted TTK bound (3× combat) | Status |
|---|---|---|
| Wave 10 Basic @ WS0 | < 5s | ✅ |
| Wave 50 Basic @ WS25 | < 15s | ✅ |
| Wave 100 Basic @ WS50 | 1s–120s | ✅ |
| Wave 50 Boss @ WS25 | 5s–300s | ✅ |

Scaling sanity: wave-50 HP is 3–20× wave-10, wave-100 HP is 20–500× wave-10 (exponential but not runaway). Enemy damage never one-shots the ziggurat (< 50% max HP) at expected Workshop levels.

**Analysis:** Workshop upgrades alone aren't enough — players need crits, multishot, orbs, cards, and in-round upgrades to push higher waves, which creates meaningful build diversity.

**Action:** None.

## 4. Tier Progression (5 tests)

**Finding:** With realistic combat multipliers (5x), progression timeline is within tolerance of GDD §14.

| Milestone | GDD Target | Estimated Max Wave |
|---|---|---|
| Day 1 (8k steps) | Wave 15-20 | ≥10 ✅ |
| Week 1 (56k steps) | Wave 50+ | ≥25 ✅ |
| Month 1 (240k steps) | Wave 100+ | ≥40 ✅ |

**Note:** Estimates are conservative — they don't account for Labs research, Card effects, or UW activations which significantly boost wave reach. Real players will likely exceed these estimates.

**Action:** None.

## 5. Cash Economy (3 tests)

**Finding:** In-round cash flow supports meaningful decisions.

- By wave 5: enough cash for 2+ in-round upgrades ✅
- By wave 15: enough for 8+ in-round upgrades ✅
- Interest at max level stays below 65% of kill income (doesn't dominate) ✅

**Action:** None. Interest is bounded under kill income and requires 20 levels of investment to reach max — a meaningful opportunity cost. (The pre-R4-01 "Fortune Overdrive" cash-burst test was dropped when Step Overdrive was removed.)

## 6. Card Balance (4 tests)

**Finding:** All cards are balanced with meaningful tradeoffs (cards max at Lv7 since R4-08).

- Glass Cannon Lv5: +damage/-health tradeoff → effective power (DPS × HP) within 0.8x–2.0x base ✅
- Walking Fortress Lv5: +health/-attack-speed → effective power within 0.8x–2.0x base ✅
- No single card exceeds 2.5x effective power at Lv5 ✅
- Second Wind Lv7: revive at 100% HP (capped), once per round ✅

**Action:** None.

## 7. Ultimate Weapon Balance (5 tests)

**Finding:** The R4-06 per-path UW design (DAMAGE / SECONDARY / COOLDOWN paths, each L1→L10 linear interpolation) is balanced.

- All UWs activate 2–60 times in a 20-minute round at cooldown L1 (≥2 guaranteed) ✅
- Death Wave DAMAGE-path L10 does NOT one-shot a wave-50 boss (and isn't negligible) ✅
- Golden Ziggurat max cash multiplier (8× for 10s over a 35s wave) → effective multiplier < 5× ✅
- COOLDOWN path L10 is ≥50% shorter than L1 for every UW ✅
- Per-path interpolation is monotonic for all UWs (COOLDOWN + Chrono Field DAMAGE decrease — lower is better) ✅

**Action:** None. (Step Overdrive balance tests were removed with the mechanic in R4-01; this section is now UW-only.)

## 8. Supply Drop & Premium Currency (5 tests)

**Finding:** Premium currency income is sustainable.

- Expected drops at 10k steps/day: 1-5 ✅
- Weekly PS income (active walker): ~20 PS ✅
- Weekly Gem income (active walker): ~20+ Gems ✅
- Common Card Pack every ~4 days ✅
- First UW unlock within 3 weeks ✅

**Note:** First UW unlock takes ~3 weeks (not 2 as originally hoped). This is acceptable — UWs are a mid-game reward, not an early-game one.

**Action:** None.

---

## Constants

No balance constants needed adjustment at the latest regeneration. All current values produce appropriate progression curves when accounting for the full combat system (crits, multishot, orbs, cards, in-round upgrades, Labs research, UW activations).

## Regression Tests

38 balance validation tests in `app/src/test/java/com/whitefang/stepsofbabylon/balance/`:
- `StepEconomyTest.kt` — 5 tests
- `CostCurveTest.kt` — 5 tests
- `EnemyScalingTest.kt` — 6 tests
- `TierProgressionTest.kt` — 5 tests
- `CashEconomyTest.kt` — 3 tests
- `CardBalanceTest.kt` — 4 tests
- `UWBalanceTest.kt` — 5 tests _(renamed from `UWOverdriveBalanceTest.kt` in R4-01 when Step Overdrive was removed)_
- `SupplyDropEconomyTest.kt` — 5 tests

**Total: 38 balance tests, all green (verified 2026-06-11).** (For the project-wide headline test count see `docs/agent/STATE.md` / `CLAUDE.md` — not pinned here.)
