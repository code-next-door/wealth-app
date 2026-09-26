package io.github.codenextdoor.wealth.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val WealthShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You colors from the wallpaper (Android 12+), instead of the brand palette. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WealthTypography,
        shapes = WealthShapes,
        content = content,
    )
}

/** Background of the headline card: a rich gradient in the theme's primary color. */
@Composable
fun heroBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val start = if (dark) scheme.primaryContainer else scheme.primary
    return Brush.linearGradient(listOf(start, lerp(start, Color.Black, if (dark) 0.35f else 0.3f)))
}

/** Text color on [heroBrush]. */
@Composable
fun onHeroColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) scheme.onPrimaryContainer else scheme.onPrimary
}
