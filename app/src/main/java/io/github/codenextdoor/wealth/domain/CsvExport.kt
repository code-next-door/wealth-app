package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Transactions and balance history as CSV files for a spreadsheet: UTF-8 with a byte
 * order mark (so Excel shows "Zürich" and "₹"), commas, CRLF lines, oldest first.
 * Amounts are plain numbers ("-45.30"; a currency without cents has none) and dates
 * ISO (2026-03-14), so spreadsheets read them in any language. Column names are a
 * file format, kept in English.
 */
object CsvExport {

    const val BOM = "\uFEFF"

    /** One line of the transactions file. [amountMinor] as stored: positive is money spent. */
    data class TransactionRow(
        val date: LocalDate,
        val description: String,
        val amountMinor: Long,
        val decimals: Int,
        val currencyCode: String,
        val category: String?,
        val kind: CashFlow.Kind,
        val account: String?,
        val note: String?,
        /** A split bill's whole amount, on each of its parts. */
        val splitOfMinor: Long? = null,
    )

    /** One balance entry. [balanceMinor] as stored: a debt is positive. */
    data class BalanceRow(
        val date: LocalDate,
        val account: String,
        val liability: Boolean,
        val type: String,
        val currencyCode: String,
        val balanceMinor: Long,
        val decimals: Int,
        val units: BigDecimal? = null,
    )

    /** A split expense becomes one row per part (as it's counted), each with the whole amount. */
    fun transactionRows(
        expenses: List<Expense>,
        categories: Map<Long, ExpenseCategory>,
        accountNames: Map<Long, String>,
        decimalsOf: (String) -> Int,
    ): List<TransactionRow> = expenses.flatMap { it.countedParts() }.map { expense ->
        val category = expense.categoryId?.let(categories::get)
        TransactionRow(
            date = expense.date,
            description = expense.description,
            amountMinor = expense.amountMinor,
            decimals = decimalsOf(expense.currencyCode),
            currencyCode = expense.currencyCode,
            category = category?.name,
            kind = CashFlow.kindOf(expense, category),
            account = expense.accountId?.let(accountNames::get),
            note = expense.note,
            splitOfMinor = expense.partOfMinor,
        )
    }

    fun transactions(rows: List<TransactionRow>): String = file(
        listOf("Date", "Description", "Amount", "Currency", "Category", "Counts as", "Account", "Note", "Split of"),
        rows.sortedBy { it.date }.map { row ->
            listOf(
                row.date.toString(),
                text(row.description),
                // Out negative, in positive, like a bank statement (stored the other way round).
                number(-row.amountMinor, row.decimals),
                row.currencyCode,
                text(row.category),
                when (row.kind) {
                    CashFlow.Kind.SPENDING -> "Spending"
                    CashFlow.Kind.INCOME -> "Income"
                    CashFlow.Kind.NOT_COUNTED -> "Not counted"
                },
                text(row.account),
                text(row.note),
                row.splitOfMinor?.let { number(-it, row.decimals) }.orEmpty(),
            )
        },
    )

    fun balances(rows: List<BalanceRow>): String = file(
        listOf("Date", "Account", "Asset or liability", "Account type", "Currency", "Balance", "Shares"),
        rows.sortedWith(compareBy({ it.date }, { it.account })).map { row ->
            listOf(
                row.date.toString(),
                text(row.account),
                if (row.liability) "Liability" else "Asset",
                text(row.type),
                row.currencyCode,
                number(if (row.liability) -row.balanceMinor else row.balanceMinor, row.decimals),
                row.units?.toPlainString().orEmpty(),
            )
        },
    )

    private fun file(header: List<String>, lines: List<List<String>>): String =
        BOM + (listOf(header) + lines).joinToString("") { cells -> cells.joinToString(",") + "\r\n" }

    private fun number(minor: Long, decimals: Int): String = BigDecimal.valueOf(minor, decimals).toPlainString()

    /**
     * Free text (statement lines, notes, names): a leading = + - @ would make a
     * spreadsheet run it as a formula, so it gets a ' in front; then quoted if needed.
     */
    private fun text(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val safe = if (value.first() in "=+-@\t\r") "'$value" else value
        return if (safe.any { it in ",\"\r\n" } || safe != safe.trim()) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
