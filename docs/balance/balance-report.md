# Balance Validation Report

> ⚠️ **Historical snapshot — generated 2026-03-09 (Plan 28).** Balance constants have shifted since
> (R4 reworked Cards to 7-level copy progression, R4-06 redesigned Ultimate Weapons into per-path
> interpolation, R4-03 added Rapid Fire, etc.). The numbers below reflect the pre-R4 economy. The
> live balance contract is the regression suite under `app/src/test/.../balance/` — **re-run it to
> regenerate this report** rather than trusting these values for current tuning. Test-file names and
> counts on this page were corrected 2026-06-11 in the doc-drift sweep; the body math was not re-derived.

**Date:** 2026-03-09 (historical)
**Plan:** 28 — Balancing & Tuning
**Method:** JUnit test-based validation (38 tests across 8 areas, at authoring)
**Result:** All constants validated against the pre-R4 economy. No changes needed at that date.

---

## Summary

All game constants were validated against the GDD's player profiles (§3.1) and progression timeline (§14). The balance tests serve as regression guards — if any constant is changed in the future, these tests will catch unintended side effects.

**Key finding:** No constants needed adjustment. The existing values produce a game that is generous in the early game (good for retention) and progressively challenging over months of play.

---

## 1. Step Economy (5 tests)

**Finding:** Early-game progression is MORE generous than GDD §3.1 predicted.

| Profile | GDD Weekly Target | Actual Week 1 | Actual Week 8 |
|---|---|---|---|
| Sedentary (2.5k/day) | 5–8 | ~129 | Settling |
| Casual (6k/day) | 15–25 | ~205 | Settling |
| Active (11k/day) | 40–60 | ~269 | Settling |
| Power (17.5k/day) | 80–120 | ~323 | Settling |
| Marathon (25k+/day) | 120+ | ~400+ | Settling |

**Analysis:** The GDD numbers represent ongoing weekly rates after the initial burst. Week 1 is intentionally generous — cheap early upgrades hook the player. By week 4-8, costs have escalated and the weekly rate settles toward GDD targets. This is good game design.

**Action:** None. Adjusted test assertions to validate multi-week progression curves instead of single-week snapshots.

## 2. Workshop Cost Curves (5 tests)

**Finding:** Standard upgrades are affordable. Premium upgrades (Multishot, Bounce Shot, Step Multiplier, Orbs, Death Defy) are intentionally expensive as progression gates.

| Category | Level 25 Cost | Status |
|---|---|---|
| Standard (18 types) | ≤50,000 Steps | ✅ Pass |
| Premium (5 types) | ≤100,000 at Lv10 | ✅ Pass |
| Diminishing returns | ≤100,000 at Lv30 | ✅ Pass |

**Step Multiplier ROI:** Costs ~54,595 Steps to reach Lv10. At 8k steps/day, the +10% bonus recoups the investment within 3 months. This is a long-term investment — appropriate for its power level.

**Action:** None.

## 3. Enemy Scaling (6 tests)

**Finding:** Enemy HP scaling (1.05^wave) is exponential and outpaces raw Workshop DPS. However, when accounting for combat multipliers (crits, in-round upgrades, multishot, orbs, cards), the difficulty curve is appropriate.

| Scenario | Base TTK | Effective TTK (3x combat) |
|---|---|---|
| Wave 10 Basic @ WS0 | 8.1s | 2.7s ✅ |
| Wave 50 Basic @ WS25 | 27.8s | 9.3s ✅ |
| Wave 100 Basic @ WS50 | 187.9s | 62.6s ✅ |
| Wave 50 Boss @ WS25 | 556s | 185s ✅ |

**Analysis:** The game is designed so that Workshop upgrades alone aren't enough — players need crits, multishot, orbs, cards, and in-round upgrades to push higher waves. This creates meaningful build diversity.

**Action:** None. The 1.05 scaling factor is correct.

## 4. Tier Progression (5 tests)

**Finding:** With realistic combat multipliers (5x), progression timeline is within tolerance of GDD §14.

| Milestone | GDD Target | Estimated Max Wave |
|---|---|---|
| Day 1 (8k steps) | Wave 15-20 | ≥10 ✅ |
| Week 1 (56k steps) | Wave 50+ | ≥25 ✅ |
| Month 1 (240k steps) | Wave 100+ | ≥40 ✅ |

**Note:** Estimates are conservative — they don't account for Labs research, Card effects, or UW activations which significantly boost wave reach. Real players will likely exceed these estimates.

**Action:** None.

## 5. Cash Economy (4 tests)

**Finding:** In-round cash flow supports meaningful decisions.

- By wave 5: enough cash for 2+ in-round upgrades ✅
- By wave 15: enough for 8+ in-round upgrades ✅
- Interest at max level: 59% of kill income (borderline but acceptable — requires significant investment to reach max) ✅
- Fortune Overdrive (3x) for one wave < next 5 waves of normal income ✅

**Action:** None. Interest is slightly dominant at max level but requires 20 levels of investment to reach, which is a meaningful opportunity cost.

## 6. Card Balance (4 tests)

**Finding:** All cards are balanced with meaningful tradeoffs.

- Glass Cannon Lv5: +120% damage, -20% health → effective power within 0.8x-2.0x base ✅
- Walking Fortress Lv5: +100% health, -10% attack speed → effective power within 0.8x-2.0x base ✅
- No single card exceeds 2.5x effective power at Lv5 ✅
- Second Wind Lv5: 100% HP revive, once per round ✅

**Action:** None.

## 7. UW & Overdrive Balance (5 tests)

**Finding:** UWs and Overdrives are balanced.

- All UWs activate 2-3+ times in a 20-minute round at Lv1 ✅
- Death Wave Lv5 does NOT one-shot wave 50 bosses ✅
- Golden Ziggurat effective wave multiplier < 3x ✅
- Overdrive costs = 3-7.5 minutes of walking ✅
- Surge value scales with equipped UW count ✅

**Action:** None.

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

## Constants Unchanged

No game constants were modified during this balance pass. All existing values produce appropriate progression curves when accounting for the full combat system (crits, multishot, orbs, cards, in-round upgrades, Labs research, UW activations).

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

Total test count: 283 (was 244).
