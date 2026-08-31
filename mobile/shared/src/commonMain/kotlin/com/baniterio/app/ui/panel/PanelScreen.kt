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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.data.ResultadoAdmin
import com.baniterio.app.model.Seccion
import com.baniterio.app.model.seccionesPanel
import com.baniterio.app.nav.Screen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.theme.baniterioFonts
import com.baniterio.app.ui.admin.AvisoPendientes

@Composable
fun PanelScreen(
    onAbrirSeccion: (Screen) -> Unit,
    onCerrarSesion: () -> Unit,
    tieneAdmin: Boolean,
    adminRepo: AdminRepository,
) {
    // Total de cosas sin atender, para la campanita de la tarjeta "Administración".
    // Solo se pide si el usuario tiene alguna área; al volver al panel esta pantalla
    // se recompone y se vuelve a pedir, así el número refleja lo ya resuelto.
    var totalPendientes by remember { mutableStateOf(0) }
    LaunchedEffect(tieneAdmin) {
        if (!tieneAdmin) return@LaunchedEffect
        when (val r = adminRepo.pendientesPorArea()) {
            is ResultadoAdmin.Exito -> totalPendientes = r.dato.values.sum()
            is ResultadoAdmin.Error -> Unit
        }
    }

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
            TarjetaSeccion(
                seccion = seccion,
                cuenta = if (seccion.destino == Screen.AdminIndex) totalPendientes else 0,
                onClick = { seccion.destino?.let(onAbrirSeccion) },
            )
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
private fun TarjetaSeccion(seccion: Seccion, cuenta: Int, onClick: () -> Unit) {
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
            } else {
                AvisoPendientes(cuenta)
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
