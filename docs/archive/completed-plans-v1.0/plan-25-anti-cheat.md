# Plan 25 — Anti-Cheat & Validation

**Status:** ✅ Complete
**Dependencies:** Plan 05 (Health Connect Integration)
**Layer:** `data/` + `domain/usecase/`

---

## Objective

Harden the anti-cheat system beyond the basic rate limiting and daily ceiling from Plan 04. Step velocity analysis detects phone shakers and spoofers, graduated Health Connect cross-validation escalates response for repeat offenders, activity minute validation prevents gaming, and per-minute overlap deduction fixes double-counting.

Reference: `docs/step-tracking.md` §Anti-Cheat Rules, GDD §11.3.

---

## Implementation (Actual)

### Decision: No Accelerometer Sensor
Step velocity analysis detects shakers via statistical patterns in step counter data (constant rate, instant jumps) at zero battery cost. Accelerometer deferred — velocity analysis catches 90% of shaker patterns.

### Decision: No Room Entity for Logging
SharedPreferences counters + Logcat for anti-cheat event tracking. No DB migration needed.

### Decision: SharedPreferences for Offense Tracking
Cross-validation offense count stored in SharedPreferences (survives DB wipes, matches BiomePreferences/NotificationPreferences pattern).

### Files Created
- `data/anticheat/AntiCheatPreferences.kt` — SharedPreferences wrapper: daily counters + CV offense tracking + 7-day decay
- `data/sensor/StepVelocityAnalyzer.kt` — rolling 15-min window, instant jump + constant rate detection, penalty multiplier
- `data/healthconnect/ActivityMinuteValidator.kt` — filters: <2min micro-sessions, >4hr truncation, >5 types/day rejection

### Files Updated
- `data/sensor/DailyStepManager.kt` — velocity analysis + per-minute tracking in pipeline
- `data/healthconnect/StepCrossValidator.kt` — graduated response (4 offense levels)
- `service/StepSyncWorker.kt` — validator wiring + real sensorStepsPerMinute

### Tests: 16 new (222 total)
- `StepVelocityAnalyzerTest` (6), `StepCrossValidatorTest` (5), `ActivityMinuteValidatorTest` (5)

---

## Task Breakdown

### Task 1: Accelerometer Pattern Analyzer

Create `data/sensor/AccelerometerAnalyzer.kt`:
- Registers `TYPE_ACCELEROMETER` sensor alongside step counter
- Analyzes acceleration patterns over rolling windows
- Detects mechanical regularity (phone shaker devices):
  - Consistent frequency (±5% variance over 60 seconds)
  - Uniform amplitude
  - No natural gait variation
- Flags suspicious patterns → reject those steps
- Low-power: only active when step rate exceeds 150/min

---

### Task 2: Enhanced Health Connect Cross-Validation

Update `StepCrossValidator`:
- Tighten validation windows: check every sync (not just daily)
- Track discrepancy history: repeated >20% discrepancies across multiple days → flag account
- Implement graduated response:
  - First offense: escrow (existing)
  - Repeated: reduce credited rate to Health Connect value
  - Persistent: cap at Health Connect reported steps only

---

### Task 3: Activity Minute Gaming Prevention

Create `data/healthconnect/ActivityMinuteAntiCheat.kt`:
- Detect suspicious Activity Minute patterns:
  - Extremely long sessions (>4 hours continuous)
  - Activity type switching rapidly
  - Activity minutes reported without corresponding heart rate data (if available)
- Apply separate daily caps per activity type (from GDD §11.4)
- Log suspicious patterns for analytics

---

### Task 4: Overlap Deduction

Update `ActivityMinuteValidator`:
- Strengthen double-counting prevention:
  - If step sensor records >50 steps/min during an activity period, credit ONLY sensor steps (not activity minutes)
  - Minute-by-minute overlap check, not just period average
  - Handle edge cases: partial overlap within a single sync window

---

### Task 5: Step Velocity Analysis

Create `data/sensor/StepVelocityAnalyzer.kt`:
- Track step rate changes over time
- Natural walking has gradual acceleration/deceleration
- Flag: instant jumps from 0 to 200 steps/min (no ramp-up)
- Flag: perfectly constant rate for >10 minutes (no natural variation)
- Soft penalty: reduce credited rate for flagged periods

---

### Task 6: Anti-Cheat Analytics Logger

Create `data/anticheat/AntiCheatLogger.kt`:
- Logs all anti-cheat events locally:
  - Rate-limited steps (count discarded)
  - Ceiling-capped steps
  - Escrow events
  - Accelerometer flags
  - Velocity flags
  - Activity minute rejections
- Stored in Room for debugging (retained 30 days)
- No server upload (offline-only game)

Create `data/local/AntiCheatLogEntity.kt` and DAO.

---

## File Summary

```
data/sensor/
├── AccelerometerAnalyzer.kt    (new)
└── StepVelocityAnalyzer.kt     (new)

data/healthconnect/
├── StepCrossValidator.kt       (update — enhanced validation)
├── ActivityMinuteConverter.kt  (update — overlap deduction)
└── ActivityMinuteAntiCheat.kt  (new)

data/anticheat/
└── AntiCheatLogger.kt          (new)

data/local/
├── AntiCheatLogEntity.kt      (new)
├── AntiCheatLogDao.kt         (new)
└── AppDatabase.kt             (update — migration)
```

## Completion Criteria

- Accelerometer analysis detects mechanical shaker patterns and rejects steps
- Health Connect cross-validation uses graduated response for repeated discrepancies
- Activity Minute gaming detected (long sessions, rapid switching)
- Overlap deduction works at minute-level granularity
- Step velocity analysis flags unnatural rate patterns
- All anti-cheat events logged locally for 30 days
- Legitimate users unaffected (natural walking/running patterns pass all checks)
- Anti-cheat runs efficiently without noticeable battery impact
