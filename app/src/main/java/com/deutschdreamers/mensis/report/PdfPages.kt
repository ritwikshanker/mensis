package com.deutschdreamers.mensis.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/**
 * The pages of a PDF held in memory, drawn with the platform's [PdfRenderer] for the report preview.
 * The renderer reads from a file descriptor, so [open] writes the bytes to a file in [open]'s directory
 * and deletes it as soon as the descriptor is open: nothing stays on disk while the pages are shown.
 *
 * [PdfRenderer] keeps one page open at a time and is not thread-safe, so [render] and [close] take the
 * same lock; call [render] off the main thread.
 */
class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private val lock = Any()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Width over height of every page, so the preview can lay pages out before drawing them. */
    val aspectRatios: List<Float> =
        (0 until pageCount).map { index -> renderer.openPage(index).use { it.width.toFloat() / it.height } }

    /** Page [index] drawn [widthPx] wide on white, or null once closed. */
    fun render(
        index: Int,
        widthPx: Int,
    ): Bitmap? {
        synchronized(lock) {
            if (closed) return null
            return renderer.openPage(index).use { page ->
                val heightPx = (widthPx.toLong() * page.height / page.width).toInt()
                Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            renderer.close()
            descriptor.close()
        }
    }

    companion object {
        /** Opens [bytes]; the file the renderer reads is already deleted from [directory] when this returns. */
        fun open(
            bytes: ByteArray,
            directory: File,
        ): PdfPages {
            directory.mkdirs()
            val file = File.createTempFile("preview", ".pdf", directory)
            val descriptor =
                try {
                    file.writeBytes(bytes)
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } finally {
                    file.delete()
                }
            var pages: PdfPages? = null
            try {
                pages = PdfPages(descriptor, PdfRenderer(descriptor))
                return pages
            } finally {
                if (pages == null) descriptor.close()
            }
        }
    }
}
