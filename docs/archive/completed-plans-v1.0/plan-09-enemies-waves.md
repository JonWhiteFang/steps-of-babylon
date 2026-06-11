# Plan 09 — Battle System: Enemies & Waves

**Status:** Complete
**Dependencies:** Plan 08 (Battle Renderer)
**Layer:** `presentation/battle/` — Game entities and wave system

---

## Objective

Implement the enemy entity system (all 6 types), wave spawning with correct timing (26s spawn + 9s cooldown), enemy scaling per wave, movement toward the ziggurat, and basic collision/damage resolution. After this plan, the battle screen has functional waves of enemies attacking the ziggurat.

Reference: GDD §10 for enemy types, `docs/battle-formulas.md` for scaling.

---

## Task Breakdown

### Task 1: Enemy Entity

Create `presentation/battle/entities/EnemyEntity.kt`:
- Extends `Entity`
- Properties: `enemyType: EnemyType`, `currentHp`, `maxHp`, `speed`, `damage`, `isRanged`
- Movement: walks toward ziggurat at `speed` pixels/sec
- On reaching ziggurat (or attack range for Ranged): deals `damage` per attack interval
- Renders as colored shape based on type (distinct silhouettes per type)
- Mini health bar above enemy

---

### Task 2: Scatter Enemy Behavior

Extend `EnemyEntity` for Scatter type:
- On death: spawns 2–3 smaller enemies at current position
- Child enemies have reduced HP/damage (50% of parent)
- Children are Basic type with Scatter's speed

---

### Task 3: Ranged Enemy Behavior

Extend `EnemyEntity` for Ranged type:
- Stops at a distance from ziggurat (e.g., 60% of screen width)
- Fires projectiles at ziggurat from range
- Enemy projectile entity: moves toward ziggurat, deals damage on hit

Create `presentation/battle/entities/EnemyProjectileEntity.kt`.

---

### Task 4: Wave Spawner

Create `presentation/battle/engine/WaveSpawner.kt`:
- Manages wave lifecycle: spawn phase (26s) → cooldown (9s) → next wave
- During spawn phase: spawns enemies at screen edges at intervals
- Enemy composition per wave: mix of types based on wave number
  - Waves 1–10: mostly Basic, some Fast
  - Waves 11–30: add Tank, more Fast
  - Waves 31–50: add Ranged, Scatter
  - Every 10 waves: Boss wave
- Tracks current wave number and phase (spawning/cooldown)

---

### Task 5: Enemy Scaling

Create `presentation/battle/engine/EnemyScaler.kt`:
- Applies wave-based scaling to enemy stats:
  ```
  enemyHealth = baseHealth × typeHealthMultiplier × waveScalingFactor(wave)
  enemyDamage = baseDamage × typeDamageMultiplier × waveScalingFactor(wave)
  ```
- `waveScalingFactor`: exponential curve (e.g., `1.08^wave` or similar tunable)
- Uses `EnemyType` multipliers from domain model

---

### Task 6: Collision Detection

Create `presentation/battle/engine/CollisionSystem.kt`:
- Checks projectile ↔ enemy collisions (ziggurat projectiles hitting enemies)
- Checks enemy ↔ ziggurat collisions (melee enemies reaching the tower)
- Simple circle/rectangle overlap detection
- On projectile hit: apply damage to enemy, destroy projectile
- On enemy reaching ziggurat: enemy attacks at its attack interval

---

### Task 7: Enemy Death & Cleanup

Update `GameEngine`:
- Remove dead enemies from entity list
- Trigger cash reward on enemy kill (tracked in `RoundState.cash`)
- Boss kill grants bonus cash
- Scatter death triggers child spawn via `WaveSpawner`

---

### Task 8: Wave Counter UI

Update `BattleScreen.kt` Compose overlay:
- Display current wave number
- Display wave phase indicator (spawning vs cooldown)
- Display enemy count remaining in current wave

---

## File Summary

```
presentation/battle/
├── entities/
│   ├── EnemyEntity.kt          (new)
│   └── EnemyProjectileEntity.kt (new)
├── engine/
│   ├── WaveSpawner.kt          (new)
│   ├── EnemyScaler.kt          (new)
│   ├── CollisionSystem.kt      (new)
│   └── GameEngine.kt           (update)
└── BattleScreen.kt             (update — wave counter UI)
```

## Completion Criteria

- All 6 enemy types render with distinct visuals and correct behavior
- Waves spawn with 26s spawn phase + 9s cooldown timing
- Enemy stats scale per wave using the formula from battle-formulas.md
- Enemies move toward ziggurat and deal damage on contact (or at range for Ranged)
- Scatter enemies split on death
- Bosses spawn every 10 waves
- Projectiles from ziggurat hit and damage enemies
- Cash earned from kills updates RoundState
- Wave counter displays in Compose overlay
