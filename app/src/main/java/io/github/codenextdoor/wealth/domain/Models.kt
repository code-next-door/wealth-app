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
    /** Accounts of this type hold shares: valued as number of shares × price, plus cash. */
    val holdsShares: Boolean = false,
    /** Loans: an account of this type can have its outstanding calculated. */
    val isLoan: Boolean = false,
    /** Pensions: an account of this type can grow between known values (see PensionValue). */
    val growsWithContributions: Boolean = false,
    /** Set for types created from default data (e.g. "real_estate"); stable when renamed. */
    val seedKey: String? = null,
)

/**
 * [countsAsSpending] false: e.g. transfers to a broker, already in net worth.
 * [isIncome]: money in under it is income (salary…), not a refund.
 */
data class ExpenseCategory(
    val id: Long,
    val name: String,
    val countsAsSpending: Boolean = true,
    val isIncome: Boolean = false,
    /** The default it was seeded from (DefaultData), if any; survives renaming. */
    val seedKey: String? = null,
)

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
    /** The recurring expense that added this one, if any. */
    val recurringId: Long? = null,
    /** A split expense's extra parts; the expense itself keeps the rest (see [countedParts]). */
    val parts: List<ExpensePart> = emptyList(),
    /** Set on a part as counted (see [countedParts]): the whole expense's amount, for "part of …". */
    val partOfMinor: Long? = null,
) {
    /** What's left for the expense's own category: its amount minus its parts. */
    val restMinor: Long get() = amountMinor - parts.sumOf { it.amountMinor }

    /**
     * How the expense counts: as itself, or, when split, as the rest (its own category and
     * note) plus one row per part (its category, its note or else the expense's). Each keeps
     * the expense's id, day, account and text, so it opens the expense and filters like one.
     */
    fun countedParts(): List<Expense> =
        if (parts.isEmpty()) {
            listOf(this)
        } else {
            listOf(copy(amountMinor = restMinor, parts = emptyList(), partOfMinor = amountMinor)) +
                parts.map { copy(amountMinor = it.amountMinor, categoryId = it.categoryId, note = it.note ?: note, parts = emptyList(), partOfMinor = amountMinor) }
        }
}

/** One extra part of a split expense: [amountMinor] has the expense's sign. */
data class ExpensePart(val id: Long = 0, val amountMinor: Long, val categoryId: Long?, val note: String? = null)

/**
 * Statement text containing [keyword] (normalized) belongs to [categoryId]. Rules
 * only categorize: whether a category counts as spending is the category's own
 * switch. A null category is an old "don't import" rule (before seed version 9, or
 * from an old backup); it matches nothing.
 */
data class CategoryRule(val id: Long, val keyword: String, val categoryId: Long?)

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
    /** For accounts holding shares: the share's ticker symbol, e.g. "GOOG". Null for other accounts. */
    val shareSymbol: String? = null,
    /** Shares held in the latest entry; then [balanceMinor] is the cash beside them. */
    val units: BigDecimal? = null,
    /** Kept and listed, but left out of net worth (the user's choice). */
    val excludedFromNetWorth: Boolean = false,
)

/**
 * An account's balance on [date], in the account's currency (minor units).
 * For accounts holding shares, [units] is the number of shares and
 * [balanceMinor] the cash.
 */
data class BalanceEntry(
    val id: Long,
    val accountId: Long,
    val date: LocalDate,
    val balanceMinor: Long,
    val units: BigDecimal? = null,
)
