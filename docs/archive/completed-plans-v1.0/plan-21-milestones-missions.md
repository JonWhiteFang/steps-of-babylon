# Plan 21 — Milestones & Daily Missions

**Status:** Complete
**Dependencies:** Plan 20 (Power Stone & Gem Economy) ✓
**Layer:** `domain/usecase/` + `data/local/` + `presentation/`

---

## Objective

Implement walking milestones (lifetime step achievements with one-time rewards) and daily missions (3 random missions refreshing at midnight with Gem/PS rewards). These systems provide short-term and long-term goals beyond the core battle loop.

Reference: GDD §16 for milestones and missions.

---

## Task Breakdown

### Task 1: Milestone Definitions

Create `domain/model/Milestone.kt`:
- Enum or sealed class with all milestones:
  - First Steps: 1,000 total steps → 10 Gems + Tutorial Card Pack
  - Morning Jogger: 10,000 → 25 Gems
  - Trail Blazer: 100,000 → 50 Gems + Rare Card Pack
  - Marathon Walker: 500,000 → 100 Gems + Epic Card Pack + Garden Ziggurat Skin
  - Iron Soles: 1,000,000 → 200 Gems + 50 PS + Lapis Lazuli Skin
  - Globe Trotter: 5,000,000 → 500 Gems + Sandals of Gilgamesh cosmetic
- Each stores: `name`, `requiredSteps`, `rewards`

---

### Task 2: Milestone Tracker

Create `domain/usecase/CheckMilestones.kt`:
- Given `totalStepsEarned`, checks which milestones are newly achieved
- Returns list of newly unlocked milestones
- Called after step balance updates

Create `data/local/MilestoneEntity.kt`:
- `milestoneId: String` (PK)
- `claimed: Boolean`
- `claimedAt: Long?`

Create `data/local/MilestoneDao.kt`.

---

### Task 3: Claim Milestone Use Case

Create `domain/usecase/ClaimMilestone.kt`:
- Awards all rewards for a milestone (Gems, PS, Card Packs, cosmetics)
- Marks milestone as claimed
- Triggers notification/celebration

---

### Task 4: Daily Mission Definitions

Create `domain/model/DailyMission.kt`:
- Data class: `type`, `description`, `target`, `progress`, `reward`
- Mission types:
  - Walking: "Walk X steps" (5,000 or 12,000)
  - Battle: "Reach Wave X" (30) or "Kill X enemies" (500)
  - Upgrade: "Spend X Steps on Workshop" (5,000) or "Complete a Lab research"
- Rewards: 2–10 Gems, sometimes + Power Stones

---

### Task 5: Daily Mission Generator

Create `domain/usecase/GenerateDailyMissions.kt`:
- Generates 3 random missions at midnight (or on first app open of the day)
- One from each category: Walking, Battle, Upgrade
- Seeded by date for consistency
- Stores in Room

Create `data/local/DailyMissionEntity.kt`:
- `id: Int` (PK, autoGenerate)
- `date: String`
- `missionType: String`
- `description: String`
- `target: Int`
- `progress: Int`
- `rewardGems: Int`
- `rewardPowerStones: Int`
- `completed: Boolean`
- `claimed: Boolean`

Create `data/local/DailyMissionDao.kt`.

---

### Task 6: Mission Progress Tracking

Create `domain/usecase/UpdateMissionProgress.kt`:
- Called from various game events:
  - Step credited → update walking mission progress
  - Wave reached → update battle wave mission
  - Enemy killed → update battle kill mission
  - Steps spent on Workshop → update upgrade mission
  - Lab research completed → update upgrade mission
- Auto-marks mission as completed when target reached

---

### Task 7: Missions & Milestones Screen

Create `presentation/missions/MissionsScreen.kt`:
- Two sections: Daily Missions (top) and Milestones (bottom)
- Daily missions: 3 cards with progress bars, "Claim" button when complete
- Milestones: list with progress toward next milestone, claimed milestones checked off
- Refresh timer showing time until midnight reset

Create `presentation/missions/MissionsViewModel.kt`.

---

### Task 8: Database & Module Updates

Update `AppDatabase`:
- Add `MilestoneEntity`, `DailyMissionEntity`
- New DAOs, migration

Update `DatabaseModule`.

---

## File Summary

```
domain/
├── model/
│   ├── Milestone.kt            (new)
│   └── DailyMission.kt        (new)
└── usecase/
    ├── CheckMilestones.kt      (new)
    ├── ClaimMilestone.kt       (new)
    ├── GenerateDailyMissions.kt (new)
    └── UpdateMissionProgress.kt (new)

data/local/
├── MilestoneEntity.kt         (new)
├── MilestoneDao.kt            (new)
├── DailyMissionEntity.kt      (new)
├── DailyMissionDao.kt         (new)
└── AppDatabase.kt             (update — migration)

presentation/missions/
├── MissionsScreen.kt          (new)
└── MissionsViewModel.kt       (new)

di/
└── DatabaseModule.kt          (update)
```

## Completion Criteria

- All 6 walking milestones trigger at correct step thresholds
- Milestone rewards credited correctly (Gems, PS, Card Packs, cosmetics)
- Milestones are one-time only (claimed flag persisted)
- 3 daily missions generated at midnight with correct variety
- Mission progress updates from game events in real-time
- Completed missions claimable for Gem/PS rewards
- Missions screen shows progress bars and claim buttons
- Midnight refresh generates new missions
