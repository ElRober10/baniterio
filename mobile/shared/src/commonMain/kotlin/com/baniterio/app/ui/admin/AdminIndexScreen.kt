package com.baniterio.app.ui.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.data.ResultadoAdmin
import com.baniterio.app.nav.Screen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

@Composable
fun AdminIndexScreen(
    areas: List<String>,
    esAdmin: Boolean,
    adminRepo: AdminRepository,
    onAbrir: (Screen) -> Unit,
    onVolver: () -> Unit,
) {
    // Pendientes por área para la campanita de cada tarjeta. Se pide al entrar;
    // al volver del detalle (p. ej. tras resolver una solicitud) esta pantalla
    // se recompone y el LaunchedEffect vuelve a pedirlo, así el número está fresco.
    var pendientes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        when (val r = adminRepo.pendientesPorArea()) {
            is ResultadoAdmin.Exito -> pendientes = r.dato
            is ResultadoAdmin.Error -> Unit // la campanita es secundaria: si falla, no se muestra número
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BaniterioWordmark()
            Text(
                text = "Volver",
                color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { onVolver() },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Administración",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        if ("ADMIN_SOLICITUDES" in areas) {
            TarjetaAdmin(
                nombre = "Solicitudes",
                descripcion = "Revisa y resuelve las peticiones de acceso a la peña.",
                cuenta = pendientes["ADMIN_SOLICITUDES"] ?: 0,
                onClick = { onAbrir(Screen.AdminSolicitudes) },
            )
            Spacer(Modifier.height(16.dp))
        }
        if ("ADMIN_PERMISOS" in areas) {
            TarjetaAdmin(
                nombre = "Permisos",
                descripcion = "Rol y accesos de cada miembro.",
                cuenta = pendientes["ADMIN_PERMISOS"] ?: 0,
                onClick = { onAbrir(Screen.AdminPermisos) },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (esAdmin) {
            TarjetaAdmin(
                nombre = "Bebidas",
                descripcion = "Acepta o rechaza las bebidas que la gente propone en la ficha de San Miguel.",
                cuenta = 0,
                onClick = { onAbrir(Screen.AdminBebidas) },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (!esAdmin && areas.none { it == "ADMIN_SOLICITUDES" || it == "ADMIN_PERMISOS" }) {
            Text(
                text = "No tienes ninguna sección de administración disponible.",
                color = BaniterioColors.muted,
            )
        }
    }
}

@Composable
private fun TarjetaAdmin(
    nombre: String,
    descripcion: String,
    cuenta: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            AvisoPendientes(cuenta)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
    }
}
