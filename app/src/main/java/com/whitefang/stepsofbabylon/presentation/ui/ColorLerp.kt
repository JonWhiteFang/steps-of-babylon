package com.whitefang.stepsofbabylon.presentation.ui

import kotlin.math.sign

/**
 * Linear per-channel interpolation between two packed-ARGB [Int] colours (Bundle E, #164).
 *
 * Pure Int bit-math (no Compose/Android dependency) so it is JVM-unit-testable. Used by the onboarding
 * biome-gradient cross-fade (spec E8): blends the current slide's BiomeTheme sky colour toward the
 * adjacent slide's as the pager scrolls. [t] is clamped to [0,1]; channels are interpolated
 * independently with truncating rounding.
 */
fun lerpArgb(a: Int, b: Int, t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val af = (a ushr 24) and 0xFF
    val rf = (a ushr 16) and 0xFF
    val gf = (a ushr 8) and 0xFF
    val bf = a and 0xFF
    val at = (b ushr 24) and 0xFF
    val rt = (b ushr 16) and 0xFF
    val gt = (b ushr 8) and 0xFF
    val bt = b and 0xFF
    val alpha = (af + (at - af) * clamped).toInt()
    val red = (rf + (rt - rf) * clamped).toInt()
    val green = (gf + (gt - gf) * clamped).toInt()
    val blue = (bf + (bt - bf) * clamped).toInt()
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

/**
 * The neighbouring page index the pager is dragging toward, for the gradient cross-fade (spec E8).
 * [offset] is `PagerState.currentPageOffsetFraction` (signed, ~[-0.5, 0.5]): negative drags toward the
 * previous page, positive toward the next. The result is clamped to `[0, lastIndex]`, so an overscroll
 * at page 0 (negative) or at the last page (positive) returns the settled page itself (no neighbour) —
 * the caller then blends colour-to-itself (a no-op), never indexing out of range.
 */
fun crossfadeNeighborIndex(page: Int, offset: Float, lastIndex: Int): Int =
    (page + sign(offset).toInt()).coerceIn(0, lastIndex)
