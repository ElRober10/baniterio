package com.baniterio.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.data.CodigoErrorAdmin
import com.baniterio.app.data.ResultadoAdmin
import com.baniterio.app.data.dto.SolicitudEventoResumen
import com.baniterio.app.theme.BaniterioColors
import kotlinx.coroutines.launch

private sealed interface EstadoSolicEvento {
    data object Cargando : EstadoSolicEvento
    data class Cargada(val items: List<SolicitudEventoResumen>) : EstadoSolicEvento
    data class Error(val mensaje: String) : EstadoSolicEvento
}

/**
 * Bloque embebido al final de [AdminSolicitudesScreen] (solo para admin/superadmin
 * de verdad): las solicitudes de crear/borrar evento pendientes, con Aprobar y
 * Rechazar. Se pinta dentro de un `item { }` del LazyColumn del padre, así que
 * usa una [Column] normal, no otro LazyColumn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitudesEventoAdmin(adminRepo: AdminRepository) {
    var estado by remember { mutableStateOf<EstadoSolicEvento>(EstadoSolicEvento.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var rechazandoId by remember { mutableStateOf<Long?>(null) }
    var motivo by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun cargar(mostrarCargando: Boolean = true) {
        if (mostrarCargando) estado = EstadoSolicEvento.Cargando
        estado = when (val r = adminRepo.solicitudesEvento()) {
            is ResultadoAdmin.Exito -> EstadoSolicEvento.Cargada(r.dato)
            is ResultadoAdmin.Error -> EstadoSolicEvento.Error(r.mensaje)
        }
    }
    LaunchedEffect(Unit) { cargar() }

    fun aprobar(s: SolicitudEventoResumen) {
        scope.launch {
            when (val r = adminRepo.aprobarSolicitudEvento(s.id)) {
                is ResultadoAdmin.Exito -> {
                    aviso = if (s.tipo == "BORRAR") "Evento borrado." else "Crédito concedido."
                    cargar(mostrarCargando = false)
                }
                is ResultadoAdmin.Error -> {
                    aviso = r.mensaje
                    if (r.codigo == CodigoErrorAdmin.SOLICITUD_YA_RESUELTA) cargar(mostrarCargando = false)
                }
            }
        }
    }

    fun confirmarRechazo(id: Long) {
        trabajando = true
        scope.launch {
            try {
                when (val r = adminRepo.rechazarSolicitudEvento(id, motivo.ifBlank { null })) {
                    is ResultadoAdmin.Exito -> {
                        rechazandoId = null
                        aviso = "Solicitud rechazada."
                        cargar(mostrarCargando = false)
                    }
                    is ResultadoAdmin.Error -> {
                        aviso = r.mensaje
                        rechazandoId = null
                    }
                }
            } finally {
                trabajando = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Solicitudes de evento",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        aviso?.let {
            Text(it, color = BaniterioColors.brandBright, style = MaterialTheme.typography.bodyMedium)
        }

        when (val e = estado) {
            is EstadoSolicEvento.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoSolicEvento.Error -> Text(e.mensaje, color = BaniterioColors.error)
            is EstadoSolicEvento.Cargada -> {
                if (e.items.isEmpty()) {
                    Text("No hay solicitudes de evento pendientes.", color = BaniterioColors.muted)
                } else {
                    e.items.forEach { s ->
                        TarjetaSolicitudEvento(
                            s = s,
                            onAprobar = { aprobar(s) },
                            onRechazar = { rechazandoId = s.id; motivo = "" },
                        )
                    }
                }
            }
        }
    }

    val idRechazo = rechazandoId
    if (idRechazo != null) {
        AlertDialog(
            onDismissRequest = { rechazandoId = null },
            title = { Text("Rechazar solicitud") },
            text = {
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo (opcional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarRechazo(idRechazo) }, enabled = !trabajando) {
                    Text("Rechazar")
                }
            },
            dismissButton = {
                TextButton(onClick = { rechazandoId = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun TarjetaSolicitudEvento(
    s: SolicitudEventoResumen,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .padding(20.dp),
    ) {
        Text(
            text = (if (s.tipo == "BORRAR") "Borrar evento" else "Crear evento") +
                "  ·  ${s.solicitante.nombre} ${s.solicitante.apellidos}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        s.evento?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                "«${it.nombre}» (${it.fecha})",
                color = BaniterioColors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        s.mensaje?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = BaniterioColors.muted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAprobar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BaniterioColors.brand,
                    contentColor = BaniterioColors.gold,
                ),
            ) { Text("Aprobar", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onRechazar) {
                Text("Rechazar", color = BaniterioColors.brandBright)
            }
        }
    }
}
