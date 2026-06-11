# Plan 22 — Stats & History Screen

**Status:** Complete
**Dependencies:** Plan 06 (Home Screen & Navigation) ✓
**Layer:** `presentation/`

---

## Objective

Build the Stats & History screen showing walking history charts (daily/weekly/monthly), battle statistics, all-time stats, and a breakdown of Steps vs Activity Minute contributions. Gives players a comprehensive view of their physical and in-game progress.

Reference: GDD §12.1 for screen description.

---

## Task Breakdown

### Task 1: StatsViewModel

Create `presentation/stats/StatsViewModel.kt`:
- `@HiltViewModel` injecting `StepRepository`, `PlayerRepository`, `WorkshopRepository`
- Exposes `StateFlow<StatsUiState>`:
  - `todaySteps: Long`, `weekSteps: Long`, `monthSteps: Long`, `allTimeSteps: Long`
  - `todayActivityMinutes: Map<String, Int>`
  - `todayStepEquivalents: Long`
  - `dailyHistory: List<DailyStepSummary>` (last 30 days)
  - `bestWavePerTier: Map<Int, Int>`
  - `currentTier: Int`
  - `totalWorkshopLevel: Int` (sum of all upgrade levels)
  - `totalRoundsPlayed: Long`
  - `totalEnemiesKilled: Long`
- Loads history from `StepRepository.getHistory()` for date ranges

---

### Task 2: Walking History Chart

Create `presentation/stats/WalkingHistoryChart.kt`:
- Bar chart composable showing daily steps
- Toggle between: last 7 days, last 30 days, last 12 weeks (weekly totals)
- Each bar split into: sensor steps (primary color) + step-equivalents (secondary color)
- Daily ceiling line at 50,000
- Tap bar for detail tooltip

---

### Task 3: Battle Stats Section

Create `presentation/stats/BattleStatsSection.kt`:
- Best wave per tier (list)
- Total rounds played
- Total enemies killed
- Total cash earned (all-time)
- Favorite tier (most rounds played)

---

### Task 4: All-Time Stats Section

Create `presentation/stats/AllTimeStatsSection.kt`:
- Total steps walked (lifetime)
- Total step-equivalents from activities
- Total Steps spent on Workshop
- Total Gems earned / spent
- Total Power Stones earned / spent
- Days active
- Average daily steps

---

### Task 5: Stats Screen UI

Create `presentation/stats/StatsScreen.kt`:
- Scrollable screen with sections:
  1. Walking History Chart (with period toggle)
  2. Today's Activity Breakdown (steps + activity minutes)
  3. Battle Stats
  4. All-Time Stats
- Pull-to-refresh

---

### Task 6: Battle Stats Persistence

Create `data/local/BattleStatsEntity.kt`:
- `id: Int` (PK, always 1)
- `totalRoundsPlayed: Long`
- `totalEnemiesKilled: Long`
- `totalCashEarned: Long`
- `roundsPerTier: String` (JSON Map<Int, Int>)

Create `data/local/BattleStatsDao.kt`.

Update battle round end flow to increment stats.

---

## File Summary

```
presentation/stats/
├── StatsViewModel.kt           (new)
├── StatsScreen.kt              (new)
├── WalkingHistoryChart.kt      (new)
├── BattleStatsSection.kt       (new)
├── AllTimeStatsSection.kt      (new)
└── StatsUiState.kt             (new)

data/local/
├── BattleStatsEntity.kt       (new)
├── BattleStatsDao.kt          (new)
└── AppDatabase.kt             (update — migration)

di/
└── DatabaseModule.kt          (update)
```

## Completion Criteria

- Walking history chart renders daily/weekly/monthly views
- Steps and step-equivalents shown separately in chart bars
- Battle stats display best wave per tier and totals
- All-time stats show lifetime aggregates
- Activity minute breakdown visible for current day
- Battle stats increment correctly after each round
- Stats screen loads data reactively from Room Flows
