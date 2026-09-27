package io.github.codenextdoor.wealth.imports

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Turns an Excel .xlsx file into plain text, so statement readers work on it
 * like on PDF text. An .xlsx file is a zip of XML files; this reads them with
 * Java's built-in zip and XML support (no library).
 *
 * Each sheet becomes "# Sheet: <name>" followed by its rows, one line each,
 * cells separated by tabs in their columns (A, B, C, ...; empty cells stay
 * empty, so a column is always at the same position).
 */
object XlsxText {

    /** The file's text, or null if it isn't a readable .xlsx file. */
    fun extract(bytes: ByteArray): String? = runCatching {
        val files = unzip(bytes)
        val workbook = files["xl/workbook.xml"]?.let(::parse) ?: return null
        val targets = files["xl/_rels/workbook.xml.rels"]?.let(::parse)?.let { rels ->
            rels.elements("Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
        }.orEmpty()
        val shared = files["xl/sharedStrings.xml"]?.let(::parse)?.let { doc ->
            doc.elements("si").map { si -> si.elements("t").joinToString("") { it.textContent } }
        }.orEmpty()

        workbook.elements("sheet").joinToString("\n") { sheet ->
            val target = targets[sheet.getAttribute("r:id")].orEmpty()
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val rows = files[path]?.let(::parse)?.elements("row").orEmpty().map { row -> rowText(row, shared) }
            (listOf("# Sheet: ${sheet.getAttribute("name")}") + rows).joinToString("\n")
        }
    }.getOrNull()

    private fun rowText(row: Element, shared: List<String>): String {
        val cells = row.elements("c").associate { cell ->
            val column = columnIndex(cell.getAttribute("r"))
            val value = cell.elements("v").firstOrNull()?.textContent.orEmpty()
            column to when (cell.getAttribute("t")) {
                "s" -> value.toIntOrNull()?.let(shared::getOrNull).orEmpty()
                "inlineStr" -> cell.elements("t").joinToString("") { it.textContent }
                else -> value
            }
        }
        val last = cells.keys.maxOrNull() ?: return ""
        return (0..last).joinToString("\t") { cells[it].orEmpty().replace('\t', ' ').replace('\n', ' ') }
    }

    /** "B7" → 1 (A = 0). */
    private fun columnIndex(reference: String): Int =
        reference.takeWhile { it.isLetter() }.fold(0) { index, letter -> index * 26 + (letter - 'A' + 1) } - 1

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                // Only the parts needed; limits memory for large files.
                if (entry.name.startsWith("xl/") && entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) put(entry.name, zip.readBytes())
            }
        }
    }

    private fun parse(bytes: ByteArray): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // Spreadsheets never need external entities; refuse them where the parser supports
        // it (Android's parser doesn't fetch them anyway).
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement

    /** Descendant elements with this tag (namespace prefix ignored). */
    private fun Element.elements(tag: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                if (child.tagName.substringAfter(':') == tag) result += child
                walk(child)
            }
        }
        walk(this)
        return result
    }
}
