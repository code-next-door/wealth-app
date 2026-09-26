package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.time.Instant

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
