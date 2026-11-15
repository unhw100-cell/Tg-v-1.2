package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLiquidGlassPalette

/**
 * Android visual treatment inspired by the iOS Liquid Glass aesthetic:
 * translucent fill, highlight gradients, rim border, and depth shadow.
 * This is a Compose approximation, not Apple's native rendering framework; performance
 * and transparency may vary by device and system composition settings.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(22.dp),
    strong: Boolean = false,
    accentTint: Color? = null,
    elevation: Dp = 8.dp
): Modifier {
    val glass = LocalLiquidGlassPalette.current
    val baseFill = if (strong) glass.glassSurfaceStrong else glass.glassSurface
    val tintOverlay = accentTint?.copy(alpha = if (glass.isDark) 0.16f else 0.10f) ?: Color.Transparent

    val rimBrush = Brush.linearGradient(
        colors = listOf(
            glass.glassSpecularTop,
            glass.glassSpecularBottom,
            glass.glassSpecularTop.copy(alpha = if (glass.isDark) 0.22f else 0.48f)
        )
    )

    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            glass.glassInnerGlow,
            Color.Transparent,
            Color.Transparent
        )
    )

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (glass.isDark) 0.45f else 0.14f),
            spotColor = Color.Black.copy(alpha = if (glass.isDark) 0.55f else 0.18f)
        )
        .clip(shape)
        .background(baseFill)
        .background(tintOverlay)
        .background(sheenBrush)
        .border(width = 1.dp, brush = rimBrush, shape = shape)
}

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlassPalette.current
    val orbPrimary = glass.accent.copy(alpha = if (glass.isDark) 0.18f else 0.14f)
    val orbSecondary = Color(0xFF34AADC).copy(alpha = if (glass.isDark) 0.14f else 0.12f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(glass.backdropGradient))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orbPrimary, Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.12f),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                    radius = size.width * 0.75f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orbSecondary, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.78f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.78f),
                    radius = size.width * 0.7f
                )
            },
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    strong: Boolean = false,
    accentTint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.liquidGlass(shape = shape, strong = strong, accentTint = accentTint),
        content = content
    )
}

@Composable
fun LiquidGlassCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val glass = LocalLiquidGlassPalette.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .liquidGlass(
                shape = shape,
                strong = selected,
                accentTint = if (selected) glass.accent else null,
                elevation = if (selected) 6.dp else 2.dp
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
fun LiquidGlassSectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp)
            .height(0.6.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    )
}

@Composable
fun TelegramChatWallpaper(
    isDarkMode: Boolean,
    wallpaperStyle: com.example.tdlib.WallpaperStyle,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val bgColors = when (wallpaperStyle) {
        com.example.tdlib.WallpaperStyle.TELEGRAM_PATTERN -> if (isDarkMode) {
            listOf(Color(0xFF070B12), Color(0xFF0F1926), Color(0xFF0A121D))
        } else {
            listOf(Color(0xFFCDE6D8), Color(0xFFC6DFEE), Color(0xFFD9ECCB))
        }
        com.example.tdlib.WallpaperStyle.ARCTIC_ICE -> if (isDarkMode) {
            listOf(Color(0xFF09131F), Color(0xFF102437), Color(0xFF0B1929))
        } else {
            listOf(Color(0xFFD4EBFF), Color(0xFFEAF5FF), Color(0xFFD2E7F9))
        }
        com.example.tdlib.WallpaperStyle.MIDNIGHT_AURORA -> if (isDarkMode) {
            listOf(Color(0xFF071119), Color(0xFF102A2F), Color(0xFF1B1536))
        } else {
            listOf(Color(0xFFC4EAE2), Color(0xFFCFE2FA), Color(0xFFE2D7FA))
        }
        com.example.tdlib.WallpaperStyle.LAVENDER_MIST -> if (isDarkMode) {
            listOf(Color(0xFF130E21), Color(0xFF1E1736), Color(0xFF141126))
        } else {
            listOf(Color(0xFFEBDDFB), Color(0xFFF6E7FA), Color(0xFFDFEAFF))
        }
        com.example.tdlib.WallpaperStyle.SOLID_MINIMAL -> if (isDarkMode) {
            listOf(Color(0xFF050608), Color(0xFF090B10), Color(0xFF050608))
        } else {
            listOf(Color(0xFFF2F5FA), Color(0xFFEEF3F9), Color(0xFFF5F7FB))
        }
    }

    val motifColor = if (isDarkMode) {
        Color.White.copy(alpha = 0.045f)
    } else {
        accentColor.copy(alpha = 0.11f)
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier.background(Brush.verticalGradient(bgColors))
    ) {
        if (wallpaperStyle == com.example.tdlib.WallpaperStyle.SOLID_MINIMAL) return@Canvas

        // Ambient liquid glass aura blobs
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = if (isDarkMode) 0.12f else 0.10f), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.25f),
                radius = size.width * 0.65f
            ),
            center = Offset(size.width * 0.2f, size.height * 0.25f),
            radius = size.width * 0.65f
        )

        if (wallpaperStyle == com.example.tdlib.WallpaperStyle.TELEGRAM_PATTERN) {
            val stepX = 104.dp.toPx()
            val stepY = 104.dp.toPx()
            val cols = (size.width / stepX).toInt() + 2
            val rows = (size.height / stepY).toInt() + 2

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cx = c * stepX + (if (r % 2 == 0) 20f else 62f)
                    val cy = r * stepY + 28f
                    when ((r + c) % 3) {
                        0 -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(cx, cy)
                                lineTo(cx + 24f, cy - 10f)
                                lineTo(cx + 13f, cy + 16f)
                                close()
                            }
                            drawPath(
                                path,
                                motifColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f)
                            )
                        }
                        1 -> {
                            drawCircle(
                                motifColor,
                                radius = 12f,
                                center = Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f)
                            )
                        }
                        else -> {
                            drawLine(
                                motifColor,
                                start = Offset(cx - 10f, cy),
                                end = Offset(cx + 10f, cy),
                                strokeWidth = 2.4f
                            )
                            drawLine(
                                motifColor,
                                start = Offset(cx, cy - 10f),
                                end = Offset(cx, cy + 10f),
                                strokeWidth = 2.4f
                            )
                        }
                    }
                }
            }
        }
    }
}
