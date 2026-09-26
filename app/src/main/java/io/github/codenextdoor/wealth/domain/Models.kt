package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class Currency(
    /** ISO 4217 code, e.g. "CHF". */
    val code: String,
    val name: String,
    /** Digits after the decimal point for amounts (2 for CHF, 0 for JPY). */
    val decimals: Int,
)

/** 1 unit of [from] equals [rate] units of [to]. */
data class ExchangeRate(
    val from: String,
    val to: String,
    val rate: BigDecimal,
    val updatedAt: Instant,
)

/** Whether something adds to (asset) or subtracts from (liability) net worth. */
enum class AssetKind { ASSET, LIABILITY }

data class Country(val id: Long, val name: String)

data class AccountType(
    val id: Long,
    val name: String,
    val kind: AssetKind,
    /** Null means "General": not tied to a country. */
    val countryId: Long?,
)

data class ExpenseCategory(val id: Long, val name: String)

data class Expense(
    val id: Long,
    val date: LocalDate,
    /** Minor units; positive is money spent, negative a refund. */
    val amountMinor: Long,
    val currencyCode: String,
    /** Statement text or the user's own description; what rules match against. */
    val description: String,
    val categoryId: Long?,
    /** True when the user chose the category, so rules leave it alone. */
    val categoryLocked: Boolean,
    val accountId: Long?,
    val note: String?,
)

/** Statement text containing [keyword] (normalized) belongs to [categoryId]. */
data class CategoryRule(val id: Long, val keyword: String, val categoryId: Long)

data class Account(
    val id: Long,
    val name: String,
    val accountTypeId: Long,
    val currencyCode: String,
    /** Null means "General" (no country). */
    val countryId: Long?,
    /** Minor units. For liabilities: the amount owed, as a positive number. */
    val balanceMinor: Long,
    val balanceUpdatedAt: Instant,
    val institution: String?,
    val note: String?,
)

/** An account's balance on [date], in the account's currency (minor units). */
data class BalanceEntry(
    val id: Long,
    val accountId: Long,
    val date: LocalDate,
    val balanceMinor: Long,
)
