package com.baniterio.app.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.baniterio.app.theme.BaniterioColors

/**
 * La "cara" de una carta de miembro: foto o avatar a lo ancho, con proporción de
 * cromo (4:5) y un marco dorado fino por dentro. Si [modelo] es `null` (o la
 * carga falla) pinta las [iniciales] centradas sobre el fondo.
 *
 * [modelo] es `Any?` para aceptar tanto una URL absoluta (avatar / foto ya
 * guardada, ver `urlMedia`) como un `ByteArray` (foto recién elegida, sin subir).
 */
@Composable
fun CaraDeCarta(modelo: Any?, iniciales: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .background(BaniterioColors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (modelo == null) {
            Text(
                text = iniciales,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = BaniterioColors.outline,
            )
        } else {
            AsyncImage(
                model = modelo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
                .border(1.dp, BaniterioColors.gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        )
    }
}
