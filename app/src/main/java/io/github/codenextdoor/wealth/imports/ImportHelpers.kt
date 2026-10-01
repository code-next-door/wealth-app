package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.ExpenseCategory
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

/**
 * A statement row's category when importing or backfilling: the matching rule's. Money
 * into a card that no rule knows (paying the bill, a refund) goes to "Credit card
 * payments", which isn't spending: listed, never counted, and a refund can be filed by
 * hand. Nothing is left out by a rule; a category's own switch decides what counts.
 */
fun importCategory(transaction: StatementTransaction, fromCard: Boolean, categorizer: Categorizer, categories: List<ExpenseCategory>): Long? {
    val moneyIn = transaction.amount.signum() > 0
    return categorizer.categoryFor(transaction.description, moneyOut = !moneyIn)
        ?: if (moneyIn && fromCard) categories.firstOrNull { it.seedKey == DefaultData.cardPaymentsCategory.key }?.id else null
}

/**
 * What's already known about a statement's rows for an account, by position: saved
 * before ([imported], by fingerprint), deleted by the user ([removed]), or probably
 * saved from another file ([possibleDuplicates]: same day and amount). Import starts
 * these unticked; backfill leaves them out.
 */
data class KnownRows(
    val keys: List<String>,
    val imported: Set<String>,
    val removed: Set<String>,
    val possibleDuplicates: Set<Int>,
) {
    fun isImported(index: Int) = keys.getOrNull(index) in imported
    fun wasRemoved(index: Int) = keys.getOrNull(index) in removed
    fun isKnown(index: Int) = isImported(index) || wasRemoved(index) || index in possibleDuplicates

    companion object {
        val NONE = KnownRows(emptyList(), emptySet(), emptySet(), emptySet())
    }
}

suspend fun knownRows(expenses: ExpenseRepository, statement: ParsedStatement, accountId: Long?, decimals: Int): KnownRows {
    val keys = ImportKeys.forTransactions(statement.transactions, accountId)
    val imported = expenses.existingImportKeys(keys)
    val removed = expenses.removedImportKeys(keys)
    val candidates = statement.transactions.withIndex()
        .filter { (index, _) -> keys[index] !in imported && keys[index] !in removed }
        .map { (index, t) ->
            // Stored like an expense: money out positive.
            ExpenseRepository.Candidate(index, t.date, t.amount.negate().movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact())
        }
    return KnownRows(keys, imported, removed, expenses.possibleDuplicates(accountId, candidates, keys))
}

