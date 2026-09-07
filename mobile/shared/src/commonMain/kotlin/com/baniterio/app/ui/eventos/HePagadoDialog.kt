package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.ListadoAsistentesDto
import com.baniterio.app.theme.BaniterioColors

/** El pago que se enviaría. En esta tanda NO se manda a ningún sitio. */
data class PagoDeclarado(
    val importe: Double,
    val metodo: String,
    val cubreUsuarioIds: List<Long>,
    val cubreAsistenciaIds: List<Long>,
)

private val METODOS = listOf(
    "TRANSFERENCIA" to "Transferencia a la cuenta de la peña",
    "BIZUM" to "Bizum al administrador",
    "EFECTIVO" to "Efectivo",
)

private fun relacionTexto(r: String) = when (r) {
    "PAREJA" -> "pareja"
    "HIJO" -> "hijo/a"
    "INVITADO" -> "invitado/a"
    else -> r
}

/**
 * Diálogo para declarar un pago (pieza 4). En esta tanda no persiste: valida y
 * llama a [onConfirmar] con el objeto; la pantalla solo muestra un aviso.
 * "A quién pagas" y "Cómo lo has pagado" van como chips que se marcan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HePagadoDialog(
    eventoId: Long,
    repo: EventosRepository,
    onConfirmar: (PagoDeclarado) -> Unit,
    onCerrar: () -> Unit,
) {
    var datos by remember { mutableStateOf<ListadoAsistentesDto?>(null) }
    var importe by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf<String?>(null) }
    val usuarioIds = remember { mutableStateListOf<Long>() }
    val asistenciaIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(eventoId) {
        (repo.asistentes(eventoId) as? ResultadoEvento.Exito)?.dato?.let {
            datos = it
            importe = it.miCuota?.let { c -> formatoImporte(c) } ?: ""
        }
    }

    val puedeEnviar = (importe.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0 && metodo != null

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(
                enabled = puedeEnviar,
                onClick = {
                    onConfirmar(
                        PagoDeclarado(
                            importe = importe.replace(',', '.').toDouble(),
                            metodo = metodo!!,
                            cubreUsuarioIds = usuarioIds.toList(),
                            cubreAsistenciaIds = asistenciaIds.toList(),
                        )
                    )
                },
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
        title = { Text("Confirmar el pago") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val d = datos
                if (d == null) {
                    Text("Cargando…")
                } else {
                    Text("¿A quién pagas?", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text("Yo" + (d.miCuota?.let { " · ${formatoImporte(it)} €" } ?: ""))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = BaniterioColors.brand,
                                disabledLabelColor = BaniterioColors.gold,
                            ),
                        )
                        d.puedoPagarPor.forEach { p ->
                            val esUsuario = p.usuarioId != null
                            val id = p.usuarioId ?: p.asistenciaId ?: return@forEach
                            val lista = if (esUsuario) usuarioIds else asistenciaIds
                            val marcado = lista.contains(id)
                            FilterChip(
                                selected = marcado,
                                onClick = { if (marcado) lista.remove(id) else lista.add(id) },
                                label = {
                                    Text("${p.nombre} · ${relacionTexto(p.relacion)} · ${formatoImporte(p.cuota)} €")
                                },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = importe,
                        onValueChange = { importe = it },
                        label = { Text("Importe pagado (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )

                    Text("Cómo lo has pagado", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        METODOS.forEach { (valor, texto) ->
                            FilterChip(
                                selected = metodo == valor,
                                onClick = { metodo = valor },
                                label = { Text(texto) },
                            )
                        }
                    }
                }
            }
        },
    )
}
