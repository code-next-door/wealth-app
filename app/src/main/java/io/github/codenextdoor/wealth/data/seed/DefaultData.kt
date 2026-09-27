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

    /**
     * Increase when new defaults are added, and seed only the new ones for
     * existing users (see DatabaseSeeder). 1: initial data. 2: category rules.
     * 3: "don't import" rules. 4: the "Shares (stock plan)" account type.
     */
    const val SEED_VERSION = 4

    /** Account type for accounts holding shares (e.g. an employee stock plan); added in seed version 4. */
    val stockPlanType = SeedAccountType("stock_plan", R.string.seed_type_stock_plan, ASSET, null)

    /**
     * Statement text that isn't spending: paying a credit card bill from the
     * bank account (the card's own statement has the actual purchases), so
     * importing both would count them twice.
     */
    val skipImportKeywords = listOf("CREDIT CARD STATEMENT", "UBS CARD CENTER", "KREDITKARTENABRECHNUNG", "SWISSCARD AECS")

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

    /**
     * Keyword -> category (by seed key) for common merchants in Switzerland and
     * India. Keywords match at the start of a word, and the longest match
     * wins, so "UBER EATS" beats "UBER". Users can edit or delete all of these.
     */
    val categoryRules: List<Pair<String, String>> = buildList {
        fun add(category: String, vararg keywords: String) = keywords.forEach { add(it to category) }
        add(
            "groceries",
            "MIGROS", "COOP", "DENNER", "ALDI", "LIDL", "VOLG", "MANOR FOOD", "FARMY",
            "BIGBASKET", "BLINKIT", "ZEPTO", "DMART", "RELIANCE FRESH", "NATURE S BASKET",
        )
        add(
            "eating_out",
            "MCDONALD", "STARBUCKS", "BURGER KING", "SUBWAY", "KFC", "DOMINO", "PIZZA", "RESTAURANT",
            "RISTORANTE", "CAFE", "UBER EATS", "JUST EAT", "SMOOD", "SWIGGY", "ZOMATO",
        )
        add(
            "transport",
            "SBB", "CFF", "FFS", "ZVV", "VBZ", "BLS", "TPG", "UBER", "BOLT", "TAXI", "MOBILITY",
            "SHELL", "AVIA", "TAMOIL", "SOCAR", "PARKING", "PARKHAUS", "OLA", "RAPIDO", "IRCTC", "FASTAG",
        )
        add("utilities", "SWISSCOM", "SUNRISE", "SALT MOBILE", "EWZ", "IWB", "AIRTEL", "JIO", "VODAFONE", "BESCOM")
        add(
            "health_insurance",
            "HELSANA", "SWICA", "SANITAS", "VISANA", "CONCORDIA", "ASSURA", "KPT", "GROUPE MUTUEL", "CSS VERSICHERUNG",
        )
        add("healthcare", "APOTHEKE", "PHARMACIE", "PHARMACY", "AMAVITA", "SUN STORE", "APOLLO PHARMACY", "PRACTO")
        add("insurance", "AXA", "ZURICH VERSICHERUNG", "MOBILIAR", "ALLIANZ", "GENERALI", "BALOISE", "LIFE INSURANCE CORP")
        add(
            "shopping",
            "AMAZON", "GALAXUS", "DIGITEC", "ZALANDO", "IKEA", "DECATHLON", "INTERDISCOUNT", "MEDIA MARKT",
            "JELMOLI", "GLOBUS", "FLIPKART", "MYNTRA", "AJIO", "NYKAA",
        )
        add(
            "travel",
            "EASYJET", "RYANAIR", "LUFTHANSA", "EDELWEISS", "AIR INDIA", "INDIGO", "EMIRATES", "BOOKING COM",
            "AIRBNB", "HOTEL", "EXPEDIA", "MAKEMYTRIP", "GOIBIBO",
        )
        add("entertainment", "PATHE", "KINO", "CINEMA", "TICKETCORNER", "BOOKMYSHOW", "STEAM", "PLAYSTATION")
        add(
            "subscriptions",
            "NETFLIX", "SPOTIFY", "DISNEY", "APPLE COM", "GOOGLE", "YOUTUBE", "AMAZON PRIME", "AUDIBLE", "OPENAI",
            "HOTSTAR",
        )
        add("education", "UDEMY", "COURSERA")
        add("taxes", "STEUERVERWALTUNG", "STEUERAMT", "SERAFE", "INCOME TAX")
        add("gifts", "SPENDE", "DONATION")
    }
}
