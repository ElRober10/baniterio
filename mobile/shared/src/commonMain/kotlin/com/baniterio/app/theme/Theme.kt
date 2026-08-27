package com.baniterio.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

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

/**
 * El nombre "Bañiterio" en la tipografía Metal Mania y en dorado, tal y como aparece
 * en la cabecera del front web en todas sus páginas.
 */
@Composable
fun BaniterioWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 24.sp) {
    Text(
        text = "Bañiterio",
        fontFamily = baniterioFonts().metal,
        color = BaniterioColors.gold,
        fontSize = fontSize,
        modifier = modifier,
    )
}
