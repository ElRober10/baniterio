package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AsistenciaRepository
import com.baniterio.app.data.BebidaRepository
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoAsistencia
import com.baniterio.app.data.ResultadoBebida
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.AsistenciaResumenDto
import com.baniterio.app.data.dto.CatalogoBebidasDto
import com.baniterio.app.data.dto.FichaBebidaBody
import kotlinx.coroutines.launch

private val METODOS_PAGO = listOf(
    "BIZUM" to "Bizum",
    "TRANSFERENCIA" to "Transferencia",
    "EFECTIVO" to "Efectivo",
)

/**
 * Diálogo "Añadir asistente": un admin/organizador da de alta a alguien sin
 * app (nombre, teléfono y, si el evento lleva ficha de bebida, lo que bebe y
 * los días que va). "Confirmar el pago ahora" hace que, tras el alta, se
 * confirme también el pago con el método elegido; si no se marca, la persona
 * queda con la cuota pendiente, igual que cualquier otro asistente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnadirAsistenteDialog(
    eventoId: Long,
    llevaFicha: Boolean,
    diasEvento: List<String>,
    asistenciaRepo: AsistenciaRepository,
    eventosRepo: EventosRepository,
    bebidaRepo: BebidaRepository,
    onAnadido: (AsistenciaResumenDto) -> Unit,
    onCerrar: () -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var confirmarPago by remember { mutableStateOf(false) }
    var metodoPago by remember { mutableStateOf("EFECTIVO") }
    var catalogo by remember { mutableStateOf<CatalogoBebidasDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(llevaFicha) {
        if (llevaFicha) {
            (bebidaRepo.catalogo() as? ResultadoBebida.Exito)?.dato?.let { catalogo = it }
        }
    }

    fun trasAlta(r: AsistenciaResumenDto) {
        if (!confirmarPago) {
            onAnadido(r)
            return
        }
        scope.launch {
            when (eventosRepo.confirmarPago(eventoId, r.id, metodoPago)) {
                is ResultadoEvento.Exito -> onAnadido(r)
                is ResultadoEvento.Error -> {
                    error = "Se ha añadido, pero no se pudo confirmar el pago."
                    onAnadido(r)
                }
            }
        }
    }

    fun anadirConFicha(ficha: FichaBebidaBody?) {
        val n = nombre.trim()
        if (n.isEmpty()) {
            error = "Escribe el nombre."
            return
        }
        error = null
        scope.launch {
            when (val r = asistenciaRepo.anadir(
                eventoId, n, ficha?.estado ?: "APUNTADO",
                telefono.trim().ifEmpty { null }, ficha,
            )) {
                is ResultadoAsistencia.Exito -> {
                    nombre = ""
                    telefono = ""
                    trasAlta(r.dato)
                }
                is ResultadoAsistencia.Error -> error = "No se pudo añadir a esa persona."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            if (!llevaFicha) {
                TextButton(enabled = nombre.isNotBlank(), onClick = { anadirConFicha(null) }) {
                    Text("Añadir")
                }
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        title = { Text("Añadir asistente") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Para invitados o gente sin la app.", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text("Teléfono (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (llevaFicha) {
                    FilaConfirmarPago(confirmarPago) { confirmarPago = it }
                    if (confirmarPago) {
                        Text("Forma de pago", style = MaterialTheme.typography.bodySmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            METODOS_PAGO.forEachIndexed { i, (valor, texto) ->
                                SegmentedButton(
                                    selected = metodoPago == valor,
                                    onClick = { metodoPago = valor },
                                    shape = SegmentedButtonDefaults.itemShape(i, METODOS_PAGO.size),
                                ) {
                                    Text(
                                        texto,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }

                    val cat = catalogo
                    if (cat == null) {
                        Text("Cargando catálogo de bebidas…")
                    } else {
                        FichaBebidaForm(
                            catalogo = cat,
                            dias = diasEvento,
                            fichaActual = null,
                            enDuda = false,
                            textoBoton = "Añadir asistente",
                            onGuardar = { anadirConFicha(it) },
                        )
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun FilaConfirmarPago(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("Confirmar el pago ahora")
    }
}
