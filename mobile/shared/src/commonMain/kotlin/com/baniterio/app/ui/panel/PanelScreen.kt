package com.baniterio.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.baniterio.app.model.Seccion
import com.baniterio.app.model.seccionesPanel
import com.baniterio.app.nav.Screen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.theme.baniterioFonts

@Composable
fun PanelScreen(
    onAbrirSeccion: (Screen) -> Unit,
    onCerrarSesion: () -> Unit,
    tieneAdmin: Boolean,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BaniterioWordmark()
                Text(
                    text = "Cerrar sesión",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BaniterioColors.muted,
                    modifier = Modifier.clickable { onCerrarSesion() },
                )
            }
        }
        item {
            Text(
                text = textoConMarca("¡Bienvenido al ", "!"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            Text(
                text = textoConMarca(
                    "Aqui podras consultar todo lo relacionado con la peña ",
                    ", los miembros, los eventos, las cuentas, el inventario y la ropa de la peña...",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.muted,
            )
        }
        items(seccionesPanel(tieneAdmin)) { seccion ->
            TarjetaSeccion(seccion = seccion, onClick = { seccion.destino?.let(onAbrirSeccion) })
        }
    }
}

/**
 * Texto con la palabra "Bañiterio" resaltada en dorado y en la tipografía Metal
 * Mania, igual que en el front web (`<span class="font-metal text-gold">`).
 */
@Composable
private fun textoConMarca(prefijo: String, sufijo: String) = buildAnnotatedString {
    append(prefijo)
    withStyle(SpanStyle(fontFamily = baniterioFonts().metal, color = BaniterioColors.gold)) {
        append("Bañiterio")
    }
    append(sufijo)
}

@Composable
private fun TarjetaSeccion(seccion: Seccion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .clickable(enabled = seccion.destino != null, onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = seccion.nombre,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            if (seccion.destino == null) {
                Text(
                    text = "PRÓXIMAMENTE",
                    style = MaterialTheme.typography.labelLarge,
                    color = BaniterioColors.muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = seccion.descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
    }
}
