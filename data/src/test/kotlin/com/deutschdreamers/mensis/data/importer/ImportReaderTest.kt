package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.Synthetic
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportFormat
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.domain.model.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.random.Random

class ImportReaderTest {
    private val export =
        ExportFiles.snapshot(
            rows =
                StoredRows(
                    dayEntries = listOf(Synthetic.day("2026-03-06"), Synthetic.day("2026-03-07", Flow.HEAVY)),
                    contraception = emptyList(),
                    settings = Synthetic.settings(),
                ),
            uiPrefs = null,
            app = ExportedApp("t", 1),
            exportedAt = Instant.parse(Synthetic.NOW),
        )

    private fun error(
        bytes: ByteArray,
        source: ImportSource = ImportSource.CSV,
    ): ImportError = assertThrows<ImportException> { ImportReader.read(source, bytes, Synthetic.TODAY) }.error

    @ParameterizedTest
    @EnumSource(ImportSource::class)
    fun `a Mensis JSON export is recognised whatever source was chosen`(source: ImportSource) {
        val outcome = ImportReader.read(source, ExportFiles.encode(export, ExportFormat.JSON), Synthetic.TODAY)
        val parsed = (outcome as ReadOutcome.Parsed).parsed
        assertEquals(ImportSource.MENSIS, parsed.source)
        assertEquals(2, parsed.days.size)
    }

    @Test
    fun `an encrypted CSV export decrypts to a CSV that maps itself`() {
        val sealed = ExportFiles.encode(export, ExportFormat.CSV, "passphrase".toCharArray(), KdfParams(1_024, 1, 1))
        assertTrue(ImportReader.isEncrypted(sealed))
        val plain = ImportReader.decrypt(sealed, "passphrase".toCharArray())
        val outcome = ImportReader.read(ImportSource.MENSIS, plain, Synthetic.TODAY) as ReadOutcome.NeedsMapping
        assertTrue(outcome.guess.hasDate)
    }

    @Test
    fun `truncated and corrupted encrypted files say damaged, not wrong password`() {
        val sealed = ExportFiles.encode(export, ExportFormat.JSON, "passphrase".toCharArray(), KdfParams(1_024, 1, 1))
        val truncated =
            assertThrows<ImportException> {
                ImportReader.decrypt(
                    sealed.copyOfRange(0, 30),
                    "passphrase".toCharArray(),
                )
            }
        assertEquals(ImportError.DAMAGED, truncated.error)
        val header = sealed.copyOf().also { it[8] = 9 }
        assertEquals(
            ImportError.DAMAGED,
            assertThrows<ImportException> {
                ImportReader.decrypt(header, "passphrase".toCharArray())
            }.error,
        )
    }

    @Test
    fun `empty, binary and oversized files are refused`() {
        assertEquals(ImportError.NOT_THIS_FORMAT, error(ByteArray(0)))
        assertEquals(ImportError.NOT_THIS_FORMAT, error("   \n".toByteArray()))
        assertEquals(ImportError.NOT_THIS_FORMAT, error(Random(7).nextBytes(4_096)))
        assertEquals(ImportError.TOO_LARGE, error(ByteArray(ImportLimits.MAX_FILE_BYTES + 1) { 'a'.code.toByte() }))
    }

    @Test
    fun `truncated Mensis JSON is damaged, foreign JSON is not this format`() {
        val json = ExportFiles.encode(export, ExportFormat.JSON)
        assertEquals(ImportError.DAMAGED, error(json.copyOfRange(0, json.size / 2)))
        assertEquals(ImportError.NOT_THIS_FORMAT, error("{\"hello\": \"world\"}".toByteArray()))
    }

    @Test
    fun `a CSV for the drip or Clue source must actually be one`() {
        val csv = "date,flow\n2026-03-06,light\n".toByteArray()
        assertEquals(ImportError.NOT_THIS_FORMAT, error(csv, ImportSource.DRIP))
        assertEquals(ImportError.NOT_THIS_FORMAT, error(csv, ImportSource.CLUE))
        assertTrue(ImportReader.read(ImportSource.CSV, csv, Synthetic.TODAY) is ReadOutcome.NeedsMapping)
    }

    @Test
    fun `text is read as UTF-8, UTF-16 with a BOM, or Windows-1252`() {
        assertEquals("Gefühl", ImportReader.text("\uFEFFGefühl".toByteArray()))
        assertEquals("Gefühl", ImportReader.text("\uFEFFGefühl".toByteArray(Charsets.UTF_16LE)))
        assertEquals("Gefühl", ImportReader.text("Gefühl".toByteArray(charset("windows-1252"))))
    }

    @Test
    fun `a container header is recognised by its magic only`() {
        assertTrue(EncryptedContainer.isContainer("MENSISBK".toByteArray()))
        assertEquals(false, EncryptedContainer.isContainer("MENSIS".toByteArray()))
    }
}
