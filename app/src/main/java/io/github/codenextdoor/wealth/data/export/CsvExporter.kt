package io.github.codenextdoor.wealth.data.export

import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.CashFlow
import io.github.codenextdoor.wealth.domain.CsvExport
import io.github.codenextdoor.wealth.domain.LoanInterest
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.LoanBalance
import io.github.codenextdoor.wealth.domain.Pension
import io.github.codenextdoor.wealth.domain.PensionValue
import io.github.codenextdoor.wealth.domain.Property
import io.github.codenextdoor.wealth.domain.PropertyValue
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.domain.monthEndValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Builds the CSV files ([CsvExport]) from what's saved. */
class CsvExporter(
    private val expenses: ExpenseRepository,
    private val catalog: CatalogRepository,
    private val accounts: AccountRepository,
    private val currencies: CurrencyRepository,
    private val loans: Flow<List<Loan>> = flowOf(emptyList()),
    private val pensions: Flow<List<Pension>> = flowOf(emptyList()),
    private val houses: Flow<List<Property>> = flowOf(emptyList()),
    private val today: () -> LocalDate = LocalDate::now,
) {

    private suspend fun decimalsOf(): (String) -> Int {
        val decimals = currencies.currencies.first().associate { it.code to it.decimals }
        return { code -> decimals[code] ?: 2 }
    }

    suspend fun transactionsCsv(): String {
        val all = expenses.allExpenses.first()
        val categories = catalog.expenseCategories.first().associateBy { it.id }
        val accountList = accounts.accounts.first()
        val decimalsOf = decimalsOf()
        // EMIs split into interest and principal exactly as the Spending tab counts them.
        val splits = CashFlow.of(
            all, categories, currencies.rateBook.first(), currencies.baseCurrency.first(), decimalsOf,
            LoanInterest.lookup(loans.first(), accounts.balanceEntries.first(), accountList),
        ).splits
        return CsvExport.transactions(
            CsvExport.transactionRows(all, categories, accountList.associate { it.id to it.name }, decimalsOf, splits),
        )
    }

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
        // Calculated loans, pensions and houses: their month-ends, worked out like their values everywhere else.
        val known = entries.groupBy { it.accountId }.mapValues { (_, list) -> list.map { it.date to it.balanceMinor } }
        val day = today()
        val monthEnds = loans.first().flatMap { loan ->
            val k = known[loan.accountId].orEmpty()
            monthEndValues(k, day) { LoanBalance.at(loan, k, it) }.mapNotNull { (date, minor) -> row(loan.accountId, date, minor, null, true) }
        } + pensions.first().flatMap { pension ->
            val k = known[pension.accountId].orEmpty()
            monthEndValues(k, day) { PensionValue.at(pension, k, it) }.mapNotNull { (date, minor) -> row(pension.accountId, date, minor, null, true) }
        } + houses.first().flatMap { house ->
            val k = known[house.accountId].orEmpty()
            val decimals = byId[house.accountId]?.let { decimalsOf(it.currencyCode) } ?: return@flatMap emptyList()
            val anchors = k.map { (date, minor) -> date to minorToDecimal(minor, decimals) }
            monthEndValues(k, day) { date ->
                PropertyValue.at(anchors, house.growthPercent, date).movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).toLong()
            }.mapNotNull { (date, minor) -> row(house.accountId, date, minor, null, true) }
        }
        return CsvExport.balances(saved + monthEnds)
    }
}
