# Plan 19 — Walking Encounters & Supply Drops

**Status:** Complete
**Dependencies:** Plan 04 (Step Counter Service)
**Layer:** `service/` + `domain/usecase/` + `presentation/`

---

## Objective

Implement the Walking Encounters system: Supply Drops generated during walks via a seeded random system, delivered as push notifications, claimable with one tap, and stored in an Unclaimed Supplies inbox (max 10). Transforms walking into a loot-driven experience.

Reference: GDD §2.3 for trigger table and drop rates.

---

## Task Breakdown

### Task 1: Supply Drop Generator

Create `domain/usecase/GenerateSupplyDrop.kt`:
- Seeded random system based on step count and time of day
- Trigger checks per step batch:
  - Every 2,000 steps: 5% chance per 100 steps after threshold → bonus Steps (50–200) or Gems (1–3)
  - Step burst (500+ in 5 min): Overdrive Charge
  - 10,000 daily milestone: Rare Card Pack or 5 Gems + 3 Power Stones
  - Random: 1% per 500 steps → random reward (Steps/Gems/Power Stones/Card Dust)
- Average rate: 2–4 drops per 10,000 steps
- Returns `SupplyDrop?` (null if no drop triggered)

---

### Task 2: Supply Drop Delivery

Update `DailyStepManager` (from Plan 04):
- After crediting steps, call `GenerateSupplyDrop` with current step totals
- If drop generated: save to `WalkingEncounterRepository` and trigger notification

---

### Task 3: Supply Drop Notifications

Create `service/SupplyDropNotificationManager.kt`:
- Rich notification with thematic message (from GDD trigger table)
- One-tap "Claim" action button
- Notification tap opens app to Unclaimed Supplies
- Uses separate notification channel (`supply_drops`)
- Respects user notification preferences

---

### Task 4: Claim Supply Drop Use Case

Create `domain/usecase/ClaimSupplyDrop.kt`:
- Marks drop as claimed in `WalkingEncounterRepository`
- Credits reward to player:
  - Steps → `PlayerRepository.addSteps()`
  - Gems → `PlayerRepository.addGems()`
  - Power Stones → `PlayerRepository.addPowerStones()`
  - Card Dust → add to player profile
  - Overdrive Charge → store for next battle
  - Card Pack → trigger pack opening flow

---

### Task 5: Unclaimed Supplies Inbox

Create `presentation/supplies/UnclaimedSuppliesScreen.kt`:
- List of unclaimed Supply Drops (max 10 stored)
- Each shows: trigger description, reward type/amount, time received
- "Claim" button per drop, "Claim All" button
- Oldest drops auto-discarded when inbox exceeds 10

Create `presentation/supplies/UnclaimedSuppliesViewModel.kt`.

---

### Task 6: Inbox Badge on Home Screen

Update `HomeScreen`:
- Badge on Unclaimed Supplies button showing count of unclaimed drops
- Navigates to `UnclaimedSuppliesScreen`

---

## File Summary

```
domain/usecase/
├── GenerateSupplyDrop.kt       (new)
└── ClaimSupplyDrop.kt          (new)

service/
└── SupplyDropNotificationManager.kt (new)

data/sensor/
└── DailyStepManager.kt         (update — trigger drop generation)

presentation/supplies/
├── UnclaimedSuppliesScreen.kt  (new)
└── UnclaimedSuppliesViewModel.kt (new)

presentation/home/
└── HomeScreen.kt               (update — inbox badge)
```

## Completion Criteria

- Supply Drops generate at correct rates (~2–4 per 10,000 steps)
- Seeded random produces consistent results for same step count + time
- Push notifications delivered with thematic messages
- One-tap claim from notification works
- Unclaimed Supplies inbox stores up to 10 drops
- All reward types credited correctly on claim
- Inbox badge shows unclaimed count on Home screen
- Drop generation works while app is backgrounded (via step service)
