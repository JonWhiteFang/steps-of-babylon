# Plan 05 — Health Connect Integration

**Status:** Complete
**Dependencies:** Plan 04 (Step Counter Service)
**Layer:** `data/healthconnect/`

> **Note:** This plan was originally titled "Google Fit Integration" but was implemented using Health Connect instead. Google Fit APIs were deprecated and shutting down in 2026. See ADR-0002 for the decision record.

---

## Objective

Integrate Health Connect SDK for step cross-validation, gap-filling when the foreground service is killed, and Activity Minute Parity (crediting indoor workouts as step-equivalents).

---

## What Was Built

### Health Connect Client & Step Reader
- `data/healthconnect/HealthConnectClientWrapper.kt` — client setup, availability check, permissions
- `data/healthconnect/HealthConnectStepReader.kt` — aggregated daily step reading via `aggregate()`

### Cross-Validation & Escrow
- `data/healthconnect/StepCrossValidator.kt` — compares sensor vs HC steps; >20% discrepancy → escrow
- Escrow lifecycle: 3 sync attempts to reconcile, then discard
- Added `escrowSteps` and `escrowSyncCount` to `DailyStepRecordEntity`

### Gap-Filling
- `data/healthconnect/StepGapFiller.kt` — recovers missed steps from HC when service was killed

### Activity Minute Parity
- `data/healthconnect/ExerciseSessionReader.kt` — reads exercise sessions from HC
- `data/healthconnect/ActivityMinuteConverter.kt` — conversion table with per-activity caps + double-counting prevention

### Integration
- `di/HealthConnectModule.kt` — organizational Hilt module
- `presentation/HealthConnectPermissionActivity.kt` — privacy policy stub for HC
- Updated `StepSyncWorker` — integrated gap-fill, cross-validation, activity minutes
- Updated `MainActivity` — HC permission request via PermissionController
- Updated `AndroidManifest.xml` — HC permissions, privacy policy activity

## File Summary

```
data/healthconnect/
├── HealthConnectClientWrapper.kt   # Client setup, availability, permissions
├── HealthConnectStepReader.kt      # Aggregated daily step reading
├── StepCrossValidator.kt           # Cross-validation, escrow system
├── StepGapFiller.kt                # Gap-filling from HC
├── ExerciseSessionReader.kt        # Exercise session reading
└── ActivityMinuteConverter.kt      # Activity minute → step-equivalent conversion

data/local/
├── DailyStepRecordEntity.kt        # Updated: healthConnectSteps, escrowSteps, escrowSyncCount
└── DailyStepDao.kt                 # Updated: clearEscrow query

di/
└── HealthConnectModule.kt          # Hilt module

presentation/
└── HealthConnectPermissionActivity.kt  # Privacy policy stub

service/
└── StepSyncWorker.kt               # Updated: HC gap-fill + cross-validation + activity minutes
```

## Known Limitations

- `StepSyncWorker` passes `dailyStepManager.getSensorStepsPerMinute()` to `ActivityMinuteConverter` (per-minute tracking implemented in Plan 25)
