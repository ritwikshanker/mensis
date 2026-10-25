package com.deutschdreamers.mensis.report

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deutschdreamers.mensis.ui.preview.FakeData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** The report preview's pages: drawn from a real PDF, with no file left in the cache. Synthetic data. */
@RunWith(AndroidJUnit4::class)
class PdfPagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "pdf-pages-test")
    private val pdf =
        ReportPdfWriter(context).write(
            ReportBuilder.build(
                FakeData.data(FakeData.insightsHistory()),
                ReportOptions(ReportRange.TWELVE_MONTHS, ReportSection.DEFAULTS),
                FakeData.insightsToday,
                appVersion = "1.0.0",
            ),
        )

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun Bitmap.notWhite(): Int {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        return pixels.count { it != Color.WHITE }
    }

    private fun filesLeft(): Int = directory.listFiles()?.size ?: 0

    @Test
    fun everyPageIsDrawn_andNoFileStaysInTheCache() {
        PdfPages.open(pdf.bytes, directory).use { pages ->
            assertEquals(0, filesLeft())
            assertEquals(pdf.pageCount, pages.pageCount)
            for (index in 0 until pages.pageCount) {
                assertEquals(595f / 842f, pages.aspectRatios[index], 0.001f)
                val page = requireNotNull(pages.render(index, 400))
                assertEquals(400, page.width)
                assertEquals(400 * 842 / 595, page.height)
                assertTrue("page ${index + 1}", page.notWhite() > 500)
            }
        }
        assertEquals(0, filesLeft())
    }

    @Test
    fun closedPages_drawNothing_andCloseTwiceIsHarmless() {
        val pages = PdfPages.open(pdf.bytes, directory)
        pages.close()
        assertNull(pages.render(0, 100))
        pages.close()
    }

    @Test
    fun bytesThatAreNotAPdf_areRefused_withoutLeavingAFile() {
        assertThrows(IOException::class.java) { PdfPages.open("not a pdf".toByteArray(), directory) }
        assertEquals(0, filesLeft())
    }
}
