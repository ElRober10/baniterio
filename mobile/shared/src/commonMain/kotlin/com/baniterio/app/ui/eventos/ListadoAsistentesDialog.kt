package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AsistenciaRepository
import com.baniterio.app.data.BebidaRepository
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.AsistenteFilaDto
import com.baniterio.app.data.dto.ListadoAsistentesDto
import com.baniterio.app.theme.BaniterioColors
import kotlinx.coroutines.launch

private fun modalidadTexto(m: String) = when (m) {
    "COMPLETA" -> "peña completa"
    "SOLO_CERVEZA" -> "solo cerveza"
    "UN_DIA" -> "un día"
    "EMBARAZADA" -> "embarazada"
    else -> m
}

private fun metodoLegible(m: String?) = when (m) {
    "BIZUM" -> "Bizum"
    "TRANSFERENCIA" -> "Transferencia"
    "EFECTIVO" -> "Efectivo"
    else -> m ?: ""
}

private fun estadoPagoTexto(e: String?) = when (e) {
    "DECLARADO" -> "Pagado, pendiente de confirmar"
    "CONFIRMADO_PENDIENTE_ENVIO" -> "Confirmado, pendiente de ingresar en la cuenta"
    "CONFIRMADO_EN_CUENTA" -> "Confirmado y en la cuenta"
    else -> "Pendiente de pago"
}

private val METODOS_PAGO = listOf(
    "BIZUM" to "Bizum",
    "TRANSFERENCIA" to "Transferencia",
    "EFECTIVO" to "Efectivo",
)

private fun bebidaTexto(a: AsistenteFilaDto): String {
    val b = a.bebida ?: return "—"
    val alc = b.alcohol ?: "sin alcohol"
    return "$alc · ${b.refresco} (${modalidadTexto(b.modalidad)})"
}

/**
 * Diálogo de solo lectura con la lista de asistentes a un evento de San Miguel
 * (pieza 4): nombre, estado, qué bebe, cuota y si ha pagado, más los totales.
 */
@Composable
fun ListadoAsistentesDialog(
    eventoId: Long,
    repo: EventosRepository,
    onCerrar: () -> Unit,
    puedoEditar: Boolean = false,
    llevaFicha: Boolean = false,
    diasEvento: List<String> = emptyList(),
    asistenciaRepo: AsistenciaRepository? = null,
    bebidaRepo: BebidaRepository? = null,
) {
    var datos by remember { mutableStateOf<ListadoAsistentesDto?>(null) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var confirmando by remember { mutableStateOf<AsistenteFilaDto?>(null) }
    var metodoElegido by remember { mutableStateOf("BIZUM") }
    var guardando by remember { mutableStateOf(false) }
    var mostrarAnadir by remember { mutableStateOf(false) }
    var actualizando by remember { mutableStateOf<AsistenteFilaDto?>(null) }
    var cuotaElegida by remember { mutableStateOf<Double?>(null) }
    var metodoCuota by remember { mutableStateOf("BIZUM") }
    var errorCuota by remember { mutableStateOf(false) }

    fun cargar() {
        scope.launch {
            when (val r = repo.asistentes(eventoId)) {
                is ResultadoEvento.Exito -> datos = r.dato
                is ResultadoEvento.Error -> error = true
            }
        }
    }

    LaunchedEffect(eventoId) { cargar() }

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (puedoEditar && asistenciaRepo != null && bebidaRepo != null) {
                    TextButton(onClick = { mostrarAnadir = true }) { Text("Añadir asistente") }
                }
                TextButton(onClick = onCerrar) { Text("Cerrar") }
            }
        },
        title = { Text("Asistentes") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val d = datos
                when {
                    error -> Text("No se ha podido cargar la lista.")
                    d == null -> Text("Cargando…")
                    d.asistentes.isEmpty() -> Text("Todavía no hay nadie apuntado.")
                    else -> {
                        d.asistentes.forEach { a ->
                            Column {
                                Text(
                                    buildString {
                                        append(a.nombre)
                                        if (a.estado == "EN_DUDA") append(" (en duda)")
                                        if (a.esManual) append(" · invitado")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val confirmado = a.estadoPago == "CONFIRMADO_EN_CUENTA" ||
                                    a.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"
                                Text(
                                    (a.cuota?.let { "${formatoImporte(it)} €" } ?: "sin cuota") +
                                        " · " + estadoPagoTexto(a.estadoPago) +
                                        (if (confirmado && a.metodoPago != null) {
                                            " · ${metodoLegible(a.metodoPago)}"
                                        } else {
                                            ""
                                        }),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                a.pendienteTransferir?.let {
                                    Text(
                                        "${formatoImporte(it)} € de la cuota pendientes de transferir",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.gold,
                                    )
                                }
                                Text(bebidaTexto(a), style = MaterialTheme.typography.bodySmall)
                                if (d.puedoConfirmarPagos && a.cuota != null) {
                                    TextButton(onClick = {
                                        metodoCuota = "BIZUM"
                                        errorCuota = false
                                        cuotaElegida = a.cuota
                                        actualizando = a
                                    }) { Text("Actualizar cuota") }
                                    if (!confirmado) {
                                        TextButton(onClick = {
                                            metodoElegido = "BIZUM"
                                            confirmando = a
                                        }) { Text("Confirmar el pago") }
                                    } else {
                                        TextButton(onClick = {
                                            scope.launch {
                                                (repo.deshacerPago(eventoId, a.asistenciaId)
                                                    as? ResultadoEvento.Exito)?.let { datos = it.dato }
                                            }
                                        }) { Text("deshacer") }
                                    }
                                }
                            }
                        }
                        Text(
                            "Total cuotas: ${formatoImporte(d.totalCuotas)} € · " +
                                "pagado: ${formatoImporte(d.totalPagado)} €",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
    )

    confirmando?.let { fila ->
        AlertDialog(
            onDismissRequest = { confirmando = null },
            confirmButton = {
                TextButton(
                    enabled = !guardando,
                    onClick = {
                        guardando = true
                        scope.launch {
                            when (val r = repo.confirmarPago(eventoId, fila.asistenciaId, metodoElegido)) {
                                is ResultadoEvento.Exito -> {
                                    datos = r.dato
                                    confirmando = null
                                }
                                is ResultadoEvento.Error -> Unit
                            }
                            guardando = false
                        }
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmando = null }) { Text("Cancelar") } },
            title = { Text("Confirmar el pago de ${fila.nombre}") },
            text = {
                @OptIn(ExperimentalMaterial3Api::class)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    METODOS_PAGO.forEachIndexed { i, (valor, etiqueta) ->
                        SegmentedButton(
                            selected = metodoElegido == valor,
                            onClick = { metodoElegido = valor },
                            shape = SegmentedButtonDefaults.itemShape(i, METODOS_PAGO.size),
                        ) {
                            Text(
                                etiqueta,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            },
        )
    }

    actualizando?.let { fila ->
        val opciones = datos?.opcionesCuota ?: emptyList()
        val actual = fila.cuota ?: 0.0
        val nueva = cuotaElegida
        val diferencia = if (nueva != null) Math.round((nueva - actual) * 100) / 100.0 else 0.0
        val confirmadoFila = fila.estadoPago == "CONFIRMADO_EN_CUENTA" ||
            fila.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"
        val pideMetodo = confirmadoFila && diferencia > 0
        AlertDialog(
            onDismissRequest = { actualizando = null },
            confirmButton = {
                TextButton(
                    enabled = !guardando && nueva != null && diferencia != 0.0,
                    onClick = {
                        guardando = true
                        errorCuota = false
                        scope.launch {
                            when (
                                val r = repo.actualizarCuota(
                                    eventoId, fila.asistenciaId, nueva ?: actual,
                                    if (pideMetodo) metodoCuota else null,
                                )
                            ) {
                                is ResultadoEvento.Exito -> {
                                    datos = r.dato
                                    actualizando = null
                                }
                                is ResultadoEvento.Error -> errorCuota = true
                            }
                            guardando = false
                        }
                    },
                ) { Text("Actualizar") }
            },
            dismissButton = { TextButton(onClick = { actualizando = null }) { Text("Cancelar") } },
            title = { Text("Actualizar la cuota de ${fila.nombre}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cuota actual: ${formatoImporte(actual)} €")
                    Text("¿A qué cuota se actualiza?")
                    opciones.chunked(2).forEach { par ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            par.forEach { o ->
                                val elegida = cuotaElegida == o.importe
                                val texto = "${o.texto}\n${formatoImporte(o.importe)} €"
                                if (elegida) {
                                    Button(
                                        onClick = { cuotaElegida = o.importe },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BaniterioColors.brand,
                                            contentColor = BaniterioColors.gold,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    ) { Text(texto, textAlign = TextAlign.Center) }
                                } else {
                                    OutlinedButton(
                                        onClick = { cuotaElegida = o.importe },
                                        modifier = Modifier.weight(1f),
                                    ) { Text(texto, textAlign = TextAlign.Center) }
                                }
                            }
                            if (par.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (diferencia != 0.0) {
                        Text(
                            "Diferencia: " + (if (diferencia > 0) "+" else "") +
                                "${formatoImporte(diferencia)} €",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (pideMetodo) {
                        Text("¿Cómo ha pagado la diferencia?")
                        @OptIn(ExperimentalMaterial3Api::class)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            METODOS_PAGO.forEachIndexed { i, (valor, etiqueta) ->
                                SegmentedButton(
                                    selected = metodoCuota == valor,
                                    onClick = { metodoCuota = valor },
                                    shape = SegmentedButtonDefaults.itemShape(i, METODOS_PAGO.size),
                                ) {
                                    Text(
                                        etiqueta,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                        if (metodoCuota != "TRANSFERENCIA") {
                            Text(
                                "Quedarán ${formatoImporte(diferencia)} € pendientes de transferir " +
                                    "a la cuenta de la peña.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BaniterioColors.muted,
                            )
                        }
                    }
                    if (errorCuota) {
                        Text("No se pudo actualizar la cuota.", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    if (mostrarAnadir && asistenciaRepo != null && bebidaRepo != null) {
        AnadirAsistenteDialog(
            eventoId = eventoId,
            llevaFicha = llevaFicha,
            diasEvento = diasEvento,
            asistenciaRepo = asistenciaRepo,
            eventosRepo = repo,
            bebidaRepo = bebidaRepo,
            onAnadido = {
                mostrarAnadir = false
                cargar()
            },
            onCerrar = { mostrarAnadir = false },
        )
    }
}
