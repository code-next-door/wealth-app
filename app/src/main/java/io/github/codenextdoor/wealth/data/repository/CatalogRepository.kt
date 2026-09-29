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
        rows.map { AccountType(it.id, it.name, it.kind, it.countryId, it.holdsShares, it.seedKey) }
    }

    val expenseCategories: Flow<List<ExpenseCategory>> =
        db.expenseCategoryDao().observeAll().map { rows ->
            rows.map { ExpenseCategory(it.id, it.name, it.countsAsSpending) }
        }

    suspend fun addCountry(name: String) {
        val dao = db.countryDao()
        dao.insert(CountryEntity(seedKey = null, name = name, sortOrder = dao.nextSortOrder()))
    }

    suspend fun renameCountry(id: Long, name: String) = db.countryDao().rename(id, name)

    suspend fun deleteCountry(id: Long) = db.countryDao().delete(id)

    suspend fun addAccountType(name: String, kind: AssetKind, countryId: Long?, holdsShares: Boolean = false) {
        val dao = db.accountTypeDao()
        dao.insert(
            AccountTypeEntity(
                seedKey = null,
                name = name,
                kind = kind,
                countryId = countryId,
                sortOrder = dao.nextSortOrder(),
                holdsShares = holdsShares,
            ),
        )
    }

    /** [holdsShares] null keeps the current setting. */
    suspend fun updateAccountType(id: Long, name: String, kind: AssetKind, countryId: Long?, holdsShares: Boolean? = null) {
        val dao = db.accountTypeDao()
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(name = name, kind = kind, countryId = countryId, holdsShares = holdsShares ?: existing.holdsShares))
    }

    /** Returns false (and deletes nothing) if accounts still use this type. */
    suspend fun deleteAccountType(id: Long): Boolean {
        if (db.accountDao().countWithType(id) > 0) return false
        db.accountTypeDao().delete(id)
        return true
    }

    suspend fun addExpenseCategory(name: String): Long {
        val dao = db.expenseCategoryDao()
        return dao.insert(ExpenseCategoryEntity(seedKey = null, name = name, sortOrder = dao.nextSortOrder()))
    }

    /**
     * The category called [name] (ignoring case and surrounding spaces), added
     * if there's none yet, so adding one from a form never makes a duplicate.
     * Returns its id, or null for a blank name.
     */
    suspend fun findOrAddExpenseCategory(name: String): Long? {
        val trimmed = name.trim().ifEmpty { return null }
        return db.withTransaction {
            db.expenseCategoryDao().getAll().firstOrNull { it.name.trim().equals(trimmed, ignoreCase = true) }?.id
                ?: addExpenseCategory(trimmed)
        }
    }

    suspend fun renameExpenseCategory(id: Long, name: String) =
        db.expenseCategoryDao().rename(id, name)

    /** Whether the category's expenses count as spending (switched off: listed, but left out of totals). */
    suspend fun setExpenseCategoryCounted(id: Long, counted: Boolean) =
        db.expenseCategoryDao().setCountsAsSpending(id, counted)

    suspend fun deleteExpenseCategory(id: Long) = db.expenseCategoryDao().delete(id)
}
