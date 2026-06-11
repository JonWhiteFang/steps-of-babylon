# Plan 15 — Ultimate Weapons

**Status:** Not Started
**Dependencies:** Plan 12 (Round Lifecycle & Post-Round)
**Layer:** `presentation/` + `domain/usecase/`

---

## Objective

Implement the Ultimate Weapon system: 6 powerful activatable abilities unlocked and upgraded with Power Stones. Players select a loadout of up to 3 UWs per round, activate them manually during battle with cooldowns, and see dramatic visual effects.

Reference: GDD §7 for UW types and costs.

---

## Task Breakdown

### Task 1: Unlock & Upgrade Use Cases

Create `domain/usecase/UnlockUltimateWeapon.kt`:
- Takes `UltimateWeaponType` and current Power Stone balance
- Checks affordability against `unlockCost`
- Deducts Power Stones, creates weapon record via `UltimateWeaponRepository`

Create `domain/usecase/UpgradeUltimateWeapon.kt`:
- Increases weapon level (Power Stone cost scales per level)
- Upgrades improve effect duration, damage, or cooldown reduction

---

### Task 2: Loadout Management

Create `domain/usecase/ManageUWLoadout.kt`:
- Equip/unequip weapons (max 3 equipped)
- Validates loadout capacity via `UltimateWeaponLoadout` model
- Persists via `UltimateWeaponRepository`

---

### Task 3: UW Management Screen

Create `presentation/weapons/UltimateWeaponScreen.kt`:
- Grid of all 6 UWs: locked (grayed, shows unlock cost) or unlocked (shows level)
- Tap unlocked UW → equip/unequip toggle
- Equipped UWs shown in loadout slots (3 max)
- Upgrade button with Power Stone cost
- Accessible from Workshop or Home screen

Create `presentation/weapons/UltimateWeaponViewModel.kt`.

---

### Task 4: UW Activation in Battle

Update `GameEngine` / create `presentation/battle/engine/UltimateWeaponSystem.kt`:
- Each equipped UW has a cooldown timer
- Player taps UW button → activates effect → starts cooldown
- Effects by type:
  - `DEATH_WAVE`: damage pulse radiating outward from ziggurat, hits all on-screen enemies
  - `CHAIN_LIGHTNING`: arcing damage chaining between up to 8 enemies
  - `BLACK_HOLE`: gravity well at target point, pulls enemies in, sustained damage
  - `CHRONO_FIELD`: all enemies slowed to 10% speed for duration
  - `POISON_SWAMP`: toxic ground area dealing % max HP/sec
  - `GOLDEN_ZIGGURAT`: 5× cash + 50% damage boost for duration

---

### Task 5: UW Battle UI

Create `presentation/battle/ui/UltimateWeaponBar.kt`:
- Row of 3 UW buttons on battle screen (only equipped UWs shown)
- Each button shows: UW icon, cooldown overlay (radial sweep), ready indicator
- Tap when ready → activate
- Tap when on cooldown → show remaining time

---

### Task 6: UW Visual Effects

Update rendering in `GameEngine`:
- `DEATH_WAVE`: expanding green shockwave circle from ziggurat
- `CHAIN_LIGHTNING`: blue-white lightning arcs between enemies
- `BLACK_HOLE`: purple-black swirling vortex at target point
- `CHRONO_FIELD`: blue time-distortion overlay on entire screen
- `POISON_SWAMP`: green bubbling ground effect in target area
- `GOLDEN_ZIGGURAT`: ziggurat glows gold, coin particle rain

---

### Task 7: UW Cooldown Scaling

Define cooldown values per UW type and level:
- Base cooldowns: 45–90 seconds depending on UW power
- Level upgrades reduce cooldown by ~5% per level
- Lab Research `UW_COOLDOWN` further reduces by 3% per research level
- Surge Overdrive resets all cooldowns instantly

---

## File Summary

```
domain/usecase/
├── UnlockUltimateWeapon.kt     (new)
├── UpgradeUltimateWeapon.kt    (new)
└── ManageUWLoadout.kt          (new)

presentation/
├── weapons/
│   ├── UltimateWeaponScreen.kt (new)
│   └── UltimateWeaponViewModel.kt (new)
├── battle/
│   ├── engine/
│   │   ├── UltimateWeaponSystem.kt (new)
│   │   └── GameEngine.kt       (update)
│   ├── ui/
│   │   └── UltimateWeaponBar.kt (new)
│   └── BattleViewModel.kt      (update — UW activation)
```

## Completion Criteria

- All 6 UW types unlockable with correct Power Stone costs
- Loadout enforces max 3 equipped
- UW activation triggers correct gameplay effect
- Cooldown timers work and display correctly
- UW level upgrades improve effects
- Lab UW_COOLDOWN research reduces cooldowns
- Surge Overdrive resets all cooldowns
- Visual effects render for each UW type
- UW management screen allows unlock/upgrade/equip
