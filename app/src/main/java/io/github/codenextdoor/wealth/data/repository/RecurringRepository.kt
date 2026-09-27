package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.ExpenseEntity
import io.github.codenextdoor.wealth.data.db.RecurringExpenseEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Recurrence
import io.github.codenextdoor.wealth.domain.RecurringExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Recurring expenses, and adding them as ordinary expenses when they fall due. */
class RecurringRepository(private val db: WealthDatabase) {

    val recurring: Flow<List<RecurringExpense>> = db.recurringExpenseDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): RecurringExpense? = db.recurringExpenseDao().get(id)?.toDomain()

    /** Inserts when [RecurringExpense.id] is 0, otherwise updates (keeping what was already added). Returns the id. */
    suspend fun save(item: RecurringExpense): Long {
        val dao = db.recurringExpenseDao()
        val entity = RecurringExpenseEntity(
            id = item.id,
            description = item.description,
            amountMinor = item.amountMinor,
            currencyCode = item.currencyCode,
            categoryId = item.categoryId,
            accountId = item.accountId,
            intervalMonths = item.intervalMonths,
            startDate = item.startDate.toEpochDay(),
            endDate = item.endDate?.toEpochDay(),
            lastAdded = item.lastAdded?.toEpochDay(),
        )
        return if (item.id == 0L) {
            dao.insert(entity.copy(id = 0))
        } else {
            dao.update(entity.copy(lastAdded = dao.get(item.id)?.lastAdded))
            item.id
        }
    }

    /** The expenses it already added stay (they happened); they just lose the link. */
    suspend fun delete(id: Long) = db.recurringExpenseDao().delete(id)

    /**
     * Adds every recurring expense that fell due up to [today] and wasn't added
     * yet, as an ordinary expense on its day. Returns how many were added.
     */
    suspend fun addDue(today: LocalDate): Int = db.withTransaction {
        val dao = db.recurringExpenseDao()
        val now = System.currentTimeMillis()
        dao.getAll().sumOf { entity ->
            val item = entity.toDomain()
            val due = Recurrence.due(item, today)
            if (due.isEmpty()) return@sumOf 0
            db.expenseDao().insertAll(
                due.map { date ->
                    ExpenseEntity(
                        date = date.toEpochDay(),
                        amountMinor = item.amountMinor,
                        currencyCode = item.currencyCode,
                        description = item.description,
                        categoryId = item.categoryId,
                        // Chosen with the recurring expense, so rules leave it alone.
                        categoryLocked = item.categoryId != null,
                        accountId = item.accountId,
                        note = null,
                        createdAt = now,
                        recurringId = item.id,
                    )
                },
            )
            dao.setLastAdded(item.id, due.last().toEpochDay())
            due.size
        }
    }

    private fun RecurringExpenseEntity.toDomain() = RecurringExpense(
        id = id,
        description = description,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        categoryId = categoryId,
        accountId = accountId,
        intervalMonths = intervalMonths,
        startDate = LocalDate.ofEpochDay(startDate),
        endDate = endDate?.let(LocalDate::ofEpochDay),
        lastAdded = lastAdded?.let(LocalDate::ofEpochDay),
    )
}
