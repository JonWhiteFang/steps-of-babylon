# Trace 11 — Home-screen widget update flow

*Phase 3 Deep Trace. Ground truth:
`service/WidgetUpdateHelper.kt`,
`service/StepWidgetProvider.kt`,
`data/sensor/DailyStepManager.kt` (`runFollowOnPipeline` stage 1),
`AndroidManifest.xml` (`receiver` declaration),
`app/src/main/res/xml/step_widget_info.xml`,
`app/src/main/res/layout/widget_step_counter.xml`. A cross-process IPC
from the app process to the launcher process via `AppWidgetManager`.*

## 1. Entry Point

Two ways a widget update fires:

1. **From the app**:
   - `DailyStepManager.runFollowOnPipeline(timestampMs)` stage 1
     calls `widgetUpdateHelper.update(dailyCreditedTotal, balance)`
     after every accepted step credit or activity-minute credit.
   - A few VMs also call `widgetUpdateHelper.update` directly in
     response to wallet changes? (Grep shows only `DailyStepManager`
     and tests use `WidgetUpdateHelper`.) This is the only code
     path at runtime.

2. **From the launcher / system**:
   - `StepWidgetProvider` is registered as an `AppWidgetProvider`
     in the manifest. Its `onUpdate(context, manager, ids)` is
     invoked by the OS at the cadence declared in
     `step_widget_info.xml` (`updatePeriodMillis` — Android's
     minimum is 30 minutes).
   - On widget add (user drags it onto home screen), on reboot, on
     app restore.

## 2. Execution Path

### 2.1 `WidgetUpdateHelper.update(dailySteps, balance)`

```kotlin
private var lastUpdateMs = 0L

fun update(dailySteps: Long, balance: Long) {
    val now = System.currentTimeMillis()
    if (now - lastUpdateMs < THROTTLE_MS) return      // 60_000 ms throttle
    lastUpdateMs = now
    StepWidgetProvider.saveData(context, dailySteps, balance)
    StepWidgetProvider.updateAllWidgets(context)
}
```

### 2.2 `StepWidgetProvider` companion helpers

```kotlin
fun saveData(context: Context, dailySteps: Long, balance: Long) {
    context.getSharedPreferences("widget_data", Context.MODE_PRIVATE).edit()
        .putLong("daily_steps", dailySteps)
        .putLong("balance", balance)
        .apply()                                       // async write; survives process death
}

fun updateAllWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, StepWidgetProvider::class.java))
    if (ids.isEmpty()) return                           // no widgets placed — skip

    val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
    val steps = prefs.getLong("daily_steps", 0)
    val balance = prefs.getLong("balance", 0)
    val fmt = NumberFormat.getNumberInstance()

    val views = RemoteViews(context.packageName, R.layout.widget_step_counter).apply {
        setTextViewText(R.id.widget_daily_steps, "${fmt.format(steps)} steps")
        setTextViewText(R.id.widget_balance, "Balance: ${fmt.format(balance)}")
        setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))
    }

    for (id in ids) manager.updateAppWidget(id, views)   // IPC across to launcher
}
```

### 2.3 OS-driven path

Every `updatePeriodMillis` (or on add/reboot) the OS calls
`StepWidgetProvider.onUpdate(context, manager, ids)`, which simply
forwards to `updateAllWidgets(context)`. That uses the most recently
saved SharedPrefs values — no code path queries Room from the widget
provider.

## 3. Resource Management

| Concern | How |
|---|---|
| Throttle | In-memory `lastUpdateMs` on `WidgetUpdateHelper` (`@Singleton`). 60 s minimum between pushes from the app. Only guards app-driven pushes, not OS-driven onUpdate. |
| IPC boundary | `AppWidgetManager.updateAppWidget` serialises the `RemoteViews` and ships it to the launcher process. RemoteViews can only reference a small set of layout primitives (TextView, ImageView, etc.). |
| Persistence | SharedPreferences `widget_data` holds the most recent numbers, so the OS-driven `onUpdate` (which may run with no Room access) has data to display. |
| Data freshness | App-driven updates are bounded to 1 per 60 s; OS-driven are 1 per 30 min. Worst case lag: up to 60 s while walking, up to 30 min while the app is dead. |
| PendingIntent | Single shared PI per update (requestCode=0), targeting MainActivity with no extras — opens Home, not a deep-link. |

## 4. Error Path

- **No widgets placed** — `ids.isEmpty()` short-circuits. No work
  done. Safe.
- **SharedPreferences write failure** — `apply()` is fire-and-forget;
  exceptions (rare) are swallowed by the framework. Next write
  overwrites.
- **`updateAppWidget` fails (e.g. launcher disabled)** — silently
  fails within the AppWidgetManager; no exception thrown to caller.
- **`getSharedPreferences("widget_data", ...)` called from the widget
  provider when app process is dead** — SharedPreferences are
  process-scoped; reading in the widget provider's process (same
  as the app process in this case, since widget providers run in
  the owning app's process) re-opens the file. Values persist.
- **Clock jump** — `System.currentTimeMillis()` is used for the 60-s
  throttle. Backward time jumps could starve updates for a while;
  forward jumps could let one extra update through. Both are rare.
- **Widget never added** — `updateAllWidgets` is a no-op forever.
  Nothing broken.

## 5. Performance Characteristics

- `saveData` cost: 2 SharedPreferences put + async commit. ~100 µs.
- `updateAllWidgets` cost: 1 `getAppWidgetIds` query, 2 prefs reads,
  1 `RemoteViews` build, N `updateAppWidget` IPC calls. O(N) in
  widget count. Typical: N=1 or 0.
- Binder marshalling per `updateAppWidget`: small (a few hundred
  bytes for two text strings).
- `DailyStepManager.runFollowOnPipeline` calls this every step
  credit; throttle collapses to once per minute max. Outside the
  throttle window the path is: `System.currentTimeMillis()` +
  comparison + `return`. Sub-microsecond.

## 6. Observable Effects

- **SharedPreferences `widget_data`**:
  - `daily_steps` = today's credited total (both walking and
    activity-minute equivalents).
  - `balance` = wallet's `currentStepBalance` at time of update.
- **Home-screen widget**: the 2×2 widget rendered by
  `res/layout/widget_step_counter.xml` repaints with the new
  numbers (`"{formatted} steps"` / `"Balance: {formatted}"`).
- **Click target**: widget_root has a PI that opens MainActivity.
  No deep-link extras; the app lands on the Home route.

Nothing else changes — no Room writes, no notifications, no
logging.

## 7. Why This Design

- **SharedPreferences cache** decouples the widget from Room. Widget
  providers should not do heavy work in `onUpdate` (they run on
  the main thread of the app's process and the OS has a short
  budget). A small JSON/int file is Android's standard pattern.
- **Throttle at 60 s** because the widget is a glanceable indicator,
  not a live dashboard. 60 s is also comfortably below the 30-min
  OS cadence — between OS updates, the app may push up to 30
  refreshes.
- **`widget_data` SharedPreferences file** is a separate named file
  from other prefs (`step_ingestion`, `anti_cheat_prefs`, etc.) —
  keeps concerns isolated.
- **No ViewModel / observer** — if we observed the wallet flow, we'd
  need to manage the observer's lifecycle inside the widget
  provider, which is awkward. Push-based via `WidgetUpdateHelper`
  is simpler.
- **PendingIntent opens Home without a deep-link** because the
  widget's primary UX is "nudge me to open the app"; a deeper
  link would be guessing what the user wants.
- **`getAppWidgetIds` short-circuit** means zero runtime cost when
  no widgets are placed — common case.

## 8. Feels Incomplete

- **No per-widget state.** If a user places two widgets, both show
  the same numbers (naturally). But if a future feature wanted
  per-widget config (e.g. "show wave milestones instead of
  steps"), there's no infrastructure.
- **Balance formatting uses locale default** (`NumberFormat.getNumberInstance()`).
  Great. But the steps text is hand-concatenated with "steps"
  suffix — not localisable. Same for "Balance: " prefix.
- **No click-to-refresh**. The `widget_root` onClick opens
  MainActivity; there's no alternate click target that would
  force a refresh.
- **Reduced-motion not considered for widget**. Probably fine,
  there's no animation in the current layout.
- **No widget sizing variants**. `step_widget_info.xml` declares
  one size; Android 12+ supports sizing, but this layout is
  static 2×2.

## 9. Feels Vulnerable

- **Stale widget data after long app death**. If the user has
  walked thousands of steps since the app was last alive and the
  30-min OS update hasn't fired, the widget is out of date. The
  ingestion service, once resurrected, takes up to 60 s to push
  a refresh via the 60-s throttle.
- **Throttle lives in-process.** A process restart resets
  `lastUpdateMs=0`, which allows an update on next call. Not a
  problem; just note.
- **Binder IPC back-pressure.** If the launcher is slow (e.g.
  under memory pressure), `updateAppWidget` may queue or drop
  updates silently. The code doesn't detect this.
- **SharedPreferences read-write race across processes.** In
  theory, if widget provider and app process were different
  processes (they're not here — same process since no
  `android:process` attribute on the receiver), there would be
  no cross-process safety. But same-process widget provider +
  SharedPreferences works fine.
- **`putLong("balance", balance)`**: if `balance` ever becomes
  negative somehow (clamped at 0 by Room, but if Room were
  bypassed), rendering `"Balance: -500"` would be ugly. Minor.

## 10. Feels Like Bad Design

- **SharedPreferences keys are stringly-typed**: `"daily_steps"`,
  `"balance"`. Accidents in future code (`"daily_step"`) would
  silently return default 0. A typed wrapper
  (`WidgetState` data class serialised to a single JSON key) would
  be safer.
- **`WidgetUpdateHelper` has 15 lines of logic.** It's a
  `@Singleton` just for the throttle state. Could be a companion
  object if we didn't need `@Inject`. A small oversight.
- **`StepWidgetProvider.saveData` + `updateAllWidgets` as
  companion statics**: doesn't follow the rest of the codebase's
  `@Inject`-everything style. The provider is framework-owned, so
  it has no constructor injection — fair — but the statics make
  the data flow hard to follow.
- **No tests for the runtime update path**. `StepWidgetProviderTest`
  covers SharedPreferences round-trips, but there's no test
  verifying that walking steps → prefs → `RemoteViews` end-to-end.
  A Robolectric test would be possible.
- **The widget's `text` fields are rebuilt every update** — they
  include the full formatted string. Android's `RemoteViews` is
  diff-based internally, but the app code unconditionally rebuilds
  and ships the whole view. Harmless for simplicity.
- **The `ComponentName(context, StepWidgetProvider::class.java)`**
  is recomputed every call. Could be a companion `val`.
- **Update cadence of 30 min is nowhere configurable**. The XML in
  `res/xml/step_widget_info.xml` is static; tuning requires a
  rebuild. No dynamic updates (e.g. "update every 5 min while
  active") are possible without moving to a different widget
  framework.
