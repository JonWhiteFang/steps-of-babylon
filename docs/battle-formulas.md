# Battle Formulas

All math used in combat, economy, and progression. Single reference for balance tuning.

## Workshop Upgrade Cost

```
cost = ceil(baseCost × scaling ^ currentLevel)
```

- `baseCost` and `scaling` are per-UpgradeType (see GDD §4)
- Returns `Long` (rounded up)
- Used for both Workshop (Step-funded) and in-round (Cash-funded) upgrades

### Example: Damage upgrade at level 10

```
50 × 1.12^10 = 50 × 3.1058 = 155.29 → 156 Steps
```

## Stats Resolution

Workshop (permanent), in-round (temporary), and Lab research (permanent outer multiplier) combine multiplicatively:

```
effectiveStat = baseStat × (1 + workshopBonus) × (1 + inRoundBonus) × (1 + labBonus)
```

This applies to all stat-bearing upgrades. The Lab tier is a third multiplicative term layered on top of the existing Workshop × In-Round product, populated from the player's completed Lab research levels (`DAMAGE_RESEARCH`, `HEALTH_RESEARCH`, `CRITICAL_RESEARCH`, `REGEN_RESEARCH`). Per-research multipliers are listed under each formula below. RO-11 wired this in 2026-05-19; pre-RO-11 the Lab tier was absent and research did nothing.

## Damage Calculation

```
rawDamage = baseDamage
          × (1 + damageLevel × 0.02)                     // Workshop DAMAGE
          × (1 + inRoundDamageLevel × 0.02)              // In-round DAMAGE
          × (1 + damageResearchLevel × 0.05)             // Lab DAMAGE_RESEARCH (RO-11)

isCrit = random() < critChance
critChance = min((critChanceLevel + inRoundCritChanceLevel) × 0.005, 0.80)
critMultiplier = (2.0 + (critFactorLevel + inRoundCritFactorLevel) × 0.1)
               × (1 + criticalResearchLevel × 0.03)        // Lab CRITICAL_RESEARCH (RO-11)

finalDamage = isCrit ? rawDamage × critMultiplier : rawDamage
```

### Damage/Meter Bonus

```
distanceBonus = 1 + (distanceToEnemy / maxRange) × damagePerMeterLevel × 0.01
finalDamage *= distanceBonus
```

## Defense Calculation

```
damageReduction = defensePercentLevel × 0.003  // cap 75%
flatBlock = defenseAbsoluteLevel × flatBlockPerLevel

damageTaken = max(0, incomingDamage × (1 - damageReduction) - flatBlock)
```

Defense % is diminishing — each point adds 0.3% but the cap is 75%.

## Health & Regen

```
maxHealth = baseHealth
          × (1 + healthLevel × 0.03)
          × (1 + inRoundHealthLevel × 0.03)
          × (1 + healthResearchLevel × 0.05)             // Lab HEALTH_RESEARCH (RO-11)
regenPerSecond = baseRegen
               × (1 + (regenLevel + inRoundRegenLevel) × 0.02)
               × (1 + regenResearchLevel × 0.04)         // Lab REGEN_RESEARCH (RO-11)
```

## Lifesteal

```
healAmount = finalDamage × min(lifestealLevel × 0.002, 0.15)
```

Cap: 15% of damage dealt.

## Knockback

```
knockbackForce = baseKnockback × (1 + knockbackLevel × 0.02)
```

Reduced by Knockback Resistance battle condition at higher tiers.

## Death Defy

```
deathDefyChance = min(deathDefyLevel × 0.01, 0.50)
```

On lethal hit: roll against chance. If success, survive at 1 HP. Once per hit.

## Thorn Damage

```
reflectedDamage = incomingDamage × thornLevel × 0.01
```

Reduced by Thorn Resistance battle condition at higher tiers.

## Orbs

Orbiting projectiles that damage nearby enemies. Count = orbLevel (cap 6). Reduced by Orb Resistance battle condition.

```
orbDamage = baseDamage × 0.5 × orbDamageMultiplier
orbKnockback = knockbackForce × 0.5 × knockbackMultiplier
```

Orbs deal half the knockback force of direct projectile hits.

## Multishot & Bounce

```
targets = min(1 + multishotLevel + multishotResearchLevel, 11)   // additive; cap 11
bounces = min(bounceShotLevel + bounceResearchLevel, 10)          // additive; cap 10
```

`multishotLevel` / `bounceShotLevel` are the combined Workshop + in-round totals;
`multishotResearchLevel` / `bounceResearchLevel` are the Lab `MULTISHOT_RESEARCH` /
`BOUNCE_RESEARCH` levels. Each bounce deals full damage to the next target.

## Cash Economy (In-Round)

```
cashFromKill = baseKillCash
             × tierCashMultiplier
             × (1 + cashBonusLevel × 0.03)               // Workshop CASH_BONUS
             × fortuneMultiplier                         // GOLDEN_ZIGGURAT UW (1.0× when inactive)
             × (1 + cardCashBonusPercent / 100)          // CASH_GRAB card (1.0× when not equipped)
             × cashResearchMultiplier                    // Lab CASH_RESEARCH outer multiplier (RO-11)
cashPerWave  = (baseCashPerWave + cashPerWaveLevel × flatBonusPerLevel)
             × cashResearchMultiplier                    // Lab CASH_RESEARCH outer multiplier (RO-11)
interest = min(heldCash × interestLevel × 0.005, heldCash × 0.10)  // cap 10%
freeUpgradeChance = min(freeUpgradeLevel × 0.01, 0.25)             // cap 25%
```

`cashResearchMultiplier` is `1.0` at level 0; +5%/lvl wires onto `GameEngine.cashResearchMultiplier` from `BattleViewModel` once per round and applies on every kill cash credit + every wave-end cash payout. `fortuneMultiplier` is the GOLDEN_ZIGGURAT UW's DAMAGE-path cash multiplier (1.0× when the UW is not active); `cardCashBonusPercent` comes from the CASH_GRAB card (`GameEngine.cashBonusPercent`, 0 when not equipped). Both stack multiplicatively on the per-kill credit only — `cashPerWave` is unaffected by them.

### Tier Cash Multipliers

| Tier | Multiplier |
|---|---|
| 1 | 1.0× |
| 2 | 1.8× |
| 3 | 2.6× |
| 4 | 3.4× |
| 5 | 4.2× |
| 6 | 5.0× |
| 7 | 6.0× |
| 8 | 7.2× |
| 9 | 8.5× |
| 10 | 10.0× |

## Wave Timing

```
spawnPhaseDuration = 26 seconds
cooldownDuration = 9 seconds
totalWaveDuration = 35 seconds (at 1x speed)
```

Speed controls: 1x / 2x / 4x (multiply game tick rate).

## Enemy Scaling

Base stats scale per wave:

```
enemyHealth = baseHealth × healthMultiplier × waveScalingFactor(wave)
enemyDamage = baseDamage × damageMultiplier × waveScalingFactor(wave)
```

### Enemy Type Multipliers

| Type | Speed | Health | Damage |
|---|---|---|---|
| Basic | 1.0× | 1.0× | 1.0× |
| Fast | 2.0× | 0.5× | 0.7× |
| Tank | 0.5× | 5.0× | 2.0× |
| Ranged | 0.8× | 0.8× | 1.2× |
| Boss | 0.5× | 20.0× | 3.0× |
| Scatter | 1.2× | 1.5× | 0.8× |

Bosses spawn every 10 waves (every 7 at Tier 9+ with More Bosses condition).

## Scatter Enemy Split

When a Scatter enemy dies, it spawns 2–3 Basic children:

```
childCount = random(2, 3)
childHp = parentMaxHp × 0.5
childDamage = parentDamage × 0.5
childSpeed = scatterBaseSpeed × enemySpeedMultiplier
```

## Armored Enemies (Tier 8+)

Enemies spawn with an armor hit counter from the ARMORED_ENEMIES battle condition:

```
armorHits = battleConditions[ARMORED_ENEMIES] (e.g., 5 at Tier 8)
```

While `armorHits > 0`, all incoming damage is fully absorbed and the counter decrements by 1. After armor breaks, enemies take full damage normally.

## Battle Condition Effect Formulas (Tier 6+)

Pre-computed from `TierConfig.forTier(tier).battleConditions`:

```
enemySpeedMultiplier     = 1.0 + (ENEMY_SPEED / 100)
enemyAttackSpeedMultiplier = 1.0 + (ENEMY_ATTACK_SPEED / 100)
orbDamageMultiplier      = 1.0 - (ORB_RESISTANCE / 100)
knockbackMultiplier      = 1.0 - (KNOCKBACK_RESISTANCE / 100)
thornMultiplier          = 1.0 - (THORN_RESISTANCE / 100)
armorHits                = ARMORED_ENEMIES value (or 0)
bossWaveInterval         = MORE_BOSSES value (7) or default (10)
attackInterval           = 1.0 / enemyAttackSpeedMultiplier
```

Applied in engine:
- Enemy speed: `baseSpeed × typeMultiplier × enemySpeedMultiplier`
- Enemy attack interval: `1.0 / enemyAttackSpeedMultiplier`
- Orb damage: `baseDamage × 0.5 × orbDamageMultiplier`
- Projectile knockback: `knockbackForce × knockbackMultiplier`
- Orb knockback: `knockbackForce × 0.5 × knockbackMultiplier`
- Thorn damage: `incomingDamage × thornPercent × thornMultiplier`

## Rapid Fire (R4-03)

> Step Overdrive (the old Assault/Fortress/Fortune/Surge once-per-round buffs) was
> **removed in R4-01** and replaced by the **Rapid Fire** Workshop upgrade (`UpgradeType.RAPID_FIRE`).

Rapid Fire fires a periodic attack-speed burst during a wave's SPAWNING phase: every
`interval` seconds the ziggurat's attack speed is multiplied by `multiplier` for
`duration` seconds, then resets to 1.0× until the next pulse. Per-level values
interpolate L1 → L10 (`RapidFireSchedule`):

```
interval(level)   = lerp(60s → 30s)    // bursts get more frequent
duration(level)   = lerp(5s  → 30s)    // each burst lasts longer
multiplier(level) = lerp(2.0× → 3.0×)  // attack-speed boost grows
```

At L10 `duration == interval` (both 30s) so the next pulse fires before the previous one
expires — a permanent +3.0× attack-speed buff. The "Now → Next" UI renders
`permanent/{m}×` once `isPermanent(level)` (duration ≥ interval).

## Ultimate Weapon Formulas

> **R4-06 redesign (ADR-0008).** Each UW has **3 independent upgrade paths** (`UWPath`:
> DAMAGE, SECONDARY, COOLDOWN), each levelled 0→10 separately. Per-level values are a
> **linear interpolation** between the path's L1 and L10 endpoints stored on
> `UltimateWeaponType` — there is no single shared `level` or `0.05 × (level-1)`
> cooldown formula anymore.

### Unlock & Upgrade

```
unlockCost  = type.unlockCost (Power Stones)
upgradeCost = unlockCost × 2 × currentPathLevel (Power Stones)   // per-path; L0→L1 is free
maxPathLevel = 10                                                // MAX_PATH_LEVEL, per path
```

Total cost to take one path L0→L10 is `90 × unlockCost`; all three paths is `270 × unlockCost`.

### Per-Path Value Interpolation

```
valueAtLevel(path, level) = L1 + (L10 − L1) / 9 × (level − 1)
```

L1 maps to the path's L1 endpoint, L10 to its L10 endpoint, with 9 equal segments between.

### Cooldown Scaling

```
cooldown = type.cooldownAtLevel(cooldownPathLevel)               // = valueAtLevel(COOLDOWN, level)
         × uwCooldownMultiplier                                  // Lab UW_COOLDOWN outer multiplier (RO-11)
```

`cooldownAtLevel` interpolates the COOLDOWN path (L10 is the *lower*, better value).
`uwCooldownMultiplier` is `1.0` at level 0; -5%/lvl reduces all UW cooldowns and the
cooldown ring-fill UI tracks it. Wired onto `GameEngine.uwCooldownMultiplier` from
`BattleViewModel` once per round.

### Per-Path Endpoints (L1 → L10)

| UW Type | Unlock | DAMAGE path | SECONDARY path | COOLDOWN path | Duration |
|---|---|---|---|---|---|
| Death Wave | 50 PS | 500 → 3,000 dmg | 0.50 → 1.00 screen radius | 60s → 20s | Instant |
| Chain Lightning | 75 PS | 500 → 2,000 per-target | 3 → 12 chain targets | 30s → 6s | Instant |
| Black Hole | 100 PS | 50 → 250 DPS | 30 → 200 px/s pull | 90s → 30s | 5s |
| Chrono Field | 75 PS | 0.50 → 0.05 slow factor | 5s → 14s duration | 75s → 25s | (SECONDARY) |
| Poison Swamp | 60 PS | 1% → 8% MaxHP/s | 0.50 → 1.00 area fraction | 60s → 20s | 6s |
| Golden Ziggurat | 80 PS | 2× → 8× cash | 1.2× → 3.0× damage | 90s → 30s | 10s |

(For Chrono Field, the DAMAGE path *is* the slow factor — smaller is stronger — and the
SECONDARY path *is* the effect duration; the Duration column's flat value is the L1
fallback.)

## Lab Research Scaling

```
researchCost = baseCostSteps × scalingFactor ^ level
researchTime = baseTimeHours × timeScalingFactor ^ level
```

Research effects are additive per level (e.g., Damage Research: +5% per level).

## Step Multiplier

Workshop `STEP_MULTIPLIER` (+1 %/lvl) and Lab `STEP_EFFICIENCY` (+2 %/lvl) both add to walking-credit on the sensor path under a shared cap. Wired in by RO-08 (`STEP_MULTIPLIER`) and RO-11 (`STEP_EFFICIENCY`).

```
bonusFraction = min(stepMultiplierLevel × 0.01
                  + stepEfficiencyResearchLevel × 0.02,
                    1.00)                                  // shared cap +100 %
bonusSteps = rawSteps × bonusFraction
totalSteps = rawSteps + bonusSteps
```

Applies on the sensor (walking) credit path only — not on Health Connect activity minutes (cycling / swimming / treadmill / etc.). Bonus is added *after* anti-cheat (rate limit + velocity analysis) and *before* the absolute 50,000 steps/day daily ceiling, so the ceiling stays a hard cap. Each credit reads the current Workshop and Lab levels fresh from the DB so level-ups take effect immediately.

## Lab Research — Wave Skip

`WAVE_SKIP` lets the player start every battle round at a higher initial wave (RO-11 #B.1):

```
startWave = max(1, 1 + waveSkipResearchLevel)               // L0 = wave 1, L10 = wave 11
```

Enemy scaling at the higher wave is automatic via `EnemyScaler` — a player at L10 starts on tougher wave-11 enemies and harvests proportional kill cash / wave-end cash / battle-step rewards from the start.

## Lab Research — Coming Soon

One of the twelve `ResearchType` enums is explicitly v1.x-deferred via `isComingSoon = true` (RO-11 #B.2 / V1X-15b):

- `AUTO_UPGRADE_AI` — reserved for v1.x, research progress preserved

(`ENEMY_INTEL` was wired in V1X-15b — ADR-0017 — and is no longer Coming Soon: +2%/level damage plus L1/L5/L10 information overlays.)

The Labs UI renders a "COMING SOON" badge and suppresses Start / Rush controls for this row; `LabsViewModel.startResearch` has a defensive guard that early-returns + emits a snackbar if a Coming Soon type ever reaches the VM via a future entry point.
