# Plan 23 — Notifications & Widget

**Status:** Complete
**Dependencies:** Plan 04 (Step Counter Service) ✓
**Layer:** `service/` + `presentation/`

---

## Objective

Enhance the persistent step count notification, build a home screen widget (2×2) with live step counter and mini ziggurat, and implement smart reminder notifications that motivate walking toward specific upgrade goals.

Reference: GDD §12.3 for notification and widget specs.

---

## Task Breakdown

### Task 1: Enhanced Persistent Notification

Update `StepNotificationManager`:
- Show: "Today: X steps | Balance: Y Steps"
- Add action buttons: "Open Workshop", "Start Battle"
- Update biome-themed small icon based on current biome
- Throttle updates to every 30 seconds max

---

### Task 2: Home Screen Widget

Create `presentation/widget/StepWidgetProvider.kt`:
- `AppWidgetProvider` for a 2×2 home screen widget
- Displays: daily step count, spendable Step balance, mini ziggurat illustration
- Tap widget → open app (Home screen)
- Long-press → open Battle directly
- Updates via `AppWidgetManager` on step changes (throttled)

Create `res/xml/step_widget_info.xml` — widget metadata.
Create `res/layout/widget_step_counter.xml` — RemoteViews layout.

---

### Task 3: Widget Update Service

Create `presentation/widget/WidgetUpdateHelper.kt`:
- Called from `DailyStepManager` when steps are credited
- Updates widget RemoteViews with latest counts
- Throttled to prevent excessive updates (every 60 seconds)

---

### Task 4: Smart Reminder Notifications

Create `service/SmartReminderManager.kt`:
- Calculates proximity to next affordable Workshop upgrade
- Sends reminder: "You're 2,000 steps away from upgrading Chain Lightning!"
- Triggers at most once per day, only if player hasn't opened app in 4+ hours
- Respects notification preferences (can be disabled)

---

### Task 5: Milestone Alert Notifications

Create `service/MilestoneNotificationManager.kt`:
- Sends notification on new personal best wave: "New personal best! Wave 87 in The Burning Sands!"
- Sends notification on milestone achievement: "Trail Blazer! 100,000 total steps!"
- Uses separate notification channel (`milestones`)

---

### Task 6: Notification Preferences

Create `presentation/settings/NotificationPreferences.kt`:
- Toggle: persistent step notification (on/off)
- Toggle: supply drop notifications (on/off)
- Toggle: smart reminders (on/off)
- Toggle: milestone alerts (on/off)
- Stored in DataStore or SharedPreferences

---

### Task 7: Manifest Updates

Update `AndroidManifest.xml`:
- Register `StepWidgetProvider` with `<receiver>` and `<meta-data>`
- Register notification channels: `step_counter`, `supply_drops`, `milestones`, `reminders`

---

## File Summary

```
service/
├── StepNotificationManager.kt  (update — enhanced notification)
├── SmartReminderManager.kt     (new)
└── MilestoneNotificationManager.kt (new)

presentation/widget/
├── StepWidgetProvider.kt       (new)
└── WidgetUpdateHelper.kt       (new)

presentation/settings/
└── NotificationPreferences.kt  (new)

res/xml/
└── step_widget_info.xml        (new)

res/layout/
└── widget_step_counter.xml     (new)

AndroidManifest.xml             (update)
```

## Completion Criteria

- Persistent notification shows daily steps and balance with action buttons
- 2×2 home screen widget displays live step count and balance
- Widget updates on step changes (throttled)
- Widget tap opens app, long-press opens battle
- Smart reminders fire at most once/day with relevant upgrade proximity
- Milestone alerts fire on new personal bests and step milestones
- All notification types respect user preferences
- Notification channels properly configured
