package com.deutschdreamers.mensis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.domain.today.CycleStrip
import com.deutschdreamers.mensis.domain.today.StripCellKind
import com.deutschdreamers.mensis.domain.today.StripLane
import com.deutschdreamers.mensis.domain.today.StripLaneKind
import com.deutschdreamers.mensis.ui.theme.MensisTheme

private val CellGap = 2.dp
private val CellHeight = 30.dp
private val TodayCellHeight = 40.dp
private val CellRadius = 3.dp

/** Design §3 cycle strip: triangle row, cells, day ticks and lane labels. Text for each row comes from [laneLabel]. */
@Composable
fun CycleStripView(
    strip: CycleStrip,
    laneLabel: (StripLaneKind) -> String,
    cellDescription: (day: Int, kind: StripCellKind, isToday: Boolean) -> String,
    modifier: Modifier = Modifier,
) {
    val n = strip.lengthDays
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            for (day in 1..n) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (day == strip.todayDay) TodayTriangle(onSurface)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(TodayCellHeight),
            horizontalArrangement = Arrangement.spacedBy(CellGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (cell in strip.cells) {
                val isToday = cell.day == strip.todayDay
                val description = cellDescription(cell.day, cell.kind, isToday)
                StripCell(
                    kind = cell.kind,
                    isToday = isToday,
                    modifier = Modifier.weight(1f).semantics { contentDescription = description },
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TickRow(n)
        Spacer(modifier = Modifier.height(4.dp))
        LaneRow(strip.lanes, n, laneLabel)
    }
}

@Composable
private fun TodayTriangle(color: Color) {
    Box(
        modifier =
            Modifier.width(10.dp).height(7.dp).drawBehind {
                val path =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                        close()
                    }
                drawPath(path, color)
            },
    )
}

@Composable
private fun StripCell(
    kind: StripCellKind,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MensisTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CellRadius)
    val height = if (isToday) TodayCellHeight else CellHeight
    val fill =
        when (kind) {
            StripCellKind.PERIOD -> Modifier.background(colors.period, shape)
            StripCellKind.PERIOD_PREDICTED ->
                Modifier
                    .hatched(
                        colors.predHatch,
                        colors.predTint,
                        CellRadius,
                    ).dashedBorder(colors.predHatch, 1.dp, CellRadius)
            StripCellKind.FERTILE -> Modifier.background(colors.fertile, shape)
            StripCellKind.FERTILE_PREDICTED ->
                Modifier
                    .hatched(
                        colors.fertile,
                        colors.fertileContainer,
                        CellRadius,
                    ).dashedBorder(colors.fertile, 1.dp, CellRadius)
            StripCellKind.OVULATION -> Modifier.background(colors.ovulation, shape)
            StripCellKind.OVULATION_PREDICTED ->
                Modifier.background(colors.fertileContainer, shape).dashedBorder(colors.ovulation, 2.dp, CellRadius)
            StripCellKind.SPOTTING ->
                Modifier
                    .background(
                        colors.periodContainer,
                        shape,
                    ).border(1.dp, colors.period, shape)
            StripCellKind.BREAK -> Modifier.background(scheme.surface, shape).border(1.dp, scheme.outlineVariant, shape)
            StripCellKind.NONE -> Modifier.background(scheme.surfaceContainerHighest, shape)
        }
    val outline =
        if (isToday) {
            Modifier
                .border(
                    2.dp,
                    scheme.onSurface,
                    RoundedCornerShape(CellRadius + 1.dp),
                ).padding(1.dp)
        } else {
            Modifier
        }
    Box(modifier = modifier.height(height).then(outline).then(fill))
}

@Composable
private fun TickRow(n: Int) {
    val ticks = listOf(1, 7, 14, 21, 28) + if (n > 32) listOf(35) else emptyList()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
        for (day in 1..n) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (day in ticks) {
                    Text(
                        text = day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun LaneRow(
    lanes: List<StripLane>,
    n: Int,
    laneLabel: (StripLaneKind) -> String,
) {
    if (lanes.isEmpty()) return
    val colors = MensisTheme.colors
    val scheme = MaterialTheme.colorScheme
    // Lanes share one row; overlapping spans are trimmed so the labels never collide.
    var cursor = 1
    Row(modifier = Modifier.fillMaxWidth()) {
        for (lane in lanes.sortedBy { it.fromDay }) {
            val from = maxOf(lane.fromDay, cursor)
            val to = lane.toDay.coerceAtMost(n)
            if (from > to) continue
            if (from > cursor) Spacer(modifier = Modifier.weight((from - cursor).toFloat()))
            val rightAligned = lane.kind == StripLaneKind.PREDICTED || lane.kind == StripLaneKind.BREAK
            val color =
                when (lane.kind) {
                    StripLaneKind.PERIOD, StripLaneKind.SPOTTING -> colors.period
                    StripLaneKind.FERTILE, StripLaneKind.FERTILE_ESTIMATED -> colors.fertile
                    else -> scheme.onSurfaceVariant
                }
            Box(
                modifier = Modifier.weight((to - from + 1).toFloat()),
                contentAlignment = if (rightAligned) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text(
                    text = laneLabel(lane.kind),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = color,
                    maxLines = 1,
                    textAlign = if (rightAligned) TextAlign.End else TextAlign.Start,
                    modifier =
                        Modifier.wrapContentWidth(
                            unbounded = true,
                            align = if (rightAligned) Alignment.End else Alignment.Start,
                        ),
                )
            }
            cursor = to + 1
        }
        if (cursor <= n) Spacer(modifier = Modifier.weight((n - cursor + 1).toFloat()))
    }
}

/** A 10×4 dp pip row for a legend or stand-alone use. */
@Composable
fun LegendSwatch(
    kind: StripCellKind,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(14.dp)) {
        StripCell(kind = kind, isToday = false, modifier = Modifier.size(14.dp))
    }
}
