package com.baniterio.app.ui.admin

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors

/**
 * Insignia "tienes N cosas sin atender". Presentacional: quien la usa le pasa la
 * [cuenta]. Con 0 o menos no pinta nada — el aviso solo existe cuando hay
 * trabajo. Más de 99 se muestra como "99+". Globo dorado sólido con texto
 * oscuro para que salte a la vista, igual que `<app-aviso-pendientes>` en web.
 *
 * Cada pocos segundos se balancea un instante (como una campana) para reclamar
 * atención y luego se queda quieto el resto del ciclo.
 */
@Composable
fun AvisoPendientes(cuenta: Int, modifier: Modifier = Modifier) {
    if (cuenta <= 0) return

    val transicion = rememberInfiniteTransition(label = "campaneo")
    val giro by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                -12f at 120
                10f at 360
                -7f at 600
                4f at 840
                0f at 1080
                0f at 4000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "giro",
    )

    Text(
        text = if (cuenta > 99) "99+" else cuenta.toString(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = BaniterioColors.brand,
        modifier = modifier
            .graphicsLayer {
                rotationZ = giro
                transformOrigin = TransformOrigin(0.5f, 0.15f)
            }
            .clip(CircleShape)
            .background(BaniterioColors.gold)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
