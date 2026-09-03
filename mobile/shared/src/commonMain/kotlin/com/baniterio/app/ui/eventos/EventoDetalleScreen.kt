package com.baniterio.app.ui.eventos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AsistenciaRepository
import com.baniterio.app.data.BorradoEvento
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoAsistencia
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoDetalle {
    data object Cargando : EstadoDetalle
    data class Cargado(val evento: EventoDetalle) : EstadoDetalle
    data class Error(val mensaje: String) : EstadoDetalle
}

private val ESTADOS_ASISTENCIA = listOf(
    "APUNTADO" to "Me apunto",
    "NO_VOY" to "No voy",
    "EN_DUDA" to "En duda",
)

@Composable
fun EventoDetalleScreen(
    eventosRepo: EventosRepository,
    asistenciaRepo: AsistenciaRepository,
    eventoId: Long,
    onEditar: () -> Unit,
    onBorrado: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoDetalle>(EstadoDetalle.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    var dialogoNotif by remember { mutableStateOf(false) }
    var textoNotif by remember { mutableStateOf("") }
    var nombreManual by remember { mutableStateOf("") }
    var estadoManual by remember { mutableStateOf("APUNTADO") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventoId, intento) {
        estado = EstadoDetalle.Cargando
        estado = when (val r = eventosRepo.detalle(eventoId)) {
            is ResultadoEvento.Exito -> EstadoDetalle.Cargado(r.dato)
            is ResultadoEvento.Error -> EstadoDetalle.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BaniterioWordmark()
            Text(
                "Volver",
                style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { onVolver() },
            )
        }

        when (val e = estado) {
            is EstadoDetalle.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoDetalle.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoDetalle.Cargado -> {
                val ev = e.evento
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        ev.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(if (ev.pasado) 0.7f else 1f),
                    )
                    Text(
                        buildString {
                            append(ev.fecha)
                            ev.fechaFin?.let { append(" – ").append(it) }
                            if (ev.pasado) append("  ·  PASADO")
                        },
                        color = BaniterioColors.muted,
                    )
                    ev.lugar?.let { Text(it, color = BaniterioColors.muted) }
                    Text("Cuenta: ${ev.cuenta.nombre}", color = BaniterioColors.muted)
                    ev.cuotaMaxima?.let {
                        Text("Cuota máxima: ${formatoImporte(it)} €", color = BaniterioColors.muted)
                    }
                    ev.descripcion?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ev.creadoPor?.let {
                        Text(
                            "Creado por ${it.nombre}",
                            color = BaniterioColors.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (!ev.pasado) {
                    Text("¿Vas a ir?", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ESTADOS_ASISTENCIA.forEach { (valor, texto) ->
                            val elegido = ev.asistencia.miAsistencia == valor
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        when (val r = asistenciaRepo.responder(eventoId, valor)) {
                                            is ResultadoAsistencia.Exito ->
                                                estado = EstadoDetalle.Cargado(r.dato)
                                            is ResultadoAsistencia.Error -> aviso = r.mensaje
                                        }
                                    }
                                },
                                colors = if (elegido) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = BaniterioColors.brand,
                                        contentColor = BaniterioColors.gold,
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                },
                            ) { Text(texto) }
                        }
                    }
                }

                Text(
                    "${ev.asistencia.apuntados} apuntados · ${ev.asistencia.enDuda} en duda · " +
                        "${ev.asistencia.sinContestar} sin contestar",
                    color = BaniterioColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )

                aviso?.let { Text(it, color = BaniterioColors.gold) }

                if (ev.puedoEditar) {
                    Button(
                        onClick = onEditar,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BaniterioColors.brand,
                            contentColor = BaniterioColors.gold,
                        ),
                    ) { Text("Gestionar", fontWeight = FontWeight.Bold) }
                }
                if (ev.puedoBorrar) {
                    OutlinedButton(
                        enabled = !ev.borradoPendiente,
                        onClick = {
                            scope.launch {
                                when (val r = eventosRepo.borrar(eventoId)) {
                                    is ResultadoEvento.Exito ->
                                        if (r.dato == BorradoEvento.BORRADO) {
                                            onBorrado()
                                        } else {
                                            aviso = "Solicitud de borrado enviada. Un administrador tiene que autorizarla."
                                            intento++
                                        }
                                    is ResultadoEvento.Error -> aviso = r.mensaje
                                }
                            }
                        },
                    ) {
                        Text(
                            when {
                                ev.borradoPendiente -> "Borrado pendiente de autorización"
                                ev.creadoPor != null -> "Solicitar borrado"
                                else -> "Borrar"
                            },
                        )
                    }
                }

                if (ev.puedoEditar) {
                    Text("Convocatoria", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (ev.asistencia.puedeNotificar) {
                        Button(
                            onClick = { textoNotif = ""; dialogoNotif = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BaniterioColors.brand,
                                contentColor = BaniterioColors.gold,
                            ),
                        ) { Text("Mandar notificación", fontWeight = FontWeight.Bold) }
                    } else {
                        Text("El evento ya ha pasado: no se pueden mandar notificaciones.",
                            color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
                    }

                    Text("Añadir a mano", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Para invitados o gente sin la app.",
                        color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = nombreManual,
                        onValueChange = { nombreManual = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ESTADOS_ASISTENCIA.forEach { (valor, texto) ->
                            val elegido = estadoManual == valor
                            OutlinedButton(
                                onClick = { estadoManual = valor },
                                colors = if (elegido) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = BaniterioColors.brand,
                                        contentColor = BaniterioColors.gold,
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                },
                            ) { Text(texto) }
                        }
                    }
                    Button(
                        enabled = nombreManual.isNotBlank(),
                        onClick = {
                            val nombre = nombreManual.trim()
                            scope.launch {
                                when (val r = asistenciaRepo.anadir(eventoId, nombre, estadoManual)) {
                                    is ResultadoAsistencia.Exito -> {
                                        nombreManual = ""
                                        estadoManual = "APUNTADO"
                                        aviso = "«$nombre» añadido."
                                        intento++
                                    }
                                    is ResultadoAsistencia.Error -> aviso = r.mensaje
                                }
                            }
                        },
                    ) { Text("Añadir") }
                }
            }
        }
    }

    if (dialogoNotif) {
        AlertDialog(
            onDismissRequest = { dialogoNotif = false },
            title = { Text("Mandar notificación") },
            text = {
                OutlinedTextField(
                    value = textoNotif,
                    onValueChange = { textoNotif = it },
                    label = { Text("Texto (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = textoNotif
                    dialogoNotif = false
                    scope.launch {
                        when (val r = asistenciaRepo.mandarNotificacion(eventoId, t)) {
                            is ResultadoAsistencia.Exito -> { aviso = "Notificación enviada."; intento++ }
                            is ResultadoAsistencia.Error -> { aviso = r.mensaje; intento++ }
                        }
                    }
                }) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { dialogoNotif = false }) { Text("Cancelar") }
            },
        )
    }
}
