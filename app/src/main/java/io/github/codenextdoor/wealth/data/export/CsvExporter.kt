package io.github.codenextdoor.wealth.data.export

import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.CsvExport
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.LoanBalance
import io.github.codenextdoor.wealth.domain.Pension
import io.github.codenextdoor.wealth.domain.PensionValue
import io.github.codenextdoor.wealth.domain.monthEndValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.time.LocalDate

/** Builds the CSV files ([CsvExport]) from what's saved. */
class CsvExporter(
    private val expenses: ExpenseRepository,
    private val catalog: CatalogRepository,
    private val accounts: AccountRepository,
    private val currencies: CurrencyRepository,
    private val loans: Flow<List<Loan>> = flowOf(emptyList()),
    private val pensions: Flow<List<Pension>> = flowOf(emptyList()),
    private val today: () -> LocalDate = LocalDate::now,
) {

    private suspend fun decimalsOf(): (String) -> Int {
        val decimals = currencies.currencies.first().associate { it.code to it.decimals }
        return { code -> decimals[code] ?: 2 }
    }

    suspend fun transactionsCsv(): String = CsvExport.transactions(
        CsvExport.transactionRows(
            expenses = expenses.allExpenses.first(),
            categories = catalog.expenseCategories.first().associateBy { it.id },
            accountNames = accounts.accounts.first().associate { it.id to it.name },
            decimalsOf = decimalsOf(),
        ),
    )

    suspend fun balancesCsv(): String {
        val decimalsOf = decimalsOf()
        val types = catalog.accountTypes.first().associateBy { it.id }
        val byId = accounts.accounts.first().associateBy { it.id }
        val entries = accounts.balanceEntries.first()
        fun row(accountId: Long, date: LocalDate, minor: Long, units: BigDecimal?, calculated: Boolean): CsvExport.BalanceRow? {
            val account = byId[accountId] ?: return null
            val type = types[account.accountTypeId]
            return CsvExport.BalanceRow(
                date = date,
                account = account.name,
                liability = type?.kind == AssetKind.LIABILITY,
                type = type?.name.orEmpty(),
                currencyCode = account.currencyCode,
                balanceMinor = minor,
                decimals = decimalsOf(account.currencyCode),
                units = units,
                calculated = calculated,
            )
        }
        val saved = entries.mapNotNull { row(it.accountId, it.date, it.balanceMinor, it.units, it.estimated) }
        // Calculated loans and pensions: their month-ends, as the account's balance history shows them.
        val known = entries.groupBy { it.accountId }.mapValues { (_, list) -> list.map { it.date to it.balanceMinor } }
        val day = today()
        val monthEnds = loans.first().flatMap { loan ->
            val k = known[loan.accountId].orEmpty()
            monthEndValues(k, day) { LoanBalance.at(loan, k, it) }.mapNotNull { (date, minor) -> row(loan.accountId, date, minor, null, true) }
        } + pensions.first().flatMap { pension ->
            val k = known[pension.accountId].orEmpty()
            monthEndValues(k, day) { PensionValue.at(pension, k, it) }.mapNotNull { (date, minor) -> row(pension.accountId, date, minor, null, true) }
        }
        return CsvExport.balances(saved + monthEnds)
    }
}
