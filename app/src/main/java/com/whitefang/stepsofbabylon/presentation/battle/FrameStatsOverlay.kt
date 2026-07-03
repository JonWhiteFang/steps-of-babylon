package com.whitefang.stepsofbabylon.presentation.battle

import android.graphics.Canvas
import android.graphics.Paint

/**
 * #384 (`perf-2`): draws the DEBUG frame-stats overlay ([FrameStats.Snapshot]) top-left on the battle
 * canvas. **DEBUG-only** — the caller ([GameLoopThread]) gates every use behind `BuildConfig.DEBUG`, so
 * this class and its work never reach a release build (R8 strips the dead branch).
 *
 * Loop-thread-confined: only the game-loop thread ever calls [draw], so the cached [Paint]s need no
 * synchronization. The `Paint`s are created ONCE here (not per frame) — per the #26 A31 "no per-frame
 * Paint allocation" fragile-zone rule.
 *
 * `open` only so `GameLoopThreadGuardTest` can inject a throwing overlay to pin the #190 crash-guard
 * coverage over the DEBUG draw path (#384 / SCOPE-4). Production always uses this concrete implementation.
 */
open class FrameStatsOverlay {
    private val textPaint =
        Paint().apply {
            color = 0xFF00FF00.toInt() // bright green — dev-only, high contrast over any biome
            textSize = 28f
            isAntiAlias = true
        }

    // Semi-opaque backing so the text stays legible over a busy background.
    private val bgPaint =
        Paint().apply {
            color = 0x99000000.toInt()
        }

    /** Draws the snapshot as a few lines top-left. No-op if [snapshot] is null (first window not yet closed). */
    open fun draw(
        canvas: Canvas,
        snapshot: FrameStats.Snapshot?,
    ) {
        val s = snapshot ?: return
        val lines =
            listOf(
                "UPS ~%.0f  (drop %d/win)".format(s.ups, s.dropped),
                "ms min %.1f  avg %.1f  max %.1f".format(s.minMs, s.avgMs, s.maxMs),
            )
        val x = 16f
        var y = 40f
        val lineH = textPaint.textSize + 8f
        // Backing rectangle sized to the widest line.
        val widest = lines.maxOf { textPaint.measureText(it) }
        canvas.drawRect(x - 8f, y - textPaint.textSize, x + widest + 8f, y + lineH * (lines.size - 1) + 8f, bgPaint)
        lines.forEach { line ->
            canvas.drawText(line, x, y, textPaint)
            y += lineH
        }
    }
}
