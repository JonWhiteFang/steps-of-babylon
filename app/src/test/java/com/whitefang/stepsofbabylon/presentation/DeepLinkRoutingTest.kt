package com.whitefang.stepsofbabylon.presentation

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeepLinkRoutingTest {

    @Test
    fun `intent with navigate_to supplies extracts correctly`() {
        val intent = Intent().putExtra("navigate_to", "supplies")
        assertEquals("supplies", intent.getStringExtra("navigate_to"))
    }

    @Test
    fun `intent with navigate_to workshop extracts correctly`() {
        val intent = Intent().putExtra("navigate_to", "workshop")
        assertEquals("workshop", intent.getStringExtra("navigate_to"))
    }

    @Test
    fun `intent without navigate_to returns null`() {
        val intent = Intent()
        assertNull(intent.getStringExtra("navigate_to"))
    }
}
