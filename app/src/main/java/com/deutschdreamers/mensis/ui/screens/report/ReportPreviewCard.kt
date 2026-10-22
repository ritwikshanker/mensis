package com.deutschdreamers.mensis.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deutschdreamers.mensis.report.ReportBarChart
import com.deutschdreamers.mensis.report.ReportList
import com.deutschdreamers.mensis.report.ReportRow
import com.deutschdreamers.mensis.report.ReportTexts
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.theme.SourceSerif

// The preview is paper, like the PDF: the design's fixed colours, unchanged in dark mode.
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1C1B1A)
private val InkSecondary = Color(0xFF514548)
private val Rule = Color(0xFFD5C3C6)
private val Bar = Color(0xFF8B4A5A)
private val PaperShape = RoundedCornerShape(4.dp)

const val REPORT_BARS_TAG: String = "report_bars"

/**
 * Design §15 step 2: a white A4-style card with the report's first page at phone size: header,
 * summary grid, cycle-length bars, symptoms, the other ticked sections in brief and the page footer.
 */
@Composable
fun ReportPreviewCard(
    preview: ReportPreview,
    modifier: Modifier = Modifier,
) {
    val model = preview.model
    val body = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, color = Ink)
    val secondary = body.copy(color = InkSecondary)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(2.dp, PaperShape)
                .background(Paper, PaperShape)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().bottomRule().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = model.title,
                style = TextStyle(fontFamily = SourceSerif, fontSize = 18.sp, lineHeight = 24.sp, color = Ink),
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(text = model.prepared, style = secondary, modifier = Modifier.alignByBaseline())
        }
        Text(text = model.subtitle, style = secondary)
        model.summary?.let { Rows(it, body) }
        model.lengths?.let { lengths ->
            Section(ReportTexts.LENGTHS_TITLE, body) {
                val chart = lengths.chart
                if (chart == null) Text(lengths.emptyText, style = secondary) else Bars(chart, secondary)
            }
        }
        model.symptoms?.let { symptoms ->
            Section(ReportTexts.SYMPTOMS_TITLE, body) {
                val line = symptoms.rows.joinToString(" $MIDDLE_DOT ") { "${it.label} ${it.value}" }
                Text(text = line.ifEmpty { symptoms.emptyText }, style = if (line.isEmpty()) secondary else body)
            }
        }
        model.medication?.let { ListSection(ReportTexts.MEDICATION_TITLE, it, body, secondary) }
        model.notes?.let { notes ->
            Section(ReportTexts.NOTES_TITLE, body) {
                val text = if (notes.rows.isEmpty()) notes.emptyText else ReportTexts.notesIncluded(notes.rows.size)
                Text(text, style = secondary)
            }
        }
        model.temperatures?.takeIf { it.charts.isEmpty() }?.let {
            Section(ReportTexts.TEMPERATURE_TITLE, body) { Text(it.emptyText, style = secondary) }
        }
        Text(
            text = preview.footer,
            style = secondary,
            modifier = Modifier.fillMaxWidth().topRule().padding(top = 10.dp),
        )
    }
}

@Composable
private fun Section(
    title: String,
    body: TextStyle,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().topRule().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = body.copy(fontWeight = FontWeight.Medium))
        content()
    }
}

@Composable
private fun ListSection(
    title: String,
    list: ReportList,
    body: TextStyle,
    secondary: TextStyle,
) {
    Section(title, body) {
        if (list.rows.isEmpty()) Text(list.emptyText, style = secondary) else Rows(list.rows, body)
    }
}

/** Label on the left, value on the right in medium weight. */
@Composable
private fun Rows(
    rows: List<ReportRow>,
    body: TextStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = row.label, style = body, modifier = Modifier.weight(1f))
                Text(
                    text = row.value,
                    style = body.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** 48 dp of bars on the chart's day axis with the first and last month under them; one spoken summary. */
@Composable
private fun Bars(
    chart: ReportBarChart,
    secondary: TextStyle,
) {
    val description =
        "Cycle lengths, oldest first: ${chart.lengths.joinToString(
            ", ",
        )} days. ${chart.meanLabel.replace(" d", " days")}."
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag(REPORT_BARS_TAG),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val span = (chart.axisMax - chart.axisMin).toFloat()
            for (days in chart.lengths) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(((days - chart.axisMin) / span).coerceIn(0.02f, 1f))
                            .background(Bar, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(text = chart.startLabel, style = secondary, modifier = Modifier.weight(1f))
            Text(text = chart.endLabel, style = secondary)
        }
    }
}

private fun Modifier.topRule(): Modifier =
    drawBehind { drawLine(Rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }

private fun Modifier.bottomRule(): Modifier =
    drawBehind { drawLine(Rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
