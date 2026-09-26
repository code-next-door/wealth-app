package io.github.codenextdoor.wealth.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Chart colors. The categorical order is a validated colorblind-safe palette
 * (adjacent slots stay distinguishable for common color-vision deficiencies);
 * dark mode uses the same hues stepped for dark backgrounds. Keep the order.
 */
object ChartColors {
    private val light = listOf(
        0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100,
        0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948,
    ).map(::Color)

    private val dark = listOf(
        0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500,
        0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767,
    ).map(::Color)

    /** Neutral for the "Other" bucket, so it never looks like a real category. */
    val other = Color(0xFF898781)

    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    /** Categorical color for [slot] (0-based); slots past the palette fall back to [other]. */
    @Composable
    fun series(slot: Int): Color = (if (isDark()) dark else light).getOrElse(slot) { other }

    /** Text color for an increase in net worth. Always paired with an arrow and sign. */
    @Composable
    fun increase(): Color = if (isDark()) Color(0xFF0CA30C) else Color(0xFF006300)

    /** Text color for a decrease in net worth. Always paired with an arrow and sign. */
    @Composable
    fun decrease(): Color = Color(0xFFD03B3B)
}
