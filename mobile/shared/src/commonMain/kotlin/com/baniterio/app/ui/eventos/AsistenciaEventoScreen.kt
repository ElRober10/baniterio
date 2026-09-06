package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AsistenciaRepository
import com.baniterio.app.data.BebidaRepository
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoAsistencia
import com.baniterio.app.data.ResultadoBebida
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.CatalogoBebidasDto
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.FichaBebidaBody
import com.baniterio.app.theme.BaniterioColors
import kotlinx.coroutines.launch

/**
 * APARCADO — no está en la navegación (rediseño de eventos en curso,
 * 2026-09-04). Reúne la parte de asistencia que antes vivía en
 * [EventoDetalleScreen]: responder Me apunto / No voy / En duda, la ficha de
 * bebida (San Miguel) y —solo admin u organizador— mandar la convocatoria y
 * añadir asistentes a mano. Se carga solo por [eventoId], igual que el detalle,
 * para poder reengancharlo tal cual cuando se decida dónde va.
 */
@Composable
fun AsistenciaEventoScreen(
    eventosRepo: EventosRepository,
    asistenciaRepo: AsistenciaRepository,
    bebidaRepo: BebidaRepository,
    eventoId: Long,
) {
    var evento by remember { mutableStateOf<EventoDetalle?>(null) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    var dialogoNotif by remember { mutableStateOf(false) }
    var textoNotif by remember { mutableStateOf("") }
    var nombreManual by remember { mutableStateOf("") }
    var estadoManual by remember { mutableStateOf("APUNTADO") }
    var catalogo by remember { mutableStateOf<CatalogoBebidasDto?>(null) }
    var resultadoFicha by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventoId, intento) {
        when (val r = eventosRepo.detalle(eventoId)) {
            is ResultadoEvento.Exito -> {
                if (r.dato.asistencia.ficha.llevaFicha && catalogo == null) {
                    (bebidaRepo.catalogo() as? ResultadoBebida.Exito)?.let { catalogo = it.dato }
                }
                evento = r.dato
                mensajeError = null
            }
            is ResultadoEvento.Error -> mensajeError = r.mensaje
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        mensajeError?.let {
            Text(it, color = BaniterioColors.muted)
            Button(onClick = { intento++ }) { Text("Reintentar") }
        }

        val ev = evento ?: return@Column

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
                                    is ResultadoAsistencia.Exito -> evento = r.dato
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

        val cat = catalogo
        val miEstado = ev.asistencia.miAsistencia
        if (ev.asistencia.ficha.llevaFicha && cat != null &&
            (miEstado == "APUNTADO" || miEstado == "EN_DUDA")
        ) {
            FichaBebidaForm(
                catalogo = cat,
                dias = ev.asistencia.ficha.diasEvento,
                fichaActual = ev.asistencia.ficha.miFicha,
                enDuda = miEstado == "EN_DUDA",
                onGuardar = { body ->
                    scope.launch {
                        when (val r = asistenciaRepo.guardarFicha(eventoId, body)) {
                            is ResultadoAsistencia.Exito -> {
                                resultadoFicha = r.dato.cuota?.let {
                                    "Tu cuota: ${formatoImporte(it)} € (${r.dato.modalidad})"
                                } ?: "Cuota pendiente de que se fije la cuota máxima."
                                intento++
                            }
                            is ResultadoAsistencia.Error -> resultadoFicha = r.mensaje
                        }
                    }
                },
            )
            resultadoFicha?.let {
                Text(it, color = BaniterioColors.gold, fontWeight = FontWeight.Bold)
            }
        }

        aviso?.let { Text(it, color = BaniterioColors.gold) }

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
            val fichaEnAlta = ev.asistencia.ficha.llevaFicha &&
                (estadoManual == "APUNTADO" || estadoManual == "EN_DUDA")

            fun anadirAMano(ficha: FichaBebidaBody?) {
                val nombre = nombreManual.trim()
                if (nombre.isEmpty()) {
                    aviso = "Escribe el nombre."
                    return
                }
                scope.launch {
                    when (val r = asistenciaRepo.anadir(eventoId, nombre, estadoManual, ficha)) {
                        is ResultadoAsistencia.Exito -> {
                            nombreManual = ""
                            estadoManual = "APUNTADO"
                            aviso = r.dato.cuota?.let { "«$nombre» añadido — cuota ${formatoImporte(it)} €." }
                                ?: "«$nombre» añadido."
                            intento++
                        }
                        is ResultadoAsistencia.Error -> aviso = r.mensaje
                    }
                }
            }

            if (fichaEnAlta && cat != null) {
                FichaBebidaForm(
                    catalogo = cat,
                    dias = ev.asistencia.ficha.diasEvento,
                    fichaActual = null,
                    enDuda = estadoManual == "EN_DUDA",
                    onGuardar = { anadirAMano(it) },
                    textoBoton = "Añadir con su ficha",
                )
            } else {
                Button(
                    enabled = nombreManual.isNotBlank(),
                    onClick = { anadirAMano(null) },
                ) { Text("Añadir") }
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
