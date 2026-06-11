# Plan 16 — Labs System

**Status:** Complete
**Dependencies:** Plan 07 (Workshop Screen & Upgrades)
**Layer:** `presentation/` + `domain/usecase/`

---

## Objective

Implement the Labs research system: long-term research projects initiated with Steps and completed over real time. Players start with 1 Lab Slot (up to 4 via Gems). Includes Gem rush to instantly complete research. 10 research types with scaling costs and durations.

Reference: GDD §8 for research types and configs.

---

## Task Breakdown

### Task 1: Start Research Use Case

Create `domain/usecase/StartResearch.kt`:
- Takes `ResearchType`, current level, and `PlayerWallet`
- Calculates Step cost: `baseCostSteps × scalingFactor^level`
- Calculates completion time: `baseTimeHours × timeScalingFactor^level`
- Checks: sufficient Steps, lab slot available, not already researching this type
- Deducts Steps, sets `startedAt` and `completesAt` in `LabRepository`

---

### Task 2: Complete Research Use Case

Create `domain/usecase/CompleteResearch.kt`:
- Checks if `completesAt` has passed (current time >= completesAt)
- Increments research level
- Clears `startedAt` and `completesAt` (frees the slot)
- Respects `maxLevel` cap

---

### Task 3: Rush Research Use Case

Create `domain/usecase/RushResearch.kt`:
- Instantly completes active research by spending Gems
- Gem cost scales with remaining time: `50–200 Gems` range
- Deducts Gems via `PlayerRepository`
- Calls `CompleteResearch` internally

---

### Task 4: Lab Slot Management

Create `domain/usecase/UnlockLabSlot.kt`:
- Players start with 1 slot, can unlock up to 4
- Each additional slot costs 200 Gems
- Track slot count on `PlayerProfile` (add `labSlotCount: Int` field)

---

### Task 5: Research Timer Background Check

Create `domain/usecase/CheckResearchCompletion.kt`:
- Called on app launch and periodically
- Scans all active research for completed timers
- Auto-completes any research where `completesAt <= now`
- Works even if app was closed during research

---

### Task 6: LabsViewModel

Create `presentation/labs/LabsViewModel.kt`:
- `@HiltViewModel` injecting `LabRepository`, `PlayerRepository`, use cases
- Exposes `StateFlow<LabsUiState>`:
  - `researchList: List<ResearchDisplayInfo>` (type, level, active/idle, time remaining)
  - `activeSlots: Int` / `totalSlots: Int`
  - `stepBalance: Long`, `gems: Long`
- Actions: `startResearch()`, `rushResearch()`, `unlockSlot()`
- Periodic timer updates for active research countdown

---

### Task 7: Labs Screen UI

Create `presentation/labs/LabsScreen.kt`:
- List of all 10 research types
- Each row shows: name, current level, effect description, cost to start next level
- Active research shows: progress bar, time remaining, "Rush" button with Gem cost
- Idle research shows: "Start" button (if slot available and affordable)
- Slot indicator: "Slots: 2/4" with unlock button
- Max-level research shown as completed

---

### Task 8: Database Migration

Update `PlayerProfileEntity`:
- Add `labSlotCount: Int` (default 1)
- Migration v3 → v4 (or appropriate version)

---

## File Summary

```
domain/usecase/
├── StartResearch.kt            (new)
├── CompleteResearch.kt         (new)
├── RushResearch.kt             (new)
├── UnlockLabSlot.kt            (new)
└── CheckResearchCompletion.kt  (new)

presentation/labs/
├── LabsViewModel.kt            (new)
├── LabsScreen.kt               (new)
└── LabsUiState.kt              (new)

data/local/
├── PlayerProfileEntity.kt      (update — add labSlotCount)
└── AppDatabase.kt              (update — migration)
```

## Completion Criteria

- All 10 research types startable with correct Step costs
- Research completes after real-time duration (works while app is closed)
- Lab slots limit concurrent research (1–4 slots)
- Gem rush instantly completes research at correct Gem cost
- Slot unlock costs 200 Gems each
- Research level caps enforced (maxLevel per type)
- Cost and time scale per level
- Labs screen shows real-time countdown for active research
- Auto-completion check runs on app launch
