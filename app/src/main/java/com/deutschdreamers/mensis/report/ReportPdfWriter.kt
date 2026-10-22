package com.deutschdreamers.mensis.report

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import androidx.core.content.res.ResourcesCompat
import com.deutschdreamers.mensis.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A finished report: the PDF itself, its page count and the pages the temperature charts are on. */
class ReportPdf(
    val bytes: ByteArray,
    val pageCount: Int,
    val temperaturePages: IntRange?,
)

/**
 * Draws a [ReportModel] as an A4 PDF with Android's [PdfDocument]: the text sections flow from page 1,
 * the temperature charts start on a page of their own. Everything happens in memory; the caller
 * decides where the bytes go.
 */
@Singleton
class ReportPdfWriter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun write(model: ReportModel): ReportPdf {
            val style =
                PdfStyle(
                    sans = ResourcesCompat.getFont(context, R.font.roboto_flex) ?: Typeface.SANS_SERIF,
                    serif = ResourcesCompat.getFont(context, R.font.source_serif_4) ?: Typeface.SERIF,
                )
            val flow = ReportFlow(model, style)
            val pages = paginate(flow.blocks, CONTENT_BOTTOM - CONTENT_TOP)
            val temperaturePages =
                flow.temperatureStart?.let { start ->
                    pages.indexOfFirst { page -> page.any { it.block === start } } + 1..pages.size
                }
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, placed ->
                    val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                    val page = document.startPage(info)
                    val canvas = page.canvas
                    placed.forEach { it.block.draw(canvas, MARGIN, CONTENT_TOP + it.top) }
                    canvas.drawLine(MARGIN, FOOTER_RULE, PAGE_WIDTH - MARGIN, FOOTER_RULE, style.divider)
                    val footer = ReportTexts.footer(index + 1, pages.size, temperaturePages)
                    canvas.drawText(footer, MARGIN, FOOTER_BASELINE, style.secondary)
                    document.finishPage(page)
                }
                val out = ByteArrayOutputStream()
                document.writeTo(out)
                return ReportPdf(out.toByteArray(), pages.size, temperaturePages)
            } finally {
                document.close()
            }
        }

        private companion object {
            // A4 in points; 48 pt (17 mm) margins, so the content is 499 pt wide.
            const val PAGE_WIDTH = 595
            const val PAGE_HEIGHT = 842
            const val MARGIN = 48f
            const val CONTENT_TOP = 48f
            const val CONTENT_BOTTOM = 774f
            const val FOOTER_RULE = 786f
            const val FOOTER_BASELINE = 802f
        }
    }

/** The report's sections as page blocks, in reading order; the temperature charts come last. */
private class ReportFlow(
    model: ReportModel,
    private val style: PdfStyle,
) {
    private val width = CONTENT_WIDTH
    val blocks = mutableListOf<PdfBlock>()

    /** The first block on the temperature pages, when there are charts to draw. */
    var temperatureStart: PdfBlock? = null
        private set

    init {
        blocks += HeaderBlock(style, width, model.title, model.prepared, model.subtitle)
        val summary = RowColumns(style.body, style.bodyMedium, SUMMARY_LABEL_SHARE)
        model.summary?.forEachIndexed { index, row ->
            blocks += RowBlock(row, summary, width, if (index == 0) FIRST_GAP else ROW_GAP)
        }
        model.lengths?.let { lengths(it) }
        model.symptoms?.let {
            list(ReportTexts.SYMPTOMS_TITLE, it, RowColumns(style.body, style.bodyMedium, SYMPTOM_LABEL_SHARE))
        }
        model.medication?.let {
            list(ReportTexts.MEDICATION_TITLE, it, RowColumns(style.body, style.body, METHOD_LABEL_SHARE))
        }
        model.notes?.let {
            val columns = RowColumns(style.secondary, style.body, NOTE_LABEL_SHARE, Layout.Alignment.ALIGN_NORMAL)
            list(ReportTexts.NOTES_TITLE, it, columns)
        }
        model.temperatures?.let { temperatures(it) }
    }

    private fun lengths(lengths: ReportLengths) {
        blocks += SectionTitleBlock(style, width, ReportTexts.LENGTHS_TITLE)
        val chart = lengths.chart
        if (chart == null) {
            blocks += TextBlock(style.secondary, width, lengths.emptyText, ROW_GAP)
            return
        }
        blocks += BarChartBlock(style, width, chart)
        blocks += TableRowBlock(style, width, TABLE_HEADER, TABLE_SHARES, header = true)
        lengths.rows.forEach { row ->
            val cells = listOf(row.start, row.cycleLength, row.periodLength)
            blocks += TableRowBlock(style, width, cells, TABLE_SHARES, header = false)
        }
    }

    private fun list(
        title: String,
        list: ReportList,
        columns: RowColumns,
    ) {
        blocks += SectionTitleBlock(style, width, title)
        if (list.rows.isEmpty()) {
            blocks += TextBlock(style.secondary, width, list.emptyText, ROW_GAP)
            return
        }
        list.note?.let { blocks += TextBlock(style.secondary, width, it, ROW_GAP) }
        list.rows.forEachIndexed { index, row ->
            blocks += RowBlock(row, columns, width, if (index == 0) FIRST_GAP else ROW_GAP)
        }
    }

    private fun temperatures(temperatures: ReportTemperatures) {
        if (temperatures.charts.isEmpty()) {
            blocks += SectionTitleBlock(style, width, ReportTexts.TEMPERATURE_TITLE)
            blocks += TextBlock(style.secondary, width, temperatures.emptyText, ROW_GAP)
            return
        }
        val title = SectionTitleBlock(style, width, ReportTexts.TEMPERATURE_TITLE, newPage = true)
        temperatureStart = title
        blocks += title
        blocks += TextBlock(style.secondary, width, temperatures.note, ROW_GAP)
        temperatures.charts.forEach { blocks += TemperatureChartBlock(style, width, it) }
    }

    private companion object {
        const val CONTENT_WIDTH = 499f
        const val FIRST_GAP = 12f
        const val ROW_GAP = 6f
        const val SUMMARY_LABEL_SHARE = 0.6f
        const val SYMPTOM_LABEL_SHARE = 0.7f
        const val METHOD_LABEL_SHARE = 0.4f
        const val NOTE_LABEL_SHARE = 0.2f
        val TABLE_HEADER = listOf("Cycle start", "Cycle length", "Period length")
        val TABLE_SHARES = listOf(0.4f, 0.3f, 0.3f)
    }
}
