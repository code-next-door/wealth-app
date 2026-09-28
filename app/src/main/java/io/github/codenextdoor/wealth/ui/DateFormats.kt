package io.github.codenextdoor.wealth.ui

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A date format with the fields in [skeleton] (e.g. "MMMMyyyy") in the phone's
 * regional order and wording: "March 2026" in English, "2026年3月" in Japanese.
 */
fun localDateFormat(skeleton: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
