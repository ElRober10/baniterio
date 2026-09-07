package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.AsistenteFilaDto
import com.baniterio.app.data.dto.ListadoAsistentesDto
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
) {
    var datos by remember { mutableStateOf<ListadoAsistentesDto?>(null) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var confirmando by remember { mutableStateOf<AsistenteFilaDto?>(null) }
    var metodoElegido by remember { mutableStateOf("BIZUM") }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(eventoId) {
        when (val r = repo.asistentes(eventoId)) {
            is ResultadoEvento.Exito -> datos = r.dato
            is ResultadoEvento.Error -> error = true
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
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
                                Text(
                                    (a.cuota?.let { "${formatoImporte(it)} €" } ?: "sin cuota") +
                                        " · " + (if (a.pagado) "pagado" else "pendiente") +
                                        (if (a.pagado && a.metodoPago != null) {
                                            " · ${metodoLegible(a.metodoPago)}"
                                        } else {
                                            ""
                                        }),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(bebidaTexto(a), style = MaterialTheme.typography.bodySmall)
                                if (d.puedoConfirmarPagos && a.cuota != null) {
                                    if (!a.pagado) {
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
                        ) { Text(etiqueta) }
                    }
                }
            },
        )
    }
}
