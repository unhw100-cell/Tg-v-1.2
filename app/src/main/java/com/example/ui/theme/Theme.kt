package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.tdlib.TelegramThemePreset

@Immutable
data class LiquidGlassPalette(
    val isDark: Boolean,
    val preset: TelegramThemePreset,
    val accent: Color,
    val backdropGradient: List<Color>,
    val glassSurface: Color,
    val glassSurfaceStrong: Color,
    val glassSpecularTop: Color,
    val glassSpecularBottom: Color,
    val glassInnerGlow: Color,
    val bubbleOutStart: Color,
    val bubbleOutEnd: Color,
    val bubbleIn: Color
)

val LocalLiquidGlassPalette = staticCompositionLocalOf {
    LiquidGlassPalette(
        isDark = false,
        preset = TelegramThemePreset.DAY,
        accent = IosBlueLight,
        backdropGradient = listOf(Color(0xFFEAF2FF), Color(0xFFF6F9FF), Color(0xFFE6F0FA)),
        glassSurface = Color(0xB8FFFFFF),
        glassSurfaceStrong = Color(0xDCFFFFFF),
        glassSpecularTop = Color(0xE6FFFFFF),
        glassSpecularBottom = Color(0x3DFFFFFF),
        glassInnerGlow = Color(0x52FFFFFF),
        bubbleOutStart = IosLightBubbleOutStart,
        bubbleOutEnd = IosLightBubbleOutEnd,
        bubbleIn = IosLightBubbleIn
    )
}

@Composable
fun RustGramTheme(
    themePreset: TelegramThemePreset = TelegramThemePreset.DAY,
    accentIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val accent = AccentSwatches[accentIndex.coerceIn(0, AccentSwatches.lastIndex)]
    val accentDarkEnd = accent.copy(alpha = 0.92f)
    val accentLightStart = Color(
        red = (accent.red + 0.22f).coerceAtMost(1f),
        green = (accent.green + 0.22f).coerceAtMost(1f),
        blue = (accent.blue + 0.22f).coerceAtMost(1f),
        alpha = 0.96f
    )

    val (colorScheme, glassPalette) = when (themePreset) {
        TelegramThemePreset.DAY -> {
            lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.14f),
                onPrimaryContainer = accent,
                secondary = IosTealAccent,
                onSecondary = Color.White,
                background = IosLightBackground,
                onBackground = IosLightTextPrimary,
                surface = Color.White,
                onSurface = IosLightTextPrimary,
                surfaceVariant = IosLightGroupedBackground,
                onSurfaceVariant = IosLightTextSecondary,
                outline = IosLightSeparator,
                error = TelegramIosRed
            ) to LiquidGlassPalette(
                isDark = false,
                preset = themePreset,
                accent = accent,
                backdropGradient = listOf(Color(0xFFDCEBFF), Color(0xFFF4F8FF), Color(0xFFE0F1FA)),
                glassSurface = Color(0xBFFFFFFF),
                glassSurfaceStrong = Color(0xE0FFFFFF),
                glassSpecularTop = Color(0xF5FFFFFF),
                glassSpecularBottom = Color(0x45FFFFFF),
                glassInnerGlow = Color(0x66FFFFFF),
                bubbleOutStart = accentLightStart,
                bubbleOutEnd = accentDarkEnd,
                bubbleIn = IosLightBubbleIn
            )
        }

        TelegramThemePreset.CLASSIC -> {
            lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.16f),
                onPrimaryContainer = accent,
                secondary = ClassicHeaderBlue,
                onSecondary = Color.White,
                background = ClassicBackground,
                onBackground = IosLightTextPrimary,
                surface = Color(0xFFF6FAFF),
                onSurface = IosLightTextPrimary,
                surfaceVariant = ClassicGroupedBackground,
                onSurfaceVariant = Color(0xFF53697E),
                outline = Color(0x40517DA2),
                error = TelegramIosRed
            ) to LiquidGlassPalette(
                isDark = false,
                preset = themePreset,
                accent = accent,
                backdropGradient = listOf(Color(0xFFCDE1F5), Color(0xFFEAF2FA), Color(0xFFD5E9E4)),
                glassSurface = Color(0xBDFFFFFF),
                glassSurfaceStrong = Color(0xE2F7FBFF),
                glassSpecularTop = Color(0xF5FFFFFF),
                glassSpecularBottom = Color(0x4D94BCE3),
                glassInnerGlow = Color(0x60FFFFFF),
                bubbleOutStart = accentLightStart,
                bubbleOutEnd = accentDarkEnd,
                bubbleIn = Color(0xECFFFFFF)
            )
        }

        TelegramThemePreset.TINTED_DARK -> {
            val darkAccent = if (accent == IosBlueLight) IosBlueDark else accent
            darkColorScheme(
                primary = darkAccent,
                onPrimary = Color.White,
                primaryContainer = TintedDarkElevated,
                onPrimaryContainer = Color.White,
                secondary = IosTealAccent,
                onSecondary = Color.White,
                background = TintedDarkBackground,
                onBackground = IosDarkTextPrimary,
                surface = TintedDarkGrouped,
                onSurface = IosDarkTextPrimary,
                surfaceVariant = TintedDarkElevated,
                onSurfaceVariant = Color(0xFF8CA0B5),
                outline = TintedDarkSeparator,
                error = TelegramIosRed
            ) to LiquidGlassPalette(
                isDark = true,
                preset = themePreset,
                accent = darkAccent,
                backdropGradient = listOf(Color(0xFF0A1420), Color(0xFF132234), Color(0xFF0E1826)),
                glassSurface = Color(0xBD182534),
                glassSurfaceStrong = Color(0xDF1D2C3D),
                glassSpecularTop = Color(0x5CE0F0FF),
                glassSpecularBottom = Color(0x18FFFFFF),
                glassInnerGlow = Color(0x1EE0F0FF),
                bubbleOutStart = darkAccent.copy(alpha = 0.72f),
                bubbleOutEnd = darkAccent.copy(alpha = 0.92f),
                bubbleIn = TintedDarkBubbleIn
            )
        }

        TelegramThemePreset.OLED_NIGHT -> {
            val darkAccent = if (accent == IosBlueLight) IosBlueDark else accent
            darkColorScheme(
                primary = darkAccent,
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
            ) to LiquidGlassPalette(
                isDark = true,
                preset = themePreset,
                accent = darkAccent,
                backdropGradient = listOf(Color(0xFF000000), Color(0xFF0B0E17), Color(0xFF050508)),
                glassSurface = Color(0xBF18181E),
                glassSurfaceStrong = Color(0xE022222A),
                glassSpecularTop = Color(0x52FFFFFF),
                glassSpecularBottom = Color(0x14FFFFFF),
                glassInnerGlow = Color(0x19FFFFFF),
                bubbleOutStart = darkAccent.copy(alpha = 0.75f),
                bubbleOutEnd = darkAccent.copy(alpha = 0.94f),
                bubbleIn = IosDarkBubbleIn
            )
        }
    }

    CompositionLocalProvider(LocalLiquidGlassPalette provides glassPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
