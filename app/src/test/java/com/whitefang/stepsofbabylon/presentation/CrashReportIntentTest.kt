package com.whitefang.stepsofbabylon.presentation

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// @Config MANDATORY: all Robolectric tests in this repo pin sdk=34 + a plain Application (there is
// no robolectric.properties). Without it, Robolectric 4.16.1 infers targetSdk=36 (> its max API 35)
// and errors, and it would boot the @HiltAndroidApp StepsOfBabylonApp (Hilt injection → crash under a
// plain runner). Matches OnboardingScreenTest.kt:40 exactly.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrashReportIntentTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `builds an ACTION_SENDTO mailto intent with the breadcrumb in subject and body`() {
        val intent =
            buildCrashReportIntent(
                context = context,
                exceptionClass = "java.lang.IllegalStateException",
                message = "boom",
                stackPreview = "at Foo.bar(Foo.kt:1)",
            )

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertTrue(
            "recipient must be the support address",
            intent.data?.schemeSpecificPart?.contains("jonwhitefang@gmail.com") == true,
        )
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue("body carries exception class", body.contains("java.lang.IllegalStateException"))
        assertTrue("body carries message", body.contains("boom"))
        assertTrue("body carries stack preview", body.contains("at Foo.bar(Foo.kt:1)"))
        assertTrue(
            "subject set",
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.isNotBlank() == true,
        )
    }
}
