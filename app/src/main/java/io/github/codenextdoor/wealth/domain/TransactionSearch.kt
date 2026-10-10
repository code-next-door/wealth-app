package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth

/**
 * Finding transactions by what was typed. Every word must match somewhere: the
 * statement text, the note, the category, the account, or a split part's note or
 * category (case and accents ignored, so "zurich" finds "ZÜRICH"). A word that is a
 * number also matches amounts, either way and of any part: "45.30" exactly, "45"
 * anything from 45.00 to 45.99.
 */
object TransactionSearch {

    /** One transaction with the names it's shown with. */
    data class Candidate(
        val expense: Expense,
        val decimals: Int,
        val categoryName: String?,
        val accountName: String?,
        val partCategoryNames: List<String> = emptyList(),
    )

    /** Money out and in for one currency (minor units, both positive). */
    data class Total(val currencyCode: String, val outMinor: Long, val inMinor: Long)

    class Query internal constructor(private val words: List<Word>) {
        fun matches(candidate: Candidate): Boolean {
            val text = with(candidate) {
                listOfNotNull(expense.description, expense.note, categoryName, accountName) +
                    expense.parts.mapNotNull { it.note } + partCategoryNames
            }.joinToString("\n") { normalize(it) }
            val amounts = (listOf(candidate.expense.amountMinor) + candidate.expense.parts.map { it.amountMinor })
                .map { BigDecimal.valueOf(it, candidate.decimals).abs() }
            return words.all { word -> word.text in text || amounts.any { word.matchesAmount(it) } }
        }
    }

    internal class Word(val text: String, private val amount: BigDecimal?, private val whole: Boolean) {
        fun matchesAmount(value: BigDecimal): Boolean = when {
            amount == null -> false
            whole -> value >= amount && value < amount + BigDecimal.ONE
            else -> value.compareTo(amount) == 0
        }
    }

    /** Null when there's nothing to search for. */
    fun parse(query: String): Query? {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return Query(
            words.map { word ->
                val amount = amountOf(word)
                Word(normalize(word), amount?.first, amount?.second ?: false)
            },
        )
    }

    /** Which category Search shows: one category's transactions (or parts of splits), or the uncategorized ones. */
    sealed interface CategoryFilter {
        data class Is(val categoryId: Long) : CategoryFilter
        data object None : CategoryFilter
    }

    /** A found transaction and the amount it counts with (signed like the expense): a split's matching parts only. */
    data class Hit(val candidate: Candidate, val countedMinor: Long) {
        val expense: Expense get() = candidate.expense
    }

    /**
     * Transactions matching [query] (if any), in [category] and [account] (if set), in the
     * given order; null when there's nothing to search for at all.
     */
    fun find(candidates: List<Candidate>, query: Query?, category: CategoryFilter?, account: Long?): List<Hit>? {
        if (query == null && category == null && account == null) return null
        return candidates.mapNotNull { candidate ->
            val expense = candidate.expense
            if (account != null && expense.accountId != account) return@mapNotNull null
            if (query != null && !query.matches(candidate)) return@mapNotNull null
            val counted = when (category) {
                null -> expense.amountMinor
                // As Spending counts a split: the rest in the expense's category, each part in its own.
                else -> expense.countedParts()
                    .filter { if (category is CategoryFilter.Is) it.categoryId == category.categoryId else it.categoryId == null }
                    .ifEmpty { return@mapNotNull null }
                    .sumOf { it.amountMinor }
            }
            Hit(candidate, counted)
        }
    }

    /**
     * The shop a statement line is from, to find its other transactions: the pattern
     * that categorized it ("COOP" in "TWINT *COOP-4521 ZÜRICH"), else its first word
     * that isn't a number or a payment method; null if there's none.
     */
    fun shopOf(description: String, categorizer: Categorizer, moneyOut: Boolean): String? =
        categorizer.match(description, moneyOut)?.keyword ?: Categorizer.suggestKeyword(description).ifEmpty { null }

    /** The [count] months up to and including [last], oldest first. */
    fun lastMonths(last: YearMonth, count: Int): List<YearMonth> = (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }

    /**
     * Per month of [months]: money out ([moneyIn] false) or in, converted by [toBase]
     * (amount, currency, day; null = no rate, left out).
     */
    fun monthly(
        hits: List<Hit>,
        months: List<YearMonth>,
        moneyIn: Boolean,
        decimalsOf: (String) -> Int,
        toBase: (BigDecimal, String, LocalDate) -> BigDecimal?,
    ): List<BigDecimal> {
        val sums = months.associateWith { BigDecimal.ZERO }.toMutableMap()
        hits.forEach { hit ->
            val month = YearMonth.from(hit.expense.date)
            val sum = sums[month] ?: return@forEach
            val minor = if (moneyIn) -hit.countedMinor else hit.countedMinor
            if (minor <= 0) return@forEach
            val amount = BigDecimal.valueOf(minor, decimalsOf(hit.expense.currencyCode))
            toBase(amount, hit.expense.currencyCode, hit.expense.date)?.let { sums[month] = sum + it }
        }
        return months.map { sums.getValue(it) }
    }

    /** Sums per currency (in the order first seen), money out and in apart. */
    fun totals(hits: List<Hit>): List<Total> =
        hits.groupBy { it.expense.currencyCode }.map { (code, list) ->
            Total(code, outMinor = list.filter { it.countedMinor > 0 }.sumOf { it.countedMinor }, inMinor = -list.filter { it.countedMinor < 0 }.sumOf { it.countedMinor })
        }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    /**
     * The amount a word stands for, and whether it was a whole number: "1'234.50",
     * "1,234.50", "45,30". The last separator is the decimal one if 1–2 digits follow,
     * otherwise all are thousands separators.
     */
    private fun amountOf(word: String): Pair<BigDecimal, Boolean>? {
        if (!word.matches(Regex("\\d[\\d'’.,]*"))) return null
        val plain = word.replace("'", "").replace("’", "")
        val last = plain.lastIndexOfAny(charArrayOf('.', ','))
        val decimalPart = if (last >= 0) plain.substring(last + 1) else ""
        val hasDecimals = last >= 0 && decimalPart.length in 1..2 && decimalPart.all { it.isDigit() }
        val digits = if (hasDecimals) {
            plain.substring(0, last).replace(".", "").replace(",", "") + "." + decimalPart
        } else {
            plain.replace(".", "").replace(",", "")
        }
        return digits.toBigDecimalOrNull()?.let { it to !hasDecimals }
    }
}
