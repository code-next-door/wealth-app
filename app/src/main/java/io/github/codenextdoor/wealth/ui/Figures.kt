package io.github.codenextdoor.wealth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import io.github.codenextdoor.wealth.domain.maskFigures

/** Whether the eye on the home screen hides figures, and how to switch it. */
data class Figures(val hidden: Boolean = false, val toggle: () -> Unit = {})

val LocalFigures = compositionLocalOf { Figures() }

/**
 * This text as shown on a tab: as it is, or with its numbers as "••••" while the
 * eye hides figures. Use it for every amount, percentage or share count a tab
 * shows (and for the arguments of sentences, not the sentence, so dates stay).
 */
@Composable
@ReadOnlyComposable
fun String.figure(): String = if (LocalFigures.current.hidden) maskFigures(this) else this
