# Plan 28 — Balancing & Tuning

**Status:** Complete
**Dependencies:** Plan 27 (Polish & Visual Effects)
**Layer:** `domain/` — tuning constants and formulas

---

## Objective

Comprehensive balance pass across the entire game: Step economy tuning for all player profiles, Workshop cost curve validation, enemy HP/damage scaling, tier difficulty curves, cash multiplier validation, Card balance, UW cooldown/power tuning, Lab research value, and Supply Drop rates. Ensure the game feels rewarding for casual walkers and challenging for power walkers.

Reference: GDD §3.1 for player profiles, `docs/battle-formulas.md` for all formulas.

---

## Task Breakdown

### Task 1: Step Economy Validation

Validate against GDD §3.1 player profiles:
- Sedentary (2–3k steps/day): 5–8 low-tier upgrades/week
- Casual (5–7k): 15–25 upgrades/week
- Active (10–12k): 40–60 upgrades, mid-tier costs
- Power (15–20k): 80–120 upgrades, high-tier
- Marathon (25k+): deep investment

Create `docs/balance/step-economy-analysis.md`:
- Simulate upgrade progression for each profile over 1 week, 1 month, 3 months
- Identify cost curve breakpoints where progression stalls
- Adjust `baseCost` and `scaling` values if needed

---

### Task 2: Workshop Cost Curve Tuning

Validate all 23 upgrade cost curves:
- Plot cost vs level for each upgrade (levels 1–100)
- Ensure no upgrade becomes unreachable for active walkers within 3 months
- Ensure Step Multiplier ROI is attractive at early levels
- Verify diminishing-return upgrades (Defense %, Crit Chance) feel worth investing in near caps
- Adjust `scaling` factors if curves are too steep or too flat

---

### Task 3: Enemy Scaling Validation

Tune `waveScalingFactor`:
- Ensure waves 1–20 are comfortable for new players
- Waves 30–50 should challenge players with Tier 1–2 Workshop levels
- Waves 75–100 should require Tier 3–5 Workshop investment
- Waves 100+ should require significant Workshop + Lab + Card investment
- Boss waves should feel like meaningful checkpoints

---

### Task 4: Tier Difficulty Curves

Validate tier progression:
- Tier 1–3: accessible to casual walkers within 2–3 weeks
- Tier 4–6: requires active walking (1–2 months)
- Tier 7–8: requires dedicated walking (2–4 months)
- Tier 9–10: endgame (4–6+ months)
- Battle conditions at T6+ should force strategy changes, not just stat checks

---

### Task 5: Cash Economy Balance

Tune in-round cash flow:
- Early waves: enough cash for 2–3 upgrades between waves
- Mid waves: meaningful choices between offense and defense
- Late waves: cash surplus allows strategic investment
- Interest mechanic: rewarding but not dominant strategy
- Free upgrade chance: noticeable but not game-breaking

---

### Task 6: Card Balance Pass

Review all 9 cards at levels 1 and 5:
- No single card should be mandatory for progression
- Epic cards (Walking Fortress, Glass Cannon) should have meaningful tradeoffs
- Second Wind should be powerful but not trivialize difficulty
- Step Surge Gem bonus should be attractive without being exploitable

---

### Task 7: UW & Overdrive Balance

Tune Ultimate Weapons:
- Cooldowns should allow 2–3 activations per round (at typical round length)
- No single UW should be strictly dominant
- Golden Ziggurat cash bonus should be strong but not replace Cash Bonus upgrades

Tune Overdrive:
- Step costs should feel meaningful (500 Steps = ~5 minutes of walking)
- Effects should be impactful enough to justify the permanent currency spend
- Fortune Overdrive early-round should be a viable strategy

---

### Task 8: Supply Drop Rate Tuning

Validate drop rates:
- Average 2–4 drops per 10,000 steps
- Rewards should feel exciting but not inflate economy
- Overdrive charges from Supply Drops: rare enough to be special
- Unclaimed inbox (max 10) shouldn't fill up for daily walkers

---

### Task 9: Progression Timeline Validation

Simulate full progression against GDD §14 timeline:
- Day 1: Tutorial, Workshop Lv3–5, Wave 15–20
- Week 1: Workshop Lv15–25, 2 UWs, Wave 50+, Tier 2
- Month 1: Deep Workshop, Step Multiplier active, Tier 5–6, Wave 100+
- Month 6+: Endgame, Tiers 9+

Document any deviations and adjust constants.

---

## File Summary

```
docs/balance/
├── step-economy-analysis.md    (new)
├── cost-curve-plots.md         (new)
├── enemy-scaling-analysis.md   (new)
└── progression-simulation.md   (new)

domain/model/
├── UpgradeType.kt              (potential tuning updates)
├── TierConfig.kt               (potential tuning updates)
├── EnemyType.kt                (potential tuning updates)
├── OverdriveType.kt            (potential tuning updates)
└── ResearchType.kt             (potential tuning updates)
```

## Completion Criteria

- Step economy validated for all 5 player profiles
- Workshop cost curves produce smooth progression without walls
- Enemy scaling creates appropriate difficulty ramp
- Tier progression timeline matches GDD §14 (±20% tolerance)
- Cash economy supports meaningful in-round decisions
- No single Card, UW, or strategy is strictly dominant
- Supply Drop rates feel rewarding without inflating economy
- Balance analysis documents created for future reference
- All tuning changes documented in CHANGELOG
