package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.AccountEntity
import io.github.codenextdoor.wealth.data.db.BalanceEntryEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.BalanceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The user's accounts, assets and liabilities, and their balance history. */
class AccountRepository(private val db: WealthDatabase) {

    val accounts: Flow<List<Account>> = db.accountDao().observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val balanceEntries: Flow<List<BalanceEntry>> = db.balanceEntryDao().observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observeHistory(accountId: Long): Flow<List<BalanceEntry>> =
        db.balanceEntryDao().observeForAccount(accountId).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Account? = db.accountDao().get(id)?.toDomain()

    /**
     * Inserts when [Account.id] is 0, otherwise updates the account's details.
     * When [recordBalance] is true, [Account.balanceMinor] is also stored as the
     * balance on [balanceDate] (replacing any entry for that day). The account's
     * current balance is always its most recent entry by date.
     */
    suspend fun save(account: Account, balanceDate: LocalDate, recordBalance: Boolean) {
        db.withTransaction {
            val dao = db.accountDao()
            val entity = AccountEntity(
                id = account.id,
                name = account.name,
                accountTypeId = account.accountTypeId,
                currencyCode = account.currencyCode,
                countryId = account.countryId,
                balanceMinor = account.balanceMinor,
                balanceUpdatedAt = balanceDate.toEpochMillis(),
                institution = account.institution,
                note = account.note,
            )
            val existing = if (account.id == 0L) null else dao.get(account.id)
            val id = if (existing == null) {
                dao.insert(entity.copy(id = 0))
            } else {
                // Keep the cached balance until it's recomputed from history below.
                dao.update(entity.copy(balanceMinor = existing.balanceMinor, balanceUpdatedAt = existing.balanceUpdatedAt))
                existing.id
            }
            if (recordBalance || existing == null) {
                db.balanceEntryDao().upsert(
                    BalanceEntryEntity(accountId = id, date = balanceDate.toEpochDay(), balanceMinor = account.balanceMinor),
                )
            }
            refreshCachedBalance(id)
        }
    }

    /** Deletes one history entry. The last remaining entry can't be deleted. */
    suspend fun deleteHistoryEntry(entryId: Long) {
        db.withTransaction {
            val entries = db.balanceEntryDao()
            val entry = entries.get(entryId) ?: return@withTransaction
            if (entries.countFor(entry.accountId) <= 1) return@withTransaction
            entries.delete(entryId)
            refreshCachedBalance(entry.accountId)
        }
    }

    /** Also deletes the account's balance history (database cascade). */
    suspend fun delete(id: Long) = db.accountDao().delete(id)

    private suspend fun refreshCachedBalance(accountId: Long) {
        val latest = db.balanceEntryDao().latestFor(accountId) ?: return
        db.accountDao().updateCachedBalance(
            accountId,
            latest.balanceMinor,
            LocalDate.ofEpochDay(latest.date).toEpochMillis(),
        )
    }

    private fun LocalDate.toEpochMillis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun AccountEntity.toDomain() = Account(
        id = id,
        name = name,
        accountTypeId = accountTypeId,
        currencyCode = currencyCode,
        countryId = countryId,
        balanceMinor = balanceMinor,
        balanceUpdatedAt = Instant.ofEpochMilli(balanceUpdatedAt),
        institution = institution,
        note = note,
    )

    private fun BalanceEntryEntity.toDomain() =
        BalanceEntry(id, accountId, LocalDate.ofEpochDay(date), balanceMinor)
}
