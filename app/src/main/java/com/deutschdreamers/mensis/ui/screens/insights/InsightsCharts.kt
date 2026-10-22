package com.deutschdreamers.mensis.ui.screens.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deutschdreamers.mensis.ui.theme.MensisTheme

// Cycle length bars (design §6): 104 dp with 8 dp on top, 6 dp between bars, values 11/12 above the
// bars, month labels 12 dp below. Text is measured, so the chart grows with the font scale.
private val BarAreaTop = 8.dp
private val BarAreaBottom = 104.dp
private val BarGap = 6.dp
private val BarRadius = 4.dp
private val ValueGap = 4.dp
private val MonthGap = 12.dp
private val MinBarHeight = 2.dp

// Temperature chart (design §6, a 348 × 130 SVG): plot 8–112 dp, 8 dp side insets, the shift band
// 4 dp beyond the plot, day labels below it.
private val PlotTop = 8.dp
private val PlotBottom = 112.dp
private val PlotInset = 8.dp
private val BandOverhang = 4.dp
private val BandHalfWidth = 5.dp
private val DayLabelGap = 6.dp
private val DotRadius = 3.dp
private val LineWidth = 2.dp
private val Dash = 4.dp
private const val BAND_ALPHA = 0.5f

/** Last complete cycles as bars, latest in `primary`, with a dashed line at the mean. One semantics summary. */
@Composable
fun CycleLengthChart(
    chart: CycleLengthChartUi,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 12.sp,
            color = scheme.onSurfaceVariant,
        )
    val density = LocalDensity.current
    val monthHeight =
        remember(style, density) {
            with(density) {
                measurer
                    .measure("Oct", style)
                    .size.height
                    .toDp()
            }
        }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BarAreaBottom + MonthGap + monthHeight)
                .semantics { contentDescription = chart.description },
    ) {
        val slots = maxOf(chart.slots, chart.bars.size, 1)
        val gap = BarGap.toPx()
        val slotWidth = (size.width - gap * (slots - 1)) / slots
        val bottom = BarAreaBottom.toPx()
        val area = bottom - BarAreaTop.toPx()
        val span = (chart.axisMax - chart.axisMin).coerceAtLeast(1).toFloat()
        chart.bars.forEachIndexed { index, bar ->
            val left = index * (slotWidth + gap)
            val value = measure(measurer, bar.days.toString(), style, slotWidth)
            val tallest = area - value.size.height - ValueGap.toPx()
            val fraction = ((bar.days - chart.axisMin) / span).coerceIn(0f, 1f)
            val height = (fraction * area).coerceIn(MinBarHeight.toPx(), tallest.coerceAtLeast(MinBarHeight.toPx()))
            val top = bottom - height
            drawTopRoundedBar(
                left = left,
                top = top,
                width = slotWidth,
                height = height,
                color = if (bar.latest) scheme.primary else scheme.primaryContainer,
            )
            drawCentred(value, left + slotWidth / 2, top - ValueGap.toPx() - value.size.height)
            val month = measure(measurer, bar.label, style, slotWidth)
            drawCentred(month, left + slotWidth / 2, bottom + MonthGap.toPx())
        }
        chart.mean?.takeIf { chart.bars.isNotEmpty() }?.let { mean ->
            val y = bottom - ((mean - chart.axisMin) / span).toFloat().coerceIn(0f, 1f) * area
            drawLine(
                color = scheme.outline,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dash.toPx(), Dash.toPx())),
            )
        }
    }
}

/**
 * Waking temperatures of the current cycle: line and 3 dp dots in `primary`, dashed coverline, a
 * teal band on the days the shift held. Late readings are grey and left off the line.
 */
@Composable
fun TemperatureChart(
    chart: TemperatureChartUi,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = MensisTheme.colors
    val measurer = rememberTextMeasurer()
    val style =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = scheme.onSurfaceVariant,
        )
    val density = LocalDensity.current
    val labelHeight =
        remember(style, density) {
            with(density) {
                measurer
                    .measure("day 1", style)
                    .size.height
                    .toDp()
            }
        }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlotBottom + BandOverhang + DayLabelGap + labelHeight)
                .semantics { contentDescription = chart.description },
    ) {
        val inset = PlotInset.toPx()
        val top = PlotTop.toPx()
        val plotBottom = PlotBottom.toPx()
        val dayStep = (size.width - 2 * inset) / (chart.dayCount - 1).coerceAtLeast(1)
        val span = (chart.maxC - chart.minC).takeIf { it > 0 } ?: 1.0

        fun x(day: Int) = inset + (day - 1) * dayStep

        fun y(celsius: Double) =
            plotBottom - ((celsius - chart.minC) / span).toFloat().coerceIn(0f, 1f) * (plotBottom - top)

        chart.shiftDays?.let { days ->
            val left = x(days.first) - BandHalfWidth.toPx()
            val right = x(days.last) + BandHalfWidth.toPx()
            drawRoundRect(
                color = colors.fertileContainer.copy(alpha = BAND_ALPHA),
                topLeft = Offset(left, top - BandOverhang.toPx()),
                size = Size(right - left, plotBottom - top + 2 * BandOverhang.toPx()),
                cornerRadius = CornerRadius(BarRadius.toPx()),
            )
        }
        chart.coverlineC?.let { coverline ->
            val lineY = y(coverline)
            drawLine(
                color = scheme.outline,
                start = Offset(inset, lineY),
                end = Offset(size.width - inset, lineY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dash.toPx(), Dash.toPx())),
            )
            chart.coverlineLabel?.let { label ->
                val text = measure(measurer, label, style, size.width)
                drawText(text, topLeft = Offset(inset, lineY - ValueGap.toPx() - text.size.height))
            }
        }
        val onTime = chart.points.filterNot { it.late }
        if (onTime.size > 1) {
            val path = Path()
            onTime.forEachIndexed { index, point ->
                if (index ==
                    0
                ) {
                    path.moveTo(x(point.day), y(point.celsius))
                } else {
                    path.lineTo(x(point.day), y(point.celsius))
                }
            }
            drawPath(
                path = path,
                color = scheme.primary,
                style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        for (point in chart.points) {
            val centre = Offset(x(point.day), y(point.celsius))
            drawCircle(color = scheme.surfaceContainerLow, radius = DotRadius.toPx(), center = centre)
            drawCircle(
                color = if (point.late) scheme.outline else scheme.primary,
                radius = DotRadius.toPx(),
                center = centre,
                style = Stroke(width = LineWidth.toPx()),
            )
        }
        val labelTop = plotBottom + BandOverhang.toPx() + DayLabelGap.toPx()
        val start = measure(measurer, chart.startLabel, style, size.width / 2)
        drawText(start, topLeft = Offset(inset, labelTop))
        val end = measure(measurer, chart.endLabel, style, size.width / 2)
        drawText(end, topLeft = Offset(size.width - inset - end.size.width, labelTop))
    }
}

private fun DrawScope.measure(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Float,
): TextLayoutResult =
    measurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
        density = this,
    )

private fun DrawScope.drawCentred(
    text: TextLayoutResult,
    centreX: Float,
    top: Float,
) = drawText(text, topLeft = Offset(centreX - text.size.width / 2f, top))

private fun DrawScope.drawTopRoundedBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    val radius = CornerRadius(BarRadius.toPx().coerceAtMost(height))
    val path =
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = left + width,
                    bottom = top + height,
                    topLeftCornerRadius = radius,
                    topRightCornerRadius = radius,
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
    drawPath(path, color)
}
