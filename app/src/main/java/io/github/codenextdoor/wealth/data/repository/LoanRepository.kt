package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.LoanEntity
import io.github.codenextdoor.wealth.data.db.LoanRateChangeEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.LoanRateChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Calculated loans: a loan account's terms and rate changes. Its balance
 * entries stay the known outstanding amounts (see domain LoanBalance).
 */
class LoanRepository(private val db: WealthDatabase) {

    val loans: Flow<List<Loan>> = combine(db.loanDao().observeAll(), db.loanDao().observeRateChanges()) { loans, changes ->
        val byAccount = changes.groupBy { it.accountId }
        loans.map { it.toDomain(byAccount[it.accountId].orEmpty()) }
    }

    suspend fun forAccount(accountId: Long): Loan? =
        db.loanDao().forAccount(accountId)?.toDomain(db.loanDao().rateChangesFor(accountId))

    /** Saves the terms and replaces the rate changes, in one go. */
    suspend fun save(loan: Loan) = db.withTransaction {
        val dao = db.loanDao()
        val existing = dao.forAccount(loan.accountId)
        dao.upsert(
            LoanEntity(
                id = existing?.id ?: 0,
                accountId = loan.accountId,
                principalMinor = loan.principalMinor,
                firstEmiDate = loan.firstEmiDate.toEpochDay(),
                emiMinor = loan.emiMinor,
                yearlyRate = loan.yearlyRate.toPlainString(),
                emiCategoryId = loan.emiCategoryId,
            ),
        )
        dao.deleteRateChangesFor(loan.accountId)
        dao.insertRateChanges(
            loan.rateChanges.map { LoanRateChangeEntity(accountId = loan.accountId, fromDate = it.from.toEpochDay(), yearlyRate = it.yearlyRate.toPlainString(), emiMinor = it.emiMinor) },
        )
    }

    /** Back to a plain liability: its terms and rate changes go; its balance entries stay. */
    suspend fun delete(accountId: Long) = db.withTransaction {
        db.loanDao().deleteRateChangesFor(accountId)
        db.loanDao().deleteForAccount(accountId)
    }

    private fun LoanEntity.toDomain(changes: List<LoanRateChangeEntity>) = Loan(
        accountId = accountId,
        principalMinor = principalMinor,
        firstEmiDate = LocalDate.ofEpochDay(firstEmiDate),
        emiMinor = emiMinor,
        yearlyRate = BigDecimal(yearlyRate),
        emiCategoryId = emiCategoryId,
        rateChanges = changes.map { LoanRateChange(it.id, LocalDate.ofEpochDay(it.fromDate), BigDecimal(it.yearlyRate), it.emiMinor) },
    )
}
