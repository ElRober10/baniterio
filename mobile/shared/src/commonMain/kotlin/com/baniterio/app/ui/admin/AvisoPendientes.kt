package com.baniterio.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors

/**
 * Insignia "tienes N cosas sin atender". Presentacional: quien la usa le pasa la
 * [cuenta]. Con 0 o menos no pinta nada — el aviso solo existe cuando hay
 * trabajo. Más de 99 se muestra como "99+". Es el equivalente de
 * `<app-aviso-pendientes>` del front web.
 */
@Composable
fun AvisoPendientes(cuenta: Int, modifier: Modifier = Modifier) {
    if (cuenta <= 0) return
    Text(
        text = if (cuenta > 99) "99+" else cuenta.toString(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = BaniterioColors.gold,
        modifier = modifier
            .clip(CircleShape)
            .background(BaniterioColors.brand)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
