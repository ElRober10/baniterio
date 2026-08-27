package com.baniterio.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import baniterio.shared.generated.resources.Res
import baniterio.shared.generated.resources.archivo_regular
import baniterio.shared.generated.resources.archivo_semibold
import baniterio.shared.generated.resources.metal_mania_regular
import baniterio.shared.generated.resources.unbounded_bold
import org.jetbrains.compose.resources.Font

data class BaniterioFonts(
    val display: FontFamily,
    val sans: FontFamily,
    val metal: FontFamily,
)

@Composable
fun baniterioFonts(): BaniterioFonts {
    val display = FontFamily(Font(Res.font.unbounded_bold, FontWeight.Bold))
    val sans = FontFamily(
        Font(Res.font.archivo_regular, FontWeight.Normal),
        Font(Res.font.archivo_semibold, FontWeight.SemiBold),
    )
    val metal = FontFamily(Font(Res.font.metal_mania_regular, FontWeight.Normal))
    return BaniterioFonts(display = display, sans = sans, metal = metal)
}

@Composable
fun baniterioTypography(fonts: BaniterioFonts): Typography {
    val base = Typography()
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = fonts.sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = fonts.sans),
        labelLarge = base.labelLarge.copy(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold),
    )
}
