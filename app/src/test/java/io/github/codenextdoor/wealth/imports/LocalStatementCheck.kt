package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the statement readers against a real statement on the developer's
 * own machine. Skipped unless -PstatementFile=/path is given. Prints only
 * counts and totals, never transactions, so real data stays out of logs.
 */
class LocalStatementCheck {

    @Test
    fun readLocalStatement() {
        val path = System.getProperty("statementFile")
        assumeTrue("No -PstatementFile given", path != null)
        val file = File(path!!)
        val text = if (file.extension.equals("pdf", ignoreCase = true)) {
            PDDocument.load(file).use { document ->
                PDFTextStripper().apply { sortByPosition = true }.getText(document)
            }
        } else {
            file.readText()
        }
        // Optional raw text dump, for working out a new layout. Keep it outside the project.
        System.getProperty("statementDump")?.let { File(it).writeText(text) }
        println("Extracted ${text.lines().size} lines")

        val parser = listOf<StatementParser>(UbsAccountStatementParser()).firstOrNull { it.canParse(text) }
        if (parser == null) {
            println("No PDF statement reader recognizes this layout")
            return
        }
        val parsed = parser.parse(text)
        val moneyOut = parsed.transactions.count { it.amount.signum() < 0 }
        println("Format: ${parsed.format}, currency: ${parsed.currency}")
        println("Transactions: ${parsed.transactions.size} ($moneyOut money out, ${parsed.transactions.size - moneyOut} money in)")
        println("Rows not matching the running balance: ${parsed.transactions.count { it.needsCheck }}")
        println("Rows with an empty description: ${parsed.transactions.count { it.description.isBlank() }}")
        println("Closing balance found: ${parsed.closingBalance != null}")
        val skip = Categorizer(DefaultData.skipImportKeywords.mapIndexed { i, k -> CategoryRule(i.toLong(), Categorizer.normalize(k), null) })
        println("Rows the default \"don't import\" rules skip: ${parsed.transactions.count { skip.match(it.description) != null }}")
    }
}
