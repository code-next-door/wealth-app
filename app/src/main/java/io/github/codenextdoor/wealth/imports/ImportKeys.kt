package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.domain.Categorizer

/**
 * Fingerprints for statement rows, so importing the same statement twice
 * doesn't create duplicates. Identical rows within one statement (two equal
 * coffees on the same day) get a running number, which stays the same when
 * that statement is imported again.
 */
object ImportKeys {
    fun forTransactions(transactions: List<StatementTransaction>, accountId: Long?): List<String> {
        val seen = HashMap<String, Int>()
        return transactions.map { t ->
            val base = listOf(
                accountId ?: 0,
                t.date,
                t.amount.stripTrailingZeros().toPlainString(),
                Categorizer.normalize(t.description).take(80),
            ).joinToString("|")
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            if (n == 1) base else "$base#$n"
        }
    }
}
