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
import org.json.JSONObject

/**
 * The backup reader as it was in v0.2.2 (org.json), frozen here unchanged:
 * phones still on that version must be able to open backups the current
 * app writes (see BackupFormatsTest).
 */
object LegacyBackupReader {
    private const val APP_ID = "io.github.codenextdoor.wealth"

    fun fromJson(text: String): BackupSnapshot {
        val root = JSONObject(text)
        if (root.optString("app") != APP_ID) throw BackupSnapshot.Companion.UnsupportedBackup("Not a Wealth backup")
        val format = root.getInt("format")
        if (format > 5) throw BackupSnapshot.Companion.UnsupportedBackup("Backup is from a newer app version")
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

    private fun <T> list(root: JSONObject, key: String, read: (JSONObject) -> T): List<T> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { read(array.getJSONObject(it)) }
    }

    // isNull is also true for a missing key, so fields added in later versions read as null.
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
