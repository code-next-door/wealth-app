package io.github.codenextdoor.wealth.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Picks a category for statement text using the user's keyword rules.
 *
 * Text and keywords are compared in a normalized form: upper case, accents
 * removed, and anything that isn't a letter or digit turned into a space
 * ("Café-Bar*ZÜRICH" -> "CAFE BAR ZURICH"). A keyword matches when it starts
 * at the beginning of a word, so "MCDONALD" matches "MCDONALDS" but "SALT"
 * doesn't match "BASALT". When several keywords match, the longest wins, so a
 * specific rule ("UBER EATS") beats a general one ("UBER").
 */
class Categorizer(
    rules: List<CategoryRule>,
    /** Income categories: their rules only match money in (a card's "INTEREST" charge isn't income). */
    private val incomeCategories: Set<Long> = emptySet(),
) {

    private val rules = rules
        .filter { it.keyword.isNotBlank() }
        .sortedWith(compareByDescending<CategoryRule> { it.keyword.length }.thenBy { it.keyword })

    /** The rule deciding [description]'s category, or null if none matches. [moneyOut] skips income rules. */
    fun match(description: String, moneyOut: Boolean = false): CategoryRule? {
        val text = normalize(description)
        return rules.firstOrNull { rule ->
            (text.startsWith(rule.keyword) || text.contains(" " + rule.keyword)) &&
                !(moneyOut && rule.categoryId in incomeCategories)
        }
    }

    /** The category for [description]; null if no rule matches or the rule says "don't import". */
    fun categoryFor(description: String, moneyOut: Boolean = false): Long? = match(description, moneyOut)?.categoryId

    companion object {
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val MARKS = Regex("\\p{M}+")
        private val DIGITS = Regex("\\d+")

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(MARKS, "")
                .uppercase(Locale.ROOT)
                .replace(NON_WORD, " ")
                .trim()

        /**
         * A keyword to suggest when the user recategorizes [description]:
         * its first word of 3+ characters that isn't just a number, e.g.
         * "TWINT *COOP-4521 ZURICH" -> "TWINT"... unless a payment-method
         * prefix is recognized, in which case the next word is used ("COOP").
         */
        fun suggestKeyword(description: String): String {
            val words = normalize(description).split(" ").filter { it.length >= 3 && !DIGITS.matches(it) }
            return words.firstOrNull { it !in PAYMENT_PREFIXES } ?: words.firstOrNull().orEmpty()
        }

        /** Words that describe how something was paid rather than who was paid. */
        private val PAYMENT_PREFIXES = setOf(
            "TWINT", "PAYPAL", "SUMUP", "ZETTLE", "SQUARE", "POS", "EFT", "CARD", "KARTE", "DEBIT",
            "PURCHASE", "EINKAUF", "ACHAT", "UPI", "NEFT", "IMPS", "ONLINE", "PAYMENT", "ZAHLUNG",
            "CRV", "SQ", "IZETTLE",
        )
    }
}
