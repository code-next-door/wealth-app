package io.github.codenextdoor.wealth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Heavier headings and tighter large text give numbers more presence. */
val WealthTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall,
)
