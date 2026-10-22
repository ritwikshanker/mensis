package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.crypto.AuthenticationFailedException
import com.deutschdreamers.mensis.data.crypto.DamagedContainerException
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.csv.Csv
import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.data.export.ExportMigrations
import com.deutschdreamers.mensis.data.export.MensisJson
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate

/** What reading a file gives: entries ready to preview, or a CSV that first needs its columns mapped. */
sealed interface ReadOutcome {
    data class Parsed(
        val parsed: ParsedImport,
    ) : ReadOutcome

    data class NeedsMapping(
        val table: CsvTable,
        val guess: ColumnMapping,
    ) : ReadOutcome
}

/**
 * Bytes from the file picker → [ReadOutcome]. The content decides the importer where it is
 * unambiguous (a Mensis JSON export, a drip. CSV); otherwise the source the user chose does. Files
 * are read on the phone only.
 */
object ImportReader {
    /** Share of control characters above which the bytes are treated as binary rather than text. */
    private const val BINARY_SHARE = 0.01

    fun isEncrypted(bytes: ByteArray): Boolean = EncryptedContainer.isContainer(bytes)

    fun decrypt(
        bytes: ByteArray,
        password: CharArray,
    ): ByteArray =
        try {
            EncryptedContainer.open(bytes, password)
        } catch (e: AuthenticationFailedException) {
            throw ImportException(ImportError.WRONG_PASSWORD, cause = e)
        } catch (e: DamagedContainerException) {
            throw ImportException(ImportError.DAMAGED, cause = e)
        }

    fun read(
        source: ImportSource,
        bytes: ByteArray,
        today: LocalDate,
        migrations: ExportMigrations = ExportMigrations.Default,
    ): ReadOutcome {
        if (bytes.size > ImportLimits.MAX_FILE_BYTES) refuse(ImportError.TOO_LARGE)
        require(!isEncrypted(bytes)) { "decrypt first" }
        val text = text(bytes)
        if (text.isBlank() || looksBinary(text)) refuse(ImportError.NOT_THIS_FORMAT)
        val trimmed = text.trimStart()
        return when {
            MensisJson.looksLikeMensis(text) -> ReadOutcome.Parsed(MensisJsonImporter.parse(text, migrations))
            trimmed.startsWith("{") || trimmed.startsWith("[") ->
                if (source == ImportSource.CLUE || ClueJsonImporter.looksLikeClue(text)) {
                    ReadOutcome.Parsed(ClueJsonImporter.parse(text, today))
                } else {
                    refuse(ImportError.NOT_THIS_FORMAT)
                }
            else -> readCsv(source, CsvTable.parse(text), today)
        }
    }

    private fun readCsv(
        source: ImportSource,
        table: CsvTable,
        today: LocalDate,
    ): ReadOutcome =
        when {
            table.header.isEmpty() -> refuse(ImportError.NOT_THIS_FORMAT)
            DripCsvImporter.looksLikeDrip(table) -> ReadOutcome.Parsed(DripCsvImporter.parse(table, today))
            source == ImportSource.DRIP || source == ImportSource.CLUE -> refuse(ImportError.NOT_THIS_FORMAT)
            else -> ReadOutcome.NeedsMapping(table, GenericCsvImporter.guess(table))
        }

    /** UTF-8 (byte-order mark dropped), UTF-16 with a byte-order mark, else Windows-1252 (older spreadsheets). */
    fun text(bytes: ByteArray): String {
        val utf16 =
            bytes.size >= 2 &&
                (
                    (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                        (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                )
        if (utf16) return String(bytes, Charsets.UTF_16)
        val decoder =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded =
            try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (expected: CharacterCodingException) {
                // Not UTF-8: read it the way Excel on Windows writes CSV.
                String(bytes, runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1))
            }
        return decoded.removePrefix(Csv.BOM.toString())
    }

    private fun looksBinary(text: String): Boolean {
        val sample = text.take(SNIFF_CHARS)
        val control = sample.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        return control > sample.length * BINARY_SHARE
    }

    private const val SNIFF_CHARS = 8_192
}
