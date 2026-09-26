package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.domain.normalizeNumberInput
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Which columns of a CSV export hold what. Column numbers are 0-based. */
data class CsvMapping(
    val headerRow: Int,
    val dateColumn: Int,
    val descriptionColumns: List<Int>,
    /** A single signed amount column... */
    val amountColumn: Int? = null,
    /** ...or separate money-out / money-in columns. */
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    val balanceColumn: Int? = null,
    val currencyColumn: Int? = null,
    val datePattern: String,
    /** For a single amount column: true when money out is negative (bank style), false when charges are positive (card style). */
    val negativeIsMoneyOut: Boolean = true,
)

/**
 * Reads bank and card CSV exports. The layout is guessed from the header row
 * (English, German, French and Italian names), and can be overridden with a
 * [CsvMapping] when the guess is wrong.
 */
object CsvStatementParser {

    val DATE_PATTERNS = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd.MM.yy", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd-MM-yyyy")

    // Header words, most specific first. Matched case-insensitively as substrings.
    private val DATE_HEADERS = listOf(
        "booking date", "buchungsdatum", "trade date", "transaction date", "abschlussdatum", "einkaufsdatum",
        "date", "datum", "data",
    )
    private val DESCRIPTION_HEADERS = listOf(
        "description", "beschreibung", "buchungstext", "text", "merchant", "händler", "details", "payee",
        "empfänger", "libellé", "descrizione", "booking text",
    )
    private val DEBIT_HEADERS = listOf("debit", "belastung", "lastschrift", "soll", "débit", "addebito", "money out")
    private val CREDIT_HEADERS = listOf("credit", "gutschrift", "haben", "crédit", "accredito", "money in")
    private val AMOUNT_HEADERS = listOf("amount", "betrag", "montant", "importo")
    private val BALANCE_HEADERS = listOf("balance", "saldo", "solde")
    private val EXCLUDED_DATE_HEADERS = listOf("value", "valuta")
    private val CURRENCY_HEADERS = listOf("currency", "währung", "ccy", "devise", "divisa")
    private val CURRENCY_KEYS = listOf("valued in", "currency", "währung", "devise", "divisa")
    private val ISO_CODE = Regex("[A-Z]{3}")

    // Key/value lines above the table, e.g. "Closing balance:;1234.56".
    private val CLOSING_KEYS = listOf("closing balance", "schlusssaldo", "solde final", "saldo finale", "saldo final")
    private val PERIOD_END_KEYS = listOf("until", "bis", "to:", "jusqu", "fino")

    /** Guesses the layout of [rows]; null if no header row with a date and an amount is found. */
    fun guessMapping(rows: List<List<String>>): CsvMapping? {
        for ((index, row) in rows.withIndex().take(40)) {
            val headers = row.map { it.trim().lowercase(Locale.ROOT) }
            val date = findColumn(headers, DATE_HEADERS, exclude = EXCLUDED_DATE_HEADERS) ?: continue
            val debit = findColumn(headers, DEBIT_HEADERS)
            val credit = findColumn(headers, CREDIT_HEADERS)
            val amount = findColumn(headers, AMOUNT_HEADERS)
            if ((debit == null || credit == null) && amount == null) continue

            val data = rows.drop(index + 1)
            val pattern = guessDatePattern(data.mapNotNull { it.getOrNull(date) }) ?: continue
            val descriptions = headers.indices.filter { i ->
                i != date && DESCRIPTION_HEADERS.any { headers[i].contains(it) }
            }
            val useDebitCredit = debit != null && credit != null
            val negativeIsMoneyOut = if (useDebitCredit || amount == null) {
                true
            } else {
                val values = data.mapNotNull { it.getOrNull(amount)?.let(::parseCsvAmount) }
                values.count { it.signum() < 0 } >= values.count { it.signum() > 0 }
            }
            return CsvMapping(
                headerRow = index,
                dateColumn = date,
                descriptionColumns = descriptions,
                amountColumn = if (useDebitCredit) null else amount,
                debitColumn = if (useDebitCredit) debit else null,
                creditColumn = if (useDebitCredit) credit else null,
                balanceColumn = findColumn(headers, BALANCE_HEADERS),
                currencyColumn = findColumn(headers, CURRENCY_HEADERS),
                datePattern = pattern,
                negativeIsMoneyOut = negativeIsMoneyOut,
            )
        }
        return null
    }

    fun parse(rows: List<List<String>>, mapping: CsvMapping, currency: String? = null): ParsedStatement {
        val formatter = formatter(mapping.datePattern)
        val transactions = mutableListOf<Pair<StatementTransaction, BigDecimal?>>()
        for (row in rows.drop(mapping.headerRow + 1)) {
            val date = row.getOrNull(mapping.dateColumn)?.trim()?.let { parseDate(it, formatter) } ?: continue
            val amount = amountOf(row, mapping) ?: continue
            if (amount.signum() == 0) continue
            val description = mapping.descriptionColumns
                .mapNotNull { row.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .joinToString(" · ")
            val balance = mapping.balanceColumn?.let { row.getOrNull(it)?.let(::parseCsvAmount) }
            transactions += StatementTransaction(date, amount, description) to balance
        }

        // Closing balance: from the header section if present, else the latest row's balance.
        val meta = rows.take(mapping.headerRow)
        val metaClosing = meta.firstNotNullOfOrNull { row -> valueFor(row, CLOSING_KEYS)?.let(::parseCsvAmount) }
        val metaEnd = meta.firstNotNullOfOrNull { row -> valueFor(row, PERIOD_END_KEYS)?.let { parseAnyDate(it) } }
        val latest = transactions.filter { it.second != null }.maxByOrNull { it.first.date }
        // Currency: as given, else the most common code in a currency column, else a "Valued in: CHF" line.
        val columnCurrency = mapping.currencyColumn?.let { column ->
            rows.drop(mapping.headerRow + 1)
                .mapNotNull { row -> row.getOrNull(column)?.trim()?.takeIf { ISO_CODE.matches(it) } }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }
        val metaCurrency = meta.firstNotNullOfOrNull { row -> valueFor(row, CURRENCY_KEYS)?.takeIf { ISO_CODE.matches(it) } }
        return ParsedStatement(
            format = "CSV",
            currency = currency ?: columnCurrency ?: metaCurrency,
            transactions = transactions.map { it.first }.sortedByDescending { it.date },
            closingBalance = metaClosing ?: latest?.second,
            closingDate = if (metaClosing != null) metaEnd ?: transactions.maxOfOrNull { it.first.date } else latest?.first?.date,
        )
    }

    private fun amountOf(row: List<String>, mapping: CsvMapping): BigDecimal? {
        mapping.amountColumn?.let { column ->
            val value = row.getOrNull(column)?.let(::parseCsvAmount) ?: return null
            return if (mapping.negativeIsMoneyOut) value else value.negate()
        }
        // Debit cells may be written with or without a minus sign.
        val debit = mapping.debitColumn?.let { row.getOrNull(it)?.let(::parseCsvAmount) }?.abs()
        val credit = mapping.creditColumn?.let { row.getOrNull(it)?.let(::parseCsvAmount) }?.abs()
        if (debit == null && credit == null) return null
        return (credit ?: BigDecimal.ZERO) - (debit ?: BigDecimal.ZERO)
    }

    private fun findColumn(headers: List<String>, names: List<String>, exclude: List<String> = emptyList()): Int? =
        names.firstNotNullOfOrNull { name ->
            headers.indexOfFirst { h -> h.contains(name) && exclude.none { h.contains(it) } }.takeIf { it >= 0 }
        }

    /** The first pattern that reads (nearly) every date cell. */
    private fun guessDatePattern(cells: List<String>): String? {
        val values = cells.map { it.trim() }.filter { it.isNotEmpty() }.take(50)
        if (values.isEmpty()) return null
        return DATE_PATTERNS.firstOrNull { pattern ->
            val f = formatter(pattern)
            val ok = values.count { parseDate(it, f) != null }
            ok > 0 && ok >= values.size * 0.6
        }
    }

    private fun valueFor(row: List<String>, keys: List<String>): String? {
        val key = row.firstOrNull()?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (keys.none { key.startsWith(it) }) return null
        return row.drop(1).firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun parseAnyDate(text: String): LocalDate? =
        DATE_PATTERNS.firstNotNullOfOrNull { parseDate(text.trim(), formatter(it)) }

    private fun formatter(pattern: String) =
        DateTimeFormatter.ofPattern(pattern.replace("yyyy", "uuuu").replace("yy", "uu")).withResolverStyle(ResolverStyle.STRICT)

    private fun parseDate(text: String, formatter: DateTimeFormatter): LocalDate? =
        runCatching { LocalDate.parse(text.substringBefore(' '), formatter) }.getOrNull()
}

/** Amounts as banks write them: "1'234.50", "-45.30", "1.234,50", "12.50-", "(12.50)". */
internal fun parseCsvAmount(text: String): BigDecimal? {
    var s = text.trim().removePrefix("CHF").removePrefix("EUR").removePrefix("USD").trim()
    if (s.isEmpty()) return null
    var negative = false
    if (s.startsWith("(") && s.endsWith(")")) {
        negative = true
        s = s.substring(1, s.length - 1)
    }
    if (s.endsWith("-")) {
        negative = true
        s = s.dropLast(1)
    }
    val value = normalizeNumberInput(s).toBigDecimalOrNull() ?: return null
    return if (negative) value.negate() else value
}

/** Minimal CSV reading: quotes, doubled quotes, and the delimiter each file uses. */
object CsvReader {

    fun read(text: String): List<List<String>> = parse(text.removePrefix("﻿"), detectDelimiter(text))

    /** The candidate that appears most consistently in the first lines (outside quotes). */
    fun detectDelimiter(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(30).toList()
        return listOf(';', ',', '\t', '|').maxByOrNull { d ->
            val counts = lines.map { line -> countOutsideQuotes(line, d) }.filter { it > 0 }
            // Favor delimiters found on many lines with a steady count (the table rows).
            counts.groupingBy { it }.eachCount().maxOfOrNull { it.value * 100 + it.key } ?: 0
        } ?: ';'
    }

    fun parse(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> { row += cell.toString(); cell.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var inQuotes = false
        return line.count { c ->
            if (c == '"') inQuotes = !inQuotes
            !inQuotes && c == d
        }
    }
}
