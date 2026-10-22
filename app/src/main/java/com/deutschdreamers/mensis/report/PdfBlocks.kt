package com.deutschdreamers.mensis.report

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation

// The report is paper: fixed colours from the design's preview card, the same in light and dark mode.
private const val INK = 0xFF1C1B1A.toInt()
private const val SECONDARY = 0xFF514548.toInt()
private const val DIVIDER = 0xFFD5C3C6.toInt()
private const val OUTLINE = 0xFF837578.toInt()
private const val ROSE = 0xFF8B4A5A.toInt()

/** The fertile container teal at half opacity, as the Insights shift band. */
private const val BAND = 0x80C8EEEA.toInt()
private const val PAPER = 0xFFFFFFFF.toInt()

/** Paints in points (1/72 inch), the unit of a [android.graphics.pdf.PdfDocument] page canvas. */
internal class PdfStyle(
    sans: Typeface,
    serif: Typeface,
) {
    val title = text(serif, TITLE_SIZE, INK)
    val body = text(sans, BODY_SIZE, INK)
    val bodyMedium = text(sans, BODY_SIZE, INK, MEDIUM)
    val heading = text(sans, HEADING_SIZE, INK, MEDIUM)
    val secondary = text(sans, SECONDARY_SIZE, SECONDARY)
    val secondaryMedium = text(sans, SECONDARY_SIZE, SECONDARY, MEDIUM)
    val small = text(sans, SMALL_SIZE, SECONDARY)

    val divider = stroke(DIVIDER, HAIRLINE)
    val dashed = stroke(OUTLINE, HAIRLINE).apply { pathEffect = DashPathEffect(floatArrayOf(DASH, DASH), 0f) }
    val line = stroke(ROSE, LINE_WIDTH).apply { strokeJoin = Paint.Join.ROUND }
    val dot = stroke(ROSE, LINE_WIDTH)
    val lateDot = stroke(OUTLINE, LINE_WIDTH)
    val bar = fill(ROSE)
    val band = fill(BAND)
    val paper = fill(PAPER)

    private fun text(
        typeface: Typeface,
        size: Float,
        color: Int,
        weight: Int = REGULAR,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
        fontVariationSettings = "'wght' $weight"
    }

    private fun stroke(
        color: Int,
        width: Float,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }

    private fun fill(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

    private companion object {
        const val TITLE_SIZE = 22f
        const val HEADING_SIZE = 11f
        const val BODY_SIZE = 10f
        const val SECONDARY_SIZE = 9f
        const val SMALL_SIZE = 7.5f
        const val REGULAR = 400
        const val MEDIUM = 500
        const val HAIRLINE = 0.75f
        const val LINE_WIDTH = 1.25f
        const val DASH = 3f
    }
}

/**
 * One piece of the page flow, laid out for the content width it was built with. Tables and lists are
 * one block per row so pages break between rows; long rows can also break between lines.
 */
internal interface PdfBlock {
    val height: Float

    /** Space above the block, dropped at the top of a page. */
    val spaceBefore: Float get() = 0f

    /** Kept on the same page as the start of the next block (section titles). */
    val keepWithNext: Boolean get() = false

    /** Starts a new page (the temperature charts). */
    val newPage: Boolean get() = false

    fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    )

    /** The part that fits in [maxHeight] and the rest, or null when nothing useful fits. */
    fun split(maxHeight: Float): Pair<PdfBlock, PdfBlock>? = null
}

internal class Placed(
    val block: PdfBlock,
    val top: Float,
)

/** Breaks [blocks] into pages of [pageHeight]; a block that cannot be split and fits no page overflows its own. */
internal fun paginate(
    blocks: List<PdfBlock>,
    pageHeight: Float,
): List<List<Placed>> {
    val pages = mutableListOf(mutableListOf<Placed>())
    val queue = ArrayDeque(blocks)
    var y = 0f

    fun place(
        block: PdfBlock,
        gap: Float,
    ) {
        pages.last() += Placed(block, y + gap)
        y += gap + block.height
    }

    fun nextPage(rest: PdfBlock) {
        queue.addFirst(rest)
        pages += mutableListOf<Placed>()
        y = 0f
    }
    while (queue.isNotEmpty()) {
        val block = queue.removeFirst()
        val empty = pages.last().isEmpty()
        val gap = if (empty) 0f else block.spaceBefore
        val room = pageHeight - y - gap
        val parts = if (block.keepWithNext) null else block.split(room)
        when {
            !empty && block.newPage -> nextPage(block)
            block.height + kept(block, queue.firstOrNull()) <= room -> place(block, gap)
            parts != null -> {
                place(parts.first, gap)
                nextPage(parts.second)
            }
            empty -> place(block, gap)
            else -> nextPage(block)
        }
    }
    return pages
}

/** Room a section title needs below it so it is not left alone at the bottom of a page. */
private fun kept(
    block: PdfBlock,
    next: PdfBlock?,
): Float = if (block.keepWithNext && next != null) next.spaceBefore + minOf(next.height, KEEP_MIN) else 0f

/** A section title is kept with at least this much of what follows it. */
private const val KEEP_MIN = 40f

internal fun TextPaint.layout(
    text: CharSequence,
    width: Float,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
): StaticLayout =
    StaticLayout.Builder
        .obtain(text, 0, text.length, this, width.toInt().coerceAtLeast(1))
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(0f, LINE_SPACING)
        .build()

private const val LINE_SPACING = 1.15f

/** Lines [from] until [until] of [layout], drawn with their first line at [top]. */
private fun Canvas.drawLines(
    layout: StaticLayout,
    left: Float,
    top: Float,
    from: Int = 0,
    until: Int = layout.lineCount,
) {
    if (from >= until) return
    val offset = layout.getLineTop(from).toFloat()
    withTranslation(left, top - offset) {
        clipRect(0f, offset, layout.width.toFloat(), layout.getLineTop(until).toFloat())
        layout.draw(this)
    }
}

private fun StaticLayout.heightOf(
    from: Int,
    until: Int,
): Float = (getLineTop(until) - getLineTop(from)).toFloat()

/** Lines from [from] that fit in [maxHeight], at least one; null when not even one does. */
private fun StaticLayout.linesFitting(
    from: Int,
    maxHeight: Float,
): Int? {
    var until = from
    while (until < lineCount && heightOf(from, until + 1) <= maxHeight) until++
    return (until - from).takeIf { it > 0 }
}

/** Single-line text with its top at [top]. */
private fun Canvas.drawTextTop(
    text: String,
    x: Float,
    top: Float,
    paint: TextPaint,
) = drawText(text, x, top - paint.fontMetrics.ascent, paint)

private fun TextPaint.lineHeight(): Float = fontMetrics.descent - fontMetrics.ascent

/** "Cycle report" with the prepared date on the right, a rule, then the range and disclaimer line. */
internal class HeaderBlock(
    private val style: PdfStyle,
    private val width: Float,
    private val title: String,
    private val prepared: String,
    subtitle: String,
) : PdfBlock {
    private val subtitle = style.secondary.layout(subtitle, width)
    private val titleHeight = style.title.lineHeight()
    override val height: Float = titleHeight + RULE_GAP * 2 + this.subtitle.height

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) {
        canvas.drawTextTop(title, left, top, style.title)
        val baseline = top - style.title.fontMetrics.ascent
        canvas.drawText(prepared, left + width - style.secondary.measureText(prepared), baseline, style.secondary)
        val rule = top + titleHeight + RULE_GAP
        canvas.drawLine(left, rule, left + width, rule, style.divider)
        canvas.drawLines(subtitle, left, rule + RULE_GAP)
    }

    private companion object {
        const val RULE_GAP = 8f
    }
}

/** A rule across the page and the section's title under it. */
internal class SectionTitleBlock(
    private val style: PdfStyle,
    private val width: Float,
    private val title: String,
    override val newPage: Boolean = false,
) : PdfBlock {
    override val height: Float = RULE_GAP + style.heading.lineHeight()
    override val spaceBefore: Float = SECTION_GAP
    override val keepWithNext: Boolean = true

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) {
        if (!newPage) canvas.drawLine(left, top, left + width, top, style.divider)
        canvas.drawTextTop(title, left, top + RULE_GAP, style.heading)
    }

    private companion object {
        const val RULE_GAP = 10f
        const val SECTION_GAP = 16f
    }
}

/** A paragraph; breaks between lines. */
internal class TextBlock private constructor(
    private val layout: StaticLayout,
    private val from: Int,
    private val until: Int,
    override val spaceBefore: Float,
) : PdfBlock {
    constructor(paint: TextPaint, width: Float, text: String, spaceBefore: Float) :
        this(paint.layout(text, width), 0, -1, spaceBefore)

    private val end = if (until < 0) layout.lineCount else until
    override val height: Float = layout.heightOf(from, end)

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) = canvas.drawLines(layout, left, top, from, end)

    override fun split(maxHeight: Float): Pair<PdfBlock, PdfBlock>? {
        val fit = layout.linesFitting(from, maxHeight)?.takeIf { from + it < end } ?: return null
        return TextBlock(layout, from, from + fit, spaceBefore) to TextBlock(layout, from + fit, end, 0f)
    }
}

/** How a [RowBlock] sets its two columns: the label takes [labelShare] of the width. */
internal class RowColumns(
    val labelPaint: TextPaint,
    val valuePaint: TextPaint,
    val labelShare: Float,
    val valueAlignment: Layout.Alignment = Layout.Alignment.ALIGN_OPPOSITE,
)

/**
 * A label column and a value column ("Cycles logged" · "12", a date and its note). Long values break
 * between lines; the label stays with the first part.
 */
internal class RowBlock private constructor(
    private val label: StaticLayout?,
    private val value: StaticLayout,
    private val valueLeft: Float,
    private val from: Int,
    private val until: Int,
    override val spaceBefore: Float,
) : PdfBlock {
    constructor(row: ReportRow, columns: RowColumns, width: Float, spaceBefore: Float) : this(
        label = columns.labelPaint.layout(row.label, width * columns.labelShare - COLUMN_GAP),
        value = columns.valuePaint.layout(row.value, width * (1 - columns.labelShare), columns.valueAlignment),
        valueLeft = width * columns.labelShare,
        from = 0,
        until = -1,
        spaceBefore = spaceBefore,
    )

    private val end = if (until < 0) value.lineCount else until
    override val height: Float = maxOf(label?.height?.toFloat() ?: 0f, value.heightOf(from, end))

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) {
        label?.let { canvas.drawLines(it, left, top) }
        canvas.drawLines(value, left + valueLeft, top, from, end)
    }

    override fun split(maxHeight: Float): Pair<PdfBlock, PdfBlock>? {
        val fit =
            value
                .linesFitting(from, maxHeight)
                ?.takeIf { (label?.height ?: 0) <= maxHeight && from + it < end }
                ?: return null
        return RowBlock(label, value, valueLeft, from, from + fit, spaceBefore) to
            RowBlock(null, value, valueLeft, from + fit, end, 0f)
    }

    private companion object {
        const val COLUMN_GAP = 12f
    }
}

/** One line of a table with columns of [shares] of the width; a header line has a rule under it. */
internal class TableRowBlock(
    private val style: PdfStyle,
    private val width: Float,
    private val cells: List<String>,
    private val shares: List<Float>,
    private val header: Boolean,
) : PdfBlock {
    private val paint = if (header) style.secondaryMedium else style.body
    override val height: Float = paint.lineHeight() + if (header) RULE_GAP else 0f
    override val spaceBefore: Float = if (header) HEADER_GAP else ROW_GAP

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) {
        var x = left
        cells.zip(shares).forEach { (cell, share) ->
            canvas.drawTextTop(cell, x, top, paint)
            x += width * share
        }
        if (header) {
            val rule = top + paint.lineHeight() + RULE_GAP / 2
            canvas.drawLine(left, rule, left + width, rule, style.divider)
        }
    }

    private companion object {
        const val RULE_GAP = 6f
        const val HEADER_GAP = 12f
        const val ROW_GAP = 3f
    }
}

/**
 * Design §15 "Cycle lengths": bars oldest first with their length on top when there is room, a dashed
 * line at the mean, and the first month, the mean and the last month under the axis.
 */
internal class BarChartBlock(
    private val style: PdfStyle,
    private val width: Float,
    private val chart: ReportBarChart,
) : PdfBlock {
    private val labelHeight = style.small.lineHeight()
    override val height: Float = labelHeight + PLOT_HEIGHT + LABEL_GAP + labelHeight
    override val spaceBefore: Float = CHART_GAP

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) {
        val plotTop = top + labelHeight
        val plotBottom = plotTop + PLOT_HEIGHT
        val count = chart.lengths.size
        val barWidth = (width - BAR_GAP * (count - 1)) / count
        val span = (chart.axisMax - chart.axisMin).toFloat()

        fun y(days: Double) = plotBottom - ((days - chart.axisMin) / span).toFloat().coerceIn(0f, 1f) * PLOT_HEIGHT
        chart.lengths.forEachIndexed { index, days ->
            val x = left + index * (barWidth + BAR_GAP)
            canvas.drawRoundRect(
                x,
                y(days.toDouble()),
                x + barWidth,
                plotBottom + BAR_RADIUS,
                BAR_RADIUS,
                BAR_RADIUS,
                style.bar,
            )
        }
        // The rounded corners reach below the axis; paper hides them so the bars stand on a flat line.
        canvas.drawRect(left, plotBottom, left + width, plotBottom + BAR_RADIUS, style.paper)
        canvas.drawLine(left, plotBottom, left + width, plotBottom, style.divider)
        val meanY = y(chart.mean)
        canvas.drawLine(left, meanY, left + width, meanY, style.dashed)
        // Each bar's length on top when there is room, on paper so the mean line does not cross it.
        if (barWidth >= MIN_LABELLED_BAR) {
            chart.lengths.forEachIndexed { index, days ->
                val text = days.toString()
                val textWidth = style.small.measureText(text)
                val textX = left + index * (barWidth + BAR_GAP) + (barWidth - textWidth) / 2
                val textTop = y(days.toDouble()) - LABEL_GAP - labelHeight
                canvas.drawRect(
                    textX - LABEL_GAP,
                    textTop,
                    textX + textWidth + LABEL_GAP,
                    textTop + labelHeight,
                    style.paper,
                )
                canvas.drawTextTop(text, textX, textTop, style.small)
            }
        }
        val labelTop = plotBottom + LABEL_GAP
        canvas.drawTextTop(chart.startLabel, left, labelTop, style.small)
        val meanX = left + (width - style.small.measureText(chart.meanLabel)) / 2
        canvas.drawTextTop(chart.meanLabel, meanX, labelTop, style.small)
        canvas.drawTextTop(
            chart.endLabel,
            left + width - style.small.measureText(chart.endLabel),
            labelTop,
            style.small,
        )
    }

    private companion object {
        const val PLOT_HEIGHT = 72f
        const val BAR_GAP = 3f
        const val BAR_RADIUS = 2f
        const val LABEL_GAP = 3f
        const val CHART_GAP = 10f
        const val MIN_LABELLED_BAR = 12f
    }
}

/**
 * One cycle's waking temperatures: title, a plot with the axis values on the left, shift band,
 * dashed coverline, line and dots (late readings grey and off the line), then the caption.
 */
internal class TemperatureChartBlock(
    private val style: PdfStyle,
    private val width: Float,
    private val chart: ReportTemperatureChart,
) : PdfBlock {
    private val caption = style.secondary.layout(chart.caption, width)
    private val titleHeight = style.bodyMedium.lineHeight()
    private val labelHeight = style.small.lineHeight()
    override val height: Float =
        titleHeight + TITLE_GAP + PLOT_HEIGHT + LABEL_GAP + labelHeight + CAPTION_GAP + caption.height
    override val spaceBefore: Float = CHART_GAP

    override fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
    ) {
        canvas.drawTextTop(chart.title, left, top, style.bodyMedium)
        val plotTop = top + titleHeight + TITLE_GAP
        val plot = RectF(left + AXIS_WIDTH, plotTop, left + width, plotTop + PLOT_HEIGHT)
        canvas.drawLine(plot.left, plot.top, plot.right, plot.top, style.divider)
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, style.divider)
        canvas.drawTextTop(chart.axisHigh, left, plot.top - labelHeight / 2, style.small)
        canvas.drawTextTop(chart.axisLow, left, plot.bottom - labelHeight / 2, style.small)
        drawReadings(canvas, plot)
        val labelTop = plot.bottom + LABEL_GAP
        val data = chart.chart
        canvas.drawTextTop(data.startLabel, plot.left + INSET, labelTop, style.small)
        val endX = plot.right - INSET - style.small.measureText(data.endLabel)
        canvas.drawTextTop(data.endLabel, endX, labelTop, style.small)
        canvas.drawLines(caption, left, labelTop + labelHeight + CAPTION_GAP)
    }

    /** Shift band, coverline, the line through the on-time readings and every reading's dot, inside [plot]. */
    private fun drawReadings(
        canvas: Canvas,
        plot: RectF,
    ) {
        val data = chart.chart
        val step = (plot.width() - 2 * INSET) / (data.dayCount - 1).coerceAtLeast(1)
        val span = (data.maxC - data.minC).takeIf { it > 0 } ?: 1.0

        fun x(day: Int) = plot.left + INSET + (day - 1) * step

        fun y(celsius: Double) = plot.bottom - ((celsius - data.minC) / span).toFloat().coerceIn(0f, 1f) * plot.height()
        data.shiftDays?.let { days ->
            val bandLeft = maxOf(plot.left, x(days.first) - step / 2)
            canvas.drawRect(bandLeft, plot.top, minOf(plot.right, x(days.last) + step / 2), plot.bottom, style.band)
        }
        data.coverlineC?.let { coverline ->
            canvas.drawLine(plot.left, y(coverline), plot.right, y(coverline), style.dashed)
            data.coverlineLabel?.let {
                // At the right end: after a shift the readings there sit well above the coverline.
                val labelX = plot.right - INSET - style.small.measureText(it)
                canvas.drawTextTop(it, labelX, y(coverline) - labelHeight - LABEL_GAP, style.small)
            }
        }
        val onTime = data.points.filterNot { it.late }
        if (onTime.size > 1) {
            val path = Path().apply { moveTo(x(onTime.first().day), y(onTime.first().celsius)) }
            onTime.drop(1).forEach { path.lineTo(x(it.day), y(it.celsius)) }
            canvas.drawPath(path, style.line)
        }
        for (point in data.points) {
            canvas.drawCircle(x(point.day), y(point.celsius), DOT_RADIUS, style.paper)
            canvas.drawCircle(x(point.day), y(point.celsius), DOT_RADIUS, if (point.late) style.lateDot else style.dot)
        }
    }

    private companion object {
        const val PLOT_HEIGHT = 96f
        const val AXIS_WIDTH = 30f
        const val INSET = 4f
        const val TITLE_GAP = 8f
        const val LABEL_GAP = 3f
        const val CAPTION_GAP = 6f
        const val CHART_GAP = 18f
        const val DOT_RADIUS = 2f
    }
}
