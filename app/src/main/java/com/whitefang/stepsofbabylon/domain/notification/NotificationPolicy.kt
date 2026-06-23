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
