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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.nav.Screen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

@Composable
fun AdminIndexScreen(
    areas: List<String>,
    onAbrir: (Screen) -> Unit,
    onVolver: () -> Unit,
) {
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
                onClick = { onAbrir(Screen.AdminSolicitudes) },
            )
            Spacer(Modifier.height(16.dp))
        }
        if ("ADMIN_PERMISOS" in areas) {
            TarjetaAdmin(
                nombre = "Permisos",
                descripcion = "Rol y accesos de cada miembro.",
                onClick = { onAbrir(Screen.AdminPermisos) },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (areas.none { it == "ADMIN_SOLICITUDES" || it == "ADMIN_PERMISOS" }) {
            Text(
                text = "No tienes ninguna sección de administración disponible.",
                color = BaniterioColors.muted,
            )
        }
    }
}

@Composable
private fun TarjetaAdmin(nombre: String, descripcion: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Text(
            text = nombre,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
    }
}
