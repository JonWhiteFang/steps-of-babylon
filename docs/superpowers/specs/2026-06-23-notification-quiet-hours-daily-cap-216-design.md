# Design — Quiet-hours + daily-cap for reminder & supply-drop notifications (#216 / NOTIF-1)

**Date:** 2026-06-23
**Issue:** #216 (`[Audit] No quiet-hours / daily-cap on reminder & supply-drop notifications (NOTIF-1)`),
severity:minor, retention. From the 2026-06-17 complete-app review §18 (orchestrator-verified).
**Status:** design approved — proceeding to implementation plan.

## Problem

Reminder and supply-drop notifications have **no quiet-hours window and no daily cap**. Two concrete
risks:

1. **Play "disruptive notifications" policy** — off-hours pings are a compliance/uninstall hazard on the
   path to closed-test → production.
2. **Retention harm** — a long walk can pelt the player with supply-drop pushes; reminders could fire at
   night.

Ground-truth state of the two in-scope emission paths (verified against `main` HEAD):

- **`service/SmartReminderManager.checkAndNotify()`** (triggered by `StepSyncWorker`, 15-min periodic):
  already gated by an enabled-check, a **de-facto 1/day cap** (`last_sent == today` SharedPref), and a
  **4-hour inactivity** gate. **Missing only: quiet-hours.**
- **`service/SupplyDropNotificationManager.notify(drop)`** (called from
  `data/sensor/DailyStepManager.runFollowOnPipeline` on every generated drop during walking): gated only
  by an enabled-check. **Missing: both quiet-hours and a daily cap.**

## Scope

### In scope
- A fixed (non-configurable) quiet-hours window applied to **both** the reminder and supply-drop
  notification paths.
- A per-day cap on **supply-drop notifications**.
- A pure-domain decision helper + JVM tests (directly satisfies the issue's validation requirement).

### Out of scope (explicit)
- **Persistent FGS step notification** (`StepNotificationManager`) — it is an *ongoing*, foreground-service-
  required notification, not a proactive ping. Suppressing it would break the foreground-service contract.
- **Milestone "new best wave" alerts** (`MilestoneNotificationManager`) — reactive achievement
  acknowledgement, not a proactive/scheduled ping. The issue names "reminder & supply-drop" specifically.
- **User-configurable quiet-hours / Settings UI** — decided fixed-default (smaller surface; users still
  have per-channel toggles + Android system notification controls). Configurable window is a possible
  post-v1.0 nicety.
- **Capping supply-drop *generation*** — the drop is still generated + persisted to the inbox (claimable
  in-app); only the *push notification* is gated. No economy / anti-cheat / `GenerateSupplyDrop` change.

## Architecture

### New pure-domain helper — the testable seam

```
domain/notification/NotificationPolicy.kt   (NEW — pure Kotlin, java.time.* only, zero Android)
```

This keeps the *decision logic* pure (JVM-testable on the fast lane, no emulator) and leaves the two
Android `*Manager` classes as thin glue — consistent with #217's observation that these managers are
untested Android glue, and with the existing pattern of pure decision cores (`domain/time/TimeIntegrity`,
`domain/battle/engine/SimulationMath`). `DomainPurityTest` enforces the Android-free constraint.

Public surface (object or top-level consts + functions):

```kotlin
object NotificationPolicy {
    val QUIET_HOURS_START: LocalTime = LocalTime.of(22, 0)   // 22:00 local
    val QUIET_HOURS_END: LocalTime = LocalTime.of(8, 0)      // 08:00 local
    const val SUPPLY_DROP_NOTIFICATION_DAILY_CAP: Int = 3

    /** True when [now] falls inside the quiet-hours window. Handles the midnight-crossing
     *  window (START > END): inside == now >= START || now < END. */
    fun isWithinQuietHours(now: LocalTime): Boolean

    /** Reminder may fire iff outside quiet hours. (Existing 1/day + 4h-inactivity gates stay in
     *  the manager.) */
    fun canSendReminder(now: LocalTime): Boolean = !isWithinQuietHours(now)

    /** Supply-drop push may fire iff outside quiet hours AND under the per-day cap. */
    fun canSendSupplyDropNotification(now: LocalTime, sentToday: Int): Boolean =
        !isWithinQuietHours(now) && sentToday < SUPPLY_DROP_NOTIFICATION_DAILY_CAP
}
```

**Quiet-hours boundary semantics (made explicit to remove ambiguity):**
- Window is `[START, END)` across midnight: `START = 22:00` inclusive, `END = 08:00` exclusive.
- `isWithinQuietHours` = `now >= START || now < END` (because START > END, i.e. the window wraps midnight).
- Therefore: `21:59:59` → allowed; `22:00:00` → quiet; `07:59:59` → quiet; `08:00:00` → allowed.

### Wiring path 1 — reminders

`SmartReminderManager.checkAndNotify()` gains one gate **before** building/sending the notification:

```kotlin
if (NotificationPolicy.isWithinQuietHours(LocalTime.now())) return
```

Placed alongside the existing early-returns (after the enabled-check / `last_sent`/inactivity checks is
fine; ordering relative to those is behavior-neutral since all are early-returns). No counter needed —
the existing `last_sent == today` already caps reminders at 1/day.

### Wiring path 2 — supply drops

`SupplyDropNotificationManager.notify(drop)` gains a quiet-hours + daily-cap gate, with a per-day sent
counter persisted in its own `SharedPreferences` (mirrors `SmartReminderManager`'s `PREFS`/`last_sent`
idiom — no Room change):

```kotlin
// inside notify(drop), after the existing isSupplyDropsEnabled() check:
val nowDateTime = LocalDateTime.now()
val today = nowDateTime.toLocalDate().toString()
val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
val storedDate = prefs.getString(KEY_DATE, "")
val sentToday = if (storedDate == today) prefs.getInt(KEY_COUNT, 0) else 0
if (!NotificationPolicy.canSendSupplyDropNotification(nowDateTime.toLocalTime(), sentToday)) return
// ...build + notificationManager.notify(...) as today...
prefs.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, sentToday + 1).apply()
```

Counter reset is implicit: a new local date makes `storedDate != today`, so `sentToday` reads 0 and the
next successful send writes the new date with count 1. `LocalDateTime.now()` is read **once** and split
into date (counter bucket) + time (quiet-hours) so date and time can't straddle a tick.

**Note on the existing inbox cap:** `DailyStepManager` already calls
`walkingEncounterRepository.enforceInboxCap(GenerateSupplyDrop.MAX_INBOX)` before persisting — that bounds
the in-app inbox and is unrelated to (and unchanged by) this notification cap. The drop is still created
and claimable when the push is suppressed.

## Data flow

```
StepSyncWorker (15-min)
  └─ SmartReminderManager.checkAndNotify()
       enabled? → last_sent!=today? → 4h-inactive? → [NEW] !quietHours? → notify()

DailyStepManager.runFollowOnPipeline()  (during active walking)
  └─ GenerateSupplyDrop → createDrop (persisted to inbox)   ← UNCHANGED, always runs
       └─ SupplyDropNotificationManager.notify(drop)
            enabled? → [NEW] !quietHours && sentToday<cap? → notify() + increment counter
```

## Error handling

- The helper is pure and total (no exceptions; pure `LocalTime` comparisons).
- `SupplyDropNotificationManager.notify` is already invoked inside `DailyStepManager`'s
  `try/catch (supply-drop)` follow-on block — the added SharedPreferences read/write inherits that
  protection; a prefs failure cannot crash the pipeline (and would, at worst, fail to gate one push).
- No new threading: `notify` runs on whatever thread the pipeline uses today (unchanged); SharedPreferences
  access is synchronous read + async `apply()`, matching `SmartReminderManager`.

## Testing

`NotificationPolicyTest` (new, pure JVM — `app/src/test/.../domain/notification/`):

- **Quiet-hours boundaries:** `21:59` allowed, `22:00` quiet, `23:30` quiet, `00:00` quiet, `07:59` quiet,
  `08:00` allowed, `12:00` allowed (covers the midnight-crossing window).
- **Supply-drop cap:** `sentToday = 0..cap-1` → allowed (outside quiet hours); `sentToday = cap` and
  `cap+1` → suppressed; suppressed inside quiet hours regardless of count.
- **Reminder gate:** allowed outside quiet hours, suppressed inside.

This directly satisfies the issue's stated validation ("a test asserting notifications are suppressed
inside quiet hours and past the daily cap"). The Android `*Manager` glue stays at the existing (untested,
#217-tracked) coverage level — no emulator/Robolectric test added for the managers in this slice.

Estimated **+~10–12 JVM tests** → headline `1256 → ~1267`.

## Files touched

| File | Change |
|---|---|
| `domain/notification/NotificationPolicy.kt` | **NEW** — pure decision helper + constants |
| `service/SmartReminderManager.kt` | add quiet-hours early-return |
| `service/SupplyDropNotificationManager.kt` | add quiet-hours + daily-cap gate + per-day SharedPreferences counter |
| `app/src/test/.../domain/notification/NotificationPolicyTest.kt` | **NEW** — pure JVM tests |

No Room/schema change. No new dependency. No DI change (helper is a stateless object; managers already
have `@ApplicationContext` for SharedPreferences). No string-resource change (no new user-facing copy —
suppression is silent).

## Risks & mitigations

- **Risk:** quiet-hours computed from device-local time can be wrong if the clock is tampered. *Mitigation:*
  acceptable — worst case is a mis-timed *informational* push; this path grants nothing and has no
  economy/anti-cheat effect (unlike the time-axis anti-cheat in `TimeIntegrity`). No clock-integrity
  coupling needed.
- **Risk:** midnight-crossing boundary off-by-one. *Mitigation:* explicit `[START, END)` semantics + boundary
  tests at 22:00/08:00/00:00.
- **Risk:** SharedPreferences counter race (pipeline is single-threaded today, but `notify` is `@Singleton`).
  *Mitigation:* the supply-drop pipeline runs serially under `DailyStepManager`'s #120 mutex; even a benign
  race only mis-counts a push by one — no correctness/economy impact. Not worth a lock here.

## Open questions

None — quiet-hours window (22:00–08:00) and cap (3/day) confirmed with the developer.
