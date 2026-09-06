package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.AsistenteFilaDto
import com.baniterio.app.data.dto.ListadoAsistentesDto

private fun modalidadTexto(m: String) = when (m) {
    "COMPLETA" -> "peña completa"
    "SOLO_CERVEZA" -> "solo cerveza"
    "UN_DIA" -> "un día"
    "EMBARAZADA" -> "embarazada"
    else -> m
}

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
                                        " · " + (if (a.pagado) "pagado" else "pendiente"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(bebidaTexto(a), style = MaterialTheme.typography.bodySmall)
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
}
