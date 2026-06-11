# Plan 14 — Step Overdrive

**Status:** Not Started
**Dependencies:** Plan 12 (Round Lifecycle & Post-Round)
**Layer:** `presentation/battle/` + `domain/usecase/`

---

## Objective

Implement the Step Overdrive system: a mid-battle mechanic where players sacrifice permanent Steps for a powerful 60-second combat boost. Four overdrive types, once per round, with visual aura effects on the ziggurat.

Reference: GDD §5.1 for overdrive types and costs.

---

## Task Breakdown

### Task 1: Activate Overdrive Use Case

Create `domain/usecase/ActivateOverdrive.kt`:
- Takes `OverdriveType` and current `PlayerWallet`
- Checks: not already used this round, sufficient Step balance
- Deducts Step cost from `PlayerRepository`
- Returns success/failure with reason

---

### Task 2: Overdrive Effect Application

Update `GameEngine` / create `presentation/battle/engine/OverdriveSystem.kt`:
- Applies overdrive effects for 60 seconds:
  - `ASSAULT`: 2× attack speed + 1.5× damage (multiply resolved stats)
  - `FORTRESS`: 2× health regen + 50% damage reduction (additive to defense %)
  - `FORTUNE`: 3× cash earned from all sources
  - `SURGE`: reset all Ultimate Weapon cooldowns instantly
- Timer countdown (60s at game speed)
- Effects stack multiplicatively with existing stats
- On expiry: revert to normal stats

---

### Task 3: Overdrive UI Button

Create `presentation/battle/ui/OverdriveButton.kt`:
- Compose overlay button on battle screen
- Shows Step balance as cost reminder
- Tap → opens overdrive type selection popup
- Four options with name, cost, and effect description
- Disabled after use (once per round)
- Disabled if insufficient Steps for any option

---

### Task 4: Overdrive Selection Popup

Create `presentation/battle/ui/OverdriveSelectionDialog.kt`:
- Modal dialog with 4 overdrive options
- Each shows: name, Step cost, effect description, duration
- Unaffordable options grayed out
- Confirm button deducts Steps and activates
- Cancel to dismiss

---

### Task 5: Visual Aura Effects

Update `ZigguratEntity`:
- During active overdrive: render pulsing aura around ziggurat
- Color by type: red (Assault), blue (Fortress), gold (Fortune), purple (Surge)
- Aura fades as timer approaches 0
- Timer bar or countdown visible near ziggurat

---

### Task 6: Free Overdrive Charge

Update `OverdriveSystem`:
- Support free overdrive charges from Walking Encounter Supply Drops
- If player has a free charge: overdrive costs 0 Steps
- Free charge consumed on use
- Track free charges in `RoundState` (passed from ViewModel)

---

## File Summary

```
domain/usecase/
└── ActivateOverdrive.kt        (new)

presentation/battle/
├── engine/
│   ├── OverdriveSystem.kt      (new)
│   └── GameEngine.kt           (update)
├── entities/
│   └── ZigguratEntity.kt       (update — aura rendering)
├── ui/
│   ├── OverdriveButton.kt      (new)
│   └── OverdriveSelectionDialog.kt (new)
└── BattleViewModel.kt          (update — overdrive activation)
```

## Completion Criteria

- All 4 overdrive types apply correct effects for 60 seconds
- Step cost deducted from permanent balance via PlayerRepository
- Once-per-round limit enforced
- Effects stack multiplicatively with Workshop and in-round upgrades
- Visual aura renders with correct color per type
- Timer countdown visible during active overdrive
- Free overdrive charges work (0 Step cost)
- Overdrive button disabled after use or if unaffordable
