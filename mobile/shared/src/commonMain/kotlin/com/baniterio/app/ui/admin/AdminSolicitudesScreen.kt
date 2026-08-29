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
import com.baniterio.app.data.dto.SolicitudResumen
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import kotlinx.coroutines.launch

private sealed interface EstadoLista {
    data object Cargando : EstadoLista
    data class Cargada(val items: List<SolicitudResumen>) : EstadoLista
    data class Error(val mensaje: String) : EstadoLista
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSolicitudesScreen(adminRepo: AdminRepository, onVolver: () -> Unit) {
    var estado by remember { mutableStateOf<EstadoLista>(EstadoLista.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var rechazandoId by remember { mutableStateOf<Long?>(null) }
    var motivoRechazo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun cargar() {
        estado = EstadoLista.Cargando
        estado = when (val r = adminRepo.solicitudes()) {
            is ResultadoAdmin.Exito -> EstadoLista.Cargada(r.dato)
            is ResultadoAdmin.Error -> EstadoLista.Error(r.mensaje)
        }
    }

    LaunchedEffect(Unit) { cargar() }

    fun aprobar(s: SolicitudResumen) {
        scope.launch {
            when (val r = adminRepo.aprobar(s.id)) {
                is ResultadoAdmin.Exito -> {
                    aviso = if (r.dato.resultado == "CUENTA_CREADA") {
                        "Cuenta creada y correo enviado."
                    } else {
                        "Teléfono autorizado y correo enviado."
                    }
                    cargar()
                }

                is ResultadoAdmin.Error -> {
                    aviso = r.mensaje
                    if (r.codigo == CodigoErrorAdmin.SOLICITUD_YA_RESUELTA) cargar()
                }
            }
        }
    }

    fun confirmarRechazo(id: Long) {
        scope.launch {
            when (val r = adminRepo.rechazar(id, motivoRechazo.ifBlank { null })) {
                is ResultadoAdmin.Exito -> {
                    rechazandoId = null
                    aviso = "Solicitud rechazada."
                    cargar()
                }

                is ResultadoAdmin.Error -> {
                    aviso = r.mensaje
                    rechazandoId = null
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
                text = "Solicitudes de acceso",
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
            is EstadoLista.Cargando -> item {
                Text("Cargando…", color = BaniterioColors.muted)
            }

            is EstadoLista.Error -> item {
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

            is EstadoLista.Cargada -> {
                if (e.items.isEmpty()) {
                    item {
                        Text("No hay solicitudes pendientes.", color = BaniterioColors.muted)
                    }
                } else {
                    items(e.items, key = { it.id }) { s ->
                        TarjetaSolicitud(
                            s = s,
                            onAprobar = { aprobar(s) },
                            onRechazar = {
                                rechazandoId = s.id
                                motivoRechazo = ""
                            },
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
                    value = motivoRechazo,
                    onValueChange = { motivoRechazo = it },
                    label = { Text("Motivo (opcional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarRechazo(idRechazo) }) {
                    Text("Rechazar")
                }
            },
            dismissButton = {
                TextButton(onClick = { rechazandoId = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun TarjetaSolicitud(
    s: SolicitudResumen,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .padding(20.dp),
    ) {
        Text(
            text = "${s.nombre} ${s.apellidos}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${s.telefono}  ·  ${s.email}",
            color = BaniterioColors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (s.traeContrasena) "Trae contraseña" else "Sin contraseña",
            color = BaniterioColors.muted,
            style = MaterialTheme.typography.labelLarge,
        )

        BloqueCampo("Motivo", s.motivo)
        BloqueCampo("Relación", s.relacion)
        BloqueCampo("Conocidos", s.conocidos)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAprobar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BaniterioColors.brand,
                    contentColor = BaniterioColors.gold,
                ),
            ) {
                Text("Aprobar", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onRechazar) {
                Text("Rechazar", color = BaniterioColors.brandBright)
            }
        }
    }
}

@Composable
private fun BloqueCampo(etiqueta: String, valor: String) {
    Spacer(Modifier.height(12.dp))
    Column {
        Text(
            text = etiqueta,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = valor,
            color = BaniterioColors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
