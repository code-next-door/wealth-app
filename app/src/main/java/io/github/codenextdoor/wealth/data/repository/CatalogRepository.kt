package io.github.codenextdoor.wealth.data.repository

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
        rows.map { AccountType(it.id, it.name, it.kind, it.countryId) }
    }

    val expenseCategories: Flow<List<ExpenseCategory>> =
        db.expenseCategoryDao().observeAll().map { rows ->
            rows.map { ExpenseCategory(it.id, it.name) }
        }

    suspend fun addCountry(name: String) {
        val dao = db.countryDao()
        dao.insert(CountryEntity(seedKey = null, name = name, sortOrder = dao.nextSortOrder()))
    }

    suspend fun renameCountry(id: Long, name: String) = db.countryDao().rename(id, name)

    suspend fun deleteCountry(id: Long) = db.countryDao().delete(id)

    suspend fun addAccountType(name: String, kind: AssetKind, countryId: Long?) {
        val dao = db.accountTypeDao()
        dao.insert(
            AccountTypeEntity(
                seedKey = null,
                name = name,
                kind = kind,
                countryId = countryId,
                sortOrder = dao.nextSortOrder(),
            ),
        )
    }

    suspend fun updateAccountType(id: Long, name: String, kind: AssetKind, countryId: Long?) {
        val dao = db.accountTypeDao()
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(name = name, kind = kind, countryId = countryId))
    }

    suspend fun deleteAccountType(id: Long) = db.accountTypeDao().delete(id)

    suspend fun addExpenseCategory(name: String) {
        val dao = db.expenseCategoryDao()
        dao.insert(ExpenseCategoryEntity(seedKey = null, name = name, sortOrder = dao.nextSortOrder()))
    }

    suspend fun renameExpenseCategory(id: Long, name: String) =
        db.expenseCategoryDao().rename(id, name)

    suspend fun deleteExpenseCategory(id: Long) = db.expenseCategoryDao().delete(id)
}
