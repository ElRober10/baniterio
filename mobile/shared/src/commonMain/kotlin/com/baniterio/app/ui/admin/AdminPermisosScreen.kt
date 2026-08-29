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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.data.ResultadoAdmin
import com.baniterio.app.data.dto.MiembroResumen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import kotlinx.coroutines.launch

private val AREAS = listOf(
    "ADMIN_SOLICITUDES" to "Solicitudes",
    "ADMIN_PERMISOS" to "Permisos",
)

private sealed interface EstadoMiembros {
    data object Cargando : EstadoMiembros
    data class Cargada(val items: List<MiembroResumen>) : EstadoMiembros
    data class Error(val mensaje: String) : EstadoMiembros
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPermisosScreen(adminRepo: AdminRepository, miId: Long?, onVolver: () -> Unit) {
    var estado by remember { mutableStateOf<EstadoMiembros>(EstadoMiembros.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun cargar() {
        estado = EstadoMiembros.Cargando
        estado = when (val r = adminRepo.miembros()) {
            is ResultadoAdmin.Exito -> EstadoMiembros.Cargada(r.dato)
            is ResultadoAdmin.Error -> EstadoMiembros.Error(r.mensaje)
        }
    }

    LaunchedEffect(Unit) { cargar() }

    fun ponerRol(m: MiembroResumen, rol: String) {
        scope.launch {
            when (val r = adminRepo.cambiarRol(m.id, rol)) {
                is ResultadoAdmin.Exito -> {
                    aviso = "Cambio guardado."
                    cargar()
                }

                is ResultadoAdmin.Error -> {
                    aviso = r.mensaje
                    cargar()
                }
            }
        }
    }

    fun activar(m: MiembroResumen, activo: Boolean) {
        scope.launch {
            when (val r = adminRepo.cambiarActivo(m.id, activo)) {
                is ResultadoAdmin.Exito -> {
                    aviso = "Cambio guardado."
                    cargar()
                }

                is ResultadoAdmin.Error -> {
                    aviso = r.mensaje
                    cargar()
                }
            }
        }
    }

    fun alternarArea(m: MiembroResumen, area: String, incluir: Boolean) {
        scope.launch {
            val nuevas = if (incluir) m.areas + area else m.areas - area
            when (val r = adminRepo.cambiarAreas(m.id, nuevas)) {
                is ResultadoAdmin.Exito -> {
                    aviso = "Cambio guardado."
                    cargar()
                }

                is ResultadoAdmin.Error -> {
                    aviso = r.mensaje
                    cargar()
                }
            }
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
                    text = "Volver",
                    color = BaniterioColors.brandBright,
                    modifier = Modifier.clickable { onVolver() },
                )
            }
        }
        item {
            Text(
                text = "Permisos de la peña",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        aviso?.let { mensaje ->
            item {
                Text(
                    text = mensaje,
                    color = BaniterioColors.brandBright,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        when (val e = estado) {
            is EstadoMiembros.Cargando -> item {
                Text("Cargando…", color = BaniterioColors.muted)
            }

            is EstadoMiembros.Error -> item {
                Column {
                    Text(e.mensaje, color = BaniterioColors.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Reintentar",
                        color = BaniterioColors.brandBright,
                        modifier = Modifier.clickable { scope.launch { cargar() } },
                    )
                }
            }

            is EstadoMiembros.Cargada -> {
                items(e.items, key = { it.id }) { m ->
                    TarjetaMiembro(
                        m = m,
                        esYo = m.id == miId,
                        onRol = { rol -> ponerRol(m, rol) },
                        onActivo = { activo -> activar(m, activo) },
                        onArea = { area, incluir -> alternarArea(m, area, incluir) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarjetaMiembro(
    m: MiembroResumen,
    esYo: Boolean,
    onRol: (String) -> Unit,
    onActivo: (Boolean) -> Unit,
    onArea: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${m.nombre} ${m.apellidos}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        m.mote?.let {
            Text(
                text = "($it)",
                color = BaniterioColors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = m.telefono,
            color = BaniterioColors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (m.esSuperadmin) {
            Text(
                text = "Fundadora · acceso total",
                color = BaniterioColors.gold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (esYo) {
            Text(
                text = "No puedes cambiar tus propios permisos aquí.",
                color = BaniterioColors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = m.rol == "ADMIN",
                onClick = { onRol("ADMIN") },
                label = { Text("Admin") },
                enabled = !m.esSuperadmin && !esYo,
            )
            FilterChip(
                selected = m.rol == "MIEMBRO",
                onClick = { onRol("MIEMBRO") },
                label = { Text("Miembro") },
                enabled = !m.esSuperadmin && !esYo,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activo", color = BaniterioColors.muted)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = m.activo,
                onCheckedChange = { onActivo(it) },
                enabled = !m.esSuperadmin && !esYo,
            )
        }

        if (m.rol == "ADMIN" || m.esSuperadmin) {
            Text(
                text = "Acceso a todas las áreas por rol",
                color = BaniterioColors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            AREAS.forEach { (clave, etiqueta) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = clave in m.areas,
                        onCheckedChange = { onArea(clave, it) },
                        enabled = !esYo,
                    )
                    Text(etiqueta, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
