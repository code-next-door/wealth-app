package io.github.codenextdoor.wealth.data.export

import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.CsvExport
import kotlinx.coroutines.flow.first

/** Builds the CSV files ([CsvExport]) from what's saved. */
class CsvExporter(
    private val expenses: ExpenseRepository,
    private val catalog: CatalogRepository,
    private val accounts: AccountRepository,
    private val currencies: CurrencyRepository,
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
        val rows = accounts.balanceEntries.first().mapNotNull { entry ->
            val account = byId[entry.accountId] ?: return@mapNotNull null
            val type = types[account.accountTypeId]
            CsvExport.BalanceRow(
                date = entry.date,
                account = account.name,
                liability = type?.kind == AssetKind.LIABILITY,
                type = type?.name.orEmpty(),
                currencyCode = account.currencyCode,
                balanceMinor = entry.balanceMinor,
                decimals = decimalsOf(account.currencyCode),
                units = entry.units,
            )
        }
        return CsvExport.balances(rows)
    }
}
