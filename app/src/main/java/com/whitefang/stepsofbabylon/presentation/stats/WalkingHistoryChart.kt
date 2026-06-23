package com.whitefang.stepsofbabylon.presentation.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DAILY_CEILING = 50_000L

@Composable
fun WalkingHistoryChart(
    bars: List<DailyBarData>,
    selectedPeriod: StatsPeriod,
    onPeriodSelected: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Period toggle
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = period == selectedPeriod,
                    onClick = { onPeriodSelected(period) },
                    label = { Text(period.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.tertiary
        val ceilingColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

        val maxVal =
            (bars.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(
                if (selectedPeriod != StatsPeriod.QUARTER) DAILY_CEILING else 1L,
            )

        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val chartLeft = 40.dp.toPx()
            val chartBottom = size.height - 20.dp.toPx()
            val chartWidth = size.width - chartLeft - 8.dp.toPx()
            val chartHeight = chartBottom - 4.dp.toPx()
            val barCount = bars.size
            if (barCount == 0) return@Canvas
            val barWidth = (chartWidth / barCount) * 0.7f
            val barSpacing = chartWidth / barCount

            // Y-axis labels
            val paint =
                android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            for (i in 0..2) {
                val v = maxVal * i / 2
                val y = chartBottom - (chartHeight * i / 2f)
                drawContext.canvas.nativeCanvas.drawText(
                    if (v >= 1000) "${v / 1000}k" else "$v",
                    chartLeft - 4.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint,
                )
            }

            // Daily ceiling line (not for quarter view)
            if (selectedPeriod != StatsPeriod.QUARTER && DAILY_CEILING <= maxVal) {
                val ceilingY = chartBottom - (chartHeight * DAILY_CEILING / maxVal.toFloat())
                drawLine(
                    ceilingColor,
                    Offset(chartLeft, ceilingY),
                    Offset(size.width - 8.dp.toPx(), ceilingY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }

            // Bars
            val labelPaint =
                android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            bars.forEachIndexed { i, bar ->
                val x = chartLeft + i * barSpacing + (barSpacing - barWidth) / 2
                val sensorH = if (maxVal > 0) chartHeight * bar.sensorSteps / maxVal.toFloat() else 0f
                val equivH = if (maxVal > 0) chartHeight * bar.stepEquivalents / maxVal.toFloat() else 0f

                // Sensor steps (bottom)
                if (sensorH > 0) {
                    drawRect(primaryColor, Offset(x, chartBottom - sensorH - equivH), Size(barWidth, sensorH))
                }
                // Step equivalents (stacked on top)
                if (equivH > 0) {
                    drawRect(secondaryColor, Offset(x, chartBottom - equivH), Size(barWidth, equivH))
                }

                // Label
                drawContext.canvas.nativeCanvas.drawText(
                    bar.label,
                    x + barWidth / 2,
                    size.height - 2.dp.toPx(),
                    labelPaint,
                )
            }
        }

        // Legend
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(primaryColor, "Steps")
            // The tertiary series plots activity-derived step-equivalents (cycling, etc.), NOT
            // raw minutes — the old "Activity Minutes" label contradicted the data.
            LegendDot(secondaryColor, "Activity Steps")
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
