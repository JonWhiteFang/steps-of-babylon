# Plan 10b — Advanced Combat Mechanics (Orbs, Multishot, Bounce Shot)

**Status:** Complete
**Dependencies:** Plan 10 (Stats & Combat)
**Layer:** `presentation/battle/`

---

## Objective

Wire the three advanced combat mechanics whose stats are already computed in `ResolvedStats` but not yet connected to gameplay: Orbs (orbiting projectiles), Multishot (fire at multiple targets), and Bounce Shot (projectile chaining). These are independent of the cash economy and can be implemented anytime after Plan 10.

---

## Task Breakdown

### Task 1: Orb System

Create `presentation/battle/entities/OrbEntity.kt`:
- Orbiting projectiles circling the ziggurat
- Count = `resolvedStats.orbCount` (cap 6)
- Each orb orbits at a fixed radius, evenly spaced around the ziggurat
- Damages nearby enemies on contact (uses base damage scaled by orb level)
- Orbit radius and angular speed configurable

Update `GameEngine`:
- On init (or stats update): spawn/despawn OrbEntities to match `resolvedStats.orbCount`
- Orbs check collision with enemies each frame

---

### Task 2: Multishot

Create `presentation/battle/engine/TargetingSystem.kt`:
- `findNearestEnemies(n: Int): List<EnemyEntity>` — returns the N nearest alive enemies within range

Update `ZigguratEntity`:
- On attack: fire at `resolvedStats.multishotTargets` enemies simultaneously (cap 5)
- Uses TargetingSystem to find targets
- Each projectile is independent (can hit different enemies)

---

### Task 3: Bounce Shot

Update `ProjectileEntity` or create `BouncingProjectileEntity`:
- On hitting an enemy: if `resolvedStats.bounceCount > 0`, find the nearest enemy to the hit target (excluding already-hit enemies)
- Chain to up to `bounceCount` additional enemies (cap 4)
- Each bounce deals full damage
- Projectile visually arcs to the next target

Update `GameEngine.onProjectileHitEnemy()`:
- After applying damage, check bounce count and spawn continuation projectile if applicable

---

## File Summary

```
presentation/battle/
├── entities/
│   └── OrbEntity.kt               (new)
├── engine/
│   ├── TargetingSystem.kt          (new)
│   ├── GameEngine.kt               (update — orb management, bounce logic)
│   └── CollisionSystem.kt          (update — orb collision)
├── entities/
│   ├── ZigguratEntity.kt           (update — multishot targeting)
│   └── ProjectileEntity.kt         (update — bounce behavior)
```

## Completion Criteria

- Orbs orbit the ziggurat and damage nearby enemies (count matches orbCount)
- Multishot fires at N targets simultaneously (N matches multishotTargets)
- Bounce Shot chains projectiles between enemies (count matches bounceCount)
- All three mechanics respond to in-round upgrade level changes (re-resolve)
- Visual feedback is clear for each mechanic
