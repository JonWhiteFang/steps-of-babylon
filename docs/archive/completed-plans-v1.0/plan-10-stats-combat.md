# Plan 10 — Battle System: Stats & Combat

**Status:** Complete
**Dependencies:** Plan 09 (Enemies & Waves)
**Layer:** `domain/usecase/` + `presentation/battle/engine/`

---

## Objective

Implement the full stats resolution engine that combines Workshop (permanent) and in-round (temporary) upgrades multiplicatively. Wire up all combat mechanics: crit system, knockback, lifesteal, thorn damage, orbs, bounce shot, multishot, damage/meter, defense calculations, health regen, and death defy.

Reference: `docs/battle-formulas.md` for all formulas.

---

## Task Breakdown

### Task 1: Stats Resolution Engine

Create `domain/usecase/ResolveStats.kt`:
- Takes Workshop upgrade levels (`Map<UpgradeType, Int>`) and in-round upgrade levels (`Map<UpgradeType, Int>`)
- Computes effective stats using multiplicative stacking:
  ```
  effectiveStat = baseStat × (1 + workshopBonus) × (1 + inRoundBonus)
  ```
- Returns `ResolvedStats` data class with all computed values:
  - `damage`, `attackSpeed`, `critChance`, `critMultiplier`, `range`
  - `maxHealth`, `healthRegen`, `defensePercent`, `defenseAbsolute`
  - `knockbackForce`, `thornDamage`, `lifestealPercent`
  - `multishotTargets`, `bounceCount`, `damagePerMeterBonus`
  - `orbCount`, `deathDefyChance`

Create `domain/model/ResolvedStats.kt`.

---

### Task 2: Damage Calculator

Create `domain/usecase/CalculateDamage.kt`:
- Input: `ResolvedStats`, distance to enemy
- Computes raw damage with damage/meter bonus
- Rolls crit chance → applies crit multiplier
- Returns `DamageResult(amount: Double, isCrit: Boolean)`

---

### Task 3: Defense Calculator

Create `domain/usecase/CalculateDefense.kt`:
- Input: incoming damage, `ResolvedStats` (for defense % and absolute)
- Applies damage reduction (cap 75%) then flat block
- Returns actual damage taken: `max(0, incoming × (1 - reduction) - flatBlock)`

---

### Task 4: Knockback System

Update `CollisionSystem` / create `presentation/battle/engine/KnockbackSystem.kt`:
- On projectile hit: push enemy back by `knockbackForce` pixels
- Knockback scales with upgrade level
- Enemies resume walking after knockback

---

### Task 5: Lifesteal System

Update ziggurat damage application:
- On dealing damage: heal ziggurat by `finalDamage × lifestealPercent`
- Cap at 15% of damage dealt
- Heal cannot exceed max HP

---

### Task 6: Thorn Damage System

Update enemy attack on ziggurat:
- When enemy deals damage to ziggurat: reflect `incomingDamage × thornLevel × 0.01` back to enemy
- Can kill enemies via reflected damage

---

### Task 7: Orb System

Create `presentation/battle/entities/OrbEntity.kt`:
- Orbiting projectiles circling the ziggurat
- Count = orb upgrade level (cap 6)
- Damage nearby enemies on contact
- Orbit radius and speed configurable

---

### Task 8: Multishot & Bounce Shot

Update ziggurat attack logic:
- Multishot: fire at `1 + floor(level/20)` targets simultaneously (cap 5)
- Bounce Shot: projectile chains to `floor(level/15)` additional enemies (cap 4)
- Each bounce deals full damage

Create `presentation/battle/engine/TargetingSystem.kt`:
- Finds nearest N enemies for multishot
- Finds nearest enemy to bounce target for bounce shot

---

### Task 9: Health Regen

Update `ZigguratEntity`:
- Regenerate HP per second based on `regenPerSecond` from resolved stats
- Applied each game tick: `hp += regenPerSecond × deltaTime`

---

### Task 10: Death Defy

Update ziggurat damage handling:
- On lethal hit: roll against `deathDefyChance`
- If success: survive at 1 HP instead of dying
- Checked per hit (not per frame)

---

### Task 11: Wire Stats to Battle

Update `BattleViewModel` and `GameEngine`:
- On round start: resolve stats from Workshop levels
- Pass `ResolvedStats` to `ZigguratEntity` and combat systems
- In-round upgrades update resolved stats dynamically (re-resolve on purchase)

---

## File Summary

```
domain/
├── model/
│   └── ResolvedStats.kt        (new)
└── usecase/
    ├── ResolveStats.kt         (new)
    ├── CalculateDamage.kt      (new)
    └── CalculateDefense.kt     (new)

presentation/battle/
├── engine/
│   ├── KnockbackSystem.kt     (new)
│   ├── TargetingSystem.kt      (new)
│   ├── CollisionSystem.kt      (update)
│   └── GameEngine.kt           (update)
├── entities/
│   ├── ZigguratEntity.kt       (update)
│   ├── OrbEntity.kt            (new)
│   └── EnemyEntity.kt          (update)
└── BattleViewModel.kt          (update)
```

## Completion Criteria

- Stats resolution correctly combines Workshop × In-Round multiplicatively
- Crit system rolls correctly with proper chance and multiplier
- Damage/meter bonus increases damage based on enemy distance
- Defense % (cap 75%) and absolute block reduce incoming damage
- Knockback pushes enemies back on hit
- Lifesteal heals ziggurat (cap 15%)
- Thorn damage reflects to attacking enemies
- Orbs orbit and damage nearby enemies (cap 6)
- Multishot hits multiple targets (cap 5)
- Bounce shot chains between enemies (cap 4)
- Health regen ticks per second
- Death defy prevents lethal hits (cap 50% chance)
- All formulas match `docs/battle-formulas.md`
