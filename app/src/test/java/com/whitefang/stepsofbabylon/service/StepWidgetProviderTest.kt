package com.whitefang.stepsofbabylon.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StepWidgetProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `saveData persists steps and balance to SharedPreferences`() {
        StepWidgetProvider.saveData(context, 5000, 12000)
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        assertEquals(5000L, prefs.getLong("daily_steps", 0))
        assertEquals(12000L, prefs.getLong("balance", 0))
    }

    @Test
    fun `saveData overwrites previous values`() {
        StepWidgetProvider.saveData(context, 1000, 2000)
        StepWidgetProvider.saveData(context, 8000, 50000)
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        assertEquals(8000L, prefs.getLong("daily_steps", 0))
        assertEquals(50000L, prefs.getLong("balance", 0))
    }

    @Test
    fun `default values are zero when no data saved`() {
        val prefs = context.getSharedPreferences("widget_data_fresh", Context.MODE_PRIVATE)
        assertEquals(0L, prefs.getLong("daily_steps", 0))
        assertEquals(0L, prefs.getLong("balance", 0))
    }
}
