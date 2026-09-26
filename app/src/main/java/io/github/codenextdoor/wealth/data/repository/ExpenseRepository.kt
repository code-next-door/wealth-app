package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.ExpenseEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** Expenses and the keyword rules that categorize them. */
class ExpenseRepository(private val db: WealthDatabase) {

    fun expensesBetween(from: LocalDate, to: LocalDate): Flow<List<Expense>> =
        db.expenseDao().observeBetween(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Expense? = db.expenseDao().get(id)?.toDomain()

    /** Inserts when [Expense.id] is 0, otherwise updates. */
    suspend fun save(expense: Expense) {
        val entity = ExpenseEntity(
            id = expense.id,
            date = expense.date.toEpochDay(),
            amountMinor = expense.amountMinor,
            currencyCode = expense.currencyCode,
            description = expense.description,
            categoryId = expense.categoryId,
            categoryLocked = expense.categoryLocked,
            accountId = expense.accountId,
            note = expense.note,
            createdAt = System.currentTimeMillis(),
        )
        val dao = db.expenseDao()
        val existing = if (expense.id == 0L) null else dao.get(expense.id)
        if (existing == null) dao.insert(entity.copy(id = 0)) else dao.update(entity.copy(createdAt = existing.createdAt))
    }

    suspend fun delete(id: Long) = db.expenseDao().delete(id)

    /** Which of [keys] were imported before. */
    suspend fun existingImportKeys(keys: List<String>): Set<String> =
        keys.chunked(500).flatMap { db.expenseDao().existingImportKeys(it) }.toSet()

    /**
     * Saves imported expenses in one go, skipping any whose import key already
     * exists. Returns how many were added.
     */
    suspend fun importExpenses(expenses: List<Pair<Expense, String>>): Int = db.withTransaction {
        val existing = existingImportKeys(expenses.map { it.second })
        val now = System.currentTimeMillis()
        val fresh = expenses.filter { it.second !in existing }.map { (e, key) ->
            ExpenseEntity(
                date = e.date.toEpochDay(),
                amountMinor = e.amountMinor,
                currencyCode = e.currencyCode,
                description = e.description,
                categoryId = e.categoryId,
                categoryLocked = e.categoryLocked,
                accountId = e.accountId,
                note = e.note,
                createdAt = now,
                importKey = key,
            )
        }
        db.expenseDao().insertAll(fresh)
        fresh.size
    }

    /** Set after an import so the Spending tab can show the imported month. */
    val showMonthRequest = MutableStateFlow<YearMonth?>(null)

    val rules: Flow<List<CategoryRule>> = db.categoryRuleDao().observeAll().map { rows ->
        rows.map { CategoryRule(it.id, it.keyword, it.categoryId) }
    }

    /**
     * Saves a rule (new when [id] is null); a null [categoryId] means "don't
     * import". The keyword is normalized; if another rule has it, it's replaced.
     */
    suspend fun saveRule(id: Long?, keyword: String, categoryId: Long?) {
        val normalized = Categorizer.normalize(keyword)
        if (normalized.isEmpty()) return
        db.withTransaction {
            if (id != null) db.categoryRuleDao().delete(id)
            db.categoryRuleDao().upsert(CategoryRuleEntity(keyword = normalized, categoryId = categoryId))
        }
    }

    suspend fun deleteRule(id: Long) = db.categoryRuleDao().delete(id)

    /**
     * Re-categorizes every expense whose category the user didn't pick, using
     * the current rules. Returns how many expenses changed.
     */
    suspend fun reapplyRules(): Int = db.withTransaction {
        val categorizer = Categorizer(rules())
        var changed = 0
        db.expenseDao().unlocked().forEach { expense ->
            val rule = categorizer.match(expense.description)
            // "Don't import" rules only matter when importing; leave saved expenses alone.
            if (rule?.skipsImport == true) return@forEach
            val category = rule?.categoryId
            if (category != expense.categoryId) {
                db.expenseDao().updateCategory(expense.id, category)
                changed++
            }
        }
        changed
    }

    /** The current rules, for categorizing new expenses. */
    suspend fun rules(): List<CategoryRule> =
        db.categoryRuleDao().getAll().map { CategoryRule(it.id, it.keyword, it.categoryId) }

    private fun ExpenseEntity.toDomain() = Expense(
        id = id,
        date = LocalDate.ofEpochDay(date),
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        description = description,
        categoryId = categoryId,
        categoryLocked = categoryLocked,
        accountId = accountId,
        note = note,
    )
}
