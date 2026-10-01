package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.ExpenseEntity
import io.github.codenextdoor.wealth.data.db.RemovedImportEntity
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

    /** The oldest expense's day (how far back the year view goes); null without any. */
    val earliestDate: Flow<LocalDate?> = db.expenseDao().observeEarliestDate().map { day -> day?.let(LocalDate::ofEpochDay) }

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
        if (existing == null) {
            dao.insert(entity.copy(id = 0))
        } else {
            // Editing keeps where it came from, so a statement row isn't imported twice.
            dao.update(entity.copy(createdAt = existing.createdAt, importKey = existing.importKey, recurringId = existing.recurringId))
        }
    }

    /**
     * Deletes an expense. One that came from a statement is remembered by its
     * fingerprint, so importing or backfilling that statement again leaves it out.
     */
    suspend fun delete(id: Long) = db.withTransaction {
        val dao = db.expenseDao()
        dao.get(id)?.importKey?.let { dao.rememberRemoved(RemovedImportEntity(it, System.currentTimeMillis())) }
        dao.delete(id)
    }


    /** Which of [keys] were imported before. */
    suspend fun existingImportKeys(keys: List<String>): Set<String> =
        keys.chunked(500).flatMap { db.expenseDao().existingImportKeys(it) }.toSet()

    /** Which of [keys] were imported and then deleted by the user. */
    suspend fun removedImportKeys(keys: List<String>): Set<String> =
        keys.chunked(500).flatMap { db.expenseDao().removedImportKeys(it) }.toSet()

    /** A statement row to check against what's saved: its position, day and amount (money out positive). */
    data class Candidate(val index: Int, val date: LocalDate, val amountMinor: Long)

    /**
     * Rows that look already saved although their fingerprint is new: the same
     * account has an expense on that day with that amount (e.g. the same payment
     * from another file or format, whose text differs). Each saved expense matches
     * one row at most, so three equal coffees in a statement need three. Expenses
     * that came from this statement ([statementKeys]) don't count.
     */
    suspend fun possibleDuplicates(accountId: Long?, candidates: List<Candidate>, statementKeys: Collection<String>): Set<Int> {
        if (accountId == null || candidates.isEmpty()) return emptySet()
        val from = candidates.minOf { it.date }.toEpochDay()
        val to = candidates.maxOf { it.date }.toEpochDay()
        val keys = statementKeys.toSet()
        val saved = db.expenseDao().forAccountBetween(accountId, from, to)
            .filter { it.importKey == null || it.importKey !in keys }
            .toMutableList()
        return candidates.mapNotNull { c ->
            val match = saved.firstOrNull { it.date == c.date.toEpochDay() && it.amountMinor == c.amountMinor } ?: return@mapNotNull null
            saved.remove(match)
            c.index
        }.toSet()
    }

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
        // Imported on purpose (e.g. ticked again on the Import screen): no longer "deleted".
        fresh.mapNotNull { it.importKey }.chunked(500).forEach { db.expenseDao().forgetRemoved(it) }
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

    /** The current rules, knowing which categories are income (their rules only match money in). */
    suspend fun categorizer(): Categorizer =
        Categorizer(rules(), db.expenseCategoryDao().getAll().filter { it.isIncome }.map { it.id }.toSet())

    /**
     * Re-categorizes every expense whose category the user didn't pick, using
     * the current rules. Returns how many expenses changed.
     */
    suspend fun reapplyRules(): Int = db.withTransaction {
        val categorizer = categorizer()
        var changed = 0
        db.expenseDao().unlocked().forEach { expense ->
            val category = categorizer.categoryFor(expense.description, moneyOut = expense.amountMinor > 0)
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
        recurringId = recurringId,
    )
}
