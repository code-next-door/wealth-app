package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.AccountTypeEntity
import io.github.codenextdoor.wealth.data.db.CountryEntity
import io.github.codenextdoor.wealth.data.db.ExpenseCategoryEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-configurable lists: countries, account types and expense categories. */
class CatalogRepository(private val db: WealthDatabase) {

    val countries: Flow<List<Country>> = db.countryDao().observeAll().map { rows ->
        rows.map { Country(it.id, it.name) }
    }

    val accountTypes: Flow<List<AccountType>> = db.accountTypeDao().observeAll().map { rows ->
        rows.map { AccountType(it.id, it.name, it.kind, it.countryId, it.holdsShares, isLoan = it.isLoan, growsWithContributions = it.growsWithContributions, seedKey = it.seedKey) }
    }

    val expenseCategories: Flow<List<ExpenseCategory>> =
        db.expenseCategoryDao().observeAll().map { rows ->
            rows.map { ExpenseCategory(it.id, it.name, it.countsAsSpending, it.isIncome, it.seedKey) }
        }

    suspend fun addCountry(name: String) {
        val dao = db.countryDao()
        dao.insert(CountryEntity(seedKey = null, name = name, sortOrder = dao.nextSortOrder()))
    }

    suspend fun renameCountry(id: Long, name: String) = db.countryDao().rename(id, name)

    suspend fun deleteCountry(id: Long) = db.countryDao().delete(id)

    suspend fun addAccountType(
        name: String,
        kind: AssetKind,
        countryId: Long?,
        holdsShares: Boolean = false,
        isLoan: Boolean = false,
        growsWithContributions: Boolean = false,
    ) {
        val dao = db.accountTypeDao()
        dao.insert(
            AccountTypeEntity(
                seedKey = null,
                name = name,
                kind = kind,
                countryId = countryId,
                sortOrder = dao.nextSortOrder(),
                holdsShares = holdsShares,
                isLoan = isLoan && kind == AssetKind.LIABILITY,
                growsWithContributions = growsWithContributions && kind == AssetKind.ASSET,
            ),
        )
    }

    /**
     * [holdsShares] / [isLoan] / [growsWithContributions] null keep the current setting;
     * only liabilities can be loans, only assets grow with contributions.
     */
    suspend fun updateAccountType(
        id: Long,
        name: String,
        kind: AssetKind,
        countryId: Long?,
        holdsShares: Boolean? = null,
        isLoan: Boolean? = null,
        growsWithContributions: Boolean? = null,
    ) {
        val dao = db.accountTypeDao()
        val existing = dao.get(id) ?: return
        dao.update(
            existing.copy(
                name = name,
                kind = kind,
                countryId = countryId,
                holdsShares = holdsShares ?: existing.holdsShares,
                isLoan = (isLoan ?: existing.isLoan) && kind == AssetKind.LIABILITY,
                growsWithContributions = (growsWithContributions ?: existing.growsWithContributions) && kind == AssetKind.ASSET,
            ),
        )
    }

    /** Returns false (and deletes nothing) if accounts still use this type. */
    suspend fun deleteAccountType(id: Long): Boolean {
        if (db.accountDao().countWithType(id) > 0) return false
        db.accountTypeDao().delete(id)
        return true
    }

    suspend fun addExpenseCategory(name: String, isIncome: Boolean = false) {
        val dao = db.expenseCategoryDao()
        dao.insert(ExpenseCategoryEntity(seedKey = null, name = name, sortOrder = dao.nextSortOrder(), isIncome = isIncome))
    }

    /** Spending or income; what its expenses already are doesn't change, only how they're counted. */
    suspend fun setIncome(id: Long, isIncome: Boolean) = db.expenseCategoryDao().setIncome(id, isIncome)

    suspend fun renameExpenseCategory(id: Long, name: String) =
        db.expenseCategoryDao().rename(id, name)

    /** Saves the order chosen in settings: every category list and dropdown follows it. */
    suspend fun reorderExpenseCategories(ids: List<Long>) = db.withTransaction {
        val dao = db.expenseCategoryDao()
        ids.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }

    suspend fun setCountsAsSpending(id: Long, counts: Boolean) =
        db.expenseCategoryDao().setCountsAsSpending(id, counts)

    suspend fun deleteExpenseCategory(id: Long) = db.expenseCategoryDao().delete(id)
}
