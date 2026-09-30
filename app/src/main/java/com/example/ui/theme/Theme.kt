package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CmfOrange,
    onPrimary = Color.White,
    primaryContainer = NothingDarkElevated,
    onPrimaryContainer = Color.White,
    secondary = NothingRed,
    onSecondary = Color.White,
    tertiary = MatrixGreen,
    onTertiary = NothingBlack,
    background = NothingBlack,
    onBackground = TextPrimaryDark,
    surface = NothingDarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NothingDarkCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = NothingBorderDark,
    outlineVariant = NothingDarkElevated
)

private val LightColorScheme = lightColorScheme(
    primary = CmfOrange,
    onPrimary = Color.White,
    primaryContainer = NothingLightElevated,
    onPrimaryContainer = TextPrimaryLight,
    secondary = NothingRed,
    onSecondary = Color.White,
    tertiary = MatrixGreen,
    onTertiary = Color.White,
    background = NothingWhite,
    onBackground = TextPrimaryLight,
    surface = NothingLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = NothingLightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = NothingBorderLight,
    outlineVariant = NothingLightElevated
)

@Composable
fun GlyphBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
