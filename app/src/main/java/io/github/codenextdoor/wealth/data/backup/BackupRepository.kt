package io.github.codenextdoor.wealth.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a backup contains, shown before restoring. */
data class BackupSummary(
    val createdAt: Long,
    val accounts: Int,
    val balanceEntries: Int,
    val expenses: Int,
    val rules: Int,
)

/**
 * Exports everything into one password-encrypted file and restores it. The
 * file is written wherever the user chooses (phone storage, or Google Drive
 * through Android's file picker), and is readable only with the password.
 */
class BackupRepository(private val db: WealthDatabase, private val context: Context) {

    suspend fun snapshot(): BackupSnapshot = db.withTransaction {
        val dao = db.backupDao()
        BackupSnapshot(
            createdAt = System.currentTimeMillis(),
            currencies = dao.currencies(),
            exchangeRates = dao.exchangeRates(),
            countries = dao.countries(),
            accountTypes = dao.accountTypes(),
            expenseCategories = dao.expenseCategories(),
            categoryRules = dao.categoryRules(),
            accounts = dao.accounts(),
            balanceEntries = dao.balanceEntries(),
            expenses = dao.expenses(),
            settings = dao.settings(),
            sharePrices = dao.sharePrices(),
            grants = dao.grants(),
        )
    }

    suspend fun export(uri: Uri, password: CharArray) {
        val json = snapshot().toJson()
        val encrypted = withContext(Dispatchers.Default) { BackupCrypto.encrypt(json.toByteArray(), password) }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(encrypted) }
        }
    }

    /** Decrypts and reads a backup file. Throws the [BackupCrypto] errors for wrong passwords or other files. */
    suspend fun read(uri: Uri, password: CharArray): BackupSnapshot {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
        return withContext(Dispatchers.Default) { BackupSnapshot.fromJson(String(BackupCrypto.decrypt(bytes, password))) }
    }

    /** Replaces all data with [snapshot], in one transaction: all or nothing. */
    suspend fun restore(snapshot: BackupSnapshot) {
        db.withTransaction {
            val dao = db.backupDao()
            dao.clearExpenses()
            dao.clearGrants()
            dao.clearSharePrices()
            dao.clearCategoryRules()
            dao.clearBalanceEntries()
            dao.clearAccounts()
            dao.clearExchangeRates()
            dao.clearAccountTypes()
            dao.clearExpenseCategories()
            dao.clearCountries()
            dao.clearCurrencies()
            dao.clearSettings()

            dao.insertCurrencies(snapshot.currencies)
            dao.insertCountries(snapshot.countries)
            dao.insertAccountTypes(snapshot.accountTypes)
            dao.insertExpenseCategories(snapshot.expenseCategories)
            dao.insertExchangeRates(snapshot.exchangeRates)
            dao.insertAccounts(snapshot.accounts)
            dao.insertBalanceEntries(snapshot.balanceEntries)
            dao.insertCategoryRules(snapshot.categoryRules)
            dao.insertExpenses(snapshot.expenses)
            dao.insertSettings(snapshot.settings)
            dao.insertSharePrices(snapshot.sharePrices)
            dao.insertGrants(snapshot.grants)
        }
    }

    companion object {
        fun summarize(snapshot: BackupSnapshot) = BackupSummary(
            createdAt = snapshot.createdAt,
            accounts = snapshot.accounts.size,
            balanceEntries = snapshot.balanceEntries.size,
            expenses = snapshot.expenses.size,
            rules = snapshot.categoryRules.size,
        )
    }
}
