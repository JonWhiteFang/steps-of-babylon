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
