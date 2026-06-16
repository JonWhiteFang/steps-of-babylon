package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A31 (audit finding) — the CHRONO_FIELD full-screen overlay must reuse a cached Paint instead of
 * allocating a new android.graphics.Paint every render() frame. This test proves (a) the overlay
 * draws with the expected colour + FILL style, and (b) the SAME Paint instance is used across two
 * render() calls (identity check — the allocation is gone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChronoOverlayPaintTest {

    @Test
    fun `chrono overlay reuses one Paint instance across frames`() {
        val engine = GameEngine()
        engine.init(800f, 600f)
        engine.setChronoActiveForTest(true)

        val canvas = mock<Canvas>()
        engine.render(canvas)
        engine.render(canvas)

        val paints = argumentCaptor<Paint>()
        // drawRect(left, top, right, bottom, paint) — capture the full-screen overlay paint.
        verify(canvas, org.mockito.kotlin.atLeast(2)).drawRect(
            org.mockito.kotlin.eq(0f), org.mockito.kotlin.eq(0f),
            org.mockito.kotlin.eq(800f), org.mockito.kotlin.eq(600f),
            paints.capture(),
        )
        val captured = paints.allValues
        assertEquals(0x222196F3, captured[0].color)
        assertEquals(Paint.Style.FILL, captured[0].style)
        // Identity: the overlay paint is the same object on both frames.
        assertEquals(true, captured[0] === captured[1])
    }
}
