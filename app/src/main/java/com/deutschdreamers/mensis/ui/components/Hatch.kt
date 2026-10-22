package com.deutschdreamers.mensis.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * The "predicted" fill: 135° hatch lines ([stroke] wide, [pitch] apart) over [tint]. Predicted days
 * are always hatched so they never read as logged, whatever the colour scheme.
 */
fun Modifier.hatched(
    line: Color,
    tint: Color,
    cornerRadius: Dp,
    pitch: Dp = 6.dp,
    stroke: Dp = 1.5.dp,
): Modifier =
    clip(RoundedCornerShape(cornerRadius)).drawWithCache {
        val step = pitch.toPx() * sqrt(2f)
        val strokePx = stroke.toPx()
        val w = size.width
        val h = size.height
        onDrawBehind {
            drawRect(tint)
            var c = 0f
            while (c <= w + h) {
                drawLine(line, Offset(c - h, h), Offset(c, 0f), strokeWidth = strokePx)
                c += step
            }
        }
    }

/** A dashed rounded-rectangle border drawn inside the bounds. */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
    dash: Dp = 3.dp,
    gap: Dp = 2.dp,
): Modifier =
    drawWithCache {
        val widthPx = width.toPx()
        val effect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))
        val radius = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            drawRoundRect(
                color = color,
                topLeft = Offset(widthPx / 2, widthPx / 2),
                size =
                    androidx.compose.ui.geometry
                        .Size(size.width - widthPx, size.height - widthPx),
                cornerRadius = radius,
                style = Stroke(width = widthPx, pathEffect = effect),
            )
        }
    }
