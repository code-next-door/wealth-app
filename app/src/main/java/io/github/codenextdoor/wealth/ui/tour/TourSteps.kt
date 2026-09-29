package io.github.codenextdoor.wealth.ui.tour

import androidx.annotation.StringRes
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.HomeTab

/** What a tour stop points at; the home screen marks each with `Modifier.tourTarget`. */
enum class TourTarget { OVERVIEW_TAB, ADD_ACCOUNT, HOUSE_TAB, IMPORT, EYE, SETTINGS }

/** One stop: the tab to show (null: stay), what to point at, and what to say. */
data class TourStep(val target: TourTarget, val tab: HomeTab?, @StringRes val title: Int, @StringRes val text: Int)

object TourSteps {
    val all = listOf(
        TourStep(TourTarget.OVERVIEW_TAB, HomeTab.OVERVIEW, R.string.tour_overview_title, R.string.tour_overview_text),
        TourStep(TourTarget.ADD_ACCOUNT, HomeTab.ACCOUNTS, R.string.tour_accounts_title, R.string.tour_accounts_text),
        TourStep(TourTarget.HOUSE_TAB, HomeTab.HOUSE, R.string.tour_house_title, R.string.tour_house_text),
        TourStep(TourTarget.IMPORT, HomeTab.SPENDING, R.string.tour_spending_title, R.string.tour_spending_text),
        TourStep(TourTarget.EYE, HomeTab.OVERVIEW, R.string.tour_eye_title, R.string.tour_eye_text),
        TourStep(TourTarget.SETTINGS, HomeTab.OVERVIEW, R.string.tour_settings_title, R.string.tour_settings_text),
    )
}
