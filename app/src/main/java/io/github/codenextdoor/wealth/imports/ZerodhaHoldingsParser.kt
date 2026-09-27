package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Zerodha holdings export (.xlsx, read by [XlsxText]). Each file is a full
 * snapshot, so only its total matters: the Combined sheet's "Present Value"
 * (stocks and mutual funds, in INR) on the "... Holdings Statement as on
 * YYYY-MM-DD" date. Holdings aren't merged between files; each file is one
 * balance on its own date. The total is checked against the rows (quantity ×
 * previous closing price).
 */
class ZerodhaHoldingsParser : StatementParser {

    override fun canParse(text: String): Boolean = text.contains("Holdings Statement as on") && text.contains("Present Value")

    override fun parse(text: String): ParsedStatement {
        val sheets = text.split(Regex("(?m)^# Sheet: ")).filter { it.isNotBlank() }
        // The Combined sheet has stocks and funds together; older exports may only have Equity.
        val sheet = sheets.firstOrNull { it.startsWith("Combined") } ?: sheets.firstOrNull { it.startsWith("Equity") } ?: text
        val rows = sheet.lines().map { line -> line.split("\t").map { it.trim() } }

        val date = rows.firstNotNullOfOrNull { row -> row.firstNotNullOfOrNull { AS_ON.find(it)?.groupValues?.get(1) } }?.let(LocalDate::parse)
        val presentValue = rows.firstNotNullOfOrNull { row ->
            val i = row.indexOf("Present Value")
            if (i >= 0) row.getOrNull(i + 1)?.toBigDecimalOrNull() else null
        }

        val header = rows.indexOfFirst { "Symbol" in it && "Quantity Available" in it && "Previous Closing Price" in it }
        val holdings = if (header < 0) emptyList() else {
            val columns = rows[header]
            val symbol = columns.indexOf("Symbol")
            val quantity = columns.indexOf("Quantity Available")
            val price = columns.indexOf("Previous Closing Price")
            rows.drop(header + 1).mapNotNull { row ->
                val q = row.getOrNull(quantity)?.toBigDecimalOrNull() ?: return@mapNotNull null
                val p = row.getOrNull(price)?.toBigDecimalOrNull() ?: return@mapNotNull null
                if (row.getOrNull(symbol).isNullOrBlank()) null else q * p
            }
        }
        val rowsTotal = holdings.fold(BigDecimal.ZERO, BigDecimal::add)
        return ParsedStatement(
            format = "Zerodha holdings",
            currency = "INR",
            transactions = emptyList(),
            closingBalance = presentValue,
            closingDate = date,
            // Rounding over dozens of rows can add up to a little; more than a rupee is a real difference.
            valueNeedsCheck = presentValue == null || (presentValue - rowsTotal).abs() > BigDecimal.ONE,
            // The file itself never says Zerodha.
            issuer = "Zerodha",
        )
    }

    private companion object {
        val AS_ON = Regex("""Holdings Statement as on (\d{4}-\d{2}-\d{2})""")
    }
}
