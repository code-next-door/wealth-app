package io.github.codenextdoor.wealth.data.seed

import androidx.annotation.StringRes
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.AssetKind.ASSET
import io.github.codenextdoor.wealth.domain.AssetKind.LIABILITY

/**
 * Everything the app sets up on first launch. After seeding these are
 * ordinary user data: all of it can be renamed, deleted or extended.
 * Names come from strings.xml so they can be translated.
 */
object DefaultData {

    /** Increase when new defaults are added, and seed only the new ones for existing users. */
    const val SEED_VERSION = 1

    const val BASE_CURRENCY = "CHF"

    val currencyCodes = listOf("CHF", "INR", "USD")

    data class SeedCountry(val key: String, @StringRes val name: Int)

    val countries = listOf(
        SeedCountry("ch", R.string.seed_country_ch),
        SeedCountry("in", R.string.seed_country_in),
    )

    data class SeedAccountType(
        val key: String,
        @StringRes val name: Int,
        val kind: AssetKind,
        /** Key of a [SeedCountry], or null for "General". */
        val countryKey: String?,
    )

    val accountTypes = listOf(
        SeedAccountType("ch_bank", R.string.seed_type_bank_account, ASSET, "ch"),
        SeedAccountType("ch_pillar2", R.string.seed_type_pillar2, ASSET, "ch"),
        SeedAccountType("ch_pillar3a", R.string.seed_type_pillar3a, ASSET, "ch"),
        SeedAccountType("ch_brokerage", R.string.seed_type_brokerage, ASSET, "ch"),
        SeedAccountType("in_nre", R.string.seed_type_nre, ASSET, "in"),
        SeedAccountType("in_nro", R.string.seed_type_nro, ASSET, "in"),
        SeedAccountType("in_fd", R.string.seed_type_fixed_deposit, ASSET, "in"),
        SeedAccountType("in_mutual_funds", R.string.seed_type_mutual_funds, ASSET, "in"),
        SeedAccountType("in_stocks", R.string.seed_type_stocks, ASSET, "in"),
        SeedAccountType("in_ppf", R.string.seed_type_ppf, ASSET, "in"),
        SeedAccountType("in_epf", R.string.seed_type_epf, ASSET, "in"),
        SeedAccountType("real_estate", R.string.seed_type_real_estate, ASSET, null),
        SeedAccountType("gold", R.string.seed_type_gold, ASSET, null),
        SeedAccountType("cash", R.string.seed_type_cash, ASSET, null),
        SeedAccountType("other_asset", R.string.seed_type_other_asset, ASSET, null),
        SeedAccountType("loan", R.string.seed_type_loan, LIABILITY, null),
        SeedAccountType("mortgage", R.string.seed_type_mortgage, LIABILITY, null),
        SeedAccountType("credit_card", R.string.seed_type_credit_card, LIABILITY, null),
    )

    data class SeedCategory(val key: String, @StringRes val name: Int)

    val expenseCategories = listOf(
        SeedCategory("housing", R.string.seed_category_housing),
        SeedCategory("groceries", R.string.seed_category_groceries),
        SeedCategory("eating_out", R.string.seed_category_eating_out),
        SeedCategory("transport", R.string.seed_category_transport),
        SeedCategory("utilities", R.string.seed_category_utilities),
        SeedCategory("health_insurance", R.string.seed_category_health_insurance),
        SeedCategory("healthcare", R.string.seed_category_healthcare),
        SeedCategory("insurance", R.string.seed_category_insurance),
        SeedCategory("shopping", R.string.seed_category_shopping),
        SeedCategory("travel", R.string.seed_category_travel),
        SeedCategory("entertainment", R.string.seed_category_entertainment),
        SeedCategory("subscriptions", R.string.seed_category_subscriptions),
        SeedCategory("education", R.string.seed_category_education),
        SeedCategory("gifts", R.string.seed_category_gifts),
        SeedCategory("family_support", R.string.seed_category_family_support),
        SeedCategory("taxes", R.string.seed_category_taxes),
        SeedCategory("other", R.string.seed_category_other),
    )
}
