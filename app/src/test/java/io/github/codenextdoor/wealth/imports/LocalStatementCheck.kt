package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the statement readers against a real (text or CSV) statement on the
 * developer's own machine. Skipped unless -PstatementFile=/path is given. Prints only
 * counts and totals, never transactions, so real data stays out of logs.
 */
class LocalStatementCheck {

    @Test
    fun readLocalStatement() {
        val path = System.getProperty("statementFile")
        assumeTrue("No -PstatementFile given", path != null)
        val file = File(path!!)
        // PDFs are read by Android's PDF engine, so they're checked on a device
        // (LocalStatementDeviceCheck); here, text, CSV and .xlsx files.
        assumeTrue("PDFs are checked on the device", !file.extension.equals("pdf", ignoreCase = true))
        val text = if (file.extension.equals("xlsx", ignoreCase = true)) XlsxText.extract(file.readBytes()) ?: "" else file.readText()
        // Optional raw text dump, for working out a new layout. Keep it outside the project.
        System.getProperty("statementDump")?.let { File(it).writeText(text) }
        println("Extracted ${text.lines().size} lines")

        val parsed = ImportViewModel.STATEMENT_PARSERS.firstOrNull { it.canParse(text) }?.parse(text)
            ?: CsvReader.read(text).let { rows -> CsvStatementParser.guessMapping(rows)?.let { CsvStatementParser.parse(rows, it) } }
        if (parsed == null) {
            println("No reader recognizes this layout")
            return
        }
        val moneyOut = parsed.transactions.count { it.amount.signum() < 0 }
        println("Format: ${parsed.format}, currency: ${parsed.currency}")
        println("Transactions: ${parsed.transactions.size} ($moneyOut money out, ${parsed.transactions.size - moneyOut} money in)")
        println("Rows not matching the running balance: ${parsed.transactions.count { it.needsCheck }}")
        println("Rows with an empty description: ${parsed.transactions.count { it.description.isBlank() }}")
        println("Closing balance found: ${parsed.closingBalance != null}, date found: ${parsed.closingDate != null}")
        println("Balance matches the statement's details: ${!parsed.valueNeedsCheck}")
        println("History points: ${StatementHistory.points(parsed).size}")
        val skip = Categorizer(DefaultData.skipImportKeywords.mapIndexed { i, k -> CategoryRule(i.toLong(), Categorizer.normalize(k), null) })
        println("Rows the default \"don't import\" rules skip: ${parsed.transactions.count { skip.match(it.description) != null }}")
    }
}
