package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Account
import java.math.RoundingMode

/**
 * The likely account for a statement, in its currency: for a share account
 * statement an account holding shares, for a card statement a liability
 * account, otherwise an asset account (bank statements aren't for cards or
 * loans). Preferably one whose name or bank appears in the file; else any
 * such account.
 */
fun guessAccount(
    accounts: List<Account>,
    liabilityTypes: Set<Long>,
    file: StatementFile,
    statement: ParsedStatement?,
    /** Seed key -> type id; with [ParsedStatement.accountTypeKey], accounts of that type come first. */
    seededTypes: Map<String, Long> = emptyMap(),
): Long? {
    val fromCard = statement?.fromCard == true
    val fitting = accounts.filter {
        val kindFits = if (statement?.holdings != null) it.shareSymbol != null else (it.accountTypeId in liabilityTypes) == fromCard
        (statement?.currency == null || it.currencyCode == statement.currency) && kindFits
    }
    // E.g. a mutual fund statement goes to a Mutual funds account, not the first INR bank account.
    val statedType = statement?.accountTypeKey?.let(seededTypes::get)
    val candidates = fitting.filter { it.accountTypeId == statedType }.ifEmpty { fitting }
    val hint = listOfNotNull(file.name, statement?.issuer, file.text.take(2000)).joinToString(" ").uppercase()
    // The account whose bank or name appears first: a statement names its issuer near the
    // top, and other banks (e.g. where to pay the bill) further down.
    val named = candidates.mapNotNull { a ->
        listOfNotNull(a.institution, a.name).filter { it.isNotBlank() }
            .map { hint.indexOf(it.uppercase()) }.filter { it >= 0 }.minOrNull()?.let { a to it }
    }.minByOrNull { it.second }?.first
    return (named ?: candidates.firstOrNull())?.id
}

/** A statement row this many days from a recurring expense's day counts as the same payment. */
private const val RECURRING_DAYS = 5L

/**
 * Money-out rows that a recurring expense already added: same amount and
 * currency, within [RECURRING_DAYS] days, each added expense used once.
 * Returns row index → the recurring expense's description.
 */
suspend fun matchRecurring(
    expenses: ExpenseRepository,
    statement: ParsedStatement,
    accountId: Long?,
    currency: String,
    decimals: Int,
): Map<Int, String> {
    val dates = statement.transactions.map { it.date }
    val first = dates.minOrNull() ?: return emptyMap()
    val candidates = expenses.addedByRecurring(first.minusDays(RECURRING_DAYS), dates.max().plusDays(RECURRING_DAYS))
        .filter { it.currencyCode == currency && (accountId == null || it.accountId == null || it.accountId == accountId) }
        .toMutableList()
    return statement.transactions.withIndex().mapNotNull { (index, t) ->
        if (t.amount.signum() >= 0) return@mapNotNull null
        val minor = t.amount.negate().movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).toLong()
        val match = candidates
            .filter { it.amountMinor == minor && kotlin.math.abs(it.date.toEpochDay() - t.date.toEpochDay()) <= RECURRING_DAYS }
            .minByOrNull { kotlin.math.abs(it.date.toEpochDay() - t.date.toEpochDay()) }
            ?: return@mapNotNull null
        candidates.remove(match)
        index to match.description
    }.toMap()
}
