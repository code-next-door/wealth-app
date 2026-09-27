package io.github.codenextdoor.wealth.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxTextTest {

    /** A minimal .xlsx like Excel writes: sheets named in the workbook, text in a shared table. */
    private fun xlsx(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private val workbook = """
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="Equity" sheetId="1" r:id="rId1"/><sheet name="Combined" sheetId="2" r:id="rId2"/></sheets>
        </workbook>
    """.trimIndent()

    private val rels = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="worksheet" Target="/xl/worksheets/sheet2.xml"/>
        </Relationships>
    """.trimIndent()

    private val strings = """
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <si><t>Symbol</t></si><si><t>Price</t></si><si><r><t>EXAMPLE</t></r><r><t xml:space="preserve"> LTD &amp; CO</t></r></si>
        </sst>
    """.trimIndent()

    private val sheet1 = """
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
          <row r="1"><c r="B1" t="s"><v>0</v></c><c r="D1" t="s"><v>1</v></c></row>
          <row r="3"><c r="B3" t="s"><v>2</v></c><c r="D3"><v>123.4567</v></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    private val sheet2 = """
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
          <row r="1"><c r="A1" t="inlineStr"><is><t>Total</t></is></c><c r="B1"><v>7</v></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    @Test
    fun readsSheetsInOrderWithCellsInTheirColumns() {
        val bytes = xlsx(
            mapOf(
                "xl/workbook.xml" to workbook,
                "xl/_rels/workbook.xml.rels" to rels,
                "xl/sharedStrings.xml" to strings,
                "xl/worksheets/sheet1.xml" to sheet1,
                "xl/worksheets/sheet2.xml" to sheet2,
            ),
        )
        assertEquals(
            "# Sheet: Equity\n\tSymbol\t\tPrice\n\tEXAMPLE LTD & CO\t\t123.4567\n# Sheet: Combined\nTotal\t7",
            XlsxText.extract(bytes),
        )
    }

    @Test
    fun notASpreadsheetGivesNothing() {
        assertNull(XlsxText.extract("not a zip".toByteArray()))
        assertNull(XlsxText.extract(xlsx(mapOf("hello.txt" to "hi"))))
    }
}
