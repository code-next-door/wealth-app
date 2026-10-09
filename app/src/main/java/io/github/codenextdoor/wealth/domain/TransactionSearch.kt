package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

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

    /** Sums per currency (in the order first seen), money out and in apart. */
    fun totals(expenses: List<Expense>): List<Total> =
        expenses.groupBy { it.currencyCode }.map { (code, list) ->
            Total(code, outMinor = list.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }, inMinor = -list.filter { it.amountMinor < 0 }.sumOf { it.amountMinor })
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
