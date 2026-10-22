package com.deutschdreamers.mensis.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.ui.preview.FakeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Builds real PDFs and reads them back with the platform renderer; no text-extraction dependency. */
@RunWith(AndroidJUnit4::class)
class ReportPdfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val writer = ReportPdfWriter(context)

    private fun model(
        entries: List<DayEntry> = FakeData.insightsHistory(),
        sections: Set<ReportSection> = ReportSection.DEFAULTS,
    ): ReportModel =
        ReportBuilder.build(
            FakeData.data(entries),
            ReportOptions(ReportRange.TWELVE_MONTHS, sections),
            FakeData.insightsToday,
            appVersion = "1.0.0",
        )

    /** Renders every page at 1 px per point and hands the bitmaps to [check]. */
    private fun render(
        pdf: ReportPdf,
        check: (List<Bitmap>) -> Unit,
    ) {
        val file = File(context.cacheDir, "report-test.pdf").apply { writeBytes(pdf.bytes) }
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pages =
                        (0 until renderer.pageCount).map { index ->
                            renderer.openPage(index).use { page ->
                                Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                }
                            }
                        }
                    check(pages)
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun Bitmap.count(predicate: (Int) -> Boolean): Int {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        return pixels.count(predicate)
    }

    @Test
    fun defaultReport_hasTextPageThenTemperaturePages_onA4() {
        val pdf = writer.write(model())
        assertTrue(String(pdf.bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        // Seven cycles with readings: four charts fit on the first temperature page, three on the next.
        assertEquals(3, pdf.pageCount)
        assertEquals(2..3, pdf.temperaturePages)
        render(pdf) { pages ->
            assertEquals(3, pages.size)
            for (page in pages) {
                assertEquals(595, page.width)
                assertEquals(842, page.height)
                assertTrue(page.count { it != Color.WHITE } > 2_000)
            }
            // The cycle-length bars on page 1 and the temperature lines on page 2 are drawn in rose.
            val rose = 0xFF8B4A5A.toInt()
            assertTrue(pages[0].count { it == rose } > 500)
            assertTrue(pages[1].count { it == rose } > 50)
        }
    }

    @Test
    fun withoutTemperatureSection_everythingFitsOnOnePage() {
        val pdf =
            writer.write(
                model(sections = setOf(ReportSection.SUMMARY, ReportSection.LENGTHS, ReportSection.SYMPTOMS)),
            )
        assertEquals(1, pdf.pageCount)
        assertNull(pdf.temperaturePages)
        render(pdf) { pages -> assertEquals(1, pages.size) }
    }

    @Test
    fun longNotes_breakAcrossPagesBetweenLines() {
        val history = FakeData.insightsHistory()
        val long = (1..200).joinToString("\n") { "Line $it of a long synthetic note." }
        val entries = history.map { if (it.date == history.last().date) it.copy(notes = long) else it }
        val pdf = writer.write(model(entries, sections = setOf(ReportSection.NOTES)))
        // 200 lines at about 11.5 pt need four pages of 726 pt.
        assertTrue("pages: ${pdf.pageCount}", pdf.pageCount in 3..5)
        render(pdf) { pages ->
            assertEquals(pdf.pageCount, pages.size)
            assertTrue(pages.all { page -> page.count { it != Color.WHITE } > 1_000 })
        }
    }

    @Test
    fun emptyHistory_stillGivesAOnePageReport() {
        val pdf = writer.write(model(entries = emptyList(), sections = ReportSection.entries.toSet()))
        assertEquals(1, pdf.pageCount)
        assertNull(pdf.temperaturePages)
    }
}
