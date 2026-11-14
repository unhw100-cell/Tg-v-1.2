package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TelegramIosDarkColorScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = IosDarkElevated,
    onPrimaryContainer = Color.White,
    secondary = IosTealAccent,
    onSecondary = Color.White,
    background = IosDarkBackground,
    onBackground = IosDarkTextPrimary,
    surface = IosDarkGroupedBackground,
    onSurface = IosDarkTextPrimary,
    surfaceVariant = IosDarkElevated,
    onSurfaceVariant = IosDarkTextSecondary,
    outline = IosDarkSeparator,
    error = TelegramIosRed
)

private val TelegramIosLightColorScheme = lightColorScheme(
    primary = IosBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = IosBlueLight,
    secondary = IosTealAccent,
    onSecondary = Color.White,
    background = IosLightBackground,
    onBackground = IosLightTextPrimary,
    surface = IosLightBackground,
    onSurface = IosLightTextPrimary,
    surfaceVariant = IosLightGroupedBackground,
    onSurfaceVariant = IosLightTextSecondary,
    outline = IosLightSeparator,
    error = TelegramIosRed
)

@Composable
fun RustGramTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) TelegramIosDarkColorScheme else TelegramIosLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
