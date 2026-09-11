package com.baniterio.app.ui.compra

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.baniterio.app.data.ListaCompraRepository
import com.baniterio.app.data.ResultadoListaCompra
import com.baniterio.app.data.dto.CrearReglaBody
import com.baniterio.app.data.dto.ReglaCompraEventoDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private data class DatosEditor(val nombre: String, val reglas: List<ReglaCompraEventoDto>)

private val FORMULAS_CREABLES = listOf(
    "POR_PENISTA" to "Por peñista",
    "POR_PENISTA_DIA" to "Por peñista y día",
    "POR_DIA" to "Por día de fiesta",
    "POR_EVENTO" to "Por evento (cantidad fija)",
    "POR_CADA_N_PENISTAS" to "Por cada N peñistas",
    "CERVEZA_ALTERNATIVA" to "Por peñista y día (bebe cerveza)",
    "TINTO_ALTERNATIVA" to "Por peñista y día (bebe tinto)",
)

private val CATEGORIAS = listOf(
    "ALCOHOL" to "Alcohol",
    "CERVEZA" to "Cerveza",
    "REFRESCOS" to "Refrescos",
    "LIMPIEZA" to "Limpieza y utensilios",
    "COMIDA" to "Comida",
)

private fun fmtEditor(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

private fun esDinamica(tipo: String) =
    tipo == "ALCOHOL_SELECCIONADO" || tipo == "REFRESCO_SELECCIONADO"

private fun formulaLegible(r: ReglaCompraEventoDto): String = when (r.tipoFormula) {
    "POR_PENISTA" -> "${fmtEditor(r.factor)} por peñista"
    "POR_PENISTA_DIA" -> "${fmtEditor(r.factor)} por peñista y día"
    "POR_DIA" -> "${fmtEditor(r.factor)} por día de fiesta"
    "POR_EVENTO" -> "${fmtEditor(r.factor)} por evento"
    "POR_CADA_N_PENISTAS" -> "${fmtEditor(r.factor)} por cada ${r.porCada} peñistas"
    "CERVEZA_ALTERNATIVA" -> "${fmtEditor(r.factor)} por peñista y día (bebe cerveza)"
    "TINTO_ALTERNATIVA" -> "${fmtEditor(r.factor)} por peñista y día (bebe tinto de verano)"
    "ALCOHOL_SELECCIONADO" -> "0,5 botellas por peñista y día, por marca elegida"
    "REFRESCO_SELECCIONADO" -> "1 botella por peñista y día, por marca elegida"
    else -> r.tipoFormula
}

/**
 * Editor de la lista de la compra de un evento: ajustar cantidades,
 * activar/desactivar reglas y añadir/quitar artículos. Área INVENTARIO.
 */
@Composable
fun ListaCompraAdminEventoScreen(
    listaCompraRepo: ListaCompraRepository,
    eventoId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoCarga<DatosEditor>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var confirmarBorrado by remember { mutableStateOf<ReglaCompraEventoDto?>(null) }
    var dialogoAnadir by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = listaCompraRepo.adminEvento(eventoId)) {
            is ResultadoListaCompra.Exito -> EstadoCarga.Cargado(DatosEditor(r.dato.evento.nombre, r.dato.reglas))
            is ResultadoListaCompra.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CabeceraPantalla(onVolver)

        PantallaConEstado(estado, onReintentar = { intento++ }) { datos ->
            Text(
                "Cantidades — ${datos.nombre}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            aviso?.let { Text(it, color = BaniterioColors.gold) }

            datos.reglas.forEach { r ->
                FilaRegla(
                    regla = r,
                    onGuardar = { cantidad, activa ->
                        scope.launch {
                            aviso = null
                            val res = listaCompraRepo.ajustarRegla(eventoId, r.id, cantidad, activa)
                            if (res is ResultadoListaCompra.Error) aviso = res.mensaje
                            intento++
                        }
                    },
                    onQuitar = { confirmarBorrado = r },
                )
            }

            OutlinedButton(onClick = { dialogoAnadir = true }) { Text("Añadir artículo") }
        }
    }

    confirmarBorrado?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmarBorrado = null },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrado = null
                    scope.launch {
                        aviso = null
                        val res = listaCompraRepo.borrarRegla(eventoId, r.id)
                        if (res is ResultadoListaCompra.Error) aviso = res.mensaje
                        intento++
                    }
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = null }) { Text("Cancelar") } },
            title = { Text("Quitar artículo") },
            text = { Text("¿Quitar «${r.nombre}» de este evento?") },
        )
    }

    if (dialogoAnadir) {
        DialogoAnadir(
            onCancelar = { dialogoAnadir = false },
            onAnadir = { body ->
                dialogoAnadir = false
                scope.launch {
                    aviso = null
                    val res = listaCompraRepo.crearRegla(eventoId, body)
                    if (res is ResultadoListaCompra.Error) aviso = res.mensaje
                    intento++
                }
            },
        )
    }
}

@Composable
private fun FilaRegla(
    regla: ReglaCompraEventoDto,
    onGuardar: (Double?, Boolean) -> Unit,
    onQuitar: () -> Unit,
) {
    var cantidad by remember(regla.id) {
        mutableStateOf(regla.cantidadAjustada?.let { fmtEditor(it) } ?: "")
    }
    var activa by remember(regla.id) { mutableStateOf(regla.activa) }
    val dinamica = esDinamica(regla.tipoFormula)

    Column(
        modifier = Modifier.fillMaxWidth().relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${regla.nombre.ifBlank { regla.etiqueta }} · ${regla.tamano}",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "${formulaLegible(regla)} · calculado: ${fmtEditor(regla.cantidadCalculada)}",
            style = MaterialTheme.typography.bodySmall,
            color = BaniterioColors.muted,
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = cantidad,
                onValueChange = { cantidad = it },
                enabled = !dinamica,
                label = { Text("Cantidad") },
                placeholder = { Text("fórmula") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = activa, onCheckedChange = { activa = it })
                Text("Activa", color = BaniterioColors.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onGuardar(if (dinamica) null else cantidad.trim().toDoubleOrNull(), activa)
            }) { Text("Guardar") }
            if (regla.origen == "MANUAL") {
                OutlinedButton(onClick = onQuitar) { Text("Quitar") }
            }
        }
    }
}

@Composable
private fun DialogoAnadir(
    onCancelar: () -> Unit,
    onAnadir: (CrearReglaBody) -> Unit,
) {
    var categoria by remember { mutableStateOf("COMIDA") }
    var tipo by remember { mutableStateOf("POR_EVENTO") }
    var nombre by remember { mutableStateOf("") }
    var tamano by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("1") }
    var porCada by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            TextButton(
                enabled = nombre.isNotBlank() && tamano.isNotBlank() && factor.toDoubleOrNull() != null,
                onClick = {
                    onAnadir(
                        CrearReglaBody(
                            categoria = categoria,
                            nombre = nombre.trim(),
                            tamano = tamano.trim(),
                            tipoFormula = tipo,
                            factor = factor.toDouble(),
                            porCada = if (tipo == "POR_CADA_N_PENISTAS") porCada.toIntOrNull() else null,
                        ),
                    )
                },
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
        title = { Text("Nuevo artículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorTexto("Categoría", categoria, CATEGORIAS) { categoria = it }
                SelectorTexto("Fórmula", tipo, FORMULAS_CREABLES) { tipo = it }
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(tamano, { tamano = it }, label = { Text("Tamaño") }, singleLine = true)
                OutlinedTextField(factor, { factor = it }, label = { Text("Factor") }, singleLine = true)
                if (tipo == "POR_CADA_N_PENISTAS") {
                    OutlinedTextField(porCada, { porCada = it }, label = { Text("Por cada (peñistas)") }, singleLine = true)
                }
            }
        },
    )
}

/** Selector simple: pulsa para rotar entre las opciones. */
@Composable
private fun SelectorTexto(
    etiqueta: String,
    valor: String,
    opciones: List<Pair<String, String>>,
    onCambio: (String) -> Unit,
) {
    val actual = opciones.firstOrNull { it.first == valor } ?: opciones.first()
    Text(
        "$etiqueta: ${actual.second}",
        color = BaniterioColors.brandBright,
        modifier = Modifier.clickable {
            val i = opciones.indexOf(actual)
            onCambio(opciones[(i + 1) % opciones.size].first)
        },
    )
}
