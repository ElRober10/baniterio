package com.baniterio.app.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors

/**
 * Relieve "3D" común a la carta de miembro (rejilla) y a la carta del editor:
 * sombra proyectada teñida de morado —en fondo oscuro la negra no se ve—,
 * degradado vertical en la cara (luz arriba, sombra abajo) y un bisel de 1 dp
 * con filo claro arriba y oscuro abajo. Se aplica a un contenedor con [forma].
 */
fun Modifier.relieveDeCarta(forma: Shape): Modifier = this
    .shadow(
        elevation = 16.dp,
        shape = forma,
        ambientColor = BaniterioColors.brand,
        spotColor = BaniterioColors.brand,
    )
    .clip(forma)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF2A2467),
                BaniterioColors.panel,
                Color(0xFF141031),
            ),
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                BaniterioColors.brandBright.copy(alpha = 0.55f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.35f),
            ),
        ),
        shape = forma,
    )
