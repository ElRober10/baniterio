package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AsistenciaRepository
import com.baniterio.app.data.BebidaRepository
import com.baniterio.app.data.ResultadoAsistencia
import com.baniterio.app.data.ResultadoBebida
import com.baniterio.app.data.dto.CatalogoBebidasDto
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.PendienteRespuestaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoResponder {
    data object Cargando : EstadoResponder
    data class Lista(val pendientes: List<PendienteRespuestaDto>) : EstadoResponder
    data class Error(val mensaje: String) : EstadoResponder
}

/**
 * Pantalla bloqueante: mientras haya convocatorias sin contestar muestra la
 * primera y tres botones grandes. Incluye las propias y las de quien se pueda
 * responder por él (pareja con vínculo aceptado, hijos con cuenta propia),
 * etiquetadas "Respondiendo por: X" cuando no son de `miUsuarioId`. Al
 * responder recarga; cuando la lista queda vacía llama [onTerminado] (que
 * lleva al panel). Sin "atrás".
 */
@Composable
fun ResponderEventoScreen(
    asistenciaRepo: AsistenciaRepository,
    bebidaRepo: BebidaRepository,
    miUsuarioId: Long?,
    onTerminado: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoResponder>(EstadoResponder.Cargando) }
    var enviando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    // Cuando la respuesta de un evento de San Miguel pide ficha, se guarda aquí
    // (detalle + catálogo) y se pinta el formulario en vez de los 3 botones.
    var ficha by remember { mutableStateOf<Pair<EventoDetalle, CatalogoBebidasDto>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun cargar() {
        ficha = null
        when (val r = asistenciaRepo.pendientes()) {
            is ResultadoAsistencia.Exito ->
                if (r.dato.isEmpty()) onTerminado() else estado = EstadoResponder.Lista(r.dato)
            is ResultadoAsistencia.Error -> estado = EstadoResponder.Error(r.mensaje)
        }
    }
    LaunchedEffect(Unit) { cargar() }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BaniterioWordmark()

        when (val e = estado) {
            is EstadoResponder.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoResponder.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { scope.launch { cargar() } }) { Text("Reintentar") }
            }
            is EstadoResponder.Lista -> {
                val actual = e.pendientes.first()
                val ev = actual.evento
                val objetivoId = actual.paraUsuario.id
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .relieveDeCarta(RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("CONVOCATORIA", color = BaniterioColors.muted,
                        style = MaterialTheme.typography.labelSmall)
                    if (objetivoId != miUsuarioId) {
                        Text("Respondiendo por: ${actual.paraUsuario.nombre}",
                            color = BaniterioColors.goldSoft,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(
                        ev.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        buildString {
                            append(formatoFecha(ev.fecha))
                            ev.fechaFin?.let { append(" – ").append(formatoFecha(it)) }
                            ev.lugar?.let { append("  ·  ").append(it) }
                        },
                        color = BaniterioColors.muted,
                    )

                    val pend = ficha
                    if (pend != null) {
                        FichaBebidaForm(
                            catalogo = pend.second,
                            dias = pend.first.asistencia.ficha.diasEvento,
                            fichaActual = pend.first.asistencia.ficha.miFicha,
                            enDuda = pend.first.asistencia.miAsistencia == "EN_DUDA",
                            textoBoton = "Responder",
                            onGuardar = { body ->
                                scope.launch {
                                    when (asistenciaRepo.guardarFicha(
                                        ev.id, body.copy(paraUsuarioId = objetivoId),
                                    )) {
                                        is ResultadoAsistencia.Exito -> cargar()
                                        is ResultadoAsistencia.Error ->
                                            aviso = "No se pudo guardar la ficha."
                                    }
                                }
                            },
                        )
                    } else {
                        Text("¿Vas a ir?", color = MaterialTheme.colorScheme.onBackground)
                        ESTADOS_ASISTENCIA.forEach { (valor, texto) ->
                            Button(
                                enabled = !enviando,
                                onClick = {
                                    enviando = true
                                    aviso = null
                                    scope.launch {
                                        when (val r = asistenciaRepo.responder(ev.id, valor, objetivoId)) {
                                            is ResultadoAsistencia.Exito ->
                                                if (valor != "NO_VOY" && r.dato.asistencia.ficha.llevaFicha) {
                                                    when (val c = bebidaRepo.catalogo()) {
                                                        is ResultadoBebida.Exito -> ficha = r.dato to c.dato
                                                        is ResultadoBebida.Error -> cargar()
                                                    }
                                                } else {
                                                    cargar()
                                                }
                                            is ResultadoAsistencia.Error -> {
                                                aviso = r.mensaje
                                                cargar()
                                            }
                                        }
                                        enviando = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BaniterioColors.brand,
                                    contentColor = BaniterioColors.gold,
                                ),
                            ) { Text(texto, fontWeight = FontWeight.Bold) }
                        }
                    }

                    if (e.pendientes.size > 1) {
                        Text("Te quedan ${e.pendientes.size} convocatorias por contestar.",
                            color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    aviso?.let { Text(it, color = BaniterioColors.gold) }
                }
            }
        }
    }
}
