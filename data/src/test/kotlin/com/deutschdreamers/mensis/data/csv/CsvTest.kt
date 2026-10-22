package com.deutschdreamers.mensis.data.csv

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class CsvTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("readCases")
    fun `reads RFC 4180 input`(
        name: String,
        input: String,
        expected: List<List<String>>,
    ) {
        assertEquals(expected, Csv.read(input), name)
    }

    @Test
    fun `quotes only fields that need it and ends lines with CRLF`() {
        val text =
            Csv.write(
                listOf(listOf("date", "notes"), listOf("2026-03-06", "said \"hi\", left\nearly"), listOf("plain", "")),
            )
        assertEquals("date,notes\r\n2026-03-06,\"said \"\"hi\"\", left\nearly\"\r\nplain,\r\n", text)
    }

    @Test
    fun `detects comma, semicolon and tab delimiters from the header outside quotes`() {
        assertEquals(',', Csv.detectDelimiter("date,flow\n1;2;3;4"))
        assertEquals(';', Csv.detectDelimiter("Datum;Blutung;\"a,b,c\"\n"))
        assertEquals('\t', Csv.detectDelimiter("date\tflow\tmood"))
        assertEquals(',', Csv.detectDelimiter("date"))
    }

    @Test
    fun `table pads short rows and trims header cells`() {
        val table = CsvTable.parse(" date , flow \n2026-03-06\n")
        assertEquals(listOf("date", "flow"), table.header)
        assertEquals("", table.cell(table.rows.single(), 1))
    }

    @Test
    fun `write then read is the identity for any fields`() {
        val field =
            Arb
                .list(
                    Arb.element("a", "Z", "1", " ", ",", ";", "\"", "\n", "\r\n", "é", "🙂", "\t"),
                    0..10,
                ).map {
                    it.joinToString("")
                }
        val rows = Arb.list(Arb.list(field, 3..3), 1..10)
        runBlocking {
            checkAll(300, rows) { table -> assertEquals(table, Csv.read(Csv.write(table), ',')) }
        }
    }

    companion object {
        @JvmStatic
        fun readCases(): List<Arguments> =
            listOf(
                Arguments.of("simple", "a,b\n1,2\n", listOf(listOf("a", "b"), listOf("1", "2"))),
                Arguments.of("CRLF and no final newline", "a,b\r\n1,2", listOf(listOf("a", "b"), listOf("1", "2"))),
                Arguments.of("bare CR", "a,b\r1,2\r", listOf(listOf("a", "b"), listOf("1", "2"))),
                Arguments.of("BOM stripped", "\uFEFFa,b\n", listOf(listOf("a", "b"))),
                Arguments.of("quoted delimiter", "\"a,b\",c\n", listOf(listOf("a,b", "c"))),
                Arguments.of("doubled quote", "\"say \"\"hi\"\"\",x\n", listOf(listOf("say \"hi\"", "x"))),
                Arguments.of("line break in quotes", "\"line 1\nline 2\",x\n", listOf(listOf("line 1\nline 2", "x"))),
                Arguments.of("empty fields", ",,\n", listOf(listOf("", "", ""))),
                Arguments.of("blank lines skipped", "a\n\n\nb\n", listOf(listOf("a"), listOf("b"))),
                Arguments.of("quoted empty field kept", "\"\"\n", listOf(listOf(""))),
                Arguments.of("unterminated quote is lenient", "\"open,x\n", listOf(listOf("open,x\n"))),
                Arguments.of("semicolons", "a;b\n1;2", listOf(listOf("a", "b"), listOf("1", "2"))),
            )
    }
}
