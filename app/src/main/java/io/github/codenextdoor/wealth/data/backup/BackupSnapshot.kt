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
import io.github.codenextdoor.wealth.data.db.GrantEntity
import io.github.codenextdoor.wealth.data.db.PropertyEntity
import io.github.codenextdoor.wealth.data.db.RecurringExpenseEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SharePriceEntity
import io.github.codenextdoor.wealth.domain.AssetKind
import org.json.JSONArray
import org.json.JSONObject

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
) {
    /**
     * JSON with explicit field names, independent of the database layout, so
     * backups keep working across app updates. When the database changes,
     * [FORMAT_VERSION] goes up and [fromJson] learns to read older versions.
     */
    fun toJson(): String = JSONObject()
        .put("format", FORMAT_VERSION)
        .put("app", APP_ID)
        .put("createdAt", createdAt)
        .put("currencies", array(currencies) { JSONObject().put("code", it.code).put("name", it.name).put("decimals", it.decimals).put("sortOrder", it.sortOrder) })
        .put("exchangeRates", array(exchangeRates) { JSONObject().put("id", it.id).put("from", it.fromCode).put("to", it.toCode).put("date", it.date).put("rate", it.rate).put("source", it.source) })
        .put("countries", array(countries) { JSONObject().put("id", it.id).putOpt("seedKey", it.seedKey).put("name", it.name).put("sortOrder", it.sortOrder) })
        .put("accountTypes", array(accountTypes) {
            JSONObject().put("id", it.id).putOpt("seedKey", it.seedKey).put("name", it.name).put("kind", it.kind.name)
                .putOpt("countryId", it.countryId).put("sortOrder", it.sortOrder).put("holdsShares", it.holdsShares)
        })
        .put("expenseCategories", array(expenseCategories) { JSONObject().put("id", it.id).putOpt("seedKey", it.seedKey).put("name", it.name).put("sortOrder", it.sortOrder) })
        .put("categoryRules", array(categoryRules) { JSONObject().put("id", it.id).put("keyword", it.keyword).putOpt("categoryId", it.categoryId) })
        .put("accounts", array(accounts) {
            JSONObject().put("id", it.id).put("name", it.name).put("accountTypeId", it.accountTypeId).put("currency", it.currencyCode)
                .putOpt("countryId", it.countryId).put("balanceMinor", it.balanceMinor).put("balanceUpdatedAt", it.balanceUpdatedAt)
                .putOpt("institution", it.institution).putOpt("note", it.note)
                .putOpt("shareSymbol", it.shareSymbol).putOpt("units", it.units)
        })
        .put("balanceEntries", array(balanceEntries) { JSONObject().put("id", it.id).put("accountId", it.accountId).put("date", it.date).put("balanceMinor", it.balanceMinor).putOpt("units", it.units) })
        .put("expenses", array(expenses) {
            JSONObject().put("id", it.id).put("date", it.date).put("amountMinor", it.amountMinor).put("currency", it.currencyCode)
                .put("description", it.description).putOpt("categoryId", it.categoryId).put("categoryLocked", it.categoryLocked)
                .putOpt("accountId", it.accountId).putOpt("note", it.note).put("createdAt", it.createdAt).putOpt("importKey", it.importKey)
                .putOpt("recurringId", it.recurringId)
        })
        .put("settings", array(settings) { JSONObject().put("name", it.name).put("value", it.value) })
        .put("sharePrices", array(sharePrices) {
            JSONObject().put("id", it.id).put("symbol", it.symbol).put("date", it.date).put("price", it.price).put("source", it.source)
        })
        .put("grants", array(grants) {
            JSONObject().put("id", it.id).put("name", it.name).put("symbol", it.symbol).put("currency", it.currencyCode)
                .put("grantDate", it.grantDate).put("totalUnits", it.totalUnits).put("vestStart", it.vestStart)
                .put("vestMonths", it.vestMonths).put("intervalMonths", it.intervalMonths).put("cliffMonths", it.cliffMonths)
                .putOpt("note", it.note)
        })
        .put("recurringExpenses", array(recurringExpenses) {
            JSONObject().put("id", it.id).put("description", it.description).put("amountMinor", it.amountMinor)
                .put("currency", it.currencyCode).putOpt("categoryId", it.categoryId).putOpt("accountId", it.accountId)
                .put("intervalMonths", it.intervalMonths).put("startDate", it.startDate).putOpt("endDate", it.endDate)
                .putOpt("lastAdded", it.lastAdded)
        })
        .put("properties", array(properties) {
            JSONObject().put("id", it.id).put("accountId", it.accountId).put("purchasePriceMinor", it.purchasePriceMinor)
                .put("purchaseDate", it.purchaseDate).put("growthPercent", it.growthPercent).putOpt("loanAccountId", it.loanAccountId)
        })
        .toString()

    companion object {
        /**
         * 2: rates say whether they were typed or fetched (version 1 rates were all typed).
         * 3: accounts holding shares, share prices, stock grants (absent before: none).
         * 4: recurring expenses, and the expenses they added (absent before: none).
         * 5: houses (absent before: none).
         */
        const val FORMAT_VERSION = 5
        private const val APP_ID = "io.github.codenextdoor.wealth"

        class UnsupportedBackup(message: String) : Exception(message)

        fun fromJson(text: String): BackupSnapshot {
            val root = JSONObject(text)
            if (root.optString("app") != APP_ID) throw UnsupportedBackup("Not a Wealth backup")
            val format = root.getInt("format")
            if (format > FORMAT_VERSION) throw UnsupportedBackup("Backup is from a newer app version")
            return BackupSnapshot(
                createdAt = root.getLong("createdAt"),
                currencies = list(root, "currencies") { CurrencyEntity(it.getString("code"), it.getString("name"), it.getInt("decimals"), it.getInt("sortOrder")) },
                exchangeRates = list(root, "exchangeRates") {
                    ExchangeRateEntity(
                        it.getLong("id"), it.getString("from"), it.getString("to"), it.getLong("date"), it.getString("rate"),
                        it.optString("source", ExchangeRateEntity.MANUAL),
                    )
                },
                countries = list(root, "countries") { CountryEntity(it.getLong("id"), it.stringOrNull("seedKey"), it.getString("name"), it.getInt("sortOrder")) },
                accountTypes = list(root, "accountTypes") {
                    AccountTypeEntity(
                        it.getLong("id"), it.stringOrNull("seedKey"), it.getString("name"), AssetKind.valueOf(it.getString("kind")),
                        it.longOrNull("countryId"), it.getInt("sortOrder"), it.optBoolean("holdsShares", false),
                    )
                },
                expenseCategories = list(root, "expenseCategories") {
                    ExpenseCategoryEntity(it.getLong("id"), it.stringOrNull("seedKey"), it.getString("name"), it.getInt("sortOrder"))
                },
                categoryRules = list(root, "categoryRules") { CategoryRuleEntity(it.getLong("id"), it.getString("keyword"), it.longOrNull("categoryId")) },
                accounts = list(root, "accounts") {
                    AccountEntity(
                        id = it.getLong("id"), name = it.getString("name"), accountTypeId = it.getLong("accountTypeId"),
                        currencyCode = it.getString("currency"), countryId = it.longOrNull("countryId"),
                        balanceMinor = it.getLong("balanceMinor"), balanceUpdatedAt = it.getLong("balanceUpdatedAt"),
                        institution = it.stringOrNull("institution"), note = it.stringOrNull("note"),
                        shareSymbol = it.stringOrNull("shareSymbol"), units = it.stringOrNull("units"),
                    )
                },
                balanceEntries = list(root, "balanceEntries") {
                    BalanceEntryEntity(it.getLong("id"), it.getLong("accountId"), it.getLong("date"), it.getLong("balanceMinor"), it.stringOrNull("units"))
                },
                expenses = list(root, "expenses") {
                    ExpenseEntity(
                        id = it.getLong("id"), date = it.getLong("date"), amountMinor = it.getLong("amountMinor"),
                        currencyCode = it.getString("currency"), description = it.getString("description"),
                        categoryId = it.longOrNull("categoryId"), categoryLocked = it.getBoolean("categoryLocked"),
                        accountId = it.longOrNull("accountId"), note = it.stringOrNull("note"),
                        createdAt = it.getLong("createdAt"), importKey = it.stringOrNull("importKey"),
                        recurringId = it.longOrNull("recurringId"),
                    )
                },
                settings = list(root, "settings") { SettingEntity(it.getString("name"), it.getString("value")) },
                sharePrices = list(root, "sharePrices") {
                    SharePriceEntity(it.getLong("id"), it.getString("symbol"), it.getLong("date"), it.getString("price"), it.optString("source", ExchangeRateEntity.MANUAL))
                },
                grants = list(root, "grants") {
                    GrantEntity(
                        id = it.getLong("id"), name = it.getString("name"), symbol = it.getString("symbol"),
                        currencyCode = it.getString("currency"), grantDate = it.getLong("grantDate"),
                        totalUnits = it.getString("totalUnits"), vestStart = it.getLong("vestStart"),
                        vestMonths = it.getInt("vestMonths"), intervalMonths = it.getInt("intervalMonths"),
                        cliffMonths = it.getInt("cliffMonths"), note = it.stringOrNull("note"),
                    )
                },
                recurringExpenses = list(root, "recurringExpenses") {
                    RecurringExpenseEntity(
                        id = it.getLong("id"), description = it.getString("description"), amountMinor = it.getLong("amountMinor"),
                        currencyCode = it.getString("currency"), categoryId = it.longOrNull("categoryId"),
                        accountId = it.longOrNull("accountId"), intervalMonths = it.getInt("intervalMonths"),
                        startDate = it.getLong("startDate"), endDate = it.longOrNull("endDate"), lastAdded = it.longOrNull("lastAdded"),
                    )
                },
                properties = list(root, "properties") {
                    PropertyEntity(
                        id = it.getLong("id"), accountId = it.getLong("accountId"), purchasePriceMinor = it.getLong("purchasePriceMinor"),
                        purchaseDate = it.getLong("purchaseDate"), growthPercent = it.getString("growthPercent"),
                        loanAccountId = it.longOrNull("loanAccountId"),
                    )
                },
            )
        }

        private fun <T> array(items: List<T>, toJson: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(toJson(it)) } }

        private fun <T> list(root: JSONObject, key: String, read: (JSONObject) -> T): List<T> {
            val array = root.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { read(array.getJSONObject(it)) }
        }

        // isNull is also true for a missing key, so fields added in later versions read as null.
        private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
        private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    }
}
