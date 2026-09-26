package io.github.codenextdoor.wealth.domain

import java.util.Locale

/** Looks up ISO 4217 currencies known to the Java runtime. */
object IsoCurrencies {

    private val CODE = Regex("[A-Z]{3}")

    /**
     * Real currency codes. Needed because on Android Currency.getInstance()
     * accepts any three letters (e.g. "XYZ"), unlike the desktop JVM.
     */
    private val KNOWN: Set<String> by lazy { java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() }

    /** Returns the currency for [code] (case-insensitive), or null if unknown. */
    fun lookup(code: String, locale: Locale = Locale.getDefault()): Currency? {
        val normalized = code.trim().uppercase(Locale.ROOT)
        if (!CODE.matches(normalized) || normalized !in KNOWN) return null
        val javaCurrency = runCatching { java.util.Currency.getInstance(normalized) }.getOrNull()
            ?: return null
        return Currency(
            code = javaCurrency.currencyCode,
            name = javaCurrency.getDisplayName(locale),
            // -1 means "not applicable" (e.g. gold "XAU"); treat as 2 decimals.
            decimals = javaCurrency.defaultFractionDigits.takeIf { it >= 0 } ?: 2,
        )
    }
}
