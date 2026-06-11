# Plan 12 — Round Lifecycle & Post-Round

**Status:** Complete
**Dependencies:** Plan 11 (In-Round Upgrades & Cash Economy)
**Layer:** `presentation/battle/` + `domain/usecase/`

---

## Objective

Implement the full round lifecycle: start flow, wave progression, speed controls (1x/2x/4x), round end (ziggurat death), and the post-round summary screen showing wave record, milestone rewards, and a return-to-Workshop path. After this plan, a complete battle round is playable from start to finish.

Reference: GDD §2.2 for battle loop, §6 for tier progression.

---

## Task Breakdown

### Task 1: Round Start Flow

Update `BattleViewModel`:
- `startRound()`:
  - Load Workshop levels from `WorkshopRepository`
  - Load player tier from `PlayerRepository`
  - Create fresh `RoundState` (wave 1, cash 0, no temp upgrades, full HP)
  - Resolve initial stats
  - Initialize `GameEngine` with ziggurat and wave spawner
  - Start game loop

---

### Task 2: Wave Progression

Update `WaveSpawner` and `GameEngine`:
- Track wave state machine: `SPAWNING` → `COOLDOWN` → `SPAWNING` (next wave)
- Increment wave counter on cooldown start
- Apply interest on cash between waves
- Check if all enemies in wave are dead before cooldown ends (early advance option)

---

### Task 3: Speed Controls

Update `BattleScreen.kt` and `GameLoopThread`:
- Three speed buttons: 1x, 2x, 4x
- Active speed highlighted
- Speed multiplier affects game tick rate (not render rate)
- Persist selected speed preference across rounds (in-memory or DataStore)

---

### Task 4: Round End Detection

Update `GameEngine`:
- Round ends when `ZigguratEntity.currentHp <= 0` (and death defy fails)
- Freeze game loop on round end
- Capture final state: wave reached, cash earned, enemies killed
- Notify `BattleViewModel` of round end

---

### Task 5: Post-Round Summary Screen

Create `presentation/battle/PostRoundScreen.kt`:
- Displays: wave reached, enemies killed, cash earned (total), time survived
- Compares wave reached to personal best for current tier
- If new best: highlight with celebration animation
- Shows milestone rewards earned (if any — placeholder until Plan 21)
- "Return to Workshop" button → navigates to Workshop
- "Play Again" button → starts new round

---

### Task 6: Best Wave Persistence

Create `domain/usecase/UpdateBestWave.kt`:
- Compares wave reached to `bestWavePerTier[currentTier]`
- If new best: updates via `PlayerRepository.updateBestWave()`
- Returns whether it was a new record

---

### Task 7: Round End Cleanup

Update `BattleViewModel`:
- On round end:
  - Call `UpdateBestWave`
  - Clear `RoundState` (cash resets, temp upgrades reset)
  - Stop game loop thread
  - Navigate to `PostRoundScreen`

---

### Task 8: Pause & Resume

Update `BattleScreen.kt` and `GameLoopThread`:
- Pause button freezes game loop (stops update ticks, continues rendering last frame)
- Resume button restarts loop
- Auto-pause when app goes to background (`Lifecycle` observer)
- Pause overlay with "Resume" and "Quit Round" options

---

## File Summary

```
domain/usecase/
└── UpdateBestWave.kt           (new)

presentation/battle/
├── PostRoundScreen.kt          (new)
├── BattleScreen.kt             (update — speed controls, pause)
├── BattleViewModel.kt          (update — round lifecycle)
├── GameLoopThread.kt           (update — pause/resume, speed)
└── engine/
    ├── GameEngine.kt           (update — round end detection)
    └── WaveSpawner.kt          (update — wave state machine)
```

## Completion Criteria

- Round starts with correct Workshop stats and player tier
- Waves progress with proper 26s spawn + 9s cooldown timing
- Speed controls (1x/2x/4x) work correctly
- Round ends when ziggurat HP reaches 0
- Post-round screen shows wave reached, enemies killed, time survived
- Best wave per tier persists to Room
- New personal best is detected and highlighted
- Pause/resume works, auto-pauses on background
- "Play Again" and "Return to Workshop" navigation works
- Cash and temp upgrades fully reset between rounds
