package com.baniterio.app.ui.eventos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
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

/** (valor de backend, etiqueta) de los tres estados de asistencia. */
internal val ESTADOS_ASISTENCIA = listOf(
    "APUNTADO" to "Me apunto",
    "NO_VOY" to "No voy",
    "EN_DUDA" to "En duda",
)

/**
 * Vista de un evento: sus datos, el recuento de asistentes y —según los permisos
 * que devuelve el backend— los botones de gestión y borrado. La parte de
 * asistencia (responder, ficha de bebida, convocatoria, añadir a mano) se movió
 * a [AsistenciaEventoScreen] mientras se rediseña.
 */
@Composable
fun EventoDetalleScreen(
    eventosRepo: EventosRepository,
    eventoId: Long,
    onEditar: () -> Unit,
    onBorrado: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoDetalle>(EstadoDetalle.Cargando) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
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
                    if (ev.oculto) {
                        Text(
                            "OCULTO (BORRADO)",
                            style = MaterialTheme.typography.labelSmall,
                            color = BaniterioColors.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        ev.nombre,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(if (ev.pasado) 0.7f else 1f),
                    )
                    Text(
                        buildString {
                            append(formatoFecha(ev.fecha))
                            ev.fechaFin?.let { append(" – ").append(formatoFecha(it)) }
                            ev.lugar?.let { append("  ·  ").append(it) }
                            if (ev.pasado) append("  ·  PASADO")
                        },
                        color = BaniterioColors.muted,
                    )
                    Text("Cuenta: ${ev.cuenta.nombre}", color = BaniterioColors.muted)
                    val cuotas = remember(ev) {
                        listOfNotNull(
                            ev.cuotaCubatas?.let { "Cubatas" to it },
                            ev.cuotaCervezas?.let { "Cervezas" to it },
                            ev.cuotaCubatas1Dia?.let { "Cubatas 1 día" to it },
                            ev.cuotaCervezas1Dia?.let { "Cervezas 1 día" to it },
                            ev.cuotaEmbarazada?.let { "Embarazada" to it },
                        )
                    }
                    if (cuotas.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(BaniterioColors.brand.copy(alpha = 0.10f))
                                .border(1.dp, BaniterioColors.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "CUOTAS A PAGAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = BaniterioColors.goldSoft,
                                fontWeight = FontWeight.Bold,
                            )
                            // 3 cajas por fila fijas; la fila incompleta (si sobran 1 o 2) se
                            // centra, igual que en la web (evento-detalle.html).
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val espaciado = 8.dp
                                val anchoCaja = (maxWidth - espaciado * 2) / 3
                                Column(verticalArrangement = Arrangement.spacedBy(espaciado)) {
                                    cuotas.chunked(3).forEach { fila ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (fila.size == 3) {
                                                Arrangement.spacedBy(espaciado)
                                            } else {
                                                Arrangement.spacedBy(espaciado, Alignment.CenterHorizontally)
                                            },
                                        ) {
                                            fila.forEach { (etiqueta, importe) ->
                                                CajaCuota(etiqueta, importe, Modifier.width(anchoCaja))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ev.asistencia.ficha.miFicha?.let { f ->
                        if (f.cuota != null) {
                            Text(
                                "Tu cuota: ${formatoImporte(f.cuota)} €",
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        } else if (f.cuotaPendiente) {
                            Text(
                                "Tu cuota está pendiente de que se fije la cuota máxima del evento.",
                                color = BaniterioColors.muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
                    if (ev.asistencia.notificacionMandada) {
                        Text(
                            "${ev.asistencia.apuntados} apuntados · ${ev.asistencia.enDuda} en duda · " +
                                "${ev.asistencia.sinContestar} sin contestar",
                            color = BaniterioColors.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    aviso?.let { Text(it, color = BaniterioColors.gold) }

                    if (ev.puedoEditar || ev.puedoBorrar) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ev.puedoEditar) {
                                Button(
                                    onClick = onEditar,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BaniterioColors.brand,
                                        contentColor = BaniterioColors.gold,
                                    ),
                                ) { Text("Editar", fontWeight = FontWeight.Bold) }
                            }
                            if (ev.puedoBorrar && !ev.oculto) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            when (val r = eventosRepo.ocultar(eventoId)) {
                                                is ResultadoEvento.Exito -> onBorrado()
                                                is ResultadoEvento.Error -> aviso = r.mensaje
                                            }
                                        }
                                    },
                                ) { Text("Borrar") }
                            }
                            if (ev.puedoBorrar && ev.oculto) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            when (val r = eventosRepo.recuperar(eventoId)) {
                                                is ResultadoEvento.Exito -> {
                                                    aviso = "Evento recuperado."
                                                    intento++
                                                }
                                                is ResultadoEvento.Error -> aviso = r.mensaje
                                            }
                                        }
                                    },
                                ) { Text("Recuperar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Una caja de la rejilla de "Cuotas a pagar" del detalle de evento. */
@Composable
private fun CajaCuota(etiqueta: String, importe: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BaniterioColors.brand.copy(alpha = 0.15f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = BaniterioColors.muted)
        Text(
            "${formatoImporte(importe)} €",
            style = MaterialTheme.typography.titleMedium,
            color = BaniterioColors.goldSoft,
            fontWeight = FontWeight.Bold,
        )
    }
}
