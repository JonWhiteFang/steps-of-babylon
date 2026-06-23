# Notification Quiet-Hours + Daily-Cap (#216) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixed quiet-hours window (22:00–08:00 local) to the reminder + supply-drop notification paths, plus a 3/day cap on supply-drop *pushes* (the drop is still generated + claimable in-app), to satisfy issue #216 (Play "disruptive notifications" + retention).

**Architecture:** A new pure-domain `NotificationPolicy` object holds the decision logic (constants + `isWithinQuietHours`/`canSendReminder`/`canSendSupplyDropNotification`), JVM-testable with zero Android. `SmartReminderManager` gains one early-return through `canSendReminder`. `SupplyDropNotificationManager` injects the existing `TimeProvider` seam, field-caches its `SharedPreferences` (it runs under the #120 credit mutex — no disk load inside `notify()`), and gates the push on quiet-hours + a per-day counter.

**Tech Stack:** Kotlin, Clean Architecture (`domain/` is Android-free, enforced by `DomainPurityTest`), Hilt DI, JUnit Jupiter (pure JVM tests), JUnit 4 + Robolectric (Android-glue tests), `./run-gradle.sh testDebugUnitTest`.

**Spec:** `docs/superpowers/specs/2026-06-23-notification-quiet-hours-daily-cap-216-design.md` (adversarial-review-passed: 3 minor survivors folded in).

**Branch:** `feat/216-notification-quiet-hours-daily-cap` (already created; spec already committed).

---

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicy.kt` | **NEW** — pure decision helper: quiet-hours window + supply-drop cap constants + 3 decision functions. Zero Android. |
| `app/src/test/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicyTest.kt` | **NEW** — pure JVM tests (JUnit Jupiter) for the helper. |
| `app/src/main/java/com/whitefang/stepsofbabylon/service/SmartReminderManager.kt` | **MODIFY** — add quiet-hours early-return via `canSendReminder`. |
| `app/src/main/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManager.kt` | **MODIFY** — inject `TimeProvider`, field-cache prefs, add quiet-hours + cap gate + per-day counter. |
| `app/src/test/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManagerTest.kt` | **NEW** — Robolectric wiring test (JUnit 4) for the cap/counter/quiet-hours behavior. |

No Room/schema change. No new dependency. No string-resource change.

---

## Task 1: Pure `NotificationPolicy` helper (TDD)

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicy.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicyTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicyTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Pure-JVM tests for [NotificationPolicy] — the quiet-hours + supply-drop-cap decision logic
 * for issue #216. No Android, no Robolectric. The manager wiring that consumes these decisions
 * is covered separately by SupplyDropNotificationManagerTest (Robolectric).
 */
class NotificationPolicyTest {
    // ---- Quiet-hours boundaries (window is [22:00, 08:00) across midnight) ----

    @Test
    fun `21-59 is outside quiet hours`() {
        assertFalse(NotificationPolicy.isWithinQuietHours(LocalTime.of(21, 59)))
    }

    @Test
    fun `22-00 start is inside quiet hours (inclusive)`() {
        assertTrue(NotificationPolicy.isWithinQuietHours(LocalTime.of(22, 0)))
    }

    @Test
    fun `23-30 is inside quiet hours`() {
        assertTrue(NotificationPolicy.isWithinQuietHours(LocalTime.of(23, 30)))
    }

    @Test
    fun `midnight is inside quiet hours`() {
        assertTrue(NotificationPolicy.isWithinQuietHours(LocalTime.MIDNIGHT))
    }

    @Test
    fun `07-59 is inside quiet hours`() {
        assertTrue(NotificationPolicy.isWithinQuietHours(LocalTime.of(7, 59)))
    }

    @Test
    fun `08-00 end is outside quiet hours (exclusive)`() {
        assertFalse(NotificationPolicy.isWithinQuietHours(LocalTime.of(8, 0)))
    }

    @Test
    fun `midday is outside quiet hours`() {
        assertFalse(NotificationPolicy.isWithinQuietHours(LocalTime.of(12, 0)))
    }

    // ---- Reminder gate (canSendReminder == !quiet) ----

    @Test
    fun `canSendReminder true outside quiet hours`() {
        assertTrue(NotificationPolicy.canSendReminder(LocalTime.of(12, 0)))
    }

    @Test
    fun `canSendReminder false inside quiet hours`() {
        assertFalse(NotificationPolicy.canSendReminder(LocalTime.of(23, 0)))
    }

    // ---- Supply-drop cap (outside quiet hours AND sentToday < cap) ----

    @Test
    fun `supply drop allowed at zero sent`() {
        assertTrue(NotificationPolicy.canSendSupplyDropNotification(LocalTime.of(12, 0), sentToday = 0))
    }

    @Test
    fun `supply drop allowed one below cap`() {
        assertTrue(
            NotificationPolicy.canSendSupplyDropNotification(
                LocalTime.of(12, 0),
                sentToday = NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP - 1,
            ),
        )
    }

    @Test
    fun `supply drop blocked at cap`() {
        assertFalse(
            NotificationPolicy.canSendSupplyDropNotification(
                LocalTime.of(12, 0),
                sentToday = NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP,
            ),
        )
    }

    @Test
    fun `supply drop blocked above cap`() {
        assertFalse(
            NotificationPolicy.canSendSupplyDropNotification(
                LocalTime.of(12, 0),
                sentToday = NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP + 1,
            ),
        )
    }

    @Test
    fun `supply drop blocked inside quiet hours even under cap`() {
        assertFalse(NotificationPolicy.canSendSupplyDropNotification(LocalTime.of(23, 0), sentToday = 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.notification.NotificationPolicyTest"`
Expected: FAIL — compile error / unresolved reference `NotificationPolicy` (class does not exist yet).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicy.kt`:

```kotlin
package com.whitefang.stepsofbabylon.domain.notification

import java.time.LocalTime

/**
 * Pure-domain decision logic for suppressing proactive notifications (issue #216 / NOTIF-1).
 *
 * Two concerns, applied to the reminder + supply-drop notification paths:
 *  - **Quiet hours** — a fixed local-time window during which no proactive push fires (Play
 *    "disruptive notifications" + retention).
 *  - **Supply-drop daily cap** — a per-day limit on supply-drop *pushes* (the drop itself is still
 *    generated and claimable in-app; only the notification is gated).
 *
 * Android-free by design (the seam is JVM-testable; `architecture/DomainPurityTest` enforces that
 * `domain/` imports no Android — `java.time` is permitted, see TimeProvider.kt). The Android
 * managers (`SmartReminderManager`, `SupplyDropNotificationManager`) read the wall clock and own the
 * per-day counter; this object only decides.
 */
object NotificationPolicy {
    /** Quiet hours run [QUIET_HOURS_START, QUIET_HOURS_END) in the device's local time, across midnight. */
    val QUIET_HOURS_START: LocalTime = LocalTime.of(22, 0) // 22:00 local, inclusive
    val QUIET_HOURS_END: LocalTime = LocalTime.of(8, 0) // 08:00 local, exclusive

    /** Max supply-drop *notifications* per local day. The drop is still generated past this. */
    const val SUPPLY_DROP_NOTIFICATION_DAILY_CAP: Int = 3

    /**
     * True when [now] falls inside the quiet-hours window. Robust to either window orientation:
     * a normal window (START <= END) is a half-open interval; a midnight-crossing window
     * (START > END, today's config) is its complement-style union. For START=22:00 > END=08:00:
     * inside == now >= 22:00 || now < 08:00.
     */
    fun isWithinQuietHours(now: LocalTime): Boolean =
        if (QUIET_HOURS_START <= QUIET_HOURS_END) {
            now >= QUIET_HOURS_START && now < QUIET_HOURS_END
        } else {
            now >= QUIET_HOURS_START || now < QUIET_HOURS_END
        }

    /** A reminder may fire iff outside quiet hours. (The 1/day + 4h-inactivity gates stay in the manager.) */
    fun canSendReminder(now: LocalTime): Boolean = !isWithinQuietHours(now)

    /** A supply-drop push may fire iff outside quiet hours AND under the per-day cap. */
    fun canSendSupplyDropNotification(
        now: LocalTime,
        sentToday: Int,
    ): Boolean = !isWithinQuietHours(now) && sentToday < SUPPLY_DROP_NOTIFICATION_DAILY_CAP
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.domain.notification.NotificationPolicyTest"`
Expected: PASS (14 tests).

- [ ] **Step 5: Verify domain purity still holds**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.architecture.DomainPurityTest"`
Expected: PASS (the new `domain/notification/` file imports only `java.time.LocalTime`, which is allowed).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicy.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/domain/notification/NotificationPolicyTest.kt
git commit -m "feat(#216): add pure NotificationPolicy (quiet-hours + supply-drop cap)"
```

---

## Task 2: Wire the reminder path (`SmartReminderManager`)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/service/SmartReminderManager.kt`

No new test: the decision (`canSendReminder`) is already covered by `NotificationPolicyTest`; the manager's broader Android-glue coverage is #217-tracked (per spec). This task is the wiring only.

- [ ] **Step 1: Add imports**

In `SmartReminderManager.kt`, add to the import block (it already imports `java.time.LocalDate`):

```kotlin
import com.whitefang.stepsofbabylon.domain.notification.NotificationPolicy
import java.time.LocalTime
```

- [ ] **Step 2: Add the quiet-hours early-return**

In `checkAndNotify()`, immediately after the existing enabled-check (currently the first line of the body), add the quiet-hours gate. The current first two lines are:

```kotlin
        suspend fun checkAndNotify() {
            if (!notificationPreferences.isSmartRemindersEnabled()) return

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
```

Change to:

```kotlin
        suspend fun checkAndNotify() {
            if (!notificationPreferences.isSmartRemindersEnabled()) return
            // #216: no reminder during quiet hours. Routed through canSendReminder (not
            // isWithinQuietHours) so the unit-tested decision fn is the one on the shipping path.
            // Placed before the last_sent write below, so a suppressed reminder doesn't burn the
            // day's slot. (This path runs from StepSyncWorker, NOT under the #120 credit mutex, so
            // its in-method getSharedPreferences is not lock-sensitive — unlike the supply-drop path.)
            if (!NotificationPolicy.canSendReminder(LocalTime.now())) return

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
```

- [ ] **Step 3: Verify it compiles**

Run: `./run-gradle.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/service/SmartReminderManager.kt
git commit -m "feat(#216): suppress smart reminders during quiet hours"
```

---

## Task 3: Wire the supply-drop path + Robolectric test (TDD)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManager.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManagerTest.kt`

> ⚠️ This is the genuinely-new logic: it runs inside `DailyStepManager.runFollowOnPipeline` under the #120 credit mutex, so the prefs instance is **field-cached** (no disk load inside `notify()`), and the clock is **injected via `TimeProvider`** so the cap/counter is deterministically testable.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManagerTest.kt`:

```kotlin
package com.whitefang.stepsofbabylon.service

import android.app.NotificationManager
import android.content.Context
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import com.whitefang.stepsofbabylon.domain.notification.NotificationPolicy
import com.whitefang.stepsofbabylon.fakes.FakeTimeProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

/**
 * Robolectric wiring test for [SupplyDropNotificationManager]'s #216 quiet-hours + daily-cap gate.
 * The pure decision logic is covered by NotificationPolicyTest; this proves the manager actually
 * suppresses the push (and advances the per-day counter) — i.e. "feature works", not just
 * "policy correct".
 *
 * The default time zone is pinned to UTC so the injected FakeTimeProvider's Instant maps to a
 * predictable local time (the production code reads quiet-hours in the device default zone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SupplyDropNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var prefs: NotificationPreferences
    private lateinit var time: FakeTimeProvider
    private lateinit var nm: NotificationManager
    private var nextId = 1

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        context = RuntimeEnvironment.getApplication()
        prefs = NotificationPreferences(context)
        time = FakeTimeProvider()
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(null)
    }

    /** Builds a manager whose clock is [time]; each call posts a distinct notification id. */
    private fun manager() = SupplyDropNotificationManager(context, prefs, time)

    private fun drop() =
        SupplyDrop(
            id = nextId++,
            trigger = SupplyDropTrigger.STEP_THRESHOLD,
            reward = SupplyDropReward.STEPS,
            rewardAmount = 100,
            claimed = false,
            createdAt = 0L,
        )

    /** Sets the fake clock to a given UTC local time on a fixed date. */
    private fun atUtc(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) {
        time.fixedInstant = Instant.parse("%sT%02d:%02d:00Z".format(date, hour, minute))
        time.fixedDate = date
    }

    private fun activeCount() = shadowOf(nm).size()

    @Test
    fun `posts when outside quiet hours and under cap`() {
        atUtc(LocalDate.of(2026, 5, 7), hour = 12)
        manager().notify(drop())
        assertEquals(1, activeCount())
    }

    @Test
    fun `does not post inside quiet hours`() {
        atUtc(LocalDate.of(2026, 5, 7), hour = 23)
        manager().notify(drop())
        assertEquals(0, activeCount())
    }

    @Test
    fun `enforces the daily cap`() {
        atUtc(LocalDate.of(2026, 5, 7), hour = 12)
        val mgr = manager()
        repeat(NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP) { mgr.notify(drop()) }
        assertEquals(NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP, activeCount())

        // One past the cap on the same day: suppressed (count unchanged).
        mgr.notify(drop())
        assertEquals(NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP, activeCount())
    }

    @Test
    fun `resets the count when the day rolls over`() {
        atUtc(LocalDate.of(2026, 5, 7), hour = 12)
        val mgr = manager()
        repeat(NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP) { mgr.notify(drop()) }
        assertEquals(NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP, activeCount())

        // Next local day: the counter resets, so a push fires again.
        atUtc(LocalDate.of(2026, 5, 8), hour = 12)
        mgr.notify(drop())
        assertEquals(NotificationPolicy.SUPPLY_DROP_NOTIFICATION_DAILY_CAP + 1, activeCount())
    }

    @Test
    fun `does not post when supply drops are disabled`() {
        prefs.setSupplyDropsEnabled(false)
        atUtc(LocalDate.of(2026, 5, 7), hour = 12)
        manager().notify(drop())
        assertEquals(0, activeCount())
    }
}
```

> **Note on `SupplyDropReward.STEPS`:** confirmed against `domain/model/SupplyDropReward.kt` — the enum
> is `{ STEPS, GEMS, POWER_STONES, CARD_COPY }`, so `STEPS` is valid. The reward value is not part of the
> notification gate; any constant works.

- [ ] **Step 2: Run test to verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.service.SupplyDropNotificationManagerTest"`
Expected: FAIL — `SupplyDropNotificationManager` constructor takes 2 args, not 3 (no `TimeProvider` param yet), so the test won't compile / the `manager()` call is unresolved.

- [ ] **Step 3: Modify `SupplyDropNotificationManager` — imports + constructor**

In `SupplyDropNotificationManager.kt`, add imports:

```kotlin
import com.whitefang.stepsofbabylon.domain.notification.NotificationPolicy
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import java.time.ZoneId
```

Add the `TimeProvider` constructor param (Hilt resolves it from the existing `di/TimeModule` binding). The current constructor is:

```kotlin
class SupplyDropNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val notificationPreferences: NotificationPreferences,
    ) {
```

Change to:

```kotlin
class SupplyDropNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val notificationPreferences: NotificationPreferences,
        private val timeProvider: TimeProvider,
    ) {
```

- [ ] **Step 4: Add the companion constants + field-cached prefs**

The current companion + fields are:

```kotlin
        companion object {
            const val CHANNEL_ID = "supply_drops"
            private const val BASE_NOTIFICATION_ID = 2000
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
```

Change to:

```kotlin
        companion object {
            const val CHANNEL_ID = "supply_drops"
            private const val BASE_NOTIFICATION_ID = 2000
            private const val PREFS = "supply_drop_notifications"
            private const val KEY_DATE = "sent_date"
            private const val KEY_COUNT = "sent_count"
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // #216: field-cached so the one-time SharedPreferences disk load happens at construction
        // (@Singleton, off the #120 credit-mutex path) — NOT inside notify(), which runs under that
        // mutex via DailyStepManager.runFollowOnPipeline. Per-call get/put is then in-memory.
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
```

- [ ] **Step 5: Add the gate + counter to `notify(drop)`**

The current `notify` body starts:

```kotlin
        fun notify(drop: SupplyDrop) {
            if (!notificationPreferences.isSupplyDropsEnabled()) return
            val intent =
```

Change the top of the method to add the gate, and add the counter write after the existing `notificationManager.notify(...)` call. Full method becomes:

```kotlin
        fun notify(drop: SupplyDrop) {
            if (!notificationPreferences.isSupplyDropsEnabled()) return

            // #216: quiet-hours + per-day cap. Read the wall clock ONCE (via the injected TimeProvider
            // seam) and split into local date (counter bucket) + local time (quiet-hours) so they can't
            // straddle a tick. The drop itself is already generated + persisted by the caller — only
            // this push is gated.
            val zoned = timeProvider.now().atZone(ZoneId.systemDefault())
            val today = zoned.toLocalDate().toString()
            val storedDate = prefs.getString(KEY_DATE, "")
            val sentToday = if (storedDate == today) prefs.getInt(KEY_COUNT, 0) else 0
            if (!NotificationPolicy.canSendSupplyDropNotification(zoned.toLocalTime(), sentToday)) return

            val intent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra("navigate_to", "supplies")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pending =
                PendingIntent.getActivity(
                    context,
                    drop.id,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle(context.getString(R.string.notif_supply_title))
                    .setContentText(drop.trigger.message)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(BASE_NOTIFICATION_ID + drop.id, notification)

            // Advance the per-day counter only after a real send (implicit reset on a new date).
            prefs.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, sentToday + 1).apply()
        }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests "com.whitefang.stepsofbabylon.service.SupplyDropNotificationManagerTest"`
Expected: PASS (5 tests).

- [ ] **Step 7: Build the whole app module (catches the Hilt constructor change + any other call site)**

Run: `./run-gradle.sh :app:assembleDebug > /tmp/216-build.log 2>&1 && tail -n 5 /tmp/216-build.log`
Expected: BUILD SUCCESSFUL. (`DailyStepManager` injects `SupplyDropNotificationManager` via Hilt — the added `TimeProvider` param is resolved automatically; no manual construction site exists in `main/`.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManager.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/service/SupplyDropNotificationManagerTest.kt
git commit -m "feat(#216): quiet-hours + daily-cap gate on supply-drop notifications"
```

---

## Task 4: Full verification + lint

**Files:** none (verification only).

- [ ] **Step 1: Run the full JVM unit suite**

Run: `./run-gradle.sh :app:testDebugUnitTest > /tmp/216-test.log 2>&1 && tail -n 15 /tmp/216-test.log`
Expected: BUILD SUCCESSFUL, 0 failures. Note the new total test count (was 1256; expect ~1275 with the 14 + 5 new tests).

- [ ] **Step 2: Record the exact test count**

Run: `grep -rEc "@Test" app/src/test/java | awk -F: '{s+=$2} END {print s}'`
(or read the gradle report). Capture the number — it feeds the CLAUDE.md headline update in Task 5.

- [ ] **Step 3: Run detekt + ktlint (CI-gated)**

Run: `./run-gradle.sh :app:detekt > /tmp/216-detekt.log 2>&1 && tail -n 5 /tmp/216-detekt.log`
Then: `./lint-kotlin.sh` (formatting check; use `./lint-kotlin.sh --format` to auto-fix, then re-stage + amend the relevant commit if it changes anything).
Expected: both clean (baseline-gated — fails only on NEW violations).

- [ ] **Step 4: If lint auto-fixed anything, commit it**

```bash
git add -A && git commit -m "style(#216): ktlint format" || echo "nothing to format"
```

---

## Task 5: Sync current-state docs + STATE/RUN_LOG (PR Task-List Convention)

**Files:**
- Modify: `CLAUDE.md` (headline test count — only if it changed)
- Modify: `CHANGELOG.md` (add an `[Unreleased]` entry)
- Modify: `docs/steering/source-files.md` (new files)
- Modify: `docs/agent/STATE.md` (snapshot)
- Append: `docs/agent/RUN_LOG.md` (session entry)

> Per CLAUDE.md's PR Task-List Convention: sync current-state docs FIRST, then STATE/RUN_LOG, then the final commit. Touch a doc only if this PR actually invalidates it. No schema change → do NOT touch `docs/database-schema.md`. No dep/convention change → do NOT touch `tech.md`/`lib-*.md`. No new module/dir → `structure.md` only if you judge `domain/notification/` worth noting (a new domain sub-package — add a one-line mention).

- [ ] **Step 1: Update the CLAUDE.md headline test count**

In `CLAUDE.md` → Testing section, update the line `**Headline count: 1256 JVM tests + 9 instrumented tests.**` to the new JVM count from Task 4 Step 2 (instrumented count unchanged at 9). Only edit if the number changed.

- [ ] **Step 2: Add a CHANGELOG `[Unreleased]` entry**

Under the `## [Unreleased]` section in `CHANGELOG.md`, add (match the file's existing entry style):

```markdown
### Added — Notification quiet-hours + supply-drop daily cap (#216)

Reminder and supply-drop notifications now respect a fixed local-time quiet-hours window
(22:00–08:00) and supply-drop *pushes* are capped at 3/day (the drop is still generated and
claimable in-app — only the notification is suppressed). Addresses the 2026-06-17 audit's NOTIF-1
(Play "disruptive notifications" + retention). New pure-domain `domain/notification/NotificationPolicy`
holds the decision logic (JVM-tested); `SupplyDropNotificationManager` gained an injected `TimeProvider`
seam + field-cached `SharedPreferences` (it runs under the #120 credit mutex). No economy/schema change.
+N JVM tests (NotificationPolicyTest + Robolectric SupplyDropNotificationManagerTest).
```

Replace `+N` with the actual delta. If the `[Unreleased]` block carries a running test-count line, update it.

- [ ] **Step 3: Add source-files.md entries**

In `docs/steering/source-files.md`, add entries for the two new source files (match existing format) — `domain/notification/NotificationPolicy.kt` (pure quiet-hours/cap decision helper) and note the two new test files if the doc indexes tests. Update the `SupplyDropNotificationManager.kt` / `SmartReminderManager.kt` rows to mention the #216 gate if they have rows.

- [ ] **Step 4: Update STATE.md**

In `docs/agent/STATE.md`: move the current objective to reflect #216 shipped to `[Unreleased]`; update the test-count line; add a one-line "Recently shipped" bullet. Keep it to one page. (Optionally add a short fragile-zone note that `SupplyDropNotificationManager.notify` runs under the #120 mutex so its prefs are field-cached — useful for future editors.)

- [ ] **Step 5: Append a RUN_LOG.md entry**

Append a new dated session entry to `docs/agent/RUN_LOG.md` (newest-last per the file's convention) summarizing: issue #216, the spec→adversarial-review (3 minor survivors)→plan→implement flow, files touched, test delta, no schema/economy change.

- [ ] **Step 6: Commit the docs**

```bash
git add CLAUDE.md CHANGELOG.md docs/steering/source-files.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#216): sync current-state docs + STATE/RUN_LOG for notification quiet-hours/cap"
```

---

## Task 6: Open the PR

**Files:** none.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/216-notification-quiet-hours-daily-cap
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(#216): notification quiet-hours + supply-drop daily cap" --body "$(cat <<'EOF'
Closes #216 (NOTIF-1).

## What
- New pure-domain `NotificationPolicy` (quiet hours 22:00–08:00 local; supply-drop push cap 3/day).
- `SmartReminderManager`: quiet-hours early-return via `canSendReminder`.
- `SupplyDropNotificationManager`: injected `TimeProvider` + field-cached `SharedPreferences` (runs under the #120 credit mutex) + quiet-hours/cap gate + per-day counter. The drop is still generated + claimable in-app; only the push is gated.

## Why
2026-06-17 complete-app review §18 NOTIF-1 — Play "disruptive notifications" policy + off-hours retention harm.

## Scope
Out of scope: persistent FGS step notification, milestone alerts, configurable Settings UI, capping generation. No Room/schema/economy change.

## Tests
- `NotificationPolicyTest` (pure JVM) — boundaries + cap + reminder gate.
- `SupplyDropNotificationManagerTest` (Robolectric) — posts/suppresses/cap/day-rollover/disabled.
- Full suite green: <N> JVM tests, 0 failures. detekt + ktlint clean.

## Process
Spec → Adversarial Review Gate (7 findings → 3 minor confirmed / 4 refuted; all 3 folded in) → plan → TDD implement. Spec: `docs/superpowers/specs/2026-06-23-notification-quiet-hours-daily-cap-216-design.md`.
EOF
)"
```

Replace `<N>` with the actual test count.

---

## Self-review notes (for the executor)

- **Spec coverage:** quiet-hours (both paths) → Tasks 2 + 3; supply-drop cap → Task 3; pure helper + tests → Task 1; Robolectric wiring test (survivor #3) → Task 3; `canSendReminder` wrapper on shipping path (survivor #2) → Task 2; field-cached prefs (survivor #1) → Task 3 Step 4; `TimeProvider` injection → Task 3 Step 3. All spec sections map to a task.
- **Type consistency:** `NotificationPolicy` API names (`isWithinQuietHours`, `canSendReminder`, `canSendSupplyDropNotification`, `SUPPLY_DROP_NOTIFICATION_DAILY_CAP`, `QUIET_HOURS_START/END`) are used identically in Tasks 1 and 3. `SupplyDropNotificationManager` 3-arg constructor in Task 3 Step 3 matches the test's `manager()` in Step 1.
- **Verify-before-assert:** `SupplyDropReward.STEPS` is flagged in Task 3 Step 1 to confirm against the real enum before running (the only externally-named symbol the plan can't fully guarantee).
```
