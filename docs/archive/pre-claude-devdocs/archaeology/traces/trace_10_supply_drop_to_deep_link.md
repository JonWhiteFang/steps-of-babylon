# Trace 10 — Supply drop → notification → deep-link → Supplies screen

*Phase 3 Deep Trace. Ground truth:
`data/sensor/DailyStepManager.kt` (inside `runFollowOnPipeline`),
`domain/usecase/GenerateSupplyDrop.kt`,
`data/repository/WalkingEncounterRepositoryImpl.kt`,
`service/SupplyDropNotificationManager.kt`,
`presentation/MainActivity.kt` (`pendingNavigation`, `onNewIntent`,
`LaunchedEffect`), `presentation/supplies/*`. This traces a cross-process
IPC through the notification tray into a deep-link into the app.*

## 1. Entry Point

Step credit path (trace 01 or 02) → inside
`DailyStepManager.runFollowOnPipeline` stage 2 (trace 04 §2). Concretely:

```kotlin
val drop = generateSupplyDrop(
    dailyCreditedSteps = dailyCreditedTotal,
    lastCheckSteps     = dropState.lastCheckSteps,
    timestampMs        = now,
    unclaimedCount     = walkingEncounterRepository.getUnclaimedCount(),
)
if (drop != null) {
    walkingEncounterRepository.enforceInboxCap(GenerateSupplyDrop.MAX_INBOX)
    val id = walkingEncounterRepository.createDrop(drop.trigger, drop.reward, drop.rewardAmount)
    supplyDropNotificationManager.notify(drop.copy(id = id.toInt()))
}
```

The user then taps the notification in the system tray.

## 2. Execution Path

### 2.1 Inside `SupplyDropNotificationManager.notify(drop)`

```kotlin
if (!notificationPreferences.isSupplyDropsEnabled()) return             // user-toggled mute

val intent = Intent(context, MainActivity::class.java).apply {
    putExtra("navigate_to", "supplies")                                  // KEY FOR DEEP-LINK
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val pending = PendingIntent.getActivity(
    context,
    drop.id,                                                             // unique requestCode per drop
    intent,
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(android.R.drawable.ic_menu_compass)
    .setContentTitle("Supply Drop!")
    .setContentText(drop.trigger.message)
    .setContentIntent(pending)
    .setAutoCancel(true)                                                 // auto-dismiss on tap
    .build()

notificationManager.notify(BASE_NOTIFICATION_ID + drop.id, notification) // 2000 + drop id
```

### 2.2 User taps notification → Android delivers Intent

Android's NotificationManager resolves the `PendingIntent` →
`ContextImpl.startActivity(intent)` → `MainActivity`.

Two delivery modes depending on app state:

- **App not running (cold start)** — OS launches
  `StepsOfBabylonApp.onCreate` (SQLCipher, WorkManager schedule) →
  `MainActivity.onCreate(savedInstanceState)`. The launching
  `intent` is the Notification's intent, available via
  `getIntent()` (and `onCreate`'s implicit `intent` field). Inside
  the first `LaunchedEffect(Unit)`:
  ```kotlin
  intent?.getStringExtra("navigate_to")?.let { pendingNavigation.value = it }
  ```

- **App already running** — because of `FLAG_ACTIVITY_SINGLE_TOP`,
  the existing `MainActivity` instance gets `onNewIntent(intent)`
  instead of a new instance:
  ```kotlin
  override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      intent.getStringExtra("navigate_to")?.let { pendingNavigation.value = it }
  }
  ```

Both paths funnel into `pendingNavigation: MutableStateFlow<String?>`
(activity-scoped, not injected).

### 2.3 Pending-navigation → nav controller

The same Activity's composition has:

```kotlin
LaunchedEffect(Unit) {
    pendingNavigation.collect { route ->
        if (route != null) {
            when (route) {
                "supplies" -> navController.navigate(Screen.Supplies.route)
                "workshop" -> navController.navigate(Screen.Workshop.route)
                "battle"   -> navController.navigate(Screen.Battle.route)
                "missions" -> navController.navigate(Screen.Missions.route)
            }
            pendingNavigation.value = null   // clear for next time
        }
    }
}
```

- The `LaunchedEffect` is keyed on `Unit` so it starts once per
  composition and survives navigation.
- `StateFlow.collect` keeps running; re-emissions from
  `onNewIntent` after the first use will re-dispatch.
- Setting `pendingNavigation.value = null` at the end prevents
  re-dispatching on every composition.

### 2.4 Supplies screen rendering

NavHost resolves:

```kotlin
composable(Screen.Supplies.route) { UnclaimedSuppliesScreen() }
```

`UnclaimedSuppliesScreen` instantiates `UnclaimedSuppliesViewModel` via
`hiltViewModel()`. The VM observes
`walkingEncounterRepository.observeUnclaimed()` (Flow<List<SupplyDrop>>)
and exposes a `StateFlow<SuppliesUiState>`. The Compose screen renders
a LazyColumn of drop cards with Claim buttons.

Tapping Claim calls `viewModel.claim(id)` which invokes
`walkingEncounterRepository.claimDrop(id)` and adds the reward to the
wallet via `playerRepository` (see `UnclaimedSuppliesViewModel.claim`
for the full reward mapping; not expanded here).

## 3. Resource Management

| Concern | How |
|---|---|
| Process lifecycle | Cold start walks through `StepsOfBabylonApp.onCreate` → `MainActivity.onCreate`. Warm start goes to `onNewIntent` on the existing instance. `SINGLE_TOP`+`CLEAR_TOP` prevent stacking multiple MainActivity instances. |
| PendingIntent | `FLAG_IMMUTABLE` (required on API 31+) + `FLAG_UPDATE_CURRENT` (if a prior PI exists for the same requestCode, update its extras). `drop.id` as requestCode distinguishes multiple drops so each notification points to its own intent. |
| Notification ID | `BASE_NOTIFICATION_ID + drop.id` uniquifies notifications, enabling simultaneous drops to appear side-by-side in the tray. |
| Channel | `supply_drops` with `IMPORTANCE_DEFAULT`. Created once in `SupplyDropNotificationManager.init`; creating again with the same ID is a no-op. |
| Autosdismiss | `setAutoCancel(true)` removes the notification when the user taps it. |
| State carrier | `MainActivity.pendingNavigation: MutableStateFlow<String?>` is an activity-scoped property — not a ViewModel. Lifetime matches activity lifetime. |

## 4. Error Path

- **`NotificationPreferences.isSupplyDropsEnabled()` false** — no
  notification. The drop is still in `walking_encounter_entity`; the
  user can find it by opening Home → Unclaimed Supplies badge.
- **Android kills the app before tap** — Notification persists.
  When the user taps it, OS cold-starts; intent extras survive.
- **User dismisses without tapping** — `autoCancel` only fires on
  tap. Swipe-to-dismiss just clears the tray; the drop remains
  unclaimed in Room and the inbox badge on Home stays.
- **Deep-link route unknown** — the `when` has no `else`; unknown
  routes are silently no-opped. Currently 4 routes are handled;
  others (Store, Stats, etc.) would not respond.
- **`onNewIntent` called without `navigate_to`** — the `?.let` skips;
  no state change.
- **`PendingIntent.FLAG_UPDATE_CURRENT` collision** — if drop.id is
  reused (Room id is auto-incremented and unique, so this shouldn't
  happen), the older PI's extras are overwritten. Not a current
  concern.
- **Activity not ready for navigation yet** — on cold start,
  `LaunchedEffect` inside composition runs only after Compose is
  set up. `pendingNavigation.value` is set in `onCreate` → first
  `LaunchedEffect`, then the *second* `LaunchedEffect` (nav
  collector) consumes it. Ordering works because they are launched
  in the same coroutine scope declaration order and the StateFlow
  retains the value until consumed.

## 5. Performance Characteristics

- Drop creation cost: 1 pure-Kotlin use-case call, 1 Room insert,
  possibly N Room deletes from `enforceInboxCap` (usually 0), 1
  Notification post. All in the follow-on pipeline (trace 04),
  throttled by natural step cadence.
- Deep-link dispatch cost: 1 Intent extra read, 1 StateFlow emit,
  1 `navController.navigate` call. < 1 ms.
- Supplies screen rendering: LazyColumn over up to 10 drops. Trivial.

## 6. Observable Effects

- **System notification tray**: a new notification with title
  "Supply Drop!" and subtext `drop.trigger.message` (one of four
  SupplyDropTrigger enum messages). Tap pendintent targets
  MainActivity. Icon is `android.R.drawable.ic_menu_compass`
  (system default).
- **Home screen badge** (already observing unclaimed count): the
  number bumps. Because the VM is in `WhileSubscribed(5000)` mode,
  this only visually updates if Home is currently on top or was
  recently visible.
- **Post-tap**: BottomNavBar's "Home" / etc. tabs are bypassed;
  user ends up on Supplies. The top back button returns to Home
  because of `popBackStack` semantics.
- **Room**: `walking_encounter_entity` row inserted; possibly oldest
  rows deleted.

## 7. Why This Design

- **String-valued `navigate_to` extra** instead of typed deep-link
  URIs because the app is single-host with a closed set of routes.
  The sealed `Screen` class would be overkill as an intent
  payload; strings are inspectable in the tray-preview fields.
- **`pendingNavigation` as a `MutableStateFlow`** bridges the
  Android intent system (imperative) to Compose (reactive). A
  single one-value channel is sufficient because deep-links
  don't accumulate in practice.
- **Dual cold/warm path consumes the same StateFlow**. `onCreate`
  sets it from `intent.getStringExtra`; `onNewIntent` sets it
  again; the `LaunchedEffect` collector handles both.
- **Using `Intent.FLAG_ACTIVITY_SINGLE_TOP | CLEAR_TOP`** keeps a
  single MainActivity instance alive. Without `SINGLE_TOP`, tapping
  a supply drop while already inside the app would push a new
  MainActivity on top, producing two stacks.
- **Per-drop requestCode + notification ID** allows multiple
  simultaneous drops (e.g. walking through a 10k boundary while
  also hitting a random roll) to not cannibalise each other.
- **`IMPORTANCE_DEFAULT`** makes the notification peek over the
  status bar but not vibrate — appropriate for a walking reward.

## 8. Feels Incomplete

- **Four deep-link routes handled** (`supplies`, `workshop`,
  `battle`, `missions`). Other screens (Store, Stats, Cards, Labs,
  Weapons, Economy, Settings, Home) cannot be deep-linked. Listed
  in `SupplyDropNotificationManager` only as "supplies"; but
  `StepNotificationManager` uses "workshop" and "battle";
  `SmartReminderManager` uses "workshop"; `MilestoneNotificationManager`
  uses "missions". No central registry.
- **Notification icon is
  `android.R.drawable.ic_menu_compass`** — system default. No app
  branding (STATE.md flags "no app icon resources").
- **No notification grouping.** If five drops fire in quick
  succession (e.g. user walks 10k in a burst), the tray gets five
  separate notifications. A `GroupingBuilder` would be nicer.
- **`drop.trigger.message`** is a fixed string per enum variant,
  not localisable via `strings.xml`.
- **No log trail**. When a user complains "I never got a supply
  drop notification", there's no way to diagnose from outside the
  running process.

## 9. Feels Vulnerable

- **PendingIntent `drop.id`** is the Room-assigned auto-increment.
  First drop id=1, next id=2, etc. If the user uninstalls and
  reinstalls, ids restart from 1. A pre-existing notification in
  the system tray (rare — they auto-cancel on tap) for "id=1" might
  still exist and collide. Very edge-case.
- **`FLAG_ACTIVITY_CLEAR_TOP`** will pop any stacked composables
  above MainActivity. If somehow the app had a secondary activity
  open (it doesn't — single-activity architecture), that activity
  would be destroyed on the deep-link. Currently safe.
- **Deep-link during mid-battle**: `navigate_to=supplies` will
  replace the Battle route with Supplies. The `BattleViewModel.onCleared`
  fires, which sets `engine.onStepReward = null`; the game loop
  thread is stopped when `BattleScreen` leaves composition; any
  in-progress round is quietly ended without the end-round
  cascade (trace 08). **Soft bug**: the round's wave/kills/cash are
  lost; best-wave is not updated.
- **Notification-channel user preference race**: user toggles
  "Supply Drops" off just as a drop is about to fire; the
  `NotificationPreferences` read is non-atomic with the drop
  creation. Worst case is one notification sneaks out. Harmless.
- **StateFlow collector survival across configuration changes**:
  `LaunchedEffect(Unit)` is keyed on `Unit`, so it restarts on
  Compose re-composition but not on config changes (Activity is
  not recreated because of `configChanges` in manifest — actually
  MainActivity doesn't declare configChanges so it *does* recreate
  on rotation, at which point the pendingNavigation flow collector
  is restarted; the StateFlow's latest value is preserved, so
  deep-links survive).

## 10. Feels Like Bad Design

- **No central deep-link registry.** Four notification sources each
  use their own string extras; the `when` in `MainActivity`
  replicates those four strings. A `DeepLinkRoute` sealed class
  (mirroring `Screen`) with a `fromString(String): DeepLinkRoute?`
  would centralise.
- **`MainActivity.pendingNavigation` is a field on the Activity.**
  It survives the Activity lifetime but not process death. A
  `SavedStateHandle`-backed navigation queue would be more
  robust — but would require routing through a ViewModel.
- **The deep-link flow is spread across 4+ files**:
  `SupplyDropNotificationManager` (post), `MainActivity` (consume),
  `Screen` (route table), `StepNotificationManager` (same pattern),
  `MilestoneNotificationManager` (same), `SmartReminderManager`
  (same). Testing the whole flow requires all four plus
  `DeepLinkRoutingTest` in `presentation/DeepLinkRoutingTest.kt`,
  which only covers the intent extraction — not the navigation.
- **`BASE_NOTIFICATION_ID + drop.id`** arithmetic is fine but
  opaque. A helper `supplyDropNotificationId(drop: SupplyDrop): Int`
  in the companion would be clearer.
- **Deep-link into Battle via notification**. This is supported —
  `StepNotificationManager`'s Battle action — but doing so mid-round
  from another notification path kills the live round. No code
  comment warns about this.
- **Re-setting `pendingNavigation.value = null`** inside the
  collector is the only thing preventing infinite re-dispatch.
  If someone modifies the `when` block and forgets the nulling, the
  bug is subtle. A `Channel<String>` instead of a `MutableStateFlow`
  would be more appropriate semantically (consume-once messages).
