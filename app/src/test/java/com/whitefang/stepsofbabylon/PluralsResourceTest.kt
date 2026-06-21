package com.whitefang.stepsofbabylon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #259: pins that each new plural selects the `one` form at n=1 and `other` at n≥2. This is the
 * regression guard for the grammatical bug (e.g. "+1 Step" not "+1 Steps", "1 day" not "1 days").
 * Robolectric reads the real res/values/plurals.xml (unitTests.isIncludeAndroidResources = true).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PluralsResourceTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun `fx_step_reward one vs other`() {
        assertEquals("+1 Step", res.getQuantityString(R.plurals.fx_step_reward, 1, 1))
        assertEquals("+3 Steps", res.getQuantityString(R.plurals.fx_step_reward, 3, 3))
    }

    @Test fun `wave_enemies one vs other`() {
        assertEquals("1 enemy", res.getQuantityString(R.plurals.wave_enemies, 1, 1))
        assertEquals("5 enemies", res.getQuantityString(R.plurals.wave_enemies, 5, 5))
    }

    @Test fun `boss_in_waves one is special-cased and other is plural`() {
        assertEquals("Boss next wave", res.getQuantityString(R.plurals.boss_in_waves, 1, 1))
        assertEquals("Boss in 2 waves", res.getQuantityString(R.plurals.boss_in_waves, 2, 2))
    }

    @Test fun `reward plurals one vs other`() {
        assertEquals("+1 Gem", res.getQuantityString(R.plurals.reward_gems, 1, 1))
        assertEquals("+2 Gems", res.getQuantityString(R.plurals.reward_gems, 2, 2))
        assertEquals("+1 Power Stone", res.getQuantityString(R.plurals.reward_power_stones, 1, 1))
        assertEquals("+1 Step", res.getQuantityString(R.plurals.reward_steps, 1, 1))
    }

    @Test fun `card_copies and days_remaining one vs other`() {
        assertEquals("+1 Copy", res.getQuantityString(R.plurals.card_copies, 1, 1))
        assertEquals("+2 Copies", res.getQuantityString(R.plurals.card_copies, 2, 2))
        assertEquals("Active — 1 day remaining", res.getQuantityString(R.plurals.days_remaining, 1, 1))
        assertEquals("Active — 5 days remaining", res.getQuantityString(R.plurals.days_remaining, 5, 5))
    }

    @Test fun `widget and notif and reminder steps one vs other`() {
        assertEquals("1 step", res.getQuantityString(R.plurals.widget_steps, 1, 1))
        assertEquals("Today: 1 step", res.getQuantityString(R.plurals.notif_today_steps, 1, 1))
        assertEquals("Today: 9 steps", res.getQuantityString(R.plurals.notif_today_steps, 9, 9))
    }

    @Test fun `page_x_of_n carries both args`() {
        assertEquals("Page 1 of 4", res.getQuantityString(R.plurals.page_x_of_n, 4, 1, 4))
    }
}
