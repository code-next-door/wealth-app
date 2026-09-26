package io.github.codenextdoor.wealth.data.repository

import io.github.codenextdoor.wealth.data.db.AccountEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The user's accounts, assets and liabilities. */
class AccountRepository(private val db: WealthDatabase) {

    val accounts: Flow<List<Account>> = db.accountDao().observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun get(id: Long): Account? = db.accountDao().get(id)?.toDomain()

    /** Inserts when [Account.id] is 0, otherwise updates. */
    suspend fun save(account: Account) {
        val dao = db.accountDao()
        val existing = if (account.id == 0L) null else dao.get(account.id)
        val balanceChanged = existing == null || existing.balanceMinor != account.balanceMinor
        val entity = AccountEntity(
            id = account.id,
            name = account.name,
            accountTypeId = account.accountTypeId,
            currencyCode = account.currencyCode,
            countryId = account.countryId,
            balanceMinor = account.balanceMinor,
            // Only a real balance change moves the "updated" date.
            balanceUpdatedAt = if (balanceChanged) System.currentTimeMillis() else existing!!.balanceUpdatedAt,
            institution = account.institution,
            note = account.note,
        )
        if (existing == null) dao.insert(entity.copy(id = 0)) else dao.update(entity)
    }

    suspend fun delete(id: Long) = db.accountDao().delete(id)

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
}
