package com.deutschdreamers.mensis.ui.screens.about

/** One block of a licence or notice text as the notices screen lays it out. */
sealed interface NoticeBlock {
    data class Heading(
        val text: String,
        val level: Int,
    ) : NoticeBlock

    data class Paragraph(
        val text: String,
    ) : NoticeBlock

    data class Bullet(
        val text: String,
    ) : NoticeBlock

    data class TableRow(
        val cells: List<String>,
        val header: Boolean,
    ) : NoticeBlock
}

/** A run of text inside a block: `**bold**` and `` `code` `` are the only inline marks used. */
data class NoticeSpan(
    val text: String,
    val bold: Boolean = false,
    val code: Boolean = false,
)

/** Turns the bundled texts into blocks. Pure, so the parsing is unit-tested. */
object NoticeText {
    private val separatorCell = Regex(":?-+:?")
    private val ruleLine = Regex("[-=_]{3,}")

    /**
     * The small Markdown subset THIRD_PARTY_NOTICES.md uses: `#` headings, paragraphs, `- ` bullets with
     * indented continuation lines, and pipe tables (the first row is the header, `|---|` rows are dropped).
     */
    fun parseMarkdown(text: String): List<NoticeBlock> {
        val blocks = mutableListOf<NoticeBlock>()
        val paragraph = StringBuilder()
        var bullet: StringBuilder? = null
        var inTable = false

        fun flush() {
            if (paragraph.isNotEmpty()) blocks += NoticeBlock.Paragraph(paragraph.toString())
            paragraph.clear()
            bullet?.let { blocks += NoticeBlock.Bullet(it.toString()) }
            bullet = null
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            val continuation = bullet
            when {
                line.isEmpty() -> {
                    flush()
                    inTable = false
                }
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += NoticeBlock.Heading(line.drop(level).trim(), level)
                }
                line.startsWith("|") -> {
                    flush()
                    val cells = line.trim('|').split('|').map { it.trim() }
                    if (!cells.all { separatorCell.matches(it) }) {
                        blocks += NoticeBlock.TableRow(cells, header = !inTable)
                        inTable = true
                    }
                }
                line.startsWith("- ") -> {
                    flush()
                    bullet = StringBuilder(line.removePrefix("- ").trim())
                }
                raw.startsWith(" ") && continuation != null -> continuation.append(' ').append(line)
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return blocks
    }

    /**
     * A plain licence text: paragraphs split at blank lines and reflowed for a phone screen (the files
     * are hard-wrapped and indented for 80 columns). Only whitespace changes; rule lines of dashes go.
     */
    fun parsePlain(text: String): List<NoticeBlock> =
        text
            .replace("\r\n", "\n")
            .split(Regex("\n[ \t]*\n"))
            .map { paragraph ->
                paragraph
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !ruleLine.matches(it) }
                    .joinToString(" ")
            }.filter { it.isNotEmpty() }
            .map { NoticeBlock.Paragraph(it) }

    fun spans(text: String): List<NoticeSpan> {
        val spans = mutableListOf<NoticeSpan>()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) spans += NoticeSpan(plain.toString())
            plain.clear()
        }

        var i = 0
        while (i < text.length) {
            val bold = text.startsWith("**", i)
            val marker = if (bold) "**" else "`"
            val end = if (bold || text[i] == '`') text.indexOf(marker, i + marker.length) else -1
            if (end > i) {
                flushPlain()
                spans += NoticeSpan(text.substring(i + marker.length, end), bold = bold, code = !bold)
                i = end + marker.length
            } else {
                plain.append(text[i])
                i++
            }
        }
        flushPlain()
        return spans
    }
}
