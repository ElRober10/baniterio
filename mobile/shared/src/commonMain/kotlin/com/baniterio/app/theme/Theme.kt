package com.baniterio.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BaniterioColorScheme = darkColorScheme(
    primary = BaniterioColors.brand,
    onPrimary = BaniterioColors.gold,
    secondary = BaniterioColors.brandBright,
    onSecondary = BaniterioColors.ink,
    background = BaniterioColors.surface,
    onBackground = BaniterioColors.ink,
    surface = BaniterioColors.panel,
    onSurface = BaniterioColors.ink,
    outline = BaniterioColors.outline,
)

@Composable
fun BaniterioTheme(content: @Composable () -> Unit) {
    val fonts = baniterioFonts()
    MaterialTheme(
        colorScheme = BaniterioColorScheme,
        typography = baniterioTypography(fonts),
        content = content,
    )
}
