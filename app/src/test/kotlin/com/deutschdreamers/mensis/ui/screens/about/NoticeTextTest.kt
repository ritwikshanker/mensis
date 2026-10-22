package com.deutschdreamers.mensis.ui.screens.about

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NoticeTextTest {
    @Test
    fun `headings, paragraphs, bullets with continuation lines and a table`() {
        val text =
            """
            # Notices

            ## Fonts

            - **Font A** — Licence one,
              continued here.
            - Font B

            Resolved by Gradle;
            versions elsewhere.

            | Library | Licence |
            |---|---|
            | Room | Apache-2.0 |
            | OkHttp | Apache-2.0 |
            """.trimIndent()

        val blocks = NoticeText.parseMarkdown(text)

        assertEquals(
            listOf(
                NoticeBlock.Heading("Notices", 1),
                NoticeBlock.Heading("Fonts", 2),
                NoticeBlock.Bullet("**Font A** — Licence one, continued here."),
                NoticeBlock.Bullet("Font B"),
                NoticeBlock.Paragraph("Resolved by Gradle; versions elsewhere."),
                NoticeBlock.TableRow(listOf("Library", "Licence"), header = true),
                NoticeBlock.TableRow(listOf("Room", "Apache-2.0"), header = false),
                NoticeBlock.TableRow(listOf("OkHttp", "Apache-2.0"), header = false),
            ),
            blocks,
        )
    }

    @Test
    fun `bold and code spans are marked, everything else is plain`() {
        assertEquals(
            listOf(
                NoticeSpan("Roboto Flex", bold = true),
                NoticeSpan(" ships as "),
                NoticeSpan("res/raw/ofl.txt", code = true),
                NoticeSpan(", a * stays"),
            ),
            NoticeText.spans("**Roboto Flex** ships as `res/raw/ofl.txt`, a * stays"),
        )
        assertEquals(listOf(NoticeSpan("an **unclosed mark")), NoticeText.spans("an **unclosed mark"))
    }

    @Test
    fun `plain licence texts split at blank lines and reflow, dropping rule lines`() {
        val blocks =
            NoticeText.parsePlain(
                "      MIT License\n\nCopyright (c) 2026\n\n   Line one\n   line two\n  \n-----\nTITLE\n=====\n",
            )
        assertEquals(
            listOf(
                NoticeBlock.Paragraph("MIT License"),
                NoticeBlock.Paragraph("Copyright (c) 2026"),
                NoticeBlock.Paragraph("Line one line two"),
                NoticeBlock.Paragraph("TITLE"),
            ),
            blocks,
        )
    }

    @Test
    fun `the bundled notices file leaves no Markdown syntax behind`() {
        val blocks = NoticeText.parseMarkdown(File("../THIRD_PARTY_NOTICES.md").readText())

        assertTrue(blocks.first() == NoticeBlock.Heading("Third-party notices", 1))
        val rows = blocks.filterIsInstance<NoticeBlock.TableRow>()
        assertTrue(rows.first().header)
        assertTrue(rows.drop(1).isNotEmpty() && rows.drop(1).none { it.header })
        val texts =
            blocks.flatMap {
                when (it) {
                    is NoticeBlock.Heading -> listOf(it.text)
                    is NoticeBlock.Paragraph -> listOf(it.text)
                    is NoticeBlock.Bullet -> listOf(it.text)
                    is NoticeBlock.TableRow -> it.cells
                }
            }
        assertFalse(texts.any { text -> text.startsWith("#") || text.startsWith("- ") || text.contains("|") })
        assertFalse(texts.flatMap(NoticeText::spans).any { "**" in it.text || "`" in it.text })
    }
}
