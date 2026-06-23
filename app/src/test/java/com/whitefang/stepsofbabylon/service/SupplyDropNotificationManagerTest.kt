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

    /** Builds a manager whose clock is [time]. */
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
