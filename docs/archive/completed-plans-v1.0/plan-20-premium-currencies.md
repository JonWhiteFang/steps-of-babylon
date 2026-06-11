# Plan 20 — Power Stone & Gem Economy

**Status:** Complete
**Dependencies:** Plan 04 (Step Counter Service)
**Layer:** `domain/usecase/` + `data/local/` + `presentation/`

---

## Objective

Implement the premium currency earning systems: weekly step challenges for Power Stones, wave milestone Power Stone rewards, daily login Power Stones, daily login streak Gems, and long-distance walking Gem bonuses. These are the free-to-play paths for earning Gems and Power Stones.

Reference: GDD §7.1 for Power Stone acquisition, §16 for milestones.

---

## Task Breakdown

### Task 1: Weekly Step Challenge Tracker

Create `domain/usecase/TrackWeeklyChallenge.kt`:
- Tracks total steps for the current week (Monday–Sunday)
- Power Stone rewards at thresholds:
  - 50,000 steps/week → 10 Power Stones
  - 75,000 steps/week → 20 Power Stones (total, not additional)
  - 100,000+ steps/week → 35 Power Stones (total)
- Resets weekly
- Persists progress and claimed thresholds

Create `data/local/WeeklyChallengeEntity.kt`:
- `weekStartDate: String` (PK, ISO date of Monday)
- `totalSteps: Long`
- `claimedTier: Int` (0, 1, 2, or 3)

Create `data/local/WeeklyChallengeDao.kt`.

---

### Task 2: Daily Login Tracker

Create `domain/usecase/TrackDailyLogin.kt`:
- Records daily login
- Awards 1 Power Stone per day (if player also walks 1,000+ steps)
- Tracks login streak for Gem rewards:
  - Day 1: 1 Gem, Day 2: 2 Gems, ... Day 7: 5 Gems, then resets
- Streak breaks if a day is missed

Create `data/local/DailyLoginEntity.kt`:
- `date: String` (PK)
- `stepsWalked: Long`
- `powerStoneClaimed: Boolean`
- `gemsClaimed: Boolean`

Create `data/local/LoginStreakEntity.kt`:
- `id: Int` (PK, always 1)
- `currentStreak: Int`
- `lastLoginDate: String`

---

### Task 3: Wave Milestone Power Stones

Create `domain/usecase/AwardWaveMilestoneReward.kt`:
- New personal-best wave records award 2–5 Power Stones
- Scaling: every 10 waves of improvement = 2 PS, every 25 = 5 PS
- Called from `UpdateBestWave` (Plan 12) when a new record is set

---

### Task 4: Premium Currency Dashboard

Create `presentation/economy/CurrencyDashboardScreen.kt`:
- Weekly challenge progress bar with threshold markers
- Daily login streak display with upcoming rewards
- Power Stone and Gem balance
- History of recent earnings

Create `presentation/economy/CurrencyDashboardViewModel.kt`.

---

### Task 5: Database & Module Updates

Update `AppDatabase`:
- Add `WeeklyChallengeEntity`, `DailyLoginEntity`, `LoginStreakEntity`
- New DAOs
- Migration

Update `DatabaseModule`:
- Provide new DAOs

---

## File Summary

```
domain/usecase/
├── TrackWeeklyChallenge.kt     (new)
├── TrackDailyLogin.kt          (new)
└── AwardWaveMilestoneReward.kt (new)

data/local/
├── WeeklyChallengeEntity.kt    (new)
├── WeeklyChallengeDao.kt       (new)
├── DailyLoginEntity.kt         (new)
├── DailyLoginDao.kt            (new)
├── LoginStreakEntity.kt        (new)
└── AppDatabase.kt              (update — new entities, migration)

presentation/economy/
├── CurrencyDashboardScreen.kt  (new)
└── CurrencyDashboardViewModel.kt (new)

di/
└── DatabaseModule.kt           (update — new DAOs)
```

## Completion Criteria

- Weekly challenge tracks steps Mon–Sun with correct PS thresholds (10/20/35)
- Weekly challenge resets each Monday
- Daily login awards 1 PS when 1,000+ steps walked
- Login streak awards scaling Gems (1–5 per day, 7-day cycle)
- Streak resets on missed day
- Wave milestone PS awards trigger on new personal bests
- Currency dashboard shows progress and balances
- All rewards persist correctly to Room
