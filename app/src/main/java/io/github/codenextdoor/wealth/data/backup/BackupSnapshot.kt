package io.github.codenextdoor.wealth.data.backup

import io.github.codenextdoor.wealth.data.db.AccountEntity
import io.github.codenextdoor.wealth.data.db.AccountTypeEntity
import io.github.codenextdoor.wealth.data.db.BalanceEntryEntity
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.CountryEntity
import io.github.codenextdoor.wealth.data.db.CurrencyEntity
import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import io.github.codenextdoor.wealth.data.db.ExpenseCategoryEntity
import io.github.codenextdoor.wealth.data.db.ExpenseEntity
import io.github.codenextdoor.wealth.data.db.ExpensePartEntity
import io.github.codenextdoor.wealth.data.db.GrantEntity
import io.github.codenextdoor.wealth.data.db.LoanEntity
import io.github.codenextdoor.wealth.data.db.LoanRateChangeEntity
import io.github.codenextdoor.wealth.data.db.PropertyEntity
import io.github.codenextdoor.wealth.data.db.RecurringExpenseEntity
import io.github.codenextdoor.wealth.data.db.RemovedImportEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SharePriceEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the app stores, as it goes into (and comes out of) a backup. */
data class BackupSnapshot(
    val createdAt: Long,
    val currencies: List<CurrencyEntity>,
    val exchangeRates: List<ExchangeRateEntity>,
    val countries: List<CountryEntity>,
    val accountTypes: List<AccountTypeEntity>,
    val expenseCategories: List<ExpenseCategoryEntity>,
    val categoryRules: List<CategoryRuleEntity>,
    val accounts: List<AccountEntity>,
    val balanceEntries: List<BalanceEntryEntity>,
    val expenses: List<ExpenseEntity>,
    val settings: List<SettingEntity>,
    val sharePrices: List<SharePriceEntity> = emptyList(),
    val grants: List<GrantEntity> = emptyList(),
    val recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
    val properties: List<PropertyEntity> = emptyList(),
    val removedImports: List<RemovedImportEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val loanRateChanges: List<LoanRateChangeEntity> = emptyList(),
    val expenseParts: List<ExpensePartEntity> = emptyList(),
) {
    /**
     * JSON with fixed field names (the entities' names, or @SerialName where the
     * backup uses another), so backups keep working across app updates. When the
     * database changes, [FORMAT_VERSION] goes up; fields a newer app adds need a
     * default in the entity so older backups (without them) still read.
     * resources/backup/format-N.json in the tests are one backup of each format.
     */
    fun toJson(): String = json.encodeToString(
        BackupFile(
            format = FORMAT_VERSION,
            app = APP_ID,
            createdAt = createdAt,
            currencies = currencies,
            exchangeRates = exchangeRates,
            countries = countries,
            accountTypes = accountTypes,
            expenseCategories = expenseCategories,
            categoryRules = categoryRules,
            accounts = accounts,
            balanceEntries = balanceEntries,
            expenses = expenses,
            settings = settings,
            sharePrices = sharePrices,
            grants = grants,
            recurringExpenses = recurringExpenses,
            properties = properties,
            removedImports = removedImports,
            loans = loans,
            loanRateChanges = loanRateChanges,
            expenseParts = expenseParts,
        ),
    )

    companion object {
        /**
         * 2: rates say whether they were typed or fetched (version 1 rates were all typed).
         * 3: accounts holding shares, share prices, stock grants (absent before: none).
         * 4: recurring expenses, and the expenses they added (absent before: none).
         * 5: houses (absent before: none).
         * 6: accounts left out of net worth (absent before: all counted).
         * 7: categories left out of spending (absent before: all counted).
         * 8: income categories (absent before: all spending); deleted imports (absent before: none).
         * 9: calculated loans and their rate changes, loan types (absent before: none).
         * 10: parts of split expenses (absent before: none).
         */
        const val FORMAT_VERSION = 10
        private const val APP_ID = "io.github.codenextdoor.wealth"

        class UnsupportedBackup(message: String) : Exception(message)

        /**
         * Missing fields (older formats) read as their default; unknown ones are skipped.
         * Nulls are left out when writing, as they always were.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun fromJson(text: String): BackupSnapshot {
            // Check what it is first: a newer format may not fit this app's layout.
            val header = json.decodeFromString<Header>(text)
            if (header.app != APP_ID) throw UnsupportedBackup("Not a Wealth backup")
            if (header.format > FORMAT_VERSION) throw UnsupportedBackup("Backup is from a newer app version")
            val file = json.decodeFromString<BackupFile>(text)
            return BackupSnapshot(
                createdAt = file.createdAt,
                currencies = file.currencies,
                exchangeRates = file.exchangeRates,
                countries = file.countries,
                accountTypes = file.accountTypes,
                expenseCategories = file.expenseCategories,
                categoryRules = file.categoryRules,
                accounts = file.accounts,
                balanceEntries = file.balanceEntries,
                expenses = file.expenses,
                settings = file.settings,
                sharePrices = file.sharePrices,
                grants = file.grants,
                recurringExpenses = file.recurringExpenses,
                properties = file.properties,
                removedImports = file.removedImports,
                loans = file.loans,
                loanRateChanges = file.loanRateChanges,
                expenseParts = file.expenseParts,
            )
        }
    }
}

@Serializable
private class Header(val app: String? = null, val format: Int = 0)

/** The backup file's layout, in the order fields are written. */
@Serializable
private class BackupFile(
    val format: Int,
    val app: String,
    val createdAt: Long,
    val currencies: List<CurrencyEntity> = emptyList(),
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),
    val countries: List<CountryEntity> = emptyList(),
    val accountTypes: List<AccountTypeEntity> = emptyList(),
    val expenseCategories: List<ExpenseCategoryEntity> = emptyList(),
    val categoryRules: List<CategoryRuleEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val balanceEntries: List<BalanceEntryEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val sharePrices: List<SharePriceEntity> = emptyList(),
    val grants: List<GrantEntity> = emptyList(),
    val recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
    val properties: List<PropertyEntity> = emptyList(),
    val removedImports: List<RemovedImportEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val loanRateChanges: List<LoanRateChangeEntity> = emptyList(),
    val expenseParts: List<ExpensePartEntity> = emptyList(),
)
