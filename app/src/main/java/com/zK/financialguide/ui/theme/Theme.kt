package com.zK.financialguide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = FinWiseBlueDark,
    onPrimary = Color(0xFF233156),
    primaryContainer = Color(0xFF354269),
    onPrimaryContainer = Color(0xFFDEE4FF),
    secondary = Color(0xFF9DD3C6),
    onSecondary = Color(0xFF08392F),
    secondaryContainer = Color(0xFF254C43),
    onSecondaryContainer = Color(0xFFC2F0E3),
    tertiary = Color(0xFFF3C66A),
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF5F4619),
    onTertiaryContainer = Color(0xFFFFE1A8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE5E6ED),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE5E6ED),
    surfaceVariant = Color(0xFF343842),
    onSurfaceVariant = Color(0xFFBAC0CE),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C23),
    surfaceContainer = Color(0xFF20232B),
    surfaceContainerHigh = Color(0xFF292D36),
    surfaceContainerHighest = Color(0xFF343842),
    outline = Color(0xFF858C9D),
    outlineVariant = Color(0xFF414651),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF632A2D),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = FinWiseBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE4F9),
    onPrimaryContainer = Color(0xFF283457),
    secondary = Color(0xFF356B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F0EA),
    onSecondaryContainer = Color(0xFF254C43),
    tertiary = Color(0xFF8A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDCB),
    onTertiaryContainer = Color(0xFF5F4619),
    background = Color(0xFFFAFAFD),
    onBackground = Color(0xFF252A34),
    surface = Color(0xFFFAFAFD),
    onSurface = Color(0xFF252A34),
    surfaceVariant = Color(0xFFE7E9F1),
    onSurfaceVariant = Color(0xFF5C6372),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F9),
    surfaceContainer = Color(0xFFEFF0F6),
    surfaceContainerHigh = Color(0xFFE7E9F1),
    surfaceContainerHighest = Color(0xFFE1E4ED),
    outline = Color(0xFF777F91),
    outlineVariant = Color(0xFFD6DAE6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE7E5),
    onErrorContainer = Color(0xFF7D241E)
)

private val FinWiseShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/** Fixed brand colours on every device; dark mode changes contrast, not the visual language. */
@Composable
fun FinWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = FinWiseShapes,
        content = content
    )
}
