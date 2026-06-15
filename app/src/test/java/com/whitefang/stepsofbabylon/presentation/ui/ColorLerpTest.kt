package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-JVM guard for the Bundle E (#164) onboarding gradient cross-fade. lerpArgb interpolates two
 * packed-ARGB Ints per channel; it is the math behind the per-slide biome gradient blend driven by
 * the pager scroll offset (spec E8). No Compose runtime needed — plain Int bit math.
 */
class ColorLerpTest {

    private val opaqueBlack = 0xFF000000.toInt()
    private val opaqueWhite = 0xFFFFFFFF.toInt()

    @Test
    fun `t of 0 returns the first colour`() {
        assertEquals(opaqueBlack, lerpArgb(opaqueBlack, opaqueWhite, 0f))
    }

    @Test
    fun `t of 1 returns the second colour`() {
        assertEquals(opaqueWhite, lerpArgb(opaqueBlack, opaqueWhite, 1f))
    }

    @Test
    fun `t of half is the per-channel midpoint`() {
        val mid = lerpArgb(opaqueBlack, opaqueWhite, 0.5f)
        assertEquals(0xFF, (mid ushr 24) and 0xFF, "alpha")
        assertEquals(0x7F, (mid ushr 16) and 0xFF, "red")
        assertEquals(0x7F, (mid ushr 8) and 0xFF, "green")
        assertEquals(0x7F, mid and 0xFF, "blue")
    }

    @Test
    fun `t is clamped below 0 and above 1`() {
        assertEquals(opaqueBlack, lerpArgb(opaqueBlack, opaqueWhite, -0.5f))
        assertEquals(opaqueWhite, lerpArgb(opaqueBlack, opaqueWhite, 1.5f))
    }

    @Test
    fun `each channel interpolates independently`() {
        val a = 0xFFFF0000.toInt()
        val b = 0xFF0000FF.toInt()
        val mid = lerpArgb(a, b, 0.5f)
        assertEquals(0xFF, (mid ushr 24) and 0xFF, "alpha")
        assertEquals(0x7F, (mid ushr 16) and 0xFF, "red")
        assertEquals(0x00, (mid ushr 8) and 0xFF, "green")
        assertEquals(0x7F, mid and 0xFF, "blue")
    }

    @Test
    fun `positive offset picks the next page`() {
        assertEquals(2, crossfadeNeighborIndex(page = 1, offset = 0.3f, lastIndex = 3))
    }

    @Test
    fun `negative offset at an interior page picks the previous page`() {
        assertEquals(0, crossfadeNeighborIndex(page = 1, offset = -0.3f, lastIndex = 3))
    }

    @Test
    fun `negative overscroll at page 0 clamps to the settled page`() {
        assertEquals(0, crossfadeNeighborIndex(page = 0, offset = -0.2f, lastIndex = 3))
    }

    @Test
    fun `positive overscroll at the last page clamps to the settled page`() {
        assertEquals(3, crossfadeNeighborIndex(page = 3, offset = 0.2f, lastIndex = 3))
    }
}
