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

This keeps the *decision logic* pure (JVM-testable on the fast lane, no emulator), following the existing
pattern of pure decision cores (`domain/time/TimeIntegrity`, `domain/battle/engine/SimulationMath`).
`DomainPurityTest` enforces the Android-free constraint (`java.time.LocalTime` is permitted — the
forbidden list is `android.*`/`androidx.*`/`com.android.*`/`com.google.android.*`/`data`/DI only;
`TimeProvider.kt` already imports `java.time`). The two Android `*Manager` classes stay thin glue around
the helper; the supply-drop manager additionally gets a focused Robolectric wiring test for its new
cap/counter logic (see Testing), while `SmartReminderManager`'s broader glue coverage stays #217-tracked.

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

`SmartReminderManager.checkAndNotify()` gains one gate **before** building/sending the notification,
routed through the **`canSendReminder` wrapper** (not `isWithinQuietHours` directly) so the unit-tested
decision function is the one on the shipping path (review survivor #2 — keep one tested decision seam per
path, mirroring the supply-drop path's use of `canSendSupplyDropNotification`):

```kotlin
if (!NotificationPolicy.canSendReminder(LocalTime.now())) return
```

Placed alongside the existing early-returns (after the enabled-check / `last_sent`/inactivity checks is
fine; ordering relative to those is behavior-neutral since all are early-returns). The quiet-hours gate
MUST sit **before** the `prefs.edit().putString("last_sent", today)` write (`SmartReminderManager.kt:102`)
and before `notificationManager.notify` — suppression must not burn the day's slot. No counter needed —
the existing `last_sent == today` already caps reminders at 1/day. (`SmartReminderManager.checkAndNotify`
runs from `StepSyncWorker`, **not** under the #120 mutex, so its in-method `getSharedPreferences` is not
lock-sensitive — unlike the supply-drop path below.)

### Wiring path 2 — supply drops

`SupplyDropNotificationManager.notify(drop)` gains a quiet-hours + daily-cap gate, with a per-day sent
counter persisted in its own dedicated `SharedPreferences` file (mirrors `SmartReminderManager`'s
`PREFS`/`last_sent` idiom — no Room change). Two adaptations vs. a verbatim copy of `SmartReminderManager`,
both from the review:

- **Inject `TimeProvider`** (the existing `domain/time/TimeProvider` seam, Hilt-bound in `di/TimeModule`)
  into the manager's constructor, instead of calling `LocalDateTime.now()` inline. This makes the
  cap/quiet-hours behavior deterministically testable (review survivor #3 — see Testing) and uses the
  repo's established clock seam. The manager derives `LocalDate`/`LocalTime` from `timeProvider.now()`
  via the device default zone (`now().atZone(ZoneId.systemDefault())`), read **once** so date and time
  can't straddle a tick.
- **Cache the `SharedPreferences` instance as a private field** (created in the constructor / existing
  `init{}` block, like `NotificationPreferences.kt:14` and every other `@Singleton` prefs holder) — NOT
  fetched inside `notify()` (review survivor #1). `notify(drop)` is called from
  `DailyStepManager.runFollowOnPipeline` (`DailyStepManager.kt:333`) **inside the #120 credit
  `mutex.withLock`**; a first-access `getSharedPreferences` does a synchronous disk read on the calling
  thread, which would briefly stall the other concurrent step producer under the lock. Field-caching
  moves that one-time disk load off the credit path; the per-call `getString`/`getInt`/`apply()` are
  then in-memory.

```kotlin
// constructor / init: private val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
// (alongside the existing notificationManager field + channel init at kt:32-40)

// inside notify(drop), after the existing isSupplyDropsEnabled() check:
val zoned = timeProvider.now().atZone(ZoneId.systemDefault())
val today = zoned.toLocalDate().toString()
val storedDate = prefs.getString(KEY_DATE, "")
val sentToday = if (storedDate == today) prefs.getInt(KEY_COUNT, 0) else 0
if (!NotificationPolicy.canSendSupplyDropNotification(zoned.toLocalTime(), sentToday)) return
// ...build + notificationManager.notify(...) as today...
prefs.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, sentToday + 1).apply()
```

Counter reset is implicit: a new local date makes `storedDate != today`, so `sentToday` reads 0 and the
next successful send writes the new date with count 1. The increment runs **only after** a real
`notificationManager.notify(...)` — the gate (and thus the early-return on suppression) sits before both
the send and the counter write, so a suppressed push neither pings nor consumes a slot.

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
  `try/catch (supply-drop)` follow-on block — the added (field-cached) SharedPreferences read/write inherits
  that protection; a prefs failure cannot crash the pipeline (and would, at worst, fail to gate one push).
- No new threading: `notify` runs on whatever thread the pipeline uses today (unchanged); the per-call
  SharedPreferences access is an in-memory read off the field-cached instance + async `apply()`. The
  one-time disk load happens at construction (`@Singleton`, off the credit-mutex path), not in `notify`.

## Testing

Two layers — the pure policy AND the manager wiring that enforces it (review survivor #3: a pure-helper
test proves "policy correct" but not "feature works"; a forgotten counter-increment or a gate placed
after `notify(...)` would pass every helper test while leaving production un-suppressed).

**1. `NotificationPolicyTest` (new, pure JVM — `app/src/test/.../domain/notification/`):**

- **Quiet-hours boundaries:** `21:59` allowed, `22:00` quiet, `23:30` quiet, `00:00` quiet, `07:59` quiet,
  `08:00` allowed, `12:00` allowed (covers the midnight-crossing window).
- **Supply-drop cap:** `sentToday = 0..cap-1` → allowed (outside quiet hours); `sentToday = cap` and
  `cap+1` → suppressed; suppressed inside quiet hours regardless of count.
- **Reminder gate (`canSendReminder`):** allowed outside quiet hours, suppressed inside. (This is the same
  wrapper the reminder wiring now calls — survivor #2 — so the tested seam is on the shipping path.)

**2. `SupplyDropNotificationManagerTest` (new, Robolectric JVM lane):** the supply-drop path carries the
genuinely-new cap/counter logic, so it gets a focused wiring test. Use `RobolectricTestRunner` +
`@Config(sdk = [34])` (precedent: `OnboardingPreferencesTest`, `StepWidgetProviderTest`) with
`ApplicationProvider.getApplicationContext()` and a `FakeTimeProvider` to drive the clock. Assert against
a Robolectric `ShadowNotificationManager` (or a spy) that `notify(drop)`:

- posts a notification when outside quiet hours and under the cap, and **advances the counter**;
- does **not** post once `sentToday` has reached the cap (cap enforced, counter not advanced);
- does **not** post inside quiet hours;
- resets the daily count when `FakeTimeProvider`'s date rolls over;
- still posts when `isSupplyDropsEnabled()` (existing gate) is true — unchanged.

The reminder manager (`SmartReminderManager`) stays at its existing (untested, #217-tracked) coverage
level — its only change is one early-return through the helper, which `NotificationPolicyTest` covers; a
full Robolectric harness for it is deferred to #217.

This satisfies the issue's validation ("notifications are suppressed inside quiet hours and past the daily
cap") at **both** the policy and the supply-drop-wiring level.

Estimated **+~13–16 JVM tests** → headline `1256 → ~1270`.

## Files touched

| File | Change |
|---|---|
| `domain/notification/NotificationPolicy.kt` | **NEW** — pure decision helper + constants |
| `service/SmartReminderManager.kt` | add quiet-hours early-return via `canSendReminder` (before the `last_sent` write) |
| `service/SupplyDropNotificationManager.kt` | inject `TimeProvider`; field-cache `SharedPreferences`; add quiet-hours + daily-cap gate + per-day counter |
| `app/src/test/.../domain/notification/NotificationPolicyTest.kt` | **NEW** — pure JVM tests |
| `app/src/test/.../service/SupplyDropNotificationManagerTest.kt` | **NEW** — Robolectric wiring test |

No Room/schema change. No new dependency (Robolectric + `FakeTimeProvider` already in the test set;
`TimeProvider` Hilt binding already exists in `di/TimeModule`). The only DI change is one added constructor
param (`TimeProvider`) on the already-`@Inject`-constructed `SupplyDropNotificationManager` — Hilt resolves
it from the existing `TimeModule` binding. No string-resource change (no new user-facing copy — suppression
is silent).

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

## Adversarial review (2026-06-23)

Spec passed the mandatory Adversarial Review Gate (multi-agent `Workflow`: 5 code-grounded dimensions →
per-finding skeptic refute). **7 findings → 3 confirmed (all `minor`) / 4 refuted; zero critical/major.**
All 3 survivors are folded into this revision:

1. *(fragile-invariants)* `SupplyDropNotificationManager.notify` runs under the #120 credit mutex, so a
   first-access `getSharedPreferences` disk load inside `notify()` would stall the other step producer →
   **field-cache the prefs** at construction (Wiring path 2).
2. *(consistency-tests)* reminder wiring called `isWithinQuietHours` directly while the test targeted the
   `canSendReminder` wrapper → **route the wiring through `canSendReminder`** so the tested seam is on the
   shipping path (Wiring path 1).
3. *(consistency-tests)* a pure-helper-only test proves "policy correct" not "feature works" → **add a
   Robolectric `SupplyDropNotificationManagerTest`** (inject `TimeProvider`) covering the cap/counter/quiet
   -hours wiring (Testing §2).

The 4 refuted findings (default-zone reliance, independent date bucket, reminder ordering edge, prefs
literal naming) were each judged "spec already correct / no change needed" by the skeptic pass.
