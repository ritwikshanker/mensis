package com.deutschdreamers.mensis.data.csv

/**
 * RFC 4180 CSV, hand-written so `:data` needs no CSV library. Fields are separated by a delimiter,
 * quoted with `"` when they contain the delimiter, a quote or a line break, and quotes inside a quoted
 * field are doubled. The writer ends lines with CRLF; the reader accepts CRLF, LF and CR, strips a
 * leading byte-order mark and skips blank lines.
 */
object Csv {
    const val BOM: Char = '\uFEFF'
    private const val QUOTE = '"'
    private const val CRLF = "\r\n"
    private val NEEDS_QUOTES = setOf(QUOTE, '\r', '\n')
    private val CANDIDATE_DELIMITERS = listOf(',', ';', '\t')

    fun write(
        rows: List<List<String>>,
        delimiter: Char = ',',
    ): String =
        buildString {
            for (row in rows) {
                row.joinTo(this, separator = delimiter.toString()) { quote(it, delimiter) }
                append(CRLF)
            }
        }

    @Suppress("CyclomaticComplexMethod") // one pass over the characters; the branches are the RFC 4180 states
    fun read(
        text: String,
        delimiter: Char = detectDelimiter(text),
    ): List<List<String>> {
        val input = text.removePrefix(BOM.toString())
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var rowQuoted = false
        var i = 0

        fun endField() {
            row += field.toString()
            field.clear()
            fieldStarted = false
        }

        fun endRow() {
            endField()
            if (row.size > 1 || row.single().isNotEmpty() || rowQuoted) rows += row.toList()
            row.clear()
            rowQuoted = false
        }

        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes && c == QUOTE && input.getOrNull(i + 1) == QUOTE -> {
                    field.append(QUOTE)
                    i++
                }
                inQuotes && c == QUOTE -> inQuotes = false
                inQuotes -> field.append(c)
                c == QUOTE && !fieldStarted -> {
                    inQuotes = true
                    fieldStarted = true
                    rowQuoted = true
                }
                c == delimiter -> endField()
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && input.getOrNull(i + 1) == '\n') i++
                    endRow()
                }
                else -> {
                    field.append(c)
                    fieldStarted = true
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) endRow()
        return rows
    }

    /** The candidate that occurs most often outside quotes in the first line; a comma when none occurs. */
    fun detectDelimiter(text: String): Char {
        val counts = CANDIDATE_DELIMITERS.associateWith { 0 }.toMutableMap()
        var inQuotes = false
        for (c in text.removePrefix(BOM.toString())) {
            when {
                c == QUOTE -> inQuotes = !inQuotes
                !inQuotes && (c == '\n' || c == '\r') -> break
                !inQuotes && c in counts -> counts[c] = counts.getValue(c) + 1
            }
        }
        return counts.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }
            ?.key ?: ','
    }

    private fun quote(
        value: String,
        delimiter: Char,
    ): String =
        if (value.any { it == delimiter || it in NEEDS_QUOTES }) {
            QUOTE + value.replace("\"", "\"\"") + QUOTE
        } else {
            value
        }
}

/** A CSV file split into its header row and data rows; short rows are padded with empty fields. */
data class CsvTable(
    val header: List<String>,
    val rows: List<List<String>>,
) {
    val columnCount: Int get() = header.size

    fun cell(
        row: List<String>,
        column: Int,
    ): String = row.getOrNull(column).orEmpty()

    companion object {
        fun parse(text: String): CsvTable {
            val lines = Csv.read(text)
            val header = lines.firstOrNull()?.map { it.trim() } ?: emptyList()
            return CsvTable(header = header, rows = lines.drop(1))
        }
    }
}
