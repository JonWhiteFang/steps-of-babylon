# Archaeology Phase 3 — Internal Interface Traces

*Companion to `devdocs/archaeology/small_summary.md` (Phase 1,
user-facing) and `devdocs/archaeology/intro2codebase.md` +
`intro2deployment.md` (Phase 2, architecture + build). This directory
goes a layer deeper: it walks each internal interface boundary
end-to-end, using code behaviour as the single source of truth.*

Each trace file follows a fixed 10-section schema:

1. Entry Point
2. Execution Path
3. Resource Management
4. Error Path
5. Performance Characteristics
6. Observable Effects
7. Why This Design
8. Feels Incomplete
9. Feels Vulnerable
10. Feels Like Bad Design

## Index

| # | Trace | What it covers | Primary boundary |
|---|---|---|---|
| 01 | [trace_01_step_sensor_to_room.md](trace_01_step_sensor_to_room.md) | Sensor HAL → `StepSensorDataSource` (callbackFlow) → `StepCounterService` → `DailyStepManager` → Room | Hardware sensor ↔ app process; sensor listener ↔ Kotlin Flow |
| 02 | [trace_02_step_sync_worker_and_heartbeat_handoff.md](trace_02_step_sync_worker_and_heartbeat_handoff.md) | 15-minute `StepSyncWorker`: heartbeat-based handoff with the service, HC gap-fill, activity-minute conversion | `@HiltWorker` ↔ foreground service; HC SDK ↔ `DailyStepManager` |
| 03 | [trace_03_hc_cross_validation_escrow.md](trace_03_hc_cross_validation_escrow.md) | `StepCrossValidator` 4-level graduated response + destructive escrow lifecycle | Anti-cheat state machine ↔ Room; offense counter ↔ SharedPreferences |
| 04 | [trace_04_follow_on_pipeline_fanout.md](trace_04_follow_on_pipeline_fanout.md) | The 5-stage `runFollowOnPipeline` fan-out: widget, supply drop, daily login, weekly challenge, walking missions | `DailyStepManager` ↔ 5 independent downstream subsystems |
| 05 | [trace_05_compose_to_surfaceview_boot.md](trace_05_compose_to_surfaceview_boot.md) | Entering battle: Compose `BattleScreen` → `AndroidView` → `GameSurfaceView` → `GameLoopThread` spawn | Compose ↔ SurfaceView; main thread ↔ dedicated game thread |
| 06 | [trace_06_game_loop_single_frame.md](trace_06_game_loop_single_frame.md) | One tick of the fixed-timestep game loop: accumulator, speed multiplier, lockCanvas, entity sweep | `GameLoopThread` ↔ `GameEngine`; `@Volatile` cross-thread reads |
| 07 | [trace_07_enemy_kill_and_battle_step_reward.md](trace_07_enemy_kill_and_battle_step_reward.md) | Enemy death: cash award on game thread + `onStepReward` callback hop to `viewModelScope` → `AwardBattleSteps` → Room | Game thread ↔ VM coroutine; engine callback ↔ use case |
| 08 | [trace_08_round_end_cascade.md](trace_08_round_end_cascade.md) | Round-end: polling detects `roundOver` → `endRound` → `UpdateBestWave` + `AwardWaveMilestone` + `CheckTierUnlock` + notification + mission updates | VM ↔ 4 use cases + notifier + mission DAO |
| 09 | [trace_09_workshop_purchase_flow.md](trace_09_workshop_purchase_flow.md) | Tap Buy → `WorkshopViewModel` → `PurchaseUpgrade` use case → atomic Room writes → reactive UI re-emit | Compose ↔ VM ↔ domain use case ↔ two repositories ↔ two DAOs |
| 10 | [trace_10_supply_drop_to_deep_link.md](trace_10_supply_drop_to_deep_link.md) | Supply drop created → system notification → user tap → `MainActivity.onNewIntent` / cold-start extras → `pendingNavigation` → NavHost | App process ↔ Android NotificationManager ↔ PendingIntent ↔ MainActivity |
| 11 | [trace_11_widget_update.md](trace_11_widget_update.md) | `DailyStepManager` fan-out → `WidgetUpdateHelper` (60 s throttle) → SharedPreferences → `StepWidgetProvider` → `AppWidgetManager.updateAppWidget` RemoteViews | App process ↔ launcher process via `AppWidgetManager` Binder IPC |
| 12 | [trace_12_db_bootstrap_and_keystore.md](trace_12_db_bootstrap_and_keystore.md) | App.onCreate → `System.loadLibrary("sqlcipher")` → Hilt `DatabaseModule.provideDatabase` → `DatabaseKeyManager` (Android Keystore AES-GCM) → Room builder with `MIGRATION_7_8` | Android Keystore ↔ SharedPreferences; SQLCipher JNI ↔ Room |
| 13 | [trace_13_boot_recovery.md](trace_13_boot_recovery.md) | `BOOT_COMPLETED` → `BootReceiver.onReceive` → permission check → `startForegroundService(StepCounterService)` | Android BroadcastReceiver ↔ foreground service |

## How to use these

- **Onboarding**: read traces 01 → 04 → 05 → 06 → 07 → 08 → 09 → 12
  in that order. They cover the daily core loop (walk → upgrade →
  battle → reward → persist) and the DB boot that underpins everything.
- **Debugging a specific phenomenon**: see the "entry-point quick
  lookup" table in `intro2codebase.md` §8, then jump to the matching
  trace.
- **Auditing for concurrency / correctness**: the *Feels Vulnerable*
  and *Feels Like Bad Design* sections of each trace list observed
  soft spots that are **candidates**, not confirmed bugs. They are
  written from reading code, not from running diagnostics.
- **Code-authoritative rule**: wherever project docs disagree with
  what code does, traces reflect code. Known drift points called out
  in `intro2codebase.md` §9 remain accurate.

## What Phase 3 deliberately does NOT cover

- **Every screen's 4-layer read path.** `HomeScreen`, `LabsScreen`,
  `CardsScreen`, `UltimateWeaponScreen`, `MissionsScreen`,
  `CurrencyDashboardScreen`, `StatsScreen`, `UnclaimedSuppliesScreen`,
  `NotificationSettingsScreen`, `StoreScreen` all follow the generic
  pattern documented inline in `intro2codebase.md` §3.3 and the
  detailed example in trace 09 (Workshop purchase). Any screen-level
  peculiarity (e.g. LabsViewModel's timer tick, StatsViewModel's
  period switching) is called out in its dedicated unit test and
  can be read at the VM source directly.
- **Every use case.** The 32 use cases in `domain/usecase/` are
  individually small and individually unit-tested. Traces cite the
  ones that participate in cross-boundary flows.
- **Every notification.** The four notification managers
  (`StepNotificationManager`, `SupplyDropNotificationManager`,
  `MilestoneNotificationManager`, `SmartReminderManager`) share the
  same PendingIntent/deep-link pattern shown in trace 10.
- **Stub billing / ad flows.** `StubBillingManager` and
  `StubRewardAdManager` are trivial (simulate with `delay`, always
  succeed). They will be replaced by real SDK integrations in
  Plan 31 per `docs/agent/STATE.md`; detailed archaeology of the
  current stubs would obsolete quickly.
- **VFX internals.** `EffectEngine`, `ParticlePool`, `ScreenShake`,
  `UWVisualEffect`, `DeathEffect` are rendering internals; trace 06
  covers their lifecycle within a frame. Deeper treatment would be
  graphics-code review, not interface archaeology.

## File naming convention

- `trace_NN_<short_name>.md`, zero-padded number.
- Numbers are stable once assigned — new traces append with the
  next number. Don't renumber existing files; tooling and links
  reference the numbers.

*Last aligned with code: 2026-05-05. Key code facts verified:
DB schema v8 with `MIGRATION_7_8`; `DAILY_BATTLE_STEP_CAP=2000`;
`DailyStepManager.DAILY_CEILING=50000`; `StepRateLimiter` 200/min
normal, 250/min burst 5-min; game loop `TICK_NS=16_666_667`;
worker heartbeat threshold 120_000 ms; callbacks on
`GameEngine.onStepReward` are `@Volatile`.*
